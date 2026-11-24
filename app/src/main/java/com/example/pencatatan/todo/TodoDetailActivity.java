package com.example.pencatatan.todo;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.example.pencatatan.common.ActivityTimeline;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.ActivityLog;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.databinding.ActivityTodoDetailBinding;
import com.example.pencatatan.notification.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TodoDetailActivity extends BaseActivity {

    public static final String EXTRA_TODO_ID = "todo_id";

    private ActivityTodoDetailBinding binding;
    private final TodoRepository repository = new TodoRepository();
    private Todo todo;
    private String todoId;
    private ListenerRegistration logListener;
    private boolean offeredComplete;

    private final ActivityResultLauncher<Intent> editLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> loadTodo());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        todoId = getIntent().getStringExtra(EXTRA_TODO_ID);
        if (uid() == null || todoId == null) {
            finish();
            return;
        }
        binding = ActivityTodoDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyBottomInsets(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());
        binding.starButton.setOnClickListener(v -> toggleStar());
        binding.moreButton.setOnClickListener(v -> showQuickActions());
        binding.editButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, TodoFormActivity.class);
            intent.putExtra(TodoFormActivity.EXTRA_TODO_ID, todoId);
            editLauncher.launch(intent);
        });
        binding.deleteButton.setOnClickListener(v -> confirmDelete());
        binding.doneButton.setOnClickListener(v -> markDone());
        binding.addSubtaskButton.setOnClickListener(v -> addSubtask());
        binding.viewDoneButton.setOnClickListener(v -> openDoneTab());
        loadTodo();
    }

    private void loadTodo() {
        repository.loadTodo(uid(), todoId, new TodoRepository.OnTodo() {
            @Override
            public void onData(Todo data) {
                todo = data;
                render();
                listenLogs();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
                finish();
            }
        });
    }

    private void toggleStar() {
        if (todo == null || todo.getDocId() == null) return;
        final boolean next = !todo.isStarred();
        todo.setStarred(next);
        render();
        repository.setStarred(uid(), todo, next, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
            }

            @Override
            public void onError(Exception error) {
                todo.setStarred(!next);
                render();
                toast(getString(R.string.save_failed));
            }
        });
    }

    /**
     * Quick actions that do not deserve their own toolbar icon: duplicate the task
     * as a template, or push the deadline out by the configured snooze length.
     * Both are reversible enough that they need no confirmation dialog.
     */
    private void showQuickActions() {
        if (todo == null) return;
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.todo_duplicate));
        if (todo.getDueDate() != null && !todo.isCompleted()) {
            actions.add(getString(R.string.todo_snooze) + " \u00B7 "
                    + getString(R.string.todo_days_format, Prefs.snoozeDays(this)));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.todo_quick_actions)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) duplicateTodo();
                    else snoozeTodo();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void duplicateTodo() {
        repository.duplicate(uid(), todo, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                toast(getString(R.string.todo_duplicated));
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.save_failed));
            }
        });
    }

    private void snoozeTodo() {
        repository.snooze(uid(), todo, Prefs.snoozeDays(this), new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                toast(getString(R.string.todo_snoozed));
                loadTodo();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.save_failed));
            }
        });
    }

    private void render() {
        if (todo == null || binding == null) return;
        binding.title.setText(todo.getTitle());

        // Star state lives in the toolbar: filled gold when starred, grey outline
        // otherwise, and the content description flips so TalkBack announces the
        // action the button will perform rather than its current state.
        binding.starButton.setIconResource(R.drawable.ic_star);
        binding.starButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this,
                todo.isStarred() ? R.color.priority_high : R.color.color_on_surface_variant)));
        binding.starButton.setContentDescription(getString(
                todo.isStarred() ? R.string.todo_unstar : R.string.todo_star));

        int status = TodoAdapter.statusOf(todo);
        binding.statusChip.setText(TodoAdapter.statusLabel(this, status));
        tintBadge(binding.statusChip, TodoAdapter.statusColor(this, status));

        // Show the flow: a finished task moves to the "Selesai" (history) tab.
        binding.statusHint.setVisibility(todo.isCompleted() ? View.VISIBLE : View.GONE);
        binding.viewDoneButton.setVisibility(todo.isCompleted() ? View.VISIBLE : View.GONE);

        int priorityColor = ContextCompat.getColor(this,
                todo.getPriority() == 2 ? R.color.priority_high
                        : todo.getPriority() == 0 ? R.color.priority_low : R.color.priority_medium);
        binding.priorityChip.setText(priorityLabel());
        tintBadge(binding.priorityChip, priorityColor);

        binding.description.setText(todo.getDescription() == null || todo.getDescription().trim().isEmpty()
                ? getString(R.string.detail_no_description) : todo.getDescription());
        binding.description.setTextColor(ContextCompat.getColor(this,
                todo.getDescription() == null || todo.getDescription().trim().isEmpty()
                        ? R.color.color_on_surface_variant : R.color.color_on_surface));

        binding.dueValue.setText(todo.getDueDate() == null
                ? getString(R.string.no_due_date)
                : Dates.relativeLabel(this, todo.getDueDate().toDate()));
        binding.reminderValue.setText(todo.reminders().isEmpty()
                ? getString(R.string.reminder_none)
                : Dates.formatDateTime(this, todo.reminders().get(0)));
        binding.repeatValue.setText(repeatLabel());

        // Once completed the action is finished: the button becomes a disabled "Selesai"
        // state (never a misleading "mark done"), so it cannot be flagged over and over.
        boolean done = todo.isLocked();
        binding.doneButton.setEnabled(!done && !toggleInFlight);
        binding.doneButton.setIconResource(done ? R.drawable.ic_check_circle : 0);
        binding.doneButton.setText(done ? R.string.todo_status_done_locked
                : toggleInFlight ? R.string.todo_saving : R.string.todo_mark_done);
        binding.doneButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                done ? R.color.income_soft : R.color.todo_primary)));
        binding.doneButton.setTextColor(ContextCompat.getColor(this,
                done ? R.color.income : R.color.todo_on_primary));
        binding.doneButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this,
                done ? R.color.income : R.color.todo_on_primary)));

        // Editing/completing a finished task would be confusing, so the edit
        // action is disabled once the task is done.
        binding.editButton.setEnabled(!done);
        binding.editButton.setAlpha(done ? 0.45f : 1f);

        renderSubtasks();
    }

    private GradientDrawable pillBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(getResources().getDisplayMetrics().density * 999);
        drawable.setColor(color);
        return drawable;
    }

    /** Applies the shared badge look: 14 % tinted pill, coloured text and dot. */
    private void tintBadge(TextView view, int color) {
        view.setTextColor(color);
        view.setBackgroundTintList(ColorStateList.valueOf(ColorUtil.withAlpha(
                String.format("#%06X", color & 0xFFFFFF), 0.14f)));
        view.setCompoundDrawableTintList(ColorStateList.valueOf(color));
    }

    private String priorityLabel() {
        if (todo.getPriority() == 2) return getString(R.string.priority_high);
        if (todo.getPriority() == 0) return getString(R.string.priority_low);
        return getString(R.string.priority_medium);
    }

    private String repeatLabel() {
        if (todo.getRepeatType() == null || "none".equals(todo.getRepeatType())) {
            return getString(R.string.repeat_none);
        }
        String label = "daily".equals(todo.getRepeatType()) ? getString(R.string.repeat_daily)
                : "weekly".equals(todo.getRepeatType()) ? getString(R.string.repeat_weekly)
                : getString(R.string.repeat_monthly);
        if (todo.getRepeatEndDate() != null) {
            return getString(R.string.repeat_until, label,
                    Dates.formatDate(this, todo.getRepeatEndDate().toDate()));
        }
        return label;
    }

    private void renderSubtasks() {
        binding.subtaskContainer.removeAllViews();
        List<Map<String, Object>> subtasks = todo.getSubtasks();
        if (subtasks == null) subtasks = new ArrayList<>();
        int done = 0;
        for (Map<String, Object> item : subtasks) {
            if (Boolean.TRUE.equals(item.get("isDone"))) done++;
        }
        binding.subtaskProgress.setText(getString(R.string.subtask_progress, done, subtasks.size()));
        binding.subtaskProgress.setVisibility(subtasks.isEmpty() ? View.GONE : View.VISIBLE);
        binding.noSubtasks.setVisibility(subtasks.isEmpty() ? View.VISIBLE : View.GONE);

        // A finished task is read-only: no more ticking, adding or removing.
        boolean locked = todo.isLocked();
        binding.addSubtaskButton.setEnabled(!locked);
        binding.addSubtaskButton.setAlpha(locked ? 0.45f : 1f);

        for (Map<String, Object> item : subtasks) {
            final Map<String, Object> subtask = item;
            boolean subtaskDone = Boolean.TRUE.equals(subtask.get("isDone"));

            // One row = tick + label + remove. Every child is centred on the
            // row's vertical axis and the label carries no extra font padding,
            // so the tick glyph, the text and the × button share one centre line
            // even when a subtask title wraps to a second line.
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setMinimumHeight(dp(40));

            com.example.pencatatan.common.AnimatedCheckBox check =
                    new com.example.pencatatan.common.AnimatedCheckBox(this);
            check.setIdleColor(ContextCompat.getColor(this, R.color.todo_primary));
            check.setActiveColor(ContextCompat.getColor(this, R.color.income));
            check.setChecked(subtaskDone, false);
            check.setLocked(locked);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            checkParams.setMarginStart(-dp(4));
            row.addView(check, checkParams);

            TextView label = new TextView(this);
            label.setText(String.valueOf(subtask.get("title")));
            label.setTextSize(spanPx(14));
            label.setIncludeFontPadding(false);
            label.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginStart(dp(4));
            row.addView(label, labelParams);
            if (subtaskDone) {
                label.setPaintFlags(label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                label.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface_variant));
            }

            com.google.android.material.button.MaterialButton remove =
                    new com.google.android.material.button.MaterialButton(this);
            remove.setIconResource(R.drawable.ic_close);
            remove.setText("");
            remove.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            remove.setIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.color_on_surface_variant)));
            remove.setContentDescription(getString(R.string.delete));
            remove.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
            remove.setInsetTop(0);
            remove.setInsetBottom(0);
            remove.setPadding(0, 0, 0, 0);
            remove.setEnabled(!locked);
            remove.setAlpha(locked ? 0.35f : 1f);
            row.addView(remove);

            check.setOnToggleListener((view, checked) -> {
                if (todo.isLocked()) return;
                subtask.put("isDone", checked);
                persistSubtasks();
            });
            remove.setOnClickListener(v -> {
                if (todo.isLocked()) return;
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete)
                        .setMessage(R.string.subtask_delete_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (dialog, which) -> {
                            List<Map<String, Object>> next = new ArrayList<>(todo.getSubtasks());
                            next.remove(subtask);
                            todo.setSubtasks(next);
                            persistSubtasks();
                        })
                        .show();
            });

            binding.subtaskContainer.addView(row);
        }
    }

    private void addSubtask() {
        if (todo == null || todo.isLocked()) return;
        EditText input = new EditText(this);
        input.setHint(R.string.subtask_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        FrameLayout container = new FrameLayout(this);
        int padding = dp(24);
        container.setPadding(padding, dp(8), padding, 0);
        container.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.subtask_add)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    List<Map<String, Object>> next = todo.getSubtasks() == null
                            ? new ArrayList<>() : new ArrayList<>(todo.getSubtasks());
                    Map<String, Object> item = new HashMap<>();
                    item.put("title", value);
                    item.put("isDone", false);
                    next.add(item);
                    todo.setSubtasks(next);
                    offeredComplete = false;
                    persistSubtasks();
                })
                .show();
    }

    private void persistSubtasks() {
        repository.updateSubtasks(uid(), todo, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                renderSubtasks();
                checkAllSubtasks();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.save_failed));
            }
        });
    }

    private void checkAllSubtasks() {
        if (offeredComplete || todo.isLocked()) return;
        List<Map<String, Object>> subtasks = todo.getSubtasks();
        if (subtasks == null || subtasks.isEmpty()) return;
        for (Map<String, Object> item : subtasks) {
            if (!Boolean.TRUE.equals(item.get("isDone"))) return;
        }
        offeredComplete = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_subtasks)
                .setMessage(R.string.subtask_complete_offer)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(R.string.todo_mark_done, (dialog, which) -> markDone())
                .show();
    }

    private boolean toggleInFlight;

    /**
     * Marks the task done. Once done, the task is final: the button becomes a
     * disabled "Selesai" state so it cannot be flagged over and over.
     */
    private void markDone() {
        if (todo == null || toggleInFlight || todo.isLocked()) return;
        toggleInFlight = true;
        setDoneButtonBusy(true);

        ReminderScheduler.cancelTodo(this, todo);
        repository.setCompleted(uid(), todo, true, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String createdId) {
                // The write is finished: release the busy state here so the button
                // never stays stuck on "Menyimpan…" if the reload below fails.
                toggleInFlight = false;
                setDoneButtonBusy(false);
                if (createdId != null && !createdId.isEmpty()) {
                    repository.loadTodo(uid(), createdId, new TodoRepository.OnTodo() {
                        @Override
                        public void onData(Todo next) {
                            ReminderScheduler.scheduleTodo(TodoDetailActivity.this, uid(), next);
                        }

                        @Override
                        public void onError(Exception error) {
                        }
                    });
                }
                // Optimistic local flip so the status chip, button and card lock
                // instantly (no "Open" flash while the reload is in flight), then
                // reload to pick up the server's completedAt and the fresh log.
                todo.setCompleted(true);
                todo.setCompletedAt(todo.getCompletedAt() == null
                        ? com.google.firebase.Timestamp.now() : todo.getCompletedAt());
                render();
                loadTodo();
                Toast.makeText(TodoDetailActivity.this, R.string.todo_completed_go_done,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception error) {
                toggleInFlight = false;
                setDoneButtonBusy(false);
                render();
                toast(getString(R.string.save_failed));
            }
        });
    }

    /** Locks the action button while the write is in flight. */
    private void setDoneButtonBusy(boolean busy) {
        if (binding == null) return;
        binding.doneButton.setEnabled(!busy);
        if (busy) {
            binding.doneButton.setText(R.string.todo_saving);
        }
    }

    /**
     * Sends the user to the "Selesai" list. Finishing a task here removes it from the open
     * list, so this is the answer to "where did my finished task go?".
     */
    private void openDoneTab() {
        Intent intent = new Intent(this, TodoActivity.class);
        intent.putExtra(TodoActivity.EXTRA_TAB, "completed");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void confirmDelete() {
        if (todo == null) return;
        confirm(getString(R.string.todo_delete_title), getString(R.string.todo_delete_msg),
                getString(R.string.delete), () -> {
                    ReminderScheduler.cancelTodo(this, todo);
                    repository.deleteTodo(uid(), todo, new TodoRepository.SaveCallback() {
                        @Override
                        public void onSuccess(String id) {
                            toast(getString(R.string.todo_deleted));
                            finish();
                        }

                        @Override
                        public void onError(Exception error) {
                            toast(getString(R.string.delete_failed));
                        }
                    });
                });
    }

    private void listenLogs() {
        if (logListener != null) logListener.remove();
        logListener = repository.listenLogsForTodo(uid(), todoId, new TodoRepository.OnLogs() {
            @Override
            public void onData(List<ActivityLog> logs) {
                renderLogs(logs);
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void renderLogs(List<ActivityLog> logs) {
        binding.activityContainer.removeAllViews();
        // Old data may already contain several identical "completed" rows
        // (written before completion became idempotent). Collapse them so the
        // timeline shows one entry per meaningful action, not a wall of
        // duplicates.
        List<ActivityLog> visible = ActivityTimeline.collapseDuplicateCompletions(logs);
        int count = 0;
        for (ActivityLog log : visible) {
            if (count >= 5) break;
            count++;
            // Dot and label share one centre line: the dot sits in a 14dp frame
            // (so 8dp dots line up under each other) and the label has no extra
            // font padding, which is what used to push it a pixel below the dot.
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(28));

            FrameLayout dotFrame = new FrameLayout(this);
            row.addView(dotFrame, new LinearLayout.LayoutParams(dp(14), dp(14)));

            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(ContextCompat.getColor(this, actionColor(log.getAction())));
            dot.setBackground(dotBg);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(8), dp(8));
            dotParams.gravity = Gravity.CENTER;
            dotFrame.addView(dot, dotParams);

            TextView label = new TextView(this);
            label.setText(actionLabel(log.getAction()));
            label.setTextSize(spanPx(12));
            label.setIncludeFontPadding(false);
            label.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginStart(dp(8));
            row.addView(label, labelParams);

            TextView time = new TextView(this);
            Date date = log.getTimestamp() == null ? null : log.getTimestamp().toDate();
            time.setText(Dates.formatDateTime(this, date));
            time.setTextSize(spanPx(11));
            time.setIncludeFontPadding(false);
            time.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface_variant));
            row.addView(time);

            binding.activityContainer.addView(row);
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.activity_empty);
            empty.setTextSize(spanPx(12));
            empty.setIncludeFontPadding(false);
            empty.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface_variant));
            empty.setPadding(0, dp(6), 0, dp(6));
            binding.activityContainer.addView(empty);
        }
    }

    private int actionColor(String action) {
        if (ActivityLog.COMPLETED.equals(action)) return R.color.income;
        if (ActivityLog.DELETED.equals(action)) return R.color.expense;
        if (ActivityLog.UPDATED.equals(action)) return R.color.warning;
        return R.color.color_primary;
    }

    private String actionLabel(String action) {
        if (ActivityLog.CREATED.equals(action)) return getString(R.string.activity_created);
        if (ActivityLog.UPDATED.equals(action)) return getString(R.string.activity_updated);
        if (ActivityLog.COMPLETED.equals(action)) return getString(R.string.activity_completed);
        if (ActivityLog.UNCOMPLETED.equals(action)) return getString(R.string.activity_uncompleted);
        if (ActivityLog.DELETED.equals(action)) return getString(R.string.activity_deleted);
        return getString(R.string.details);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** sp → the value TextView.setTextSize(float) expects (it scales by scaledDensity). */
    private float spanPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity
                / getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logListener != null) logListener.remove();
    }
}
