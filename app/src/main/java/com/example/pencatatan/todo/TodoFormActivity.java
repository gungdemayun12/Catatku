package com.example.pencatatan.todo;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityTodoFormBinding;
import com.example.pencatatan.notification.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TodoFormActivity extends BaseActivity {

    public static final String EXTRA_TODO_ID = "todo_id";
    public static final String EXTRA_DUE_DATE = "due_date";
    /** Title typed in the quick-capture sheet, carried into the full form. */
    public static final String EXTRA_PREFILL_TITLE = "prefill_title";

    private ActivityTodoFormBinding binding;
    private final TodoRepository repository = new TodoRepository();
    private final UserRepository userRepository = new UserRepository();
    private final List<TodoCategory> categories = new ArrayList<>();
    private final List<String> categoryIds = new ArrayList<>();
    private final List<String> categoryNames = new ArrayList<>();

    private String todoId;
    private boolean isNew = true;
    private Todo editing;
    private Calendar dueDate;
    private final List<Calendar> reminders = new ArrayList<>();
    private Calendar repeatEnd;
    private String repeatType = "none";
    private String categoryId;
    private String categoryName;
    private long priority = 1;
    /** Chosen effort estimate in minutes; 0 means the user did not say. */
    private long estimateMinutes = 0L;
    private String defaultReminder = "08:00";
    private ListenerRegistration categoryListener;

    /**
     * Keeps the footer Save button above the navigation bar and the keyboard —
     * see BaseActivity#applyBottomInsets for why the root needs padding.
     */
    private void applyWindowInsets() {
        applyBottomInsets(binding.getRoot());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        binding = ActivityTodoFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyWindowInsets();

        todoId = getIntent().getStringExtra(EXTRA_TODO_ID);
        isNew = todoId == null || todoId.trim().isEmpty();
        binding.formTitle.setText(isNew ? R.string.todo_new : R.string.todo_edit);
        binding.backButton.setOnClickListener(v -> finish());

        if (isNew) {
            priority = Prefs.getDefaultPriority(this);
            categoryId = Prefs.getDefaultCategoryId(this);
            categoryName = Prefs.getDefaultCategoryName(this);
        }

        setupRows();
        setupListeners();
        loadUser();
        listenCategories();

        long preset = getIntent().getLongExtra(EXTRA_DUE_DATE, 0L);
        if (preset > 0) {
            dueDate = Calendar.getInstance();
            dueDate.setTimeInMillis(preset);
            dueDate.set(Calendar.HOUR_OF_DAY, 9);
            dueDate.set(Calendar.MINUTE, 0);
            updateScheduleRows();
        }

        // Hand-off from quick capture: the title was already typed in the sheet,
        // so open the full form with it filled in and the cursor ready to
        // continue rather than making the user retype it.
        String prefill = getIntent().getStringExtra(EXTRA_PREFILL_TITLE);
        if (isNew && prefill != null && !prefill.trim().isEmpty()) {
            binding.titleEdit.setText(prefill.trim());
            binding.titleEdit.setSelection(binding.titleEdit.getText().length());
        }

        if (!isNew) loadTodo();
        updateScheduleRows();
    }

    private void setupRows() {
        binding.dateRow.rowIcon.setImageResource(R.drawable.ic_calendar);
        binding.dateRow.rowLabel.setText(R.string.due_date);
        binding.timeRow.rowIcon.setImageResource(R.drawable.ic_clock);
        binding.timeRow.rowLabel.setText(R.string.time_label);
        binding.repeatRow.rowIcon.setImageResource(R.drawable.ic_repeat);
        binding.repeatRow.rowLabel.setText(R.string.repeat_label);
        binding.repeatEndRow.rowIcon.setImageResource(R.drawable.ic_calendar);
        binding.repeatEndRow.rowLabel.setText(R.string.repeat_end_date);
        binding.categoryRow.rowIcon.setImageResource(R.drawable.ic_tag);
        binding.categoryRow.rowLabel.setText(R.string.category_label);
        binding.categoryRow.rowValue.setText(R.string.category_none);

        binding.dateRow.getRoot().setOnClickListener(v -> pickDate());
        binding.timeRow.getRoot().setOnClickListener(v -> pickTime());
        binding.addReminderRow.setOnClickListener(v -> addReminder());
        binding.repeatRow.getRoot().setOnClickListener(v -> chooseRepeat());
        binding.repeatEndRow.getRoot().setOnClickListener(v -> pickRepeatEnd());
        binding.categoryRow.getRoot().setOnClickListener(v -> chooseCategory());
        binding.estimateRow.setOnClickListener(v -> chooseEstimate());
        binding.saveButton.setOnClickListener(v -> save());
    }

    private void setupListeners() {
        binding.reminderSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked && dueDate == null) {
                binding.reminderSwitch.setChecked(false);
                toast(getString(R.string.error_choose_date));
                return;
            }
            if (checked) {
                if (reminders.isEmpty()) {
                    pickReminderDateTime(-1);
                }
            } else {
                reminders.clear();
            }
            updateScheduleRows();
        });

        binding.priorityGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.priorityLow) priority = 0;
            else if (checkedId == R.id.priorityHigh) priority = 2;
            else priority = 1;
        });
        binding.priorityGroup.check(priority == 2 ? R.id.priorityHigh
                : priority == 0 ? R.id.priorityLow : R.id.priorityMedium);
    }

    private Calendar defaultReminderFor(Calendar due) {
        Calendar value = Calendar.getInstance();
        if (due != null) {
            value = (Calendar) due.clone();
            value.add(Calendar.DAY_OF_MONTH, -1);
            if (value.getTimeInMillis() <= System.currentTimeMillis()) {
                value = (Calendar) due.clone();
                value.add(Calendar.HOUR_OF_DAY, -1);
            }
        } else {
            value.add(Calendar.HOUR_OF_DAY, 1);
        }
        value.set(Calendar.SECOND, 0);
        value.set(Calendar.MILLISECOND, 0);
        return value;
    }

    private void loadUser() {
        userRepository.get(uid(), new UserRepository.UserListener() {
            @Override
            public void onUser(User user) {
                if (user.getDefaultReminderTime() != null) {
                    defaultReminder = user.getDefaultReminderTime();
                }
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void loadTodo() {
        repository.loadTodo(uid(), todoId, new TodoRepository.OnTodo() {
            @Override
            public void onData(Todo todo) {
                editing = todo;
                binding.titleEdit.setText(todo.getTitle());
                binding.descEdit.setText(todo.getDescription());
                priority = todo.getPriority();
                binding.priorityGroup.check(priority == 2 ? R.id.priorityHigh
                        : priority == 0 ? R.id.priorityLow : R.id.priorityMedium);
                if (todo.getDueDate() != null) {
                    dueDate = Calendar.getInstance();
                    dueDate.setTime(todo.getDueDate().toDate());
                }
                if (todo.getReminderTimes() != null && !todo.getReminderTimes().isEmpty()) {
                    reminders.clear();
                    for (Timestamp timestamp : todo.getReminderTimes()) {
                        if (timestamp == null) continue;
                        Calendar value = Calendar.getInstance();
                        value.setTime(timestamp.toDate());
                        reminders.add(value);
                    }
                    if (!reminders.isEmpty()) binding.reminderSwitch.setChecked(true);
                }
                if (todo.getRepeatEndDate() != null) {
                    repeatEnd = Calendar.getInstance();
                    repeatEnd.setTime(todo.getRepeatEndDate().toDate());
                }
                repeatType = todo.getRepeatType() == null ? "none" : todo.getRepeatType();
                estimateMinutes = todo.getEstimateMinutes();
                updateEstimateValue();
                categoryId = todo.getCategoryId();
                categoryName = todo.getCategoryName();
                updateCategoryValue();
                updateScheduleRows();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
            }
        });
    }

    private void listenCategories() {
        categoryListener = repository.listenCategories(uid(), new TodoRepository.OnCategories() {
            @Override
            public void onData(List<TodoCategory> data) {
                categories.clear();
                if (data != null) categories.addAll(data);
                rebuildCategoryChoices();
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void rebuildCategoryChoices() {
        categoryIds.clear();
        categoryNames.clear();
        categoryIds.add(null);
        categoryNames.add(getString(R.string.category_none));
        for (TodoCategory category : categories) {
            if (category.getDocId() == null) continue;
            categoryIds.add(category.getDocId());
            categoryNames.add(NameKeys.resolve(this, category.getName(), category.getNameKey()));
        }
        updateCategoryValue();
    }

    private void updateCategoryValue() {
        if (categoryId == null) {
            binding.categoryRow.rowValue.setText(R.string.category_none);
            return;
        }
        boolean found = false;
        for (TodoCategory category : categories) {
            if (categoryId.equals(category.getDocId())) {
                categoryName = NameKeys.resolve(this, category.getName(), category.getNameKey());
                found = true;
                break;
            }
        }
        if (!found && !categories.isEmpty()) {
            categoryId = null;
            categoryName = null;
            binding.categoryRow.rowValue.setText(R.string.category_none);
            return;
        }
        binding.categoryRow.rowValue.setText(categoryName == null
                ? getString(R.string.category_none) : categoryName);
    }

    /**
     * Effort picker.
     *
     * <p>A fixed set of chips instead of a number pad: "2 hours" is a decision
     * people can make in a second, "117 minutes" is not, and the load total in
     * the task list only needs a rough order of magnitude to be useful. The
     * first choice clears the estimate, so the field is never a one-way door.</p>
     */
    private void chooseEstimate() {
        long[] values = {0L, 15L, 30L, 60L, 120L, 480L};
        String[] labels = {
                getString(R.string.todo_estimate_none),
                getString(R.string.todo_estimate_15),
                getString(R.string.todo_estimate_30),
                getString(R.string.todo_estimate_60),
                getString(R.string.todo_estimate_120),
                getString(R.string.todo_estimate_480)
        };
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == estimateMinutes) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.todo_estimate_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    estimateMinutes = values[which];
                    updateEstimateValue();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateEstimateValue() {
        binding.estimateValue.setText(estimateMinutes <= 0
                ? getString(R.string.todo_estimate_none)
                : Dates.formatDuration(this, estimateMinutes));
    }

    private void chooseCategory() {
        if (categoryNames.isEmpty()) return;
        int checked = 0;
        int index = categoryIds.indexOf(categoryId);
        if (index > 0) checked = index;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.category_label)
                .setSingleChoiceItems(categoryNames.toArray(new String[0]), checked, (dialog, which) -> {
                    categoryId = categoryIds.get(which);
                    categoryName = which == 0 ? null : categoryNames.get(which);
                    binding.categoryRow.rowValue.setText(categoryName == null
                            ? getString(R.string.category_none) : categoryName);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickDate() {
        Calendar base = dueDate == null ? Calendar.getInstance() : dueDate;
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar value = Calendar.getInstance();
            value.set(year, month, day,
                    dueDate == null ? 9 : dueDate.get(Calendar.HOUR_OF_DAY),
                    dueDate == null ? 0 : dueDate.get(Calendar.MINUTE), 0);
            value.set(Calendar.MILLISECOND, 0);
            dueDate = value;
            updateScheduleRows();
        }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        if (dueDate == null) {
            toast(getString(R.string.error_choose_date));
            return;
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            dueDate.set(Calendar.HOUR_OF_DAY, hour);
            dueDate.set(Calendar.MINUTE, minute);
            dueDate.set(Calendar.SECOND, 0);
            dueDate.set(Calendar.MILLISECOND, 0);
            updateScheduleRows();
        }, dueDate.get(Calendar.HOUR_OF_DAY), dueDate.get(Calendar.MINUTE), true).show();
    }

    private void pickReminderDateTime(int index) {
        Calendar base;
        if (index >= 0 && index < reminders.size()) {
            base = reminders.get(index);
        } else {
            base = defaultReminderFor(dueDate);
        }

        new DatePickerDialog(this, (dateView, year, month, day) -> {
            new TimePickerDialog(this, (timeView, hour, minute) -> {
                Calendar value = Calendar.getInstance();
                value.set(year, month, day, hour, minute, 0);
                value.set(Calendar.MILLISECOND, 0);
                if (index >= 0 && index < reminders.size()) {
                    reminders.set(index, value);
                } else {
                    reminders.add(value);
                }
                updateScheduleRows();
            }, base.get(Calendar.HOUR_OF_DAY), base.get(Calendar.MINUTE), true).show();
        }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void addReminder() {
        if (!binding.reminderSwitch.isChecked()) {
            binding.reminderSwitch.setChecked(true);
            return;
        }
        pickReminderDateTime(-1);
    }

    private void removeReminder(int index) {
        if (index < 0 || index >= reminders.size()) return;
        reminders.remove(index);
        if (reminders.isEmpty()) {
            binding.reminderSwitch.setChecked(false);
            return;
        }
        updateScheduleRows();
    }

    private void renderReminders() {
        binding.reminderList.removeAllViews();
        for (int i = 0; i < reminders.size(); i++) {
            View row = getLayoutInflater().inflate(R.layout.item_reminder_row,
                    binding.reminderList, false);
            TextView time = row.findViewById(R.id.reminderTime);
            time.setText(Dates.formatDateTime(this, reminders.get(i).getTime()));
            final int index = i;
            row.setOnClickListener(v -> pickReminderDateTime(index));
            row.findViewById(R.id.reminderRemove).setOnClickListener(v -> removeReminder(index));
            binding.reminderList.addView(row);
            if (i < reminders.size() - 1) addReminderDivider();
        }
    }

    private void addReminderDivider() {
        int density = (int) getResources().getDisplayMetrics().density;
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, density));
        params.setMarginStart(64 * density);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.color_outline_variant));
        binding.reminderList.addView(divider);
    }

    private void pickRepeatEnd() {
        Calendar base = repeatEnd == null ? (dueDate == null ? Calendar.getInstance() : dueDate)
                : repeatEnd;
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar value = Calendar.getInstance();
            value.set(year, month, day, 23, 59, 59);
            value.set(Calendar.MILLISECOND, 999);
            repeatEnd = value;
            updateScheduleRows();
        }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void chooseRepeat() {
        String[] options = {
                getString(R.string.repeat_none),
                getString(R.string.repeat_daily),
                getString(R.string.repeat_weekly),
                getString(R.string.repeat_monthly)
        };
        String[] values = {"none", "daily", "weekly", "monthly"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(repeatType)) checked = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.repeat_label)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    repeatType = values[which];
                    if ("none".equals(repeatType)) repeatEnd = null;
                    updateScheduleRows();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateScheduleRows() {
        binding.dateRow.rowValue.setText(dueDate == null
                ? getString(R.string.no_due_date) : Dates.formatDate(this, dueDate.getTime()));
        binding.timeRow.rowValue.setText(dueDate == null
                ? getString(R.string.not_set) : Dates.formatTime(this, dueDate.getTime()));

        boolean reminderOn = binding.reminderSwitch.isChecked();
        binding.reminderList.setVisibility(reminderOn ? View.VISIBLE : View.GONE);
        binding.addReminderRow.setVisibility(reminderOn ? View.VISIBLE : View.GONE);
        renderReminders();

        int repeatLabel = "daily".equals(repeatType) ? R.string.repeat_daily
                : "weekly".equals(repeatType) ? R.string.repeat_weekly
                : "monthly".equals(repeatType) ? R.string.repeat_monthly : R.string.repeat_none;
        binding.repeatRow.rowValue.setText(repeatLabel);

        boolean repeats = !"none".equals(repeatType);
        binding.repeatEndRow.getRoot().setVisibility(repeats ? View.VISIBLE : View.GONE);
        binding.repeatEndDivider.setVisibility(repeats ? View.VISIBLE : View.GONE);
        binding.repeatEndRow.rowValue.setText(repeatEnd == null
                ? getString(R.string.optional) : Dates.formatDate(this, repeatEnd.getTime()));
        updateEstimateValue();
    }

    private void save() {
        binding.titleLayout.setError(null);
        String title = text(binding.titleEdit.getText());
        String description = text(binding.descEdit.getText());
        if (title.isEmpty()) {
            binding.titleLayout.setError(getString(R.string.error_title_required));
            binding.titleEdit.requestFocus();
            return;
        }
        if (title.length() > 100) {
            binding.titleLayout.setError(getString(R.string.error_title_long));
            return;
        }
        if (description.length() > 500) {
            binding.descLayout.setError(getString(R.string.error_desc_long));
            return;
        }
        if (dueDate != null && isNew && dueDate.getTimeInMillis() < System.currentTimeMillis()) {
            toast(getString(R.string.error_deadline_past));
            return;
        }
        if (binding.reminderSwitch.isChecked()) {
            if (reminders.isEmpty()) {
                toast(getString(R.string.error_choose_date));
                return;
            }
            for (Calendar value : reminders) {
                if (value.getTimeInMillis() <= System.currentTimeMillis()) {
                    toast(getString(R.string.error_deadline_past));
                    return;
                }
                if (dueDate != null && value.after(dueDate)) {
                    toast(getString(R.string.error_reminder_after_deadline));
                    return;
                }
            }
        }

        if (editing != null) ReminderScheduler.cancelTodo(this, editing);

        Todo todo = editing == null ? new Todo() : editing;
        todo.setTitle(title);
        todo.setDescription(description.isEmpty() ? null : description);
        todo.setCategoryId(categoryId);
        todo.setCategoryName(categoryName);
        todo.setPriority(priority);
        todo.setDueDate(dueDate == null ? null : new Timestamp(dueDate.getTime()));
        todo.setRepeatType(repeatType);
        todo.setRepeatEndDate(repeatEnd == null ? null : new Timestamp(repeatEnd.getTime()));
        todo.setEstimateMinutes(estimateMinutes);
        // starred and subtasks are edited from the detail screen, never here.
        // They are preserved automatically because the existing model is reused
        // above — but only as long as this method never builds a fresh Todo for
        // an edit, so that is called out here on purpose.

        List<Timestamp> times = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (binding.reminderSwitch.isChecked()) {
            for (Calendar value : reminders) {
                times.add(new Timestamp(value.getTime()));
                ids.add((long) ReminderScheduler.newId());
            }
        }
        todo.setReminderTimes(times);
        todo.setNotificationIds(ids);

        binding.saveButton.setEnabled(false);
        final boolean newTask = isNew;
        repository.saveTodo(uid(), todo, isNew, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                todo.setDocId(id);
                // Only a new task starts open; an edit must keep its completion state.
                if (newTask) {
                    todo.setCompleted(false);
                    todo.setCompletedAt(null);
                }
                if (binding.reminderSwitch.isChecked()) {
                    ReminderScheduler.scheduleTodo(TodoFormActivity.this, uid(), todo);
                }
                toast(getString(newTask ? R.string.todo_saved : R.string.todo_saved));
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(Exception error) {
                binding.saveButton.setEnabled(true);
                toast(getString(R.string.save_failed));
            }
        });
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (categoryListener != null) categoryListener.remove();
    }
}
