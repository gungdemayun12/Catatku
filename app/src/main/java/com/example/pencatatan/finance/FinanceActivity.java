package com.example.pencatatan.finance;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.Navigator;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.model.Wallet;
import com.example.pencatatan.databinding.ActivityFinanceBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FinanceActivity extends BaseActivity {

    public static final String EXTRA_TAB = "tab";
    public static final String TAB_TRANSACTIONS = "transactions";
    public static final String TAB_BUDGETS = "budgets";
    public static final String TAB_REPORTS = "reports";
    public static final String TAB_SETTINGS = "settings";

    private ActivityFinanceBinding binding;
    private FinanceViewModel viewModel;
    private String baseCurrency = "IDR";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            startActivity(new Intent(this, com.example.pencatatan.auth.LoginActivity.class));
            finish();
            return;
        }
        binding = ActivityFinanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        viewModel.start(uid());

        binding.homeButton.setOnClickListener(v -> Navigator.goHome(this));
        binding.switchButton.setOnClickListener(v -> Navigator.switchFeature(this, Navigator.FEATURE_TODO));
        binding.addFab.setOnClickListener(v -> startActivity(new Intent(this, TransactionFormActivity.class)));

        binding.bottomNav.setItemIconTintList(ContextCompat.getColorStateList(this,
                R.color.bottom_nav_tint_finance));
        binding.bottomNav.setItemTextColor(ContextCompat.getColorStateList(this,
                R.color.bottom_nav_tint_finance));
        binding.bottomNav.setItemActiveIndicatorColor(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.finance_primary_container)));
        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            int item = navId(getIntent().getStringExtra(EXTRA_TAB));
            if (binding.bottomNav.getSelectedItemId() == item) {
                showTab(item);
            } else {
                binding.bottomNav.setSelectedItemId(item);
            }
        }

        observe();
    }

    private void observe() {
        viewModel.user().observe(this, user -> {
            baseCurrency = user == null || user.getCurrency() == null ? "IDR" : user.getCurrency();
            renderHeader();
        });
        viewModel.transactions().observe(this, transactions -> renderHeader());
        viewModel.wallets().observe(this, wallets -> renderHeader());
        viewModel.rates().observe(this, rates -> renderHeader());
        viewModel.monthKey().observe(this, key -> renderHeader());
    }

    private void renderHeader() {
        if (binding == null) return;
        User user = viewModel.user().getValue();
        if (user != null && user.getCurrency() != null) baseCurrency = user.getCurrency();

        List<Transaction> all = viewModel.transactions().getValue();
        if (all == null) all = new ArrayList<>();
        String month = viewModel.monthKey().getValue();
        List<Transaction> monthly = FinanceCalc.forMonth(all, month);
        long income = FinanceCalc.income(monthly);
        long expense = FinanceCalc.expense(monthly);

        binding.monthText.setText(Dates.formatMonth(this, FinanceCalc.monthCalendar(month)));
        binding.headerSubtitle.setText(Dates.formatMonth(this, FinanceCalc.monthCalendar(month))
                + " · " + getResources().getQuantityString(R.plurals.plural_transactions,
                monthly.size(), monthly.size()));
        binding.incomeChip.setText(MoneyText.formatSigned(this, baseCurrency, income, true));
        binding.expenseChip.setText(MoneyText.formatSigned(this, baseCurrency, expense, false));

        List<Wallet> wallets = viewModel.wallets().getValue();
        Map<String, ExchangeRate> rates = viewModel.rates().getValue();
        long balance = FinanceCalc.totalBalance(wallets, all, rates, baseCurrency);
        binding.balanceText.setText(MoneyText.format(this, baseCurrency, balance));
        boolean missingRate = FinanceCalc.missingRate(wallets, rates, baseCurrency);
        if (missingRate) {
            binding.balanceWarning.setText(R.string.finance_no_wallet_warning);
            binding.balanceWarning.setVisibility(View.VISIBLE);
        } else if (FinanceCalc.hasForeignCurrencyWallet(wallets, baseCurrency)) {
            binding.balanceWarning.setText(R.string.finance_estimated_total);
            binding.balanceWarning.setVisibility(View.VISIBLE);
        } else {
            binding.balanceWarning.setVisibility(View.GONE);
        }
    }

    private void showTab(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_budgets) {
            fragment = new FinanceBudgetsFragment();
        } else if (itemId == R.id.nav_reports) {
            fragment = new FinanceReportsFragment();
        } else if (itemId == R.id.nav_settings) {
            fragment = new FinanceSettingsFragment();
        } else {
            fragment = new FinanceTransactionsFragment();
        }
        binding.addFab.setVisibility(itemId == R.id.nav_transactions ? View.VISIBLE : View.GONE);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private int navId(String tab) {
        if (TAB_BUDGETS.equals(tab)) return R.id.nav_budgets;
        if (TAB_REPORTS.equals(tab)) return R.id.nav_reports;
        if (TAB_SETTINGS.equals(tab)) return R.id.nav_settings;
        return R.id.nav_transactions;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String tab = intent.getStringExtra(EXTRA_TAB);
        if (tab != null) {
            int item = navId(tab);
            if (binding.bottomNav.getSelectedItemId() == item) {
                showTab(item);
            } else {
                binding.bottomNav.setSelectedItemId(item);
            }
        }
    }

}
