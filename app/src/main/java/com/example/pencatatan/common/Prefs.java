package com.example.pencatatan.common;

import android.content.Context;
import android.content.SharedPreferences;

/** Small local preferences: UI mirrors, last feature, and one-time alert flags. */
public final class Prefs {

    private static final String FILE = "catatku_prefs";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String LANGUAGE_SYSTEM = "system";
    public static final String FEATURE_TODO = "todo";
    public static final String FEATURE_FINANCE = "finance";
    public static final String SORT_DUE = "due";
    public static final String SORT_PRIORITY = "priority";
    public static final String SORT_CREATED = "created";
    public static final String SORT_TITLE = "title";
    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_INCOME = "income";

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String getTheme(Context context) {
        return prefs(context).getString("theme", THEME_SYSTEM);
    }

    public static void setTheme(Context context, String theme) {
        prefs(context).edit().putString("theme", theme).apply();
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString("language", LANGUAGE_SYSTEM);
    }

    public static void setLanguage(Context context, String language) {
        prefs(context).edit().putString("language", language).apply();
    }

    public static String getLastFeature(Context context) {
        return prefs(context).getString("lastFeature", FEATURE_TODO);
    }

    public static void setLastFeature(Context context, String feature) {
        prefs(context).edit().putString("lastFeature", feature).apply();
    }

    public static boolean notificationsEnabled(Context context) {
        return prefs(context).getBoolean("notificationsEnabled", true);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("notificationsEnabled", enabled).apply();
    }

    public static void setMirroredProfile(Context context, String lastFeature, String theme,
                                          String language, boolean notifications, String currency) {
        prefs(context).edit()
                .putString("lastFeature", lastFeature == null ? FEATURE_TODO : lastFeature)
                .putString("theme", theme == null ? THEME_SYSTEM : theme)
                .putString("language", language == null ? LANGUAGE_SYSTEM : language)
                .putBoolean("notificationsEnabled", notifications)
                .putString("currency", currency == null ? "IDR" : currency)
                .apply();
    }

    public static String getCurrency(Context context) {
        return prefs(context).getString("currency", "IDR");
    }

    public static void setCurrency(Context context, String currency) {
        prefs(context).edit().putString("currency", currency).apply();
    }

    /** True once the given threshold has already been alerted for that month. */
    public static boolean hasAlertedBudget(Context context, String monthKey, String categoryId, int threshold) {
        return prefs(context).getBoolean(
                "budget_alert_" + monthKey + "_" + categoryId + "_" + threshold, false);
    }

    /**
     * Claims the right to show a budget alert: returns {@code true} exactly once
     * per category/month/threshold.
     *
     * <p>Callers must only invoke this <b>after</b> the notification was actually
     * posted — claiming first and then failing (e.g. permission denied) burned
     * the flag and the alert never appeared again that month.</p>
     */
    public static boolean shouldAlertBudget(Context context, String monthKey, String categoryId, int threshold) {
        String key = "budget_alert_" + monthKey + "_" + categoryId + "_" + threshold;
        if (prefs(context).getBoolean(key, false)) return false;
        prefs(context).edit().putBoolean(key, true).apply();
        return true;
    }

    public static boolean wasNotifPromptShown(Context context) {
        return prefs(context).getBoolean("notif_prompt_shown", false);
    }

    public static void markNotifPromptShown(Context context) {
        prefs(context).edit().putBoolean("notif_prompt_shown", true).apply();
    }

    // ----- To-do preferences -----

    public static int getDefaultPriority(Context context) {
        return prefs(context).getInt("todo_default_priority", 1);
    }

    public static void setDefaultPriority(Context context, int priority) {
        prefs(context).edit().putInt("todo_default_priority", priority).apply();
    }

    public static String getDefaultCategoryId(Context context) {
        return prefs(context).getString("todo_default_category_id", null);
    }

    public static String getDefaultCategoryName(Context context) {
        return prefs(context).getString("todo_default_category_name", null);
    }

    public static void setDefaultCategory(Context context, String id, String name) {
        prefs(context).edit()
                .putString("todo_default_category_id", id)
                .putString("todo_default_category_name", name)
                .apply();
    }

    public static String getTaskSort(Context context) {
        return prefs(context).getString("todo_sort", SORT_DUE);
    }

    public static void setTaskSort(Context context, String sort) {
        prefs(context).edit().putString("todo_sort", sort).apply();
    }

    public static boolean showCompletedTasks(Context context) {
        return prefs(context).getBoolean("todo_show_completed", true);
    }

    public static void setShowCompletedTasks(Context context, boolean show) {
        prefs(context).edit().putBoolean("todo_show_completed", show).apply();
    }

    /** Group the task list into Overdue / Today / Upcoming / No date sections. */
    public static boolean groupTasks(Context context) {
        return prefs(context).getBoolean("todo_group_tasks", true);
    }

    public static void setGroupTasks(Context context, boolean group) {
        prefs(context).edit().putBoolean("todo_group_tasks", group).apply();
    }

    /** Day offset used by the "snooze" action on an overdue task. */
    public static int snoozeDays(Context context) {
        return prefs(context).getInt("todo_snooze_days", 3);
    }

    public static void setSnoozeDays(Context context, int days) {
        prefs(context).edit().putInt("todo_snooze_days", days).apply();
    }

    /** Days the "Stale" smart filter considers old enough to resurface. */
    public static int staleDays(Context context) {
        return prefs(context).getInt("todo_stale_days", 14);
    }

    public static void setStaleDays(Context context, int days) {
        prefs(context).edit().putInt("todo_stale_days", days).apply();
    }

    /** Increments once per completed task so a streak can be recomputed cheaply. */
    public static int completedCount(Context context) {
        return prefs(context).getInt("todo_completed_count", 0);
    }

    public static void setCompletedCount(Context context, int count) {
        prefs(context).edit().putInt("todo_completed_count", count).apply();
    }

    // ----- Finance preferences -----

    public static String getDefaultTransactionType(Context context) {
        return prefs(context).getString("finance_default_type", TYPE_EXPENSE);
    }

    public static void setDefaultTransactionType(Context context, String type) {
        prefs(context).edit().putString("finance_default_type", type).apply();
    }

    public static String getDefaultWalletId(Context context) {
        return prefs(context).getString("finance_default_wallet_id", null);
    }

    public static String getDefaultWalletName(Context context) {
        return prefs(context).getString("finance_default_wallet_name", null);
    }

    public static void setDefaultWallet(Context context, String id, String name) {
        prefs(context).edit()
                .putString("finance_default_wallet_id", id)
                .putString("finance_default_wallet_name", name)
                .apply();
    }

    public static boolean budgetAlertsEnabled(Context context) {
        return prefs(context).getBoolean("finance_budget_alerts", true);
    }

    public static void setBudgetAlertsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("finance_budget_alerts", enabled).apply();
    }

    // ----- General preferences -----

    public static boolean hapticEnabled(Context context) {
        return prefs(context).getBoolean("haptic_enabled", true);
    }

    public static void setHapticEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("haptic_enabled", enabled).apply();
    }

    public static void clearSession(Context context) {
        String theme = getTheme(context);
        String language = getLanguage(context);
        prefs(context).edit().clear().apply();
        setTheme(context, theme);
        setLanguage(context, language);
    }
}
