package app.clickbridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Un profil : un nom et les positions des boutons + et − (en fractions de l'écran) par orientation. */
final class Profile {
    static final String[] ORIENTS = {"port", "land"};

    final String id;
    String name;
    private final Map<String, float[]> points = new HashMap<>();

    Profile(String id, String name) {
        this.id = id;
        this.name = name;
    }

    private static String key(String orient, boolean plus) {
        return orient + "_" + (plus ? "plus" : "minus");
    }

    float[] point(boolean plus, String orient) {
        float[] p = points.get(key(orient, plus));
        return p == null ? null : new float[]{p[0], p[1]};
    }

    void setPoint(boolean plus, String orient, float x, float y) {
        points.put(key(orient, plus), new float[]{x, y});
    }

    boolean hasPoints(String orient) {
        return point(true, orient) != null && point(false, orient) != null;
    }

    Profile copy(String newId, String newName) {
        Profile c = new Profile(newId, newName);
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
        JSONObject pts = new JSONObject();
        for (String orient : ORIENTS) {
            JSONObject po = new JSONObject();
            put(po, "plus", point(true, orient));
            put(po, "minus", point(false, orient));
            if (po.length() > 0) pts.put(orient, po);
        }
        o.put("points", pts);
        return o;
    }

    private static void put(JSONObject o, String k, float[] p) throws JSONException {
        if (p == null) return;
        o.put(k, new JSONArray().put((double) p[0]).put((double) p[1]));
    }

    /**
     * Lit un profil. Renvoie null si le nom est absent. Les points invalides (hors de l'écran,
     * pas deux nombres) sont ignorés. forcedId impose l'identifiant (import : toujours un nouveau).
     */
    static Profile fromJson(JSONObject o, String forcedId) {
        String name = o.optString("name", "").trim();
        if (name.isEmpty()) return null;
        if (name.length() > 40) name = name.substring(0, 40);
        String id = forcedId != null ? forcedId : o.optString("id", "");
        if (id.isEmpty()) id = UUID.randomUUID().toString();
        Profile p = new Profile(id, name);
        JSONObject pts = o.optJSONObject("points");
        if (pts != null) {
            for (String orient : ORIENTS) {
                JSONObject po = pts.optJSONObject(orient);
                if (po == null) continue;
                read(p, po, "plus", true, orient);
                read(p, po, "minus", false, orient);
            }
        }
        return p;
    }

    private static void read(Profile p, JSONObject po, String k, boolean plus, String orient) {
        JSONArray a = po.optJSONArray(k);
        if (a == null || a.length() != 2) return;
        double x = a.optDouble(0, Double.NaN);
        double y = a.optDouble(1, Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y) || x < 0 || x > 1 || y < 0 || y > 1) return;
        p.setPoint(plus, orient, (float) x, (float) y);
    }
}
