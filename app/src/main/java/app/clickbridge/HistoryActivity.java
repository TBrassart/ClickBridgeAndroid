package app.clickbridge;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Historique des sessions : durée, profil, nombre d'appuis, batterie ; export CSV. */
public class HistoryActivity extends BaseActivity {

    private static final int REQ_CSV = 21;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();      // une session en cours a pu évoluer
    }

    private void rebuild() {
        content.removeAllViews();
        backHeader("Historique", "Tes sessions de connexion au Click");

        List<SessionBook.Session> sessions = SessionLog.list(this);
        long now = System.currentTimeMillis();

        LinearLayout summary = Ui.card(this, theme);
        if (sessions.isEmpty()) {
            summary.addView(Ui.text(this, "Aucune session pour le moment.", 15, theme.text, true));
            summary.addView(Ui.text(this,
                    "Une session commence quand le Click est connecté et prêt (« Connecté : prêt »), "
                            + "et se termine quand tu arrêtes ClickBridge ou que le Click reste déconnecté "
                            + "plus de 5 minutes. Une reconnexion rapide reste dans la même session.",
                    13, theme.muted, false));
            content.addView(summary);
            return;
        }

        long total = 0;
        int plus = 0, minus = 0;
        for (SessionBook.Session s : sessions) {
            total += SessionBook.duration(s, now);
            plus += s.plus;
            minus += s.minus;
        }
        summary.addView(Ui.text(this, "RÉSUMÉ", 11, theme.muted, true));
        TextView t = Ui.text(this, sessions.size() + (sessions.size() > 1 ? " sessions" : " session")
                + "  ·  " + SessionBook.formatDuration(total), 18, theme.text, true);
        t.setPadding(0, dp(4), 0, dp(8));
        summary.addView(t);
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.addView(Ui.pill(this, "+  " + plus + " appuis", theme.ok));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        pills.addView(Ui.pill(this, "−  " + minus + " appuis", theme.cyan), lp);
        summary.addView(pills);
        content.addView(summary);

        for (SessionBook.Session s : sessions) content.addView(sessionCard(s, now));

        LinearLayout tools = Ui.card(this, theme);
        tools.addView(Ui.text(this, "Données", 17, theme.text, true));
        TextView hint = Ui.text(this, "L'historique garde les 200 dernières sessions, sur cet appareil "
                + "uniquement. Le fichier CSV s'ouvre dans un tableur.", 12, theme.muted, false);
        hint.setPadding(0, dp(6), 0, 0);
        tools.addView(hint);
        tools.addView(Ui.soft(this, theme, "Exporter en CSV", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("text/csv");
                i.putExtra(Intent.EXTRA_TITLE, "clickbridge-historique.csv");
                startActivityForResult(i, REQ_CSV);
            }
        }));
        android.widget.Button clear = Ui.soft(this, theme, "Effacer l'historique", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        clear.setTextColor(theme.err);
        tools.addView(clear);
        content.addView(tools);
    }

    private LinearLayout sessionCard(SessionBook.Session s, long now) {
        LinearLayout card = Ui.card(this, theme);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, SessionBook.formatDate(s.start), 16, theme.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (s.open && s.endPending == 0) head.addView(Ui.pill(this, "● En cours", theme.ok));
        card.addView(head);

        TextView line = Ui.text(this, "Durée : " + SessionBook.formatDuration(SessionBook.duration(s, now)),
                14, theme.text, false);
        line.setPadding(0, dp(6), 0, 0);
        card.addView(line);
        if (!s.profile.isEmpty()) card.addView(Ui.text(this, "Profil : " + s.profile, 13, theme.muted, false));
        if (s.battery0 >= 0) {
            card.addView(Ui.text(this, "Batterie du Click : " + s.battery0 + " % → " + s.battery1 + " %",
                    13, theme.muted, false));
        }

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setPadding(0, dp(8), 0, 0);
        pills.addView(Ui.pill(this, "+  " + s.plus, theme.ok));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        pills.addView(Ui.pill(this, "−  " + s.minus, theme.cyan), lp);
        card.addView(pills);
        return card;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Effacer l'historique ?")
                .setMessage("Toutes les sessions enregistrées seront supprimées (la session en cours continue).")
                .setPositiveButton("Effacer", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        SessionLog.clear(HistoryActivity.this);
                        rebuild();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CSV || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("Fichier inaccessible");
            out.write(("\uFEFF" + SessionLog.csv(this)).getBytes(StandardCharsets.UTF_8));
            toast("Historique exporté.");
        } catch (IOException e) {
            toast("Export impossible : " + e.getMessage());
        }
    }
}
