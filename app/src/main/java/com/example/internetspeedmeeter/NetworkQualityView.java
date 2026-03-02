package com.example.internetspeedmeeter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Draws an animated arc (180° sweep) representing a Network Quality Score from 0 to 100.
 * Color: green (≥70), orange (40–69), red (<40).
 */
public class NetworkQualityView extends View {

    private Paint trackPaint;
    private Paint arcPaint;
    private Paint textPaint;
    private Paint labelPaint;

    private final RectF arcRect = new RectF();

    private int displayScore = 0;     // currently rendered score (animated)
    private int targetScore  = 0;     // desired score

    public NetworkQualityView(Context context) {
        super(context);
        init();
    }

    public NetworkQualityView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NetworkQualityView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(Color.parseColor("#E3F2FD"));  // light blue track
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#1A1A2E"));   // dark text
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(Color.parseColor("#6B7280"));  // grey label
    }

    /** Update the score with a smooth animation. */
    public void setScore(int score) {
        targetScore = Math.max(0, Math.min(100, score));
        ValueAnimator anim = ValueAnimator.ofInt(displayScore, targetScore);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            displayScore = (int) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size   = Math.min(getWidth(), getHeight());
        float cx     = getWidth()  / 2f;
        float cy     = getHeight() / 2f;   // dead center of the square
        float stroke = size * 0.09f;
        float radius = (size / 2f) - stroke - 6f;

        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeWidth(stroke);

        // Arc bounding box centered in the square
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Track — upper semicircle (180° sweep from left to right)
        canvas.drawArc(arcRect, 180f, 180f, false, trackPaint);

        // Colored foreground arc
        float sweep = displayScore * 1.8f;
        arcPaint.setColor(scoreColor(displayScore));
        canvas.drawArc(arcRect, 180f, sweep, false, arcPaint);

        // Score number — centered vertically inside the arc opening
        textPaint.setTextSize(radius * 0.65f);
        canvas.drawText(String.valueOf(displayScore), cx, cy + radius * 0.20f, textPaint);

        // Quality label below the number
        labelPaint.setTextSize(radius * 0.25f);
        canvas.drawText(qualityLabel(displayScore), cx, cy + radius * 0.55f, labelPaint);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Always a perfect square — side = the resolved width
        int w = MeasureSpec.getSize(widthSpec);
        if (w == 0) w = 400;
        setMeasuredDimension(w, w);   // height == width → square
    }

    private int scoreColor(int score) {
        if (score >= 70) return Color.parseColor("#1565C0");  // deep blue  = excellent
        if (score >= 40) return Color.parseColor("#1E88E5");  // mid blue   = fair
        return Color.parseColor("#90CAF9");                   // light blue = poor
    }

    private String qualityLabel(int score) {
        if (score >= 70) return "Excellent";
        if (score >= 55) return "Good";
        if (score >= 40) return "Fair";
        if (score >= 20) return "Poor";
        return "Very Poor";
    }
}
