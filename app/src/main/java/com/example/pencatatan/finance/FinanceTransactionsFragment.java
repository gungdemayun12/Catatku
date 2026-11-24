package com.example.pencatatan.finance;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.FabPadding;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.model.Wallet;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.databinding.FragmentFinanceTransactionsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FinanceTransactionsFragment extends Fragment {

    private FragmentFinanceTransactionsBinding binding;
    private FinanceViewModel viewModel;
    private TransactionAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();

    private final List<Transaction> all = new ArrayList<>();
    private final List<FinanceCategory> categories = new ArrayList<>();
    private final List<Wallet> wallets = new ArrayList<>();

    private String type = "all";
    private String categoryId;
    private String walletId;
    private String query = "";
    private String baseCurrency = "IDR";

    /** Advanced filters. */
    private static final int DATE_ANY = 0;
    private static final int DATE_TODAY = 1;
    private static final int DATE_7 = 2;
    private static final int DATE_30 = 3;
    private static final int DATE_CUSTOM = 4;
    private static final String SORT_DATE_DESC = "date_desc";
    private static final String SORT_DATE_ASC = "date_asc";
    private static final String SORT_AMOUNT_DESC = "amount_desc";
    private static final String SORT_AMOUNT_ASC = "amount_asc";

    private int dateMode = DATE_ANY;
    private Date customStart;
    private Date customEnd;
    private long minAmount = -1;
    private long maxAmount = -1;
    private String sortMode = SORT_DATE_DESC;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFinanceTransactionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(FinanceViewModel.class);

        adapter = new TransactionAdapter(new TransactionAdapter.Listener() {
            @Override
            public void onOpen(Transaction transaction) {
                openForm(transaction.getDocId());
            }

            @Override
            public void onLongPress(Transaction transaction) {
                confirmDelete(transaction, -1);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        FabPadding.apply(binding.recycler);
        attachSwipeToDelete();

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_empty_finance);
        binding.emptyState.emptyAction.setText(R.string.finance_add_first_cta);
        binding.emptyState.emptyAction.setOnClickListener(v -> openForm(null));

        binding.monthPrev.setOnClickListener(v -> viewModel.moveMonth(-1));
        binding.monthNext.setOnClickListener(v -> viewModel.moveMonth(1));

        binding.typeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.filterIncome) type = Transaction.INCOME;
            else if (id == R.id.filterExpense) type = Transaction.EXPENSE;
            else type = "all";
            apply();
        });

        binding.categoryFilter.setOnClickListener(v -> chooseCategory());
        binding.walletFilter.setOnClickListener(v -> chooseWallet());
        binding.dateFilter.setOnClickListener(v -> chooseDateRange());
        binding.amountFilter.setOnClickListener(v -> chooseAmountRange());
        binding.sortFilter.setOnClickListener(v -> chooseSort());
        binding.resetFilters.setOnClickListener(v -> resetFilters());
        binding.searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.getDefault());
                apply();
            }
        });

        viewModel.user().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                baseCurrency = user.getCurrency() == null ? "IDR" : user.getCurrency();
            }
            apply();
        });
        viewModel.transactions().observe(getViewLifecycleOwner(), data -> {
            all.clear();
            if (data != null) all.addAll(data);
            apply();
        });
        viewModel.categories().observe(getViewLifecycleOwner(), data -> {
            categories.clear();
            if (data != null) categories.addAll(data);
            adapter.setCategories(data);
            apply();
        });
        viewModel.wallets().observe(getViewLifecycleOwner(), data -> {
            wallets.clear();
            if (data != null) wallets.addAll(data);
            apply();
        });
        viewModel.monthKey().observe(getViewLifecycleOwner(), key -> apply());
    }

    private void apply() {
        if (binding == null) return;
        String monthKey = viewModel.monthKey().getValue();
        binding.monthText.setText(Dates.formatMonth(requireContext(),
                FinanceCalc.monthCalendar(monthKey)));

        List<Transaction> monthly = FinanceCalc.forMonth(all, monthKey);

        // The filter is applied first so the totals in the header always describe
        // the same rows the list below shows. Previously they were computed from
        // the whole month, so a filtered view could read "Pemasukan 0" while the
        // list clearly showed income.
        List<Transaction> filtered = new ArrayList<>();
        for (Transaction transaction : monthly) {
            if (!"all".equals(type) && !type.equals(transaction.getType())) continue;
            if (categoryId != null && !categoryId.equals(transaction.getCategoryId())) continue;
            if (walletId != null && !walletId.equals(transaction.getWalletId())) continue;
            if (!query.isEmpty() && !matches(transaction)) continue;
            if (!withinDateRange(transaction)) continue;
            if (minAmount >= 0 && transaction.getAmountBase() < minAmount) continue;
            if (maxAmount >= 0 && transaction.getAmountBase() > maxAmount) continue;
            filtered.add(transaction);
        }

        long income = FinanceCalc.income(filtered);
        long expense = FinanceCalc.expense(filtered);
        long difference = income - expense;
        binding.incomeValue.setText(MoneyText.format(requireContext(), baseCurrency, income));
        binding.expenseValue.setText(MoneyText.format(requireContext(), baseCurrency, expense));
        binding.differenceValue.setText(MoneyText.formatSigned(requireContext(), baseCurrency,
                difference, difference >= 0));
        binding.differenceValue.setTextColor(ContextCompat.getColor(requireContext(),
                difference >= 0 ? R.color.income : R.color.expense));

        // Say out loud what the totals cover, otherwise a filtered sum can look
        // like a broken balance.
        boolean filtersActive = hasActiveFilters();
        if (binding.summaryScope != null) {
            binding.summaryScope.setText(filtersActive
                    ? R.string.finance_summary_filtered
                    : R.string.finance_summary_month);
            binding.summaryScope.setVisibility(View.VISIBLE);
        }

        sortTransactions(filtered);
        adapter.setBaseCurrency(baseCurrency);
        adapter.submit(filtered);
        binding.resultCount.setText(getResources().getQuantityString(R.plurals.plural_transactions,
                filtered.size(), filtered.size()));

        updateFilterChips();

        if (filtered.isEmpty()) {
            boolean filteredView = hasActiveFilters();
            boolean noDataAtAll = monthly.isEmpty() && !filteredView;
            int controlsVisibility = noDataAtAll ? View.GONE : View.VISIBLE;
            binding.searchLayout.setVisibility(controlsVisibility);
            binding.filterScroll.setVisibility(controlsVisibility);
            binding.filterRow.setVisibility(controlsVisibility);
            binding.advancedRow.setVisibility(controlsVisibility);
            binding.emptyState.getRoot().setVisibility(View.VISIBLE);
            if (noDataAtAll) {
                binding.emptyState.emptyTitle.setText(R.string.finance_no_transactions);
                binding.emptyState.emptyDesc.setText(R.string.finance_add_first);
                binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
            } else {
                binding.emptyState.emptyTitle.setText(R.string.finance_empty_filtered_title);
                binding.emptyState.emptyDesc.setText(R.string.finance_empty_filtered_desc);
                binding.emptyState.emptyAction.setVisibility(View.GONE);
            }
        } else {
            binding.searchLayout.setVisibility(View.VISIBLE);
            binding.filterScroll.setVisibility(View.VISIBLE);
            binding.filterRow.setVisibility(View.VISIBLE);
            binding.advancedRow.setVisibility(View.VISIBLE);
            binding.emptyState.getRoot().setVisibility(View.GONE);
        }
    }

    private boolean withinDateRange(Transaction transaction) {
        if (dateMode == DATE_ANY) return true;
        Date date = transaction.getDate() == null ? null : transaction.getDate().toDate();
        if (date == null) return false;
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        switch (dateMode) {
            case DATE_TODAY:
                return Dates.isSameDay(Dates.calendarOf(date), Calendar.getInstance());
            case DATE_7:
                start.add(Calendar.DAY_OF_MONTH, -6);
                break;
            case DATE_30:
                start.add(Calendar.DAY_OF_MONTH, -29);
                break;
            case DATE_CUSTOM:
                if (customStart == null || customEnd == null) return true;
                return !date.before(customStart) && !date.after(customEnd);
            default:
                return true;
        }
        return !date.before(Dates.startOfDay(start).getTime())
                && !date.after(Dates.endOfDay(end).getTime());
    }

    private void sortTransactions(List<Transaction> list) {
        Comparator<Transaction> comparator;
        switch (sortMode) {
            case SORT_DATE_ASC:
                comparator = Comparator.comparingLong(FinanceTransactionsFragment::timeOf);
                break;
            case SORT_AMOUNT_DESC:
                comparator = (a, b) -> Long.compare(b.getAmountBase(), a.getAmountBase());
                break;
            case SORT_AMOUNT_ASC:
                comparator = (a, b) -> Long.compare(a.getAmountBase(), b.getAmountBase());
                break;
            default:
                comparator = (a, b) -> Long.compare(timeOf(b), timeOf(a));
                break;
        }
        list.sort(comparator);
    }

    private static long timeOf(Transaction transaction) {
        return transaction.getDate() == null ? 0L : transaction.getDate().toDate().getTime();
    }

    private boolean hasActiveFilters() {
        return !"all".equals(type) || categoryId != null || walletId != null
                || !query.isEmpty() || dateMode != DATE_ANY
                || minAmount >= 0 || maxAmount >= 0
                || !SORT_DATE_DESC.equals(sortMode);
    }

    private void updateFilterChips() {
        int active = 0;
        if (!"all".equals(type)) active++;
        if (categoryId != null) active++;
        if (walletId != null) active++;
        if (dateMode != DATE_ANY) active++;
        if (minAmount >= 0 || maxAmount >= 0) active++;
        if (!query.isEmpty()) active++;
        if (!SORT_DATE_DESC.equals(sortMode)) active++;

        binding.activeFilterBar.setVisibility(active > 0 ? View.VISIBLE : View.GONE);
        if (active > 0) {
            binding.activeFilterText.setText(getString(R.string.finance_filter_active, active));
        }

        binding.dateFilter.setText(dateMode == DATE_ANY
                ? getString(R.string.finance_filter_date) : dateLabel());
        binding.amountFilter.setText(minAmount >= 0 || maxAmount >= 0
                ? getString(R.string.finance_filter_amount_label,
                minAmount >= 0 ? MoneyText.format(requireContext(), baseCurrency, minAmount)
                        : "0",
                maxAmount >= 0 ? MoneyText.format(requireContext(), baseCurrency, maxAmount)
                        : "\u221E")
                : getString(R.string.finance_filter_amount));
        binding.sortFilter.setText(sortLabel(sortMode));
    }

    private String sortLabel(String mode) {
        switch (mode) {
            case SORT_DATE_ASC:
                return getString(R.string.finance_filter_sort_date_asc);
            case SORT_AMOUNT_DESC:
                return getString(R.string.finance_filter_sort_amount_desc);
            case SORT_AMOUNT_ASC:
                return getString(R.string.finance_filter_sort_amount_asc);
            default:
                return getString(R.string.finance_filter_sort_date_desc);
        }
    }

    private String dateLabel() {
        switch (dateMode) {
            case DATE_TODAY:
                return getString(R.string.finance_filter_date_today);
            case DATE_7:
                return getString(R.string.finance_filter_date_7);
            case DATE_30:
                return getString(R.string.finance_filter_date_30);
            case DATE_CUSTOM:
                if (customStart != null && customEnd != null) {
                    return getString(R.string.finance_filter_range_label,
                            Dates.formatShortDate(requireContext(), customStart),
                            Dates.formatShortDate(requireContext(), customEnd));
                }
                return getString(R.string.finance_filter_date_custom);
            default:
                return getString(R.string.finance_filter_date_any);
        }
    }

    private void chooseDateRange() {
        String[] labels = {
                getString(R.string.finance_filter_date_any),
                getString(R.string.finance_filter_date_today),
                getString(R.string.finance_filter_date_7),
                getString(R.string.finance_filter_date_30),
                getString(R.string.finance_filter_date_custom)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_filter_date)
                .setSingleChoiceItems(labels, dateMode, (dialog, which) -> {
                    if (which == DATE_CUSTOM) {
                        dialog.dismiss();
                        pickCustomRange();
                        return;
                    }
                    dateMode = which;
                    dialog.dismiss();
                    apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickCustomRange() {
        Calendar today = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar start = Calendar.getInstance();
            start.set(year, month, day, 0, 0, 0);
            start.set(Calendar.MILLISECOND, 0);
            new DatePickerDialog(requireContext(), (endView, endYear, endMonth, endDay) -> {
                Calendar end = Calendar.getInstance();
                end.set(endYear, endMonth, endDay, 23, 59, 59);
                end.set(Calendar.MILLISECOND, 999);
                if (end.before(start)) {
                    Date tmp = start.getTime();
                    customStart = end.getTime();
                    customEnd = tmp;
                } else {
                    customStart = start.getTime();
                    customEnd = end.getTime();
                }
                dateMode = DATE_CUSTOM;
                apply();
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH))
                    .show();
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void chooseAmountRange() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);

        TextInputLayout minLayout = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        minLayout.setHint(getString(R.string.finance_filter_amount_min));
        TextInputEditText minEdit = new TextInputEditText(requireContext());
        minEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (minAmount > 0) {
            minEdit.setText(String.valueOf(Money.toMajorUnits(baseCurrency, minAmount)));
        }
        minLayout.addView(minEdit);
        container.addView(minLayout);

        TextInputLayout maxLayout = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        maxLayout.setHint(getString(R.string.finance_filter_amount_max));
        TextInputEditText maxEdit = new TextInputEditText(requireContext());
        maxEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (maxAmount > 0) {
            maxEdit.setText(String.valueOf(Money.toMajorUnits(baseCurrency, maxAmount)));
        }
        maxLayout.addView(maxEdit);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = pad / 2;
        container.addView(maxLayout, params);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_filter_amount)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.finance_filter_reset, (dialog, which) -> {
                    minAmount = -1;
                    maxAmount = -1;
                    apply();
                })
                .setPositiveButton(R.string.finance_filter_amount_apply, (dialog, which) -> {
                    minAmount = parseMinor(minEdit.getText() == null ? ""
                            : minEdit.getText().toString());
                    maxAmount = parseMinor(maxEdit.getText() == null ? ""
                            : maxEdit.getText().toString());
                    apply();
                })
                .show();
    }

    /**
     * Parses a user-typed amount into minor units.
     *
     * <p>Accepts decimals (e.g. {@code 1500.50}) and locale separators, because
     * currencies like USD need them — the previous integer-only parse silently
     * turned "1500.50" into 150050. Returns -1 when the text is not a number.</p>
     */
    private long parseMinor(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.isEmpty()) return -1;
        // Keep digits and the last separator, which is the decimal one.
        String digitsOnly = cleaned.replaceAll("[^0-9.,]", "");
        if (digitsOnly.isEmpty()) return -1;
        int lastSeparator = Math.max(digitsOnly.lastIndexOf(','), digitsOnly.lastIndexOf('.'));
        String normalised;
        if (lastSeparator < 0) {
            normalised = digitsOnly;
        } else {
            String whole = digitsOnly.substring(0, lastSeparator)
                    .replace(".", "").replace(",", "");
            String fraction = digitsOnly.substring(lastSeparator + 1)
                    .replace(".", "").replace(",", "");
            normalised = whole + (fraction.isEmpty() ? "" : "." + fraction);
        }
        try {
            double major = Double.parseDouble(normalised);
            if (major < 0) return -1;
            return Money.toMinorUnits(baseCurrency, major);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private void chooseSort() {
        String[] modes = {SORT_DATE_DESC, SORT_DATE_ASC, SORT_AMOUNT_DESC, SORT_AMOUNT_ASC};
        String[] labels = {getString(R.string.finance_filter_sort_date_desc),
                getString(R.string.finance_filter_sort_date_asc),
                getString(R.string.finance_filter_sort_amount_desc),
                getString(R.string.finance_filter_sort_amount_asc)};
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(sortMode)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_filter_sort)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    sortMode = modes[which];
                    dialog.dismiss();
                    apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void resetFilters() {
        type = "all";
        categoryId = null;
        walletId = null;
        query = "";
        dateMode = DATE_ANY;
        customStart = null;
        customEnd = null;
        minAmount = -1;
        maxAmount = -1;
        sortMode = SORT_DATE_DESC;
        binding.typeGroup.check(R.id.filterAll);
        binding.searchEdit.setText("");
        binding.categoryFilter.setText(R.string.finance_filter_category);
        binding.walletFilter.setText(R.string.finance_filter_wallet);
        apply();
        Toast.makeText(requireContext(), R.string.finance_filter_clear_done,
                Toast.LENGTH_SHORT).show();
    }

    private boolean matches(Transaction transaction) {
        String note = transaction.getNote() == null ? "" : transaction.getNote().toLowerCase(Locale.getDefault());
        String category = transaction.getCategoryName() == null ? ""
                : transaction.getCategoryName().toLowerCase(Locale.getDefault());
        String wallet = transaction.getWalletName() == null ? ""
                : transaction.getWalletName().toLowerCase(Locale.getDefault());
        return note.contains(query) || category.contains(query) || wallet.contains(query);
    }

    private void chooseCategory() {
        final List<String> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        ids.add(null);
        names.add(getString(R.string.filter_all));
        for (FinanceCategory category : categories) {
            if (category.getDocId() == null) continue;
            ids.add(category.getDocId());
            names.add(NameKeys.resolve(requireContext(), category.getName(), category.getNameKey()));
        }
        int checked = categoryId == null ? 0 : Math.max(0, ids.indexOf(categoryId));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_filter_category)
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (dialog, which) -> {
                    categoryId = ids.get(which);
                    binding.categoryFilter.setText(which == 0
                            ? getString(R.string.finance_filter_category) : names.get(which));
                    dialog.dismiss();
                    apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseWallet() {
        final List<String> ids = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        ids.add(null);
        names.add(getString(R.string.filter_all));
        for (Wallet wallet : wallets) {
            if (wallet.getDocId() == null) continue;
            ids.add(wallet.getDocId());
            names.add(NameKeys.resolve(requireContext(), wallet.getName(), wallet.getNameKey()));
        }
        int checked = walletId == null ? 0 : Math.max(0, ids.indexOf(walletId));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finance_filter_wallet)
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (dialog, which) -> {
                    walletId = ids.get(which);
                    binding.walletFilter.setText(which == 0
                            ? getString(R.string.finance_filter_wallet) : names.get(which));
                    dialog.dismiss();
                    apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openForm(@Nullable String transactionId) {
        Intent intent = new Intent(requireContext(), TransactionFormActivity.class);
        if (transactionId != null) {
            intent.putExtra(TransactionFormActivity.EXTRA_TRANSACTION_ID, transactionId);
        }
        startActivity(intent);
    }

    private void attachSwipeToDelete() {
        final Drawable background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_swipe_delete);
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                if (adapter.transactionAt(viewHolder.getBindingAdapterPosition()) == null) {
                    return 0;
                }
                return makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (Prefs.hapticEnabled(requireContext())) {
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                int position = viewHolder.getBindingAdapterPosition();
                Transaction transaction = adapter.transactionAt(position);
                if (transaction == null) {
                    adapter.notifyItemChanged(position);
                    return;
                }
                confirmDelete(transaction, position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && background != null) {
                    View item = viewHolder.itemView;
                    background.setBounds(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
                    background.draw(canvas);
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });
        helper.attachToRecyclerView(binding.recycler);
    }

    private void confirmDelete(Transaction transaction, int position) {
        // A transfer is stored as two rows. Saying so avoids surprising the user
        // when the opposite leg disappears from the other wallet too.
        int message = transaction.isTransfer()
                ? R.string.transfer_delete_msg : R.string.transaction_delete_msg;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.transaction_delete_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> resetSwipe(position))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    String uid = currentUid();
                    if (uid == null) return;
                    // deleteTransfer() removes both legs when this row is one.
                    repository.deleteTransfer(uid, transaction, new FinanceRepository.SaveCallback() {
                        @Override
                        public void onSuccess(String id) {
                            if (isAdded()) {
                                android.widget.Toast.makeText(requireContext(),
                                        R.string.transaction_deleted,
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            if (isAdded()) {
                                android.widget.Toast.makeText(requireContext(),
                                        R.string.delete_failed,
                                        android.widget.Toast.LENGTH_SHORT).show();
                                resetSwipe(position);
                            }
                        }
                    });
                })
                .setOnCancelListener(dialog -> resetSwipe(position))
                .show();
    }

    private void resetSwipe(int position) {
        if (position >= 0 && adapter != null) adapter.notifyItemChanged(position);
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
