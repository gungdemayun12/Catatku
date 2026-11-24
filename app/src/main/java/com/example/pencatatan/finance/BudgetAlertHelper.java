package com.example.pencatatan.finance;

import android.content.Context;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Budget;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.notification.NotificationHelper;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

/** Sends one 80% and one 100% budget notification per category and month. */
public final class BudgetAlertHelper {

    private BudgetAlertHelper() {
    }

    public static void check(Context context, String uid, Transaction transaction) {
        if (context == null || uid == null || transaction == null || transaction.isIncome()) return;
        if (transaction.getCategoryId() == null || transaction.getMonthKey() == null) return;
        if (!Prefs.notificationsEnabled(context)) return;
        if (!Prefs.budgetAlertsEnabled(context)) return;

        final Context app = context.getApplicationContext();
        FinanceRepository repository = new FinanceRepository();
        final String monthKey = transaction.getMonthKey();
        final String categoryId = transaction.getCategoryId();

        repository.findBudget(uid, monthKey, categoryId, new FinanceRepository.OnBudgets() {
            @Override
            public void onData(List<Budget> budgets) {
                if (budgets == null || budgets.isEmpty()) return;
                final Budget budget = budgets.get(0);
                if (budget.getLimitAmount() <= 0) return;

                repository.transactionsForMonth(uid, monthKey).get()
                        .addOnCompleteListener(task -> {
                            if (!task.isSuccessful() || task.getResult() == null) return;
                            long spent = 0;
                            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                                Transaction saved = doc.toObject(Transaction.class);
                                if (saved == null || saved.isIncome()) continue;
                                if (!categoryId.equals(saved.getCategoryId())) continue;
                                spent += saved.getAmountBase();
                            }
                            if (spent <= 0) return;
                            long percentValue = spent * 100 / budget.getLimitAmount();
                            int percent = (int) Math.min(999, percentValue);
                            String name = budget.getCategoryName() == null
                                    || budget.getCategoryName().trim().isEmpty()
                                    ? app.getString(R.string.finance_expense)
                                    : budget.getCategoryName();

                            if (percentValue >= 100) {
                                alert(app, monthKey, categoryId, name, 100);
                            } else if (percentValue >= 80) {
                                alert(app, monthKey, categoryId, name, percent);
                            }
                        });
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    /**
     * Shows the alert and only then remembers it was shown.
     *
     * <p>The order matters: if the flag were written first and the notification
     * failed (permission revoked, notifications off at system level), that
     * threshold would stay silently blocked for the rest of the month.</p>
     */
    private static void alert(Context app, String monthKey, String categoryId,
                              String name, int percent) {
        if (Prefs.hasAlertedBudget(app, monthKey, categoryId, percent >= 100 ? 100 : 80)) return;
        boolean shown = NotificationHelper.showBudgetAlert(app, categoryId, name, percent);
        if (!shown) return;
        // Claim the threshold only after a successful post; this also keeps the
        // gate idempotent when two transactions land at the same moment.
        Prefs.shouldAlertBudget(app, monthKey, categoryId, percent >= 100 ? 100 : 80);
    }
}
