// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

/** Keeps a requested quality target within the selected encoder's advertised limits. */
final class VideoBitratePolicy {
    private VideoBitratePolicy() {}

    static int supportedTarget(int requested, int minimum, int maximum) {
        if (requested <= 0 || minimum <= 0 || maximum < minimum) {
            throw new IllegalArgumentException("Invalid video bitrate range");
        }
        return Math.max(minimum, Math.min(maximum, requested));
    }
}
