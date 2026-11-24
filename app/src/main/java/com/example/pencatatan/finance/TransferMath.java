package com.example.pencatatan.finance;

import com.example.pencatatan.common.Money;

/**
 * Currency maths for moving money between two wallets.
 *
 * <p>Extracted so it can be unit-tested without Firestore. The bug this fixes:
 * the incoming leg of a transfer kept the source amount verbatim, so moving
 * Rp 1.600.000 into a USD wallet credited 1.600.000 USD.</p>
 */
public final class TransferMath {

    private TransferMath() {
    }

    /** True when no conversion is needed. */
    public static boolean sameCurrency(String a, String b) {
        return a != null && a.equals(b);
    }

    /**
     * Converts {@code amount} from one currency to another.
     *
     * @param rate            value of one source unit expressed in the base
     *                        currency (e.g. 1 IDR = 0.0000625 USD when the base
     *                        is USD); ≤ 0 means "unknown, do not convert"
     * @param toCurrencyRate  the same measure for the destination currency
     */
    public static long convert(long amount, String fromCurrency, String toCurrency,
                               double rate, double toCurrencyRate) {
        if (sameCurrency(fromCurrency, toCurrency)) return amount;
        if (rate <= 0) return amount;
        // Both rates are "value in base", so their quotient is the direct rate.
        double targetRate = toCurrencyRate > 0 ? toCurrencyRate : rate;
        double fromMajor = amount / majorScale(fromCurrency);
        double baseMajor = fromMajor * rate;
        double toMajor = baseMajor / targetRate;
        long converted = Math.round(toMajor * majorScale(toCurrency));
        // Never let a real amount disappear entirely through rounding.
        return converted <= 0 ? 1 : converted;
    }

    private static double majorScale(String currency) {
        return Math.pow(10, Money.decimals(currency));
    }

    /** Rate implied by a completed conversion, used for the incoming leg. */
    public static double impliedRate(long amount, String fromCurrency,
                                     long incomingAmount, String toCurrency) {
        if (sameCurrency(fromCurrency, toCurrency) || incomingAmount <= 0) return 1.0;
        double fromMajor = amount / majorScale(fromCurrency);
        double toMajor = incomingAmount / majorScale(toCurrency);
        return fromMajor <= 0 ? 1.0 : toMajor / fromMajor;
    }
}
