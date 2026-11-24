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
 * Small grouped bar chart showing income and expense across several months.
 *
 * <p>Bars carry a vertical gradient (solid tip, softer base), sit on faint
 * horizontal guides, and animate up from the baseline so switching months feels
 * responsive instead of a hard re-draw.</p>
 */
public class FinanceTrendChartView extends View {

    private static final long ANIM_DURATION = 460L;

    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String[] labels = new String[0];
    private float[] income = new float[0];
    private float[] expense = new float[0];

    private int incomeColor;
    private int expenseColor;
    private float progress = 1f;
    private ValueAnimator animator;
    private Shader incomeShader;
    private Shader expenseShader;
    private int shaderTop = -1;
    private int shaderBottom = -1;

    public FinanceTrendChartView(Context context) {
        this(context, null);
    }

    public FinanceTrendChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FinanceTrendChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        incomeColor = ContextCompat.getColor(context, R.color.income);
        expenseColor = ContextCompat.getColor(context, R.color.expense);
        incomePaint.setColor(incomeColor);
        expensePaint.setColor(expenseColor);
        labelPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(context, R.color.color_on_surface_variant)));
        labelPaint.setTextSize(dp(9.5f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        gridPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant,
                ContextCompat.getColor(context, R.color.chart_grid)));
        gridPaint.setStrokeWidth(dp(0.8f));
    }

    public void setData(String[] labels, float[] income, float[] expense) {
        this.labels = labels == null ? new String[0] : labels;
        this.income = income == null ? new float[0] : income;
        this.expense = expense == null ? new float[0] : expense;
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

    /** Rebuilds the cached vertical ramps only when the bounds change. */
    private Shader ramp(boolean incomeSeries, float top, float bottom) {
        int t = Math.round(top);
        int b = Math.round(bottom);
        if (t != shaderTop || b != shaderBottom) {
            incomeShader = new LinearGradient(0f, top, 0f, bottom, incomeColor,
                    DonutChartView.withAlpha(incomeColor, 0.6f), Shader.TileMode.CLAMP);
            expenseShader = new LinearGradient(0f, top, 0f, bottom, expenseColor,
                    DonutChartView.withAlpha(expenseColor, 0.6f), Shader.TileMode.CLAMP);
            shaderTop = t;
            shaderBottom = b;
        }
        return incomeSeries ? incomeShader : expenseShader;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = Math.min(labels.length, Math.min(income.length, expense.length));
        if (count == 0) return;

        float left = dp(4);
        float right = getWidth() - dp(4);
        float top = dp(12);
        float bottom = getHeight() - dp(20);
        if (bottom <= top) return;

        float max = 0f;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, Math.max(income[i], expense[i]));
        }
        if (max <= 0f) max = 1f;

        for (int i = 0; i < 3; i++) {
            float y = top + (bottom - top) * (i / 2f);
            gridPaint.setAlpha(i == 2 ? 150 : 90);
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        gridPaint.setAlpha(255);

        float slot = (right - left) / count;
        float barWidth = Math.min(dp(13), slot * 0.24f);
        float gap = Math.max(dp(2), barWidth * 0.2f);

        incomePaint.setShader(ramp(true, top, bottom));
        expensePaint.setShader(ramp(false, top, bottom));

        for (int i = 0; i < count; i++) {
            float center = left + slot * i + slot / 2f;
            float incomeHeight = (bottom - top) * (Math.max(0f, income[i]) / max) * progress;
            float expenseHeight = (bottom - top) * (Math.max(0f, expense[i]) / max) * progress;

            drawBar(canvas, center - barWidth - gap / 2f, bottom, barWidth, incomeHeight,
                    incomePaint);
            drawBar(canvas, center + gap / 2f, bottom, barWidth, expenseHeight, expensePaint);
            canvas.drawText(labels[i] == null ? "" : labels[i], center, getHeight() - dp(4),
                    labelPaint);
        }

        incomePaint.setShader(null);
        expensePaint.setShader(null);
    }

    private void drawBar(Canvas canvas, float left, float bottom, float width, float height,
                         Paint paint) {
        rect.set(left, bottom - Math.max(height, dp(3)), left + width, bottom);
        float radius = width / 2f;
        canvas.drawRoundRect(rect, radius, radius, paint);
        // Square off the bottom edge so the bar appears planted on the axis.
        rect.set(left, bottom - radius, left + width, bottom);
        canvas.drawRect(rect, paint);
    }
}
