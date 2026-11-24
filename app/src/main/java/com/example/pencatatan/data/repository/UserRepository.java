package com.example.pencatatan.data.repository;

import com.example.pencatatan.common.RepoCallback;
import com.example.pencatatan.data.model.FinanceCategory;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.data.model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class UserRepository extends BaseRepository {

    public interface UserListener {
        void onUser(User user);

        void onError(Exception error);
    }

    public ListenerRegistration listen(String uid, UserListener listener) {
        return userDoc(uid).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                User user = snapshot.toObject(User.class);
                if (user != null) listener.onUser(user);
            }
        });
    }

    public void get(String uid, UserListener listener) {
        userDoc(uid).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                listener.onError(task.getException());
                return;
            }
            DocumentSnapshot snapshot = task.getResult();
            if (snapshot != null && snapshot.exists()) {
                User user = snapshot.toObject(User.class);
                if (user != null) {
                    listener.onUser(user);
                    return;
                }
            }
            listener.onError(new IllegalStateException("missing_profile"));
        });
    }

    /** Creates users/{uid} with defaults and the starter categories and wallet when missing. */
    public void ensureProfile(FirebaseUser authUser, String name, String photoUrl, RepoCallback callback) {
        if (authUser == null) {
            callback.onError(new RepoException(RepoException.NOT_SIGNED_IN));
            return;
        }
        String uid = authUser.getUid();
        DocumentReference userRef = userDoc(uid);
        userRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onError(task.getException());
                return;
            }
            DocumentSnapshot snapshot = task.getResult();
            if (snapshot != null && snapshot.exists()) {
                callback.onSuccess();
                return;
            }
            writeDefaults(userRef, uid, name, authUser.getEmail(), photoUrl, callback);
        });
    }

    private void writeDefaults(DocumentReference userRef, String uid, String name, String email,
                               String photoUrl, RepoCallback callback) {
        WriteBatch batch = db.batch();
        String finalName = name == null || name.trim().isEmpty() ? "CatatKu" : name.trim();

        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("name", finalName);
        user.put("email", email == null ? "" : email);
        user.put("photoUrl", photoUrl);
        user.put("lastFeature", "todo");
        user.put("themeMode", "system");
        user.put("language", "system");
        user.put("currency", "IDR");
        user.put("defaultReminderTime", "08:00");
        user.put("notificationsEnabled", true);
        user.put("createdAt", FieldValue.serverTimestamp());
        user.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(userRef, user);

        addTodoCategory(batch, uid, "Personal", "todocat_personal", "#3949AB");
        addTodoCategory(batch, uid, "College / Work", "todocat_work", "#00897B");
        addTodoCategory(batch, uid, "Shopping", "todocat_shopping", "#D97757");

        addFinanceCategory(batch, uid, "Food", "cat_food", FinanceCategory.EXPENSE, "food", "#EF6C00");
        addFinanceCategory(batch, uid, "Transport", "cat_transport", FinanceCategory.EXPENSE, "transport", "#1E88E5");
        addFinanceCategory(batch, uid, "Shopping", "cat_shopping", FinanceCategory.EXPENSE, "shopping", "#8E24AA");
        addFinanceCategory(batch, uid, "Bills", "cat_bills", FinanceCategory.EXPENSE, "bill", "#546E7A");
        addFinanceCategory(batch, uid, "Entertainment", "cat_entertainment", FinanceCategory.EXPENSE, "fun", "#D81B60");
        addFinanceCategory(batch, uid, "Other", "cat_other_expense", FinanceCategory.EXPENSE, "other", "#607D8B");
        addFinanceCategory(batch, uid, "Salary", "cat_salary", FinanceCategory.INCOME, "salary", "#2E7D32");
        addFinanceCategory(batch, uid, "Allowance", "cat_allowance", FinanceCategory.INCOME, "gift", "#00897B");
        addFinanceCategory(batch, uid, "Other income", "cat_other_income", FinanceCategory.INCOME, "other", "#3949AB");

        Map<String, Object> wallet = new HashMap<>();
        wallet.put("name", "Cash");
        wallet.put("nameKey", "wallet_cash");
        wallet.put("type", "cash");
        wallet.put("currency", "IDR");
        wallet.put("initialBalance", 0L);
        wallet.put("colorHex", "#00796B");
        wallet.put("createdAt", FieldValue.serverTimestamp());
        batch.set(collection(uid, "wallets").document(), wallet);

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onSuccess();
            } else {
                callback.onError(task.getException());
            }
        });
    }

    private void addTodoCategory(WriteBatch batch, String uid, String name, String key, String color) {
        TodoCategory category = new TodoCategory(name, key, color);
        batch.set(collection(uid, "todoCategories").document(), category);
    }

    private void addFinanceCategory(WriteBatch batch, String uid, String name, String key, String type,
                                    String icon, String color) {
        FinanceCategory category = new FinanceCategory(name, key, type, icon, color);
        batch.set(collection(uid, "financeCategories").document(), category);
    }

    public void updateFields(String uid, Map<String, Object> fields, RepoCallback callback) {
        fields.put("updatedAt", FieldValue.serverTimestamp());
        userDoc(uid).update(fields).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess();
            else callback.onError(task.getException());
        });
    }

    public void setLastFeature(String uid, String feature) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("lastFeature", feature);
        fields.put("updatedAt", FieldValue.serverTimestamp());
        userDoc(uid).update(fields);
    }

    public void setNotificationsEnabled(String uid, boolean enabled, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("notificationsEnabled", enabled);
        updateFields(uid, fields, callback);
    }

    public void updateName(String uid, String name, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        updateFields(uid, fields, callback);
    }

    public void updateTheme(String uid, String theme, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("themeMode", theme);
        updateFields(uid, fields, callback);
    }

    public void updateLanguage(String uid, String language, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("language", language);
        updateFields(uid, fields, callback);
    }

    public void updateCurrency(String uid, String currency, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("currency", currency);
        updateFields(uid, fields, callback);
    }

    public void updateDefaultReminder(String uid, String time, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("defaultReminderTime", time);
        updateFields(uid, fields, callback);
    }

    public void updateProfile(String uid, String name, String photoUrl, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        if (photoUrl != null) fields.put("photoUrl", photoUrl);
        updateFields(uid, fields, callback);
    }

    /** Stores a small profile photo as a compressed base64 data string on the user document. */
    public void updatePhotoUrl(String uid, String photoUrl, RepoCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        if (photoUrl == null) {
            fields.put("photoUrl", FieldValue.delete());
        } else {
            fields.put("photoUrl", photoUrl);
        }
        updateFields(uid, fields, callback);
    }

    public Timestamp now() {
        return Timestamp.now();
    }
}
