package com.example.pencatatan.auth;

import com.example.pencatatan.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;

/** Translates Firebase and Google errors into friendly, localized messages. */
public final class AuthErrorMapper {

    private AuthErrorMapper() {
    }

    public static int messageRes(Exception error) {
        if (error == null) return R.string.err_generic;

        if (error instanceof GoogleSignInError) {
            String code = ((GoogleSignInError) error).getCode();
            if (GoogleSignInError.CANCELLED.equals(code)) return R.string.err_google_cancelled;
            if (GoogleSignInError.NO_ACCOUNT.equals(code)) return R.string.err_google_no_account;
            if (GoogleSignInError.CONFIG.equals(code)) return R.string.err_google_config;
            return R.string.err_generic;
        }

        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if (code == null) return R.string.err_generic;
            switch (code) {
                case "ERROR_INVALID_EMAIL":
                    return R.string.err_email_invalid;
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_INVALID_CREDENTIAL":
                    return R.string.err_password_wrong;
                case "ERROR_USER_NOT_FOUND":
                    return R.string.err_user_not_found;
                case "ERROR_NETWORK_REQUEST_FAILED":
                    return R.string.err_no_internet;
                case "ERROR_EMAIL_ALREADY_IN_USE":
                case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                    return R.string.err_email_in_use;
                case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                    return R.string.err_google_collision;
                case "ERROR_WEAK_PASSWORD":
                    return R.string.err_weak_password;
                case "ERROR_TOO_MANY_REQUESTS":
                    return R.string.err_too_many_requests;
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return R.string.err_operation_not_allowed;
                case "ERROR_MISSING_GOOGLE_ID_TOKEN":
                    return R.string.err_google_config;
                case "ERROR_NO_SUCH_PROVIDER":
                case "ERROR_PROVIDER_ALREADY_LINKED":
                    return R.string.err_google_collision;
                default:
                    return R.string.err_generic;
            }
        }

        if (error instanceof FirebaseNetworkException) {
            return R.string.err_no_internet;
        }

        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestore = (FirebaseFirestoreException) error;
            if (firestore.getCode() == Code.UNAVAILABLE) return R.string.err_no_internet;
        }

        String message = String.valueOf(error.getMessage());
        if (message.contains("network") || message.contains("UNAVAILABLE")) {
            return R.string.err_no_internet;
        }
        return R.string.err_generic;
    }
}
