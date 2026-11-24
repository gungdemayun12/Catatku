package com.example.pencatatan.auth;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.databinding.ActivityForgotPasswordBinding;
import com.google.firebase.auth.FirebaseUser;

public class ForgotPasswordActivity extends BaseActivity {

    private ActivityForgotPasswordBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private AuthBanner banner;
    private LiveValidation emailCheck;
    private boolean loading = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        banner = new AuthBanner(this, binding.banner,
                binding.bannerIcon, binding.bannerText, binding.bannerClose);

        emailCheck = LiveValidation.attach(binding.emailInput, LiveValidation.emailForLogin())
                .onChange(() -> {
                    if (!loading) binding.resetButton.setEnabled(emailCheck.validate());
                });
        binding.resetButton.setEnabled(false);

        binding.resetButton.setOnClickListener(v -> sendReset());
        binding.emailInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sendReset();
                return true;
            }
            return false;
        });
    }

    private void sendReset() {
        if (loading) return;
        emailCheck.touch();
        String email = emailCheck.value().trim();
        if (!emailCheck.validate()) {
            banner.error(AuthValidator.emailError(email));
            AuthBanner.shake(binding.getRoot());
            binding.emailInput.requestFocus();
            return;
        }
        setLoading(true);
        authRepository.sendPasswordReset(email, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                banner.show(AuthBanner.Kind.SUCCESS, getString(R.string.reset_sent));
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                banner.error(getString(AuthErrorMapper.messageRes(error)));
                AuthBanner.shake(binding.getRoot());
            }
        });
    }

    private void setLoading(boolean value) {
        loading = value;
        binding.progress.setVisibility(value ? View.VISIBLE : View.GONE);
        binding.resetButton.setEnabled(!value && emailCheck.validate());
        binding.emailInput.setEnabled(!value);
        binding.resetButton.setText(value ? R.string.register_loading : R.string.send_reset_link);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
