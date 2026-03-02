package com.example.internetspeedmeeter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PingAdapter extends RecyclerView.Adapter<PingAdapter.ViewHolder> {

    public static class ServerEntry {
        public final String host;
        public final String name;
        public final String description;
        public long   pingMs   = -1; // -1 = not yet measured

        public ServerEntry(String host, String name, String description) {
            this.host        = host;
            this.name        = name;
            this.description = description;
        }
    }

    private final List<ServerEntry> servers;

    public PingAdapter(List<ServerEntry> servers) {
        this.servers = servers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ping_server, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ServerEntry e = servers.get(position);
        h.server.setText(e.name);
        h.desc.setText(e.description);

        if (e.pingMs < 0) {
            h.ms.setText("-- ms");
            h.ms.setTextColor(ContextCompat.getColor(h.ms.getContext(),
                    R.color.text_secondary_dark));
            h.dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(h.dot.getContext(), R.color.text_secondary_dark)));
        } else if (e.pingMs == Long.MAX_VALUE) {
            h.ms.setText("Timeout");
            h.ms.setTextColor(ContextCompat.getColor(h.ms.getContext(), R.color.meter_red));
            h.dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(h.dot.getContext(), R.color.meter_red)));
        } else {
            h.ms.setText(e.pingMs + " ms");
            int color = pingColor(e.pingMs, h.ms.getContext());
            h.ms.setTextColor(color);
            h.dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    @Override
    public int getItemCount() { return servers.size(); }

    /** Color codes: green < 50ms, yellow < 100ms, orange < 200ms, red >= 200ms */
    public static int pingColor(long ms, android.content.Context ctx) {
        if (ms < 50)  return ContextCompat.getColor(ctx, R.color.meter_green);
        if (ms < 100) return Color.parseColor("#FFC107");  // amber
        if (ms < 200) return Color.parseColor("#FF9800");  // orange
        return ContextCompat.getColor(ctx, R.color.meter_red);
    }

    public static String pingQualityLabel(long ms) {
        if (ms < 0)   return "Measuring...";
        if (ms < 50)  return "🟢 Excellent";
        if (ms < 100) return "🟡 Good";
        if (ms < 200) return "🟠 Fair";
        return "🔴 Poor";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView server, desc, ms, dot;
        ViewHolder(@NonNull View v) {
            super(v);
            server = v.findViewById(R.id.pingItemServer);
            desc   = v.findViewById(R.id.pingItemDesc);
            ms     = v.findViewById(R.id.pingItemMs);
            dot    = v.findViewById(R.id.pingItemDot);
        }
    }
}
