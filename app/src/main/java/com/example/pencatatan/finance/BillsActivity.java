package com.example.pencatatan.finance;

import com.example.pencatatan.common.DialogScroll;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.AmountInput;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Bill;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityBillsBinding;
import com.example.pencatatan.databinding.DialogBillBinding;
import com.example.pencatatan.notification.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BillsActivity extends BaseActivity {

    private ActivityBillsBinding binding;
    private BillAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();
    private final UserRepository userRepository = new UserRepository();

    private final List<Bill> bills = new ArrayList<>();
    private String baseCurrency = "IDR";

    private ListenerRegistration billListener;
    private ListenerRegistration userListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        baseCurrency = Prefs.getCurrency(this);
        binding = ActivityBillsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.backButton.setOnClickListener(v -> finish());
        binding.addButton.setOnClickListener(v -> openEditor(null));

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_receipt);
        binding.emptyState.emptyTitle.setText(R.string.bill_empty);
        binding.emptyState.emptyDesc.setText(R.string.bill_empty_desc);
        binding.emptyState.emptyAction.setText(R.string.bill_add);
        binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
        binding.emptyState.emptyAction.setOnClickListener(v -> openEditor(null));

        adapter = new BillAdapter(new BillAdapter.Listener() {
            @Override
            public void onEdit(Bill bill) {
                openEditor(bill);
            }

            @Override
            public void onDelete(Bill bill) {
                confirmDelete(bill);
            }

            @Override
            public void onToggleActive(Bill bill, boolean active) {
                setActive(bill, active);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.statusFilter.setOnClickListener(v -> chooseStatus());
        binding.sortFilter.setOnClickListener(v -> chooseSort());
        binding.resetFilters.setOnClickListener(v -> {
            statusMode = STATUS_ALL;
            sortMode = SORT_DUE;
            query = "";
            binding.searchEdit.setText("");
            render();
        });
        binding.searchEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                query = s == null ? "" : s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                render();
            }
        });

        listenUser();
        listenBills();
    }

    private static final String STATUS_ALL = "all";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_INACTIVE = "inactive";
    private static final String SORT_DUE = "due";
    private static final String SORT_AMOUNT_DESC = "amount_desc";
    private static final String SORT_AMOUNT_ASC = "amount_asc";
    private static final String SORT_TITLE = "title";

    private String statusMode = STATUS_ALL;
    private String sortMode = SORT_DUE;
    private String query = "";

    /** Applies search + status + sort, then refreshes list and empty states. */
    private void render() {
        if (binding == null) return;
        List<Bill> filtered = new ArrayList<>();
        for (Bill bill : bills) {
            if (STATUS_ACTIVE.equals(statusMode) && !bill.isActive()) continue;
            if (STATUS_INACTIVE.equals(statusMode) && bill.isActive()) continue;
            if (!query.isEmpty()) {
                String title = bill.getTitle() == null ? ""
                        : bill.getTitle().toLowerCase(java.util.Locale.ROOT);
                if (!title.contains(query)) continue;
            }
            filtered.add(bill);
        }
        switch (sortMode) {
            case SORT_AMOUNT_DESC:
                filtered.sort((a, b) -> Long.compare(b.getAmount(), a.getAmount()));
                break;
            case SORT_AMOUNT_ASC:
                filtered.sort((a, b) -> Long.compare(a.getAmount(), b.getAmount()));
                break;
            case SORT_TITLE:
                filtered.sort((a, b) -> {
                    String ta = a.getTitle() == null ? "" : a.getTitle();
                    String tb = b.getTitle() == null ? "" : b.getTitle();
                    return ta.compareToIgnoreCase(tb);
                });
                break;
            default:
                filtered.sort((a, b) -> Long.compare(a.getDueDay(), b.getDueDay()));
                break;
        }

        adapter.submit(filtered);
        binding.resultCount.setText(getResources().getQuantityString(
                R.plurals.plural_bills, filtered.size(), filtered.size()));

        boolean filteredView = !STATUS_ALL.equals(statusMode) || !query.isEmpty()
                || !SORT_DUE.equals(sortMode);
        binding.resetFilters.setVisibility(filteredView ? View.VISIBLE : View.GONE);
        binding.statusFilter.setText(STATUS_ACTIVE.equals(statusMode)
                ? getString(R.string.bill_filter_active)
                : STATUS_INACTIVE.equals(statusMode) ? getString(R.string.bill_filter_inactive)
                : getString(R.string.bill_filter_all));
        binding.sortFilter.setText(sortLabel());

        if (filtered.isEmpty()) {
            binding.emptyState.getRoot().setVisibility(View.VISIBLE);
            if (bills.isEmpty()) {
                binding.emptyState.emptyTitle.setText(R.string.bill_empty);
                binding.emptyState.emptyDesc.setText(R.string.bill_empty_desc);
                binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
            } else {
                binding.emptyState.emptyTitle.setText(R.string.bill_empty_filtered);
                binding.emptyState.emptyDesc.setText(R.string.todo_no_results_desc);
                binding.emptyState.emptyAction.setVisibility(View.GONE);
            }
        } else {
            binding.emptyState.getRoot().setVisibility(View.GONE);
        }
    }

    private String sortLabel() {
        switch (sortMode) {
            case SORT_AMOUNT_DESC:
                return getString(R.string.bill_sort_amount_desc);
            case SORT_AMOUNT_ASC:
                return getString(R.string.bill_sort_amount_asc);
            case SORT_TITLE:
                return getString(R.string.bill_sort_title);
            default:
                return getString(R.string.bill_sort_due);
        }
    }

    private void chooseStatus() {
        String[] modes = {STATUS_ALL, STATUS_ACTIVE, STATUS_INACTIVE};
        String[] labels = {getString(R.string.bill_filter_all),
                getString(R.string.bill_filter_active),
                getString(R.string.bill_filter_inactive)};
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(statusMode)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bill_filter_all)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    statusMode = modes[which];
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseSort() {
        String[] modes = {SORT_DUE, SORT_AMOUNT_DESC, SORT_AMOUNT_ASC, SORT_TITLE};
        String[] labels = {getString(R.string.bill_sort_due),
                getString(R.string.bill_sort_amount_desc),
                getString(R.string.bill_sort_amount_asc),
                getString(R.string.bill_sort_title)};
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(sortMode)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finance_filter_sort)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    sortMode = modes[which];
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void listenUser() {
        userListener = userRepository.listen(uid(), new UserRepository.UserListener() {
            @Override
            public void onUser(User user) {
                if (user != null && user.getCurrency() != null && !user.getCurrency().isEmpty()) {
                    baseCurrency = user.getCurrency();
                    Prefs.setCurrency(BillsActivity.this, baseCurrency);
                }
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void listenBills() {
        billListener = repository.listenBills(uid(), new FinanceRepository.OnBills() {
            @Override
            public void onData(List<Bill> data) {
                bills.clear();
                if (data != null) bills.addAll(data);
                render();
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
            }
        });
    }

    private void openEditor(@Nullable Bill existing) {
        DialogBillBinding dialogBinding = DialogBillBinding.inflate(getLayoutInflater());
        boolean isNew = existing == null;
        final String[] currency = {existing == null || existing.getCurrency() == null
                ? baseCurrency : existing.getCurrency()};

        if (!isNew) {
            dialogBinding.titleEdit.setText(existing.getTitle());
            setAmount(dialogBinding.amountEdit, existing.getAmount(), currency[0]);
            dialogBinding.dueDayEdit.setText(String.valueOf(existing.getDueDay()));
            dialogBinding.daysEdit.setText(String.valueOf(existing.getReminderDaysBefore()));
            dialogBinding.repeatGroup.check("yearly".equals(existing.getRepeatType())
                    ? R.id.repeatYearly : R.id.repeatMonthly);
            dialogBinding.reminderSwitch.setChecked(existing.isReminderEnabled());
        } else {
            dialogBinding.dueDayEdit.setText("1");
            dialogBinding.daysEdit.setText("1");
        }

        dialogBinding.amountLayout.setPrefixText(currency[0]);
        updateCurrencyButton(dialogBinding, currency[0]);
        dialogBinding.daysLayout.setVisibility(
                dialogBinding.reminderSwitch.isChecked() ? View.VISIBLE : View.GONE);
        AmountInput.attach(dialogBinding.amountEdit, () -> currency[0], () -> updatePreview(dialogBinding));

        dialogBinding.reminderSwitch.setOnCheckedChangeListener((button, checked) -> {
            dialogBinding.daysLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
            updatePreview(dialogBinding);
        });
        dialogBinding.repeatGroup.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (checked) updatePreview(dialogBinding);
        });
        AmountInput.SimpleWatcher watcher = new AmountInput.SimpleWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable editable) {
                updatePreview(dialogBinding);
            }
        };
        dialogBinding.dueDayEdit.addTextChangedListener(watcher);
        dialogBinding.daysEdit.addTextChangedListener(watcher);

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
                                text(dialogBinding.amountEdit.getText()), previous);
                        String next = Money.SUPPORTED[which];
                        currency[0] = next;
                        setAmount(dialogBinding.amountEdit,
                                convertMajor(amount, previous, next), next);
                        dialogBinding.amountLayout.setPrefixText(currency[0]);
                        updateCurrencyButton(dialogBinding, currency[0]);
                        updatePreview(dialogBinding);
                        choice.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        updatePreview(dialogBinding);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isNew ? R.string.bill_add : R.string.bill_edit)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    dialogBinding.titleLayout.setError(null);
                    dialogBinding.amountLayout.setError(null);
                    dialogBinding.dueDayLayout.setError(null);
                    dialogBinding.daysLayout.setError(null);

                    String title = text(dialogBinding.titleEdit.getText());
                    if (title.isEmpty()) {
                        dialogBinding.titleLayout.setError(getString(R.string.error_name_required));
                        return;
                    }
                    long amount = AmountInput.parse(this,
                            text(dialogBinding.amountEdit.getText()), currency[0]);
                    if (amount <= 0) {
                        dialogBinding.amountLayout.setError(
                                getString(R.string.error_amount_positive));
                        return;
                    }
                    int dueDay = parseInt(text(dialogBinding.dueDayEdit.getText()), -1);
                    if (dueDay < 1 || dueDay > 31) {
                        dialogBinding.dueDayLayout.setError(getString(R.string.error_due_day));
                        return;
                    }
                    boolean reminder = dialogBinding.reminderSwitch.isChecked();
                    int daysBefore = parseInt(text(dialogBinding.daysEdit.getText()), 1);
                    if (reminder && (daysBefore < 0 || daysBefore > 30)) {
                        dialogBinding.daysLayout.setError(getString(R.string.error_days_before));
                        return;
                    }
                    if (!reminder) daysBefore = 0;

                    /* the previous notification id, kept so the old alarm can be cancelled */ final long[] notificationIdRef = {
                            existing != null && existing.getNotificationId() != null
                                    ? existing.getNotificationId() : 0L };
                    Bill bill = isNew ? new Bill() : existing;
                    bill.setTitle(title);
                    bill.setAmount(amount);
                    bill.setCurrency(currency[0]);
                    bill.setDueDay(dueDay);
                    bill.setRepeatType(dialogBinding.repeatGroup.getCheckedButtonId()
                            == R.id.repeatYearly ? "yearly" : "monthly");
                    // Remember the month so a yearly bill keeps recurring in it.
                    // A new yearly bill has no date picker yet, so the current
                    // month is the only anchor available — but that month must be
                    // derived from the due day relative to today, otherwise a
                    // bill due on the 5th created on the 28th would be anchored a
                    // whole month late on its very first occurrence.
                    if (bill.getDueMonth() < 0) {
                        bill.setDueMonth(defaultDueMonth(dueDay));
                    }
                    bill.setReminderEnabled(reminder);
                    bill.setReminderDaysBefore(daysBefore);
                    bill.setActive(isNew || existing.isActive());
                    if (bill.getNotificationId() == null) {
                        bill.setNotificationId((long) ReminderScheduler.newId());
                    }

                    repository.saveBill(uid(), bill, isNew,
                            new FinanceRepository.SaveCallback() {
                                @Override
                                public void onSuccess(String id) {
                                    bill.setDocId(id);
                                    // An edit changes the trigger time, so the old
                                    // alarm has to go first — otherwise the old
                                    // schedule survives and the user gets a ghost
                                    // reminder (or two) for a bill they just moved.
                                    ReminderScheduler.cancelBill(BillsActivity.this, notificationIdRef[0]);
                                    ReminderScheduler.scheduleBill(BillsActivity.this, uid(), bill);
                                    toast(getString(R.string.bill_saved));
                                    dialog.dismiss();
                                }

                                @Override
                                public void onError(Exception error) {
                                    toast(getString(R.string.save_failed));
                                }
                            });
                }));
        dialog.show();
    }

    private void updatePreview(DialogBillBinding dialogBinding) {
        if (!dialogBinding.reminderSwitch.isChecked()) {
            dialogBinding.nextReminderText.setVisibility(View.GONE);
            return;
        }
        int dueDay = parseInt(text(dialogBinding.dueDayEdit.getText()), 1);
        int daysBefore = parseInt(text(dialogBinding.daysEdit.getText()), 1);
        if (dueDay < 1 || dueDay > 31) {
            dialogBinding.nextReminderText.setVisibility(View.GONE);
            return;
        }
        Bill preview = new Bill();
        preview.setDueDay(dueDay);
        preview.setReminderDaysBefore(Math.max(0, Math.min(30, daysBefore)));
        preview.setRepeatType(dialogBinding.repeatGroup.getCheckedButtonId()
                == R.id.repeatYearly ? "yearly" : "monthly");
        Date next = ReminderScheduler.nextBillDisplay(preview);
        dialogBinding.nextReminderText.setText(getString(R.string.bill_next_due,
                Dates.formatDateTime(this, next)));
        dialogBinding.nextReminderText.setVisibility(View.VISIBLE);
    }

    private void setActive(Bill bill, boolean active) {
        bill.setActive(active);
        // Repaint immediately: the row's switch and its status chip both read
        // from the model, so without this the tap looked like it did nothing
        // until the next Firestore snapshot came back.
        if (adapter != null) adapter.notifyDataSetChanged();
        repository.saveBill(uid(), bill, false, new FinanceRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                if (active) {
                    ReminderScheduler.scheduleBill(BillsActivity.this, uid(), bill);
                } else {
                    ReminderScheduler.cancelBill(BillsActivity.this, bill);
                }
            }

            @Override
            public void onError(Exception error) {
                bill.setActive(!active);
                if (adapter != null) adapter.notifyDataSetChanged();
                toast(getString(R.string.save_failed));
            }
        });
    }

    private void confirmDelete(Bill bill) {
        confirm(getString(R.string.bill_delete_title), getString(R.string.bill_delete_msg),
                getString(R.string.delete), () -> repository.deleteBill(uid(), bill,
                        new FinanceRepository.SaveCallback() {
                            @Override
                            public void onSuccess(String id) {
                                ReminderScheduler.cancelBill(BillsActivity.this, bill);
                                toast(getString(R.string.bill_deleted));
                            }

                            @Override
                            public void onError(Exception error) {
                                toast(getString(R.string.delete_failed));
                            }
                        }));
    }

    private void updateCurrencyButton(DialogBillBinding dialogBinding, String currency) {
        dialogBinding.currencyButton.setText(currency + "  " + Money.countryFlag(currency));
    }

    /**
     * Month a brand-new yearly bill should recur in.
     *
     * <p>If the chosen due day has already passed this month, the first
     * occurrence is next month, so that is the month worth remembering.
     * Otherwise it is the current month.</p>
     */
    static int defaultDueMonth(int dueDay) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int currentMonth = now.get(java.util.Calendar.MONTH);
        int today = now.get(java.util.Calendar.DAY_OF_MONTH);
        if (dueDay >= today) return currentMonth;
        return (currentMonth + 1) % 12;
    }

    private void setAmount(EditText edit, long amount, String currency) {
        edit.setText(AmountInput.formatMinor(this, currency, amount));
        edit.setSelection(edit.getText() == null ? 0 : edit.getText().length());
    }

    private long convertMajor(long amount, String from, String to) {
        if (from == null || from.equals(to)) return amount;
        return Money.toMinorUnits(to, Money.toMajorUnits(from, amount));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billListener != null) billListener.remove();
        if (userListener != null) userListener.remove();
    }
}
