package com.example.pencatatan.todo;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.databinding.FragmentTodoCalendarBinding;
import com.example.pencatatan.notification.ReminderScheduler;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CalendarFragment extends Fragment {

    private FragmentTodoCalendarBinding binding;
    private final Calendar displayed = Calendar.getInstance();
    private final Calendar selected = Calendar.getInstance();
    private final List<Todo> all = new ArrayList<>();
    private TodoAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodoCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TodoViewModel viewModel = new ViewModelProvider(requireActivity()).get(TodoViewModel.class);
        displayed.set(Calendar.DAY_OF_MONTH, 1);
        selected.set(Calendar.HOUR_OF_DAY, 0);
        selected.set(Calendar.MINUTE, 0);
        selected.set(Calendar.SECOND, 0);
        selected.set(Calendar.MILLISECOND, 0);

        adapter = new TodoAdapter(new TodoAdapter.Listener() {
            @Override
            public void onOpen(Todo todo) {
                Intent intent = new Intent(requireContext(), TodoDetailActivity.class);
                intent.putExtra(TodoDetailActivity.EXTRA_TODO_ID, todo.getDocId());
                startActivity(intent);
            }

            @Override
            public void onToggle(Todo todo, boolean completed) {
                toggle(todo, completed);
            }
        });
        binding.dayRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.dayRecycler.setAdapter(adapter);

        binding.prevMonth.setOnClickListener(v -> {
            displayed.add(Calendar.MONTH, -1);
            renderCalendar();
            renderDay();
        });
        binding.nextMonth.setOnClickListener(v -> {
            displayed.add(Calendar.MONTH, 1);
            renderCalendar();
            renderDay();
        });

        buildWeekHeader();
        renderCalendar();

        viewModel.todos().observe(getViewLifecycleOwner(), todos -> {
            all.clear();
            if (todos != null) all.addAll(todos);
            renderCalendar();
            renderDay();
        });
        viewModel.categories().observe(getViewLifecycleOwner(), adapter::setCategories);
    }

    private void buildWeekHeader() {
        binding.weekdayRow.removeAllViews();
        int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        for (int day : order) {
            TextView cell = new TextView(requireContext());
            cell.setText(Dates.dayLetter(requireContext(), day));
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(11);
            cell.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_surface_variant));
            LinearLayoutParams(cell);
            binding.weekdayRow.addView(cell);
        }
    }

    private void LinearLayoutParams(TextView cell) {
        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cell.setLayoutParams(params);
    }

    private void renderCalendar() {
        binding.monthLabel.setText(Dates.formatMonth(requireContext(), displayed));
        binding.calendarGrid.removeAllViews();

        Calendar first = (Calendar) displayed.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int max = displayed.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < offset; i++) {
            binding.calendarGrid.addView(emptyCell());
        }
        for (int day = 1; day <= max; day++) {
            final Calendar date = (Calendar) displayed.clone();
            date.set(Calendar.DAY_OF_MONTH, day);
            binding.calendarGrid.addView(dayCell(date));
        }
    }

    private TextView emptyCell() {
        TextView cell = new TextView(requireContext());
        cell.setLayoutParams(cellParams());
        return cell;
    }

    private TextView dayCell(Calendar date) {
        TextView cell = new TextView(requireContext());
        cell.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)));
        cell.setGravity(Gravity.CENTER);
        cell.setTextSize(14);
        cell.setLayoutParams(cellParams());
        cell.setClickable(true);
        cell.setFocusable(true);

        boolean isToday = Dates.isSameDay(date, Calendar.getInstance());
        boolean isSelected = Dates.isSameDay(date, selected);
        int textColor = ContextCompat.getColor(requireContext(), R.color.color_on_surface);

        if (isSelected) {
            cell.setBackgroundResource(R.drawable.bg_day_selected);
            textColor = ContextCompat.getColor(requireContext(), R.color.color_on_primary);
        } else if (isToday) {
            cell.setBackgroundResource(R.drawable.bg_day_today);
            textColor = ContextCompat.getColor(requireContext(), R.color.color_on_primary_container);
        }
        cell.setTextColor(textColor);

        for (Todo todo : all) {
            if (todo.getDueDate() != null && Dates.isSameDay(
                    Dates.calendarOf(todo.getDueDate().toDate()), date)) {
                cell.setTypeface(null, android.graphics.Typeface.BOLD);
                break;
            }
        }

        cell.setOnClickListener(v -> {
            selected.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
            renderCalendar();
            renderDay();
        });
        return cell;
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        int size = Math.round(40 * getResources().getDisplayMetrics().density);
        params.width = 0;
        params.height = size;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(2, 2, 2, 2);
        return params;
    }

    private void renderDay() {
        binding.selectedDate.setText(android.text.format.DateFormat.getDateFormat(requireContext())
                .format(selected.getTime()));
        List<Todo> dayTasks = new ArrayList<>();
        for (Todo todo : all) {
            if (todo.getDueDate() != null && Dates.isSameDay(
                    Dates.calendarOf(todo.getDueDate().toDate()), selected)) {
                dayTasks.add(todo);
            }
        }
        adapter.submit(dayTasks);
        binding.selectedCount.setText(getResources().getQuantityString(
                R.plurals.plural_tasks, dayTasks.size(), dayTasks.size()));
        boolean empty = dayTasks.isEmpty();
        binding.emptyDay.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.dayRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            // fillViewport keeps the content at least screen-height, so a bottom margin
            // on the message alone cannot lift it above the FAB: there is nothing left to
            // scroll. Padding on the scroll view itself extends the scrollable area, which
            // is what actually lets the message sit clear of the button.
            reserveRoomForFab(binding.calendarScroll);
        }
    }

    /**
     * Adds scrollable room below the content so it clears the floating action button.
     *
     * <p>Uses the button's real measured height, which is what grows when its label
     * wraps at a large font size; falls back to a font-scale estimate before the first
     * layout pass.</p>
     */
    private void reserveRoomForFab(View scroll) {
        if (scroll == null) return;
        View fab = requireActivity().findViewById(R.id.addFab);
        int gap = Math.round(16 * getResources().getDisplayMetrics().density);
        int height = fab != null && fab.getHeight() > 0
                ? fab.getHeight()
                : Math.round(56 * getResources().getDisplayMetrics().density
                        * getResources().getConfiguration().fontScale);
        scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(),
                scroll.getPaddingRight(), height + gap);
    }

    private final java.util.Set<String> pendingToggles = new java.util.HashSet<>();

    private void toggle(Todo todo, boolean completed) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid == null || todo.getDocId() == null) return;
        // A finished task is final, and a write already in flight is ignored.
        if (todo.isLocked()) return;
        // Same state or a write already in flight for this task: ignore.
        if (todo.isCompleted() == completed) return;
        if (!pendingToggles.add(todo.getDocId())) return;

        if (completed) ReminderScheduler.cancelTodo(requireContext(), todo);
        new TodoRepository().setCompleted(uid, todo, completed, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String createdId) {
                pendingToggles.remove(todo.getDocId());
                if (!completed) ReminderScheduler.scheduleTodo(requireContext(), uid, todo);
            }

            @Override
            public void onError(Exception error) {
                pendingToggles.remove(todo.getDocId());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
