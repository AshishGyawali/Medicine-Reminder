package com.example.medicinereminderapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.LinkedHashSet;
import java.util.Set;

public class DayOfWeekPicker extends LinearLayout {
    private static final String[] LABELS = {"आ", "सो", "मं", "बु", "बि", "शु", "श"};
    private final TextView[] bubbles = new TextView[7];

    public DayOfWeekPicker(Context context) {
        super(context);
        init();
    }

    public DayOfWeekPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DayOfWeekPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        int size = dp(40);
        int gap = dp(6);
        ColorStateList textColors = ContextCompat.getColorStateList(getContext(), R.color.day_bubble_text);
        for (int i = 0; i < 7; i++) {
            TextView tv = new TextView(getContext());
            tv.setText(LABELS[i]);
            tv.setBackgroundResource(R.drawable.bg_day_bubble);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(13);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            if (textColors != null) tv.setTextColor(textColors);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.leftMargin = gap;
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> v.setSelected(!v.isSelected()));
            bubbles[i] = tv;
            addView(tv);
        }
    }

    public void setSelectedDays(Set<Integer> days) {
        for (int i = 0; i < 7; i++) {
            bubbles[i].setSelected(days != null && days.contains(i));
        }
    }

    public Set<Integer> getSelectedDays() {
        Set<Integer> out = new LinkedHashSet<>();
        for (int i = 0; i < 7; i++) {
            if (bubbles[i].isSelected()) out.add(i);
        }
        return out;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
