package com.example.pencatatan.auth;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.pencatatan.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Wires live (as-you-type) validation into a {@link TextInputLayout}.
 *
 * <p>Behaviour that matters for a calm form:
 * <ul>
 *   <li>A field that was never touched only shows the green check, never a red error.</li>
 *   <li>Once a field is touched (typing in it or submitting), errors update on every keystroke.</li>
 *   <li>Errors never block typing and never steal focus.</li>
 * </ul>
 */
final class LiveValidation implements TextWatcher {

    interface TextValue {
        String get();
    }

    interface Rule {
        /** @return 0 when the value is fine, otherwise a string resource for the message. */
        @StringRes
        int check(String value);

        /** Password fields keep their show/hide toggle instead of the check icon. */
        default boolean showSuccessIcon() {
            return true;
        }
    }

    private final TextInputEditText input;
    private final TextInputLayout layout;
    private final Rule rule;
    private boolean touched;
    private Runnable onChanged;

    private LiveValidation(TextInputEditText input, Rule rule) {
        this.input = input;
        this.layout = (TextInputLayout) input.getParent().getParent();
        this.rule = rule;
        input.addTextChangedListener(this);
        // Moving focus away (including the IME next/done action) also counts as touched.
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                touched = true;
                validate();
            }
        });
    }

    static LiveValidation attach(TextInputEditText input, Rule rule) {
        return new LiveValidation(input, rule);
    }

    LiveValidation onChange(Runnable callback) {
        this.onChanged = callback;
        return this;
    }

    void touch() {
        touched = true;
        validate();
    }

    boolean isTouched() {
        return touched;
    }

    String value() {
        Editable text = input.getText();
        return text == null ? "" : text.toString();
    }

    boolean isEmpty() {
        return value().trim().isEmpty();
    }

    /** @return true when this field currently passes its rule. */
    boolean validate() {
        int error = rule.check(value());
        boolean valid = error == 0;
        if (valid) {
            layout.setError(null);
            boolean showCheck = touched && !isEmpty() && rule.showSuccessIcon();
            layout.setEndIconMode(showCheck
                    ? TextInputLayout.END_ICON_CUSTOM : TextInputLayout.END_ICON_NONE);
            if (showCheck) {
                layout.setEndIconDrawable(R.drawable.ic_check_circle);
                layout.setEndIconContentDescription(
                        input.getContext().getString(R.string.password_confirm_ok));
                View icon = layout.findViewById(
                        com.google.android.material.R.id.text_input_end_icon);
                if (icon != null) icon.setEnabled(false);
            }
        } else if (touched) {
            layout.setError(input.getContext().getString(error));
        } else {
            layout.setError(null);
        }
        return valid;
    }

    // ------------------------------------------------------- TextWatcher

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (!touched && !isEmpty()) touched = true;
        validate();
        if (onChanged != null) onChanged.run();
    }

    // -------------------------------------------------- built-in rules

    static final class PasswordRule implements Rule {
        @Nullable
        private final TextValue identity;

        PasswordRule(@Nullable TextValue identity) {
            this.identity = identity;
        }

        @Override
        public int check(String value) {
            return AuthValidator.passwordError(value, identity == null ? null : identity.get());
        }

        @Override
        public boolean showSuccessIcon() {
            return false;
        }
    }

    static final class ConfirmRule implements Rule {
        private final TextValue password;

        ConfirmRule(TextValue password) {
            this.password = password;
        }

        @Override
        public int check(String value) {
            return AuthValidator.confirmError(password.get(), value);
        }

        @Override
        public boolean showSuccessIcon() {
            return false;
        }
    }

    /** Login cannot know the account's rules, so only the essentials are enforced there. */
    static final class LoginPasswordRule implements Rule {
        @Override
        public int check(String value) {
            String pwd = value == null ? "" : value;
            if (pwd.isEmpty()) return R.string.password_required;
            if (pwd.length() < AuthValidator.PASSWORD_MIN) return R.string.password_shortest;
            if (pwd.length() > AuthValidator.PASSWORD_MAX) return R.string.password_too_long;
            return 0;
        }

        @Override
        public boolean showSuccessIcon() {
            return false;
        }
    }

    static final class NameRule implements Rule {
        @Override
        public int check(String value) {
            return AuthValidator.nameError(value);
        }
    }

    private static final class EmailRule implements Rule {
        @Override
        public int check(String value) {
            String email = value == null ? "" : value;
            if (email.isEmpty()) return R.string.email_required;
            // Never guess at Caps Lock: an uppercase letter in an address is
            // perfectly valid and must not block the form.
            return AuthValidator.emailError(email);
        }
    }

    static Rule name() {
        return new NameRule();
    }

    static Rule email() {
        return new EmailRule();
    }

    static Rule emailForLogin() {
        return new EmailRule();
    }

    static Rule password(@Nullable TextValue identity) {
        return new PasswordRule(identity);
    }

    static Rule confirm(TextValue password) {
        return new ConfirmRule(password);
    }

    static Rule loginPassword() {
        return new LoginPasswordRule();
    }
}
