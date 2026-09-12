package com.yo1no.gramarye;

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
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Stateless descriptor for {@code gramarye:active_cast}. */
final class P9ActiveCastTriggerType implements TriggerType<P9ActiveCastTriggerPayloadV0> {
    static final P9ActiveCastTriggerType INSTANCE = new P9ActiveCastTriggerType();

    private static final MapCodec<P9ActiveCastTriggerPayloadV0> CODEC =
            P9StarterSkillContent.closedPayload(
                    Set.of(), MapCodec.unit(P9ActiveCastTriggerPayloadV0.INSTANCE));
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
    private static final TriggerPayloadInspector<P9ActiveCastTriggerPayloadV0> INSPECTOR =
            payload -> {
                Objects.requireNonNull(payload, "payload");
                return new PayloadInspectionResult.Success<>(PROJECTION);
            };

    private P9ActiveCastTriggerType() {
    }

    @Override
    public int currentPayloadSchemaVersion() {
        return 0;
    }

    @Override
    public Optional<TriggerPayloadInspector<P9ActiveCastTriggerPayloadV0>> payloadInspector() {
        return Optional.of(INSPECTOR);
    }

    @Override
    public MapCodec<P9ActiveCastTriggerPayloadV0> payloadCodec() {
        return CODEC;
    }

    @Override
    public TriggerCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ValidationResult validate(
            P9ActiveCastTriggerPayloadV0 payload, ValidationContext context) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        return ValidationResult.valid();
    }
}
