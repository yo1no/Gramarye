package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Typed result of one controlled operation against the live skill Store subsystem. */
public sealed interface SkillSubsystemResult<T>
        permits SkillSubsystemResult.Available, SkillSubsystemResult.Unavailable {
    /** The subsystem was available and the operation produced {@code value}. */
    record Available<T>(T value) implements SkillSubsystemResult<T> {
        public Available {
            Objects.requireNonNull(value, "value");
        }
    }

    /** The subsystem was installed but unavailable for controlled Store operations. */
    record Unavailable<T>(SkillSubsystemUnavailableReason reason)
            implements SkillSubsystemResult<T> {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
