package com.example.internetspeedmeeter;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppDataUsageAdapter
        extends RecyclerView.Adapter<AppDataUsageAdapter.ViewHolder> {

    public interface OnRestrictClickListener {
        void onRestrict(String packageName);
    }

    private OnRestrictClickListener restrictListener;

    public void setOnRestrictClickListener(OnRestrictClickListener l) {
        this.restrictListener = l;
    }

    public static class AppEntry {
        public final String   packageName;
        public final String   appName;
        public final Drawable icon;
        public final long     rxBytes;
        public final long     txBytes;
        public final int      progressPercent;
        public final boolean  activeNow;  // true = downloading right now (live mode)

        public AppEntry(String packageName, String appName, Drawable icon,
                        long rxBytes, long txBytes, int progressPercent) {
            this(packageName, appName, icon, rxBytes, txBytes, progressPercent, false);
        }

        public AppEntry(String packageName, String appName, Drawable icon,
                        long rxBytes, long txBytes, int progressPercent, boolean activeNow) {
            this.packageName      = packageName;
            this.appName          = appName;
            this.icon             = icon;
            this.rxBytes          = rxBytes;
            this.txBytes          = txBytes;
            this.progressPercent  = progressPercent;
            this.activeNow        = activeNow;
        }

        public long totalBytes() { return rxBytes + txBytes; }
    }

    private final List<AppEntry> entries;

    public AppDataUsageAdapter(List<AppEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /** Replace all entries (used by live mode refresh). */
    public void updateEntries(List<AppEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_usage, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        AppEntry e = entries.get(position);

        if (e.icon != null) h.icon.setImageDrawable(e.icon);
        h.name.setText(e.appName);
        h.data.setText(SpeedUtils.formatDataUsage(e.totalBytes()));
        h.rx.setText("↓ " + SpeedUtils.formatDataUsage(e.rxBytes));
        h.tx.setText("↑ " + SpeedUtils.formatDataUsage(e.txBytes));
        h.rank.setText("#" + (position + 1));

        // Highlight active apps in live mode
        if (e.activeNow) {
            h.itemView.setBackgroundColor(0x33FF4444); // semi-transparent red glow
            if (h.tvLive != null) h.tvLive.setVisibility(View.VISIBLE);
        } else {
            h.itemView.setBackgroundColor(0x00000000);
            if (h.tvLive != null) h.tvLive.setVisibility(View.GONE);
        }

        // Animate progress bar
        h.bar.setProgress(0);
        h.bar.postDelayed(() -> {
            android.animation.ObjectAnimator anim = android.animation.ObjectAnimator
                    .ofInt(h.bar, "progress", 0, e.progressPercent);
            anim.setDuration(600);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.start();
        }, position * 40L);

        // Restrict button
        if (h.btnRestrict != null) {
            h.btnRestrict.setOnClickListener(v -> {
                if (restrictListener != null) restrictListener.onRestrict(e.packageName);
            });
        }
    }

    @Override
    public int getItemCount() { return entries.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView   icon;
        final TextView    name, data, rx, tx, rank;
        final ProgressBar bar;
        final TextView    tvLive;
        final View        btnRestrict;

        ViewHolder(@NonNull View v) {
            super(v);
            icon       = v.findViewById(R.id.itemAppIcon);
            name       = v.findViewById(R.id.itemAppName);
            data       = v.findViewById(R.id.itemAppData);
            rx         = v.findViewById(R.id.itemAppRx);
            tx         = v.findViewById(R.id.itemAppTx);
            rank       = v.findViewById(R.id.itemAppRank);
            bar        = v.findViewById(R.id.itemAppBar);
            tvLive     = v.findViewById(R.id.itemAppLiveDot);
            btnRestrict = v.findViewById(R.id.btnRestrictApp);
        }
    }
}
