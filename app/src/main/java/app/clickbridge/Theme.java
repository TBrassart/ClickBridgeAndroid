package app.clickbridge;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** Palette de l'appli (inspirée du logo : bleu nuit, bleu électrique, cyan), claire ou sombre. */
final class Theme {
    private static final Theme DARK = new Theme(true);
    private static final Theme LIGHT = new Theme(false);

    final boolean dark;
    final int bgTop, bgBottom, card, stroke, text, muted;
    final int cyan, blue, plusA, plusB, minusA, minusB;
    final int ok, warn, err, tileOff;

    static Theme of(Context c) {
        int mode = Prefs.theme(c);
        boolean night;
        if (mode == 1) {
            night = true;
        } else if (mode == 2) {
            night = false;
        } else {
            int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            night = ui == Configuration.UI_MODE_NIGHT_YES;
        }
        return night ? DARK : LIGHT;
    }

    private static int c(String hex) {
        return Color.parseColor(hex);
    }

    private Theme(boolean dark) {
        this.dark = dark;
        if (dark) {
            bgTop = c("#0E2A5C");
            bgBottom = c("#050D1F");
            card = c("#10244A");
            stroke = c("#22427C");
            text = c("#F4F8FF");
            muted = c("#9FB3D9");
            cyan = c("#22C3F2");
            blue = c("#2B7BFF");
            plusA = c("#34D399");
            plusB = c("#16A34A");
            minusA = c("#8B7CFF");
            minusB = c("#5B3FE0");
            ok = c("#34D399");
            warn = c("#FBBF24");
            err = c("#F87171");
            tileOff = c("#12284F");
        } else {
            bgTop = c("#D9EAFF");
            bgBottom = c("#F6FAFF");
            card = c("#FFFFFF");
            stroke = c("#CFE0F7");
            text = c("#0B1E3F");
            muted = c("#566A8F");
            cyan = c("#0E9FCB");
            blue = c("#1E6BFF");
            plusA = c("#22C55E");
            plusB = c("#15803D");
            minusA = c("#6D5BFF");
            minusB = c("#4A33CC");
            ok = c("#15803D");
            warn = c("#B45309");
            err = c("#DC2626");
            tileOff = c("#E4EDFB");
        }
    }
}
