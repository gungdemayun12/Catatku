package com.example.pencatatan.data.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public abstract class BaseRepository {

    protected final FirebaseFirestore db = FirebaseFirestore.getInstance();

    protected DocumentReference userDoc(String uid) {
        return db.collection("users").document(uid);
    }

    protected CollectionReference collection(String uid, String name) {
        return userDoc(uid).collection(name);
    }

    protected String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public static class RepoException extends Exception {
        public static final String WALLET_IN_USE = "wallet_in_use";
        public static final String CATEGORY_IN_USE = "category_in_use";
        public static final String NOT_SIGNED_IN = "not_signed_in";

        private final String code;

        public RepoException(String code) {
            super(code);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
