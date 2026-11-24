package com.example.pencatatan.todo;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.pencatatan.R;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.databinding.FragmentTodoStatsBinding;
import com.example.pencatatan.databinding.ItemReportLegendBinding;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * To-Do statistics: completion rate, tallies, a trend chart that follows the
 * selected period, plus category and priority doughnuts for the open backlog.
 */
public class StatsFragment extends Fragment {

    private FragmentTodoStatsBinding binding;
    private final List<Todo> all = new ArrayList<>();
    private final Map<String, TodoCategory> categories = new HashMap<>();
    private int periodDays = 7;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodoStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TodoViewModel viewModel = new ViewModelProvider(requireActivity()).get(TodoViewModel.class);
        binding.barChart.setBarColor(ContextCompat.getColor(requireContext(), R.color.todo_primary));

        binding.periodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.period30) periodDays = 30;
            else if (checkedId == R.id.periodMonth) periodDays = 0;
            else periodDays = 7;
            render();
        });

        viewModel.todos().observe(getViewLifecycleOwner(), todos -> {
            all.clear();
            if (todos != null) all.addAll(todos);
            render();
        });
        viewModel.categories().observe(getViewLifecycleOwner(), data -> {
            categories.clear();
            if (data != null) {
                for (TodoCategory category : data) {
                    if (category.getDocId() != null) categories.put(category.getDocId(), category);
                }
            }
            render();
        });
    }

    private void render() {
        if (binding == null) return;
        Calendar start = Calendar.getInstance();
        if (periodDays == 0) {
            start = Dates.startOfMonth(start);
        } else {
            start.add(Calendar.DAY_OF_MONTH, -(periodDays - 1));
            start = Dates.startOfDay(start);
        }
        Date startDate = start.getTime();

        int completed = 0;
        int pending = 0;
        int overdue = 0;
        for (Todo todo : all) {
            if (todo.isCompleted()) {
                if (todo.getCompletedAt() != null
                        && !todo.getCompletedAt().toDate().before(startDate)) {
                    completed++;
                }
            } else {
                if (todo.isOverdue()) overdue++;
                if (todo.getDueDate() != null
                        && !todo.getDueDate().toDate().before(startDate)) {
                    pending++;
                }
            }
        }

        int total = completed + pending;
        int rate = total == 0 ? 0 : Math.round(completed * 100f / total);
        binding.rateValue.setText(getString(R.string.stats_rate_value, rate));
        binding.rateBar.setProgressCompat(rate, true);
        binding.rateCaption.setText(getString(R.string.stats_completed_of, completed, total));
        binding.completedValue.setText(String.valueOf(completed));
        binding.pendingValue.setText(String.valueOf(pending));
        binding.overdueValue.setText(String.valueOf(overdue));

        renderTrend();
        renderCategoryDonut();
        renderPriorityDonut();
    }

    /**
     * Buckets completed tasks by day (7/30) or by week (this month) so the chart
     * always matches the period the user picked.
     */
    private void renderTrend() {
        Date now = new Date();
        List<Date> starts = new ArrayList<>();
        List<Date> ends = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        if (periodDays == 7) {
            for (int i = 6; i >= 0; i--) {
                Calendar day = Calendar.getInstance();
                day.add(Calendar.DAY_OF_MONTH, -i);
                starts.add(Dates.startOfDay((Calendar) day.clone()).getTime());
                ends.add(Dates.endOfDay(day).getTime());
                labels.add(Dates.dayLetter(requireContext(), day.get(Calendar.DAY_OF_WEEK)));
            }
        } else if (periodDays == 30) {
            for (int i = 3; i >= 0; i--) {
                Calendar start = Dates.startOfDay(Calendar.getInstance());
                start.add(Calendar.DAY_OF_MONTH, -i * 7 - 6);
                Calendar end = Dates.endOfDay((Calendar) start.clone());
                end.add(Calendar.DAY_OF_MONTH, 6);
                starts.add(start.getTime());
                ends.add(end.getTime());
                labels.add(getString(R.string.stats_week_short, 4 - i));
            }
        } else {
            Calendar monthStart = Dates.startOfMonth(Calendar.getInstance());
            int days = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH);
            int weeks = (days + 6) / 7;
            for (int i = 0; i < weeks; i++) {
                Calendar start = (Calendar) monthStart.clone();
                start.add(Calendar.DAY_OF_MONTH, i * 7);
                Calendar end = (Calendar) start.clone();
                end.add(Calendar.DAY_OF_MONTH, 6);
                ends.add(Dates.endOfDay(end).getTime());
                starts.add(start.getTime());
                labels.add(getString(R.string.stats_week_short, i + 1));
            }
        }

        int buckets = starts.size();
        int[] counts = new int[buckets];
        for (Todo todo : all) {
            if (todo.getCompletedAt() == null) continue;
            Date completedAt = todo.getCompletedAt().toDate();
            for (int i = 0; i < buckets; i++) {
                if (!completedAt.before(starts.get(i)) && !completedAt.after(ends.get(i))) {
                    counts[i]++;
                    break;
                }
            }
        }

        float[] values = new float[buckets];
        for (int i = 0; i < buckets; i++) values[i] = counts[i];
        binding.barChart.setValues(labels.toArray(new String[0]), values);

        boolean empty = countsTotal(counts) == 0;
        binding.chartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.barChart.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private int countsTotal(int[] counts) {
        int total = 0;
        for (int value : counts) total += value;
        return total;
    }

    private int paletteColor(int index) {
        int[] palette = {
                R.color.chart_palette_1, R.color.chart_palette_2, R.color.chart_palette_3,
                R.color.chart_palette_4, R.color.chart_palette_5, R.color.chart_palette_6,
                R.color.chart_palette_7, R.color.chart_palette_8
        };
        return ContextCompat.getColor(requireContext(), palette[index % palette.length]);
    }

    /** Open (not completed) tasks grouped by category. */
    private void renderCategoryDonut() {
        Map<String, Long> totals = new LinkedHashMap<>();
        int open = 0;
        for (Todo todo : all) {
            if (todo.isCompleted()) continue;
            open++;
            String key = todo.getCategoryId() == null ? "__none__" : todo.getCategoryId();
            totals.put(key, totals.getOrDefault(key, 0L) + 1);
        }

        List<Slice> slices = new ArrayList<>();
        int fallback = 0;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            TodoCategory category = categories.get(entry.getKey());
            String name;
            if (category == null) {
                name = entry.getKey().equals("__none__")
                        ? getString(R.string.category_none)
                        : NameKeys.resolve(requireContext(), null, null);
            } else {
                name = NameKeys.resolve(requireContext(), category.getName(),
                        category.getNameKey());
            }
            if (name == null || name.trim().isEmpty()) name = getString(R.string.category_none);
            int fallbackColor = paletteColor(fallback++);
            int color = category == null ? fallbackColor
                    : ColorUtil.parse(category.getColorHex(), fallbackColor);
            slices.add(new Slice(name, entry.getValue(), color));
        }
        slices.sort((a, b) -> Long.compare(b.amount, a.amount));

        renderDonut(binding.categoryDonut, binding.categoryEmpty, binding.categoryLegend,
                slices, String.valueOf(open), getString(R.string.stats_open),
                getString(R.string.stats_by_category_empty));
        binding.categoryCaption.setText(getResources().getQuantityString(
                R.plurals.stats_open_total_plural, open, open));
        binding.categoryCaption.setVisibility(open == 0 ? View.GONE : View.VISIBLE);
    }

    /** Open tasks grouped by priority. */
    private void renderPriorityDonut() {
        String[] labels = {getString(R.string.priority_low), getString(R.string.priority_medium),
                getString(R.string.priority_high)};
        int[] colors = {
                ContextCompat.getColor(requireContext(), R.color.priority_low),
                ContextCompat.getColor(requireContext(), R.color.priority_medium),
                ContextCompat.getColor(requireContext(), R.color.priority_high)
        };
        long[] counts = new long[3];
        int open = 0;
        for (Todo todo : all) {
            if (todo.isCompleted()) continue;
            open++;
            int index = todo.getPriority() == 2 ? 2 : todo.getPriority() == 0 ? 0 : 1;
            counts[index]++;
        }
        List<Slice> slices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (counts[i] > 0) slices.add(new Slice(labels[i], counts[i], colors[i]));
        }
        renderDonut(binding.priorityDonut, binding.priorityEmpty, binding.priorityLegend,
                slices, String.valueOf(open), getString(R.string.stats_open),
                getString(R.string.stats_priority_empty));
    }

    private void renderDonut(com.example.pencatatan.common.DonutChartView donut,
                             View empty, android.widget.LinearLayout legend,
                             List<Slice> slices, String centerValue, String centerCaption,
                             String emptyText) {
        long total = 0;
        for (Slice slice : slices) total += slice.amount;

        float[] values = new float[slices.size()];
        int[] colors = new int[slices.size()];
        for (int i = 0; i < slices.size(); i++) {
            values[i] = slices.get(i).amount;
            colors[i] = slices.get(i).color;
        }
        donut.setData(values, colors, centerValue, centerCaption);
        boolean isEmpty = total <= 0;
        donut.setAlpha(isEmpty ? 0.45f : 1f);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        legend.removeAllViews();
        legend.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        for (Slice slice : slices) {
            ItemReportLegendBinding row = ItemReportLegendBinding.inflate(getLayoutInflater(),
                    legend, false);
            row.legendDot.setBackgroundTintList(ColorStateList.valueOf(slice.color));
            row.legendName.setText(slice.name);
            int percent = total <= 0 ? 0 : (int) Math.round(slice.amount * 100.0 / total);
            row.legendValue.setText(getString(R.string.report_legend_value,
                    String.valueOf(slice.amount), percent));
            legend.addView(row.getRoot());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class Slice {
        final String name;
        final long amount;
        final int color;

        Slice(String name, long amount, int color) {
            this.name = name;
            this.amount = amount;
            this.color = color;
        }
    }
}
