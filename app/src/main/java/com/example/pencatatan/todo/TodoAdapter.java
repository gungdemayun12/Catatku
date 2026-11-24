package com.example.pencatatan.todo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.AnimatedCheckBox;
import com.example.pencatatan.common.ColorUtil;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.NameKeys;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.databinding.ItemSectionHeaderBinding;
import com.example.pencatatan.databinding.ItemTodoBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TodoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** A heading row ("Today", "Later", ...) interleaved with the task cards. */
    public static final class Section {
        public final String key;
        public final String title;
        public final int count;

        public Section(String key, String title, int count) {
            this.key = key;
            this.title = title;
            this.count = count;
        }
    }

    /** A list entry: either a task or a section heading. */
    public static final class Row {
        public final Todo todo;
        public final Section section;

        private Row(Todo todo, Section section) {
            this.todo = todo;
            this.section = section;
        }

        static Row of(Todo todo) {
            return new Row(todo, null);
        }

        static Row header(Section section) {
            return new Row(null, section);
        }

        public boolean isHeader() {
            return section != null;
        }
    }

    private static final int TYPE_TODO = 0;
    private static final int TYPE_HEADER = 1;

    public interface Listener {
        void onOpen(Todo todo);

        void onToggle(Todo todo, boolean completed);

        /**
         * Long-press: enters multi-select (or leaves it once selection is active).
         * Optional, because the calendar renders a different list where selecting
         * several tasks at once has no bulk action to apply.
         */
        default void onLongPress(Todo todo) {
        }

        /** Star toggled from the card. */
        default void onStar(Todo todo, boolean starred) {
        }

        /** Tapped a card while the list is in multi-select mode. */
        default void onSelect(Todo todo) {
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, TodoCategory> categories = new HashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Listener listener;
    private boolean selecting;

    public TodoAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Flattened task list, no headings. */
    private final List<Todo> flat = new ArrayList<>();

    public void submit(List<Todo> data) {
        submit(data, null);
    }

    /**
     * Submits the tasks together with an optional ordered list of headings. When
     * {@code sections} is null the list renders flat, exactly as before grouping
     * was added.
     */
    public void submit(List<Todo> data, List<Section> sections) {
        rows.clear();
        flat.clear();
        if (data != null) {
            flat.addAll(data);
            rows.addAll(null == sections ? flatRows(data) : groupedRows(data, sections));
        }
        // Drop selections for tasks that are no longer visible, so the counter and
        // the bulk bar can never point at something the user cannot see.
        selected.retainAll(taskIds());
        if (selected.isEmpty()) selecting = false;
        notifyDataSetChanged();
    }

    private List<Row> flatRows(List<Todo> data) {
        List<Row> result = new ArrayList<>();
        for (Todo todo : data) result.add(Row.of(todo));
        return result;
    }

    private List<Row> groupedRows(List<Todo> data, List<Section> sections) {
        List<Row> result = new ArrayList<>();
        for (Section section : sections) {
            result.add(Row.header(section));
            for (Todo todo : data) {
                if (section.key.equals(sectionKeyOf(todo))) result.add(Row.of(todo));
            }
        }
        return result;
    }

    private Set<String> taskIds() {
        Set<String> ids = new HashSet<>();
        for (Todo todo : flat) {
            if (todo.getDocId() != null) ids.add(todo.getDocId());
        }
        return ids;
    }

    /**
     * Which heading a task belongs to. Kept public and static so the fragment can
     * build the section list with exactly the same bucketing rules.
     */
    public static String sectionKeyOf(Todo todo) {
        if (todo.isCompleted()) return "done";
        if (todo.isOverdue()) return "overdue";
        long days = todo.daysUntilDue();
        if (days == Long.MAX_VALUE) return "nodate";
        if (days <= 0) return "today";
        if (days <= 7) return "week";
        return "later";
    }

    /** The single task at an adapter position, or null for a heading. */
    public Todo getAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        return rows.get(position).todo;
    }

    public List<Todo> items() {
        return flat;
    }

    // ---------------------------------------------------------------- selection

    public boolean isSelecting() {
        return selecting;
    }

    public void setSelecting(boolean selecting) {
        this.selecting = selecting;
        if (!selecting) selected.clear();
        notifyDataSetChanged();
    }

    public void toggleSelection(Todo todo) {
        if (todo == null || todo.getDocId() == null) return;
        if (!selected.remove(todo.getDocId())) selected.add(todo.getDocId());
        if (selected.isEmpty()) selecting = false;
        notifyDataSetChanged();
    }

    public List<Todo> selectedTodos() {
        List<Todo> result = new ArrayList<>();
        for (Todo todo : flat) {
            if (todo.getDocId() != null && selected.contains(todo.getDocId())) result.add(todo);
        }
        return result;
    }

    public int selectedCount() {
        return selected.size();
    }

    public void selectAll() {
        selecting = true;
        for (Todo todo : flat) {
            if (todo.getDocId() != null) selected.add(todo.getDocId());
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selected.clear();
        selecting = false;
        notifyDataSetChanged();
    }

    public void setCategories(List<TodoCategory> data) {
        categories.clear();
        if (data != null) {
            for (TodoCategory category : data) {
                if (category.getDocId() != null) categories.put(category.getDocId(), category);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader() ? TYPE_HEADER : TYPE_TODO;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false));
        }
        return new Holder(ItemTodoBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row.section);
        } else {
            ((Holder) holder).bind(row.todo);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    /**
     * Status shown in the badge column. There are three, one per state the list
     * can show, so the slot is never empty:
     * <ul>
     *     <li>{@link #STATUS_DONE} — finished (also the history tab).</li>
     *     <li>{@link #STATUS_PROGRESS} — still open and already started: overdue,
     *     due today, or with part of its subtasks ticked off.</li>
     *     <li>{@link #STATUS_OPEN} — still open, nothing started yet.</li>
     * </ul>
     */
    static final int STATUS_OPEN = 0;
    static final int STATUS_PROGRESS = 1;
    static final int STATUS_DONE = 2;

    static int statusOf(Todo todo) {
        if (todo.isCompleted()) return STATUS_DONE;
        if (todo.isOverdue() || todo.hasStarted() || isDueToday(todo)) return STATUS_PROGRESS;
        return STATUS_OPEN;
    }

    /** Public alias so the detail screen reuses the exact same three states. */
    public static int statusOf(Context context, Todo todo) {
        return statusOf(todo);
    }

    private static boolean isDueToday(Todo todo) {
        if (todo.getDueDate() == null) return false;
        return Dates.isSameDay(Dates.calendarOf(todo.getDueDate().toDate()), java.util.Calendar.getInstance());
    }

    static String statusLabel(Context context, int status) {
        if (status == STATUS_DONE) return context.getString(R.string.todo_status_done);
        if (status == STATUS_PROGRESS) return context.getString(R.string.todo_status_in_progress);
        return context.getString(R.string.todo_status_open);
    }

    public static String statusLabelOf(Context context, Todo todo) {
        return statusLabel(context, statusOf(todo));
    }

    static int statusColor(Context context, int status) {
        if (status == STATUS_DONE) return ContextCompat.getColor(context, R.color.income);
        if (status == STATUS_PROGRESS) return ContextCompat.getColor(context, R.color.status_progress);
        return ContextCompat.getColor(context, R.color.status_open);
    }

    class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemSectionHeaderBinding binding;

        HeaderHolder(ItemSectionHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Section section) {
            binding.headerTitle.setText(section.title);
            binding.headerCount.setText(String.valueOf(section.count));
        }
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ItemTodoBinding binding;

        Holder(ItemTodoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Todo todo) {
            Context context = binding.getRoot().getContext();
            binding.title.setText(todo.getTitle());

            boolean isSelected = todo.getDocId() != null && selected.contains(todo.getDocId());

            // ---- selection mode ----
            // In selection mode the tick in the card is replaced by a plain
            // checkbox: the animated tick keeps meaning "this task is done", and
            // reusing it for "this task is selected" would be ambiguous.
            binding.selectTick.setVisibility(selecting ? View.VISIBLE : View.GONE);
            binding.selectTick.setChecked(isSelected);
            binding.checkbox.setVisibility(selecting ? View.INVISIBLE : View.VISIBLE);

            // ---- starred ----
            binding.starButton.setVisibility(todo.isStarred() ? View.VISIBLE : View.GONE);
            binding.starButton.setContentDescription(context.getString(
                    todo.isStarred() ? R.string.todo_unstar : R.string.todo_star));
            binding.starButton.setOnClickListener(v -> {
                if (selecting) {
                    listener.onSelect(todo);
                    return;
                }
                if (Prefs.hapticEnabled(v.getContext())) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                listener.onStar(todo, !todo.isStarred());
            });

            // A finished task keeps its tick but is no longer toggled from the list:
            // the tick stays fully coloured (never greyed out) so "done" reads as
            // success rather than as a broken control.
            boolean locked = todo.isLocked();
            binding.checkbox.setOnToggleListener(null);
            binding.checkbox.setChecked(todo.isCompleted(), false);
            binding.checkbox.setLocked(locked);

            int priorityColor = ContextCompat.getColor(context,
                    todo.getPriority() == 2 ? R.color.priority_high
                            : todo.getPriority() == 0 ? R.color.priority_low : R.color.priority_medium);
            // Open tasks outline the box in their priority colour; a finished task
            // closes the loop in the "done" green so the tick matches its badge.
            binding.checkbox.setIdleColor(priorityColor);
            binding.checkbox.setActiveColor(ContextCompat.getColor(context, R.color.income));

            if (todo.isCompleted()) {
                binding.title.setPaintFlags(binding.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                binding.title.setTextColor(ContextCompat.getColor(context, R.color.color_on_surface_variant));
            } else {
                binding.title.setPaintFlags(binding.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                binding.title.setTextColor(ContextCompat.getColor(context, R.color.color_on_surface));
            }

            TodoCategory category = todo.getCategoryId() == null ? null
                    : categories.get(todo.getCategoryId());
            String categoryName = category == null
                    ? (todo.getCategoryName() == null ? "" : todo.getCategoryName())
                    : NameKeys.resolve(context, category.getName(), category.getNameKey());
            if (categoryName.isEmpty()) {
                binding.categoryDot.setVisibility(View.GONE);
                binding.categoryName.setText(R.string.category_none);
            } else {
                binding.categoryDot.setVisibility(View.VISIBLE);
                binding.categoryName.setText(categoryName);
                int color = ColorUtil.parse(category == null ? null : category.getColorHex(),
                        ContextCompat.getColor(context, R.color.todo_primary));
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(color);
                binding.categoryDot.setBackground(dot);
            }

            if (todo.getDueDate() == null) {
                binding.dueText.setText(R.string.no_due_date);
                binding.dueText.setTextColor(ContextCompat.getColor(context, R.color.color_on_surface_variant));
            } else {
                boolean overdue = todo.isOverdue();
                binding.dueText.setText(Dates.relativeLabel(context, todo.getDueDate().toDate()));
                binding.dueText.setTextColor(ContextCompat.getColor(context,
                        overdue ? R.color.expense : R.color.color_on_surface_variant));
                if (overdue) binding.dueText.setText(context.getString(R.string.overdue)
                        + " \u00B7 " + Dates.formatShortDate(context, todo.getDueDate().toDate()));
            }

            List<java.util.Date> reminders = todo.reminders();
            if (!todo.isCompleted() && !reminders.isEmpty()) {
                binding.reminderRow.setVisibility(View.VISIBLE);
                binding.reminderText.setText(context.getString(R.string.reminder_at,
                        Dates.formatDateTime(context, reminders.get(0))));
            } else {
                binding.reminderRow.setVisibility(View.GONE);
            }

            // Explain the follow-up copy so a repeating task is not mistaken for
            // an accidental duplicate.
            boolean repeats = todo.isRepeating();
            binding.repeatRow.setVisibility(repeats && !todo.isCompleted()
                    ? View.VISIBLE : View.GONE);

            // Subtask progress: a thin bar + "2/5" so a task with several steps
            // shows how far along it is without opening the detail screen. At zero
            // progress the bar would just be a full-width track, which reads as a
            // separator rather than as progress — so only the "0/5" text is shown
            // until at least one step is ticked.
            int[] progress = todo.subtaskProgress();
            if (progress[1] > 0) {
                binding.subtaskRow.setVisibility(View.VISIBLE);
                int percent = Math.round(progress[0] * 100f / progress[1]);
                binding.subtaskBar.setVisibility(percent > 0 ? View.VISIBLE : View.GONE);
                binding.subtaskBar.setProgressCompat(percent, false);
                binding.subtaskBar.setIndicatorColor(ContextCompat.getColor(context,
                        progress[0] == progress[1] ? R.color.income : R.color.todo_primary));
                binding.subtaskText.setText(context.getString(R.string.todo_subtask_ratio,
                        progress[0], progress[1]));
            } else {
                binding.subtaskRow.setVisibility(View.GONE);
            }

            // ---- focus reason + effort estimate ----
            // One compact line that explains both why this row matters and how
            // long it costs. Both halves are optional: a task with no due date
            // has no reason, a task with no estimate has no duration, and the
            // row simply hides when neither applies instead of leaving a gap.
            String reasonText = "";
            String reasonKey = todo.focusReasonKey();
            if (reasonKey != null) {
                if ("overdue".equals(reasonKey)) {
                    long days = todo.overdueDays();
                    reasonText = context.getResources().getQuantityString(
                            R.plurals.todo_focus_overdue_days, (int) days, days);
                } else if ("today".equals(reasonKey)) {
                    reasonText = context.getString(R.string.todo_focus_reason_today);
                } else if ("soon".equals(reasonKey)) {
                    long days = todo.daysUntilDue();
                    reasonText = context.getString(R.string.todo_focus_reason_soon, (int) days);
                } else if ("starred".equals(reasonKey)) {
                    reasonText = context.getString(R.string.todo_focus_reason_starred);
                }
            }
            String estimateText = todo.hasEstimate()
                    ? context.getString(R.string.todo_estimate_short,
                    Dates.formatDuration(context, todo.getEstimateMinutes()))
                    : "";
            if (reasonText.isEmpty() && estimateText.isEmpty()) {
                binding.signalRow.setVisibility(View.GONE);
            } else {
                binding.signalRow.setVisibility(View.VISIBLE);
                String joined = reasonText.isEmpty() ? estimateText
                        : estimateText.isEmpty() ? reasonText
                        : reasonText + " \u00B7 " + estimateText;
                binding.signalText.setText(joined);
                boolean urgent = "overdue".equals(reasonKey) || "today".equals(reasonKey);
                binding.signalIcon.setImageResource(
                        estimateText.isEmpty() ? R.drawable.ic_target : R.drawable.ic_timer);
                binding.signalIcon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context,
                                urgent ? R.color.expense : R.color.color_on_surface_variant)));
            }

            // ---- badge column: status on top, priority below ----
            int status = statusOf(todo);
            int statusColor = statusColor(context, status);
            binding.statusBadge.setText(statusLabel(context, status));
            binding.statusBadge.setTextColor(statusColor);
            binding.statusBadge.setBackgroundTintList(ColorStateList.valueOf(
                    ColorUtil.withAlpha(hex(statusColor), 0.14f)));
            binding.statusBadge.setCompoundDrawableTintList(ColorStateList.valueOf(statusColor));

            binding.priorityBadge.setText(priorityLabel(context, todo.getPriority()));
            binding.priorityBadge.setTextColor(priorityColor);
            binding.priorityBadge.setBackgroundTintList(ColorStateList.valueOf(
                    ColorUtil.withAlpha(hex(priorityColor), 0.14f)));

            binding.getRoot().setOnClickListener(v -> {
                if (selecting) listener.onSelect(todo);
                else listener.onOpen(todo);
            });
            binding.getRoot().setOnLongClickListener(v -> {
                if (Prefs.hapticEnabled(v.getContext())) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                listener.onLongPress(todo);
                return true;
            });
            // Selection ring: the card outline thickens and takes the accent colour
            // so "picked" is obvious without moving anything in the layout.
            binding.getRoot().setStrokeWidth(isSelected ? 2 : 0);
            binding.getRoot().setStrokeColor(ContextCompat.getColor(context,
                    isSelected ? R.color.todo_primary : android.R.color.transparent));

            binding.checkbox.setOnToggleListener((view, checked) -> {
                // A finished task is final, so nothing may re-open or re-complete it.
                if (todo.isLocked()) {
                    view.setChecked(todo.isCompleted(), false);
                    return;
                }
                if (Prefs.hapticEnabled(view.getContext())) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                listener.onToggle(todo, checked);
            });
        }

        private String priorityLabel(Context context, long priority) {
            if (priority == 2) return context.getString(R.string.priority_high);
            if (priority == 0) return context.getString(R.string.priority_low);
            return context.getString(R.string.priority_medium);
        }

        private String hex(int color) {
            return String.format("#%06X", color & 0xFFFFFF);
        }
    }
}
