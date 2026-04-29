package com.example.medicinereminderapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddMedicineActivity extends AppCompatActivity {
    private static final String TAG = "AddMedicineActivity";
    private EditText nameInput, dosageInput, totalStockInput, dailyConsumptionInput;
    private Spinner medicineTypeSpinner;
    private LinearLayout timePickersContainer;
    private TextView nameError, dosageError, typeError, stockError, consumptionError, timeError;
    private Button saveBtn;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int currentTimeCount = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_add_medicine);

            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            nameInput = findViewById(R.id.medicine_name);
            nameError = findViewById(R.id.medicine_name_error);
            dosageInput = findViewById(R.id.dosage);
            dosageError = findViewById(R.id.dosage_error);
            medicineTypeSpinner = findViewById(R.id.medicine_type);
            typeError = findViewById(R.id.medicine_type_error);
            totalStockInput = findViewById(R.id.total_stock);
            stockError = findViewById(R.id.total_stock_error);
            dailyConsumptionInput = findViewById(R.id.daily_consumption);
            consumptionError = findViewById(R.id.daily_consumption_error);
            timePickersContainer = findViewById(R.id.time_pickers_container);
            timeError = findViewById(R.id.time_picker_error);
            saveBtn = findViewById(R.id.save_btn);

            CustomSpinnerAdapter adapter = new CustomSpinnerAdapter(this,
                    android.R.layout.simple_spinner_item, getResources().getTextArray(R.array.medicine_types));
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            medicineTypeSpinner.setAdapter(adapter);
            medicineTypeSpinner.setSelection(0);

            renderTimePickers(1);
            dailyConsumptionInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    int n = parseTimesCount(s.toString());
                    if (n != currentTimeCount) {
                        renderTimePickers(n);
                    }
                }
            });

            saveBtn.setOnClickListener(v -> saveMedicine());
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
            Toast.makeText(this, "त्रुटि: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int parseTimesCount(String s) {
        try {
            float f = Float.parseFloat(s.trim());
            return Medicine.reminderCountFromConsumption(f);
        } catch (Exception e) {
            return 1;
        }
    }

    private void renderTimePickers(int count) {
        currentTimeCount = count;
        TimePickerListHelper.render(this, timePickersContainer, count, null);
    }

    private void saveMedicine() {
        nameError.setVisibility(View.GONE);
        dosageError.setVisibility(View.GONE);
        typeError.setVisibility(View.GONE);
        stockError.setVisibility(View.GONE);
        consumptionError.setVisibility(View.GONE);
        timeError.setVisibility(View.GONE);

        String name = nameInput.getText().toString().trim();
        String dosage = dosageInput.getText().toString().trim();
        String type = medicineTypeSpinner.getSelectedItem() != null
                && medicineTypeSpinner.getSelectedItem().toString().equals("ट्याब्लेट") ? "TABLET" : "LIQUID";
        String totalStockStr = totalStockInput.getText().toString().trim();
        String dailyConsumptionStr = dailyConsumptionInput.getText().toString().trim();

        boolean hasError = false;
        if (name.isEmpty()) { showError(nameError, R.string.error_name_required); hasError = true; }
        if (dosage.isEmpty()) { showError(dosageError, R.string.error_dosage_required); hasError = true; }
        float totalStock = 0;
        if (totalStockStr.isEmpty()) { showError(stockError, R.string.error_stock_required); hasError = true; }
        else {
            try {
                totalStock = Float.parseFloat(totalStockStr);
                if (totalStock <= 0) { showError(stockError, R.string.error_stock_invalid); hasError = true; }
            } catch (NumberFormatException e) { showError(stockError, R.string.error_stock_number); hasError = true; }
        }
        float dailyConsumption = 0;
        if (dailyConsumptionStr.isEmpty()) { showError(consumptionError, R.string.error_consumption_required); hasError = true; }
        else {
            try {
                dailyConsumption = Float.parseFloat(dailyConsumptionStr);
                if (dailyConsumption <= 0) { showError(consumptionError, R.string.error_consumption_invalid); hasError = true; }
            } catch (NumberFormatException e) { showError(consumptionError, R.string.error_consumption_number); hasError = true; }
        }

        List<int[]> times = TimePickerListHelper.readValues(timePickersContainer);

        if (hasError) {
            Toast.makeText(this, "कृपया सबै विवरणहरू भर्नुहोस्", Toast.LENGTH_SHORT).show();
            return;
        }

        Medicine medicine = new Medicine();
        medicine.name = name;
        medicine.dosage = dosage;
        medicine.type = type;
        medicine.totalStock = totalStock;
        medicine.dailyConsumption = dailyConsumption;
        medicine.hour = times.get(0)[0];
        medicine.minute = times.get(0)[1];
        medicine.times = Medicine.timesToCsv(times);

        executor.execute(() -> {
            try {
                long id = AppDatabase.get(this).medicineDao().insert(medicine);
                medicine.id = (int) id;
                mainHandler.post(() -> {
                    ReminderScheduler.scheduleAll(getApplicationContext(), medicine);
                    Toast.makeText(this, "औषधि थपियो", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "insert failed", e);
                mainHandler.post(() -> Toast.makeText(this,
                        "औषधि सेभ गर्न असफल: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showError(TextView v, int resId) {
        v.setText(getString(resId));
        v.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
