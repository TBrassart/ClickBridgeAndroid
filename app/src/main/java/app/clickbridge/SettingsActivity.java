package app.clickbridge;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Réglages : chaque option est accompagnée d'une explication. */
public class SettingsActivity extends BaseActivity implements Bridge.Ui {

    private TextView pointsPill, holdValue, accessPill, bgPill, addressText, logView;
    private Button verboseButton, logButton;
    private final Button[] themeButtons = new Button[3];
    private boolean logVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backHeader("Réglages", "Chaque option est expliquée juste en dessous");

        content.addView(wizardButton("✨  Relancer l'assistant de configuration",
                "Reprend les étapes une par une.", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this, WizardActivity.class));
                    }
                }));

        buildTapsCard();
        buildAccessibilityCard();
        buildBackgroundCard();
        buildClickCard();
        buildAppearanceCard();
        buildDiagnosticCard();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bridge.get().setUi(this);
        logView.setText(Bridge.get().logText());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onStop() {
        Bridge.get().clearUi(this);
        super.onStop();
    }

    // ---------------------------------------------------------------- cartes

    private LinearLayout section(String title, String intro) {
        LinearLayout card = Ui.card(this, theme);
        card.addView(Ui.text(this, title, 17, theme.text, true));
        TextView t = Ui.text(this, intro, 13, theme.muted, false);
        t.setPadding(0, dp(6), 0, dp(4));
        card.addView(t);
        content.addView(card);
        return card;
    }

    private TextView hint(LinearLayout card, String text) {
        TextView t = Ui.text(this, text, 12, theme.muted, false);
        t.setPadding(0, dp(8), 0, 0);
        card.addView(t);
        return t;
    }

    private void buildTapsCard() {
        LinearLayout card = section("👆  Appuis tactiles",
                "ClickBridge touche l'écran à ta place, aux deux endroits que tu as réglés : les "
                        + "boutons de vitesse de MyWhoosh. Les points sont mémorisés séparément en portrait "
                        + "et en paysage, et ne valent que pour cet appareil.");
        pointsPill = Ui.pill(this, "", theme.muted);
        card.addView(pointsPill, Ui.wrapTop(this, 6));
        card.addView(Ui.primary(this, "Régler les points d'appui", theme.cyan, theme.blue,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { Actions.startCalibration(SettingsActivity.this); }
                }));
        hint(card, "Ouvre MyWhoosh sur l'écran de course avant de régler : les pastilles + et − "
                + "restent affichées par-dessus.");

        TextView holdTitle = Ui.text(this, "Durée du toucher", 15, theme.text, true);
        holdTitle.setPadding(0, dp(16), 0, 0);
        card.addView(holdTitle);
        holdValue = Ui.text(this, "", 14, theme.cyan, true);
        card.addView(holdValue);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button minus = Ui.soft(this, theme, "−  10 ms", new View.OnClickListener() {
            @Override public void onClick(View v) { changeHold(-10); }
        });
        Button plus = Ui.soft(this, theme, "+  10 ms", new View.OnClickListener() {
            @Override public void onClick(View v) { changeHold(10); }
        });
        LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpA.topMargin = dp(8);
        lpA.rightMargin = dp(6);
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpB.topMargin = dp(8);
        lpB.leftMargin = dp(6);
        row.addView(minus, lpA);
        row.addView(plus, lpB);
        card.addView(row);
        hint(card, "Temps pendant lequel le doigt « reste posé ». 50 ms est la valeur de départ ; "
                + "si MyWhoosh ignore certains appuis, augmente-la par pas de 10 ms.");
    }

    private void buildAccessibilityCard() {
        LinearLayout card = section("♿  Service d'accessibilité",
                "C'est lui qui envoie les touchers sur l'écran. Il ne lit pas le contenu de ce que tu "
                        + "regardes : il ne sert qu'à toucher les deux points réglés.");
        accessPill = Ui.pill(this, "", theme.muted);
        card.addView(accessPill, Ui.wrapTop(this, 6));
        card.addView(Ui.soft(this, theme, "Ouvrir les réglages d'accessibilité", new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.openAccessibilitySettings(SettingsActivity.this); }
        }));
        card.addView(Ui.soft(this, theme, "Paramètres restreints : infos de l'appli", new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.openAppDetails(SettingsActivity.this); }
        }));
        hint(card, "Si Android refuse d'activer le service (« paramètre restreint ») : essaie de "
                + "l'activer, puis ouvre les infos de l'appli, menu ⋮ en haut à droite, « Autoriser les "
                + "paramètres restreints », et recommence.");
    }

    private void buildBackgroundCard() {
        LinearLayout card = section("🔋  Arrière-plan",
                "ClickBridge reste connecté au Click pendant que MyWhoosh est à l'écran, grâce à une "
                        + "notification discrète. Pour qu'Android ne l'endorme pas, autorise-le à "
                        + "fonctionner sans restriction de batterie.");
        bgPill = Ui.pill(this, "", theme.muted);
        card.addView(bgPill, Ui.wrapTop(this, 6));
        card.addView(Ui.soft(this, theme, "Autoriser en arrière-plan", new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.askBatteryExemption(SettingsActivity.this); }
        }));
    }

    private void buildClickCard() {
        LinearLayout card = section("🔗  Appairage du Click",
                "Après la première connexion, ClickBridge mémorise ton Click et s'y reconnecte tout "
                        + "seul. Si tu changes de Click, ou si la connexion échoue sans raison, oublie-le "
                        + "puis redémarre.");
        addressText = Ui.text(this, "", 13, theme.text, true);
        addressText.setPadding(0, dp(6), 0, 0);
        card.addView(addressText);
        card.addView(Ui.soft(this, theme, "Oublier le Click", new View.OnClickListener() {
            @Override public void onClick(View v) {
                BridgeService s = BridgeService.current;
                if (s != null) {
                    s.forgetClick();
                } else {
                    Prefs.setClickAddress(SettingsActivity.this, null);
                    Bridge.get().log("Click oublié.");
                }
                refresh();
            }
        }));
        hint(card, "Un Click ne peut être connecté qu'à un seul appareil à la fois : ferme Zwift, "
                + "Companion et ClickBridge sur ton PC avant de l'utiliser ici.");
    }

    private void buildAppearanceCard() {
        LinearLayout card = section("🎨  Apparence",
                "« Auto » suit le thème clair ou sombre de ton téléphone.");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Auto", "Sombre", "Clair"};
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            Button b = Ui.soft(this, theme, names[i], new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.setTheme(SettingsActivity.this, mode);
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.topMargin = dp(8);
            if (i > 0) lp.leftMargin = dp(8);
            themeButtons[i] = b;
            row.addView(b, lp);
        }
        card.addView(row);
    }

    private void buildDiagnosticCard() {
        LinearLayout card = section("🛠  Diagnostic",
                "Le journal aide à comprendre un problème : copie-le et colle-le dans la conversation "
                        + "pour obtenir de l'aide.");
        verboseButton = Ui.soft(this, theme, "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean now = !Prefs.verbose(SettingsActivity.this);
                Prefs.setVerbose(SettingsActivity.this, now);
                BridgeService s = BridgeService.current;
                if (s != null) s.setVerbose(now);
                refresh();
            }
        });
        card.addView(verboseButton);
        hint(card, "« Détaillé » ajoute chaque message reçu du Click au journal. À activer seulement "
                + "pour chercher une panne.");
        logButton = Ui.soft(this, theme, "Afficher le journal ▾", new View.OnClickListener() {
            @Override public void onClick(View v) {
                logVisible = !logVisible;
                logView.setVisibility(logVisible ? View.VISIBLE : View.GONE);
                logButton.setText(logVisible ? "Masquer le journal ▴" : "Afficher le journal ▾");
            }
        });
        card.addView(logButton);
        card.addView(Ui.soft(this, theme, "Copier le journal", new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.copyLog(SettingsActivity.this); }
        }));
        logView = Ui.text(this, "", 11, theme.text, false);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setVisibility(View.GONE);
        logView.setPadding(0, dp(10), 0, 0);
        card.addView(logView);
    }

    // ---------------------------------------------------------------- mise à jour

    private void changeHold(int delta) {
        Prefs.setHoldMs(this, Prefs.holdMs(this) + delta);
        refresh();
    }

    private void refresh() {
        boolean port = Prefs.hasPoints(this, "port");
        boolean land = Prefs.hasPoints(this, "land");
        Ui.setPill(this, pointsPill, "Portrait " + (port ? "✓" : "✗") + "   ·   Paysage " + (land ? "✓" : "✗"),
                (port || land) ? theme.ok : theme.warn);
        holdValue.setText(Prefs.holdMs(this) + " ms");

        boolean access = TapService.isActive();
        Ui.setPill(this, accessPill, access ? "✓  Service actif" : "✗  Service inactif",
                access ? theme.ok : theme.err);

        boolean bg = Actions.isBatteryExempt(this);
        Ui.setPill(this, bgPill, bg ? "✓  Autorisé sans restriction" : "!  Optimisation de batterie active",
                bg ? theme.ok : theme.warn);

        String address = Prefs.clickAddress(this);
        addressText.setText(address == null ? "Aucun Click mémorisé" : "Click mémorisé : " + address);

        verboseButton.setText(Prefs.verbose(this) ? "Journal détaillé : activé" : "Journal détaillé : désactivé");
        int mode = Prefs.theme(this);
        for (int i = 0; i < 3; i++) Ui.styleSoft(this, theme, themeButtons[i], i == mode);
    }

    // ---------------------------------------------------------------- Bridge.Ui

    @Override public void onStatus(String text, int level) { }

    @Override
    public void onLog(String line) {
        logView.append(line + "\n");
    }

    @Override public void onButtons(boolean plus, boolean minus) { }

    @Override public void onBattery(int percent) { }

    @Override public void onRunning(boolean running) { }
}
