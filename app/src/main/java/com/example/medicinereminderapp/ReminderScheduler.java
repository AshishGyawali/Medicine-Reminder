package com.example.medicinereminderapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

public final class ReminderScheduler {
    private static final String TAG = "ReminderScheduler";

    private ReminderScheduler() {}

    public static int requestCode(int medicineId, int timeIndex) {
        return medicineId * 100 + timeIndex;
    }

    public static void scheduleAll(Context context, Medicine medicine) {
        cancelAll(context, medicine.id);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        List<int[]> times = medicine.parseTimes();
        for (int i = 0; i < times.size(); i++) {
            int hour = times.get(i)[0];
            int minute = times.get(i)[1];
            scheduleSlot(context, am, medicine.id, medicine.name, medicine.dosage,
                    i, hour, minute, false);
        }
    }

    public static void scheduleNextDayForSlot(Context context, int medicineId, String name,
                                              String dosage, int index, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        scheduleSlot(context, am, medicineId, name, dosage, index, hour, minute, true);
    }

    public static void scheduleSnooze(Context context, int medicineId, String name, String dosage,
                                      int index, int hour, int minute, long snoozeMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long trigger = System.currentTimeMillis() + snoozeMs;
        PendingIntent pi = buildPendingIntent(context, medicineId, name, dosage, index, hour, minute);
        setExact(am, trigger, pi);
    }

    public static void cancelAll(Context context, int medicineId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < Medicine.MAX_TIMES_PER_DAY; i++) {
            Intent intent = new Intent(context, ReminderReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(medicineId, i), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }
    }

    private static void scheduleSlot(Context context, AlarmManager am, int medicineId, String name,
                                     String dosage, int index, int hour, int minute,
                                     boolean forceTomorrow) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (forceTomorrow || c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        PendingIntent pi = buildPendingIntent(context, medicineId, name, dosage, index, hour, minute);
        setExact(am, c.getTimeInMillis(), pi);
        Log.d(TAG, "Scheduled " + name + " idx=" + index + " at " + c.getTime());
    }

    private static PendingIntent buildPendingIntent(Context context, int medicineId, String name,
                                                    String dosage, int index, int hour, int minute) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("medicine_id", medicineId);
        intent.putExtra("medicine_name", name);
        intent.putExtra("dosage", dosage);
        intent.putExtra("time_index", index);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        return PendingIntent.getBroadcast(context, requestCode(medicineId, index), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setExact(AlarmManager am, long trigger, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        } catch (Exception e) {
            Log.e(TAG, "setExact failed", e);
        }
    }
}
