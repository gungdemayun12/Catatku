package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.pencatatan.notification.NotificationHelper;

import org.junit.Test;

/**
 * Notification ids must never mix a task reminder with a bill reminder.
 *
 * <p>Both used to be {@code documentId.hashCode()}. Todo ids and bill ids come
 * from different collections, so nothing stopped a bill from reusing a task's
 * id: the second reminder replaced the first, and tapping "mark as done" on one
 * cancelled the other.</p>
 *
 * <p>{@link NotificationHelper#notificationId} reserves odd ids for tasks and
 * even ids for bills, so the two sets are disjoint by construction. The method
 * is package-private and pure, which is exactly why it can be tested here
 * without an Android runtime.</p>
 */
public class NotificationIdTest {

    @Test
    public void taskIdIsOdd() {
        assertEquals(1, NotificationHelper.notificationId("todo", "abc") % 2);
        assertEquals(1, NotificationHelper.notificationId("todo", "x9Kq2Lm") % 2);
    }

    @Test
    public void billIdIsEven() {
        assertEquals(0, NotificationHelper.notificationId("bill", "abc") % 2);
        assertEquals(0, NotificationHelper.notificationId("bill", "x9Kq2Lm") % 2);
    }

    @Test
    public void sameDocumentIdInBothKindsNeverCollides() {
        // Even when the underlying hash is identical (same string), the two
        // notifications must be distinct.
        assertNotEquals(NotificationHelper.notificationId("todo", "sameId"),
                NotificationHelper.notificationId("bill", "sameId"));
    }

    @Test
    public void idsStayPositive() {
        // A negative id is legal for the framework but makes the parity rule
        // fragile on values near Integer.MIN_VALUE.
        String[] ids = {"a", "b", "zzz", "0", "\uD83D\uDE00", "long-document-id-123456"};
        for (String id : ids) {
            assertEquals(true, NotificationHelper.notificationId("todo", id) > 0);
            assertEquals(true, NotificationHelper.notificationId("bill", id) >= 0);
        }
    }

    @Test
    public void distinctDocumentsKeepDistinctTaskIds() {
        // Hash collisions are theoretically possible, so this checks a realistic
        // set of Firestore auto-ids rather than trusting two arbitrary strings.
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int collisions = 0;
        for (int i = 0; i < 5_000; i++) {
            String id = String.format(java.util.Locale.US, "T%07dXq", i);
            if (!seen.add(NotificationHelper.notificationId("todo", id))) collisions++;
        }
        assertEquals(0, collisions);
    }

    @Test
    public void distinctDocumentsKeepDistinctBillIds() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int collisions = 0;
        for (int i = 0; i < 5_000; i++) {
            String id = String.format(java.util.Locale.US, "B%07dQp", i);
            if (!seen.add(NotificationHelper.notificationId("bill", id))) collisions++;
        }
        assertEquals(0, collisions);
    }
}
