package com.example.pencatatan.finance;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.IconMap;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.databinding.ItemTransactionBinding;
import com.example.pencatatan.databinding.ItemTransactionDayBinding;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    public interface Listener {
        void onOpen(Transaction transaction);

        void onLongPress(Transaction transaction);
    }

    private static final class Row {
        final boolean header;
        final Transaction transaction;
        final Date day;
        final long net;
        final int count;

        private Row(boolean header, Transaction transaction, Date day, long net, int count) {
            this.header = header;
            this.transaction = transaction;
            this.day = day;
            this.net = net;
            this.count = count;
        }

        static Row header(Date day, long net, int count) {
            return new Row(true, null, day, net, count);
        }

        static Row item(Transaction transaction) {
            return new Row(false, transaction, null, 0L, 0);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, FinanceCategory> categories = new HashMap<>();
    private final Listener listener;
    private String baseCurrency = "IDR";

    public TransactionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Transaction> data) {
        rows.clear();
        if (data != null && !data.isEmpty()) {
            Map<String, Long> netByDay = new HashMap<>();
            Map<String, Integer> countByDay = new HashMap<>();
            for (Transaction transaction : data) {
                String key = dayKey(transaction);
                // The day's net is real money in minus real money out. Transfer legs
                // cancel out inside the same day, so excluding them keeps the header
                // honest instead of showing a phantom loss.
                long signed = transaction.isTransfer() ? 0L
                        : transaction.isIncome()
                        ? transaction.getAmountBase() : -transaction.getAmountBase();
                Long previous = netByDay.get(key);
                netByDay.put(key, previous == null ? signed : previous + signed);
                Integer count = countByDay.get(key);
                countByDay.put(key, count == null ? 1 : count + 1);
            }
            String current = null;
            for (Transaction transaction : data) {
                String key = dayKey(transaction);
                if (current == null || !current.equals(key)) {
                    current = key;
                    Long net = netByDay.get(key);
                    Integer count = countByDay.get(key);
                    rows.add(Row.header(transaction.getDate() == null ? null
                                    : transaction.getDate().toDate(),
                            net == null ? 0L : net, count == null ? 0 : count));
                }
                rows.add(Row.item(transaction));
            }
        }
        notifyDataSetChanged();
    }

    /** Returns the transaction at the given adapter position, or null for day headers. */
    @Nullable
    public Transaction transactionAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Row row = rows.get(position);
        return row.header ? null : row.transaction;
    }

    public void setCategories(List<FinanceCategory> data) {
        categories.clear();
        if (data != null) {
            for (FinanceCategory category : data) {
                if (category.getDocId() != null) categories.put(category.getDocId(), category);
            }
        }
        notifyDataSetChanged();
    }

    public void setBaseCurrency(String currency) {
        String next = currency == null || currency.isEmpty() ? "IDR" : currency;
        if (next.equals(baseCurrency)) return;
        baseCurrency = next;
        notifyDataSetChanged();
    }

    private static String dayKey(Transaction transaction) {
        return transaction.getDate() == null ? "" : Dates.dayKey(transaction.getDate().toDate());
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemTransactionDayBinding.inflate(inflater, parent, false));
        }
        return new TransactionHolder(ItemTransactionBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row);
        } else if (holder instanceof TransactionHolder) {
            ((TransactionHolder) holder).bind(row.transaction);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionDayBinding binding;

        HeaderHolder(ItemTransactionDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            Context context = binding.getRoot().getContext();
            binding.dayLabel.setText(Dates.formatDayHeader(context, row.day));
            binding.dayCount.setText(context.getResources().getQuantityString(
                    R.plurals.plural_transactions, row.count, row.count));
            binding.dayNet.setText(context.getString(R.string.finance_day_net,
                    MoneyText.formatSigned(context, baseCurrency, row.net, row.net >= 0)));
            binding.dayNet.setTextColor(ContextCompat.getColor(context,
                    row.net >= 0 ? R.color.income : R.color.expense));
        }
    }

    class TransactionHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;

        TransactionHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Transaction transaction) {
            Context context = binding.getRoot().getContext();
            boolean transfer = transaction.isTransfer();
            FinanceCategory category = transaction.getCategoryId() == null ? null
                    : categories.get(transaction.getCategoryId());
            String categoryName = category == null
                    ? NameKeys.resolve(context, transaction.getCategoryName(), null)
                    : NameKeys.resolve(context, category.getName(), category.getNameKey());
            if (categoryName == null || categoryName.trim().isEmpty()) {
                categoryName = context.getString(R.string.category_none);
            }
            // A transfer has no category by design. Saying "No category" would read
            // like a recording mistake, so it is named for what it actually is.
            binding.title.setText(transfer ? context.getString(R.string.wallet_transfer)
                    : categoryName);
            binding.repeatBadge.setVisibility(transaction.isRepeating()
                    ? View.VISIBLE : View.GONE);

            String note = transaction.getNote() == null ? "" : transaction.getNote().trim();
            String wallet = transaction.getWalletName() == null ? "" : transaction.getWalletName().trim();
            binding.subtitle.setText(!note.isEmpty() ? note : context.getString(
                    transfer ? (transaction.isIncome() ? R.string.wallet_transfer_in
                            : R.string.wallet_transfer_out)
                            : (transaction.isIncome() ? R.string.finance_income
                                    : R.string.finance_expense)));

            if (wallet.isEmpty()) {
                binding.dateText.setVisibility(View.GONE);
            } else {
                binding.dateText.setVisibility(View.VISIBLE);
                binding.dateText.setText(wallet);
            }

            int tint = ColorUtil.parse(category == null ? null : category.getColorHex(),
                    ContextCompat.getColor(context, R.color.finance_primary));
            binding.categoryIcon.setImageResource(transfer
                    ? R.drawable.ic_arrow_forward
                    : IconMap.forFinance(category == null ? null : category.getIcon()));
            binding.categoryIcon.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
            GradientDrawable iconBackground = new GradientDrawable();
            iconBackground.setCornerRadius(context.getResources().getDisplayMetrics().density * 15);
            iconBackground.setColor(ColorUtil.withAlpha(String.format("#%06X", tint & 0xFFFFFF),
                    0.14f));
            binding.iconBackground.setBackground(iconBackground);

            binding.amountText.setText(MoneyText.formatSigned(context,
                    transaction.getCurrency(), transaction.getAmount(), transaction.isIncome()));
            binding.amountText.setTextColor(ContextCompat.getColor(context,
                    transaction.isIncome() ? R.color.income : R.color.expense));

            binding.getRoot().setOnClickListener(v -> listener.onOpen(transaction));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLongPress(transaction);
                return true;
            });
        }
    }
}
