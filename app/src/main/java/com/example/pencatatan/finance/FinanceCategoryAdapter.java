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
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.databinding.ItemFinanceCategoryBinding;
import com.example.pencatatan.databinding.ItemSectionHeaderBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FinanceCategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onEdit(FinanceCategory category);

        void onDelete(FinanceCategory category);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CATEGORY = 1;

    private static class Row {
        final boolean header;
        final int headerTitle;
        final int headerCount;
        final FinanceCategory category;
        final int usage;

        private Row(int headerTitle, int headerCount) {
            this.header = true;
            this.headerTitle = headerTitle;
            this.headerCount = headerCount;
            this.category = null;
            this.usage = 0;
        }

        private Row(FinanceCategory category, int usage) {
            this.header = false;
            this.headerTitle = 0;
            this.headerCount = 0;
            this.category = category;
            this.usage = usage;
        }
    }

    private final List<Row> items = new ArrayList<>();
    private final Listener listener;

    public FinanceCategoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<FinanceCategory> categories, Map<String, Integer> usageCounts) {
        items.clear();
        if (categories != null) {
            List<FinanceCategory> expense = new ArrayList<>();
            List<FinanceCategory> income = new ArrayList<>();
            for (FinanceCategory category : categories) {
                if (category == null) continue;
                if (category.isIncome()) income.add(category);
                else expense.add(category);
            }
            if (!expense.isEmpty()) {
                items.add(new Row(R.string.finance_category_expense_section, expense.size()));
                for (FinanceCategory category : expense) {
                    items.add(new Row(category, count(usageCounts, category)));
                }
            }
            if (!income.isEmpty()) {
                items.add(new Row(R.string.finance_category_income_section, income.size()));
                for (FinanceCategory category : income) {
                    items.add(new Row(category, count(usageCounts, category)));
                }
            }
        }
        notifyDataSetChanged();
    }

    private int count(Map<String, Integer> counts, FinanceCategory category) {
        if (counts == null || category.getDocId() == null) return 0;
        Integer value = counts.get(category.getDocId());
        return value == null ? 0 : value;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).header ? TYPE_HEADER : TYPE_CATEGORY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false));
        }
        return new CategoryHolder(ItemFinanceCategoryBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = items.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row);
        } else {
            ((CategoryHolder) holder).bind(row);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemSectionHeaderBinding binding;

        HeaderHolder(ItemSectionHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            binding.headerTitle.setText(row.headerTitle);
            binding.headerCount.setText(String.valueOf(row.headerCount));
        }
    }

    class CategoryHolder extends RecyclerView.ViewHolder {
        private final ItemFinanceCategoryBinding binding;

        CategoryHolder(ItemFinanceCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            Context context = binding.getRoot().getContext();
            FinanceCategory category = row.category;
            binding.name.setText(NameKeys.resolve(context, category.getName(),
                    category.getNameKey()));

            binding.subtitle.setText(row.usage == 0
                    ? context.getString(R.string.finance_category_no_transactions)
                    : context.getString(R.string.finance_category_usage, row.usage));

            int tint = ColorUtil.parse(category.getColorHex(),
                    ContextCompat.getColor(context, R.color.finance_primary));
            binding.icon.setImageResource(IconMap.forFinance(category.getIcon()));
            binding.icon.setImageTintList(ColorStateList.valueOf(tint));
            GradientDrawable iconBackground = new GradientDrawable();
            iconBackground.setCornerRadius(context.getResources().getDisplayMetrics().density * 15);
            iconBackground.setColor(ColorUtil.withAlpha(
                    String.format("#%06X", tint & 0xFFFFFF), 0.14f));
            binding.iconBackground.setBackground(iconBackground);

            boolean income = category.isIncome();
            int color = ContextCompat.getColor(context,
                    income ? R.color.income : R.color.expense);
            int soft = ContextCompat.getColor(context,
                    income ? R.color.income_soft : R.color.expense_soft);
            binding.typeBadge.setText(income ? R.string.finance_income : R.string.finance_expense);
            binding.typeBadge.setTextColor(color);
            binding.typeBadge.setBackgroundTintList(ColorStateList.valueOf(soft));

            binding.getRoot().setOnClickListener(v -> listener.onEdit(category));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onDelete(category);
                return true;
            });
            binding.editButton.setOnClickListener(v -> listener.onEdit(category));
            binding.deleteButton.setOnClickListener(v -> listener.onDelete(category));
        }
    }
}
