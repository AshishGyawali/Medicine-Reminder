package com.example.medicinereminderapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

public final class ReminderScheduler {
    private static final String TAG = "ReminderScheduler";

    private ReminderScheduler() {}

    public static int requestCode(int medicineId, int timeIndex) {
        return medicineId * 100 + timeIndex;
    }

    public static void scheduleAll(Context context, Medicine medicine) {
        cancelAll(context, medicine.id);
        if (!medicine.active) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        List<int[]> times = medicine.parseTimes();
        Set<Integer> days = medicine.parseDays();
        for (int i = 0; i < times.size(); i++) {
            int hour = times.get(i)[0];
            int minute = times.get(i)[1];
            scheduleNext(context, am, medicine.id, medicine.name, medicine.dosage,
                    i, hour, minute, days, medicine.daysOfWeek, false);
        }
    }

    public static void scheduleAfterFire(Context context, int medicineId, String name, String dosage,
                                         int index, int hour, int minute, String daysCsv) {
        Set<Integer> days = Medicine.parseDaysCsv(daysCsv);
        if (days.isEmpty()) return; // one-time, no reschedule
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        scheduleNext(context, am, medicineId, name, dosage, index, hour, minute, days, daysCsv, true);
    }

    public static void scheduleSnooze(Context context, int medicineId, String name, String dosage,
                                      int index, int hour, int minute, String daysCsv, long snoozeMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long trigger = System.currentTimeMillis() + snoozeMs;
        PendingIntent pi = buildPendingIntent(context, medicineId, name, dosage, index, hour, minute, daysCsv);
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

    private static void scheduleNext(Context context, AlarmManager am, int medicineId, String name,
                                     String dosage, int index, int hour, int minute,
                                     Set<Integer> days, String daysCsv, boolean afterFire) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (afterFire || c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (days.isEmpty()) {
            // One-time: just schedule the next occurrence (today if upcoming, else tomorrow).
            // afterFire is filtered out earlier, so this only runs on initial schedule.
            PendingIntent pi = buildPendingIntent(context, medicineId, name, dosage, index, hour, minute, daysCsv);
            setExact(am, c.getTimeInMillis(), pi);
            Log.d(TAG, "One-time scheduled " + name + " idx=" + index + " at " + c.getTime());
            return;
        }

        // Repeating: walk forward up to 7 days to find an enabled weekday.
        for (int j = 0; j < 7; j++) {
            int dayIdx = Medicine.calendarDayToIndex(c.get(Calendar.DAY_OF_WEEK));
            if (days.contains(dayIdx)) {
                PendingIntent pi = buildPendingIntent(context, medicineId, name, dosage, index, hour, minute, daysCsv);
                setExact(am, c.getTimeInMillis(), pi);
                Log.d(TAG, "Scheduled " + name + " idx=" + index + " at " + c.getTime());
                return;
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private static PendingIntent buildPendingIntent(Context context, int medicineId, String name,
                                                    String dosage, int index, int hour, int minute,
                                                    String daysCsv) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("medicine_id", medicineId);
        intent.putExtra("medicine_name", name);
        intent.putExtra("dosage", dosage);
        intent.putExtra("time_index", index);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("days_csv", daysCsv == null ? "" : daysCsv);
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
