package app.clickbridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Jauge de batterie arrondie, verte / orange / rouge selon le niveau. */
final class BatteryBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int track, ok, warn, err;
    private int level = -1;

    BatteryBar(Context c, Theme t) {
        super(c);
        track = Ui.alpha(t.muted, 0x40);
        ok = t.ok;
        warn = t.warn;
        err = t.err;
    }

    void setLevel(int percent) {
        level = percent;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        paint.setColor(track);
        canvas.drawRoundRect(0f, 0f, w, h, r, r, paint);
        if (level >= 0) {
            paint.setColor(level >= 40 ? ok : (level >= 20 ? warn : err));
            float fill = Math.max(h, w * Math.min(100, level) / 100f);
            canvas.drawRoundRect(0f, 0f, fill, h, r, r, paint);
        }
    }
}
