package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.example.pencatatan.data.model.Transaction;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Ensures generated recurring transactions are idempotent.
 *
 * <p>Before this, every catch-up pass inserted new documents with random ids, so
 * restarting the app (or two devices syncing) could duplicate the same
 * occurrence.</p>
 */
public class RecurringOccurrenceIdTest {

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month - 1, day, 9, 0, 0);
        return calendar.getTime();
    }

    private static Transaction template(String docId) {
        Transaction tx = new Transaction();
        tx.setDocId(docId);
        tx.setRepeatType("monthly");
        return tx;
    }

    @Test
    public void sameOccurrence_producesSameId() {
        Transaction tx = template("series-1");
        assertEquals("re-running the catch-up must not duplicate",
                RecurringHelper.occurrenceId(tx, date(2026, 3, 15)),
                RecurringHelper.occurrenceId(tx, date(2026, 3, 15)));
    }

    @Test
    public void differentOccurrences_produceDifferentIds() {
        Transaction tx = template("series-1");
        assertNotEquals(
                RecurringHelper.occurrenceId(tx, date(2026, 3, 15)),
                RecurringHelper.occurrenceId(tx, date(2026, 4, 15)));
    }

    @Test
    public void differentSeries_produceDifferentIds() {
        assertNotEquals(
                RecurringHelper.occurrenceId(template("series-1"), date(2026, 3, 15)),
                RecurringHelper.occurrenceId(template("series-2"), date(2026, 3, 15)));
    }

    @Test
    public void idIsDocumentIdSafe() {
        String id = RecurringHelper.occurrenceId(template("a/b/c"), date(2026, 12, 31));
        assertFalse("Firestore ids may not contain '/'", id.contains("/"));
    }
}
