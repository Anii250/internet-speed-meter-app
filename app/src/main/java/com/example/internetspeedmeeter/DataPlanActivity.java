package com.example.internetspeedmeeter;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Data Plan Manager
 * Lets the user set a monthly data limit (in GB).
 * Shows how much has been used vs the limit with a colour-coded progress bar.
 */
public class DataPlanActivity extends AppCompatActivity {

    public static final String KEY_PLAN_LIMIT_BYTES = "data_plan_limit_bytes";
    private static final String PREFS_NAME = "SpeedPrefs";
    private static final String KEY_MOBILE_DATA = "total_mobile_data";
    private static final String KEY_WIFI_DATA   = "total_wifi_data";

    private SharedPreferences prefs;

    private EditText   etLimitGb;
    private Button     btnSave;
    private Button     btnReset;
    private ProgressBar progressBar;
    private TextView   tvUsed;
    private TextView   tvLimit;
    private TextView   tvPercent;
    private TextView   tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_plan);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        etLimitGb  = findViewById(R.id.etLimitGb);
        btnSave    = findViewById(R.id.btnSavePlan);
        btnReset   = findViewById(R.id.btnResetPlan);
        progressBar = findViewById(R.id.planProgressBar);
        tvUsed     = findViewById(R.id.tvPlanUsed);
        tvLimit    = findViewById(R.id.tvPlanLimit);
        tvPercent  = findViewById(R.id.tvPlanPercent);
        tvStatus   = findViewById(R.id.tvPlanStatus);

        // Show saved limit
        long savedLimitBytes = prefs.getLong(KEY_PLAN_LIMIT_BYTES, 0);
        if (savedLimitBytes > 0) {
            double gb = savedLimitBytes / (1024.0 * 1024.0 * 1024.0);
            etLimitGb.setText(String.format(java.util.Locale.getDefault(), "%.1f", gb));
        }

        btnSave.setOnClickListener(v -> savePlan());
        btnReset.setOnClickListener(v -> resetPlan());

        refreshUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    private void savePlan() {
        String input = etLimitGb.getText().toString().trim();
        if (TextUtils.isEmpty(input)) return;
        try {
            double gb = Double.parseDouble(input);
            if (gb <= 0) return;
            long limitBytes = (long)(gb * 1024L * 1024L * 1024L);
            prefs.edit().putLong(KEY_PLAN_LIMIT_BYTES, limitBytes).apply();
            refreshUI();
        } catch (NumberFormatException ignored) {}
    }

    private void resetPlan() {
        prefs.edit()
             .putLong(KEY_PLAN_LIMIT_BYTES, 0)
             .putLong(KEY_MOBILE_DATA, 0)
             .putLong(KEY_WIFI_DATA, 0)
             .apply();
        etLimitGb.setText("");
        refreshUI();
    }

    private void refreshUI() {
        long limitBytes = prefs.getLong(KEY_PLAN_LIMIT_BYTES, 0);
        long mobileBytes = prefs.getLong(KEY_MOBILE_DATA, 0);
        long wifiBytes   = prefs.getLong(KEY_WIFI_DATA, 0);
        long usedBytes   = mobileBytes + wifiBytes;

        tvUsed.setText("Used: " + SpeedUtils.formatDataUsage(usedBytes));

        if (limitBytes <= 0) {
            tvLimit.setText("Limit: Not set");
            tvPercent.setText("—");
            tvStatus.setText("Set a data limit above to track your plan.");
            progressBar.setProgress(0);
            return;
        }

        double limitGb = limitBytes / (1024.0 * 1024.0 * 1024.0);
        tvLimit.setText(String.format(java.util.Locale.getDefault(),
                "Limit: %.1f GB", limitGb));

        int pct = (int) Math.min(100, usedBytes * 100L / limitBytes);
        progressBar.setProgress(pct);
        tvPercent.setText(pct + "%");

        // Colour coding
        if (pct >= 100) {
            tvStatus.setText("⛔ Data limit exceeded!");
            tvPercent.setTextColor(ContextCompat.getColor(this, R.color.meter_red));
            progressBar.getProgressDrawable()
                    .setColorFilter(ContextCompat.getColor(this, R.color.meter_red),
                            android.graphics.PorterDuff.Mode.SRC_IN);
        } else if (pct >= 80) {
            tvStatus.setText("⚠️ You've used " + pct + "% of your plan. Slow down!");
            tvPercent.setTextColor(ContextCompat.getColor(this, R.color.alert_orange));
            progressBar.getProgressDrawable()
                    .setColorFilter(ContextCompat.getColor(this, R.color.alert_orange),
                            android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            tvStatus.setText("✅ You're within your data plan.");
            tvPercent.setTextColor(ContextCompat.getColor(this, R.color.meter_green));
            progressBar.getProgressDrawable()
                    .setColorFilter(ContextCompat.getColor(this, R.color.meter_green),
                            android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }
}
