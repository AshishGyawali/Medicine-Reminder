package com.example.medicinereminderapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "ReminderReceiver";
    private static final String CHANNEL_ID = "medicine_reminder_channel_v3";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            int medicineId = intent.getIntExtra("medicine_id", -1);
            String medicineName = intent.getStringExtra("medicine_name");
            String dosage = intent.getStringExtra("dosage");
            int timeIndex = intent.getIntExtra("time_index", 0);
            int hour = intent.getIntExtra("hour", -1);
            int minute = intent.getIntExtra("minute", -1);
            String daysCsv = intent.getStringExtra("days_csv");
            if (daysCsv == null) daysCsv = "0,1,2,3,4,5,6";

            Log.d(TAG, "Reminder fired: id=" + medicineId + ", idx=" + timeIndex + ", name=" + medicineName);

            if (medicineId == -1 || medicineName == null) {
                Log.e(TAG, "Invalid extras");
                return;
            }

            // Lock in next valid occurrence right away so the reminder repeats on its
            // selected days even if the user never interacts with this one. One-time
            // schedules (empty days_csv) are not rescheduled.
            if (hour >= 0 && minute >= 0) {
                ReminderScheduler.scheduleAfterFire(context, medicineId, medicineName,
                        dosage, timeIndex, hour, minute, daysCsv);
            }

            Intent activityIntent = new Intent(context, ReminderActivity.class);
            activityIntent.putExtra("medicine_id", medicineId);
            activityIntent.putExtra("medicine_name", medicineName);
            activityIntent.putExtra("dosage", dosage);
            activityIntent.putExtra("time_index", timeIndex);
            activityIntent.putExtra("hour", hour);
            activityIntent.putExtra("minute", minute);
            activityIntent.putExtra("days_csv", daysCsv);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION);

            ensureChannel(context);
            postFullScreenNotification(context, activityIntent, medicineId, medicineName);

            try {
                context.startActivity(activityIntent);
            } catch (Exception e) {
                Log.w(TAG, "startActivity from background blocked; relying on full-screen intent", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "onReceive error", e);
        }
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "Medicine Reminders", NotificationManager.IMPORTANCE_HIGH);
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                channel.enableLights(true);
                channel.enableVibration(true);
                Uri alarm = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.alarm);
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(alarm, attrs);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void postFullScreenNotification(Context context, Intent activityIntent,
                                            int medicineId, String medicineName) {
        PendingIntent fullScreen = PendingIntent.getActivity(context, medicineId, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(medicineName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(medicineId, b.build());
    }
}
