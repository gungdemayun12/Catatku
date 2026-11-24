package com.example.pencatatan.finance;

import com.example.pencatatan.common.DialogScroll;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.AmountInput;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.data.model.Budget;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.databinding.DialogBudgetBinding;
import com.example.pencatatan.databinding.FragmentFinanceBudgetsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinanceBudgetsFragment extends Fragment {

    private FragmentFinanceBudgetsBinding binding;
    private FinanceViewModel viewModel;
    private BudgetAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();

    private final List<Budget> budgets = new ArrayList<>();
    private final List<FinanceCategory> categories = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, FinanceCategory> categoryMap = new HashMap<>();
    private String baseCurrency = "IDR";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFinanceBudgetsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);

        adapter = new BudgetAdapter(new BudgetAdapter.Listener() {
            @Override
            public void onOpen(BudgetAdapter.Row row) {
                openEditor(row.budget);
            }

            @Override
            public void onLongPress(BudgetAdapter.Row row) {
                confirmDelete(row.budget);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_money_circle);
        binding.emptyState.emptyTitle.setText(R.string.budget_empty);
        binding.emptyState.emptyDesc.setText(R.string.budget_empty_desc);
        binding.emptyState.emptyAction.setText(R.string.finance_budget_empty_action);
        binding.emptyState.emptyAction.setOnClickListener(v -> openEditor(null));

        binding.monthPrev.setOnClickListener(v -> viewModel.moveMonth(-1));
        binding.monthNext.setOnClickListener(v -> viewModel.moveMonth(1));
        binding.addBudgetButton.setOnClickListener(v -> openEditor(null));

        viewModel.user().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                baseCurrency = user.getCurrency() == null ? "IDR" : user.getCurrency();
            }
            apply();
        });
        viewModel.budgets().observe(getViewLifecycleOwner(), data -> {
            budgets.clear();
            if (data != null) budgets.addAll(data);
            apply();
        });
        viewModel.categories().observe(getViewLifecycleOwner(), data -> {
            categories.clear();
            categoryMap.clear();
            if (data != null) {
                for (FinanceCategory category : data) {
                    if (category.getDocId() == null) continue;
                    categories.add(category);
                    categoryMap.put(category.getDocId(), category);
                }
            }
            apply();
        });
        viewModel.transactions().observe(getViewLifecycleOwner(), data -> {
            transactions.clear();
            if (data != null) transactions.addAll(data);
            apply();
        });
        viewModel.monthKey().observe(getViewLifecycleOwner(), key -> apply());
    }

    private void apply() {
        if (binding == null) return;
        String monthKey = viewModel.monthKey().getValue();
        binding.monthText.setText(Dates.formatMonth(requireContext(),
                FinanceCalc.monthCalendar(monthKey)));
        List<Transaction> monthly = FinanceCalc.forMonth(transactions, monthKey);

        long totalLimit = 0;
        long totalSpent = 0;
        List<BudgetAdapter.Row> rows = new ArrayList<>();
        for (Budget budget : budgets) {
            FinanceCategory category = budget.getCategoryId() == null ? null
                    : categoryMap.get(budget.getCategoryId());
            long spent = FinanceCalc.spentForCategory(monthly, budget.getCategoryId());
            rows.add(new BudgetAdapter.Row(budget, category, spent));
            totalLimit += budget.getLimitAmount();
            totalSpent += spent;
        }
        adapter.setBaseCurrency(baseCurrency);
        adapter.submit(rows);

        binding.totalValue.setText(MoneyText.format(requireContext(), baseCurrency, totalLimit));
        binding.usedValue.setText(MoneyText.format(requireContext(), baseCurrency, totalSpent));
        long remaining = totalLimit - totalSpent;
        binding.remainingValue.setText(MoneyText.format(requireContext(), baseCurrency,
                Math.max(0, remaining)));
        binding.remainingValue.setTextColor(ContextCompat.getColor(requireContext(),
                remaining >= 0 ? R.color.income : R.color.expense));
        int percent = totalLimit <= 0 ? 0 : (int) Math.min(100, totalSpent * 100 / totalLimit);
        binding.totalProgress.setProgress(percent);
        binding.totalProgress.setIndicatorColor(ContextCompat.getColor(requireContext(),
                totalSpent > totalLimit ? R.color.expense : R.color.finance_primary));

        binding.emptyState.getRoot().setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openEditor(@Nullable Budget existing) {
        if (!isAdded()) return;
        final List<FinanceCategory> expenseCategories = new ArrayList<>();
        for (FinanceCategory category : categories) {
            if (FinanceCategory.EXPENSE.equals(category.getType())) expenseCategories.add(category);
        }
        if (expenseCategories.isEmpty()) {
            android.widget.Toast.makeText(requireContext(),
                    R.string.budget_no_expense_categories, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        DialogBudgetBinding dialogBinding = DialogBudgetBinding.inflate(getLayoutInflater());
        final FinanceCategory[] selected = {
                existing == null ? null : categoryMap.get(existing.getCategoryId())
        };
        if (selected[0] == null && existing != null) {
            FinanceCategory placeholder = new FinanceCategory();
            placeholder.setDocId(existing.getCategoryId());
            placeholder.setName(existing.getCategoryName());
            placeholder.setType(FinanceCategory.EXPENSE);
            selected[0] = placeholder;
        }
        if (selected[0] == null) selected[0] = expenseCategories.get(0);

        String[] month = {viewModel.monthKey().getValue()};
        dialogBinding.limitLayout.setPrefixText(baseCurrency);
        AmountInput.attach(dialogBinding.limitEdit, () -> baseCurrency);
        updateCategoryButton(dialogBinding, selected[0]);
        if (existing != null) {
            dialogBinding.categoryButton.setEnabled(false);
            dialogBinding.categoryButton.setAlpha(0.8f);
            dialogBinding.limitEdit.setText(AmountInput.formatMinor(requireContext(), baseCurrency,
                    existing.getLimitAmount()));
            if (existing.getCategoryId() != null) {
                long spent = FinanceCalc.spentForCategory(
                        FinanceCalc.forMonth(transactions, month[0]), existing.getCategoryId());
                dialogBinding.budgetSummary.setVisibility(View.VISIBLE);
                dialogBinding.budgetSummary.setText(getString(R.string.budget_progress,
                        MoneyText.format(requireContext(), baseCurrency, spent),
                        MoneyText.format(requireContext(), baseCurrency,
                                existing.getLimitAmount())));
            }
        }

        dialogBinding.categoryButton.setOnClickListener(v -> {
            final List<String> names = new ArrayList<>();
            final List<FinanceCategory> choices = new ArrayList<>();
            for (FinanceCategory category : expenseCategories) {
                choices.add(category);
                names.add(NameKeys.resolve(requireContext(), category.getName(),
                        category.getNameKey()));
            }
            int checked = Math.max(0, choices.indexOf(selected[0]));
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.budget_select_category)
                    .setSingleChoiceItems(names.toArray(new String[0]), checked, (dialog, which) -> {
                        selected[0] = choices.get(which);
                        updateCategoryButton(dialogBinding, selected[0]);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? R.string.budget_add : R.string.budget_edit)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    dialogBinding.limitLayout.setError(null);
                    long limit = AmountInput.parse(requireContext(),
                            text(dialogBinding.limitEdit.getText()), baseCurrency);
                    if (selected[0] == null) {
                        android.widget.Toast.makeText(requireContext(),
                                R.string.budget_select_category,
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (limit <= 0) {
                        dialogBinding.limitLayout.setError(
                                getString(R.string.error_limit_positive));
                        return;
                    }
                    saveBudget(month[0], selected[0], limit, dialog);
                }));
        dialog.show();
    }

    private void updateCategoryButton(DialogBudgetBinding dialogBinding, FinanceCategory category) {
        if (category == null) {
            dialogBinding.categoryButton.setText(R.string.budget_select_category);
            return;
        }
        dialogBinding.categoryButton.setText(NameKeys.resolve(requireContext(),
                category.getName(), category.getNameKey()));
    }

    private void saveBudget(String monthKey, FinanceCategory category, long limit, Dialog dialog) {
        String uid = currentUid();
        if (uid == null) return;
        repository.saveBudget(uid, monthKey, category, limit,
                new FinanceRepository.SaveCallback() {
                    @Override
                    public void onSuccess(String id) {
                        if (!isAdded()) return;
                        android.widget.Toast.makeText(requireContext(), R.string.budget_saved,
                                android.widget.Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!isAdded()) return;
                        android.widget.Toast.makeText(requireContext(), R.string.save_failed,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmDelete(Budget budget) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.budget_delete_title)
                .setMessage(R.string.budget_delete_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    String uid = currentUid();
                    if (uid == null) return;
                    repository.deleteBudget(uid, budget.getDocId(),
                            new FinanceRepository.SaveCallback() {
                                @Override
                                public void onSuccess(String id) {
                                    if (!isAdded()) return;
                                    android.widget.Toast.makeText(requireContext(),
                                            R.string.budget_deleted,
                                            android.widget.Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(Exception error) {
                                    if (!isAdded()) return;
                                    android.widget.Toast.makeText(requireContext(),
                                            R.string.delete_failed,
                                            android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .show();
    }

    @Nullable
    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
