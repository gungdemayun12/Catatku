package com.example.pencatatan.finance;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.data.model.SavingsGoal;
import com.example.pencatatan.databinding.ItemSavingsGoalBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the savings-goal cards.
 *
 * <p>The card is deliberately optimistic in tone: the amount saved is the
 * largest number, and the remaining amount is phrased as "kurang Rp X" (still
 * short by X) rather than as a debt. A reached goal swaps the footer for a
 * quiet "target tercapai" so it stops asking for money.</p>
 */
public class SavingsGoalAdapter extends RecyclerView.Adapter<SavingsGoalAdapter.Holder> {

    public interface Listener {
        void onOpen(SavingsGoal goal);

        void onTopUp(SavingsGoal goal);
    }

    private final Listener listener;
    private final List<SavingsGoal> items = new ArrayList<>();
    private String currency = "IDR";

    public SavingsGoalAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setCurrency(String currency) {
        if (currency != null && !currency.isEmpty()) this.currency = currency;
        notifyDataSetChanged();
    }

    public void submit(List<SavingsGoal> goals) {
        items.clear();
        if (goals != null) items.addAll(goals);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSavingsGoalBinding binding = ItemSavingsGoalBinding.inflate(
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
        private final ItemSavingsGoalBinding binding;

        Holder(ItemSavingsGoalBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SavingsGoal goal) {
            Context context = binding.getRoot().getContext();
            binding.title.setText(goal.getTitle());
            binding.goalIcon.setImageResource(goalIcon(goal.getGoalType()));

            int percent = goal.progressPercent();
            boolean reached = goal.isReached();
            binding.percent.setText(context.getString(R.string.goal_percent, percent));
            binding.percent.setTextColor(ContextCompat.getColor(context,
                    reached ? R.color.income : R.color.color_on_surface_variant));

            binding.amounts.setText(context.getString(R.string.goal_amounts,
                    MoneyText.format(context, currency, goal.getSavedAmount()),
                    MoneyText.format(context, currency, goal.getTargetAmount())));

            binding.progress.setProgressCompat(percent, true);
            binding.progress.setIndicatorColor(ContextCompat.getColor(context,
                    reached ? R.color.income : R.color.color_primary));

            if (reached) {
                binding.footerIcon.setImageResource(R.drawable.ic_check);
                binding.footerIcon.setImageTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(context, R.color.income)));
                binding.footerText.setText(R.string.goal_reached);
                binding.topUpButton.setVisibility(View.INVISIBLE);
            } else {
                binding.footerIcon.setImageResource(R.drawable.ic_target);
                binding.footerIcon.setImageTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(context,
                                        R.color.color_on_surface_variant)));
                binding.footerText.setText(context.getString(R.string.goal_remaining,
                        MoneyText.format(context, currency, goal.remainingAmount())));
                binding.topUpButton.setVisibility(View.VISIBLE);
            }

            binding.getRoot().setOnClickListener(v -> listener.onOpen(goal));
            binding.topUpButton.setOnClickListener(v -> listener.onTopUp(goal));
        }
    }

    /**
     * Each goal type gets its own glyph so a stack of goals can be scanned by
     * shape instead of by reading every title.
     */
    static int goalIcon(String goalType) {
        if (goalType == null) return R.drawable.ic_savings;
        switch (goalType) {
            case SavingsGoal.TYPE_TRAVEL:
                return R.drawable.ic_place;
            case SavingsGoal.TYPE_GADGET:
                return R.drawable.ic_phone;
            case SavingsGoal.TYPE_EMERGENCY:
                return R.drawable.ic_shield;
            case SavingsGoal.TYPE_HOME:
                return R.drawable.ic_home;
            case SavingsGoal.TYPE_EDUCATION:
                return R.drawable.ic_school;
            default:
                return R.drawable.ic_savings;
        }
    }
}
