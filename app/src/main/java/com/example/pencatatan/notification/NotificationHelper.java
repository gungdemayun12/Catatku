package com.example.pencatatan.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.pencatatan.R;
import com.example.pencatatan.finance.BillsActivity;
import com.example.pencatatan.finance.FinanceActivity;
import com.example.pencatatan.todo.TodoDetailActivity;

public final class NotificationHelper {

    public static final String CHANNEL_TODO = "todo_reminders";
    public static final String CHANNEL_FINANCE = "bills_budget";
    public static final String KIND_TODO = "todo";
    public static final String KIND_BILL = "bill";

    private NotificationHelper() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel todo = new NotificationChannel(
                CHANNEL_TODO,
                context.getString(R.string.channel_todo_name),
                NotificationManager.IMPORTANCE_HIGH);
        todo.setDescription(context.getString(R.string.channel_todo_desc));
        todo.enableVibration(true);
        manager.createNotificationChannel(todo);

        NotificationChannel finance = new NotificationChannel(
                CHANNEL_FINANCE,
                context.getString(R.string.channel_finance_name),
                NotificationManager.IMPORTANCE_HIGH);
        finance.setDescription(context.getString(R.string.channel_finance_desc));
        finance.enableVibration(true);
        manager.createNotificationChannel(finance);
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Notification ids are derived from document ids, but todo and bill ids live
     * in different collections and their hash codes can collide. A bill reminder
     * could therefore replace a task reminder, or (worse) the "mark done" action
     * of one could cancel the other. Odd ids are reserved for tasks and even ones
     * for bills, which keeps the two sets disjoint.
     */
    public static int notificationId(String kind, String id) {
        int hash = id == null ? 0 : id.hashCode();
        if (KIND_BILL.equals(kind)) {
            return (hash & 0x7FFFFFFE);
        }
        return (hash & 0x7FFFFFFE) + 1;
    }

    public static void showTodoReminder(Context context, String todoId, String uid, String title) {
        int notifyId = notificationId(KIND_TODO, todoId);
        Intent intent = new Intent(context, TodoDetailActivity.class);
        intent.putExtra(TodoDetailActivity.EXTRA_TODO_ID, todoId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, notifyId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent doneIntent = new Intent(context, NotificationActionReceiver.class);
        doneIntent.setAction(NotificationActionReceiver.ACTION_MARK_DONE);
        doneIntent.putExtra(NotificationActionReceiver.EXTRA_TODO_ID, todoId);
        doneIntent.putExtra(NotificationActionReceiver.EXTRA_UID, uid);
        PendingIntent donePending = PendingIntent.getBroadcast(context, notifyId + 11,
                doneIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(context, NotificationActionReceiver.class);
        snoozeIntent.setAction(NotificationActionReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra(NotificationActionReceiver.EXTRA_TODO_ID, todoId);
        snoozeIntent.putExtra(NotificationActionReceiver.EXTRA_UID, uid);
        snoozeIntent.putExtra(NotificationActionReceiver.EXTRA_TITLE, title);
        PendingIntent snoozePending = PendingIntent.getBroadcast(context, notifyId + 12,
                snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = title == null ? "" : title;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_TODO)
                .setSmallIcon(R.drawable.ic_check)
                .setColor(ContextCompat.getColor(context, R.color.color_primary))
                .setContentTitle(context.getString(R.string.notif_todo_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_check, context.getString(R.string.notif_action_done), donePending)
                .addAction(R.drawable.ic_clock, context.getString(R.string.notif_action_snooze), snoozePending)
                .setContentIntent(pending);
        notify(context, notifyId, builder);
    }

    public static void showBillReminder(Context context, String billId, String title, String amount,
                                        int daysBefore) {
        int notifyId = notificationId(KIND_BILL, billId);
        Intent intent = new Intent(context, BillsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, notifyId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = context.getString(R.string.notif_bill_text, title, amount, daysBefore);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_FINANCE)
                .setSmallIcon(R.drawable.ic_receipt)
                .setColor(ContextCompat.getColor(context, R.color.finance_primary))
                .setContentTitle(context.getString(R.string.notif_bill_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pending);
        notify(context, notifyId, builder);
    }

    /**
     * Posts the budget alert.
     *
     * @return {@code true} only when the notification was actually delivered, so
     *         callers can avoid marking the alert as "already shown" when the
     *         post was blocked by a missing permission.
     */
    public static boolean showBudgetAlert(Context context, String categoryId, String categoryName, int percent) {
        Intent intent = new Intent(context, FinanceActivity.class);
        intent.putExtra(FinanceActivity.EXTRA_TAB, "budgets");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, categoryId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = context.getString(R.string.notif_budget_text, categoryName, percent);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_FINANCE)
                .setSmallIcon(R.drawable.ic_warning)
                .setColor(ContextCompat.getColor(context, R.color.warning))
                .setContentTitle(context.getString(R.string.notif_budget_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending);
        return notify(context, ("budget_" + categoryId).hashCode(), builder);
    }

    private static boolean notify(Context context, int id, NotificationCompat.Builder builder) {
        if (!hasPermission(context)) return false;
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
            return true;
        } catch (SecurityException ignored) {
            return false;
        }
    }
}
