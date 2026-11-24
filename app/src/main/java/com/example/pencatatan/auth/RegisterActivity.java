package com.example.pencatatan.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseUser;

/**
 * Registration form with strict, real-time validation.
 *
 * <p>Nothing is validated only on submit: the name, email, password strength and confirmation
 * are checked on every keystroke, so the user always knows what still needs fixing. When the
 * account is created Firebase is asked to sign out again and the user is sent to the login
 * screen with the email pre-filled, so they sign in with the credentials they just created.
 */
public class RegisterActivity extends BaseActivity implements AuthRepository.ClassicSignInHost {

    /** Extra consumed by {@link LoginActivity} to show a success banner. */
    static final String EXTRA_REGISTERED_EMAIL = "com.example.pencatatan.extra.REGISTERED_EMAIL";
    static final String EXTRA_REGISTERED_NAME = "com.example.pencatatan.extra.REGISTERED_NAME";

    private ActivityRegisterBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private AuthBanner banner;

    private LiveValidation nameCheck;
    private LiveValidation emailCheck;
    private LiveValidation passwordCheck;
    private LiveValidation confirmCheck;
    private boolean loading = false;

    /** Classic Play Services picker, used when Credential Manager finds no account. */
    private final ActivityResultLauncher<Intent> classicLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> authRepository.handleClassicResult(result.getData()));

    @Override
    public void launchClassicGoogleSignIn(Intent intent) {
        classicLauncher.launch(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyTopInsets(binding.brandHeader);

        banner = new AuthBanner(this, binding.banner,
                binding.bannerIcon, binding.bannerText, binding.bannerClose);

        setUpValidation();

        binding.registerButton.setOnClickListener(v -> attemptRegister());
        binding.googleButton.setOnClickListener(v -> attemptGoogle());
        binding.backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.loginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        binding.confirmInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });

        // Large fonts push "Daftar" below the fold; keep it reachable while typing.
        binding.confirmInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) revealRegisterButton();
        });
    }

    /**
     * Scrolls the form so the primary action stays visible.
     *
     * <p>Unconditional by design: gating on the keyboard left the button off-screen
     * as soon as the user dismissed the keyboard to look for it.</p>
     */
    private void revealRegisterButton() {
        if (binding == null) return;
        binding.registerButton.postDelayed(() -> {
            if (binding == null || isFinishing()) return;
            binding.scroll.smoothScrollTo(0, binding.registerButton.getBottom());
        }, 320);
    }

    // ------------------------------------------------------------ validation

    private void setUpValidation() {
        LiveValidation.TextValue identity = () -> {
            String name = nameCheck == null ? "" : nameCheck.value();
            String email = emailCheck == null ? "" : emailCheck.value();
            return (name + " " + email).trim();
        };

        nameCheck = LiveValidation.attach(binding.nameInput, LiveValidation.name())
                .onChange(this::onFormChanged);
        emailCheck = LiveValidation.attach(binding.emailInput, LiveValidation.email())
                .onChange(this::onFormChanged);
        passwordCheck = LiveValidation.attach(binding.passwordInput,
                        LiveValidation.password(identity))
                .onChange(this::onPasswordChanged);
        confirmCheck = LiveValidation.attach(binding.confirmInput,
                        LiveValidation.confirm(() -> passwordCheck.value()))
                .onChange(this::onFormChanged);

        onPasswordChanged();
    }

    private void onPasswordChanged() {
        if (passwordCheck == null) return;
        updateStrengthMeter();
        // Re-check the confirmation against the new password as well, so a stale
        // "Passwords do not match" never outlives the mismatch.
        if (confirmCheck != null) confirmCheck.validate();
    }

    /**
     * The Register button is never disabled: a disabled button with no obvious
     * reason is exactly what made the form feel broken. Tapping it explains any
     * problem inline, while this callback keeps the strength panel in sync.
     */
    private void onFormChanged() {
        if (loading) return;
        updateStrengthMeter();
    }

    private void updateStrengthMeter() {
        if (passwordCheck == null || binding == null) return;
        String password = passwordCheck.value();
        if (password.isEmpty()) {
            binding.strengthBox.setVisibility(View.GONE);
            return;
        }
        binding.strengthBox.setVisibility(View.VISIBLE);

        String identity = nameCheck.value() + " " + emailCheck.value();
        AuthValidator.Strength strength = AuthValidator.strength(password, identity);

        int color = strengthColor(strength.score);
        binding.strengthLabel.setText(strengthHeadline(strength.score));
        binding.strengthLabel.setTextColor(color);
        tintSegments(strength.score, color);

        // Keep the form calm: only the requirements that are still missing are
        // listed, and once the password is good the checklist disappears.
        boolean showRules = renderRules(strength);
        binding.rulesHeader.setVisibility(showRules ? View.VISIBLE : View.GONE);
        binding.rulesContainer.setVisibility(showRules ? View.VISIBLE : View.GONE);
    }

    /** Four segments: each one lights up as the password gets better. */
    private void tintSegments(int score, int activeColor) {
        View[] segments = {
                binding.strengthSeg1, binding.strengthSeg2,
                binding.strengthSeg3, binding.strengthSeg4
        };
        int active = score <= 1 ? 1 : score == 2 ? 2 : score == 3 ? 3 : 4;
        if (score <= 0) active = 0;
        int inactive = ContextCompat.getColor(this, R.color.color_surface_variant);
        for (int i = 0; i < segments.length; i++) {
            segments[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    i < active ? activeColor : inactive));
        }
    }

    private int strengthHeadline(int score) {
        if (score <= 1) return R.string.strength_pw_very_weak;
        if (score == 2) return R.string.strength_pw_weak;
        if (score == 3) return R.string.strength_pw_fair;
        if (score == 4) return R.string.strength_pw_good;
        return R.string.strength_pw_strong;
    }

    /**
     * Shows only the requirements that are still missing, and only the ones that actually
     * block the password. The two "bonus tips" (no repeats, no sequences) are omitted so the
     * checklist can never contradict a password the form already accepts.
     *
     * @return true when there is something to show, false when the password is good.
     */
    private boolean renderRules(AuthValidator.Strength strength) {
        java.util.List<AuthValidator.Rule> missing = new java.util.ArrayList<>();
        for (AuthValidator.Rule rule : strength.rules()) {
            // Only required rules are listed: showing a failing "tip" next to a
            // password the form happily accepts is what made the form look broken.
            if (rule.required && !rule.met) missing.add(rule);
        }
        LinearLayout container = binding.rulesContainer;
        if (missing.isEmpty()) {
            container.removeAllViews();
            return false;
        }
        if (container.getChildCount() != missing.size()) {
            container.removeAllViews();
            for (int i = 0; i < missing.size(); i++) {
                container.addView(buildRuleRow());
            }
        }
        for (int i = 0; i < missing.size(); i++) {
            AuthValidator.Rule rule = missing.get(i);
            LinearLayout row = (LinearLayout) container.getChildAt(i);
            TextView icon = (TextView) row.getChildAt(0);
            TextView label = (TextView) row.getChildAt(1);
            icon.setText("•");
            icon.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface_variant));
            label.setText(rule.label);
            label.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface_variant));
        }
        binding.rulesHeader.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface));
        return true;
    }

    private View buildRuleRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(2));

        TextView icon = new TextView(this);
        icon.setTextSize(12);
        icon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        icon.setWidth(dp(18));
        row.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setTextSize(12);
        label.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(dp(2));
        row.addView(label, params);
        return row;
    }

    private int strengthColor(int score) {
        if (score <= 1) return ContextCompat.getColor(this, R.color.expense);
        if (score == 2) return ContextCompat.getColor(this, R.color.warning);
        if (score == 3) return ContextCompat.getColor(this, R.color.priority_medium);
        return ContextCompat.getColor(this, R.color.income);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- submit

    private void attemptRegister() {
        if (loading) return;
        banner.hide();

        // Touch everything so any remaining problem is explained exactly where it lives.
        nameCheck.touch();
        emailCheck.touch();
        passwordCheck.touch();
        confirmCheck.touch();

        String name = nameCheck.value().trim();
        String email = emailCheck.value().trim();
        String password = passwordCheck.value();

        boolean valid = nameCheck.validate()
                & emailCheck.validate()
                & passwordCheck.validate()
                & confirmCheck.validate();
        if (!valid) {
            AuthBanner.shake(binding.getRoot());
            // Name the field instead of a vague "check the highlighted fields": the
            // button used to look dead because nothing said what was wrong.
            banner.error(getString(fieldProblem()));
            focusFirstInvalid();
            return;
        }

        hideKeyboard();
        setLoading(true);
        authRepository.register(name, email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                // Keep whatever appearance the user already chose on this device.
                // Signing up must never silently switch the app back to English or
                // to the system theme.
                Prefs.setMirroredProfile(RegisterActivity.this, "todo",
                        Prefs.getTheme(RegisterActivity.this),
                        Prefs.getLanguage(RegisterActivity.this),
                        true,
                        Prefs.getCurrency(RegisterActivity.this));
                setLoading(false);
                // Firebase already signed the new user in; drop it so the login form is used.
                authRepository.signOutLocally();
                goToLogin(email, name);
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                String message = getString(AuthErrorMapper.messageRes(error));
                banner.error(message);
                AuthBanner.shake(binding.getRoot());
                if (AuthErrorMapper.messageRes(error) == R.string.err_email_in_use) {
                    binding.emailLayout.setError(message);
                    binding.emailInput.requestFocus();
                }
            }
        });
    }

    private void goToLogin(String email, String name) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(EXTRA_REGISTERED_EMAIL, email);
        intent.putExtra(EXTRA_REGISTERED_NAME, name);
        startActivity(intent);
        finish();
    }

    /**
     * The first rule that still fails, as a ready-to-show message. Returning the concrete
     * rule (instead of "check the highlighted fields") is what makes the button feel alive:
     * the user immediately reads what to type.
     */
    private int fieldProblem() {
        String identity = nameCheck.value() + " " + emailCheck.value();
        int error = AuthValidator.nameError(nameCheck.value());
        if (error != 0) return error;
        error = AuthValidator.emailError(emailCheck.value());
        if (error != 0) return error;
        error = AuthValidator.passwordError(passwordCheck.value(), identity);
        if (error != 0) return error;
        error = AuthValidator.confirmError(passwordCheck.value(), confirmCheck.value());
        if (error != 0) return error;
        return R.string.login_needs_attention;
    }

    private void focusFirstInvalid() {
        if (AuthValidator.nameError(nameCheck.value()) != 0) {
            binding.nameInput.requestFocus();
        } else if (AuthValidator.emailError(emailCheck.value()) != 0) {
            binding.emailInput.requestFocus();
        } else if (AuthValidator.passwordError(passwordCheck.value(),
                nameCheck.value() + " " + emailCheck.value()) != 0) {
            binding.passwordInput.requestFocus();
        } else {
            binding.confirmInput.requestFocus();
        }
    }

    private void attemptGoogle() {
        if (loading) return;
        banner.hide();
        setLoading(true);
        authRepository.signInWithGoogle(this, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                Intent intent = new Intent(RegisterActivity.this,
                        com.example.pencatatan.home.HomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                banner.error(getString(AuthErrorMapper.messageRes(error)));
            }
        });
    }

    // ----------------------------------------------------------------- misc

    private void setLoading(boolean value) {
        loading = value;
        binding.progress.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.progressCard.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.progressScrim.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.registerButton.setEnabled(!value);
        binding.googleButton.setEnabled(!value);
        binding.loginLink.setEnabled(!value);
        binding.nameInput.setEnabled(!value);
        binding.emailInput.setEnabled(!value);
        binding.passwordInput.setEnabled(!value);
        binding.confirmInput.setEnabled(!value);
        binding.registerButton.setText(value ? R.string.register_loading : R.string.register);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        focused.clearFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
