package com.alradno.banking.common.events;

import java.util.Set;

public final class EventCompatibility {
    public static final int CURRENT_ENVELOPE_VERSION = 1;
    private static final Set<Integer> SUPPORTED_ENVELOPE_VERSIONS = Set.of(CURRENT_ENVELOPE_VERSION);

    private EventCompatibility() {
    }

    public static void requireSupported(EventEnvelope envelope) {
        if (!SUPPORTED_ENVELOPE_VERSIONS.contains(envelope.eventVersion())) {
            throw new IllegalArgumentException("unsupported event envelope version " + envelope.eventVersion());
        }
    }
}
