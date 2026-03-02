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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live Ping / Latency Monitor.
 * Pings 6 popular servers every 3 seconds using TCP socket connect.
 * Shows live current ping, min, avg, max, and per-server results with colour coding.
 */
public class PingActivity extends AppCompatActivity {

    private static final int PING_INTERVAL_MS = 3_000;
    private static final int PING_TIMEOUT_MS  = 3_000;
    private static final int PING_PORT        = 53;

    private final Handler         handler  = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    // --- Views ---
    private TextView    tvCurrentMs;
    private TextView    tvCurrentServer;
    private TextView    tvQuality;
    private TextView    tvMin, tvAvg, tvMax;
    private TextView    tvLiveIndicator;

    // --- Stats ---
    private long pingsTotal = 0;
    private long pingsSum   = 0;
    private long pingsMin   = Long.MAX_VALUE;
    private long pingsMax   = 0;

    // --- Data ---
    private List<PingAdapter.ServerEntry> servers;
    private PingAdapter adapter;
    private int currentServerIndex = 0;

    // --- Live dot blink ---
    private boolean blinkState = true;

    private final Runnable pingCycle = new Runnable() {
        @Override
        public void run() {
            pingAllServers();
            blinkLiveIndicator();
            handler.postDelayed(this, PING_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ping);

        bindViews();
        setupServerList();
        handler.postDelayed(pingCycle, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    private void bindViews() {
        tvCurrentMs     = findViewById(R.id.pingCurrentMs);
        tvCurrentServer = findViewById(R.id.pingCurrentServer);
        tvQuality       = findViewById(R.id.pingQualityLabel);
        tvMin           = findViewById(R.id.pingMin);
        tvAvg           = findViewById(R.id.pingAvg);
        tvMax           = findViewById(R.id.pingMax);
        tvLiveIndicator = findViewById(R.id.pingLiveIndicator);
    }

    private void setupServerList() {
        servers = new ArrayList<>();
        servers.add(new PingAdapter.ServerEntry("8.8.8.8",          "Google",     "8.8.8.8 — Google DNS"));
        servers.add(new PingAdapter.ServerEntry("1.1.1.1",          "Cloudflare", "1.1.1.1 — Cloudflare DNS"));
        servers.add(new PingAdapter.ServerEntry("208.67.222.222",   "OpenDNS",    "208.67.222.222 — OpenDNS"));
        servers.add(new PingAdapter.ServerEntry("52.94.236.248",    "Amazon",     "AWS — ap-south-1"));
        servers.add(new PingAdapter.ServerEntry("157.240.200.35",   "Meta",       "Meta / Facebook servers"));
        servers.add(new PingAdapter.ServerEntry("142.250.182.238",  "YouTube",    "Google/YouTube CDN"));

        adapter = new PingAdapter(servers);
        RecyclerView rv = findViewById(R.id.pingRecyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    private void pingAllServers() {
        for (int i = 0; i < servers.size(); i++) {
            final int idx = i;
            final PingAdapter.ServerEntry entry = servers.get(idx);

            executor.execute(() -> {
                long ms = measurePing(entry.host);
                entry.pingMs = ms;

                runOnUiThread(() -> {
                    adapter.notifyItemChanged(idx);

                    // Update big display with the rotating current server
                    if (idx == currentServerIndex) {
                        updateBigDisplay(entry, ms);
                    }

                    // Update global stats (skip timeouts from avg/min)
                    if (ms > 0 && ms != Long.MAX_VALUE) {
                        pingsTotal++;
                        pingsSum += ms;
                        if (ms < pingsMin) pingsMin = ms;
                        if (ms > pingsMax) pingsMax = ms;
                        updateStats();
                    }

                    // Rotate which server is featured every cycle
                    if (idx == servers.size() - 1) {
                        currentServerIndex = (currentServerIndex + 1) % servers.size();
                    }
                });
            });
        }
    }

    private void updateBigDisplay(PingAdapter.ServerEntry entry, long ms) {
        tvCurrentServer.setText("→ " + entry.name + " (" + entry.host + ")");

        if (ms == Long.MAX_VALUE) {
            tvCurrentMs.setText("Timeout");
            tvCurrentMs.setTextColor(ContextCompat.getColor(this, R.color.meter_red));
            tvQuality.setText("🔴 Connection Failed");
            tvQuality.setTextColor(ContextCompat.getColor(this, R.color.meter_red));
        } else {
            tvCurrentMs.setText(ms + " ms");
            int color = PingAdapter.pingColor(ms, this);
            tvCurrentMs.setTextColor(color);

            // Pulse animation on value change
            ObjectAnimator pulse = ObjectAnimator.ofFloat(tvCurrentMs, "scaleX", 1f, 1.12f, 1f);
            pulse.setDuration(300);
            pulse.start();
            ObjectAnimator pulseY = ObjectAnimator.ofFloat(tvCurrentMs, "scaleY", 1f, 1.12f, 1f);
            pulseY.setDuration(300);
            pulseY.start();

            tvQuality.setText(PingAdapter.pingQualityLabel(ms));
            tvQuality.setTextColor(color);
        }
    }

    private void updateStats() {
        long avg = pingsTotal > 0 ? pingsSum / pingsTotal : 0;

        tvMin.setText(pingsMin == Long.MAX_VALUE ? "-- ms" : pingsMin + " ms");
        tvAvg.setText(avg > 0 ? avg + " ms" : "-- ms");
        tvMax.setText(pingsMax > 0 ? pingsMax + " ms" : "-- ms");

        tvMin.setTextColor(ContextCompat.getColor(this, R.color.meter_green));
        tvAvg.setTextColor(ContextCompat.getColor(this, R.color.accent_color));
        tvMax.setTextColor(ContextCompat.getColor(this, R.color.meter_red));
    }

    private void blinkLiveIndicator() {
        blinkState = !blinkState;
        tvLiveIndicator.setAlpha(blinkState ? 1f : 0.3f);
    }

    /**
     * Measures TCP connect latency to host:80.
     * Returns milliseconds, or Long.MAX_VALUE on timeout/failure.
     */
    private long measurePing(String host) {
        try (Socket socket = new Socket()) {
            long start = System.currentTimeMillis();
            socket.connect(new InetSocketAddress(host, PING_PORT), PING_TIMEOUT_MS);
            return System.currentTimeMillis() - start;
        } catch (IOException e) {
            return Long.MAX_VALUE; // timeout or unreachable
        }
    }
}
