package com.example.pencatatan.finance;

import android.os.Handler;
import android.os.Looper;

import com.example.pencatatan.common.Money;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calls the FreeCurrencyAPI to fetch live exchange rates.
 * API key: fca_live_1t6EVSM4g5muyM84tQPIilmvymLFirwY9fJTokOc
 *
 * Usage:
 *   CurrencyApiService.fetchRates("IDR", new CurrencyApiService.RateCallback() { … });
 */
public final class CurrencyApiService {

    private static final String API_KEY = "fca_live_1t6EVSM4g5muyM84tQPIilmvymLFirwY9fJTokOc";
    private static final String BASE_URL = "https://api.freecurrencyapi.com/v1/latest";

    /**
     * Created lazily on purpose.
     *
     * <p>Static initialisers that touch {@code Looper.getMainLooper()} throw
     * outside an Android runtime, which made the pure rate maths in this class
     * untestable — and untestable maths is how the inverted-rate bug survived.
     * Nothing but {@link #fetchRates} needs these, so they are created on first
     * use instead of at class-load time.</p>
     */
    private static ExecutorService executor;
    private static Handler mainHandler;

    private static synchronized ExecutorService executor() {
        if (executor == null) executor = Executors.newSingleThreadExecutor();
        return executor;
    }

    private static synchronized Handler mainHandler() {
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        return mainHandler;
    }

    public interface RateCallback {
        /** rates: map of currency code → "how many of baseCurrency = 1 of foreign currency" (rateToBase) */
        void onRates(Map<String, Double> rates);
        void onError(String message);
    }

    private CurrencyApiService() {}

    /**
     * Default USD fallback rates (1 USD = X foreign currency).
     * Used when API is offline or when specific currency is not supplied by API.
     */
    public static Map<String, Double> getFallbackUsdRates() {
        Map<String, Double> map = new HashMap<>();
        map.put("USD", 1.0);
        map.put("IDR", 16300.0);
        map.put("EUR", 0.92);
        map.put("GBP", 0.78);
        map.put("JPY", 155.0);
        map.put("SGD", 1.35);
        map.put("MYR", 4.40);
        map.put("AUD", 1.50);
        map.put("CAD", 1.38);
        map.put("CNY", 7.25);
        map.put("HKD", 7.80);
        map.put("INR", 83.5);
        map.put("KRW", 1380.0);
        map.put("THB", 36.5);
        map.put("PHP", 58.5);
        map.put("VND", 25400.0);
        map.put("SAR", 3.75);
        map.put("AED", 3.67);
        map.put("CHF", 0.89);
        map.put("NZD", 1.65);
        map.put("TWD", 32.5);
        return map;
    }

    /** 1 unit of {@code currency} = {@code result} units of the base currency. */
    public static double toBase(String currency, String baseCurrency) {
        if (currency == null || baseCurrency == null) return 1.0;
        if (currency.equalsIgnoreCase(baseCurrency)) return 1.0;

        Map<String, Double> usdRates = getFallbackUsdRates();
        Double currencyInUnits = usdRates.get(currency.toUpperCase());
        Double baseInUnits = usdRates.get(baseCurrency.toUpperCase());

        if (baseInUnits == null || baseInUnits <= 0) baseInUnits = 1.0;
        if (currencyInUnits == null || currencyInUnits <= 0) currencyInUnits = 1.0;

        // Both figures are "units per 1 USD", which is the inverse of a value:
        // IDR 16.000 per USD means 1 IDR is worth 1/16.000 USD. So the rate of
        // one currency in base units is basePerUsd / currencyPerUsd.
        return baseInUnits / currencyInUnits;
    }

    /**
     * Rate of one {@code currency} unit expressed in the base currency.
     *
     * <p>Numerically identical to {@link #getFallbackRate} but named after what
     * it returns, because the two directions are easy to mix up: converting
     * {@code to} the base must <b>multiply</b> by this number, never divide.</p>
     */
    public static double rateToBase(String currency, String baseCurrency) {
        return toBase(currency, baseCurrency);
    }

    /**
     * Calculates fallback rateToBase (1 foreign = X baseCurrency) using USD triangulation.
     */
    public static double getFallbackRate(String currency, String baseCurrency) {
        return toBase(currency, baseCurrency);
    }

    /**
     * Fetches live rates relative to [baseCurrency].
     * Result map contains: 1 foreignCurrency = X baseCurrency (rateToBase).
     */
    public static void fetchRates(String baseCurrency, RateCallback callback) {
        executor().execute(() -> {
            String safeBase = (baseCurrency == null || baseCurrency.trim().isEmpty())
                    ? "IDR" : baseCurrency.trim().toUpperCase();
            try {
                // Try fetching directly with base_currency
                Map<String, Double> rateToBase = tryFetchDirect(safeBase);
                if (rateToBase != null && !rateToBase.isEmpty()) {
                    mainHandler().post(() -> callback.onRates(rateToBase));
                    return;
                }

                // If direct fetch fails (e.g. HTTP 422 for unsupported base like VND), try USD base
                Map<String, Double> usdConverted = tryFetchUsdBase(safeBase);
                if (usdConverted != null && !usdConverted.isEmpty()) {
                    mainHandler().post(() -> callback.onRates(usdConverted));
                    return;
                }

                // If network fails or API produces empty rates, use fallbacks
                Map<String, Double> fallbackMap = buildFallbackRateMap(safeBase);
                mainHandler().post(() -> callback.onRates(fallbackMap));

            } catch (Exception e) {
                // Return fallback map even on error so user is never blocked
                Map<String, Double> fallbackMap = buildFallbackRateMap(safeBase);
                mainHandler().post(() -> callback.onRates(fallbackMap));
            }
        });
    }

    private static Map<String, Double> tryFetchDirect(String baseCurrency) {
        try {
            String urlStr = BASE_URL + "?apikey=" + API_KEY + "&base_currency=" + baseCurrency;
            JSONObject data = makeApiCall(urlStr);
            if (data == null) return null;

            Map<String, Double> rateToBase = new HashMap<>();

            // Calculate rateToBase: 1 foreign = (1 / apiRate) baseCurrency
            java.util.Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next().toUpperCase();
                if (key.equalsIgnoreCase(baseCurrency)) continue;
                double apiRate = data.optDouble(key, 0);
                if (apiRate > 0) {
                    rateToBase.put(key, 1.0 / apiRate);
                }
            }

            // Fill missing currencies using USD triangulation fallback
            fillMissingRates(rateToBase, baseCurrency);

            return rateToBase;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Double> tryFetchUsdBase(String baseCurrency) {
        try {
            String urlStr = BASE_URL + "?apikey=" + API_KEY + "&base_currency=USD";
            JSONObject data = makeApiCall(urlStr);
            if (data == null) return null;

            Map<String, Double> usdRates = getFallbackUsdRates();
            java.util.Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next().toUpperCase();
                double apiRate = data.optDouble(key, 0);
                if (apiRate > 0) {
                    usdRates.put(key, apiRate);
                }
            }

            return convertUsdRatesToBase(usdRates, baseCurrency);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject makeApiCall(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", API_KEY);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();

        JSONObject root = new JSONObject(sb.toString());
        return root.optJSONObject("data");
    }

    private static Map<String, Double> convertUsdRatesToBase(Map<String, Double> usdRates, String baseCurrency) {
        Map<String, Double> rateToBase = new HashMap<>();
        Double baseUsd = usdRates.get(baseCurrency);
        if (baseUsd == null || baseUsd <= 0) baseUsd = 1.0;

        for (String code : Money.SUPPORTED) {
            if (code.equalsIgnoreCase(baseCurrency)) continue;
            Double foreignUsd = usdRates.get(code);
            if (foreignUsd == null || foreignUsd <= 0) {
                foreignUsd = getFallbackUsdRates().getOrDefault(code, 1.0);
            }
            if (foreignUsd != null && foreignUsd > 0) {
                rateToBase.put(code, baseUsd / foreignUsd);
            }
        }
        return rateToBase;
    }

    private static void fillMissingRates(Map<String, Double> rateToBase, String baseCurrency) {
        for (String code : Money.SUPPORTED) {
            if (code.equalsIgnoreCase(baseCurrency)) continue;
            Double existing = rateToBase.get(code);
            if (!rateToBase.containsKey(code) || existing == null || existing <= 0) {
                rateToBase.put(code, getFallbackRate(code, baseCurrency));
            }
        }
    }

    private static Map<String, Double> buildFallbackRateMap(String baseCurrency) {
        Map<String, Double> rateToBase = new HashMap<>();
        for (String code : Money.SUPPORTED) {
            if (code.equalsIgnoreCase(baseCurrency)) continue;
            rateToBase.put(code, getFallbackRate(code, baseCurrency));
        }
        return rateToBase;
    }
}


