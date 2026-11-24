package com.example.pencatatan.todo;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pencatatan.R;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.databinding.DialogQuickCaptureBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Calendar;

/**
 * Add a task without leaving the task list.
 *
 * <p>The full form is the right place to shape a task, but it is the wrong place
 * to jot one down: five taps and a screen transition for "buy soap" is exactly
 * the friction that makes people stop capturing. This sheet reduces that to
 * type + Add, with the four things people reach for most (today, tomorrow,
 * high priority, star) as one-tap chips.</p>
 *
 * <p>It is intentionally a fragment rather than a plain Dialog so a rotation or
 * a process kill does not lose what was typed. "More options" hands the typed
 * title over to the full form instead of duplicating its fields here.</p>
 */
public class QuickCaptureDialog extends BottomSheetDialogFragment {

    public interface OnTaskAdded {
        void onTaskAdded();
    }

    /** Extra keys used to hand the typed title to the full editor. */
    public static final String RESULT_TITLE = "quick_capture_title";

    private DialogQuickCaptureBinding binding;
    private final TodoRepository repository = new TodoRepository();
    private OnTaskAdded callback;
    private String uid;

    public static QuickCaptureDialog newInstance() {
        return new QuickCaptureDialog();
    }

    public void setOnTaskAdded(OnTaskAdded callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // The sheet is short on purpose; it must not try to grow to full height
        // when the keyboard opens or the chips end up hidden behind it.
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogQuickCaptureBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        uid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Today and Tomorrow are mutually exclusive; the other two toggle freely.
        binding.chipToday.setOnCheckedChangeListener((button, checked) -> {
            if (checked) binding.chipTomorrow.setChecked(false);
        });
        binding.chipTomorrow.setOnCheckedChangeListener((button, checked) -> {
            if (checked) binding.chipToday.setChecked(false);
        });

        binding.saveButton.setOnClickListener(v -> save());
        binding.moreButton.setOnClickListener(v -> openFullForm());
        binding.titleEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save();
                return true;
            }
            return false;
        });

        binding.titleEdit.requestFocus();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            // Show the keyboard immediately: the whole point is typing right away.
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void save() {
        String title = text(binding.titleEdit);
        if (title.isEmpty()) {
            binding.titleLayout.setError(getString(R.string.todo_quick_capture_empty));
            return;
        }
        if (uid == null) {
            dismiss();
            return;
        }
        binding.titleLayout.setError(null);
        binding.saveButton.setEnabled(false);

        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setPriority(binding.chipHigh.isChecked() ? 2L
                : Prefs.getDefaultPriority(requireContext()));
        todo.setStarred(binding.chipStar.isChecked());
        String categoryId = Prefs.getDefaultCategoryId(requireContext());
        if (categoryId != null && !categoryId.isEmpty()) {
            todo.setCategoryId(categoryId);
            todo.setCategoryName(Prefs.getDefaultCategoryName(requireContext()));
        }
        if (binding.chipToday.isChecked()) {
            todo.setDueDate(endOfToday());
        } else if (binding.chipTomorrow.isChecked()) {
            todo.setDueDate(endOfTomorrow());
        }

        repository.saveTodo(uid, todo, true, new TodoRepository.SaveCallback() {
            @Override
            public void onSuccess(String id) {
                if (!isAdded()) return;
                if (callback != null) callback.onTaskAdded();
                dismiss();
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded()) return;
                binding.saveButton.setEnabled(true);
                binding.titleLayout.setError(getString(R.string.save_failed));
            }
        });
    }

    /**
     * Hands the typed title to the full form instead of duplicating every field
     * here. The sheet closes first so the user never sees two editors stacked.
     */
    private void openFullForm() {
        String title = text(binding.titleEdit);
        TodoActivity activity = (requireActivity() instanceof TodoActivity)
                ? (TodoActivity) requireActivity() : null;
        dismiss();
        if (activity != null) activity.openNewTaskWithTitle(title);
    }

    /** A due date always means "by the end of that day", so it is never instantly overdue. */
    private Timestamp endOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return new Timestamp(calendar.getTime());
    }

    private Timestamp endOfTomorrow() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return new Timestamp(calendar.getTime());
    }

    private static String text(@Nullable android.widget.EditText edit) {
        return edit == null || edit.getText() == null
                ? "" : edit.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
