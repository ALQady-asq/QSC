// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

/** Keeps controls off captured frames until their surface has been excluded. */
final class RecordingControlsPolicy {
    private final boolean pauseOnly;
    private final boolean excludeFromCapture;
    private boolean excluded;
    private boolean unavailable;

    RecordingControlsPolicy(boolean pauseOnly, boolean excludeFromCapture) {
        this.pauseOnly = pauseOnly;
        this.excludeFromCapture = excludeFromCapture;
    }

    boolean needsExclusion() {
        return excludeFromCapture && !pauseOnly && !unavailable;
    }

    void exclusionApplied() {
        excluded = true;
    }

    void exclusionUnavailable() {
        excluded = false;
        unavailable = true;
    }

    boolean visible(boolean paused) {
        return paused || (!pauseOnly && (!excludeFromCapture || (excluded && !unavailable)));
    }
}
