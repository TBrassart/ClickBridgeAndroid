package app.clickbridge;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Historique des sessions (logique pure, le temps est passé en paramètre).
 * Une session = une période pendant laquelle le Click est connecté et prêt. Si le Click se
 * reconnecte dans les 5 minutes, c'est la même session.
 */
final class SessionBook {

    static final class Session {
        long start, end, endPending;
        String profile = "";
        int plus, minus;
        int battery0 = -1, battery1 = -1;
        boolean open;
    }

    static final long GRACE_MS = 5 * 60 * 1000L;
    static final int MAX_SESSIONS = 200;

    final List<Session> sessions = new ArrayList<>();

    Session current() {
        if (sessions.isEmpty()) return null;
        Session s = sessions.get(sessions.size() - 1);
        return s.open ? s : null;
    }

    private void close(Session s, long now) {
        if (s.endPending > 0) {
            s.end = s.endPending;
        } else {
            s.end = Math.max(s.end, now);
        }
        s.endPending = 0;
        s.open = false;
    }

    /** Le Click est connecté et prêt. */
    void ready(long now, String profile, int battery) {
        Session s = current();
        if (s != null && s.endPending > 0 && now - s.endPending <= GRACE_MS) {
            s.endPending = 0;               // reprise de la même session
            s.end = now;
            return;
        }
        if (s != null) close(s, now);
        Session n = new Session();
        n.start = now;
        n.end = now;
        n.open = true;
        n.profile = profile == null ? "" : profile;
        n.battery0 = battery;
        n.battery1 = battery;
        sessions.add(n);
        while (sessions.size() > MAX_SESSIONS) sessions.remove(0);
    }

    /** Le Click n'est plus connecté (il peut revenir pendant le délai de grâce). */
    void notReady(long now) {
        Session s = current();
        if (s != null && s.endPending == 0) {
            s.endPending = now;
            s.end = now;
        }
    }

    boolean tap(long now, boolean plus) {
        Session s = current();
        if (s == null || s.endPending > 0) return false;
        if (plus) s.plus++; else s.minus++;
        s.end = now;
        return true;
    }

    void battery(int percent) {
        Session s = current();
        if (s == null || s.endPending > 0 || percent < 0) return;
        if (s.battery0 < 0) s.battery0 = percent;
        s.battery1 = percent;
    }

    void profile(String name) {
        Session s = current();
        if (s == null || name == null) return;
        if (s.profile.equals(name) || s.profile.endsWith(" → " + name)) return;
        s.profile = s.profile.isEmpty() ? name : s.profile + " → " + name;
    }

    /** Arrêt du service : la session en cours est close. */
    void finish(long now) {
        Session s = current();
        if (s != null) close(s, now);
    }

    /** Ferme une session restée sans Click au-delà du délai de grâce. */
    void tick(long now) {
        Session s = current();
        if (s != null && s.endPending > 0 && now - s.endPending > GRACE_MS) close(s, now);
    }

    static long duration(Session s, long now) {
        return (s.open && s.endPending == 0) ? now - s.start : s.end - s.start;
    }

    // ---------------------------------------------------------------- stockage

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return "";
        }
    }

    String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Session s : sessions) {
            sb.append(s.start).append('|').append(s.end).append('|').append(s.endPending).append('|')
                    .append(s.plus).append('|').append(s.minus).append('|')
                    .append(s.battery0).append('|').append(s.battery1).append('|')
                    .append(s.open ? 1 : 0).append('|').append(enc(s.profile)).append('\n');
        }
        return sb.toString();
    }

    /** Relit l'historique. Une session restée ouverte (appli tuée) est close à sa dernière date connue. */
    static SessionBook parse(String text) {
        SessionBook b = new SessionBook();
        if (text == null) return b;
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\\|", -1);
            if (f.length < 9) continue;
            try {
                Session s = new Session();
                s.start = Long.parseLong(f[0]);
                s.end = Long.parseLong(f[1]);
                s.endPending = Long.parseLong(f[2]);
                s.plus = Integer.parseInt(f[3]);
                s.minus = Integer.parseInt(f[4]);
                s.battery0 = Integer.parseInt(f[5]);
                s.battery1 = Integer.parseInt(f[6]);
                boolean wasOpen = "1".equals(f[7]);
                s.profile = dec(f[8]);
                if (wasOpen) {
                    if (s.endPending > 0) s.end = s.endPending;
                    s.endPending = 0;
                }
                s.open = false;
                b.sessions.add(s);
            } catch (NumberFormatException ignored) {
                // ligne abîmée : on l'ignore
            }
        }
        return b;
    }

    // ---------------------------------------------------------------- affichage / export

    static String formatDate(long ms) {
        return new SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    static String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + " h " + String.format(Locale.FRANCE, "%02d", m) + " min";
        if (m > 0) return m + " min " + String.format(Locale.FRANCE, "%02d", sec) + " s";
        return sec + " s";
    }

    private static String csv(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    String toCsv(long now) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        StringBuilder sb = new StringBuilder("debut;fin;duree_s;profil;appuis_plus;appuis_moins;batterie_debut;batterie_fin\n");
        for (Session s : sessions) {
            long end = (s.open && s.endPending == 0) ? now : s.end;
            sb.append(f.format(new Date(s.start))).append(';')
                    .append(f.format(new Date(end))).append(';')
                    .append(Math.max(0, (end - s.start) / 1000)).append(';')
                    .append(csv(s.profile)).append(';')
                    .append(s.plus).append(';').append(s.minus).append(';')
                    .append(s.battery0 < 0 ? "" : String.valueOf(s.battery0)).append(';')
                    .append(s.battery1 < 0 ? "" : String.valueOf(s.battery1)).append('\n');
        }
        return sb.toString();
    }
}
