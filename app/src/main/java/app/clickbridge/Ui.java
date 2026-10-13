package app.clickbridge;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Petits outils pour construire l'interface sans bibliothèque externe. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static GradientDrawable rect(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable grad(Context c, int a, int b, float radiusDp,
                                 GradientDrawable.Orientation orientation) {
        GradientDrawable g = new GradientDrawable(orientation, new int[]{a, b});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Drawable ripple(Context c, Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), content,
                rect(c, Color.WHITE, radiusDp));
    }

    static LinearLayout.LayoutParams lp(Context c, int width, int height, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static LinearLayout.LayoutParams wrapTop(Context c, float topDp) {
        return lp(c, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, topDp);
    }

    static LinearLayout.LayoutParams fullTop(Context c, float topDp) {
        return lp(c, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, topDp);
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0f, 1.15f);
        return t;
    }

    static LinearLayout card(Context c, Theme t) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 16));
        GradientDrawable g = rect(c, t.card, 20);
        g.setStroke(dp(c, 1), t.stroke);
        l.setBackground(g);
        l.setLayoutParams(fullTop(c, 14));
        return l;
    }

    /** Bouton plein, en dégradé. */
    static Button primary(Context c, String label, int colorA, int colorB, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        b.setBackground(ripple(c, grad(c, colorA, colorB, 16, GradientDrawable.Orientation.LEFT_RIGHT), 16));
        b.setOnClickListener(l);
        b.setLayoutParams(fullTop(c, 10));
        return b;
    }

    /** Bouton secondaire, teinté. */
    static Button soft(Context c, Theme t, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(t.dark ? t.text : t.blue);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        GradientDrawable g = rect(c, alpha(t.dark ? Color.WHITE : t.blue, t.dark ? 0x1C : 0x16), 14);
        g.setStroke(dp(c, 1), alpha(t.dark ? Color.WHITE : t.blue, 0x30));
        b.setBackground(ripple(c, g, 14));
        b.setOnClickListener(l);
        b.setLayoutParams(fullTop(c, 8));
        return b;
    }

    static void styleSoft(Context c, Theme t, Button b, boolean selected) {
        if (selected) {
            b.setTextColor(Color.WHITE);
            b.setBackground(ripple(c, grad(c, t.cyan, t.blue, 14, GradientDrawable.Orientation.LEFT_RIGHT), 14));
        } else {
            b.setTextColor(t.dark ? t.text : t.blue);
            GradientDrawable g = rect(c, alpha(t.dark ? Color.WHITE : t.blue, t.dark ? 0x1C : 0x16), 14);
            g.setStroke(dp(c, 1), alpha(t.dark ? Color.WHITE : t.blue, 0x30));
            b.setBackground(ripple(c, g, 14));
        }
    }

    /** Pastille colorée (état). */
    static TextView pill(Context c, String label, int color) {
        TextView v = text(c, label, 13, color, true);
        v.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        v.setBackground(rect(c, alpha(color, 0x2A), 20));
        return v;
    }

    static void setPill(Context c, TextView v, String label, int color) {
        v.setText(label);
        v.setTextColor(color);
        v.setBackground(rect(c, alpha(color, 0x2A), 20));
        v.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
    }

    /** Bouton rond avec un symbole (retour, réglages, aide). */
    static TextView roundButton(Context c, Theme t, String glyph, View.OnClickListener l) {
        TextView v = text(c, glyph, 20, t.text, true);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = rect(c, t.card, 22);
        g.setStroke(dp(c, 1), t.stroke);
        v.setBackground(ripple(c, g, 22));
        v.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44));
        p.leftMargin = dp(c, 8);
        v.setLayoutParams(p);
        return v;
    }
}
