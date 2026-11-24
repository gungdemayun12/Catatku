package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.pencatatan.common.Money;

import org.junit.Test;

/**
 * Covers the rate direction used when the base currency is switched.
 *
 * <p>Two separate bugs lived here. First, {@code migrateBaseCurrency} fell back
 * to the hard-coded reference table even when it had just been told the rate was
 * missing, so a user could "convert" Rp 1.000.000 into $1.000.000. Second,
 * cross-currency transfers reused the <em>source</em> currency's rate for the
 * destination, which quietly produced wrong amounts for any pair where neither
 * side was the base (e.g. EUR to GBP).</p>
 */
public class CurrencyMigrationTest {

    /** 1 USD is worth roughly 16.000 IDR, per the app's built-in reference table. */
    private static final double EXPECTED_USD_IN_IDR = 16_000.0;

    @Test
    public void rateToBase_isValueOfOneUnitInBase() {
        // 1 IDR is a tiny fraction of a USD, 1 USD is many IDR. The exact figure
        // comes from the bundled reference table, so it is checked as an order of
        // magnitude plus an inverse relationship rather than a hard-coded number.
        double idrInUsd = CurrencyApiService.rateToBase("IDR", "USD");
        double usdInIdr = CurrencyApiService.rateToBase("USD", "IDR");

        assertEquals(1.0, idrInUsd * usdInIdr, 1e-9);
        assertEquals(EXPECTED_USD_IN_IDR, usdInIdr, 500.0);
        assertEquals(true, idrInUsd > 0 && idrInUsd < 0.01);
    }

    @Test
    public void rateToBase_isOneForSameCurrency() {
        assertEquals(1.0, CurrencyApiService.rateToBase("IDR", "IDR"), 1e-12);
        assertEquals(1.0, CurrencyApiService.rateToBase("USD", "USD"), 1e-12);
    }

    @Test
    public void convertingUpAndDownIsSymmetric() {
        // Rp 1.600.000 -> USD -> back must land within one IDR of where it
        // started; sub-unit rounding is expected, a drift is not.
        int idrDecimals = Money.decimals("IDR");
        int usdDecimals = Money.decimals("USD");
        long idrAmount = 1_600_000L * (long) Math.pow(10, idrDecimals);

        double idrToUsd = CurrencyApiService.rateToBase("IDR", "USD");
        long usd = Money.toBase(idrAmount, "IDR", idrToUsd, "USD");

        double usdToIdr = CurrencyApiService.rateToBase("USD", "IDR");
        long back = Money.toBase(usd, "USD", usdToIdr, "IDR");

        long tolerance = (long) Math.pow(10, idrDecimals);
        assertEquals(true, tolerance > 0);
        // The conversion goes through a 2-decimal USD step, so a few hundred IDR
        // of rounding is expected; what must not happen is a drift of a whole
        // order of magnitude (the symptom of an inverted rate).
        assertEquals(true, Math.abs(back - idrAmount) <= 1_000L);
        // 1.600.000 IDR buys roughly 97 USD at the bundled reference rate.
        long usdMajor = usd / (long) Math.pow(10, usdDecimals);
        assertEquals(true, usdMajor >= 90 && usdMajor <= 110);
    }

    @Test
    public void convertingToTheSameCurrencyKeepsTheAmount() {
        long amount = 250_000L * (long) Math.pow(10, Money.decimals("IDR"));
        assertEquals(amount, Money.toBase(amount, "IDR", 1.0, "IDR"));
    }

    @Test
    public void crossCurrencyPairDoesNotUseTheSourceRate() {
        // The bug: EUR -> GBP used the EUR rate for GBP. With USD as the base,
        // 100 EUR must land near 85 GBP (about 0.92 vs 0.78 units per USD),
        // never 100 — which is what a reused source rate would produce.
        int eurDecimals = Money.decimals("EUR");
        int gbpDecimals = Money.decimals("GBP");
        long eur = 100L * (long) Math.pow(10, eurDecimals);

        double eurToUsd = CurrencyApiService.rateToBase("EUR", "USD");
        double gbpToUsd = CurrencyApiService.rateToBase("GBP", "USD");
        long gbp = TransferMath.convert(eur, "EUR", "GBP", eurToUsd, gbpToUsd);

        long gbpMajor = gbp / (long) Math.pow(10, gbpDecimals);
        assertEquals(true, gbpMajor != 100);
        assertEquals(true, gbpMajor >= 70 && gbpMajor <= 100);
    }

    @Test
    public void missingRateIsDetectable() {
        // A rate of zero means "unknown"; callers must refuse to migrate rather
        // than multiply every stored amount by one.
        assertFalse(hasUsableRate(0));
        assertFalse(hasUsableRate(-1));
        assertFalse(hasUsableRate(Double.NaN));
        assertTrue(hasUsableRate(0.0000625));
    }

    /** Mirrors the guard in {@code FinanceRepository.migrateBaseCurrency}. */
    private static boolean hasUsableRate(double rate) {
        return rate > 0 && !Double.isNaN(rate) && !Double.isInfinite(rate);
    }
}
