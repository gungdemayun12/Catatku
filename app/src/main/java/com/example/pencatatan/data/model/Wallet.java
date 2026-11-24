package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Wallet {

    @DocumentId
    private String docId;
    private String name;
    private String nameKey;
    private String type = "cash";
    private String currency = "IDR";
    private long initialBalance = 0L;
    private String colorHex = "#00796B";
    private Timestamp createdAt;

    public Wallet() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getInitialBalance() { return initialBalance; }
    public void setInitialBalance(long initialBalance) { this.initialBalance = initialBalance; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
