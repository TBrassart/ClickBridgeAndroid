package app.clickbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service de premier plan : garde les connexions Bluetooth aux manettes (le Click, ou les deux
 * unités du Ride) quand une autre appli est à l'écran, transforme chaque appui en toucher via
 * TapService (avec répétition si le bouton reste enfoncé) et tient l'historique des sessions.
 */
public class BridgeService extends Service {

    static final String ACTION_STOP = "app.clickbridge.STOP";
    static final String ACTION_CALIBRATE = "app.clickbridge.CALIBRATE";
    static final String ACTION_REPOST = "app.clickbridge.REPOST";
    static final String ACTION_NEXT_PROFILE = "app.clickbridge.NEXT_PROFILE";
    private static final String CHANNEL = "clickbridge";
    private static final int NOTIF_ID = 1;
    private static final long MAX_REPEAT_MS = 60_000L;   // garde-fou si un relâchement est perdu
    private static final long RIDE_STAGGER_MS = 1500L;   // la 2e unité du Ride démarre un peu après la 1re

    static volatile BridgeService current;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ClickClient> clients = new ArrayList<>();
    private final Map<String, Integer> roleLevel = new HashMap<>();
    private final Map<String, String> roleText = new HashMap<>();
    private final Map<String, Integer> roleBattery = new HashMap<>();
    private final Set<String> down = new HashSet<>();
    private final Map<String, Runnable> repeats = new HashMap<>();
    private final Map<String, Long> pressedAt = new HashMap<>();
    private final Map<String, Integer> repeatCount = new HashMap<>();
    private boolean started;
    private boolean sessionReady;
    private String lastNotificationText = "";
    private long lastWarn;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(CHANNEL, "ClickBridge",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        current = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            shutdown();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startInForeground(composeText());
        } catch (RuntimeException e) {
            // Android refuse parfois le démarrage au premier plan (permission manquante, redémarrage
            // automatique en arrière-plan) : on l'indique au lieu de planter.
            Bridge.get().log("Service de premier plan refusé : " + e.getMessage());
            Bridge.get().status("Android a refusé le service en arrière-plan : relance depuis l'appli.",
                    ClickClient.RED);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_CALIBRATE.equals(action)) {
            TapService t = TapService.instance;
            if (t != null) {
                t.closeNotificationShade();
                t.startCalibration();
            } else {
                Bridge.get().log("Active d'abord le service d'accessibilité de ClickBridge.");
            }
        } else if (ACTION_NEXT_PROFILE.equals(action)) {
            Profile p = Profiles.next(this);
            if (p != null) Bridge.get().log("Profil actif : " + p.name);
        }

        if (!started) {
            started = true;
            startClients();
            Bridge.get().running(true);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        current = null;
        shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- manettes

    private List<String> rolesForMode() {
        List<String> roles = new ArrayList<>();
        if (Buttons.MODE_RIDE.equals(Prefs.deviceMode(this))) {
            roles.add(ClickClient.ROLE_RIDE_LEFT);
            roles.add(ClickClient.ROLE_RIDE_RIGHT);
        } else {
            roles.add(ClickClient.ROLE_CLICK);
        }
        return roles;
    }

    /** Une écoute par manette : les événements sont rapportés au service avec le rôle de la manette. */
    private final class RoleListener implements ClickClient.Listener {
        private final String role;

        RoleListener(String role) {
            this.role = role;
        }

        @Override public void onStatus(String text, int level) {
            roleText.put(role, text);
            roleLevel.put(role, level);
            recomputeStatus();
        }

        @Override public void onLog(String text) {
            Bridge.get().log(text);
        }

        @Override public void onBattery(int percent) {
            roleBattery.put(role, percent);
            int min = 101;
            for (int v : roleBattery.values()) if (v >= 0 && v < min) min = v;
            if (min <= 100) {
                Bridge.get().battery(min);
                SessionLog.onBattery(BridgeService.this, min);
            }
        }

        @Override public void onButton(String id, boolean pressed) {
            handleButton(id, pressed);
        }
    }

    private void startClients() {
        clients.clear();
        roleLevel.clear();
        roleText.clear();
        roleBattery.clear();
        final boolean verbose = Prefs.verbose(this);
        List<String> roles = rolesForMode();
        for (int i = 0; i < roles.size(); i++) {
            final ClickClient c = new ClickClient(this, roles.get(i), new RoleListener(roles.get(i)));
            c.setVerbose(verbose);
            clients.add(c);
            if (i == 0) {
                c.start();
            } else {
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (started && clients.contains(c)) c.start();
                    }
                }, RIDE_STAGGER_MS * i);
            }
        }
    }

    private void stopClients() {
        for (ClickClient c : clients) c.stop();
        clients.clear();
        for (String id : new ArrayList<>(down)) handleButton(id, false);
        sessionReady = false;
    }

    private void shutdown() {
        started = false;
        handler.removeCallbacksAndMessages(null);
        for (Runnable r : repeats.values()) handler.removeCallbacks(r);
        repeats.clear();
        stopClients();
        SessionLog.finish(this);
        Bridge.get().running(false);
    }

    /** L'appareil choisi (Click ou Ride) a changé : on repart avec les bonnes manettes. */
    void applyMode() {
        if (!started) return;
        stopClients();
        SessionLog.finish(this);
        Bridge.get().status("Changement d'appareil…", ClickClient.ORANGE);
        startClients();
    }

    void setVerbose(boolean v) {
        for (ClickClient c : clients) c.setVerbose(v);
    }

    void forgetDevices() {
        for (ClickClient c : clients) c.forget();
    }

    // ---------------------------------------------------------------- état global

    private static String sideName(String role) {
        return ClickClient.ROLE_RIDE_LEFT.equals(role) ? "Gauche" : "Droite";
    }

    /** Résume l'état de toutes les manettes en un statut et une couleur. */
    private void recomputeStatus() {
        List<String> roles = rolesForMode();
        int ready = 0;
        boolean anyRed = false;
        for (String r : roles) {
            Integer lv = roleLevel.get(r);
            if (lv != null && lv == ClickClient.GREEN) ready++;
            if (lv != null && lv == ClickClient.RED) anyRed = true;
        }
        String text;
        int level;
        if (roles.size() == 1) {
            String t = roleText.get(roles.get(0));
            Integer lv = roleLevel.get(roles.get(0));
            text = t == null ? "…" : t;
            level = lv == null ? ClickClient.GRAY : lv;
        } else if (ready == roles.size()) {
            text = "Ride connecté : unités gauche et droite prêtes";
            level = ClickClient.GREEN;
        } else {
            StringBuilder sb = new StringBuilder();
            for (String r : roles) {
                String t = roleText.get(r);
                Integer lv = roleLevel.get(r);
                if (sb.length() > 0) sb.append('\n');
                sb.append(sideName(r)).append(" : ")
                        .append(lv != null && lv == ClickClient.GREEN ? "prête" : (t == null ? "…" : t));
            }
            text = sb.toString();
            level = ready > 0 ? ClickClient.ORANGE : (anyRed ? ClickClient.RED : ClickClient.ORANGE);
        }
        Bridge.get().status(text, level);

        if (ready > 0 && !sessionReady) {
            sessionReady = true;
            SessionLog.onReady(this, Profiles.active(this).name, Bridge.get().battery());
        } else if (ready == 0 && sessionReady) {
            sessionReady = false;
            SessionLog.onNotReady(this);
        }
        if (started) updateNotification(composeText());     // pas de notification fantôme après l'arrêt
    }

    // ---------------------------------------------------------------- notification

    private String composeText() {
        return Profiles.active(this).name + " · " + Bridge.get().statusText().replace('\n', ' ');
    }

    /** Appelé quand le profil actif change. */
    void refreshNotification() {
        if (started) updateNotification(composeText());
    }

    private void startInForeground(String text) {
        Notification n = buildNotification(text);
        lastNotificationText = text;
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification buildNotification(String text) {
        int immutable = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), immutable);
        PendingIntent calibrate = PendingIntent.getService(this, 1,
                new Intent(this, BridgeService.class).setAction(ACTION_CALIBRATE), immutable);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, BridgeService.class).setAction(ACTION_STOP), immutable);
        // Android 14+ permet de balayer cette notification : on la réaffiche aussitôt
        PendingIntent dismissed = PendingIntent.getService(this, 3,
                new Intent(this, BridgeService.class).setAction(ACTION_REPOST), immutable);
        PendingIntent nextProfile = PendingIntent.getService(this, 4,
                new Intent(this, BridgeService.class).setAction(ACTION_NEXT_PROFILE), immutable);

        Notification.Action calibrateAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_manage),
                "Régler les points", calibrate).build();
        Notification.Action profileAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_next),
                "Profil suivant", nextProfile).build();
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Arrêter", stop).build();

        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("ClickBridge")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(open)
                .setOngoing(true)
                .setDeleteIntent(dismissed)
                .setOnlyAlertOnce(true)
                .addAction(calibrateAction)
                .addAction(profileAction)
                .addAction(stopAction)
                .build();
    }

    private void updateNotification(String text) {
        if (text.equals(lastNotificationText)) return;
        lastNotificationText = text;
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(text));
    }

    // ---------------------------------------------------------------- appui et répétition

    private void handleButton(String id, boolean pressed) {
        boolean was = down.contains(id);
        if (pressed == was) return;
        String name = Buttons.label(id);
        if (pressed) {
            down.add(id);
            pressedAt.put(id, SystemClock.uptimeMillis());
            repeatCount.put(id, 0);
            Bridge.get().lastButton(name);
            if (Prefs.verbose(this)) {
                Bridge.get().log("Appui : " + name + (Buttons.repeatable(id)
                        ? " · répétition " + (Prefs.repeatEnabled(this)
                        ? "activée (délai " + Prefs.repeatDelay(this) + " ms, intervalle "
                        + Math.max(Prefs.repeatInterval(this), Prefs.holdMs(this) + 20) + " ms)"
                        : "désactivée") : ""));
            }
            tap(id);
            scheduleRepeat(id);
        } else {
            down.remove(id);
            if (Prefs.verbose(this)) {
                Long since = pressedAt.get(id);
                Integer n = repeatCount.get(id);
                Bridge.get().log("Relâché : " + name + " après "
                        + (since == null ? 0 : SystemClock.uptimeMillis() - since) + " ms, "
                        + (n == null ? 0 : n) + " répétition(s)");
            }
            cancelRepeat(id);
        }
        updateTiles();
    }

    /** Les deux indicateurs de l'accueil : « vitesse + » et « vitesse − ». */
    private void updateTiles() {
        boolean plus = false, minus = false;
        for (String id : down) {
            int d = Buttons.direction(id);
            if (d > 0) plus = true; else if (d < 0) minus = true;
        }
        Bridge.get().buttons(plus, minus);
    }

    /** Si la répétition est activée et que le bouton reste enfoncé : un toucher après le délai, puis à intervalle régulier. */
    private void scheduleRepeat(final String id) {
        cancelRepeat(id);
        if (!Buttons.repeatable(id) || !Prefs.repeatEnabled(this)) return;
        Runnable r = new Runnable() {
            @Override public void run() {
                if (!down.contains(id) || !Prefs.repeatEnabled(BridgeService.this)) return;
                Long since = pressedAt.get(id);
                if (since != null && SystemClock.uptimeMillis() - since > MAX_REPEAT_MS) {
                    Bridge.get().log("Répétition arrêtée après 60 s (relâchement non reçu ?).");
                    return;
                }
                Integer n = repeatCount.get(id);
                int next = (n == null ? 0 : n) + 1;
                repeatCount.put(id, next);
                if (Prefs.verbose(BridgeService.this)) Bridge.get().log("Répétition n°" + next);
                tap(id);
                // jamais plus rapide que la durée du toucher, sinon les gestes se chevauchent
                long interval = Math.max(Prefs.repeatInterval(BridgeService.this),
                        Prefs.holdMs(BridgeService.this) + 20L);
                handler.postDelayed(this, interval);
            }
        };
        repeats.put(id, r);
        handler.postDelayed(r, Prefs.repeatDelay(this));
    }

    private void cancelRepeat(String id) {
        Runnable r = repeats.remove(id);
        if (r != null) handler.removeCallbacks(r);
    }

    private void tap(String id) {
        TapService t = TapService.instance;
        String problem;
        if (t == null) {
            problem = "Service d'accessibilité inactif : active-le dans les réglages d'Android.";
        } else {
            problem = t.tap(id);
        }
        if (problem == null) {
            int dir = Buttons.direction(id);
            if (dir != 0) SessionLog.onTap(this, dir > 0);     // l'historique compte les changements de vitesse
            if (Prefs.verbose(this)) Bridge.get().log("Toucher envoyé : " + Buttons.label(id));
        } else if (problem.isEmpty()) {
            if (Prefs.verbose(this)) {
                Bridge.get().log(Buttons.label(id) + " : non piloté par le profil actif (à activer dans « Gérer les profils »).");
            }
        } else {
            long now = SystemClock.uptimeMillis();
            if (Prefs.verbose(this) || now - lastWarn > 8000) {   // sinon : un message toutes les 8 s
                lastWarn = now;
                Bridge.get().log(problem);
            }
        }
    }
}
