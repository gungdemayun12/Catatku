package com.example.pencatatan.common;

import android.content.Context;

import java.util.Currency;

public final class Money {

    private Money() {
    }

    public static final String[] SUPPORTED = {
            "IDR", "USD", "EUR", "GBP", "JPY", "SGD", "MYR", "AUD", "CAD", "CNY",
            "HKD", "INR", "KRW", "THB", "PHP", "VND", "SAR", "AED", "CHF", "NZD", "TWD"
    };

    /** Rupiah and yen are shown without decimals even though java.util.Currency differs. */
    public static int decimals(String code) {
        if (code == null) return 0;
        if ("IDR".equals(code) || "JPY".equals(code) || "KRW".equals(code) || "VND".equals(code)) {
            return 0;
        }
        try {
            int digits = Currency.getInstance(code).getDefaultFractionDigits();
            return Math.max(digits, 0);
        } catch (IllegalArgumentException e) {
            return 2;
        }
    }

    public static long toMinorUnits(String code, double major) {
        int decimals = decimals(code);
        return Math.round(major * Math.pow(10, decimals));
    }

    public static double toMajorUnits(String code, long amount) {
        return amount / Math.pow(10, decimals(code));
    }

    public static String symbol(Context context, String code) {
        try {
            return Currency.getInstance(code).getSymbol(context.getResources().getConfiguration().locale);
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    /**
     * amountBase = round(amount / 10^decimals(currency) * rate * 10^decimals(base))
     */
    public static long toBase(long amount, String currency, double rate, String base) {
        if (currency == null || currency.equals(base)) return amount;
        double major = toMajorUnits(currency, amount);
        double baseMajor = major * rate;
        return toMinorUnits(base, baseMajor);
    }

    public static String countryFlag(String code) {
        if (code == null) return "";
        switch (code) {
            case "IDR":
                return "🇮🇩";
            case "USD":
                return "🇺🇸";
            case "EUR":
                return "🇪🇺";
            case "GBP":
                return "🇬🇧";
            case "JPY":
                return "🇯🇵";
            case "SGD":
                return "🇸🇬";
            case "MYR":
                return "🇲🇾";
            case "AUD":
                return "🇦🇺";
            case "CAD":
                return "🇨🇦";
            case "CNY":
                return "🇨🇳";
            case "HKD":
                return "🇭🇰";
            case "INR":
                return "🇮🇳";
            case "KRW":
                return "🇰🇷";
            case "THB":
                return "🇹🇭";
            case "PHP":
                return "🇵🇭";
            case "VND":
                return "🇻🇳";
            case "SAR":
                return "🇸🇦";
            case "AED":
                return "🇦🇪";
            case "CHF":
                return "🇨🇭";
            case "NZD":
                return "🇳🇿";
            case "TWD":
                return "🇹🇼";
            default:
                return "";
        }
    }
}
