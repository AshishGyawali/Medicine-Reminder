package com.example.medicinereminderapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Log.d(TAG, "Boot completed, rescheduling reminders");

        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                List<Medicine> medicines = AppDatabase.get(app).medicineDao().getAll();
                for (Medicine m : medicines) {
                    ReminderScheduler.scheduleAll(app, m);
                }
                Log.d(TAG, "Rescheduled " + medicines.size() + " medicines");
            } catch (Exception e) {
                Log.e(TAG, "boot reschedule failed", e);
            }
        }).start();
    }
}
