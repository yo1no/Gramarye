package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** Package-local P3-C3 result; it is neither a commit plan nor a committed revision. */
sealed interface SubmissionPreparationCheck
        permits SubmissionPreparationCheck.Prepared,
                SubmissionPreparationCheck.Invalid,
                SubmissionPreparationCheck.RevisionExhausted {
    ValidationResult report();

    /** Transient validated preparation whose revision remains only a proposal. */
    record Prepared(
            SubmissionAuthorityCheck.Passed authority,
            SkillDocument proposedDocument,
            ValidatedSkillDefinition validatedDefinition,
            ValidationResult report) implements SubmissionPreparationCheck {
        public Prepared {
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(proposedDocument, "proposedDocument");
            Objects.requireNonNull(validatedDefinition, "validatedDefinition");
            Objects.requireNonNull(report, "report");
            if (report.hasErrors()) {
                throw new IllegalArgumentException("prepared report cannot contain an error");
            }

            var draft = authority.precheck().input().draft();
            if (!draft.skillId().equals(proposedDocument.skillId())) {
                throw new IllegalArgumentException(
                        "prepared document must retain the authority Draft SkillId");
            }
            var documentReference = referenceOf(proposedDocument);
            if (!documentReference.equals(validatedDefinition.reference())) {
                throw new IllegalArgumentException(
                        "validated definition reference must match the proposed document");
            }
            if (proposedDocument.schemaVersion() != SkillDocument.CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "prepared document must use the current skill schema");
            }
            if (proposedDocument.nodes().isEmpty()) {
                throw new IllegalArgumentException("prepared document must contain a node");
            }
            if (validatedDefinition.nodes().isEmpty()) {
                throw new IllegalArgumentException(
                        "prepared validated definition must contain a node");
            }
            if (validatedDefinition.nodes().size() != proposedDocument.nodes().size()) {
                throw new IllegalArgumentException(
                        "prepared document and definition node counts must match");
            }
            for (var index = 0; index < validatedDefinition.nodes().size(); index++) {
                if (validatedDefinition.nodes().get(index).nodeIndex() != index) {
                    throw new IllegalArgumentException(
                            "validated nodeIndex must equal its list position");
                }
            }

            var proposal = SubmissionRevisionProposer.propose(
                    authority.authorization().state());
            if (!(proposal instanceof SubmissionRevisionProposal.Proposed proposed)
                    || !proposed.revision().equals(proposedDocument.revision())) {
                throw new IllegalArgumentException(
                        "prepared document revision must equal the current proposal");
            }
        }
    }

    /** Data-invalid preparation with no partial document or validated projection. */
    record Invalid(ValidationResult report) implements SubmissionPreparationCheck {
        public Invalid {
            Objects.requireNonNull(report, "report");
            if (!report.hasErrors()) {
                throw new IllegalArgumentException("invalid preparation requires an error");
            }
        }
    }

    /** Revision-space exhaustion derived entirely from the accepted authority snapshot. */
    record RevisionExhausted(SubmissionAuthorityCheck.Passed authority)
            implements SubmissionPreparationCheck {
        public RevisionExhausted {
            Objects.requireNonNull(authority, "authority");
            if (!(SubmissionRevisionProposer.propose(authority.authorization().state())
                    instanceof SubmissionRevisionProposal.Exhausted)) {
                throw new IllegalArgumentException(
                        "revision exhaustion requires an existing maximum revision");
            }
            if (authority.report().hasErrors()) {
                throw new IllegalArgumentException(
                        "revision-exhausted report cannot contain an error");
            }
        }

        SkillReference latest() {
            return switch (authority.authorization().state()) {
                case AuthorizedSkillState.Existing existing ->
                        existing.latestStoredRevision();
                case AuthorizedSkillState.New ignored -> throw new IllegalStateException(
                        "new skill authority cannot be revision-exhausted");
            };
        }

        @Override
        public ValidationResult report() {
            return authority.report();
        }
    }

    private static SkillReference referenceOf(SkillDocument document) {
        return new SkillReference(document.skillId(), document.revision());
    }
}
