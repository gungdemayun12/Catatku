package com.example.pencatatan.settings;

import com.example.pencatatan.common.DialogScroll;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.pencatatan.BuildConfig;
import com.example.pencatatan.R;
import com.example.pencatatan.auth.AuthErrorMapper;
import com.example.pencatatan.auth.AuthRepository;
import com.example.pencatatan.common.Appearance;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.common.RepoCallback;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivitySettingsBinding;
import com.example.pencatatan.databinding.DialogEditNameBinding;
import com.example.pencatatan.notification.AlarmCleaner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.ListenerRegistration;

public class SettingsActivity extends BaseActivity {

    /**
     * Pending theme choice. The theme is applied after the screen closes:
     * recreating the Activity while a dialog and a Firestore listener are live
     * is what used to make the selection feel unstable.
     */
    private static final String STATE_PENDING_THEME = "pending_theme";
    private static final String STATE_PENDING_LANGUAGE = "pending_language";

    private ActivitySettingsBinding binding;
    private final UserRepository userRepository = new UserRepository();
    private final FinanceRepository financeRepository = new FinanceRepository();
    private final AuthRepository authRepository = new AuthRepository();
    private ListenerRegistration userListener;
    private User user;
    private boolean deleting;
    private boolean savingPhoto;
    @Nullable
    private String pendingTheme;
    @Nullable
    private String pendingLanguage;

    /** Opens the system photo picker; the URI is valid only until the Activity is recreated. */
    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadPhoto(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        if (savedInstanceState != null) {
            pendingTheme = savedInstanceState.getString(STATE_PENDING_THEME);
            pendingLanguage = savedInstanceState.getString(STATE_PENDING_LANGUAGE);
        }
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());

        setupRows();
        binding.editProfileButton.setOnClickListener(v -> editName());
        binding.avatarBox.setOnClickListener(v -> choosePhoto());
        binding.versionText.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        userListener = userRepository.listen(uid(), new UserRepository.UserListener() {
            @Override
            public void onUser(User data) {
                user = data;
                render();
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void setupRows() {
        binding.rowTheme.rowIcon.setImageResource(R.drawable.ic_palette);
        binding.rowTheme.rowTitle.setText(R.string.settings_theme);
        binding.rowTheme.rowSubtitle.setText(R.string.settings_theme_desc);
        binding.rowTheme.getRoot().setOnClickListener(v -> chooseTheme());

        binding.rowLanguage.rowIcon.setImageResource(R.drawable.ic_language);
        binding.rowLanguage.rowTitle.setText(R.string.settings_language);
        binding.rowLanguage.rowSubtitle.setText(R.string.settings_language_desc);
        binding.rowLanguage.getRoot().setOnClickListener(v -> chooseLanguage());

        binding.rowCurrency.rowIcon.setImageResource(R.drawable.ic_currency);
        binding.rowCurrency.rowTitle.setText(R.string.settings_currency);
        binding.rowCurrency.getRoot().setOnClickListener(v -> chooseCurrency());

        binding.rowNotifications.rowIcon.setImageResource(R.drawable.ic_bell);
        binding.rowNotifications.rowTitle.setText(R.string.settings_notifications);
        binding.rowNotifications.rowSubtitle.setText(R.string.settings_notifications_desc);
        binding.rowNotifications.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, NotificationSettingsActivity.class)));

        binding.rowHaptic.rowIcon.setImageResource(R.drawable.ic_vibrate);
        binding.rowHaptic.rowTitle.setText(R.string.settings_haptic);
        binding.rowHaptic.rowSubtitle.setText(R.string.settings_haptic_desc);
        binding.rowHaptic.rowSwitch.setChecked(Prefs.hapticEnabled(this));
        binding.rowHaptic.getRoot().setOnClickListener(v -> binding.rowHaptic.rowSwitch.toggle());
        binding.rowHaptic.rowSwitch.setOnCheckedChangeListener(
                (button, checked) -> Prefs.setHapticEnabled(this, checked));

        binding.rowAbout.rowIcon.setImageResource(R.drawable.ic_info);
        binding.rowAbout.rowTitle.setText(R.string.settings_about);
        binding.rowAbout.rowSubtitle.setText(R.string.settings_about_desc);
        binding.rowAbout.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        binding.rowLogout.rowIcon.setImageResource(R.drawable.ic_logout);
        binding.rowLogout.rowTitle.setText(R.string.settings_logout);
        binding.rowLogout.rowSubtitle.setText(R.string.settings_logout_msg);
        binding.rowLogout.getRoot().setOnClickListener(v -> logout());

        binding.rowDelete.rowIcon.setImageResource(R.drawable.ic_delete);
        binding.rowDelete.rowTitle.setText(R.string.settings_account_delete);
        binding.rowDelete.rowSubtitle.setText(R.string.settings_account_delete_desc);
        binding.rowDelete.rowTitle.setTextColor(getColor(R.color.expense));
        binding.rowDelete.rowIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                getColor(R.color.expense)));
        binding.rowDelete.getRoot().setOnClickListener(v -> confirmDeleteAccount());
    }

    private void render() {
        if (user == null || binding == null) return;
        binding.nameText.setText(user.safeName());
        binding.emailText.setText(user.getEmail());
        bindAvatar(user.getPhotoUrl());
        binding.rowTheme.rowSubtitle.setText(themeLabel(Prefs.getTheme(this)));
        binding.rowLanguage.rowSubtitle.setText(languageLabel(Prefs.getLanguage(this)));
        binding.rowCurrency.rowSubtitle.setText(Prefs.getCurrency(this));
    }

    /** Shows the uploaded photo when there is one, otherwise the coloured initial. */
    private void bindAvatar(String photoUrl) {
        boolean hasPhoto = ProfilePhoto.apply(binding.avatarImage, photoUrl);
        binding.avatarImage.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        binding.avatarInitial.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
        if (!hasPhoto) binding.avatarInitial.setText(user == null ? "" : user.initial());
    }

    private void choosePhoto() {
        if (savingPhoto) return;
        boolean hasPhoto = user != null && ProfilePhoto.isPhoto(user.getPhotoUrl());
        String[] options = hasPhoto
                ? new String[]{getString(R.string.settings_photo_pick),
                getString(R.string.settings_photo_remove)}
                : new String[]{getString(R.string.settings_photo_pick)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_photo_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        try {
                            pickPhoto.launch("image/*");
                        } catch (Exception error) {
                            toast(getString(R.string.settings_photo_failed));
                        }
                    } else {
                        removePhoto();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void uploadPhoto(Uri uri) {
        if (savingPhoto || uid() == null) return;
        savingPhoto = true;
        toast(getString(R.string.settings_photo_uploading));
        String encoded = ProfilePhoto.encode(this, uri);
        if (encoded == null) {
            savingPhoto = false;
            toast(getString(R.string.settings_photo_failed));
            return;
        }
        userRepository.updatePhotoUrl(uid(), encoded, new RepoCallback() {
            @Override
            public void onSuccess() {
                savingPhoto = false;
                toast(getString(R.string.settings_photo_saved));
            }

            @Override
            public void onError(Exception error) {
                savingPhoto = false;
                toast(getString(R.string.settings_photo_failed));
            }
        });
    }

    private void removePhoto() {
        if (savingPhoto || uid() == null) return;
        savingPhoto = true;
        userRepository.updatePhotoUrl(uid(), null, new RepoCallback() {
            @Override
            public void onSuccess() {
                savingPhoto = false;
                toast(getString(R.string.settings_photo_removed));
            }

            @Override
            public void onError(Exception error) {
                savingPhoto = false;
                toast(getString(R.string.settings_photo_remove_failed));
            }
        });
    }

    private String themeLabel(String value) {
        if (Prefs.THEME_LIGHT.equals(value)) return getString(R.string.theme_light);
        if (Prefs.THEME_DARK.equals(value)) return getString(R.string.theme_dark);
        return getString(R.string.theme_system);
    }

    private String languageLabel(String value) {
        if ("id".equals(value)) return getString(R.string.language_id);
        if ("en".equals(value)) return getString(R.string.language_en);
        return getString(R.string.language_system);
    }

    private void editName() {
        DialogEditNameBinding dialogBinding = DialogEditNameBinding.inflate(getLayoutInflater());
        TextInputEditText input = dialogBinding.nameEdit;
        input.setText(user == null ? "" : user.safeName());
        input.setSelection(input.getText().length());
        dialogBinding.nameLayout.setError(null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_edit_profile_title)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            input.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = input.getText() == null ? "" : input.getText().toString().trim();
                if (name.isEmpty()) {
                    dialogBinding.nameLayout.setError(getString(R.string.name_required));
                    return;
                }
                dialogBinding.nameLayout.setError(null);
                saveName(name);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void saveName(String name) {
        userRepository.updateProfile(uid(), name, null, new RepoCallback() {
            @Override
            public void onSuccess() {
                authRepository.updateDisplayName(name);
                toast(getString(R.string.settings_saved));
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.settings_save_failed));
            }
        });
    }

    private void chooseTheme() {
        String[] values = {Prefs.THEME_LIGHT, Prefs.THEME_DARK, Prefs.THEME_SYSTEM};
        String[] labels = {getString(R.string.theme_light), getString(R.string.theme_dark),
                getString(R.string.theme_system)};
        String current = Prefs.getTheme(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    applyTheme(values[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Stores the theme locally and on the profile, then restarts the screen so
     * every row is drawn with the new palette.
     *
     * <p>The old version wrote it only to {@code SharedPreferences}. On the next
     * sign-in {@code Prefs.setMirroredProfile()} copies the theme back from
     * Firestore, so the user's choice was silently undone — it looked like a
     * setting that never stuck. Writing to both keeps them in sync.</p>
     */
    private void applyTheme(String value) {
        if (value == null || value.equals(Prefs.getTheme(this))) return;
        Prefs.setTheme(this, value);
        if (uid() != null) userRepository.updateTheme(uid(), value, silentCallback());
        pendingTheme = value;
        Appearance.applyTheme(value);
        binding.rowTheme.rowSubtitle.setText(themeLabel(value));
        toast(getString(R.string.settings_saved));
    }

    private void chooseLanguage() {
        String[] values = {Prefs.LANGUAGE_SYSTEM, "id", "en"};
        String[] labels = {getString(R.string.language_system), getString(R.string.language_id),
                getString(R.string.language_en)};
        String current = Prefs.getLanguage(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    applyLanguage(values[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Same reasoning as {@link #applyTheme}: local mirror plus profile. */
    private void applyLanguage(String value) {
        if (value == null || value.equals(Prefs.getLanguage(this))) return;
        Prefs.setLanguage(this, value);
        if (uid() != null) userRepository.updateLanguage(uid(), value, silentCallback());
        pendingLanguage = value;
        binding.rowLanguage.rowSubtitle.setText(languageLabel(value));
        Appearance.applyLanguage(value);
    }

    private void chooseCurrency() {
        showCurrencyDialog();
    }

    private void showCurrencyDialog() {
        String[] options = new String[Money.SUPPORTED.length];
        for (int i = 0; i < Money.SUPPORTED.length; i++) {
            options[i] = Money.SUPPORTED[i] + "  " + Money.countryFlag(Money.SUPPORTED[i]);
        }
        String current = Prefs.getCurrency(this);
        int checked = 0;
        for (int i = 0; i < Money.SUPPORTED.length; i++) {
            if (Money.SUPPORTED[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.currency_main)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    applyCurrencyChange(Money.SUPPORTED[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyCurrencyChange(String code) {
        String old = Prefs.getCurrency(this);
        if (code == null || code.equals(old)) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.currency_change_title)
                .setMessage(getString(R.string.currency_change_msg, old, code))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_label, (dialog, which) -> migrateCurrency(old, code))
                .show();
    }

    private void migrateCurrency(String oldCode, String code) {
        toast(getString(R.string.currency_converting));
        financeRepository.migrateBaseCurrency(uid(), oldCode, code,
                new FinanceRepository.MigrateCallback() {
                    @Override
                    public void onSuccess() {
                        Prefs.setCurrency(SettingsActivity.this, code);
                        binding.rowCurrency.rowSubtitle.setText(code);
                        userRepository.updateCurrency(uid(), code, silentCallback());
                        toast(getString(R.string.currency_changed));
                    }

                    @Override
                    public void onError(Exception error) {
                        toast("rate_missing".equals(error.getMessage())
                                ? getString(R.string.currency_rate_required)
                                : getString(R.string.save_failed));
                    }
                });
    }

    private void logout() {
        confirm(getString(R.string.settings_logout_title), getString(R.string.settings_logout_msg),
                getString(R.string.settings_logout), () ->
                        AlarmCleaner.cancelAll(this, uid(), this::finishLogout));
    }

    private void finishLogout() {
        // Google sign-out used to run first and could throw, which left the
        // Firebase session alive and the user staring at an unchanged screen.
        // The Firebase session is the one that matters, so it is cleared first.
        authRepository.logout(this);
        Prefs.clearSession(this);
        toast(getString(R.string.logout_done));
        Intent intent = new Intent(this, com.example.pencatatan.auth.LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ------------------------------------------------------- delete account

    /**
     * Deletes the account, handling Firebase's "requires recent login" rule.
     *
     * <p>{@code delete()} fails with {@code ERROR_REQUIRES_RECENT_LOGIN} when
     * the session is more than a few minutes old. That is not an error the user
     * can do anything about except sign in again, so the previous generic
     * "failed" toast was a dead end. Now the user is offered a re-auth prompt
     * and, once that succeeds, the deletion is retried automatically.</p>
     */
    private void confirmDeleteAccount() {
        EditText input = new EditText(this);
        input.setHint(R.string.delete_account_confirm);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        FrameLayout container = new FrameLayout(this);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, dp(8), padding, 0);
        container.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_account_delete)
                .setMessage(R.string.delete_account_warning)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String word = input.getText().toString().trim();
                    if (!getString(R.string.delete_account_word).equalsIgnoreCase(word)) {
                        input.setError(getString(R.string.delete_account_confirm));
                        return;
                    }
                    if (deleting) return;
                    deleting = true;
                    AlarmCleaner.cancelAll(this, uid(), () -> authRepository.deleteAccount(this,
                            new AuthRepository.AuthCallback() {
                                @Override
                                public void onSuccess(com.google.firebase.auth.FirebaseUser ignoredUser) {
                                    deleting = false;
                                    Prefs.clearSession(SettingsActivity.this);
                                    toast(getString(R.string.delete_account_done));
                                    dialog.dismiss();
                                    Intent intent = new Intent(SettingsActivity.this,
                                            com.example.pencatatan.auth.LoginActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }

                                @Override
                                public void onError(Exception error) {
                                    deleting = false;
                                    // "requires recent login" is the one failure the
                                    // user can actually resolve, so it gets its own path.
                                    if (isRecentLoginRequired(error)) {
                                        dialog.dismiss();
                                        promptReauthenticateThenDelete();
                                        return;
                                    }
                                    toast(getString(R.string.delete_account_failed));
                                }
                            }));
                }));
        dialog.show();
    }

    private static boolean isRecentLoginRequired(Exception error) {
        return error instanceof com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
                || (error != null && error.getMessage() != null
                && error.getMessage().contains("recent"));
    }

    /**
     * Asks for the password again, then retries the deletion.
     *
     * <p>Only e-mail/password accounts are handled: a Google account has no
     * password to type, so those users are told to sign out and back in, which
     * is the only route Firebase offers that the app can instruct them through.</p>
     */
    private void promptReauthenticateThenDelete() {
        com.google.firebase.auth.FirebaseUser current = authRepository.currentUser();
        String email = current == null ? null : current.getEmail();
        boolean passwordAccount = email != null && !email.isEmpty()
                && (current.getProviderData() == null || current.getProviderData().size() <= 1
                || "password".equals(current.getProviderData().get(0).getProviderId()));
        if (!passwordAccount) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_account_reauth_title)
                    .setMessage(R.string.delete_account_reauth_google)
                    .setNegativeButton(R.string.close, null)
                    .show();
            return;
        }
        TextInputEditText password = new TextInputEditText(this);
        password.setHint(R.string.delete_account_password_hint);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);
        FrameLayout container = new FrameLayout(this);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, dp(8), padding, 0);
        container.addView(password);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_account_reauth_title)
                .setMessage(R.string.delete_account_reauth_msg)
                .setView(DialogScroll.wrap(container))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = password.getText() == null ? ""
                            : password.getText().toString();
                    if (value.isEmpty()) {
                        password.setError(getString(R.string.password_required));
                        return;
                    }
                    if (deleting) return;
                    deleting = true;
                    authRepository.reauthenticate(email, value, new AuthRepository.AuthCallback() {
                        @Override
                        public void onSuccess(com.google.firebase.auth.FirebaseUser ignoredUser) {
                            deleting = false;
                            dialog.dismiss();
                            // Signed in freshly: run the whole deletion again.
                            confirmDeleteAccount();
                        }

                        @Override
                        public void onError(Exception error) {
                            deleting = false;
                            toast(getString(AuthErrorMapper.messageRes(error)));
                        }
                    });
                }));
        dialog.show();
    }

    private RepoCallback silentCallback() {
        return new RepoCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onError(Exception error) {
            }
        };
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A theme/language change is only fully visible after a rebuild, so it
        // is applied here — once the choosing dialog is gone — instead of while
        // it was still on screen.
        boolean needsRecreate = (pendingTheme != null
                && pendingTheme.equals(Prefs.getTheme(this)))
                || (pendingLanguage != null && pendingLanguage.equals(Prefs.getLanguage(this)));
        if (!needsRecreate) return;
        pendingTheme = null;
        pendingLanguage = null;
        recreate();
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingTheme != null) outState.putString(STATE_PENDING_THEME, pendingTheme);
        if (pendingLanguage != null) outState.putString(STATE_PENDING_LANGUAGE, pendingLanguage);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
    }
}
