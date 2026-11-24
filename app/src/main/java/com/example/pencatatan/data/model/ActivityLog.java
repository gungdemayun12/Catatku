package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ActivityLog {

    public static final String CREATED = "created";
    public static final String UPDATED = "updated";
    public static final String COMPLETED = "completed";
    public static final String UNCOMPLETED = "uncompleted";
    public static final String DELETED = "deleted";

    @DocumentId
    private String docId;
    private String todoId;
    private String todoTitle;
    private String action;
    private String detail;
    private Timestamp timestamp;

    public ActivityLog() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getTodoId() { return todoId; }
    public void setTodoId(String todoId) { this.todoId = todoId; }
    public String getTodoTitle() { return todoTitle; }
    public void setTodoTitle(String todoTitle) { this.todoTitle = todoTitle; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
