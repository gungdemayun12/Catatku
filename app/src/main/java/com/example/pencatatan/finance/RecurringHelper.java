package com.example.pencatatan.finance;

import com.example.pencatatan.common.Dates;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.google.firebase.Timestamp;

import java.util.Calendar;

/**
 * The arithmetic and bookkeeping behind recurring transactions.
 *
 * <p>Two things live here. {@link #calculateNext} is pure date maths, used both
 * when a series is created and when a copy is generated. {@link #generateDue}
 * is the catch-up step: when the app opens, any series whose next date has
 * passed is materialised into real transactions. Generating on demand (rather
 * than with a server job) means the feature works with no backend and no
 * background permissions — the cost is that the copies appear when the user
 * next opens Finance, which is exactly when they would look at them anyway.</p>
 */
public final class RecurringHelper {

    /**
     * Safety valve: never generate more than this many copies in one pass.
     *
     * <p>The cap is deliberately generous. A user who leaves the app closed for
     * two years of a weekly series is owed 100+ rows; a low cap silently dropped
     * the remainder and then advanced the template past them, losing them for
     * good. Anything above the cap is simply produced on the next pass.</p>
     */
    private static final int MAX_CATCH_UP = 366;

    private RecurringHelper() {
    }

    /**
     * The next occurrence after {@code from}, preserving the day-of-month the
     * series was anchored to.
     *
     * <p>Calendar {@code add()} clamps "31st in February" to the 28th, but it
     * clamps permanently: stepping again from the 28th yields the 28th in
     * March, and the series drifts for the rest of its life. To avoid that we
     * remember the anchor day and restore it after every step, so a payment due
     * on the 31st lands on 28/29 Feb and then returns to the 31st in March.</p>
     *
     * @param anchorDay day-of-month to restore (1-31); values outside that
     *                  range mean "no anchor", falling back to plain clamping
     */
    public static Timestamp calculateNext(Calendar from, String repeatType) {
        return calculateNext(from, repeatType, 0);
    }

    public static Timestamp calculateNext(Calendar from, String repeatType, int anchorDay) {
        if (from == null || repeatType == null || "none".equals(repeatType)) return null;
        Calendar next = (Calendar) from.clone();
        switch (repeatType) {
            case "daily":
                next.add(Calendar.DAY_OF_MONTH, 1);
                return new Timestamp(next.getTime());
            case "weekly":
                next.add(Calendar.DAY_OF_MONTH, 7);
                return new Timestamp(next.getTime());
            case "monthly":
            case "yearly":
                break;
            default:
                return null;
        }

        // Month/year steps are computed on the day-of-month pinned to 1 first.
        // Adding a month to Jan 31 would otherwise land on Feb 28 *before* the
        // anchor is applied, and a Feb 29 yearly series would become Feb 28
        // forever. Pinning to day 1 makes the month arithmetic exact and lets
        // the anchor be restored afterwards, clamped only by the target month's
        // real length.
        boolean useAnchor = anchorDay >= 1 && anchorDay <= 31;
        int day = useAnchor ? anchorDay : next.get(Calendar.DAY_OF_MONTH);
        next.set(Calendar.DAY_OF_MONTH, 1);
        if ("monthly".equals(repeatType)) {
            next.add(Calendar.MONTH, 1);
        } else {
            next.add(Calendar.YEAR, 1);
        }
        int maxDay = next.getActualMaximum(Calendar.DAY_OF_MONTH);
        next.set(Calendar.DAY_OF_MONTH, Math.min(day, maxDay));
        return new Timestamp(next.getTime());
    }

    /** The day-of-month a calendar is currently parked on. */
    public static int dayOf(Calendar calendar) {
        return calendar == null ? 0 : calendar.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Creates every occurrence that is due as of now.
     *
     * <p>Each generated copy keeps the series' template (amount, wallet,
     * category, repeat group) but gets its own date and month key so it shows up
     * in the correct month's totals. The template row's {@code repeatNextDate}
     * always points at the next occurrence still to come.</p>
     */
    public static void generateDue(String uid, FinanceRepository repository,
                                   Transaction template) {
        if (template == null || !template.isRepeating()) return;
        if (template.getRepeatNextDate() == null) return;
        if (template.getDocId() == null) return;

        long now = System.currentTimeMillis();
        Timestamp endStamp = template.getRepeatEndDate();
        long endLimit = endStamp == null ? Long.MAX_VALUE : endStamp.toDate().getTime();

        Calendar next = Calendar.getInstance();
        next.setTime(template.getRepeatNextDate().toDate());
        // Older rows predate the anchor field; derive it from the next date so
        // their series stops drifting from this version onwards.
        int anchorDay = template.getRepeatAnchorDay();
        if (anchorDay < 1 && ("monthly".equals(template.getRepeatType())
                || "yearly".equals(template.getRepeatType()))) {
            anchorDay = dayOf(next);
        }

        Timestamp lastGenerated = null;
        int generated = 0;
        while (next.getTimeInMillis() <= now
                && next.getTimeInMillis() <= endLimit
                && generated < MAX_CATCH_UP) {

            Transaction copy = new Transaction();
            // Copy the "what", not the "when": identity fields are new.
            copy.setType(template.getType());
            copy.setAmount(template.getAmount());
            copy.setCurrency(template.getCurrency());
            copy.setExchangeRate(template.getExchangeRate());
            copy.setAmountBase(template.getAmountBase());
            copy.setWalletId(template.getWalletId());
            copy.setWalletName(template.getWalletName());
            copy.setCategoryId(template.getCategoryId());
            copy.setCategoryName(template.getCategoryName());
            copy.setNote(template.getNote());
            copy.setDate(new Timestamp(next.getTime()));
            copy.setMonthKey(Dates.monthKey(next.getTime()));
            copy.setRepeatGroup(template.getRepeatGroup());
            copy.setRepeatType(template.getRepeatType());
            copy.setRepeatEndDate(template.getRepeatEndDate());
            copy.setRepeatAnchorDay(anchorDay);
            copy.setGenerated(true);
            copy.setRepeatNextDate(null);
            // A deterministic id makes this step idempotent: two passes over the
            // same occurrence write the same document instead of adding a
            // duplicate transaction the user never made.
            copy.setDocId(occurrenceId(template, next.getTime()));

            repository.saveTransaction(uid, copy, false, new FinanceRepository.SaveCallback() {
                @Override
                public void onSuccess(String id) {
                }

                @Override
                public void onError(Exception error) {
                }
            });

            lastGenerated = new Timestamp(next.getTime());
            generated++;
            next = calendarOf(calculateNext(next, template.getRepeatType(), anchorDay));
            if (next == null) break;
        }

        if (lastGenerated != null) {
            // Advance the template so the same copies are never created twice.
            Calendar following = calendarOf(
                    calculateNext(calendarOf(lastGenerated), template.getRepeatType(), anchorDay));
            template.setRepeatAnchorDay(anchorDay);
            template.setRepeatNextDate(following == null ? null
                    : new Timestamp(following.getTime()));
            if (following != null && following.getTimeInMillis() > endLimit) {
                template.setRepeatType("none");
                template.setRepeatNextDate(null);
            }
            repository.saveTransaction(uid, template, false,
                    new FinanceRepository.SaveCallback() {
                        @Override
                        public void onSuccess(String id) {
                        }

                        @Override
                        public void onError(Exception error) {
                        }
                    });
        }
    }

    /**
     * Stable document id for one generated occurrence of a series.
     *
     * <p>Derived from the template id plus the occurrence date, so re-running the
     * catch-up (app restart, two devices, a retried write) overwrites the same
     * row instead of creating a duplicate.</p>
     */
    static String occurrenceId(Transaction template, java.util.Date occurrence) {
        String group = template.getDocId() == null ? "" : template.getDocId();
        String when = occurrence == null ? "none"
                : new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                .format(occurrence);
        String raw = "gen_" + group + "_" + when;
        // Firestore ids may not contain '/'; the hash keeps it short and safe.
        return raw.replace("/", "_") + "_" + Integer.toHexString(raw.hashCode());
    }

    private static Calendar calendarOf(Timestamp stamp) {
        if (stamp == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(stamp.toDate());
        return calendar;
    }
}
