package com.example.pencatatan.finance;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.data.model.Bill;
import com.example.pencatatan.databinding.ItemBillBinding;
import com.example.pencatatan.notification.ReminderScheduler;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BillAdapter extends RecyclerView.Adapter<BillAdapter.Holder> {

    public interface Listener {
        void onEdit(Bill bill);

        void onDelete(Bill bill);

        void onToggleActive(Bill bill, boolean active);
    }

    private final List<Bill> items = new ArrayList<>();
    private final Listener listener;

    public BillAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Bill> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBillBinding.inflate(LayoutInflater.from(parent.getContext()),
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
        private final ItemBillBinding binding;

        Holder(ItemBillBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Bill bill) {
            Context context = binding.getRoot().getContext();
            String currency = bill.getCurrency() == null ? "IDR" : bill.getCurrency();
            binding.title.setText(bill.getTitle());
            binding.amountText.setText(MoneyText.format(context, currency, bill.getAmount()));

            String repeat = context.getString("yearly".equals(bill.getRepeatType())
                    ? R.string.bill_yearly : R.string.bill_monthly);
            binding.dueText.setText(context.getString(R.string.bill_due_on, (int) bill.getDueDay())
                    + " \u00B7 " + repeat);

            if (bill.isReminderEnabled()) {
                binding.reminderText.setText(context.getString(R.string.bill_reminder_summary,
                        (int) bill.getReminderDaysBefore(), MoneyText.format(context, currency,
                                bill.getAmount())));
            } else {
                binding.reminderText.setText(R.string.bill_notification_none);
            }

            if (!bill.isActive()) {
                binding.nextDueText.setText(R.string.bill_inactive);
            } else if (bill.isReminderEnabled()) {
                Date next = ReminderScheduler.nextBillDisplay(bill);
                binding.nextDueText.setText(context.getString(R.string.bill_next_due,
                        Dates.formatDateTime(context, next)));
            } else {
                binding.nextDueText.setText(R.string.bill_notification_none);
            }

            binding.activeSwitch.setOnCheckedChangeListener(null);
            binding.activeSwitch.setChecked(bill.isActive());
            binding.activeSwitch.setOnCheckedChangeListener((button, checked) ->
                    listener.onToggleActive(bill, checked));

            binding.getRoot().setOnClickListener(v -> listener.onEdit(bill));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onDelete(bill);
                return true;
            });
            binding.editButton.setOnClickListener(v -> listener.onEdit(bill));
            binding.deleteButton.setOnClickListener(v -> listener.onDelete(bill));
        }
    }
}
