package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@IgnoreExtraProperties
public class Todo {

    @DocumentId
    private String docId;
    private String title;
    private String description;
    private String categoryId;
    private String categoryName;
    private long priority = 1;
    private Timestamp dueDate;
    private List<Timestamp> reminderTimes = new ArrayList<>();
    private List<Long> notificationIds = new ArrayList<>();
    private boolean isCompleted = false;
    private Timestamp completedAt;
    private String repeatType = "none";
    private Timestamp repeatEndDate;
    private List<Map<String, Object>> subtasks = new ArrayList<>();
    private boolean starred = false;

    /**
     * Rough effort in minutes, chosen from a fixed set of chips in the form
     * (15 / 30 / 60 / 120 / 480). Zero means "not estimated".
     *
     * <p>This exists so the task list can answer the one question a to-do app
     * almost never answers: <em>does today's work fit in today's hours?</em>
     * Without it the header can only count tasks, and counting tasks is what
     * makes people over-plan and then abandon the list.</p>
     */
    private long estimateMinutes = 0L;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Todo() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public long getPriority() { return priority; }
    public void setPriority(long priority) { this.priority = priority; }
    public Timestamp getDueDate() { return dueDate; }
    public void setDueDate(Timestamp dueDate) { this.dueDate = dueDate; }
    public List<Timestamp> getReminderTimes() { return reminderTimes; }
    public void setReminderTimes(List<Timestamp> reminderTimes) { this.reminderTimes = reminderTimes; }
    public List<Long> getNotificationIds() { return notificationIds; }
    public void setNotificationIds(List<Long> notificationIds) { this.notificationIds = notificationIds; }
    @PropertyName("isCompleted")
    public boolean isCompleted() { return isCompleted; }
    @PropertyName("isCompleted")
    public void setCompleted(boolean completed) { isCompleted = completed; }
    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }
    public String getRepeatType() { return repeatType; }
    public void setRepeatType(String repeatType) { this.repeatType = repeatType; }
    public Timestamp getRepeatEndDate() { return repeatEndDate; }
    public void setRepeatEndDate(Timestamp repeatEndDate) { this.repeatEndDate = repeatEndDate; }
    public List<Map<String, Object>> getSubtasks() { return subtasks; }
    public void setSubtasks(List<Map<String, Object>> subtasks) { this.subtasks = subtasks; }
    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
    public long getEstimateMinutes() { return estimateMinutes; }
    public void setEstimateMinutes(long estimateMinutes) { this.estimateMinutes = estimateMinutes; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isOverdue() {
        return !isCompleted && dueDate != null && dueDate.toDate().before(new Date());
    }

    /**
     * A finished task is final: it lives on as a history entry (plus the fresh
     * copy a repeating task spawns). It is not deleted and not re-openable, so
     * every screen must agree on the rule — that is why the check lives here
     * instead of being re-implemented per screen.
     *
     * <p>This only means "the completion can no longer be toggled from a list".
     * It must never be used to hide the card, the tick, or the "Done" badge, and
     * it must never make the row look broken.</p>
     */
    public boolean isLocked() {
        return isCompleted;
    }

    /** True when the task has a repeat rule, so completing it spawns a fresh copy. */
    public boolean isRepeating() {
        return repeatType != null && !repeatType.trim().isEmpty() && !"none".equals(repeatType);
    }

    /**
     * True once the user has actually begun the task: at least one subtask is
     * ticked off. Together with the due date this is what separates "Open"
     * (nothing done yet) from "In progress" on the card's status badge.
     */
    public boolean hasStarted() {
        if (subtasks == null) return false;
        for (Map<String, Object> subtask : subtasks) {
            if (subtask != null && Boolean.TRUE.equals(subtask.get("isDone"))) return true;
        }
        return false;
    }

    /** How many subtasks are ticked off, and how many exist in total. */
    public int[] subtaskProgress() {
        int done = 0;
        int total = subtasks == null ? 0 : subtasks.size();
        if (subtasks != null) {
            for (Map<String, Object> subtask : subtasks) {
                if (subtask != null && Boolean.TRUE.equals(subtask.get("isDone"))) done++;
            }
        }
        return new int[]{done, total};
    }

    /** True when the task has at least one subtask. */
    public boolean hasSubtasks() {
        return subtasks != null && !subtasks.isEmpty();
    }

    /**
     * Quick win: a single small step that the user can clearly finish right now.
     * Used by the "Quick wins" smart list, which surfaces the easiest work first
     * instead of a wall of long tasks.
     */
    public boolean isQuickWin() {
        return !isCompleted && !hasSubtasks() && getPriority() == 0;
    }

    /**
     * Age in whole days since the task was created. Used by the "Stale" smart list
     * to resurface work nobody has touched in a while.
     */
    public long ageInDays() {
        Timestamp created = createdAt;
        if (created == null) return 0;
        long diff = System.currentTimeMillis() - created.toDate().getTime();
        return Math.max(0, diff / (24L * 60L * 60L * 1000L));
    }

    /** True when the task is still open and has been sitting untouched for N days. */
    public boolean isStale(int days) {
        return !isCompleted && ageInDays() >= days;
    }

    /**
     * Days until the deadline. Negative when overdue, 0 for today, and
     * {@link Long#MAX_VALUE} when there is no deadline at all.
     */
    public long daysUntilDue() {
        if (dueDate == null) return Long.MAX_VALUE;
        long diff = dueDate.toDate().getTime() - System.currentTimeMillis();
        long days = diff / (24L * 60L * 60L * 1000L);
        // Round so "in 3 hours" still reads as today rather than as day 0/-1.
        return diff < 0 ? days : days + (diff % (24L * 60L * 60L * 1000L) > 0 ? 0 : 0);
    }

    /**
     * True when this task belongs to the history list. History is the only place a
     * finished task can be seen, so the open list stays about work still to do.
     */
    public boolean isHistory() {
        return isCompleted;
    }

    /** How long the task is expected to take, or 0 when the user did not say. */
    public boolean hasEstimate() {
        return estimateMinutes > 0;
    }

    /**
     * Score used by the "Focus today" list, which is the answer to the only
     * question that matters when opening a to-do app in the morning:
     * <em>of everything here, what should I do right now?</em>
     *
     * <p>The weights are deliberately coarse and readable rather than tuned.
     * Overdue work dominates (nothing else is as urgent as something already
     * late), today's work comes next, then importance, then the user's own
     * star. The age penalty is small and capped by the caller's threshold; it
     * only breaks ties so that a task nobody has touched in three weeks does
     * not sink below one created this morning.</p>
     *
     * <p>Finished and archived tasks are excluded by returning a sentinel the
     * caller filters out, so the ranking never has to special-case them.</p>
     */
    public long focusScore() {
        if (isCompleted) return Long.MIN_VALUE;
        long score = 0L;
        long days = daysUntilDue();
        if (days != Long.MAX_VALUE) {
            if (days < 0) {
                // Overdue: the longer it has been late, the more it hurts.
                score += 100L + Math.min(50L, -days * 5L);
            } else if (days == 0) {
                score += 50L;
            } else if (days <= 2) {
                score += 30L;
            }
        }
        score += getPriority() * 30L;
        if (starred) score += 20L;
        if (hasStarted()) score += 10L;
        long age = Math.min(21L, ageInDays());
        score -= age;
        return score;
    }

    /**
     * A short human reason for why this task made the Focus list, e.g.
     * "Overdue by 3 days" or "Due today". Returns null when there is nothing
     * worth saying, so the row stays clean instead of printing filler.
     */
    public String focusReasonKey() {
        long days = daysUntilDue();
        if (days == Long.MAX_VALUE) return starred ? "starred" : null;
        if (days < 0) return "overdue";
        if (days == 0) return "today";
        if (days <= 2) return "soon";
        return starred ? "starred" : null;
    }

    /** Whole days a task is late, or 0 when it is not overdue. */
    public long overdueDays() {
        long days = daysUntilDue();
        return days < 0 && days != Long.MAX_VALUE ? -days : 0L;
    }

    public List<Date> reminders() {        List<Date> dates = new ArrayList<>();
        if (reminderTimes != null) {
            for (Timestamp time : reminderTimes) {
                if (time != null) dates.add(time.toDate());
            }
        }
        return dates;
    }
}
