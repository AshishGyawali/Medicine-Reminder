package com.example.medicinereminderapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import bikramsambat.BsCalendar;
import bikramsambat.BsException;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MedicineCalculatorActivity extends AppCompatActivity {
    private static final String TAG = "MedicineCalculator";
    private static final BsCalendar BS_CALENDAR = BsCalendar.getInstance();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<SelectableMedicine> selectableMedicines = new ArrayList<>();
    private final List<CalculationResult> lastResults = new ArrayList<>();
    private final DecimalFormat amountFormat =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private RecyclerView medicineSelectionList;
    private CheckBox selectAllCheckbox;
    private TextView activeMedicineCountView;
    private TextView noActiveMedicinesView;
    private EditText daysInput;
    private TextView daysInputError;
    private TextView selectionError;
    private Button calculateButton;
    private LinearLayout resultsSection;
    private TextView resultSubtitle;
    private TextView generatedOnText;
    private LinearLayout resultListContainer;
    private TextView summaryFooter;
    private LinearLayout exportCard;
    private Button copyTextButton;
    private Button generateJpegButton;
    private Button generatePdfButton;

    private MedicineSelectionAdapter adapter;
    private boolean isUpdatingSelectAll;
    private int lastCalculatedDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_calculator);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        medicineSelectionList = findViewById(R.id.medicine_selection_list);
        selectAllCheckbox = findViewById(R.id.select_all_checkbox);
        activeMedicineCountView = findViewById(R.id.active_medicine_count);
        noActiveMedicinesView = findViewById(R.id.no_active_medicines);
        daysInput = findViewById(R.id.days_input);
        daysInputError = findViewById(R.id.days_input_error);
        selectionError = findViewById(R.id.selection_error);
        calculateButton = findViewById(R.id.calculate_button);
        resultsSection = findViewById(R.id.results_section);
        resultSubtitle = findViewById(R.id.result_subtitle);
        generatedOnText = findViewById(R.id.generated_on_text);
        resultListContainer = findViewById(R.id.result_list_container);
        summaryFooter = findViewById(R.id.summary_footer);
        exportCard = findViewById(R.id.export_card);
        copyTextButton = findViewById(R.id.copy_text_button);
        generateJpegButton = findViewById(R.id.generate_jpeg_button);
        generatePdfButton = findViewById(R.id.generate_pdf_button);

        adapter = new MedicineSelectionAdapter();
        medicineSelectionList.setLayoutManager(new LinearLayoutManager(this));
        medicineSelectionList.setAdapter(adapter);

        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingSelectAll) return;
            setAllSelected(isChecked);
        });

        daysInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                daysInputError.setVisibility(View.GONE);
                clearResults();
            }
        });

        calculateButton.setOnClickListener(v -> calculatePurchasePlan());
        copyTextButton.setOnClickListener(v -> copySummaryText());
        generateJpegButton.setOnClickListener(v -> exportSummaryAsJpeg());
        generatePdfButton.setOnClickListener(v -> exportSummaryAsPdf());

        loadActiveMedicines();
    }

    private void loadActiveMedicines() {
        executor.execute(() -> {
            try {
                List<Medicine> medicines = AppDatabase.get(this).medicineDao().getActiveMedicines();
                if (medicines == null) medicines = new ArrayList<>();
                final List<Medicine> activeMedicines = medicines;
                mainHandler.post(() -> showActiveMedicines(activeMedicines));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load active medicines", e);
                mainHandler.post(() -> Toast.makeText(this,
                        "सक्रिय औषधि लोड गर्न असफल", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showActiveMedicines(List<Medicine> medicines) {
        selectableMedicines.clear();
        for (Medicine medicine : medicines) {
            selectableMedicines.add(new SelectableMedicine(medicine));
        }
        adapter.notifyDataSetChanged();

        boolean hasItems = !selectableMedicines.isEmpty();
        activeMedicineCountView.setText(getString(R.string.active_medicine_count, selectableMedicines.size()));
        noActiveMedicinesView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        medicineSelectionList.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        selectAllCheckbox.setEnabled(hasItems);
        daysInput.setEnabled(hasItems);
        calculateButton.setEnabled(hasItems);
        updateSelectAllState();
    }

    private void calculatePurchasePlan() {
        selectionError.setVisibility(View.GONE);
        daysInputError.setVisibility(View.GONE);

        List<Medicine> selectedMedicines = new ArrayList<>();
        for (SelectableMedicine selectableMedicine : selectableMedicines) {
            if (selectableMedicine.selected) {
                selectedMedicines.add(selectableMedicine.medicine);
            }
        }

        if (selectedMedicines.isEmpty()) {
            selectionError.setText(R.string.select_medicine_error);
            selectionError.setVisibility(View.VISIBLE);
            clearResults();
            return;
        }

        int days;
        String daysValue = daysInput.getText().toString().trim();
        if (daysValue.isEmpty()) {
            daysInputError.setText(R.string.days_required_error);
            daysInputError.setVisibility(View.VISIBLE);
            clearResults();
            return;
        }

        try {
            days = Integer.parseInt(daysValue);
            if (days <= 0) throw new NumberFormatException("Days must be positive");
        } catch (NumberFormatException e) {
            daysInputError.setText(R.string.days_invalid_error);
            daysInputError.setVisibility(View.VISIBLE);
            clearResults();
            return;
        }

        List<CalculationResult> results = new ArrayList<>();
        for (Medicine medicine : selectedMedicines) {
            float totalRequired = medicine.dailyConsumption * days;
            float toBuy = Math.max(0f, totalRequired - medicine.totalStock);
            results.add(new CalculationResult(
                    medicine,
                    medicine.totalStock,
                    medicine.dailyConsumption,
                    totalRequired,
                    toBuy,
                    getUnitLabel(medicine)
            ));
        }
        showResults(results, days);
    }

    private void showResults(List<CalculationResult> results, int days) {
        lastResults.clear();
        lastResults.addAll(results);
        lastCalculatedDays = days;

        resultSubtitle.setText(getString(R.string.calculation_summary_for_days, days));
        generatedOnText.setText(getString(
                R.string.generated_on,
                formatCurrentBsDate()
        ));

        resultListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (CalculationResult result : results) {
            View row = inflater.inflate(R.layout.item_calculation_result, resultListContainer, false);
            bindCalculationResultRow(row, result);
            resultListContainer.addView(row);
        }

        summaryFooter.setText(getString(R.string.summary_medicine_count_line, results.size()));
        resultsSection.setVisibility(View.VISIBLE);
    }

    private void bindCalculationResultRow(View row, CalculationResult result) {
        TextView name = row.findViewById(R.id.result_name);
        TextView dosage = row.findViewById(R.id.result_dosage);
        TextView currentStock = row.findViewById(R.id.result_current_stock);
        TextView dailyUse = row.findViewById(R.id.result_daily_use);
        TextView totalRequired = row.findViewById(R.id.result_total_required);
        TextView toBuy = row.findViewById(R.id.result_to_buy);

        name.setText(result.medicine.name);
        dosage.setText(result.medicine.dosageWithUnit());
        currentStock.setText(labelValueLine(
                R.string.current_stock_label,
                formatQuantity(result.currentStock) + " " + result.unitLabel));
        dailyUse.setText(labelValueLine(
                R.string.daily_use_label,
                getString(R.string.unit_per_day,
                        formatQuantity(result.dailyConsumption) + " " + result.unitLabel)));
        totalRequired.setText(labelValueLine(
                R.string.total_required_label,
                formatQuantity(result.totalRequired) + " " + result.unitLabel));

        if (result.toBuy > 0f) {
            toBuy.setText(labelValueLine(
                    R.string.to_buy_label,
                    formatQuantity(result.toBuy) + " " + result.unitLabel));
            toBuy.setTextColor(getColor(R.color.brand_primary));
        } else {
            toBuy.setText(getString(R.string.stock_enough_label));
            toBuy.setTextColor(getColor(R.color.stock_good));
        }
    }

    private void copySummaryText() {
        if (lastResults.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.medicine_calculator_title),
                buildSummaryText()
        ));
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    private void exportSummaryAsJpeg() {
        if (lastResults.isEmpty()) return;
        exportCard.post(() -> {
            Bitmap bitmap = renderViewToBitmap(exportCard);
            if (bitmap == null) {
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
                return;
            }
            executor.execute(() -> {
                File file = null;
                try {
                    file = createExportFile("medicine_purchase_", ".jpg");
                    try (FileOutputStream outputStream = new FileOutputStream(file)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 96, outputStream);
                    }
                    Uri uri = FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", file);
                    mainHandler.post(() -> shareFile(uri, "image/jpeg", R.string.share_jpeg));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to export JPEG", e);
                    mainHandler.post(() -> Toast.makeText(this,
                            R.string.export_failed, Toast.LENGTH_LONG).show());
                } finally {
                    bitmap.recycle();
                    if (file != null) file.deleteOnExit();
                }
            });
        });
    }

    private void exportSummaryAsPdf() {
        if (lastResults.isEmpty()) return;
        exportCard.post(() -> {
            Bitmap bitmap = renderViewToBitmap(exportCard);
            if (bitmap == null) {
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
                return;
            }
            executor.execute(() -> {
                File file = null;
                try {
                    file = createExportFile("medicine_purchase_", ".pdf");
                    writeBitmapToPdf(bitmap, file);
                    Uri uri = FileProvider.getUriForFile(this,
                            getPackageName() + ".fileprovider", file);
                    mainHandler.post(() -> shareFile(uri, "application/pdf", R.string.share_pdf));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to export PDF", e);
                    mainHandler.post(() -> Toast.makeText(this,
                            R.string.export_failed, Toast.LENGTH_LONG).show());
                } finally {
                    bitmap.recycle();
                    if (file != null) file.deleteOnExit();
                }
            });
        });
    }

    private Bitmap renderViewToBitmap(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            int fallbackWidth = getResources().getDisplayMetrics().widthPixels - (int) dpToPx(40f);
            int widthSpec = View.MeasureSpec.makeMeasureSpec(fallbackWidth, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            view.measure(widthSpec, heightSpec);
            width = view.getMeasuredWidth();
            height = view.getMeasuredHeight();
            view.layout(0, 0, width, height);
        }
        if (width <= 0 || height <= 0) return null;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        return bitmap;
    }

    private void writeBitmapToPdf(Bitmap bitmap, File targetFile) throws Exception {
        PdfDocument document = new PdfDocument();
        try {
            final int pageWidth = 595;
            final int margin = 24;
            int contentWidth = pageWidth - (margin * 2);
            float scale = contentWidth / (float) bitmap.getWidth();
            int contentHeight = Math.max(1, Math.round(bitmap.getHeight() * scale));
            int pageHeight = contentHeight + (margin * 2);

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(
                    bitmap,
                    new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                    new RectF(margin, margin, margin + contentWidth, margin + contentHeight),
                    null
            );
            document.finishPage(page);

            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                document.writeTo(outputStream);
            }
        } finally {
            document.close();
        }
    }

    private File createExportFile(String prefix, String extension) {
        File dir = new File(getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create export directory");
        }
        return new File(dir, prefix + System.currentTimeMillis() + extension);
    }

    private void shareFile(Uri uri, String mimeType, int titleRes) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, buildSummaryText());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(titleRes)));
    }

    private String buildSummaryText() {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.medicine_calculator_title)).append('\n');
        builder.append(getString(R.string.calculation_summary_for_days, lastCalculatedDays)).append('\n');
        builder.append(getString(
                R.string.generated_on,
                formatCurrentBsDate()
        ));

        for (int i = 0; i < lastResults.size(); i++) {
            CalculationResult result = lastResults.get(i);
            builder.append("\n\n").append(i + 1).append(". ").append(result.medicine.name);
            if (!result.medicine.dosageWithUnit().isEmpty()) {
                builder.append(" (").append(result.medicine.dosageWithUnit()).append(')');
            }
            builder.append("\n").append(labelValueLine(
                    R.string.current_stock_label,
                    formatQuantity(result.currentStock) + " " + result.unitLabel));
            builder.append("\n").append(labelValueLine(
                    R.string.daily_use_label,
                    getString(R.string.unit_per_day,
                            formatQuantity(result.dailyConsumption) + " " + result.unitLabel)));
            builder.append("\n").append(labelValueLine(
                    R.string.total_required_label,
                    formatQuantity(result.totalRequired) + " " + result.unitLabel));
            builder.append("\n");
            if (result.toBuy > 0f) {
                builder.append(labelValueLine(
                        R.string.to_buy_label,
                        formatQuantity(result.toBuy) + " " + result.unitLabel));
            } else {
                builder.append(getString(R.string.stock_enough_label));
            }
        }
        return builder.toString();
    }

    private void clearResults() {
        lastResults.clear();
        lastCalculatedDays = 0;
        resultsSection.setVisibility(View.GONE);
    }

    private void setAllSelected(boolean selected) {
        for (SelectableMedicine selectableMedicine : selectableMedicines) {
            selectableMedicine.selected = selected;
        }
        adapter.notifyDataSetChanged();
        selectionError.setVisibility(View.GONE);
        clearResults();
    }

    private void onItemSelectionChanged() {
        selectionError.setVisibility(View.GONE);
        updateSelectAllState();
        clearResults();
    }

    private void updateSelectAllState() {
        boolean allSelected = !selectableMedicines.isEmpty();
        for (SelectableMedicine selectableMedicine : selectableMedicines) {
            if (!selectableMedicine.selected) {
                allSelected = false;
                break;
            }
        }
        isUpdatingSelectAll = true;
        selectAllCheckbox.setChecked(allSelected);
        isUpdatingSelectAll = false;
    }

    private String getUnitLabel(Medicine medicine) {
        return "TABLET".equals(medicine.type)
                ? getString(R.string.pills_nepali)
                : getString(R.string.ml_nepali);
    }

    private String formatQuantity(float value) {
        return amountFormat.format(value);
    }

    private String labelValueLine(int labelRes, String value) {
        return getString(labelRes) + ": " + value;
    }

    private String formatCurrentBsDate() {
        try {
            return BS_CALENDAR.toBik_text(LocalDate.now().toString());
        } catch (BsException e) {
            Log.w(TAG, "Falling back to ISO date because BS conversion failed", e);
            return LocalDate.now().toString();
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private class MedicineSelectionAdapter
            extends RecyclerView.Adapter<MedicineSelectionAdapter.SelectionViewHolder> {

        @NonNull
        @Override
        public SelectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calculator_medicine, parent, false);
            return new SelectionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SelectionViewHolder holder, int position) {
            SelectableMedicine selectableMedicine = selectableMedicines.get(position);
            Medicine medicine = selectableMedicine.medicine;

            holder.nameView.setText(medicine.name);
            holder.dosageView.setText(medicine.dosageWithUnit());
            holder.stockView.setText(labelValueLine(
                    R.string.current_stock_label,
                    formatQuantity(medicine.totalStock) + " " + getUnitLabel(medicine)));
            holder.dailyConsumptionView.setText(labelValueLine(
                    R.string.daily_use_label,
                    getString(R.string.unit_per_day,
                            formatQuantity(medicine.dailyConsumption) + " " + getUnitLabel(medicine))));

            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(selectableMedicine.selected);
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;
                selectableMedicines.get(adapterPosition).selected = isChecked;
                onItemSelectionChanged();
            });
            holder.itemView.setOnClickListener(v -> holder.checkBox.performClick());
        }

        @Override
        public int getItemCount() {
            return selectableMedicines.size();
        }

        class SelectionViewHolder extends RecyclerView.ViewHolder {
            final CheckBox checkBox;
            final TextView nameView;
            final TextView dosageView;
            final TextView stockView;
            final TextView dailyConsumptionView;

            SelectionViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.medicine_checkbox);
                nameView = itemView.findViewById(R.id.medicine_name);
                dosageView = itemView.findViewById(R.id.medicine_dosage);
                stockView = itemView.findViewById(R.id.medicine_stock);
                dailyConsumptionView = itemView.findViewById(R.id.medicine_daily_consumption);
            }
        }
    }

    private static class SelectableMedicine {
        final Medicine medicine;
        boolean selected;

        SelectableMedicine(Medicine medicine) {
            this.medicine = medicine;
        }
    }

    private static class CalculationResult {
        final Medicine medicine;
        final float currentStock;
        final float dailyConsumption;
        final float totalRequired;
        final float toBuy;
        final String unitLabel;

        CalculationResult(Medicine medicine, float currentStock, float dailyConsumption,
                          float totalRequired, float toBuy, String unitLabel) {
            this.medicine = medicine;
            this.currentStock = currentStock;
            this.dailyConsumption = dailyConsumption;
            this.totalRequired = totalRequired;
            this.toBuy = toBuy;
            this.unitLabel = unitLabel;
        }
    }
}
