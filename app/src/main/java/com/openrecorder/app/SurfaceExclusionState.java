// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

/** Cache by native layer ID, since a SurfaceControl object can be reused for a new layer. */
final class SurfaceExclusionState {
    private int excludedLayerId = -1;

    boolean needsApply(int layerId) {
        return layerId < 0 || layerId != excludedLayerId;
    }

    void applied(int layerId) {
        excludedLayerId = layerId;
    }
}
