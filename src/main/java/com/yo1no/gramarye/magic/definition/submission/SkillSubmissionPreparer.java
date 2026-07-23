package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalysis;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.List;
import java.util.Objects;

/** Prepares a transient validated proposal without allocating a revision or mutating Store state. */
final class SkillSubmissionPreparer {
    private static final SkillDocumentReadReport EMPTY_DOCUMENT_READ_REPORT =
            new SkillDocumentReadReport(List.of(), false);

    private final Stages stages;

    SkillSubmissionPreparer(
            DraftFormalizer formalizer,
            SkillCandidateResolver resolver,
            SkillValidationAnalyzer analyzer,
            SkillDefinitionProjector projector) {
        this(productionStages(formalizer, resolver, analyzer, projector));
    }

    SkillSubmissionPreparer(Stages stages) {
        this.stages = Objects.requireNonNull(stages, "stages");
    }

    SubmissionPreparationCheck prepare(
            SubmissionAuthorityCheck.Passed authority,
            ValidationContext validationContext) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(validationContext, "validationContext");

        return switch (SubmissionRevisionProposer.propose(
                authority.authorization().state())) {
            case SubmissionRevisionProposal.Exhausted ignored ->
                    new SubmissionPreparationCheck.RevisionExhausted(authority);
            case SubmissionRevisionProposal.Proposed proposed ->
                    prepareProposed(authority, proposed.revision(), validationContext);
        };
    }

    private SubmissionPreparationCheck prepareProposed(
            SubmissionAuthorityCheck.Passed authority,
            com.yo1no.gramarye.magic.api.id.SkillRevision proposedRevision,
            ValidationContext validationContext) {
        var formalization = Objects.requireNonNull(
                stages.formalize(authority.precheck()), "formalization");
        if (formalization instanceof DraftFormalizationResult.Invalid invalid) {
            return new SubmissionPreparationCheck.Invalid(invalid.report());
        }

        var ready = (DraftFormalizationResult.Ready) formalization;
        if (ready.input() != authority.precheck().input()) {
            throw new IllegalStateException(
                    "formalization must retain the authority submission input identity");
        }
        var draft = authority.precheck().input().draft();
        var proposedDocument = new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                draft.skillId(),
                proposedRevision,
                ready.nodes(),
                draft.appearance());

        var candidate = Objects.requireNonNull(
                stages.resolve(proposedDocument, EMPTY_DOCUMENT_READ_REPORT), "candidate");
        var analysis = Objects.requireNonNull(
                stages.analyze(candidate, validationContext), "analysis");
        var outcome = Objects.requireNonNull(stages.project(analysis), "validationOutcome");
        var merged = SubmissionReportMerger.merge(ready.report(), outcome.report());

        return switch (outcome) {
            case SkillValidationOutcome.Rejected ignored -> {
                if (!merged.hasErrors()) {
                    throw new IllegalStateException(
                            "rejected validation outcome must merge to an invalid report");
                }
                yield new SubmissionPreparationCheck.Invalid(merged);
            }
            case SkillValidationOutcome.Accepted accepted -> {
                if (merged.hasErrors()) {
                    throw new IllegalStateException(
                            "accepted validation outcome must merge to a warning-only report");
                }
                var documentReference = new SkillReference(
                        proposedDocument.skillId(), proposedDocument.revision());
                if (!documentReference.equals(accepted.definition().reference())) {
                    throw new IllegalStateException(
                            "accepted definition reference must match the proposed document");
                }
                yield new SubmissionPreparationCheck.Prepared(
                        authority, proposedDocument, accepted.definition(), merged);
            }
        };
    }

    private static Stages productionStages(
            DraftFormalizer formalizer,
            SkillCandidateResolver resolver,
            SkillValidationAnalyzer analyzer,
            SkillDefinitionProjector projector) {
        Objects.requireNonNull(formalizer, "formalizer");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(analyzer, "analyzer");
        Objects.requireNonNull(projector, "projector");
        return new Stages() {
            @Override
            public DraftFormalizationResult formalize(DraftSubmissionPrecheck.Ready ready) {
                return formalizer.formalize(ready);
            }

            @Override
            public ResolvedSkillCandidate resolve(
                    SkillDocument document,
                    SkillDocumentReadReport report) {
                return resolver.resolve(document, report);
            }

            @Override
            public SkillValidationAnalysis analyze(
                    ResolvedSkillCandidate candidate,
                    ValidationContext context) {
                return analyzer.analyze(candidate, context);
            }

            @Override
            public SkillValidationOutcome project(SkillValidationAnalysis analysis) {
                return projector.project(analysis);
            }
        };
    }

    interface Stages {
        DraftFormalizationResult formalize(DraftSubmissionPrecheck.Ready ready);

        ResolvedSkillCandidate resolve(
                SkillDocument document,
                SkillDocumentReadReport report);

        SkillValidationAnalysis analyze(
                ResolvedSkillCandidate candidate,
                ValidationContext context);

        SkillValidationOutcome project(SkillValidationAnalysis analysis);
    }
}
