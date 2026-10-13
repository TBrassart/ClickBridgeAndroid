package app.clickbridge;

/**
 * État partagé entre le service Bluetooth et l'écran (même processus, thread principal).
 * Garde le dernier statut, la batterie et le journal pour que l'écran les retrouve à l'ouverture.
 */
final class Bridge {

    interface Ui {
        void onStatus(String text, int level);
        void onLog(String line);
        void onButtons(boolean plus, boolean minus);
        void onBattery(int percent);
        void onRunning(boolean running);
    }

    private static final Bridge INSTANCE = new Bridge();
    private static final int MAX_LOG = 20000;

    static Bridge get() {
        return INSTANCE;
    }

    private Ui ui;
    private String statusText = "Arrêté";
    private int statusLevel = ClickClient.GRAY;
    private int battery = -1;
    private boolean running;
    private final StringBuilder log = new StringBuilder();

    void setUi(Ui ui) {
        this.ui = ui;
    }

    /** Retire l'écouteur seulement s'il est encore le nôtre (l'écran suivant a pu s'enregistrer). */
    void clearUi(Ui ui) {
        if (this.ui == ui) this.ui = null;
    }

    String statusText() { return statusText; }
    int statusLevel() { return statusLevel; }
    int battery() { return battery; }
    boolean isRunning() { return running; }
    String logText() { return log.toString(); }

    void status(String text, int level) {
        statusText = text;
        statusLevel = level;
        if (ui != null) ui.onStatus(text, level);
    }

    void log(String line) {
        String stamped = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date()) + "  " + line;
        log.append(stamped).append('\n');
        if (log.length() > MAX_LOG) log.delete(0, log.length() - MAX_LOG);
        if (ui != null) ui.onLog(stamped);
    }

    void battery(int percent) {
        battery = percent;
        if (ui != null) ui.onBattery(percent);
    }

    void buttons(boolean plus, boolean minus) {
        if (ui != null) ui.onButtons(plus, minus);
    }

    void running(boolean r) {
        running = r;
        if (ui != null) ui.onRunning(r);
    }
}
