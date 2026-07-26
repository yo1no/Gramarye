package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Bounded public reason why the installed skill Store subsystem is unavailable. */
public record SkillSubsystemUnavailableReason(Code code) {
    public SkillSubsystemUnavailableReason {
        Objects.requireNonNull(code, "code");
    }

    /** Returns whether this reason represents load-time quarantine or runtime unavailability. */
    public State state() {
        return code.state();
    }

    /** The non-sensitive live-state category exposed to controlled callers. */
    public enum State {
        QUARANTINED,
        UNAVAILABLE
    }

    /** Fixed machine-readable reason codes; no raw failure payload crosses this API. */
    public enum Code {
        OUTER_SAVED_DATA_UNREADABLE(State.QUARANTINED),
        SAVED_DATA_FILE_CAPACITY_EXCEEDED(State.QUARANTINED),
        UNSUPPORTED_PRIMARY_FILE_TYPE(State.QUARANTINED),
        PRIMARY_FILE_IDENTITY_UNAVAILABLE(State.QUARANTINED),
        PRIMARY_FILE_RACE_DETECTED(State.QUARANTINED),
        MALFORMED_GZIP(State.QUARANTINED),
        MULTIPLE_GZIP_MEMBERS(State.QUARANTINED),
        COMPRESSED_TRAILING_DATA(State.QUARANTINED),
        DECOMPRESSED_CARRIER_FAILURE(State.QUARANTINED),
        RUNTIME_CARRIER_INVARIANT(State.UNAVAILABLE);

        private final State state;

        Code(State state) {
            this.state = state;
        }

        public State state() {
            return state;
        }
    }
}
