package com.example.pencatatan.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.pencatatan.data.model.ActivityLog;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Covers the timeline clean-up that hides duplicate "Diselesaikan" rows left
 * behind by the old, non-idempotent completion write.
 */
public class ActivityTimelineTest {

    private static ActivityLog log(String action) {
        ActivityLog entry = new ActivityLog();
        entry.setAction(action);
        return entry;
    }

    @Test
    public void consecutiveCompletions_collapseToOne() {
        List<ActivityLog> logs = new ArrayList<>(Arrays.asList(
                log(ActivityLog.COMPLETED),
                log(ActivityLog.COMPLETED),
                log(ActivityLog.COMPLETED),
                log(ActivityLog.COMPLETED),
                log(ActivityLog.CREATED)));

        List<ActivityLog> result = ActivityTimeline.collapseDuplicateCompletions(logs);

        assertEquals("five repeated 'done' rows must collapse to one", 2, result.size());
        assertEquals(ActivityLog.COMPLETED, result.get(0).getAction());
        assertEquals(ActivityLog.CREATED, result.get(1).getAction());
    }

    @Test
    public void separatedCompletions_areKept() {
        // completed -> reopened -> completed is a genuine sequence.
        List<ActivityLog> logs = new ArrayList<>(Arrays.asList(
                log(ActivityLog.COMPLETED),
                log(ActivityLog.UNCOMPLETED),
                log(ActivityLog.COMPLETED)));

        assertEquals(3, ActivityTimeline.collapseDuplicateCompletions(logs).size());
    }

    @Test
    public void repeatedEdits_areNotTouched() {
        List<ActivityLog> logs = new ArrayList<>(Arrays.asList(
                log(ActivityLog.UPDATED),
                log(ActivityLog.UPDATED)));

        assertEquals("editing twice is legitimate, keep both",
                2, ActivityTimeline.collapseDuplicateCompletions(logs).size());
    }

    @Test
    public void nullAndEmpty_areSafe() {
        assertTrue(ActivityTimeline.collapseDuplicateCompletions(null).isEmpty());
        assertTrue(ActivityTimeline.collapseDuplicateCompletions(
                new ArrayList<>()).isEmpty());
    }

    @Test
    public void nullEntries_areSkipped() {
        List<ActivityLog> logs = new ArrayList<>(Arrays.asList(
                log(ActivityLog.CREATED), null, log(ActivityLog.COMPLETED)));

        assertEquals(2, ActivityTimeline.collapseDuplicateCompletions(logs).size());
    }
}
