package com.example.pencatatan.finance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.data.model.Debt;
import com.example.pencatatan.databinding.ItemDebtBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders debt / receivable rows.
 *
 * <p>Direction is carried by three independent signals — the arrow glyph, the
 * colour, and the text ("you owe" vs "you are owed") — so the list stays
 * readable in grayscale and for colour-blind users. The outstanding amount is
 * the largest number on the row because that is the figure being tracked.</p>
 */
public class DebtAdapter extends RecyclerView.Adapter<DebtAdapter.Holder> {

    public interface Listener {
        void onOpen(Debt debt);

        void onRecordPayment(Debt debt);
    }

    private final Listener listener;
    private final List<Debt> items = new ArrayList<>();
    private String currency = "IDR";

    public DebtAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setCurrency(String currency) {
        if (currency != null && !currency.isEmpty()) this.currency = currency;
        notifyDataSetChanged();
    }

    public void submit(List<Debt> debts) {
        items.clear();
        if (debts != null) items.addAll(debts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDebtBinding binding = ItemDebtBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
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
        private final ItemDebtBinding binding;

        Holder(ItemDebtBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Debt debt) {
            Context context = binding.getRoot().getContext();
            boolean receivable = debt.isReceivable();
            boolean settled = debt.isSettled() || debt.remainingAmount() == 0;

            int accent = ContextCompat.getColor(context,
                    receivable ? R.color.income : R.color.expense);
            binding.personName.setText(debt.getPersonName());
            binding.directionIcon.setImageResource(R.drawable.ic_handshake);
            binding.directionIcon.setImageTintList(ColorStateList.valueOf(accent));

            binding.paidOf.setText(context.getString(R.string.debt_paid_of,
                    MoneyText.format(context, currency, debt.getPaidAmount()),
                    MoneyText.format(context, currency, debt.getAmount())));

            if (settled) {
                binding.remaining.setText(R.string.debt_settled);
                binding.remaining.setTextColor(ContextCompat.getColor(context,
                        R.color.color_on_surface_variant));
            } else {
                binding.remaining.setText(MoneyText.format(context, currency,
                        debt.remainingAmount()));
                binding.remaining.setTextColor(accent);
            }

            binding.progress.setProgressCompat(debt.progressPercent(), true);
            binding.progress.setIndicatorColor(accent);

            // Due line: overdue wins over "due on", because a late debt needs a
            // decision while an upcoming one only needs a reminder.
            if (settled) {
                binding.dueIcon.setImageResource(R.drawable.ic_check);
                setIconTint(binding.dueIcon, ContextCompat.getColor(context, R.color.income));
                binding.dueText.setText(R.string.debt_settled);
                binding.dueText.setTextColor(ContextCompat.getColor(context,
                        R.color.income));
            } else if (debt.getDueDate() == null) {
                binding.dueIcon.setImageResource(R.drawable.ic_calendar);
                setIconTint(binding.dueIcon, ContextCompat.getColor(context,
                        R.color.color_on_surface_variant));
                binding.dueText.setText(R.string.debt_no_due);
                binding.dueText.setTextColor(ContextCompat.getColor(context,
                        R.color.color_on_surface_variant));
            } else if (debt.isOverdue()) {
                binding.dueIcon.setImageResource(R.drawable.ic_warning);
                setIconTint(binding.dueIcon, ContextCompat.getColor(context, R.color.warning));
                binding.dueText.setText(R.string.debt_overdue);
                binding.dueText.setTextColor(ContextCompat.getColor(context, R.color.warning));
            } else {
                binding.dueIcon.setImageResource(R.drawable.ic_calendar);
                setIconTint(binding.dueIcon, ContextCompat.getColor(context,
                        R.color.color_on_surface_variant));
                binding.dueText.setText(context.getString(R.string.debt_due_on,
                        Dates.formatDate(context, debt.getDueDate().toDate())));
                binding.dueText.setTextColor(ContextCompat.getColor(context,
                        R.color.color_on_surface_variant));
            }

            binding.payButton.setVisibility(settled ? View.GONE : View.VISIBLE);
            binding.payButton.setOnClickListener(v -> listener.onRecordPayment(debt));
            binding.getRoot().setOnClickListener(v -> listener.onOpen(debt));
        }

        private void setIconTint(android.widget.ImageView view, int color) {
            view.setImageTintList(ColorStateList.valueOf(color));
        }
    }
}
