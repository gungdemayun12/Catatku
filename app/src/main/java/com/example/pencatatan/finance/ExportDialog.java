package com.example.pencatatan.finance;

import android.app.Activity;
import android.content.Context;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.data.model.Transaction;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Small helper that asks the user which format to export and then dispatches to
 * the matching writer. Keeps the two export call sites (reports + settings)
 * perfectly in sync.
 */
public final class ExportDialog {

    public interface Source {
        /** Transactions to export for the given scope. */
        List<Transaction> transactionsFor(boolean wholeHistory);

        /** Human readable period label used in the filename and PDF header. */
        String rangeLabel(boolean wholeHistory);

        /** Base currency code, e.g. {@code IDR}. */
        String baseCurrency();
    }
    private ExportDialog() {
    }

    /** CSV or PDF, always covering the caller's current scope. */
    public static void showFormatPicker(Activity activity, Source source) {
        showFormatPicker(activity, source, false, null);
    }

    /**
     * Builds the format picker.
     *
     * <p>Material3's alert dialog renders {@code setMessage} and {@code setItems} in
     * the same slot, so setting both silently drops the list — the dialog then
     * shows a prompt and a Cancel button but no formats to pick, and the user
     * cannot export at all. The picker therefore relies on {@code setItems}
     * alone: the two format names are self-explanatory, and the explanation
     * lives in {@code R.string.export_format_title}/the empty-data toast.</p>
     */
    private static void showFormatPicker(Activity activity, Source source,
                                         boolean withScope, String monthName) {
        CharSequence[] formats = new CharSequence[]{
                activity.getString(R.string.export_format_pdf),
                activity.getString(R.string.export_format_csv)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.export_format_title)
                .setItems(formats, (dialog, which) -> {
                    boolean pdf = which == 0;
                    if (withScope) {
                        showScopePicker(activity, source, pdf, monthName);
                    } else {
                        showMonthOrRangePicker(activity, source, pdf);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Reports screen: export the open month, or any day range. */
    private static void showMonthOrRangePicker(Activity activity, Source source, boolean pdf) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.export_scope_title)
                .setItems(new CharSequence[]{
                        activity.getString(R.string.export_scope_month),
                        activity.getString(R.string.export_scope_custom)
                }, (dialog, which) -> {
                    if (which == 1) {
                        pickDateRange(activity, source, pdf);
                    } else {
                        export(activity, source, false, pdf);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Format first, then scope (month vs. everything) for the settings screen. */
    public static void showScopedPicker(Activity activity, Source source, String monthName) {
        showFormatPicker(activity, source, true, monthName);
    }

    /**
     * Second step of the settings flow: month-only, a custom day range, or the
     * whole history.
     *
     * <p>The custom option is offered here and not on the reports screen because
     * the reports screen already has a month switcher; adding a third picker
     * there would be redundant. The settings entry point has no month context of
     * its own, so a free range is genuinely useful.</p>
     */
    private static void showScopePicker(Activity activity, Source source, boolean pdf,
                                        String monthName) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.export_scope_title)
                .setItems(new CharSequence[]{
                        activity.getString(R.string.export_scope_month_only, monthName),
                        activity.getString(R.string.export_scope_all),
                        activity.getString(R.string.export_scope_custom)
                }, (scopeDialog, scope) -> {
                    if (scope == 2) {
                        pickDateRange(activity, source, pdf);
                    } else {
                        export(activity, source, scope == 1, pdf);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Material date range picker, then filters by the chosen days.
     *
     * <p>{@link MaterialDatePicker} reports selection in UTC midnight, so the
     * values are converted back to the device time zone before use; without that
     * a user east of UTC can lose the first day and gain one at the end.</p>
     */
    private static void pickDateRange(Activity activity, Source source, boolean pdf) {
        if (!(activity instanceof androidx.fragment.app.FragmentActivity)) {
            // A range picker needs a FragmentManager; without one the option is
            // simply not offered rather than crashing on a plain Activity.
            export(activity, source, true, pdf);
            return;
        }
        androidx.fragment.app.FragmentActivity host =
                (androidx.fragment.app.FragmentActivity) activity;
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.export_range_title)
                        .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) {
                return;
            }
            Calendar start = utcToLocal(selection.first);
            Calendar end = utcToLocal(selection.second);
            List<Transaction> data = FinanceCalc.forRange(
                    source.transactionsFor(true), start, end);
            if (data.isEmpty()) {
                android.widget.Toast.makeText(activity, R.string.report_export_empty,
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String label = Dates.formatShortDate(activity, start.getTime())
                    + " \u2013 " + Dates.formatShortDate(activity, end.getTime());
            dispatch(activity, data, source.baseCurrency(), label, pdf);
        });
        picker.show(host.getSupportFragmentManager(), "export_range");
    }

    /** Material pickers hand back UTC-midnight millis; rebuild them in local time. */
    private static Calendar utcToLocal(Long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local;
    }

    private static void export(Context context, Source source, boolean wholeHistory,
                               boolean pdf) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        List<Transaction> data = source.transactionsFor(wholeHistory);
        if (data == null || data.isEmpty()) {
            android.widget.Toast.makeText(activity, R.string.report_export_empty,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        dispatch(activity, data, source.baseCurrency(), source.rangeLabel(wholeHistory), pdf);
    }

    private static void dispatch(Activity activity, List<Transaction> data, String currency,
                                 String label, boolean pdf) {
        if (pdf) {
            PdfExporter.exportAndShare(activity, data, currency, label);
        } else {
            CsvExporter.exportAndShare(activity, data, currency, label);
        }
    }
}
