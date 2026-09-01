package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.lookup.RegistryActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.RegistryTriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.List;
import java.util.Objects;

/** Deterministic exact-document adapter to the existing immutable P3 runtime projection. */
final class P5RuntimeProjector {
    private static final SkillDocumentReadReport EMPTY_READ_REPORT =
            new SkillDocumentReadReport(List.of(), false);

    private final SkillCandidateResolver candidateResolver;
    private final SkillValidationAnalyzer validationAnalyzer;
    private final SkillDefinitionProjector definitionProjector;

    P5RuntimeProjector() {
        this(
                new SkillCandidateResolver(
                        new RegistryTriggerTypeLookup(),
                        new RegistryActionTypeLookup()),
                new SkillValidationAnalyzer(
                        new NodeProjectionResolver(),
                        ProfileAvailabilityView.unknown()),
                new SkillDefinitionProjector());
    }

    P5RuntimeProjector(
            SkillCandidateResolver candidateResolver,
            SkillValidationAnalyzer validationAnalyzer,
            SkillDefinitionProjector definitionProjector) {
        this.candidateResolver = Objects.requireNonNull(candidateResolver, "candidateResolver");
        this.validationAnalyzer = Objects.requireNonNull(validationAnalyzer, "validationAnalyzer");
        this.definitionProjector = Objects.requireNonNull(definitionProjector, "definitionProjector");
    }

    Projection project(
            SkillReference expectedReference,
            SkillDocument document,
            ValidationContext context) {
        Objects.requireNonNull(expectedReference, "expectedReference");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(context, "context");

        var documentReference = new SkillReference(document.skillId(), document.revision());
        if (!expectedReference.equals(documentReference)) {
            return Projection.Unavailable.INSTANCE;
        }

        var candidate = candidateResolver.resolve(document, EMPTY_READ_REPORT);
        if (!expectedReference.equals(candidate.skill())) {
            return Projection.Unavailable.INSTANCE;
        }
        var analysis = validationAnalyzer.analyze(candidate, context);
        var outcome = definitionProjector.project(analysis);
        return switch (outcome) {
            case SkillValidationOutcome.Accepted accepted ->
                    expectedReference.equals(accepted.definition().reference())
                            ? new Projection.Available(accepted.definition())
                            : Projection.Unavailable.INSTANCE;
            case SkillValidationOutcome.Rejected ignored -> Projection.Unavailable.INSTANCE;
        };
    }

    sealed interface Projection permits Projection.Available, Projection.Unavailable {
        record Available(ValidatedSkillDefinition definition) implements Projection {
            public Available {
                Objects.requireNonNull(definition, "definition");
            }
        }

        enum Unavailable implements Projection {
            INSTANCE
        }
    }
}
