package app.clickbridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Un profil : un nom, les boutons pilotés (+ et − du Click, leviers et boutons du Ride…) et, pour
 * chacun, sa position sur l'écran (fractions de l'écran) en portrait et en paysage.
 */
final class Profile {
    static final String[] ORIENTS = {"port", "land"};

    final String id;
    String name;
    /** Boutons activés dans ce profil (dans l'ordre d'affichage). */
    final List<String> buttons = new ArrayList<>();
    private final Map<String, float[]> points = new HashMap<>();

    Profile(String id, String name, String[] enabled) {
        this.id = id;
        this.name = name;
        setButtons(java.util.Arrays.asList(enabled));
    }

    private static String key(String orient, String button) {
        return orient + "_" + button;
    }

    float[] point(String button, String orient) {
        float[] p = points.get(key(orient, button));
        return p == null ? null : new float[]{p[0], p[1]};
    }

    void setPoint(String button, String orient, float x, float y) {
        points.put(key(orient, button), new float[]{x, y});
    }

    boolean isEnabled(String button) {
        return buttons.contains(button);
    }

    /** Remplace la liste des boutons activés (garde l'ordre d'affichage, ignore les inconnus). */
    void setButtons(List<String> enabled) {
        buttons.clear();
        for (String id : Buttons.ALL) {
            if (enabled.contains(id)) buttons.add(id);
        }
    }

    /** Tous les boutons activés ont une position pour cette orientation (et il y en a au moins un). */
    boolean hasPoints(String orient) {
        if (buttons.isEmpty()) return false;
        for (String b : buttons) if (point(b, orient) == null) return false;
        return true;
    }

    /** Nombre de boutons activés qui ont une position pour cette orientation. */
    int pointCount(String orient) {
        int n = 0;
        for (String b : buttons) if (point(b, orient) != null) n++;
        return n;
    }

    Profile copy(String newId, String newName) {
        Profile c = new Profile(newId, newName, buttons.toArray(new String[0]));
        for (Map.Entry<String, float[]> e : points.entrySet()) {
            c.points.put(e.getKey(), new float[]{e.getValue()[0], e.getValue()[1]});
        }
        return c;
    }

    // ---------------------------------------------------------------- JSON

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        JSONArray bs = new JSONArray();
        for (String b : buttons) bs.put(b);
        o.put("buttons", bs);
        JSONObject pts = new JSONObject();
        for (String orient : ORIENTS) {
            JSONObject po = new JSONObject();
            for (String b : Buttons.ALL) {
                float[] p = point(b, orient);
                if (p != null) po.put(b, new JSONArray().put((double) p[0]).put((double) p[1]));
            }
            if (po.length() > 0) pts.put(orient, po);
        }
        o.put("points", pts);
        return o;
    }

    /**
     * Lit un profil (formats 1 et 2). Renvoie null si le nom est absent. Les boutons inconnus et les
     * points invalides (hors de l'écran, pas deux nombres) sont ignorés. forcedId impose
     * l'identifiant (import : toujours un nouveau).
     */
    static Profile fromJson(JSONObject o, String forcedId) {
        String name = o.optString("name", "").trim();
        if (name.isEmpty()) return null;
        if (name.length() > 40) name = name.substring(0, 40);
        String id = forcedId != null ? forcedId : o.optString("id", "");
        if (id.isEmpty()) id = UUID.randomUUID().toString();

        List<String> enabled = new ArrayList<>();
        JSONArray bs = o.optJSONArray("buttons");
        if (bs != null) {
            for (int i = 0; i < bs.length(); i++) {
                String b = normalize(bs.optString(i, ""));
                if (b != null && !enabled.contains(b)) enabled.add(b);
            }
        }
        if (enabled.isEmpty()) enabled.addAll(java.util.Arrays.asList(Buttons.CLICK_DEFAULT));   // format 1
        Profile p = new Profile(id, name, enabled.toArray(new String[0]));

        JSONObject pts = o.optJSONObject("points");
        if (pts != null) {
            for (String orient : ORIENTS) {
                JSONObject po = pts.optJSONObject(orient);
                if (po == null) continue;
                Iterator<String> it = po.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    String b = normalize(k);               // format 1 : « plus », « minus »
                    if (b == null) continue;
                    JSONArray a = po.optJSONArray(k);
                    if (a == null || a.length() != 2) continue;
                    double x = a.optDouble(0, Double.NaN);
                    double y = a.optDouble(1, Double.NaN);
                    if (Double.isNaN(x) || Double.isNaN(y) || x < 0 || x > 1 || y < 0 || y > 1) continue;
                    p.setPoint(b, orient, (float) x, (float) y);
                }
            }
        }
        return p;
    }

    private static String normalize(String key) {
        String u = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        return Buttons.isKnown(u) ? u : null;
    }
}
