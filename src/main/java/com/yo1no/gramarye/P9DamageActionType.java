package com.yo1no.gramarye;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
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

/** Stateless descriptor for {@code gramarye:damage}. */
final class P9DamageActionType implements ActionType<P9DamageActionPayloadV0> {
    static final P9DamageActionType INSTANCE = new P9DamageActionType();

    private static final ValidationIssueCode NONCANONICAL_MAGNITUDE =
            code("p9.damage.noncanonical_magnitude");
    private static final ValidationIssueCode NONCANONICAL_MANA_COST =
            code("p9.damage.noncanonical_mana_cost");
    private static final MapCodec<P9DamageActionPayloadV0> CODEC =
            P9StarterSkillContent.closedPayload(
                    Set.of("magnitude", "mana_cost"),
                    RecordCodecBuilder.mapCodec(instance -> instance.group(
                                    P9StarterSkillContent.EXACT_LONG.fieldOf("magnitude")
                                            .forGetter(P9DamageActionPayloadV0::magnitude),
                                    P9StarterSkillContent.EXACT_LONG.fieldOf("mana_cost")
                                            .forGetter(P9DamageActionPayloadV0::manaCost))
                            .apply(instance, P9DamageActionPayloadV0::new)));
    private static final ActionCapabilities CAPABILITIES = new ActionCapabilities(
            SourceRequirement.NONE,
            TargetRequirement.REQUIRED,
            false,
            Set.of(),
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            ControlClass.NONE,
            AppearanceParameterPolicy.none());
    private static final ActionReferenceProjection PROJECTION = new ActionReferenceProjection(
            SourceSelection.NONE,
            TargetSelection.CURRENT_TARGET,
            List.of(),
            Set.of());
    private static final ActionPayloadInspector<P9DamageActionPayloadV0> INSPECTOR =
            payload -> {
                Objects.requireNonNull(payload, "payload");
                return new PayloadInspectionResult.Success<>(PROJECTION);
            };

    private P9DamageActionType() {
    }

    @Override
    public int currentPayloadSchemaVersion() {
        return 0;
    }

    @Override
    public Optional<ActionPayloadInspector<P9DamageActionPayloadV0>> payloadInspector() {
        return Optional.of(INSPECTOR);
    }

    @Override
    public MapCodec<P9DamageActionPayloadV0> payloadCodec() {
        return CODEC;
    }

    @Override
    public ActionCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ValidationResult validate(
            P9DamageActionPayloadV0 payload, ValidationContext context) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        var collector = new ValidationCollector();
        if (payload.magnitude() != 4_000L) {
            collector.add(error(NONCANONICAL_MAGNITUDE, "magnitude"));
        }
        if (payload.manaCost() != 0L) {
            collector.add(error(NONCANONICAL_MANA_COST, "mana_cost"));
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
