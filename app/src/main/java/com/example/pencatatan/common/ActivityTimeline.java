package com.example.pencatatan.common;

import com.example.pencatatan.data.model.ActivityLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Timeline de-duplication for a single task's activity history.
 *
 * <p>Completing a task is now atomic and happens exactly once, but data
 * written <b>before</b> that fix can already contain several identical
 * "completed" rows. Those are always leftovers, never real events: a task
 * cannot be completed twice, so consecutive duplicates are collapsed.</p>
 *
 * <p>Other actions (edited, reopened, deleted) are genuinely repeatable and
 * are left untouched.</p>
 */
public final class ActivityTimeline {

    private ActivityTimeline() {
    }

    /**
     * Removes consecutive {@code completed} entries, keeping the most recent
     * one. The input must already be sorted newest-first.
     */
    public static List<ActivityLog> collapseDuplicateCompletions(List<ActivityLog> logs) {
        List<ActivityLog> result = new ArrayList<>();
        if (logs == null) return result;
        for (ActivityLog log : logs) {
            if (log == null) continue;
            if (ActivityLog.COMPLETED.equals(log.getAction()) && !result.isEmpty()
                    && ActivityLog.COMPLETED.equals(
                    result.get(result.size() - 1).getAction())) {
                continue;
            }
            result.add(log);
        }
        return result;
    }
}
