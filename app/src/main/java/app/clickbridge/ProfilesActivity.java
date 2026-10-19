package app.clickbridge;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Gestion des profils : choisir, créer, renommer, dupliquer, supprimer, exporter, importer. */
public class ProfilesActivity extends BaseActivity implements Bridge.Ui {

    private static final int REQ_EXPORT = 11, REQ_IMPORT = 12;
    private static final int MAX_IMPORT_BYTES = 1_000_000;

    private interface TextCallback {
        void run(String text);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rebuild();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bridge.get().setUi(this);
    }

    @Override
    protected void onStop() {
        Bridge.get().clearUi(this);
        super.onStop();
    }

    // ---------------------------------------------------------------- construction

    private void rebuild() {
        content.removeAllViews();
        backHeader("Profils", "Un profil = les positions des boutons d'une application");

        LinearLayout intro = Ui.card(this, theme);
        intro.addView(Ui.text(this,
                "Crée un profil par application (MyWhoosh, Rouvy, Zwift…) : chacun garde ses propres "
                        + "positions de boutons, en portrait et en paysage.\n\n"
                        + "Le profil par défaut est celui qui est actif au démarrage de l'appli. Tu peux "
                        + "changer le profil actif à tout moment depuis l'accueil, ou depuis la "
                        + "notification (« Profil suivant »).", 13, theme.muted, false));
        content.addView(intro);

        for (Profile p : Profiles.all(this)) content.addView(profileCard(p));
        content.addView(toolsCard());
    }

    private LinearLayout profileCard(final Profile p) {
        final boolean isActive = p.id.equals(Profiles.active(this).id);
        final boolean isDefault = p.id.equals(Profiles.defaultId(this));
        final int count = Profiles.all(this).size();

        LinearLayout card = Ui.card(this, theme);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, p.name, 18, theme.text, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (isActive) head.addView(Ui.pill(this, "● Actif", theme.cyan));
        card.addView(head);

        if (isDefault) card.addView(Ui.pill(this, "★ Par défaut", theme.warn), Ui.wrapTop(this, 8));
        StringBuilder names = new StringBuilder();
        for (String b : p.buttons) {
            if (names.length() > 0) names.append("  ");
            names.append(Buttons.glyph(b));
        }
        TextView info = Ui.text(this, "Boutons : " + (names.length() == 0 ? "aucun" : names)
                + "\nPoints : portrait " + (p.hasPoints("port") ? "✓" : "✗")
                + "  ·  paysage " + (p.hasPoints("land") ? "✓" : "✗"), 13, theme.muted, false);
        info.setPadding(0, dp(8), 0, 0);
        card.addView(info);

        if (!isActive) {
            card.addView(Ui.primary(this, "Utiliser ce profil", theme.cyan, theme.blue, new View.OnClickListener() {
                @Override public void onClick(View v) { Profiles.setActive(ProfilesActivity.this, p.id); }
            }));
        }

        Button setPoints = Ui.soft(this, theme, "Régler les points", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Profiles.setActive(ProfilesActivity.this, p.id);
                Actions.startCalibration(ProfilesActivity.this);
            }
        });
        Button chooseButtons = Ui.soft(this, theme, "Boutons du profil", new View.OnClickListener() {
            @Override public void onClick(View v) { chooseButtons(p); }
        });
        Button rename = Ui.soft(this, theme, "Renommer", new View.OnClickListener() {
            @Override public void onClick(View v) {
                prompt("Renommer le profil", p.name, new TextCallback() {
                    @Override public void run(String text) { Profiles.rename(ProfilesActivity.this, p.id, text); }
                });
            }
        });
        Button duplicate = Ui.soft(this, theme, "Dupliquer", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Profiles.duplicate(ProfilesActivity.this, p.id) == null) {
                    toast("Limite de " + ProfileBook.MAX_PROFILES + " profils atteinte.");
                }
            }
        });
        if (isDefault) {
            addButtons(card, setPoints, chooseButtons, rename, duplicate);
        } else {
            Button makeDefault = Ui.soft(this, theme, "Définir par défaut", new View.OnClickListener() {
                @Override public void onClick(View v) { Profiles.setDefault(ProfilesActivity.this, p.id); }
            });
            addButtons(card, setPoints, chooseButtons, rename, duplicate, makeDefault);
        }
        if (count > 1) {
            Button delete = Ui.soft(this, theme, "Supprimer ce profil", new View.OnClickListener() {
                @Override public void onClick(View v) { confirmDelete(p); }
            });
            delete.setTextColor(theme.err);
            card.addView(delete);
        }
        return card;
    }

    private LinearLayout toolsCard() {
        LinearLayout card = Ui.card(this, theme);
        card.addView(Ui.text(this, "Gérer les profils", 17, theme.text, true));
        card.addView(Ui.primary(this, "➕  Nouveau profil", theme.plusA, theme.plusB, new View.OnClickListener() {
            @Override public void onClick(View v) {
                prompt("Nouveau profil", "", new TextCallback() {
                    @Override public void run(String text) {
                        if (Profiles.create(ProfilesActivity.this, text) == null) {
                            toast("Limite de " + ProfileBook.MAX_PROFILES + " profils atteinte.");
                        } else {
                            toast("Profil créé. Utilise « Régler les points » pour placer ses boutons.");
                        }
                    }
                });
            }
        }));

        TextView exp = Ui.text(this, "Exporter et importer", 15, theme.text, true);
        exp.setPadding(0, dp(18), 0, 0);
        card.addView(exp);
        TextView hint = Ui.text(this,
                "Sauvegarde tous tes profils dans un fichier, ou passe-les d'un appareil à l'autre "
                        + "(téléphone, tablette) via un fichier ou le presse-papiers. L'import ajoute les "
                        + "profils sans rien écraser.\n\n"
                        + "Les positions sont relatives à l'écran : après un import sur un autre appareil, "
                        + "vérifie-les avec « Régler les points » puis « Tester ».", 12, theme.muted, false);
        hint.setPadding(0, dp(4), 0, 0);
        card.addView(hint);

        addButtons(card,
                Ui.soft(this, theme, "Exporter (fichier)", new View.OnClickListener() {
                    @Override public void onClick(View v) { startExport(); }
                }),
                Ui.soft(this, theme, "Importer (fichier)", new View.OnClickListener() {
                    @Override public void onClick(View v) { startImport(); }
                }),
                Ui.soft(this, theme, "Copier (presse-papiers)", new View.OnClickListener() {
                    @Override public void onClick(View v) { copyToClipboard(); }
                }),
                Ui.soft(this, theme, "Coller (presse-papiers)", new View.OnClickListener() {
                    @Override public void onClick(View v) { pasteFromClipboard(); }
                }));
        return card;
    }

    // ---------------------------------------------------------------- dialogues

    private void prompt(String title, String initial, final TextCallback callback) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        callback.run(input.getText().toString());
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Choisit quels boutons (Click, leviers et boutons du Ride) ce profil pilote. */
    private void chooseButtons(final Profile p) {
        final String[] ids = Buttons.ALL;
        CharSequence[] labels = new CharSequence[ids.length];
        final boolean[] checked = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) {
            labels[i] = Buttons.label(ids[i]);
            checked[i] = p.isEnabled(ids[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Boutons de « " + p.name + " »")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        List<String> enabled = new ArrayList<>();
                        for (int i = 0; i < ids.length; i++) if (checked[i]) enabled.add(ids[i]);
                        Profiles.setButtons(ProfilesActivity.this, p.id, enabled);
                        toast(enabled.isEmpty()
                                ? "Aucun bouton activé : ce profil ne touchera rien."
                                : "Boutons enregistrés. Utilise « Régler les points » pour les placer.");
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void confirmDelete(final Profile p) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer « " + p.name + " » ?")
                .setMessage("Ses positions de boutons seront perdues (pense à exporter d'abord si besoin).")
                .setPositiveButton("Supprimer", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Profiles.delete(ProfilesActivity.this, p.id);
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("Import impossible").setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    // ---------------------------------------------------------------- export / import

    private void startExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "clickbridge-profils.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Fichier inaccessible");
                    out.write(Profiles.exportJson(this).getBytes(StandardCharsets.UTF_8));
                }
                toast("Profils exportés.");
            } else if (requestCode == REQ_IMPORT) {
                importText(readText(uri));
            }
        } catch (IOException e) {
            showError("Lecture ou écriture du fichier impossible : " + e.getMessage());
        }
    }

    private String readText(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Fichier inaccessible");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_IMPORT_BYTES) throw new IOException("fichier trop volumineux");
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void importText(String text) {
        try {
            int n = Profiles.importJson(this, text);
            toast(n + (n > 1 ? " profils importés." : " profil importé."));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void copyToClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("profils ClickBridge", Profiles.exportJson(this)));
        toast("Profils copiés : colle-les sur l'autre appareil avec « Coller ».");
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            showError("Le presse-papiers est vide.");
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.length() > MAX_IMPORT_BYTES) {
            showError("Le presse-papiers ne contient pas un export ClickBridge.");
            return;
        }
        importText(text.toString());
    }

    // ---------------------------------------------------------------- Bridge.Ui

    @Override public void onStatus(String text, int level) { }

    @Override public void onLog(String line) { }

    @Override public void onButtons(boolean plus, boolean minus) { }

    @Override public void onBattery(int percent) { }

    @Override public void onRunning(boolean running) { }

    @Override
    public void onProfileChanged() {
        rebuild();
    }
}
