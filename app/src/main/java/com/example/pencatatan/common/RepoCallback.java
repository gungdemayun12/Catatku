package com.example.pencatatan.common;

public interface RepoCallback {
    void onSuccess();

    void onError(Exception error);
}
