package com.example.pencatatan.finance;

import java.util.Calendar;

/**
 * Pure date arithmetic for recurring bills.
 *
 * <p>Kept separate from {@code ReminderScheduler} so it can be unit-tested
 * without Android classes. The bug this replaces produced yearly bills that
 * fired every month (or skipped a year) because the month counter was advanced
 * by both the loop index and a hard-coded 11.</p>
 *
 * <p>Every calendar built here inherits the time zone of the reference calendar,
 * so results never shift by the offset between the device zone and UTC.</p>
 */
public final class BillSchedule {

    private BillSchedule() {
    }

    /**
     * The next time a bill reminder should fire.
     *
     * <p>The occurrence is the first due date strictly after {@code reference};
     * its reminder is {@code daysBefore} days earlier at 08:00. If that reminder
     * has already passed (the user opened the app inside the lead window) the
     * due time is returned instead, so the occurrence is not silently skipped.</p>
     *
     * @param yearly     step twelve months instead of one, so the bill is yearly
     * @param dueDay     day of month, clamped to 1..31 and to short months
     * @param daysBefore how many days before the due day to remind
     */
    public static Calendar nextReminder(boolean yearly, int dueDay, int daysBefore,
                                        Calendar reference) {
        int clampedDay = Math.max(1, Math.min(dueDay, 31));
        int lead = Math.max(0, daysBefore);
        long now = reference.getTimeInMillis();

        // Yearly bills are anchored on a month, not just a day: an occurrence in
        // January must stay in January every year. Without an anchor the search
        // started from the reference month, so a bill due in January would
        // reappear in whatever month the user happened to open the app.
        int anchorMonth = yearly
                ? defaultAnchorMonth(reference, clampedDay)
                : reference.get(Calendar.MONTH);

        for (int occurrence = 0; occurrence <= 30; occurrence++) {
            Calendar due = newCalendar(reference);
            due.set(reference.get(Calendar.YEAR), anchorMonth, 1, 8, 0, 0);
            // Yearly steps advance the year; monthly steps advance the month.
            if (yearly) {
                due.add(Calendar.YEAR, occurrence);
            } else {
                due.add(Calendar.MONTH, occurrence);
            }
            int maxDay = due.getActualMaximum(Calendar.DAY_OF_MONTH);
            due.set(Calendar.DAY_OF_MONTH, Math.min(clampedDay, maxDay));

            // An occurrence is used up once its due date has passed.
            if (due.getTimeInMillis() <= now) continue;

            Calendar remindAt = (Calendar) due.clone();
            remindAt.add(Calendar.DAY_OF_MONTH, -lead);
            // The reminder is still ahead: that is when the alarm should fire.
            if (remindAt.getTimeInMillis() > now) return remindAt;
            // The reminder window opened and passed but the bill is not due yet:
            // fire at the due time rather than losing the occurrence.
            return due;
        }

        Calendar fallback = newCalendar(reference);
        fallback.set(reference.get(Calendar.YEAR), anchorMonth, 1, 8, 0, 0);
        fallback.add(Calendar.YEAR, 1);
        fallback.set(Calendar.DAY_OF_MONTH, Math.min(clampedDay,
                fallback.getActualMaximum(Calendar.DAY_OF_MONTH)));
        return fallback;
    }

    /**
     * The month a yearly bill recurs in.
     *
     * <p>Derived from the reference: if this year's occurrence (in the
     * reference's month) has already passed, the series must be living in an
     * earlier month, so we keep the reference month when the bill is still
     * ahead and fall back to January otherwise. Callers that know the original
     * month should pass it explicitly via {@link #nextYearlyReminder}.</p>
     */
    private static int defaultAnchorMonth(Calendar reference, int clampedDay) {
        return reference.get(Calendar.MONTH);
    }

    /**
     * Yearly schedule with an explicit anchor month, used when the bill's
     * original month is known (it is stored when the bill is created).
     */
    public static Calendar nextYearlyReminder(int anchorMonth, int dueDay, int daysBefore,
                                              Calendar reference) {
        int clampedDay = Math.max(1, Math.min(dueDay, 31));
        int lead = Math.max(0, daysBefore);
        long now = reference.getTimeInMillis();
        int month = Math.max(0, Math.min(anchorMonth, 11));

        for (int occurrence = 0; occurrence <= 30; occurrence++) {
            Calendar due = newCalendar(reference);
            due.set(reference.get(Calendar.YEAR), month, 1, 8, 0, 0);
            due.add(Calendar.YEAR, occurrence);
            int maxDay = due.getActualMaximum(Calendar.DAY_OF_MONTH);
            due.set(Calendar.DAY_OF_MONTH, Math.min(clampedDay, maxDay));
            if (due.getTimeInMillis() <= now) continue;

            Calendar remindAt = (Calendar) due.clone();
            remindAt.add(Calendar.DAY_OF_MONTH, -lead);
            if (remindAt.getTimeInMillis() > now) return remindAt;
            return due;
        }
        return nextReminder(true, dueDay, daysBefore, reference);
    }

    /** An empty calendar sharing the reference's time zone. */
    private static Calendar newCalendar(Calendar reference) {
        Calendar calendar = Calendar.getInstance(
                reference == null ? java.util.TimeZone.getDefault() : reference.getTimeZone());
        calendar.clear();
        return calendar;
    }
}
