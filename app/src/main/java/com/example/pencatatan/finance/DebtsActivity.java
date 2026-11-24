package com.example.pencatatan.finance;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Debt;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.databinding.ActivityDebtsBinding;
import com.example.pencatatan.databinding.DialogDebtBinding;
import com.example.pencatatan.databinding.DialogDebtPaymentBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Debts and receivables in one list.
 *
 * <p>The screen exists because "who owes what" is normally spread across chat
 * history and memory. It leads with the net position, offers per-row partial
 * payments, and keeps settled records visible (greyed) so the user can check
 * that something really was paid off rather than having to trust the memory of
 * deleting it.</p>
 */
public class DebtsActivity extends BaseActivity {

    private ActivityDebtsBinding binding;
    private DebtAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();

    private final List<Debt> debts = new ArrayList<>();
    private ListenerRegistration debtListener;
    private String baseCurrency = "IDR";

    private static final int FILTER_ALL = 0;
    private static final int FILTER_OWE = 1;
    private static final int FILTER_OWED = 2;
    private static final int FILTER_OPEN = 3;
    private int filter = FILTER_ALL;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        baseCurrency = Prefs.getCurrency(this);
        binding = ActivityDebtsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.backButton.setOnClickListener(v -> finish());
        binding.addButton.setOnClickListener(v -> openEditor(null));

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_handshake);
        binding.emptyState.emptyTitle.setText(R.string.debt_empty);
        binding.emptyState.emptyDesc.setText(R.string.debt_empty_desc);
        binding.emptyState.emptyAction.setText(R.string.debt_add);
        binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
        binding.emptyState.emptyAction.setOnClickListener(v -> openEditor(null));

        adapter = new DebtAdapter(new DebtAdapter.Listener() {
            @Override
            public void onOpen(Debt debt) {
                openEditor(debt);
            }

            @Override
            public void onRecordPayment(Debt debt) {
                showRecordPayment(debt);
            }
        });
        adapter.setCurrency(baseCurrency);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.filterOwe) filter = FILTER_OWE;
            else if (id == R.id.filterOwed) filter = FILTER_OWED;
            else if (id == R.id.filterOpen) filter = FILTER_OPEN;
            else filter = FILTER_ALL;
            render();
        });

        listenDebts();
    }

    private void listenDebts() {
        debtListener = repository.listenDebts(uid(), new FinanceRepository.OnDebts() {
            @Override
            public void onData(List<Debt> data) {
                debts.clear();
                if (data != null) debts.addAll(data);
                render();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
            }
        });
    }

    private void render() {
        if (binding == null) return;

        // Totals always come from the full set, not the filtered view: the net
        // position is a fact about the user's money, not about the current tab.
        long owe = 0L;
        long owed = 0L;
        for (Debt debt : debts) {
            if (debt.isSettled()) continue;
            if (debt.isReceivable()) owed += debt.remainingAmount();
            else owe += debt.remainingAmount();
        }
        long net = owed - owe;
        binding.oweAmount.setText(MoneyText.format(this, baseCurrency, owe));
        binding.owedAmount.setText(MoneyText.format(this, baseCurrency, owed));
        binding.netAmount.setText(MoneyText.format(this, baseCurrency, Math.abs(net)));
        binding.netLabel.setText(net > 0 ? R.string.debt_net_positive
                : net < 0 ? R.string.debt_net_negative : R.string.debt_net_balanced);
        binding.netAmount.setTextColor(ContextCompat.getColor(this,
                net > 0 ? R.color.income : net < 0 ? R.color.expense
                        : R.color.color_on_surface));

        List<Debt> visible = new ArrayList<>();
        for (Debt debt : debts) {
            if (filter == FILTER_OWE && debt.isReceivable()) continue;
            if (filter == FILTER_OWED && !debt.isReceivable()) continue;
            if (filter == FILTER_OPEN && debt.isSettled()) continue;
            visible.add(debt);
        }
        adapter.submit(visible);
        binding.resultCount.setText(getResources().getQuantityString(
                R.plurals.plural_debts, visible.size(), visible.size()));

        if (visible.isEmpty()) {
            binding.emptyState.getRoot().setVisibility(View.VISIBLE);
            if (debts.isEmpty()) {
                binding.emptyState.emptyTitle.setText(R.string.debt_empty);
                binding.emptyState.emptyDesc.setText(R.string.debt_empty_desc);
                binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
            } else {
                binding.emptyState.emptyTitle.setText(R.string.debt_empty_filtered);
                binding.emptyState.emptyDesc.setText(R.string.todo_no_results_desc);
                binding.emptyState.emptyAction.setVisibility(View.GONE);
            }
        } else {
            binding.emptyState.getRoot().setVisibility(View.GONE);
        }
    }

    private void openEditor(@Nullable Debt existing) {
        DialogDebtBinding form = DialogDebtBinding.inflate(getLayoutInflater());
        boolean isNew = existing == null;
        form.paidLayout.setVisibility(isNew ? View.VISIBLE : View.GONE);

        if (!isNew) {
            form.directionGroup.check(existing.isReceivable()
                    ? R.id.directionOwed : R.id.directionOwe);
            form.personEdit.setText(existing.getPersonName());
            form.amountEdit.setText(plain(existing.getAmount()));
            form.noteEdit.setText(existing.getNote());
        }

        final Calendar[] due = {existing == null ? null : toCalendar(existing.getDueDate())};
        if (due[0] != null) form.dueSwitch.setChecked(true);
        updateDueButton(form, due[0]);
        form.dueSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) due[0] = null;
            form.dueButton.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateDueButton(form, due[0]);
        });
        form.dueButton.setOnClickListener(v -> {
            if (isFinishing()) return;
            Calendar seed = due[0] == null ? Calendar.getInstance() : due[0];
            new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, day, 0, 0, 0);
                picked.set(Calendar.MILLISECOND, 0);
                due[0] = picked;
                updateDueButton(form, picked);
            }, seed.get(Calendar.YEAR), seed.get(Calendar.MONTH),
                    seed.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isNew ? R.string.debt_add : R.string.debt_edit)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(isNew ? R.string.save : R.string.debt_mark_settled, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.show();
        if (!isNew) {
            // Editing an open debt offers "mark as settled" as the one-tap path.
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                existing.setSettled(true);
                existing.setPaidAmount(existing.getAmount());
                repository.saveDebt(uid(), existing, false, new FinanceRepository.SaveCallback() {
                    @Override
                    public void onSuccess(String id) {
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(Exception error) {
                        toast(getString(R.string.save_failed));
                    }
                });
            });
            // A direction flip is not an edit: it turns "I owe Rp 300.000" into
            // "I am owed Rp 300.000", and the payments already recorded were
            // made in the opposite direction. Deleting and re-adding is the
            // honest way to change it, so the choice is locked here.
            for (int i = 0; i < form.directionGroup.getChildCount(); i++) {
                View child = form.directionGroup.getChildAt(i);
                child.setEnabled(false);
                child.setAlpha(0.6f);
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(View.VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.VISIBLE);
            addDeleteButton(dialog, form, existing);
        } else {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            form.personLayout.setError(null);
            form.amountLayout.setError(null);
            form.paidLayout.setError(null);

            String person = text(form.personEdit.getText());
            if (person.isEmpty()) {
                form.personLayout.setError(getString(R.string.debt_error_person));
                return;
            }
            long amount = parseAmount(form.amountEdit.getText());
            if (amount <= 0) {
                form.amountLayout.setError(getString(R.string.debt_error_amount));
                return;
            }
            long paid = isNew ? parseAmount(form.paidEdit.getText()) : existing.getPaidAmount();
            if (paid > amount) {
                // Paying more than owed is a data-entry mistake, not a refund.
                form.paidLayout.setError(getString(R.string.debt_error_amount));
                return;
            }

            Debt debt = isNew ? new Debt() : existing;
            debt.setDirection(form.directionGroup.getCheckedButtonId() == R.id.directionOwed
                    ? Debt.TYPE_RECEIVABLE : Debt.TYPE_DEBT);
            debt.setPersonName(person);
            debt.setAmount(amount);
            debt.setPaidAmount(paid);
            debt.setSettled(paid >= amount);
            debt.setDueDate(due[0] == null ? null : new Timestamp(due[0].getTime()));
            // Amounts are typed in the app's current base currency. Storing that
            // explicitly keeps old rows readable after a currency change instead
            // of leaving every debt labelled with the hard-coded default.
            debt.setCurrency(baseCurrency);
            String note = text(form.noteEdit.getText());
            debt.setNote(note.isEmpty() ? null : note);

            repository.saveDebt(uid(), debt, isNew, new FinanceRepository.SaveCallback() {
                @Override
                public void onSuccess(String id) {
                    dialog.dismiss();
                }

                @Override
                public void onError(Exception error) {
                    toast(getString(R.string.save_failed));
                }
            });
        });
    }

    /**
     * Partial-payment sheet.
     *
     * <p>Writes through {@code saveDebt} with an accumulated {@code paidAmount},
     * so the record stays a single row the user can reason about. The "pay the
     * rest" shortcut exists because that is the overwhelmingly common case when
     * a debt finally ends.</p>
     */
    private void showRecordPayment(Debt debt) {
        DialogDebtPaymentBinding form = DialogDebtPaymentBinding.inflate(getLayoutInflater());
        form.caption.setText(getString(R.string.debt_pay_caption, debt.getPersonName(),
                MoneyText.format(this, baseCurrency, debt.remainingAmount())));
        form.payRemainingButton.setOnClickListener(v ->
                form.amountEdit.setText(plain(debt.remainingAmount())));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.debt_pay_title)
                .setView(form.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.debt_pay_confirm, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            form.amountLayout.setError(null);
            long payment = parseAmount(form.amountEdit.getText());
            if (payment == 0) {
                form.amountLayout.setError(getString(R.string.debt_error_payment));
                return;
            }
            long updated = debt.getPaidAmount() + payment;
            debt.setPaidAmount(updated);
            debt.setSettled(updated >= debt.getAmount());
            repository.saveDebt(uid(), debt, false, new FinanceRepository.SaveCallback() {
                @Override
                public void onSuccess(String id) {
                    dialog.dismiss();
                }

                @Override
                public void onError(Exception error) {
                    toast(getString(R.string.save_failed));
                }
            });
        });
    }

    /**
     * Adds a delete action for an existing debt.
     *
     * <p>Before this the screen had no delete at all: a debt created by mistake
     * (wrong person, wrong amount) could only be "settled", which keeps a false
     * record in the totals forever. Deleting is the only honest fix for a typo.</p>
     */
    private void addDeleteButton(AlertDialog dialog, DialogDebtBinding form, Debt existing) {
        // The dialog layout has no delete row, so one is added above the buttons
        // using the same text style as the neutral action.
        android.widget.TextView deleteRow = new android.widget.TextView(this);
        deleteRow.setText(R.string.debt_delete);
        deleteRow.setTextColor(ContextCompat.getColor(this, R.color.expense));
        int height = dp(48);
        deleteRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height));
        deleteRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        deleteRow.setPadding(dp(24), 0, dp(24), 0);
        deleteRow.setBackgroundResource(android.R.drawable.list_selector_background);
        deleteRow.setOnClickListener(v -> confirmDeleteDebt(dialog, existing));
        if (form.getRoot() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) form.getRoot()).addView(deleteRow);
        }
    }

    private void confirmDeleteDebt(AlertDialog editor, Debt debt) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.debt_delete_title)
                .setMessage(getString(R.string.debt_delete_msg, debt.getPersonName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        repository.deleteDebt(uid(), debt, new FinanceRepository.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                // Close the editor too: it is now about a document
                                // that no longer exists.
                                if (editor.isShowing()) editor.dismiss();
                            }

                            @Override
                            public void onError(Exception error) {
                                toast(getString(R.string.save_failed));
                            }
                        }))
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateDueButton(DialogDebtBinding form, Calendar due) {
        form.dueButton.setText(due == null ? getString(R.string.debt_due_none)
                : Dates.formatDate(this, due.getTime()));
    }

    @Nullable
    private static Calendar toCalendar(Timestamp stamp) {
        if (stamp == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(stamp.toDate());
        return calendar;
    }

    private static String plain(long amount) {
        return amount <= 0 ? "" : String.valueOf(amount);
    }

    private long parseAmount(CharSequence raw) {
        String cleaned = raw == null ? "" : raw.toString().replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) return 0L;
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    protected void onDestroy() {
        if (debtListener != null) debtListener.remove();
        super.onDestroy();
    }
}
