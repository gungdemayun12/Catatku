package com.example.pencatatan.todo;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pencatatan.BuildConfig;
import com.example.pencatatan.R;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.common.RepoCallback;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.FragmentTodoSettingsBinding;
import com.example.pencatatan.settings.NotificationSettingsActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TodoSettingsFragment extends Fragment {

    private FragmentTodoSettingsBinding binding;
    private final UserRepository userRepository = new UserRepository();
    private final TodoRepository todoRepository = new TodoRepository();
    private String reminderTime = "08:00";
    private ListenerRegistration categoryListener;
    private final List<TodoCategory> categories = new ArrayList<>();
    private final List<String> categoryIds = new ArrayList<>();
    private final List<String> categoryNames = new ArrayList<>();
    private String defaultCategoryId;
    private String defaultCategoryName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodoSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.rowCategories.rowIcon.setImageResource(R.drawable.ic_tag);
        binding.rowCategories.rowTitle.setText(R.string.todo_settings_categories);
        binding.rowCategories.rowSubtitle.setText(R.string.todo_settings_categories_desc);
        binding.rowCategories.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CategoriesActivity.class)));

        binding.rowDefaultReminder.rowIcon.setImageResource(R.drawable.ic_clock);
        binding.rowDefaultReminder.rowTitle.setText(R.string.todo_settings_default_reminder);
        binding.rowDefaultReminder.rowSubtitle.setText(R.string.todo_settings_default_reminder_desc);
        binding.rowDefaultReminder.getRoot().setOnClickListener(v -> pickReminderTime());

        binding.rowDefaultPriority.rowIcon.setImageResource(R.drawable.ic_flag);
        binding.rowDefaultPriority.rowTitle.setText(R.string.todo_settings_default_priority);
        binding.rowDefaultPriority.rowSubtitle.setText(R.string.todo_settings_default_priority_desc);
        binding.rowDefaultPriority.getRoot().setOnClickListener(v -> pickDefaultPriority());

        binding.rowDefaultCategory.rowIcon.setImageResource(R.drawable.ic_tag);
        binding.rowDefaultCategory.rowTitle.setText(R.string.todo_settings_default_category);
        binding.rowDefaultCategory.rowSubtitle.setText(R.string.todo_settings_default_category_desc);
        binding.rowDefaultCategory.getRoot().setOnClickListener(v -> pickDefaultCategory());

        binding.rowSort.rowIcon.setImageResource(R.drawable.ic_sort);
        binding.rowSort.rowTitle.setText(R.string.todo_settings_sort);
        binding.rowSort.rowSubtitle.setText(R.string.todo_settings_sort_desc);
        binding.rowSort.getRoot().setOnClickListener(v -> pickSort());

        binding.rowActivity.rowIcon.setImageResource(R.drawable.ic_stats);
        binding.rowActivity.rowTitle.setText(R.string.todo_settings_activity);
        binding.rowActivity.rowSubtitle.setText(R.string.todo_settings_activity_desc);
        binding.rowActivity.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ActivityLogActivity.class)));

        binding.rowNotifications.rowIcon.setImageResource(R.drawable.ic_bell);
        binding.rowNotifications.rowTitle.setText(R.string.todo_settings_notifications);
        binding.rowNotifications.rowSubtitle.setText(R.string.todo_settings_notifications_desc);
        binding.rowNotifications.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationSettingsActivity.class)));

        binding.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        binding.rowGrouping.rowIcon.setImageResource(R.drawable.ic_list);
        binding.rowGrouping.rowTitle.setText(R.string.todo_group_tasks);
        binding.rowGrouping.rowSubtitle.setText(R.string.todo_settings_group_desc);
        binding.rowGrouping.rowSwitch.setChecked(Prefs.groupTasks(requireContext()));
        binding.rowGrouping.getRoot().setOnClickListener(v -> {
            boolean next = !binding.rowGrouping.rowSwitch.isChecked();
            binding.rowGrouping.rowSwitch.setChecked(next);
            Prefs.setGroupTasks(requireContext(), next);
        });

        binding.rowSnooze.rowIcon.setImageResource(R.drawable.ic_clock);
        binding.rowSnooze.rowTitle.setText(R.string.todo_snooze_days);
        binding.rowSnooze.getRoot().setOnClickListener(v -> pickSnoozeDays());

        binding.rowStale.rowIcon.setImageResource(R.drawable.ic_warning);
        binding.rowStale.rowTitle.setText(R.string.todo_stale_days);
        binding.rowStale.getRoot().setOnClickListener(v -> pickStaleDays());

        binding.rowClearHistory.rowIcon.setImageResource(R.drawable.ic_delete);
        binding.rowClearHistory.rowTitle.setText(R.string.todo_clear_history);
        binding.rowClearHistory.rowSubtitle.setText(R.string.todo_clear_history_desc);
        binding.rowClearHistory.getRoot().setOnClickListener(v -> confirmClearHistory());

        updateSnoozeRow();
        updateStaleRow();

        defaultCategoryId = Prefs.getDefaultCategoryId(requireContext());
        defaultCategoryName = Prefs.getDefaultCategoryName(requireContext());
        updatePriorityRow();
        updateSortRow();
        updateDefaultCategoryRow();
        listenCategories();

        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid != null) {
            userRepository.get(uid, new UserRepository.UserListener() {
                @Override
                public void onUser(User user) {
                    reminderTime = user.getDefaultReminderTime() == null
                            ? "08:00" : user.getDefaultReminderTime();
                    updateReminderRow();
                }

                @Override
                public void onError(Exception error) {
                }
            });
        }
    }

    private void updateReminderRow() {
        if (binding == null) return;
        binding.rowDefaultReminder.rowSubtitle.setText(reminderTime);
    }

    private void updateSnoozeRow() {
        if (binding == null) return;
        binding.rowSnooze.rowSubtitle.setText(
                getString(R.string.todo_days_format, Prefs.snoozeDays(requireContext())));
    }

    private void updateStaleRow() {
        if (binding == null) return;
        binding.rowStale.rowSubtitle.setText(
                getString(R.string.todo_days_format, Prefs.staleDays(requireContext())));
    }

    private void pickSnoozeDays() {
        int[] values = {1, 3, 7, 14};
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(R.string.todo_days_format, values[i]);
        }
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == Prefs.snoozeDays(requireContext())) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_snooze_days)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Prefs.setSnoozeDays(requireContext(), values[which]);
                    updateSnoozeRow();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickStaleDays() {
        int[] values = {7, 14, 30, 60};
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(R.string.todo_days_format, values[i]);
        }
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == Prefs.staleDays(requireContext())) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_stale_days)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Prefs.setStaleDays(requireContext(), values[which]);
                    updateStaleRow();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearHistory() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_clear_history)
                .setMessage(R.string.todo_clear_history_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        todoRepository.clearHistory(uid, new RepoCallback() {
                            @Override
                            public void onSuccess() {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(),
                                            R.string.todo_clear_history_done,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), R.string.delete_failed,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        }))
                .show();
    }

    private void updatePriorityRow() {
        if (binding == null) return;
        binding.rowDefaultPriority.rowSubtitle.setText(priorityLabel(Prefs.getDefaultPriority(requireContext())));
    }

    private void updateSortRow() {
        if (binding == null) return;
        binding.rowSort.rowSubtitle.setText(sortLabel(Prefs.getTaskSort(requireContext())));
    }

    private void updateDefaultCategoryRow() {
        if (binding == null) return;
        binding.rowDefaultCategory.rowSubtitle.setText(defaultCategoryName == null
                ? getString(R.string.category_none) : defaultCategoryName);
    }

    private String priorityLabel(int value) {
        return getString(value == 2 ? R.string.priority_high
                : value == 0 ? R.string.priority_low : R.string.priority_medium);
    }

    private String sortLabel(String value) {
        if (Prefs.SORT_PRIORITY.equals(value)) return getString(R.string.sort_priority);
        if (Prefs.SORT_CREATED.equals(value)) return getString(R.string.sort_created);
        if (Prefs.SORT_TITLE.equals(value)) return getString(R.string.sort_title);
        return getString(R.string.sort_due_date);
    }

    private void pickDefaultPriority() {
        String[] values = {String.valueOf(0), String.valueOf(1), String.valueOf(2)};
        String[] labels = {getString(R.string.priority_low), getString(R.string.priority_medium),
                getString(R.string.priority_high)};
        int current = Prefs.getDefaultPriority(requireContext());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_settings_default_priority)
                .setSingleChoiceItems(labels, current, (dialog, which) -> {
                    Prefs.setDefaultPriority(requireContext(), Integer.parseInt(values[which]));
                    updatePriorityRow();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickSort() {
        String[] values = {Prefs.SORT_DUE, Prefs.SORT_PRIORITY, Prefs.SORT_CREATED, Prefs.SORT_TITLE};
        String[] labels = {getString(R.string.sort_due_date), getString(R.string.sort_priority),
                getString(R.string.sort_created), getString(R.string.sort_title)};
        String current = Prefs.getTaskSort(requireContext());
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_settings_sort)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Prefs.setTaskSort(requireContext(), values[which]);
                    updateSortRow();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void listenCategories() {
        categoryListener = todoRepository.listenCategories(uidOf(),
                new TodoRepository.OnCategories() {
                    @Override
                    public void onData(List<TodoCategory> data) {
                        categories.clear();
                        if (data != null) categories.addAll(data);
                        rebuildCategories();
                        if (defaultCategoryId != null && !containsCategory(defaultCategoryId)) {
                            defaultCategoryId = null;
                            defaultCategoryName = null;
                            Prefs.setDefaultCategory(requireContext(), null, null);
                        }
                        updateDefaultCategoryRow();
                    }

                    @Override
                    public void onError(Exception error) {
                    }
                });
    }

    private boolean containsCategory(String id) {
        for (TodoCategory category : categories) {
            if (id.equals(category.getDocId())) return true;
        }
        return false;
    }

    private void rebuildCategories() {
        categoryIds.clear();
        categoryNames.clear();
        categoryIds.add(null);
        categoryNames.add(getString(R.string.category_none));
        for (TodoCategory category : categories) {
            if (category.getDocId() == null) continue;
            categoryIds.add(category.getDocId());
            categoryNames.add(NameKeys.resolve(requireContext(), category.getName(),
                    category.getNameKey()));
        }
    }

    private void pickDefaultCategory() {
        if (categoryNames.isEmpty()) return;
        int checked = 0;
        int index = categoryIds.indexOf(defaultCategoryId);
        if (index > 0) checked = index;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_settings_default_category)
                .setSingleChoiceItems(categoryNames.toArray(new String[0]), checked, (dialog, which) -> {
                    defaultCategoryId = categoryIds.get(which);
                    defaultCategoryName = which == 0 ? null : categoryNames.get(which);
                    Prefs.setDefaultCategory(requireContext(), defaultCategoryId, defaultCategoryName);
                    updateDefaultCategoryRow();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String uidOf() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private void pickReminderTime() {
        int hour = 8;
        int minute = 0;
        try {
            String[] parts = reminderTime.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }
        TimePickerDialog dialog = new TimePickerDialog(requireContext(),
                (view, h, m) -> {
                    reminderTime = String.format(Locale.US, "%02d:%02d", h, m);
                    updateReminderRow();
                    String uid = uidOf();
                    if (uid != null) {
                        userRepository.updateDefaultReminder(uid, reminderTime, new RepoCallback() {
                            @Override
                            public void onSuccess() {
                                if (isAdded()) {
                                    android.widget.Toast.makeText(requireContext(),
                                            R.string.default_reminder_saved,
                                            android.widget.Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                            }
                        });
                    }
                }, hour, minute, true);
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (categoryListener != null) categoryListener.remove();
        binding = null;
    }
}
