package app.clickbridge;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements ClickClient.Listener {

    private static final int REQ_PERMS = 1;
    private static final long MIN_LIT_MS = 180;      // durée minimale d'allumage d'un indicateur
    private static final int MAX_LOG_CHARS = 20000;

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
    private final StringBuilder logBuf = new StringBuilder();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private ClickClient client;
    private TextView statusView, batteryView, logView;
    private ScrollView logScroll;
    private Button toggleButton;
    private Tile plusTile, minusTile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        client = new ClickClient(this, this);
        buildUi();

        if (hasPermissions()) {
            client.start();
        } else {
            requestPermissions(neededPermissions(), REQ_PERMS);
        }
    }

    @Override
    protected void onDestroy() {
        client.stop();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- permissions

    private String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasPermissions() {
        for (String p : neededPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS) return;
        if (hasPermissions()) {
            client.start();
        } else {
            onStatus("Permission refusée : autorise « Appareils à proximité » "
                    + "dans les paramètres de l'appli.", ClickClient.RED);
        }
    }

    // ---------------------------------------------------------------- interface

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(dp(16) + insets.getSystemWindowInsetLeft(),
                        dp(16) + insets.getSystemWindowInsetTop(),
                        dp(16) + insets.getSystemWindowInsetRight(),
                        dp(16) + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });

        statusView = new TextView(this);
        statusView.setTextSize(18);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setTextColor(Color.parseColor("#8a8a8a"));
        statusView.setText("…");
        root.addView(statusView);

        batteryView = new TextView(this);
        batteryView.setTextSize(14);
        batteryView.setTextColor(Color.parseColor("#cfcfcf"));
        batteryView.setText("Batterie : –");
        batteryView.setPadding(0, dp(4), 0, dp(12));
        root.addView(batteryView);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        plusTile = makeTile("+", Color.parseColor("#1f9d4d"));
        minusTile = makeTile("−", Color.parseColor("#2a6fdb"));
        tiles.addView(plusTile.view);
        tiles.addView(minusTile.view);
        root.addView(tiles);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, dp(8));
        toggleButton = new Button(this);
        toggleButton.setText("Arrêter");
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggle();
            }
        });
        Button copyButton = new Button(this);
        copyButton.setText("Copier le journal");
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("journal ClickBridge", logBuf.toString()));
            }
        });
        buttons.addView(toggleButton);
        buttons.addView(copyButton);
        root.addView(buttons);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextColor(Color.parseColor("#d0d0d0"));
        logView.setTextIsSelectable(true);
        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        root.addView(logScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private Tile makeTile(String symbol, int onColor) {
        TextView v = new TextView(this);
        v.setText(symbol);
        v.setTextSize(64);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(140), 1f);
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
        t.view.setTextColor(t.lit ? Color.WHITE : Color.parseColor("#9a9a9a"));
    }

    private void toggle() {
        if (client.isRunning()) {
            client.stop();
            toggleButton.setText("Démarrer");
        } else if (hasPermissions()) {
            client.start();
            toggleButton.setText("Arrêter");
        } else {
            requestPermissions(neededPermissions(), REQ_PERMS);
        }
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

    // ---------------------------------------------------------------- ClickClient.Listener

    @Override
    public void onStatus(String text, int level) {
        int color;
        switch (level) {
            case ClickClient.GREEN: color = Color.parseColor("#22a04b"); break;
            case ClickClient.ORANGE: color = Color.parseColor("#e08a00"); break;
            case ClickClient.RED: color = Color.parseColor("#e04b3a"); break;
            default: color = Color.parseColor("#8a8a8a");
        }
        statusView.setText(text);
        statusView.setTextColor(color);
    }

    @Override
    public void onLog(String text) {
        logBuf.append(clock.format(new Date())).append("  ").append(text).append('\n');
        if (logBuf.length() > MAX_LOG_CHARS) {
            logBuf.delete(0, logBuf.length() - MAX_LOG_CHARS);
        }
        logView.setText(logBuf);
        logScroll.post(new Runnable() {
            @Override public void run() {
                logScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
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
}
