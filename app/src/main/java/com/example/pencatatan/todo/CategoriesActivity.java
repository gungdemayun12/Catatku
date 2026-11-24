package com.example.pencatatan.todo;

import com.example.pencatatan.common.DialogScroll;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.databinding.ActivityCategoriesBinding;
import com.example.pencatatan.databinding.DialogCategoryBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoriesActivity extends BaseActivity {

    private ActivityCategoriesBinding binding;
    private TodoCategoryAdapter adapter;
    private final TodoRepository repository = new TodoRepository();
    private ListenerRegistration categoryListener;
    private ListenerRegistration todoListener;
    private final Map<String, Integer> counts = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        binding = ActivityCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());
        binding.addButton.setOnClickListener(v -> openEditor(null));

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_tag);
        binding.emptyState.emptyTitle.setText(R.string.category_empty);
        binding.emptyState.emptyDesc.setText(R.string.category_empty_desc);
        binding.emptyState.emptyAction.setText(R.string.category_add);
        binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
        binding.emptyState.emptyAction.setOnClickListener(v -> openEditor(null));

        adapter = new TodoCategoryAdapter(new TodoCategoryAdapter.Listener() {
            @Override
            public void onEdit(TodoCategory category) {
                openEditor(category);
            }

            @Override
            public void onDelete(TodoCategory category) {
                confirmDelete(category);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        categoryListener = repository.listenCategories(uid(), new TodoRepository.OnCategories() {
            @Override
            public void onData(List<TodoCategory> categories) {
                adapter.submit(categories);
                binding.emptyState.getRoot().setVisibility(
                        categories == null || categories.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
            }
        });

        todoListener = repository.listenTodos(uid(), new TodoRepository.OnTodos() {
            @Override
            public void onData(List<Todo> todos) {
                counts.clear();
                for (Todo todo : todos) {
                    if (todo.getCategoryId() == null) continue;
                    counts.put(todo.getCategoryId(),
                            counts.getOrDefault(todo.getCategoryId(), 0) + 1);
                }
                adapter.setCounts(counts);
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void openEditor(@Nullable TodoCategory existing) {
        DialogCategoryBinding dialogBinding = DialogCategoryBinding.inflate(getLayoutInflater());
        boolean isNew = existing == null;
        if (!isNew) {
            dialogBinding.nameEdit.setText(
                    com.example.pencatatan.common.NameKeys.resolve(this,
                            existing.getName(), existing.getNameKey()));
        }
        final String[] selectedColor = {
                existing == null || existing.getColorHex() == null
                        ? ColorUtil.PALETTE[0] : existing.getColorHex()
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(isNew ? R.string.category_add : R.string.category_edit)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);
        Dialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            buildColorChoices(dialogBinding, selectedColor);
            dialogBinding.nameLayout.setError(null);
            ((androidx.appcompat.app.AlertDialog) dialog).getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String name = dialogBinding.nameEdit.getText() == null ? ""
                                : dialogBinding.nameEdit.getText().toString().trim();
                        if (name.isEmpty()) {
                            dialogBinding.nameLayout.setError(getString(R.string.error_name_required));
                            return;
                        }
                        TodoCategory category = isNew ? new TodoCategory() : existing;
                        category.setName(name);
                        category.setColorHex(selectedColor[0]);
                        category.setNameKey(null);
                        repository.saveCategory(uid(), category, isNew,
                                new TodoRepository.SaveCallback() {
                                    @Override
                                    public void onSuccess(String id) {
                                        toast(getString(R.string.category_saved));
                                        dialog.dismiss();
                                    }

                                    @Override
                                    public void onError(Exception error) {
                                        toast(getString(R.string.save_failed));
                                    }
                                });
                    });
        });
        dialog.show();
    }

    private void buildColorChoices(DialogCategoryBinding dialogBinding, String[] selectedColor) {
        dialogBinding.colorRow.removeAllViews();
        for (String hex : ColorUtil.PALETTE) {
            View swatch = new View(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(34), dp(34));
            params.setMarginEnd(dp(8));
            swatch.setLayoutParams(params);
            swatch.setBackground(swatchDrawable(hex, hex.equalsIgnoreCase(selectedColor[0])));
            swatch.setOnClickListener(v -> {
                selectedColor[0] = hex;
                buildColorChoices(dialogBinding, selectedColor);
            });
            dialogBinding.colorRow.addView(swatch);
        }
    }

    private GradientDrawable swatchDrawable(String hex, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(ColorUtil.parse(hex, ContextCompat.getColor(this, R.color.todo_primary)));
        if (selected) {
            drawable.setStroke(dp(3), ContextCompat.getColor(this, R.color.color_on_surface_variant));
        }
        return drawable;
    }

    private void confirmDelete(TodoCategory category) {
        confirm(getString(R.string.category_delete_title), getString(R.string.category_delete_msg),
                getString(R.string.delete), () -> repository.deleteCategory(uid(), category,
                        new TodoRepository.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                toast(getString(R.string.category_deleted));
                            }

                            @Override
                            public void onError(Exception error) {
                                toast(getString(R.string.delete_failed));
                            }
                        }));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (categoryListener != null) categoryListener.remove();
        if (todoListener != null) todoListener.remove();
    }
}
