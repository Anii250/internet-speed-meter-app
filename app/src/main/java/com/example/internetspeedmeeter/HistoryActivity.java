package com.example.internetspeedmeeter;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<SpeedTestResult> cachedResults = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        RecyclerView rv    = findViewById(R.id.historyRecyclerView);
        TextView     empty = findViewById(R.id.historyEmpty);
        BarChartView chart = findViewById(R.id.barChartView);
        TextView insightCard = findViewById(R.id.tvAiInsight);
        View     btnCsv  = findViewById(R.id.btnExportCsv);
        View     btnPdf  = findViewById(R.id.btnExportPdf);

        rv.setLayoutManager(new LinearLayoutManager(this));

        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<SpeedTestResult> results = db.speedTestDao().getLast30();
            List<DailyUsage>      usages  = db.dailyUsageDao().getLast7Days();

            // AI Insights
            List<String> insights = SpeedInsightEngine.generate(results);
            String insightText = String.join("\n\n", insights);

            cachedResults = results;

            runOnUiThread(() -> {
                // Insight card
                if (insightCard != null && !insightText.isEmpty()) {
                    insightCard.setText(insightText);
                    insightCard.setVisibility(View.VISIBLE);
                }

                // Speed test list
                if (results.isEmpty()) {
                    empty.setVisibility(View.VISIBLE);
                    rv.setVisibility(View.GONE);
                } else {
                    empty.setVisibility(View.GONE);
                    rv.setVisibility(View.VISIBLE);
                    rv.setAdapter(new SpeedTestResultAdapter(results));
                }

                // Bar chart
                chart.setEntries(buildChartEntries(usages));

                // Export buttons
                if (btnCsv != null) btnCsv.setOnClickListener(v ->
                        executor.execute(() -> ReportExporter.exportCsv(this, cachedResults)));
                if (btnPdf != null) btnPdf.setOnClickListener(v ->
                        executor.execute(() -> ReportExporter.exportPdf(this, cachedResults)));
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private List<BarChartView.BarEntry> buildChartEntries(List<DailyUsage> usages) {
        java.util.Map<String, DailyUsage> map = new java.util.HashMap<>();
        for (DailyUsage u : usages) map.put(u.dateKey, u);

        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE",      Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        List<BarChartView.BarEntry> entries = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String key   = keyFmt.format(cal.getTime());
            String label = dayFmt.format(cal.getTime());
            DailyUsage u = map.get(key);
            float mobileGb = u != null ? u.mobileBytes / (1024f * 1024f * 1024f) : 0f;
            float wifiGb   = u != null ? u.wifiBytes   / (1024f * 1024f * 1024f) : 0f;
            entries.add(new BarChartView.BarEntry(label, mobileGb, wifiGb));
        }
        return entries;
    }
}
