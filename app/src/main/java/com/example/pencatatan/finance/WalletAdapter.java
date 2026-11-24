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
import com.example.pencatatan.data.model.Wallet;
import com.example.pencatatan.databinding.ItemWalletBinding;

import java.util.ArrayList;
import java.util.List;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.Holder> {

    public interface Listener {
        void onEdit(Row row);

        void onDelete(Row row);
    }

    public static class Row {
        public final Wallet wallet;
        public final long balance;
        public final boolean hasTransactions;

        public Row(Wallet wallet, long balance, boolean hasTransactions) {
            this.wallet = wallet;
            this.balance = balance;
            this.hasTransactions = hasTransactions;
        }
    }

    private final List<Row> items = new ArrayList<>();
    private final Listener listener;

    public WalletAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Row> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemWalletBinding.inflate(LayoutInflater.from(parent.getContext()),
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
        private final ItemWalletBinding binding;

        Holder(ItemWalletBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            Context context = binding.getRoot().getContext();
            Wallet wallet = row.wallet;
            binding.name.setText(NameKeys.resolve(context, wallet.getName(), wallet.getNameKey()));

            String type = walletType(context, wallet.getType());
            String currency = wallet.getCurrency() == null ? "IDR" : wallet.getCurrency();
            binding.subtitle.setText(type + " \u00B7 " + currency);
            binding.balance.setText(MoneyText.format(context, currency, row.balance));
            binding.balance.setTextColor(ContextCompat.getColor(context,
                    row.balance < 0 ? R.color.expense : R.color.color_on_surface));

            int tint = ColorUtil.parse(wallet.getColorHex(),
                    ContextCompat.getColor(context, R.color.finance_primary));
            binding.icon.setImageResource(IconMap.forWallet(wallet.getType()));
            binding.icon.setImageTintList(ColorStateList.valueOf(tint));
            GradientDrawable iconBackground = new GradientDrawable();
            iconBackground.setCornerRadius(context.getResources().getDisplayMetrics().density * 15);
            iconBackground.setColor(ColorUtil.withAlpha(
                    String.format("#%06X", tint & 0xFFFFFF), 0.14f));
            binding.iconBackground.setBackground(iconBackground);

            binding.getRoot().setOnClickListener(v -> listener.onEdit(row));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onDelete(row);
                return true;
            });
            binding.editButton.setOnClickListener(v -> listener.onEdit(row));
            binding.deleteButton.setOnClickListener(v -> listener.onDelete(row));
        }

        private String walletType(Context context, String type) {
            if ("bank".equals(type)) return context.getString(R.string.wallet_bank);
            if ("ewallet".equals(type)) return context.getString(R.string.wallet_ewallet);
            return context.getString(R.string.wallet_cash);
        }
    }
}
