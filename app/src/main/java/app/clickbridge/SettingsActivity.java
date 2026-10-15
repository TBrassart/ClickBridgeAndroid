package app.clickbridge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Réglages : chaque option est accompagnée d'une explication. */
public class SettingsActivity extends BaseActivity implements Bridge.Ui {

    private interface IntPref {
        int get();
        void set(int value);
    }

    private final List<Runnable> updaters = new ArrayList<>();
    private TextView profileText, pointsPill, accessPill, bgPill, addressText, logView;
    private Button repeatButton, verboseButton, logButton;
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

        buildLinksCard();
        buildTapsCard();
        buildRepeatCard();
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

    // ---------------------------------------------------------------- outils de construction

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

    /** Valeur numérique réglable par pas, avec son explication. */
    private void addStepper(LinearLayout card, String title, final String unit, final int step,
                            final IntPref pref, String explanation) {
        TextView t = Ui.text(this, title, 15, theme.text, true);
        t.setPadding(0, dp(16), 0, 0);
        card.addView(t);
        final TextView value = Ui.text(this, "", 14, theme.cyan, true);
        card.addView(value);
        updaters.add(new Runnable() {
            @Override public void run() { value.setText(pref.get() + " " + unit); }
        });
        Button minus = Ui.soft(this, theme, "−  " + step + " " + unit, new View.OnClickListener() {
            @Override public void onClick(View v) {
                pref.set(pref.get() - step);
                refresh();
            }
        });
        Button plus = Ui.soft(this, theme, "+  " + step + " " + unit, new View.OnClickListener() {
            @Override public void onClick(View v) {
                pref.set(pref.get() + step);
                refresh();
            }
        });
        card.addView(buttonRow(minus, plus));
        hint(card, explanation);
    }

    // ---------------------------------------------------------------- cartes

    private void buildLinksCard() {
        LinearLayout card = section("📂  Profils et historique",
                "Un profil mémorise les positions des boutons d'une application (MyWhoosh, Rouvy, "
                        + "Zwift…). L'historique garde la trace de tes sessions.");
        profileText = Ui.text(this, "", 14, theme.cyan, true);
        profileText.setPadding(0, dp(6), 0, 0);
        card.addView(profileText);
        card.addView(buttonRow(
                Ui.soft(this, theme, "Gérer les profils", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this, ProfilesActivity.class));
                    }
                }),
                Ui.soft(this, theme, "Historique", new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this, HistoryActivity.class));
                    }
                })));
    }

    private void buildTapsCard() {
        LinearLayout card = section("👆  Appuis tactiles",
                "ClickBridge touche l'écran à ta place, aux deux endroits réglés dans le profil actif : "
                        + "les boutons de vitesse de l'appli. Les points sont mémorisés séparément en "
                        + "portrait et en paysage, et ne valent que pour cet appareil.");
        pointsPill = Ui.pill(this, "", theme.muted);
        card.addView(pointsPill, Ui.wrapTop(this, 6));
        card.addView(Ui.primary(this, "Régler les points d'appui", theme.cyan, theme.blue,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { Actions.startCalibration(SettingsActivity.this); }
                }));
        hint(card, "Ouvre l'appli (MyWhoosh…) sur l'écran de course avant de régler : les pastilles + et − "
                + "restent affichées par-dessus.");

        addStepper(card, "Durée du toucher", "ms", 10, new IntPref() {
            @Override public int get() { return Prefs.holdMs(SettingsActivity.this); }
            @Override public void set(int v) { Prefs.setHoldMs(SettingsActivity.this, v); }
        }, "Temps pendant lequel le doigt « reste posé ». 50 ms est la valeur de départ ; si l'appli "
                + "ignore certains appuis, augmente-la par pas de 10 ms.");
    }

    private void buildRepeatCard() {
        LinearLayout card = section("🔁  Appui continu",
                "Quand tu maintiens un bouton du Click enfoncé, ClickBridge peut répéter le toucher pour "
                        + "changer plusieurs fois de vitesse sans relâcher. La répétition s'arrête dès que "
                        + "tu relâches le bouton.");
        repeatButton = Ui.soft(this, theme, "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.setRepeatEnabled(SettingsActivity.this, !Prefs.repeatEnabled(SettingsActivity.this));
                refresh();
            }
        });
        card.addView(repeatButton);

        addStepper(card, "Délai avant la première répétition", "ms", 50, new IntPref() {
            @Override public int get() { return Prefs.repeatDelay(SettingsActivity.this); }
            @Override public void set(int v) { Prefs.setRepeatDelay(SettingsActivity.this, v); }
        }, "Temps d'attente, bouton maintenu, avant le premier toucher répété. Plus court = plus "
                + "réactif, mais un appui un peu long risque de déclencher une répétition. Valeur de "
                + "départ : 400 ms.");

        addStepper(card, "Temps entre deux répétitions", "ms", 10, new IntPref() {
            @Override public int get() { return Prefs.repeatInterval(SettingsActivity.this); }
            @Override public void set(int v) { Prefs.setRepeatInterval(SettingsActivity.this, v); }
        }, "Rythme des touchers répétés. ClickBridge ne descend jamais sous la durée du toucher + 20 ms, "
                + "pour que les gestes ne se chevauchent pas. Valeur de départ : 120 ms.");
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
                "ClickBridge reste connecté au Click pendant que l'appli est à l'écran, grâce à une "
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

    private void refresh() {
        for (Runnable r : updaters) r.run();

        profileText.setText("Profil actif : " + Profiles.active(this).name);

        boolean port = Prefs.hasPoints(this, "port");
        boolean land = Prefs.hasPoints(this, "land");
        Ui.setPill(this, pointsPill, "Portrait " + (port ? "✓" : "✗") + "   ·   Paysage " + (land ? "✓" : "✗"),
                (port || land) ? theme.ok : theme.warn);

        boolean repeat = Prefs.repeatEnabled(this);
        repeatButton.setText(repeat ? "Répétition : activée" : "Répétition : désactivée");
        Ui.styleSoft(this, theme, repeatButton, repeat);

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

    @Override
    public void onProfileChanged() {
        refresh();
    }
}
