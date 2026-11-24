package com.example.pencatatan.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.example.pencatatan.R;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.repository.TodoRepository;

/**
 * Handles the quick actions shown on a task reminder notification:
 * "Mark as done" and "Snooze 10 min" (PRD FR-N7).
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_MARK_DONE = "com.example.pencatatan.action.NOTIF_DONE";
    public static final String ACTION_SNOOZE = "com.example.pencatatan.action.NOTIF_SNOOZE";
    public static final String EXTRA_TODO_ID = "todo_id";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_TITLE = "title";

    private static final int SNOOZE_MINUTES = 10;

    private final TodoRepository repository = new TodoRepository();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String todoId = intent.getStringExtra(EXTRA_TODO_ID);
        String uid = intent.getStringExtra(EXTRA_UID);
        String title = intent.getStringExtra(EXTRA_TITLE);
        if (todoId == null) return;

        try {
            NotificationManagerCompat.from(context)
                    .cancel(NotificationHelper.notificationId(NotificationHelper.KIND_TODO, todoId));
        } catch (SecurityException ignored) {
        }

        if (ACTION_MARK_DONE.equals(action)) {
            markDone(context, uid, todoId);
        } else if (ACTION_SNOOZE.equals(action)) {
            ReminderScheduler.scheduleSnooze(context, uid, todoId, title, SNOOZE_MINUTES);
            Toast.makeText(context, R.string.notif_snoozed, Toast.LENGTH_SHORT).show();
        }
    }

    private void markDone(Context context, String uid, String todoId) {
        if (uid == null) return;
        final PendingResult result = goAsync();
        repository.loadTodo(uid, todoId, new TodoRepository.OnTodo() {
            @Override
            public void onData(Todo todo) {
                ReminderScheduler.cancelTodo(context, todo);
                repository.setCompleted(uid, todo, true, new TodoRepository.SaveCallback() {
                    @Override
                    public void onSuccess(String createdId) {
                        Toast.makeText(context, R.string.notif_marked_done, Toast.LENGTH_SHORT).show();
                        result.finish();
                    }

                    @Override
                    public void onError(Exception error) {
                        result.finish();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                result.finish();
            }
        });
    }
}
