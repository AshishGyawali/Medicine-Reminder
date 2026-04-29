package com.example.medicinereminderapp;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderActivity extends AppCompatActivity {
    private static final String TAG = "ReminderActivity";
    private static final long AUTO_DISMISS_MS = 5 * 60 * 1000L;
    private static final long SNOOZE_MS = 10 * 60 * 1000L;

    private Ringtone ringtone;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private Handler mainHandler;
    private Runnable autoDismiss;

    private int medicineId;
    private String medicineName;
    private String dosage;
    private int timeIndex;
    private int slotHour;
    private int slotMinute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockscreen();
        setContentView(R.layout.activity_reminder);

        Intent intent = getIntent();
        medicineId = intent.getIntExtra("medicine_id", -1);
        medicineName = intent.getStringExtra("medicine_name");
        dosage = intent.getStringExtra("dosage");
        timeIndex = intent.getIntExtra("time_index", 0);
        slotHour = intent.getIntExtra("hour", -1);
        slotMinute = intent.getIntExtra("minute", -1);

        if (medicineId == -1 || medicineName == null) {
            Log.e(TAG, "Invalid extras, finishing");
            finish();
            return;
        }

        // Hide the fallback heads-up notification now that the activity is on screen.
        cancelNotification();

        bindViews();
        startAlarmFx();
        loadStock();

        mainHandler = new Handler(Looper.getMainLooper());
        autoDismiss = this::doSnooze;
        mainHandler.postDelayed(autoDismiss, AUTO_DISMISS_MS);
    }

    private void showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    TAG + "::WakeLock");
            wakeLock.acquire(AUTO_DISMISS_MS + 5000);
        }
    }

    private void bindViews() {
        TextView clock = findViewById(R.id.clock_text);
        TextView title = findViewById(R.id.medicine_title);
        Button snooze = findViewById(R.id.snooze_btn);
        SlideToStopView slide = findViewById(R.id.slide_to_stop);

        SimpleDateFormat fmt = new SimpleDateFormat("h:mm", Locale.getDefault());
        clock.setText(fmt.format(new Date()));
        title.setText(medicineName);

        snooze.setOnClickListener(v -> doSnooze());
        slide.setOnSlideCompleteListener(this::doConfirm);
    }

    private void loadStock() {
        TextView stockText = findViewById(R.id.stock_text);
        new Thread(() -> {
            try {
                Medicine m = AppDatabase.get(this).medicineDao().getById(medicineId);
                if (m == null) return;
                String unit = "TABLET".equals(m.type)
                        ? getString(R.string.pills_nepali)
                        : getString(R.string.ml_nepali);
                String text = String.format(Locale.getDefault(), "%s: %.1f %s",
                        getString(R.string.stock_remaining), m.totalStock, unit);
                runOnUiThread(() -> stockText.setText(text));
            } catch (Exception e) {
                Log.e(TAG, "loadStock failed", e);
            }
        }).start();
    }

    private void startAlarmFx() {
        try {
            Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alarm);
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "ringtone failed", e);
        }

        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 800, 600};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "vibrate failed", e);
        }
    }

    private void stopAlarmFx() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
    }

    private void doConfirm() {
        cancelAutoDismiss();
        stopAlarmFx();
        cancelNotification();
        new Thread(() -> {
            try {
                AppDatabase.get(this).medicineDao().decreaseStock(medicineId);
            } catch (Exception e) {
                Log.e(TAG, "confirm failed", e);
            }
        }).start();
        // Tomorrow's slot was already scheduled in ReminderReceiver — nothing else to do.
        finishAndRemoveTaskCompat();
    }

    private void doSnooze() {
        cancelAutoDismiss();
        stopAlarmFx();
        cancelNotification();
        if (slotHour >= 0 && slotMinute >= 0) {
            ReminderScheduler.scheduleSnooze(getApplicationContext(), medicineId, medicineName,
                    dosage, timeIndex, slotHour, slotMinute, SNOOZE_MS);
        }
        finishAndRemoveTaskCompat();
    }

    private void cancelAutoDismiss() {
        if (mainHandler != null && autoDismiss != null) {
            mainHandler.removeCallbacks(autoDismiss);
        }
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(medicineId);
    }

    private void finishAndRemoveTaskCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        // Block back press — user must explicitly snooze or stop.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAutoDismiss();
        stopAlarmFx();
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
    }
}
