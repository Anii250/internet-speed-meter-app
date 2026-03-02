package com.example.internetspeedmeeter;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SpeedTestDao {

    @Insert
    void insert(SpeedTestResult result);

    /** Returns the 30 most recent speed test results, newest first. */
    @Query("SELECT * FROM speed_test_results ORDER BY timestamp DESC LIMIT 30")
    List<SpeedTestResult> getLast30();

    @Query("DELETE FROM speed_test_results")
    void deleteAll();
}
