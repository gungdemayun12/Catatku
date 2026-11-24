package com.example.pencatatan.common;

import android.app.Activity;
import android.content.Intent;

import com.example.pencatatan.finance.FinanceActivity;
import com.example.pencatatan.home.HomeActivity;
import com.example.pencatatan.todo.TodoActivity;

public final class Navigator {

    private Navigator() {
    }

    public static final String FEATURE_TODO = "todo";
    public static final String FEATURE_FINANCE = "finance";

    /**
     * Sends the user to the Home screen after signing in or after the splash
     * finishes.
     *
     * <p>Home is the landing page <b>by design</b>: it shows the greeting, this
     * month's summary and two big cards ("To-Do" and "Keuangan"). Jumping straight
     * into whichever feature was opened last meant a first-time user never saw that
     * Home existed, and a returning user could not tell which section they were in
     * until they read the header.</p>
     *
     * <p>It takes no destination argument on purpose. The previous signature was
     * {@code routeAfterAuth(activity, lastFeature)}, which made "land on Home" and
     * "resume the last feature" look like a caller's choice when in fact every
     * caller passed the same stored value and always skipped Home.</p>
     *
     * @param activity the activity handing over navigation
     */
    public static void routeAfterAuth(Activity activity) {
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void openFeatureFromHome(Activity activity, String feature) {
        Prefs.setLastFeature(activity, feature);
        activity.startActivity(featureIntent(activity, feature));
    }

    public static void switchFeature(Activity activity, String feature) {
        Prefs.setLastFeature(activity, feature);
        Intent intent = featureIntent(activity, feature);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void goHome(Activity activity) {
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        if (!(activity instanceof HomeActivity)) activity.finish();
    }

    private static Intent featureIntent(Activity activity, String feature) {
        if (FEATURE_FINANCE.equals(feature)) {
            return new Intent(activity, FinanceActivity.class);
        }
        return new Intent(activity, TodoActivity.class);
    }
}
