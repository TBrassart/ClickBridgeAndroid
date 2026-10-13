package app.clickbridge;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Fond en dégradé, défilement, marges système et thème communs à tous les écrans. */
abstract class BaseActivity extends Activity {

    protected Theme theme;
    protected LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = Theme.of(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(theme.bgBottom));
        if (!theme.dark && Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.grad(this, theme.bgTop, theme.bgBottom, 0,
                GradientDrawable.Orientation.TOP_BOTTOM));

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                scroll.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // le thème a changé (réglage ou mode sombre du téléphone) : on reconstruit l'écran
        if (Theme.of(this) != theme) recreate();
    }

    protected int dp(float v) {
        return Ui.dp(this, v);
    }

    /** En-tête des écrans secondaires : bouton retour, titre, sous-titre. */
    protected void backHeader(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));

        TextView back = Ui.roundButton(this, theme, "‹", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        ((LinearLayout.LayoutParams) back.getLayoutParams()).leftMargin = 0;
        row.addView(back);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(Ui.text(this, title, 22, theme.text, true));
        if (subtitle != null) col.addView(Ui.text(this, subtitle, 13, theme.muted, false));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(row);
    }

    /** Bouton large en dégradé orange-rose : l'assistant de configuration. */
    protected View wizardButton(String title, String subtitle, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(Ui.ripple(this, Ui.grad(this, 0xFFFF9F43, 0xFFFF5E7E, 20,
                GradientDrawable.Orientation.LEFT_RIGHT), 20));
        box.setLayoutParams(Ui.fullTop(this, 14));
        box.setOnClickListener(l);
        box.addView(Ui.text(this, title, 18, 0xFFFFFFFF, true));
        TextView sub = Ui.text(this, subtitle, 13, 0xE6FFFFFF, false);
        sub.setPadding(0, dp(4), 0, 0);
        box.addView(sub);
        return box;
    }
}
