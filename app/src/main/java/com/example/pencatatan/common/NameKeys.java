package com.example.pencatatan.common;

import android.content.Context;

/** Resolves localized names for built-in categories and wallets via their nameKey. */
public final class NameKeys {

    private NameKeys() {
    }

    public static String resolve(Context context, String name, String nameKey) {
        if (nameKey != null && !nameKey.trim().isEmpty()) {
            int id = context.getResources().getIdentifier(nameKey, "string", context.getPackageName());
            if (id != 0) return context.getString(id);
        }
        return name == null ? "" : name;
    }
}
