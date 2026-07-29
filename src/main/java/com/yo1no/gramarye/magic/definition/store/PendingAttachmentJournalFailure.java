package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;

/** Bounded machine-readable failure for journal persistence and operation. */
record PendingAttachmentJournalFailure(
        Code code,
        Stage stage,
        Field field,
        long observedAtLeast,
        long maximum,
        int entryIndex,
        Optional<SkillId> skillId,
        Optional<SkillReference> reference,
        Optional<String> exceptionClassName) {
    PendingAttachmentJournalFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(field, "field");
        skillId = Objects.requireNonNull(skillId, "skillId");
        reference = Objects.requireNonNull(reference, "reference");
        exceptionClassName = Objects.requireNonNull(exceptionClassName, "exceptionClassName")
                .map(PendingAttachmentJournalFailure::boundedClassName);
        if (observedAtLeast < -1 || maximum < -1 || entryIndex < -1) {
            throw new IllegalArgumentException("journal failure metadata is out of range");
        }
    }

    static PendingAttachmentJournalFailure simple(Code code) {
        return new PendingAttachmentJournalFailure(
                code, Stage.NONE, Field.NONE, -1, -1, -1,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    static PendingAttachmentJournalFailure at(Code code, Stage stage, Field field) {
        return new PendingAttachmentJournalFailure(
                code, stage, field, -1, -1, -1,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    static PendingAttachmentJournalFailure capacity(Code code, long observedAtLeast, long maximum) {
        return new PendingAttachmentJournalFailure(
                code, Stage.FRAMING, Field.NONE, observedAtLeast, maximum, -1,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    static PendingAttachmentJournalFailure duplicateAt(long byteOffset) {
        return new PendingAttachmentJournalFailure(
                Code.DUPLICATE_PHYSICAL_FIELD,
                Stage.PHYSICAL,
                Field.ROOT,
                byteOffset,
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES,
                -1,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    static PendingAttachmentJournalFailure entry(Code code, int entryIndex) {
        return new PendingAttachmentJournalFailure(
                code, Stage.DOMAIN, Field.ENTRY, -1, -1, entryIndex,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    static PendingAttachmentJournalFailure entry(
            Code code, int entryIndex, SkillId skillId, SkillReference reference) {
        return new PendingAttachmentJournalFailure(
                code, Stage.DOMAIN, Field.ENTRY, -1, -1, entryIndex,
                Optional.ofNullable(skillId), Optional.ofNullable(reference), Optional.empty());
    }

    static PendingAttachmentJournalFailure exception(Code code, int edge, RuntimeException exception) {
        Objects.requireNonNull(exception, "exception");
        return new PendingAttachmentJournalFailure(
                code, Stage.MIGRATION, Field.NONE, -1, -1, edge,
                Optional.empty(), Optional.empty(), Optional.of(exception.getClass().getName()));
    }

    private static String boundedClassName(String value) {
        return value.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? value
                : value.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
    }

    enum Code {
        ENCODED_CAPACITY_EXCEEDED,
        MALFORMED_ROOT,
        DUPLICATE_PHYSICAL_FIELD,
        MISSING_FIELD,
        UNKNOWN_FIELD,
        WRONG_TAG_TYPE,
        TRAILING_DATA,
        UNSUPPORTED_SCHEMA,
        MISSING_MIGRATION_EDGE,
        MIGRATION_EXCEPTION,
        MIGRATION_PARTIAL,
        ENTRY_COUNT_EXCEEDED,
        GENERATION_INVALID,
        GENERATION_EXHAUSTED,
        POINTER_ROUTE_MISMATCH,
        DUPLICATE_STABLE_KEY,
        BROKEN_GENERATION_CHAIN,
        BROKEN_POINTER_CHAIN,
        TARGET_MISSING,
        TARGET_OWNER_MISMATCH,
        JOURNAL_NOT_BOOTSTRAPPED,
        JOURNAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        AUTHORITY_UNAVAILABLE,
        BOOTSTRAP_ALREADY_INSTALLED,
        PREPARED_BASE_MISMATCH,
        PREPARED_ALREADY_CONSUMED,
        PREFIX_TARGET_MISMATCH,
        CARRIER_INVARIANT_FAILURE,
        POST_COMMIT_INVARIANT_FAILURE,
        TRANSITION_SERVER_MISMATCH
    }

    enum Stage {
        NONE,
        FRAMING,
        SCHEMA,
        MIGRATION,
        PHYSICAL,
        DOMAIN,
        TARGET_AUDIT,
        OPERATION
    }

    enum Field {
        NONE,
        ROOT,
        VERSION,
        ENTRIES,
        ENTRY,
        OWNER,
        SKILL_ID,
        EXPECTED_GENERATION,
        TARGET_GENERATION,
        EXPECTED_POINTER,
        TARGET_POINTER,
        REVISION
    }
}
