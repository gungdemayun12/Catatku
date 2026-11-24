package com.example.pencatatan.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;

/**
 * The task tick used on every to-do card.
 *
 * <p>It deliberately does <b>not</b> look like a stock {@code CheckBox}: the
 * reference design is one self-contained control where the <em>whole</em> shape
 * carries the state.</p>
 *
 * <ul>
 *     <li><b>Open</b> — a rounded square outlined in the priority colour.</li>
 *     <li><b>Done</b> — the outline grows into a filled disc in the status
 *     colour and the tick is drawn inside it. Because the colour fills the
 *     circle that encloses the box, "done" reads at a glance from across the
 *     list instead of from a tiny 18dp checkbox glyph.</li>
 * </ul>
 *
 * <p>It is a plain {@link View} with a single tap target, so it can never emit
 * the burst of change callbacks a real {@code CheckBox} produces while it is
 * rebound — that used to trigger several completion writes.</p>
 */
public class AnimatedCheckBox extends View {

    /** Height the control occupies, keeping a comfortable touch target. */
    private static final float TOUCH_SIZE_DP = 40f;
    /** Diameter of the drawn shape. */
    private static final float SIZE_DP = 24f;
    private static final float STROKE_DP = 2f;
    private static final long DURATION_MS = 220L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tickPath = new Path();
    private final Path tickSegment = new Path();
    private final PathMeasure pathMeasure = new PathMeasure();
    private final RectF shapeRect = new RectF();

    private boolean checked;
    private boolean locked;
    private int idleColor;
    private int activeColor;

    /** 0 = square outline, 1 = filled disc with tick. */
    private float progress;

    @Nullable
    private ValueAnimator animator;

    public interface OnToggleListener {
        void onToggle(AnimatedCheckBox view, boolean checked);
    }

    @Nullable
    private OnToggleListener toggleListener;

    public AnimatedCheckBox(Context context) {
        this(context, null);
    }

    public AnimatedCheckBox(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnimatedCheckBox(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(STROKE_DP * density);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setStrokeJoin(Paint.Join.ROUND);
        tickPaint.setColor(Color.WHITE);
        tickPaint.setStrokeWidth(2.3f * density);

        idleColor = ContextCompat.getColor(context, R.color.color_outline);
        activeColor = ContextCompat.getColor(context, R.color.color_primary);
        syncPaintColors();

        setClickable(true);
        setFocusable(true);
        setBackgroundResource(R.drawable.bg_check_touch);
        setContentDescription(context.getString(R.string.todo_mark_done));
    }

    // ------------------------------------------------------------------ API

    /** Colour of the outline while the task is still open. */
    public void setIdleColor(int color) {
        if (idleColor == color) return;
        idleColor = color;
        syncPaintColors();
        invalidate();
    }

    /** Colour of the filled disc once the task is done. */
    public void setActiveColor(int color) {
        if (activeColor == color) return;
        activeColor = color;
        syncPaintColors();
        invalidate();
    }

    public void setOnToggleListener(@Nullable OnToggleListener listener) {
        toggleListener = listener;
    }

    /**
     * Paints the state without notifying the listener, so a list rebind can
     * never be mistaken for a user tap.
     *
     * @param animate play the morph — used for a deliberate tap only
     */
    public void setChecked(boolean checked, boolean animate) {
        boolean changed = this.checked != checked;
        this.checked = checked;
        float target = checked ? 1f : 0f;
        if (!animate || !changed) {
            cancelAnimator();
            progress = target;
            syncPaintColors();
            invalidate();
            return;
        }
        cancelAnimator();
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            syncPaintColors();
            invalidate();
        });
        animator.start();
    }

    public boolean isChecked() {
        return checked;
    }

    /** A finished task keeps its tick but stops accepting taps. */
    public void setLocked(boolean locked) {
        this.locked = locked;
        setEnabled(!locked);
        setClickable(!locked);
        // Never grey a finished task out: a dimmed tick reads as broken/buggy.
        setAlpha(1f);
    }

    /** Momentary press feedback; the tick itself is not a toggle any more. */
    public void pulse() {
        animate().cancel();
        animate().scaleX(0.88f).scaleY(0.88f).setDuration(90L)
                .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(140L).start())
                .start();
    }

    // --------------------------------------------------------------- events

    @Override
    public boolean performClick() {
        super.performClick();
        if (locked) return true;
        setChecked(!checked, true);
        if (toggleListener != null) toggleListener.onToggle(this, checked);
        return true;
    }

    // --------------------------------------------------------------- layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int desired = Math.round(TOUCH_SIZE_DP * density);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(Math.max(width, desired / 2), Math.max(height, desired / 2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float size = SIZE_DP * density;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        shapeRect.set(left, top, left + size, top + size);

        float radius = size / 2f;
        float strokeInset = strokePaint.getStrokeWidth() / 2f;

        RectF ring = new RectF(shapeRect);
        ring.inset(strokeInset, strokeInset);
        float corner = radius * progress;

        // 1. Outline: the square's corners round off until they become the rim
        //    of a circle, so the square turns into the enclosing disc.
        canvas.drawRoundRect(ring, corner, corner, strokePaint);

        // 2. Fill washes in, sealing the disc.
        if (progress > 0.02f) {
            canvas.drawRoundRect(ring, corner, corner, fillPaint);
        }

        // 3. Tick, drawn along its own length so it "writes" itself.
        if (progress > 0.12f) {
            float fraction = Math.min(1f, (progress - 0.12f) / 0.88f);
            float w = shapeRect.width();
            tickPath.reset();
            tickPath.moveTo(left + w * 0.29f, top + w * 0.53f);
            tickPath.lineTo(left + w * 0.44f, top + w * 0.68f);
            tickPath.lineTo(left + w * 0.72f, top + w * 0.36f);
            pathMeasure.setPath(tickPath, false);
            tickSegment.reset();
            pathMeasure.getSegment(0f, pathMeasure.getLength() * fraction, tickSegment, true);
            canvas.drawPath(tickSegment, tickPaint);
        }
    }

    private void syncPaintColors() {
        int color = blend(idleColor, activeColor, progress);
        strokePaint.setColor(color);
        fillPaint.setColor(color);
    }

    private static int blend(int from, int to, float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }

    private void cancelAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimator();
        super.onDetachedFromWindow();
    }
}
