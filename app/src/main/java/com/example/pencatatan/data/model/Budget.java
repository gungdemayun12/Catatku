package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Budget {

    @DocumentId
    private String docId;
    private String categoryId;
    private String categoryName;
    private String monthKey;
    private long limitAmount;
    private Timestamp createdAt;

    public Budget() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getMonthKey() { return monthKey; }
    public void setMonthKey(String monthKey) { this.monthKey = monthKey; }
    public long getLimitAmount() { return limitAmount; }
    public void setLimitAmount(long limitAmount) { this.limitAmount = limitAmount; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public static String idFor(String monthKey, String categoryId) {
        return monthKey + "_" + categoryId;
    }
}
