// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SurfaceExclusionStateTest {
    @Test public void unchangedSurfaceAvoidsRepeatedSynchronousTransactions() {
        SurfaceExclusionState state = new SurfaceExclusionState();
        assertTrue(state.needsApply(42));
        state.applied(42);
        for (int draw = 0; draw < 120; draw++) assertFalse(state.needsApply(42));
    }

    @Test public void recreatedSurfaceMustBeExcludedBeforeItsFirstFrame() {
        SurfaceExclusionState state = new SurfaceExclusionState();
        state.applied(42);
        assertTrue(state.needsApply(43));
        state.applied(43);
        assertFalse(state.needsApply(43));
    }

    @Test public void failedAttemptIsNotRememberedAsExcluded() {
        SurfaceExclusionState state = new SurfaceExclusionState();
        assertTrue(state.needsApply(42));
        assertTrue(state.needsApply(42));
    }

    @Test public void unavailableIdentifierPreservesPerDrawProtection() {
        SurfaceExclusionState state = new SurfaceExclusionState();
        state.applied(-1);
        assertTrue(state.needsApply(-1));
        state.applied(42);
        assertTrue(state.needsApply(-1));
    }
}
