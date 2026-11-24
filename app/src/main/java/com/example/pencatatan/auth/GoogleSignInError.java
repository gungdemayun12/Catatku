package com.example.pencatatan.auth;

public class GoogleSignInError extends Exception {

    public static final String CANCELLED = "cancelled";
    public static final String NO_ACCOUNT = "no_account";
    public static final String CONFIG = "config";

    private final String code;

    public GoogleSignInError(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
