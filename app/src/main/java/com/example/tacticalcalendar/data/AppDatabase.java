package com.example.tacticalcalendar.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CalendarEntry.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract CalendarDao calendarDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "calendar_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
