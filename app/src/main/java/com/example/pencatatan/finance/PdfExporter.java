package com.example.pencatatan.finance;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.data.model.Transaction;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a polished, dependency-free PDF statement with {@link PdfDocument}.
 *
 * <p>Layout: brand header band, a summary trio (income / expense / net), a
 * per-category breakdown and a zebra-striped transaction table that paginates
 * automatically. Everything is drawn with plain {@link Paint} so no extra
 * library is needed.</p>
 */
public final class PdfExporter {

    private static final int PAGE_WIDTH = 595;   // A4 @ 72dpi
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 38f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2f;
    private static final float FOOTER_HEIGHT = 34f;

    private static final int INK = 0xFF10231E;
    private static final int INK_SOFT = 0xFF5D6F69;
    private static final int TEAL = 0xFF0F766E;
    private static final int TEAL_DEEP = 0xFF0B4F4A;
    private static final int EMERALD = 0xFF059669;
    private static final int ROSE = 0xFFDC2626;
    private static final int LINE = 0xFFE2E8E6;
    private static final int STRIPE = 0xFFF5FAF8;

    private PdfExporter() {
    }

    /** Builds the PDF and hands it to the system share sheet. */
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
                    "catatku_" + ExportNaming.safe(rangeLabel) + ".pdf");
            write(activity, file, transactions, baseCurrency, rangeLabel);

            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", file);
            ExportShare.share(activity, uri, "application/pdf");
            Toast.makeText(activity, R.string.report_export_done_pdf, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(activity, R.string.report_export_error, Toast.LENGTH_SHORT).show();
        }
    }

    private static void write(Activity activity, File file, List<Transaction> transactions,
                              String baseCurrency, String rangeLabel) throws Exception {
        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparingLong(PdfExporter::timeOf));

        long income = 0;
        long expense = 0;
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Transaction tx : sorted) {
            // A transfer leg must be skipped here for the same reason FinanceCalc
            // skips it: the money never left the user's own accounts, so counting
            // it would report a self-transfer as income *and* spending and make the
            // exported totals disagree with the app's own screens.
            if (tx.isTransfer()) continue;
            if (tx.isIncome()) {
                income += tx.getAmountBase();
            } else {
                expense += tx.getAmountBase();
                String name = tx.getCategoryName() == null || tx.getCategoryName().trim().isEmpty()
                        ? activity.getString(R.string.category_none) : tx.getCategoryName();
                byCategory.put(name, byCategory.getOrDefault(name, 0L) + tx.getAmountBase());
            }
        }
        List<Map.Entry<String, Long>> topCategories = new ArrayList<>(byCategory.entrySet());
        topCategories.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        if (topCategories.size() > 5) {
            long other = 0;
            for (int i = 5; i < topCategories.size(); i++) other += topCategories.get(i).getValue();
            topCategories = new ArrayList<>(topCategories.subList(0, 5));
            if (other > 0) {
                topCategories.add(new java.util.AbstractMap.SimpleEntry<>(
                        activity.getString(R.string.other), other));
            }
        }

        PdfDocument document = new PdfDocument();
        try {
            Pages pages = new Pages(activity, document, baseCurrency, rangeLabel);
            pages.startPage();
            pages.drawHeader(income, expense, income - expense, sorted.size(), topCategories);
            pages.drawTableHeader();
            for (Transaction tx : sorted) {
                pages.drawRow(tx);
            }
            pages.drawTotals(income, expense, income - expense);
            pages.finish();

            try (FileOutputStream out = new FileOutputStream(file)) {
                document.writeTo(out);
            }
        } finally {
            document.close();
        }
    }

    private static long timeOf(Transaction tx) {
        return tx.getDate() == null ? 0L : tx.getDate().toDate().getTime();
    }

    /** Mutable paginating writer. */
    private static final class Pages {
        private final Activity activity;
        private final PdfDocument document;
        private final String currency;
        private final String rangeLabel;

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        private PdfDocument.Page page;
        private Canvas canvas;
        private float y;
        private int pageIndex;

        Pages(Activity activity, PdfDocument document, String currency, String rangeLabel) {
            this.activity = activity;
            this.document = document;
            this.currency = currency;
            this.rangeLabel = rangeLabel;

            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(0.8f);
            stroke.setColor(LINE);

            text.setColor(INK);
            text.setTextSize(9f);

            bold.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        }

        void startPage() {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, ++pageIndex).create();
            page = document.startPage(info);
            canvas = page.getCanvas();
            y = MARGIN;
            drawFooter();
        }

        void finish() {
            if (page != null) document.finishPage(page);
            page = null;
            canvas = null;
        }

        private void newPageIfNeeded(float needed) {
            if (y + needed <= PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) return;
            document.finishPage(page);
            startPage();
            drawTableHeader();
        }

        private void drawFooter() {
            Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
            soft.setColor(INK_SOFT);
            soft.setTextSize(7.5f);
            soft.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(activity.getString(R.string.pdf_footer), MARGIN,
                    PAGE_HEIGHT - 20f, soft);
            soft.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.US, "%d", pageIndex),
                    PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 20f, soft);
        }

        void drawHeader(long income, long expense, long net, int count,
                        List<Map.Entry<String, Long>> topCategories) {
            // Brand band with a teal → emerald gradient built from stacked strips.
            float bandHeight = 74f;
            int steps = 48;
            Paint band = new Paint();
            for (int i = 0; i < steps; i++) {
                float fraction = (float) i / steps;
                band.setColor(blend(TEAL_DEEP, EMERALD, fraction));
                canvas.drawRect(MARGIN, y + bandHeight / steps * i,
                        PAGE_WIDTH - MARGIN, y + bandHeight / steps * (i + 1), band);
            }

            Paint brand = new Paint(Paint.ANTI_ALIAS_FLAG);
            brand.setColor(Color.WHITE);
            brand.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            brand.setTextSize(19f);
            canvas.drawText(activity.getString(R.string.app_name), MARGIN + 16f, y + 30f, brand);

            Paint subtitle = new Paint(Paint.ANTI_ALIAS_FLAG);
            subtitle.setColor(0xCCEAFBF6);
            subtitle.setTextSize(9.5f);
            canvas.drawText(activity.getString(R.string.pdf_report_title)
                    + " \u2022 " + rangeLabel, MARGIN + 16f, y + 47f, subtitle);
            canvas.drawText(activity.getString(R.string.pdf_generated,
                    Dates.formatDateTime(activity, Calendar.getInstance().getTime())),
                    MARGIN + 16f, y + 61f, subtitle);

            Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            countPaint.setColor(Color.WHITE);
            countPaint.setTextAlign(Paint.Align.RIGHT);
            countPaint.setTextSize(10f);
            countPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            canvas.drawText(activity.getResources().getQuantityString(
                            R.plurals.plural_transactions, count, count),
                    PAGE_WIDTH - MARGIN - 16f, y + 34f, countPaint);

            y += bandHeight + 18f;

            // Summary trio
            float cardTop = y;
            float cardHeight = 54f;
            float gap = 8f;
            float cardWidth = (CONTENT_WIDTH - gap * 2f) / 3f;
            drawStatCard(MARGIN, cardTop, cardWidth, cardHeight,
                    activity.getString(R.string.report_total_income),
                    MoneyText.format(activity, currency, income), EMERALD);
            drawStatCard(MARGIN + cardWidth + gap, cardTop, cardWidth, cardHeight,
                    activity.getString(R.string.report_total_expense),
                    MoneyText.format(activity, currency, expense), ROSE);
            drawStatCard(MARGIN + (cardWidth + gap) * 2f, cardTop, cardWidth, cardHeight,
                    activity.getString(R.string.finance_net_flow),
                    MoneyText.formatSigned(activity, currency, net, net >= 0),
                    net >= 0 ? EMERALD : ROSE);
            y = cardTop + cardHeight + 16f;

            // Per-category breakdown
            if (!topCategories.isEmpty()) {
                drawSectionTitle(activity.getString(R.string.pdf_breakdown));
                long totalExpense = Math.max(1L, expense);
                for (Map.Entry<String, Long> entry : topCategories) {
                    // newPageIfNeeded can swap the canvas out mid-loop, so it must be
                    // called *before* any paint of this row is drawn; drawing first
                    // and paging after would leave a row clipped at the page break.
                    newPageIfNeeded(26f);
                    float share = entry.getValue() / (float) totalExpense;
                    text.setColor(INK);
                    text.setTextSize(8.5f);
                    canvas.drawText(ellipsize(entry.getKey(), 210f, text),
                            MARGIN, y + 8f, text);
                    Paint amountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    amountPaint.setColor(INK_SOFT);
                    amountPaint.setTextSize(8.5f);
                    amountPaint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(MoneyText.format(activity, currency, entry.getValue())
                                    + "  " + Math.round(share * 100) + "%",
                            PAGE_WIDTH - MARGIN, y + 8f, amountPaint);

                    box.set(MARGIN, y + 12f, PAGE_WIDTH - MARGIN, y + 16f);
                    fill.setColor(0xFFEDF4F2);
                    canvas.drawRoundRect(box, 2f, 2f, fill);
                    box.set(MARGIN, y + 12f, MARGIN + CONTENT_WIDTH * share, y + 16f);
                    fill.setColor(blend(TEAL, EMERALD, share));
                    canvas.drawRoundRect(box, 2f, 2f, fill);
                    y += 24f;
                }
                y += 6f;
            }
        }

        private void drawStatCard(float left, float top, float width, float height,
                                  String label, String value, int accent) {
            box.set(left, top, left + width, top + height);
            fill.setColor(0xFFF6FAF9);
            canvas.drawRoundRect(box, 7f, 7f, fill);
            // A private paint, not the shared `stroke`: mutating the shared one here
            // leaked an ink colour into every table separator drawn afterwards.
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(0.8f);
            border.setColor(LINE);
            canvas.drawRoundRect(box, 7f, 7f, border);

            Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            accentPaint.setColor(accent);
            box.set(left, top, left + 3.5f, top + height);
            canvas.drawRoundRect(box, 2f, 2f, accentPaint);

            Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            captionPaint.setColor(INK_SOFT);
            captionPaint.setTextSize(7.5f);
            captionPaint.setLetterSpacing(0f);
            canvas.drawText(label.toUpperCase(Locale.getDefault()),
                    left + 12f, top + 18f, captionPaint);

            Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valuePaint.setColor(accent);
            valuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            float size = 12.5f;
            valuePaint.setTextSize(size);
            while (valuePaint.measureText(value) > width - 22f && size > 7.5f) {
                size -= 0.5f;
                valuePaint.setTextSize(size);
            }
            canvas.drawText(value, left + 12f, top + 38f, valuePaint);
        }

        private void drawSectionTitle(String title) {
            newPageIfNeeded(30f);
            Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setColor(INK);
            titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            titlePaint.setTextSize(10.5f);
            canvas.drawText(title, MARGIN, y + 4f, titlePaint);
            y += 14f;
        }

        void drawTableHeader() {
            newPageIfNeeded(34f);
            box.set(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f);
            fill.setColor(TEAL_DEEP);
            canvas.drawRoundRect(box, 4f, 4f, fill);

            Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
            head.setColor(Color.WHITE);
            head.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            head.setTextSize(8f);

            canvas.drawText(activity.getString(R.string.csv_date), MARGIN + 8f, y + 14.5f, head);
            canvas.drawText(activity.getString(R.string.csv_category), columnX(1), y + 14.5f, head);
            canvas.drawText(activity.getString(R.string.csv_wallet), columnX(2), y + 14.5f, head);
            canvas.drawText(activity.getString(R.string.csv_note), columnX(3), y + 14.5f, head);

            head.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(activity.getString(R.string.csv_base_amount),
                    PAGE_WIDTH - MARGIN - 8f, y + 14.5f, head);
            head.setTextAlign(Paint.Align.LEFT);
            y += 28f;
        }

        void drawRow(Transaction tx) {
            newPageIfNeeded(20f);
            float rowHeight = 19f;
            int index = rowIndex++;

            if (index % 2 == 1) {
                box.set(MARGIN, y - 4f, PAGE_WIDTH - MARGIN, y - 4f + rowHeight);
                fill.setColor(STRIPE);
                canvas.drawRoundRect(box, 3f, 3f, fill);
            }

            float baseline = y + 9f;
            boolean income = tx.isIncome();
            Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
            cell.setTextSize(8f);
            cell.setColor(INK);
            canvas.drawText(formatDate(tx), MARGIN + 8f, baseline, cell);

            cell.setColor(income ? EMERALD : INK);
            canvas.drawText(ellipsize(safe(tx.getCategoryName()), columnWidth(1) - 10f, cell),
                    columnX(1), baseline, cell);

            cell.setColor(INK_SOFT);
            canvas.drawText(ellipsize(safe(tx.getWalletName()), columnWidth(2) - 10f, cell),
                    columnX(2), baseline, cell);
            canvas.drawText(ellipsize(safe(tx.getNote()), columnWidth(3) - 10f, cell),
                    columnX(3), baseline, cell);

            cell.setColor(income ? EMERALD : ROSE);
            cell.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            cell.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText((income ? "+" : "-") + MoneyText.format(activity, currency,
                            tx.getAmountBase()), PAGE_WIDTH - MARGIN - 8f, baseline, cell);
            cell.setTextAlign(Paint.Align.LEFT);

            stroke.setColor(LINE);
            canvas.drawLine(MARGIN, y + rowHeight - 4f, PAGE_WIDTH - MARGIN,
                    y + rowHeight - 4f, stroke);
            y += rowHeight;
        }

        private int rowIndex;

        void drawTotals(long income, long expense, long net) {
            newPageIfNeeded(60f);
            y += 6f;
            box.set(MARGIN, y, PAGE_WIDTH - MARGIN, y + 46f);
            fill.setColor(0xFFF0F7F5);
            canvas.drawRoundRect(box, 6f, 6f, fill);

            Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
            label.setColor(INK_SOFT);
            label.setTextSize(8.5f);
            label.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            canvas.drawText(activity.getString(R.string.pdf_grand_total), MARGIN + 12f, y + 15f, label);

            Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
            value.setTextSize(9f);
            value.setTextAlign(Paint.Align.RIGHT);

            value.setColor(EMERALD);
            canvas.drawText(activity.getString(R.string.report_total_income) + "  "
                            + MoneyText.format(activity, currency, income),
                    PAGE_WIDTH - MARGIN - 12f, y + 15f, value);
            value.setColor(ROSE);
            canvas.drawText(activity.getString(R.string.report_total_expense) + "  "
                            + MoneyText.format(activity, currency, expense),
                    PAGE_WIDTH - MARGIN - 12f, y + 28f, value);
            value.setColor(net >= 0 ? EMERALD : ROSE);
            value.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            canvas.drawText(activity.getString(R.string.finance_net_flow) + "  "
                            + MoneyText.formatSigned(activity, currency, net, net >= 0),
                    PAGE_WIDTH - MARGIN - 12f, y + 41f, value);
            value.setTextAlign(Paint.Align.LEFT);
            y += 46f;
        }

        private float columnX(int column) {
            float[] widths = {0.20f, 0.22f, 0.20f, 0.38f};
            float x = MARGIN + 8f;
            for (int i = 0; i < column; i++) {
                x += CONTENT_WIDTH * widths[i];
            }
            return x;
        }

        private float columnWidth(int column) {
            if (column == 0) return CONTENT_WIDTH * 0.20f;
            if (column == 1) return CONTENT_WIDTH * 0.22f;
            if (column == 2) return CONTENT_WIDTH * 0.20f;
            return CONTENT_WIDTH * 0.38f;
        }

        private String formatDate(Transaction tx) {
            if (tx.getDate() == null) return "-";
            // formatShortDate drops the year ("d MMM"), which is fine on a screen
            // that already shows the month but wrong in a printed statement: a
            // 12-month export would print "3 Feb" twice for two different years.
            return Dates.formatDayMonthYear(activity, tx.getDate().toDate());
        }

        private String safe(String value) {
            if (value == null) return "";
            return value.replace('\n', ' ').replace('\r', ' ').trim();
        }

        private String ellipsize(String value, float maxWidth, Paint paint) {
            if (value == null || value.isEmpty()) return "";
            if (paint.measureText(value) <= maxWidth) return value;
            String ellipsis = "\u2026";
            int cut = value.length();
            while (cut > 1 && paint.measureText(value.substring(0, cut) + ellipsis) > maxWidth) {
                cut--;
            }
            return value.substring(0, cut) + ellipsis;
        }

        private static int blend(int from, int to, float fraction) {
            float clamped = Math.max(0f, Math.min(1f, fraction));
            int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * clamped);
            int g = Math.round(Color.green(from)
                    + (Color.green(to) - Color.green(from)) * clamped);
            int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped);
            return Color.rgb(r, g, b);
        }
    }

    /** Shared file-name sanitiser for CSV/PDF exports. */
    static final class ExportNaming {
        private ExportNaming() {
        }

        static String safe(String value) {
            if (value == null || value.trim().isEmpty()) return "export";
            return value.replaceAll("[^a-zA-Z0-9_-]", "_");
        }
    }

}
