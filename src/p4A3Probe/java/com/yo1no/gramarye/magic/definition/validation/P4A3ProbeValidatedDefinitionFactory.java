package com.yo1no.gramarye.magic.definition.validation;

import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Test-only construction of a minimal validated projection for legal submission plans. */
public final class P4A3ProbeValidatedDefinitionFactory {
    private static final ProbeTriggerType TRIGGER_TYPE = new ProbeTriggerType();
    private static final ProbeActionType ACTION_TYPE = new ProbeActionType();
    private static final ResolvedTriggerDefinition<ProbeTriggerPayload> TRIGGER =
            new ResolvedTriggerDefinition<>(TRIGGER_TYPE, 0, ProbeTriggerPayload.INSTANCE);
    private static final ResolvedActionDefinition<ProbeActionPayload> ACTION =
            new ResolvedActionDefinition<>(ACTION_TYPE, 0, ProbeActionPayload.INSTANCE);

    private P4A3ProbeValidatedDefinitionFactory() {
    }

    public static ValidatedSkillDefinition create(SkillDocument document) {
        var nodes = java.util.stream.IntStream.range(0, document.nodes().size())
                .mapToObj(P4A3ProbeValidatedDefinitionFactory::node)
                .toList();
        return new ValidatedSkillDefinition(
                new SkillReference(document.skillId(), document.revision()),
                nodes,
                RuntimeNeutralAppearance.Default.INSTANCE);
    }

    private static ValidatedNodeDefinition node(int index) {
        var triggerReferences = new ValidatedTriggerReferenceProjection(
                SourceSelection.NONE,
                TargetSelection.NONE,
                false,
                List.of());
        var actionReferences = new ValidatedActionReferenceProjection(
                SourceSelection.NONE,
                TargetSelection.NONE,
                List.of(),
                Set.of());
        return new ValidatedNodeDefinition(
                index,
                TRIGGER,
                ACTION,
                new ValidatedNodeReferenceProjection(triggerReferences, actionReferences),
                RuntimeNeutralAppearanceOverride.None.INSTANCE);
    }

    private enum ProbeTriggerPayload implements TriggerPayload {
        INSTANCE
    }

    private enum ProbeActionPayload implements ActionPayload {
        INSTANCE
    }

    private static final class ProbeTriggerType implements TriggerType<ProbeTriggerPayload> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(ResourceLocation.fromNamespaceAndPath(
                        "gramarye", "p4_a3_probe"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<ProbeTriggerPayload> payloadCodec() {
            return MapCodec.unit(ProbeTriggerPayload.INSTANCE);
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                ProbeTriggerPayload payload,
                ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    private static final class ProbeActionType implements ActionType<ProbeActionPayload> {
        private static final ActionCapabilities CAPABILITIES = new ActionCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ControlClass.NONE,
                AppearanceParameterPolicy.none());

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<ProbeActionPayload> payloadCodec() {
            return MapCodec.unit(ProbeActionPayload.INSTANCE);
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                ProbeActionPayload payload,
                ValidationContext context) {
            return ValidationResult.valid();
        }
    }
}
