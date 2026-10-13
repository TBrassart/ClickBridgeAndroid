package app.clickbridge;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Aide : sections dépliables sur la configuration et les problèmes courants. */
public class HelpActivity extends BaseActivity {

    private static final String[][] SECTIONS = {
        {"Comment ça marche",
            "ClickBridge lit les appuis sur ton Zwift Click V1 en Bluetooth, puis touche l'écran à "
                    + "l'endroit où se trouvent les boutons de vitesse de MyWhoosh.\n\n"
                    + "Il faut donc : (1) que le Click soit connecté, (2) que le service d'accessibilité "
                    + "soit activé, (3) que les deux points d'appui soient réglés sur l'écran de MyWhoosh.\n\n"
                    + "L'assistant de configuration (bouton orange de l'accueil) te guide dans cet ordre."},
        {"Première configuration",
            "1. Autorise Bluetooth et notifications.\n"
                    + "2. Active « ClickBridge (appuis tactiles) » dans les réglages d'accessibilité.\n"
                    + "3. Règle les deux points d'appui sur MyWhoosh.\n"
                    + "4. Autorise l'appli en arrière-plan (conseillé).\n"
                    + "5. Démarre, puis réveille le Click.\n\n"
                    + "Chaque étape est détaillée dans l'assistant, avec son état."},
        {"Régler les points d'appui",
            "Place MyWhoosh sur l'écran de course, celui où les boutons de changement de vitesse sont "
                    + "visibles. Reviens dans ClickBridge et appuie sur « Régler les points d'appui » : deux "
                    + "pastilles, + (verte) et − (violette), s'affichent par-dessus MyWhoosh.\n\n"
                    + "• Glisse chaque pastille pile au centre du bouton correspondant.\n"
                    + "• « Tester + » et « Tester − » envoient un vrai toucher : tu dois voir la vitesse "
                    + "changer dans MyWhoosh.\n"
                    + "• Le bouton ⇅ déplace le panneau de commandes s'il cache un bouton.\n"
                    + "• « Valider » enregistre. « Annuler » ferme sans rien changer.\n\n"
                    + "Tu peux aussi lancer le réglage depuis la notification (« Régler les points »)."},
        {"Pourquoi refaire le réglage ?",
            "Les points sont enregistrés par orientation (portrait ou paysage) et pour chaque appareil, "
                    + "car la position des boutons de MyWhoosh change avec la taille et la forme de l'écran.\n\n"
                    + "Sur ta tablette, ou si tu fais pivoter l'écran, il faut donc régler les points une "
                    + "fois dans cette configuration. L'accueil indique si l'orientation actuelle est réglée."},
        {"Le Click ne se connecte pas",
            "• Réveille-le : appuie sur un bouton jusqu'à ce que la LED pulse en bleu.\n"
                    + "• Un Click ne se connecte qu'à un seul appareil à la fois : ferme Zwift, Companion, "
                    + "ClickBridge sur le PC et tout autre téléphone qui l'utiliserait.\n"
                    + "• Vérifie que le Bluetooth du téléphone est activé.\n"
                    + "• Dans les réglages, « Oublier le Click », puis redémarre : l'appli refait une "
                    + "recherche complète.\n"
                    + "• Si le statut parle d'« échange des clés impossible », le Click est probablement tenu "
                    + "par une autre appli, ou son firmware est différent de ceux testés (1.1.0)."},
        {"L'accessibilité est refusée",
            "Android bloque l'activation des services d'accessibilité pour les applis installées hors "
                    + "du Play Store (« paramètre restreint »).\n\n"
                    + "1. Essaie d'abord d'activer le service dans Accessibilité.\n"
                    + "2. Ouvre les infos de l'appli (bouton dans l'assistant ou les réglages), menu ⋮ en "
                    + "haut à droite, « Autoriser les paramètres restreints ».\n"
                    + "3. Retourne dans Accessibilité et active « ClickBridge (appuis tactiles) »."},
        {"Les appuis sont ratés ou ignorés",
            "• Augmente la durée du toucher dans les réglages, par pas de 10 ms.\n"
                    + "• Refais le réglage des points en visant bien le centre des boutons de MyWhoosh.\n"
                    + "• Vérifie que l'orientation actuelle est réglée (pastille verte sur l'accueil).\n"
                    + "• MyWhoosh doit être au premier plan : les touchers visent l'écran affiché.\n"
                    + "• Si le journal indique « Service d'accessibilité inactif », réactive-le : Android "
                    + "peut l'arrêter après une mise à jour de l'appli."},
        {"Batterie et arrière-plan",
            "ClickBridge tourne en service de premier plan, avec une notification discrète. Il n'écoute "
                    + "le Click que par petits messages à chaque appui, ce qui consomme très peu.\n\n"
                    + "Après le premier appairage, il mémorise le Click et se reconnecte sans scan "
                    + "permanent. Autoriser l'arrière-plan sans restriction évite qu'Android ne l'endorme.\n\n"
                    + "Sur certains téléphones, fermer l'appli depuis les applis récentes peut l'arrêter : "
                    + "relance-la alors depuis l'accueil."},
        {"Journal et diagnostic",
            "Dans les réglages, la carte « Diagnostic » permet d'afficher le journal et de le copier. "
                    + "Colle-le dans la conversation pour qu'on puisse voir où ça bloque.\n\n"
                    + "« Journal détaillé » ajoute chaque message reçu du Click : à n'activer que pour "
                    + "chercher une panne."},
        {"À propos",
            "ClickBridge version 0.3.\n\n"
                    + "Compatible avec le Zwift Click V1 (testé avec le firmware 1.1.0). Le Click V2 et le "
                    + "Zwift Ride ne sont pas encore pris en charge."},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backHeader("Aide", "Touche une rubrique pour la déplier");
        for (String[] section : SECTIONS) addSection(section[0], section[1]);
    }

    private void addSection(final String title, String body) {
        LinearLayout card = Ui.card(this, theme);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, title, 16, theme.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView arrow = Ui.text(this, "▾", 18, theme.cyan, true);
        head.addView(arrow);
        card.addView(head);

        final TextView text = Ui.text(this, body, 14, theme.muted, false);
        text.setPadding(0, dp(10), 0, 0);
        text.setVisibility(View.GONE);
        card.addView(text);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean open = text.getVisibility() == View.VISIBLE;
                text.setVisibility(open ? View.GONE : View.VISIBLE);
                arrow.setText(open ? "▾" : "▴");
            }
        });
        content.addView(card);
    }
}
