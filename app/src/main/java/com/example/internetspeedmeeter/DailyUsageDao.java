package com.example.internetspeedmeeter;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DailyUsageDao {

    /** Insert or replace a DailyUsage row for the given date. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DailyUsage usage);

    /** Returns the last 7 days of usage, most recent first. */
    @Query("SELECT * FROM daily_usage ORDER BY dateKey DESC LIMIT 7")
    List<DailyUsage> getLast7Days();
}
