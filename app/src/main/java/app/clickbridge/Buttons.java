package app.clickbridge;

import java.util.Arrays;

/** Les boutons que ClickBridge connaît, indépendamment de l'appareil qui les envoie. */
final class Buttons {
    private Buttons() {}

    // Zwift Click
    static final String PLUS = "PLUS", MINUS = "MINUS";
    // Zwift Ride, unité gauche : croix directionnelle + leviers
    static final String UP = "UP", DOWN = "DOWN", LEFT = "LEFT", RIGHT = "RIGHT";
    static final String SHIFT_UP_L = "SHIFT_UP_L", SHIFT_DN_L = "SHIFT_DN_L";
    // Zwift Ride, unité droite : boutons A B Y Z + leviers
    static final String A = "A", B = "B", Y = "Y", Z = "Z";
    static final String SHIFT_UP_R = "SHIFT_UP_R", SHIFT_DN_R = "SHIFT_DN_R";

    static final String MODE_CLICK = "click";
    static final String MODE_RIDE = "ride";

    /** Tous les boutons, dans l'ordre d'affichage. */
    static final String[] ALL = {
            PLUS, MINUS,
            SHIFT_UP_R, SHIFT_DN_R, SHIFT_UP_L, SHIFT_DN_L,
            UP, DOWN, LEFT, RIGHT,
            A, B, Y, Z};

    static final String[] CLICK_DEFAULT = {PLUS, MINUS};
    static final String[] RIDE_DEFAULT = {SHIFT_UP_R, SHIFT_DN_R, SHIFT_UP_L, SHIFT_DN_L};

    static boolean isKnown(String id) {
        return id != null && Arrays.asList(ALL).contains(id);
    }

    /** Boutons activés par défaut dans un nouveau profil, selon l'appareil utilisé. */
    static String[] defaultsFor(String mode) {
        return MODE_RIDE.equals(mode) ? RIDE_DEFAULT : CLICK_DEFAULT;
    }

    static String label(String id) {
        switch (id) {
            case PLUS: return "Bouton +";
            case MINUS: return "Bouton −";
            case SHIFT_UP_R: return "Levier droit haut";
            case SHIFT_DN_R: return "Levier droit bas";
            case SHIFT_UP_L: return "Levier gauche haut";
            case SHIFT_DN_L: return "Levier gauche bas";
            case UP: return "Croix : haut";
            case DOWN: return "Croix : bas";
            case LEFT: return "Croix : gauche";
            case RIGHT: return "Croix : droite";
            case A: return "Bouton A";
            case B: return "Bouton B";
            case Y: return "Bouton Y";
            case Z: return "Bouton Z";
            default: return id;
        }
    }

    /** Texte court affiché dans la pastille de réglage. */
    static String glyph(String id) {
        switch (id) {
            case PLUS: return "+";
            case MINUS: return "−";
            case SHIFT_UP_R: return "R▲";
            case SHIFT_DN_R: return "R▼";
            case SHIFT_UP_L: return "L▲";
            case SHIFT_DN_L: return "L▼";
            case UP: return "↑";
            case DOWN: return "↓";
            case LEFT: return "←";
            case RIGHT: return "→";
            default: return id;
        }
    }

    /** +1 pour les boutons « vitesse + », −1 pour « vitesse − », 0 sinon (indicateurs de l'accueil). */
    static int direction(String id) {
        switch (id) {
            case PLUS:
            case SHIFT_UP_R:
            case SHIFT_UP_L:
                return 1;
            case MINUS:
            case SHIFT_DN_R:
            case SHIFT_DN_L:
                return -1;
            default:
                return 0;
        }
    }

    /** La répétition sur appui long ne concerne que les boutons de changement de vitesse. */
    static boolean repeatable(String id) {
        return direction(id) != 0;
    }

    /** Couleur de la pastille de réglage (ARGB). */
    static int color(String id) {
        int d = direction(id);
        if (d > 0) return 0xFF22C55E;
        if (d < 0) return 0xFF6D5BFF;
        if (id.equals(A) || id.equals(B) || id.equals(Y) || id.equals(Z)) return 0xFFF59E0B;
        return 0xFF0EA5E9;
    }
}
