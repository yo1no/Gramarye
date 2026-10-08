package com.yo1no.gramarye;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationStep;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationStepOutput;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Stateless descriptor for {@code gramarye:active_cast}. */
final class P9ActiveCastTriggerType implements TriggerType<P9ActiveCastTriggerPayloadV1> {
    static final P9ActiveCastTriggerType INSTANCE = new P9ActiveCastTriggerType();

    private static final MapCodec<P9ActiveCastTriggerPayloadV1> CODEC =
            P9StarterSkillContent.closedPayload(
                    Set.of("cooldown_ticks"),
                    P9StarterSkillContent.EXACT_INT.fieldOf("cooldown_ticks")
                            .xmap(P9ActiveCastTriggerPayloadV1::new,
                                    P9ActiveCastTriggerPayloadV1::cooldownTicks));
    private static final PayloadMigrationPlan MIGRATION_PLAN =
            new PayloadMigrationPlan(List.of(new LegacyActiveCastMigration()));
    private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
            SourceRequirement.NONE,
            TargetRequirement.NONE,
            false,
            Set.of(new TriggerEventKind(P9StarterSkillContent.ACTIVE_CAST_ID)),
            Set.of(TriggerSourceScope.CURRENT_INSTANCE),
            Set.of(TriggerGranularity.PER_EVENT));
    private static final TriggerReferenceProjection PROJECTION = new TriggerReferenceProjection(
            SourceSelection.NONE,
            TargetSelection.NONE,
            false,
            List.of());
    private static final TriggerPayloadInspector<P9ActiveCastTriggerPayloadV1> INSPECTOR =
            payload -> {
                Objects.requireNonNull(payload, "payload");
                return new PayloadInspectionResult.Success<>(PROJECTION);
            };

    private P9ActiveCastTriggerType() {
    }

    @Override
    public int currentPayloadSchemaVersion() {
        return 1;
    }

    @Override
    public PayloadMigrationPlan payloadMigrationPlan() {
        return MIGRATION_PLAN;
    }

    @Override
    public Optional<TriggerPayloadInspector<P9ActiveCastTriggerPayloadV1>> payloadInspector() {
        return Optional.of(INSPECTOR);
    }

    @Override
    public MapCodec<P9ActiveCastTriggerPayloadV1> payloadCodec() {
        return CODEC;
    }

    @Override
    public TriggerCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ValidationResult validate(
            P9ActiveCastTriggerPayloadV1 payload, ValidationContext context) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        var collector = new ValidationCollector();
        if (payload.cooldownTicks() < 0 || payload.cooldownTicks() > 600) {
            collector.add(new ValidationIssue(
                    ValidationIssueCode.fromNamespaceAndPath(
                            Gramarye.MOD_ID, "p11.active_cast.cooldown_out_of_range"),
                    ValidationSeverity.ERROR,
                    ValidationPath.empty().field("cooldown_ticks"),
                    ValidationIssueMetadata.none()));
        }
        return collector.result();
    }

    /** Only the exact historical empty map is a zero policy; malformed input stays failed. */
    private static final class LegacyActiveCastMigration implements PayloadMigrationStep {
        @Override
        public int fromVersion() {
            return 0;
        }

        @Override
        public int toVersion() {
            return 1;
        }

        @Override
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(Dynamic<T> input) {
            return input.getOps().getMap(input.getValue()).flatMap(map -> {
                if (map.entries().findAny().isPresent()) {
                    return DataResult.error(() -> "Legacy active_cast must be exactly empty");
                }
                return CODEC.codec().encodeStart(input.getOps(), new P9ActiveCastTriggerPayloadV1(0))
                        .map(encoded -> new PayloadMigrationStepOutput<>(
                                new Dynamic<>(input.getOps(), encoded)));
            });
        }
    }
}
