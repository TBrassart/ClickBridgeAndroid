package app.clickbridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** La liste des profils, le profil par défaut et le profil actif (logique pure, sans Android). */
final class ProfileBook {
    static final int MAX_PROFILES = 50;
    static final int MAX_NAME = 40;

    final List<Profile> list = new ArrayList<>();
    String defaultId;
    String activeId;

    static ProfileBook single(String name) {
        ProfileBook b = new ProfileBook();
        Profile p = new Profile(UUID.randomUUID().toString(), name);
        b.list.add(p);
        b.defaultId = p.id;
        b.activeId = p.id;
        return b;
    }

    Profile byId(String id) {
        if (id == null) return null;
        for (Profile p : list) if (p.id.equals(id)) return p;
        return null;
    }

    Profile defaultProfile() {
        Profile p = byId(defaultId);
        return p != null ? p : (list.isEmpty() ? null : list.get(0));
    }

    Profile active() {
        Profile p = byId(activeId);
        return p != null ? p : defaultProfile();
    }

    private boolean nameTaken(String name, String exceptId) {
        for (Profile p : list) {
            if (!p.id.equals(exceptId) && p.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Nom sans doublon : « MyWhoosh », « MyWhoosh (2) », « MyWhoosh (3) »… */
    String uniqueName(String base, String exceptId) {
        String b = base == null ? "" : base.trim();
        if (b.isEmpty()) b = "Profil";
        if (b.length() > MAX_NAME) b = b.substring(0, MAX_NAME);
        String candidate = b;
        int n = 2;
        while (nameTaken(candidate, exceptId)) {
            candidate = b + " (" + n + ")";
            n++;
        }
        return candidate;
    }

    Profile create(String name) {
        if (list.size() >= MAX_PROFILES) return null;
        Profile p = new Profile(UUID.randomUUID().toString(), uniqueName(name, null));
        list.add(p);
        if (defaultId == null) defaultId = p.id;
        if (activeId == null) activeId = p.id;
        return p;
    }

    Profile duplicate(String id) {
        Profile src = byId(id);
        if (src == null || list.size() >= MAX_PROFILES) return null;
        Profile p = src.copy(UUID.randomUUID().toString(), uniqueName(src.name + " (copie)", null));
        list.add(list.indexOf(src) + 1, p);
        return p;
    }

    boolean rename(String id, String name) {
        Profile p = byId(id);
        if (p == null) return false;
        p.name = uniqueName(name, id);
        return true;
    }

    boolean delete(String id) {
        Profile p = byId(id);
        if (p == null || list.size() <= 1) return false;
        list.remove(p);
        if (id.equals(defaultId)) defaultId = list.get(0).id;
        if (id.equals(activeId)) activeId = defaultId;
        return true;
    }

    boolean setDefault(String id) {
        if (byId(id) == null) return false;
        defaultId = id;
        return true;
    }

    boolean setActive(String id) {
        if (byId(id) == null) return false;
        activeId = id;
        return true;
    }

    /** Passe au profil suivant (en boucle) et le renvoie. */
    Profile next() {
        if (list.isEmpty()) return null;
        int i = list.indexOf(active());
        Profile n = list.get((i + 1) % list.size());
        activeId = n.id;
        return n;
    }

    // ---------------------------------------------------------------- stockage interne

    String toStorage() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("default", defaultId);
        JSONArray arr = new JSONArray();
        for (Profile p : list) arr.put(p.toJson());
        o.put("profiles", arr);
        return o.toString();
    }

    static ProfileBook fromStorage(String text) throws JSONException {
        JSONObject o = new JSONObject(text);
        JSONArray arr = o.getJSONArray("profiles");
        ProfileBook b = new ProfileBook();
        for (int i = 0; i < arr.length(); i++) {
            Profile p = Profile.fromJson(arr.getJSONObject(i), null);
            if (p != null && b.byId(p.id) == null) b.list.add(p);
        }
        b.defaultId = o.optString("default", "");
        if (b.byId(b.defaultId) == null && !b.list.isEmpty()) b.defaultId = b.list.get(0).id;
        b.activeId = b.defaultId;
        return b;
    }

    // ---------------------------------------------------------------- export / import

    String exportJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("app", "ClickBridge");
        o.put("format", 1);
        JSONArray arr = new JSONArray();
        for (Profile p : list) arr.put(p.toJson());
        o.put("profiles", arr);
        return o.toString(2);
    }

    /**
     * Ajoute les profils d'un export (jamais d'écrasement : les doublons de nom sont renommés).
     * @return le nombre de profils ajoutés
     * @throws IllegalArgumentException avec un message lisible si le texte n'est pas valide
     */
    int importJson(String text) {
        JSONObject o;
        try {
            o = new JSONObject(text == null ? "" : text.trim());
        } catch (JSONException e) {
            throw new IllegalArgumentException("Texte illisible : ce n'est pas un export ClickBridge.");
        }
        JSONArray arr = o.optJSONArray("profiles");
        if (!"ClickBridge".equals(o.optString("app")) || o.optInt("format", 0) != 1 || arr == null) {
            throw new IllegalArgumentException("Ce n'est pas un export de profils ClickBridge.");
        }
        int added = 0;
        for (int i = 0; i < arr.length(); i++) {
            if (list.size() >= MAX_PROFILES) break;
            JSONObject po = arr.optJSONObject(i);
            if (po == null) continue;
            Profile p = Profile.fromJson(po, UUID.randomUUID().toString());
            if (p == null) continue;
            p.name = uniqueName(p.name, null);
            list.add(p);
            added++;
        }
        if (added == 0) throw new IllegalArgumentException("Aucun profil valide dans ce fichier.");
        return added;
    }
}
