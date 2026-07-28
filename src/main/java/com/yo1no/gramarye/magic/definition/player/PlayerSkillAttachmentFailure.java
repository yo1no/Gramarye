package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.api.id.SkillId;
import java.util.Objects;
import java.util.Optional;

/** Bounded, raw-free machine diagnostics for one player Attachment admission attempt. */
record PlayerSkillAttachmentFailure(
        Code code,
        Stage stage,
        long observed,
        long maximum,
        Optional<SkillId> skillId,
        int index,
        String exceptionClass) {
    private static final int MAX_EXCEPTION_CLASS_LENGTH = 256;

    PlayerSkillAttachmentFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(stage, "stage");
        skillId = Objects.requireNonNull(skillId, "skillId");
        exceptionClass = boundedClassName(exceptionClass);
    }

    static PlayerSkillAttachmentFailure simple(Code code, Stage stage) {
        return new PlayerSkillAttachmentFailure(code, stage, -1, -1, Optional.empty(), -1, "");
    }

    static PlayerSkillAttachmentFailure capacity(Code code, Stage stage, long observed, long maximum) {
        return new PlayerSkillAttachmentFailure(
                code, stage, observed, maximum, Optional.empty(), -1, "");
    }

    static PlayerSkillAttachmentFailure at(Code code, Stage stage, int index) {
        return new PlayerSkillAttachmentFailure(code, stage, -1, -1, Optional.empty(), index, "");
    }

    static PlayerSkillAttachmentFailure route(Code code, Stage stage, SkillId skillId) {
        return new PlayerSkillAttachmentFailure(
                code, stage, -1, -1, Optional.of(Objects.requireNonNull(skillId, "skillId")), -1, "");
    }

    static PlayerSkillAttachmentFailure exception(Code code, Stage stage, RuntimeException exception) {
        Objects.requireNonNull(exception, "exception");
        return new PlayerSkillAttachmentFailure(
                code, stage, -1, -1, Optional.empty(), -1, exception.getClass().getName());
    }

    private static String boundedClassName(String value) {
        Objects.requireNonNull(value, "exceptionClass");
        return value.length() <= MAX_EXCEPTION_CLASS_LENGTH
                ? value
                : value.substring(0, MAX_EXCEPTION_CLASS_LENGTH);
    }

    enum Code {
        ATTACHMENT_ENCODED_CAPACITY_EXCEEDED,
        ATTACHMENT_ENVELOPE_MALFORMED,
        ATTACHMENT_SCHEMA_UNSUPPORTED,
        ATTACHMENT_MIGRATION_FAILED,
        DRAFT_ENTRY_CAPACITY_EXCEEDED,
        DRAFT_PHYSICAL_MIGRATION_FAILED,
        DRAFT_LOGICAL_MIGRATION_FAILED,
        DRAFT_DECODE_FAILED,
        DRAFT_ROUTE_MISMATCH,
        DRAFT_CARRIER_MISMATCH,
        DUPLICATE_DRAFT_ROUTE,
        DUPLICATE_LATEST_ROUTE,
        DUPLICATE_EQUIPPED_SLOT,
        LATEST_POINTER_ROUTE_MISMATCH,
        GENERATION_INVALID,
        EDITOR_STATE_INVALID,
        OPAQUE_DRAFT_TOKEN_INVARIANT_VIOLATION,
        OPAQUE_DRAFT_RAW_INVARIANT_VIOLATION,
        INTERNAL_CODEC_EXCEPTION
    }

    enum Stage {
        TOTAL_COUNT,
        MARKER,
        OUTER_MIGRATION,
        OUTER_SCHEMA,
        DRAFT_COUNT,
        LATEST_COUNT,
        EQUIPPED_COUNT,
        DRAFT_CAPTURE,
        DRAFT_LOAD,
        DRAFT_VALIDATION,
        LATEST_VALIDATION,
        EQUIPPED_VALIDATION,
        EDITOR_VALIDATION,
        CARRIER_BUILD
    }
}
