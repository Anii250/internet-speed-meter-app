package com.example.internetspeedmeeter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom canvas-drawn bar chart for displaying daily data usage.
 * No external library required.
 */
public class BarChartView extends View {

    public static class BarEntry {
        public final String label;     // e.g. "Mon"
        public final float mobileGb;
        public final float wifiGb;

        public BarEntry(String label, float mobileGb, float wifiGb) {
            this.label    = label;
            this.mobileGb = mobileGb;
            this.wifiGb   = wifiGb;
        }
    }

    private final Paint mobilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wifiPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect        = new RectF();

    private List<BarEntry> entries = new ArrayList<>();
    private float maxValue = 1f; // in GB

    public BarChartView(Context context) {
        super(context);
        init(context);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mobilePaint.setColor(ContextCompat.getColor(context, R.color.accent_color));
        mobilePaint.setStyle(Paint.Style.FILL);

        wifiPaint.setColor(ContextCompat.getColor(context, R.color.meter_green));
        wifiPaint.setStyle(Paint.Style.FILL);
        wifiPaint.setAlpha(180);

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary_dark));
        labelPaint.setTextSize(sp(10));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        axisPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary_dark));
        axisPaint.setAlpha(60);
        axisPaint.setStrokeWidth(1f);
        axisPaint.setStyle(Paint.Style.STROKE);
    }

    public void setEntries(List<BarEntry> entries) {
        this.entries = entries;
        maxValue = 0.01f;
        for (BarEntry e : entries) {
            float total = e.mobileGb + e.wifiGb;
            if (total > maxValue) maxValue = total;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        float paddingBottom = dp(24);
        float paddingTop    = dp(8);
        float chartH        = h - paddingBottom - paddingTop;
        float barW          = (float) w / entries.size();
        float gap           = barW * 0.2f;

        // Axis line
        canvas.drawLine(0, h - paddingBottom, w, h - paddingBottom, axisPaint);

        for (int i = 0; i < entries.size(); i++) {
            BarEntry e = entries.get(i);
            float left  = i * barW + gap;
            float right = (i + 1) * barW - gap;
            float cx    = (left + right) / 2f;

            float totalFraction = (e.mobileGb + e.wifiGb) / maxValue;
            float totalBarH     = totalFraction * chartH;

            float mobileFraction = entries.size() > 0 && (e.mobileGb + e.wifiGb) > 0
                    ? e.mobileGb / (e.mobileGb + e.wifiGb) : 1f;
            float mobileH = totalBarH * mobileFraction;
            float wifiH   = totalBarH - mobileH;

            float barBottom = h - paddingBottom;

            // Draw Wi-Fi portion (bottom)
            if (wifiH > 0) {
                rect.set(left, barBottom - wifiH, right, barBottom);
                canvas.drawRoundRect(rect, dp(4), dp(4), wifiPaint);
            }

            // Draw mobile portion (top of wifi)
            if (mobileH > 0) {
                rect.set(left, barBottom - totalBarH, right, barBottom - wifiH);
                canvas.drawRoundRect(rect, dp(4), dp(4), mobilePaint);
            }

            // Day label below bar
            canvas.drawText(e.label, cx, h - dp(6), labelPaint);
        }
    }

    private float dp(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float sp(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
