package com.example.pencatatan.common;

import android.content.Context;
import android.text.InputFilter;
import android.text.Spanned;
import android.widget.EditText;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/** Formats a money field live, respecting the wallet currency's decimal count. */
public final class AmountInput {

    public interface CurrencyCodeProvider {
        String get();
    }

    private AmountInput() {
    }

    public static void attach(EditText editText, CurrencyCodeProvider provider) {
        attach(editText, provider, null);
    }

    public static void attach(EditText editText, CurrencyCodeProvider provider, Runnable onChanged) {
        final boolean[] updating = {false};
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        editText.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable editable) {
                if (updating[0]) return;
                String code = provider.get();
                if (code == null) code = "IDR";
                String formatted = format(editText.getContext(), editable.toString(), code);
                if (!formatted.equals(editable.toString())) {
                    updating[0] = true;
                    editText.setText(formatted);
                    editText.setSelection(formatted.length());
                    updating[0] = false;
                }
                if (onChanged != null) onChanged.run();
            }
        });
    }

    public static String format(Context context, String raw, String currency) {
        if (raw == null) raw = "";
        int decimals = Money.decimals(currency);
        Locale locale = context.getResources().getConfiguration().locale;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        char group = symbols.getGroupingSeparator();
        char decimal = symbols.getDecimalSeparator();

        StringBuilder digits = new StringBuilder();
        boolean seenDecimal = false;
        int decimalCount = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                if (seenDecimal) {
                    if (decimalCount < decimals) {
                        digits.append(c);
                        decimalCount++;
                    }
                } else {
                    digits.append(c);
                }
            } else if (c == group) {
                // Ignore the locale's grouping separator that we inserted ourselves.
                continue;
            } else if (c == decimal || c == '.' || c == ',') {
                if (decimals > 0 && !seenDecimal) {
                    seenDecimal = true;
                    digits.append('.');
                    decimalCount = 0;
                }
            }
        }
        String value = digits.toString();
        int dot = value.indexOf('.');
        String integerPart = dot >= 0 ? value.substring(0, dot) : value;
        String fraction = dot >= 0 ? value.substring(dot + 1) : "";
        integerPart = integerPart.replaceFirst("^0+(?=\\d)", "");
        if (integerPart.isEmpty()) integerPart = "0";

        StringBuilder grouped = new StringBuilder();
        int counter = 0;
        for (int i = integerPart.length() - 1; i >= 0; i--) {
            grouped.insert(0, integerPart.charAt(i));
            counter++;
            if (counter % 3 == 0 && i > 0) grouped.insert(0, group);
        }
        if (decimals > 0 && (seenDecimal || !fraction.isEmpty())) {
            grouped.append(decimal);
            for (int i = 0; i < fraction.length(); i++) grouped.append(fraction.charAt(i));
        }
        return grouped.toString();
    }

    /** Formats an amount stored in minor units using the app locale's separators. */
    public static String formatMinor(Context context, String currency, long amount) {
        String code = currency == null || currency.isEmpty() ? "IDR" : currency;
        int decimals = Money.decimals(code);
        Locale locale = context.getResources().getConfiguration().locale;
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(Money.toMajorUnits(code, amount));
    }

    public static long parse(Context context, String text, String currency) {
        if (text == null) return 0L;
        Locale locale = context.getResources().getConfiguration().locale;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        String value = text;
        value = value.replace(String.valueOf(symbols.getGroupingSeparator()), "");
        value = value.replace(String.valueOf(symbols.getDecimalSeparator()), ".");
        value = value.replaceAll("[^0-9.]", "");
        if (value.isEmpty() || value.equals(".")) return 0L;
        try {
            double major = Double.parseDouble(value);
            return Money.toMinorUnits(currency, major);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static boolean isValidPositive(Context context, String text, String currency) {
        return parse(context, text, currency) > 0;
    }

    /** A TextWatcher with empty defaults, so callers only override what they need. */
    public abstract static class SimpleWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    public static class DigitsOnly implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            StringBuilder builder = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (Character.isDigit(source.charAt(i))) builder.append(source.charAt(i));
            }
            return builder.toString();
        }
    }
}
