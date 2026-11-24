package com.example.pencatatan.todo;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.data.model.ActivityLog;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.databinding.ActivityLogBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Paginated activity history with client-side action and date-range filters so
 * the user can quickly answer "what did I finish last week?".
 */
public class ActivityLogActivity extends BaseActivity {

    private static final int PAGE = 30;

    private ActivityLogBinding binding;
    private LogAdapter adapter;
    private final TodoRepository repository = new TodoRepository();
    private final List<ActivityLog> loaded = new ArrayList<>();
    private Date oldest;
    private boolean loading;

    private String actionFilter = null;   // null = all
    private int rangeDays = 0;            // 0 = all time

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        binding = ActivityLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_stats);
        binding.emptyState.emptyTitle.setText(R.string.activity_empty);
        binding.emptyState.emptyDesc.setVisibility(View.GONE);
        binding.emptyState.emptyAction.setVisibility(View.GONE);
        binding.emptyState.getRoot().setVisibility(View.GONE);

        adapter = new LogAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.loadMore.setOnClickListener(v -> load(true));
        binding.actionFilter.setOnClickListener(v -> chooseAction());
        binding.rangeFilter.setOnClickListener(v -> chooseRange());
        binding.resetFilters.setOnClickListener(v -> {
            actionFilter = null;
            rangeDays = 0;
            render();
        });
        load(false);
    }

    private void chooseAction() {
        final String[] actions = {null, "created", "updated", "completed", "uncompleted",
                "deleted"};
        String[] labels = {
                getString(R.string.activity_filter_all),
                getString(R.string.activity_created),
                getString(R.string.activity_updated),
                getString(R.string.activity_completed),
                getString(R.string.activity_uncompleted),
                getString(R.string.activity_deleted)
        };
        int checked = 0;
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] == null ? actionFilter == null : actions[i].equals(actionFilter)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.activity_filter_all)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    actionFilter = actions[which];
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseRange() {
        int[] days = {0, 1, 7, 30};
        String[] labels = {
                getString(R.string.finance_filter_date_any),
                getString(R.string.activity_filter_today),
                getString(R.string.activity_filter_week),
                getString(R.string.activity_filter_month)
        };
        int checked = 0;
        for (int i = 0; i < days.length; i++) {
            if (days[i] == rangeDays) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finance_filter_date)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    rangeDays = days[which];
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void render() {
        if (binding == null) return;
        boolean active = actionFilter != null || rangeDays > 0;
        binding.resetFilters.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.actionFilter.setText(actionFilter == null
                ? getString(R.string.activity_filter_all) : actionLabel(actionFilter));
        binding.rangeFilter.setText(rangeDays == 0
                ? getString(R.string.finance_filter_date) : rangeLabel());

        List<ActivityLog> filtered = new ArrayList<>();
        Date cutoff = null;
        if (rangeDays > 0) {
            Calendar start = Calendar.getInstance();
            if (rangeDays > 1) start.add(Calendar.DAY_OF_MONTH, -(rangeDays - 1));
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            cutoff = start.getTime();
        }
        for (ActivityLog log : loaded) {
            if (actionFilter != null && !actionFilter.equals(log.getAction())) continue;
            if (cutoff != null) {
                if (log.getTimestamp() == null) continue;
                if (log.getTimestamp().toDate().before(cutoff)) continue;
            }
            filtered.add(log);
        }
        adapter.submit(filtered);
        binding.resultCount.setText(getResources().getQuantityString(
                R.plurals.activity_result_plural, filtered.size(), filtered.size()));

        if (filtered.isEmpty()) {
            binding.emptyState.getRoot().setVisibility(View.VISIBLE);
            binding.emptyState.emptyTitle.setText(active
                    ? R.string.activity_filter_empty : R.string.activity_empty);
        } else {
            binding.emptyState.getRoot().setVisibility(View.GONE);
        }
    }

    private String actionLabel(String action) {
        switch (action) {
            case "created":
                return getString(R.string.activity_created);
            case "updated":
                return getString(R.string.activity_updated);
            case "completed":
                return getString(R.string.activity_completed);
            case "uncompleted":
                return getString(R.string.activity_uncompleted);
            case "deleted":
                return getString(R.string.activity_deleted);
            default:
                return getString(R.string.activity_filter_all);
        }
    }

    private String rangeLabel() {
        if (rangeDays == 1) return getString(R.string.activity_filter_today);
        if (rangeDays == 7) return getString(R.string.activity_filter_week);
        if (rangeDays == 30) return getString(R.string.activity_filter_month);
        return getString(R.string.finance_filter_date);
    }

    private void load(boolean more) {
        if (loading) return;
        loading = true;
        if (more) binding.loadMore.setEnabled(false);
        repository.loadLogs(uid(), more ? oldest : null, PAGE, new TodoRepository.OnLogs() {
            @Override
            public void onData(List<ActivityLog> logs) {
                loading = false;
                binding.loadMore.setEnabled(true);
                if (more) {
                    loaded.addAll(logs);
                } else {
                    loaded.clear();
                    loaded.addAll(logs);
                }

                if (!logs.isEmpty()) {
                    ActivityLog last = logs.get(logs.size() - 1);
                    if (last.getTimestamp() != null) oldest = last.getTimestamp().toDate();
                }
                boolean hasMore = logs.size() == PAGE;
                binding.loadMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                render();
            }

            @Override
            public void onError(Exception error) {
                loading = false;
                binding.loadMore.setEnabled(true);
                toast(getString(R.string.error_load));
            }
        });
    }
}
