package com.openrecorder.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RecordingOptionsTest {
    @Test
    public void highQualityChoicesSurvivePreferenceNormalization() {
        assertEquals(80_000_000, RecordingOptions.normalizeVideoBitrate(80_000_000));
        assertEquals(100_000_000, RecordingOptions.normalizeVideoBitrate(100_000_000));
        assertEquals(RecordingOptions.VIDEO_BITRATE_AUTO,
                RecordingOptions.normalizeVideoBitrate(101_000_000));
    }

    @Test
    public void factoryProfileIsTheDefault() {
        assertEquals(
                RecordingOptions.SAMPLE_RATE_44_1_KHZ,
                RecordingOptions.DEFAULT_SAMPLE_RATE);
        assertEquals(
                RecordingOptions.VIDEO_RESOLUTION_NATIVE,
                RecordingOptions.DEFAULT_VIDEO_RESOLUTION);
        assertEquals(
                RecordingOptions.VIDEO_FRAME_RATE_AUTO,
                RecordingOptions.DEFAULT_VIDEO_FRAME_RATE);
        assertEquals(
                RecordingOptions.VIDEO_CODEC_H264,
                RecordingOptions.DEFAULT_VIDEO_CODEC);
        assertEquals(
                RecordingOptions.VIDEO_BITRATE_AUTO,
                RecordingOptions.DEFAULT_VIDEO_BITRATE);
    }
}
