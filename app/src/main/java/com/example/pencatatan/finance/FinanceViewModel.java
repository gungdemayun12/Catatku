package com.example.pencatatan.finance;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.pencatatan.common.Dates;
import com.example.pencatatan.data.model.Budget;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.model.Wallet;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinanceViewModel extends ViewModel {

    private final FinanceRepository repository = new FinanceRepository();
    private final UserRepository userRepository = new UserRepository();

    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<List<Transaction>> transactions =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Wallet>> wallets = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<FinanceCategory>> categories =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, ExchangeRate>> rates =
            new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<List<Budget>> budgets = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> monthKey =
            new MutableLiveData<>(Dates.monthKey(Calendar.getInstance()));

    private ListenerRegistration userListener;
    private ListenerRegistration transactionListener;
    private ListenerRegistration walletListener;
    private ListenerRegistration categoryListener;
    private ListenerRegistration rateListener;
    private ListenerRegistration budgetListener;
    private String uid;

    public LiveData<User> user() {
        return user;
    }

    public LiveData<List<Transaction>> transactions() {
        return transactions;
    }

    public LiveData<List<Wallet>> wallets() {
        return wallets;
    }

    public LiveData<List<FinanceCategory>> categories() {
        return categories;
    }

    public LiveData<Map<String, ExchangeRate>> rates() {
        return rates;
    }

    public LiveData<List<Budget>> budgets() {
        return budgets;
    }

    public LiveData<String> monthKey() {
        return monthKey;
    }

    public String baseCurrency() {
        User value = user.getValue();
        if (value == null || value.getCurrency() == null) return "IDR";
        return value.getCurrency();
    }

    public void start(String uid) {
        if (uid == null || uid.equals(this.uid)) return;
        stop();
        this.uid = uid;
        userListener = userRepository.listen(uid, new UserRepository.UserListener() {
            @Override
            public void onUser(User data) {
                user.setValue(data);
            }

            @Override
            public void onError(Exception error) {
            }
        });
        transactionListener = repository.listenAllTransactions(uid, new FinanceRepository.OnTransactions() {
            @Override
            public void onData(List<Transaction> data) {
                transactions.setValue(data);
                // Recurring series are caught up here rather than by a server
                // job: this listener already holds every transaction, so the
                // check is free, and the generated copies land in the same
                // snapshot the UI is about to render.
                generateDueRecurrences(data);
            }

            @Override
            public void onError(Exception error) {
            }
        });
        walletListener = repository.listenWallets(uid, new FinanceRepository.OnWallets() {
            @Override
            public void onData(List<Wallet> data) {
                wallets.setValue(data);
            }

            @Override
            public void onError(Exception error) {
            }
        });
        categoryListener = repository.listenCategories(uid, new FinanceRepository.OnFinanceCategories() {
            @Override
            public void onData(List<FinanceCategory> data) {
                categories.setValue(data);
            }

            @Override
            public void onError(Exception error) {
            }
        });
        rateListener = repository.listenRates(uid, new FinanceRepository.OnRates() {
            @Override
            public void onData(Map<String, ExchangeRate> data) {
                rates.setValue(data);
            }

            @Override
            public void onError(Exception error) {
            }
        });
        listenBudgets(monthKey.getValue());
    }

    public void setMonth(String key) {
        if (key == null || key.equals(monthKey.getValue())) return;
        monthKey.setValue(key);
        if (uid != null) listenBudgets(key);
    }

    public void moveMonth(int delta) {
        String current = monthKey.getValue();
        String next = FinanceCalc.nextMonthKey(current == null
                ? Dates.monthKey(Calendar.getInstance()) : current, delta);
        setMonth(next);
    }

    private void listenBudgets(String key) {
        if (budgetListener != null) {
            budgetListener.remove();
            budgetListener = null;
        }
        if (uid == null || key == null) return;
        budgetListener = repository.listenBudgets(uid, key, new FinanceRepository.OnBudgets() {
            @Override
            public void onData(List<Budget> data) {
                budgets.setValue(data);
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void stop() {
        ListenerRegistration[] all = {userListener, transactionListener, walletListener,
                categoryListener, rateListener, budgetListener};
        for (ListenerRegistration registration : all) {
            if (registration != null) registration.remove();
        }
        userListener = null;
        transactionListener = null;
        walletListener = null;
        categoryListener = null;
        rateListener = null;
        budgetListener = null;
        recurrenceChecked.clear();
    }

    /** Ids already examined this session, so a re-emitted snapshot is free. */
    private final java.util.Set<String> recurrenceChecked = new java.util.HashSet<>();

    /**
     * Materialises any recurring transaction whose next date has arrived.
     *
     * <p>Only one template per series is examined: generated copies carry the
     * same {@code repeatGroup} but no {@code repeatNextDate}, so they are
     * skipped naturally. The {@code recurrenceChecked} set keeps a busy
     * listener from re-examining the same row on every snapshot.</p>
     */
    private void generateDueRecurrences(List<Transaction> data) {
        if (data == null || uid == null) return;
        long now = System.currentTimeMillis();
        for (Transaction template : data) {
            if (!template.isRepeating()) continue;
            if (template.getRepeatNextDate() == null) continue;
            if (template.getDocId() == null) continue;
            if (template.getRepeatNextDate().toDate().getTime() > now) continue;
            // Key on id + next date so that after the template is advanced the
            // same series is eligible again for its later occurrences.
            String key = template.getDocId() + "@"
                    + template.getRepeatNextDate().toDate().getTime();
            if (!recurrenceChecked.add(key)) continue;
            RecurringHelper.generateDue(uid, repository, template);
        }
    }

    @Override
    protected void onCleared() {
        stop();
        super.onCleared();
    }
}
