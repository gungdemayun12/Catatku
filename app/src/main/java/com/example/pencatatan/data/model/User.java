package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {

    @DocumentId
    private String docId;
    private String uid;
    private String name;
    private String email;
    private String photoUrl;
    private String lastFeature = "todo";
    private String themeMode = "system";
    private String language = "system";
    private String currency = "IDR";
    private String defaultReminderTime = "08:00";
    private boolean notificationsEnabled = true;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public User() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getLastFeature() { return lastFeature; }
    public void setLastFeature(String lastFeature) { this.lastFeature = lastFeature; }
    public String getThemeMode() { return themeMode; }
    public void setThemeMode(String themeMode) { this.themeMode = themeMode; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDefaultReminderTime() { return defaultReminderTime; }
    public void setDefaultReminderTime(String defaultReminderTime) { this.defaultReminderTime = defaultReminderTime; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String safeName() {
        return name == null || name.trim().isEmpty() ? "CatatKu" : name;
    }

    public String initial() {
        String n = safeName();
        return n.substring(0, 1).toUpperCase();
    }
}
