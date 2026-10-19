package app.clickbridge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Connexion Bluetooth au Zwift Click V1 : handshake chiffré puis lecture des boutons.
 * Après un premier appairage réussi, l'adresse est mémorisée et la reconnexion se fait par
 * « connexion automatique » du système, sans scan permanent (moins de batterie).
 */
public final class ClickClient {

    public interface Listener {
        void onStatus(String text, int level);
        void onLog(String text);
        /** Un bouton vient d'être enfoncé (pressed = true) ou relâché. */
        void onButton(String id, boolean pressed);
        void onBattery(int percent);
    }

    public static final int GRAY = 0, ORANGE = 1, GREEN = 2, RED = 3;

    /** Manettes gérées : le Click (une seule) ou les deux unités du Ride. */
    public static final String ROLE_CLICK = "click";
    public static final String ROLE_RIDE_LEFT = "ride_left";
    public static final String ROLE_RIDE_RIGHT = "ride_right";

    private static final String BASE = "-19ca-4651-86e5-fa29dcdd09d1";
    private static final UUID SERVICE = UUID.fromString("00000001" + BASE);
    private static final UUID ASYNC = UUID.fromString("00000002" + BASE);
    private static final UUID SYNC_RX = UUID.fromString("00000003" + BASE);
    private static final UUID SYNC_TX = UUID.fromString("00000004" + BASE);
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int ZWIFT_COMPANY_ID = 0x094A;
    private static final int TYPE_CLICK = 0x37, TYPE_BATTERY = 0x19;
    private static final int TYPE_CLICK_DEVICE = 0x09, TYPE_RIDE_LEFT = 0x08, TYPE_RIDE_RIGHT = 0x07;

    private final Context context;
    private final Listener ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<BluetoothGattDescriptor> descriptorQueue = new ArrayDeque<>();

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private ClickCrypto crypto;
    private volatile boolean running;
    private boolean scanning;
    private boolean ready;
    private final String role;
    private final Set<String> down = new HashSet<>();
    private volatile boolean verbose;

    public ClickClient(Context context, String role, Listener ui) {
        this.context = context.getApplicationContext();
        this.role = role;
        this.ui = ui;
    }

    public String role() {
        return role;
    }

    /** « Click », « Ride (gauche) », « Ride (droite) » : pour les messages. */
    private String deviceName() {
        if (ROLE_RIDE_LEFT.equals(role)) return "Ride (gauche)";
        if (ROLE_RIDE_RIGHT.equals(role)) return "Ride (droite)";
        return "Click";
    }

    private String waitText() {
        return "En attente du " + deviceName() + "… appuie sur un bouton (LED bleue)";
    }

    public void setVerbose(boolean v) {
        verbose = v;
    }

    // ---------------------------------------------------------------- cycle de vie

    public void start() {
        if (running) return;
        running = true;
        startOrConnect();
    }

    public void stop() {
        running = false;
        main.removeCallbacks(reconnectRunnable);
        main.removeCallbacks(handshakeTimeout);
        stopScan();
        closeGatt();
        status("Arrêté", GRAY);
        releaseAll();
    }

    /** Oublie la manette mémorisée : le prochain démarrage refera un scan. */
    public void forget() {
        Prefs.setDeviceAddress(context, role, null);
        log(deviceName() + " oublié : la prochaine connexion refera une recherche.");
        if (running) {
            main.removeCallbacks(reconnectRunnable);
            stopScan();
            closeGatt();
            startOrConnect();
        }
    }

    public boolean isRunning() {
        return running;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    private final Runnable reconnectRunnable = new Runnable() {
        @Override public void run() {
            startOrConnect();
        }
    };

    private void scheduleReconnect(long delayMs) {
        main.removeCallbacks(reconnectRunnable);
        if (running) main.postDelayed(reconnectRunnable, delayMs);
    }

    private void startOrConnect() {
        if (!running) return;
        BluetoothAdapter adapter = adapter();
        if (adapter == null || !adapter.isEnabled()) {
            status("Bluetooth désactivé : active-le, l'appli réessaie toute seule.", RED);
            scheduleReconnect(3000);
            return;
        }
        String address = Prefs.deviceAddress(context, role);
        if (address == null) {
            startScan(adapter);
            return;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            status(waitText(), ORANGE);
            connect(device, true);
        } catch (IllegalArgumentException e) {
            Prefs.setDeviceAddress(context, role, null);
            startScan(adapter);
        }
    }

    // ---------------------------------------------------------------- scan (premier appairage)

    private void startScan(BluetoothAdapter adapter) {
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            status("Scanner Bluetooth indisponible.", RED);
            scheduleReconnect(3000);
            return;
        }
        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(new ArrayList<ScanFilter>(), settings, scanCallback);
            scanning = true;
            status("Recherche du " + deviceName() + "… appuie sur un bouton (LED bleue)", ORANGE);
        } catch (SecurityException e) {
            status("Permission Bluetooth manquante.", RED);
        }
    }

    private void stopScan() {
        if (scanning && scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {
                // Bluetooth coupé ou permission retirée
            }
        }
        scanning = false;
    }

    /** Le type de manette est dans les données constructeur : 9 = Click, 8 = Ride gauche, 7 = Ride droite. */
    private boolean matches(ScanRecord rec) {
        byte[] manuf = rec.getManufacturerSpecificData(ZWIFT_COMPANY_ID);
        int type = (manuf != null && manuf.length > 0) ? (manuf[0] & 0xFF) : -1;
        if (ROLE_RIDE_LEFT.equals(role)) return type == TYPE_RIDE_LEFT;
        if (ROLE_RIDE_RIGHT.equals(role)) return type == TYPE_RIDE_RIGHT;
        String name = rec.getDeviceName();
        return type == TYPE_CLICK_DEVICE || (name != null && name.startsWith("Zwift Click"));
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord rec = result.getScanRecord();
            if (rec == null) return;
            if (!matches(rec)) return;
            stopScan();
            log(deviceName() + " trouvé : " + result.getDevice().getAddress());
            status(deviceName() + " trouvé, connexion…", ORANGE);
            connect(result.getDevice(), false);
        }

        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            log("Échec du scan (code " + errorCode + ")");
            status("Scan Bluetooth impossible (code " + errorCode + ").", RED);
            scheduleReconnect(3000);
        }
    };

    // ---------------------------------------------------------------- connexion

    private void connect(BluetoothDevice device, boolean autoConnect) {
        closeGatt();
        ready = false;
        down.clear();
        try {
            gatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            status("Permission Bluetooth manquante.", RED);
        }
    }

    private void closeGatt() {
        BluetoothGatt g = gatt;
        gatt = null;
        if (g != null) {
            try {
                g.disconnect();
                g.close();
            } catch (Exception ignored) {
                // déjà fermé
            }
        }
    }

    private final Runnable handshakeTimeout = new Runnable() {
        @Override public void run() {
            if (!running || ready) return;
            log("Pas de réponse déchiffrable après le handshake : nouvelle tentative.");
            status("Échange des clés impossible : le " + deviceName() + " est peut-être déjà connecté "
                    + "à une autre appli, ou son firmware est différent.", RED);
            closeGatt();
            scheduleReconnect(3000);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connecté (statut " + statusCode + ")");
                status("Connecté, négociation…", ORANGE);
                // Une réponse de 72 octets exige un MTU d'au moins 75.
                if (!g.requestMtu(247)) g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Déconnecté (statut " + statusCode + ")");
                main.removeCallbacks(handshakeTimeout);
                try {
                    g.close();
                } catch (Exception ignored) {
                    // déjà fermé
                }
                if (gatt == g) gatt = null;
                releaseAll();
                if (running) {
                    status(deviceName() + " déconnecté : réveille-le (appui sur un bouton)", ORANGE);
                    scheduleReconnect(1500);
                }
            }
        }

        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int statusCode) {
            log("MTU négocié : " + mtu + " (statut " + statusCode + ")");
            g.discoverServices();
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            BluetoothGattService s = g.getService(SERVICE);
            if (statusCode != BluetoothGatt.GATT_SUCCESS || s == null) {
                log("Service Zwift introuvable (statut " + statusCode + ")");
                for (BluetoothGattService other : g.getServices()) {
                    log("  service " + other.getUuid());
                }
                status("Service Zwift introuvable : ce n'est peut-être pas un Zwift Click ou Ride.", RED);
                return;
            }
            if (verbose) {
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    log("  caractéristique " + c.getUuid().toString().substring(0, 8)
                            + " propriétés=" + c.getProperties());
                }
            }
            try {
                crypto = new ClickCrypto();
            } catch (GeneralSecurityException e) {
                log("Cryptographie indisponible : " + e);
                status("Cryptographie indisponible sur cet appareil.", RED);
                return;
            }
            descriptorQueue.clear();
            enableNotifications(g, s.getCharacteristic(ASYNC));
            enableNotifications(g, s.getCharacteristic(SYNC_TX));
            writeNextDescriptorOrHandshake(g);
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int statusCode) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                log("Écriture du descripteur : statut " + statusCode);
            }
            writeNextDescriptorOrHandshake(g);
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) {
            if (verbose || statusCode != BluetoothGatt.GATT_SUCCESS) {
                log("Écriture du handshake : statut " + statusCode);
            }
        }

        // Anciennes versions d'Android (jusqu'à 12)
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (Build.VERSION.SDK_INT < 33) postData(c.getUuid(), c.getValue());
        }

        // Android 13 et plus
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            postData(c.getUuid(), value);
        }
    };

    private void enableNotifications(BluetoothGatt g, BluetoothGattCharacteristic c) {
        if (c == null) {
            log("Caractéristique absente");
            return;
        }
        g.setCharacteristicNotification(c, true);
        BluetoothGattDescriptor d = c.getDescriptor(CCCD);
        if (d == null) {
            log("Pas de descripteur de notification sur " + c.getUuid());
            return;
        }
        boolean notify = (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        d.setValue(notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        descriptorQueue.add(d);
    }

    private void writeNextDescriptorOrHandshake(BluetoothGatt g) {
        BluetoothGattDescriptor d = descriptorQueue.poll();
        if (d != null) {
            if (!g.writeDescriptor(d)) {
                log("writeDescriptor refusé");
                writeNextDescriptorOrHandshake(g);
            }
            return;
        }
        sendHandshake(g);
    }

    private void sendHandshake(BluetoothGatt g) {
        BluetoothGattService s = g.getService(SERVICE);
        BluetoothGattCharacteristic rx = s == null ? null : s.getCharacteristic(SYNC_RX);
        if (rx == null || crypto == null) {
            log("Caractéristique d'écriture introuvable");
            return;
        }
        rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        rx.setValue(crypto.hello());
        boolean ok = g.writeCharacteristic(rx);
        if (verbose || !ok) log("Handshake envoyé : " + ok);
        status("Connecté, échange des clés…", ORANGE);
        main.removeCallbacks(handshakeTimeout);
        main.postDelayed(handshakeTimeout, 15000);
    }

    // ---------------------------------------------------------------- données reçues

    private void postData(final UUID uuid, byte[] value) {
        if (value == null) return;
        final byte[] data = value.clone();
        main.post(new Runnable() {
            @Override public void run() {
                process(uuid, data);
            }
        });
    }

    private void process(UUID uuid, byte[] data) {
        if (SYNC_TX.equals(uuid)) {
            try {
                if (crypto != null && crypto.accept(data)) {
                    if (verbose) log("Clé de l'appareil reçue");
                } else {
                    log("Réponse sync_tx inattendue (" + data.length + " octets) : " + hex(data));
                }
            } catch (GeneralSecurityException e) {
                log("Erreur d'échange de clés : " + e);
            }
            return;
        }
        if (!ASYNC.equals(uuid)) return;

        byte[] plain;
        if (data.length >= 9) {                       // trame chiffrée
            if (crypto == null || !crypto.ready()) return;
            try {
                plain = crypto.decrypt(data);
            } catch (GeneralSecurityException e) {
                if (verbose) log("Trame illisible : " + hex(data));
                return;
            }
            if (!ready) {
                ready = true;
                main.removeCallbacks(handshakeTimeout);
                BluetoothGatt g = gatt;
                if (g != null) {
                    String address = g.getDevice().getAddress();
                    if (!address.equals(Prefs.deviceAddress(context, role))) {
                        Prefs.setDeviceAddress(context, role, address);
                        log(deviceName() + " mémorisé : " + address);
                    }
                }
                status("Connecté : prêt", GREEN);
            }
        } else {                                      // trame courte, en clair
            plain = data;
        }
        handlePlain(plain);
    }

    private void handlePlain(byte[] pt) {
        if (pt.length == 0) return;
        int type = pt[0] & 0xFF;
        Map<Integer, Long> f = Pb.parseVarints(pt, 1);
        if (verbose) log(String.format("type=0x%02x champs=%s brut=%s", type, f, hex(pt)));
        if (type == TYPE_BATTERY && f.containsKey(2)) {
            ui.onBattery(f.get(2).intValue());
        } else if (type == TYPE_CLICK && ROLE_CLICK.equals(role)) {
            Set<String> now = new LinkedHashSet<>();
            if (Long.valueOf(0L).equals(f.get(1))) now.add(Buttons.PLUS);       // 0 = bouton enfoncé
            if (Long.valueOf(0L).equals(f.get(2))) now.add(Buttons.MINUS);
            applyPressed(now);
        } else if (type == RideProtocol.TYPE_BUTTONS && !ROLE_CLICK.equals(role)) {
            Long mask = f.get(1);
            if (mask != null) applyPressed(RideProtocol.pressed(mask));
        }
    }

    /** Compare avec l'état précédent et signale chaque bouton qui change (relâchements d'abord). */
    private void applyPressed(Set<String> now) {
        for (String id : new ArrayList<>(down)) {
            if (!now.contains(id)) {
                down.remove(id);
                ui.onButton(id, false);
            }
        }
        for (String id : now) {
            if (down.add(id)) ui.onButton(id, true);
        }
    }

    /** Plus de connexion : tous les boutons sont considérés comme relâchés. */
    private void releaseAll() {
        final ArrayList<String> ids = new ArrayList<>(down);
        down.clear();
        if (ids.isEmpty()) return;
        main.post(new Runnable() {
            @Override public void run() {
                for (String id : ids) ui.onButton(id, false);
            }
        });
    }

    // ---------------------------------------------------------------- utilitaires

    private void log(final String text) {
        final String line = ROLE_CLICK.equals(role) ? text : "[" + deviceName() + "] " + text;
        main.post(new Runnable() {
            @Override public void run() {
                ui.onLog(line);
            }
        });
    }

    private void status(final String text, final int level) {
        main.post(new Runnable() {
            @Override public void run() {
                ui.onStatus(text, level);
            }
        });
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
