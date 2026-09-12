package com.yo1no.gramarye;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
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

/** Stateless descriptor for {@code gramarye:spawn_projectile}. */
final class P9SpawnProjectileActionType implements ActionType<P9SpawnProjectileActionPayloadV0> {
    static final P9SpawnProjectileActionType INSTANCE = new P9SpawnProjectileActionType();

    private static final ValidationIssueCode NONCANONICAL_PROFILE_CODE =
            code("p9.spawn_projectile.noncanonical_profile_code");
    private static final ValidationIssueCode NONCANONICAL_MANA_COST =
            code("p9.spawn_projectile.noncanonical_mana_cost");
    private static final MapCodec<P9SpawnProjectileActionPayloadV0> CODEC =
            P9StarterSkillContent.closedPayload(
                    Set.of("profile_code", "mana_cost"),
                    RecordCodecBuilder.mapCodec(instance -> instance.group(
                                    P9StarterSkillContent.EXACT_INT.fieldOf("profile_code")
                                            .forGetter(P9SpawnProjectileActionPayloadV0::profileCode),
                                    P9StarterSkillContent.EXACT_LONG.fieldOf("mana_cost")
                                            .forGetter(P9SpawnProjectileActionPayloadV0::manaCost))
                            .apply(instance, P9SpawnProjectileActionPayloadV0::new)));
    private static final ActionCapabilities CAPABILITIES = new ActionCapabilities(
            SourceRequirement.NONE,
            TargetRequirement.NONE,
            false,
            Set.of(ActionOutputKind.PROJECTILE),
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            ControlClass.NONE,
            AppearanceParameterPolicy.none());
    private static final ActionReferenceProjection PROJECTION = new ActionReferenceProjection(
            SourceSelection.NONE,
            TargetSelection.NONE,
            List.of(),
            Set.of(ActionOutputKind.PROJECTILE));
    private static final ActionPayloadInspector<P9SpawnProjectileActionPayloadV0> INSPECTOR =
            payload -> {
                Objects.requireNonNull(payload, "payload");
                return new PayloadInspectionResult.Success<>(PROJECTION);
            };

    private P9SpawnProjectileActionType() {
    }

    @Override
    public int currentPayloadSchemaVersion() {
        return 0;
    }

    @Override
    public Optional<ActionPayloadInspector<P9SpawnProjectileActionPayloadV0>> payloadInspector() {
        return Optional.of(INSPECTOR);
    }

    @Override
    public MapCodec<P9SpawnProjectileActionPayloadV0> payloadCodec() {
        return CODEC;
    }

    @Override
    public ActionCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ValidationResult validate(
            P9SpawnProjectileActionPayloadV0 payload, ValidationContext context) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        var collector = new ValidationCollector();
        if (payload.profileCode() != 0) {
            collector.add(error(NONCANONICAL_PROFILE_CODE, "profile_code"));
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
