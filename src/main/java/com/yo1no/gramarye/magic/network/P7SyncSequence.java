package com.yo1no.gramarye.magic.network;

/** One outbound family's next local submission, never delivery tracking. */
record P7SyncSequence(long value, boolean exhausted) {
    P7SyncSequence {
        if (value < 1 || exhausted && value != Long.MAX_VALUE) {
            throw new P7SemanticInvariantException("invalid outbound sequence");
        }
    }

    static P7SyncSequence initial() {
        return new P7SyncSequence(1, false);
    }

    P7SyncSequence submitted() {
        if (exhausted) {
            throw new P7SemanticInvariantException("outbound sequence exhausted");
        }
        return value == Long.MAX_VALUE
                ? new P7SyncSequence(value, true)
                : new P7SyncSequence(Math.addExact(value, 1), false);
    }
}
