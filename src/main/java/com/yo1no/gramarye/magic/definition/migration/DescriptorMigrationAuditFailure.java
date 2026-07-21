package com.yo1no.gramarye.magic.definition.migration;

import java.util.Objects;

/** Bounded machine-readable developer wiring failure from the startup migration audit. */
public record DescriptorMigrationAuditFailure(Code code) {
    public DescriptorMigrationAuditFailure {
        Objects.requireNonNull(code, "code");
    }

    public enum Code {
        TRIGGER_PAYLOAD_COVERAGE_INVALID,
        ACTION_PAYLOAD_COVERAGE_INVALID,
        SKILL_DOCUMENT_COVERAGE_INVALID,
        DESCRIPTOR_CONTRACT_EXCEPTION
    }
}
