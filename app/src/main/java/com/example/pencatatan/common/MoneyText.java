package com.example.pencatatan.common;

import android.content.Context;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class MoneyText {

    private MoneyText() {
    }

    public static String format(Context context, String currencyCode, long amount) {
        String code = currencyCode == null || currencyCode.isEmpty() ? "IDR" : currencyCode;
        Locale locale = context.getResources().getConfiguration().locale;
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        try {
            format.setCurrency(Currency.getInstance(code));
        } catch (IllegalArgumentException ignored) {
        }
        int decimals = Money.decimals(code);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(Money.toMajorUnits(code, amount));
    }

    public static String formatSigned(Context context, String currencyCode, long amount, boolean income) {
        String value = format(context, currencyCode, Math.abs(amount));
        return (income ? "+" : "−") + value;
    }

    public static String formatDecimal(double value, int maxFraction) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(maxFraction);
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }

    public static String formatRate(double value) {
        if (value == Math.rint(value)) {
            NumberFormat format = NumberFormat.getNumberInstance();
            format.setMaximumFractionDigits(0);
            format.setGroupingUsed(false);
            return format.format(value);
        }
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(6);
        format.setGroupingUsed(false);
        return format.format(value);
    }
}
