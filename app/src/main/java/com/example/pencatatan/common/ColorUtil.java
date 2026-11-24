package com.example.pencatatan.common;

import android.content.Context;
import android.graphics.Color;

public final class ColorUtil {

    private ColorUtil() {
    }

    public static final String[] PALETTE = {
            "#00796B", "#3949AB", "#00897B", "#5C6BC0", "#D97757",
            "#EF6C00", "#8E24AA", "#00838F", "#43A047", "#C62828"
    };

    public static int parse(String hex, int fallback) {
        if (hex == null || hex.trim().isEmpty()) return fallback;
        try {
            String value = hex.trim();
            if (!value.startsWith("#")) value = "#" + value;
            if (value.length() == 7) return Color.parseColor(value);
            if (value.length() == 9) return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
        }
        return fallback;
    }

    public static String normalize(String hex, String fallback) {
        if (hex == null || !hex.matches("^#[0-9a-fA-F]{6}$")) return fallback;
        return hex.toUpperCase();
    }

    public static int withAlpha(String hex, float alpha) {
        int color = parse(hex, Color.GRAY);
        return Color.argb((int) (alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int contrastOn(String hex) {
        int color = parse(hex, Color.GRAY);
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.62 ? Color.parseColor("#1B1F1E") : Color.WHITE;
    }

    public static int priorityColor(int priority) {
        switch (priority) {
            case 0:
                return Color.parseColor("#43A047");
            case 2:
                return Color.parseColor("#C62828");
            default:
                return Color.parseColor("#F57F17");
        }
    }
}
