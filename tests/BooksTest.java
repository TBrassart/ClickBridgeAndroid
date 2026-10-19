package app.clickbridge;

import org.json.JSONObject;
import java.util.Arrays;

/** Vérifie les profils (création, export/import) et l'historique des sessions, sans Android. */
public class BooksTest {
    static int failures = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  ÉCHEC ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] a) throws Exception {
        // ---------------- profils
        ProfileBook b = ProfileBook.single("MyWhoosh");
        Profile my = b.list.get(0);
        my.setPoint(Buttons.PLUS, "port", 0.8f, 0.3f);
        my.setPoint(Buttons.MINUS, "port", 0.8f, 0.6f);
        check(my.hasPoints("port") && !my.hasPoints("land"), "points par orientation");
        Profile rouvy = b.create("Rouvy");
        Profile dup = b.create("rouvy");
        check(dup.name.equals("rouvy (2)"), "nom en double renommé : " + dup.name);
        Profile copy = b.duplicate(my.id);
        check(copy.hasPoints("port") && copy.name.equals("MyWhoosh (copie)"), "duplication copie les points");
        check(b.setDefault(rouvy.id) && b.defaultProfile() == rouvy, "profil par défaut");
        check(b.active() == my, "actif = MyWhoosh");
        b.setActive(rouvy.id);
        Profile n = b.next();
        check(n != rouvy && b.active() == n, "profil suivant : " + n.name);
        for (int i = 0; i < b.list.size() + 1; i++) b.next();
        check(b.active() != null, "boucle sur les profils");
        check(b.delete(rouvy.id) && b.defaultId.equals(b.list.get(0).id), "suppression du profil par défaut -> nouveau défaut");
        ProfileBook one = ProfileBook.single("Seul");
        check(!one.delete(one.list.get(0).id), "impossible de supprimer le dernier profil");

        // stockage interne : aller-retour
        ProfileBook back = ProfileBook.fromStorage(b.toStorage());
        check(back.list.size() == b.list.size() && back.defaultId.equals(b.defaultId), "stockage : aller-retour");
        Profile bm = back.list.get(0);
        check(bm.point(Buttons.PLUS, "port") != null && Math.abs(bm.point(Buttons.PLUS, "port")[0] - 0.8f) < 1e-6, "stockage : points conservés");

        // export / import
        String json = b.exportJson();
        ProfileBook other = ProfileBook.single("MyWhoosh");
        int added = other.importJson(json);
        check(added == b.list.size(), "import : " + added + " profils ajoutés");
        check(other.list.size() == 1 + added, "import n'écrase rien");
        boolean uniq = true;
        for (Profile x : other.list) for (Profile y : other.list) if (x != y && x.name.equalsIgnoreCase(y.name)) uniq = false;
        check(uniq, "import : noms uniques");
        boolean newIds = true;
        for (Profile x : other.list) for (Profile y : b.list) if (x.id.equals(y.id)) newIds = false;
        check(newIds, "import : nouveaux identifiants");

        // import invalide ou malveillant
        String[] bad = {"", "pas du json", "{}", "{\"app\":\"Autre\",\"format\":1,\"profiles\":[]}",
                "{\"app\":\"ClickBridge\",\"format\":3,\"profiles\":[{\"name\":\"x\"}]}",
                "{\"app\":\"ClickBridge\",\"format\":1,\"profiles\":[{\"nom\":\"x\"}]}"};
        for (String s : bad) {
            try { new ProfileBook().importJson(s); check(false, "import refusé : " + s); }
            catch (IllegalArgumentException e) { check(true, "import refusé (« " + e.getMessage() + " »)"); }
        }
        ProfileBook p2 = new ProfileBook();
        p2.importJson("{\"app\":\"ClickBridge\",\"format\":1,\"profiles\":[{\"name\":\"Hors écran\","
                + "\"points\":{\"port\":{\"plus\":[5,0.5],\"minus\":[0.2,\"x\"]},\"land\":{\"plus\":[0.1,0.2],\"minus\":[0.3,0.4]}}}]}");
        Profile he = p2.list.get(0);
        check(!he.hasPoints("port") && he.point(Buttons.PLUS, "port") == null && he.hasPoints("land"),
                "import : points hors écran ou invalides ignorés");
        ProfileBook p3 = new ProfileBook();
        String longName = "N".repeat(200);
        p3.importJson("{\"app\":\"ClickBridge\",\"format\":1,\"profiles\":[{\"name\":\"" + longName + "\"}]}");
        check(p3.list.get(0).name.length() <= 40, "import : nom limité à 40 caractères");


        // ---------------- boutons du Ride et formats
        ProfileBook rb = ProfileBook.single("Rouvy", Buttons.RIDE_DEFAULT);
        Profile rp = rb.list.get(0);
        check(rp.buttons.equals(Arrays.asList(Buttons.SHIFT_UP_R, Buttons.SHIFT_DN_R, Buttons.SHIFT_UP_L, Buttons.SHIFT_DN_L)),
                "profil Ride : 4 leviers par défaut");
        check(!rp.hasPoints("port"), "profil Ride sans point : non réglé");
        for (String id : Buttons.RIDE_DEFAULT) rp.setPoint(id, "land", 0.5f, 0.5f);
        check(rp.hasPoints("land") && rp.pointCount("land") == 4, "profil Ride : 4 points réglés en paysage");
        rp.setButtons(Arrays.asList(Buttons.A, Buttons.SHIFT_UP_R, "INCONNU", Buttons.SHIFT_UP_R));
        check(rp.buttons.equals(Arrays.asList(Buttons.SHIFT_UP_R, Buttons.A)), "setButtons : ordre d'affichage, inconnus et doublons ignorés");
        check(!rp.hasPoints("land"), "bouton ajouté sans point : orientation non réglée");
        ProfileBook rb2 = ProfileBook.fromStorage(rb.toStorage());
        check(rb2.list.get(0).buttons.equals(rp.buttons) && rb2.list.get(0).point(Buttons.SHIFT_DN_L, "land") != null, "stockage : boutons et points du Ride conservés");

        // ancien format (0.4) : « plus » / « minus », sans liste de boutons
        ProfileBook old = new ProfileBook();
        old.importJson("{\"app\":\"ClickBridge\",\"format\":1,\"profiles\":[{\"name\":\"Ancien\","
                + "\"points\":{\"port\":{\"plus\":[0.8,0.3],\"minus\":[0.8,0.6]}}}]}");
        Profile op = old.list.get(0);
        check(op.buttons.equals(Arrays.asList(Buttons.PLUS, Buttons.MINUS)) && op.hasPoints("port"), "ancien format 1 relu (PLUS/MINUS)");
        ProfileBook st = ProfileBook.fromStorage("{\"default\":\"x\",\"profiles\":[{\"id\":\"x\",\"name\":\"v0.4\","
                + "\"points\":{\"land\":{\"plus\":[0.1,0.2],\"minus\":[0.3,0.4]}}}]}");
        check(st.list.get(0).hasPoints("land") && st.defaultId.equals("x"), "stockage 0.4 relu sans perte");
        String ex = rb.exportJson();
        check(ex.contains("\"format\": 2") && ex.contains("SHIFT_UP_R"), "export au format 2 avec les identifiants de boutons");
        ProfileBook imp = new ProfileBook();
        imp.importJson("{\"app\":\"ClickBridge\",\"format\":2,\"profiles\":[{\"name\":\"X\",\"buttons\":[\"A\",\"ZZZ\",\"UP\"],"
                + "\"points\":{\"port\":{\"A\":[0.1,0.2],\"ZZZ\":[0.3,0.3],\"UP\":[2,2]}}}]}");
        Profile ip = imp.list.get(0);
        check(ip.buttons.equals(Arrays.asList(Buttons.UP, Buttons.A)) && ip.point(Buttons.A, "port") != null && ip.point(Buttons.UP, "port") == null,
                "import : bouton inconnu et point hors écran ignorés");

        // ---------------- sessions
        long t = 1_000_000_000L; long MIN = 60_000L;
        SessionBook s = new SessionBook();
        s.ready(t, "MyWhoosh", 80);
        s.tap(t + 5000, true); s.tap(t + 6000, true); s.tap(t + 7000, false);
        s.battery(78);
        s.profile("Rouvy"); s.profile("Rouvy");
        check(s.sessions.size() == 1 && s.current().plus == 2 && s.current().minus == 1, "session : appuis comptés");
        check(s.current().profile.equals("MyWhoosh → Rouvy"), "session : changement de profil : " + s.current().profile);
        s.notReady(t + 20 * MIN);
        s.ready(t + 22 * MIN, "MyWhoosh", 70);                 // reprise dans les 5 minutes
        check(s.sessions.size() == 1 && s.sessions.get(0).endPending == 0, "reconnexion < 5 min : même session");
        s.tap(t + 23 * MIN, true);
        s.notReady(t + 30 * MIN);
        check(!s.tap(t + 31 * MIN, true), "pas d'appui compté hors connexion");
        s.ready(t + 40 * MIN, "Zwift", 60);                    // > 5 min : nouvelle session
        check(s.sessions.size() == 2, "reconnexion > 5 min : nouvelle session");
        check(!s.sessions.get(0).open && s.sessions.get(0).end == t + 30 * MIN, "ancienne session close à la déconnexion");
        s.battery(55);
        s.finish(t + 50 * MIN);
        Object last = s.sessions.get(1);
        check(!s.sessions.get(1).open && s.sessions.get(1).end == t + 50 * MIN, "arrêt du service : session close");
        check(SessionBook.duration(s.sessions.get(0), 0) == 30 * MIN, "durée session 1 = 30 min");
        check(s.sessions.get(1).battery0 == 60 && s.sessions.get(1).battery1 == 55, "batterie début/fin");

        // tick : session sans Click au-delà de la grâce
        SessionBook k = new SessionBook();
        k.ready(t, "A", 90); k.notReady(t + MIN);
        k.tick(t + 3 * MIN); check(k.current() != null, "tick avant 5 min : encore ouverte");
        k.tick(t + 7 * MIN); check(k.current() == null && k.sessions.get(0).end == t + MIN, "tick après 5 min : close");

        // stockage, avec un nom de profil piégé et une appli tuée en cours de session
        SessionBook z = new SessionBook();
        z.ready(t, "Rouvy; \"test\" | é\nligne", 50);
        z.tap(t + 1000, true);
        SessionBook z2 = SessionBook.parse(z.serialize());
        check(z2.sessions.size() == 1 && z2.sessions.get(0).profile.equals("Rouvy; \"test\" | é\nligne"), "stockage : nom piégé conservé");
        check(!z2.sessions.get(0).open && z2.sessions.get(0).plus == 1, "appli tuée : session rouverte close, appuis conservés");
        check(SessionBook.parse("n'importe quoi\n1|2|3\n").sessions.isEmpty(), "lignes abîmées ignorées");

        // plafond
        SessionBook m = new SessionBook();
        for (int i = 0; i < 250; i++) { m.ready(t + i * 60 * MIN, "P", 50); m.finish(t + i * 60 * MIN + 1000); }
        check(m.sessions.size() == SessionBook.MAX_SESSIONS, "plafond de " + SessionBook.MAX_SESSIONS + " sessions");

        // CSV
        String csv = z.toCsv(t + 5000);
        check(csv.startsWith("debut;fin;duree_s;profil;") && csv.contains("\"Rouvy; \"\"test\"\" | é\nligne\""), "CSV : échappement correct");
        check(SessionBook.formatDuration(45_000).equals("45 s") && SessionBook.formatDuration(725_000).equals("12 min 05 s")
                && SessionBook.formatDuration(4_320_000).equals("1 h 12 min"), "format des durées");

        System.out.println(failures == 0 ? "\nTOUT EST OK" : "\n" + failures + " ÉCHEC(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
