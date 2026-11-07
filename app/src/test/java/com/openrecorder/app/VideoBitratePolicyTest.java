// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class VideoBitratePolicyTest {
    @Test public void hundredMbpsReachesCapableEncoderUnchanged() {
        assertEquals(100_000_000, VideoBitratePolicy.supportedTarget(
                RecordingOptions.VIDEO_BITRATE_100_MBPS, 64_000, 120_000_000));
    }

    @Test public void limitedHardwareKeepsItsSupportedTargetInsteadOfFailingOrUsingSoftware() {
        assertEquals(80_000_000, VideoBitratePolicy.supportedTarget(
                RecordingOptions.VIDEO_BITRATE_100_MBPS, 64_000, 80_000_000));
    }

    @Test public void lowerEncoderBoundIsAlsoRespected() {
        assertEquals(5_000_000, VideoBitratePolicy.supportedTarget(4_000_000, 5_000_000, 80_000_000));
    }

    @Test public void existingQualityChoicesArePreservedInsideRange() {
        for (int target : new int[]{4_000_000, 16_000_000, 60_000_000, 80_000_000}) {
            assertEquals(target, VideoBitratePolicy.supportedTarget(target, 64_000, 100_000_000));
        }
    }

    @Test(expected = IllegalArgumentException.class) public void invalidEncoderRangeIsRejected() {
        VideoBitratePolicy.supportedTarget(100_000_000, 80_000_000, 1_000_000);
    }
}
