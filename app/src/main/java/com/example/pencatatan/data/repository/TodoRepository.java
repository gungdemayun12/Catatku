package com.example.pencatatan.data.repository;

import com.example.pencatatan.common.RepoCallback;
import com.example.pencatatan.common.RepeatIds;
import com.example.pencatatan.data.model.ActivityLog;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.TodoCategory;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TodoRepository extends BaseRepository {

    public interface OnTodos {
        void onData(List<Todo> todos);

        void onError(Exception error);
    }

    public interface OnCategories {
        void onData(List<TodoCategory> categories);

        void onError(Exception error);
    }

    public interface OnLogs {
        void onData(List<ActivityLog> logs);

        void onError(Exception error);
    }

    public interface OnTodo {
        void onData(Todo todo);

        void onError(Exception error);
    }

    public interface SaveCallback {
        void onSuccess(String id);

        void onError(Exception error);
    }

    public ListenerRegistration listenTodos(String uid, OnTodos listener) {
        return collection(uid, "todos").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<Todo> todos = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    Todo todo = doc.toObject(Todo.class);
                    if (todo != null) todos.add(todo);
                }
            }
            sortTodos(todos);            listener.onData(todos);
        });
    }

    public static void sortTodos(List<Todo> todos) {
        Collections.sort(todos, (a, b) -> {
            if (a.isCompleted() != b.isCompleted()) return a.isCompleted() ? 1 : -1;
            Date da = a.getDueDate() == null ? null : a.getDueDate().toDate();
            Date db = b.getDueDate() == null ? null : b.getDueDate().toDate();
            if (da != null && db != null) {
                int cmp = da.compareTo(db);
                if (cmp != 0) return cmp;
            } else if (da != null) {
                return -1;
            } else if (db != null) {
                return 1;
            }
            if (a.getPriority() != b.getPriority()) {
                return Long.compare(b.getPriority(), a.getPriority());
            }
            return String.valueOf(a.getTitle()).compareToIgnoreCase(String.valueOf(b.getTitle()));
        });
    }

    public ListenerRegistration listenCategories(String uid, OnCategories listener) {
        return collection(uid, "todoCategories").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            List<TodoCategory> categories = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    TodoCategory category = doc.toObject(TodoCategory.class);
                    if (category != null) categories.add(category);
                }
            }
            Collections.sort(categories, (a, b) -> String.valueOf(a.getName())
                    .compareToIgnoreCase(String.valueOf(b.getName())));
            listener.onData(categories);
        });
    }

    public void loadTodo(String uid, String todoId, OnTodo listener) {
        collection(uid, "todos").document(todoId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                listener.onError(task.getException());
                return;
            }
            Todo todo = task.getResult() == null ? null : task.getResult().toObject(Todo.class);
            if (todo == null) {
                listener.onError(new IllegalStateException("todo_missing"));
            } else {
                listener.onData(todo);
            }
        });
    }

    /**
     * Creates or updates a todo.
     *
     * <p><b>Editing must never change the completion state.</b> {@code toData()} writes
     * {@code isCompleted} and {@code completedAt} too, so saving a stale in-memory copy (one
     * loaded before the task was ticked, or one that was never loaded at all) used to reset a
     * finished task back to "Open". Those two fields are therefore stripped out of every
     * update; only {@link #setCompleted} may write them, inside its own transaction.</p>
     */
    public void saveTodo(String uid, Todo todo, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "todos").document()
                : collection(uid, "todos").document(todo.getDocId());
        WriteBatch batch = db.batch();
        Map<String, Object> data = toData(todo);
        data.put("updatedAt", FieldValue.serverTimestamp());
        if (isNew) {
            // A brand new task is always open, whatever the caller had in memory.
            data.put("isCompleted", false);
            data.put("completedAt", null);
            data.put("createdAt", FieldValue.serverTimestamp());
        } else {
            // Never touch the completion fields of an existing task while editing it.
            data.remove("isCompleted");
            data.remove("completedAt");
        }
        batch.set(ref, data);
        addLog(batch, uid, ref.getId(), todo.getTitle(),
                isNew ? ActivityLog.CREATED : ActivityLog.UPDATED, null);
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteTodo(String uid, Todo todo, SaveCallback callback) {
        WriteBatch batch = db.batch();
        batch.delete(collection(uid, "todos").document(todo.getDocId()));
        addLog(batch, uid, todo.getDocId(), todo.getTitle(), ActivityLog.DELETED, null);
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(todo.getDocId());
            else callback.onError(task.getException());
        });
    }

    /**
     * Marks a todo as done/undone.
     *
     * <p><b>Completing is final and happens exactly once.</b> The status is
     * re-read inside a Firestore transaction, so a double tap, a retried
     * write, a tap from two screens, or a notification action can never write
     * a second {@code completed} entry — the first one wins and the rest are
     * no-ops. This is what stops the activity feed from filling up with
     * identical "Diselesaikan" rows.</p>
     *
     * <p>For repeating tasks the follow-up copy uses a deterministic document
     * id derived from {@code repeatType + dueDate}, so it is created once and
     * never piles up duplicates.</p>
     */
    public void setCompleted(String uid, Todo todo, boolean completed, SaveCallback callback) {
        if (todo == null || todo.getDocId() == null) {
            callback.onError(new IllegalStateException("missing_todo"));
            return;
        }
        // NOTE: there is deliberately NO "in-memory state already matches" guard here.
        // Callers flip the model optimistically so the card updates instantly, which
        // means todo.isCompleted() is ALREADY the target value by the time we get here.
        // The transaction below is the only trustworthy source: it re-reads the
        // document and skips the write only when the SERVER is already in that state.
        DocumentReference ref = collection(uid, "todos").document(todo.getDocId());
        db.runTransaction(transaction -> {
            // Firestore requires that EVERY read happens before ANY write, so
            // both the task and its would-be follow-up copy are read up front.
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                // Task was deleted meanwhile: nothing to complete.
                return RESULT_NOOP;
            }
            Todo current = snapshot.toObject(Todo.class);
            boolean alreadyDone = current != null && current.isCompleted();
            // Someone else (another tap/screen/device) got here first, or the
            // task is already in the requested state: skip the write entirely.
            if (alreadyDone == completed) {
                return RESULT_NOOP;
            }

            Todo source = current != null ? current : todo;
            Todo next = completed ? buildNextRepeating(source) : null;
            DocumentReference copyRef = next == null ? null
                    : collection(uid, "todos").document(RepeatIds.withRoot(
                            repeatingDocId(source), source.getDocId()));
            boolean copyMissing = false;
            if (copyRef != null) {
                // Read BEFORE any write (see note above).
                copyMissing = !transaction.get(copyRef).exists();
            }

            // ---- all reads done, writes start here ----
            Map<String, Object> updates = new HashMap<>();
            updates.put("isCompleted", completed);
            // A plain null is rejected inside an update(): clearing a field needs
            // FieldValue.delete(). Writing null here made the whole completion
            // transaction fail, so "done" silently reverted on the next refresh.
            updates.put("completedAt", completed ? Timestamp.now() : FieldValue.delete());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(ref, updates);
            addLog(transaction, uid, todo.getDocId(), todo.getTitle(),
                    completed ? ActivityLog.COMPLETED : ActivityLog.UNCOMPLETED, null);

            if (next != null && copyRef != null && copyMissing) {
                Map<String, Object> nextData = toData(next);
                // The copy starts fresh: never inherit a stale completion state.
                nextData.put("isCompleted", false);
                nextData.put("completedAt", null);
                // set() rejects FieldValue.delete(); an unset repeat end date must be
                // written as a literal null (the model reads it back as "no end").
                if (nextData.get("repeatEndDate") == null) nextData.put("repeatEndDate", null);
                nextData.put("updatedAt", FieldValue.serverTimestamp());
                nextData.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(copyRef, nextData);
                addLog(transaction, uid, copyRef.getId(), next.getTitle(),
                        ActivityLog.CREATED, null);
                return copyRef.getId();
            }
            return RESULT_DONE;
        }).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onError(task.getException());
                return;
            }
            Object result = task.getResult();
            // RESULT_NOOP => already done/undone, nothing was written.
            // RESULT_DONE => marked, no follow-up copy.
            // anything else => id of the freshly created repeating copy.
            String createdId = result instanceof String
                    && !RESULT_DONE.equals(result) && !RESULT_NOOP.equals(result)
                    ? (String) result : "";
            callback.onSuccess(createdId);
        });
    }

    private static final String RESULT_NOOP = "__noop__";
    private static final String RESULT_DONE = "__done__";

    /** Stable id shared by every copy of the same repeating occurrence. */
    private String repeatingDocId(Todo todo) {
        return RepeatIds.forNextOccurrence(todo.getRepeatType(),
                todo.getDueDate() == null ? null : todo.getDueDate().toDate(),
                todo.getDocId());
    }

    /**
     * Removes identical open duplicates that earlier builds created when a repeating
     * task was completed several times.
     *
     * <p>A follow-up copy is only kept once per (title, dueDate, repeatType): the first
     * one wins, the rest are deleted together with their activity log. Tasks that differ
     * in any of those three fields are never touched, so genuinely separate tasks are
     * safe. Runs in the background and reports how many rows it removed.</p>
     */
    public void removeDuplicateRepeats(String uid, RepoCallback callback) {
        collection(uid, "todos").get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                callback.onError(task.getException());
                return;
            }
            Map<String, DocumentReference> keep = new HashMap<>();
            List<DocumentReference> duplicates = new ArrayList<>();
            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                Todo todo = doc.toObject(Todo.class);
                if (todo == null || !todo.isRepeating() || todo.isCompleted()) continue;
                String key = signature(todo);
                DocumentReference keeper = keep.get(key);
                if (keeper == null) {
                    keep.put(key, doc.getReference());
                } else {
                    duplicates.add(doc.getReference());
                }
            }
            if (duplicates.isEmpty()) {
                callback.onSuccess();
                return;
            }
            WriteBatch batch = db.batch();
            for (DocumentReference ref : duplicates) batch.delete(ref);
            batch.commit().addOnCompleteListener(commit -> {
                if (commit.isSuccessful()) callback.onSuccess();
                else callback.onError(commit.getException());
            });
        });
    }

    /** Title + due date + repeat rule: what makes two copies "the same occurrence". */
    private static String signature(Todo todo) {
        long due = todo.getDueDate() == null ? 0L : todo.getDueDate().toDate().getTime();
        return (todo.getTitle() == null ? "" : todo.getTitle().trim().toLowerCase(java.util.Locale.ROOT))
                + "|" + due + "|" + String.valueOf(todo.getRepeatType());
    }

    public void updateSubtasks(String uid, Todo todo, SaveCallback callback) {
        if (todo == null || todo.getDocId() == null) {
            callback.onError(new IllegalStateException("missing_todo"));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("subtasks", todo.getSubtasks() == null ? new ArrayList<>() : todo.getSubtasks());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        collection(uid, "todos").document(todo.getDocId()).update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) callback.onSuccess(todo.getDocId());
                    else callback.onError(task.getException());
                });
    }

    /**
     * Moves a task's deadline by a number of days, used by the "Snooze" action on
     * an overdue task. Reminders move with it by the same offset, otherwise the
     * task would keep alerting on its old schedule.
     */
    public void snooze(String uid, Todo todo, int days, SaveCallback callback) {
        if (todo == null || todo.getDocId() == null || todo.getDueDate() == null) {
            callback.onError(new IllegalStateException("missing_due_date"));
            return;
        }
        long delta = days * 24L * 60L * 60L * 1000L;
        Calendar next = Calendar.getInstance();
        next.setTime(todo.getDueDate().toDate());
        next.add(Calendar.DAY_OF_MONTH, days);

        List<Timestamp> reminders = new ArrayList<>();
        if (todo.getReminderTimes() != null) {
            for (Timestamp reminder : todo.getReminderTimes()) {
                if (reminder != null) {
                    reminders.add(new Timestamp(new Date(reminder.toDate().getTime() + delta)));
                }
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("dueDate", new Timestamp(next.getTime()));
        updates.put("reminderTimes", reminders);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        WriteBatch batch = db.batch();
        batch.update(collection(uid, "todos").document(todo.getDocId()), updates);
        addLog(batch, uid, todo.getDocId(), todo.getTitle(), ActivityLog.UPDATED, null);
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(todo.getDocId());
            else callback.onError(task.getException());
        });
    }

    /**
     * Creates an independent copy of a task (same fields, no completion state, no
     * reminders) so the user can reuse it as a template without retyping.
     */
    public void duplicate(String uid, Todo todo, SaveCallback callback) {
        if (todo == null) {
            callback.onError(new IllegalStateException("missing_todo"));
            return;
        }
        Todo copy = new Todo();
        copy.setTitle(todo.getTitle());
        copy.setDescription(todo.getDescription());
        copy.setCategoryId(todo.getCategoryId());
        copy.setCategoryName(todo.getCategoryName());
        copy.setPriority(todo.getPriority());
        copy.setDueDate(todo.getDueDate());
        copy.setRepeatType(todo.getRepeatType());
        copy.setRepeatEndDate(todo.getRepeatEndDate());
        copy.setSubtasks(resetSubtasks(todo.getSubtasks()));
        copy.setStarred(todo.isStarred());
        // A copy is a fresh piece of work: it never inherits "done".
        saveTodo(uid, copy, true, callback);
    }

    /**
     * Stars or unstars a task. Starred tasks power the "Starred" smart list, which
     * is how a user keeps a handful of tasks pinned at the top of their attention
     * without giving them an artificial deadline.
     */
    public void setStarred(String uid, Todo todo, boolean starred, SaveCallback callback) {
        if (todo == null || todo.getDocId() == null) {
            callback.onError(new IllegalStateException("missing_todo"));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("starred", starred);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        collection(uid, "todos").document(todo.getDocId()).update(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) callback.onSuccess(todo.getDocId());
                    else callback.onError(task.getException());
                });
    }

    /**
     * Completes several tasks in one batch. Repeating tasks still spawn their next
     * copy, which is why each one goes through the same {@link #onComplete} helper
     * used by the single-task path instead of a plain batch update.
     */
    public void completeMany(String uid, List<Todo> todos, SaveCallback callback) {
        if (todos == null || todos.isEmpty()) {
            callback.onSuccess("");
            return;
        }
        final int[] remaining = {todos.size()};
        final boolean[] failed = {false};
        for (Todo todo : todos) {
            setCompleted(uid, todo, true, new SaveCallback() {
                @Override
                public void onSuccess(String id) {
                    finish();
                }

                @Override
                public void onError(Exception error) {
                    failed[0] = true;
                    finish();
                }

                private void finish() {
                    remaining[0]--;
                    if (remaining[0] > 0) return;
                    if (failed[0]) callback.onError(new IllegalStateException("partial_failure"));
                    else callback.onSuccess("");
                }
            });
        }
    }

    /** Deletes several tasks in a single batch (and logs each removal). */
    public void deleteMany(String uid, List<Todo> todos, SaveCallback callback) {
        if (todos == null || todos.isEmpty()) {
            callback.onSuccess("");
            return;
        }
        WriteBatch batch = db.batch();
        for (Todo todo : todos) {
            if (todo == null || todo.getDocId() == null) continue;
            batch.delete(collection(uid, "todos").document(todo.getDocId()));
            addLog(batch, uid, todo.getDocId(), todo.getTitle(), ActivityLog.DELETED, null);
        }
        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess("");
            else callback.onError(task.getException());
        });
    }

    /**
     * Clears the completed history. Only finished tasks are removed, so an open
     * task can never be deleted by this cleanup.
     */
    public void clearHistory(String uid, RepoCallback callback) {
        collection(uid, "todos").whereEqualTo("isCompleted", true).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null
                            || task.getResult().isEmpty()) {
                        if (task.isSuccessful()) callback.onSuccess();
                        else callback.onError(task.getException());
                        return;
                    }
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit().addOnCompleteListener(commit -> {
                        if (commit.isSuccessful()) callback.onSuccess();
                        else callback.onError(commit.getException());
                    });
                });
    }

    private Todo buildNextRepeating(Todo todo) {
        if (todo.getRepeatType() == null || "none".equals(todo.getRepeatType()) || todo.getDueDate() == null) {
            return null;
        }
        Calendar next = Calendar.getInstance();
        next.setTime(todo.getDueDate().toDate());
        if ("daily".equals(todo.getRepeatType())) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        } else if ("weekly".equals(todo.getRepeatType())) {
            next.add(Calendar.DAY_OF_MONTH, 7);
        } else if ("monthly".equals(todo.getRepeatType())) {
            next.add(Calendar.MONTH, 1);
        } else {
            return null;
        }
        if (todo.getRepeatEndDate() != null && next.getTime().after(todo.getRepeatEndDate().toDate())) {
            return null;
        }
        long delta = next.getTimeInMillis() - todo.getDueDate().toDate().getTime();

        Todo copy = new Todo();
        copy.setTitle(todo.getTitle());
        copy.setDescription(todo.getDescription());
        copy.setCategoryId(todo.getCategoryId());
        copy.setCategoryName(todo.getCategoryName());
        copy.setPriority(todo.getPriority());
        copy.setDueDate(new Timestamp(next.getTime()));
        copy.setRepeatType(todo.getRepeatType());
        copy.setRepeatEndDate(todo.getRepeatEndDate());
        copy.setSubtasks(resetSubtasks(todo.getSubtasks()));
        copy.setCompleted(false);
        // Effort and starring belong to the series, not to a single occurrence:
        // dropping them here made every follow-up copy show up as 0 min of work
        // and silently un-star the task on the "Fokus hari ini" list.
        copy.setEstimateMinutes(todo.getEstimateMinutes());
        copy.setStarred(todo.isStarred());
        List<Timestamp> reminders = new ArrayList<>();
        if (todo.getReminderTimes() != null) {
            for (Timestamp reminder : todo.getReminderTimes()) {
                if (reminder != null) {
                    reminders.add(new Timestamp(new Date(reminder.toDate().getTime() + delta)));
                }
            }
        }
        copy.setReminderTimes(reminders);
        copy.setNotificationIds(new ArrayList<>());
        return copy;
    }

    private List<Map<String, Object>> resetSubtasks(List<Map<String, Object>> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (source == null) return result;
        for (Map<String, Object> item : source) {
            Map<String, Object> copy = new HashMap<>(item);
            copy.put("isDone", false);
            result.add(copy);
        }
        return result;
    }

    private Map<String, Object> toData(Todo todo) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", todo.getTitle() == null ? "" : todo.getTitle());
        data.put("description", todo.getDescription());
        data.put("categoryId", todo.getCategoryId());
        data.put("categoryName", todo.getCategoryName());
        data.put("priority", todo.getPriority());
        data.put("dueDate", todo.getDueDate());
        data.put("reminderTimes", todo.getReminderTimes() == null ? new ArrayList<Timestamp>() : todo.getReminderTimes());
        data.put("notificationIds", todo.getNotificationIds() == null ? new ArrayList<Long>() : todo.getNotificationIds());
        data.put("isCompleted", todo.isCompleted());
        data.put("completedAt", todo.getCompletedAt());
        data.put("repeatType", todo.getRepeatType() == null ? "none" : todo.getRepeatType());
        data.put("repeatEndDate", todo.getRepeatEndDate());
        data.put("subtasks", todo.getSubtasks() == null ? new ArrayList<>() : todo.getSubtasks());
        data.put("starred", todo.isStarred());
        // Effort estimate in minutes (0 = not set). Persisted so the list can
        // total up a day's workload without the form being open.
        data.put("estimateMinutes", todo.getEstimateMinutes());
        return data;
    }

    private void addLog(WriteBatch batch, String uid, String todoId, String title, String action, String detail) {
        batch.set(collection(uid, "activityLogs").document(), logData(todoId, title, action, detail));
    }

    /** Transaction variant, so logging stays inside the atomic completion write. */
    private void addLog(Transaction transaction, String uid, String todoId, String title,
                        String action, String detail) {
        transaction.set(collection(uid, "activityLogs").document(),
                logData(todoId, title, action, detail));
    }

    private Map<String, Object> logData(String todoId, String title, String action, String detail) {
        Map<String, Object> log = new HashMap<>();
        log.put("todoId", todoId);
        log.put("todoTitle", title == null ? "" : title);
        log.put("action", action);
        log.put("detail", detail);
        log.put("timestamp", FieldValue.serverTimestamp());
        return log;
    }

    public void saveCategory(String uid, TodoCategory category, boolean isNew, SaveCallback callback) {
        DocumentReference ref = isNew
                ? collection(uid, "todoCategories").document()
                : collection(uid, "todoCategories").document(category.getDocId());
        Map<String, Object> data = new HashMap<>();
        data.put("name", category.getName());
        data.put("nameKey", category.getNameKey());
        data.put("colorHex", category.getColorHex());
        if (isNew) data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(ref.getId());
            else callback.onError(task.getException());
        });
    }

    public void deleteCategory(String uid, TodoCategory category, SaveCallback callback) {
        collection(uid, "todos").whereEqualTo("categoryId", category.getDocId()).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onError(task.getException());
                        return;
                    }
                    WriteBatch batch = db.batch();
                    if (task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("categoryId", null);
                            updates.put("categoryName", null);
                            updates.put("updatedAt", FieldValue.serverTimestamp());
                            batch.update(doc.getReference(), updates);
                        }
                    }
                    batch.delete(collection(uid, "todoCategories").document(category.getDocId()));
                    batch.commit().addOnCompleteListener(t -> {
                        if (t.isSuccessful()) callback.onSuccess(category.getDocId());
                        else callback.onError(t.getException());
                    });
                });
    }

    public ListenerRegistration listenLogsForTodo(String uid, String todoId, OnLogs listener) {
        return collection(uid, "activityLogs")
                .whereEqualTo("todoId", todoId)
                .limit(50)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    List<ActivityLog> logs = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ActivityLog log = doc.toObject(ActivityLog.class);
                            if (log != null) logs.add(log);
                        }
                    }
                    sortLogs(logs);
                    listener.onData(logs);
                });
    }

    public void loadLogs(String uid, Date olderThan, int limit, OnLogs listener) {
        Query query = collection(uid, "activityLogs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit);
        if (olderThan != null) query = query.startAfter(new Timestamp(olderThan));
        query.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                listener.onError(task.getException());
                return;
            }
            List<ActivityLog> logs = new ArrayList<>();
            if (task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    ActivityLog log = doc.toObject(ActivityLog.class);
                    if (log != null) logs.add(log);
                }
            }
            listener.onData(logs);
        });
    }

    private void sortLogs(List<ActivityLog> logs) {
        Collections.sort(logs, (a, b) -> {
            Date da = a.getTimestamp() == null ? new Date(0) : a.getTimestamp().toDate();
            Date db = b.getTimestamp() == null ? new Date(0) : b.getTimestamp().toDate();
            return db.compareTo(da);
        });
    }
}
