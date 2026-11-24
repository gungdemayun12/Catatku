package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.pencatatan.data.model.Transaction;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Covers the date-range filter that backs the "pick a date range" export.
 *
 * <p>The important cases are the boundaries: a transaction at 23:59 on the last
 * day must be included (comparing raw timestamps against a midnight end would
 * drop it), and a reversed range must still work instead of returning nothing.</p>
 */
public class FinanceRangeExportTest {

    private static Transaction tx(String id, int year, int month, int day,
                                  int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        Transaction transaction = new Transaction();
        transaction.setDocId(id);
        transaction.setDate(new Timestamp(calendar.getTime()));
        transaction.setMonthKey(String.format(Locale.US, "%04d-%02d", year, month));
        return transaction;
    }

    private static Calendar day(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar;
    }

    private static List<Transaction> sample() {
        List<Transaction> data = new ArrayList<>();
        data.add(tx("before", 2026, 1, 31, 23, 59));
        data.add(tx("first-day-early", 2026, 2, 1, 0, 1));
        data.add(tx("middle", 2026, 2, 10, 12, 0));
        data.add(tx("last-day-late", 2026, 2, 28, 23, 59));
        data.add(tx("after", 2026, 3, 1, 0, 1));
        return data;
    }

    private static List<String> ids(List<Transaction> data) {
        List<String> result = new ArrayList<>();
        for (Transaction transaction : data) result.add(transaction.getDocId());
        return result;
    }

    @Test
    public void includesWholeFirstAndLastDay() {
        List<String> result = ids(FinanceCalc.forRange(sample(),
                day(2026, 2, 1), day(2026, 2, 28)));
        assertEquals(3, result.size());
        assertTrue(result.contains("first-day-early"));
        assertTrue(result.contains("middle"));
        assertTrue(result.contains("last-day-late"));
        assertFalse(result.contains("before"));
        assertFalse(result.contains("after"));
    }

    @Test
    public void singleDayRangeWorks() {
        List<String> result = ids(FinanceCalc.forRange(sample(),
                day(2026, 2, 10), day(2026, 2, 10)));
        assertEquals(1, result.size());
        assertEquals("middle", result.get(0));
    }

    @Test
    public void reversedRangeIsSwappedNotEmpty() {
        List<String> result = ids(FinanceCalc.forRange(sample(),
                day(2026, 2, 28), day(2026, 2, 1)));
        assertEquals(3, result.size());
    }

    @Test
    public void nullBoundsAreOpenEnded() {
        assertEquals(5, FinanceCalc.forRange(sample(), null, null).size());
        // Only the upper bound: everything up to and including Feb 1st.
        assertEquals(2, FinanceCalc.forRange(sample(), null, day(2026, 2, 1)).size());
        // Only the lower bound: everything from Feb 28th onwards.
        assertEquals(2, FinanceCalc.forRange(sample(), day(2026, 2, 28), null).size());
    }

    @Test
    public void transactionsWithoutDateAreSkipped() {
        List<Transaction> data = new ArrayList<>(sample());
        Transaction undated = new Transaction();
        undated.setDocId("undated");
        data.add(undated);

        List<String> result = ids(FinanceCalc.forRange(data, null, null));
        assertFalse(result.contains("undated"));
    }

    @Test
    public void emptyRangeReturnsEmptyListNotNull() {
        List<Transaction> result = FinanceCalc.forRange(sample(),
                day(2025, 6, 1), day(2025, 6, 30));
        assertTrue(result.isEmpty());
    }

    @Test
    public void originalCalendarsAreNotMutated() {
        Calendar start = day(2026, 2, 1);
        Calendar end = day(2026, 2, 28);
        int startHour = start.get(Calendar.HOUR_OF_DAY);
        int endHour = end.get(Calendar.HOUR_OF_DAY);
        FinanceCalc.forRange(sample(), start, end);
        // The caller's calendars must keep their own time of day; the filter works
        // on clones so a second export with the same objects behaves identically.
        assertEquals(startHour, start.get(Calendar.HOUR_OF_DAY));
        assertEquals(endHour, end.get(Calendar.HOUR_OF_DAY));
    }
}
