package com.example.pencatatan.auth;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.pencatatan.R;
import com.example.pencatatan.SplashActivity;
import com.example.pencatatan.common.Appearance;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Navigator;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityLoginBinding;
import com.example.pencatatan.home.HomeActivity;
import com.google.firebase.auth.FirebaseUser;

/**
 * Login form with real-time validation.
 *
 * <p>Arriving from {@link RegisterActivity} the email is pre-filled and a success banner is
 * shown, so the user only needs to type the password they just created.
 */
public class LoginActivity extends BaseActivity implements AuthRepository.ClassicSignInHost {

    private ActivityLoginBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private AuthBanner banner;
    private LiveValidation emailCheck;
    private LiveValidation passwordCheck;
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
        if (authRepository.currentUser() != null && !isFromRegistration()) {
            startActivity(new Intent(this, SplashActivity.class));
            finish();
            return;
        }
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Teal must reach the top edge; the logo inside must not.
        applyTopInsets(binding.brandHeader);

        banner = new AuthBanner(this, binding.banner,
                binding.bannerIcon, binding.bannerText, binding.bannerClose);

        setUpValidation();

        binding.loginButton.setOnClickListener(v -> attemptLogin());
        binding.googleButton.setOnClickListener(v -> attemptGoogle());
        binding.forgotLink.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
        binding.registerLink.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
        binding.passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        // At large font sizes the Sign in button can sit below the fold, and the
        // on-screen keyboard hides even more of it. Scrolling to the button while
        // the keyboard is up means the user never has to hunt for it after typing.
        //
        // Only the PASSWORD field triggers this: scrolling as soon as the email
        // field is focused moved the layout while the user was still typing there,
        // which sent subsequent keystrokes into the wrong field.
        binding.passwordInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) revealLoginButton();
        });

        showRegistrationResult();
    }

    /**
     * Scrolls the form so the primary action is on screen.
     *
     * <p>Deliberately unconditional: an earlier version only scrolled while the
     * keyboard was up, which left the button off-screen again the moment the user
     * dismissed the keyboard to look for it. Waiting a beat lets the IME finish
     * its own show/hide animation so the two do not fight.</p>
     */
    private void revealLoginButton() {
        if (binding == null) return;
        binding.loginButton.postDelayed(() -> {
            if (binding == null || isFinishing()) return;
            binding.scroll.smoothScrollTo(0, binding.loginButton.getBottom());
        }, 320);
    }

    // ------------------------------------------------------------ validation

    private void setUpValidation() {
        emailCheck = LiveValidation.attach(binding.emailInput, LiveValidation.emailForLogin())
                .onChange(this::onFormChanged);
        passwordCheck = LiveValidation.attach(binding.passwordInput,
                        LiveValidation.loginPassword())
                .onChange(this::onFormChanged);

        onFormChanged();
    }

    private void onFormChanged() {
        if (loading) return;
        boolean valid = emailCheck.validate() & passwordCheck.validate();
        binding.loginButton.setEnabled(valid);
    }

    // ------------------------------------------------------- register -> login

    private boolean isFromRegistration() {
        Intent intent = getIntent();
        return intent != null && intent.hasExtra(RegisterActivity.EXTRA_REGISTERED_EMAIL);
    }

    /** Pre-fills the email and tells the user exactly what happened and what to do next. */
    private void showRegistrationResult() {
        if (!isFromRegistration()) return;
        String email = getIntent().getStringExtra(RegisterActivity.EXTRA_REGISTERED_EMAIL);
        String name = getIntent().getStringExtra(RegisterActivity.EXTRA_REGISTERED_NAME);
        if (email == null) email = "";

        binding.emailInput.setText(email);
        binding.emailInput.setSelection(email.length());
        if (name != null) {
            banner.show(AuthBanner.Kind.SUCCESS,
                    getString(R.string.register_success_msg, name, email));
        } else {
            banner.show(AuthBanner.Kind.SUCCESS, getString(R.string.register_success_toast));
        }
        // The password is the only thing left to type.
        binding.passwordInput.requestFocus();
        binding.emailInput.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.passwordInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    // ---------------------------------------------------------------- submit

    private void attemptLogin() {
        if (loading) return;

        emailCheck.touch();
        passwordCheck.touch();

        String email = emailCheck.value().trim();
        String password = passwordCheck.value();

        boolean valid = emailCheck.validate() & passwordCheck.validate();
        if (!valid) {
            AuthBanner.shake(binding.getRoot());
            banner.error(R.string.login_needs_attention);
            if (AuthValidator.emailError(email) != 0) binding.emailInput.requestFocus();
            else binding.passwordInput.requestFocus();
            return;
        }

        hideKeyboard();
        setLoading(true);
        authRepository.login(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                afterAuth(user);
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                int message = AuthErrorMapper.messageRes(error);
                banner.error(message);
                AuthBanner.shake(binding.getRoot());
                // Wrong password or unknown account: point at the password field directly.
                if (message == R.string.err_password_wrong || message == R.string.err_user_not_found
                        || message == R.string.err_google_collision) {
                    binding.passwordInput.requestFocus();
                    binding.passwordInput.selectAll();
                } else if (message == R.string.err_email_invalid) {
                    binding.emailInput.requestFocus();
                }
            }
        });
    }

    private void attemptGoogle() {
        if (loading) return;
        banner.hide();
        setLoading(true);
        authRepository.signInWithGoogle(this, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                afterAuth(user);
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                banner.error(getString(AuthErrorMapper.messageRes(error)));
            }
        });
    }

    private void afterAuth(FirebaseUser firebaseUser) {
        String uid = firebaseUser.getUid();
        userRepository.get(uid, new UserRepository.UserListener() {
            @Override
            public void onUser(User user) {
                Prefs.setMirroredProfile(LoginActivity.this, user.getLastFeature(), user.getThemeMode(),
                        user.getLanguage(), user.isNotificationsEnabled(), user.getCurrency());
                Appearance.applyTheme(user.getThemeMode());
                Appearance.applyLanguage(user.getLanguage());
                setLoading(false);
                toast(getString(R.string.login_success_greeting, displayName(firebaseUser)));
                // Always land on Home: the user picks To-Do or Keuangan from there.
                Navigator.routeAfterAuth(LoginActivity.this);
            }

            @Override
            public void onError(Exception error) {
                userRepository.ensureProfile(firebaseUser, firebaseUser.getDisplayName(),
                        firebaseUser.getPhotoUrl() == null ? null : firebaseUser.getPhotoUrl().toString(),
                        new com.example.pencatatan.common.RepoCallback() {
                            @Override
                            public void onSuccess() {
                                setLoading(false);
                                toast(getString(R.string.login_success_greeting,
                                        displayName(firebaseUser)));
                                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                                finish();
                            }

                            @Override
                            public void onError(Exception e) {
                                setLoading(false);
                                banner.error(getString(AuthErrorMapper.messageRes(e)));
                            }
                        });
            }
        });
    }

    private String displayName(FirebaseUser user) {
        String name = user == null ? null : user.getDisplayName();
        if (name != null && !name.trim().isEmpty()) return name.trim();
        String email = user == null ? null : user.getEmail();
        if (email != null && email.indexOf('@') > 0) {
            return email.substring(0, email.indexOf('@'));
        }
        return getString(R.string.app_name);
    }

    // ----------------------------------------------------------------- misc

    private void setLoading(boolean value) {
        loading = value;
        binding.progress.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.progressCard.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.progressScrim.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.loginButton.setEnabled(!value);
        binding.googleButton.setEnabled(!value);
        binding.forgotLink.setEnabled(!value);
        binding.registerLink.setEnabled(!value);
        binding.emailInput.setEnabled(!value);
        binding.passwordInput.setEnabled(!value);
        binding.loginButton.setText(value ? R.string.login_loading : R.string.login);
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
