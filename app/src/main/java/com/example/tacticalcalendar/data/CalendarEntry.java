package com.example.tacticalcalendar.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "calendar_entries")
public class CalendarEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String title;
    public String description;
    public long date; // timestamp for the day
    public int color;
    public String tags; // stored as comma-separated string for simplicity
    public boolean hasAlarm;
    public long alarmTime;

    public CalendarEntry() {}

    public CalendarEntry(String title, String description, long date, int color, String tags) {
        this.title = title;
        this.description = description;
        this.date = date;
        this.color = color;
        this.tags = tags;
    }
}
