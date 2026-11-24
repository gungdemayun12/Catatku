package com.example.pencatatan.auth;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The small inline notification at the top of the auth cards: success, error, info.
 *
 * <p>Inline (rather than a Toast or a Snackbar) so it stays visible while the reader types,
 * and stays on screen until it is dismissed — except success messages, which fade after a while.
 */
final class AuthBanner {

    enum Kind {
        SUCCESS(R.drawable.bg_banner_success, R.drawable.ic_check_circle, R.color.income),
        ERROR(R.drawable.bg_banner_error, R.drawable.ic_warning, R.color.expense),
        INFO(R.drawable.bg_banner_info, R.drawable.ic_info, R.color.warning);

        @DrawableRes
        final int background;
        @DrawableRes
        final int icon;
        final int tint;

        Kind(int background, int icon, int tint) {
            this.background = background;
            this.icon = icon;
            this.tint = tint;
        }
    }

    private final Activity activity;
    private final View root;
    private final ImageView icon;
    private final TextView text;
    private Runnable autoHide;

    AuthBanner(Activity activity, View root, ImageView icon, TextView text, View close) {
        this.activity = activity;
        this.root = root;
        this.icon = icon;
        this.text = text;
        if (close != null) close.setOnClickListener(v -> hide());
    }

    void show(Kind kind, CharSequence message) {
        show(kind, message, false);
    }

    void show(Kind kind, CharSequence message, boolean autoHideSuccess) {
        if (message == null || message.length() == 0) {
            hide();
            return;
        }
        root.setBackgroundResource(kind.background);
        icon.setImageResource(kind.icon);
        icon.setColorFilter(ContextCompat.getColor(activity, kind.tint));
        text.setText(message);

        boolean wasVisible = root.getVisibility() == View.VISIBLE;
        root.setVisibility(View.VISIBLE);
        if (!wasVisible) {
            root.setAlpha(0f);
            root.setTranslationY(-dp(8));
            root.animate().alpha(1f).translationY(0f).setDuration(180).start();
        }
        scheduleAutoHide(kind == Kind.SUCCESS && autoHideSuccess);
    }

    void success(@StringRes int message) {
        show(Kind.SUCCESS, activity.getString(message));
    }

    void error(@StringRes int message) {
        show(Kind.ERROR, activity.getString(message));
    }

    void error(CharSequence message) {
        show(Kind.ERROR, message);
    }

    void hide() {
        cancelAutoHide();
        if (root.getVisibility() != View.VISIBLE) return;
        ObjectAnimator fade = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).setDuration(140);
        fade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                root.setVisibility(View.GONE);
                root.setAlpha(1f);
            }
        });
        fade.start();
    }

    boolean isVisible() {
        return root.getVisibility() == View.VISIBLE;
    }

    /** Shake the card content so a failed submit is noticed even with the keyboard open. */
    static void shake(@Nullable View view) {
        if (view == null) return;
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                0f, -10f, 10f, -6f, 6f, 0f).setDuration(320);
        animator.start();
    }

    static void dialog(Activity activity, @StringRes int title, CharSequence message,
                       @StringRes int positive) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, null)
                .show();
    }

    private void scheduleAutoHide(boolean enabled) {
        cancelAutoHide();
        if (!enabled) return;
        autoHide = this::hide;
        root.postDelayed(autoHide, 6000);
    }

    private void cancelAutoHide() {
        if (autoHide != null) {
            root.removeCallbacks(autoHide);
            autoHide = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
