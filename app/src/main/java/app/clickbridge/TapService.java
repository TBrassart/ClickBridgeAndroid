package app.clickbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Path;
import android.hardware.display.DisplayManager;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

/**
 * Service d'accessibilité : simule un toucher sur l'écran (dispatchGesture).
 * Il ne lit pas le contenu de l'écran ; il ne sert qu'à envoyer des gestes.
 */
public class TapService extends AccessibilityService {

    static volatile TapService instance;

    private CalibrationOverlay overlay;

    static boolean isActive() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Bridge.get().log("Service d'accessibilité actif.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // volontairement vide : aucun contenu d'écran n'est lu
    }

    @Override
    public void onInterrupt() {
        // rien à interrompre
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // la rotation change l'écran : on réaffiche le calque de réglage dans la nouvelle orientation
        if (overlay != null) overlay.restart();
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        if (overlay != null) overlay.hide();
        Bridge.get().log("Service d'accessibilité arrêté.");
        return super.onUnbind(intent);
    }

    // ---------------------------------------------------------------- écran

    /** Taille réelle de l'écran dans l'orientation actuelle : {largeur, hauteur} en pixels. */
    int[] screenSize() {
        // On interroge l'écran lui-même : la configuration du service n'est pas toujours à jour
        // juste après une rotation.
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics m = new DisplayMetrics();
        display.getRealMetrics(m);
        return new int[]{m.widthPixels, m.heightPixels};
    }

    String orientationKey() {
        int[] s = screenSize();
        return s[0] > s[1] ? "land" : "port";
    }

    /** Referme le volet des notifications (Android 12 et plus) ; sans effet s'il est déjà fermé. */
    void closeNotificationShade() {
        if (Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        }
    }

    // ---------------------------------------------------------------- toucher

    /**
     * Touche le point réglé du bouton. Renvoie null si le toucher est parti, "" si ce bouton n'est
     * pas piloté par le profil actif (rien à faire), sinon un message d'erreur.
     */
    String tap(String button) {
        if (!Profiles.isEnabled(this, button)) return "";
        String orient = orientationKey();
        float[] p = Profiles.point(this, button, orient);
        if (p == null) {
            return "Point d'appui non réglé pour « " + Buttons.label(button) + " » en "
                    + ("land".equals(orient) ? "paysage" : "portrait") + ".";
        }
        int[] s = screenSize();
        boolean ok = dispatchTap(p[0] * s[0], p[1] * s[1], Prefs.holdMs(this));
        return ok ? null : "Android a refusé le geste.";
    }

    boolean dispatchTap(float x, float y, long durationMs) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(1L, durationMs));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCancelled(GestureDescription g) {
                Bridge.get().log("Geste annulé par Android.");
            }
        }, null);
    }

    // ---------------------------------------------------------------- réglage des points

    void startCalibration() {
        if (overlay == null) overlay = new CalibrationOverlay(this);
        overlay.show();
    }
}
