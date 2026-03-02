package com.example.internetspeedmeeter;

import java.util.Locale;

/**
 * Shared utility methods for speed and data usage formatting.
 * Used by both {@link MainActivity} and {@link SpeedService} to avoid duplication.
 */
public final class SpeedUtils {

    private SpeedUtils() {
        // Utility class — no instances
    }

    /**
     * Formats a byte-per-second value into a human-readable speed string.
     * Automatically scales between KB/s and MB/s.
     *
     * @param bytesPerSecond bytes transferred in the last second
     * @return formatted string, e.g. "1.4 MB/s" or "512 KB/s"
     */
    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 0) bytesPerSecond = 0;
        if (bytesPerSecond >= 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f MB/s",
                    bytesPerSecond / (1024.0 * 1024.0));
        }
        return String.format(Locale.getDefault(), "%.1f KB/s",
                bytesPerSecond / 1024.0);
    }

    /**
     * Formats a total byte count into a human-readable data usage string.
     * Scales through KB, MB, and GB.
     *
     * @param bytes total bytes consumed
     * @return formatted string, e.g. "1.23 GB", "45.67 MB", or "512 KB"
     */
    public static String formatDataUsage(long bytes) {
        if (bytes < 0) bytes = 0;
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.2f GB",
                    bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.2f MB",
                    bytes / (1024.0 * 1024.0));
        }
        return (bytes / 1024) + " KB";
    }
}
