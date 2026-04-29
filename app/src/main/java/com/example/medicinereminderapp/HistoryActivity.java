package com.example.medicinereminderapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private static final String TAG = "HistoryActivity";
    private AppDatabase db;
    private ListView historyList;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_history);

            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);

            historyList = findViewById(R.id.history_list);
            adapter = new HistoryAdapter(this, new ArrayList<>());
            historyList.setAdapter(adapter);

            db = AppDatabase.get(this);

            loadHistory();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: ", e);
            throw e;
        }
    }

    private void loadHistory() {
        new Thread(() -> {
            try {
                List<Medicine> medicines = db.medicineDao().getAll();
                runOnUiThread(() -> {
                    adapter.setMedicines(medicines);
                    Log.d(TAG, "Loaded medicines: " + medicines.size());
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading history: ", e);
                runOnUiThread(() -> adapter.setMedicines(new ArrayList<>()));
            }
        }).start();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private class HistoryAdapter extends BaseAdapter {
        private List<Medicine> medicines;
        private final LayoutInflater inflater;

        public HistoryAdapter(HistoryActivity context, List<Medicine> medicines) {
            this.medicines = medicines != null ? medicines : new ArrayList<>();
            this.inflater = LayoutInflater.from(context);
        }

        public void setMedicines(List<Medicine> medicines) {
            this.medicines = medicines != null ? medicines : new ArrayList<>();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return medicines.size();
        }

        @Override
        public Medicine getItem(int position) {
            return medicines.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.history_item, parent, false);
                holder = new ViewHolder();
                holder.nameTextView = convertView.findViewById(R.id.medicine_name);
                holder.dosageTextView = convertView.findViewById(R.id.medicine_dosage);
                holder.stockTextView = convertView.findViewById(R.id.medicine_stock);
                holder.daysLeftTextView = convertView.findViewById(R.id.medicine_days_left);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Medicine medicine = medicines.get(position);
            holder.nameTextView.setText(medicine.name);
            holder.dosageTextView.setText(medicine.dosage);
            holder.stockTextView.setText(String.format("%s: %.1f %s",
                    getString(R.string.stock_nepali),
                    medicine.totalStock,
                    medicine.type.equals("TABLET") ? getString(R.string.pills_nepali) : getString(R.string.ml_nepali)));

            float daysLeft = medicine.dailyConsumption > 0 ? medicine.totalStock / medicine.dailyConsumption : 0;
            holder.daysLeftTextView.setText(String.format("%s: %.1f %s",
                    getString(R.string.days_left_nepali),
                    daysLeft,
                    getString(R.string.days_nepali)));

            // Set stock background color
            int backgroundColor;
            if (daysLeft <= 2) {
                backgroundColor = ContextCompat.getColor(HistoryActivity.this, android.R.color.holo_red_light);
            } else if (daysLeft <= 7) {
                backgroundColor = ContextCompat.getColor(HistoryActivity.this, android.R.color.holo_orange_light);
            } else {
                backgroundColor = ContextCompat.getColor(HistoryActivity.this, android.R.color.holo_green_light);
            }
            holder.stockTextView.setBackgroundResource(R.drawable.rounded_background);
            holder.stockTextView.getBackground().setTint(backgroundColor);

            return convertView;
        }

        private class ViewHolder {
            TextView nameTextView, dosageTextView, stockTextView, daysLeftTextView;
        }
    }
}