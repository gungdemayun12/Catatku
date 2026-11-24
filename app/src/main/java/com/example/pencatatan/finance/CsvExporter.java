package com.example.pencatatan.finance;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.data.model.Transaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Writes transactions to a shareable UTF-8 (BOM) CSV with a tidy layout:
 * a summary block, the transaction table and a totals row at the end.
 *
 * <p>Every cell is escaped so the file opens cleanly in Excel, Numbers and
 * Google Sheets — even when notes contain commas, quotes or semicolons.</p>
 */
public final class CsvExporter {

    private CsvExporter() {
    }

    public static void exportAndShare(Activity activity, List<Transaction> transactions,
                                      String baseCurrency, String rangeLabel) {
        if (transactions == null || transactions.isEmpty()) {
            Toast.makeText(activity, R.string.report_export_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File directory = new File(activity.getCacheDir(), "exports");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("mkdirs");
            }
            File file = new File(directory,
                    "catatku_" + PdfExporter.ExportNaming.safe(rangeLabel) + ".csv");
            write(activity, file, transactions, baseCurrency, rangeLabel);

            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", file);
            ExportShare.share(activity, uri, "text/csv");
            Toast.makeText(activity, R.string.report_export_done_csv, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(activity, R.string.report_export_error, Toast.LENGTH_SHORT).show();
        }
    }

    private static void write(Activity activity, File file, List<Transaction> transactions,
                             String baseCurrency, String rangeLabel) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparingLong(tx -> tx.getDate() == null ? 0L
                : tx.getDate().toDate().getTime()));

        long income = 0;
        long expense = 0;
        for (Transaction tx : sorted) {
            // Transfers move the user's own money, so they are neither income nor
            // spending. Skipping them keeps these totals identical to the app's
            // Finance screen, which excludes them too.
            if (tx.isTransfer()) continue;
            if (tx.isIncome()) income += tx.getAmountBase();
            else expense += tx.getAmountBase();
        }
        long net = income - expense;

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            // BOM so Excel detects UTF-8 (important for Indonesian text).
            writer.write('\uFEFF');

            // ---- Summary block ------------------------------------------------
            writer.write(csv(activity.getString(R.string.app_name)));
            writer.write(",");
            writer.write(csv(activity.getString(R.string.pdf_report_title)));
            writer.newLine();
            writer.write(csv(activity.getString(R.string.csv_export_period)));
            writer.write(",");
            writer.write(csv(rangeLabel));
            writer.newLine();
            writer.write(csv(activity.getString(R.string.pdf_generated,
                    Dates.formatDateTime(activity, new Date()))));
            writer.newLine();
            writer.newLine();

            writer.write(csv(activity.getString(R.string.csv_summary)));
            writer.newLine();
            writer.write(csv(activity.getString(R.string.csv_total_income)));
            writer.write(",");
            writer.write(decimal(income, baseCurrency));
            writer.newLine();
            writer.write(csv(activity.getString(R.string.csv_total_expense)));
            writer.write(",");
            writer.write(decimal(expense, baseCurrency));
            writer.newLine();
            writer.write(csv(activity.getString(R.string.csv_net)));
            writer.write(",");
            writer.write(decimal(net, baseCurrency));
            writer.newLine();
            writer.newLine();

            // ---- Table --------------------------------------------------------
            writer.write(csv(activity.getString(R.string.csv_section_transactions)));
            writer.newLine();
            writer.write(String.join(",",
                    activity.getString(R.string.csv_date),
                    activity.getString(R.string.csv_type),
                    activity.getString(R.string.csv_category),
                    activity.getString(R.string.csv_wallet),
                    activity.getString(R.string.csv_amount),
                    activity.getString(R.string.csv_currency),
                    activity.getString(R.string.csv_rate),
                    activity.getString(R.string.csv_base_amount),
                    activity.getString(R.string.csv_note)));
            writer.newLine();

            for (Transaction transaction : sorted) {
                Date date = transaction.getDate() == null ? null : transaction.getDate().toDate();
                writer.write(String.join(",",
                        csv(date == null ? "" : dateFormat.format(date)),
                        csv(activity.getString(transaction.isIncome()
                                ? R.string.finance_income : R.string.finance_expense)),
                        csv(transaction.getCategoryName()),
                        csv(transaction.getWalletName()),
                        decimal(transaction.getAmount(), transaction.getCurrency()),
                        csv(transaction.getCurrency()),
                        rate(transaction.getExchangeRate()),
                        decimal(transaction.getAmountBase(), baseCurrency),
                        csv(transaction.getNote())));
                writer.newLine();
            }

            // ---- Totals -------------------------------------------------------
            writer.write(String.join(",",
                    csv(activity.getString(R.string.pdf_grand_total)),
                    "", "", "", "", "", "",
                    decimal(net, baseCurrency),
                    csv(activity.getResources().getQuantityString(
                            R.plurals.csv_export_count_plural, sorted.size(), sorted.size()))));
            writer.newLine();
        }
    }

    private static String decimal(long amount, String currency) {
        int decimals = Money.decimals(currency);
        return String.format(Locale.US, "%." + decimals + "f",
                Money.toMajorUnits(currency, amount));
    }

    private static String rate(double value) {
        return String.format(Locale.US, "%.6f", value).replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    /** Quotes a cell when needed and strips anything that would break the grid. */
    private static String csv(String value) {
        if (value == null) return "";
        String clean = value.replace("\r", " ").replace("\n", " ").trim();
        if (clean.contains(",") || clean.contains("\"") || clean.contains(";")) {
            return "\"" + clean.replace("\"", "\"\"") + "\"";
        }
        return clean;
    }
}
