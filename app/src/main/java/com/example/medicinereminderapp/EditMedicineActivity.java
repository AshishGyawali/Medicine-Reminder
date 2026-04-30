package com.example.medicinereminderapp;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditMedicineActivity extends AppCompatActivity {
    private static final String TAG = "EditMedicineActivity";
    private EditText nameInput, dosageInput, totalStockInput, dailyConsumptionInput;
    private Spinner medicineTypeSpinner, dosageUnitSpinner;
    private LinearLayout timePickersContainer;
    private TextView nameError, dosageError, typeError, stockError, consumptionError, timeError;
    private Button saveBtn;
    private DayOfWeekPicker dayPicker;
    private SwitchCompat activeSwitch;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Medicine medicine;
    private int currentTimeCount = 1;
    private List<int[]> initialTimes;
    private String selectedImagePath = "";
    private ShapeableImageView imagePreview;
    private ImageView pickerIcon;
    private TextView pickerLabel;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private File pendingCameraFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_edit_medicine);

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
            dayPicker = findViewById(R.id.day_picker);
            activeSwitch = findViewById(R.id.active_switch);

            FrameLayout pickerContainer = findViewById(R.id.image_picker_container);
            imagePreview = findViewById(R.id.medicine_image_preview);
            pickerIcon = findViewById(R.id.image_picker_icon);
            pickerLabel = findViewById(R.id.image_picker_label);
            imagePickerLauncher = registerForActivityResult(
                    new ActivityResultContracts.GetContent(), this::onImagePicked);
            cameraLauncher = registerForActivityResult(
                    new ActivityResultContracts.TakePicture(), success -> {
                        if (success && pendingCameraFile != null) {
                            applyCapturedFile(pendingCameraFile.getAbsolutePath());
                        }
                    });
            pickerContainer.setOnClickListener(v -> showPhotoSourceDialog());

            CustomSpinnerAdapter adapter = new CustomSpinnerAdapter(this,
                    android.R.layout.simple_spinner_item, getResources().getTextArray(R.array.medicine_types));
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            medicineTypeSpinner.setAdapter(adapter);

            dosageUnitSpinner = findViewById(R.id.dosage_unit_spinner);
            CustomSpinnerAdapter unitAdapter = new CustomSpinnerAdapter(this,
                    android.R.layout.simple_spinner_item, getResources().getTextArray(R.array.dosage_units));
            unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            dosageUnitSpinner.setAdapter(unitAdapter);

            int medicineId = getIntent().getIntExtra("medicine_id", -1);
            loadMedicine(medicineId);

            dailyConsumptionInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    int n = parseTimesCount(s.toString());
                    if (n != currentTimeCount) {
                        currentTimeCount = n;
                        TimePickerListHelper.render(EditMedicineActivity.this, timePickersContainer, n, initialTimes);
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

    private void loadMedicine(int medicineId) {
        executor.execute(() -> {
            try {
                medicine = AppDatabase.get(this).medicineDao().getById(medicineId);
                mainHandler.post(() -> {
                    if (medicine == null) {
                        Toast.makeText(this, "औषधि लोड गर्न असफल", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    if (getSupportActionBar() != null && medicine.name != null
                            && !medicine.name.isEmpty()) {
                        getSupportActionBar().setTitle(medicine.name);
                    }
                    selectedImagePath = medicine.imageUri == null ? "" : medicine.imageUri;
                    showExistingImage(selectedImagePath);
                    initialTimes = medicine.parseTimes();
                    currentTimeCount = Math.max(1, initialTimes.size());
                    TimePickerListHelper.render(this, timePickersContainer, currentTimeCount, initialTimes);
                    nameInput.setText(medicine.name);
                    dosageInput.setText(medicine.dosage);
                    medicineTypeSpinner.setSelection("TABLET".equals(medicine.type) ? 0 : 1);
                    totalStockInput.setText(String.valueOf(medicine.totalStock));
                    dailyConsumptionInput.setText(String.valueOf(medicine.dailyConsumption));
                    dayPicker.setSelectedDays(medicine.parseDays());
                    activeSwitch.setChecked(medicine.active);
                    // Set dosage unit spinner to match saved unit
                    CharSequence[] units = getResources().getTextArray(R.array.dosage_units);
                    for (int i = 0; i < units.length; i++) {
                        if (units[i].toString().equals(medicine.dosageUnit)) {
                            dosageUnitSpinner.setSelection(i);
                            break;
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "load failed", e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "औषधि लोड गर्न असफल", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
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

        medicine.name = name;
        medicine.dosage = dosage;
        medicine.type = type;
        medicine.totalStock = totalStock;
        medicine.dailyConsumption = dailyConsumption;
        medicine.hour = times.get(0)[0];
        medicine.minute = times.get(0)[1];
        medicine.times = Medicine.timesToCsv(times);
        medicine.imageUri = selectedImagePath;
        medicine.daysOfWeek = Medicine.daysToCsv(dayPicker.getSelectedDays());
        medicine.active = activeSwitch.isChecked();
        medicine.dosageUnit = dosageUnitSpinner.getSelectedItem() != null
                ? dosageUnitSpinner.getSelectedItem().toString() : "mg";

        executor.execute(() -> {
            try {
                AppDatabase.get(this).medicineDao().update(medicine);
                mainHandler.post(() -> {
                    ReminderScheduler.scheduleAll(getApplicationContext(), medicine);
                    Toast.makeText(this, "औषधि अपडेट गरियो", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "update failed", e);
                mainHandler.post(() -> Toast.makeText(this,
                        "औषधि अपडेट गर्न असफल: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showPhotoSourceDialog() {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.take_photo),
                getString(R.string.choose_from_gallery)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.photo_source)
                .setItems(options, (d, which) -> {
                    if (which == 0) launchCamera();
                    else imagePickerLauncher.launch("image/*");
                })
                .show();
    }

    private void launchCamera() {
        try {
            File dir = new File(getFilesDir(), "medicine_images");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "क्यामेरा खोल्न असफल", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingCameraFile = new File(dir, "med_cam_" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", pendingCameraFile);
            cameraLauncher.launch(uri);
        } catch (Exception e) {
            Log.e(TAG, "camera launch failed", e);
            Toast.makeText(this, "क्यामेरा खोल्न असफल", Toast.LENGTH_SHORT).show();
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        executor.execute(() -> {
            try {
                String path = MedicineImageStore.saveCopy(this, uri);
                Bitmap bmp = MedicineImageStore.loadBitmap(path, 1024);
                mainHandler.post(() -> applyBitmap(path, bmp));
            } catch (Exception e) {
                Log.e(TAG, "image pick failed", e);
                mainHandler.post(() -> Toast.makeText(this, "तस्बिर सेभ गर्न असफल", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void applyCapturedFile(String path) {
        executor.execute(() -> {
            Bitmap bmp = MedicineImageStore.loadBitmap(path, 1024);
            mainHandler.post(() -> applyBitmap(path, bmp));
        });
    }

    private void applyBitmap(String path, Bitmap bmp) {
        if (bmp == null) {
            Toast.makeText(this, "तस्बिर लोड गर्न असफल", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedImagePath != null && !selectedImagePath.isEmpty()
                && !selectedImagePath.equals(path)) {
            MedicineImageStore.delete(selectedImagePath);
        }
        selectedImagePath = path;
        imagePreview.setImageBitmap(bmp);
        pickerIcon.setVisibility(View.GONE);
        pickerLabel.setText(R.string.change_photo);
    }

    private void showExistingImage(String path) {
        if (path == null || path.isEmpty()) return;
        executor.execute(() -> {
            Bitmap bmp = MedicineImageStore.loadBitmap(path, 1024);
            if (bmp == null) return;
            mainHandler.post(() -> {
                imagePreview.setImageBitmap(bmp);
                pickerIcon.setVisibility(View.GONE);
                pickerLabel.setText(R.string.change_photo);
            });
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
