package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.lookup.RegistryActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.RegistryTriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Non-authoritative, non-persistent adapter to the existing production definition pipeline. */
final class P10TemplateValidation {
    // Used only to satisfy the existing pure document pipeline; never persisted or exposed.
    private static final SkillId ADAPTER_ID = new SkillId(new UUID(0L, 0L));
    private static final SkillDocumentReadReport NO_READ_FACTS =
            new SkillDocumentReadReport(List.of(), false);
    private final SkillSubmissionPolicyProvider policies;
    private final P8ServerPresentationService presentation;
    private final SkillCandidateResolver resolver;
    private final ProfileAvailabilityView profiles;

    P10TemplateValidation(
            SkillSubmissionPolicyProvider policies, P8ServerPresentationService presentation) {
        this(policies, presentation,
                new SkillCandidateResolver(new RegistryTriggerTypeLookup(), new RegistryActionTypeLookup()),
                presentation.profileAvailabilityView());
    }

    P10TemplateValidation(
            SkillSubmissionPolicyProvider policies,
            P8ServerPresentationService presentation,
            SkillCandidateResolver resolver,
            ProfileAvailabilityView profiles) {
        this.policies = Objects.requireNonNull(policies, "policies");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    Result validate(MinecraftServer server, P10TemplateBody body) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Template validation requires the server thread");
        }
        if (presentation.captureAppearanceResolutionSnapshot().isEmpty()) {
            return new Result(Classification.CONTEXT_UNAVAILABLE, ValidationResult.valid());
        }
        var policy = policies.snapshot(server);
        if (policy == null) {
            return new Result(Classification.CONTEXT_UNAVAILABLE, ValidationResult.valid());
        }
        return validate(body, policy.validationContext(), ValidationResult.valid());
    }

    Result validate(P10TemplateBody body, ValidationContext context, ValidationResult prefix) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(prefix, "prefix");
        var primary = prefix.hasErrors() ? Classification.SEMANTIC_INVALID : Classification.ACCEPTED;
        var document = new SkillDocument(SkillDocument.CURRENT_SCHEMA_VERSION,
                ADAPTER_ID, new SkillRevision(0), body.nodes(), body.appearance());
        var ordered = resolver.resolveAndValidate(document, NO_READ_FACTS, context, profiles);
        var analysis = ordered.analysis();
        primary = first(primary, switch (ordered.firstFailure()) {
            case NONE -> Classification.ACCEPTED;
            case UNKNOWN_TYPE -> Classification.UNKNOWN_TYPE;
            case FUTURE_SCHEMA -> Classification.FUTURE_SCHEMA;
            case MIGRATION_FAILED -> Classification.MIGRATION_FAILED;
            case DECODE_FAILED -> Classification.DECODE_FAILED;
            case SEMANTIC_INVALID -> Classification.SEMANTIC_INVALID;
        });
        // Production has no prefix: retain the pipeline's bounded report without copying it.
        // The package-private diagnostic seam may merge a prefix, but never reruns validation.
        var report = prefix.equals(ValidationResult.valid())
                ? analysis.report() : new ValidationCollector().add(prefix).add(analysis.report()).result();
        if (primary == Classification.ACCEPTED) {
            var projected = new SkillDefinitionProjector().project(analysis);
            if (!(projected instanceof SkillValidationOutcome.Accepted accepted)
                    || !P9StarterSkillContent.hasSupportedStarterGameplay(accepted.definition())) {
                primary = Classification.SEMANTIC_INVALID;
            }
        }
        return new Result(primary, report);
    }

    private static Classification first(Classification current, Classification next) {
        return current == Classification.ACCEPTED ? next : current;
    }

    enum Classification {
        ACCEPTED, UNKNOWN_TYPE, MIGRATION_FAILED, FUTURE_SCHEMA, DECODE_FAILED,
        SEMANTIC_INVALID, CONTEXT_UNAVAILABLE
    }

    record Result(Classification classification, ValidationResult report) {
        Result {
            Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(report, "report");
            if (classification == Classification.ACCEPTED && report.hasErrors()) {
                throw new IllegalArgumentException("An accepted result cannot hide a fatal issue");
            }
        }

        boolean ready() {
            return classification == Classification.ACCEPTED;
        }
    }
}
