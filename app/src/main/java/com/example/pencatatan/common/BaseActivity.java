package com.example.pencatatan.common;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.pencatatan.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

public abstract class BaseActivity extends AppCompatActivity {

    /**
     * Keeps a screen's bottom edge above the navigation bar and the keyboard.
     *
     * <p>Two very different worlds have to work here:</p>
     *
     * <ul>
     *   <li><b>Window already inset</b> (Android 11 with a nav bar, and every
     *       device where the status/nav bars are opaque): the DecorView only
     *       hands the content a rectangle that stops above the bars, so the root
     *       measures to, say, 1977px on a 2280px screen. Adding padding here
     *       does not move the button up — it makes the weighted scroll view
     *       shorter and squeezes the 16px footer into a 16px sliver. Nothing is
     *       needed, and anything added is actively harmful.</li>
     *   <li><b>Edge-to-edge window</b> (targetSdk 35+, gesture nav): the root
     *       spans the whole 2280px and the footer really is drawn under the nav
     *       bar, so the bottom inset must be turned into padding.</li>
     * </ul>
     *
     * <p>So the inset is only applied when the window actually overlaps the
     * system bars, which is exactly what {@code getRootWindowInsets()} reports.
     * That is decided at insets time rather than guessed up front.</p>
     */
    protected void applyBottomInsets(View root) {
        // fitsSystemWindows would try to consume the insets itself, competing
        // with the listener below; the padding is handled explicitly instead.
        if (root.getFitsSystemWindows()) {
            root.setFitsSystemWindows(false);
        }
        final int basePadding = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root,
                new androidx.core.view.OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v,
                                                                 @NonNull WindowInsetsCompat insets) {
                        // Where the keyboard is up, the IME inset is the real
                        // obstruction and the nav bar is already covered by it.
                        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                        int extra = Math.max(ime.bottom, bars.bottom);
                        int wanted = basePadding + extra;
                        if (v.getPaddingBottom() != wanted) {
                            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                                    v.getPaddingRight(), wanted);
                        }
                        return insets;
                    }
                });
        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Turns the status-bar overlap into top padding.
     *
     * <p>The auth screens paint a teal header behind a transparent status bar.
     * The colour must run to the very top of the screen, but the logo inside it
     * must not — otherwise it is drawn underneath the clock and gets clipped.
     * Padding the header is what separates the two.</p>
     */
    protected void applyTopInsets(View root) {
        if (root.getFitsSystemWindows()) {
            root.setFitsSystemWindows(false);
        }
        final int basePadding = root.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root,
                new androidx.core.view.OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v,
                                                                 @NonNull WindowInsetsCompat insets) {
                        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                        int wanted = basePadding + bars.top;
                        if (v.getPaddingTop() != wanted) {
                            v.setPadding(v.getPaddingLeft(), wanted,
                                    v.getPaddingRight(), v.getPaddingBottom());
                        }
                        return insets;
                    }
                });
        ViewCompat.requestApplyInsets(root);
    }

    @Nullable
    protected String uid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null
                ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    protected void toast(String message) {
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    protected void setupToolbar(Toolbar toolbar, CharSequence title, boolean showBack) {
        toolbar.setTitle(title);
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_back);
            toolbar.setNavigationOnClickListener(v -> {
                if (getOnBackPressedDispatcher().hasEnabledCallbacks()) {
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    finish();
                }
            });
        }
    }

    protected void confirm(String title, String message, String positive, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(positive, (dialog, which) -> onConfirm.run())
                .show();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Every screen tells the repository which currency this session is
        // denominated in, so base-denominated maths (transfer legs, migration)
        // no longer assumes IDR after the user switched currency.
        com.example.pencatatan.data.repository.FinanceRepository.useBaseCurrency(this);
    }
}
