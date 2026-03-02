package com.example.internetspeedmeeter;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gaming Mode Dashboard
 * Shows real-time: Ping, Jitter, Packet Loss, and a Gaming Readiness score.
 * Uses TCP socket connect to google DNS (8.8.8.8:80) every 500ms.
 */
public class GamingModeActivity extends AppCompatActivity {

    private static final int PING_INTERVAL_MS = 500;
    private static final int HISTORY_SIZE     = 20;   // last 20 pings for jitter
    private static final String PING_HOST     = "8.8.8.8";
    private static final int    PING_PORT     = 53;
    private static final int    PING_TIMEOUT  = 2_000;

    private final Handler         handler  = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Deque<Long>     history  = new ArrayDeque<>();

    private int totalPings   = 0;
    private int failedPings  = 0;

    private TextView tvCurrentPing;
    private TextView tvJitter;
    private TextView tvPacketLoss;
    private TextView tvReadiness;
    private TextView tvReadinessLabel;
    private TextView tvLive;

    private boolean blinkOn = true;

    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            executor.execute(() -> {
                totalPings++;
                long ms = measurePing();
                runOnUiThread(() -> onPingResult(ms));
                handler.postDelayed(this, PING_INTERVAL_MS);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gaming_mode);

        tvCurrentPing    = findViewById(R.id.tvGamingPing);
        tvJitter         = findViewById(R.id.tvGamingJitter);
        tvPacketLoss     = findViewById(R.id.tvGamingPacketLoss);
        tvReadiness      = findViewById(R.id.tvGamingReadiness);
        tvReadinessLabel = findViewById(R.id.tvGamingReadinessLabel);
        tvLive           = findViewById(R.id.tvGamingLive);

        handler.postDelayed(pingRunnable, 200);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    private void onPingResult(long ms) {
        // Blink live indicator
        blinkOn = !blinkOn;
        tvLive.setAlpha(blinkOn ? 1f : 0.3f);

        if (ms < 0) {
            failedPings++;
            tvCurrentPing.setText("Timeout");
            tvCurrentPing.setTextColor(ContextCompat.getColor(this, R.color.meter_red));
        } else {
            // Pulse animation
            ObjectAnimator pulse = ObjectAnimator.ofFloat(tvCurrentPing, "scaleX", 1f, 1.08f, 1f);
            pulse.setDuration(250).start();
            ObjectAnimator pulseY = ObjectAnimator.ofFloat(tvCurrentPing, "scaleY", 1f, 1.08f, 1f);
            pulseY.setDuration(250).start();

            tvCurrentPing.setText(ms + " ms");
            tvCurrentPing.setTextColor(pingColor(ms));

            // Maintain rolling history
            history.addLast(ms);
            if (history.size() > HISTORY_SIZE) history.pollFirst();
        }

        // Jitter = standard deviation of last N pings
        tvJitter.setText(calcJitter() + " ms");

        // Packet loss %
        int lossPct = totalPings > 0 ? (failedPings * 100 / totalPings) : 0;
        tvPacketLoss.setText(lossPct + "%");
        tvPacketLoss.setTextColor(lossPct > 10
                ? ContextCompat.getColor(this, R.color.meter_red)
                : ContextCompat.getColor(this, R.color.meter_green));

        // Readiness score
        updateReadiness(ms < 0 ? 999 : ms, calcJitter(), lossPct);
    }

    private void updateReadiness(long pingMs, long jitterMs, int lossPct) {
        if (pingMs <= 40 && jitterMs <= 10 && lossPct == 0) {
            tvReadiness.setText("✅");
            tvReadinessLabel.setText("Ready to Game!");
            tvReadinessLabel.setTextColor(ContextCompat.getColor(this, R.color.meter_green));
        } else if (pingMs <= 100 && jitterMs <= 30 && lossPct <= 5) {
            tvReadiness.setText("⚠️");
            tvReadinessLabel.setText("Playable");
            tvReadinessLabel.setTextColor(ContextCompat.getColor(this, R.color.swatch_yellow));
        } else {
            tvReadiness.setText("❌");
            tvReadinessLabel.setText("Poor Connection");
            tvReadinessLabel.setTextColor(ContextCompat.getColor(this, R.color.meter_red));
        }
    }

    private long calcJitter() {
        if (history.size() < 2) return 0;
        long sum = 0;
        for (long v : history) sum += v;
        double mean = sum / (double) history.size();
        double variance = 0;
        for (long v : history) variance += (v - mean) * (v - mean);
        return (long) Math.sqrt(variance / history.size());
    }

    private int pingColor(long ms) {
        if (ms <= 40)  return ContextCompat.getColor(this, R.color.meter_green);
        if (ms <= 100) return ContextCompat.getColor(this, R.color.swatch_yellow);
        return ContextCompat.getColor(this, R.color.meter_red);
    }

    private long measurePing() {
        try (Socket s = new Socket()) {
            long start = System.currentTimeMillis();
            s.connect(new InetSocketAddress(PING_HOST, PING_PORT), PING_TIMEOUT);
            return System.currentTimeMillis() - start;
        } catch (IOException e) {
            return -1;
        }
    }
}
