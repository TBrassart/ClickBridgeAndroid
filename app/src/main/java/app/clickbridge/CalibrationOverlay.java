package app.clickbridge;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Réglage des points d'appui : deux pastilles (+ et −) et un petit panneau, affichés par-dessus
 * MyWhoosh. Chaque élément est une PETITE fenêtre indépendante : le reste de l'écran (barre de
 * navigation, autres applis, MyWhoosh) reste utilisable pendant le réglage.
 */
final class CalibrationOverlay {

    private static final int FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

    private final TapService svc;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView plusMarker, minusMarker;
    private WindowManager.LayoutParams plusLp, minusLp, panelLp;
    private LinearLayout panel;
    private boolean panelTop = true;
    private boolean shown;
    private boolean restartPending;
    private int size;

    CalibrationOverlay(TapService svc) {
        this.svc = svc;
        this.wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(int v) {
        return (int) (v * svc.getResources().getDisplayMetrics().density + 0.5f);
    }

    boolean isShown() {
        return shown;
    }

    // ---------------------------------------------------------------- affichage

    void show() {
        if (shown) return;
        size = dp(56);
        int[] screen = svc.screenSize();
        String orient = svc.orientationKey();
        Bridge.get().log("Calque de réglage affiché : " + orient + " " + screen[0] + "x" + screen[1]);

        // positions de départ : points déjà réglés, sinon côté droit de l'écran
        float[] p = Prefs.point(svc, true, orient);
        float[] m = Prefs.point(svc, false, orient);
        float px = p != null ? p[0] * screen[0] : screen[0] * 0.82f;
        float py = p != null ? p[1] * screen[1] : screen[1] * 0.38f;
        float mx = m != null ? m[0] * screen[0] : screen[0] * 0.82f;
        float my = m != null ? m[1] * screen[1] : screen[1] * 0.62f;

        plusMarker = makeMarker("+", Color.parseColor("#22C55E"));
        minusMarker = makeMarker("−", Color.parseColor("#6D5BFF"));
        plusLp = markerParams(px, py);
        minusLp = markerParams(mx, my);
        attachDrag(plusMarker, plusLp);
        attachDrag(minusMarker, minusLp);
        buildPanel();

        try {
            wm.addView(plusMarker, plusLp);
            wm.addView(minusMarker, minusLp);
            wm.addView(panel, panelLp);
        } catch (Exception e) {
            Bridge.get().log("Impossible d'afficher le calque : " + e);
            removeViews();
            return;
        }
        shown = true;
        correct(plusMarker, plusLp, px, py);
        correct(minusMarker, minusLp, mx, my);
    }

    /** Fermeture définitive (Valider, Annuler, service arrêté). */
    void hide() {
        restartPending = false;
        handler.removeCallbacks(showAgain);
        removeViews();
    }

    private final Runnable showAgain = new Runnable() {
        @Override public void run() {
            if (restartPending) {
                restartPending = false;
                show();
            }
        }
    };

    /** Après une rotation : retire le calque puis le réaffiche dans la nouvelle orientation. */
    void restart() {
        if (!shown) return;                     // aucun réglage en cours (ou déjà relancé)
        removeViews();
        restartPending = true;
        handler.removeCallbacks(showAgain);
        handler.postDelayed(showAgain, 600);    // laisse la rotation se terminer
    }

    private void removeViews() {
        shown = false;
        View[] views = {plusMarker, minusMarker, panel};
        for (View v : views) {
            if (v == null) continue;
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
                // déjà retiré
            }
        }
        plusMarker = null;
        minusMarker = null;
        panel = null;
    }

    private void updateLayout(View v, WindowManager.LayoutParams lp) {
        if (!shown) return;
        try {
            wm.updateViewLayout(v, lp);
        } catch (Exception ignored) {
            // fenêtre déjà retirée
        }
    }

    // ---------------------------------------------------------------- pastilles

    private WindowManager.LayoutParams baseParams(int width, int height) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                FLAGS, PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    private WindowManager.LayoutParams markerParams(float centerX, float centerY) {
        WindowManager.LayoutParams lp = baseParams(size, size);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.round(centerX - size / 2f);
        lp.y = Math.round(centerY - size / 2f);
        return lp;
    }

    private TextView makeMarker(String symbol, int color) {
        TextView v = new TextView(svc);
        v.setText(symbol);
        v.setTextSize(26);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(3), Color.WHITE);
        v.setBackground(d);
        v.setAlpha(0.92f);
        return v;
    }

    private void attachDrag(View v, final WindowManager.LayoutParams lp) {
        v.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;

            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int[] s = svc.screenSize();
                        lp.x = clamp(startX + Math.round(e.getRawX() - downX), 0, s[0] - size);
                        lp.y = clamp(startY + Math.round(e.getRawY() - downY), 0, s[1] - size);
                        updateLayout(view, lp);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    /** Compense un éventuel décalage (encoche, barre d'état) : le centre doit tomber en (cx, cy). */
    private void correct(final View v, final WindowManager.LayoutParams lp,
                         final float cx, final float cy) {
        v.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean done;

            @Override public void onGlobalLayout() {
                if (done) return;
                done = true;
                v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                int dx = Math.round(cx - size / 2f) - loc[0];
                int dy = Math.round(cy - size / 2f) - loc[1];
                if (dx != 0 || dy != 0) {
                    lp.x += dx;
                    lp.y += dy;
                    updateLayout(v, lp);
                }
            }
        });
    }

    /** Centre de la pastille, en coordonnées d'écran. */
    private float[] centerOnScreen(View marker) {
        int[] loc = new int[2];
        marker.getLocationOnScreen(loc);
        return new float[]{loc[0] + marker.getWidth() / 2f, loc[1] + marker.getHeight() / 2f};
    }

    // ---------------------------------------------------------------- panneau

    private Button makeButton(String text, View.OnClickListener l) {
        Button b = new Button(svc);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void buildPanel() {
        panel = new LinearLayout(svc) {
            @Override protected void onConfigurationChanged(Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                post(new Runnable() {
                    @Override public void run() { restart(); }
                });
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F01E1E1E"));
        bg.setCornerRadius(dp(14));
        panel.setBackground(bg);

        TextView help = new TextView(svc);
        help.setTextColor(Color.WHITE);
        help.setTextSize(13);
        help.setMaxWidth(dp(300));
        help.setText("Profil « " + Profiles.active(svc).name + " » : glisse + et − sur les boutons "
                + "de vitesse de l'appli, puis « Valider ». Tu peux changer d'appli normalement "
                + "pendant le réglage.");
        panel.addView(help);

        LinearLayout row1 = new LinearLayout(svc);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(makeButton("Tester +", new View.OnClickListener() {
            @Override public void onClick(View v) { test(plusMarker, plusLp); }
        }));
        row1.addView(makeButton("Tester −", new View.OnClickListener() {
            @Override public void onClick(View v) { test(minusMarker, minusLp); }
        }));
        row1.addView(makeButton("⇅", new View.OnClickListener() {
            @Override public void onClick(View v) { movePanel(); }
        }));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(svc);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(makeButton("Valider", new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        }));
        row2.addView(makeButton("Annuler", new View.OnClickListener() {
            @Override public void onClick(View v) { hide(); }
        }));
        panel.addView(row2);

        panelLp = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        applyPanelPosition();
    }

    private void applyPanelPosition() {
        panelLp.gravity = (panelTop ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        panelLp.x = 0;
        panelLp.y = dp(48);
    }

    private void movePanel() {
        if (!shown || panel == null) return;
        panelTop = !panelTop;
        applyPanelPosition();
        updateLayout(panel, panelLp);
    }

    // ---------------------------------------------------------------- actions

    /** Envoie un vrai toucher à l'endroit de la pastille, qui s'efface le temps du test. */
    private void test(final TextView marker, final WindowManager.LayoutParams lp) {
        if (!shown || marker == null) return;
        final float[] c = centerOnScreen(marker);
        marker.setVisibility(View.INVISIBLE);
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        updateLayout(marker, lp);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                svc.dispatchTap(c[0], c[1], Prefs.holdMs(svc));
            }
        }, 150);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!shown) return;
                lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                marker.setVisibility(View.VISIBLE);
                updateLayout(marker, lp);
            }
        }, 900);
    }

    private void save() {
        if (!shown || plusMarker == null || minusMarker == null) return;
        int[] screen = svc.screenSize();
        String orient = svc.orientationKey();
        float[] p = centerOnScreen(plusMarker);
        float[] m = centerOnScreen(minusMarker);
        Prefs.setPoint(svc, true, orient, p[0] / screen[0], p[1] / screen[1]);
        Prefs.setPoint(svc, false, orient, m[0] / screen[0], m[1] / screen[1]);
        String name = "land".equals(orient) ? "paysage" : "portrait";
        Bridge.get().log("Points d'appui enregistrés (" + name + ").");
        Toast.makeText(svc, "Points enregistrés (" + name + ")", Toast.LENGTH_SHORT).show();
        hide();
    }
}
