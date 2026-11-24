package com.example.pencatatan.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.google.android.material.color.MaterialColors;

/**
 * Small, dependency-free bar chart used by the To-Do statistics screen.
 *
 * <p>Bars grow from the baseline with a short ease-out animation and use a soft
 * vertical gradient (solid at the top, slightly lighter at the base) so the chart
 * reads as a modern, polished widget rather than a flat block diagram.</p>
 */
public class BarChartView extends View {

    private static final long ANIM_DURATION = 520L;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private String[] labels = new String[0];
    private float[] values = new float[0];
    private int barColor;
    private float progress = 1f;
    private ValueAnimator animator;
    private Shader barShader;
    private int shaderTop = -1;
    private int shaderBottom = -1;
    private int shaderColor;

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        barColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.color_primary));
        labelPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(context, R.color.color_on_surface_variant)));
        labelPaint.setTextSize(dp(10));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(context, R.color.color_on_surface)));
        valuePaint.setTextSize(dp(10));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);
        trackPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceVariant,
                ContextCompat.getColor(context, R.color.chart_track)));
    }

    public void setBarColor(int color) {
        barColor = color;
        invalidate();
    }

    public void setValues(String[] labels, float[] values) {
        this.labels = labels == null ? new String[0] : labels;
        this.values = values == null ? new float[0] : values;
        animateIn();
        invalidate();
    }

    private void animateIn() {
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

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** Caches the vertical ramp; only rebuilt when size or colour changes. */
    private Shader shaderFor(float top, float bottom) {
        int t = Math.round(top);
        int b = Math.round(bottom);
        if (barShader == null || t != shaderTop || b != shaderBottom || barColor != shaderColor) {
            barShader = new LinearGradient(0f, top, 0f, bottom, barColor,
                    DonutChartView.withAlpha(barColor, 0.55f), Shader.TileMode.CLAMP);
            shaderTop = t;
            shaderBottom = b;
            shaderColor = barColor;
        }
        return barShader;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = Math.min(labels.length, values.length);
        if (count == 0) return;
        float left = dp(4);
        float right = getWidth() - dp(4);
        float top = dp(16);
        float bottom = getHeight() - dp(20);
        if (bottom <= top) return;

        float max = 0f;
        for (int i = 0; i < count; i++) max = Math.max(max, values[i]);
        if (max <= 0f) max = 1f;

        float slot = (right - left) / count;
        float barWidth = Math.min(dp(26), slot * 0.52f);
        barPaint.setColor(barColor);
        barPaint.setShader(shaderFor(top, bottom));

        for (int i = 0; i < count; i++) {
            float center = left + slot * i + slot / 2f;
            float barLeft = center - barWidth / 2f;
            float barRight = center + barWidth / 2f;
            rect.set(barLeft, top, barRight, bottom);
            trackPaint.setAlpha(70);
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, trackPaint);
            trackPaint.setAlpha(255);

            if (values[i] > 0) {
                float height = (bottom - top) * (values[i] / max) * progress;
                height = Math.max(height, dp(4));
                rect.set(barLeft, bottom - height, barRight, bottom);
                canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint);

                // Only show the value label once the bar is tall enough to host it.
                if (height > dp(16) || progress > 0.98f) {
                    valuePaint.setAlpha((int) (255 * Math.min(1f, progress * 1.4f)));
                    String value = String.valueOf((int) values[i]);
                    canvas.drawText(value, center, bottom - height - dp(6), valuePaint);
                    valuePaint.setAlpha(255);
                }
            }
            canvas.drawText(labels[i] == null ? "" : labels[i], center, getHeight() - dp(4),
                    labelPaint);
        }
        barPaint.setShader(null);
    }
}
