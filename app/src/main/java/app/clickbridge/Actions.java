package app.clickbridge;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Actions communes aux différents écrans. */
final class Actions {
    private Actions() {}

    static final int REQ_PERMS = 1;

    static List<String> bluetoothPermissions() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return list;
    }

    static boolean hasBluetoothPermissions(Context c) {
        for (String p : bluetoothPermissions()) {
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    static boolean hasNotificationPermission(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static void requestAllPermissions(Activity a) {
        List<String> list = bluetoothPermissions();
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS);
        a.requestPermissions(list.toArray(new String[0]), REQ_PERMS);
    }

    static void startBridge(Context c) {
        c.startForegroundService(new Intent(c, BridgeService.class));
    }

    static void stopBridge(Context c) {
        c.startService(new Intent(c, BridgeService.class).setAction(BridgeService.ACTION_STOP));
    }

    static void openAccessibilitySettings(Activity a) {
        Toast.makeText(a, "Cherche « ClickBridge (appuis tactiles) » dans la liste, puis active-le.",
                Toast.LENGTH_LONG).show();
        a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    static void openAppDetails(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName())));
    }

    static boolean startCalibration(Activity a) {
        TapService t = TapService.instance;
        if (t == null) {
            Toast.makeText(a, "Active d'abord le service d'accessibilité.", Toast.LENGTH_LONG).show();
            return false;
        }
        t.startCalibration();
        Toast.makeText(a, "Ouvre MyWhoosh : les pastilles + et − restent affichées.",
                Toast.LENGTH_LONG).show();
        a.moveTaskToBack(true);
        return true;
    }

    static boolean isBatteryExempt(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static void askBatteryExemption(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (ActivityNotFoundException e) {
            a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    static void copyLog(Context c) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("journal ClickBridge", Bridge.get().logText()));
        Toast.makeText(c, "Journal copié", Toast.LENGTH_SHORT).show();
    }

    /** "port" ou "land" selon l'orientation actuelle. */
    static String orientationKey(Context c) {
        return c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "land" : "port";
    }

    static String orientationName(Context c) {
        return "land".equals(orientationKey(c)) ? "paysage" : "portrait";
    }
}
