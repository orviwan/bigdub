package com.bigdub.clock;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;

/**
 * 1-bit pixel-exact 4x Pebble 91Dub Canvas renderer with dirty-rectangle partial E-Ink updates.
 * Only invalidates the exact bounding rectangle of changed digits/icons on each minute tick.
 */
public class ClockView extends View {

    public interface TouchZoneListener {
        void onTopBarTapped();
    }

    private static final int FACE_W = 576;
    private static final int FACE_H = 672;
    private static final String[] DAY_NAMES = new String[] {
        "sun", "mon", "tue", "wed", "thu", "fri", "sat"
    };

    private final HashMap<String, Bitmap> spriteCache = new HashMap<String, Bitmap>();
    private final Paint bitmapPaint = new Paint();
    private final Paint barPaint = new Paint();
    private final Rect clipBounds = new Rect();
    private final Rect tmpRect = new Rect();
    private final Handler handler = new Handler();

    private TouchZoneListener touchListener;

    // Settings
    private boolean is24h = true;
    private boolean invert = false;
    private boolean hideBranding = false;
    private boolean realBattery = true; // true = real Nook battery %, false = day countdown
    private int offsetY = -14;
    private int ghostClearMinutes = 360; // Every 6 hours (4x/day) for battery preservation

    // Current & Previous Rendered State for Dirty-Rect Diffing
    private boolean hasDrawnOnce = false;
    private int curHourTens = -2;
    private int curHourUnits = -1;
    private int curMinTens = -1;
    private int curMinUnits = -1;
    private int curDayOfWeek = -1;
    private int curDateTens = -1;
    private int curDateUnits = -1;
    private int curTimeFormat = -1; // 0 = none (AM), 1 = PM, 2 = 24H
    private int curBatPct = -1;
    private boolean curBatChg = false;
    private int lastTotalMinutes = -1;

    // Anti-ghosting flash state: 0 = normal, 1 = solid black, 2 = solid white
    private int flashPhase = 0;

    public ClockView(Context context) {
        super(context);
        setFocusable(true);
        bitmapPaint.setFilterBitmap(false);
        bitmapPaint.setAntiAlias(false);
        bitmapPaint.setDither(false);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(Color.BLACK);
        barPaint.setAntiAlias(false);
    }

    public void setTouchZoneListener(TouchZoneListener listener) {
        this.touchListener = listener;
    }

    public void applySettings(boolean is24h, boolean realBattery, boolean invert,
                              boolean hideBranding, int offsetY, int ghostClearMinutes) {
        boolean fullRedrawNeeded = (this.invert != invert)
            || (this.hideBranding != hideBranding)
            || (this.offsetY != offsetY)
            || (this.is24h != is24h)
            || (this.realBattery != realBattery);

        this.is24h = is24h;
        this.realBattery = realBattery;
        this.invert = invert;
        this.hideBranding = hideBranding;
        this.offsetY = offsetY;
        this.ghostClearMinutes = ghostClearMinutes;

        refreshClock(fullRedrawNeeded);
    }

    /**
     * Recomputes time & battery state and invalidates ONLY the changed sub-rectangles.
     */
    public void refreshClock(boolean forceFull) {
        Calendar cal = Calendar.getInstance();
        int hours = cal.get(Calendar.HOUR_OF_DAY);
        int minutes = cal.get(Calendar.MINUTE);
        int dayIdx = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY; // 0..6
        if (dayIdx < 0 || dayIdx > 6) dayIdx = 0;
        int dateNum = cal.get(Calendar.DAY_OF_MONTH);

        int totalMinutes = hours * 60 + minutes;
        boolean minuteAdvanced = (lastTotalMinutes != -1 && totalMinutes != lastTotalMinutes);
        lastTotalMinutes = totalMinutes;

        // Periodic anti-ghosting waveform clear (e.g., every 6 hours on boundary)
        if (!forceFull && minuteAdvanced && ghostClearMinutes > 0
                && (totalMinutes % ghostClearMinutes == 0)) {
            updateInternalState(hours, minutes, dayIdx, dateNum, null);
            triggerGhostClear();
            return;
        }

        int vw = getWidth() > 0 ? getWidth() : 600;
        int vh = getHeight() > 0 ? getHeight() : 800;
        int ox = (vw - FACE_W) / 2;
        int oy = (vh - FACE_H) / 2 + offsetY;

        if (forceFull || !hasDrawnOnce) {
            updateInternalState(hours, minutes, dayIdx, dateNum, null);
            hasDrawnOnce = true;
            invalidate();
            return;
        }

        Rect dirty = new Rect();
        updateInternalState(hours, minutes, dayIdx, dateNum, dirty);
        if (!dirty.isEmpty()) {
            dirty.offset(ox, oy);
            invalidate(dirty.left, dirty.top, dirty.right, dirty.bottom);
        }
    }

    private void updateInternalState(int hours, int minutes, int dayIdx, int dateNum, Rect dirty) {
        // 1. Format hour & AM/PM/24H indicator
        int displayHour = hours;
        int nextTimeFormat;
        if (!is24h) {
            boolean isPM = (hours >= 12);
            displayHour = hours % 12;
            if (displayHour == 0) displayHour = 12;
            nextTimeFormat = isPM ? 1 : 0;
        } else {
            nextTimeFormat = 2;
        }

        int nextHourTens = (!is24h && (displayHour / 10) == 0) ? -1 : (displayHour / 10);
        int nextHourUnits = displayHour % 10;
        int nextMinTens = minutes / 10;
        int nextMinUnits = minutes % 10;
        int nextDateTens = dateNum / 10;
        int nextDateUnits = dateNum % 10;

        // 2. Battery state (Real Nook fuel gauge vs Day Countdown)
        int nextBatPct;
        boolean nextBatChg;
        if (realBattery) {
            PowerOptimizer.BatteryState bs = PowerOptimizer.readHardwareBattery(getContext());
            nextBatPct = bs.percent;
            nextBatChg = bs.charging;
        } else {
            int elapsed = hours * 60 + minutes;
            nextBatPct = Math.round(((1440.0f - elapsed) / 1440.0f) * 100.0f);
            if (nextBatPct < 0) nextBatPct = 0;
            if (nextBatPct > 100) nextBatPct = 100;
            nextBatChg = false;
        }

        // 3. Diff each slot and union dirty rectangles in watchface coordinates
        if (dirty != null) {
            if (nextHourTens != curHourTens) unionSlot(dirty, 40, 336, 104, 164);
            if (nextHourUnits != curHourUnits) unionSlot(dirty, 160, 336, 104, 164);
            if (nextMinTens != curMinTens) unionSlot(dirty, 308, 336, 104, 164);
            if (nextMinUnits != curMinUnits) unionSlot(dirty, 420, 336, 104, 164);
            if (nextTimeFormat != curTimeFormat) unionSlot(dirty, 68, 272, 76, 32);
            if (dayIdx != curDayOfWeek) unionSlot(dirty, 276, 244, 136, 60);
            if (nextDateTens != curDateTens) unionSlot(dirty, 432, 244, 40, 60);
            if (nextDateUnits != curDateUnits) unionSlot(dirty, 484, 244, 40, 60);

            int prevBar = calcBarWidth(curBatPct);
            int nextBar = calcBarWidth(nextBatPct);
            if (prevBar != nextBar || nextBatChg != curBatChg) {
                unionSlot(dirty, 352, 172, 64, 36);
            }
            if ((nextBatPct >= 100) != (curBatPct >= 100)) {
                unionSlot(dirty, 432, 172, 76, 32);
            } else if (nextBatPct < 100 && curBatPct >= 0) {
                if ((nextBatPct / 10) != (curBatPct / 10)) unionSlot(dirty, 432, 172, 20, 32);
                if ((nextBatPct % 10) != (curBatPct % 10)) unionSlot(dirty, 456, 172, 20, 32);
            }
        }

        curHourTens = nextHourTens;
        curHourUnits = nextHourUnits;
        curMinTens = nextMinTens;
        curMinUnits = nextMinUnits;
        curTimeFormat = nextTimeFormat;
        curDayOfWeek = dayIdx;
        curDateTens = nextDateTens;
        curDateUnits = nextDateUnits;
        curBatPct = nextBatPct;
        curBatChg = nextBatChg;
    }

    private static int calcBarWidth(int pct) {
        if (pct <= 0) return 0;
        int w = Math.round((pct / 100.0f) * 44.0f);
        if (w > 44) w = 44;
        return w;
    }

    private static void unionSlot(Rect dirty, int x, int y, int w, int h) {
        dirty.union(x, y, x + w, y + h);
    }

    public void triggerGhostClear() {
        flashPhase = 1;
        invalidate();
        handler.postDelayed(new Runnable() {
            public void run() {
                flashPhase = 2;
                invalidate();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        flashPhase = 0;
                        invalidate();
                        PowerOptimizer.triggerHardwareEpdClear();
                    }
                }, 150);
            }
        }, 150);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshClock(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (flashPhase == 1) {
            canvas.drawColor(Color.BLACK);
            return;
        } else if (flashPhase == 2) {
            canvas.drawColor(Color.WHITE);
            return;
        }

        if (!hasDrawnOnce) {
            Calendar cal = Calendar.getInstance();
            int dayIdx = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            if (dayIdx < 0 || dayIdx > 6) dayIdx = 0;
            updateInternalState(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                dayIdx,
                cal.get(Calendar.DAY_OF_MONTH),
                null);
            hasDrawnOnce = true;
        }

        if (!canvas.getClipBounds(clipBounds)) {
            clipBounds.set(0, 0, getWidth(), getHeight());
        }

        // Outer margin (#000000 in classic mode, #FFFFFF in outer-bezel invert mode)
        canvas.drawColor(invert ? Color.WHITE : Color.BLACK);

        int ox = (getWidth() - FACE_W) / 2;
        int oy = (getHeight() - FACE_H) / 2 + offsetY;

        // 1. Background layer (also clears white LCD window behind any hidden slots)
        drawSpriteIfVisible(canvas, ox, oy, 0, 0, 576, 672,
            invert ? "background-invert.png" : "background.png");

        // 2. Optional Pebble branding mask
        if (hideBranding) {
            drawSpriteIfVisible(canvas, ox, oy, 0, 0, 576, 76,
                invert ? "branding-mask-invert.png" : "branding-mask.png");
        }

        // 3. Status bar icons
        drawSpriteIfVisible(canvas, ox, oy, 68, 172, 48, 36, "icon_meter.png");
        drawSpriteIfVisible(canvas, ox, oy, 132, 172, 28, 36, "icon_bluetooth.png");

        // 4. Battery icon (z-index 3) followed by fill bar on top (z-index 4)
        drawSpriteIfVisible(canvas, ox, oy, 352, 172, 64, 36,
            curBatChg ? "icon_battery_charge.png" : "icon_battery.png");

        if (!curBatChg) {
            int barW = calcBarWidth(curBatPct);
            if (barW > 0) {
                tmpRect.set(ox + 360, oy + 180, ox + 360 + barW, oy + 200);
                if (Rect.intersects(clipBounds, tmpRect)) {
                    canvas.drawRect(tmpRect, barPaint);
                }
            }
        }

        // 5. Battery percentage digits (hidden at 100% per Pebble 91Dub spec)
        if (curBatPct >= 0 && curBatPct < 100) {
            drawSpriteIfVisible(canvas, ox, oy, 432, 172, 20, 32,
                "tiny_" + (curBatPct / 10) + ".png");
            drawSpriteIfVisible(canvas, ox, oy, 456, 172, 20, 32,
                "tiny_" + (curBatPct % 10) + ".png");
            drawSpriteIfVisible(canvas, ox, oy, 480, 172, 28, 28, "tiny_percent.png");
        }

        // 6. Time format (24H or PM)
        if (curTimeFormat == 2) {
            drawSpriteIfVisible(canvas, ox, oy, 68, 272, 76, 32, "time_format_24H.png");
        } else if (curTimeFormat == 1) {
            drawSpriteIfVisible(canvas, ox, oy, 68, 272, 76, 32, "time_format_PM.png");
        }

        // 7. Day of week & Date
        if (curDayOfWeek >= 0 && curDayOfWeek < DAY_NAMES.length) {
            drawSpriteIfVisible(canvas, ox, oy, 276, 244, 136, 60,
                "day_" + DAY_NAMES[curDayOfWeek] + ".png");
        }
        drawSpriteIfVisible(canvas, ox, oy, 432, 244, 40, 60, "datenum_" + curDateTens + ".png");
        drawSpriteIfVisible(canvas, ox, oy, 484, 244, 40, 60, "datenum_" + curDateUnits + ".png");

        // 8. Big 4x DS-Digital-Bold time digits
        if (curHourTens >= 0) {
            drawSpriteIfVisible(canvas, ox, oy, 40, 336, 104, 164, "num_" + curHourTens + ".png");
        }
        drawSpriteIfVisible(canvas, ox, oy, 160, 336, 104, 164, "num_" + curHourUnits + ".png");
        drawSpriteIfVisible(canvas, ox, oy, 276, 364, 24, 108, "separator.png");
        drawSpriteIfVisible(canvas, ox, oy, 308, 336, 104, 164, "num_" + curMinTens + ".png");
        drawSpriteIfVisible(canvas, ox, oy, 420, 336, 104, 164, "num_" + curMinUnits + ".png");
    }

    private void drawSpriteIfVisible(Canvas canvas, int ox, int oy,
                                     int x, int y, int w, int h, String assetName) {
        int left = ox + x;
        int top = oy + y;
        tmpRect.set(left, top, left + w, top + h);
        if (!Rect.intersects(clipBounds, tmpRect)) {
            return;
        }
        Bitmap bmp = getScaledSprite(assetName, w, h);
        if (bmp != null) {
            canvas.drawBitmap(bmp, left, top, bitmapPaint);
        }
    }

    private Bitmap getScaledSprite(String assetName, int targetW, int targetH) {
        String key = assetName + "@" + targetW + "x" + targetH;
        Bitmap cached = spriteCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        AssetManager am = getContext().getAssets();
        InputStream is = null;
        try {
            is = am.open("images/" + assetName);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            opts.inDither = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap raw = BitmapFactory.decodeStream(is, null, opts);
            if (raw == null) return null;

            Bitmap result;
            if (raw.getWidth() == targetW && raw.getHeight() == targetH) {
                result = raw;
            } else {
                // filter=false enforces crisp integer nearest-neighbor pixel scaling
                result = Bitmap.createScaledBitmap(raw, targetW, targetH, false);
                if (result != raw) {
                    raw.recycle();
                }
            }
            spriteCache.put(key, result);
            return result;
        } catch (Throwable t) {
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float y = event.getY();
            if (y <= 80.0f) {
                if (touchListener != null) {
                    touchListener.onTopBarTapped();
                }
                return true;
            } else if (y >= (getHeight() - 80.0f)) {
                triggerGhostClear();
                return true;
            }
        }
        return true;
    }
}
