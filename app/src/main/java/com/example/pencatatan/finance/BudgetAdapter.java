package com.example.pencatatan.finance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.IconMap;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.Budget;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.databinding.ItemBudgetBinding;

import java.util.ArrayList;
import java.util.List;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.Holder> {

    public interface Listener {
        void onOpen(Row row);

        void onLongPress(Row row);
    }

    public static class Row {
        public final Budget budget;
        public final FinanceCategory category;
        public final long spent;

        public Row(Budget budget, FinanceCategory category, long spent) {
            this.budget = budget;
            this.category = category;
            this.spent = spent;
        }
    }

    private final List<Row> items = new ArrayList<>();
    private final Listener listener;
    private String baseCurrency = "IDR";

    public BudgetAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency == null ? "IDR" : baseCurrency;
        notifyDataSetChanged();
    }

    public void submit(List<Row> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBudgetBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemBudgetBinding binding;

        Holder(ItemBudgetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            Context context = binding.getRoot().getContext();
            FinanceCategory category = row.category;
            String name = category == null
                    ? NameKeys.resolve(context, row.budget.getCategoryName(), null)
                    : NameKeys.resolve(context, category.getName(), category.getNameKey());
            if (name == null || name.trim().isEmpty()) {
                name = context.getString(R.string.category_none);
            }
            binding.title.setText(name);

            int tint = ColorUtil.parse(category == null ? null : category.getColorHex(),
                    ContextCompat.getColor(context, R.color.finance_primary));
            binding.categoryIcon.setImageResource(IconMap.forFinance(
                    category == null ? null : category.getIcon()));
            binding.categoryIcon.setImageTintList(ColorStateList.valueOf(tint));
            GradientDrawable iconBackground = new GradientDrawable();
            iconBackground.setCornerRadius(context.getResources().getDisplayMetrics().density * 15);
            iconBackground.setColor(ColorUtil.withAlpha(String.format("#%06X", tint & 0xFFFFFF),
                    0.14f));
            binding.iconBackground.setBackground(iconBackground);

            long limit = row.budget.getLimitAmount();
            int percent = limit <= 0 ? 0 : (int) Math.min(999, row.spent * 100 / limit);
            int progress = Math.min(100, percent);

            binding.percentText.setText(getString(context, R.string.budget_percent, percent));
            binding.subtitle.setText(context.getString(R.string.budget_progress,
                    MoneyText.format(context, baseCurrency, row.spent),
                    MoneyText.format(context, baseCurrency, limit)));

            int percentColor;
            int percentBackground;
            if (percent >= 100) {
                percentColor = ContextCompat.getColor(context, R.color.expense);
                percentBackground = ContextCompat.getColor(context, R.color.expense_soft);
                binding.footer.setText(context.getString(R.string.budget_over));
            } else if (percent >= 80) {
                percentColor = ContextCompat.getColor(context, R.color.warning);
                percentBackground = ContextCompat.getColor(context, R.color.warning_soft);
                binding.footer.setText(context.getString(R.string.budget_remaining) + ": "
                        + MoneyText.format(context, baseCurrency, Math.max(0, limit - row.spent)));
            } else {
                percentColor = tint;
                percentBackground = ColorUtil.withAlpha(String.format("#%06X", tint & 0xFFFFFF), 0.12f);
                binding.footer.setText(context.getString(R.string.budget_remaining) + ": "
                        + MoneyText.format(context, baseCurrency, Math.max(0, limit - row.spent)));
            }
            binding.percentText.setTextColor(percentColor);
            binding.percentText.setBackgroundTintList(ColorStateList.valueOf(percentBackground));

            binding.progress.setProgress(progress);
            binding.progress.setIndicatorColor(percent >= 100
                    ? ContextCompat.getColor(context, R.color.expense)
                    : percent >= 80 ? ContextCompat.getColor(context, R.color.warning) : tint);

            binding.getRoot().setOnClickListener(v -> listener.onOpen(row));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLongPress(row);
                return true;
            });
        }

        private String getString(Context context, int resource, int value) {
            return context.getString(resource, value);
        }
    }
}
