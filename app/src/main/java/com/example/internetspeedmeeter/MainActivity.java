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
    private TextView        downloadSpeedView;
    private TextView        uploadSpeedView;
    private TextView        mobileDataUsageView;
    private TextView        wifiDataUsageView;
    private TextView        signalStrengthView;
    private Button          btnSpeedTest;
    private Button          btnReset;
    private ProgressBar     speedProgressBar;
    private SpeedometerView speedometerView;
    private CardView           speedometerCardView;
    private TextView           speedTestResultView;
    private CardView           speedTestResultCard;
    private NetworkQualityView networkQualityView;
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
            if (!isSpeedTestRunning) {
                downloadSpeedView.setText("↓ " + SpeedUtils.formatSpeed(rxDiff));
            }
            uploadSpeedView.setText("↑ " + SpeedUtils.formatSpeed(txDiff));

            // Update Network Quality Score
            int qualityScore = computeQualityScore(rxDiff + txDiff, lastPingMs);
            networkQualityView.setScore(qualityScore);

            mobileDataUsageView.setText(getString(R.string.mobile_data_usage,
                    SpeedUtils.formatDataUsage(totalMobileBytes)));
            wifiDataUsageView.setText(getString(R.string.wifi_data_usage,
                    SpeedUtils.formatDataUsage(totalWifiBytes)));

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
        downloadSpeedView   = findViewById(R.id.speedTextView);
        uploadSpeedView     = findViewById(R.id.uploadSpeedView);
        mobileDataUsageView = findViewById(R.id.mobileDataUsageTextView);
        wifiDataUsageView   = findViewById(R.id.wifiDataUsageTextView);
        signalStrengthView  = findViewById(R.id.signalStrengthView);
        btnSpeedTest        = findViewById(R.id.btnSpeedTest);
        btnReset            = findViewById(R.id.btnReset);
        speedProgressBar    = findViewById(R.id.speedProgressBar);
        speedometerView     = findViewById(R.id.speedometerView);
        speedometerCardView = findViewById(R.id.speedometerCardView);
        speedTestResultView = findViewById(R.id.speedTestResultView);
        speedTestResultCard = findViewById(R.id.speedTestResultCard);
        networkQualityView  = findViewById(R.id.networkQualityView);
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
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                overridePendingTransition(0, 0);
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
        btnReset.setOnClickListener(v -> resetSession());

        // Feature grid cards
        rippleCard(R.id.cardPing,      () -> startActivity(new Intent(this, PingActivity.class)));
        rippleCard(R.id.cardAppUsage,  () -> startActivity(new Intent(this, AppDataUsageActivity.class)));
        rippleCard(R.id.cardDataPlan,  () -> startActivity(new Intent(this, DataPlanActivity.class)));
        rippleCard(R.id.cardThrottle,  () -> startActivity(new Intent(this, ThrottleTestActivity.class)));
        rippleCard(R.id.cardGaming,    () -> startActivity(new Intent(this, GamingModeActivity.class)));
        rippleCard(R.id.cardHistory,   () -> startActivity(new Intent(this, HistoryActivity.class)));
    }

    /** Adds a click listener + scale animation to a CardView feature tile. */
    private void rippleCard(int cardId, Runnable action) {
        android.view.View card = findViewById(cardId);
        if (card == null) return;
        card.setOnClickListener(v -> {
            v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction(action).start()
            ).start();
        });
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
        mobileDataUsageView.setText(getString(R.string.mobile_data_usage,
                SpeedUtils.formatDataUsage(totalMobileBytes)));
        wifiDataUsageView.setText(getString(R.string.wifi_data_usage,
                SpeedUtils.formatDataUsage(totalWifiBytes)));
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
        mobileDataUsageView.setText(getString(R.string.mobile_data_usage, "0 KB"));
        wifiDataUsageView.setText(getString(R.string.wifi_data_usage, "0 KB"));
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
        downloadSpeedView.setText(getString(R.string.speed_test_testing));
        speedometerCardView.setVisibility(View.VISIBLE);
        speedTestResultCard.setVisibility(View.GONE);
        speedProgressBar.setVisibility(View.VISIBLE);
        speedProgressBar.setProgress(0);
        speedometerView.setMaxSpeedMb(600f);
        speedometerView.setSpeedMbImmediate(0f);
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
            downloadSpeedView.setText("⚠️ No server found");
            onSpeedTestFinished();
        });
    }

    @Override public void onFindingBestServerStarted() {
        handler.post(() -> {
            downloadSpeedView.setText("Finding best server…");
            speedometerView.setCountdownText("↗");
        });
    }

    @Override public void onTestFinished(SpeedTestResult result) {
        handler.post(() -> {
            float dlMBs = result.getDownloadSpeed() / 8f; // Mbps → MB/s
            float ulMBs = result.getUploadSpeed()   / 8f;

            speedometerView.setSpeedMb(dlMBs);
            speedProgressBar.setProgress(100);

            downloadSpeedView.setText(String.format(Locale.getDefault(),
                    "↓ %.2f MB/s", dlMBs));
            int ping = result.getPing() != null ? result.getPing() : 0;
            speedTestResultView.setText(String.format(Locale.getDefault(),
                    "↓ %.2f MB/s   ↑ %.2f MB/s   Ping %d ms",
                    dlMBs, ulMBs, ping));
            speedTestResultCard.setVisibility(View.VISIBLE);

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
            downloadSpeedView.setText("Server Ping: " + ping + " ms");
        });
    }

    @Override public void onDownloadTestStarted() {
        handler.post(() -> {
            downloadSpeedView.setText("Downloading…");
            speedometerView.setCountdownText("↓");
        });
    }

    @Override public void onDownloadTestProgress(int percent, double speedMbs, double avgSpeedMbs) {
        handler.post(() -> {
            speedProgressBar.setProgress(percent / 2); // download = first 50% of bar
            speedometerView.setSpeedMb((float) speedMbs);
            downloadSpeedView.setText(String.format(Locale.getDefault(),
                    "↓ %.1f MB/s  (avg %.1f)", speedMbs, avgSpeedMbs));
        });
    }

    @Override public void onDownloadTestFinished(double avgSpeedMbs) {
        handler.post(() -> {
            speedometerView.setCountdownText("↑");
            downloadSpeedView.setText(String.format(Locale.getDefault(),
                    "↓ %.2f MB/s ✓  Testing upload…", avgSpeedMbs));
        });
    }

    @Override public void onUploadTestStarted() { /* handled in onDownloadTestFinished */ }

    @Override public void onUploadTestProgress(int percent, double speedMbs, double avgSpeedMbs) {
        handler.post(() -> {
            speedProgressBar.setProgress(50 + percent / 2); // upload = last 50% of bar
            speedometerView.setSpeedMb((float) speedMbs);
            downloadSpeedView.setText(String.format(Locale.getDefault(),
                    "↑ %.1f MB/s  (avg %.1f)", speedMbs, avgSpeedMbs));
        });
    }

    @Override public void onUploadTestFinished(double avgSpeedMbs) { /* shown in onTestFinished */ }

    @Override public void onTestWarning(String warning) { }

    @Override public void onTestFatalError(String error) {
        handler.post(() -> {
            downloadSpeedView.setText("Test error: " + error);
            onSpeedTestFinished();
        });
    }

    @Override public void onTestInterrupted(String reason) {
        handler.post(() -> {
            downloadSpeedView.setText("Test interrupted: " + reason);
            onSpeedTestFinished();
        });
    }



    private void onSpeedTestFinished() {
        isSpeedTestRunning = false;
        btnSpeedTest.setEnabled(true);
        speedProgressBar.setVisibility(View.GONE);
        speedometerView.setCountdownText("");
        handler.postDelayed(() -> speedometerCardView.setVisibility(View.GONE), 4_000L);
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

