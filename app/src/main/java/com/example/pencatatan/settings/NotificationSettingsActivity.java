package com.example.pencatatan.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityNotificationSettingsBinding;
import com.example.pencatatan.notification.AlarmCleaner;
import com.example.pencatatan.notification.NotificationHelper;
import com.example.pencatatan.notification.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class NotificationSettingsActivity extends BaseActivity {

    private ActivityNotificationSettingsBinding binding;
    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());

        binding.permissionRow.rowIcon.setImageResource(R.drawable.ic_bell);
        binding.permissionRow.rowTitle.setText(R.string.settings_notification_permission);
        binding.permissionRow.rowSubtitle.setText(R.string.settings_notification_permission_desc);
        binding.permissionRow.getRoot().setOnClickListener(v -> openNotificationSettings());

        binding.exactAlarmRow.rowIcon.setImageResource(R.drawable.ic_clock);
        binding.exactAlarmRow.rowTitle.setText(R.string.settings_exact_alarm);
        binding.exactAlarmRow.rowSubtitle.setText(R.string.settings_exact_alarm_desc);
        binding.exactAlarmRow.getRoot().setOnClickListener(v -> {
            if (ReminderScheduler.canScheduleExact(this)) {
                toast(getString(R.string.allowed));
            } else {
                ReminderScheduler.openExactAlarmSettings(this);
            }
        });

        binding.batteryRow.rowIcon.setImageResource(R.drawable.ic_warning);
        binding.batteryRow.rowTitle.setText(R.string.settings_battery);
        binding.batteryRow.rowSubtitle.setText(R.string.settings_battery_desc);
        binding.batteryRow.getRoot().setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.battery_guide_title)
                .setMessage(R.string.battery_guide_text)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.settings_open_system, (dialog, which) -> openAppSettings())
                .show());

        binding.notifSwitch.setChecked(Prefs.notificationsEnabled(this));
        binding.notifSwitch.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setNotificationsEnabled(this, checked);
            String uid = uid();
            if (uid != null) {
                userRepository.setNotificationsEnabled(uid, checked, new com.example.pencatatan.common.RepoCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(Exception error) {
                    }
                });
                if (checked) AlarmCleaner.rescheduleAll(this, uid);
                else AlarmCleaner.cancelAll(this, uid, () -> {
                });
            }
        });

        refresh();
    }

    private void refresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean exact = ReminderScheduler.canScheduleExact(this);
            binding.exactAlarmRow.rowSubtitle.setText(exact ? R.string.allowed : R.string.not_allowed);
            binding.exactAlarmRow.getRoot().setVisibility(View.VISIBLE);
        } else {
            binding.exactAlarmRow.getRoot().setVisibility(View.GONE);
        }
        binding.permissionRow.rowSubtitle.setText(NotificationHelper.hasPermission(this)
                ? R.string.settings_notification_permission_desc : R.string.notif_permission_denied);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (Exception e) {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
