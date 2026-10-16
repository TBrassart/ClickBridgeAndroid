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

/**
 * Service de premier plan : garde la connexion Bluetooth au Click quand une autre appli
 * (MyWhoosh) est à l'écran, transforme chaque appui en toucher via TapService (avec répétition
 * si le bouton reste enfoncé) et tient l'historique des sessions.
 */
public class BridgeService extends Service implements ClickClient.Listener {

    static final String ACTION_STOP = "app.clickbridge.STOP";
    static final String ACTION_CALIBRATE = "app.clickbridge.CALIBRATE";
    static final String ACTION_REPOST = "app.clickbridge.REPOST";
    static final String ACTION_NEXT_PROFILE = "app.clickbridge.NEXT_PROFILE";
    private static final String CHANNEL = "clickbridge";
    private static final int NOTIF_ID = 1;
    private static final long MAX_REPEAT_MS = 60_000L;   // garde-fou si un relâchement est perdu

    static volatile BridgeService current;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ClickClient client;
    private boolean plusDown, minusDown;
    private Runnable plusRepeat, minusRepeat;
    private long plusSince, minusSince;
    private int plusRepeats, minusRepeats;
    private String lastNotificationText = "";
    private long lastWarn;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel(CHANNEL, "ClickBridge",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        client = new ClickClient(this, this);
        current = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            cancelRepeat(true);
            cancelRepeat(false);
            client.stop();
            SessionLog.finish(this);
            Bridge.get().running(false);
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

        client.setVerbose(Prefs.verbose(this));
        if (!client.isRunning()) {
            client.start();
            Bridge.get().running(true);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        current = null;
        cancelRepeat(true);
        cancelRepeat(false);
        client.stop();
        SessionLog.finish(this);
        Bridge.get().running(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    void setVerbose(boolean v) {
        client.setVerbose(v);
    }

    void forgetClick() {
        client.forget();
    }

    // ---------------------------------------------------------------- notification

    private String composeText() {
        return Profiles.active(this).name + " · " + Bridge.get().statusText();
    }

    /** Appelé quand le profil actif change. */
    void refreshNotification() {
        updateNotification(composeText());
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

    // ---------------------------------------------------------------- ClickClient.Listener

    @Override
    public void onStatus(String text, int level) {
        Bridge.get().status(text, level);
        if (level == ClickClient.GREEN) {
            SessionLog.onReady(this, Profiles.active(this).name, Bridge.get().battery());
        } else {
            SessionLog.onNotReady(this);
        }
        updateNotification(composeText());
    }

    @Override
    public void onLog(String text) {
        Bridge.get().log(text);
    }

    @Override
    public void onBattery(int percent) {
        Bridge.get().battery(percent);
        SessionLog.onBattery(this, percent);
    }

    @Override
    public void onButtons(boolean plus, boolean minus) {
        Bridge.get().buttons(plus, minus);
        handleEdge(true, plus);
        handleEdge(false, minus);
    }

    // ---------------------------------------------------------------- appui et répétition

    private void handleEdge(boolean isPlus, boolean pressed) {
        boolean was = isPlus ? plusDown : minusDown;
        if (isPlus) plusDown = pressed; else minusDown = pressed;
        String name = isPlus ? "+" : "−";
        if (pressed && !was) {
            long now = SystemClock.uptimeMillis();
            if (isPlus) { plusSince = now; plusRepeats = 0; } else { minusSince = now; minusRepeats = 0; }
            if (Prefs.verbose(this)) {
                Bridge.get().log("Appui " + name + " : répétition " + (Prefs.repeatEnabled(this)
                        ? "activée (délai " + Prefs.repeatDelay(this) + " ms, intervalle "
                        + Math.max(Prefs.repeatInterval(this), Prefs.holdMs(this) + 20) + " ms)"
                        : "désactivée"));
            }
            
            scheduleRepeat(isPlus);
        } else if (!pressed && was) {
            if (Prefs.verbose(this)) {
                long since = isPlus ? plusSince : minusSince;
                int n = isPlus ? plusRepeats : minusRepeats;
                Bridge.get().log("Relâché " + name + " après " + (SystemClock.uptimeMillis() - since)
                        + " ms, " + n + " répétition(s)");
            }
            cancelRepeat(isPlus);
        }
    }

    /** Si la répétition est activée et que le bouton reste enfoncé : un toucher après le délai, puis à intervalle régulier. */
    private void scheduleRepeat(final boolean isPlus) {
        cancelRepeat(isPlus);
        if (!Prefs.repeatEnabled(this)) return;
        Runnable r = new Runnable() {
            @Override public void run() {
                boolean down = isPlus ? plusDown : minusDown;
                long since = isPlus ? plusSince : minusSince;
                if (!down || !Prefs.repeatEnabled(BridgeService.this)) return;
                if (SystemClock.uptimeMillis() - since > MAX_REPEAT_MS) {
                    Bridge.get().log("Répétition arrêtée après 60 s (relâchement non reçu ?).");
                    return;
                }
                int n = isPlus ? ++plusRepeats : ++minusRepeats;
                if (Prefs.verbose(BridgeService.this)) Bridge.get().log("Répétition n°" + n);
                tap(isPlus);
                // jamais plus rapide que la durée du toucher, sinon les gestes se chevauchent
                long interval = Math.max(Prefs.repeatInterval(BridgeService.this),
                        Prefs.holdMs(BridgeService.this) + 20L);
                handler.postDelayed(this, interval);
            }
        };
        if (isPlus) plusRepeat = r; else minusRepeat = r;
        handler.postDelayed(r, Prefs.repeatDelay(this));
    }

    private void cancelRepeat(boolean isPlus) {
        Runnable r = isPlus ? plusRepeat : minusRepeat;
        if (r != null) handler.removeCallbacks(r);
        if (isPlus) plusRepeat = null; else minusRepeat = null;
    }

    private void tap(boolean plus) {
        TapService t = TapService.instance;
        String problem;
        if (t == null) {
            problem = "Service d'accessibilité inactif : active-le dans les réglages d'Android.";
        } else {
            problem = t.tap(plus);
        }
        if (problem != null) {
            long now = SystemClock.uptimeMillis();
            if (Prefs.verbose(this) || now - lastWarn > 8000) {   // sinon : un message toutes les 8 s
                lastWarn = now;
                Bridge.get().log(problem);
            }
        } else {
            SessionLog.onTap(this, plus);
            if (Prefs.verbose(this)) {
                Bridge.get().log("Toucher envoyé (" + (plus ? "+" : "−") + ")");
            }
        }
    }
}
