package com.example.tacticalcalendar.receiver;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.tacticalcalendar.MainActivity;
import com.example.tacticalcalendar.R;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_SNOOZE = "com.example.tacticalcalendar.SNOOZE";
    public static final String ACTION_DISMISS = "com.example.tacticalcalendar.DISMISS";
    public static final String EXTRA_ENTRY_ID = "entry_id";
    private static final String CHANNEL_ID = "calendar_alarm_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        int entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1);
        String title = intent.getStringExtra("title");
        String action = intent.getAction();

        if (ACTION_SNOOZE.equals(action)) {
            // Logic for snooze - would need AlarmManager again
            snoozeAlarm(context, entryId, title);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(entryId);
            return;
        } else if (ACTION_DISMISS.equals(action)) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(entryId);
            return;
        }

        showNotification(context, entryId, title);
    }

    private void showNotification(Context context, int entryId, String title) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Calendar Alarms", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        Intent snoozeIntent = new Intent(context, AlarmReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra(EXTRA_ENTRY_ID, entryId);
        snoozeIntent.putExtra("title", title);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(context, entryId + 1000, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(context, AlarmReceiver.class);
        dismissIntent.setAction(ACTION_DISMISS);
        dismissIntent.putExtra(EXTRA_ENTRY_ID, entryId);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context, entryId + 2000, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Calendar Entry: " + title)
                .setContentText("Alarm for your calendar entry")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 15m", snoozePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
                .setAutoCancel(true);

        nm.notify(entryId, builder.build());
    }

    private void snoozeAlarm(Context context, int entryId, String title) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra("title", title);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, entryId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        long snoozeTime = System.currentTimeMillis() + (15 * 60 * 1000); // 15 minutes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
        }
    }
}
