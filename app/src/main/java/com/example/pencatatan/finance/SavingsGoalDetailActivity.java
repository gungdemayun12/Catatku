package com.example.pencatatan.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.SavingsGoal;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.databinding.ActivitySavingsGoalDetailBinding;
import com.example.pencatatan.databinding.DialogGoalTopupBinding;
import com.example.pencatatan.databinding.ItemGoalContributionBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One savings goal in detail: how far along it is, and every deposit that got
 * it there.
 *
 * <p>The deposit history is the part that keeps this honest. A progress bar
 * alone is a number the app asserts; the history is a number the user can
 * check against their own memory of putting money aside.</p>
 */
public class SavingsGoalDetailActivity extends BaseActivity {

    public static final String EXTRA_GOAL_ID = "extra_goal_id";

    private ActivitySavingsGoalDetailBinding binding;
    private final FinanceRepository repository = new FinanceRepository();

    private String goalId;
    private SavingsGoal goal;
    private ListenerRegistration listener;
    private String baseCurrency = "IDR";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        goalId = getIntent().getStringExtra(EXTRA_GOAL_ID);
        if (goalId == null || goalId.isEmpty()) {
            finish();
            return;
        }
        baseCurrency = Prefs.getCurrency(this);
        binding = ActivitySavingsGoalDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // The primary action is pinned to the bottom edge. Keep it clear of the
        // navigation bar so it stays fully tappable on devices where the bar is
        // taller than the swatch it leaves here.
        applyBottomInsets(binding.getRoot());

        binding.backButton.setOnClickListener(v -> finish());
        binding.topUpButton.setOnClickListener(v -> showTopUp());
        binding.editButton.setOnClickListener(v -> openEditor());
        binding.deleteButton.setOnClickListener(v -> confirmDelete());

        listenGoal();
    }

    private void listenGoal() {
        listener = repository.listenGoal(uid(), goalId, new FinanceRepository.OnGoal() {
            @Override
            public void onData(SavingsGoal loaded) {
                if (loaded == null) {
                    // Deleted elsewhere; nothing useful to show.
                    finish();
                    return;
                }
                goal = loaded;
                render();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
            }
        });
    }

    private void render() {
        if (binding == null || goal == null) return;
        binding.titleView.setText(goal.getTitle());
        binding.savedAmount.setText(MoneyText.format(this, baseCurrency, goal.getSavedAmount()));
        binding.targetAmount.setText(getString(R.string.goal_of_target,
                MoneyText.format(this, baseCurrency, goal.getTargetAmount())));

        int percent = goal.progressPercent();
        boolean reached = goal.isReached();
        binding.progress.setProgressCompat(percent, true);
        binding.progress.setIndicatorColor(ContextCompat.getColor(this,
                reached ? R.color.income : R.color.color_primary));

        if (reached) {
            binding.remainingText.setText(R.string.goal_reached_detail);
            binding.remainingText.setTextColor(ContextCompat.getColor(this, R.color.income));
            binding.topUpButton.setText(R.string.goal_topup);
        } else {
            binding.remainingText.setText(getString(R.string.goal_remaining_percent,
                    MoneyText.format(this, baseCurrency, goal.remainingAmount()), percent));
            binding.remainingText.setTextColor(ContextCompat.getColor(this,
                    R.color.color_on_surface));
        }

        renderDeadline();
        renderHistory();
    }

    /**
     * Turns the deadline into an actionable number: "Rp 620.000 per week".
     * A date on its own does not tell anyone what to do differently this month.
     */
    private void renderDeadline() {
        if (goal.getTargetDate() == null || goal.isReached()) {
            binding.deadlineText.setVisibility(View.GONE);
            return;
        }
        binding.deadlineText.setVisibility(View.VISIBLE);
        String date = Dates.formatDate(this, goal.getTargetDate().toDate());
        long days = Math.max(1L, (goal.getTargetDate().toDate().getTime()
                - System.currentTimeMillis()) / (24L * 60 * 60 * 1000));
        long perWeek = Math.round(goal.remainingAmount() / (days / 7.0));
        binding.deadlineText.setText(getString(R.string.goal_deadline_hint,
                date, MoneyText.format(this, baseCurrency, perWeek)));
    }

    private void renderHistory() {
        binding.historyList.removeAllViews();
        List<SavingsGoal.Contribution> history = new ArrayList<>(goal.getContributions());
        if (history.isEmpty()) {
            binding.historyEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.historyEmpty.setVisibility(View.GONE);
        // Newest first: the most recent deposit is the one being remembered.
        Collections.sort(history, (a, b) -> {
            if (a.getAt() == null || b.getAt() == null) return 0;
            return b.getAt().compareTo(a.getAt());
        });
        LayoutInflater inflater = getLayoutInflater();
        for (SavingsGoal.Contribution entry : history) {
            ItemGoalContributionBinding row = ItemGoalContributionBinding.inflate(
                    inflater, binding.historyList, false);
            row.entryDate.setText(entry.getAt() == null
                    ? "-" : Dates.formatDateTime(this, entry.getAt().toDate()));
            boolean negative = entry.getAmount() < 0;
            String amount = MoneyText.format(this, baseCurrency, Math.abs(entry.getAmount()));
            row.entryAmount.setText(negative ? "-" + amount : "+" + amount);
            row.entryAmount.setTextColor(ContextCompat.getColor(this,
                    negative ? R.color.expense : R.color.income));
            if (entry.getNote() != null && !entry.getNote().trim().isEmpty()) {
                row.entryNote.setVisibility(View.VISIBLE);
                row.entryNote.setText(entry.getNote().trim());
            } else {
                row.entryNote.setVisibility(View.GONE);
            }
            binding.historyList.addView(row.getRoot());
        }
    }

    private void showTopUp() {
        if (goal == null) return;
        DialogGoalTopupBinding form = DialogGoalTopupBinding.inflate(getLayoutInflater());
        form.goalCaption.setText(getString(R.string.goal_topup_caption, goal.getTitle(),
                MoneyText.format(this, baseCurrency, goal.remainingAmount())));

        long remaining = Math.max(1L, goal.remainingAmount());
        List<Long> suggestions = new ArrayList<>();
        suggestions.add(roundMoney(remaining / 4));
        suggestions.add(roundMoney(remaining / 2));
        if (goal.isReached()) {
            // Already there: offer useful round amounts instead of "the rest".
            suggestions.clear();
            suggestions.add(100_000L);
            suggestions.add(500_000L);
            suggestions.add(1_000_000L);
        } else {
            suggestions.add(remaining);
        }
        for (long amount : suggestions) {
            if (amount <= 0) continue;
            Chip chip = new Chip(this);
            chip.setText(MoneyText.format(this, baseCurrency, amount));
            chip.setOnClickListener(v -> form.amountEdit.setText(String.valueOf(amount)));
            form.suggestionGroup.addView(chip);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.goal_topup_title)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.goal_topup_confirm, null)
                .create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    form.amountLayout.setError(null);
                    long amount = parseAmount(form.amountEdit.getText());
                    if (amount == 0) {
                        form.amountLayout.setError(getString(R.string.goal_error_amount));
                        return;
                    }
                    String note = form.noteEdit.getText() == null
                            ? "" : form.noteEdit.getText().toString().trim();
                    repository.addToGoal(uid(), goalId, amount,
                            note.isEmpty() ? null : note, new FinanceRepository.SaveCallback() {
                                @Override
                                public void onSuccess(String id) {
                                    dialog.dismiss();
                                }

                                @Override
                                public void onError(Exception error) {
                                    toast(getString(R.string.save_failed));
                                }
                            });
                });
    }

    private void openEditor() {
        if (goal == null) return;
        // Hand the id over so the list screen can open this exact goal's editor
        // instead of dropping the user on the list to find it again.
        Intent intent = new Intent(this, SavingsGoalsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(SavingsGoalsActivity.EXTRA_EDIT_GOAL_ID, goal.getDocId());
        startActivity(intent);
        finish();
    }

    private void confirmDelete() {
        if (goal == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.goal_delete_title)
                .setMessage(getString(R.string.goal_delete_msg, goal.getTitle()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        repository.deleteGoal(uid(), goal, new FinanceRepository.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                finish();
                            }

                            @Override
                            public void onError(Exception error) {
                                toast(getString(R.string.save_failed));
                            }
                        }))
                .show();
    }

    private static long roundMoney(long value) {
        if (value <= 0) return 0L;
        return Math.round(value / 1000.0) * 1000L;
    }

    private long parseAmount(CharSequence raw) {
        String cleaned = raw == null ? "" : raw.toString().replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) return 0L;
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    protected void onDestroy() {
        if (listener != null) listener.remove();
        super.onDestroy();
    }
}
