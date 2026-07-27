package com.yo1no.gramarye.magic.definition.player;

import java.util.Objects;

/** Bounded outer-migration failure. */
record PlayerSkillAttachmentMigrationFailure(Code code, int schemaVersion, String exceptionClass) {
    PlayerSkillAttachmentMigrationFailure {
        Objects.requireNonNull(code, "code");
        exceptionClass = Objects.requireNonNull(exceptionClass, "exceptionClass");
        if (exceptionClass.length() > 256) {
            exceptionClass = exceptionClass.substring(0, 256);
        }
    }

    enum Code {
        ENVELOPE_MALFORMED,
        SCHEMA_UNSUPPORTED,
        MISSING_MIGRATION_EDGE,
        PARTIAL_MIGRATION,
        STEP_FAILED,
        OPAQUE_TOKEN_INVARIANT_VIOLATION
    }
}
