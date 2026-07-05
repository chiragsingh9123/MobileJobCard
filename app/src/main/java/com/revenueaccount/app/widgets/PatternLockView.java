package com.revenueaccount.app.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom pattern-lock view — user 3x3 dots ke upar ungli ghuma kar
 * apne phone ka unlock pattern bana sakta hai. Result ek String me store
 * hota hai jaise "0-1-2-5-8" (dot indices 0-8, top-left se bottom-right).
 * editable=false karke sirf dikhane (read-only replay) ke liye bhi use ho sakta hai.
 */
public class PatternLockView extends View {

    public interface OnPatternChangeListener {
        void onPatternComplete(String patternCode);
    }

    private final List<Integer> selectedDots = new ArrayList<>();
    private final float[][] dotPositions = new float[9][2];
    private float touchX, touchY;
    private float lastTouchX, lastTouchY;
    private boolean dragging = false;
    private boolean editable = true;
    private float hitRadius = 60f; // view size ke hisaab se onSizeChanged me set hota hai

    private Paint dotPaint, selectedDotPaint, linePaint;
    private OnPatternChangeListener listener;

    public PatternLockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.parseColor("#BDBDBD"));
        dotPaint.setStyle(Paint.Style.FILL);

        selectedDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedDotPaint.setColor(Color.parseColor("#1565C0"));
        selectedDotPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#1565C0"));
        linePaint.setStrokeWidth(8f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setEditable(boolean editable) { this.editable = editable; }
    public void setOnPatternChangeListener(OnPatternChangeListener l) { this.listener = l; }

    /** Ek saved pattern code ("0-1-2-5-8") ko dikhane ke liye load karo (read-only replay) */
    public void setPatternCode(String code) {
        selectedDots.clear();
        if (code != null && !code.trim().isEmpty()) {
            try {
                for (String part : code.split("-")) {
                    selectedDots.add(Integer.parseInt(part.trim()));
                }
            } catch (NumberFormatException ignored) {}
        }
        invalidate();
    }

    public String getPatternCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedDots.size(); i++) {
            if (i > 0) sb.append("-");
            sb.append(selectedDots.get(i));
        }
        return sb.toString();
    }

    public void clear() {
        selectedDots.clear();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int size = Math.min(w, h);
        float margin = size * 0.15f;
        float spacing = (size - 2 * margin) / 2f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                dotPositions[idx][0] = margin + col * spacing;
                dotPositions[idx][1] = margin + row * spacing;
            }
        }
        // FIX: hit radius ab view ke actual size ke hisaab se scale hota hai
        // (pehle fixed 60-70 raw pixels tha, jo chhote/bade ya high-density screens
        // par galat size ka touch-target banata tha — isliye dots miss ho jaate the).
        hitRadius = Math.max(spacing * 0.42f, 48f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < selectedDots.size() - 1; i++) {
            float[] a = dotPositions[selectedDots.get(i)];
            float[] b = dotPositions[selectedDots.get(i + 1)];
            canvas.drawLine(a[0], a[1], b[0], b[1], linePaint);
        }
        if (editable && dragging && !selectedDots.isEmpty()) {
            float[] last = dotPositions[selectedDots.get(selectedDots.size() - 1)];
            canvas.drawLine(last[0], last[1], touchX, touchY, linePaint);
        }
        for (int i = 0; i < 9; i++) {
            boolean selected = selectedDots.contains(i);
            canvas.drawCircle(dotPositions[i][0], dotPositions[i][1], selected ? 26f : 20f,
            selected ? selectedDotPaint : dotPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editable) return false;
        lastTouchX = touchX;
        lastTouchY = touchY;
        touchX = event.getX();
        touchY = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            // Yeh view aksar ek ScrollView ke andar hoti hai (New Job Card / Edit Job).
            // ScrollView default me drag gesture ko touch se cheen leta hai (scroll samajh kar),
            // isliye pattern beech me hi toot jaata tha. Yeh line parent ko batati hai ki
            // jab tak finger uthe nahi, scroll mat karo.
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            selectedDots.clear();
            dragging = true;
            lastTouchX = touchX;
            lastTouchY = touchY;
            checkDotHit(touchX, touchY, touchX, touchY);
            invalidate();
            return true;
            case MotionEvent.ACTION_MOVE:
            if (dragging) {
                // FIX: fast swipe me beech ke dots miss ho jaate the kyunki hum sirf
                // current touch POINT check karte the. Ab hum last-position se current
                // position tak ki poori LINE check karte hain, taaki tez gesture me bhi
                // beech me aaya koi dot chhoote nahi.
                checkDotHit(lastTouchX, lastTouchY, touchX, touchY);
                invalidate();
            }
            return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            dragging = false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            invalidate();
            if (event.getAction() == MotionEvent.ACTION_UP && listener != null) {
                listener.onPatternComplete(getPatternCode());
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    /** Line-segment (x1,y1)-(x2,y2) ke raaste me jo bhi dot paas se guzra, use register karta hai
     * — sirf end-point check karne se fast gesture me beech ke dots miss ho jaate the. */
    private void checkDotHit(float x1, float y1, float x2, float y2) {
        // Path ko chhote steps me todkar har step par nearest dot check karo
        float dist = (float) Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) (dist / (hitRadius * 0.5f)));
        for (int s = 0; s <= steps; s++) {
            float t = steps == 0 ? 1f : (float) s / steps;
            float px = x1 + (x2 - x1) * t;
            float py = y1 + (y2 - y1) * t;
            int nearest = -1;
            double nearestDist = Double.MAX_VALUE;
            for (int i = 0; i < 9; i++) {
                if (selectedDots.contains(i)) continue;
                float dx = px - dotPositions[i][0];
                float dy = py - dotPositions[i][1];
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < hitRadius && d < nearestDist) {
                    nearestDist = d;
                    nearest = i;
                }
            }
            if (nearest >= 0) selectedDots.add(nearest);
        }
    }
}
