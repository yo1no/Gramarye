package com.yo1no.gramarye.magic.definition.migration;

import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.util.Objects;
import java.util.Optional;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Post-registration fail-fast verification for document and descriptor migration coverage. */
public final class DescriptorMigrationAudit {
    private final SkillMigrationPlan skillMigrationPlan;

    public DescriptorMigrationAudit() {
        this(SkillMigrationPlans.production());
    }

    DescriptorMigrationAudit(SkillMigrationPlan skillMigrationPlan) {
        this.skillMigrationPlan = Objects.requireNonNull(
                skillMigrationPlan, "skillMigrationPlan");
    }

    public void register(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus").addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::requireValidRegisteredPlans);
    }

    private void requireValidRegisteredPlans() {
        var failure = auditTo(
                MagicRegistries.triggerTypeRegistry(),
                MagicRegistries.actionTypeRegistry(),
                skillMigrationPlan,
                SkillDocument.CURRENT_SCHEMA_VERSION);
        if (failure.isPresent()) {
            throw new IllegalStateException(
                    "Descriptor migration audit failed: " + failure.orElseThrow().code());
        }
    }

    /** Verifies registered descriptors and the production current skill-document schema. */
    public static Optional<DescriptorMigrationAuditFailure> audit(
            Iterable<? extends TriggerType<?>> triggerDescriptors,
            Iterable<? extends ActionType<?>> actionDescriptors) {
        return audit(triggerDescriptors, actionDescriptors, SkillMigrationPlans.production());
    }

    static Optional<DescriptorMigrationAuditFailure> audit(
            Iterable<? extends TriggerType<?>> triggerDescriptors,
            Iterable<? extends ActionType<?>> actionDescriptors,
            SkillMigrationPlan skillMigrationPlan) {
        return auditTo(
                triggerDescriptors,
                actionDescriptors,
                skillMigrationPlan,
                SkillDocument.CURRENT_SCHEMA_VERSION);
    }

    static Optional<DescriptorMigrationAuditFailure> auditTo(
            Iterable<? extends TriggerType<?>> triggerDescriptors,
            Iterable<? extends ActionType<?>> actionDescriptors,
            SkillMigrationPlan skillMigrationPlan,
            int currentSkillSchemaVersion) {
        Objects.requireNonNull(triggerDescriptors, "triggerDescriptors");
        Objects.requireNonNull(actionDescriptors, "actionDescriptors");
        Objects.requireNonNull(skillMigrationPlan, "skillMigrationPlan");
        try {
            for (var descriptor : triggerDescriptors) {
                Objects.requireNonNull(descriptor, "triggerDescriptor");
                var plan = Objects.requireNonNull(
                        descriptor.payloadMigrationPlan(), "trigger payloadMigrationPlan");
                if (plan.verifyCoverage(descriptor.currentPayloadSchemaVersion()).error().isPresent()) {
                    return Optional.of(new DescriptorMigrationAuditFailure(
                            DescriptorMigrationAuditFailure.Code.TRIGGER_PAYLOAD_COVERAGE_INVALID));
                }
            }
            for (var descriptor : actionDescriptors) {
                Objects.requireNonNull(descriptor, "actionDescriptor");
                var plan = Objects.requireNonNull(
                        descriptor.payloadMigrationPlan(), "action payloadMigrationPlan");
                if (plan.verifyCoverage(descriptor.currentPayloadSchemaVersion()).error().isPresent()) {
                    return Optional.of(new DescriptorMigrationAuditFailure(
                            DescriptorMigrationAuditFailure.Code.ACTION_PAYLOAD_COVERAGE_INVALID));
                }
            }
            if (skillMigrationPlan.verifyCoverage(currentSkillSchemaVersion).error().isPresent()) {
                return Optional.of(new DescriptorMigrationAuditFailure(
                        DescriptorMigrationAuditFailure.Code.SKILL_DOCUMENT_COVERAGE_INVALID));
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.of(new DescriptorMigrationAuditFailure(
                    DescriptorMigrationAuditFailure.Code.DESCRIPTOR_CONTRACT_EXCEPTION));
        }
    }
}
