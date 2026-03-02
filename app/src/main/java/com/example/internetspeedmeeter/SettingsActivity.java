package com.example.internetspeedmeeter;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME           = "SpeedPrefs";
    public  static final String KEY_METER_COLOR      = "meter_color";
    public  static final String KEY_OVERLAY_SIZE     = "overlay_size";    // 10/12/16
    public  static final String KEY_OVERLAY_POSITION = "overlay_position";// 0=right,1=left
    public  static final String KEY_UPDATE_INTERVAL  = "update_interval"; // 1000/2000
    public  static final String KEY_THEME            = "theme_mode";      // -1/1/2
    public  static final String KEY_SLOW_THRESHOLD   = "slow_threshold";  // bytes/s, 0=off

    // 8 color swatches
    private static final int[] SWATCH_COLORS = {
            0xFF00E5FF, // cyan
            0xFFFFFFFF, // white
            0xFFFF4444, // red
            0xFF00C853, // green
            0xFFFFD600, // yellow
            0xFFFF6D00, // orange
            0xFFAA00FF, // purple
            0xFFFF4081  // pink
    };

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        buildColorSwatches();
        setupOverlaySize();
        setupPosition();
        setupInterval();
        setupTheme();
        setupSlowAlert();
    }

    // ── Color Swatches ────────────────────────────────────────────────────────
    private void buildColorSwatches() {
        LinearLayout grid = findViewById(R.id.colorGrid);
        int currentColor  = prefs.getInt(KEY_METER_COLOR, SWATCH_COLORS[0]);
        int dp8 = dp(8);
        int dp36 = dp(36);

        for (int color : SWATCH_COLORS) {
            FrameLayout swatch = new FrameLayout(this);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(dp36, dp36);
            lp.setMargins(dp8, 0, dp8, 0);
            swatch.setLayoutParams(lp);

            // Outer ring if selected
            if (color == currentColor) {
                swatch.setBackgroundColor(Color.WHITE);
                swatch.setPadding(dp(2), dp(2), dp(2), dp(2));
            }

            View inner = new View(this);
            FrameLayout.LayoutParams innerLp =
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT);
            inner.setLayoutParams(innerLp);
            inner.setBackgroundColor(color);

            // round the inner view - set background to solid color shape
            android.graphics.drawable.GradientDrawable gd =
                    new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(color);
            inner.setBackground(gd);
            swatch.addView(inner);

            final int selectedColor = color;
            swatch.setOnClickListener(v -> {
                prefs.edit().putInt(KEY_METER_COLOR, selectedColor).apply();
                // Restart service to pick up new color
                stopService(new android.content.Intent(this, SpeedService.class));
                startService(new android.content.Intent(this, SpeedService.class));
                // Refresh swatches to update selection ring
                grid.removeAllViews();
                buildColorSwatches();
            });

            grid.addView(swatch);
        }
    }

    // ── Overlay Size ──────────────────────────────────────────────────────────
    private void setupOverlaySize() {
        RadioGroup rg = findViewById(R.id.rgOverlaySize);
        int size = prefs.getInt(KEY_OVERLAY_SIZE, 12);
        if      (size <= 10) rg.check(R.id.rbSizeSmall);
        else if (size <= 12) rg.check(R.id.rbSizeMedium);
        else                 rg.check(R.id.rbSizeLarge);

        rg.setOnCheckedChangeListener((g, id) -> {
            int v = 12;
            if      (id == R.id.rbSizeSmall)  v = 10;
            else if (id == R.id.rbSizeMedium) v = 12;
            else if (id == R.id.rbSizeLarge)  v = 16;
            prefs.edit().putInt(KEY_OVERLAY_SIZE, v).apply();
            restartService();
        });
    }

    // ── Overlay Position ──────────────────────────────────────────────────────
    private void setupPosition() {
        RadioGroup rg = findViewById(R.id.rgPosition);
        int pos = prefs.getInt(KEY_OVERLAY_POSITION, 0); // 0 = right
        rg.check(pos == 0 ? R.id.rbPosRight : R.id.rbPosLeft);
        rg.setOnCheckedChangeListener((g, id) -> {
            prefs.edit().putInt(KEY_OVERLAY_POSITION, id == R.id.rbPosRight ? 0 : 1).apply();
            restartService();
        });
    }

    // ── Update Interval ───────────────────────────────────────────────────────
    private void setupInterval() {
        RadioGroup rg = findViewById(R.id.rgInterval);
        int interval = prefs.getInt(KEY_UPDATE_INTERVAL, 1000);
        rg.check(interval <= 1000 ? R.id.rbInterval1s : R.id.rbInterval2s);
        rg.setOnCheckedChangeListener((g, id) -> {
            prefs.edit().putInt(KEY_UPDATE_INTERVAL, id == R.id.rbInterval1s ? 1000 : 2000).apply();
            restartService();
        });
    }

    // ── Theme ─────────────────────────────────────────────────────────────────
    private void setupTheme() {
        RadioGroup rg = findViewById(R.id.rgTheme);
        int mode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if      (mode == AppCompatDelegate.MODE_NIGHT_YES)            rg.check(R.id.rbThemeDark);
        else if (mode == AppCompatDelegate.MODE_NIGHT_NO)             rg.check(R.id.rbThemeLight);
        else                                                          rg.check(R.id.rbThemeSystem);

        rg.setOnCheckedChangeListener((g, id) -> {
            int m = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if      (id == R.id.rbThemeDark)  m = AppCompatDelegate.MODE_NIGHT_YES;
            else if (id == R.id.rbThemeLight) m = AppCompatDelegate.MODE_NIGHT_NO;
            prefs.edit().putInt(KEY_THEME, m).apply();
            AppCompatDelegate.setDefaultNightMode(m);
        });
    }

    // ── Slow Speed Alert ──────────────────────────────────────────────────────
    private void setupSlowAlert() {
        RadioGroup rg = findViewById(R.id.rgSlowAlert);
        long threshold = prefs.getLong(KEY_SLOW_THRESHOLD, 0);
        if      (threshold == 0)               rg.check(R.id.rbAlertOff);
        else if (threshold <= 100 * 1024L)     rg.check(R.id.rbAlert100kb);
        else if (threshold <= 500 * 1024L)     rg.check(R.id.rbAlert500kb);
        else                                   rg.check(R.id.rbAlert1mb);

        rg.setOnCheckedChangeListener((g, id) -> {
            long v = 0;
            if      (id == R.id.rbAlert100kb) v = 100 * 1024L;
            else if (id == R.id.rbAlert500kb) v = 500 * 1024L;
            else if (id == R.id.rbAlert1mb)   v = 1024 * 1024L;
            prefs.edit().putLong(KEY_SLOW_THRESHOLD, v).apply();
        });
    }

    private void restartService() {
        stopService(new android.content.Intent(this, SpeedService.class));
        startService(new android.content.Intent(this, SpeedService.class));
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
