package com.example.pencatatan.common;

import android.content.Context;

import com.example.pencatatan.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Dates {

    private Dates() {
    }

    public static Locale locale(Context context) {
        return context.getResources().getConfiguration().locale;
    }

    public static String formatDate(Context context, Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("d MMM yyyy", locale(context)).format(date);
    }

    /**
     * Human form of a minute count, e.g. "45 mnt", "2 jam", "1 jam 30 mnt".
     *
     * <p>Kept here rather than in the adapter because both the task list header
     * ("7 tasks · ~2 hours") and the task card ("~30 min") need the exact same
     * wording — two copies of this rule drift apart the moment one of them is
     * tweaked.</p>
     */
    public static String formatDuration(Context context, long minutes) {
        if (minutes <= 0) return "";
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours <= 0) return context.getString(R.string.duration_minutes, rest);
        if (rest == 0) return context.getString(R.string.duration_hours, hours);
        return context.getString(R.string.duration_hours_minutes, hours, rest);
    }

    public static String formatShortDate(Context context, Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("d MMM", locale(context)).format(date);
    }

    /**
     * Day + month + year. Exports span multiple years, so a date without the year
     * ("3 Feb") is ambiguous in a PDF statement; this keeps it unambiguous.
     */
    public static String formatDayMonthYear(Context context, Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("d MMM yyyy", locale(context)).format(date);
    }

    public static String formatDateTime(Context context, Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("d MMM yyyy · HH:mm", locale(context)).format(date);
    }

    public static String formatTime(Context context, Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("HH:mm", locale(context)).format(date);
    }

    public static String formatMonth(Context context, Calendar calendar) {
        return new SimpleDateFormat("MMMM yyyy", locale(context)).format(calendar.getTime());
    }

    public static String formatMonthShort(Context context, Calendar calendar) {
        return new SimpleDateFormat("MMM", locale(context)).format(calendar.getTime());
    }

    public static String dayKey(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    public static String formatDayHeader(Context context, Date date) {
        if (date == null) return "";
        Calendar day = Calendar.getInstance();
        day.setTime(date);
        Calendar today = Calendar.getInstance();
        if (isSameDay(day, today)) {
            return context.getString(R.string.today);
        }
        today.add(Calendar.DAY_OF_MONTH, -1);
        if (isSameDay(day, today)) {
            return context.getString(R.string.yesterday);
        }
        return new SimpleDateFormat("EEEE, d MMM", locale(context)).format(date);
    }

    public static String dayLetter(Context context, int dayOfWeek) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        String value = new SimpleDateFormat("EEEEE", locale(context)).format(cal.getTime());
        return value.substring(0, 1).toUpperCase(locale(context));
    }

    public static String monthKey(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.getTime());
    }

    public static String monthKey(Date date) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(date);
    }

    public static Calendar calendarOf(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar;
    }

    public static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isSameMonth(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH) == b.get(Calendar.MONTH);
    }

    public static Calendar startOfDay(Calendar calendar) {
        Calendar copy = (Calendar) calendar.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    public static Calendar endOfDay(Calendar calendar) {
        Calendar copy = (Calendar) calendar.clone();
        copy.set(Calendar.HOUR_OF_DAY, 23);
        copy.set(Calendar.MINUTE, 59);
        copy.set(Calendar.SECOND, 59);
        copy.set(Calendar.MILLISECOND, 999);
        return copy;
    }

    public static Calendar startOfMonth(Calendar calendar) {
        Calendar copy = (Calendar) calendar.clone();
        copy.set(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(copy);
    }

    public static Calendar endOfMonth(Calendar calendar) {
        Calendar copy = (Calendar) calendar.clone();
        copy.set(Calendar.DAY_OF_MONTH, copy.getActualMaximum(Calendar.DAY_OF_MONTH));
        return endOfDay(copy);
    }

    public static Date combine(Calendar day, int hour, int minute) {
        Calendar copy = (Calendar) day.clone();
        copy.set(Calendar.HOUR_OF_DAY, hour);
        copy.set(Calendar.MINUTE, minute);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy.getTime();
    }

    public static String relativeLabel(Context context, Date date) {
        if (date == null) return context.getString(R.string.no_due_date);
        Calendar target = calendarOf(date);
        Calendar today = Calendar.getInstance();
        if (isSameDay(target, today)) return context.getString(R.string.today) + " · " + formatTime(context, date);
        today.add(Calendar.DAY_OF_MONTH, 1);
        if (isSameDay(target, today)) return context.getString(R.string.tomorrow) + " · " + formatTime(context, date);
        return formatDateTime(context, date);
    }
}
