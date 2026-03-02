package com.example.internetspeedmeeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpeedTestResultAdapter
        extends RecyclerView.Adapter<SpeedTestResultAdapter.ViewHolder> {

    private final List<SpeedTestResult> items;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault());

    public SpeedTestResultAdapter(List<SpeedTestResult> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_speed_test_result, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        SpeedTestResult r = items.get(position);
        h.speed.setText(String.format(Locale.getDefault(), "%.2f MB/s", r.speedMbps));
        h.date.setText(DATE_FMT.format(new Date(r.timestamp)));
        h.duration.setText(r.durationSec + "s");
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView speed, date, duration;
        ViewHolder(@NonNull View v) {
            super(v);
            speed    = v.findViewById(R.id.itemSpeed);
            date     = v.findViewById(R.id.itemDate);
            duration = v.findViewById(R.id.itemDuration);
        }
    }
}
