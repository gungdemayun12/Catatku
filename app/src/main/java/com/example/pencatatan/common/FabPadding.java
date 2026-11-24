package com.example.pencatatan.common;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;

/**
 * Keeps a scrollable list's last items reachable when the floating action button
 * grows with the system font size.
 *
 * <p>Both the To-Do and the Finance list put an extended FAB ("Tugas baru" /
 * "Transaksi baru") over a list that reserves a fixed 96dp of bottom padding.
 * The button's height is driven by its label, so at 130–150% font it becomes
 * taller than that padding and covers the last card: the card looks cut off and
 * its checkbox or amount sits underneath the button, unreachable by tap.</p>
 *
 * <p>Resource qualifiers cannot match on font scale, so the extra room is
 * computed at runtime from {@link Configuration#fontScale}. The value is
 * recalculated on configuration change, which is what happens when the user
 * picks a larger font in Settings → Display while the list is on screen.</p>
 */
public final class FabPadding {

    /** Bottom padding the layouts ship with, in dp. */
    private static final float BASE_DP = 96f;

    private FabPadding() {
    }

    /**
     * Applies a bottom padding that grows with the font scale.
     *
     * @param view list whose bottom padding should be adjusted
     */
    public static void apply(View view) {
        if (view == null) return;
        Resources res = view.getResources();
        float density = res.getDisplayMetrics().density;
        float scale = res.getConfiguration().fontScale;
        // Each 10% of extra font size adds ~10% of the base padding, which tracks
        // the FAB's growth closely enough without needing its measured height.
        int extra = Math.round(Math.max(0f, scale - 1f) * BASE_DP * density);
        int bottom = Math.round(BASE_DP * density) + extra;
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), bottom);
    }
}
