package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Guards the recurring-bill schedule.
 *
 * <p>The regression this covers: a yearly bill advanced its month by both the
 * loop index and a hard-coded eleven, so it fired twelve times a year or skipped
 * a year entirely.</p>
 *
 * <p>All references are built in UTC and results are read in UTC, so the
 * assertions do not depend on the machine's time zone.</p>
 */
public class BillScheduleTest {

    private static Calendar at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar;
    }

    /** Formats an instant in UTC, independent of the default time zone. */
    private static String key(Calendar calendar) {
        Calendar inUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        inUtc.setTimeInMillis(calendar.getTimeInMillis());
        return String.format("%04d-%02d-%02d", inUtc.get(Calendar.YEAR),
                inUtc.get(Calendar.MONTH) + 1, inUtc.get(Calendar.DAY_OF_MONTH));
    }

    // ------------------------------------------------------------------ yearly

    @Test
    public void yearlyBill_firesOnceAYear() {
        // February bill, due on the 20th, reminder 5 days before.
        Calendar first = BillSchedule.nextYearlyReminder(1, 20, 5, at(2026, 1, 1, 12, 0));
        assertEquals("2026-02-15", key(first));

        // After the February occurrence was paid, the next one is a full YEAR
        // later — not next month, and not two years later.
        assertEquals("2027-02-15",
                key(BillSchedule.nextYearlyReminder(1, 20, 5, at(2026, 3, 1, 12, 0))));
        assertEquals("2028-02-15",
                key(BillSchedule.nextYearlyReminder(1, 20, 5, at(2027, 3, 1, 12, 0))));
    }

    @Test
    public void yearlyBill_keepsItsMonthWhateverMonthTheUserOpensIt() {
        // A January bill must stay in January: opening the app in October must
        // not push the next reminder to October.
        assertEquals("2027-01-01",
                key(BillSchedule.nextYearlyReminder(0, 1, 0, at(2026, 10, 5, 12, 0))));
        assertEquals("2027-01-15",
                key(BillSchedule.nextYearlyReminder(0, 20, 5, at(2026, 10, 5, 12, 0))));
    }

    @Test
    public void yearlyBill_crossesNewYearCorrectly() {
        // Due 1 Jan, reminded 5 days before: on 20 Dec 2026 the upcoming
        // occurrence is 1 Jan 2027, whose reminder is 27 Dec 2026.
        assertEquals("2026-12-27",
                key(BillSchedule.nextYearlyReminder(0, 1, 5, at(2026, 12, 20, 9, 0))));
        // Once January 2027 has passed, the next reminder is a year later.
        assertEquals("2027-12-27",
                key(BillSchedule.nextYearlyReminder(0, 1, 5, at(2027, 1, 5, 9, 0))));
    }

    @Test
    public void yearlyBill_noReminderLead_landsOnTheDueDate() {
        assertEquals("2027-01-01",
                key(BillSchedule.nextYearlyReminder(0, 1, 0, at(2026, 12, 31, 9, 0))));
    }

    // ----------------------------------------------------------------- monthly

    @Test
    public void monthlyBill_stepsOneMonth() {
        Calendar first = BillSchedule.nextReminder(false, 20, 5, at(2026, 2, 2, 9, 0));
        assertEquals("2026-02-15", key(first));
        // Standing exactly at the reminder instant consumes it; the due time comes
        // next, then the following month's reminder.
        assertEquals("2026-02-20", key(BillSchedule.nextReminder(false, 20, 5, first)));
        assertEquals("2026-03-15",
                key(BillSchedule.nextReminder(false, 20, 5, at(2026, 2, 21, 9, 0))));
    }

    @Test
    public void dueDay31_clampsToShortMonth_andRecovers() {
        // Due on the 31st: February cannot hold it, so it falls on the 28th...
        Calendar next = BillSchedule.nextReminder(false, 31, 0, at(2026, 1, 31, 9, 0));
        assertEquals("2026-02-28", key(next));
        // ...and March gets the 31st back instead of drifting to the 28th forever.
        assertEquals("2026-03-31", key(BillSchedule.nextReminder(false, 31, 0, next)));
    }

    @Test
    public void monthlyOccurrences_neverSkipAMonth() {
        // Walking the schedule forward visits every consecutive month. This is
        // exactly what the old yearly-month arithmetic broke.
        Calendar cursor = at(2026, 1, 21, 12, 0);
        int previousMonth = 0;
        for (int i = 0; i < 23; i++) {
            cursor = BillSchedule.nextReminder(false, 15, 3, cursor);
            Calendar inUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            inUtc.setTimeInMillis(cursor.getTimeInMillis());
            assertEquals("step " + i + " must visit the next month",
                    (previousMonth + 1) % 12, inUtc.get(Calendar.MONTH));
            previousMonth = inUtc.get(Calendar.MONTH);
            // Move just past this occurrence before asking again.
            cursor = at(inUtc.get(Calendar.YEAR), inUtc.get(Calendar.MONTH) + 1, 16, 12, 0);
        }
    }

    @Test
    public void reminderIsAlwaysInTheFuture() {
        Calendar reference = at(2026, 6, 15, 12, 0);
        for (int dueDay = 1; dueDay <= 31; dueDay++) {
            for (int lead = 0; lead <= 10; lead++) {
                Calendar monthly = BillSchedule.nextReminder(false, dueDay, lead, reference);
                assertTrue("monthly due " + dueDay + "/lead " + lead + " must be future",
                        monthly.getTimeInMillis() > reference.getTimeInMillis());
                Calendar yearly = BillSchedule.nextYearlyReminder(0, dueDay, lead, reference);
                assertTrue("yearly due " + dueDay + "/lead " + lead + " must be future",
                        yearly.getTimeInMillis() > reference.getTimeInMillis());
            }
        }
    }
}
