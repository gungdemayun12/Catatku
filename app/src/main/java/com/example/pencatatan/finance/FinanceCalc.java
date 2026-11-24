package com.example.pencatatan.finance;

import com.example.pencatatan.common.Money;
import com.example.pencatatan.data.model.Bill;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.Wallet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure calculations shared by the finance screens. */
public final class FinanceCalc {

    private FinanceCalc() {
    }

    public static List<Transaction> forMonth(List<Transaction> source, String monthKey) {
        List<Transaction> result = new ArrayList<>();
        if (source == null) return result;
        for (Transaction tx : source) {
            if (monthKey != null && monthKey.equals(tx.getMonthKey())) result.add(tx);
        }
        return result;
    }

    /**
     * Transactions that fall inside an arbitrary inclusive day range, used by the
     * custom-range export. {@code start}'s time is moved to 00:00:00.000 and
     * {@code end}'s to 23:59:59.999 so the whole last day is included — comparing
     * raw timestamps would silently drop everything after midnight on the final
     * day.
     *
     * <p>A null bound means "open ended" on that side. When both bounds are given
     * and {@code start} is after {@code end} the range is treated as swapped
     * rather than returning nothing, which is what a user dragging a range picker
     * backwards expects.</p>
     */
    public static List<Transaction> forRange(List<Transaction> source, Calendar start, Calendar end) {
        List<Transaction> result = new ArrayList<>();
        if (source == null) return result;

        Calendar from = start == null ? null : (Calendar) start.clone();
        Calendar to = end == null ? null : (Calendar) end.clone();
        if (from != null && to != null && from.after(to)) {
            Calendar swap = from;
            from = to;
            to = swap;
        }
        if (from != null) {
            from.set(Calendar.HOUR_OF_DAY, 0);
            from.set(Calendar.MINUTE, 0);
            from.set(Calendar.SECOND, 0);
            from.set(Calendar.MILLISECOND, 0);
        }
        if (to != null) {
            to.set(Calendar.HOUR_OF_DAY, 23);
            to.set(Calendar.MINUTE, 59);
            to.set(Calendar.SECOND, 59);
            to.set(Calendar.MILLISECOND, 999);
        }

        for (Transaction tx : source) {
            if (tx.getDate() == null) continue;
            long time = tx.getDate().toDate().getTime();
            if (from != null && time < from.getTimeInMillis()) continue;
            if (to != null && time > to.getTimeInMillis()) continue;
            result.add(tx);
        }
        return result;
    }

    public static long income(List<Transaction> transactions) {
        long total = 0;
        for (Transaction tx : transactions) {
            // A transfer leg is not income: the money was already the user's, it
            // only changed wallets. Counting it here would inflate the month.
            if (tx.isIncome() && !tx.isTransfer()) total += tx.getAmountBase();
        }
        return total;
    }

    public static long expense(List<Transaction> transactions) {
        long total = 0;
        for (Transaction tx : transactions) {
            // Same rule as income(): a transfer does not change how much was spent.
            if (!tx.isIncome() && !tx.isTransfer()) total += tx.getAmountBase();
        }
        return total;
    }

    public static long spentForCategory(List<Transaction> transactions, String categoryId) {
        long total = 0;
        for (Transaction tx : transactions) {
            if (!tx.isIncome() && !tx.isTransfer()
                    && categoryId != null && categoryId.equals(tx.getCategoryId())) {
                total += tx.getAmountBase();
            }
        }
        return total;
    }

    /** Balance per wallet in the wallet's own currency. */
    public static Map<String, Long> walletBalances(List<Wallet> wallets,
                                                   List<Transaction> transactions) {
        Map<String, Long> result = new HashMap<>();
        if (wallets != null) {
            for (Wallet wallet : wallets) {
                result.put(wallet.getDocId(), wallet.getInitialBalance());
            }
        }
        if (transactions != null) {
            for (Transaction tx : transactions) {
                if (tx.getWalletId() == null) continue;
                Long current = result.get(tx.getWalletId());
                if (current == null) current = 0L;
                result.put(tx.getWalletId(),
                        current + (tx.isIncome() ? tx.getAmount() : -tx.getAmount()));
            }
        }
        return result;
    }

    private static String rateKey(String currency) {
        return currency == null ? "" : currency.toUpperCase(java.util.Locale.US);
    }

    public static long totalBalance(List<Wallet> wallets, List<Transaction> transactions,
                                    Map<String, ExchangeRate> rates, String base) {
        Map<String, Long> balances = walletBalances(wallets, transactions);
        long total = 0;
        if (wallets == null) return 0;
        for (Wallet wallet : wallets) {
            long value = balances.getOrDefault(wallet.getDocId(), 0L);
            if (wallet.getCurrency() == null || wallet.getCurrency().equals(base)) {
                total += value;
                continue;
            }
            ExchangeRate rate = rates == null ? null : rates.get(rateKey(wallet.getCurrency()));
            double rateValue = (rate != null && rate.getRateToBase() > 0)
                    ? rate.getRateToBase()
                    : CurrencyApiService.getFallbackRate(wallet.getCurrency(), base);
            total += Money.toBase(value, wallet.getCurrency(), rateValue, base);
        }
        return total;
    }

    public static boolean missingRate(List<Wallet> wallets, Map<String, ExchangeRate> rates,
                                      String base) {
        if (wallets == null) return false;
        for (Wallet wallet : wallets) {
            if (wallet.getCurrency() == null || wallet.getCurrency().equals(base)) continue;
            ExchangeRate rate = rates == null ? null : rates.get(rateKey(wallet.getCurrency()));
            if (rate == null || rate.getRateToBase() <= 0) return true;
        }
        return false;
    }

    public static boolean hasForeignCurrencyWallet(List<Wallet> wallets, String base) {
        if (wallets == null) return false;
        for (Wallet wallet : wallets) {
            if (wallet.getCurrency() != null && !wallet.getCurrency().equals(base)) return true;
        }
        return false;
    }

    public static String nextMonthKey(String monthKey, int delta) {
        try {
            String[] parts = monthKey.split("-");
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, 1);
            calendar.add(Calendar.MONTH, delta);
            return new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                    .format(calendar.getTime());
        } catch (Exception e) {
            return monthKey;
        }
    }

    public static Calendar monthCalendar(String monthKey) {
        Calendar calendar = Calendar.getInstance();
        try {
            String[] parts = monthKey.split("-");
            calendar.clear();
            calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, 1);
        } catch (Exception ignored) {
        }
        return calendar;
    }

    public static boolean isActiveBill(Bill bill) {
        return bill != null && bill.isActive();
    }

    // ------------------------------------------------------------------ insights

    /**
     * Percent change from {@code previous} to {@code current}, rounded to a whole
     * number. Returns 0 when there is nothing to compare against, so the caller
     * never has to show a meaningless "∞ %" on a brand-new account.
     */
    public static int percentChange(long previous, long current) {
        if (previous == 0) return 0;
        return (int) Math.round((current - previous) * 100.0 / Math.abs(previous));
    }

    /**
     * Number of days in the month that counts as "elapsed" for daily averages:
     * the whole month for a past month, today's day number for the current month,
     * and 1 for a future month (so the divisor is never zero).
     */
    public static int elapsedDays(String monthKey) {
        Calendar today = Calendar.getInstance();
        String currentKey = new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                .format(today.getTime());
        if (monthKey == null) return 1;
        if (monthKey.equals(currentKey)) return Math.max(1, today.get(Calendar.DAY_OF_MONTH));
        Calendar month = monthCalendar(monthKey);
        if (month.before(today)) return month.getActualMaximum(Calendar.DAY_OF_MONTH);
        return 1;
    }

    /** Total spent per category, biggest first. Unresolved ids are skipped. */
    public static Map<String, Long> expenseByCategory(List<Transaction> transactions) {
        Map<String, Long> totals = new HashMap<>();
        if (transactions == null) return totals;
        for (Transaction tx : transactions) {
            if (tx.isIncome() || tx.isTransfer()) continue;
            String key = tx.getCategoryId() == null ? "__none__" : tx.getCategoryId();
            totals.put(key, totals.getOrDefault(key, 0L) + tx.getAmountBase());
        }
        return totals;
    }

    /** The single largest expense in the list, or null when the list is empty. */
    public static Transaction largestExpense(List<Transaction> transactions) {
        Transaction largest = null;
        if (transactions == null) return null;
        for (Transaction tx : transactions) {
            if (tx.isIncome() || tx.isTransfer()) continue;
            if (largest == null || tx.getAmountBase() > largest.getAmountBase()) largest = tx;
        }
        return largest;
    }

    /**
     * Projects the month-end expense from what has been spent so far, assuming the
     * current daily pace holds. A past month projects to its own total.
     */
    public static long projectedExpense(List<Transaction> transactions, String monthKey) {
        long spent = expense(transactions);
        int elapsed = elapsedDays(monthKey);
        int total = monthCalendar(monthKey).getActualMaximum(Calendar.DAY_OF_MONTH);
        if (elapsed >= total) return spent;
        return Math.round(spent * (double) total / elapsed);
    }

    /**
     * Compares one category's spend against the same category in the previous
     * month and returns the biggest increase, or null when nothing grew. Used for
     * the "spending jumped here" insight.
     */
    public static CategoryDelta biggestIncrease(List<Transaction> current,
                                                List<Transaction> previous) {
        Map<String, Long> now = expenseByCategory(current);
        Map<String, Long> before = expenseByCategory(previous);
        CategoryDelta best = null;
        for (Map.Entry<String, Long> entry : now.entrySet()) {
            long previousValue = before.getOrDefault(entry.getKey(), 0L);
            long delta = entry.getValue() - previousValue;
            if (delta <= 0) continue;
            if (best == null || delta > best.delta) {
                best = new CategoryDelta(entry.getKey(), previousValue, entry.getValue(), delta);
            }
        }
        return best;
    }

    /** One category's month-over-month movement. */
    public static final class CategoryDelta {
        public final String categoryId;
        public final long previous;
        public final long current;
        public final long delta;

        CategoryDelta(String categoryId, long previous, long current, long delta) {
            this.categoryId = categoryId;
            this.previous = previous;
            this.current = current;
            this.delta = delta;
        }
    }
}
