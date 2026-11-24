package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers cross-currency transfers.
 *
 * <p>Before the fix the incoming leg kept the source amount verbatim, so
 * Rp 1.600.000 moved into a USD wallet credited 1.600.000 USD — a balance that
 * was wrong by several orders of magnitude.</p>
 *
 * <p>The expected values are derived from {@code Money.decimals()} instead of
 * hard-coded, because the JVM's own {@code java.util.Currency} data may report
 * a different decimal count for IDR than the app's explicit rule does.</p>
 */
public class TransferMathTest {

    private static final int FROM_DECIMALS = com.example.pencatatan.common.Money.decimals("IDR");
    private static final int TO_DECIMALS = com.example.pencatatan.common.Money.decimals("USD");
    /** 1 USD is worth 16.000 IDR. */
    private static final double USD_TO_IDR = 16000.0;

    /** Value of one unit of {@code currency} expressed in the base currency. */
    private static double rateToBase(double unitsPerUsd) {
        return 1.0 / unitsPerUsd;
    }

    private static long minor(long major, int decimals) {
        return major * (long) Math.pow(10, decimals);
    }

    @Test
    public void idrToUsd_convertsDown() {
        // 1.600.000 IDR is 100 USD, whatever the minor-unit scale is.
        long idr = minor(1_600_000L, FROM_DECIMALS);
        long usd = TransferMath.convert(idr, "IDR", "USD",
                rateToBase(USD_TO_IDR), 1.0);
        assertEquals(minor(100L, TO_DECIMALS), usd);
    }

    @Test
    public void usdToIdr_convertsUp() {
        long usd = minor(100L, TO_DECIMALS);
        long idr = TransferMath.convert(usd, "USD", "IDR",
                1.0, rateToBase(USD_TO_IDR));
        assertEquals(minor(1_600_000L, FROM_DECIMALS), idr);
    }

    @Test
    public void sameCurrency_isUnchanged() {
        long amount = minor(250_000L, FROM_DECIMALS);
        assertEquals(amount, TransferMath.convert(amount, "IDR", "IDR", 1.0, 1.0));
    }

    @Test
    public void roundTripIsStable() {
        long idr = minor(1_600_000L, FROM_DECIMALS);
        long usd = TransferMath.convert(idr, "IDR", "USD", rateToBase(USD_TO_IDR), 1.0);
        long back = TransferMath.convert(usd, "USD", "IDR", 1.0, rateToBase(USD_TO_IDR));
        assertEquals(idr, back);
    }

    @Test
    public void zeroRate_fallsBackToFaceValue() {
        // An unknown rate must not zero the balance out.
        long amount = minor(75_000L, FROM_DECIMALS);
        assertEquals(amount, TransferMath.convert(amount, "IDR", "USD", 0, 0));
    }

    @Test
    public void tinyAmount_neverBecomesZero() {
        assertTrue(TransferMath.convert(1L, "IDR", "USD", rateToBase(USD_TO_IDR), 1.0) > 0);
    }

    @Test
    public void impliedRate_matchesTheConversion() {
        long idr = minor(1_600_000L, FROM_DECIMALS);
        long usd = TransferMath.convert(idr, "IDR", "USD", rateToBase(USD_TO_IDR), 1.0);
        // 100 USD per 1.600.000 IDR.
        assertEquals(100.0 / 1_600_000.0, TransferMath.impliedRate(idr, "IDR", usd, "USD"), 1e-9);
    }

    @Test
    public void impliedRate_isOneForSameCurrency() {
        assertEquals(1.0, TransferMath.impliedRate(1000L, "IDR", 1000L, "IDR"), 1e-9);
    }
}
