package app.clickbridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;

/** Banc d'essai piloté par tests/check_crypto.py : compare ClickCrypto à la bibliothèque Python. */
public class CryptoTest {
    static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        ClickCrypto c = new ClickCrypto();
        System.out.println("HELLO " + hex(c.hello()));
        System.out.flush();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            String[] p = line.trim().split(" ");
            if (p[0].equals("DEV")) {
                System.out.println("ACCEPT " + c.accept(unhex(p[1])));
            } else if (p[0].equals("FRAME")) {            // FRAME <trame> <clair attendu>
                try {
                    byte[] pt = c.decrypt(unhex(p[1]));
                    System.out.println(Arrays.equals(pt, unhex(p[2])) ? "OK" : "MISMATCH " + hex(pt));
                } catch (Exception e) {
                    System.out.println("ERR " + e.getClass().getSimpleName());
                }
            } else if (p[0].equals("BAD")) {              // trame altérée : doit être rejetée
                try {
                    c.decrypt(unhex(p[1]));
                    System.out.println("ACCEPTED_BAD");
                } catch (Exception e) {
                    System.out.println("REJECTED");
                }
            } else if (p[0].equals("QUIT")) {
                break;
            }
            System.out.flush();
        }
    }
}
