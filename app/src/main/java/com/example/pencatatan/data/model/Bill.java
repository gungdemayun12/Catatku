package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

@IgnoreExtraProperties
public class Bill {

    @DocumentId
    private String docId;
    private String title;
    private long amount;
    private String currency = "IDR";
    private long dueDay = 1;
    private String repeatType = "monthly";
    /**
     * Month (0-based) a yearly bill recurs in. Stored so the bill keeps its
     * original month even when the app is opened in a different month.
     */
    private int dueMonth = -1;
    private boolean reminderEnabled = true;
    private long reminderDaysBefore = 1;
    private Long notificationId;
    private boolean isActive = true;
    private Timestamp createdAt;

    public Bill() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getDueDay() { return dueDay; }
    public void setDueDay(long dueDay) { this.dueDay = dueDay; }
    public String getRepeatType() { return repeatType; }
    public void setRepeatType(String repeatType) { this.repeatType = repeatType; }
    /** Month (0-based) a yearly bill recurs in, or -1 when not set yet. */
    public int getDueMonth() { return dueMonth; }
    public void setDueMonth(int dueMonth) { this.dueMonth = dueMonth; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }
    public long getReminderDaysBefore() { return reminderDaysBefore; }
    public void setReminderDaysBefore(long reminderDaysBefore) { this.reminderDaysBefore = reminderDaysBefore; }
    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    @PropertyName("isActive")
    public boolean isActive() { return isActive; }
    @PropertyName("isActive")
    public void setActive(boolean active) { isActive = active; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
