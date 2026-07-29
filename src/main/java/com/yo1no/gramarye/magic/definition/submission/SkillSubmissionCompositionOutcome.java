package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCapacityScope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;
import java.util.Optional;

/** Final bounded vocabulary for the future authenticated submission composition. */
public sealed interface SkillSubmissionCompositionOutcome
        permits SkillSubmissionCompositionOutcome.DraftUnavailable,
                SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation,
                SkillSubmissionCompositionOutcome.PreparationRejected,
                SkillSubmissionCompositionOutcome.PersistenceCapacityRejected,
                SkillSubmissionCompositionOutcome.CommitConflict,
                SkillSubmissionCompositionOutcome.QuotaRejected,
                SkillSubmissionCompositionOutcome.CapacityRejected,
                SkillSubmissionCompositionOutcome.IdentityRejected,
                SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation,
                SkillSubmissionCompositionOutcome.Committed,
                SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery {

    record DraftUnavailable(SkillId skillId) implements SkillSubmissionCompositionOutcome {
        public DraftUnavailable {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    record SubsystemUnavailableBeforePreparation(
            SkillId skillId,
            BeforePreparationFailure failure) implements SkillSubmissionCompositionOutcome {
        public SubsystemUnavailableBeforePreparation {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(failure, "failure");
        }
    }

    record PreparationRejected(SkillSubmissionOutcome outcome)
            implements SkillSubmissionCompositionOutcome {
        public PreparationRejected {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome instanceof SkillSubmissionOutcome.Prepared) {
                throw new IllegalArgumentException(
                        "PreparationRejected cannot wrap a Prepared outcome");
            }
        }

        public ValidationResult report() {
            return outcome.report();
        }

        @Override
        public String toString() {
            return "PreparationRejected[outcome="
                    + outcome.getClass().getSimpleName() + ']';
        }
    }

    record PersistenceCapacityRejected(
            PersistenceCapacityScope scope,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public PersistenceCapacityRejected {
            Objects.requireNonNull(scope, "scope");
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "PersistenceCapacityRejected[scope=" + scope + ']';
        }
    }

    record CommitConflict(
            CommitConflictDetail detail,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public CommitConflict {
            Objects.requireNonNull(detail, "detail");
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "CommitConflict[detail=" + detail.getClass().getSimpleName() + ']';
        }
    }

    record QuotaRejected(
            SkillId skillId,
            int current,
            int maximum,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public QuotaRejected {
            Objects.requireNonNull(skillId, "skillId");
            requireCurrentMaximum(current, maximum);
            if (maximum > MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER) {
                throw new IllegalArgumentException(
                        "quota maximum exceeds the Store owner hard ceiling");
            }
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "QuotaRejected[skillId=" + skillId
                    + ", current=" + current + ", maximum=" + maximum + ']';
        }
    }

    record CapacityRejected(
            SkillStoreCapacityScope scope,
            int current,
            int maximum,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public CapacityRejected {
            Objects.requireNonNull(scope, "scope");
            requireCurrentMaximum(current, maximum);
            if (maximum != canonicalMaximum(scope)) {
                throw new IllegalArgumentException(
                        "capacity maximum must match the canonical Store scope ceiling");
            }
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "CapacityRejected[scope=" + scope
                    + ", current=" + current + ", maximum=" + maximum + ']';
        }
    }

    record IdentityRejected(
            SkillId skillId,
            SkillIdentityRejectionCode reason,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public IdentityRejected {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(reason, "reason");
            if (reason != SkillIdentityRejectionCode.NOT_AUTHORIZED) {
                throw new IllegalArgumentException(
                        "Store owner rejection must remain opaque NOT_AUTHORIZED");
            }
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "IdentityRejected[skillId=" + skillId + ", reason=" + reason + ']';
        }
    }

    record SubsystemUnavailableAfterPreparation(
            SkillReference target,
            AfterPreparationPhase phase,
            AfterPreparationFailure failure,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public SubsystemUnavailableAfterPreparation {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(failure, "failure");
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "SubsystemUnavailableAfterPreparation[target=" + target
                    + ", phase=" + phase + ", failure=" + failure + ']';
        }
    }

    record Committed(
            SkillReference reference,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public Committed {
            Objects.requireNonNull(reference, "reference");
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "Committed[reference=" + reference + ']';
        }
    }

    record CommittedPendingAttachmentRecovery(
            SkillReference reference,
            AttachmentPublicationFailure failure,
            ValidationResult report) implements SkillSubmissionCompositionOutcome {
        public CommittedPendingAttachmentRecovery {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(failure, "failure");
            requireWarningOnly(report);
        }

        @Override
        public String toString() {
            return "CommittedPendingAttachmentRecovery[reference=" + reference
                    + ", failure=" + failure.code() + ']';
        }
    }

    enum PersistenceCapacityScope {
        ATTACHMENT_ENCODED,
        DOCUMENT_BLOB,
        REVISION_BLOB,
        HISTORY_BLOB,
        STORE_BLOB,
        JOURNAL_ENTRY_COUNT,
        JOURNAL_ENCODED_BYTES
    }

    enum AfterPreparationPhase {
        PRE_COMMIT,
        POST_COMMIT_STORE_COMMITTED
    }

    enum BeforePreparationFailureCode {
        ATTACHMENT_PRESERVED_RAW_QUARANTINE,
        ATTACHMENT_OVERSIZE_QUARANTINE,
        JOURNAL_NOT_BOOTSTRAPPED,
        JOURNAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        AUTHORITY_UNAVAILABLE,
        POLICY_SNAPSHOT_NULL,
        POLICY_PROVIDER_RUNTIME_EXCEPTION
    }

    record BeforePreparationFailure(
            BeforePreparationFailureCode code,
            Optional<String> exceptionClassName) {
        public BeforePreparationFailure {
            Objects.requireNonNull(code, "code");
            exceptionClassName = requireExceptionMetadata(
                    exceptionClassName,
                    code == BeforePreparationFailureCode.POLICY_PROVIDER_RUNTIME_EXCEPTION);
        }

        public static BeforePreparationFailure of(BeforePreparationFailureCode code) {
            return new BeforePreparationFailure(code, Optional.empty());
        }

        public static BeforePreparationFailure policyProviderException(
                RuntimeException exception) {
            Objects.requireNonNull(exception, "exception");
            return new BeforePreparationFailure(
                    BeforePreparationFailureCode.POLICY_PROVIDER_RUNTIME_EXCEPTION,
                    Optional.of(boundedClassName(exception.getClass().getName())));
        }

        @Override
        public String toString() {
            return "BeforePreparationFailure[code=" + code
                    + ", hasExceptionClass=" + exceptionClassName.isPresent() + ']';
        }
    }

    enum AfterPreparationFailure {
        ATTACHMENT_PRESERVED_RAW_QUARANTINE,
        ATTACHMENT_OVERSIZE_QUARANTINE,
        GENERATION_EXHAUSTED,
        ATTACHMENT_STATE_CHANGED,
        JOURNAL_NOT_BOOTSTRAPPED,
        JOURNAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        AUTHORITY_UNAVAILABLE,
        STORE_JOURNAL_STATE_CHANGED,
        STORE_CARRIER_INVARIANT_FAILURE,
        JOURNAL_CHAIN_INVARIANT_FAILURE,
        SAVED_DATA_CARRIER_INVARIANT_FAILURE,
        PLAN_TRANSITION_PAIRING_FAILURE,
        AUTHORITY_PRECONDITION_MISMATCH,
        NORMAL_SUBMISSION_NO_OP,
        STORE_JOURNAL_PUBLICATION_INVARIANT
    }

    sealed interface CommitConflictDetail
            permits CommitConflictDetail.ExpectedAbsentButPresent,
                    CommitConflictDetail.ExpectedLatestButAbsent,
                    CommitConflictDetail.LatestMismatch,
                    CommitConflictDetail.AuthorityChanged {
        SkillId skillId();

        record ExpectedAbsentButPresent(SkillId skillId) implements CommitConflictDetail {
            public ExpectedAbsentButPresent {
                Objects.requireNonNull(skillId, "skillId");
            }
        }

        record ExpectedLatestButAbsent(SkillReference expected)
                implements CommitConflictDetail {
            public ExpectedLatestButAbsent {
                Objects.requireNonNull(expected, "expected");
            }

            @Override
            public SkillId skillId() {
                return expected.skillId();
            }
        }

        record LatestMismatch(
                SkillReference expected,
                SkillReference observed) implements CommitConflictDetail {
            public LatestMismatch {
                Objects.requireNonNull(expected, "expected");
                Objects.requireNonNull(observed, "observed");
                if (!expected.skillId().equals(observed.skillId())) {
                    throw new IllegalArgumentException(
                            "expected and observed conflicts must use the same SkillId");
                }
                if (expected.equals(observed)) {
                    throw new IllegalArgumentException(
                            "expected and observed conflict references must differ");
                }
            }

            @Override
            public SkillId skillId() {
                return expected.skillId();
            }
        }

        record AuthorityChanged(SkillId skillId) implements CommitConflictDetail {
            public AuthorityChanged {
                Objects.requireNonNull(skillId, "skillId");
            }
        }
    }

    record AttachmentPublicationFailure(
            AttachmentPublicationFailureCode code,
            Optional<String> exceptionClassName) {
        public AttachmentPublicationFailure {
            Objects.requireNonNull(code, "code");
            exceptionClassName = requireExceptionMetadata(
                    exceptionClassName,
                    code == AttachmentPublicationFailureCode.RUNTIME_EXCEPTION);
        }

        public static AttachmentPublicationFailure of(
                AttachmentPublicationFailureCode code) {
            return new AttachmentPublicationFailure(code, Optional.empty());
        }

        public static AttachmentPublicationFailure runtime(RuntimeException exception) {
            Objects.requireNonNull(exception, "exception");
            return new AttachmentPublicationFailure(
                    AttachmentPublicationFailureCode.RUNTIME_EXCEPTION,
                    Optional.of(boundedClassName(exception.getClass().getName())));
        }

        @Override
        public String toString() {
            return "AttachmentPublicationFailure[code=" + code
                    + ", hasExceptionClass=" + exceptionClassName.isPresent() + ']';
        }
    }

    enum AttachmentPublicationFailureCode {
        STATE_CHANGED,
        ATTACHMENT_QUARANTINED,
        UNEXPECTED_NO_OP,
        RUNTIME_EXCEPTION
    }

    private static ValidationResult requireWarningOnly(ValidationResult report) {
        Objects.requireNonNull(report, "report");
        if (report.hasErrors()) {
            throw new IllegalArgumentException(
                    "post-preparation outcome requires a warning-only report");
        }
        return report;
    }

    private static void requireCurrentMaximum(int current, int maximum) {
        if (current < 0 || maximum < 0 || current < maximum) {
            throw new IllegalArgumentException(
                    "rejection metadata requires non-negative current >= maximum");
        }
    }

    private static int canonicalMaximum(SkillStoreCapacityScope scope) {
        return switch (scope) {
            case OWNER_SKILL_HISTORIES ->
                    MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER;
            case GLOBAL_SKILL_HISTORIES ->
                    MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL;
            case SKILL_RETAINED_REVISIONS ->
                    MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL;
            case GLOBAL_RETAINED_REVISIONS ->
                    MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL;
        };
    }

    private static Optional<String> requireExceptionMetadata(
            Optional<String> exceptionClassName,
            boolean required) {
        exceptionClassName = Objects.requireNonNull(
                exceptionClassName, "exceptionClassName");
        if (exceptionClassName.isPresent() != required) {
            throw new IllegalArgumentException(
                    "exception class presence must match the runtime-exception code");
        }
        exceptionClassName.ifPresent(name -> {
            if (name.isBlank() || name.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException(
                        "exception class name must be non-blank and bounded");
            }
        });
        return exceptionClassName;
    }

    private static String boundedClassName(String className) {
        Objects.requireNonNull(className, "className");
        return className.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? className
                : className.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
    }
}
