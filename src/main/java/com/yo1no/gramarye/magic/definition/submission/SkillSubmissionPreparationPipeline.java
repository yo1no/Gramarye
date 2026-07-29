package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.Objects;

/** Package-local exactly-once composition of the approved P3-C1 through P3-C4 stages. */
final class SkillSubmissionPreparationPipeline {
    private final Stages stages;

    SkillSubmissionPreparationPipeline(
            SkillCandidateResolver resolver,
            SkillValidationAnalyzer analyzer,
            SkillDefinitionProjector projector) {
        this(productionStages(resolver, analyzer, projector));
    }

    SkillSubmissionPreparationPipeline(Stages stages) {
        this.stages = Objects.requireNonNull(stages, "stages");
    }

    DraftSubmissionPrecheck precheck(SkillDraft draft) {
        var input = SkillSubmissionInput.direct(Objects.requireNonNull(draft, "draft"));
        return Objects.requireNonNull(stages.precheck(input), "precheck result");
    }

    SubmissionAuthorityCheck checkAuthority(
            DraftSubmissionPrecheck.Ready precheck,
            SkillSubmissionAuthorizationResult authorization) {
        return Objects.requireNonNull(
                stages.checkAuthority(
                        Objects.requireNonNull(precheck, "precheck"),
                        Objects.requireNonNull(authorization, "authorization")),
                "authority result");
    }

    SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid) {
        return Objects.requireNonNull(
                stages.map(Objects.requireNonNull(invalid, "invalid")),
                "mapped invalid outcome");
    }

    SkillSubmissionOutcome map(SubmissionAuthorityCheck.IdentityRejected rejected) {
        return Objects.requireNonNull(
                stages.map(Objects.requireNonNull(rejected, "rejected")),
                "mapped identity outcome");
    }

    SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict) {
        return Objects.requireNonNull(
                stages.map(Objects.requireNonNull(conflict, "conflict")),
                "mapped conflict outcome");
    }

    SkillSubmissionOutcome prepareAndMap(
            SubmissionAuthorityCheck.Passed passed,
            ValidationContext context) {
        var preparation = Objects.requireNonNull(
                stages.prepare(
                        Objects.requireNonNull(passed, "passed"),
                        Objects.requireNonNull(context, "context")),
                "preparation result");
        return Objects.requireNonNull(stages.map(preparation), "mapped preparation outcome");
    }

    private static Stages productionStages(
            SkillCandidateResolver resolver,
            SkillValidationAnalyzer analyzer,
            SkillDefinitionProjector projector) {
        var prechecker = new DraftSubmissionPrechecker();
        var authorityChecker = new SubmissionAuthorityChecker();
        var preparer = new SkillSubmissionPreparer(
                new DraftFormalizer(),
                Objects.requireNonNull(resolver, "resolver"),
                Objects.requireNonNull(analyzer, "analyzer"),
                Objects.requireNonNull(projector, "projector"));
        return new Stages() {
            @Override
            public DraftSubmissionPrecheck precheck(SkillSubmissionInput input) {
                return prechecker.check(input);
            }

            @Override
            public SubmissionAuthorityCheck checkAuthority(
                    DraftSubmissionPrecheck.Ready ready,
                    SkillSubmissionAuthorizationResult authorization) {
                return authorityChecker.check(ready, authorization);
            }

            @Override
            public SubmissionPreparationCheck prepare(
                    SubmissionAuthorityCheck.Passed passed,
                    ValidationContext context) {
                return preparer.prepare(passed, context);
            }

            @Override
            public SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid) {
                return SkillSubmissionOutcomeMapper.from(invalid);
            }

            @Override
            public SkillSubmissionOutcome map(
                    SubmissionAuthorityCheck.IdentityRejected rejected) {
                return SkillSubmissionOutcomeMapper.from(rejected);
            }

            @Override
            public SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict) {
                return SkillSubmissionOutcomeMapper.from(conflict);
            }

            @Override
            public SkillSubmissionOutcome map(SubmissionPreparationCheck preparation) {
                return SkillSubmissionOutcomeMapper.from(preparation);
            }
        };
    }

    /** Single package-local injection seam used only to count the existing stage invocations. */
    interface Stages {
        DraftSubmissionPrecheck precheck(SkillSubmissionInput input);

        SubmissionAuthorityCheck checkAuthority(
                DraftSubmissionPrecheck.Ready ready,
                SkillSubmissionAuthorizationResult authorization);

        SubmissionPreparationCheck prepare(
                SubmissionAuthorityCheck.Passed passed,
                ValidationContext context);

        SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid);

        SkillSubmissionOutcome map(SubmissionAuthorityCheck.IdentityRejected rejected);

        SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict);

        SkillSubmissionOutcome map(SubmissionPreparationCheck preparation);
    }
}
