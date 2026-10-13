package app.clickbridge;

import android.content.Context;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Calque affiché par-dessus MyWhoosh : deux pastilles (+ et −) à glisser sur les boutons de
 * vitesse. « Tester » envoie un vrai toucher à l'endroit de la pastille ; « Valider » l'enregistre.
 */
final class CalibrationOverlay {

    private final TapService svc;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WindowManager.LayoutParams lp;
    private TextView plusMarker, minusMarker;
    private LinearLayout panel;
    private boolean panelTop = true;
    private int size;

    CalibrationOverlay(TapService svc) {
        this.svc = svc;
        this.wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(int v) {
        return (int) (v * svc.getResources().getDisplayMetrics().density + 0.5f);
    }

    boolean isShown() {
        return root != null;
    }

    void show() {
        if (root != null) return;
        size = dp(56);
        final int[] screen = svc.screenSize();
        final String orient = svc.orientationKey();

        root = new FrameLayout(svc);
        root.setBackgroundColor(Color.argb(70, 0, 0, 0));

        plusMarker = makeMarker("+", Color.parseColor("#22C55E"));
        minusMarker = makeMarker("−", Color.parseColor("#6D5BFF"));
        root.addView(plusMarker, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START));
        root.addView(minusMarker, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START));

        buildPanel();

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        wm.addView(root, lp);

        // positions de départ : points déjà réglés, sinon côté droit de l'écran
        final float[] p = Prefs.point(svc, true, orient);
        final float[] m = Prefs.point(svc, false, orient);
        root.post(new Runnable() {
            @Override public void run() {
                if (root == null) return;
                place(plusMarker, p != null ? p[0] * screen[0] : screen[0] * 0.82f,
                        p != null ? p[1] * screen[1] : screen[1] * 0.38f);
                place(minusMarker, m != null ? m[0] * screen[0] : screen[0] * 0.82f,
                        m != null ? m[1] * screen[1] : screen[1] * 0.62f);
            }
        });
    }

    void hide() {
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
            // déjà retiré
        }
        root = null;
    }

    // ---------------------------------------------------------------- éléments

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
        v.setAlpha(0.9f);
        v.setOnTouchListener(new View.OnTouchListener() {
            private float dx, dy;

            @Override public boolean onTouch(View view, MotionEvent e) {
                if (root == null) return true;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = view.getX() - e.getRawX();
                        dy = view.getY() - e.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        view.setX(clamp(e.getRawX() + dx, 0f, root.getWidth() - size));
                        view.setY(clamp(e.getRawY() + dy, 0f, root.getHeight() - size));
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        return v;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Place le centre de la pastille aux coordonnées d'écran (x, y). */
    private void place(View marker, float screenX, float screenY) {
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        marker.setX(clamp(screenX - origin[0] - size / 2f, 0f, Math.max(0, root.getWidth() - size)));
        marker.setY(clamp(screenY - origin[1] - size / 2f, 0f, Math.max(0, root.getHeight() - size)));
    }

    /** Centre de la pastille, en coordonnées d'écran. */
    private float[] centerOnScreen(View marker) {
        int[] loc = new int[2];
        marker.getLocationOnScreen(loc);
        return new float[]{loc[0] + marker.getWidth() / 2f, loc[1] + marker.getHeight() / 2f};
    }

    private Button makeButton(String text, View.OnClickListener l) {
        Button b = new Button(svc);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void buildPanel() {
        panel = new LinearLayout(svc);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E61E1E1E"));
        bg.setCornerRadius(dp(14));
        panel.setBackground(bg);

        TextView help = new TextView(svc);
        help.setTextColor(Color.WHITE);
        help.setTextSize(13);
        help.setText("Glisse + et − sur les boutons de vitesse de MyWhoosh, puis « Valider ».");
        panel.addView(help);

        LinearLayout row1 = new LinearLayout(svc);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(makeButton("Tester +", new View.OnClickListener() {
            @Override public void onClick(View v) { test(plusMarker); }
        }));
        row1.addView(makeButton("Tester −", new View.OnClickListener() {
            @Override public void onClick(View v) { test(minusMarker); }
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

        applyPanelPosition();
    }

    private void applyPanelPosition() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                (panelTop ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL);
        p.topMargin = dp(48);
        p.bottomMargin = dp(48);
        if (panel.getParent() != null) root.removeView(panel);
        root.addView(panel, p);
    }

    private void movePanel() {
        panelTop = !panelTop;
        applyPanelPosition();
    }

    // ---------------------------------------------------------------- actions

    /** Envoie un vrai toucher à l'endroit de la pastille, le calque étant temporairement transparent. */
    private void test(View marker) {
        if (root == null) return;
        final float[] c = centerOnScreen(marker);
        root.setVisibility(View.INVISIBLE);
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(root, lp);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                svc.dispatchTap(c[0], c[1], Prefs.holdMs(svc));
            }
        }, 150);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (root == null) return;
                lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                wm.updateViewLayout(root, lp);
                root.setVisibility(View.VISIBLE);
            }
        }, 900);
    }

    private void save() {
        if (root == null) return;
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
