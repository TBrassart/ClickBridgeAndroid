package app.clickbridge;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Message de boutons du Zwift Ride (type 0x23), déduit de captures réelles (firmware 1.1.0).
 * Le champ 1 est un masque de 32 bits : tous les bits sont à 1 au repos, un bit passe à 0 quand
 * le bouton correspondant est enfoncé. Chaque unité (gauche, droite) n'envoie que ses boutons.
 */
final class RideProtocol {
    private RideProtocol() {}

    static final int TYPE_BUTTONS = 0x23;

    private static final long[] BITS = {
            0x1, 0x2, 0x4, 0x8,            // unité gauche : croix gauche, haut, droite, bas
            0x10, 0x20, 0x40, 0x80,        // unité droite : A, B, Y, Z
            0x100, 0x200,                  // unité gauche : levier haut, levier bas
            0x1000, 0x2000};               // unité droite : levier haut, levier bas
    private static final String[] IDS = {
            Buttons.LEFT, Buttons.UP, Buttons.RIGHT, Buttons.DOWN,
            Buttons.A, Buttons.B, Buttons.Y, Buttons.Z,
            Buttons.SHIFT_UP_L, Buttons.SHIFT_DN_L,
            Buttons.SHIFT_UP_R, Buttons.SHIFT_DN_R};

    /** Boutons enfoncés dans un masque (bit à 0 = enfoncé). Les bits inconnus sont ignorés. */
    static Set<String> pressed(long mask) {
        Set<String> set = new LinkedHashSet<>();
        for (int i = 0; i < BITS.length; i++) {
            if ((mask & BITS[i]) == 0) set.add(IDS[i]);
        }
        return set;
    }
}
