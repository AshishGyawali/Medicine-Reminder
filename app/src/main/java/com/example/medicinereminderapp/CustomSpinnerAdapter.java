package com.example.medicinereminderapp;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CustomSpinnerAdapter extends ArrayAdapter<CharSequence> {
    public CustomSpinnerAdapter(@NonNull Context context, int resource, @NonNull CharSequence[] objects) {
        super(context, resource, objects);
    }

    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(0xFF000000);
            ((TextView) view).setTextSize(18);
            ((TextView) view).setTypeface(null, android.graphics.Typeface.BOLD);
        }
        return view;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(0xFF000000);
            ((TextView) view).setTextSize(18);
            ((TextView) view).setTypeface(null, android.graphics.Typeface.BOLD);
        }
        return view;
    }
}