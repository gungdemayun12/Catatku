package com.example.pencatatan.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.pencatatan.common.Dates;
import com.example.pencatatan.data.model.Transaction;
import com.example.pencatatan.data.model.Todo;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.TodoRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<List<Todo>> todos = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Transaction>> transactions = new MutableLiveData<>(new ArrayList<>());

    private final UserRepository userRepository = new UserRepository();
    private final TodoRepository todoRepository = new TodoRepository();
    private final FinanceRepository financeRepository = new FinanceRepository();

    private ListenerRegistration userReg;
    private ListenerRegistration todoReg;
    private ListenerRegistration transactionReg;
    private boolean started = false;
    /** Month the transaction listener is currently bound to, e.g. "2026-02". */
    private String listenedMonth;

    public HomeViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<User> user() {
        return user;
    }

    public LiveData<List<Todo>> todos() {
        return todos;
    }

    public LiveData<List<Transaction>> transactions() {
        return transactions;
    }

    public void start() {
        if (started) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        started = true;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userReg = userRepository.listen(uid, new UserRepository.UserListener() {
            @Override
            public void onUser(User model) {
                user.postValue(model);
            }

            @Override
            public void onError(Exception error) {
            }
        });
        todoReg = todoRepository.listenTodos(uid, new TodoRepository.OnTodos() {
            @Override
            public void onData(List<Todo> list) {
                todos.postValue(list);
            }

            @Override
            public void onError(Exception error) {
            }
        });
        listenCurrentMonth(uid);
    }

    /**
     * Binds the transaction listener to the month that is current <em>now</em>.
     *
     * <p>The listener used to be created once, with the month captured at
     * Activity creation. Left open across midnight on the 1st (or simply left
     * in the background overnight) the home screen kept showing last month's
     * spending while labelling it "this month".</p>
     */
    private void listenCurrentMonth(String uid) {
        String month = Dates.monthKey(Calendar.getInstance());
        if (month.equals(listenedMonth)) return;
        listenedMonth = month;
        if (transactionReg != null) transactionReg.remove();
        transactionReg = financeRepository.listenTransactions(uid, month,
                new FinanceRepository.OnTransactions() {
                    @Override
                    public void onData(List<Transaction> list) {
                        transactions.postValue(list);
                    }

                    @Override
                    public void onError(Exception error) {
                    }
                });
    }

    /** Called when the screen comes back to the front. */
    public void refreshMonth() {
        if (!started) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        listenCurrentMonth(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (userReg != null) userReg.remove();
        if (todoReg != null) todoReg.remove();
        if (transactionReg != null) transactionReg.remove();
    }
}
