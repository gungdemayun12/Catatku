package com.example.pencatatan.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Bill;
import com.example.pencatatan.data.model.Todo;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/** Reschedules future alarms after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (!Prefs.notificationsEnabled(context)) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        PendingResult result = goAsync();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        final int[] pending = {2};
        Runnable finish = () -> {
            pending[0]--;
            if (pending[0] <= 0) result.finish();
        };

        // All todos, not just the open ones: a completed task still carries
        // notification ids, and its stale alarm has to be cancelled explicitly.
        // Querying only open tasks left finished reminders able to fire after a
        // reboot.
        db.collection("users").document(uid).collection("todos")
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                                Todo todo = doc.toObject(Todo.class);
                                if (todo == null) continue;
                                if (todo.isCompleted()) ReminderScheduler.cancelTodo(context, todo);
                                else ReminderScheduler.scheduleTodo(context, uid, todo);
                            }
                        }
                    } finally {
                        finish.run();
                    }
                });

        // Same reasoning for bills: an inactive bill must not keep its alarm.
        db.collection("users").document(uid).collection("bills")
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                                Bill bill = doc.toObject(Bill.class);
                                if (bill == null) continue;
                                if (bill.isActive()) ReminderScheduler.scheduleBill(context, uid, bill);
                                else ReminderScheduler.cancelBill(context, bill);
                            }
                        }
                    } finally {
                        finish.run();
                    }
                });
    }
}
