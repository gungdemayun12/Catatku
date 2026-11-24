package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ExchangeRate {

    @DocumentId
    private String docId;
    private double rateToBase;
    private Timestamp updatedAt;

    public ExchangeRate() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public double getRateToBase() { return rateToBase; }
    public void setRateToBase(double rateToBase) { this.rateToBase = rateToBase; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
