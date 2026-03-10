package com.example.internetspeedmeeter;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.speedchecker.android.sdk.Public.SpeedTestListener;
import com.speedchecker.android.sdk.Public.SpeedTestResult;
import com.speedchecker.android.sdk.SpeedcheckerSDK;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SpeedTestListener {

    private static final String PREFS_NAME            = "SpeedPrefs";
    private static final String KEY_MOBILE_DATA       = "total_mobile_data";
    private static final String KEY_WIFI_DATA         = "total_wifi_data";
    private static final long   UPDATE_INTERVAL_MS    = 1_000L;
    private static final int    DOWNLOAD_BUFFER_BYTES = 16_384;
    private static final long   SPEED_UPDATE_NS       = 200_000_000L;
    private static final String SPEED_TEST_URL        = "https://cachefly.cachefly.net/100mb.test";
    private static final int    SPEED_TEST_DURATION_MS = 30_000;

    // --- Views ---
    private TextView        downloadSpeedView; // We'll keep this variable alive but unused for text
    private TextView        uploadSpeedView;   // keep alive
    private TextView        mobileDataUsageView;
    private TextView        wifiDataUsageView;
    private TextView        signalStrengthView;
    private Button          btnSpeedTest;
    private ProgressBar     speedProgressBar;
    private SpeedometerView speedometerView;
    private TextView           speedTestResultView;
    
    // Chart
    private BarChart weeklyUsageChart;

    // Running ping average for quality score (ms)
    private long lastPingMs = 50;

    // --- State ---
    private long    lastMobileBytes    = 0;
    private long    lastWifiBytes      = 0;
    private long    totalMobileBytes   = 0;
    private long    totalWifiBytes     = 0;
    private long    lastUploadBytes    = 0;
    private boolean isSpeedTestRunning = false;

    // --- Threading ---
    private final Handler         handler         = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    // --- Permission Launchers ---
    private final ActivityResultLauncher<String> requestNotificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    ignored -> startMyService());

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> startMyService());

    // --- Named Runnable for screen updates ---
    private final Runnable mScreenUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            long curMobile  = TrafficStats.getMobileRxBytes();
            long curTotal   = TrafficStats.getTotalRxBytes();
            long curTxTotal = TrafficStats.getTotalTxBytes();

            if (curMobile == TrafficStats.UNSUPPORTED
                    || curTotal == TrafficStats.UNSUPPORTED) {
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
                return;
            }

            long curWifi    = curTotal - curMobile;
            long mobileDiff = Math.max(0, curMobile - lastMobileBytes);
            long wifiDiff   = Math.max(0, curWifi   - lastWifiBytes);
            long txDiff     = Math.max(0, curTxTotal - lastUploadBytes);

            totalMobileBytes += mobileDiff;
            totalWifiBytes   += wifiDiff;

            long rxDiff = mobileDiff + wifiDiff;
            // We just update the custom gauge view now since the old textviews are removed
            if (!isSpeedTestRunning) {
                // Not running speed test: show live Rx usage on the gauge
                float combinedMb = rxDiff / (1024f * 1024f); 
                speedometerView.setTitleText("Live Traffic");
                speedometerView.setSpeedMb(combinedMb);
            }
            // uploadSpeedView.setText("↑ " + SpeedUtils.formatSpeed(txDiff)); // removed from xml
            
            // Network Quality Score removed from UI
            // int qualityScore = computeQualityScore(rxDiff + txDiff, lastPingMs);
            // networkQualityView.setScore(qualityScore);

            mobileDataUsageView.setText(SpeedUtils.formatDataUsage(totalMobileBytes));
            wifiDataUsageView.setText(SpeedUtils.formatDataUsage(totalWifiBytes));

            lastMobileBytes = curMobile;
            lastWifiBytes   = curWifi;
            lastUploadBytes = curTxTotal;

            persistDataUsage();

            executorService.execute(() -> {
                String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                        .format(new Date());
                DailyUsage du = new DailyUsage(today, totalMobileBytes, totalWifiBytes);
                AppDatabase.getInstance(MainActivity.this).dailyUsageDao().upsert(du);
            });

            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedTheme = prefs.getInt(SettingsActivity.KEY_THEME,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        setContentView(R.layout.activity_main);

        // Status bar color follows day/night theme automatically via theme XML

        // Init SpeedChecker SDK — required before calling startTest()
        SpeedcheckerSDK.init(this);
        SpeedcheckerSDK.askPermissions(this); // asks LOCATION permission needed for server selection

        bindViews();
        setupBottomNav();
        setupButtons();
        setupChart();
        requestPermissionsAndStartService();
        loadPersistedData();
        startScreenUpdate();
        scheduleSignalRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always highlight the Home tab when we return to this screen
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null) nav.setSelectedItemId(R.id.nav_home);
        
        loadChartData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(mScreenUpdateRunnable);
        handler.removeCallbacksAndMessages(null);
        executorService.shutdown();
        persistDataUsage();
    }

    // =========================================================================
    // View Binding
    // =========================================================================

    private void bindViews() {
        mobileDataUsageView = findViewById(R.id.mobileDataUsageTextView);
        wifiDataUsageView   = findViewById(R.id.wifiDataUsageTextView);
        signalStrengthView  = findViewById(R.id.signalStrengthView);
        btnSpeedTest        = findViewById(R.id.btnSpeedTest);
        speedProgressBar    = findViewById(R.id.speedProgressBar);
        speedometerView     = findViewById(R.id.speedometerView);
        speedTestResultView = findViewById(R.id.speedTestResultView);
        weeklyUsageChart    = findViewById(R.id.weeklyUsageChart);
        
        // Ensure starting state
        speedometerView.setMaxSpeedMb(150f);
        speedometerView.setTitleText("Live Traffic");
        speedometerView.setCountdownText("");
    }

    // =========================================================================
    // Bar Chart
    // =========================================================================
    
    private void setupChart() {
        weeklyUsageChart.getDescription().setEnabled(false);
        weeklyUsageChart.getLegend().setEnabled(false);
        weeklyUsageChart.setPinchZoom(false);
        weeklyUsageChart.setDrawBarShadow(false);
        weeklyUsageChart.setDrawGridBackground(false);
        weeklyUsageChart.setTouchEnabled(false);

        XAxis xAxis = weeklyUsageChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(android.graphics.Color.parseColor("#718096"));
        xAxis.setAxisLineColor(android.graphics.Color.parseColor("#152036"));
        // No static format here; labels are dynamic based on actual data
        xAxis.setGranularity(1f);

        weeklyUsageChart.getAxisLeft().setDrawGridLines(true);
        weeklyUsageChart.getAxisLeft().setGridColor(android.graphics.Color.parseColor("#152036"));
        weeklyUsageChart.getAxisLeft().setTextColor(android.graphics.Color.parseColor("#718096"));
        weeklyUsageChart.getAxisLeft().setAxisLineColor(android.graphics.Color.TRANSPARENT);
        weeklyUsageChart.getAxisLeft().setAxisMinimum(0f);
        weeklyUsageChart.getAxisRight().setEnabled(false);

        loadChartData();
    }

    private void loadChartData() {
        executorService.execute(() -> {
            java.util.List<DailyUsage> recentUsage = AppDatabase.getInstance(MainActivity.this)
                    .dailyUsageDao().getLast7Days();
            
            // Reverse to show oldest to newest (left to right)
            java.util.Collections.reverse(recentUsage);
            
            ArrayList<BarEntry> entries = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();
            
            for (int i = 0; i < recentUsage.size(); i++) {
                DailyUsage u = recentUsage.get(i);
                float totalMb = (u.mobileBytes + u.wifiBytes) / (1024f * 1024f);
                entries.add(new BarEntry(i, totalMb));
                
                try {
                    Date date = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(u.dateKey);
                    String dayStr = new SimpleDateFormat("EEE", Locale.getDefault()).format(date);
                    labels.add(dayStr);
                } catch (Exception e) {
                    labels.add("");
                }
            }
            
            handler.post(() -> {
                if (entries.isEmpty()) {
                    weeklyUsageChart.clear();
                    return;
                }
                
                BarDataSet dataSet = new BarDataSet(entries, "Weekly Data");
                
                int startColor = android.graphics.Color.parseColor("#00E1D9");
                int endColor = android.graphics.Color.parseColor("#007CFF");
                dataSet.setGradientColor(startColor, endColor);
                dataSet.setDrawValues(false); 
                
                BarData data = new BarData(dataSet);
                data.setBarWidth(0.5f);
                
                weeklyUsageChart.setData(data);
                weeklyUsageChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
                weeklyUsageChart.getXAxis().setLabelCount(labels.size());
                
                weeklyUsageChart.animateY(1000);
                weeklyUsageChart.invalidate();
            });
        });
    }

    // =========================================================================
    // Bottom Navigation
    // =========================================================================

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Already home — do nothing (scroll to top optional)
                return true;
            } else if (id == R.id.nav_main_menu) {
                showToolsBottomSheet();
                // We return false or true? If true it stays selected, let's just make it behave like a button or re-select home
                nav.post(() -> nav.setSelectedItemId(R.id.nav_home));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return true;
        });
    }

    // =========================================================================
    // Buttons
    // =========================================================================

    private void setupButtons() {
        btnSpeedTest.setOnClickListener(v -> runSpeedTest());
        
        android.view.View btnHistory = findViewById(R.id.btnHistory);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        }
    }

    private void showToolsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_tools_bottom_sheet, null);
        dialog.setContentView(sheetView);

        // Bind clicks inside bottom sheet
        sheetView.findViewById(R.id.cardPing).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, PingActivity.class));
        });
        sheetView.findViewById(R.id.cardAppUsage).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, AppDataUsageActivity.class));
        });
        sheetView.findViewById(R.id.cardDataPlan).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, DataPlanActivity.class));
        });
        sheetView.findViewById(R.id.cardThrottle).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, ThrottleTestActivity.class));
        });
        sheetView.findViewById(R.id.cardGaming).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, GamingModeActivity.class));
        });
        sheetView.findViewById(R.id.cardSettings).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });

        dialog.show();
    }

    // =========================================================================
    // Permissions & Service
    // =========================================================================

    private void requestPermissionsAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        startMyService();
    }

    private void startMyService() {
        Intent si = new Intent(this, SpeedService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si);
        else startService(si);
    }

    // =========================================================================
    // Data persistence
    // =========================================================================

    private void loadPersistedData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        totalMobileBytes = prefs.getLong(KEY_MOBILE_DATA, 0);
        totalWifiBytes   = prefs.getLong(KEY_WIFI_DATA, 0);
        mobileDataUsageView.setText(SpeedUtils.formatDataUsage(totalMobileBytes));
        wifiDataUsageView.setText(SpeedUtils.formatDataUsage(totalWifiBytes));
    }

    private void persistDataUsage() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putLong(KEY_MOBILE_DATA, totalMobileBytes)
                .putLong(KEY_WIFI_DATA,   totalWifiBytes)
                .apply();
    }

    // =========================================================================
    // Session Reset
    // =========================================================================

    private void resetSession() {
        totalMobileBytes = 0;
        totalWifiBytes   = 0;
        persistDataUsage();
        mobileDataUsageView.setText("0 KB");
        wifiDataUsageView.setText("0 KB");
    }

    // =========================================================================
    // Screen Update Loop
    // =========================================================================

    private void startScreenUpdate() {
        long rx    = TrafficStats.getMobileRxBytes();
        long total = TrafficStats.getTotalRxBytes();
        long tx    = TrafficStats.getTotalTxBytes();
        if (rx == TrafficStats.UNSUPPORTED || total == TrafficStats.UNSUPPORTED) {
            lastMobileBytes = 0; lastWifiBytes = 0; lastUploadBytes = 0;
        } else {
            lastMobileBytes = rx; lastWifiBytes = total - rx; lastUploadBytes = tx;
        }
        handler.postDelayed(mScreenUpdateRunnable, UPDATE_INTERVAL_MS);
    }

    // =========================================================================
    // Signal Strength
    // =========================================================================

    private void scheduleSignalRefresh() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSignalStrength();
                handler.postDelayed(this, 5_000L);
            }
        }, 1_000L);
    }

    private void updateSignalStrength() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                WifiInfo info = wm.getConnectionInfo();
                int rssi = info.getRssi();
                String bars;
                int color;
                if (rssi >= -55) {
                    bars = "WiFi ▁▃▅▇ Excellent"; color = R.color.signal_good;
                } else if (rssi >= -70) {
                    bars = "WiFi ▁▃▅ Good";       color = R.color.signal_good;
                } else if (rssi >= -80) {
                    bars = "WiFi ▁▃ Fair";         color = R.color.signal_medium;
                } else {
                    bars = "WiFi ▁ Weak";           color = R.color.signal_weak;
                }
                signalStrengthView.setText(bars);
                signalStrengthView.setTextColor(ContextCompat.getColor(this, color));
                return;
            }
            signalStrengthView.setText("Mobile Data");
            signalStrengthView.setTextColor(
                    ContextCompat.getColor(this, R.color.text_secondary_dark));
        } catch (Exception e) {
            signalStrengthView.setText(getString(R.string.signal_na));
        }
    }

    // =========================================================================
    // Speed Test  —  powered by SpeedChecker SDK (285+ global servers)
    // =========================================================================

    private void runSpeedTest() {
        if (isSpeedTestRunning) return;
        isSpeedTestRunning = true;

        btnSpeedTest.setEnabled(false);
        speedTestResultView.setText("");
        speedProgressBar.setVisibility(View.VISIBLE);
        speedProgressBar.setProgress(0);
        speedometerView.setMaxSpeedMb(600f);
        speedometerView.setSpeedMbImmediate(0f);
        speedometerView.setTitleText("Testing");
        speedometerView.setCountdownText("…");

        // SDK picks the nearest server automatically and runs the full test
        SpeedcheckerSDK.SpeedTest.setOnSpeedTestListener(this);
        SpeedcheckerSDK.SpeedTest.startTest(this);
    }

    // ── SpeedTestListener callbacks (all called on main thread by SDK) ────────

    @Override public void onTestStarted() {
        handler.post(() -> speedometerView.setCountdownText("▶"));
    }

    @Override public void onFetchServerFailed(Integer serverId) {
        handler.post(() -> {
            speedometerView.setTitleText("No Server");
            onSpeedTestFinished();
        });
    }

    @Override public void onFindingBestServerStarted() {
        handler.post(() -> {
            speedometerView.setTitleText("Finding Server");
            speedometerView.setCountdownText("↗");
        });
    }

    @Override public void onTestFinished(SpeedTestResult result) {
        handler.post(() -> {
            float dlMBs = result.getDownloadSpeed() / 8f; // Mbps → MB/s
            float ulMBs = result.getUploadSpeed()   / 8f;

            speedometerView.setSpeedMb(dlMBs);
            speedProgressBar.setProgress(100);

            int ping = result.getPing() != null ? result.getPing() : 0;
            speedTestResultView.setText(String.format(Locale.getDefault(),
                    "↓ %.2f MB/s   ↑ %.2f MB/s   Ping %d ms",
                    dlMBs, ulMBs, ping));

            // Save to Room DB
            executorService.execute(() -> {
                com.example.internetspeedmeeter.SpeedTestResult r =
                        new com.example.internetspeedmeeter.SpeedTestResult(
                                System.currentTimeMillis(), dlMBs, 30);
                AppDatabase.getInstance(MainActivity.this).speedTestDao().insert(r);
            });

            onSpeedTestFinished();
        });
    }

    @Override public void onPingStarted() {}

    @Override public void onPingFinished(int ping, int jitter) {
        handler.post(() -> {
            lastPingMs = ping;
            speedTestResultView.setText("Ping: " + ping + " ms");
        });
    }

    @Override public void onDownloadTestStarted() {
        handler.post(() -> {
            speedometerView.setTitleText("Download");
            speedometerView.setCountdownText("↓");
        });
    }

    @Override public void onDownloadTestProgress(int percent, double speedMbs, double avgSpeedMbs) {
        handler.post(() -> {
            speedProgressBar.setProgress(percent / 2); // download = first 50% of bar
            speedometerView.setSpeedMb((float) speedMbs);
        });
    }

    @Override public void onDownloadTestFinished(double avgSpeedMbs) {
        handler.post(() -> {
            speedometerView.setTitleText("Upload");
            speedometerView.setCountdownText("↑");
            speedometerView.setSpeedMbImmediate(0f);
        });
    }

    @Override public void onUploadTestStarted() { /* handled in onDownloadTestFinished */ }

    @Override public void onUploadTestProgress(int percent, double speedMbs, double avgSpeedMbs) {
        handler.post(() -> {
            speedProgressBar.setProgress(50 + percent / 2); // upload = last 50% of bar
            speedometerView.setSpeedMb((float) speedMbs);
        });
    }

    @Override public void onUploadTestFinished(double avgSpeedMbs) { /* shown in onTestFinished */ }

    @Override public void onTestWarning(String warning) { }

    @Override public void onTestFatalError(String error) {
        handler.post(() -> {
            speedometerView.setTitleText("Test Error");
            onSpeedTestFinished();
        });
    }

    @Override public void onTestInterrupted(String reason) {
        handler.post(() -> {
            speedometerView.setTitleText("Interrupted");
            onSpeedTestFinished();
        });
    }



    private void onSpeedTestFinished() {
        isSpeedTestRunning = false;
        btnSpeedTest.setEnabled(true);
        speedProgressBar.setVisibility(View.GONE);
        speedometerView.setCountdownText("");
    }

    // =========================================================================
    // Network Quality Score
    // =========================================================================

    /**
     * Blends download speed (50%), ping (30%), and a stability constant (20%)
     * into a 0-100 quality score.
     *
     * @param bytesPerSec current total bytes/sec (rx+tx)
     * @param pingMs      last known ping in milliseconds
     */
    private int computeQualityScore(long bytesPerSec, long pingMs) {
        // Speed component: cap at 50 MB/s → 100 pts
        double speedMb = bytesPerSec / (1024.0 * 1024.0);
        double speedScore = Math.min(100.0, speedMb * 2.0);

        // Ping component: ≤20ms=100, ≥500ms=0
        double pingScore = Math.max(0.0, 100.0 - ((pingMs - 20) * 100.0 / 480.0));

        // Blend
        return (int) Math.round(speedScore * 0.6 + pingScore * 0.4);
    }
}

