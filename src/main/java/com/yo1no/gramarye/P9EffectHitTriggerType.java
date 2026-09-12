package com.yo1no.gramarye;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.inspection.NodeReference;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Stateless descriptor for {@code gramarye:effect_hit}. */
final class P9EffectHitTriggerType implements TriggerType<P9EffectHitTriggerPayloadV0> {
    static final P9EffectHitTriggerType INSTANCE = new P9EffectHitTriggerType();

    private static final ValidationIssueCode NONCANONICAL_SOURCE_NODE =
            code("p9.effect_hit.noncanonical_source_node_index");
    private static final ValidationIssueCode NONCANONICAL_SOURCE_OUTPUT =
            code("p9.effect_hit.noncanonical_source_output_ordinal");
    private static final ValidationIssueCode NONCANONICAL_DERIVED_POLICY =
            code("p9.effect_hit.noncanonical_include_derived");
    private static final MapCodec<P9EffectHitTriggerPayloadV0> CODEC =
            P9StarterSkillContent.closedPayload(
                    Set.of("source_node_index", "source_output_ordinal", "include_derived"),
                    RecordCodecBuilder.mapCodec(instance -> instance.group(
                                    P9StarterSkillContent.EXACT_INT.fieldOf("source_node_index")
                                            .forGetter(P9EffectHitTriggerPayloadV0::sourceNodeIndex),
                                    P9StarterSkillContent.EXACT_INT.fieldOf("source_output_ordinal")
                                            .forGetter(P9EffectHitTriggerPayloadV0::sourceOutputOrdinal),
                                    com.mojang.serialization.Codec.BOOL.fieldOf("include_derived")
                                            .forGetter(P9EffectHitTriggerPayloadV0::includeDerived))
                            .apply(instance, P9EffectHitTriggerPayloadV0::new)));
    private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
            SourceRequirement.PRIOR_NODE,
            TargetRequirement.REQUIRED,
            true,
            Set.of(new TriggerEventKind(P9StarterSkillContent.EFFECT_HIT_ID)),
            Set.of(TriggerSourceScope.SOURCE_FAMILY),
            Set.of(TriggerGranularity.PER_SOURCE));
    private static final TriggerPayloadInspector<P9EffectHitTriggerPayloadV0> INSPECTOR =
            payload -> new PayloadInspectionResult.Success<>(new TriggerReferenceProjection(
                    SourceSelection.PRIOR_NODE,
                    TargetSelection.CURRENT_TARGET,
                    true,
                    List.of(new NodeReference(
                            Objects.requireNonNull(payload, "payload").sourceNodeIndex(),
                            ReferenceRole.SOURCE,
                            ValidationPath.empty().field("source_node_index"),
                            Optional.of(ActionOutputKind.PROJECTILE)))));

    private P9EffectHitTriggerType() {
    }

    @Override
    public int currentPayloadSchemaVersion() {
        return 0;
    }

    @Override
    public Optional<TriggerPayloadInspector<P9EffectHitTriggerPayloadV0>> payloadInspector() {
        return Optional.of(INSPECTOR);
    }

    @Override
    public MapCodec<P9EffectHitTriggerPayloadV0> payloadCodec() {
        return CODEC;
    }

    @Override
    public TriggerCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ValidationResult validate(
            P9EffectHitTriggerPayloadV0 payload, ValidationContext context) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        var collector = new ValidationCollector();
        if (payload.sourceNodeIndex() != 0) {
            collector.add(error(NONCANONICAL_SOURCE_NODE, "source_node_index"));
        }
        if (payload.sourceOutputOrdinal() != 0) {
            collector.add(error(NONCANONICAL_SOURCE_OUTPUT, "source_output_ordinal"));
        }
        if (!payload.includeDerived()) {
            collector.add(error(NONCANONICAL_DERIVED_POLICY, "include_derived"));
        }
        return collector.result();
    }

    private static ValidationIssue error(ValidationIssueCode code, String field) {
        return new ValidationIssue(
                code,
                ValidationSeverity.ERROR,
                ValidationPath.empty().field(field),
                ValidationIssueMetadata.none());
    }

    private static ValidationIssueCode code(String path) {
        return ValidationIssueCode.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }
}
