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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQ_NOTIFICATIONS = 100;
    private static final float SWIPE_OPEN_DP = 80f;
    private static final float FAB_MENU_TRANSLATION_DP = 20f;

    private AppDatabase db;
    private RecyclerView medicineList;
    private MedicineAdapter adapter;
    private TextView totalCountView;
    private TextView lowCountView;
    private View emptyState;
    private View fabMenuScrim;
    private View fabAddMedicineOption;
    private View fabBuyMedicineOption;
    private FloatingActionButton addMedicineFab;
    private boolean isFabMenuOpen;

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

            fabMenuScrim = findViewById(R.id.fab_menu_scrim);
            fabAddMedicineOption = findViewById(R.id.fab_add_medicine_option);
            fabBuyMedicineOption = findViewById(R.id.fab_buy_medicine_option);
            addMedicineFab = findViewById(R.id.add_medicine_fab);

            fabMenuScrim.setOnClickListener(v -> closeFabMenu(true));
            fabAddMedicineOption.setOnClickListener(v -> {
                closeFabMenu(false);
                startActivity(new Intent(this, AddMedicineActivity.class));
            });
            fabBuyMedicineOption.setOnClickListener(v -> {
                closeFabMenu(false);
                startActivity(new Intent(this, MedicineCalculatorActivity.class));
            });
            addMedicineFab.setOnClickListener(v -> toggleFabMenu());

            setupDeveloperCredit();

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
        closeFabMenu(false);
        loadMedicines();
    }

    @Override
    public void onBackPressed() {
        if (isFabMenuOpen) {
            closeFabMenu(true);
            return;
        }
        super.onBackPressed();
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

    private void setupDeveloperCredit() {
        TextView credit = findViewById(R.id.developer_credit);
        String full = getString(R.string.developer_credit);
        String name = "Ashish Gyawali";
        int start = full.indexOf(name);
        if (start < 0) return;
        int end = start + name.length();

        SpannableString ss = new SpannableString(full);
        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.developer_linkedin))));
            }

            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                ds.setUnderlineText(false);
                ds.setColor(ContextCompat.getColor(MainActivity.this, R.color.brand_primary));
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        credit.setText(ss);
        credit.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void toggleFabMenu() {
        if (isFabMenuOpen) {
            closeFabMenu(true);
        } else {
            openFabMenu();
        }
    }

    private void openFabMenu() {
        if (isFabMenuOpen) return;
        isFabMenuOpen = true;
        fabMenuScrim.setVisibility(View.VISIBLE);
        fabMenuScrim.setAlpha(0f);
        fabMenuScrim.animate()
                .alpha(1f)
                .setDuration(180)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        showFabOption(fabAddMedicineOption, 0);
        showFabOption(fabBuyMedicineOption, 35);
        addMedicineFab.animate()
                .rotation(45f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    private void closeFabMenu(boolean animate) {
        if (!isFabMenuOpen && animate) return;
        isFabMenuOpen = false;
        if (!animate) {
            fabMenuScrim.animate().cancel();
            fabMenuScrim.setAlpha(0f);
            fabMenuScrim.setVisibility(View.GONE);
            hideFabOptionImmediately(fabAddMedicineOption);
            hideFabOptionImmediately(fabBuyMedicineOption);
            addMedicineFab.animate().cancel();
            addMedicineFab.setRotation(0f);
            return;
        }
        fabMenuScrim.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> fabMenuScrim.setVisibility(View.GONE))
                .start();
        hideFabOption(fabBuyMedicineOption, 0);
        hideFabOption(fabAddMedicineOption, 25);
        addMedicineFab.animate()
                .rotation(0f)
                .setDuration(180)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void showFabOption(View option, long startDelayMs) {
        option.setVisibility(View.VISIBLE);
        option.setAlpha(0f);
        option.setScaleX(0.92f);
        option.setScaleY(0.92f);
        option.setTranslationY(dpToPx(FAB_MENU_TRANSLATION_DP));
        option.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(startDelayMs)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator(0.85f))
                .start();
    }

    private void hideFabOption(View option, long startDelayMs) {
        option.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .translationY(dpToPx(FAB_MENU_TRANSLATION_DP))
                .setStartDelay(startDelayMs)
                .setDuration(150)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> option.setVisibility(View.GONE))
                .start();
    }

    private void hideFabOptionImmediately(View option) {
        option.animate().cancel();
        option.setAlpha(0f);
        option.setScaleX(0.92f);
        option.setScaleY(0.92f);
        option.setTranslationY(dpToPx(FAB_MENU_TRANSLATION_DP));
        option.setVisibility(View.GONE);
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
