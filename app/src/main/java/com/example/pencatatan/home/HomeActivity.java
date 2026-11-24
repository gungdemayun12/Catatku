package com.example.pencatatan.home;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.Navigator;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityHomeBinding;
import com.example.pencatatan.notification.NotificationHelper;
import com.example.pencatatan.settings.NotificationSettingsActivity;
import com.example.pencatatan.settings.SettingsActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.List;

public class HomeActivity extends BaseActivity {

    private ActivityHomeBinding binding;
    private HomeViewModel viewModel;
    private final UserRepository userRepository = new UserRepository();
    private String baseCurrency = "IDR";

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) toast(getString(R.string.notif_permission_denied));
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            Intent intent = new Intent(this, com.example.pencatatan.auth.LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        viewModel.start();

        binding.settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        binding.avatarBox.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        binding.notifButton.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationSettingsActivity.class)));
        binding.todoCard.setOnClickListener(v -> openFeature(Navigator.FEATURE_TODO));
        binding.financeCard.setOnClickListener(v -> openFeature(Navigator.FEATURE_FINANCE));

        binding.loading.setVisibility(View.VISIBLE);
        observe();
        maybeExplainNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Keeps "spent this month" bound to the real current month, and restores
        // the avatar/name immediately after returning from Settings.
        if (viewModel != null) viewModel.refreshMonth();
    }

    private void observe() {
        viewModel.user().observe(this, user -> {
            if (user == null) return;
            baseCurrency = user.getCurrency() == null ? "IDR" : user.getCurrency();
            boolean hasPhoto = com.example.pencatatan.settings.ProfilePhoto.apply(
                    binding.avatarImage, user.getPhotoUrl());
            binding.avatarImage.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
            binding.avatarInitial.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
            binding.avatarInitial.setText(user.initial());
            binding.userName.setText(user.safeName());
            binding.greeting.setText(greeting());
            binding.notifButton.setAlpha(user.isNotificationsEnabled() ? 1f : 0.45f);
            render(viewModel.todos().getValue(), viewModel.transactions().getValue());
        });
        viewModel.todos().observe(this, todos -> render(todos, viewModel.transactions().getValue()));
        viewModel.transactions().observe(this, transactions ->
                render(viewModel.todos().getValue(), transactions));
    }

    private void render(List<Todo> todos, List<Transaction> transactions) {
        binding.loading.setVisibility(View.GONE);
        int dueToday = 0;
        int overdue = 0;
        if (todos != null) {
            Calendar today = Calendar.getInstance();
            for (Todo todo : todos) {
                if (todo.isCompleted()) continue;
                if (todo.getDueDate() != null) {
                    if (Dates.isSameDay(Dates.calendarOf(todo.getDueDate().toDate()), today)) dueToday++;
                }
                if (todo.isOverdue()) overdue++;
            }
        }
        long expense = 0;
        if (transactions != null) {
            for (Transaction tx : transactions) {
                // Skip transfer legs, exactly like FinanceCalc does: moving money
                // between the user's own wallets is not spending, and counting it
                // would make this figure disagree with the Finance screen.
                if (tx.isTransfer()) continue;
                if (!tx.isIncome()) expense += tx.getAmountBase();
            }
        }

        binding.heroTasksValue.setText(String.valueOf(dueToday));
        binding.heroSpentValue.setText(MoneyText.format(this, baseCurrency, expense));
        if (overdue > 0) {
            binding.heroOverdueBadge.setVisibility(View.VISIBLE);
            binding.heroOverdueBadge.setText(getResources().getQuantityString(
                    R.plurals.plural_tasks, overdue, overdue) + " · " + getString(R.string.overdue));
        } else {
            binding.heroOverdueBadge.setVisibility(View.GONE);
        }

        // Colour the overdue pill so it reads as an alert on the dark hero card.
        binding.heroOverdueBadge.setTextColor(ContextCompat.getColor(this,
                overdue > 0 ? R.color.warning : R.color.white));
        binding.heroOverdueBadge.setAlpha(overdue > 0 ? 1f : 0.9f);

        if (todos == null) {
            binding.todoSummary.setText(R.string.loading);
        } else {
            int open = 0;
            for (Todo todo : todos) if (!todo.isCompleted()) open++;
            binding.todoSummary.setText(getString(R.string.todo_summary, open, overdue));
        }
        binding.financeSummary.setText(MoneyText.format(this, baseCurrency, expense)
                + " · " + getString(R.string.home_spent_month));
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 11) return getString(R.string.home_greeting_morning);
        if (hour < 15) return getString(R.string.home_greeting_afternoon);
        if (hour < 19) return getString(R.string.home_greeting_evening);
        return getString(R.string.home_greeting_night);
    }

    private void openFeature(String feature) {
        if (uid() != null) userRepository.setLastFeature(uid(), feature);
        Prefs.setLastFeature(this, feature);
        if (NotificationHelper.hasPermission(this)) {
            Navigator.openFeatureFromHome(this, feature);
        } else {
            maybeExplainNotifications();
            Navigator.openFeatureFromHome(this, feature);
        }
    }

    private void maybeExplainNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (NotificationHelper.hasPermission(this)) return;
        if (Prefs.wasNotifPromptShown(this)) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notif_permission_title)
                .setMessage(R.string.notif_permission_text)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(R.string.allow, (dialog, which) -> {
                    Prefs.markNotifPromptShown(this);
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                })
                .show();
        Prefs.markNotifPromptShown(this);
    }
}
