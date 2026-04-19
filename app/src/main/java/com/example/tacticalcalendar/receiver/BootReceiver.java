package com.example.tacticalcalendar.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.tacticalcalendar.data.AppDatabase;
import com.example.tacticalcalendar.data.CalendarDao;
import com.example.tacticalcalendar.data.CalendarEntry;
import android.app.AlarmManager;
import android.app.PendingIntent;
import java.util.List;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Executors.newSingleThreadExecutor().execute(() -> {
                CalendarDao dao = AppDatabase.getDatabase(context).calendarDao();
                List<CalendarEntry> entries = dao.getEntriesWithAlarmsSync(System.currentTimeMillis());
                for (CalendarEntry entry : entries) {
                    scheduleAlarm(context, (int)entry.id, entry.title, entry.alarmTime);
                }
            });
        }
    }

    private void scheduleAlarm(Context context, int entryId, String title, long timeInMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_ENTRY_ID, entryId);
        intent.putExtra("title", title);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, entryId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (timeInMillis > System.currentTimeMillis()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        }
    }
}
