package com.example.medicinereminderapp;

import android.content.Context;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.List;

public final class TimePickerListHelper {
    private TimePickerListHelper() {}

    public static void render(Context context, LinearLayout container, int count, List<int[]> existing) {
        List<int[]> previous = currentValues(container, existing);
        container.removeAllViews();
        for (int i = 0; i < count; i++) {
            container.addView(buildHeader(context, i));
            container.addView(buildPicker(context, valueAt(previous, i, existing, i)));
        }
    }

    public static List<int[]> readValues(LinearLayout container) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View v = container.getChildAt(i);
            if (v instanceof TimePicker) {
                TimePicker tp = (TimePicker) v;
                int hour = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? tp.getHour() : tp.getCurrentHour();
                int minute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? tp.getMinute() : tp.getCurrentMinute();
                out.add(new int[]{hour, minute});
            }
        }
        return out;
    }

    private static List<int[]> currentValues(LinearLayout container, List<int[]> fallback) {
        List<int[]> out = readValues(container);
        if (out.isEmpty() && fallback != null) out.addAll(fallback);
        return out;
    }

    private static int[] valueAt(List<int[]> previous, int i, List<int[]> fallback, int fallbackIndex) {
        if (previous != null && i < previous.size()) return previous.get(i);
        if (fallback != null && fallbackIndex < fallback.size()) return fallback.get(fallbackIndex);
        return new int[]{8, 0};
    }

    private static TextView buildHeader(Context context, int index) {
        TextView tv = new TextView(context);
        tv.setText(context.getString(R.string.reminder_time_n, index + 1));
        tv.setTextColor(0xFF333333);
        tv.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int top = (int) (12 * context.getResources().getDisplayMetrics().density);
        lp.topMargin = top;
        tv.setLayoutParams(lp);
        return tv;
    }

    private static TimePicker buildPicker(Context context, int[] hm) {
        Context themed = new ContextThemeWrapper(context, R.style.SpinnerTimePickerTheme);
        TimePicker tp = new TimePicker(themed);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try { tp.setIs24HourView(android.text.format.DateFormat.is24HourFormat(context)); } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setHour(hm[0]);
            tp.setMinute(hm[1]);
        } else {
            tp.setCurrentHour(hm[0]);
            tp.setCurrentMinute(hm[1]);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        tp.setLayoutParams(lp);
        return tp;
    }
}
