package com.example.pencatatan.finance;

import com.example.pencatatan.common.DialogScroll;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.AmountInput;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.model.Wallet;
import com.example.pencatatan.data.repository.BaseRepository;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityWalletsBinding;
import com.example.pencatatan.databinding.DialogTransferBinding;
import com.example.pencatatan.databinding.DialogWalletBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WalletsActivity extends BaseActivity {

    private ActivityWalletsBinding binding;
    private WalletAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();
    private final UserRepository userRepository = new UserRepository();

    private final List<Wallet> wallets = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, ExchangeRate> rates = new HashMap<>();
    private String baseCurrency = "IDR";

    private ListenerRegistration walletListener;
    private ListenerRegistration transactionListener;
    private ListenerRegistration rateListener;
    private ListenerRegistration userListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        baseCurrency = Prefs.getCurrency(this);
        binding = ActivityWalletsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());
        binding.addButton.setOnClickListener(v -> openEditor(null));
        binding.transferButton.setOnClickListener(v -> openTransfer());

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_wallet);
        binding.emptyState.emptyTitle.setText(R.string.wallet_empty);
        binding.emptyState.emptyDesc.setText(R.string.wallet_empty_desc);
        binding.emptyState.emptyAction.setText(R.string.wallet_add);
        binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
        binding.emptyState.emptyAction.setOnClickListener(v -> openEditor(null));

        adapter = new WalletAdapter(new WalletAdapter.Listener() {
            @Override
            public void onEdit(WalletAdapter.Row row) {
                openEditor(row.wallet);
            }

            @Override
            public void onDelete(WalletAdapter.Row row) {
                confirmDelete(row.wallet);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        listenUser();
        listenWallets();
        listenTransactions();
        listenRates();
    }

    private void listenUser() {
        userListener = userRepository.listen(uid(), new UserRepository.UserListener() {
            @Override
            public void onUser(User user) {
                if (user != null && user.getCurrency() != null && !user.getCurrency().isEmpty()) {
                    baseCurrency = user.getCurrency();
                    Prefs.setCurrency(WalletsActivity.this, baseCurrency);
                    apply();
                }
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

    private void listenRates() {
        rateListener = repository.listenRates(uid(), new FinanceRepository.OnRates() {
            @Override
            public void onData(Map<String, ExchangeRate> data) {
                rates.clear();
                if (data != null) rates.putAll(data);
                apply();
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void apply() {
        if (binding == null) return;
        Map<String, Long> balances = FinanceCalc.walletBalances(wallets, transactions);
        Map<String, Integer> usage = new HashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getWalletId() == null) continue;
            usage.put(transaction.getWalletId(),
                    usage.getOrDefault(transaction.getWalletId(), 0) + 1);
        }

        List<WalletAdapter.Row> rows = new ArrayList<>();
        for (Wallet wallet : wallets) {
            if (wallet.getDocId() == null) continue;
            rows.add(new WalletAdapter.Row(wallet,
                    balances.getOrDefault(wallet.getDocId(), 0L),
                    usage.getOrDefault(wallet.getDocId(), 0) > 0));
        }
        adapter.submit(rows);

        long total = FinanceCalc.totalBalance(wallets, transactions, rates, baseCurrency);
        binding.totalBalance.setText(MoneyText.format(this, baseCurrency, total));
        binding.totalBalance.setTextColor(ContextCompat.getColor(this,
                total < 0 ? R.color.expense : R.color.color_on_surface));
        binding.walletCount.setText(getResources().getQuantityString(
                R.plurals.plural_wallets, rows.size(), rows.size()));
        boolean missingRate = FinanceCalc.missingRate(wallets, rates, baseCurrency);
        if (missingRate) {
            binding.rateWarning.setText(R.string.finance_no_wallet_warning);
            binding.rateWarning.setVisibility(View.VISIBLE);
        } else if (FinanceCalc.hasForeignCurrencyWallet(wallets, baseCurrency)) {
            binding.rateWarning.setText(R.string.finance_estimated_total);
            binding.rateWarning.setVisibility(View.VISIBLE);
        } else {
            binding.rateWarning.setVisibility(View.GONE);
        }
        binding.emptyState.getRoot().setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Move money between two wallets.
     * <p>
     * The dialog is built from the live wallet list, so a wallet the user just
     * added is immediately available. Picking the same wallet on both sides is
     * blocked before the write happens: a self-transfer would create a pair of
     * transactions that cancel out but still clutter the history.
     */
    private void openTransfer() {
        if (wallets.size() < 2) {
            Toast.makeText(this, R.string.wallet_transfer_need_two, Toast.LENGTH_SHORT).show();
            return;
        }
        DialogTransferBinding dialogBinding = DialogTransferBinding.inflate(getLayoutInflater());

        final Wallet[] from = {wallets.get(0)};
        final Wallet[] to = {wallets.get(1)};

        Runnable refresh = () -> {
            dialogBinding.fromValue.setText(NameKeys.resolve(this,
                    from[0].getName(), from[0].getNameKey()));
            dialogBinding.toValue.setText(NameKeys.resolve(this,
                    to[0].getName(), to[0].getNameKey()));
            dialogBinding.amountLayout.setPrefixText(
                    from[0].getCurrency() == null ? baseCurrency : from[0].getCurrency());
        };
        refresh.run();
        AmountInput.attach(dialogBinding.amountEdit,
                () -> from[0].getCurrency() == null ? baseCurrency : from[0].getCurrency());

        // The whole "from" row is the tap target, not just the text.
        ((View) dialogBinding.fromValue.getParent().getParent())
                .setOnClickListener(v -> pickWallet(from, to, refresh, true));
        ((View) dialogBinding.toValue.getParent().getParent())
                .setOnClickListener(v -> pickWallet(from, to, refresh, false));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.wallet_transfer)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.wallet_transfer, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialogBinding.amountLayout.setError(null);
            long amount = AmountInput.parse(this, text(dialogBinding.amountEdit.getText()),
                    from[0].getCurrency() == null ? baseCurrency : from[0].getCurrency());
            if (amount <= 0) {
                dialogBinding.amountLayout.setError(getString(R.string.error_amount_positive));
                return;
            }
            if (from[0].getDocId().equals(to[0].getDocId())) {
                dialogBinding.transferHint.setText(R.string.wallet_transfer_same);
                dialogBinding.transferHint.setTextColor(
                        ContextCompat.getColor(this, R.color.expense));
                return;
            }
            // Hand the current rate to the repository so a cross-currency
            // transfer credits the destination wallet the converted value.
            String sourceCurrency = from[0].getCurrency() == null ? baseCurrency : from[0].getCurrency();
            ExchangeRate rate = rates.get(sourceCurrency);
            repository.transfer(uid(), from[0], to[0], amount,
                    com.google.firebase.Timestamp.now(),
                    text(dialogBinding.noteEdit.getText()),
                    rate,
                    new FinanceRepository.SaveCallback() {
                        @Override
                        public void onSuccess(String id) {
                            Toast.makeText(WalletsActivity.this,
                                    R.string.wallet_transfer_done, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }

                        @Override
                        public void onError(Exception error) {
                            Toast.makeText(WalletsActivity.this,
                                    R.string.save_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
        }));
        dialog.show();
    }

    /** Wallet picker for one side of the transfer. */
    private void pickWallet(Wallet[] from, Wallet[] to, Runnable refresh, boolean isFrom) {
        String[] names = new String[wallets.size()];
        Wallet current = isFrom ? from[0] : to[0];
        int checked = 0;
        for (int i = 0; i < wallets.size(); i++) {
            Wallet wallet = wallets.get(i);
            names[i] = NameKeys.resolve(this, wallet.getName(), wallet.getNameKey());
            if (wallet.getDocId() != null && wallet.getDocId().equals(current.getDocId())) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(isFrom ? R.string.wallet_transfer_from : R.string.wallet_transfer_to)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    if (isFrom) from[0] = wallets.get(which);
                    else to[0] = wallets.get(which);
                    refresh.run();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openEditor(@Nullable Wallet existing) {
        DialogWalletBinding dialogBinding = DialogWalletBinding.inflate(getLayoutInflater());
        boolean isNew = existing == null;

        final String[] type = {existing == null || existing.getType() == null
                ? "cash" : existing.getType()};
        final String[] currency = {existing == null || existing.getCurrency() == null
                ? baseCurrency : existing.getCurrency()};
        final String[] selectedColor = {existing == null || existing.getColorHex() == null
                ? ColorUtil.PALETTE[0] : existing.getColorHex()};
        final boolean locked = existing != null && hasTransactions(existing.getDocId());

        if (!isNew) {
            dialogBinding.nameEdit.setText(
                    NameKeys.resolve(this, existing.getName(), existing.getNameKey()));
            setAmount(dialogBinding.balanceEdit, existing.getInitialBalance(), currency[0]);
        }

        checkType(dialogBinding, type[0]);
        updateCurrencyButton(dialogBinding, currency[0]);
        dialogBinding.balanceLayout.setPrefixText(currency[0]);

        if (locked) {
            dialogBinding.typeCash.setEnabled(false);
            dialogBinding.typeBank.setEnabled(false);
            dialogBinding.typeEwallet.setEnabled(false);
            dialogBinding.currencyButton.setEnabled(false);
            dialogBinding.currencyButton.setAlpha(0.7f);
            dialogBinding.typeHint.setVisibility(View.VISIBLE);
        }

        AmountInput.attach(dialogBinding.balanceEdit, () -> currency[0]);

        dialogBinding.currencyButton.setOnClickListener(v -> {
            final String[] options = new String[Money.SUPPORTED.length];
            for (int i = 0; i < Money.SUPPORTED.length; i++) {
                options[i] = Money.SUPPORTED[i] + "  " + Money.countryFlag(Money.SUPPORTED[i]);
            }
            int checked = 0;
            for (int i = 0; i < Money.SUPPORTED.length; i++) {
                if (Money.SUPPORTED[i].equals(currency[0])) checked = i;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.wallet_currency)
                    .setSingleChoiceItems(options, checked, (choice, which) -> {
                        String previous = currency[0];
                        long amount = AmountInput.parse(this,
                                text(dialogBinding.balanceEdit.getText()), previous);
                        String next = Money.SUPPORTED[which];
                        currency[0] = next;
                        setAmount(dialogBinding.balanceEdit,
                                convertMajor(amount, previous, next), next);
                        dialogBinding.balanceLayout.setPrefixText(currency[0]);
                        updateCurrencyButton(dialogBinding, currency[0]);
                        choice.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isNew ? R.string.wallet_add : R.string.wallet_edit)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            buildColorChoices(dialogBinding, selectedColor);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialogBinding.nameLayout.setError(null);
                String name = text(dialogBinding.nameEdit.getText());
                if (name.isEmpty()) {
                    dialogBinding.nameLayout.setError(getString(R.string.error_name_required));
                    return;
                }
                type[0] = typeFromGroup(dialogBinding);
                long initial = AmountInput.parse(this,
                        text(dialogBinding.balanceEdit.getText()), currency[0]);

                Wallet wallet = isNew ? new Wallet() : existing;
                wallet.setName(name);
                wallet.setNameKey(null);
                wallet.setType(type[0]);
                wallet.setCurrency(currency[0]);
                wallet.setInitialBalance(initial);
                wallet.setColorHex(selectedColor[0]);

                repository.saveWallet(uid(), wallet, isNew, new FinanceRepository.SaveCallback() {
                    @Override
                    public void onSuccess(String id) {
                        toast(getString(R.string.wallet_saved));
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

    private void confirmDelete(Wallet wallet) {
        confirm(getString(R.string.wallet_delete_title), getString(R.string.wallet_delete_msg),
                getString(R.string.delete), () -> repository.deleteWallet(uid(), wallet,
                        new FinanceRepository.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                toast(getString(R.string.wallet_deleted));
                            }

                            @Override
                            public void onError(Exception error) {
                                if (isWalletInUse(error)) {
                                    new MaterialAlertDialogBuilder(WalletsActivity.this)
                                            .setTitle(R.string.wallet_delete_title)
                                            .setMessage(R.string.wallet_in_use)
                                            .setPositiveButton(R.string.ok, null)
                                            .show();
                                } else {
                                    toast(getString(R.string.delete_failed));
                                }
                            }
                        }));
    }

    private boolean hasTransactions(String walletId) {
        if (walletId == null) return false;
        for (Transaction transaction : transactions) {
            if (walletId.equals(transaction.getWalletId())) return true;
        }
        return false;
    }

    private boolean isWalletInUse(Exception error) {
        return error instanceof BaseRepository.RepoException
                && BaseRepository.RepoException.WALLET_IN_USE.equals(
                ((BaseRepository.RepoException) error).getCode());
    }

    private void updateCurrencyButton(DialogWalletBinding dialogBinding, String currency) {
        dialogBinding.currencyButton.setText(currency + "  " + Money.countryFlag(currency));
    }

    private void checkType(DialogWalletBinding dialogBinding, String type) {
        int id = R.id.typeCash;
        if ("bank".equals(type)) id = R.id.typeBank;
        else if ("ewallet".equals(type)) id = R.id.typeEwallet;
        dialogBinding.typeGroup.check(id);
    }

    private String typeFromGroup(DialogWalletBinding dialogBinding) {
        int id = dialogBinding.typeGroup.getCheckedButtonId();
        if (id == R.id.typeBank) return "bank";
        if (id == R.id.typeEwallet) return "ewallet";
        return "cash";
    }

    private void setAmount(android.widget.EditText edit, long amount, String currency) {
        edit.setText(AmountInput.formatMinor(this, currency, amount));
        edit.setSelection(edit.getText() == null ? 0 : edit.getText().length());
    }

    private long convertMajor(long amount, String from, String to) {
        if (from == null || from.equals(to)) return amount;
        return Money.toMinorUnits(to, Money.toMajorUnits(from, amount));
    }

    private void buildColorChoices(DialogWalletBinding dialogBinding, String[] selectedColor) {
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
        drawable.setColor(ColorUtil.parse(hex, ContextCompat.getColor(this, R.color.finance_primary)));
        if (selected) {
            drawable.setStroke(dp(3), ContextCompat.getColor(this, R.color.color_on_surface_variant));
        }
        return drawable;
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
        ListenerRegistration[] all = {walletListener, transactionListener, rateListener, userListener};
        for (ListenerRegistration registration : all) {
            if (registration != null) registration.remove();
        }
    }
}
