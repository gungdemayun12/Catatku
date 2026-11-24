package com.example.pencatatan.todo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.TodoCategory;
import com.example.pencatatan.data.repository.TodoRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class TodoViewModel extends ViewModel {

    private final TodoRepository repository = new TodoRepository();
    private final MutableLiveData<List<Todo>> todos = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<TodoCategory>> categories =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration todoListener;
    private ListenerRegistration categoryListener;
    private String uid;

    public LiveData<List<Todo>> todos() {
        return todos;
    }

    /**
     * Publishes an updated list to every observer (the list itself and the header
     * counts). Used for an optimistic flip: the screen should show the new state
     * immediately, but {@link #todos} holds the same objects as the listener, so a
     * plain mutation would never notify anyone and the header would keep stale
     * "N to do" counts.
     */
    public void refresh(List<Todo> updated) {
        todos.setValue(updated == null ? new ArrayList<>() : new ArrayList<>(updated));
    }

    public LiveData<List<TodoCategory>> categories() {
        return categories;
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    public LiveData<String> error() {
        return error;
    }

    public void start(String uid) {
        if (uid == null || uid.equals(this.uid)) return;
        this.uid = uid;
        stop();
        todoListener = repository.listenTodos(uid, new TodoRepository.OnTodos() {
            @Override
            public void onData(List<Todo> data) {
                todos.setValue(data);
                loading.setValue(false);
            }

            @Override
            public void onError(Exception e) {
                error.setValue(e == null ? null : e.getMessage());
                loading.setValue(false);
            }
        });
        categoryListener = repository.listenCategories(uid, new TodoRepository.OnCategories() {
            @Override
            public void onData(List<TodoCategory> data) {
                categories.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                // Categories are supplementary; tasks stay usable without them.
            }
        });
    }

    private void stop() {
        if (todoListener != null) todoListener.remove();
        if (categoryListener != null) categoryListener.remove();
        todoListener = null;
        categoryListener = null;
    }

    @Override
    protected void onCleared() {
        stop();
        super.onCleared();
    }
}
