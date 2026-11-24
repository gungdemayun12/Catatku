package com.example.pencatatan.finance;

import com.example.pencatatan.common.DialogScroll;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.IconMap;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.repository.BaseRepository;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.databinding.ActivityFinanceCategoriesBinding;
import com.example.pencatatan.databinding.DialogFinanceCategoryBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinanceCategoriesActivity extends BaseActivity {

    private ActivityFinanceCategoriesBinding binding;
    private FinanceCategoryAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();

    private final List<FinanceCategory> categories = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();

    private ListenerRegistration categoryListener;
    private ListenerRegistration transactionListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        binding = ActivityFinanceCategoriesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());
        binding.addButton.setOnClickListener(v -> openEditor(null));

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_tag);
        binding.emptyState.emptyTitle.setText(R.string.finance_category_empty);
        binding.emptyState.emptyDesc.setText(R.string.finance_category_empty_desc);
        binding.emptyState.emptyAction.setText(R.string.finance_category_add);
        binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
        binding.emptyState.emptyAction.setOnClickListener(v -> openEditor(null));

        adapter = new FinanceCategoryAdapter(new FinanceCategoryAdapter.Listener() {
            @Override
            public void onEdit(FinanceCategory category) {
                openEditor(category);
            }

            @Override
            public void onDelete(FinanceCategory category) {
                confirmDelete(category);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        listenCategories();
        listenTransactions();
    }

    private void listenCategories() {
        categoryListener = repository.listenCategories(uid(),
                new FinanceRepository.OnFinanceCategories() {
                    @Override
                    public void onData(List<FinanceCategory> data) {
                        categories.clear();
                        if (data != null) categories.addAll(data);
                        apply();
                    }

                    @Override
                    public void onError(Exception error) {
                        toast(getString(R.string.error_load));
                    }
                });
    }

    private void listenTransactions() {
        transactionListener = repository.listenAllTransactions(uid(),
                new FinanceRepository.OnTransactions() {
                    @Override
                    public void onData(List<Transaction> data) {
                        transactions.clear();
                        if (data != null) transactions.addAll(data);
                        apply();
                    }

                    @Override
                    public void onError(Exception error) {
                    }
                });
    }

    private void apply() {
        if (binding == null) return;
        Map<String, Integer> usage = new HashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getCategoryId() == null) continue;
            usage.put(transaction.getCategoryId(),
                    usage.getOrDefault(transaction.getCategoryId(), 0) + 1);
        }
        adapter.submit(categories, usage);
        binding.emptyState.getRoot().setVisibility(
                categories.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openEditor(@Nullable FinanceCategory existing) {
        DialogFinanceCategoryBinding dialogBinding =
                DialogFinanceCategoryBinding.inflate(getLayoutInflater());
        boolean isNew = existing == null;
        final boolean locked = existing != null && usageCount(existing.getDocId()) > 0;
        final String[] type = {existing == null ? FinanceCategory.EXPENSE : existing.getType()};
        final String[] selectedIcon = {existing == null || existing.getIcon() == null
                ? "other" : existing.getIcon()};
        final String[] selectedColor = {existing == null || existing.getColorHex() == null
                ? ColorUtil.PALETTE[0] : existing.getColorHex()};

        if (!isNew) {
            dialogBinding.nameEdit.setText(
                    NameKeys.resolve(this, existing.getName(), existing.getNameKey()));
        }
        checkType(dialogBinding, type[0]);

        if (locked) {
            dialogBinding.typeExpense.setEnabled(false);
            dialogBinding.typeIncome.setEnabled(false);
            dialogBinding.typeHint.setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isNew ? R.string.finance_category_add : R.string.finance_category_edit)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            buildIconChoices(dialogBinding, selectedIcon, selectedColor);
            buildColorChoices(dialogBinding, selectedIcon, selectedColor);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialogBinding.nameLayout.setError(null);
                String name = text(dialogBinding.nameEdit.getText());
                if (name.isEmpty()) {
                    dialogBinding.nameLayout.setError(getString(R.string.error_name_required));
                    return;
                }
                type[0] = typeFromGroup(dialogBinding);

                FinanceCategory category = isNew ? new FinanceCategory() : existing;
                category.setName(name);
                category.setNameKey(null);
                category.setType(type[0]);
                category.setIcon(selectedIcon[0]);
                category.setColorHex(selectedColor[0]);

                repository.saveCategory(uid(), category, isNew,
                        new FinanceRepository.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                toast(getString(R.string.finance_category_saved));
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

    private void buildIconChoices(DialogFinanceCategoryBinding dialogBinding,
                                  String[] selectedIcon, String[] selectedColor) {
        dialogBinding.iconRow.removeAllViews();
        int selectedTint = ColorUtil.parse(selectedColor[0],
                ContextCompat.getColor(this, R.color.finance_primary));
        for (String key : IconMap.FINANCE_ICONS) {
            boolean selected = key.equals(selectedIcon[0]);
            ImageView view = new ImageView(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(42), dp(42));
            params.setMarginEnd(dp(8));
            view.setLayoutParams(params);
            view.setPadding(dp(9), dp(9), dp(9), dp(9));
            view.setImageResource(IconMap.forFinance(key));
            view.setContentDescription(key);
            view.setImageTintList(ColorStateList.valueOf(selected
                    ? ContextCompat.getColor(this, R.color.white)
                    : ContextCompat.getColor(this, R.color.color_on_surface_variant)));
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(selected ? selectedTint
                    : ContextCompat.getColor(this, R.color.color_surface_variant));
            view.setBackground(background);
            view.setOnClickListener(v -> {
                selectedIcon[0] = key;
                buildIconChoices(dialogBinding, selectedIcon, selectedColor);
            });
            dialogBinding.iconRow.addView(view);
        }
    }

    private void buildColorChoices(DialogFinanceCategoryBinding dialogBinding,
                                   String[] selectedIcon, String[] selectedColor) {
        dialogBinding.colorRow.removeAllViews();
        for (String hex : ColorUtil.PALETTE) {
            View swatch = new View(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(34), dp(34));
            params.setMarginEnd(dp(8));
            swatch.setLayoutParams(params);
            swatch.setBackground(swatchDrawable(hex, hex.equalsIgnoreCase(selectedColor[0])));
            swatch.setOnClickListener(v -> {
                selectedColor[0] = hex;
                buildColorChoices(dialogBinding, selectedIcon, selectedColor);
                buildIconChoices(dialogBinding, selectedIcon, selectedColor);
            });
            dialogBinding.colorRow.addView(swatch);
        }
    }

    private GradientDrawable swatchDrawable(String hex, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(ColorUtil.parse(hex, ContextCompat.getColor(this, R.color.finance_primary)));
        if (selected) {
            drawable.setStroke(dp(3), ContextCompat.getColor(this, R.color.color_on_surface_variant));
        }
        return drawable;
    }

    private void confirmDelete(FinanceCategory category) {
        confirm(getString(R.string.finance_category_delete_title),
                getString(R.string.finance_category_delete_msg), getString(R.string.delete),
                () -> repository.deleteCategory(uid(), category,
                        new FinanceRepository.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                toast(getString(R.string.finance_category_deleted));
                            }

                            @Override
                            public void onError(Exception error) {
                                if (isCategoryInUse(error)) {
                                    new MaterialAlertDialogBuilder(FinanceCategoriesActivity.this)
                                            .setTitle(R.string.finance_category_delete_title)
                                            .setMessage(R.string.finance_category_in_use)
                                            .setPositiveButton(R.string.ok, null)
                                            .show();
                                } else {
                                    toast(getString(R.string.delete_failed));
                                }
                            }
                        }));
    }

    private int usageCount(String categoryId) {
        if (categoryId == null) return 0;
        int count = 0;
        for (Transaction transaction : transactions) {
            if (categoryId.equals(transaction.getCategoryId())) count++;
        }
        return count;
    }

    private boolean isCategoryInUse(Exception error) {
        return error instanceof BaseRepository.RepoException
                && BaseRepository.RepoException.CATEGORY_IN_USE.equals(
                ((BaseRepository.RepoException) error).getCode());
    }

    private void checkType(DialogFinanceCategoryBinding dialogBinding, String type) {
        dialogBinding.typeGroup.check(FinanceCategory.INCOME.equals(type)
                ? R.id.typeIncome : R.id.typeExpense);
    }

    private String typeFromGroup(DialogFinanceCategoryBinding dialogBinding) {
        return dialogBinding.typeGroup.getCheckedButtonId() == R.id.typeIncome
                ? FinanceCategory.INCOME : FinanceCategory.EXPENSE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (categoryListener != null) categoryListener.remove();
        if (transactionListener != null) transactionListener.remove();
    }
}
