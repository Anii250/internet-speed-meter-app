package com.example.internetspeedmeeter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * A sleek, neon-glowing circular speedometer gauge.
 * Starts at the bottom (-225 degrees) and sweeps clockwise to the other side.
 */
public class SpeedometerView extends View {

    private static final float DEFAULT_MAX_SPEED_MB = 150f;
    private static final int NEEDLE_ANIM_DURATION_MS = 350;

    // Track (background arc)
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Progress (glowing arc)
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    private float maxSpeedMb = DEFAULT_MAX_SPEED_MB;
    private float currentSpeedMb = 0f;
    
    // Angles for a full 270-degree sweeping arc
    private static final float START_ANGLE = 135f; 
    private static final float MAX_SWEEP_ANGLE = 270f;
    
    private float currentSweepAngle = 0f;
    private ValueAnimator needleAnimator;
    
    private String titleText = "Download";
    private String subtitleText = "MB/s"; // e.g., "MB/s"
    private String countdownText = ""; // e.g., "28s"

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countdownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Gradients
    private int[] gradientColors;

    public SpeedometerView(Context context) {
        super(context);
        init();
    }

    public SpeedometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpeedometerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Deep blue track
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(16));
        trackPaint.setColor(Color.parseColor("#152036")); // Dark track color
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // Neon glowing progress
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dp(16));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        // We will set the shader in onSizeChanged when we know the bounds

        gradientColors = new int[]{
                Color.parseColor("#00E1D9"), // Cyan
                Color.parseColor("#007CFF"), // Blue
                Color.parseColor("#9D00FF"), // Purple
                Color.parseColor("#FF007A")  // Pink
        };

        titlePaint.setColor(Color.parseColor("#A0ABC0")); // Light gray
        titlePaint.setTextSize(sp(14));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        speedTextPaint.setColor(Color.WHITE);
        speedTextPaint.setTextSize(sp(48));
        speedTextPaint.setTextAlign(Paint.Align.CENTER);
        speedTextPaint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));

        subtitlePaint.setColor(Color.parseColor("#718096")); // Darker gray
        subtitlePaint.setTextSize(sp(12));
        subtitlePaint.setTextAlign(Paint.Align.CENTER);

        countdownPaint.setColor(Color.parseColor("#A0ABC0"));
        countdownPaint.setTextSize(sp(14));
        countdownPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float sp(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    public void setMaxSpeedMb(float maxSpeedMb) {
        this.maxSpeedMb = Math.max(0.1f, maxSpeedMb);
    }

    public void setCountdownText(String text) {
        this.countdownText = text == null ? "" : text;
        invalidate();
    }
    
    public void setTitleText(String text) {
        this.titleText = text == null ? "" : text;
        invalidate();
    }

    public void setSpeedMb(float speedMb) {
        float clamped = Math.max(0, Math.min(speedMb, maxSpeedMb));
        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }
        float endSweep = speedToSweepAngle(clamped);
        currentSpeedMb = clamped;
        
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            float startSweep = currentSweepAngle;
            needleAnimator = ValueAnimator.ofFloat(startSweep, endSweep);
            needleAnimator.setDuration(NEEDLE_ANIM_DURATION_MS);
            needleAnimator.setInterpolator(new DecelerateInterpolator());
            needleAnimator.addUpdateListener(animation -> {
                currentSweepAngle = (float) animation.getAnimatedValue();
                invalidate();
            });
            needleAnimator.start();
        } else {
            currentSweepAngle = endSweep;
        }
        invalidate();
    }

    public void setSpeedMbImmediate(float speedMb) {
        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }
        currentSpeedMb = Math.max(0, Math.min(speedMb, maxSpeedMb));
        currentSweepAngle = speedToSweepAngle(currentSpeedMb);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (needleAnimator != null && needleAnimator.isRunning()) {
            needleAnimator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            // Setup gradient when size changes
            float cx = w / 2f;
            float cy = h / 2f;
            // A sweep gradient centered in the view
            SweepGradient sweepGradient = new SweepGradient(cx, cy, gradientColors, null);
            // Rotate the gradient to align with our start angle
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.preRotate(START_ANGLE, cx, cy);
            sweepGradient.setLocalMatrix(matrix);
            progressPaint.setShader(sweepGradient);
            
            // Add a subtle drop shadow to the progress paint for the "neon" effect
            progressPaint.setShadowLayer(dp(8), 0, 0, Color.parseColor("#88007CFF"));
            
            invalidate();
        }
    }

    private float speedToSweepAngle(float speedMb) {
        float t = maxSpeedMb > 0 ? (speedMb / maxSpeedMb) : 0;
        return t * MAX_SWEEP_ANGLE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padding = dp(20); // Make room for stroke width and shadow
        float radius = Math.min(w, h) / 2f - padding;
        float cx = w / 2f;
        float cy = h / 2f;

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        
        // 1. Draw the background track
        canvas.drawArc(arcRect, START_ANGLE, MAX_SWEEP_ANGLE, false, trackPaint);

        // 2. Draw the glowing progress arc
        if (currentSweepAngle > 0.01f) {
            canvas.drawArc(arcRect, START_ANGLE, currentSweepAngle, false, progressPaint);
        }

        // 3. Draw Inner Text
        // Title (e.g. "Download")
        canvas.drawText(titleText, cx, cy - dp(32), titlePaint);
        
        // Speed Value (e.g. "2800")
        String speedStr = currentSpeedMb >= 0.01f
                ? String.format(Locale.getDefault(), "%.1f", currentSpeedMb)
                : "0";
        // Slightly lower the y offset to horizontally center the large text visually
        canvas.drawText(speedStr, cx, cy + dp(12), speedTextPaint);
        
        // Subtitle (e.g. "MB/s")
        canvas.drawText(subtitleText, cx, cy + dp(38), subtitlePaint);

        // Countdown or extra info (e.g., "Finding server...")
        if (!countdownText.isEmpty()) {
            canvas.drawText(countdownText, cx, cy + dp(60), countdownPaint);
        }
    }
}
