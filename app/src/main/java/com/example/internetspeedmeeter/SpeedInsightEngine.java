package com.example.internetspeedmeeter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local AI Insight Engine.
 *
 * Analyses the last 30 speed test results stored in Room DB and produces
 * human-readable insights about the user's internet quality patterns.
 * No external APIs — pure heuristics on local data.
 */
public class SpeedInsightEngine {

    /** Returns a prioritised list of plain-English insight strings. Max 3 returned. */
    public static List<String> generate(List<SpeedTestResult> results) {
        List<String> insights = new ArrayList<>();
        if (results == null || results.size() < 3) {
            insights.add("💡 Run a few more speed tests to unlock AI insights.");
            return insights;
        }

        // 1. Peak-hour slowdown detection (group by hour-of-day)
        Map<Integer, List<Double>> byHour = new HashMap<>();
        for (SpeedTestResult r : results) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(r.timestamp);
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(r.speedMbps);
        }

        double overallAvg = results.stream().mapToDouble(r -> r.speedMbps).average().orElse(0);

        int slowestHour = -1;
        double worstRatio = 1.0;
        for (Map.Entry<Integer, List<Double>> entry : byHour.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            double hourAvg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(overallAvg);
            double ratio = hourAvg / Math.max(0.01, overallAvg);
            if (ratio < worstRatio) {
                worstRatio = ratio;
                slowestHour = entry.getKey();
            }
        }
        if (slowestHour >= 0 && worstRatio < 0.60) {
            String period = slowestHour >= 6 && slowestHour < 12 ? "morning"
                    : slowestHour >= 12 && slowestHour < 17 ? "afternoon"
                    : slowestHour >= 17 && slowestHour < 22 ? "evening" : "night";
            insights.add(String.format("⏰ Your speed drops ~%.0f%% during %s hours (%d:00). "
                    + "Your ISP may be throttling at peak times.",
                    (1 - worstRatio) * 100, period, slowestHour));
        }

        // 2. Consistency check — high std deviation = unstable connection
        double mean = overallAvg;
        double variance = results.stream().mapToDouble(r -> (r.speedMbps - mean) * (r.speedMbps - mean))
                .average().orElse(0);
        double stddev = Math.sqrt(variance);
        if (stddev > mean * 0.5 && results.size() >= 5) {
            insights.add(String.format("📉 Your connection is unstable. Speed varies widely "
                    + "(avg %.1f MB/s ± %.1f). Consider restarting your router.", mean, stddev));
        }

        // 3. Trend — is speed improving or declining?
        if (results.size() >= 6) {
            double recentAvg = results.subList(0, 3).stream()
                    .mapToDouble(r -> r.speedMbps).average().orElse(0);
            double olderAvg = results.subList(results.size() - 3, results.size()).stream()
                    .mapToDouble(r -> r.speedMbps).average().orElse(0);
            double trendPct = (recentAvg - olderAvg) / Math.max(0.01, olderAvg) * 100;
            if (trendPct > 20) {
                insights.add(String.format("📈 Great news! Your speed improved by %.0f%% recently.", trendPct));
            } else if (trendPct < -20) {
                insights.add(String.format("📉 Speed has declined by %.0f%% over recent tests. "
                        + "Check for new background apps or router issues.", Math.abs(trendPct)));
            }
        }

        // 4. Absolute quality rating
        if (insights.isEmpty()) {
            if (overallAvg >= 25) {
                insights.add(String.format("✅ Your connection is excellent! Avg speed: %.1f MB/s.", overallAvg));
            } else if (overallAvg >= 5) {
                insights.add(String.format("👍 Your connection is good. Avg speed: %.1f MB/s.", overallAvg));
            } else {
                insights.add(String.format("⚠️ Your average speed (%.1f MB/s) is slow. "
                        + "Try running tests at different times.", overallAvg));
            }
        }

        return insights.subList(0, Math.min(3, insights.size()));
    }
}
