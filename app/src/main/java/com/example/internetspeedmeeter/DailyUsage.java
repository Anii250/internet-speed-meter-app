package com.example.internetspeedmeeter;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for tracking daily mobile + WiFi data consumption.
 * One row per calendar day, keyed by date string "yyyyMMdd".
 */
@Entity(tableName = "daily_usage")
public class DailyUsage {

    @PrimaryKey
    @NonNull
    public String dateKey; // format: "yyyyMMdd"

    public long mobileBytes;
    public long wifiBytes;

    public DailyUsage(String dateKey, long mobileBytes, long wifiBytes) {
        this.dateKey      = dateKey;
        this.mobileBytes  = mobileBytes;
        this.wifiBytes    = wifiBytes;
    }
}
