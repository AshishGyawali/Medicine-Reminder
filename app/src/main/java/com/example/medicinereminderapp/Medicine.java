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
