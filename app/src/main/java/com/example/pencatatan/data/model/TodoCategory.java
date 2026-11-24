package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class TodoCategory {

    @DocumentId
    private String docId;
    private String name;
    private String nameKey;
    private String colorHex = "#3949AB";
    private Timestamp createdAt;

    public TodoCategory() {
    }

    public TodoCategory(String name, String nameKey, String colorHex) {
        this.name = name;
        this.nameKey = nameKey;
        this.colorHex = colorHex;
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameKey() { return nameKey; }
    public void setNameKey(String nameKey) { this.nameKey = nameKey; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
