package com.example.pencatatan.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.google.android.material.color.MaterialColors;

/**
 * Dependency-free donut chart for the monthly spending report.
 *
 * <p>Visual notes: slices are drawn with round caps on a soft track ring, a thin
 * white "card" ring separates the ring from the centre label, and the whole ring
 * animates in from 0 degrees so the report feels alive without being noisy.</p>
 */
public class DonutChartView extends View {

    private static final float GAP_DEGREES = 2.0f;
    private static final long ANIM_DURATION = 620L;

    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private float[] values = new float[0];
    private int[] colors = new int[0];
    private String centerValue = "";
    private String centerCaption = "";

    private float progress = 1f;
    private ValueAnimator animator;

    public DonutChartView(Context context) {
        this(context, null);
    }

    public DonutChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DonutChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        slicePaint.setStyle(Paint.Style.STROKE);
        slicePaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.BUTT);
        trackPaint.setColor(resolveColor(com.google.android.material.R.attr.colorSurfaceVariant,
                R.color.chart_track));
        centerRingPaint.setStyle(Paint.Style.FILL);
        centerRingPaint.setColor(resolveColor(com.google.android.material.R.attr.colorSurface,
                R.color.color_surface));
        valuePaint.setColor(resolveColor(com.google.android.material.R.attr.colorOnSurface,
                R.color.color_on_surface));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);
        captionPaint.setColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
                R.color.color_on_surface_variant));
        captionPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(float[] values, int[] colors, String centerValue, String centerCaption) {
        this.values = values == null ? new float[0] : values;
        this.colors = colors == null ? new int[0] : colors;
        this.centerValue = centerValue == null ? "" : centerValue;
        this.centerCaption = centerCaption == null ? "" : centerCaption;
        animateIn();
        invalidate();
    }

    /** Small "pop" animation so the ring sweeps in when the report loads. */
    public void animateIn() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!isAttachedToWindow()) {
            progress = 1f;
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIM_DURATION);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    private int resolveColor(int materialAttr, int fallbackRes) {
        return MaterialColors.getColor(this, materialAttr,
                ContextCompat.getColor(getContext(), fallbackRes));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        float stroke = Math.max(dp(15), size * 0.115f);
        float inset = stroke / 2f + dp(5);
        arc.set(getWidth() / 2f - size / 2f + inset,
                getHeight() / 2f - size / 2f + inset,
                getWidth() / 2f + size / 2f - inset,
                getHeight() / 2f + size / 2f - inset);

        trackPaint.setStrokeWidth(stroke);
        canvas.drawArc(arc, 0, 360, false, trackPaint);

        float total = 0f;
        for (float value : values) total += Math.max(0f, value);
        if (total <= 0f) {
            drawCenter(canvas, stroke);
            return;
        }

        slicePaint.setStrokeWidth(stroke);
        float start = -90f;
        float limit = progress * 360f;
        for (int i = 0; i < values.length; i++) {
            float value = Math.max(0f, values[i]);
            if (value <= 0f) continue;
            float sweep = 360f * (value / total);
            if (start + 90f >= limit) break;
            float visible = Math.min(sweep, limit - (start + 90f));
            slicePaint.setColor(colors.length == 0
                    ? ContextCompat.getColor(getContext(), R.color.chart_palette_1)
                    : colors[i % colors.length]);
            // Round caps want a little breathing room so neighbouring slices
            // never visually merge into one another.
            float drawSweep = Math.max(1f, visible - GAP_DEGREES);
            canvas.drawArc(arc, start + GAP_DEGREES / 2f, drawSweep, false, slicePaint);
            start += sweep;
        }
        drawCenter(canvas, stroke);
    }

    private void drawCenter(Canvas canvas, float stroke) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float innerRadius = Math.min(getWidth(), getHeight()) / 2f - stroke - dp(5);
        float maxWidth = Math.max(dp(40), innerRadius * 1.55f);

        // A soft disc behind the label keeps long currency values legible.
        centerRingPaint.setAlpha(255);
        canvas.drawCircle(centerX, centerY, Math.max(dp(10), innerRadius - dp(3)), centerRingPaint);

        float valueSize = dp(20);
        valuePaint.setTextSize(valueSize);
        while (valuePaint.measureText(centerValue) > maxWidth && valueSize > dp(11)) {
            valueSize -= dp(1);
            valuePaint.setTextSize(valueSize);
        }
        captionPaint.setTextSize(dp(9.5f));
        captionPaint.setTextAlign(Paint.Align.CENTER);

        float valueY = centerY + valueSize * 0.18f - dp(3);
        canvas.drawText(centerValue, centerX, valueY, valuePaint);
        canvas.drawText(centerCaption, centerX, valueY + dp(15), captionPaint);
    }

    /** Utility used by callers that want a translucent slice colour. */
    public static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * Math.min(1f, Math.max(0f, alpha))),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
