package com.example.pencatatan.todo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.databinding.ItemCategoryBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TodoCategoryAdapter extends RecyclerView.Adapter<TodoCategoryAdapter.Holder> {

    public interface Listener {
        void onEdit(TodoCategory category);

        void onDelete(TodoCategory category);
    }

    private final List<TodoCategory> items = new ArrayList<>();
    private Map<String, Integer> counts = new HashMap<>();
    private final Listener listener;

    public TodoCategoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TodoCategory> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void setCounts(Map<String, Integer> counts) {
        this.counts = counts == null ? new HashMap<>() : counts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.getContext()),
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
        private final ItemCategoryBinding binding;

        Holder(ItemCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TodoCategory category) {
            Context context = binding.getRoot().getContext();
            binding.name.setText(NameKeys.resolve(context, category.getName(), category.getNameKey()));
            int count = counts.getOrDefault(category.getDocId(), 0);
            binding.count.setText(context.getResources().getQuantityString(
                    R.plurals.plural_tasks, count, count));

            int color = ColorUtil.parse(category.getColorHex(),
                    ContextCompat.getColor(context, R.color.todo_primary));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(ColorUtil.withAlpha(String.format("#%06X", color & 0xFFFFFF), 0.16f));
            ((View) binding.icon.getParent()).setBackground(circle);
            binding.icon.setImageTintList(ColorStateList.valueOf(color));

            binding.editButton.setOnClickListener(v -> listener.onEdit(category));
            binding.deleteButton.setOnClickListener(v -> listener.onDelete(category));
            binding.getRoot().setOnClickListener(v -> listener.onEdit(category));
        }
    }
}
