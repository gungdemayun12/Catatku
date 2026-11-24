package com.example.pencatatan.finance;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.pencatatan.R;
import com.example.pencatatan.common.AmountInput;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.model.Wallet;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityTransactionFormBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionFormActivity extends BaseActivity {

    public static final String EXTRA_TRANSACTION_ID = "transaction_id";

    private ActivityTransactionFormBinding binding;
    private final FinanceRepository repository = new FinanceRepository();
    private final UserRepository userRepository = new UserRepository();
    private final List<Wallet> wallets = new ArrayList<>();
    private final List<FinanceCategory> categories = new ArrayList<>();
    private final Map<String, ExchangeRate> rates = new HashMap<>();

    private ListenerRegistration walletListener;
    private ListenerRegistration categoryListener;
    private ListenerRegistration rateListener;

    private String transactionId;
    private boolean isNew = true;
    private Transaction editing;
    private boolean editingLoaded = false;
    private boolean editingAmountApplied = false;
    private boolean walletsLoaded = false;
    private boolean categoriesLoaded = false;
    private boolean walletWarningShown = false;
    private String baseCurrency = "IDR";
    private String type = Transaction.EXPENSE;
    private String walletId;
    private String categoryId;
    private double rate = 1;
    private Calendar date = Calendar.getInstance();
    /** "none" | "daily" | "weekly" | "monthly" | "yearly". */
    private String repeatType = "none";
    /** Optional last allowed occurrence; null means "until I stop it". */
    private Calendar repeatEnd;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        baseCurrency = Prefs.getCurrency(this);
        if (uid() == null) {
            finish();
            return;
        }
        binding = ActivityTransactionFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyBottomInsets(binding.getRoot());
        transactionId = getIntent().getStringExtra(EXTRA_TRANSACTION_ID);
        isNew = transactionId == null || transactionId.trim().isEmpty();
        if (isNew) type = Prefs.getDefaultTransactionType(this);
        binding.formTitle.setText(isNew ? R.string.transaction_new : R.string.transaction_edit);
        binding.backButton.setOnClickListener(v -> finish());

        setupRows();
        setupListeners();
        AmountInput.attach(binding.amountEdit, () -> currency(), () -> {
            binding.amountLayout.setError(null);
            updateBasePreview();
        });
        loadUser();
        listenWallets();
        listenCategories();
        listenRates();
        if (!isNew) loadTransaction();
        updateCurrencyUi();
    }

    private void setupRows() {
        binding.walletRow.rowIcon.setImageResource(R.drawable.ic_wallet);
        binding.walletRow.rowLabel.setText(R.string.transaction_wallet);
        binding.walletRow.getRoot().setOnClickListener(v -> chooseWallet());

        binding.categoryRow.rowIcon.setImageResource(R.drawable.ic_tag);
        binding.categoryRow.rowLabel.setText(R.string.transaction_category);
        binding.categoryRow.getRoot().setOnClickListener(v -> chooseCategory());

        binding.dateRow.rowIcon.setImageResource(R.drawable.ic_calendar);
        binding.dateRow.rowLabel.setText(R.string.transaction_date);
        binding.dateRow.getRoot().setOnClickListener(v -> pickDate());

        binding.rateRow.rowIcon.setImageResource(R.drawable.ic_currency);
        binding.rateRow.getRoot().setOnClickListener(v -> openRateDialog());

        binding.repeatRow.rowIcon.setImageResource(R.drawable.ic_repeat);
        binding.repeatRow.rowLabel.setText(R.string.repeat_label);
        binding.repeatRow.getRoot().setOnClickListener(v -> chooseRepeat());
        binding.repeatEndRow.rowIcon.setImageResource(R.drawable.ic_calendar);
        binding.repeatEndRow.rowLabel.setText(R.string.repeat_end_date);
        binding.repeatEndRow.getRoot().setOnClickListener(v -> pickRepeatEnd());

        binding.saveButton.setOnClickListener(v -> save());
        updateRepeatRows();
    }

    /**
     * Repeat picker.
     *
     * <p>Kept to the four rhythms that actually recur in a personal budget:
     * daily (coffee, parking), weekly (cleaning, allowance), monthly (rent,
     * subscriptions) and yearly (insurance, domains). "None" is first so the
     * field is always reversible.</p>
     */
    private void chooseRepeat() {
        String[] values = {"none", "daily", "weekly", "monthly", "yearly"};
        String[] labels = {
                getString(R.string.repeat_none),
                getString(R.string.repeat_daily),
                getString(R.string.repeat_weekly),
                getString(R.string.repeat_monthly),
                getString(R.string.repeat_yearly)
        };
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(repeatType)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.repeat_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    repeatType = values[which];
                    if ("none".equals(repeatType)) repeatEnd = null;
                    updateRepeatRows();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickRepeatEnd() {
        if (!isRepeating()) return;
        Calendar seed = repeatEnd == null ? (Calendar) date.clone() : repeatEnd;
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, day, 0, 0, 0);
            picked.set(Calendar.MILLISECOND, 0);
            // An end date before the first occurrence would make a series that
            // never generates anything; treat it as "no end".
            if (picked.before(Dates.startOfDay(date))) picked = null;
            repeatEnd = picked;
            updateRepeatRows();
        }, seed.get(Calendar.YEAR), seed.get(Calendar.MONTH),
                seed.get(Calendar.DAY_OF_MONTH)).show();
    }

    private boolean isRepeating() {
        return !"none".equals(repeatType);
    }

    private void updateRepeatRows() {
        int label = "daily".equals(repeatType) ? R.string.repeat_daily
                : "weekly".equals(repeatType) ? R.string.repeat_weekly
                : "monthly".equals(repeatType) ? R.string.repeat_monthly
                : "yearly".equals(repeatType) ? R.string.repeat_yearly
                : R.string.repeat_none;
        binding.repeatRow.rowValue.setText(label);

        boolean repeats = isRepeating();
        binding.repeatEndRow.getRoot().setVisibility(repeats ? View.VISIBLE : View.GONE);
        binding.repeatEndDivider.setVisibility(repeats ? View.VISIBLE : View.GONE);
        binding.repeatEndRow.rowValue.setText(repeatEnd == null
                ? getString(R.string.optional) : Dates.formatDate(this, repeatEnd.getTime()));
    }

    private void setupListeners() {
        binding.typeGroup.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            type = checkedId == R.id.typeIncome ? Transaction.INCOME : Transaction.EXPENSE;
            ensureCategoryForType();
            updateTypeColors();
        });
        binding.typeGroup.check(Transaction.INCOME.equals(type) ? R.id.typeIncome : R.id.typeExpense);
        updateTypeColors();
    }

    private void updateTypeColors() {
        int active = type.equals(Transaction.INCOME)
                ? getColor(R.color.income) : getColor(R.color.expense);
        binding.saveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active));
        binding.saveButton.setTextColor(getColor(R.color.white));
    }

    private void loadUser() {
        userRepository.get(uid(), new UserRepository.UserListener() {
            @Override
            public void onUser(User user) {
                if (user.getCurrency() != null && !user.getCurrency().isEmpty()) {
                    baseCurrency = user.getCurrency();
                    Prefs.setCurrency(TransactionFormActivity.this, baseCurrency);
                }
                updateCurrencyUi();
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void listenWallets() {
        walletListener = repository.listenWallets(uid(), new FinanceRepository.OnWallets() {
            @Override
            public void onData(List<Wallet> data) {
                wallets.clear();
                if (data != null) wallets.addAll(data);
                walletsLoaded = true;
                if (walletId == null && !wallets.isEmpty()) {
                    walletId = preferredWalletId();
                }
                applyEditing();
                updateCurrencyUi();
                if (wallets.isEmpty() && !walletWarningShown) {
                    walletWarningShown = true;
                    toast(getString(R.string.wallet_no_wallet));
                }
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void listenCategories() {
        categoryListener = repository.listenCategories(uid(),
                new FinanceRepository.OnFinanceCategories() {
                    @Override
                    public void onData(List<FinanceCategory> data) {
                        categories.clear();
                        if (data != null) categories.addAll(data);
                        categoriesLoaded = true;
                        ensureCategoryForType();
                        applyEditing();
                    }

                    @Override
                    public void onError(Exception error) {
                    }
                });
    }

    private void listenRates() {
        rateListener = repository.listenRates(uid(), new FinanceRepository.OnRates() {
            @Override
            public void onData(Map<String, ExchangeRate> data) {
                rates.clear();
                if (data != null) rates.putAll(data);
                if (!editingLoaded && rate <= 0) {
                    rate = rateFor(currency());
                }
                updateCurrencyUi();
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void loadTransaction() {
        repository.loadTransaction(uid(), transactionId, new FinanceRepository.OnTransaction() {
            @Override
            public void onData(Transaction transaction) {
                // A transfer is stored as two rows that must stay balanced. Editing
                // one leg here would change only one wallet's balance, so the money
                // would appear or disappear. The honest route is to delete the
                // transfer and record it again.
                if (transaction.isTransfer()) {
                    toast(getString(R.string.transfer_edit_blocked));
                    finish();
                    return;
                }
                editing = transaction;
                editingLoaded = true;
                type = transaction.isIncome() ? Transaction.INCOME : Transaction.EXPENSE;
                walletId = transaction.getWalletId();
                categoryId = transaction.getCategoryId();
                rate = transaction.getExchangeRate();
                if (transaction.getDate() != null) {
                    date = Calendar.getInstance();
                    date.setTime(transaction.getDate().toDate());
                }
                repeatType = transaction.getRepeatType();
                if (transaction.getRepeatEndDate() != null) {
                    repeatEnd = Calendar.getInstance();
                    repeatEnd.setTime(transaction.getRepeatEndDate().toDate());
                }
                updateRepeatRows();
                binding.noteEdit.setText(transaction.getNote());
                binding.typeGroup.check(transaction.isIncome() ? R.id.typeIncome : R.id.typeExpense);
                applyEditing();
                updateCurrencyUi();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
                finish();
            }
        });
    }

    private void applyEditing() {
        if (!editingLoaded || editing == null || !walletsLoaded || binding == null) return;
        if (!editingAmountApplied) {
            binding.amountEdit.setText(AmountInput.formatMinor(this, currency(),
                    editing.getAmount()));
            binding.amountEdit.setSelection(binding.amountEdit.getText() == null ? 0
                    : binding.amountEdit.getText().length());
            editingAmountApplied = true;
        }
        updateRows();
        updateBasePreview();
    }

    private void updateRows() {
        Wallet wallet = wallet();
        if (wallet == null) {
            binding.walletRow.rowValue.setText(R.string.wallet_no_wallet);
        } else {
            binding.walletRow.rowValue.setText(
                    NameKeys.resolve(this, wallet.getName(), wallet.getNameKey())
                            + " · " + wallet.getCurrency());
        }

        FinanceCategory category = category();
        binding.categoryRow.rowValue.setText(category == null
                ? getString(R.string.error_category_required)
                : NameKeys.resolve(this, category.getName(), category.getNameKey()));

        binding.dateRow.rowValue.setText(Dates.formatDate(this, date.getTime()));
    }

    private void updateCurrencyUi() {
        if (binding == null) return;
        String currency = currency();
        binding.amountLayout.setPrefixText(currency);
        boolean converted = !currency.equals(baseCurrency);
        binding.rateRow.getRoot().setVisibility(converted ? android.view.View.VISIBLE
                : android.view.View.GONE);
        binding.rateDivider.setVisibility(converted ? android.view.View.VISIBLE
                : android.view.View.GONE);
        if (converted) {
            binding.rateRow.rowLabel.setText(getString(R.string.exchange_rate_label,
                    currency, baseCurrency));
            binding.rateRow.rowValue.setText(rate > 0
                    ? "1 " + currency + " = " + MoneyText.formatRate(rate) + " " + baseCurrency
                    : getString(R.string.rate_missing));
        }
        updateRows();
        updateBasePreview();
    }

    private void updateBasePreview() {
        if (binding == null) return;
        String currency = currency();
        long amount = AmountInput.parse(this, text(binding.amountEdit.getText()), currency);
        if (amount > 0 && !currency.equals(baseCurrency) && rate > 0) {
            long base = Money.toBase(amount, currency, rate, baseCurrency);
            binding.basePreview.setText(getString(R.string.transaction_base_hint,
                    MoneyText.format(this, baseCurrency, base), baseCurrency));
            binding.basePreview.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.basePreview.setVisibility(android.view.View.GONE);
        }
    }

    private String currency() {
        Wallet wallet = wallet();
        if (wallet != null && wallet.getCurrency() != null) return wallet.getCurrency();
        if (editing != null && editing.getCurrency() != null) return editing.getCurrency();
        return baseCurrency;
    }

    @Nullable
    private Wallet wallet() {
        if (walletId == null) return null;
        for (Wallet wallet : wallets) {
            if (walletId.equals(wallet.getDocId())) return wallet;
        }
        return null;
    }

    private String preferredWalletId() {
        String preferred = Prefs.getDefaultWalletId(this);
        if (preferred != null) {
            for (Wallet wallet : wallets) {
                if (preferred.equals(wallet.getDocId())) return preferred;
            }
        }
        return wallets.get(0).getDocId();
    }

    @Nullable
    private FinanceCategory category() {
        if (categoryId == null) return null;
        for (FinanceCategory category : categories) {
            if (categoryId.equals(category.getDocId())) return category;
        }
        return null;
    }

    private double rateFor(String currency) {
        if (currency == null || currency.equals(baseCurrency)) return 1;
        ExchangeRate stored = rates.get(currency);
        if (stored != null && stored.getRateToBase() > 0) {
            return stored.getRateToBase();
        }
        return CurrencyApiService.getFallbackRate(currency, baseCurrency);
    }

    private void ensureCategoryForType() {
        if (editingLoaded && !categoriesLoaded) return;
        FinanceCategory current = category();
        if (current != null && type.equals(current.getType())) return;
        categoryId = null;
        for (FinanceCategory category : categories) {
            if (type.equals(category.getType())) {
                categoryId = category.getDocId();
                break;
            }
        }
        if (binding != null) updateRows();
    }

    private void chooseWallet() {
        if (wallets.isEmpty()) {
            toast(getString(R.string.wallet_no_wallet));
            return;
        }
        final List<String> names = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        for (Wallet wallet : wallets) {
            if (wallet.getDocId() == null) continue;
            ids.add(wallet.getDocId());
            names.add(NameKeys.resolve(this, wallet.getName(), wallet.getNameKey())
                    + " · " + wallet.getCurrency());
        }
        int checked = Math.max(0, ids.indexOf(walletId));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.transaction_wallet)
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (dialog, which) -> {
                    walletId = ids.get(which);
                    rate = rateFor(currency());
                    applyEditingAmountForCurrency();
                    updateCurrencyUi();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyEditingAmountForCurrency() {
        String currency = currency();
        long amount = AmountInput.parse(this, text(binding.amountEdit.getText()), currency);
        binding.amountEdit.setText(AmountInput.formatMinor(this, currency, amount));
        binding.amountEdit.setSelection(binding.amountEdit.getText() == null ? 0
                : binding.amountEdit.getText().length());
    }

    private void chooseCategory() {
        final List<String> names = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        for (FinanceCategory category : categories) {
            if (!type.equals(category.getType())) continue;
            ids.add(category.getDocId());
            names.add(NameKeys.resolve(this, category.getName(), category.getNameKey()));
        }
        if (names.isEmpty()) {
            toast(getString(R.string.finance_category_none));
            return;
        }
        int checked = Math.max(0, ids.indexOf(categoryId));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.transaction_category)
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (dialog, which) -> {
                    categoryId = ids.get(which);
                    updateRows();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickDate() {
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar value = Calendar.getInstance();
            value.set(year, month, day, 12, 0, 0);
            value.set(Calendar.MILLISECOND, 0);
            date = value;
            updateRows();
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH),
                date.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void openRateDialog() {
        String currency = currency();
        if (currency.equals(baseCurrency)) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);
        input.setHint(R.string.rate_hint);
        if (rate > 0) {
            input.setText(MoneyText.formatRate(rate));
            input.setSelection(input.getText().length());
        }
        FrameLayout container = new FrameLayout(this);
        int padding = dp(24);
        container.setPadding(padding, dp(6), padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.rate_for, currency, baseCurrency))
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    double value = parseRate(text(input.getText()));
                    if (value <= 0) {
                        input.setError(getString(R.string.error_rate_positive));
                        return;
                    }
                    rate = value;
                    updateCurrencyUi();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private double parseRate(String raw) {
        if (raw == null) return 0;
        try {
            return Double.parseDouble(raw.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void save() {
        binding.amountLayout.setError(null);
        binding.noteLayout.setError(null);
        String currency = currency();
        long amount = AmountInput.parse(this, text(binding.amountEdit.getText()), currency);
        if (amount <= 0) {
            binding.amountLayout.setError(getString(R.string.error_amount_positive));
            return;
        }
        Wallet wallet = wallet();
        if (wallet == null) {
            toast(getString(R.string.error_wallet_required));
            return;
        }
        FinanceCategory category = category();
        if (category == null || !type.equals(category.getType())) {
            toast(getString(R.string.error_category_required));
            return;
        }
        if (!currency.equals(baseCurrency) && rate <= 0) {
            toast(getString(R.string.exchange_rate_required));
            openRateDialog();
            return;
        }
        String note = text(binding.noteEdit.getText());
        if (note.length() > 200) {
            binding.noteLayout.setError(getString(R.string.transaction_note_too_long));
            return;
        }

        long amountBase = Money.toBase(amount, currency, rate, baseCurrency);
        if (amountBase <= 0) {
            binding.amountLayout.setError(getString(R.string.error_amount_positive));
            return;
        }

        Transaction transaction = editing == null ? new Transaction() : editing;
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setExchangeRate(rate);
        transaction.setAmountBase(amountBase);
        transaction.setWalletId(wallet.getDocId());
        transaction.setWalletName(NameKeys.resolve(this, wallet.getName(), wallet.getNameKey()));
        transaction.setCategoryId(category.getDocId());
        transaction.setCategoryName(NameKeys.resolve(this, category.getName(), category.getNameKey()));
        transaction.setNote(note.isEmpty() ? null : note);
        transaction.setDate(new Timestamp(date.getTime()));
        transaction.setMonthKey(Dates.monthKey(date.getTime()));

        // Recurrence. The series is anchored on the transaction's own date, so
        // the first copy is the one being saved and the next one is computed by
        // stepping forward from here (see RecurringHelper.calculateNext).
        if (isRepeating()) {
            boolean sameSeries = editing != null
                    && repeatType.equals(editing.getRepeatType())
                    && editing.getRepeatNextDate() != null;
            transaction.setRepeatType(repeatType);
            transaction.setRepeatEndDate(repeatEnd == null
                    ? null : new Timestamp(Dates.endOfDay(repeatEnd).getTime()));
            // Anchor on the day-of-month the user picked. Kept on the template so
            // a month that clamps (31 -> 28 Feb) cannot permanently shorten the
            // series.
            int anchorDay = RecurringHelper.dayOf(date);
            transaction.setRepeatAnchorDay(anchorDay);
            // Editing an existing series must NOT rewind its schedule: resetting
            // repeatNextDate to "the next date after this row" would make
            // generateDue() create the same occurrence a second time. Keep the
            // stored pointer unless the recurrence itself changed.
            transaction.setRepeatNextDate(sameSeries
                    ? editing.getRepeatNextDate()
                    : RecurringHelper.calculateNext(date, repeatType, anchorDay));
            if (transaction.getRepeatGroup() == null || transaction.getRepeatGroup().isEmpty()) {
                transaction.setRepeatGroup(java.util.UUID.randomUUID().toString());
            }
        } else {
            transaction.setRepeatType("none");
            transaction.setRepeatNextDate(null);
            transaction.setRepeatEndDate(null);
            transaction.setRepeatAnchorDay(0);
        }

        binding.saveButton.setEnabled(false);
        repository.saveTransaction(uid(), transaction, isNew,
                new FinanceRepository.SaveCallback() {
                    @Override
                    public void onSuccess(String id) {
                        transaction.setDocId(id);
                        if (!transaction.isIncome()) {
                            BudgetAlertHelper.check(TransactionFormActivity.this, uid(), transaction);
                        }
                        toast(getString(R.string.transaction_saved));
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onError(Exception error) {
                        binding.saveButton.setEnabled(true);
                        toast(getString(R.string.save_failed));
                    }
                });
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (walletListener != null) walletListener.remove();
        if (categoryListener != null) categoryListener.remove();
        if (rateListener != null) rateListener.remove();
    }
}
