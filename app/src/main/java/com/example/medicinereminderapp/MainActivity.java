package com.example.medicinereminderapp;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQ_NOTIFICATIONS = 100;
    private static final float SWIPE_OPEN_DP = 80f;

    private AppDatabase db;
    private RecyclerView medicineList;
    private MedicineAdapter adapter;
    private TextView totalCountView;
    private TextView lowCountView;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);

            totalCountView = findViewById(R.id.total_count);
            lowCountView = findViewById(R.id.low_count);
            emptyState = findViewById(R.id.empty_state);

            medicineList = findViewById(R.id.medicine_list);
            medicineList.setLayoutManager(new LinearLayoutManager(this));
            adapter = new MedicineAdapter(this, new ArrayList<>());
            medicineList.setAdapter(adapter);

            db = AppDatabase.get(this);

            FloatingActionButton fab = findViewById(R.id.add_medicine_fab);
            fab.setOnClickListener(v -> startActivity(new Intent(this, AddMedicineActivity.class)));

            requestRuntimePermissions();
            loadMedicines();
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
            Toast.makeText(this, "त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                Toast.makeText(this, R.string.full_screen_intent_required, Toast.LENGTH_LONG).show();
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    Log.w(TAG, "FSI settings unavailable", e);
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_required, Toast.LENGTH_LONG).show();
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMedicines();
    }

    private void loadMedicines() {
        new Thread(() -> {
            try {
                List<Medicine> medicines = db.medicineDao().getAll();
                if (medicines == null) medicines = new ArrayList<>();
                int low = 0;
                for (Medicine m : medicines) {
                    ReminderScheduler.scheduleAll(getApplicationContext(), m);
                    float daysLeft = m.dailyConsumption > 0 ? m.totalStock / m.dailyConsumption : 0;
                    if (daysLeft <= 7) low++;
                }
                final List<Medicine> toShow = medicines;
                final int lowCount = low;
                runOnUiThread(() -> {
                    adapter.updateMedicines(toShow);
                    totalCountView.setText(String.valueOf(toShow.size()));
                    lowCountView.setText(String.valueOf(lowCount));
                    emptyState.setVisibility(toShow.isEmpty() ? View.VISIBLE : View.GONE);
                    medicineList.setVisibility(toShow.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
                runOnUiThread(() -> Toast.makeText(this, "औषधि लोड गर्न असफल", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.ViewHolder> {
        private List<Medicine> medicines;
        private final Context context;

        MedicineAdapter(Context context, List<Medicine> medicines) {
            this.context = context;
            this.medicines = medicines;
        }

        void updateMedicines(List<Medicine> newMedicines) {
            this.medicines = newMedicines;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.medicine_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Medicine m = medicines.get(position);
            holder.indexText.setText((position + 1) + ".");
            holder.nameTextView.setText(m.name);
            holder.dosageTextView.setText(m.dosageWithUnit());

            // Schedule days display
            holder.scheduleDays.setText("🔁  " + m.daysLabel());
            holder.stockTextView.setText(String.format("%s: %.1f %s",
                    context.getString(R.string.stock_nepali),
                    m.totalStock,
                    "TABLET".equals(m.type)
                            ? context.getString(R.string.pills_nepali)
                            : context.getString(R.string.ml_nepali)));
            float daysLeft = m.dailyConsumption > 0 ? m.totalStock / m.dailyConsumption : 0;
            holder.daysLeftTextView.setText(String.format("%s: %.1f %s",
                    context.getString(R.string.days_left_nepali),
                    daysLeft,
                    context.getString(R.string.days_nepali)));

            int chipBg;
            int chipText;
            if (daysLeft <= 2) {
                chipBg = R.drawable.bg_chip_red;
                chipText = ContextCompat.getColor(context, R.color.stock_low);
            } else if (daysLeft <= 7) {
                chipBg = R.drawable.bg_chip_orange;
                chipText = ContextCompat.getColor(context, R.color.stock_warn);
            } else {
                chipBg = R.drawable.bg_chip_green;
                chipText = ContextCompat.getColor(context, R.color.stock_good);
            }
            holder.stockTextView.setBackgroundResource(chipBg);
            holder.stockTextView.setTextColor(chipText);

            holder.contentLayout.setTranslationX(0);
            holder.swipeOpen = false;
            attachSwipe(holder);

            // Paused state: dim children only (keep card bg opaque so delete strip doesn't bleed through)
            float childAlpha = m.active ? 1f : 0.45f;
            android.view.ViewGroup card = (android.view.ViewGroup) holder.contentLayout;
            for (int i = 0; i < card.getChildCount(); i++) {
                card.getChildAt(i).setAlpha(childAlpha);
            }
            if (!m.active) {
                holder.pausedBadge.setVisibility(View.VISIBLE);
            } else {
                holder.pausedBadge.setVisibility(View.GONE);
            }

            holder.infoIcon.setOnClickListener(v -> openEdit(m));
            holder.deleteIcon.setOnClickListener(v -> deleteMedicine(holder, m));
        }

        private void openEdit(Medicine m) {
            Intent intent = new Intent(MainActivity.this, EditMedicineActivity.class);
            intent.putExtra("medicine_id", m.id);
            startActivity(intent);
        }

        private void deleteMedicine(ViewHolder holder, Medicine m) {
            new Thread(() -> {
                try {
                    db.medicineDao().delete(m);
                    ReminderScheduler.cancelAll(getApplicationContext(), m.id);
                    runOnUiThread(() -> {
                        int pos = holder.getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION && pos < medicines.size()
                                && medicines.get(pos).id == m.id) {
                            medicines.remove(pos);
                            notifyItemRemoved(pos);
                            notifyItemRangeChanged(pos, medicines.size());
                        } else {
                            loadMedicines();
                        }
                        Toast.makeText(MainActivity.this, "औषधि हटाइयो: " + m.name, Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "delete failed", e);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                "औषधि हटाउन असफल: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        loadMedicines();
                    });
                }
            }).start();
        }

        private void attachSwipe(ViewHolder holder) {
            final float openX = -dpToPx(SWIPE_OPEN_DP);
            final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            holder.contentLayout.setOnTouchListener(new View.OnTouchListener() {
                float startX, startTrans;
                boolean dragging;

                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = ev.getRawX();
                            startTrans = v.getTranslationX();
                            dragging = false;
                            return false;
                        case MotionEvent.ACTION_MOVE: {
                            float dx = ev.getRawX() - startX;
                            if (!dragging && Math.abs(dx) > touchSlop) dragging = true;
                            if (dragging) {
                                float newX = Math.max(openX, Math.min(0f, startTrans + dx));
                                v.setTranslationX(newX);
                                return true;
                            }
                            return false;
                        }
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (!dragging) return false;
                            float endX = v.getTranslationX();
                            float target = endX < openX / 2f ? openX : 0f;
                            holder.swipeOpen = target == openX;
                            v.animate().translationX(target).setDuration(150).start();
                            return true;
                    }
                    return false;
                }
            });
        }

        @Override
        public int getItemCount() {
            return medicines.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView indexText, nameTextView, dosageTextView, stockTextView, daysLeftTextView, pausedBadge, scheduleDays;
            View contentLayout;
            ImageView deleteIcon, infoIcon;
            boolean swipeOpen;

            ViewHolder(View v) {
                super(v);
                indexText = v.findViewById(R.id.index_text);
                nameTextView = v.findViewById(R.id.medicine_name);
                dosageTextView = v.findViewById(R.id.medicine_dosage);
                stockTextView = v.findViewById(R.id.medicine_stock);
                daysLeftTextView = v.findViewById(R.id.medicine_days_left);
                contentLayout = v.findViewById(R.id.content_layout);
                deleteIcon = v.findViewById(R.id.delete_icon);
                infoIcon = v.findViewById(R.id.info_icon);
                pausedBadge = v.findViewById(R.id.paused_badge);
                scheduleDays = v.findViewById(R.id.schedule_days);
            }
        }
    }
}
