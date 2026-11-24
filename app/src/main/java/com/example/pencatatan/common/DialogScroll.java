package com.example.pencatatan.common;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/**
 * Makes a dialog body scrollable.
 *
 * <p>A {@code MaterialAlertDialogBuilder} sizes its body to the content, and
 * nothing inside it scrolls. On a short screen — or any screen once the keyboard
 * is up — the bottom of a tall form (a wallet's "initial balance" and colour row,
 * a bill's reminder settings) ends up clipped outside the dialog, with no way to
 * reach it: the user drags, the dialog does not move, and the field is simply
 * lost. Wrapping the body in a {@link ScrollView} guarantees every field can be
 * scrolled to.</p>
 *
 * <p>{@code fillViewport} is off so a short form still renders at its natural
 * height instead of stretching to fill the dialog.</p>
 */
public final class DialogScroll {

    private DialogScroll() {
    }

    public static View wrap(View content) {
        if (content instanceof ScrollView) return content;
        ScrollView scroll = new ScrollView(content.getContext());
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        // A nested ScrollView would steal the drag; a plain FrameLayout keeps the
        // layout params the dialog expects while letting the ScrollView measure
        // the content with an unbounded height.
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            // Keep horizontal margins the dialog may have applied.
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) params;
            scroll.setLayoutParams(new FrameLayout.LayoutParams(
                    flp.width, FrameLayout.LayoutParams.WRAP_CONTENT, flp.gravity));
        }
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }
}
