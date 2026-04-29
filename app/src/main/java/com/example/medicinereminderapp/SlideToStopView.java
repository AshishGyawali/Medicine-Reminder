package com.example.medicinereminderapp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public class SlideToStopView extends FrameLayout {
    public interface OnSlideCompleteListener {
        void onSlideComplete();
    }

    private View thumb;
    private TextView label;
    private float startX;
    private float thumbStartX;
    private boolean dragging;
    private OnSlideCompleteListener listener;

    public SlideToStopView(Context context) {
        super(context);
        init(context);
    }

    public SlideToStopView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SlideToStopView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundResource(R.drawable.bg_slide_track);
        setClipChildren(false);

        label = new TextView(context);
        label.setText(R.string.slide_to_stop);
        label.setTextColor(0xFF8E8E93);
        label.setTextSize(18);
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelLp.gravity = android.view.Gravity.CENTER;
        addView(label, labelLp);

        thumb = new View(context);
        thumb.setBackgroundResource(R.drawable.bg_slide_thumb);
        int thumbSize = dp(64);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START;
        thumbLp.leftMargin = dp(6);
        addView(thumb, thumbLp);

        View stopIcon = new View(context);
        stopIcon.setBackgroundResource(R.drawable.ic_stop_square);
        int iconSize = dp(22);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START;
        iconLp.leftMargin = dp(6) + (thumbSize - iconSize) / 2;
        addView(stopIcon, iconLp);

        thumb.setOnTouchListener((v, event) -> handleTouch(event, stopIcon));
    }

    private boolean handleTouch(MotionEvent event, View stopIcon) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getRawX();
                thumbStartX = thumb.getTranslationX();
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                float dx = event.getRawX() - startX;
                float maxTranslate = getWidth() - thumb.getWidth() - dp(12);
                float newX = Math.max(0, Math.min(maxTranslate, thumbStartX + dx));
                thumb.setTranslationX(newX);
                stopIcon.setTranslationX(newX);
                float progress = maxTranslate > 0 ? newX / maxTranslate : 0f;
                label.setAlpha(1f - progress);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                float maxT = getWidth() - thumb.getWidth() - dp(12);
                if (thumb.getTranslationX() >= maxT * 0.85f) {
                    if (listener != null) listener.onSlideComplete();
                } else {
                    animateBack(stopIcon);
                }
                return true;
        }
        return false;
    }

    private void animateBack(View stopIcon) {
        float from = thumb.getTranslationX();
        ValueAnimator anim = ValueAnimator.ofFloat(from, 0f);
        anim.setDuration(200);
        anim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            thumb.setTranslationX(v);
            stopIcon.setTranslationX(v);
            float maxT = getWidth() - thumb.getWidth() - dp(12);
            label.setAlpha(maxT > 0 ? 1f - v / maxT : 1f);
        });
        anim.start();
    }

    public void setOnSlideCompleteListener(OnSlideCompleteListener l) {
        this.listener = l;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
