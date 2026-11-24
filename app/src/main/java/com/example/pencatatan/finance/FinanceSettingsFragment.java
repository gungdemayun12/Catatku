package com.example.pencatatan.finance;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.pencatatan.BuildConfig;
import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.common.RepoCallback;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.model.Wallet;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.FragmentFinanceSettingsBinding;
import com.example.pencatatan.settings.NotificationSettingsActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;

public class FinanceSettingsFragment extends Fragment {

    private FragmentFinanceSettingsBinding binding;
    private FinanceViewModel viewModel;
    private final FinanceRepository repository = new FinanceRepository();
    private final UserRepository userRepository = new UserRepository();
    private final List<Wallet> wallets = new ArrayList<>();
    private String baseCurrency = "IDR";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFinanceSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);

        binding.rowWallets.rowIcon.setImageResource(R.drawable.ic_wallet);
        binding.rowWallets.rowTitle.setText(R.string.finance_settings_wallets);
        binding.rowWallets.rowSubtitle.setText(R.string.finance_settings_wallets_desc);
        binding.rowWallets.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), WalletsActivity.class)));

        binding.rowCategories.rowIcon.setImageResource(R.drawable.ic_tag);
        binding.rowCategories.rowTitle.setText(R.string.finance_settings_categories);
        binding.rowCategories.rowSubtitle.setText(R.string.finance_settings_categories_desc);
        binding.rowCategories.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FinanceCategoriesActivity.class)));

        binding.rowBills.rowIcon.setImageResource(R.drawable.ic_receipt);
        binding.rowBills.rowTitle.setText(R.string.finance_settings_bills);
        binding.rowBills.rowSubtitle.setText(R.string.finance_settings_bills_desc);
        binding.rowBills.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BillsActivity.class)));

        binding.rowGoals.rowIcon.setImageResource(R.drawable.ic_savings);
        binding.rowGoals.rowTitle.setText(R.string.finance_settings_goals);
        binding.rowGoals.rowSubtitle.setText(R.string.finance_settings_goals_desc);
        binding.rowGoals.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SavingsGoalsActivity.class)));

        binding.rowDebts.rowIcon.setImageResource(R.drawable.ic_handshake);
        binding.rowDebts.rowTitle.setText(R.string.finance_settings_debts);
        binding.rowDebts.rowSubtitle.setText(R.string.finance_settings_debts_desc);
        binding.rowDebts.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DebtsActivity.class)));

        binding.rowRates.rowIcon.setImageResource(R.drawable.ic_currency);
        binding.rowRates.rowTitle.setText(R.string.finance_settings_rates);
        binding.rowRates.rowSubtitle.setText(R.string.finance_settings_rates_desc);
        binding.rowRates.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ExchangeRatesActivity.class)));

        binding.rowCurrency.rowIcon.setImageResource(R.drawable.ic_currency);
        binding.rowCurrency.rowTitle.setText(R.string.finance_settings_currency);
        binding.rowCurrency.rowSubtitle.setText(R.string.finance_settings_currency_desc);
        binding.rowCurrency.getRoot().setOnClickListener(v -> chooseCurrency());

        binding.rowDefaultWallet.rowIcon.setImageResource(R.drawable.ic_wallet);
        binding.rowDefaultWallet.rowTitle.setText(R.string.finance_settings_default_wallet);
        binding.rowDefaultWallet.getRoot().setOnClickListener(v -> pickDefaultWallet());

        binding.rowDefaultType.rowIcon.setImageResource(R.drawable.ic_payments);
        binding.rowDefaultType.rowTitle.setText(R.string.finance_settings_default_type);
        binding.rowDefaultType.rowSubtitle.setText(R.string.finance_settings_default_type_desc);
        binding.rowDefaultType.getRoot().setOnClickListener(v -> pickDefaultType());
        updateDefaultTypeRow();

        binding.rowBudgetAlerts.rowIcon.setImageResource(R.drawable.ic_bell);
        binding.rowBudgetAlerts.rowTitle.setText(R.string.finance_settings_budget_alerts);
        binding.rowBudgetAlerts.rowSubtitle.setText(R.string.finance_settings_budget_alerts_desc);
        binding.rowBudgetAlerts.rowSwitch.setChecked(Prefs.budgetAlertsEnabled(requireContext()));
        binding.rowBudgetAlerts.getRoot().setOnClickListener(v ->
                binding.rowBudgetAlerts.rowSwitch.toggle());
        binding.rowBudgetAlerts.rowSwitch.setOnCheckedChangeListener((button, checked) ->
                Prefs.setBudgetAlertsEnabled(requireContext(), checked));

        binding.rowExport.rowIcon.setImageResource(R.drawable.ic_download);
        binding.rowExport.rowTitle.setText(R.string.finance_settings_export);
        binding.rowExport.rowSubtitle.setText(R.string.finance_settings_export_desc);
        binding.rowExport.getRoot().setOnClickListener(v -> exportData());

        binding.rowNotifications.rowIcon.setImageResource(R.drawable.ic_bell);
        binding.rowNotifications.rowTitle.setText(R.string.settings_notifications);
        binding.rowNotifications.rowSubtitle.setText(R.string.settings_notifications_desc);
        binding.rowNotifications.getRoot().setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationSettingsActivity.class)));

        binding.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        binding.rowCurrency.rowSubtitle.setText(baseCurrency);

        viewModel.user().observe(getViewLifecycleOwner(), user -> {
            if (user != null && user.getCurrency() != null) {
                baseCurrency = user.getCurrency();
                binding.rowCurrency.rowSubtitle.setText(baseCurrency);
            }
        });

        viewModel.wallets().observe(getViewLifecycleOwner(), data -> {
            wallets.clear();
            if (data != null) wallets.addAll(data);
            updateDefaultWalletRow();
        });
        updateDefaultWalletRow();
    }

    private void updateDefaultWalletRow() {
        if (binding == null) return;
        String id = Prefs.getDefaultWalletId(requireContext());
        if (id == null) {
            binding.rowDefaultWallet.rowSubtitle.setText(
                    R.string.finance_settings_default_wallet_none);
            return;
        }
        for (Wallet wallet : wallets) {
            if (id.equals(wallet.getDocId())) {
                Prefs.setDefaultWallet(requireContext(), id,
                        NameKeys.resolve(requireContext(), wallet.getName(), wallet.getNameKey()));
                binding.rowDefaultWallet.rowSubtitle.setText(
                        NameKeys.resolve(requireContext(), wallet.getName(), wallet.getNameKey()));
                return;
            }
        }
        if (!wallets.isEmpty()) {
            Prefs.setDefaultWallet(requireContext(), null, null);
            binding.rowDefaultWallet.rowSubtitle.setText(
                    R.string.finance_settings_default_wallet_none);
        } else {
            String stored = Prefs.getDefaultWalletName(requireContext());
            binding.rowDefaultWallet.rowSubtitle.setText(stored == null
                    ? getString(R.string.finance_settings_default_wallet_none) : stored);
        }
    }

    private void updateDefaultTypeRow() {
        if (binding == null) return;
        String type = Prefs.getDefaultTransactionType(requireContext());
        binding.rowDefaultType.rowSubtitle.setText(Prefs.TYPE_INCOME.equals(type)
                ? R.string.finance_income : R.string.finance_expense);
    }

    private void pickDefaultWallet() {
        final List<String> names = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        names.add(getString(R.string.finance_settings_default_wallet_none));
        ids.add(null);
        for (Wallet wallet : wallets) {
            if (wallet.getDocId() == null) continue;
            ids.add(wallet.getDocId());
            names.add(NameKeys.resolve(requireContext(), wallet.getName(), wallet.getNameKey())
                    + " · " + wallet.getCurrency());
        }
        String current = Prefs.getDefaultWalletId(requireContext());
        int checked = Math.max(0, ids.indexOf(current));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_settings_default_wallet)
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (dialog, which) -> {
                    String id = ids.get(which);
                    String name = which == 0 ? null : names.get(which);
                    Prefs.setDefaultWallet(requireContext(), id, name);
                    updateDefaultWalletRow();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickDefaultType() {
        String[] values = {Prefs.TYPE_EXPENSE, Prefs.TYPE_INCOME};
        String[] labels = {getString(R.string.finance_expense), getString(R.string.finance_income)};
        String current = Prefs.getDefaultTransactionType(requireContext());
        int checked = Prefs.TYPE_INCOME.equals(current) ? 1 : 0;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_settings_default_type)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    Prefs.setDefaultTransactionType(requireContext(), values[which]);
                    updateDefaultTypeRow();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportData() {
        List<Transaction> all = viewModel.transactions().getValue();
        String monthKey = viewModel.monthKey().getValue();
        String monthName = Dates.formatMonth(requireContext(), FinanceCalc.monthCalendar(monthKey));
        ExportDialog.showScopedPicker(requireActivity(), new ExportDialog.Source() {
            @Override
            public List<Transaction> transactionsFor(boolean wholeHistory) {
                List<Transaction> data = viewModel.transactions().getValue();
                if (data == null) return new ArrayList<>();
                return wholeHistory ? new ArrayList<>(data)
                        : FinanceCalc.forMonth(data, monthKey);
            }

            @Override
            public String rangeLabel(boolean wholeHistory) {
                return wholeHistory ? getString(R.string.export_scope_all) : monthName;
            }

            @Override
            public String baseCurrency() {
                return baseCurrency;
            }
        }, monthName);
    }

    private void chooseCurrency() {
        String uid = currentUid();
        if (uid == null) return;
        showCurrencyDialog(uid);
    }

    private void showCurrencyDialog(String uid) {
        String[] options = new String[Money.SUPPORTED.length];
        for (int i = 0; i < Money.SUPPORTED.length; i++) {
            options[i] = Money.SUPPORTED[i] + "  " + Money.countryFlag(Money.SUPPORTED[i]);
        }
        int checked = 0;
        for (int i = 0; i < Money.SUPPORTED.length; i++) {
            if (Money.SUPPORTED[i].equals(baseCurrency)) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_settings_currency)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    applyCurrencyChange(uid, Money.SUPPORTED[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyCurrencyChange(String uid, String code) {
        if (code == null || code.equals(baseCurrency)) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.currency_change_title)
                .setMessage(getString(R.string.currency_change_msg, baseCurrency, code))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_label,
                        (dialog, which) -> migrateCurrency(uid, code))
                .show();
    }

    private void migrateCurrency(String uid, String code) {
        android.widget.Toast.makeText(requireContext(), R.string.currency_converting,
                android.widget.Toast.LENGTH_SHORT).show();
        repository.migrateBaseCurrency(uid, baseCurrency, code,
                new FinanceRepository.MigrateCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        baseCurrency = code;
                        Prefs.setCurrency(requireContext(), code);
                        if (binding != null) binding.rowCurrency.rowSubtitle.setText(code);
                        userRepository.updateCurrency(uid, code, new RepoCallback() {
                            @Override
                            public void onSuccess() {
                            }

                            @Override
                            public void onError(Exception error) {
                            }
                        });

                        // Fetch and save fresh live rates for the new base currency
                        CurrencyApiService.fetchRates(code, new CurrencyApiService.RateCallback() {
                            @Override
                            public void onRates(Map<String, Double> ratesMap) {
                                if (ratesMap != null) {
                                    for (Map.Entry<String, Double> entry : ratesMap.entrySet()) {
                                        repository.saveRate(uid, entry.getKey(), entry.getValue(), new FinanceRepository.SaveCallback() {
                                            @Override
                                            public void onSuccess(String id) {}
                                            @Override
                                            public void onError(Exception error) {}
                                        });
                                    }
                                }
                            }

                            @Override
                            public void onError(String message) {}
                        });

                        android.widget.Toast.makeText(requireContext(),
                                R.string.currency_changed,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!isAdded()) return;
                        // A missing rate is not a generic failure: tell the user
                        // to enter it first, otherwise they retry the same thing.
                        android.widget.Toast.makeText(requireContext(),
                                "rate_missing".equals(error.getMessage())
                                        ? R.string.currency_rate_required
                                        : R.string.save_failed,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
