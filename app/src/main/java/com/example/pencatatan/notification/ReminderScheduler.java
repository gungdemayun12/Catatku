package com.example.pencatatan.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Bill;
import com.example.pencatatan.finance.BillSchedule;
import com.example.pencatatan.data.model.Todo;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

public final class ReminderScheduler {

    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_UID = "uid";
    public static final String KIND_TODO = "todo";
    public static final String KIND_BILL = "bill";

    private ReminderScheduler() {
    }

    public static int newId() {
        return new Random().nextInt(1_000_000_000) + 1000;
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    public static void openExactAlarmSettings(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public static void scheduleTodo(Context context, String uid, Todo todo) {
        if (todo == null || !Prefs.notificationsEnabled(context)) return;
        List<Timestamp> times = todo.getReminderTimes();
        List<Long> ids = todo.getNotificationIds();
        if (times == null || ids == null) return;
        long now = System.currentTimeMillis();
        int count = Math.min(times.size(), ids.size());
        for (int i = 0; i < count; i++) {
            Timestamp time = times.get(i);
            Long id = ids.get(i);
            if (time == null || id == null) continue;
            if (time.toDate().getTime() <= now) continue;
            if (todo.isCompleted()) continue;
            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction("com.example.pencatatan.TODO_REMINDER");
            intent.putExtra(EXTRA_KIND, KIND_TODO);
            intent.putExtra(EXTRA_ID, todo.getDocId());
            intent.putExtra(EXTRA_TITLE, todo.getTitle());
            intent.putExtra(EXTRA_UID, uid);
            intent.putExtra("requestCode", id.intValue());
            PendingIntent pending = PendingIntent.getBroadcast(context, id.intValue(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            setAlarm(context, time.toDate().getTime(), pending);
        }
    }

    /** Re-fires a task reminder after the given delay (used by the "Snooze" notification action). */
    public static void scheduleSnooze(Context context, String uid, String todoId, String title, int minutes) {
        if (todoId == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.pencatatan.TODO_REMINDER");
        intent.putExtra(EXTRA_KIND, KIND_TODO);
        intent.putExtra(EXTRA_ID, todoId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_UID, uid);
        int request = newId();
        PendingIntent pending = PendingIntent.getBroadcast(context, request, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(context, System.currentTimeMillis() + minutes * 60_000L, pending);
    }

    public static void cancelTodo(Context context, Todo todo) {
        if (todo == null || todo.getNotificationIds() == null) return;
        for (Long id : todo.getNotificationIds()) {
            if (id == null) continue;
            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction("com.example.pencatatan.TODO_REMINDER");
            PendingIntent pending = PendingIntent.getBroadcast(context, id.intValue(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager != null) manager.cancel(pending);
        }
    }

    public static void scheduleBill(Context context, String uid, Bill bill) {
        if (bill == null || bill.getNotificationId() == null) return;
        if (!Prefs.notificationsEnabled(context) || !bill.isActive() || !bill.isReminderEnabled()) {
            cancelBill(context, bill);
            return;
        }
        long trigger = nextBillTrigger(bill);
        if (trigger <= System.currentTimeMillis()) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.pencatatan.BILL_REMINDER");
        intent.putExtra(EXTRA_KIND, KIND_BILL);
        intent.putExtra(EXTRA_ID, bill.getDocId());
        intent.putExtra(EXTRA_TITLE, bill.getTitle());
        intent.putExtra(EXTRA_UID, uid);
        intent.putExtra("amount", bill.getAmount());
        intent.putExtra("currency", bill.getCurrency());
        intent.putExtra("daysBefore", (int) bill.getReminderDaysBefore());
        int request = bill.getNotificationId().intValue();
        PendingIntent pending = PendingIntent.getBroadcast(context, request, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(context, trigger, pending);
    }

    public static void cancelBill(Context context, Bill bill) {
        if (bill == null || bill.getNotificationId() == null) return;
        cancelBill(context, bill.getNotificationId());
    }

    /**
     * Cancels a bill alarm by raw notification id.
     *
     * <p>Needed when editing a bill: the alarm was scheduled with the id stored
     * on the <em>old</em> copy, so it must be cancelled before the updated copy
     * is scheduled.</p>
     */
    public static void cancelBill(Context context, long notificationId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.pencatatan.BILL_REMINDER");
        PendingIntent pending = PendingIntent.getBroadcast(context, (int) notificationId,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(pending);
    }

    /**
     * Due day minus reminder days, at 08:00. Months without that day use the last day,
     * and past occurrences roll to the next month/year.
     */
    public static long nextBillTrigger(Bill bill) {
        Calendar now = Calendar.getInstance();
        Calendar target = billTargetAfter(bill, now);
        return target.getTimeInMillis();
    }

    public static Calendar billTargetAfter(Bill bill, Calendar reference) {
        boolean yearly = "yearly".equals(bill.getRepeatType());
        if (yearly && bill.getDueMonth() >= 0) {
            return BillSchedule.nextYearlyReminder(bill.getDueMonth(), (int) bill.getDueDay(),
                    (int) bill.getReminderDaysBefore(), reference);
        }
        return BillSchedule.nextReminder(yearly, (int) bill.getDueDay(),
                (int) bill.getReminderDaysBefore(), reference);
    }

    public static Date nextBillDisplay(Bill bill) {
        return billTargetAfter(bill, Calendar.getInstance()).getTime();
    }

    private static void setAlarm(Context context, long triggerAt, PendingIntent pending) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        } catch (SecurityException e) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }
}
