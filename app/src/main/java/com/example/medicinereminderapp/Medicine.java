package com.example.medicinereminderapp;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity(tableName = "medicine")
public class Medicine {
    public static final int MAX_TIMES_PER_DAY = 12;

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "name", defaultValue = "")
    public String name;

    @ColumnInfo(name = "dosage", defaultValue = "")
    public String dosage;

    @ColumnInfo(name = "type", defaultValue = "TABLET")
    public String type;

    @ColumnInfo(name = "totalStock", defaultValue = "0")
    public float totalStock;

    @ColumnInfo(name = "dailyConsumption", defaultValue = "0")
    public float dailyConsumption;

    @ColumnInfo(name = "hour", defaultValue = "0")
    public int hour;

    @ColumnInfo(name = "minute", defaultValue = "0")
    public int minute;

    @NonNull
    @ColumnInfo(name = "times", defaultValue = "")
    public String times = "";

    @NonNull
    @ColumnInfo(name = "imageUri", defaultValue = "")
    public String imageUri = "";

    @ColumnInfo(name = "active", defaultValue = "1")
    public boolean active = true;

    @NonNull
    @ColumnInfo(name = "daysOfWeek", defaultValue = "0,1,2,3,4,5,6")
    public String daysOfWeek = "0,1,2,3,4,5,6";

    @NonNull
    @ColumnInfo(name = "dosageUnit", defaultValue = "mg")
    public String dosageUnit = "mg";

    private static final String[] DAY_LABELS = {"आ", "सो", "मं", "बु", "बि", "शु", "श"};

    /** Human-readable label for the selected repeat days. */
    public String daysLabel() {
        java.util.Set<Integer> days = parseDays();
        if (days.size() == 7) return "हरेक दिन";
        if (days.isEmpty()) return "एक पटक";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (days.contains(i)) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(DAY_LABELS[i]);
            }
        }
        return sb.toString();
    }

    /** Full dosage display string including unit, e.g. "500 mg" */
    public String dosageWithUnit() {
        if (dosage == null || dosage.isEmpty()) return "";
        String unit = (dosageUnit != null && !dosageUnit.isEmpty()) ? dosageUnit : "";
        return dosage + (unit.isEmpty() ? "" : " " + unit);
    }

    public List<int[]> parseTimes() {
        List<int[]> out = new ArrayList<>();
        if (times != null && !times.isEmpty()) {
            for (String t : times.split(",")) {
                String[] hm = t.split(":");
                if (hm.length == 2) {
                    try {
                        out.add(new int[]{Integer.parseInt(hm[0].trim()), Integer.parseInt(hm[1].trim())});
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (out.isEmpty()) out.add(new int[]{hour, minute});
        return out;
    }

    public static String timesToCsv(List<int[]> hms) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hms.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%02d:%02d", hms.get(i)[0], hms.get(i)[1]));
        }
        return sb.toString();
    }

    public java.util.Set<Integer> parseDays() {
        return parseDaysCsv(daysOfWeek);
    }

    public static java.util.Set<Integer> parseDaysCsv(String csv) {
        java.util.Set<Integer> out = new java.util.LinkedHashSet<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String tok : csv.split(",")) {
            try {
                int v = Integer.parseInt(tok.trim());
                if (v >= 0 && v < 7) out.add(v);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public static String daysToCsv(java.util.Set<Integer> days) {
        if (days == null || days.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < 7; i++) {
            if (days.contains(i)) {
                if (!first) sb.append(',');
                sb.append(i);
                first = false;
            }
        }
        return sb.toString();
    }

    /** Maps Calendar.DAY_OF_WEEK (Sun=1 … Sat=7) to our index (Sun=0 … Sat=6). */
    public static int calendarDayToIndex(int calendarDayOfWeek) {
        return calendarDayOfWeek - 1;
    }

    public static int reminderCountFromConsumption(float dailyConsumption) {
        int n = (int) Math.floor(dailyConsumption);
        if (n < 1) n = 1;
        if (n > MAX_TIMES_PER_DAY) n = MAX_TIMES_PER_DAY;
        return n;
    }

    @Override
    public String toString() {
        return "Medicine{id=" + id + ", name='" + name + "', times='" + times + "'}";
    }
}
