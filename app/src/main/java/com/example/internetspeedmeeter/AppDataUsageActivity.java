package com.example.internetspeedmeeter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-App Data Usage — with LIVE mode.
 * In live mode, refreshes every 2 seconds and highlights apps actively downloading.
 */
public class AppDataUsageActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler         handler  = new Handler(Looper.getMainLooper());
    private       boolean         liveMode = false;
    private       AppDataUsageAdapter adapter;

    // Snapshot of rx bytes per UID for delta calculation
    private final java.util.HashMap<Integer, Long> prevRxMap = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_data_usage);

        RecyclerView rv      = findViewById(R.id.appUsageRecyclerView);
        ProgressBar  loading = findViewById(R.id.appUsageLoading);
        Switch       liveSwitch = findViewById(R.id.switchLiveMode);
        TextView     tvLiveLabel = findViewById(R.id.tvLiveLabel);

        rv.setLayoutManager(new LinearLayoutManager(this));

        // Initial load
        executor.execute(() -> {
            List<AppDataUsageAdapter.AppEntry> entries = loadAppUsage();
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
                adapter = new AppDataUsageAdapter(entries);
                adapter.setOnRestrictClickListener(pkg -> openDataRestriction(pkg));
                rv.setAdapter(adapter);
            });
        });

        if (liveSwitch != null) {
            liveSwitch.setOnCheckedChangeListener((btn, checked) -> {
                liveMode = checked;
                if (tvLiveLabel != null) {
                    tvLiveLabel.setText(checked ? "● LIVE" : "Static");
                    tvLiveLabel.setTextColor(checked
                            ? getColor(R.color.meter_green)
                            : getColor(R.color.text_secondary_dark));
                }
                if (checked) scheduleLiveRefresh();
            });
        }
    }

    private void scheduleLiveRefresh() {
        if (!liveMode) return;
        handler.postDelayed(() -> {
            if (!liveMode) return;
            executor.execute(() -> {
                List<AppDataUsageAdapter.AppEntry> entries = loadAppUsageLive();
                runOnUiThread(() -> {
                    if (adapter != null) adapter.updateEntries(entries);
                });
            });
            scheduleLiveRefresh();
        }, 2_000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        liveMode = false;
        handler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    private List<AppDataUsageAdapter.AppEntry> loadAppUsage() {
        return buildEntries(false);
    }

    private List<AppDataUsageAdapter.AppEntry> loadAppUsageLive() {
        return buildEntries(true);
    }

    private List<AppDataUsageAdapter.AppEntry> buildEntries(boolean live) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppDataUsageAdapter.AppEntry> raw = new ArrayList<>();

        for (ApplicationInfo info : apps) {
            int   uid      = info.uid;
            long  rxBytes  = TrafficStats.getUidRxBytes(uid);
            long  txBytes  = TrafficStats.getUidTxBytes(uid);

            if (rxBytes <= 0 && txBytes <= 0) continue;
            if (rxBytes == TrafficStats.UNSUPPORTED
                    || txBytes == TrafficStats.UNSUPPORTED) continue;

            String appName;
            android.graphics.drawable.Drawable icon;
            try {
                appName = pm.getApplicationLabel(info).toString();
                icon    = pm.getApplicationIcon(info.packageName);
            } catch (PackageManager.NameNotFoundException e) {
                appName = info.packageName;
                icon    = null;
            }

            // Live delta
            boolean activeNow = false;
            if (live) {
                Long prev = prevRxMap.get(uid);
                if (prev != null && rxBytes - prev > 0) {
                    activeNow = true;
                }
                prevRxMap.put(uid, rxBytes);
            }

            raw.add(new AppDataUsageAdapter.AppEntry(
                    info.packageName, appName, icon,
                    Math.max(0, rxBytes), Math.max(0, txBytes), 0, activeNow));
        }

        Collections.sort(raw, (a, b) -> Long.compare(b.totalBytes(), a.totalBytes()));
        if (raw.size() > 50) raw = raw.subList(0, 50);

        long maxBytes = raw.isEmpty() ? 1L : raw.get(0).totalBytes();
        List<AppDataUsageAdapter.AppEntry> result = new ArrayList<>();
        for (AppDataUsageAdapter.AppEntry e : raw) {
            int pct = (int)(e.totalBytes() * 100L / maxBytes);
            result.add(new AppDataUsageAdapter.AppEntry(
                    e.packageName, e.appName, e.icon,
                    e.rxBytes, e.txBytes, pct, e.activeNow));
        }
        return result;
    }

    private void openDataRestriction(String packageName) {
        try {
            Intent intent = new Intent();
            intent.setAction("android.settings.APP_DATA_USAGE_SETTINGS");
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            // Fallback to app info
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }
    }
}
