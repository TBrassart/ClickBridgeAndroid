package app.clickbridge;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/** Vérifie le décodage du Ride contre les trames réelles capturées (tests/fixtures/ride_frames.txt). */
public class RideProtocolTest {
    static int failures = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  ÉCHEC ") + what);
        if (!ok) failures++;
    }
    static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    public static void main(String[] args) throws Exception {
        // (unité|marqueur) -> union des boutons vus enfoncés
        Map<String, Set<String>> seen = new LinkedHashMap<>();
        int frames = 0;
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] p = line.split("\\|");
            byte[] pt = unhex(p[2]);
            if ((pt[0] & 0xFF) != RideProtocol.TYPE_BUTTONS) continue;
            Map<Integer, Long> f = Pb.parseVarints(pt, 1);
            check(f.containsKey(1), "trame 0x23 avec masque : " + p[1]);
            frames++;
            seen.computeIfAbsent(p[0] + "|" + p[1], k -> new TreeSet<>()).addAll(RideProtocol.pressed(f.get(1)));
        }
        System.out.println(frames + " trames 0x23 réelles décodées");

        Object[][] expected = {
            {"gauche|haut", Buttons.UP}, {"gauche|gauche", Buttons.LEFT}, {"gauche|droite", Buttons.RIGHT},
            {"gauche|bas", Buttons.DOWN}, {"gauche|levier haut", Buttons.SHIFT_UP_L}, {"gauche|levier bas", Buttons.SHIFT_DN_L},
            {"droite|aa", Buttons.A}, {"droite|b", Buttons.B}, {"droite|y", Buttons.Y}, {"droite|z", Buttons.Z},
            {"droite|levier haut", Buttons.SHIFT_UP_R}, {"droite|levier bas", Buttons.SHIFT_DN_R},
        };
        for (Object[] e : expected) {
            Set<String> got = seen.get((String) e[0]);
            check(got != null && got.equals(new TreeSet<>(Collections.singleton((String) e[1]))),
                    e[0] + " -> " + e[1] + " (vu : " + got + ")");
        }
        // leviers orange (analogiques) : aucun bouton numérique
        for (String k : new String[]{"gauche|levier exterieur poussé", "gauche|levier orange tirer", "droite|levier orange", "droite|levier orange tiré"}) {
            check(seen.containsKey(k) && seen.get(k).isEmpty(), k + " : aucun bouton numérique");
        }
        // repos : masque plein, aucun bouton
        check(RideProtocol.pressed(0xFFFFFFFFL).isEmpty(), "masque de repos : rien d'enfoncé");
        check(RideProtocol.pressed(0xFFFFFFFCL).equals(new LinkedHashSet<>(Arrays.asList(Buttons.LEFT, Buttons.UP))), "deux boutons simultanés");
        check(RideProtocol.pressed(0xFFFFFBFFL).isEmpty(), "bit inconnu (0x400) ignoré");
        System.out.println(failures == 0 ? "\nTOUT EST OK" : "\n" + failures + " ÉCHEC(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
