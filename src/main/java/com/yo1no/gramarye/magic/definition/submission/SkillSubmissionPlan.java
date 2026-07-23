package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import java.util.Objects;

/**
 * Transient preparation result that may be offered to the future P3-D commit boundary.
 *
 * <p>The plan is not a committed, allocated, persisted, quota-admitted, or runtime-visible
 * revision. Its owner and precondition describe prepare-time state and are not credentials.
 */
public final class SkillSubmissionPlan {
    private final SkillOwnerId owner;
    private final SkillCommitPrecondition precondition;
    private final SkillDocument proposedDocument;
    private final ValidatedSkillDefinition validatedDefinition;

    private SkillSubmissionPlan(
            SkillOwnerId owner,
            SkillCommitPrecondition precondition,
            SkillDocument proposedDocument,
            ValidatedSkillDefinition validatedDefinition) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.precondition = Objects.requireNonNull(precondition, "precondition");
        this.proposedDocument = Objects.requireNonNull(proposedDocument, "proposedDocument");
        this.validatedDefinition = Objects.requireNonNull(
                validatedDefinition, "validatedDefinition");
    }

    static SkillSubmissionPlan from(SubmissionPreparationCheck.Prepared prepared) {
        Objects.requireNonNull(prepared, "prepared");
        var authority = Objects.requireNonNull(prepared.authority(), "authority");
        var authorization = Objects.requireNonNull(
                authority.authorization(), "authorization");
        var state = Objects.requireNonNull(authorization.state(), "state");
        var owner = Objects.requireNonNull(authorization.owner(), "owner");
        var precondition = SkillCommitPreconditionFactory.from(state);
        var document = Objects.requireNonNull(
                prepared.proposedDocument(), "proposedDocument");
        var definition = Objects.requireNonNull(
                prepared.validatedDefinition(), "validatedDefinition");

        requireInvariants(state, precondition, document, definition);
        return new SkillSubmissionPlan(owner, precondition, document, definition);
    }

    public SkillOwnerId owner() {
        return owner;
    }

    public SkillCommitPrecondition precondition() {
        return precondition;
    }

    public SkillDocument proposedDocument() {
        return proposedDocument;
    }

    public ValidatedSkillDefinition validatedDefinition() {
        return validatedDefinition;
    }

    private static void requireInvariants(
            AuthorizedSkillState state,
            SkillCommitPrecondition precondition,
            SkillDocument document,
            ValidatedSkillDefinition definition) {
        if (!precondition.skillId().equals(document.skillId())) {
            throw new IllegalArgumentException(
                    "commit precondition and proposed document SkillId must match");
        }
        var documentReference = new SkillReference(document.skillId(), document.revision());
        if (!documentReference.equals(definition.reference())) {
            throw new IllegalArgumentException(
                    "validated definition reference must match the proposed document");
        }
        if (document.schemaVersion() != SkillDocument.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "proposed document must use the current skill schema");
        }
        if (document.nodes().isEmpty() || definition.nodes().isEmpty()) {
            throw new IllegalArgumentException(
                    "submission plan requires non-empty document and definition nodes");
        }
        if (document.nodes().size() != definition.nodes().size()) {
            throw new IllegalArgumentException(
                    "proposed document and validated definition node counts must match");
        }
        for (var index = 0; index < definition.nodes().size(); index++) {
            if (definition.nodes().get(index).nodeIndex() != index) {
                throw new IllegalArgumentException(
                        "validated nodeIndex must equal its list position");
            }
        }

        var proposedRevision = switch (SubmissionRevisionProposer.propose(state)) {
            case SubmissionRevisionProposal.Proposed proposed -> proposed.revision();
            case SubmissionRevisionProposal.Exhausted ignored ->
                    // Defensive unreachable branch: C3 Prepared rejects Existing(MAX).
                    throw new IllegalStateException(
                            "prepared submission cannot have exhausted revision space");
        };
        if (!proposedRevision.equals(document.revision())) {
            throw new IllegalArgumentException(
                    "proposed document revision must equal the authority-state proposal");
        }

        switch (precondition) {
            case SkillCommitPrecondition.ExpectedAbsent ignored -> {
                if (document.revision().value() != 0) {
                    throw new IllegalArgumentException(
                            "ExpectedAbsent requires proposed revision zero");
                }
            }
            case SkillCommitPrecondition.ExpectedLatest expected -> {
                if (!(state instanceof AuthorizedSkillState.Existing existing)
                        || expected.latest() != existing.latestStoredRevision()) {
                    throw new IllegalArgumentException(
                            "ExpectedLatest must retain the authority latest reference");
                }
            }
        }
    }
}
