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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglage des points d'appui : une pastille par bouton du profil actif (+ et − du Click, leviers
 * et boutons du Ride…) et un petit panneau, affichés par-dessus l'appli. Chaque élément est une
 * PETITE fenêtre indépendante : le reste de l'écran (barre de navigation, autres applis) reste
 * utilisable pendant le réglage. Toucher une pastille sans la déplacer envoie un toucher de test.
 */
final class CalibrationOverlay {

    private static final int FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

    private final TapService svc;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Map<String, TextView> markers = new LinkedHashMap<>();
    private final Map<String, WindowManager.LayoutParams> markerParams = new LinkedHashMap<>();
    private WindowManager.LayoutParams panelLp;
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
        List<String> ids = Profiles.buttons(svc);
        if (ids.isEmpty()) {
            Toast.makeText(svc, "Ce profil n'a aucun bouton activé : ajoute-en dans « Gérer les profils ».",
                    Toast.LENGTH_LONG).show();
            return;
        }
        size = dp(52);
        int[] screen = svc.screenSize();
        String orient = svc.orientationKey();
        Bridge.get().log("Calque de réglage affiché : " + orient + " " + screen[0] + "x" + screen[1]
                + ", " + ids.size() + " bouton(s)");

        final Map<String, float[]> wanted = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            float[] saved = Profiles.point(svc, id, orient);
            float x, y;
            if (saved != null) {
                x = saved[0] * screen[0];
                y = saved[1] * screen[1];
            } else if (ids.size() <= 2) {
                x = screen[0] * 0.82f;
                y = screen[1] * (0.38f + 0.24f * i);
            } else {                                   // plusieurs boutons : une grille côté droit
                int col = i / 6, row = i % 6;
                x = screen[0] * (0.90f - 0.11f * col);
                y = screen[1] * (0.17f + 0.12f * row);
            }
            wanted.put(id, new float[]{x, y});
            TextView m = makeMarker(id);
            WindowManager.LayoutParams lp = markerParams(x, y);
            markers.put(id, m);
            markerParams.put(id, lp);
            attachDrag(id, m, lp);
        }
        buildPanel();

        try {
            for (String id : ids) wm.addView(markers.get(id), markerParams.get(id));
            wm.addView(panel, panelLp);
        } catch (Exception e) {
            Bridge.get().log("Impossible d'afficher le calque : " + e);
            removeViews();
            return;
        }
        shown = true;
        for (String id : ids) {
            float[] w = wanted.get(id);
            correct(markers.get(id), markerParams.get(id), w[0], w[1]);
        }
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
        List<View> views = new ArrayList<View>(markers.values());
        if (panel != null) views.add(panel);
        for (View v : views) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
                // déjà retiré
            }
        }
        markers.clear();
        markerParams.clear();
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

    private TextView makeMarker(String id) {
        TextView v = new TextView(svc);
        String glyph = Buttons.glyph(id);
        v.setText(glyph);
        v.setTextSize(glyph.length() > 1 ? 17 : 24);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Buttons.color(id));
        d.setStroke(dp(3), Color.WHITE);
        v.setBackground(d);
        v.setAlpha(0.92f);
        return v;
    }

    /** Glisser déplace la pastille ; un simple toucher envoie un toucher de test. */
    private void attachDrag(final String id, View v, final WindowManager.LayoutParams lp) {
        v.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;
            private boolean moved;

            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - downX) > dp(8) || Math.abs(e.getRawY() - downY) > dp(8)) {
                            moved = true;
                        }
                        if (moved) {
                            int[] s = svc.screenSize();
                            lp.x = clamp(startX + Math.round(e.getRawX() - downX), 0, s[0] - size);
                            lp.y = clamp(startY + Math.round(e.getRawY() - downY), 0, s[1] - size);
                            updateLayout(view, lp);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!moved) test(id);
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
        help.setText("Profil « " + Profiles.active(svc).name + " » : glisse chaque pastille sur le bouton "
                + "correspondant de l'appli, puis « Valider ». Touche une pastille pour la tester. "
                + "Tu peux changer d'appli normalement pendant le réglage.");
        panel.addView(help);

        LinearLayout row1 = new LinearLayout(svc);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(makeButton("Tout tester", new View.OnClickListener() {
            @Override public void onClick(View v) { testAll(); }
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
    private void test(String id) {
        final TextView marker = markers.get(id);
        final WindowManager.LayoutParams lp = markerParams.get(id);
        if (!shown || marker == null || lp == null) return;
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

    /** Teste toutes les pastilles l'une après l'autre. */
    private void testAll() {
        int i = 0;
        for (final String id : new ArrayList<>(markers.keySet())) {
            handler.postDelayed(new Runnable() {
                @Override public void run() { test(id); }
            }, 1000L * i++);
        }
    }

    private void save() {
        if (!shown) return;
        int[] screen = svc.screenSize();
        String orient = svc.orientationKey();
        for (Map.Entry<String, TextView> e : markers.entrySet()) {
            float[] c = centerOnScreen(e.getValue());
            Profiles.setPoint(svc, e.getKey(), orient, c[0] / screen[0], c[1] / screen[1]);
        }
        String name = "land".equals(orient) ? "paysage" : "portrait";
        Bridge.get().log("Points d'appui enregistrés (" + name + ").");
        Toast.makeText(svc, "Points enregistrés (" + name + ")", Toast.LENGTH_SHORT).show();
        hide();
    }
}
