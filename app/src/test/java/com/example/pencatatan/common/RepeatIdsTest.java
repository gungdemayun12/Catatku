package com.example.pencatatan.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Covers {@link RepeatIds} — the deterministic key that stops completing a
 * repeating task from creating duplicate copies.
 */
public class RepeatIdsTest {

    private static Date date(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute);
        return calendar.getTime();
    }

    @Test
    public void sameOccurrence_producesSameId() {
        Date due = date(2026, 3, 10, 9, 0);
        assertEquals("double tap must not create a second copy",
                RepeatIds.forNextOccurrence("weekly", due, "todo-1"),
                RepeatIds.forNextOccurrence("weekly", due, "todo-1"));
    }

    @Test
    public void differentOccurrence_producesDifferentId() {
        assertNotEquals(
                RepeatIds.forNextOccurrence("weekly", date(2026, 3, 10, 9, 0), "todo-1"),
                RepeatIds.forNextOccurrence("weekly", date(2026, 3, 17, 9, 0), "todo-1"));
    }

    @Test
    public void differentRepeatType_producesDifferentId() {
        Date due = date(2026, 3, 10, 9, 0);
        assertNotEquals(
                RepeatIds.forNextOccurrence("daily", due, "todo-1"),
                RepeatIds.forNextOccurrence("weekly", due, "todo-1"));
    }

    @Test
    public void differentSourceTask_producesDifferentId() {
        Date due = date(2026, 3, 10, 9, 0);
        assertNotEquals(
                RepeatIds.forNextOccurrence("monthly", due, "todo-1"),
                RepeatIds.forNextOccurrence("monthly", due, "todo-2"));
    }

    @Test
    public void idIsDocumentIdSafe() {
        String id = RepeatIds.forNextOccurrence("daily",
                date(2026, 12, 31, 23, 59), "abc/def");
        assertFalse("no slashes allowed in Firestore document ids", id.contains("/"));
        assertTrue(id.length() < 100);
    }

    @Test
    public void isRepeating_knowsTheNeutralValue() {
        assertFalse(RepeatIds.isRepeating(null));
        assertFalse(RepeatIds.isRepeating("none"));
        assertTrue(RepeatIds.isRepeating("daily"));
        assertTrue(RepeatIds.isRepeating("weekly"));
        assertTrue(RepeatIds.isRepeating("monthly"));
    }

    /**
     * Completing copy N must hash from the ORIGINAL task, not from copy N itself.
     * Without this a retried completion of the same date produced a second copy.
     */
    @Test
    public void chainedOccurrences_hashFromTheSameRoot() {
        String root = "root-task";
        // First follow-up copy, created by completing the original task.
        String secondId = RepeatIds.withRoot(
                RepeatIds.forNextOccurrence("weekly", date(2026, 3, 10, 9, 0), root), root);
        // Completing that copy repeats the same date as the direct path would.
        String chained = RepeatIds.withRoot(
                RepeatIds.forNextOccurrence("weekly", date(2026, 3, 17, 9, 0), secondId), secondId);
        String direct = RepeatIds.withRoot(
                RepeatIds.forNextOccurrence("weekly", date(2026, 3, 17, 9, 0), root), root);
        assertEquals("both paths must land on the same document id", direct, chained);
    }

    @Test
    public void withRoot_keepsIdDocumentSafeAndUnique() {
        String id = RepeatIds.withRoot("rep_weekly_20260310_0900_abc", "source-1");
        assertFalse(id.contains("/"));
        assertTrue(id.length() < 100);
        assertNotEquals(RepeatIds.withRoot("rep_weekly_20260310_0900_abc", "source-2"),
                RepeatIds.withRoot("rep_weekly_20260310_0900_abc", "source-3"));
    }

    @Test
    public void withRoot_nullOrEmptySource_leavesIdUntouched() {
        assertEquals("rep_x", RepeatIds.withRoot("rep_x", null));
        assertEquals("rep_x", RepeatIds.withRoot("rep_x", ""));
    }
}
