package com.example.pencatatan.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.SavingsGoal;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.databinding.ActivitySavingsGoalsBinding;
import com.example.pencatatan.databinding.DialogSavingsGoalBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of savings targets.
 *
 * <p>Saving is the one part of personal finance where the good news is quiet:
 * nothing bad happens when you do it right. So this screen is built to make the
 * accumulated total the loudest thing on it, and to keep "add money" one tap
 * from the list — the friction between thinking "I should put some aside" and
 * actually recording it is what decides whether a tracker gets used at all.</p>
 */
public class SavingsGoalsActivity extends BaseActivity {

    /** Id of a goal whose editor should open as soon as the list arrives. */
    public static final String EXTRA_EDIT_GOAL_ID = "extra_edit_goal_id";

    private ActivitySavingsGoalsBinding binding;
    private SavingsGoalAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();

    private final List<SavingsGoal> goals = new ArrayList<>();
    private ListenerRegistration goalListener;
    private String baseCurrency = "IDR";
    /** Set when the detail screen asked for one specific goal to be edited. */
    @Nullable
    private String pendingEditId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        baseCurrency = Prefs.getCurrency(this);
        binding = ActivitySavingsGoalsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());
        binding.addButton.setOnClickListener(v -> openEditor(null));
        pendingEditId = getIntent().getStringExtra(EXTRA_EDIT_GOAL_ID);

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_savings);
        binding.emptyState.emptyTitle.setText(R.string.goal_empty);
        binding.emptyState.emptyDesc.setText(R.string.goal_empty_desc);
        binding.emptyState.emptyAction.setText(R.string.goal_add);
        binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
        binding.emptyState.emptyAction.setOnClickListener(v -> openEditor(null));

        adapter = new SavingsGoalAdapter(new SavingsGoalAdapter.Listener() {
            @Override
            public void onOpen(SavingsGoal goal) {
                openDetail(goal);
            }

            @Override
            public void onTopUp(SavingsGoal goal) {
                showTopUp(goal);
            }
        });
        adapter.setCurrency(baseCurrency);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        // Paint the empty state immediately. Waiting for the first snapshot
        // would leave a blank screen with an empty hero card for as long as the
        // network takes, which reads as broken rather than as "loading".
        render();

        listenGoals();
    }

    private void listenGoals() {
        goalListener = repository.listenGoals(uid(), new FinanceRepository.OnGoals() {
            @Override
            public void onData(List<SavingsGoal> data) {
                goals.clear();
                if (data != null) goals.addAll(data);
                render();
                openPendingEditor();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
            }
        });
    }

    private void render() {
        if (binding == null) return;
        adapter.submit(goals);

        // Hero figures only count goals that are still being worked on; a
        // reached goal is banked, not pending, and including it would inflate
        // the total target and make the bar look stuck.
        long savedTotal = 0L;
        long targetTotal = 0L;
        int active = 0;
        for (SavingsGoal goal : goals) {
            if (goal.isArchived()) continue;
            savedTotal += goal.getSavedAmount();
            targetTotal += goal.getTargetAmount();
            active++;
        }
        binding.heroSaved.setText(MoneyText.format(this, baseCurrency, savedTotal));
        binding.heroTarget.setText(getString(R.string.goal_hero_target,
                MoneyText.format(this, baseCurrency, targetTotal), active));
        int percent = targetTotal <= 0 ? 0
                : (int) Math.max(0L, Math.min(100L, Math.round(savedTotal * 100.0 / targetTotal)));
        binding.heroProgress.setProgressCompat(percent, false);

        binding.resultCount.setText(getResources().getQuantityString(
                R.plurals.plural_goals, active, active));

        boolean empty = goals.isEmpty();
        binding.emptyState.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void openDetail(SavingsGoal goal) {
        Intent intent = new Intent(this, SavingsGoalDetailActivity.class);
        intent.putExtra(SavingsGoalDetailActivity.EXTRA_GOAL_ID, goal.getDocId());
        startActivity(intent);
    }

    /** Opens the editor once, for the goal the detail screen asked to edit. */
    private void openPendingEditor() {
        if (pendingEditId == null) return;
        String target = pendingEditId;
        pendingEditId = null;
        for (SavingsGoal goal : goals) {
            if (target.equals(goal.getDocId())) {
                openEditor(goal);
                return;
            }
        }
    }

    /**
     * Create / edit dialog.
     *
     * <p>The "already saved" field only exists while creating: once a goal is
     * running, money can only enter through {@link #showTopUp}, which also
     * writes a history entry. Letting the editor overwrite the total later
     * would silently rewrite history.</p>
     */
    private void openEditor(@Nullable SavingsGoal existing) {
        DialogSavingsGoalBinding form = DialogSavingsGoalBinding.inflate(getLayoutInflater());
        boolean isNew = existing == null;
        form.initialLayout.setVisibility(isNew ? View.VISIBLE : View.GONE);
        // The form has no note field, so an existing note must not be dropped
        // when the goal is edited — saveGoal() writes whatever the model holds.
        final String existingNote = existing == null ? null : existing.getNote();

        if (!isNew) {
            form.titleEdit.setText(existing.getTitle());
            form.targetEdit.setText(AmountInputHelper.plain(existing.getTargetAmount()));
            selectType(form, existing.getGoalType());
        }

        final java.util.Calendar[] deadline = {
                existing == null ? null : toCalendar(existing.getTargetDate())};
        if (deadline[0] != null) form.deadlineSwitch.setChecked(true);
        updateDeadlineButton(form, deadline[0]);
        form.deadlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) deadline[0] = null;
            form.deadlineButton.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateDeadlineButton(form, deadline[0]);
        });
        form.deadlineButton.setOnClickListener(v -> {
            if (isFinishing()) return;
            java.util.Calendar seed = deadline[0] == null
                    ? java.util.Calendar.getInstance() : deadline[0];
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                java.util.Calendar picked = java.util.Calendar.getInstance();
                picked.set(year, month, day, 0, 0, 0);
                picked.set(java.util.Calendar.MILLISECOND, 0);
                deadline[0] = picked;
                updateDeadlineButton(form, picked);
            }, seed.get(java.util.Calendar.YEAR), seed.get(java.util.Calendar.MONTH),
                    seed.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isNew ? R.string.goal_add : R.string.goal_edit)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.show();
        // Override the positive button so a validation error keeps it open.
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    form.titleLayout.setError(null);
                    form.targetLayout.setError(null);
                    form.initialLayout.setError(null);

                    String title = text(form.titleEdit.getText());
                    if (title.isEmpty()) {
                        form.titleLayout.setError(getString(R.string.error_title_required));
                        return;
                    }
                    long target = parseAmount(form.targetEdit.getText());
                    if (target <= 0) {
                        form.targetLayout.setError(getString(R.string.goal_error_target));
                        return;
                    }
                    if (!isNew && target < existing.getSavedAmount()) {
                        // Lowering the target below what is already saved would
                        // make remainingAmount() and the history contradict the
                        // progress bar. Ask for a real correction via a negative
                        // deposit instead of silently keeping an impossible total.
                        form.targetLayout.setError(getString(R.string.goal_error_target_below_saved,
                                MoneyText.format(this, baseCurrency, existing.getSavedAmount())));
                        return;
                    }
                    long initial = parseAmount(form.initialEdit.getText());
                    if (initial < 0) {
                        form.initialLayout.setError(getString(R.string.goal_error_initial));
                        return;
                    }

                    SavingsGoal goal = isNew ? new SavingsGoal() : existing;
                    goal.setTitle(title);
                    goal.setTargetAmount(target);
                    goal.setGoalType(selectedType(form));
                    goal.setTargetDate(deadline[0] == null ? null
                            : new com.google.firebase.Timestamp(deadline[0].getTime()));
                    if (isNew) {
                        goal.setSavedAmount(initial);
                        if (initial > 0) {
                            List<SavingsGoal.Contribution> seed = new ArrayList<>();
                            seed.add(new SavingsGoal.Contribution(initial,
                                    com.google.firebase.Timestamp.now(),
                                    getString(R.string.goal_initial_note)));
                            goal.setContributions(seed);
                        }
                    } else {
                        // Restore what the form cannot edit, otherwise the save
                        // would null them out (saveGoal writes the whole model).
                        goal.setNote(existingNote);
                    }

                    repository.saveGoal(uid(), goal, isNew, new FinanceRepository.SaveCallback() {
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

    private void selectType(DialogSavingsGoalBinding form, String goalType) {
        int id;
        if (SavingsGoal.TYPE_GADGET.equals(goalType)) id = R.id.typeGadget;
        else if (SavingsGoal.TYPE_TRAVEL.equals(goalType)) id = R.id.typeTravel;
        else if (SavingsGoal.TYPE_EMERGENCY.equals(goalType)) id = R.id.typeEmergency;
        else if (SavingsGoal.TYPE_HOME.equals(goalType)) id = R.id.typeHome;
        else if (SavingsGoal.TYPE_EDUCATION.equals(goalType)) id = R.id.typeEducation;
        else id = R.id.typeOther;
        form.typeGroup.check(id);
    }

    private String selectedType(DialogSavingsGoalBinding form) {
        int id = form.typeGroup.getCheckedChipId();
        if (id == R.id.typeGadget) return SavingsGoal.TYPE_GADGET;
        if (id == R.id.typeTravel) return SavingsGoal.TYPE_TRAVEL;
        if (id == R.id.typeEmergency) return SavingsGoal.TYPE_EMERGENCY;
        if (id == R.id.typeHome) return SavingsGoal.TYPE_HOME;
        if (id == R.id.typeEducation) return SavingsGoal.TYPE_EDUCATION;
        return SavingsGoal.TYPE_OTHER;
    }

    /**
     * "Add money" sheet.
     *
     * <p>This is the heart of the feature: whatever amount the user types is
     * applied through a transaction in the repository, so the running total can
     * never be lost to a double tap. Suggested amounts are fractions of what is
     * still missing, which is the only set of numbers that is useful for both a
     * Rp 50.000 goal and a Rp 50.000.000 one.</p>
     */
    private void showTopUp(SavingsGoal goal) {
        com.example.pencatatan.databinding.DialogGoalTopupBinding form =
                com.example.pencatatan.databinding.DialogGoalTopupBinding.inflate(getLayoutInflater());
        form.goalCaption.setText(getString(R.string.goal_topup_caption, goal.getTitle(),
                MoneyText.format(this, baseCurrency, goal.remainingAmount())));

        long remaining = Math.max(1L, goal.remainingAmount());
        long[] suggestions = {
                roundMoney(remaining / 4), roundMoney(remaining / 2), remaining};
        for (long amount : suggestions) {
            if (amount <= 0) continue;
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(this);
            chip.setText(MoneyText.format(this, baseCurrency, amount));
            chip.setOnClickListener(v ->
                    form.amountEdit.setText(AmountInputHelper.plain(amount)));
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
                    String note = text(form.noteEdit.getText());
                    repository.addToGoal(uid(), goal.getDocId(), amount,
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

    private void updateDeadlineButton(DialogSavingsGoalBinding form, java.util.Calendar deadline) {
        form.deadlineButton.setText(deadline == null
                ? getString(R.string.goal_no_deadline)
                : com.example.pencatatan.common.Dates.formatDate(this, deadline.getTime()));
    }

    @Nullable
    private static java.util.Calendar toCalendar(com.google.firebase.Timestamp stamp) {
        if (stamp == null) return null;
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(stamp.toDate());
        return calendar;
    }

    /** Rounds to the nearest thousand so suggestions read like real amounts. */
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

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    protected void onDestroy() {
        if (goalListener != null) goalListener.remove();
        super.onDestroy();
    }

    /**
     * Tiny helper so the dialogs can show a plain digit string in an amount
     * field (no separators) while the rest of the app keeps the formatted form.
     */
    static final class AmountInputHelper {
        private AmountInputHelper() {
        }

        static String plain(long amount) {
            return amount <= 0 ? "" : String.valueOf(amount);
        }
    }
}
