package app.clickbridge;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Assistant pas à pas : permissions, accessibilité, points d'appui, arrière-plan, connexion. */
public class WizardActivity extends BaseActivity implements Bridge.Ui {

    private static final class Step {
        final TextView badge;
        final TextView state;
        boolean done;

        Step(TextView badge, TextView state) {
            this.badge = badge;
            this.state = state;
        }
    }

    private final List<Step> steps = new ArrayList<>();
    private TextView summary;
    private Button doneButton, startButton;
    private TextView connectState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backHeader("Assistant de configuration", "5 étapes, dans l'ordre. Tu peux quitter à tout moment.");

        // 1. permissions
        LinearLayout c1 = addStep(1, "Autoriser Bluetooth et notifications",
                "ClickBridge parle au Click en Bluetooth (Android l'appelle « Appareils à proximité ») "
                        + "et affiche une notification discrète pendant qu'il tourne en arrière-plan.");
        c1.addView(Ui.primary(this, "Autoriser", theme.cyan, theme.blue, new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.requestAllPermissions(WizardActivity.this); }
        }));

        // 2. accessibilité
        LinearLayout c2 = addStep(2, "Activer le service d'accessibilité",
                "C'est lui qui touche l'écran de MyWhoosh à ta place quand tu appuies sur le Click. "
                        + "Il ne lit pas le contenu de l'écran.\n\n"
                        + "Si Android affiche « Paramètre restreint » : appuie sur « Infos de l'appli », "
                        + "puis sur le menu ⋮ en haut à droite, « Autoriser les paramètres restreints » "
                        + "(il faut avoir essayé d'activer le service avant que l'option apparaisse), puis "
                        + "reviens activer « ClickBridge (appuis tactiles) ».");
        c2.addView(Ui.primary(this, "Ouvrir l'accessibilité", theme.cyan, theme.blue, new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.openAccessibilitySettings(WizardActivity.this); }
        }));
        c2.addView(Ui.soft(this, theme, "Infos de l'appli (paramètres restreints)", new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.openAppDetails(WizardActivity.this); }
        }));

        // 3. points d'appui
        LinearLayout c3 = addStep(3, "Régler les points d'appui",
                "1. Ouvre MyWhoosh sur l'écran de course.\n"
                        + "2. Reviens ici et appuie sur « Afficher les pastilles » : elles se posent "
                        + "par-dessus MyWhoosh.\n"
                        + "3. Glisse + et − sur les boutons de vitesse de MyWhoosh. « Tester » envoie un "
                        + "vrai toucher ; « Valider » enregistre.\n\n"
                        + "Les points sont enregistrés dans le profil actif (un profil par application : voir « Profils »). "
                        + "À refaire en portrait et en paysage, et sur chaque appareil (téléphone, tablette).");
        c3.addView(Ui.primary(this, "Afficher les pastilles", theme.cyan, theme.blue, new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.startCalibration(WizardActivity.this); }
        }));

        // 4. arrière-plan
        LinearLayout c4 = addStep(4, "Autoriser l'arrière-plan (conseillé)",
                "Pour qu'Android ne coupe pas ClickBridge quand MyWhoosh est à l'écran, autorise-le à "
                        + "fonctionner sans restriction de batterie. Sa consommation reste très faible : "
                        + "il ne fait quelque chose que lorsque tu appuies sur le Click.");
        c4.addView(Ui.primary(this, "Autoriser", theme.cyan, theme.blue, new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.askBatteryExemption(WizardActivity.this); }
        }));

        // 5. connexion
        LinearLayout c5 = addStep(5, "Connecter le Click",
                "Ferme Zwift, Companion et ClickBridge sur ton PC : le Click ne se connecte qu'à un "
                        + "appareil à la fois. Appuie sur Démarrer, puis sur un bouton du Click jusqu'à ce "
                        + "que sa LED pulse en bleu.");
        connectState = Ui.text(this, "", 14, theme.muted, true);
        connectState.setPadding(0, dp(10), 0, 0);
        c5.addView(connectState);
        startButton = Ui.primary(this, "Démarrer", theme.cyan, theme.blue, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Bridge.get().isRunning()) {
                    Actions.stopBridge(WizardActivity.this);
                } else if (Actions.hasBluetoothPermissions(WizardActivity.this)) {
                    Actions.startBridge(WizardActivity.this);
                } else {
                    Actions.requestAllPermissions(WizardActivity.this);
                }
            }
        });
        c5.addView(startButton);

        // conclusion
        LinearLayout end = Ui.card(this, theme);
        summary = Ui.text(this, "", 14, theme.text, false);
        end.addView(summary);
        doneButton = Ui.primary(this, "Terminer", theme.plusA, theme.plusB, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        end.addView(doneButton);
        content.addView(end);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bridge.get().setUi(this);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    // ---------------------------------------------------------------- construction

    private LinearLayout addStep(int number, String title, String body) {
        LinearLayout card = Ui.card(this, theme);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = Ui.text(this, String.valueOf(number), 16, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        head.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(Ui.text(this, title, 16, theme.text, true));
        TextView state = Ui.text(this, "", 12, theme.muted, true);
        col.addView(state);
        head.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(head);

        TextView text = Ui.text(this, body, 13, theme.muted, false);
        text.setPadding(0, dp(10), 0, 0);
        card.addView(text);

        content.addView(card);
        steps.add(new Step(badge, state));
        return card;
    }

    // ---------------------------------------------------------------- état

    private void mark(int index, boolean done, String doneText, String todoText) {
        Step s = steps.get(index);
        s.done = done;
        s.badge.setText(done ? "✓" : String.valueOf(index + 1));
        s.badge.setBackground(Ui.rect(this, done ? theme.ok : theme.blue, 18));
        s.state.setText(done ? doneText : todoText);
        s.state.setTextColor(done ? theme.ok : theme.warn);
    }

    private void refresh() {
        boolean perms = Actions.hasBluetoothPermissions(this) && Actions.hasNotificationPermission(this);
        mark(0, perms, "Fait", "À faire");

        mark(1, TapService.isActive(), "Fait : service actif", "À faire : service inactif");

        String orient = Actions.orientationName(this);
        mark(2, Prefs.hasPoints(this, Actions.orientationKey(this)),
                "Fait pour l'orientation " + orient, "À faire pour l'orientation " + orient);

        mark(3, Actions.isBatteryExempt(this), "Fait", "Conseillé");

        Bridge b = Bridge.get();
        boolean connected = b.isRunning() && b.statusLevel() == ClickClient.GREEN;
        mark(4, connected, "Fait : Click connecté", "À faire");
        connectState.setText(b.statusText());
        int color;
        switch (b.statusLevel()) {
            case ClickClient.GREEN: color = theme.ok; break;
            case ClickClient.ORANGE: color = theme.warn; break;
            case ClickClient.RED: color = theme.err; break;
            default: color = theme.muted;
        }
        connectState.setTextColor(color);
        startButton.setText(b.isRunning() ? "Arrêter" : "Démarrer");

        int missing = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (i != 3 && !steps.get(i).done) missing++;     // l'étape 4 est conseillée, pas obligatoire
        }
        if (missing == 0) {
            summary.setText("🎉  Tout est prêt ! Lance MyWhoosh : chaque appui sur le Click touche "
                    + "maintenant l'écran à l'endroit réglé.");
            doneButton.setVisibility(View.VISIBLE);
        } else {
            summary.setText("Il reste " + missing + " étape" + (missing > 1 ? "s" : "")
                    + " à terminer. Si quelque chose bloque, ouvre l'aide (« ? » sur l'accueil).");
            doneButton.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- Bridge.Ui

    @Override public void onStatus(String text, int level) { refresh(); }

    @Override public void onLog(String line) { }

    @Override public void onButtons(boolean plus, boolean minus) { }

    @Override public void onBattery(int percent) { }

    @Override public void onRunning(boolean running) { refresh(); }
}
