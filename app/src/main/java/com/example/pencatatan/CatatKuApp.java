package com.example.pencatatan;

import android.app.Application;

import com.example.pencatatan.common.Appearance;
import com.example.pencatatan.notification.NotificationHelper;

public class CatatKuApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Appearance.applyAll(this);
        NotificationHelper.createChannels(this);
    }
}
