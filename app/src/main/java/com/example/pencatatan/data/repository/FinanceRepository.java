package com.example.pencatatan.data.repository;

import com.example.pencatatan.common.Money;
import com.example.pencatatan.data.model.Bill;
import com.example.pencatatan.data.model.Budget;
import com.example.pencatatan.data.model.Debt;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.SavingsGoal;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.Wallet;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FinanceRepository extends BaseRepository {

    /** Currency every wallet balance is consolidated into. */
    private static final String DEFAULT_BASE_CURRENCY = "IDR";

    /**
     * Base currency, mirrored from the user's own preference.
     *
     * <p>This used to be a hard-coded {@code "IDR"}, so a user who changed the
     * base currency to USD kept getting transfer legs and exchanges converted
     * to IDR. The value is filled by {@link #useBaseCurrency(Context)} when a
     * screen opens, and falls back to IDR when nothing is known.</p>
     */
    private static String baseCurrency = DEFAULT_BASE_CURRENCY;

    /** Tells the repository which currency this session is denominated in. */
    public static void useBaseCurrency(android.content.Context context) {
        if (context == null) return;
        String value = com.example.pencatatan.common.Prefs.getCurrency(context);
        baseCurrency = value == null || value.trim().isEmpty()
                ? DEFAULT_BASE_CURRENCY : value;
    }

    public static String baseCurrency() {
        return baseCurrency;
    }

    public interface OnTransactions {
        void onData(List<Transaction> transactions);

        void onError(Exception error);
    }

    public interface OnWallets {
        void onData(List<Wallet> wallets);

        void onError(Exception error);
    }

    public interface OnFinanceCategories {
        void onData(List<FinanceCategory> categories);

        void onError(Exception error);
    }

    public interface OnBudgets {
        void onData(List<Budget> budgets);

        void onError(Exception error);
    }

    public interface OnRates {
        void onData(Map<String, ExchangeRate> rates);

        void onError(Exception error);
    }

    public interface OnBills {
        void onData(List<Bill> bills);

        void onError(Exception error);
    }

    public interface OnGoals {
        void onData(List<SavingsGoal> goals);

        void onError(Exception error);
    }

    public interface OnGoal {
        void onData(SavingsGoal goal);

        void onError(Exception error);
    }

    public interface OnDebts {
        void onData(List<Debt> debts);

        void onError(Exception error);
    }

    public interface SaveCallback {
        void onSuccess(String id);

        void onError(Exception error);
    }

    public interface OnTransaction {
        void onData(Transaction transaction);

        void onError(Exception error);
    }

    // ---------------------------------------------------------------- transactions

    public ListenerRegistration listenTransactions(String uid, String monthKey, OnTransactions listener) {
        return collection(uid, "transactions")
                .whereEqualTo("monthKey", monthKey)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<Transaction> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx != null) list.add(tx);
                        }
                    }
                    sortTransactions(list);
                    listener.onData(list);
                });
    }

    public ListenerRegistration listenAllTransactions(String uid, OnTransactions listener) {
        return collection(uid, "transactions").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Transaction> list = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    Transaction tx = doc.toObject(Transaction.class);
                    if (tx != null) list.add(tx);
                }
            }
            sortTransactions(list);
            listener.onData(list);
        });
    }

    public void loadTransactionsForMonths(String uid, List<String> monthKeys, OnTransactions listener) {
        collection(uid, "transactions").whereIn("monthKey", monthKeys).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        listener.onError(task.getException());
                        return;
                    }
                    List<Transaction> list = new ArrayList<>();
                    if (task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx != null) list.add(tx);
                        }
                    }
                    sortTransactions(list);
                    listener.onData(list);
                });
    }

    public static void sortTransactions(List<Transaction> list) {
        Collections.sort(list, (a, b) -> {
            Date da = a.getDate() == null ? new Date(0) : a.getDate().toDate();
            Date db = b.getDate() == null ? new Date(0) : b.getDate().toDate();
            return db.compareTo(da);
        });
    }

    public void loadTransaction(String uid, String id, OnTransaction listener) {
        if (id == null) {
            listener.onError(new IllegalArgumentException("id"));
            return;
        }
        collection(uid, "transactions").document(id).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                listener.onError(task.getException());
                return;
            }
            DocumentSnapshot snapshot = task.getResult();
            if (snapshot == null || !snapshot.exists()) {
                listener.onError(new IllegalStateException("missing_transaction"));
                return;
            }
            Transaction tx = snapshot.toObject(Transaction.class);
            if (tx == null) {
                listener.onError(new IllegalStateException("missing_transaction"));
                return;
            }
            listener.onData(tx);
        });
    }

    public void saveTransaction(String uid, Transaction tx, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "transactions").document()
                : collection(uid, "transactions").document(tx.getDocId());
        Map<String, Object> data = new HashMap<>();
        data.put("type", tx.getType());
        data.put("amount", tx.getAmount());
        data.put("currency", tx.getCurrency());
        data.put("exchangeRate", tx.getExchangeRate());
        data.put("amountBase", tx.getAmountBase());
        data.put("walletId", tx.getWalletId());
        data.put("walletName", tx.getWalletName());
        data.put("categoryId", tx.getCategoryId());
        data.put("categoryName", tx.getCategoryName());
        data.put("note", tx.getNote());
        data.put("date", tx.getDate());
        data.put("monthKey", tx.getMonthKey());
        data.put("repeatType", tx.getRepeatType());
        data.put("repeatNextDate", tx.getRepeatNextDate());
        data.put("repeatEndDate", tx.getRepeatEndDate());
        data.put("repeatGroup", tx.getRepeatGroup());
        data.put("repeatAnchorDay", tx.getRepeatAnchorDay());
        data.put("isGenerated", tx.isGenerated());
        data.put("updatedAt", FieldValue.serverTimestamp());
        // Always set createdAt: a generated occurrence is written with a
        // deterministic id (so it is idempotent), which means the very first
        // write must still stamp the creation time.
        data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteTransaction(String uid, Transaction tx, SaveCallback callback) {
        collection(uid, "transactions").document(tx.getDocId()).delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) callback.onSuccess(tx.getDocId());
                    else callback.onError(task.getException());
                });
    }

    /**
     * Moves money between two wallets.
     * <p>
     * A transfer is written as <b>two</b> ordinary transactions in one batch: an
     * expense out of the source wallet and an income into the destination. That
     * keeps a single source of truth — every balance in the app is derived from the
     * transaction list, so introducing a separate "transfer" record would mean a
     * second, parallel balance calculation that could disagree with this one.
     * <p>
     * Both legs share a {@code transferGroup} id so they can be identified (and
     * deleted together) later, and carry no category: a transfer does not change
     * how much was spent, only where it is kept.
     */
    public void transfer(String uid, Wallet from, Wallet to, long amount,
                         Timestamp date, String note, SaveCallback callback) {
        transfer(uid, from, to, amount, date, note, null, callback);
    }

    /**
     * Moves {@code amount} (expressed in the <b>source</b> wallet's currency).
     *
     * <p>When both wallets use different currencies the destination must be
     * credited the converted value, otherwise the receiving wallet grows by the
     * raw number (Rp 1.600.000 arriving in a USD wallet would become
     * 1.600.000 USD). The conversion uses the rate snapshot handed in by the UI;
     * when it is missing a rate of 1 is used which is only correct for
     * same-currency transfers.</p>
     */
    public void transfer(String uid, Wallet from, Wallet to, long amount,
                         Timestamp date, String note, ExchangeRate rate, SaveCallback callback) {
        if (from == null || to == null || from.getDocId() == null || to.getDocId() == null
                || from.getDocId().equals(to.getDocId()) || amount <= 0) {
            callback.onError(new IllegalArgumentException("invalid_transfer"));
            return;
        }
        String fromCurrency = from.getCurrency() == null ? "IDR" : from.getCurrency();
        String toCurrency = to.getCurrency() == null ? "IDR" : to.getCurrency();

        // amountBase keeps the books balanced: the money never leaves the user's
        // own accounts, so both legs carry the same base value. The *incoming*
        // leg's nominal amount is what the destination wallet actually receives.
        double rateToBase = rate == null || rate.getRateToBase() <= 0 ? 0 : rate.getRateToBase();
        long amountBase = rateToBase > 0
                ? Money.toBase(amount, fromCurrency, rateToBase, baseCurrency)
                : amount;
        long incomingAmount = sameCurrency(fromCurrency, toCurrency)
                ? amount
                : convertedAmount(amount, fromCurrency, toCurrency, rateToBase, rate);

        String group = UUID.randomUUID().toString();
        String monthKey = new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                .format(date == null ? new Date() : date.toDate());
        Timestamp when = date == null ? Timestamp.now() : date;

        WriteBatch batch = db.batch();
        DocumentReference outRef = collection(uid, "transactions").document();
        DocumentReference inRef = collection(uid, "transactions").document();

        Map<String, Object> out = new HashMap<>();
        out.put("type", Transaction.EXPENSE);
        out.put("amount", amount);
        out.put("currency", fromCurrency);
        out.put("exchangeRate", rateToBase > 0 ? rateToBase : 1.0);
        out.put("amountBase", amountBase);
        out.put("walletId", from.getDocId());
        out.put("walletName", from.getName());
        out.put("categoryId", null);
        out.put("categoryName", null);
        out.put("note", note);
        out.put("date", when);
        out.put("monthKey", monthKey);
        out.put("transferGroup", group);
        out.put("createdAt", FieldValue.serverTimestamp());
        out.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> in = new HashMap<>(out);
        in.put("type", Transaction.INCOME);
        in.put("amount", incomingAmount);
        in.put("currency", toCurrency);
        // The incoming rate describes the destination currency, not the source.
        in.put("exchangeRate", incomingRate(fromCurrency, toCurrency, amount, incomingAmount));
        in.put("amountBase", amountBase);
        in.put("walletId", to.getDocId());
        in.put("walletName", to.getName());

        batch.set(outRef, out);
        batch.set(inRef, in);
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(outRef.getId());
            else callback.onError(task.getException());
        });
    }

    private static boolean sameCurrency(String a, String b) {
        return com.example.pencatatan.finance.TransferMath.sameCurrency(a, b);
    }

    /**
     * Converts a nominal amount between two currencies.
     *
     * <p>Two rates are involved and confusing them was a real bug: {@code rate}
     * is "1 unit of source = rate units of base", while the destination
     * currency needs its <em>own</em> rate to the base. The previous code passed
     * the source rate as the destination rate for every currency pair where the
     * source was not the base, which quietly produced wildly wrong amounts
     * (e.g. EUR to GBP used the EUR rate for GBP).</p>
     *
     * @param rate      source currency's rate to the base, or ≤ 0 when unknown
     * @param snapshot  the stored rate row for the <em>source</em> currency; its
     *                  {@code toString} is never used, it only signals that a
     *                  live rate set exists
     */
    static long convertedAmount(long amount, String fromCurrency, String toCurrency,
                                double rate, com.example.pencatatan.data.model.ExchangeRate snapshot) {
        double destinationRate = rateForCurrency(toCurrency, snapshot);
        return com.example.pencatatan.finance.TransferMath.convert(amount, fromCurrency,
                toCurrency, rate, destinationRate);
    }

    /**
     * Rate of {@code currency} against the base.
     *
     * <p>The source-currency rate handed in by the caller is not reusable here,
     * so the destination rate comes from the app's built-in reference table.
     * The base currency itself is always 1.</p>
     */
    private static double rateForCurrency(String currency,
                                          com.example.pencatatan.data.model.ExchangeRate ignored) {
        if (currency == null || baseCurrency.equals(currency)) return 1.0;
        double rate = com.example.pencatatan.finance.CurrencyApiService
                .getFallbackRate(currency, baseCurrency);
        return rate > 0 ? rate : 1.0;
    }

    private static double incomingRate(String fromCurrency, String toCurrency,
                                       long amount, long incomingAmount) {
        return com.example.pencatatan.finance.TransferMath.impliedRate(amount, fromCurrency,
                incomingAmount, toCurrency);
    }

    // ---------------------------------------------------------------- wallets

    public ListenerRegistration listenWallets(String uid, OnWallets listener) {
        return collection(uid, "wallets").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Wallet> wallets = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    Wallet wallet = doc.toObject(Wallet.class);
                    if (wallet != null) wallets.add(wallet);
                }
            }
            Collections.sort(wallets, (a, b) -> String.valueOf(a.getName())
                    .compareToIgnoreCase(String.valueOf(b.getName())));
            listener.onData(wallets);
        });
    }

    public void saveWallet(String uid, Wallet wallet, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "wallets").document()
                : collection(uid, "wallets").document(wallet.getDocId());
        Map<String, Object> data = new HashMap<>();
        data.put("name", wallet.getName());
        data.put("nameKey", wallet.getNameKey());
        data.put("type", wallet.getType());
        data.put("currency", wallet.getCurrency());
        data.put("initialBalance", wallet.getInitialBalance());
        data.put("colorHex", wallet.getColorHex());
        if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteWallet(String uid, Wallet wallet, SaveCallback callback) {
        if (wallet == null || wallet.getDocId() == null) {
            callback.onError(new IllegalArgumentException("missing_wallet"));
            return;
        }
        // A wallet that only ever appears in transfer legs is still "in use":
        // deleting it would leave the opposite leg of every transfer pointing at
        // a wallet that no longer exists, and the balances would stop adding up.
        // So the check looks at all transactions, transfers included.
        collection(uid, "transactions").whereEqualTo("walletId", wallet.getDocId()).limit(1).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onError(task.getException());
                        return;
                    }
                    if (task.getResult() != null && !task.getResult().isEmpty()) {
                        callback.onError(new RepoException(RepoException.WALLET_IN_USE));
                        return;
                    }
                    collection(uid, "wallets").document(wallet.getDocId()).delete()
                            .addOnCompleteListener(t -> {
                                if (t.isSuccessful()) callback.onSuccess(wallet.getDocId());
                                else callback.onError(t.getException());
                            });
                });
    }

    /**
     * Deletes both legs of a transfer.
     *
     * <p>Deleting a single leg left the other one behind, so the money appeared
     * to have left one wallet and never arrived anywhere: the total balance
     * silently dropped by the transferred amount. Transfers are one record seen
     * from two sides and must disappear together.</p>
     */
    public void deleteTransfer(String uid, Transaction leg, SaveCallback callback) {
        if (leg == null || leg.getDocId() == null) {
            callback.onError(new IllegalArgumentException("missing_transaction"));
            return;
        }
        String group = leg.getTransferGroup();
        if (group == null || group.isEmpty()) {
            deleteTransaction(uid, leg, callback);
            return;
        }
        collection(uid, "transactions").whereEqualTo("transferGroup", group).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onError(task.getException());
                        return;
                    }
                    WriteBatch batch = db.batch();
                    if (task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                    }
                    if (task.getResult() == null || task.getResult().isEmpty()) {
                        batch.delete(collection(uid, "transactions").document(leg.getDocId()));
                    }
                    batch.commit().addOnCompleteListener(t -> {
                        if (t.isSuccessful()) callback.onSuccess(leg.getDocId());
                        else callback.onError(t.getException());
                    });
                });
    }

    // ---------------------------------------------------------------- categories

    public ListenerRegistration listenCategories(String uid, OnFinanceCategories listener) {
        return collection(uid, "financeCategories").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<FinanceCategory> categories = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    FinanceCategory category = doc.toObject(FinanceCategory.class);
                    if (category != null) categories.add(category);
                }
            }
            Collections.sort(categories, (a, b) -> String.valueOf(a.getName())
                    .compareToIgnoreCase(String.valueOf(b.getName())));
            listener.onData(categories);
        });
    }

    public void saveCategory(String uid, FinanceCategory category, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "financeCategories").document()
                : collection(uid, "financeCategories").document(category.getDocId());
        Map<String, Object> data = new HashMap<>();
        data.put("name", category.getName());
        data.put("nameKey", category.getNameKey());
        data.put("type", category.getType());
        data.put("icon", category.getIcon());
        data.put("colorHex", category.getColorHex());
        if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteCategory(String uid, FinanceCategory category, SaveCallback callback) {
        collection(uid, "transactions").whereEqualTo("categoryId", category.getDocId()).limit(1).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onError(task.getException());
                        return;
                    }
                    if (task.getResult() != null && !task.getResult().isEmpty()) {
                        callback.onError(new RepoException(RepoException.CATEGORY_IN_USE));
                        return;
                    }
                    // A category can also be referenced by a budget row. Deleting
                    // it while that row exists leaves an orphan budget that shows
                    // up as "Rp0 dari Rp500.000" and can no longer be edited.
                    collection(uid, "budgets")
                            .whereEqualTo("categoryId", category.getDocId()).limit(1).get()
                            .addOnCompleteListener(budgetTask -> {
                                if (!budgetTask.isSuccessful()) {
                                    callback.onError(budgetTask.getException());
                                    return;
                                }
                                if (budgetTask.getResult() != null && !budgetTask.getResult().isEmpty()) {
                                    callback.onError(new RepoException(RepoException.CATEGORY_IN_USE));
                                    return;
                                }
                                collection(uid, "financeCategories").document(category.getDocId()).delete()
                                        .addOnCompleteListener(t -> {
                                            if (t.isSuccessful()) callback.onSuccess(category.getDocId());
                                            else callback.onError(t.getException());
                                        });
                            });
                });
    }

    // ---------------------------------------------------------------- budgets

    public ListenerRegistration listenBudgets(String uid, String monthKey, OnBudgets listener) {
        return collection(uid, "budgets")
                .whereEqualTo("monthKey", monthKey)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<Budget> budgets = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Budget budget = doc.toObject(Budget.class);
                            if (budget != null) budgets.add(budget);
                        }
                    }
                    listener.onData(budgets);
                });
    }

    public void saveBudget(String uid, String monthKey, FinanceCategory category, long limit,
                           SaveCallback callback) {
        String id = Budget.idFor(monthKey, category.getDocId());
        DocumentReference ref = collection(uid, "budgets").document(id);
        Map<String, Object> data = new HashMap<>();
        data.put("categoryId", category.getDocId());
        data.put("categoryName", category.getName());
        data.put("monthKey", monthKey);
        data.put("limitAmount", limit);
        ref.set(data, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(id);
            else callback.onError(task.getException());
        });
    }

    public void deleteBudget(String uid, String budgetId, SaveCallback callback) {
        collection(uid, "budgets").document(budgetId).delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(budgetId);
            else callback.onError(task.getException());
        });
    }

    /** Finds the budget for a category/month, used after saving an expense. */
    public void findBudget(String uid, String monthKey, String categoryId, OnBudgets listener) {
        collection(uid, "budgets").document(Budget.idFor(monthKey, categoryId)).get()
                .addOnCompleteListener(task -> {
                    List<Budget> result = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Budget budget = task.getResult().toObject(Budget.class);
                        if (budget != null) result.add(budget);
                    }
                    listener.onData(result);
                });
    }

    // ---------------------------------------------------------------- exchange rates

    public ListenerRegistration listenRates(String uid, OnRates listener) {
        return collection(uid, "exchangeRates").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            Map<String, ExchangeRate> rates = new HashMap<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    ExchangeRate rate = doc.toObject(ExchangeRate.class);
                    if (rate != null) rates.put(doc.getId(), rate);
                }
            }
            listener.onData(rates);
        });
    }

    public void saveRate(String uid, String currency, double rate, SaveCallback callback) {
        if (currency == null || rate <= 0) {
            callback.onError(new IllegalArgumentException("rate"));
            return;
        }
        DocumentReference ref = collection(uid, "exchangeRates").document(currency);
        Map<String, Object> data = new HashMap<>();
        data.put("rateToBase", rate);
        data.put("updatedAt", FieldValue.serverTimestamp());
        ref.set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(currency);
            else callback.onError(task.getException());
        });
    }

    // ---------------------------------------------------------------- bills

    public ListenerRegistration listenBills(String uid, OnBills listener) {
        return collection(uid, "bills").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Bill> bills = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    Bill bill = doc.toObject(Bill.class);
                    if (bill != null) bills.add(bill);
                }
            }
            Collections.sort(bills, (a, b) -> Long.compare(a.getDueDay(), b.getDueDay()));
            listener.onData(bills);
        });
    }

    public void saveBill(String uid, Bill bill, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "bills").document()
                : collection(uid, "bills").document(bill.getDocId());
        Map<String, Object> data = new HashMap<>();
        data.put("title", bill.getTitle());
        data.put("amount", bill.getAmount());
        data.put("currency", bill.getCurrency());
        data.put("dueDay", bill.getDueDay());
        data.put("dueMonth", bill.getDueMonth());
        data.put("repeatType", bill.getRepeatType());
        data.put("reminderEnabled", bill.isReminderEnabled());
        data.put("reminderDaysBefore", bill.getReminderDaysBefore());
        data.put("notificationId", bill.getNotificationId());
        data.put("isActive", bill.isActive());
        if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteBill(String uid, Bill bill, SaveCallback callback) {
        collection(uid, "bills").document(bill.getDocId()).delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(bill.getDocId());
            else callback.onError(task.getException());
        });
    }

    // ------------------------------------------------------- savings goals

    public ListenerRegistration listenGoals(String uid, OnGoals listener) {
        return collection(uid, "savingsGoals").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<SavingsGoal> goals = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    SavingsGoal goal = doc.toObject(SavingsGoal.class);
                    if (goal != null) goals.add(goal);
                }
            }
            // In-progress goals first (closest to the target on top), then the
            // ones already reached, then archived ones last.
            Collections.sort(goals, (a, b) -> {
                if (a.isArchived() != b.isArchived()) return a.isArchived() ? 1 : -1;
                if (a.isReached() != b.isReached()) return a.isReached() ? 1 : -1;
                return Integer.compare(b.progressPercent(), a.progressPercent());
            });
            listener.onData(goals);
        });
    }

    /**
     * Watches a single goal, used by the detail screen so a deposit made from
     * any device (or a second tap) is reflected without a manual refresh.
     */
    public ListenerRegistration listenGoal(String uid, String goalId, OnGoal listener) {
        return collection(uid, "savingsGoals").document(goalId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    listener.onData(snapshot == null ? null
                            : snapshot.toObject(SavingsGoal.class));
                });
    }

    public void saveGoal(String uid, SavingsGoal goal, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "savingsGoals").document()
                : collection(uid, "savingsGoals").document(goal.getDocId());
        Map<String, Object> data = new HashMap<>();
        data.put("title", goal.getTitle());
        data.put("note", goal.getNote());
        data.put("goalType", goal.getGoalType());
        data.put("targetAmount", goal.getTargetAmount());
        data.put("savedAmount", goal.getSavedAmount());
        data.put("targetDate", goal.getTargetDate());
        data.put("walletId", goal.getWalletId());
        data.put("walletName", goal.getWalletName());
        data.put("isArchived", goal.isArchived());
        data.put("contributions", goal.getContributions());
        data.put("updatedAt", FieldValue.serverTimestamp());
        if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteGoal(String uid, SavingsGoal goal, SaveCallback callback) {
        collection(uid, "savingsGoals").document(goal.getDocId()).delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) callback.onSuccess(goal.getDocId());
                    else callback.onError(task.getException());
                });
    }

    /**
     * Adds {@code amount} to a goal's saved total and records one history entry.
     *
     * <p>Uses a Firestore transaction so two quick taps cannot both read the old
     * total and write the same new one — the reader would silently lose a
     * deposit, which is exactly the kind of bug that destroys trust in a savings
     * tracker. A negative amount is allowed so a correction ("I moved it back")
     * is possible; the total is clamped at zero because a negative balance on a
     * savings goal is meaningless.</p>
     */
    public void addToGoal(String uid, String goalId, long amount, String note,
                          SaveCallback callback) {
        if (goalId == null) {
            callback.onError(new IllegalArgumentException("missing_goal"));
            return;
        }
        DocumentReference ref = collection(uid, "savingsGoals").document(goalId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            SavingsGoal goal = snapshot.toObject(SavingsGoal.class);
            if (goal == null) return null;
            long updated = Math.max(0L, goal.getSavedAmount() + amount);
            goal.setSavedAmount(updated);

            List<SavingsGoal.Contribution> history = new ArrayList<>(goal.getContributions());
            history.add(new SavingsGoal.Contribution(amount, Timestamp.now(), note));
            goal.setContributions(history);

            Map<String, Object> data = new HashMap<>();
            data.put("savedAmount", goal.getSavedAmount());
            data.put("contributions", goal.getContributions());
            data.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(ref, data, SetOptions.merge());
            return null;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(goalId);
            else callback.onError(task.getException());
        });
    }

    // ---------------------------------------------------------------- debts

    public ListenerRegistration listenDebts(String uid, OnDebts listener) {
        return collection(uid, "debts").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Debt> debts = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    Debt debt = doc.toObject(Debt.class);
                    if (debt != null) debts.add(debt);
                }
            }
            // Unsettled first; within that, the ones already late float up
            // because they are the only rows that need a decision today.
            Collections.sort(debts, (a, b) -> {
                if (a.isSettled() != b.isSettled()) return a.isSettled() ? 1 : -1;
                if (a.isOverdue() != b.isOverdue()) return a.isOverdue() ? -1 : 1;
                if (a.getDueDate() == null || b.getDueDate() == null) {
                    if (a.getDueDate() != null) return -1;
                    if (b.getDueDate() != null) return 1;
                    return 0;
                }
                return a.getDueDate().compareTo(b.getDueDate());
            });
            listener.onData(debts);
        });
    }

    public void saveDebt(String uid, Debt debt, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "debts").document()
                : collection(uid, "debts").document(debt.getDocId());
        Map<String, Object> data = new HashMap<>();
        data.put("direction", debt.getDirection());
        data.put("personName", debt.getPersonName());
        data.put("amount", debt.getAmount());
        data.put("currency", debt.getCurrency());
        data.put("paidAmount", debt.getPaidAmount());
        data.put("dueDate", debt.getDueDate());
        data.put("note", debt.getNote());
        data.put("walletId", debt.getWalletId());
        data.put("walletName", debt.getWalletName());
        data.put("isSettled", debt.isSettled());
        data.put("updatedAt", FieldValue.serverTimestamp());
        if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteDebt(String uid, Debt debt, SaveCallback callback) {
        collection(uid, "debts").document(debt.getDocId()).delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) callback.onSuccess(debt.getDocId());
                    else callback.onError(task.getException());
                });
    }

    // ---------------------------------------------------------------- helpers

    public void hasAnyTransaction(String uid, final Callback<Boolean> callback) {
        collection(uid, "transactions").limit(1).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onError(task.getException());
                return;
            }
            callback.onResult(task.getResult() != null && !task.getResult().isEmpty());
        });
    }

    public Query transactionsForMonth(String uid, String monthKey) {
        return collection(uid, "transactions").whereEqualTo("monthKey", monthKey);
    }

    /**
     * Converts every base-denominated amount (transactions' amountBase and budgets' limit)
     * plus every stored exchange rate from {@code oldBase} to {@code newBase}. Requires a
     * stored rate for {@code newBase} (meaning "1 unit of newBase = rate oldBase").
     */
    public void migrateBaseCurrency(String uid, String oldBase, String newBase,
                                    MigrateCallback callback) {
        if (uid == null || newBase == null || newBase.equals(oldBase)) {
            callback.onSuccess();
            return;
        }
        collection(uid, "exchangeRates").get().addOnCompleteListener(rateTask -> {
            if (!rateTask.isSuccessful()) {
                callback.onError(rateTask.getException());
                return;
            }
            Map<String, Double> rates = new HashMap<>();
            if (rateTask.getResult() != null) {
                for (DocumentSnapshot doc : rateTask.getResult().getDocuments()) {
                    Double value = doc.getDouble("rateToBase");
                    if (value != null && value > 0) rates.put(doc.getId(), value);
                }
            }
            Double target = rates.get(newBase);
            if (target == null || target <= 0) {
                target = com.example.pencatatan.finance.CurrencyApiService.toBase(newBase, oldBase);
            }
            if (target == null || target <= 0 || Double.isNaN(target) || Double.isInfinite(target)) {
                // Without a rate the conversion would silently multiply every
                // amount by 1 (turning Rp 1.000 into $1.000). Refuse instead.
                callback.onError(new IllegalStateException("rate_missing"));
                return;
            }
            double factor = target;
            collection(uid, "transactions").get().addOnCompleteListener(txTask -> {
                if (!txTask.isSuccessful()) {
                    callback.onError(txTask.getException());
                    return;
                }
                collection(uid, "budgets").get().addOnCompleteListener(budgetTask -> {
                    if (!budgetTask.isSuccessful()) {
                        callback.onError(budgetTask.getException());
                        return;
                    }
                    List<MigrationOp> ops = new ArrayList<>();
                    if (txTask.getResult() != null) {
                        for (DocumentSnapshot doc : txTask.getResult().getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx == null) continue;
                            Map<String, Object> data = new HashMap<>();
                            data.put("amountBase",
                                    convertMinor(oldBase, newBase, tx.getAmountBase(), factor));
                            data.put("exchangeRate", newBase.equals(tx.getCurrency())
                                    ? 1d : tx.getExchangeRate() / factor);
                            data.put("updatedAt", FieldValue.serverTimestamp());
                            ops.add(MigrationOp.update(
                                    collection(uid, "transactions").document(doc.getId()), data));
                        }
                    }
                    if (budgetTask.getResult() != null) {
                        for (DocumentSnapshot doc : budgetTask.getResult().getDocuments()) {
                            Long limit = doc.getLong("limitAmount");
                            if (limit == null) continue;
                            Map<String, Object> data = new HashMap<>();
                            data.put("limitAmount",
                                    convertMinor(oldBase, newBase, limit, factor));
                            ops.add(MigrationOp.update(
                                    collection(uid, "budgets").document(doc.getId()), data));
                        }
                    }
                    for (Map.Entry<String, Double> entry : rates.entrySet()) {
                        if (newBase.equals(entry.getKey())) continue;
                        Map<String, Object> data = new HashMap<>();
                        data.put("rateToBase", entry.getValue() / factor);
                        data.put("updatedAt", FieldValue.serverTimestamp());
                        ops.add(MigrationOp.update(
                                collection(uid, "exchangeRates").document(entry.getKey()), data));
                    }
                    if (!rates.containsKey(oldBase)) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("rateToBase", 1d / factor);
                        data.put("updatedAt", FieldValue.serverTimestamp());
                        ops.add(MigrationOp.update(
                                collection(uid, "exchangeRates").document(oldBase), data));
                    }
                    ops.add(MigrationOp.delete(
                            collection(uid, "exchangeRates").document(newBase)));
                    commitMigration(ops, 0, callback);
                });
            });
        });
    }

    private static long convertMinor(String oldBase, String newBase, long amountBase,
                                     double factor) {
        double major = Money.toMajorUnits(oldBase, amountBase) / factor;
        return Money.toMinorUnits(newBase, major);
    }

    private void commitMigration(List<MigrationOp> ops, int start, MigrateCallback callback) {
        if (start >= ops.size()) {
            callback.onSuccess();
            return;
        }
        WriteBatch batch = db.batch();
        int end = Math.min(start + 400, ops.size());
        for (int i = start; i < end; i++) {
            MigrationOp op = ops.get(i);
            if (op.delete) batch.delete(op.ref);
            else batch.set(op.ref, op.data, SetOptions.merge());
        }
        batch.commit().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onError(task.getException());
                return;
            }
            commitMigration(ops, end, callback);
        });
    }

    public interface Callback<T> {
        void onResult(T value);

        void onError(Exception error);
    }

    public interface MigrateCallback {
        void onSuccess();

        void onError(Exception error);
    }

    private static final class MigrationOp {
        final DocumentReference ref;
        final Map<String, Object> data;
        final boolean delete;

        private MigrationOp(DocumentReference ref, Map<String, Object> data, boolean delete) {
            this.ref = ref;
            this.data = data;
            this.delete = delete;
        }

        static MigrationOp update(DocumentReference ref, Map<String, Object> data) {
            return new MigrationOp(ref, data, false);
        }

        static MigrationOp delete(DocumentReference ref) {
            return new MigrationOp(ref, null, true);
        }
    }
}
