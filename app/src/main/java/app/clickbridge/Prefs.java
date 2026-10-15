package app.clickbridge;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages enregistrés. Les points d'appui sont portés par les profils (voir Profiles). */
final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("clickbridge", Context.MODE_PRIVATE);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------------------------------------------------------------- toucher

    static int holdMs(Context c) {
        return sp(c).getInt("hold_ms", 50);
    }

    static void setHoldMs(Context c, int ms) {
        sp(c).edit().putInt("hold_ms", clamp(ms, 10, 500)).apply();
    }

    // ---------------------------------------------------------------- appui continu

    static boolean repeatEnabled(Context c) {
        return sp(c).getBoolean("repeat_enabled", false);
    }

    static void setRepeatEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("repeat_enabled", on).apply();
    }

    /** Délai avant la première répétition (ms). */
    static int repeatDelay(Context c) {
        return sp(c).getInt("repeat_delay", 400);
    }

    static void setRepeatDelay(Context c, int ms) {
        sp(c).edit().putInt("repeat_delay", clamp(ms, 100, 3000)).apply();
    }

    /** Temps entre deux répétitions (ms). */
    static int repeatInterval(Context c) {
        return sp(c).getInt("repeat_interval", 120);
    }

    static void setRepeatInterval(Context c, int ms) {
        sp(c).edit().putInt("repeat_interval", clamp(ms, 50, 1000)).apply();
    }

    // ---------------------------------------------------------------- divers

    static boolean verbose(Context c) {
        return sp(c).getBoolean("verbose", false);
    }

    static void setVerbose(Context c, boolean v) {
        sp(c).edit().putBoolean("verbose", v).apply();
    }

    /** 0 = suit le téléphone, 1 = sombre, 2 = clair. */
    static int theme(Context c) {
        return sp(c).getInt("theme", 0);
    }

    static void setTheme(Context c, int mode) {
        sp(c).edit().putInt("theme", mode).apply();
    }

    static String clickAddress(Context c) {
        return sp(c).getString("click_address", null);
    }

    static void setClickAddress(Context c, String address) {
        SharedPreferences.Editor e = sp(c).edit();
        if (address == null) e.remove("click_address"); else e.putString("click_address", address);
        e.apply();
    }

    // ---------------------------------------------------------------- points : profil actif

    /** @param orient "port" ou "land" ; renvoie {x, y} en fractions de l'écran, ou null. */
    static float[] point(Context c, boolean plus, String orient) {
        return Profiles.point(c, plus, orient);
    }

    static void setPoint(Context c, boolean plus, String orient, float x, float y) {
        Profiles.setPoint(c, plus, orient, x, y);
    }

    static boolean hasPoints(Context c, String orient) {
        return Profiles.hasPoints(c, orient);
    }

    /** Anciens points (versions 0.2 et 0.3, avant les profils), pour la migration. */
    static float[] legacyPoint(Context c, boolean plus, String orient) {
        String k = "pt_" + (plus ? "plus" : "minus") + "_" + orient;
        SharedPreferences p = sp(c);
        if (!p.contains(k + "_x") || !p.contains(k + "_y")) return null;
        return new float[]{p.getFloat(k + "_x", 0f), p.getFloat(k + "_y", 0f)};
    }
}
