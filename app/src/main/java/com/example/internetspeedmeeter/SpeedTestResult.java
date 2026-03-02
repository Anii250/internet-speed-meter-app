package com.example.internetspeedmeeter;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity that stores the result of a single speed test.
 */
@Entity(tableName = "speed_test_results")
public class SpeedTestResult {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Unix timestamp (milliseconds) when the test was completed. */
    public long timestamp;

    /** Average download speed in MB/s for the test. */
    public double speedMbps;

    /** Duration of the test in seconds (target: 30s). */
    public int durationSec;

    public SpeedTestResult(long timestamp, double speedMbps, int durationSec) {
        this.timestamp   = timestamp;
        this.speedMbps   = speedMbps;
        this.durationSec = durationSec;
    }
}
