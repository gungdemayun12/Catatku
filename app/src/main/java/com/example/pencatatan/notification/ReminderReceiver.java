package com.example.pencatatan.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.data.model.Bill;
import com.google.firebase.firestore.FirebaseFirestore;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.createChannels(context);
        String kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND);
        String id = intent.getStringExtra(ReminderScheduler.EXTRA_ID);
        String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        String uid = intent.getStringExtra(ReminderScheduler.EXTRA_UID);

        if (ReminderScheduler.KIND_TODO.equals(kind)) {
            NotificationHelper.showTodoReminder(context, id, uid, title);
        } else if (ReminderScheduler.KIND_BILL.equals(kind)) {
            long amount = intent.getLongExtra("amount", 0);
            String currency = intent.getStringExtra("currency");
            int daysBefore = intent.getIntExtra("daysBefore", 1);
            String amountText = MoneyText.format(context,
                    currency == null ? "IDR" : currency, amount);
            NotificationHelper.showBillReminder(context, id, title, amountText, daysBefore);
            scheduleNextOccurrence(context, uid, id);
        }
    }

    /** Repeating bills schedule their next reminder when the current one fires. */
    private void scheduleNextOccurrence(Context context, String uid, String billId) {
        if (uid == null || billId == null) return;
        PendingResult result = goAsync();
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("bills").document(billId)
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            Bill bill = task.getResult().toObject(Bill.class);
                            if (bill != null) {
                                bill.setNotificationId(bill.getNotificationId() == null
                                        ? ReminderScheduler.newId() : bill.getNotificationId());
                                ReminderScheduler.scheduleBill(context, uid, bill);
                            }
                        }
                    } finally {
                        result.finish();
                    }
                });
    }
}
