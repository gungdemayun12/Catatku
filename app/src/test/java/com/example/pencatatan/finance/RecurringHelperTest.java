package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Guards the date maths behind recurring transactions.
 *
 * <p>The case that matters here is a series anchored on the 31st: February
 * clamps it to the 28th, and the series must return to the 31st in March
 * instead of drifting to the 28th forever.</p>
 */
public class RecurringHelperTest {

    private static Calendar calendarOf(int year, int monthIndex, int day) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, monthIndex, day, 8, 0, 0);
        return c;
    }

    private static int dayOf(Timestamp stamp) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTime(stamp.toDate());
        return c.get(Calendar.DAY_OF_MONTH);
    }

    @Test
    public void monthlyFromThe31stReturnsToThe31stAfterFebruary() {
        int anchor = 31;
        Timestamp feb = RecurringHelper.calculateNext(
                calendarOf(2026, Calendar.JANUARY, 31), "monthly", anchor);
        assertEquals(28, dayOf(feb));

        Timestamp mar = RecurringHelper.calculateNext(
                calendarOf(2026, Calendar.FEBRUARY, 28), "monthly", anchor);
        assertEquals("March must go back to the 31st", 31, dayOf(mar));

        Timestamp apr = RecurringHelper.calculateNext(
                calendarOf(2026, Calendar.MARCH, 31), "monthly", anchor);
        assertEquals(30, dayOf(apr));
    }

    @Test
    public void withoutAnchorTheOldBehaviourStaysClamped() {
        // Documents why the anchor field exists: plain stepping drifts.
        Timestamp feb = RecurringHelper.calculateNext(
                calendarOf(2026, Calendar.JANUARY, 31), "monthly", 0);
        assertEquals(28, dayOf(feb));
        Timestamp mar = RecurringHelper.calculateNext(
                calendarOf(2026, Calendar.FEBRUARY, 28), "monthly", 0);
        assertEquals("this is the bug the anchor fixes", 28, dayOf(mar));
    }

    @Test
    public void weeklyAndDailyIgnoreTheAnchor() {
        Timestamp next = RecurringHelper.calculateNext(
                calendarOf(2026, Calendar.JANUARY, 31), "weekly", 31);
        assertEquals(7, dayOf(next));
    }

    @Test
    public void yearlyFromThe29thOfFebruary() {
        // 2026 and 2027 are not leap years, so the series pauses on the 28th.
        Timestamp next = RecurringHelper.calculateNext(
                calendarOf(2024, Calendar.FEBRUARY, 29), "yearly", 29);
        assertEquals(28, dayOf(next));
        // ...but the anchor still holds: the next leap year gets the 29th back.
        Timestamp leap = RecurringHelper.calculateNext(
                calendarOf(2027, Calendar.FEBRUARY, 28), "yearly", 29);
        assertEquals("2028 is a leap year, so the 29th returns", 29, dayOf(leap));
    }

    @Test
    public void noneHasNoNextDate() {
        assertNull(RecurringHelper.calculateNext(
                calendarOf(2026, Calendar.JANUARY, 31), "none", 31));
    }
}
