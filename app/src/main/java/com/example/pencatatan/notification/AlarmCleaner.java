package com.example.pencatatan.notification;

import android.content.Context;

import com.example.pencatatan.data.model.Bill;
import com.example.pencatatan.data.model.Todo;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.atomic.AtomicInteger;

/** Cancels every local alarm for a user, e.g. on logout or when notifications are disabled. */
public final class AlarmCleaner {

    private AlarmCleaner() {
    }

    public interface Callback {
        void onDone();
    }

    public static void cancelAll(Context context, String uid, Callback callback) {
        if (uid == null) {
            callback.onDone();
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        AtomicInteger pending = new AtomicInteger(2);
        Runnable finish = () -> {
            if (pending.decrementAndGet() == 0) callback.onDone();
        };

        db.collection("users").document(uid).collection("todos").get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                                Todo todo = doc.toObject(Todo.class);
                                if (todo != null) ReminderScheduler.cancelTodo(context, todo);
                            }
                        }
                    } finally {
                        finish.run();
                    }
                });

        db.collection("users").document(uid).collection("bills").get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                                Bill bill = doc.toObject(Bill.class);
                                if (bill != null) ReminderScheduler.cancelBill(context, bill);
                            }
                        }
                    } finally {
                        finish.run();
                    }
                });
    }

    /** Schedules every active reminder again, e.g. after notifications are re-enabled. */
    public static void rescheduleAll(Context context, String uid) {
        if (uid == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).collection("todos").get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Todo todo = doc.toObject(Todo.class);
                            if (todo == null) continue;
                            if (todo.isCompleted()) ReminderScheduler.cancelTodo(context, todo);
                            else ReminderScheduler.scheduleTodo(context, uid, todo);
                        }
                    }
                });
        db.collection("users").document(uid).collection("bills").get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Bill bill = doc.toObject(Bill.class);
                            if (bill == null) continue;
                            // An inactive bill has no alarm to re-create, but it
                            // still holds a notification id that must be cleared.
                            if (bill.isActive()) ReminderScheduler.scheduleBill(context, uid, bill);
                            else ReminderScheduler.cancelBill(context, bill);
                        }
                    }
                });
    }
}
