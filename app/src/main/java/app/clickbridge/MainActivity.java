package app.clickbridge;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Bridge.Ui {

    private static final int REQ_PERMS = 1;
    private static final long MIN_LIT_MS = 180;      // durée minimale d'allumage d'un indicateur
    private static final int TEXT = Color.parseColor("#e6e6e6");
    private static final int MUTED = Color.parseColor("#9a9a9a");

    private static final class Tile {
        final TextView view;
        final int onColor;
        boolean lit;
        long since;
        Runnable offTask;

        Tile(TextView view, int onColor) {
            this.view = view;
            this.onColor = onColor;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusView, batteryView, logView, accessView, pointsView, holdView, batteryOptView;
    private Button toggleButton, verboseButton, logButton;
    private Tile plusTile, minusTile;
    private boolean logVisible;

    // ---------------------------------------------------------------- cycle de vie

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bridge b = Bridge.get();
        b.setUi(this);
        onStatus(b.statusText(), b.statusLevel());
        if (b.battery() >= 0) onBattery(b.battery());
        logView.setText(b.logText());
        onRunning(b.isRunning());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSetup();
    }

    @Override
    protected void onStop() {
        Bridge.get().setUi(null);
        super.onStop();
    }

    // ---------------------------------------------------------------- permissions

    private List<String> bluetoothPermissions() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return list;
    }

    private boolean hasBluetoothPermissions() {
        for (String p : bluetoothPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestAllPermissions() {
        List<String> list = bluetoothPermissions();
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(list.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS) return;
        if (hasBluetoothPermissions()) {
            startBridge();
        } else {
            onStatus("Permission refusée : autorise « Appareils à proximité » "
                    + "dans les paramètres de l'appli.", ClickClient.RED);
        }
    }

    // ---------------------------------------------------------------- actions

    private void startBridge() {
        startForegroundService(new Intent(this, BridgeService.class));
    }

    private void stopBridge() {
        startService(new Intent(this, BridgeService.class).setAction(BridgeService.ACTION_STOP));
    }

    private void toggle() {
        if (Bridge.get().isRunning()) {
            stopBridge();
        } else if (hasBluetoothPermissions()) {
            startBridge();
        } else {
            requestAllPermissions();
        }
    }

    private void openAccessibilitySettings() {
        Toast.makeText(this, "Cherche « ClickBridge » dans la liste, puis active-le.",
                Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openAppDetails() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void calibrate() {
        TapService t = TapService.instance;
        if (t == null) {
            Toast.makeText(this, "Active d'abord le service d'accessibilité.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        t.startCalibration();
        Toast.makeText(this, "Ouvre MyWhoosh : les pastilles + et − restent affichées.",
                Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    private void askBatteryExemption() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void changeHold(int delta) {
        Prefs.setHoldMs(this, Prefs.holdMs(this) + delta);
        refreshSetup();
    }

    private void toggleVerbose() {
        boolean v = !Prefs.verbose(this);
        Prefs.setVerbose(this, v);
        BridgeService s = BridgeService.current;
        if (s != null) s.setVerbose(v);
        refreshSetup();
    }

    private void forgetClick() {
        BridgeService s = BridgeService.current;
        if (s != null) {
            s.forgetClick();
        } else {
            Prefs.setClickAddress(this, null);
            Bridge.get().log("Click oublié.");
        }
    }

    private void toggleLog() {
        logVisible = !logVisible;
        logView.setVisibility(logVisible ? View.VISIBLE : View.GONE);
        logButton.setText(logVisible ? "Masquer le journal ▴" : "Afficher le journal ▾");
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("journal ClickBridge", Bridge.get().logText()));
        Toast.makeText(this, "Journal copié", Toast.LENGTH_SHORT).show();
    }

    private void refreshSetup() {
        accessView.setText(TapService.isActive()
                ? "Service d'accessibilité : actif ✓"
                : "Service d'accessibilité : inactif ✗");
        accessView.setTextColor(TapService.isActive()
                ? Color.parseColor("#22a04b") : Color.parseColor("#e04b3a"));

        boolean port = Prefs.hasPoints(this, "port");
        boolean land = Prefs.hasPoints(this, "land");
        pointsView.setText("Points d'appui réglés : portrait " + (port ? "✓" : "✗")
                + "  ·  paysage " + (land ? "✓" : "✗"));

        holdView.setText("Durée du toucher : " + Prefs.holdMs(this) + " ms");
        verboseButton.setText(Prefs.verbose(this) ? "Journal détaillé : oui" : "Journal détaillé : non");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean exempt = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryOptView.setText(exempt
                ? "Arrière-plan : autorisé sans restriction de batterie ✓"
                : "Arrière-plan : optimisation de batterie active (conseillé : l'autoriser)");
    }

    // ---------------------------------------------------------------- interface

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1c1c1c"));
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        box.setLayoutParams(lp);
        box.addView(label(title, 15, Color.WHITE, true));
        parent.addView(box);
        return box;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#121212"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });

        statusView = label("…", 18, MUTED, true);
        root.addView(statusView);
        batteryView = label("Batterie : –", 14, TEXT, false);
        root.addView(batteryView);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setPadding(0, dp(10), 0, 0);
        plusTile = makeTile("+", Color.parseColor("#1f9d4d"));
        minusTile = makeTile("−", Color.parseColor("#2a6fdb"));
        tiles.addView(plusTile.view);
        tiles.addView(minusTile.view);
        root.addView(tiles);

        toggleButton = button("Démarrer", new View.OnClickListener() {
            @Override public void onClick(View v) { toggle(); }
        });
        root.addView(toggleButton);

        LinearLayout taps = section(root, "Appuis tactiles sur MyWhoosh");
        accessView = label("", 14, TEXT, false);
        taps.addView(accessView);
        taps.addView(button("Ouvrir les réglages d'accessibilité", new View.OnClickListener() {
            @Override public void onClick(View v) { openAccessibilitySettings(); }
        }));
        taps.addView(button("Infos de l'appli (« paramètres restreints »)", new View.OnClickListener() {
            @Override public void onClick(View v) { openAppDetails(); }
        }));
        pointsView = label("", 14, TEXT, false);
        taps.addView(pointsView);
        taps.addView(button("Régler les points d'appui", new View.OnClickListener() {
            @Override public void onClick(View v) { calibrate(); }
        }));
        holdView = label("", 14, TEXT, false);
        taps.addView(holdView);
        LinearLayout holdRow = new LinearLayout(this);
        holdRow.setOrientation(LinearLayout.HORIZONTAL);
        holdRow.addView(button("− 10 ms", new View.OnClickListener() {
            @Override public void onClick(View v) { changeHold(-10); }
        }));
        holdRow.addView(button("+ 10 ms", new View.OnClickListener() {
            @Override public void onClick(View v) { changeHold(10); }
        }));
        taps.addView(holdRow);

        LinearLayout bg = section(root, "Arrière-plan et appairage");
        batteryOptView = label("", 14, TEXT, false);
        bg.addView(batteryOptView);
        bg.addView(button("Autoriser en arrière-plan", new View.OnClickListener() {
            @Override public void onClick(View v) { askBatteryExemption(); }
        }));
        bg.addView(button("Oublier le Click (refaire un appairage)", new View.OnClickListener() {
            @Override public void onClick(View v) { forgetClick(); }
        }));

        LinearLayout diag = section(root, "Diagnostic");
        verboseButton = button("", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleVerbose(); }
        });
        diag.addView(verboseButton);
        logButton = button("Afficher le journal ▾", new View.OnClickListener() {
            @Override public void onClick(View v) { toggleLog(); }
        });
        diag.addView(logButton);
        diag.addView(button("Copier le journal", new View.OnClickListener() {
            @Override public void onClick(View v) { copyLog(); }
        }));
        logView = label("", 11, Color.parseColor("#d0d0d0"), false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setVisibility(View.GONE);
        diag.addView(logView);

        setContentView(scroll);
    }

    private Tile makeTile(String symbol, int onColor) {
        TextView v = new TextView(this);
        v.setText(symbol);
        v.setTextSize(56);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(110), 1f);
        lp.setMargins(dp(6), 0, dp(6), 0);
        v.setLayoutParams(lp);
        Tile t = new Tile(v, onColor);
        paint(t);
        return t;
    }

    private void paint(Tile t) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(16));
        d.setColor(t.lit ? t.onColor : Color.parseColor("#2d2d2d"));
        t.view.setBackground(d);
        t.view.setTextColor(t.lit ? Color.WHITE : MUTED);
    }

    private void update(final Tile t, boolean pressed) {
        if (pressed) {
            if (t.offTask != null) {
                handler.removeCallbacks(t.offTask);
                t.offTask = null;
            }
            if (!t.lit) {
                t.lit = true;
                t.since = SystemClock.uptimeMillis();
                paint(t);
            }
        } else if (t.lit && t.offTask == null) {
            long remaining = Math.max(0L, MIN_LIT_MS - (SystemClock.uptimeMillis() - t.since));
            t.offTask = new Runnable() {
                @Override public void run() {
                    t.offTask = null;
                    t.lit = false;
                    paint(t);
                }
            };
            handler.postDelayed(t.offTask, remaining);
        }
    }

    // ---------------------------------------------------------------- Bridge.Ui

    @Override
    public void onStatus(String text, int level) {
        int color;
        switch (level) {
            case ClickClient.GREEN: color = Color.parseColor("#22a04b"); break;
            case ClickClient.ORANGE: color = Color.parseColor("#e08a00"); break;
            case ClickClient.RED: color = Color.parseColor("#e04b3a"); break;
            default: color = MUTED;
        }
        statusView.setText(text);
        statusView.setTextColor(color);
    }

    @Override
    public void onLog(String line) {
        logView.append(line + "\n");
    }

    @Override
    public void onButtons(boolean plus, boolean minus) {
        update(plusTile, plus);
        update(minusTile, minus);
    }

    @Override
    public void onBattery(int percent) {
        batteryView.setText("Batterie : " + percent + " %");
    }

    @Override
    public void onRunning(boolean running) {
        toggleButton.setText(running ? "Arrêter" : "Démarrer");
        if (!running) batteryView.setText("Batterie : –");
    }
}
