package com.example.pencatatan.todo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.IconMap;
import com.example.pencatatan.data.model.ActivityLog;
import com.example.pencatatan.databinding.ItemLogBinding;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.Holder> {

    private final List<ActivityLog> items = new ArrayList<>();

    public void submit(List<ActivityLog> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void append(List<ActivityLog> data) {
        if (data == null || data.isEmpty()) return;
        int start = items.size();
        items.addAll(data);
        notifyItemRangeInserted(start, data.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemLogBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        private final ItemLogBinding binding;

        Holder(ItemLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ActivityLog log) {
            Context context = binding.getRoot().getContext();
            String title = log.getTodoTitle();
            binding.title.setText(title == null || title.trim().isEmpty()
                    ? context.getString(R.string.activity_deleted_task) : title);
            binding.action.setText(actionLabel(context, log.getAction()));
            binding.time.setText(Dates.formatDateTime(context,
                    log.getTimestamp() == null ? null : log.getTimestamp().toDate()));

            int color = actionColor(context, log.getAction());
            binding.icon.setImageResource(IconMap.forAction(log.getAction()));
            binding.icon.setImageTintList(ColorStateList.valueOf(color));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(com.example.pencatatan.common.ColorUtil.withAlpha(String.format("#%06X",
                    color & 0xFFFFFF), 0.14f));
            ((android.view.View) binding.icon.getParent()).setBackground(circle);
        }

        private int actionColor(Context context, String action) {
            if (ActivityLog.COMPLETED.equals(action)) return ContextCompat.getColor(context, R.color.income);
            if (ActivityLog.DELETED.equals(action)) return ContextCompat.getColor(context, R.color.expense);
            if (ActivityLog.UPDATED.equals(action)) return ContextCompat.getColor(context, R.color.warning);
            return ContextCompat.getColor(context, R.color.color_primary);
        }

        private String actionLabel(Context context, String action) {
            if (ActivityLog.CREATED.equals(action)) return context.getString(R.string.activity_created);
            if (ActivityLog.UPDATED.equals(action)) return context.getString(R.string.activity_updated);
            if (ActivityLog.COMPLETED.equals(action)) return context.getString(R.string.activity_completed);
            if (ActivityLog.UNCOMPLETED.equals(action)) return context.getString(R.string.activity_uncompleted);
            if (ActivityLog.DELETED.equals(action)) return context.getString(R.string.activity_deleted);
            return context.getString(R.string.details);
        }
    }
}
