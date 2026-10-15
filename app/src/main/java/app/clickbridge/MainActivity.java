package app.clickbridge;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Accueil : état de la connexion, indicateurs + et −, démarrage, état de la configuration. */
public class MainActivity extends BaseActivity implements Bridge.Ui {

    private static final long MIN_LIT_MS = 180;      // durée minimale d'allumage d'un indicateur

    private static final class Tile {
        final LinearLayout box;
        final TextView symbol, label;
        final int colorA, colorB;
        boolean lit;
        long since;
        Runnable offTask;

        Tile(LinearLayout box, TextView symbol, TextView label, int colorA, int colorB) {
            this.box = box;
            this.symbol = symbol;
            this.label = label;
            this.colorA = colorA;
            this.colorB = colorB;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusText, batteryText, chipBt, chipAccess, chipPoints, chipBg;
    private TextView profileName, profileInfo, historyText;
    private BatteryBar batteryBar;
    private Button startButton;
    private Tile plusTile, minusTile;
    private boolean pendingStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildHome();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bridge b = Bridge.get();
        b.setUi(this);
        onStatus(b.statusText(), b.statusLevel());
        onBattery(b.battery());
        onRunning(b.isRunning());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChips();
        refreshProfile();
        refreshHistory();
    }

    @Override
    protected void onStop() {
        Bridge.get().clearUi(this);
        super.onStop();
    }

    // ---------------------------------------------------------------- construction

    private void buildHome() {
        // en-tête : logo, titre, réglages, aide
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(8), 0, dp(4));
        ImageView logo = new ImageView(this);
        try (InputStream in = getAssets().open("logo.png")) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            logo.setImageBitmap(bmp);
        } catch (IOException ignored) {
            // pas de logo : on garde seulement le titre
        }
        head.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(Ui.text(this, "ClickBridge", 24, theme.text, true));
        col.addView(Ui.text(this, "Zwift Click → MyWhoosh", 13, theme.muted, false));
        head.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.roundButton(this, theme, "⚙", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        head.addView(Ui.roundButton(this, theme, "?", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HelpActivity.class));
            }
        }));
        content.addView(head);

        // carte d'état
        LinearLayout status = Ui.card(this, theme);
        status.addView(Ui.text(this, "ÉTAT", 11, theme.muted, true));
        statusText = Ui.text(this, "Arrêté", 17, theme.muted, true);
        statusText.setPadding(0, dp(4), 0, dp(12));
        status.addView(statusText);
        batteryBar = new BatteryBar(this, theme);
        status.addView(batteryBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));
        batteryText = Ui.text(this, "Batterie du Click : –", 13, theme.muted, false);
        batteryText.setPadding(0, dp(6), 0, 0);
        status.addView(batteryText);
        content.addView(status);

        // indicateurs + et −
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setLayoutParams(Ui.fullTop(this, 14));
        plusTile = makeTile("+", "Bouton +", theme.plusA, theme.plusB);
        minusTile = makeTile("−", "Bouton −", theme.minusA, theme.minusB);
        LinearLayout.LayoutParams lpPlus = new LinearLayout.LayoutParams(0, dp(120), 1f);
        lpPlus.rightMargin = dp(7);
        LinearLayout.LayoutParams lpMinus = new LinearLayout.LayoutParams(0, dp(120), 1f);
        lpMinus.leftMargin = dp(7);
        tiles.addView(plusTile.box, lpPlus);
        tiles.addView(minusTile.box, lpMinus);
        content.addView(tiles);

        // démarrer / arrêter
        startButton = Ui.primary(this, "Démarrer", theme.cyan, theme.blue, new View.OnClickListener() {
            @Override public void onClick(View v) { toggle(); }
        });
        ((LinearLayout.LayoutParams) startButton.getLayoutParams()).topMargin = dp(14);
        content.addView(startButton);

        // profil actif
        LinearLayout prof = Ui.card(this, theme);
        prof.addView(Ui.text(this, "PROFIL ACTIF", 11, theme.muted, true));
        profileName = Ui.text(this, "", 20, theme.text, true);
        profileName.setPadding(0, dp(4), 0, 0);
        prof.addView(profileName);
        profileInfo = Ui.text(this, "", 12, theme.muted, false);
        prof.addView(profileInfo);
        addButtons(prof,
                Ui.soft(this, theme, "Changer de profil", new View.OnClickListener() {
                    @Override public void onClick(View v) { chooseProfile(); }
                }),
                Ui.soft(this, theme, "Gérer les profils", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, ProfilesActivity.class));
                    }
                }));
        content.addView(prof);

        // assistant : bouton bien visible
        content.addView(wizardButton("✨  Assistant de configuration",
                "Permissions, accessibilité, points d'appui… pas à pas, avec des explications.",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, WizardActivity.class));
                    }
                }));

        // état de la configuration
        LinearLayout cfg = Ui.card(this, theme);
        cfg.addView(Ui.text(this, "CONFIGURATION", 11, theme.muted, true));
        chipBt = addChip(cfg);
        chipAccess = addChip(cfg);
        chipPoints = addChip(cfg);
        chipBg = addChip(cfg);
        content.addView(cfg);

        // historique
        LinearLayout hist = Ui.card(this, theme);
        hist.addView(Ui.text(this, "HISTORIQUE", 11, theme.muted, true));
        historyText = Ui.text(this, "", 14, theme.text, false);
        historyText.setPadding(0, dp(4), 0, 0);
        hist.addView(historyText);
        hist.addView(Ui.soft(this, theme, "Voir l'historique des sessions", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        }));
        content.addView(hist);

        TextView foot = Ui.text(this, "Version 0.4  ·  Zwift Click V1", 12, theme.muted, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(18), 0, 0);
        content.addView(foot, Ui.fullTop(this, 0));
    }

    private TextView addChip(LinearLayout parent) {
        TextView chip = Ui.pill(this, "", theme.muted);
        parent.addView(chip, Ui.wrapTop(this, 10));
        return chip;
    }

    private Tile makeTile(String symbol, String caption, int a, int b) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView sym = Ui.text(this, symbol, 52, a, true);
        sym.setGravity(Gravity.CENTER);
        TextView lab = Ui.text(this, caption, 12, theme.muted, false);
        lab.setGravity(Gravity.CENTER);
        box.addView(sym);
        box.addView(lab);
        Tile t = new Tile(box, sym, lab, a, b);
        paint(t);
        return t;
    }

    private void paint(Tile t) {
        if (t.lit) {
            t.box.setBackground(Ui.grad(this, t.colorA, t.colorB, 22,
                    GradientDrawable.Orientation.TL_BR));
            t.symbol.setTextColor(Color.WHITE);
            t.label.setTextColor(0xE6FFFFFF);
            t.box.setElevation(dp(12));
        } else {
            GradientDrawable g = Ui.rect(this, theme.tileOff, 22);
            g.setStroke(dp(1), theme.stroke);
            t.box.setBackground(g);
            t.symbol.setTextColor(t.colorA);
            t.label.setTextColor(theme.muted);
            t.box.setElevation(0f);
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

    // ---------------------------------------------------------------- actions

    private void toggle() {
        if (Bridge.get().isRunning()) {
            Actions.stopBridge(this);
        } else if (Actions.hasBluetoothPermissions(this)) {
            Actions.startBridge(this);
        } else {
            pendingStart = true;
            Actions.requestAllPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != Actions.REQ_PERMS) return;
        refreshChips();
        if (pendingStart && Actions.hasBluetoothPermissions(this)) {
            Actions.startBridge(this);
        } else if (pendingStart) {
            onStatus("Permission refusée : autorise « Appareils à proximité » "
                    + "dans les paramètres de l'appli.", ClickClient.RED);
        }
        pendingStart = false;
    }

    private void refreshChips() {
        boolean bt = Actions.hasBluetoothPermissions(this);
        Ui.setPill(this, chipBt, bt ? "✓  Bluetooth autorisé" : "✗  Bluetooth à autoriser",
                bt ? theme.ok : theme.err);

        boolean access = TapService.isActive();
        Ui.setPill(this, chipAccess, access ? "✓  Accessibilité activée" : "✗  Accessibilité à activer",
                access ? theme.ok : theme.err);

        boolean points = Prefs.hasPoints(this, Actions.orientationKey(this));
        String orient = Actions.orientationName(this);
        Ui.setPill(this, chipPoints,
                points ? "✓  Points d'appui réglés (" + orient + ")"
                        : "✗  Points d'appui à régler (" + orient + ")  ·  profil " + Profiles.active(this).name,
                points ? theme.ok : theme.err);

        boolean bg = Actions.isBatteryExempt(this);
        Ui.setPill(this, chipBg, bg ? "✓  Arrière-plan autorisé" : "!  Arrière-plan : à autoriser (conseillé)",
                bg ? theme.ok : theme.warn);
    }

    private void refreshProfile() {
        Profile p = Profiles.active(this);
        profileName.setText(p.name);
        boolean isDefault = p.id.equals(Profiles.defaultId(this));
        profileInfo.setText((isDefault ? "Profil par défaut  ·  " : "")
                + "portrait " + (p.hasPoints("port") ? "✓" : "✗")
                + "  ·  paysage " + (p.hasPoints("land") ? "✓" : "✗"));
    }

    private void chooseProfile() {
        final List<Profile> list = Profiles.all(this);
        String activeId = Profiles.active(this).id;
        CharSequence[] names = new CharSequence[list.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = (list.get(i).id.equals(activeId) ? "✓  " : "      ") + list.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("Changer de profil")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Profiles.setActive(MainActivity.this, list.get(which).id);
                    }
                })
                .show();
    }

    private void refreshHistory() {
        List<SessionBook.Session> sessions = SessionLog.list(this);
        if (sessions.isEmpty()) {
            historyText.setText("Aucune session pour le moment.");
            return;
        }
        SessionBook.Session last = sessions.get(0);
        long now = System.currentTimeMillis();
        historyText.setText("Dernière session : " + SessionBook.formatDate(last.start) + "\n"
                + SessionBook.formatDuration(SessionBook.duration(last, now))
                + "  ·  + " + last.plus + "  ·  − " + last.minus);
    }

    // ---------------------------------------------------------------- Bridge.Ui

    @Override
    public void onStatus(String text, int level) {
        int color;
        switch (level) {
            case ClickClient.GREEN: color = theme.ok; break;
            case ClickClient.ORANGE: color = theme.warn; break;
            case ClickClient.RED: color = theme.err; break;
            default: color = theme.muted;
        }
        statusText.setText(text);
        statusText.setTextColor(color);
    }

    @Override
    public void onLog(String line) {
        // le journal se consulte dans les réglages
    }

    @Override
    public void onButtons(boolean plus, boolean minus) {
        update(plusTile, plus);
        update(minusTile, minus);
    }

    @Override
    public void onBattery(int percent) {
        batteryBar.setLevel(percent);
        batteryText.setText(percent < 0 ? "Batterie du Click : –" : "Batterie du Click : " + percent + " %");
    }

    @Override
    public void onProfileChanged() {
        refreshProfile();
        refreshChips();
    }

    @Override
    public void onRunning(boolean running) {
        if (running) {
            startButton.setText("Arrêter");
            startButton.setBackground(Ui.ripple(this, Ui.grad(this, 0xFFFB7185, 0xFFE11D48, 16,
                    GradientDrawable.Orientation.LEFT_RIGHT), 16));
        } else {
            startButton.setText("Démarrer");
            startButton.setBackground(Ui.ripple(this, Ui.grad(this, theme.cyan, theme.blue, 16,
                    GradientDrawable.Orientation.LEFT_RIGHT), 16));
            onBattery(-1);
        }
    }
}
