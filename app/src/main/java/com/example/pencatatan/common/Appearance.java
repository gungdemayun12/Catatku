package com.example.pencatatan.common;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Applies theme and language before the first screen is shown. */
public final class Appearance {

    private Appearance() {
    }

    public static void applyTheme(String mode) {
        int nightMode;
        if (Prefs.THEME_LIGHT.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (Prefs.THEME_DARK.equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode);
        }
    }

    public static void applyLanguage(String language) {
        LocaleListCompat locales;
        if ("id".equals(language)) {
            locales = LocaleListCompat.forLanguageTags("id-ID");
        } else if ("en".equals(language)) {
            locales = LocaleListCompat.forLanguageTags("en");
        } else {
            locales = LocaleListCompat.getEmptyLocaleList();
        }
        if (!locales.equals(AppCompatDelegate.getApplicationLocales())) {
            AppCompatDelegate.setApplicationLocales(locales);
        }
    }

    public static void applyAll(Context context) {
        applyTheme(Prefs.getTheme(context));
        applyLanguage(Prefs.getLanguage(context));
    }
}
