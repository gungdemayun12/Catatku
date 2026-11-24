package com.example.pencatatan.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helpers for repeating tasks.
 *
 * <p>The follow-up copy created when a repeating task is completed uses a
 * <b>deterministic</b> document id. Before this existed, every completion
 * generated a brand-new random id, so tapping "done" twice (or a retried
 * write, or two screens racing) produced identical duplicates.</p>
 */
public final class RepeatIds {

    private RepeatIds() {
    }

    /**
     * @param repeatType one of {@code daily}, {@code weekly}, {@code monthly}
     * @param due        the due date of the occurrence that was completed
     * @param sourceId   document id of the task that was completed
     * @return a stable, Firestore-safe document id for the next occurrence
     */
    public static String forNextOccurrence(String repeatType, Date due, String sourceId) {
        String type = repeatType == null || repeatType.trim().isEmpty() ? "none" : repeatType;
        String dueKey = due == null ? "none"
                : new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(due);
        // Every occurrence of one series must hash from the SAME root id, otherwise
        // each copy becomes its own root and a repeated completion of the same date
        // (offline retry, two devices) creates duplicates. Generated ids already
        // carry their root, so chaining is stable from the very first copy on.
        String source = rootOf(sourceId);
        return "rep_" + type + "_" + dueKey + "_" + Integer.toHexString(source.hashCode());
    }

    /** Extracts the series root from an id produced by {@link #forNextOccurrence}. */
    private static String rootOf(String sourceId) {
        if (sourceId == null) return "";
        int marker = sourceId.indexOf(ROOT_MARKER);
        if (marker < 0) return sourceId;
        return sourceId.substring(marker + ROOT_MARKER.length());
    }

    private static final String ROOT_MARKER = "_root-";

    /**
     * Appends the series root to a generated document id so the next occurrence can
     * hash from the original task instead of from its immediate predecessor.
     */
    public static String withRoot(String id, String sourceId) {
        if (id == null || rootOf(sourceId).isEmpty()) return id;
        return id + ROOT_MARKER + rootOf(sourceId);
    }

    /** True when the task is part of a recurrence series. */
    public static boolean isRepeating(String repeatType) {
        return repeatType != null && !"none".equals(repeatType) && !repeatType.trim().isEmpty();
    }
}
