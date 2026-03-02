package com.example.internetspeedmeeter;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ISP Throttle Detector
 *
 * Downloads a small file from 3 different CDNs and compares sustained speeds.
 * If speeds vary significantly across providers it suggests throttling.
 */
public class ThrottleTestActivity extends AppCompatActivity {

    private static final int    CONNECT_TIMEOUT_MS = 8_000;
    private static final int    READ_TIMEOUT_MS    = 12_000;
    private static final int    TEST_DURATION_MS   = 8_000;
    private static final int    BUFFER             = 8_192;

    private static final String[] CDN_URLS = {
            "https://speed.cloudflare.com/__down?bytes=10000000",
            "https://proof.ovh.net/files/10Mb.dat",
            "https://cachefly.cachefly.net/10mb.test"
    };
    private static final String[] CDN_NAMES = { "Cloudflare", "OVH", "CacheFly" };

    private final Handler         handler  = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    // Track result per CDN: positive = MB/s, 0 = timeout, -1 = error
    private final double[] speeds   = new double[3];
    private final String[] statuses = new String[3];   // display label per CDN

    private TextView  tvResult;
    private TextView  tvDetail;
    private ProgressBar progressBar;
    private TextView  tvStatus;
    private View      btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_throttle_test);

        tvResult    = findViewById(R.id.tvThrottleResult);
        tvDetail    = findViewById(R.id.tvThrottleDetail);
        progressBar = findViewById(R.id.throttleProgress);
        tvStatus    = findViewById(R.id.tvThrottleStatus);
        btnStart    = findViewById(R.id.btnStartThrottleTest);

        btnStart.setOnClickListener(v -> startTest());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }

    private void startTest() {
        btnStart.setEnabled(false);
        tvResult.setVisibility(View.GONE);
        tvDetail.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);

        for (int i = 0; i < CDN_NAMES.length; i++) {
            speeds[i]   = -2;     // -2 = not yet run
            statuses[i] = "…";
        }

        // Animate progress bar over total expected duration
        long totalMs = (long) CDN_NAMES.length * (TEST_DURATION_MS + 500);
        ObjectAnimator prog = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        prog.setDuration(totalMs);
        prog.start();

        // Run each CDN test sequentially (staggered by TEST_DURATION_MS)
        for (int i = 0; i < CDN_URLS.length; i++) {
            final int idx = i;
            handler.postDelayed(() -> {
                tvStatus.setText("Testing " + CDN_NAMES[idx] + "…");
                Future<Double> future = executor.submit(() -> measureSpeed(CDN_URLS[idx]));
                executor.execute(() -> {
                    try {
                        double result = future.get(TEST_DURATION_MS + CONNECT_TIMEOUT_MS + 2000L,
                                TimeUnit.MILLISECONDS);
                        speeds[idx]   = result;
                        statuses[idx] = result > 0
                                ? String.format(java.util.Locale.getDefault(), "%.2f MB/s", result)
                                : "Error";
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        speeds[idx]   = 0;
                        statuses[idx] = "Timeout";
                    } catch (Exception e) {
                        speeds[idx]   = -1;
                        statuses[idx] = "Unreachable";
                    }
                    if (idx == CDN_NAMES.length - 1) {
                        handler.post(this::showResult);
                    }
                });
            }, (long) idx * (TEST_DURATION_MS + 500));
        }
    }

    private double measureSpeed(String urlStr) {
        long totalBytes = 0;
        long start = System.currentTimeMillis();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return -1;
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[BUFFER];
                int read;
                while ((read = is.read(buf)) != -1) {
                    totalBytes += read;
                    if (System.currentTimeMillis() - start >= TEST_DURATION_MS) break;
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            return -1;
        }
        long elapsed = Math.max(1, System.currentTimeMillis() - start);
        return (totalBytes / (double) elapsed) * 1000.0 / (1024.0 * 1024.0);
    }

    private void showResult() {
        progressBar.setVisibility(View.GONE);
        tvStatus.setText("Test complete");

        // Build detail lines
        StringBuilder detail = new StringBuilder();
        double max = 0, min = Double.MAX_VALUE;
        int validCount = 0;

        for (int i = 0; i < CDN_NAMES.length; i++) {
            String icon;
            double s = speeds[i];
            if (s > 0) {
                icon = "✅ ";
                validCount++;
                if (s > max) max = s;
                if (s < min) min = s;
            } else if (s == 0) {
                icon = "⏱ ";   // timeout
            } else {
                icon = "⚠️ ";  // unreachable
            }
            detail.append(icon)
                  .append(CDN_NAMES[i])
                  .append(":  ")
                  .append(statuses[i])
                  .append("\n");
        }

        tvDetail.setText(detail.toString().trim());
        tvDetail.setVisibility(View.VISIBLE);
        tvResult.setVisibility(View.VISIBLE);

        if (validCount < 2) {
            tvResult.setText("⚠️ Inconclusive\n(Not enough servers responded)");
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.alert_orange));
            btnStart.setEnabled(true);
            return;
        }

        // Throttling heuristic: >50% spread between fastest & slowest valid server
        double spread = (max - min) / max;
        if (spread > 0.50) {
            tvResult.setText("⛔ Throttling Likely Detected\n"
                    + String.format(java.util.Locale.getDefault(),
                        "(%.0f%% speed difference between CDNs)", spread * 100));
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.meter_red));
        } else {
            tvResult.setText("✅ No Throttling Detected\n"
                    + String.format(java.util.Locale.getDefault(),
                        "(%.0f%% spread — within normal range)", spread * 100));
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.meter_green));
        }

        btnStart.setEnabled(true);
    }
}
