package com.example.pencatatan.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Covers the arithmetic behind {@link FabPadding}: the extra room a list reserves
 * so the extended FAB never covers the last card at large font sizes.
 */
public class FabPaddingMathTest {

    private static final float BASE_DP = 96f;

    /** Mirrors the calculation inside FabPadding.apply for a 1x density. */
    private static int bottomPadding(float fontScale, float density) {
        int extra = Math.round(Math.max(0f, fontScale - 1f) * BASE_DP * density);
        return Math.round(BASE_DP * density) + extra;
    }

    @Test
    public void normalFont_keepsTheOriginalPadding() {
        assertEquals(96, bottomPadding(1.0f, 1f));
    }

    @Test
    public void largeFont_addsRoomProportionalToTheScale() {
        // 150% font needs noticeably more room than the shipped 96dp.
        assertEquals(96 + 48, bottomPadding(1.5f, 1f));
        assertEquals(96 + 29, bottomPadding(1.3f, 1f));
    }

    @Test
    public void paddingNeverShrinksBelowTheBase() {
        // A font scale below 1 (possible via adb) must not remove the safety room.
        assertEquals(96, bottomPadding(0.85f, 1f));
    }

    @Test
    public void accountsForScreenDensity() {
        // 440dpi device: density ~2.75, so the dp value is scaled up.
        float density = 2.75f;
        assertEquals(Math.round(96 * density), bottomPadding(1.0f, density));
    }
}
