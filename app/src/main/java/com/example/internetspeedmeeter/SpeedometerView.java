package com.example.internetspeedmeeter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import java.util.Locale;

/**
 * A speedometer gauge that shows current speed with an animated needle.
 * 0 is on the left, max speed on the right (semicircle).
 */
public class SpeedometerView extends View {

    private static final float DEFAULT_MAX_SPEED_MB = 150f;
    private static final int NEEDLE_ANIM_DURATION_MS = 280;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float maxSpeedMb = DEFAULT_MAX_SPEED_MB;
    private float currentSpeedMb = 0f;
    private float currentAngleDeg = 180f;  // 0 speed = left (180°)
    private ValueAnimator needleAnimator;
    private String countdownText = ""; // e.g. "28s"

    private int arcColor;
    private int needleColor;
    private int textColor;
    private final Paint countdownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpeedometerView(Context context) {
        super(context);
        init(context);
    }

    public SpeedometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SpeedometerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        arcColor = ContextCompat.getColor(context, R.color.text_secondary_dark);
        needleColor = ContextCompat.getColor(context, R.color.accent_color);
        textColor = ContextCompat.getColor(context, R.color.text_primary_dark);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(8));
        arcPaint.setColor(arcColor);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(dp(4));
        needlePaint.setColor(needleColor);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(textColor);
        textPaint.setTextSize(sp(18));
        textPaint.setTextAlign(Paint.Align.CENTER);

        countdownPaint.setColor(textColor);
        countdownPaint.setTextSize(sp(28));
        countdownPaint.setTextAlign(Paint.Align.CENTER);
        countdownPaint.setAlpha(180);
    }

    private float dp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float sp(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    /** Set max speed (MB/s) for the scale. Needle will not go beyond this. */
    public void setMaxSpeedMb(float maxSpeedMb) {
        this.maxSpeedMb = Math.max(0.1f, maxSpeedMb);
    }

    /** Sets the countdown text drawn in the center of the gauge (e.g. "28s"). Pass empty string to clear. */
    public void setCountdownText(String text) {
        this.countdownText = text == null ? "" : text;
        invalidate();
    }

    /**
     * Set current speed (MB/s). Needle animates smoothly to the new value.
     */
    public void setSpeedMb(float speedMb) {
        float clamped = Math.max(0, Math.min(speedMb, maxSpeedMb));
        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }
        float endAngle = speedToAngle(clamped);
        currentSpeedMb = clamped;
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            float startAngle = currentAngleDeg;
            needleAnimator = ValueAnimator.ofFloat(startAngle, endAngle);
            needleAnimator.setDuration(NEEDLE_ANIM_DURATION_MS);
            needleAnimator.addUpdateListener(animation -> {
                currentAngleDeg = (float) animation.getAnimatedValue();
                invalidate();
            });
            needleAnimator.start();
        } else {
            currentAngleDeg = endAngle;
        }
        invalidate();
    }

    /** Set speed without animation (e.g. reset to 0). */
    public void setSpeedMbImmediate(float speedMb) {
        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }
        currentSpeedMb = Math.max(0, Math.min(speedMb, maxSpeedMb));
        currentAngleDeg = speedToAngle(currentSpeedMb);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Cancel running animator to prevent memory/resource leak
        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) invalidate();
    }

    private float speedToAngle(float speedMb) {
        // 0 MB/s -> 180° (left), maxSpeed -> 0° (right)
        float t = maxSpeedMb > 0 ? (speedMb / maxSpeedMb) : 0;
        return 180f - t * 180f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padding = dp(24);
        float radius = Math.min(w, h) / 2f - padding;
        float cx = w / 2f;
        float cy = h / 2f;

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        // Draw bottom semicircle: start at 180°, sweep 180°
        canvas.drawArc(arcRect, 180f, 180f, false, arcPaint);

        // Optional: draw a filled arc for "progress" (0 to current speed)
        // Skip for a cleaner needle-only look

        // Needle: from center to edge at currentAngleDeg
        double rad = Math.toRadians(currentAngleDeg);
        float needleLen = radius - dp(12);
        float nx = (float) (cx + needleLen * Math.cos(rad));
        float ny = (float) (cy + needleLen * Math.sin(rad));
        canvas.drawLine(cx, cy, nx, ny, needlePaint);

        // Speed text below the gauge — clamp so it never draws outside the view
        String speedStr = currentSpeedMb >= 0.01f
                ? String.format(Locale.getDefault(), "%.2f MB/s", currentSpeedMb)
                : "0 MB/s";
        float textY = cy + radius + dp(28);
        float maxTextY = h - dp(4);
        if (textY > maxTextY) textY = maxTextY;
        canvas.drawText(speedStr, cx, textY, textPaint);

        // Countdown text in the center of the gauge
        if (!countdownText.isEmpty()) {
            canvas.drawText(countdownText, cx, cy + dp(8), countdownPaint);
        }
    }
}
