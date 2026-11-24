package com.example.pencatatan;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.example.pencatatan.auth.AuthRepository;
import com.example.pencatatan.common.Appearance;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Navigator;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivitySplashBinding;
import com.example.pencatatan.home.HomeActivity;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends BaseActivity {

    private ActivitySplashBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private boolean routed = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        animateIntro();
        // Start the countdown only once the branding is actually on screen. A
        // cold start can take longer than the intended splash duration, and a
        // timer started in onCreate would then expire before the first frame —
        // the user would see an empty gradient and never the logo.
        scheduleCheckSession();
    }

    private void scheduleCheckSession() {
        binding.getRoot().getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        binding.getRoot().getViewTreeObserver().removeOnPreDrawListener(this);
                        // One frame has been committed, so the splash is visible now.
                        new Handler(Looper.getMainLooper()).postDelayed(SplashActivity.this::checkSession, 1800);
                        return true;
                    }
                });
    }

    private void animateIntro() {
        // Outer glow - slow fade in
        binding.glowOuter.setAlpha(0f);
        binding.glowOuter.setScaleX(0.6f);
        binding.glowOuter.setScaleY(0.6f);
        binding.glowOuter.animate()
                .alpha(0.4f).scaleX(1f).scaleY(1f)
                .setStartDelay(0)
                .setDuration(1200)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Inner glow
        binding.glow.setScaleX(0.72f);
        binding.glow.setScaleY(0.72f);
        binding.glow.animate()
                .alpha(0.9f).scaleX(1f).scaleY(1f)
                .setStartDelay(120)
                .setDuration(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Logo badge - pop in with overshoot
        binding.logoBadge.setAlpha(0f);
        binding.logoBadge.setScaleX(0.4f);
        binding.logoBadge.setScaleY(0.4f);
        binding.logoBadge.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(100)
                .setDuration(700)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        fadeUp(binding.appTitle, 300);
        fadeUp(binding.taglineText, 420);
        fadeUp(binding.dotsRow, 560);
        fadeUp(binding.progress, 600);
        fadeUp(binding.loadingText, 650);

        startGlowBreathe();
        startOuterGlowRotation();
        startLogoPulse();
        startDotShimmer();
    }

    private void startGlowBreathe() {
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0.4f);
        ObjectAnimator breathe = ObjectAnimator.ofPropertyValuesHolder(binding.glow,
                scaleX, scaleY, alpha);
        breathe.setDuration(2200);
        breathe.setStartDelay(1200);
        breathe.setRepeatCount(ObjectAnimator.INFINITE);
        breathe.setRepeatMode(ObjectAnimator.REVERSE);
        breathe.setInterpolator(new AccelerateDecelerateInterpolator());
        breathe.start();
    }

    private void startOuterGlowRotation() {
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0.15f);
        ObjectAnimator outerBreathe = ObjectAnimator.ofPropertyValuesHolder(binding.glowOuter,
                scaleX, scaleY, alpha);
        outerBreathe.setDuration(3000);
        outerBreathe.setStartDelay(1400);
        outerBreathe.setRepeatCount(ObjectAnimator.INFINITE);
        outerBreathe.setRepeatMode(ObjectAnimator.REVERSE);
        outerBreathe.setInterpolator(new AccelerateDecelerateInterpolator());
        outerBreathe.start();
    }

    private void startLogoPulse() {
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f);
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(binding.logoBadge, scaleX, scaleY);
        pulse.setDuration(1000);
        pulse.setStartDelay(900);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
    }

    /** Cascading dot shimmer: dot1 → dot2 → dot3 → repeat */
    private void startDotShimmer() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            shimmerDot(binding.dot1, 0);
            shimmerDot(binding.dot2, 200);
            shimmerDot(binding.dot3, 400);
        }, 800);
    }

    private void shimmerDot(View dot, long delay) {
        Runnable cycle = new Runnable() {
            @Override
            public void run() {
                dot.animate()
                        .alpha(1f)
                        .scaleX(1.4f)
                        .scaleY(1.4f)
                        .setDuration(300)
                        .setStartDelay(delay)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> dot.animate()
                                .alpha(0.3f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(300)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .withEndAction(() -> new Handler(Looper.getMainLooper())
                                        .postDelayed(this, 600))
                                .start())
                        .start();
            }
        };
        new Handler(Looper.getMainLooper()).postDelayed(cycle, 800 + delay);
    }

    private void fadeUp(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(28));
        view.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(delay)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void checkSession() {
        if (routed) return;
        FirebaseUser user = authRepository.currentUser();
        if (user == null) {
            routed = true;
            startActivity(new Intent(this, com.example.pencatatan.auth.LoginActivity.class));
            finish();
            return;
        }
        userRepository.get(user.getUid(), new UserRepository.UserListener() {
            @Override
            public void onUser(User model) {
                if (routed) return;
                routed = true;
                Prefs.setMirroredProfile(SplashActivity.this, model.getLastFeature(),
                        model.getThemeMode(), model.getLanguage(),
                        model.isNotificationsEnabled(), model.getCurrency());
                Appearance.applyTheme(model.getThemeMode());
                Appearance.applyLanguage(model.getLanguage());
                // Always land on Home: the user picks To-Do or Keuangan from there.
                Navigator.routeAfterAuth(SplashActivity.this);
            }

            @Override
            public void onError(Exception error) {
                if (routed) return;
                routed = true;
                userRepository.ensureProfile(user, user.getDisplayName(), null, new com.example.pencatatan.common.RepoCallback() {
                    @Override
                    public void onSuccess() {
                        Navigator.routeAfterAuth(SplashActivity.this);
                    }

                    @Override
                    public void onError(Exception e) {
                        routed = true;
                        startActivity(new Intent(SplashActivity.this, HomeActivity.class));
                        finish();
                    }
                });
            }
        });
    }
}
