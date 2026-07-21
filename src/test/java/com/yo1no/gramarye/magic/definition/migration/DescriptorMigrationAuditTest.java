package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescriptorMigrationAuditTest {
    @Test
    void schemaZeroAndDefaultDescriptorPlanAreValid() {
        var trigger = new DefaultPlanTriggerDescriptor();
        var action = new DefaultPlanActionDescriptor();

        assertTrue(trigger.payloadMigrationPlan().steps().isEmpty());
        assertTrue(action.payloadMigrationPlan().steps().isEmpty());
        assertTrue(DescriptorMigrationAudit.audit(
                        List.of(trigger),
                        List.of(action),
                        SkillMigrationPlan.empty())
                .isEmpty());
    }

    private static final class DefaultPlanActionDescriptor
            implements ActionType<P3B2TestFixtures.ActionData> {
        private final P3B2TestFixtures.ActionDescriptor delegate =
                new P3B2TestFixtures.ActionDescriptor(0, PayloadMigrationPlan.empty());

        @Override
        public int currentPayloadSchemaVersion() {
            return delegate.currentPayloadSchemaVersion();
        }

        @Override
        public MapCodec<P3B2TestFixtures.ActionData> payloadCodec() {
            return delegate.payloadCodec();
        }

        @Override
        public ActionCapabilities capabilities() {
            return delegate.capabilities();
        }

        @Override
        public ValidationResult validate(
                P3B2TestFixtures.ActionData payload,
                ValidationContext context) {
            return delegate.validate(payload, context);
        }
    }

    @Test
    void completeDescriptorAndSkillCoveragePasses() {
        var payloadPlan = new PayloadMigrationPlan(List.of(
                new PassThroughPayloadStep(0),
                new PassThroughPayloadStep(1)));
        var skillPlan = new SkillMigrationPlan(List.of(
                new PassThroughSkillStep(0),
                new PassThroughSkillStep(1)));

        assertTrue(DescriptorMigrationAudit.auditTo(
                        List.of(new P3B2TestFixtures.TriggerDescriptor(2, payloadPlan)),
                        List.of(new P3B2TestFixtures.ActionDescriptor(2, payloadPlan)),
                        skillPlan,
                        2)
                .isEmpty());
    }

    @Test
    void triggerAndActionMissingEdgesAreReportedSeparately() {
        var triggerFailure = DescriptorMigrationAudit.auditTo(
                List.of(new P3B2TestFixtures.TriggerDescriptor(1, PayloadMigrationPlan.empty())),
                List.of(),
                SkillMigrationPlan.empty(),
                0);
        var actionFailure = DescriptorMigrationAudit.auditTo(
                List.of(),
                List.of(new P3B2TestFixtures.ActionDescriptor(1, PayloadMigrationPlan.empty())),
                SkillMigrationPlan.empty(),
                0);

        assertEquals(
                DescriptorMigrationAuditFailure.Code.TRIGGER_PAYLOAD_COVERAGE_INVALID,
                triggerFailure.orElseThrow().code());
        assertEquals(
                DescriptorMigrationAuditFailure.Code.ACTION_PAYLOAD_COVERAGE_INVALID,
                actionFailure.orElseThrow().code());
    }

    @Test
    void missingSkillMigrationEdgeIsReported() {
        var failure = DescriptorMigrationAudit.auditTo(
                List.of(),
                List.of(),
                SkillMigrationPlan.empty(),
                2);

        assertEquals(
                DescriptorMigrationAuditFailure.Code.SKILL_DOCUMENT_COVERAGE_INVALID,
                failure.orElseThrow().code());
    }

    private static final class DefaultPlanTriggerDescriptor
            implements TriggerType<P3B2TestFixtures.TriggerData> {
        private final P3B2TestFixtures.TriggerDescriptor delegate =
                new P3B2TestFixtures.TriggerDescriptor(0, PayloadMigrationPlan.empty());

        @Override
        public int currentPayloadSchemaVersion() {
            return delegate.currentPayloadSchemaVersion();
        }

        @Override
        public MapCodec<P3B2TestFixtures.TriggerData> payloadCodec() {
            return delegate.payloadCodec();
        }

        @Override
        public TriggerCapabilities capabilities() {
            return delegate.capabilities();
        }

        @Override
        public ValidationResult validate(
                P3B2TestFixtures.TriggerData payload,
                ValidationContext context) {
            return delegate.validate(payload, context);
        }
    }

    private record PassThroughPayloadStep(int fromVersion) implements PayloadMigrationStep {
        @Override
        public int toVersion() {
            return fromVersion + 1;
        }

        @Override
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                Dynamic<T> defensivePayloadCopy) {
            return DataResult.success(new PayloadMigrationStepOutput<>(defensivePayloadCopy));
        }
    }

    private record PassThroughSkillStep(int fromVersion) implements SkillMigrationStep {
        @Override
        public int toVersion() {
            return fromVersion + 1;
        }

        @Override
        public DataResult<SkillMigrationStepOutput> migrate(Dynamic<?> defensiveSourceCopy) {
            return DataResult.success(new SkillMigrationStepOutput(defensiveSourceCopy));
        }
    }
}
