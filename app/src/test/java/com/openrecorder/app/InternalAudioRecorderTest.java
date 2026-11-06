package com.openrecorder.app;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class InternalAudioRecorderTest {
    @Test
    public void mixedAudioKeepsBothSourcesAndLeavesHeadroomForCoincidentPeaks() {
        short[] playback = {10000, 0, Short.MAX_VALUE, Short.MIN_VALUE, 10000};
        short[] microphone = {0, 10000, Short.MAX_VALUE, Short.MIN_VALUE};

        InternalAudioRecorder.mix(playback, microphone, microphone.length);

        assertArrayEquals(new short[] {4000, 5500, 31128, -31129, 10000}, playback);
    }
}
