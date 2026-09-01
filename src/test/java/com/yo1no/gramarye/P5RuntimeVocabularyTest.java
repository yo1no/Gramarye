package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class P5RuntimeVocabularyTest {
    private static final TriggerEventKind TEST_EVENT_KIND = new TriggerEventKind(
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "p5_vocabulary_test"));

    @Test
    void resultFamiliesHaveExactlyTheClosedPermitsSets() {
        assertEquals(
                Set.of(
                        "AcceptedMemoryOnly",
                        "PersistentScheduleUnsupported",
                        "DelayOutOfRange",
                        "DelayOverflow",
                        "DeadlineOutOfRange",
                        "DeadlineOverflow",
                        "DeadlineBeforeScheduledTick",
                        "InvalidRuntimeReference",
                        "SkillRevisionUnavailable",
                        "InvalidEvent",
                        "OwnerInstanceUnavailable",
                        "ActiveLineageCapacityExceeded",
                        "ActiveBudgetAttributionCapacityExceeded",
                        "RootAdmissionBudgetExceeded",
                        "CircuitBroken",
                        "ServerNotRunning",
                        "ServerStopping",
                        "WrongThread",
                        "SequenceExhausted",
                        "TickExhausted",
                        "KernelFaulted"),
                permittedSimpleNames(RuntimeAdmissionResult.class));
        assertEquals(
                Set.of(
                        "CancelledEvent",
                        "CancelledSkillInstance",
                        "CancellationRequested",
                        "InFlight",
                        "AlreadyCancelled",
                        "NotPending",
                        "WrongServer",
                        "WrongThread",
                        "ServerNotRunning",
                        "ServerStopping",
                        "CancellationBudgetExceeded",
                        "CancellationTokenInvalid"),
                permittedSimpleNames(RuntimeCancellationResult.class));
        assertEquals(
                Set.of(
                        "Completed",
                        "CompletedWithChildren",
                        "RejectedByExecutionPort",
                        "SkillRevisionUnavailable",
                        "SourceMissing",
                        "TargetMissing",
                        "InvalidRuntimeReference",
                        "DeadlineExpired",
                        "Cancelled",
                        "OwnerInstanceUnavailable",
                        "ServerStopping",
                        "BudgetRejected",
                        "ScheduleRejected",
                        "CircuitBroken",
                        "InvalidEvent"),
                permittedSimpleNames(RuntimeExecutionOutcome.class));
        assertEquals(
                Set.of("Completed", "Rejected"),
                permittedSimpleNames(RuntimePortOutcome.class));
        assertEquals(
                Set.of(
                        "DefinitionSubsystemUnavailable",
                        "ExactRevisionMissing",
                        "RuntimeProjectionUnavailable",
                        "TransientPinUnavailable"),
                permittedSimpleNames(SkillRevisionUnavailableReason.class));
        assertEquals(
                Set.of("Resolved", "SourceMissing", "TargetMissing", "InvalidRuntimeReference"),
                permittedSimpleNames(RuntimeReferenceResolutionOutcome.class));
    }

    @Test
    void stableAndResolvedReferenceFamiliesHaveExactlyTheClosedPermitsSets() {
        assertEquals(
                Set.of("ServerOrigin", "PlayerOrigin", "EntityOrigin", "BlockOrigin"),
                permittedSimpleNames(RuntimeOrigin.class));
        assertEquals(
                Set.of("PlayerTarget", "EntityTarget", "BlockTarget"),
                permittedSimpleNames(RuntimeTarget.class));
        assertEquals(
                Set.of("RootTriggerCause", "ChildTriggerCause"),
                permittedSimpleNames(RuntimeTriggerCause.class));
        assertEquals(
                Set.of("NoRuntimeExecutionData"),
                permittedSimpleNames(RuntimeExecutionData.class));
        assertEquals(
                Set.of("PlayerRuntimeBudgetAttribution", "NonPlayerRuntimeBudgetAttribution"),
                permittedSimpleNames(RuntimeBudgetAttribution.class));
        assertEquals(
                Set.of("RuntimeCancellationToken", "RuntimeEventToken"),
                permittedSimpleNames(RuntimeCancellationHandle.class));
        assertEquals(
                Set.of(
                        "ResolvedServerOrigin",
                        "ResolvedPlayerOrigin",
                        "ResolvedEntityOrigin",
                        "ResolvedBlockOrigin"),
                permittedSimpleNames(ResolvedRuntimeOrigin.class));
        assertEquals(
                Set.of(
                        "NoResolvedRuntimeTarget",
                        "ResolvedPlayerTarget",
                        "ResolvedEntityTarget",
                        "ResolvedBlockTarget"),
                permittedSimpleNames(ResolvedRuntimeTarget.class));
    }

    @Test
    void enumsHaveExactlyTheClosedConstants() {
        assertEnum(RuntimeEntityKind.class, "ANY_ENTITY", "LIVING_ENTITY");
        assertEnum(RuntimeSchedulePersistence.class, "MEMORY_ONLY", "PERSISTENT");
        assertEnum(NonPlayerRuntimeBudgetDomain.class, "SERVER_AUTOMATION");
        assertEnum(RuntimeCancellationTokenInvalidReason.class, "EVENT_OWNER_MISMATCH");
        assertEnum(
                RuntimeBudgetDecision.class,
                "EXECUTE",
                "DEFER_SKILL_INSTANCE_TICK_LIMIT",
                "DEFER_PLAYER_TICK_LIMIT",
                "DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT");
        assertEnum(
                RuntimeCircuitBreakReason.class,
                "SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED",
                "PLAYER_PENDING_EVENTS_EXCEEDED",
                "NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED",
                "SERVER_PENDING_EVENTS_EXCEEDED");
        assertEnum(
                RuntimeReferenceFailureReason.class,
                "WRONG_SERVER",
                "DIMENSION_UNAVAILABLE",
                "WRONG_DIMENSION",
                "MISSING",
                "MISSING_OR_UNLOADED",
                "UNLOADED",
                "TYPE_MISMATCH");
        assertEnum(
                RuntimePortRejectionReason.class,
                "PORT_UNAVAILABLE",
                "EFFECT_SPECIFIC_SOURCE_REJECTED",
                "EFFECT_SPECIFIC_TARGET_REJECTED");
        assertEnum(RuntimeSequenceKind.class, "EVENT_SEQUENCE", "SKILL_INSTANCE_SEQUENCE");
        assertEnum(RuntimeDrainStopReason.class, "SERVER_EXECUTION_LIMIT_REACHED");
        assertEnum(RuntimeTickAdvanceResult.class, "ADVANCED", "EXHAUSTED");
        assertEnum(
                RuntimeScheduleRejectionReason.class,
                "DELAY_OUT_OF_RANGE",
                "DELAY_OVERFLOW",
                "DEADLINE_OUT_OF_RANGE",
                "DEADLINE_OVERFLOW",
                "DEADLINE_BEFORE_SCHEDULED_TICK");
        assertEnum(
                RuntimeBudgetRejectionReason.class,
                "LINEAGE_EVENT_LIMIT_EXCEEDED",
                "DEPTH_LIMIT_EXCEEDED",
                "DIRECT_CHILD_LIMIT_EXCEEDED",
                "ZERO_DELAY_CHILD_LIMIT_EXCEEDED",
                "EVENT_SEQUENCE_CAPACITY_EXCEEDED");
        assertEnum(
                InvalidEventReason.class,
                "INVALID_NODE_COORDINATE",
                "INVALID_NODE_CAPABILITY",
                "INVALID_TRIGGER_CAUSE",
                "INVALID_REFERENCE_SHAPE",
                "INVALID_EXECUTION_DATA",
                "INVALID_BUDGET_ATTRIBUTION",
                "BUDGET_ATTRIBUTION_MISMATCH",
                "DUPLICATE_LIVE_SKILL_INSTANCE_ID");
        assertEnum(
                P5RuntimeConfigurationFailureReason.class,
                "CONFIG_UNAVAILABLE",
                "MISSING_REQUIRED_VALUE",
                "WRONG_VALUE_TYPE",
                "BELOW_MINIMUM",
                "ABOVE_HARD_MAXIMUM",
                "RELATION_VIOLATION",
                "DERIVATION_OVERFLOW");
    }

    @Test
    void kernelCodesAndLimitKeysAreExact() {
        assertEnum(
                RuntimeKernelException.Code.class,
                "DUPLICATE_SERVER_INSTALL",
                "SECOND_ACTIVE_SERVER",
                "STOPPED_SERVER_INSTALL",
                "TICK_BEFORE_INSTALL",
                "WRONG_THREAD_LIFECYCLE",
                "WRONG_THREAD_DRAIN",
                "WRONG_THREAD_CHILD_ADMISSION",
                "NESTED_DRAIN",
                "QUEUED_EVENT_IDENTITY_INVARIANT",
                "EVENT_INDEX_INVARIANT",
                "RESERVATION_ACCOUNTING_INVARIANT",
                "LEASE_ACCOUNTING_INVARIANT",
                "ATTRIBUTION_STATE_CAPACITY_INVARIANT",
                "DEFERRED_BUFFER_OVERFLOW",
                "BREAKER_SCRATCH_OVERFLOW",
                "CHILD_PLAN_HARD_CAPACITY_EXCEEDED",
                "NULL_EXECUTION_BATCH",
                "INVALID_OUTCOME_PLAN_PAIRING",
                "INVALID_CHILD_PLAN_INVARIANT",
                "SERVER_SLOT_TOKEN_EXHAUSTED");
        assertEnum(
                P5RuntimeLimitKey.class,
                "PENDING_EVENTS_PER_SKILL_INSTANCE",
                "PENDING_EVENTS_PER_ATTRIBUTION",
                "PENDING_EVENTS_PER_SERVER",
                "ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION",
                "ACTIVE_SKILL_INSTANCES_PER_SERVER",
                "ROOT_ADMISSIONS_PER_TICK",
                "EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK",
                "EXECUTIONS_PER_ATTRIBUTION_PER_TICK",
                "EXECUTIONS_PER_SERVER_PER_TICK",
                "EVENTS_PER_SKILL_INSTANCE",
                "MAXIMUM_DEPTH",
                "DIRECT_CHILDREN_PER_EVENT",
                "ZERO_DELAY_CHILDREN_PER_EVENT",
                "MAXIMUM_DELAY_TICKS",
                "MAXIMUM_DEADLINE_HORIZON_TICKS",
                "CANCELLATIONS_PER_TICK");
    }

    @Test
    void primitiveIdentityAndHandleConstructorsRejectInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeServerToken(0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeSkillInstanceSequence(-1));
        assertThrows(NullPointerException.class, () -> new RuntimePlayerId(null));
        assertThrows(NullPointerException.class, () -> new RuntimeEntityId(null));

        var server = new RuntimeServerToken(1);
        var instance = new SkillInstanceId(UUID.randomUUID());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeEventToken(server, instance, new EventId(0)));
        assertThrows(
                NullPointerException.class,
                () -> new RuntimeCancellationToken(server, null));
    }

    @Test
    void blockValuesAndChildPlansAreDefensivelyCopiedAndHardBounded() {
        var server = new RuntimeServerToken(1);
        var position = new BlockPos(1, 2, 3);
        var origin = new BlockOrigin(server, net.minecraft.world.level.Level.OVERWORLD, position);
        assertEquals(position, origin.position());
        assertNotSame(position, origin.position());

        var mutable = new ArrayList<RuntimeChildSpec>();
        mutable.add(childSpec(server));
        var plan = new RuntimeChildPlan(mutable);
        mutable.clear();
        assertEquals(1, plan.children().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.children().clear());

        var failure = assertThrows(
                RuntimeKernelException.class,
                () -> new RuntimeChildPlan(Collections.nCopies(
                        RuntimeChildPlan.PHYSICAL_MAXIMUM + 1,
                        childSpec(server))));
        assertEquals(
                RuntimeKernelException.Code.CHILD_PLAN_HARD_CAPACITY_EXCEEDED,
                failure.code());
    }

    @Test
    void executionBatchAndBudgetRelationsAreClosed() {
        var server = new RuntimeServerToken(1);
        var nonempty = new RuntimeChildPlan(List.of(childSpec(server)));
        var pairing = assertThrows(
                RuntimeKernelException.class,
                () -> new RuntimeExecutionBatch(
                        new RuntimePortOutcome.Rejected(
                                RuntimePortRejectionReason.PORT_UNAVAILABLE),
                        nonempty));
        assertEquals(RuntimeKernelException.Code.INVALID_OUTCOME_PLAN_PAIRING, pairing.code());
        new RuntimeExecutionBatch(new RuntimePortOutcome.Completed(), nonempty);

        new RuntimeExecutionBudget(32, 16, 511, 32, 12_000, 12_000, 255, 1_023, 4_095);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeExecutionBudget(
                        32, 16, 31, 32, 12_000, 12_000, 255, 1_023, 4_095));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeExecutionBudget(1, 1, 1, 0, 0, 0, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeExecutionBudget(0, 0, 0, 0, 1, 0, 0, 0, 0));
    }

    @Test
    void runtimeEventEnforcesPublishedOwnershipAndRootChildShape() {
        var server = new RuntimeServerToken(1);
        var instance = new SkillInstanceId(UUID.randomUUID());
        var token = new RuntimeCancellationToken(server, instance);
        var event = new RuntimeEvent(
                new EventId(1),
                instance,
                new RuntimeSkillInstanceSequence(1),
                token,
                Optional.empty(),
                reference(),
                0,
                0,
                0,
                0,
                0,
                0,
                RuntimeSchedulePersistence.MEMORY_ONLY,
                new NonPlayerRuntimeBudgetAttribution(
                        server, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION),
                new ServerOrigin(server),
                Optional.empty(),
                new RootTriggerCause(TEST_EVENT_KIND),
                NoRuntimeExecutionData.INSTANCE);
        assertEquals(new EventId(1), event.eventId());

        var wrongOwner = new RuntimeCancellationToken(
                server, new SkillInstanceId(UUID.randomUUID()));
        var identityFailure = assertThrows(
                RuntimeKernelException.class,
                () -> new RuntimeEvent(
                        new EventId(2),
                        instance,
                        new RuntimeSkillInstanceSequence(1),
                        wrongOwner,
                        Optional.empty(),
                        reference(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        RuntimeSchedulePersistence.MEMORY_ONLY,
                        new NonPlayerRuntimeBudgetAttribution(
                                server, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION),
                        new ServerOrigin(server),
                        Optional.empty(),
                        new RootTriggerCause(TEST_EVENT_KIND),
                        NoRuntimeExecutionData.INSTANCE));
        assertEquals(
                RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT,
                identityFailure.code());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeEvent(
                        new EventId(2),
                        instance,
                        new RuntimeSkillInstanceSequence(1),
                        token,
                        Optional.empty(),
                        reference(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        RuntimeSchedulePersistence.PERSISTENT,
                        new NonPlayerRuntimeBudgetAttribution(
                                server, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION),
                        new ServerOrigin(server),
                        Optional.empty(),
                        new RootTriggerCause(TEST_EVENT_KIND),
                        NoRuntimeExecutionData.INSTANCE));
    }

    @Test
    void resultConstructorsRejectUnauthorizedReasonAndCountShapes() {
        new RuntimeAdmissionResult.DelayOutOfRange(-1, 12_000);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeAdmissionResult.DelayOutOfRange(0, 12_000));
        new RuntimeAdmissionResult.DeadlineBeforeScheduledTick(2, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeAdmissionResult.DeadlineBeforeScheduledTick(1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeAdmissionResult.InvalidEvent(
                        InvalidEventReason.INVALID_NODE_CAPABILITY));
        new RuntimeAdmissionResult.InvalidEvent(
                InvalidEventReason.DUPLICATE_LIVE_SKILL_INSTANCE_ID);

        new RuntimeCancellationResult.CancelledSkillInstance(256);
        new RuntimeCancellationResult.CancellationRequested(255);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeCancellationResult.CancelledSkillInstance(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeCancellationResult.CancellationRequested(256));

        new RuntimeExecutionOutcome.SourceMissing(RuntimeReferenceFailureReason.UNLOADED);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeExecutionOutcome.SourceMissing(
                        RuntimeReferenceFailureReason.WRONG_SERVER));
        new RuntimeExecutionOutcome.InvalidRuntimeReference(
                RuntimeReferenceFailureReason.WRONG_DIMENSION);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeExecutionOutcome.InvalidRuntimeReference(
                        RuntimeReferenceFailureReason.MISSING));
        new RuntimeExecutionOutcome.DeadlineExpired(10, 11);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeExecutionOutcome.DeadlineExpired(10, 10));
    }

    @Test
    void circuitBreakerSummarySeparatesRootAndInFlightBounds() {
        var rootSummary = new RuntimeCircuitBreakerSummary(
                RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED,
                1_024,
                1,
                1_024,
                0,
                false);
        new RuntimeAdmissionResult.CircuitBroken(rootSummary);

        var inFlightSummary = new RuntimeCircuitBreakerSummary(
                RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
                256,
                1,
                256,
                255,
                true);
        new RuntimeExecutionOutcome.CircuitBroken(inFlightSummary);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeCircuitBreakerSummary(
                        RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
                        256,
                        1,
                        256,
                        256,
                        true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeCircuitBreakerSummary(
                        RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                        1_000,
                        1,
                        1_000,
                        999,
                        true));
    }

    @Test
    void configurationFailureShapesAreClosed() {
        new P5RuntimeConfigurationFailure(
                P5RuntimeConfigurationFailureReason.CONFIG_UNAVAILABLE,
                Optional.empty(),
                Optional.empty());
        new P5RuntimeConfigurationFailure(
                P5RuntimeConfigurationFailureReason.RELATION_VIOLATION,
                Optional.of(P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT),
                Optional.of(P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new P5RuntimeConfigurationFailure(
                        P5RuntimeConfigurationFailureReason.RELATION_VIOLATION,
                        Optional.of(P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS),
                        Optional.of(P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new P5RuntimeConfigurationFailure(
                        P5RuntimeConfigurationFailureReason.CONFIG_UNAVAILABLE,
                        Optional.of(P5RuntimeLimitKey.MAXIMUM_DELAY_TICKS),
                        Optional.empty()));
    }

    @Test
    void canonicalHardCapacityConstantsMatchTheAuthority() {
        assertEquals(1, MagicSafetyCeilings.MAX_SERVER_SLOTS_PER_MOD_INSTANCE);
        assertEquals(4_096, MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER);
        assertEquals(256, MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SKILL_INSTANCE);
        assertEquals(1_024, MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_PLAYER);
        assertEquals(1_024, MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_NON_PLAYER_DOMAIN);
        assertEquals(128, MagicSafetyCeilings.MAX_ACTIVE_LINEAGES_PER_SERVER);
        assertEquals(
                32,
                MagicSafetyCeilings.MAX_ACTIVE_SKILL_INSTANCES_PER_BUDGET_ATTRIBUTION);
        assertEquals(64, MagicSafetyCeilings.MAX_ROOT_ADMISSIONS_PER_TICK);
        assertEquals(64, MagicSafetyCeilings.MAX_EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK);
        assertEquals(128, MagicSafetyCeilings.MAX_EXECUTIONS_PER_PLAYER_PER_TICK);
        assertEquals(
                128,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_NON_PLAYER_DOMAIN_PER_TICK);
        assertEquals(512, MagicSafetyCeilings.MAX_EXECUTIONS_PER_SERVER_PER_TICK);
        assertEquals(512, MagicSafetyCeilings.MAX_EVENTS_PER_LINEAGE);
        assertEquals(511, MagicSafetyCeilings.MAX_DESCENDANTS_PER_LINEAGE);
        assertEquals(32, MagicSafetyCeilings.MAX_DEPTH_PER_LINEAGE);
        assertEquals(32, MagicSafetyCeilings.MAX_DIRECT_CHILDREN_PER_EVENT);
        assertEquals(16, MagicSafetyCeilings.MAX_ZERO_DELAY_CHILDREN_PER_EVENT);
        assertEquals(12_000, MagicSafetyCeilings.MAX_DELAY_TICKS);
        assertEquals(12_000, MagicSafetyCeilings.MAX_DEADLINE_HORIZON_TICKS);
        assertEquals(128, MagicSafetyCeilings.MAX_CANCELLATIONS_PER_TICK);
        assertEquals(0, MagicSafetyCeilings.MAX_RETAINED_EVENT_HISTORY);
        assertEquals(256, MagicSafetyCeilings.MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER);
        assertEquals(2, MagicSafetyCeilings.MAX_CURRENT_TICK_TOP_OFFENDER_SLOTS);
        assertEquals(128, MagicSafetyCeilings.MAX_DEFINITION_LEASES_PER_SERVER);
        assertEquals(32, MagicSafetyCeilings.MAX_TRANSIENT_CHILD_PLAN_ENTRIES);
        assertEquals(
                192,
                MagicSafetyCeilings.MAX_RUNTIME_BUDGET_ATTRIBUTION_STATES_PER_SERVER);
        assertEquals(4_096, MagicSafetyCeilings.MAX_BUDGET_DEFERRED_EVENTS);
        assertEquals(4_096, MagicSafetyCeilings.MAX_BREAKER_CLEANUP_SCRATCH_EVENTS);
        assertEquals(0, MagicSafetyCeilings.MAX_PERSISTENT_SCHEDULES_PER_SERVER);
    }

    @Test
    void vocabularyTopLevelTypesStayPackagePrivate() {
        for (var type : List.of(
                RuntimeServerToken.class,
                RuntimeOrigin.class,
                RuntimeTarget.class,
                RuntimeEvent.class,
                RuntimeAdmissionResult.class,
                RuntimeCancellationResult.class,
                RuntimeExecutionOutcome.class,
                RuntimePortOutcome.class,
                RuntimeReferenceResolutionOutcome.class,
                RuntimeExecutionPort.class,
                RuntimeKernelException.class,
                P5RuntimeConfigurationException.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
        }
    }

    private static RuntimeChildSpec childSpec(RuntimeServerToken server) {
        return new RuntimeChildSpec(
                0,
                0,
                0,
                new ServerOrigin(server),
                Optional.empty(),
                new ChildTriggerCause(TEST_EVENT_KIND),
                NoRuntimeExecutionData.INSTANCE);
    }

    private static SkillReference reference() {
        return new SkillReference(new SkillId(UUID.randomUUID()), new SkillRevision(0));
    }

    private static Set<String> permittedSimpleNames(Class<?> type) {
        assertTrue(type.isSealed(), type.getName());
        return Arrays.stream(type.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void assertEnum(Class<? extends Enum<?>> type, String... expected) {
        assertEquals(
                Set.of(expected),
                Arrays.stream(type.getEnumConstants())
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
