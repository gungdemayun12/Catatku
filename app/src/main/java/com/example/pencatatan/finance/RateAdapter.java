package com.example.pencatatan.finance;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.databinding.ItemRateBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RateAdapter extends RecyclerView.Adapter<RateAdapter.Holder> {

    public interface Listener {
        void onEdit(String currency);
    }

    private static class Row {
        final String currency;
        final ExchangeRate rate;

        Row(String currency, ExchangeRate rate) {
            this.currency = currency;
            this.rate = rate;
        }
    }

    private final List<Row> items = new ArrayList<>();
    private final Listener listener;
    private String baseCurrency = "IDR";

    public RateAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(Map<String, ExchangeRate> rates, String baseCurrency) {
        this.baseCurrency = baseCurrency == null ? "IDR" : baseCurrency;
        items.clear();
        for (String code : Money.SUPPORTED) {
            if (code.equals(this.baseCurrency)) continue;
            items.add(new Row(code, rates == null ? null : rates.get(code)));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemRateBinding.inflate(LayoutInflater.from(parent.getContext()),
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
        private final ItemRateBinding binding;

        Holder(ItemRateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Row row) {
            Context context = binding.getRoot().getContext();
            binding.flag.setText(Money.countryFlag(row.currency));
            binding.currencyCode.setText(row.currency);

            boolean hasRate = row.rate != null && row.rate.getRateToBase() > 0;
            if (hasRate) {
                binding.rateValue.setText(MoneyText.formatRate(row.rate.getRateToBase()));
                binding.rateValue.setTextColor(ContextCompat.getColor(context,
                        R.color.color_on_surface));
                binding.rateStatus.setText(baseCurrency);
            } else {
                binding.rateValue.setText("\u2014");
                binding.rateValue.setTextColor(ContextCompat.getColor(context, R.color.warning));
                binding.rateStatus.setText(R.string.rate_missing);
            }

            if (row.rate == null || row.rate.getUpdatedAt() == null) {
                binding.updated.setText(R.string.rate_updated_never);
            } else {
                binding.updated.setText(context.getString(R.string.rate_updated,
                        Dates.formatDateTime(context, row.rate.getUpdatedAt().toDate())));
            }

            binding.getRoot().setOnClickListener(v -> listener.onEdit(row.currency));
            binding.editButton.setOnClickListener(v -> listener.onEdit(row.currency));
        }
    }
}
