package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class FinanceCategory {

    public static final String INCOME = "income";
    public static final String EXPENSE = "expense";

    @DocumentId
    private String docId;
    private String name;
    private String nameKey;
    private String type = EXPENSE;
    private String icon = "other";
    private String colorHex = "#00796B";
    private Timestamp createdAt;

    public FinanceCategory() {
    }

    public FinanceCategory(String name, String nameKey, String type, String icon, String colorHex) {
        this.name = name;
        this.nameKey = nameKey;
        this.type = type;
        this.icon = icon;
        this.colorHex = colorHex;
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isIncome() {
        return INCOME.equals(type);
    }
}
