package com.example.pencatatan.todo;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Navigator;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.databinding.ActivityTodoBinding;

public class TodoActivity extends BaseActivity {

    public static final String EXTRA_TAB = "tab";

    private ActivityTodoBinding binding;
    private TodoViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            startActivity(new Intent(this, com.example.pencatatan.auth.LoginActivity.class));
            finish();
            return;
        }
        binding = ActivityTodoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(TodoViewModel.class);
        viewModel.start(uid());

        binding.homeButton.setOnClickListener(v -> Navigator.goHome(this));
        binding.switchButton.setOnClickListener(v -> Navigator.switchFeature(this, Navigator.FEATURE_FINANCE));
        binding.addFab.setOnClickListener(v -> startActivity(new Intent(this, TodoFormActivity.class)));
        binding.addFab.setOnLongClickListener(v -> {
            showQuickCapture();
            return true;
        });

        binding.bottomNav.setItemIconTintList(ContextCompat.getColorStateList(this, R.color.bottom_nav_tint_todo));
        binding.bottomNav.setItemTextColor(ContextCompat.getColorStateList(this, R.color.bottom_nav_tint_todo));
        binding.bottomNav.setItemActiveIndicatorColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.todo_primary_container)));

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_tasks);
            // A caller may ask to land directly on a filtered task list,
            // e.g. "Lihat tugas selesai" from a finished task's detail screen.
            String wantedTab = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_TAB);
            if (wantedTab != null && !wantedTab.isEmpty()) {
                showTab(R.id.nav_tasks);
                TasksFragment fragment = TasksFragment.withStatus(wantedTab);
                getSupportFragmentManager().beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.fragmentContainer, fragment)
                        .commit();
            }
        }
        viewModel.todos().observe(this, this::renderHeader);
    }

    /**
     * Long-press on the "+" button opens quick capture: a one-field sheet that
     * saves a task in two taps. A normal tap still opens the full form, so the
     * classic path is untouched and discoverable, while the fast path is there
     * for people in a hurry.
     */
    private void showQuickCapture() {
        QuickCaptureDialog dialog = QuickCaptureDialog.newInstance();
        dialog.setOnTaskAdded(() -> {
            if (viewModel != null) viewModel.refresh(viewModel.todos().getValue());
        });
        dialog.show(getSupportFragmentManager(), "quick_capture");
    }

    /**
     * Called by {@link QuickCaptureDialog} when the user asks for the full form
     * from the sheet: the typed title follows them instead of being thrown away.
     */
    public void openNewTaskWithTitle(String title) {
        Intent intent = new Intent(this, TodoFormActivity.class);
        if (title != null && !title.trim().isEmpty()) {
            intent.putExtra(TodoFormActivity.EXTRA_PREFILL_TITLE, title.trim());
        }
        startActivity(intent);
    }

    private void showTab(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_calendar) {
            fragment = new CalendarFragment();
        } else if (itemId == R.id.nav_stats) {
            fragment = new StatsFragment();
        } else if (itemId == R.id.nav_settings) {
            fragment = new TodoSettingsFragment();
        } else {
            fragment = new TasksFragment();
        }
        binding.addFab.setVisibility(itemId == R.id.nav_tasks ? android.view.View.VISIBLE
                : android.view.View.GONE);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void renderHeader(java.util.List<Todo> todos) {
        int open = 0;
        int overdue = 0;
        if (todos != null) {
            for (Todo todo : todos) {
                if (!todo.isCompleted()) {
                    open++;
                    if (todo.isOverdue()) overdue++;
                }
            }
        }
        binding.headerSubtitle.setText(getString(R.string.todo_summary, open, overdue));
        binding.openChip.setText(getString(R.string.todo_chip_open, open));
        if (overdue > 0) {
            binding.overduePill.setVisibility(android.view.View.VISIBLE);
            binding.overdueChip.setText(getString(R.string.todo_chip_overdue, overdue));
        } else {
            // Hide the whole pill, not just its label: an empty pill would still
            // show its flag icon with nothing beside it.
            binding.overduePill.setVisibility(android.view.View.GONE);
        }
    }
}
