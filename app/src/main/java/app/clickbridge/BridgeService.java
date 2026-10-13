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
import android.os.IBinder;
import android.os.SystemClock;

/**
 * Service de premier plan : garde la connexion Bluetooth au Click quand une autre appli
 * (MyWhoosh) est à l'écran, et transforme chaque appui en toucher via TapService.
 */
public class BridgeService extends Service implements ClickClient.Listener {

    static final String ACTION_STOP = "app.clickbridge.STOP";
    static final String ACTION_CALIBRATE = "app.clickbridge.CALIBRATE";
    private static final String CHANNEL = "clickbridge";
    private static final int NOTIF_ID = 1;

    static volatile BridgeService current;

    private ClickClient client;
    private boolean plusDown, minusDown;
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
            client.stop();
            Bridge.get().running(false);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startInForeground(Bridge.get().statusText());
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
                t.startCalibration();
            } else {
                Bridge.get().log("Active d'abord le service d'accessibilité de ClickBridge.");
            }
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
        client.stop();
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

        Notification.Action calibrateAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_manage),
                "Régler les points", calibrate).build();
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Arrêter", stop).build();

        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("ClickBridge")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(calibrateAction)
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
        updateNotification(text);
    }

    @Override
    public void onLog(String text) {
        Bridge.get().log(text);
    }

    @Override
    public void onBattery(int percent) {
        Bridge.get().battery(percent);
    }

    @Override
    public void onButtons(boolean plus, boolean minus) {
        Bridge.get().buttons(plus, minus);
        if (plus && !plusDown) tap(true);
        if (minus && !minusDown) tap(false);
        plusDown = plus;
        minusDown = minus;
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
            if (now - lastWarn > 8000) {      // évite de remplir le journal à chaque appui
                lastWarn = now;
                Bridge.get().log(problem);
            }
        } else if (Prefs.verbose(this)) {
            Bridge.get().log("Toucher envoyé (" + (plus ? "+" : "−") + ")");
        }
    }
}
