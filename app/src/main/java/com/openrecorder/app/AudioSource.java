package com.openrecorder.app;

enum AudioSource {
    NONE,
    INTERNAL,
    MICROPHONE,
    INTERNAL_AND_MICROPHONE,
    INTERNAL_AND_MICROPHONE_MIXED;

    boolean usesInternalAudio() {
        return this == INTERNAL
                || this == INTERNAL_AND_MICROPHONE
                || this == INTERNAL_AND_MICROPHONE_MIXED;
    }

    boolean usesMicrophone() {
        return this == MICROPHONE
                || this == INTERNAL_AND_MICROPHONE
                || this == INTERNAL_AND_MICROPHONE_MIXED;
    }

    static AudioSource fromOrdinal(int ordinal) {
        AudioSource[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
