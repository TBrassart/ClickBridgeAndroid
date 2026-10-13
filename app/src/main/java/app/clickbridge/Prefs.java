package app.clickbridge;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages enregistrés : durée du toucher, adresse du Click, points d'appui par orientation. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("clickbridge", Context.MODE_PRIVATE);
    }

    static int holdMs(Context c) {
        return sp(c).getInt("hold_ms", 50);
    }

    static void setHoldMs(Context c, int ms) {
        sp(c).edit().putInt("hold_ms", Math.max(10, Math.min(500, ms))).apply();
    }

    static boolean verbose(Context c) {
        return sp(c).getBoolean("verbose", false);
    }

    static void setVerbose(Context c, boolean v) {
        sp(c).edit().putBoolean("verbose", v).apply();
    }

    static String clickAddress(Context c) {
        return sp(c).getString("click_address", null);
    }

    static void setClickAddress(Context c, String address) {
        SharedPreferences.Editor e = sp(c).edit();
        if (address == null) e.remove("click_address"); else e.putString("click_address", address);
        e.apply();
    }

    /** @param orient "port" ou "land" ; renvoie {x, y} en fractions de l'écran, ou null. */
    static float[] point(Context c, boolean plus, String orient) {
        String k = "pt_" + (plus ? "plus" : "minus") + "_" + orient;
        SharedPreferences p = sp(c);
        if (!p.contains(k + "_x") || !p.contains(k + "_y")) return null;
        return new float[]{p.getFloat(k + "_x", 0f), p.getFloat(k + "_y", 0f)};
    }

    static void setPoint(Context c, boolean plus, String orient, float x, float y) {
        String k = "pt_" + (plus ? "plus" : "minus") + "_" + orient;
        sp(c).edit().putFloat(k + "_x", x).putFloat(k + "_y", y).apply();
    }

    static boolean hasPoints(Context c, String orient) {
        return point(c, true, orient) != null && point(c, false, orient) != null;
    }
}
