package com.example.tacticalcalendar.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CalendarDao {
    @Insert
    long insert(CalendarEntry entry);

    @Update
    void update(CalendarEntry entry);

    @Delete
    void delete(CalendarEntry entry);

    @Query("SELECT * FROM calendar_entries WHERE date = :date")
    LiveData<List<CalendarEntry>> getEntriesForDate(long date);

    @Query("SELECT * FROM calendar_entries ORDER BY date ASC")
    LiveData<List<CalendarEntry>> getAllEntries();

    @Query("SELECT * FROM calendar_entries ORDER BY date ASC")
    List<CalendarEntry> getAllEntriesSync();
    
    @Query("SELECT * FROM calendar_entries WHERE id = :id")
    CalendarEntry getEntryById(int id);

    @Query("SELECT * FROM calendar_entries WHERE hasAlarm = 1 AND alarmTime > :currentTime")
    List<CalendarEntry> getEntriesWithAlarmsSync(long currentTime);
    
    @Query("SELECT DISTINCT date FROM calendar_entries")
    LiveData<List<Long>> getDatesWithEntries();

    @Query("DELETE FROM calendar_entries")
    void deleteAll();
}
