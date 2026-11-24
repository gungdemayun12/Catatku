package com.example.pencatatan.finance;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.pencatatan.R;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.Budget;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.databinding.FragmentFinanceReportsBinding;
import com.example.pencatatan.databinding.ItemReportLegendBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FinanceReportsFragment extends Fragment {

    private FragmentFinanceReportsBinding binding;
    private FinanceViewModel viewModel;
    private final List<Transaction> all = new ArrayList<>();
    private final Map<String, FinanceCategory> categories = new HashMap<>();
    private String baseCurrency = "IDR";
    private String monthKey = "";
    private int trendMonths = 6;
    private final List<Budget> budgets = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFinanceReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);
        binding.exportButton.setOnClickListener(v -> {
            List<Transaction> monthly = FinanceCalc.forMonth(all, monthKey);
            if (monthly.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), R.string.report_export_empty,
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            ExportDialog.showFormatPicker(requireActivity(), new ExportDialog.Source() {
                @Override
                public List<Transaction> transactionsFor(boolean wholeHistory) {
                    return wholeHistory ? all : FinanceCalc.forMonth(all, monthKey);
                }

                @Override
                public String rangeLabel(boolean wholeHistory) {
                    if (wholeHistory) return getString(R.string.export_scope_all);
                    return Dates.formatMonth(requireContext(), FinanceCalc.monthCalendar(monthKey));
                }

                @Override
                public String baseCurrency() {
                    return baseCurrency;
                }
            });
        });

        viewModel.user().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                baseCurrency = user.getCurrency() == null ? "IDR" : user.getCurrency();
            }
            render();
        });
        binding.trendRangeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            trendMonths = checkedId == R.id.trend12 ? 12 : 6;
            renderTrend();
        });
        viewModel.transactions().observe(getViewLifecycleOwner(), data -> {
            all.clear();
            if (data != null) all.addAll(data);
            render();
        });
        viewModel.categories().observe(getViewLifecycleOwner(), data -> {
            categories.clear();
            if (data != null) {
                for (FinanceCategory category : data) {
                    if (category.getDocId() != null) categories.put(category.getDocId(), category);
                }
            }
            render();
        });
        viewModel.monthKey().observe(getViewLifecycleOwner(), key -> {
            monthKey = key == null ? "" : key;
            render();
        });
        viewModel.budgets().observe(getViewLifecycleOwner(), data -> {
            budgets.clear();
            if (data != null) budgets.addAll(data);
            render();
        });
    }

    private void render() {
        if (binding == null) return;
        if (monthKey == null) monthKey = "";
        List<Transaction> monthly = FinanceCalc.forMonth(all, monthKey);
        long income = FinanceCalc.income(monthly);
        long expense = FinanceCalc.expense(monthly);
        long net = income - expense;

        binding.incomeValue.setText(MoneyText.format(requireContext(), baseCurrency, income));
        binding.expenseValue.setText(MoneyText.format(requireContext(), baseCurrency, expense));
        binding.netValue.setText(MoneyText.formatSigned(requireContext(), baseCurrency, net, net >= 0));
        binding.netValue.setTextColor(ContextCompat.getColor(requireContext(),
                net >= 0 ? R.color.income : R.color.expense));

        renderDonut(monthly, expense);
        renderBudget(monthly);
        renderTrend();
        renderInsights(monthly, income, expense);
        renderComparison(monthly, expense);
    }

    /** Small helper for the dp/sp conversions the programmatic rows need. */
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * One "icon + label + value" line. The icon sits in a fixed 18dp frame and the
     * label has font padding off, so every insight row shares the same left edge
     * and the same centre line — the same rule the to-do meta rows follow.
     */
    private LinearLayout insightRow(int iconRes, CharSequence text, int textColor) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(34));

        FrameLayout frame = new FrameLayout(requireContext());
        row.addView(frame, new LinearLayout.LayoutParams(dp(18), dp(18)));

        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        icon.setImageResource(iconRes);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(ContextCompat.getColor(requireContext(), textColor));
        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(dp(16), dp(16), android.view.Gravity.CENTER);
        frame.addView(icon, iconParams);

        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextSize(12);
        label.setIncludeFontPadding(false);
        label.setLineSpacing(0, 1f);
        label.setTextColor(ContextCompat.getColor(requireContext(), textColor));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.setMarginStart(dp(8));
        row.addView(label, labelParams);
        return row;
    }

    /**
     * Builds the plain-language answer list. Each entry is something a person would
     * otherwise have to work out by comparing charts, so the card stays useful even
     * when the user does not read the graphs at all.
     */
    private void renderInsights(List<Transaction> monthly, long income, long expense) {
        binding.insightContainer.removeAllViews();
        boolean hasData = income > 0 || expense > 0;
        binding.insightEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        if (!hasData) return;

        long net = income - expense;
        binding.insightContainer.addView(insightRow(
                net >= 0 ? R.drawable.ic_trending_up : R.drawable.ic_trending_down,
                net >= 0
                        ? getString(R.string.finance_insight_net_saved,
                        MoneyText.format(requireContext(), baseCurrency, net))
                        : getString(R.string.finance_insight_net_overspent,
                        MoneyText.format(requireContext(), baseCurrency, -net)),
                net >= 0 ? R.color.income : R.color.expense));

        // Month-over-month comparison, only when last month actually has records.
        String previousKey = FinanceCalc.nextMonthKey(monthKey, -1);
        List<Transaction> previous = FinanceCalc.forMonth(all, previousKey);
        long previousExpense = FinanceCalc.expense(previous);
        if (previousExpense > 0) {
            int change = FinanceCalc.percentChange(previousExpense, expense);
            int color = change > 0 ? R.color.expense : R.color.income;
            CharSequence text;
            int icon;
            if (change == 0) {
                text = getString(R.string.finance_insight_vs_last_same);
                icon = R.drawable.ic_money_circle;
                color = R.color.color_on_surface_variant;
            } else if (change > 0) {
                text = getString(R.string.finance_insight_vs_last_up, change);
                icon = R.drawable.ic_trending_up;
            } else {
                text = getString(R.string.finance_insight_vs_last_down, Math.abs(change));
                icon = R.drawable.ic_trending_down;
            }
            binding.insightContainer.addView(insightRow(icon, text, color));

            // Spell out which category moved the most, so an increase is actionable.
            FinanceCalc.CategoryDelta jump = FinanceCalc.biggestIncrease(monthly, previous);
            if (jump != null) {
                FinanceCategory category = categories.get(jump.categoryId);
                String name = category == null ? getString(R.string.category_none)
                        : NameKeys.resolve(requireContext(), category.getName(),
                        category.getNameKey());
                binding.insightContainer.addView(insightRow(R.drawable.ic_warning,
                        getString(R.string.finance_insight_cat_jump, name,
                                MoneyText.format(requireContext(), baseCurrency, jump.delta)),
                        R.color.warning));
            }
        }

        // Biggest category share.
        Map<String, Long> byCategory = FinanceCalc.expenseByCategory(monthly);
        String topId = null;
        long topValue = 0;
        for (Map.Entry<String, Long> entry : byCategory.entrySet()) {
            if (entry.getValue() > topValue) {
                topValue = entry.getValue();
                topId = entry.getKey();
            }
        }
        if (topId != null && expense > 0) {
            FinanceCategory category = categories.get(topId);
            String name = category == null ? getString(R.string.category_none)
                    : NameKeys.resolve(requireContext(), category.getName(),
                    category.getNameKey());
            int share = (int) Math.round(topValue * 100.0 / expense);
            binding.insightContainer.addView(insightRow(R.drawable.ic_receipt,
                    getString(R.string.finance_insight_top_category, name) + " \u00B7 "
                            + getString(R.string.finance_insight_top_category_value,
                            MoneyText.format(requireContext(), baseCurrency, topValue), share),
                    R.color.color_on_surface));
        }

        // Single largest expense: usually the "oh, that one" transaction.
        Transaction largest = FinanceCalc.largestExpense(monthly);
        if (largest != null) {
            String date = largest.getDate() == null ? ""
                    : Dates.formatShortDate(requireContext(), largest.getDate().toDate());
            binding.insightContainer.addView(insightRow(R.drawable.ic_flag,
                    getString(R.string.finance_insight_largest) + ": "
                            + getString(R.string.finance_insight_largest_value,
                            MoneyText.format(requireContext(), baseCurrency,
                                    largest.getAmountBase()), date),
                    R.color.color_on_surface_variant));
        }

        // Average daily pace + projection to the end of the month.
        int elapsed = FinanceCalc.elapsedDays(monthKey);
        long daily = elapsed == 0 ? 0 : Math.round(expense / (double) elapsed);
        binding.insightContainer.addView(insightRow(R.drawable.ic_calendar,
                getString(R.string.finance_insight_daily) + ": "
                        + getString(R.string.finance_insight_daily_value,
                        MoneyText.format(requireContext(), baseCurrency, daily), elapsed),
                R.color.color_on_surface_variant));

        long projected = FinanceCalc.projectedExpense(monthly, monthKey);
        if (projected > expense) {
            binding.insightContainer.addView(insightRow(R.drawable.ic_stats,
                    getString(R.string.finance_insight_projection) + ": "
                            + MoneyText.format(requireContext(), baseCurrency, projected),
                    projected > income && income > 0 ? R.color.expense
                            : R.color.color_on_surface_variant));
        }
    }

    /**
     * Horizontal bars per category, normalised to the biggest one, so a reader can
     * compare magnitudes directly. Shares the palette with the donut, which makes
     * the two views readable together.
     */
    private void renderComparison(List<Transaction> monthly, long expense) {
        Map<String, Long> byCategory = FinanceCalc.expenseByCategory(monthly);
        if (byCategory.isEmpty() || expense <= 0) {
            binding.compareContainer.removeAllViews();
            binding.compareEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.compareEmpty.setVisibility(View.GONE);

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(byCategory.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        long max = sorted.get(0).getValue();
        binding.compareContainer.removeAllViews();
        int shown = 0;
        for (Map.Entry<String, Long> entry : sorted) {
            if (shown >= 6) break;
            shown++;
            FinanceCategory category = categories.get(entry.getKey());
            String name = category == null ? getString(R.string.category_none)
                    : NameKeys.resolve(requireContext(), category.getName(),
                    category.getNameKey());
            int color = category == null ? paletteColor(shown)
                    : ColorUtil.parse(category.getColorHex(), paletteColor(shown));

            LinearLayout block = new LinearLayout(requireContext());
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(4), 0, dp(4));

            LinearLayout header = new LinearLayout(requireContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView label = new TextView(requireContext());
            label.setText(name);
            label.setTextSize(12);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setIncludeFontPadding(false);
            label.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_surface));
            header.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView value = new TextView(requireContext());
            int share = (int) Math.round(entry.getValue() * 100.0 / expense);
            value.setText(getString(R.string.finance_compare_share,
                    MoneyText.format(requireContext(), baseCurrency, entry.getValue()), share));
            value.setTextSize(10);
            value.setMaxLines(1);
            value.setIncludeFontPadding(false);
            value.setTextColor(ContextCompat.getColor(requireContext(),
                    R.color.color_on_surface_variant));
            header.addView(value);
            block.addView(header);

            com.google.android.material.progressindicator.LinearProgressIndicator bar =
                    new com.google.android.material.progressindicator.LinearProgressIndicator(
                            requireContext());
            bar.setMax(100);
            bar.setProgressCompat(max <= 0 ? 0
                    : Math.max(2, Math.round(entry.getValue() * 100f / max)), false);
            bar.setIndicatorColor(color);
            bar.setTrackColor(ContextCompat.getColor(requireContext(), R.color.chart_track));
            bar.setTrackCornerRadius(dp(4));
            bar.setTrackThickness(dp(7));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            barParams.topMargin = dp(5);
            block.addView(bar, barParams);

            binding.compareContainer.addView(block);
        }
    }

    /** Compact "spent / limit" progress rows for every budget in the month. */
    private void renderBudget(List<Transaction> monthly) {
        List<Budget> monthBudgets = new ArrayList<>();
        for (Budget budget : budgets) {
            if (monthKey.equals(budget.getMonthKey())) monthBudgets.add(budget);
        }
        monthBudgets.sort((a, b) -> Long.compare(b.getLimitAmount(), a.getLimitAmount()));

        binding.budgetContainer.removeAllViews();
        binding.budgetCaption.setText(getResources().getQuantityString(
                R.plurals.report_budget_count_plural, monthBudgets.size(), monthBudgets.size()));
        binding.budgetCaption.setVisibility(monthBudgets.isEmpty() ? View.GONE : View.VISIBLE);
        binding.budgetEmpty.setVisibility(monthBudgets.isEmpty() ? View.VISIBLE : View.GONE);

        for (Budget budget : monthBudgets) {
            FinanceCategory category = categories.get(budget.getCategoryId());
            String name = category == null
                    ? (budget.getCategoryName() == null ? getString(R.string.category_none)
                    : budget.getCategoryName())
                    : NameKeys.resolve(requireContext(), category.getName(), category.getNameKey());

            long spent = FinanceCalc.spentForCategory(monthly, budget.getCategoryId());
            long limit = budget.getLimitAmount();
            int percent = limit <= 0 ? 0 : (int) Math.round(spent * 100.0 / limit);
            int accent = ContextCompat.getColor(requireContext(),
                    percent >= 100 ? R.color.expense
                            : percent >= 80 ? R.color.warning : R.color.income);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = Math.round(4 * getResources().getDisplayMetrics().density);
            row.setPadding(0, pad, 0, pad);

            LinearLayout header = new LinearLayout(requireContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView title = new TextView(requireContext());
            title.setText(name);
            title.setTextSize(12);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_surface));
            header.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView value = new TextView(requireContext());
            value.setText(getString(R.string.report_budget_line,
                    MoneyText.format(requireContext(), baseCurrency, spent),
                    MoneyText.format(requireContext(), baseCurrency, limit), percent));
            value.setTextSize(10);
            value.setMaxLines(1);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            value.setTextColor(ContextCompat.getColor(requireContext(),
                    R.color.color_on_surface_variant));
            header.addView(value);
            row.addView(header);

            com.google.android.material.progressindicator.LinearProgressIndicator bar =
                    new com.google.android.material.progressindicator.LinearProgressIndicator(
                            requireContext());
            bar.setMax(100);
            bar.setProgressCompat(Math.min(100, percent), false);
            bar.setIndicatorColor(accent);
            bar.setTrackColor(ContextCompat.getColor(requireContext(), R.color.chart_track));
            bar.setTrackCornerRadius((int) (4 * getResources().getDisplayMetrics().density));
            bar.setTrackThickness((int) (8 * getResources().getDisplayMetrics().density));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            barParams.topMargin = pad;
            row.addView(bar, barParams);

            if (percent >= 100) {
                TextView over = new TextView(requireContext());
                over.setText(getString(R.string.report_budget_over, MoneyText.format(
                        requireContext(), baseCurrency, spent - limit)));
                over.setTextSize(10);
                over.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense));
                row.addView(over);
            }
            binding.budgetContainer.addView(row);
        }
    }

    /** Deterministic palette colour so uncategorised slices stay distinguishable. */
    private int paletteColor(int index) {
        int[] palette = {
                R.color.chart_palette_1, R.color.chart_palette_2, R.color.chart_palette_3,
                R.color.chart_palette_4, R.color.chart_palette_5, R.color.chart_palette_6,
                R.color.chart_palette_7, R.color.chart_palette_8
        };
        return ContextCompat.getColor(requireContext(), palette[index % palette.length]);
    }

    private void renderDonut(List<Transaction> monthly, long totalExpense) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Transaction transaction : monthly) {
            if (transaction.isIncome()) continue;
            // Transfer legs have no category; without this they would surface as a
            // phantom "uncategorised" slice and the slices would no longer add up to
            // the total shown in the middle of the donut.
            if (transaction.isTransfer()) continue;
            String key = transaction.getCategoryId() == null ? "__none__"
                    : transaction.getCategoryId();
            totals.put(key, totals.getOrDefault(key, 0L) + transaction.getAmountBase());
        }

        List<Slice> slices = new ArrayList<>();
        int fallbackIndex = 0;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            if (entry.getValue() <= 0) continue;
            FinanceCategory category = categories.get(entry.getKey());
            String name = category == null
                    ? (entry.getKey().equals("__none__") ? getString(R.string.category_none)
                    : NameKeys.resolve(requireContext(), null, null))
                    : NameKeys.resolve(requireContext(), category.getName(), category.getNameKey());
            if (name == null || name.trim().isEmpty()) name = getString(R.string.category_none);
            int fallback = paletteColor(fallbackIndex++);
            int color = category == null ? fallback
                    : ColorUtil.parse(category.getColorHex(), fallback);
            slices.add(new Slice(name, entry.getValue(), color));
        }
        slices.sort((a, b) -> Long.compare(b.amount, a.amount));

        if (slices.size() > 6) {
            long other = 0;
            for (int i = 6; i < slices.size(); i++) other += slices.get(i).amount;
            slices = new ArrayList<>(slices.subList(0, 6));
            if (other > 0) {
                slices.add(new Slice(getString(R.string.other), other,
                        ContextCompat.getColor(requireContext(), R.color.chart_palette_8)));
            }
        }

        int[] colors = new int[slices.size()];
        float[] values = new float[slices.size()];
        for (int i = 0; i < slices.size(); i++) {
            colors[i] = slices.get(i).color;
            values[i] = (float) Money.toMajorUnits(baseCurrency, slices.get(i).amount);
        }
        binding.donut.setData(values, colors,
                MoneyText.format(requireContext(), baseCurrency, totalExpense),
                getString(R.string.finance_expense));
        binding.donutEmpty.setVisibility(totalExpense <= 0 ? View.VISIBLE : View.GONE);
        binding.donut.setAlpha(totalExpense <= 0 ? 0.5f : 1f);

        binding.legendContainer.removeAllViews();
        binding.legendContainer.setVisibility(slices.isEmpty() ? View.GONE : View.VISIBLE);
        for (Slice slice : slices) {
            ItemReportLegendBinding row = ItemReportLegendBinding.inflate(getLayoutInflater(),
                    binding.legendContainer, false);
            row.legendDot.setBackgroundTintList(ColorStateList.valueOf(slice.color));
            row.legendName.setText(slice.name);
            int percent = totalExpense <= 0 ? 0
                    : (int) Math.round(slice.amount * 100.0 / totalExpense);
            row.legendValue.setText(getString(R.string.report_legend_value,
                    MoneyText.format(requireContext(), baseCurrency, slice.amount), percent));
            binding.legendContainer.addView(row.getRoot());
        }
    }

    private void renderTrend() {
        int months = trendMonths;
        String[] labels = new String[months];
        float[] income = new float[months];
        float[] expense = new float[months];
        boolean hasData = false;
        for (int i = 0; i < months; i++) {
            String key = FinanceCalc.nextMonthKey(monthKey, i - (months - 1));
            labels[i] = Dates.formatMonthShort(requireContext(),
                    FinanceCalc.monthCalendar(key));
            List<Transaction> monthly = FinanceCalc.forMonth(all, key);
            income[i] = (float) Money.toMajorUnits(baseCurrency, FinanceCalc.income(monthly));
            expense[i] = (float) Money.toMajorUnits(baseCurrency, FinanceCalc.expense(monthly));
            if (income[i] > 0 || expense[i] > 0) hasData = true;
        }
        binding.trendChart.setData(labels, income, expense);
        binding.trendEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        binding.trendChart.setAlpha(hasData ? 1f : 0.45f);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
