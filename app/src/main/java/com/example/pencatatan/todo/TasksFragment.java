package com.example.pencatatan.todo;

import android.content.Intent;
import android.content.res.Configuration;
import android.app.DatePickerDialog;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Dates;
import com.example.pencatatan.common.FabPadding;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.databinding.FragmentTodoListBinding;
import com.example.pencatatan.notification.ReminderScheduler;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TasksFragment extends Fragment {

    private FragmentTodoListBinding binding;
    private TodoViewModel viewModel;
    private TodoAdapter adapter;
    private final TodoRepository repository = new TodoRepository();
    private final List<Todo> all = new ArrayList<>();
    private final Map<String, TodoCategory> categories = new HashMap<>();
    private List<String> categoryIds = new ArrayList<>();
    private List<String> categoryNames = new ArrayList<>();

    private String status = "all";

    /** Smart-list filter: none, or one of the four one-tap slices. */
    private static final int SMART_NONE = 0;
    private static final int SMART_FOCUS = 1;
    private static final int SMART_STARRED = 2;
    private static final int SMART_STALE = 3;
    private static final int SMART_QUICK = 4;
    private static final int SMART_SUBTASKS = 5;
    private int smartMode = SMART_NONE;

    /** Optional starting filter, so another screen can jump straight to "Selesai". */
    public static final String ARG_STATUS = "arg_status";

    public static TasksFragment withStatus(String status) {
        TasksFragment fragment = new TasksFragment();
        Bundle args = new Bundle();
        args.putString(ARG_STATUS, status);
        fragment.setArguments(args);
        return fragment;
    }
    private String categoryId = null;
    private int priority = -1;
    private String query = "";

    /** Due-date filter mode. */
    private static final String DUE_ANY = "any";
    private String dueMode = DUE_ANY;
    private Date dueFrom;
    private Date dueTo;

    /**
     * Guards against firing several completion writes for the same task.
     * A CheckBox can emit several change callbacks while the list rebinds, and
     * each write used to create another copy of a repeating task.
     */
    private final java.util.Set<String> pendingToggles = new java.util.HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTodoListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TodoViewModel.class);
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

            @Override
            public void onLongPress(Todo todo) {
                // Long-press is the only way in to multi-select, which keeps the
                // normal tap meaning "open". Tapping the already-selected task
                // again leaves selection mode once the last one is dropped.
                if (!adapter.isSelecting()) {
                    adapter.setSelecting(true);
                    adapter.toggleSelection(todo);
                } else {
                    adapter.toggleSelection(todo);
                }
                updateBulkBar();
            }

            @Override
            public void onStar(Todo todo, boolean starred) {
                setStarred(todo, starred);
            }

            @Override
            public void onSelect(Todo todo) {
                adapter.toggleSelection(todo);
                updateBulkBar();
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        applyFontScaleListPadding();
        attachSwipeToDelete();

        binding.emptyState.emptyAction.setText(R.string.todo_empty_cta);
        binding.emptyState.emptyAction.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TodoFormActivity.class)));

        binding.filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.filterToday) status = "today";
            else if (id == R.id.filterUpcoming) status = "upcoming";
            else if (id == R.id.filterOverdue) status = "overdue";
            else if (id == R.id.filterCompleted) status = "completed";
            else status = "all";
            apply();
        });

        binding.categoryFilter.setOnClickListener(v -> chooseCategory());
        binding.priorityFilter.setOnClickListener(v -> choosePriority());
        binding.dateFilter.setOnClickListener(v -> chooseDueRange());
        binding.resetFilters.setOnClickListener(v -> resetFilters());
        binding.smartGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                smartMode = SMART_NONE;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.smartFocus) smartMode = SMART_FOCUS;
                else if (id == R.id.smartStarred) smartMode = SMART_STARRED;
                else if (id == R.id.smartStale) smartMode = SMART_STALE;
                else if (id == R.id.smartQuickWins) smartMode = SMART_QUICK;
                else if (id == R.id.smartWithSubtasks) smartMode = SMART_SUBTASKS;
                else smartMode = SMART_NONE;
            }
            apply();
        });

        binding.bulkClose.setOnClickListener(v -> exitSelection());
        binding.bulkDone.setOnClickListener(v -> bulkComplete());
        binding.bulkDelete.setOnClickListener(v -> bulkDelete());

        binding.searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.getDefault());
                apply();
            }
        });

        viewModel.todos().observe(getViewLifecycleOwner(), todos -> {
            all.clear();
            if (todos != null) all.addAll(todos);
            apply();
        });
        // Honour a starting filter (e.g. opened from "Lihat tugas selesai").
        Bundle args = getArguments();
        String initialStatus = args == null ? null : args.getString(ARG_STATUS);
        if (initialStatus != null && !initialStatus.isEmpty()) {
            status = initialStatus;
            int chip = "completed".equals(initialStatus) ? R.id.filterCompleted
                    : "today".equals(initialStatus) ? R.id.filterToday
                    : "upcoming".equals(initialStatus) ? R.id.filterUpcoming
                    : "overdue".equals(initialStatus) ? R.id.filterOverdue
                    : R.id.filterAll;
            binding.filterGroup.check(chip);
            apply();
        }
        viewModel.categories().observe(getViewLifecycleOwner(), data -> {
            categories.clear();
            categoryIds = new ArrayList<>();
            categoryNames = new ArrayList<>();
            categoryNames.add(getString(R.string.filter_all));
            categoryIds.add(null);
            if (data != null) {
                for (TodoCategory category : data) {
                    if (category.getDocId() == null) continue;
                    categories.put(category.getDocId(), category);
                    categoryIds.add(category.getDocId());
                    categoryNames.add(com.example.pencatatan.common.NameKeys.resolve(requireContext(),
                            category.getName(), category.getNameKey()));
                }
            }
            adapter.setCategories(data);
            apply();
        });
    }

    /**
     * Grows the list's bottom padding together with the system font scale so the
     * floating "Tugas baru" button never covers the last card. See {@link FabPadding}.
     */
    private void applyFontScaleListPadding() {
        if (binding == null) return;
        FabPadding.apply(binding.recycler);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Font size can change while the list is on screen (Settings → Display).
        applyFontScaleListPadding();
    }

    private void attachSwipeToDelete() {
        final Drawable background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_swipe_delete);
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                // Headings are not swipeable: only a task card can be deleted.
                int position = viewHolder.getBindingAdapterPosition();
                if (adapter.getAt(position) == null) return makeMovementFlags(0, 0);
                return makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (Prefs.hapticEnabled(requireContext())) {
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                int position = viewHolder.getBindingAdapterPosition();
                Todo todo = adapter.getAt(position);
                if (todo == null) {
                    adapter.notifyItemChanged(position);
                    return;
                }
                confirmDelete(todo, position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && background != null) {
                    View item = viewHolder.itemView;
                    background.setBounds(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
                    background.draw(canvas);
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });
        helper.attachToRecyclerView(binding.recycler);
    }

    private void confirmDelete(Todo todo, int position) {
        String uid = currentUid();
        if (todo.getDocId() == null || uid == null) {
            adapter.notifyItemChanged(position);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_delete_title)
                .setMessage(R.string.todo_delete_msg)
                .setNegativeButton(R.string.cancel, (dialog, which) ->
                        adapter.notifyItemChanged(position))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    ReminderScheduler.cancelTodo(requireContext(), todo);
                    repository.deleteTodo(uid, todo, new TodoRepository.SaveCallback() {
                        @Override
                        public void onSuccess(String id) {
                            if (isAdded()) {
                                android.widget.Toast.makeText(requireContext(), R.string.todo_deleted,
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            if (isAdded()) {
                                android.widget.Toast.makeText(requireContext(), R.string.delete_failed,
                                        android.widget.Toast.LENGTH_SHORT).show();
                                adapter.notifyItemChanged(position);
                            }
                        }
                    });
                })
                .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                .show();
    }

    @Nullable
    private String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private void toggle(Todo todo, boolean completed) {
        if (todo.getDocId() == null) return;
        String uid = currentUid();
        if (uid == null) return;
        // A finished task is final: it cannot be ticked again from the list. It is
        // still reachable from the "Completed" tab, where it lives as history.
        if (todo.isLocked()) return;
        // Ignore taps that repeat the state we already have, and ignore
        // re-entrant taps while a write for this task is still in flight.
        if (todo.isCompleted() == completed) return;
        if (!pendingToggles.add(todo.getDocId())) return;

        // Optimistic local flip: the tick, the strikethrough and the "Done" badge
        // appear on the spot and the card leaves the open list immediately. The
        // Firestore listener then confirms (or the reload below corrects) it.
        final boolean wasCompleted = todo.isCompleted();
        todo.setCompleted(completed);
        todo.setCompletedAt(completed ? com.google.firebase.Timestamp.now() : null);
        publish();

        if (completed) ReminderScheduler.cancelTodo(requireContext(), todo);
        repository.setCompleted(uid, todo, completed, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String createdId) {
                pendingToggles.remove(todo.getDocId());
                if (!completed) {
                    ReminderScheduler.scheduleTodo(requireContext(), uid, todo);
                } else {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.todo_completed_go_done,
                                Toast.LENGTH_SHORT).show();
                    }
                    if (createdId != null && !createdId.isEmpty()) {
                        repository.loadTodo(uid, createdId, new TodoRepository.OnTodo() {
                            @Override
                            public void onData(Todo next) {
                                ReminderScheduler.scheduleTodo(requireContext(), uid, next);
                            }

                            @Override
                            public void onError(Exception error) {
                            }
                        });
                    }
                }
            }

            @Override
            public void onError(Exception error) {
                pendingToggles.remove(todo.getDocId());
                // Roll the optimistic flip back: the write never landed, so the task
                // is still open and the card belongs in the list it was in.
                todo.setCompleted(wasCompleted);
                publish();
                if (isAdded()) {
                    android.widget.Toast.makeText(requireContext(), R.string.save_failed,
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ------------------------------------------------------- selection + actions

    /** Shows/hides the bulk bar and keeps its counter in step with the selection. */
    private void updateBulkBar() {
        if (binding == null) return;
        int count = adapter.selectedCount();
        boolean visible = adapter.isSelecting() && count > 0;
        binding.bulkBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            binding.bulkCount.setText(getString(R.string.todo_bulk_selected, count));
        }
        if (!adapter.isSelecting() && binding.bulkBar.getVisibility() == View.VISIBLE) {
            binding.bulkBar.setVisibility(View.GONE);
        }
        // The "New task" FAB would sit on top of the bulk bar, so it steps aside
        // while a selection is active. It lives in the hosting activity.
        View fab = requireActivity().findViewById(R.id.addFab);
        if (fab != null) {
            fab.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
    }

    private void exitSelection() {
        adapter.clearSelection();
        updateBulkBar();
    }

    private void bulkComplete() {
        String uid = currentUid();
        List<Todo> chosen = adapter.selectedTodos();
        if (uid == null || chosen.isEmpty()) return;
        List<Todo> open = new ArrayList<>();
        for (Todo todo : chosen) {
            if (!todo.isCompleted() && !todo.isLocked()) open.add(todo);
        }
        if (open.isEmpty()) {
            exitSelection();
            return;
        }
        // Ignore a second bulk tap while the first write is still running, so the
        // same tasks cannot be queued twice.
        for (Todo todo : open) {
            if (todo.getDocId() != null && pendingToggles.contains(todo.getDocId())) return;
        }
        for (Todo todo : open) {
            if (todo.getDocId() != null) pendingToggles.add(todo.getDocId());
        }
        // Optimistic: every chosen row flips at once so the list reacts instantly.
        for (Todo todo : open) {
            todo.setCompleted(true);
            todo.setCompletedAt(com.google.firebase.Timestamp.now());
            ReminderScheduler.cancelTodo(requireContext(), todo);
        }
        final int total = open.size();
        adapter.clearSelection();
        publish();
        repository.completeMany(uid, open, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                for (Todo todo : open) {
                    if (todo.getDocId() != null) pendingToggles.remove(todo.getDocId());
                }
                if (isAdded()) {
                    Toast.makeText(requireContext(),
                            getString(R.string.todo_bulk_done_msg, total), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception error) {
                // Roll the optimistic flips back, exactly like toggle() does.
                // Without this the rows stayed ticked on screen until the next
                // snapshot arrived, so the user believed work was saved that was not.
                for (Todo todo : open) {
                    if (todo.getDocId() != null) pendingToggles.remove(todo.getDocId());
                    todo.setCompleted(false);
                    todo.setCompletedAt(null);
                    ReminderScheduler.scheduleTodo(requireContext(), uid, todo);
                }
                publish();
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void bulkDelete() {
        String uid = currentUid();
        List<Todo> chosen = adapter.selectedTodos();
        if (uid == null || chosen.isEmpty()) return;
        final int total = chosen.size();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.todo_bulk_delete_msg, total))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    List<Todo> removed = new ArrayList<>(chosen);
                    List<Integer> positions = new ArrayList<>();
                    for (Todo todo : removed) {
                        positions.add(all.indexOf(todo));
                        ReminderScheduler.cancelTodo(requireContext(), todo);
                    }
                    for (Todo todo : removed) all.remove(todo);
                    adapter.clearSelection();
                    publish();
                    repository.deleteMany(uid, removed, new TodoRepository.SaveCallback() {
                        @Override
                        public void onSuccess(String id) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        getString(R.string.todo_bulk_deleted, total),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            // Put the rows back. Deleting from the local list
                            // before the write succeeded made a failed delete
                            // look like a successful one until the next refresh.
                            for (int i = 0; i < removed.size(); i++) {
                                int at = positions.get(i);
                                if (at >= 0 && at <= all.size()) all.add(at, removed.get(i));
                                else all.add(removed.get(i));
                                ReminderScheduler.scheduleTodo(requireContext(), uid, removed.get(i));
                            }
                            publish();
                            if (isAdded()) {
                                Toast.makeText(requireContext(), R.string.delete_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                })
                .show();
    }

    private void setStarred(Todo todo, boolean starred) {
        String uid = currentUid();
        if (uid == null || todo.getDocId() == null) return;
        todo.setStarred(starred);
        if (todo.getDocId() != null) adapter.notifyDataSetChanged();
        repository.setStarred(uid, todo, starred, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
            }

            @Override
            public void onError(Exception error) {
                todo.setStarred(!starred);
                if (binding != null) adapter.notifyDataSetChanged();
            }
        });
    }

    private void chooseCategory() {
        if (categoryNames.isEmpty()) return;
        int checked = categoryId == null ? 0 : Math.max(0, categoryIds.indexOf(categoryId));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_filter_category)
                .setSingleChoiceItems(categoryNames.toArray(new String[0]), checked, (dialog, which) -> {
                    categoryId = categoryIds.get(which);
                    binding.categoryFilter.setText(which == 0
                            ? getString(R.string.todo_filter_category) : categoryNames.get(which));
                    dialog.dismiss();
                    apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void choosePriority() {
        String[] options = {
                getString(R.string.filter_all),
                getString(R.string.priority_low),
                getString(R.string.priority_medium),
                getString(R.string.priority_high)
        };
        int checked = priority < 0 ? 0 : priority + 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_filter_priority)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    priority = which - 1;
                    binding.priorityFilter.setText(which == 0
                            ? getString(R.string.todo_filter_priority) : options[which]);
                    dialog.dismiss();
                    apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Re-renders the list <b>and</b> tells the ViewModel, so the header counters in
     * {@code TodoActivity} follow an optimistic flip immediately instead of waiting
     * for the next Firestore snapshot (which may never come while offline).
     */
    private void publish() {
        viewModel.refresh(all);
        apply();
    }

    private void apply() {        if (binding == null) return;
        Calendar today = Calendar.getInstance();
        List<Todo> filtered = new ArrayList<>();
        for (Todo todo : all) {
            if (query.length() > 0 && !matchesQuery(todo)) continue;
            if (categoryId != null && !categoryId.equals(todo.getCategoryId())) continue;
            if (priority >= 0 && todo.getPriority() != priority) continue;
            if (!matchesDueRange(todo)) continue;
            if (!matchesSmartList(todo)) continue;

            Calendar due = todo.getDueDate() == null ? null
                    : Dates.calendarOf(todo.getDueDate().toDate());
            boolean sameDay = due != null && Dates.isSameDay(due, today);
            boolean future = due != null && Dates.startOfDay(due).after(Dates.startOfDay(today));

            // Where a finished task is shown depends on the tab:
            //   • "Done" (history)  -> only finished tasks, newest first.
            //   • All               -> both, so a task the user just ticked stays in
            //                          place with its "Selesai" badge instead of
            //                          vanishing with a confusing "no matching tasks".
            //   • Today/Upcoming/Overdue -> still-to-do work only, because those tabs
            //                          answer "what do I need to do by then".
            if ("completed".equals(status)) {
                if (!todo.isCompleted()) continue;
            } else if ("all".equals(status)) {
                // Keep everything; finished rows render with the done badge.
            } else {
                if (todo.isCompleted()) continue;
                if ("today".equals(status)) {
                    if (!sameDay) continue;
                } else if ("upcoming".equals(status)) {
                    if (!future) continue;
                } else if ("overdue".equals(status)) {
                    if (!todo.isOverdue()) continue;
                }
            }
            filtered.add(todo);
        }
        sortTasks(filtered);
        adapter.submit(filtered, buildSections(filtered));
        updateHeader(filtered);
        updateBulkBar();

        if (filtered.isEmpty()) {
            binding.emptyState.getRoot().setVisibility(View.VISIBLE);
            boolean filteredView = hasActiveFilters();
            if (smartMode != SMART_NONE) {
                // A smart list with nothing in it is good news, not a mistake.
                binding.emptyState.emptyTitle.setText(R.string.todo_smart_empty);
                binding.emptyState.emptyDesc.setText(smartEmptyHint());
                binding.emptyState.emptyAction.setVisibility(View.GONE);
            } else if (all.isEmpty() && !filteredView) {
                binding.emptyState.emptyTitle.setText(R.string.todo_empty_title);
                binding.emptyState.emptyDesc.setText(R.string.todo_empty_desc);
                binding.emptyState.emptyAction.setVisibility(View.VISIBLE);
            } else if ("completed".equals(status) && query.isEmpty()) {
                // History is empty: that is good news, not an error.
                binding.emptyState.emptyTitle.setText(R.string.todo_history_empty_title);
                binding.emptyState.emptyDesc.setText(R.string.todo_history_empty_desc);
                binding.emptyState.emptyAction.setVisibility(View.GONE);
            } else {
                binding.emptyState.emptyTitle.setText(R.string.todo_no_results_title);
                binding.emptyState.emptyDesc.setText(R.string.todo_no_results_desc);
                binding.emptyState.emptyAction.setVisibility(View.GONE);
            }
        } else {
            binding.emptyState.getRoot().setVisibility(View.GONE);
        }
        updateFilterChips();
    }

    /**
     * Header line under the controls.
     *
     * <p>Two answers live here. In Focus mode it explains <em>why</em> the list
     * is ordered this way, so the ranking is never a mystery. Everywhere else it
     * folds the time estimates of the visible tasks into a single number, which
     * is the difference between "7 tasks" (reassuring, meaningless) and
     * "7 tasks · ~4 h 30 min" (a decision the user can actually make).</p>
     */
    private void updateHeader(List<Todo> filtered) {
        if (binding == null) return;
        binding.focusCaption.setVisibility(
                smartMode == SMART_FOCUS ? View.VISIBLE : View.GONE);

        long minutes = 0L;
        int open = 0;
        for (Todo todo : filtered) {
            if (todo.isCompleted()) continue;
            open++;
            if (todo.hasEstimate()) minutes += todo.getEstimateMinutes();
        }

        String count = getResources().getQuantityString(
                R.plurals.plural_tasks, filtered.size(), filtered.size());
        // Only warn when there is genuinely more work than a day holds. The
        // threshold is deliberately generous (7 h) so it does not nag people
        // with a normal Tuesday.
        boolean heavy = minutes >= 420L && open > 1;
        binding.loadWarning.setVisibility(heavy ? View.VISIBLE : View.GONE);
        if (minutes <= 0) {
            binding.resultCount.setText(count);
            binding.resultCount.setTextColor(ContextCompat.getColor(
                    requireContext(), R.color.color_on_surface_variant));
            return;
        }
        // Estimated work plus the task count, e.g. "7 tasks · ~2 h 10 min".
        String text = getString(R.string.todo_load_summary, count,
                Dates.formatDuration(requireContext(), minutes));
        binding.resultCount.setText(text);
        // More than seven hours of open work is not a plan, it is a wish. Flag it
        // in the warning colour instead of silently letting the day fail.
        binding.resultCount.setTextColor(ContextCompat.getColor(requireContext(),
                minutes >= 420L ? R.color.warning : R.color.color_on_surface_variant));
    }

    /**
     * Applies the smart-list filter. Each list answers a different question about
     * the backlog, so they are mutually exclusive (the ChipGroup is single choice).
     */
    private boolean matchesSmartList(Todo todo) {
        switch (smartMode) {
            case SMART_FOCUS:
                return todo.focusScore() != Long.MIN_VALUE;
            case SMART_STARRED:
                return todo.isStarred() && !todo.isCompleted();
            case SMART_STALE:
                return todo.isStale(Prefs.staleDays(requireContext()));
            case SMART_QUICK:
                return todo.isQuickWin();
            case SMART_SUBTASKS:
                return todo.hasSubtasks() && !todo.isCompleted();
            default:
                return true;
        }
    }

    private int smartEmptyHint() {
        switch (smartMode) {
            case SMART_FOCUS:
                return R.string.todo_focus_empty;
            case SMART_STARRED:
                return R.string.todo_star_hint;
            case SMART_STALE:
                return R.string.todo_stale_hint;
            case SMART_QUICK:
                return R.string.todo_quick_hint;
            case SMART_SUBTASKS:
                return R.string.todo_subtasks_hint;
            default:
                return R.string.todo_no_results_desc;
        }
    }

    /**
     * Builds the heading list. Grouping is a setting because some people want the
     * date buckets and others want one flat, sort-ordered list — the data is the
     * same, only the headers differ.
     */
    @Nullable
    private List<TodoAdapter.Section> buildSections(List<Todo> filtered) {
        if (!Prefs.groupTasks(requireContext()) || filtered.isEmpty()) return null;
        String[] order = {"overdue", "today", "week", "later", "nodate", "done"};
        int[] titles = {R.string.todo_section_overdue, R.string.todo_section_today,
                R.string.todo_section_week, R.string.todo_section_later,
                R.string.todo_section_nodate, R.string.todo_section_done};
        List<TodoAdapter.Section> sections = new ArrayList<>();
        for (int i = 0; i < order.length; i++) {
            int count = 0;
            for (Todo todo : filtered) {
                if (order[i].equals(TodoAdapter.sectionKeyOf(todo))) count++;
            }
            if (count > 0) {
                sections.add(new TodoAdapter.Section(order[i],
                        getString(titles[i]), count));
            }
        }
        return sections;
    }

    /**
     * Full-text search across everything the user can see on a card: title,
     * description, category, priority and subtask titles. Completed history is
     * searched the same way once the "Completed" chip is selected.
     */
    private boolean matchesQuery(Todo todo) {
        if (query.isEmpty()) return true;
        StringBuilder haystack = new StringBuilder();
        haystack.append(todo.getTitle() == null ? "" : todo.getTitle()).append(' ')
                .append(todo.getDescription() == null ? "" : todo.getDescription());

        TodoCategory category = todo.getCategoryId() == null
                ? null : categories.get(todo.getCategoryId());
        String categoryName = category == null
                ? (todo.getCategoryName() == null ? "" : todo.getCategoryName())
                : com.example.pencatatan.common.NameKeys.resolve(requireContext(),
                category.getName(), category.getNameKey());
        haystack.append(' ').append(categoryName);

        String priorityLabel = todo.getPriority() == 2 ? getString(R.string.priority_high)
                : todo.getPriority() == 0 ? getString(R.string.priority_low)
                : getString(R.string.priority_medium);
        haystack.append(' ').append(priorityLabel);

        if (todo.getSubtasks() != null) {
            for (Map<String, Object> subtask : todo.getSubtasks()) {
                Object title = subtask == null ? null : subtask.get("title");
                if (title != null) haystack.append(' ').append(title);
            }
        }
        return haystack.toString().toLowerCase(Locale.getDefault()).contains(query);
    }

    private boolean matchesDueRange(Todo todo) {
        if (DUE_ANY.equals(dueMode)) return true;
        Date due = todo.getDueDate() == null ? null : todo.getDueDate().toDate();
        if (due == null) return false;
        if ("overdue".equals(dueMode)) return todo.isOverdue();
        if ("week".equals(dueMode) || "month".equals(dueMode)) {
            int days = "week".equals(dueMode) ? 7 : 30;
            Calendar start = Dates.startOfDay(Calendar.getInstance());
            Calendar end = Dates.endOfDay(Calendar.getInstance());
            end.add(Calendar.DAY_OF_MONTH, days);
            return !due.before(start.getTime()) && !due.after(end.getTime());
        }
        if ("custom".equals(dueMode) && dueFrom != null && dueTo != null) {
            return !due.before(dueFrom) && !due.after(dueTo);
        }
        return true;
    }

    private boolean hasActiveFilters() {
        return !"all".equals(status) || categoryId != null || priority >= 0
                || !query.isEmpty() || !DUE_ANY.equals(dueMode) || smartMode != SMART_NONE;
    }

    private void updateFilterChips() {
        int active = 0;
        if (!"all".equals(status)) active++;
        if (categoryId != null) active++;
        if (priority >= 0) active++;
        if (!DUE_ANY.equals(dueMode)) active++;
        if (!query.isEmpty()) active++;
        if (smartMode != SMART_NONE) active++;

        binding.activeFilterBar.setVisibility(active > 0 ? View.VISIBLE : View.GONE);
        if (active > 0) {
            binding.activeFilterText.setText(getString(R.string.finance_filter_active, active));
        }
        binding.dateFilter.setText(dueLabel());
    }

    private String dueLabel() {
        switch (dueMode) {
            case "week":
                return getString(R.string.todo_filter_date_week);
            case "month":
                return getString(R.string.todo_filter_date_month);
            case "overdue":
                return getString(R.string.todo_filter_date_overdue);
            case "custom":
                if (dueFrom != null && dueTo != null) {
                    return getString(R.string.finance_filter_range_label,
                            Dates.formatShortDate(requireContext(), dueFrom),
                            Dates.formatShortDate(requireContext(), dueTo));
                }
                return getString(R.string.todo_filter_date_custom);
            default:
                return getString(R.string.todo_filter_date);
        }
    }

    private void chooseDueRange() {
        String[] modes = {DUE_ANY, "week", "month", "overdue", "custom"};
        String[] labels = {
                getString(R.string.todo_filter_date_any),
                getString(R.string.todo_filter_date_week),
                getString(R.string.todo_filter_date_month),
                getString(R.string.todo_filter_date_overdue),
                getString(R.string.todo_filter_date_custom)
        };
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(dueMode)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.todo_filter_date)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if ("custom".equals(modes[which])) {
                        dialog.dismiss();
                        pickCustomDueRange();
                        return;
                    }
                    dueMode = modes[which];
                    dialog.dismiss();
                    apply();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickCustomDueRange() {
        Calendar today = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar start = Calendar.getInstance();
            start.set(year, month, day, 0, 0, 0);
            start.set(Calendar.MILLISECOND, 0);
            new DatePickerDialog(requireContext(), (endView, endYear, endMonth, endDay) -> {
                Calendar end = Calendar.getInstance();
                end.set(endYear, endMonth, endDay, 23, 59, 59);
                end.set(Calendar.MILLISECOND, 999);
                dueFrom = start.getTime();
                dueTo = end.getTime();
                if (dueTo.before(dueFrom)) {
                    Date tmp = dueFrom;
                    dueFrom = dueTo;
                    dueTo = tmp;
                }
                dueMode = "custom";
                apply();
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH))
                    .show();
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void resetFilters() {
        status = "all";
        categoryId = null;
        priority = -1;
        query = "";
        dueMode = DUE_ANY;
        dueFrom = null;
        dueTo = null;
        smartMode = SMART_NONE;
        binding.filterGroup.check(R.id.filterAll);
        binding.smartGroup.clearCheck();
        binding.searchEdit.setText("");
        binding.categoryFilter.setText(R.string.todo_filter_category);
        binding.priorityFilter.setText(R.string.todo_filter_priority);
        apply();
        Toast.makeText(requireContext(), R.string.finance_filter_clear_done,
                Toast.LENGTH_SHORT).show();
    }

    private void sortTasks(List<Todo> list) {
        String sort = Prefs.getTaskSort(requireContext());
        Comparator<Todo> comparator;
        if (Prefs.SORT_PRIORITY.equals(sort)) {
            comparator = (a, b) -> {
                int cmp = Long.compare(b.getPriority(), a.getPriority());
                return cmp != 0 ? cmp : compareDue(a, b);
            };
        } else if (Prefs.SORT_CREATED.equals(sort)) {
            comparator = (a, b) -> {
                long ta = a.getCreatedAt() == null ? 0 : a.getCreatedAt().toDate().getTime();
                long tb = b.getCreatedAt() == null ? 0 : b.getCreatedAt().toDate().getTime();
                return Long.compare(tb, ta);
            };
        } else if (Prefs.SORT_TITLE.equals(sort)) {
            comparator = (a, b) -> {
                String ta = a.getTitle() == null ? "" : a.getTitle();
                String tb = b.getTitle() == null ? "" : b.getTitle();
                return ta.compareToIgnoreCase(tb);
            };
        } else {
            comparator = this::compareDue;
        }
        // Unfinished work always sorts above finished work, whatever the chosen
        // order is. In the "Done" tab every row is finished, so this is a no-op
        // there and the history keeps its own ordering.
        Comparator<Todo> byDone = (a, b) -> Boolean.compare(a.isCompleted(), b.isCompleted());
        list.sort(byDone.thenComparing(comparator));
    }
    private int compareDue(Todo a, Todo b) {
        boolean an = a.getDueDate() == null;
        boolean bn = b.getDueDate() == null;
        if (an && bn) return 0;
        if (an) return 1;
        if (bn) return -1;
        return a.getDueDate().toDate().compareTo(b.getDueDate().toDate());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) apply();
    }

    @Override
    public void onDestroyView() {
        // Never leave the host's FAB hidden if we go away mid-selection.
        View fab = requireActivity().findViewById(R.id.addFab);
        if (fab != null) fab.setVisibility(View.VISIBLE);
        super.onDestroyView();
        binding = null;
    }
}
