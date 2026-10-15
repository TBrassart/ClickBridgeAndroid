package app.clickbridge;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Historique des sessions : enregistrement dans les préférences (les écritures sont espacées). */
final class SessionLog {
    private SessionLog() {}

    private static final long SAVE_EVERY_MS = 15000L;
    private static SessionBook book;
    private static long lastSave;

    private static SessionBook book(Context c) {
        if (book == null) book = SessionBook.parse(Prefs.sp(c).getString("sessions", ""));
        return book;
    }

    private static void save(Context c) {
        Prefs.sp(c).edit().putString("sessions", book(c).serialize()).apply();
        lastSave = System.currentTimeMillis();
    }

    private static void saveSoon(Context c) {
        if (System.currentTimeMillis() - lastSave > SAVE_EVERY_MS) save(c);
    }

    static synchronized void onReady(Context c, String profile, int battery) {
        book(c).ready(System.currentTimeMillis(), profile, battery);
        save(c);
    }

    static synchronized void onNotReady(Context c) {
        SessionBook b = book(c);
        if (b.current() == null) return;
        b.notReady(System.currentTimeMillis());
        save(c);
    }

    static synchronized void onTap(Context c, boolean plus) {
        if (book(c).tap(System.currentTimeMillis(), plus)) saveSoon(c);
    }

    static synchronized void onBattery(Context c, int percent) {
        book(c).battery(percent);
    }

    static synchronized void onProfile(Context c, String name) {
        book(c).profile(name);
    }

    static synchronized void finish(Context c) {
        SessionBook b = book(c);
        if (b.current() == null) return;
        b.finish(System.currentTimeMillis());
        save(c);
    }

    /** Sessions, de la plus récente à la plus ancienne. */
    static synchronized List<SessionBook.Session> list(Context c) {
        SessionBook b = book(c);
        b.tick(System.currentTimeMillis());
        List<SessionBook.Session> copy = new ArrayList<>(b.sessions);
        Collections.reverse(copy);
        return copy;
    }

    static synchronized void clear(Context c) {
        SessionBook b = book(c);
        SessionBook.Session cur = b.current();
        b.sessions.clear();
        if (cur != null) b.sessions.add(cur);       // la session en cours continue
        save(c);
    }

    static synchronized String csv(Context c) {
        return book(c).toCsv(System.currentTimeMillis());
    }
}
