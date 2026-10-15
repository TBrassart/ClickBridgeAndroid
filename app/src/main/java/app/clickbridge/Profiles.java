package app.clickbridge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Accès aux profils depuis l'appli : chargement, enregistrement et notification des écrans. */
final class Profiles {
    private Profiles() {}

    private static ProfileBook book;

    private static synchronized ProfileBook book(Context c) {
        if (book != null) return book;
        SharedPreferences sp = Prefs.sp(c);
        ProfileBook b = null;
        String stored = sp.getString("profiles", null);
        if (stored != null) {
            try {
                b = ProfileBook.fromStorage(stored);
            } catch (JSONException e) {
                b = null;
            }
        }
        if (b == null || b.list.isEmpty()) {
            // première utilisation (ou stockage illisible) : un profil, avec les anciens points
            b = ProfileBook.single("MyWhoosh");
            Profile p = b.list.get(0);
            for (String orient : Profile.ORIENTS) {
                for (boolean plus : new boolean[]{true, false}) {
                    float[] pt = Prefs.legacyPoint(c, plus, orient);
                    if (pt != null) p.setPoint(plus, orient, pt[0], pt[1]);
                }
            }
            save(c, b);
        }
        b.activeId = b.defaultId;
        book = b;
        return b;
    }

    private static void save(Context c, ProfileBook b) {
        try {
            Prefs.sp(c).edit().putString("profiles", b.toStorage()).apply();
        } catch (JSONException ignored) {
            // ne devrait pas arriver : les données sont toujours valides
        }
    }

    /** Après une modification : enregistre, prévient la notification et les écrans. */
    private static void changed(Context c, ProfileBook b) {
        save(c, b);
        BridgeService s = BridgeService.current;
        if (s != null) s.refreshNotification();
        Bridge.get().profileChanged();
    }

    // ---------------------------------------------------------------- lecture

    static synchronized List<Profile> all(Context c) {
        return new ArrayList<>(book(c).list);
    }

    static synchronized Profile active(Context c) {
        return book(c).active();
    }

    static synchronized String defaultId(Context c) {
        Profile p = book(c).defaultProfile();
        return p == null ? null : p.id;
    }

    // ---------------------------------------------------------------- points (profil actif)

    static synchronized float[] point(Context c, boolean plus, String orient) {
        return book(c).active().point(plus, orient);
    }

    static synchronized void setPoint(Context c, boolean plus, String orient, float x, float y) {
        ProfileBook b = book(c);
        b.active().setPoint(plus, orient, x, y);
        changed(c, b);
    }

    static synchronized boolean hasPoints(Context c, String orient) {
        return book(c).active().hasPoints(orient);
    }

    // ---------------------------------------------------------------- gestion

    static synchronized void setActive(Context c, String id) {
        ProfileBook b = book(c);
        if (b.setActive(id)) {
            SessionLog.onProfile(c, b.active().name);
            changed(c, b);
        }
    }

    static synchronized Profile next(Context c) {
        ProfileBook b = book(c);
        Profile p = b.next();
        if (p != null) {
            SessionLog.onProfile(c, p.name);
            changed(c, b);
        }
        return p;
    }

    static synchronized void setDefault(Context c, String id) {
        ProfileBook b = book(c);
        if (b.setDefault(id)) changed(c, b);
    }

    static synchronized Profile create(Context c, String name) {
        ProfileBook b = book(c);
        Profile p = b.create(name);
        if (p != null) changed(c, b);
        return p;
    }

    static synchronized Profile duplicate(Context c, String id) {
        ProfileBook b = book(c);
        Profile p = b.duplicate(id);
        if (p != null) changed(c, b);
        return p;
    }

    static synchronized void rename(Context c, String id, String name) {
        ProfileBook b = book(c);
        if (b.rename(id, name)) changed(c, b);
    }

    static synchronized boolean delete(Context c, String id) {
        ProfileBook b = book(c);
        boolean wasActive = b.active() != null && b.active().id.equals(id);
        boolean ok = b.delete(id);
        if (ok) {
            if (wasActive) SessionLog.onProfile(c, b.active().name);
            changed(c, b);
        }
        return ok;
    }

    // ---------------------------------------------------------------- export / import

    static synchronized String exportJson(Context c) {
        try {
            return book(c).exportJson();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @throws IllegalArgumentException avec un message lisible si le texte est invalide */
    static synchronized int importJson(Context c, String text) {
        ProfileBook b = book(c);
        int n = b.importJson(text);
        changed(c, b);
        return n;
    }
}
