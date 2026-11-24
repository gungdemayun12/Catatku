package com.example.pencatatan.auth;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.pencatatan.R;
import com.example.pencatatan.common.RepoCallback;
import com.example.pencatatan.data.repository.UserRepository;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import androidx.annotation.Nullable;

public class AuthRepository {

    private static final String TAG = "CatatKuAuth";

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);

        void onError(Exception error);
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final UserRepository userRepository = new UserRepository();

    /** Kept so the classic flow can sign the user out of Play Services on logout. */
    @Nullable
    private GoogleSignInClient classicClient;
    @Nullable
    private AuthCallback classicCallback;

    public FirebaseUser currentUser() {
        return auth.getCurrentUser();
    }

    /**
     * Firebase signs the new user in as soon as the account is created. We want the user to
     * sign in deliberately with the credentials they just chose, so the session is dropped here.
     */
    public void signOutLocally() {
        auth.signOut();
    }

    public void login(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        callback.onSuccess(task.getResult().getUser());
                    } else {
                        callback.onError(task.getException());
                    }
                });
    }

    public void register(String name, String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        callback.onError(task.getException());
                        return;
                    }
                    FirebaseUser user = task.getResult().getUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("missing_user"));
                        return;
                    }
                    UserProfileChangeRequest update = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();
                    user.updateProfile(update).addOnCompleteListener(ignored -> {
                        userRepository.ensureProfile(user, name, null, new RepoCallback() {
                            @Override
                            public void onSuccess() {
                                callback.onSuccess(user);
                            }

                            @Override
                            public void onError(Exception error) {
                                callback.onError(error);
                            }
                        });
                    });
                });
    }

    /**
     * Keeps the Firebase Auth display name in sync with the Firestore profile name.
     * Best effort: a failure here never blocks the profile save.
     */
    public void updateDisplayName(String name) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || name == null) return;
        UserProfileChangeRequest update = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        user.updateProfile(update);
    }

    public void sendPasswordReset(String email, AuthCallback callback) {        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onSuccess(null);
            } else {
                callback.onError(task.getException());
            }
        });
    }

    /**
     * Re-signs the current user in with their password.
     *
     * <p>Firebase refuses account deletion ("requires recent login"). Without
     * this the delete button was simply unusable for anyone who had been signed
     * in for more than a few minutes.</p>
     */
    public void reauthenticate(String email, String password, AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || email == null || password == null || password.isEmpty()) {
            callback.onError(new IllegalStateException("recent_login_required"));
            return;
        }
        AuthCredential credential = EmailAuthProvider.getCredential(email, password);
        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) callback.onSuccess(auth.getCurrentUser());
            else callback.onError(task.getException());
        });
    }

    /**
     * Google Sign-In through Credential Manager, with a classic Google Sign-In fallback.
     *
     * <p>Credential Manager is the modern route, but on many devices (and most emulators) the
     * "Google ID" flow answers "no credential" even though the phone is clearly signed in to
     * a Google account. Reporting that as "no account on this device" was simply wrong, so
     * every failure now walks down a chain:</p>
     *
     * <ol>
     *   <li>{@link GetGoogleIdOption} with authorized-account filtering off (all accounts),</li>
     *   <li>{@link GetSignInWithGoogleOption}, the explicit account picker,</li>
     *   <li>the classic {@link GoogleSignInClient} picker, which reads accounts from
     *       Play Services directly.</li>
     * </ol>
     */
    public void signInWithGoogle(Activity activity, AuthCallback callback) {
        String clientId;
        try {
            clientId = activity.getString(R.string.default_web_client_id);
        } catch (Exception e) {
            callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, e));
            return;
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            // Missing default_web_client_id means google-services.json has no OAuth client.
            callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, null));
            return;
        }

        // 1) Credential Manager, every account on the device.
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        requestCredential(activity, request, clientId, GoogleStage.ALL_ACCOUNTS, callback);
    }

    /** Which rung of the fallback ladder we are on. */
    private enum GoogleStage {
        /** {@code GetGoogleIdOption}: silently picks an existing authorized account. */
        ALL_ACCOUNTS,
        /** {@code GetSignInWithGoogleOption}: shows the Credential Manager picker. */
        PICKER,
        /** Classic {@code GoogleSignInClient}: the last resort before giving up. */
        CLASSIC
    }

    private void requestCredential(Activity activity, GetCredentialRequest request, String clientId,
                                    GoogleStage stage, AuthCallback callback) {
        CredentialManager manager = CredentialManager.create(activity);
        manager.getCredentialAsync(activity, request, null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleGoogleCredential(activity, response, callback);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onError(new GoogleSignInError(GoogleSignInError.CANCELLED, e));
                            return;
                        }
                        if (stage != GoogleStage.CLASSIC) {
                            Log.w(TAG, "Credential Manager (" + stage + ") failed, trying the "
                                    + "next Google sign-in method", e);
                            nextGoogleStage(activity, clientId, stage, callback);
                            return;
                        }
                        // Even the classic picker failed. Report what actually happened
                        // instead of blaming a missing account.
                        if (e instanceof NoCredentialException) {
                            callback.onError(new GoogleSignInError(
                                    GoogleSignInError.NO_ACCOUNT, e));
                        } else {
                            Log.e(TAG, "Credential Manager error", e);
                            callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, e));
                        }
                    }
                });
    }

    /** Moves one step down the ladder. */
    private void nextGoogleStage(Activity activity, String clientId, GoogleStage failed,
                                 AuthCallback callback) {
        if (failed == GoogleStage.ALL_ACCOUNTS) {
            GetCredentialRequest pickerRequest = new GetCredentialRequest.Builder()
                    .addCredentialOption(new GetSignInWithGoogleOption.Builder(clientId).build())
                    .build();
            requestCredential(activity, pickerRequest, clientId, GoogleStage.PICKER, callback);
            return;
        }
        // PICKER failed -> classic Google Sign-In.
        signInClassic(activity, clientId, callback);
    }

    /** Classic Play Services picker, used only when Credential Manager finds nothing. */
    private void signInClassic(Activity activity, String clientId, AuthCallback callback) {
        try {
            GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                    GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(clientId)
                    .requestEmail()
                    .build();
            GoogleSignInClient client = GoogleSignIn.getClient(activity, options);
            classicClient = client;
            classicCallback = callback;
            // The host activity owns the launcher: an Activity result can only be started
            // from an Activity that registered for it.
            if (!(activity instanceof ClassicSignInHost)) {
                classicCallback = null;
                callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, null));
                return;
            }
            ((ClassicSignInHost) activity).launchClassicGoogleSignIn(client.getSignInIntent());
        } catch (Exception e) {
            Log.e(TAG, "Classic Google Sign-In could not start", e);
            classicCallback = null;
            callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, e));
        }
    }

    /**
     * Implemented by an Activity that can start the classic Google Sign-In intent and hand
     * the result back to {@link #handleClassicResult(Intent)}.
     */
    public interface ClassicSignInHost {
        void launchClassicGoogleSignIn(Intent intent);
    }

    /**
     * Completes the classic Google Sign-In flow. Called by the host activity from its
     * activity-result callback.
     */
    public void handleClassicResult(@Nullable Intent data) {
        AuthCallback callback = classicCallback;
        classicCallback = null;
        if (callback == null) return;
        if (data == null) {
            callback.onError(new GoogleSignInError(GoogleSignInError.CANCELLED, null));
            return;
        }
        GoogleSignIn.getSignedInAccountFromIntent(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                GoogleSignInAccount account = task.getResult();
                String idToken = account == null ? null : account.getIdToken();
                if (idToken == null || idToken.isEmpty()) {
                    callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, null));
                    return;
                }
                exchangeToken(idToken, callback);
                return;
            }
            Exception error = task.getException();
            if (error instanceof ApiException) {
                int status = ((ApiException) error).getStatusCode();
                if (status == CommonStatusCodes.CANCELED) {
                    callback.onError(new GoogleSignInError(GoogleSignInError.CANCELLED, error));
                    return;
                }
                if (status == CommonStatusCodes.SIGN_IN_REQUIRED) {
                    callback.onError(new GoogleSignInError(GoogleSignInError.NO_ACCOUNT, error));
                    return;
                }
            }
            Log.e(TAG, "Classic Google Sign-In failed", error);
            callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, error));
        });
    }

    private void handleGoogleCredential(Activity activity, GetCredentialResponse response,
                                        AuthCallback callback) {
        try {
            if (!(response.getCredential() instanceof CustomCredential)) {
                callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, null));
                return;
            }
            GoogleIdTokenCredential googleId = GoogleIdTokenCredential
                    .createFrom(response.getCredential().getData());
            exchangeToken(googleId.getIdToken(), callback);
        } catch (Exception e) {
            Log.e(TAG, "Google token parse failed", e);
            callback.onError(new GoogleSignInError(GoogleSignInError.CONFIG, e));
        }
    }

    private void exchangeToken(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                callback.onError(task.getException());
                return;
            }
            FirebaseUser user = task.getResult().getUser();
            if (user == null) {
                callback.onError(new IllegalStateException("missing_user"));
                return;
            }
            userRepository.ensureProfile(user, user.getDisplayName(),
                    user.getPhotoUrl() == null ? null : user.getPhotoUrl().toString(),
                    new RepoCallback() {
                        @Override
                        public void onSuccess() {
                            callback.onSuccess(user);
                        }

                        @Override
                        public void onError(Exception error) {
                            callback.onError(error);
                        }
                    });
        });
    }

    public void deleteAccount(Activity activity, AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("not_signed_in"));
            return;
        }
        String uid = user.getUid();
        String[] collections = {
                "todos", "todoCategories", "activityLogs", "wallets", "transactions",
                "financeCategories", "budgets", "bills", "exchangeRates", "debts", "savingsGoals"
        };
        deleteNext(uid, collections, 0, new Runnable() {
            @Override
            public void run() {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid).delete()
                        .addOnCompleteListener(task -> {
                            FirebaseUser current = auth.getCurrentUser();
                            if (current == null) {
                                callback.onError(new IllegalStateException("not_signed_in"));
                                return;
                            }
                            current.delete().addOnCompleteListener(deleteTask -> {
                                if (deleteTask.isSuccessful()) callback.onSuccess(null);
                                else callback.onError(deleteTask.getException());
                            });
                        });
            }
        });
    }

    private void deleteNext(String uid, String[] collections, int index, Runnable done) {
        if (index >= collections.length) {
            done.run();
            return;
        }
        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("users").document(uid).collection(collections[index]).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        done.run();
                        return;
                    }
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : task.getResult().getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit().addOnCompleteListener(commit -> deleteNext(uid, collections,
                            index + 1, done));
                });
    }

    public void logout(Activity activity) {
        auth.signOut();
        try {
            if (classicClient != null) classicClient.signOut();
        } catch (Exception ignored) {
        }
        try {
            CredentialManager manager = CredentialManager.create(activity);
            manager.clearCredentialState(new androidx.credentials.ClearCredentialStateRequest(),
                    new kotlin.coroutines.Continuation<kotlin.Unit>() {
                        @Override
                        public kotlin.coroutines.CoroutineContext getContext() {
                            return kotlin.coroutines.EmptyCoroutineContext.INSTANCE;
                        }

                        @Override
                        public void resumeWith(Object result) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }
}
