// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordingControlsPolicyTest {
    @Test public void recordingDoesNotExposeUnprotectedFirstFrame() {
        RecordingControlsPolicy policy = new RecordingControlsPolicy(false, true);
        assertTrue(policy.needsExclusion());
        assertFalse(policy.visible(false));
        assertTrue(policy.visible(true));
    }

    @Test public void excludedBarStaysVisibleAcrossPauseAndResume() {
        RecordingControlsPolicy policy = new RecordingControlsPolicy(false, true);
        policy.exclusionApplied();
        assertTrue(policy.visible(false));
        assertTrue(policy.visible(true));
        assertTrue(policy.visible(false));
        assertTrue(policy.needsExclusion()); // A new surface must also be excluded.
    }

    @Test public void unsupportedSystemFallsBackToPauseOnly() {
        RecordingControlsPolicy policy = new RecordingControlsPolicy(false, true);
        policy.exclusionUnavailable();
        assertFalse(policy.needsExclusion());
        assertFalse(policy.visible(false));
        assertTrue(policy.visible(true));
        assertFalse(policy.visible(false));
    }

    @Test public void failureAfterSurfaceRecreationRevokesRecordingVisibility() {
        RecordingControlsPolicy policy = new RecordingControlsPolicy(false, true);
        policy.exclusionApplied();
        policy.exclusionUnavailable();
        assertFalse(policy.visible(false));
        assertTrue(policy.visible(true));
        assertFalse(policy.needsExclusion());
    }

    @Test public void explicitPauseOnlyModeTakesPrecedence() {
        RecordingControlsPolicy policy = new RecordingControlsPolicy(true, true);
        assertFalse(policy.needsExclusion());
        assertFalse(policy.visible(false));
        assertTrue(policy.visible(true));
    }

    @Test public void disablingExclusionAllowsOrdinaryCapturedControls() {
        RecordingControlsPolicy policy = new RecordingControlsPolicy(false, false);
        assertFalse(policy.needsExclusion());
        assertTrue(policy.visible(false));
        assertTrue(policy.visible(true));
    }

    @Test public void pauseOnlyStillWorksWithExclusionDisabled() {
        RecordingControlsPolicy policy = new RecordingControlsPolicy(true, false);
        assertFalse(policy.needsExclusion());
        assertFalse(policy.visible(false));
        assertTrue(policy.visible(true));
    }
}
