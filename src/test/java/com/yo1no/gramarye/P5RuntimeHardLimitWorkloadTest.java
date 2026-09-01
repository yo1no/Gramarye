package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentServiceTestSupport;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;

final class P5RuntimeHardLimitWorkloadTest {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final String EXPECTED_DEFAULT_DRAIN_DIGEST = "994d53cb0c302785";
    private static final RuntimeServerToken SERVER_TOKEN = new RuntimeServerToken(1);
    private static final TriggerEventKind EVENT_KIND = new TriggerEventKind(
            ResourceLocation.fromNamespaceAndPath(
                    Gramarye.MOD_ID, "p5_hard_limit_workload"));

    @Test
    void defaultAndLowerHardLimitWorkloadsAreDeterministicBoundedAndDrainToZero() {
        var services = serviceFixture();
        var service = services.nominal();
        var limits = hardLimits();
        var lowerLimits = lowerLimits();

        assertLimitBoundaries(limits);
        assertLowerSnapshotBoundaries(service, lowerLimits);
        assertExactValueBoundaries(limits);
        exerciseExecutionAndPendingBoundaries(service, limits);

        var physicalSlot = service.newRunningSlot(SERVER_TOKEN, limits);
        // Instances and leases are value-only coordinates in this ordinary workload. Real
        // ServerSlot instance/lease graphs remain 0 -> 0 because exact pins are server-backed.
        assertTrue(physicalSlot.instances.isEmpty());
        assertTrue(physicalSlot.leases.isEmpty());
        assertPhysicalCapacities(physicalSlot);
        service.reintegrateDeferred(physicalSlot);
        assertTrue(physicalSlot.queue.isEmpty());

        var generated = buildDefaultWorkload(limits);
        var model = new WorkloadModel(limits, SERVER_TOKEN);
        model.admit(generated);
        assertDefaultWorkloadCoordinates(model, generated, limits);
        exerciseScheduleDeadlineAndPersistence(
                service, limits, generated.liveFrontier().getFirst());

        var cancellation = model.exerciseCancellationBudget(
                service, generated.cancellationEventId());
        assertAll(
                () -> assertEquals(128, cancellation.attempts()),
                () -> assertEquals(1, cancellation.cancelledEvents()),
                () -> assertEquals(127, cancellation.notPending()),
                () -> assertEquals(4_095, model.committedPending));

        var unrelatedBefore = model.pendingFor(generated.unrelatedLineage());
        var broken = model.breakSource(generated.breakLineage());
        assertAll(
                () -> assertTrue(broken.removedQueued() > 0),
                () -> assertTrue(broken.removedDeferred() > 0),
                () -> assertEquals(
                        broken.removedQueued() + broken.removedDeferred(),
                        broken.removedTotal()),
                () -> assertFalse(model.lineages.containsKey(generated.breakLineage())),
                () -> assertEquals(
                        unrelatedBefore, model.pendingFor(generated.unrelatedLineage())),
                () -> assertTrue(model.lineageCleanupScans <= 128));

        var drained = model.drainToZero();
        assertAll(
                () -> assertEquals(
                        EXPECTED_DEFAULT_DRAIN_DIGEST,
                        Long.toUnsignedString(drained.digest(), 16)),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER
                                - cancellation.cancelledEvents()
                                - broken.removedTotal(),
                        drained.drainedEvents()),
                () -> assertEquals(128, drained.lineageCleanupScans()),
                () -> assertEquals(128, drained.cancellationAttempts()));
        model.assertCompletelyZero();

        exerciseLowerWorkload(service, lowerLimits);
        exerciseModeledResolverPortAndFaultBoundaries(services, service, limits);
        exerciseBreakerRingOverwrite(service, physicalSlot, generated.liveFrontier());

        service.stopSlot(physicalSlot);
        assertRealSlotCleanedToZero(physicalSlot);
    }

    private static P5RuntimeLimits hardLimits() {
        return P5RuntimeLimits.fromRequested(new P5RuntimeRequestedLimits(
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SKILL_INSTANCE,
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_PLAYER,
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER,
                MagicSafetyCeilings.MAX_ACTIVE_SKILL_INSTANCES_PER_BUDGET_ATTRIBUTION,
                MagicSafetyCeilings.MAX_ACTIVE_LINEAGES_PER_SERVER,
                MagicSafetyCeilings.MAX_ROOT_ADMISSIONS_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_PLAYER_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_SERVER_PER_TICK,
                MagicSafetyCeilings.MAX_EVENTS_PER_LINEAGE,
                MagicSafetyCeilings.MAX_DEPTH_PER_LINEAGE,
                MagicSafetyCeilings.MAX_DIRECT_CHILDREN_PER_EVENT,
                MagicSafetyCeilings.MAX_ZERO_DELAY_CHILDREN_PER_EVENT,
                MagicSafetyCeilings.MAX_DELAY_TICKS,
                MagicSafetyCeilings.MAX_DEADLINE_HORIZON_TICKS,
                MagicSafetyCeilings.MAX_CANCELLATIONS_PER_TICK));
    }

    private static P5RuntimeLimits lowerLimits() {
        return P5RuntimeLimits.fromRequested(new P5RuntimeRequestedLimits(
                4,
                8,
                16,
                2,
                4,
                2,
                1,
                2,
                4,
                4,
                1,
                3,
                1,
                1,
                1,
                2));
    }

    private static void assertLimitBoundaries(P5RuntimeLimits limits) {
        assertAll(
                () -> assertEquals(256, limits.pendingEventsPerSkillInstance()),
                () -> assertEquals(1_024, limits.pendingEventsPerAttribution()),
                () -> assertEquals(4_096, limits.pendingEventsPerServer()),
                () -> assertEquals(32, limits.activeSkillInstancesPerAttribution()),
                () -> assertEquals(128, limits.activeSkillInstancesPerServer()),
                () -> assertEquals(64, limits.rootAdmissionsPerTick()),
                () -> assertEquals(64, limits.executionsPerSkillInstancePerTick()),
                () -> assertEquals(128, limits.executionsPerAttributionPerTick()),
                () -> assertEquals(512, limits.executionsPerServerPerTick()),
                () -> assertEquals(512, limits.eventsPerSkillInstance()),
                () -> assertEquals(511, limits.descendantsPerSkillInstance()),
                () -> assertEquals(32, limits.maximumDepth()),
                () -> assertEquals(32, limits.directChildrenPerEvent()),
                () -> assertEquals(16, limits.zeroDelayChildrenPerEvent()),
                () -> assertEquals(12_000, limits.maximumDelayTicks()),
                () -> assertEquals(12_000, limits.maximumDeadlineHorizonTicks()),
                () -> assertEquals(128, limits.cancellationsPerTick()),
                () -> assertEquals(128, limits.definitionLeasesPerServer()),
                () -> assertEquals(192, limits.runtimeBudgetAttributionStatesPerServer()));
    }

    private static void assertLowerSnapshotBoundaries(
            SkillRuntimeService service, P5RuntimeLimits limits) {
        assertAll(
                () -> assertEquals(16, limits.pendingEventsPerServer()),
                () -> assertEquals(4, limits.activeSkillInstancesPerServer()),
                () -> assertEquals(4, limits.executionsPerServerPerTick()),
                () -> assertEquals(3, limits.descendantsPerSkillInstance()),
                () -> assertEquals(6, limits.runtimeBudgetAttributionStatesPerServer()),
                () -> assertEquals(
                        RuntimeBudgetDecision.EXECUTE,
                        service.decideExecution(limits, 0, 0, playerAttribution(0))),
                () -> assertEquals(
                        RuntimeBudgetDecision.DEFER_SKILL_INSTANCE_TICK_LIMIT,
                        service.decideExecution(limits, 1, 1, playerAttribution(0))));
    }

    private static void assertExactValueBoundaries(P5RuntimeLimits limits) {
        new RuntimeAdmissionResult.ActiveLineageCapacityExceeded(128, 128);
        new RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded(32, 32);
        new RuntimeAdmissionResult.RootAdmissionBudgetExceeded(64);
        new RuntimeCancellationResult.CancellationBudgetExceeded(128);
        new RuntimeCancellationResult.CancelledSkillInstance(256);
        new RuntimeCancellationResult.CancellationRequested(255);
        new RuntimeExecutionOutcome.CompletedWithChildren(32);

        var children = new ArrayList<RuntimeChildSpec>(limits.directChildrenPerEvent());
        for (var index = 0; index < limits.directChildrenPerEvent(); index++) {
            children.add(new RuntimeChildSpec(
                    index,
                    index < limits.zeroDelayChildrenPerEvent() ? 0 : limits.maximumDelayTicks(),
                    limits.maximumDeadlineHorizonTicks(),
                    new ServerOrigin(SERVER_TOKEN),
                    Optional.empty(),
                    new ChildTriggerCause(EVENT_KIND),
                    NoRuntimeExecutionData.INSTANCE));
        }
        assertEquals(32, new RuntimeChildPlan(children).children().size());
        new RuntimeExecutionBudget(32, 16, 511, 32, 12_000, 12_000, 255, 1_023, 4_095);
    }

    private static void assertPhysicalCapacities(ServerSlot slot) {
        assertAll(
                () -> assertEquals(4_096, slot.deferred.length),
                () -> assertEquals(4_096, slot.cleanupScratch.length),
                () -> assertEquals(256, slot.diagnostics.breakerRing.length),
                () -> assertEquals(4, slot.diagnostics.breakerTotals.length),
                () -> assertEquals(2, MagicSafetyCeilings.MAX_CURRENT_TICK_TOP_OFFENDER_SLOTS));
    }

    private static void exerciseExecutionAndPendingBoundaries(
            SkillRuntimeService service, P5RuntimeLimits limits) {
        var executions = 0;
        for (var playerIndex = 0; playerIndex < 4; playerIndex++) {
            var player = playerAttribution(playerIndex);
            for (var cohort = 0; cohort < 2; cohort++) {
                for (var attempt = 0; attempt < 64; attempt++) {
                    assertEquals(
                            RuntimeBudgetDecision.EXECUTE,
                            service.decideExecution(
                                    limits, attempt, cohort * 64 + attempt, player));
                    executions++;
                }
            }
        }
        assertEquals(512, executions);

        var automation = automationAttribution();
        var automationExecutions = 0;
        for (var cohort = 0; cohort < 2; cohort++) {
            for (var attempt = 0; attempt < 64; attempt++) {
                assertEquals(
                        RuntimeBudgetDecision.EXECUTE,
                        service.decideExecution(
                                limits, attempt, cohort * 64 + attempt, automation));
                automationExecutions++;
            }
        }
        assertEquals(128, automationExecutions);
        assertEquals(
                RuntimeBudgetDecision.DEFER_SKILL_INSTANCE_TICK_LIMIT,
                service.decideExecution(limits, 64, 127, playerAttribution(0)));
        assertEquals(
                RuntimeBudgetDecision.DEFER_PLAYER_TICK_LIMIT,
                service.decideExecution(limits, 63, 128, playerAttribution(0)));
        assertEquals(
                RuntimeBudgetDecision.DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT,
                service.decideExecution(limits, 63, 128, automation));

        assertNull(service.selectPendingBreak(
                limits, 255, 1_023, 4_095, playerAttribution(0), 1));
        assertEquals(
                RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
                service.selectPendingBreak(
                        limits, 256, 1_024, 4_096, playerAttribution(0), 1).reason());
        assertEquals(
                RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED,
                service.selectPendingBreak(
                        limits, 1, 1_024, 4_096, playerAttribution(0), 1).reason());
        assertEquals(
                RuntimeCircuitBreakReason.NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED,
                service.selectPendingBreak(limits, 1, 1_024, 4_096, automation, 1).reason());
        assertEquals(
                RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                service.selectPendingBreak(
                        limits, 1, 1, 4_096, playerAttribution(0), 1).reason());

        var serverBoundary = service.newRunningSlot(new RuntimeServerToken(2), limits);
        serverBoundary.executionsThisTick = 512;
        SkillRuntimeService.observeDrainStop(
                serverBoundary, RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED);
        assertTrue(serverBoundary.diagnostics.serverExecutionLimitReachedThisTick);
        assertEquals(1, serverBoundary.diagnostics.serverExecutionLimitReachedTickTotal);
        service.stopSlot(serverBoundary);
    }

    private static GeneratedWorkload buildDefaultWorkload(P5RuntimeLimits limits) {
        var builder = new WorkloadBuilder(limits, SERVER_TOKEN);
        for (var playerIndex = 0; playerIndex < 4; playerIndex++) {
            var attribution = playerAttribution(playerIndex);
            builder.addLineage(
                    attribution,
                    256,
                    playerIndex == 0 ? LineageProfile.LIFETIME : LineageProfile.MAX_FRONTIER);
            for (var index = 0; index < 6; index++) {
                builder.addLineage(attribution, 23, LineageProfile.ORDINARY);
            }
            for (var index = 0; index < 17; index++) {
                builder.addLineage(
                        attribution,
                        22,
                        playerIndex == 0 && index == 0
                                ? LineageProfile.DEPTH
                                : LineageProfile.ORDINARY);
            }
        }
        var automation = automationAttribution();
        builder.addLineage(automation, 256, LineageProfile.MAX_FRONTIER);
        for (var index = 0; index < 24; index++) {
            builder.addLineage(automation, 25, LineageProfile.ORDINARY);
        }
        for (var index = 0; index < 6; index++) {
            builder.addLineage(automation, 24, LineageProfile.ORDINARY);
        }
        builder.addLineage(automation, 24, LineageProfile.ORDINARY);
        return builder.build();
    }

    private static GeneratedWorkload buildLowerWorkload(P5RuntimeLimits limits) {
        var builder = new WorkloadBuilder(limits, SERVER_TOKEN);
        builder.addLineage(playerAttribution(0), 3, LineageProfile.ORDINARY);
        builder.addLineage(playerAttribution(0), 3, LineageProfile.ORDINARY);
        builder.addLineage(playerAttribution(1), 3, LineageProfile.ORDINARY);
        builder.addLineage(playerAttribution(1), 3, LineageProfile.ORDINARY);
        return builder.build();
    }

    private static void assertDefaultWorkloadCoordinates(
            WorkloadModel model, GeneratedWorkload generated, P5RuntimeLimits limits) {
        assertAll(
                () -> assertEquals(4_096, generated.liveFrontier().size()),
                () -> assertTrue(generated.trace().size() > generated.liveFrontier().size()),
                () -> assertEquals(4_096, model.committedPending),
                () -> assertEquals(2_048, model.queue.size()),
                () -> assertEquals(2_048, model.deferredCount),
                () -> assertEquals(4_096, model.index.size()),
                () -> assertEquals(128, model.lineages.size()),
                () -> assertEquals(192, model.attributions.size()),
                () -> assertEquals(128, model.modeledLeaseReferences.size()),
                () -> assertTrue(model.attributions.containsKey(playerAttribution(0))),
                () -> assertTrue(model.attributions.containsKey(playerAttribution(1))),
                () -> assertTrue(model.attributions.containsKey(playerAttribution(2))),
                () -> assertTrue(model.attributions.containsKey(playerAttribution(3))),
                () -> assertTrue(model.attributions.containsKey(automationAttribution())),
                () -> assertEquals(768, model.pendingFor(playerAttribution(0))),
                () -> assertEquals(768, model.pendingFor(playerAttribution(1))),
                () -> assertEquals(768, model.pendingFor(playerAttribution(2))),
                () -> assertEquals(768, model.pendingFor(playerAttribution(3))),
                () -> assertEquals(1_024, model.pendingFor(automationAttribution())),
                () -> assertEquals(24, model.activeFor(playerAttribution(0))),
                () -> assertEquals(32, model.activeFor(automationAttribution())),
                () -> assertEquals(512, model.createdFor(generated.lifetimeLineage())),
                () -> assertEquals(511, model.descendantsFor(generated.lifetimeLineage())),
                () -> assertEquals(256, model.pendingFor(generated.lifetimeLineage())),
                () -> assertEquals(256, model.terminalFor(generated.lifetimeLineage())),
                () -> assertEquals(54, model.createdFor(generated.depthLineage())),
                () -> assertEquals(32, model.terminalFor(generated.depthLineage())),
                () -> assertEquals(22, model.pendingFor(generated.depthLineage())),
                () -> assertEquals(265, model.createdFor(generated.maximumFrontierLineage())),
                () -> assertEquals(9, model.terminalFor(generated.maximumFrontierLineage())),
                () -> assertEquals(256, model.pendingFor(generated.maximumFrontierLineage())),
                () -> assertEquals(32, model.maximumObservedDepth),
                () -> assertEquals(32, model.maximumObservedDirectChildren),
                () -> assertEquals(16, model.maximumObservedZeroDelayChildren),
                () -> assertTrue(model.observedDelays.contains(0L)),
                () -> assertTrue(model.observedDelays.contains(1L)),
                () -> assertTrue(model.observedDelays.contains(12_000L)),
                () -> assertEquals(1, model.maximumDelayEventCount),
                () -> assertEquals(limits.pendingEventsPerServer(), model.committedPending));
        model.assertUniqueMembership();
        model.assertScratchClear();
    }

    private static void exerciseScheduleDeadlineAndPersistence(
            SkillRuntimeService service, P5RuntimeLimits limits, RuntimeEvent event) {
        var zero = new RuntimeScheduleSpec(0, 0, RuntimeSchedulePersistence.MEMORY_ONLY);
        var one = new RuntimeScheduleSpec(1, 1, RuntimeSchedulePersistence.MEMORY_ONLY);
        var maximum = new RuntimeScheduleSpec(
                limits.maximumDelayTicks(),
                limits.maximumDeadlineHorizonTicks(),
                RuntimeSchedulePersistence.MEMORY_ONLY);
        var persistent = new RuntimeScheduleSpec(0, 0, RuntimeSchedulePersistence.PERSISTENT);
        assertAll(
                () -> assertEquals(0, zero.delayTicks()),
                () -> assertEquals(1, one.delayTicks()),
                () -> assertEquals(12_000, maximum.delayTicks()),
                () -> assertEquals(12_000, maximum.deadlineHorizonTicks()),
                () -> assertTrue(service.persistenceFailure(zero).isEmpty()),
                () -> assertInstanceOf(
                        RuntimeAdmissionResult.PersistentScheduleUnsupported.class,
                        service.persistenceFailure(persistent).orElseThrow()));

        var expirySlot = service.newRunningSlot(SERVER_TOKEN, limits);
        expirySlot.runtimeTick = event.deadlineRuntimeTick();
        assertFalse(service.deadlineExpired(expirySlot, event));
        expirySlot.runtimeTick = Math.incrementExact(event.deadlineRuntimeTick());
        assertTrue(service.deadlineExpired(expirySlot, event));
        service.observeExpired(expirySlot, event);
        assertAll(
                () -> assertEquals(1, expirySlot.diagnostics.deadlineExpiredEventsThisTick),
                () -> assertEquals(1, expirySlot.diagnostics.deadlineExpiredEventTotal),
                () -> assertEquals(
                        1, expirySlot.diagnostics.maximumDeadlineLatenessTicksThisTick),
                () -> assertEquals(
                        expirySlot.runtimeTick - event.scheduledRuntimeTick(),
                        expirySlot.diagnostics.maximumLagTicksThisTick));
        service.stopSlot(expirySlot);
    }

    private static void exerciseLowerWorkload(
            SkillRuntimeService service, P5RuntimeLimits limits) {
        var generated = buildLowerWorkload(limits);
        var first = new WorkloadModel(limits, SERVER_TOKEN);
        first.admit(generated);
        assertAll(
                () -> assertEquals(12, first.committedPending),
                () -> assertEquals(4, first.lineages.size()),
                () -> assertEquals(6, first.attributions.size()),
                () -> assertEquals(4, first.modeledLeaseReferences.size()),
                () -> assertEquals(3, first.maximumObservedDirectChildren),
                () -> assertEquals(1, first.maximumObservedZeroDelayChildren),
                () -> assertEquals(1, first.maximumObservedDepth),
                () -> assertTrue(first.observedDelays.contains(0L)),
                () -> assertTrue(first.observedDelays.contains(1L)));
        var cancellation = first.exerciseCancellationBudget(
                service, generated.cancellationEventId());
        var broken = first.breakSource(generated.breakLineage());
        var drained = first.drainToZero();
        first.assertCompletelyZero();

        var second = new WorkloadModel(limits, SERVER_TOKEN);
        second.admit(generated);
        second.exerciseCancellationBudget(service, generated.cancellationEventId());
        second.breakSource(generated.breakLineage());
        var repeated = second.drainToZero();
        second.assertCompletelyZero();
        assertAll(
                () -> assertEquals(2, cancellation.attempts()),
                () -> assertEquals(1, cancellation.cancelledEvents()),
                () -> assertEquals(1, cancellation.notPending()),
                () -> assertTrue(broken.removedTotal() > 0),
                () -> assertEquals(4, drained.lineageCleanupScans()),
                () -> assertEquals(drained, repeated));
    }

    private static void exerciseModeledResolverPortAndFaultBoundaries(
            ServiceFixture services,
            SkillRuntimeService service,
            P5RuntimeLimits limits) {
        // Ordinary JUnit has no legal MinecraftServer instance. These checks therefore construct
        // the actual service with named injected dependencies, but cover only detached mapping and
        // cleanup seams. The guarded resolver -> P6 invocation remains server-backed qualification.
        var missingResolver = new DeterministicMissingReferenceResolver(
                new RuntimeReferenceResolutionOutcome.SourceMissing(
                        RuntimeReferenceFailureReason.MISSING_OR_UNLOADED));
        var successService = services.with(
                missingResolver, DeterministicSuccessExecutionPort.INSTANCE);
        assertEquals(
                RuntimeReferenceFailureReason.MISSING_OR_UNLOADED,
                assertInstanceOf(
                                RuntimeExecutionOutcome.SourceMissing.class,
                                successService.referenceFailureOutcome(missingResolver.outcome()))
                        .reason());

        for (var reason : List.of(
                RuntimeReferenceFailureReason.MISSING,
                RuntimeReferenceFailureReason.MISSING_OR_UNLOADED,
                RuntimeReferenceFailureReason.UNLOADED)) {
            assertEquals(
                    reason,
                    assertInstanceOf(
                                    RuntimeExecutionOutcome.TargetMissing.class,
                                    service.referenceFailureOutcome(
                                            new RuntimeReferenceResolutionOutcome.TargetMissing(
                                                    reason)))
                            .reason());
        }
        for (var reason : List.of(
                RuntimeReferenceFailureReason.WRONG_SERVER,
                RuntimeReferenceFailureReason.DIMENSION_UNAVAILABLE,
                RuntimeReferenceFailureReason.WRONG_DIMENSION,
                RuntimeReferenceFailureReason.TYPE_MISMATCH)) {
            assertEquals(
                    reason,
                    assertInstanceOf(
                                    RuntimeExecutionOutcome.InvalidRuntimeReference.class,
                                    service.referenceFailureOutcome(
                                            new RuntimeReferenceResolutionOutcome.InvalidRuntimeReference(
                                                    reason)))
                            .reason());
        }

        var completed = DeterministicSuccessExecutionPort.INSTANCE.fixedBatch();
        assertInstanceOf(RuntimePortOutcome.Completed.class, completed.outcome());
        assertTrue(completed.children().children().isEmpty());
        var rejectedService = services.with(
                missingResolver, DeterministicRejectedExecutionPort.INSTANCE);
        var rejected = assertInstanceOf(
                RuntimePortOutcome.Rejected.class,
                DeterministicRejectedExecutionPort.INSTANCE.fixedBatch().outcome());
        assertEquals(
                RuntimePortRejectionReason.PORT_UNAVAILABLE,
                assertInstanceOf(
                                RuntimeExecutionOutcome.RejectedByExecutionPort.class,
                                rejectedService.portRejectionOutcome(rejected))
                        .reason());

        var resolverRuntimePrimary =
                new IllegalArgumentException("deterministic-resolver-runtime-primary");
        var runtimeResolver =
                new DeterministicRuntimeExceptionReferenceResolver(resolverRuntimePrimary);
        var runtimeResolverService = services.with(
                runtimeResolver, DeterministicSuccessExecutionPort.INSTANCE);
        var runtimeResolverSlot = faultSlot(runtimeResolverService, limits, 1, 0, 20);
        var observedResolverRuntime = assertThrows(
                IllegalArgumentException.class,
                () -> {
                    throw runtimeResolverService.preserveRuntimeFault(
                            runtimeResolverSlot, runtimeResolver.primary());
                });
        assertSame(resolverRuntimePrimary, observedResolverRuntime);
        assertFaulted(runtimeResolverSlot, 1, 0);

        var resolverErrorPrimary = new AssertionError("deterministic-resolver-error-primary");
        var errorResolver = new DeterministicErrorReferenceResolver(resolverErrorPrimary);
        var errorResolverService = services.with(
                errorResolver, DeterministicSuccessExecutionPort.INSTANCE);
        var errorResolverSlot = faultSlot(errorResolverService, limits, 1, 0, 21);
        var observedResolverError = assertThrows(
                AssertionError.class,
                () -> {
                    throw errorResolverService.preserveErrorFault(
                            errorResolverSlot, errorResolver.primary());
                });
        assertSame(resolverErrorPrimary, observedResolverError);
        assertFaulted(errorResolverSlot, 1, 0);

        var resolverOomePrimary = new OutOfMemoryError("deterministic-resolver-oome-primary");
        var oomeResolver = new DeterministicOutOfMemoryReferenceResolver(resolverOomePrimary);
        var oomeResolverService = services.with(
                oomeResolver, DeterministicSuccessExecutionPort.INSTANCE);
        var oomeResolverSlot = faultSlot(oomeResolverService, limits, 1, 0, 22);
        var observedResolverOome = assertThrows(
                OutOfMemoryError.class,
                () -> {
                    throw oomeResolverService.preserveErrorFault(
                            oomeResolverSlot, oomeResolver.primary());
                });
        assertSame(resolverOomePrimary, observedResolverOome);
        assertFaulted(oomeResolverSlot, 1, 0);

        var runtimePrimary = new IllegalStateException("deterministic-port-runtime-primary");
        var runtimePort = new DeterministicRuntimeExceptionExecutionPort(runtimePrimary);
        var runtimeService = services.with(missingResolver, runtimePort);
        var runtimeSlot = faultSlot(runtimeService, limits, 1, 1, 23);
        var observedRuntime = assertThrows(
                IllegalStateException.class,
                () -> {
                    throw runtimeService.preserveRuntimeFault(runtimeSlot, runtimePort.primary());
                });
        assertSame(runtimePrimary, observedRuntime);
        assertFaulted(runtimeSlot, 1, 1);

        var errorPrimary = new AssertionError("deterministic-port-error-primary");
        var errorPort = new DeterministicErrorExecutionPort(errorPrimary);
        var errorService = services.with(missingResolver, errorPort);
        var errorSlot = faultSlot(errorService, limits, 1, 1, 24);
        var observedError = assertThrows(
                AssertionError.class,
                () -> {
                    throw errorService.preserveErrorFault(errorSlot, errorPort.primary());
                });
        assertSame(errorPrimary, observedError);
        assertFaulted(errorSlot, 1, 1);

        var oomePrimary = new OutOfMemoryError("deterministic-port-oome-primary");
        var oomePort = new DeterministicOutOfMemoryExecutionPort(oomePrimary);
        var oomeService = services.with(missingResolver, oomePort);
        var oomeSlot = faultSlot(oomeService, limits, 1, 1, 25);
        var observedOome = assertThrows(
                OutOfMemoryError.class,
                () -> {
                    throw oomeService.preserveErrorFault(oomeSlot, oomePort.primary());
                });
        assertSame(oomePrimary, observedOome);
        assertFaulted(oomeSlot, 1, 1);
    }

    private static ServerSlot faultSlot(
            SkillRuntimeService service,
            P5RuntimeLimits limits,
            int executionAttempts,
            int portInvocations,
            long token) {
        var slot = service.newRunningSlot(new RuntimeServerToken(token), limits);
        slot.executionsThisTick = executionAttempts;
        slot.diagnostics.executionAttemptsThisTick = executionAttempts;
        slot.diagnostics.portInvocationsThisTick = portInvocations;
        return slot;
    }

    private static void assertFaulted(
            ServerSlot slot, int executionAttempts, int portInvocations) {
        assertAll(
                () -> assertEquals(ServerSlot.State.FAULTED, slot.state),
                () -> assertTrue(slot.queue.isEmpty()),
                () -> assertTrue(slot.eventIndex.isEmpty()),
                () -> assertTrue(slot.instances.isEmpty()),
                () -> assertTrue(slot.attributions.isEmpty()),
                () -> assertTrue(slot.leases.isEmpty()),
                () -> assertEquals(0, slot.committedPending),
                () -> assertEquals(0, slot.reservedPending),
                () -> assertEquals(0, slot.currentReservationCount),
                () -> assertNull(slot.currentReservationOwner),
                () -> assertEquals(executionAttempts, slot.executionsThisTick),
                () -> assertEquals(
                        executionAttempts, slot.diagnostics.executionAttemptsThisTick),
                () -> assertEquals(
                        portInvocations, slot.diagnostics.portInvocationsThisTick));
        assertAllCellsNull(slot.deferred);
        assertAllCellsNull(slot.cleanupScratch);
    }

    private static void exerciseBreakerRingOverwrite(
            SkillRuntimeService service, ServerSlot slot, List<RuntimeEvent> events) {
        var summary = new RuntimeCircuitBreakerSummary(
                RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                4_096,
                1,
                4_096,
                0,
                false);
        for (var index = 0; index <= 256; index++) {
            var event = events.get(index);
            slot.runtimeTick = index;
            service.observeBreaker(
                    slot,
                    event.skillInstanceId(),
                    Optional.of(event.skillInstanceSequence()),
                    Optional.of(event.eventId()),
                    event.budgetAttribution() instanceof PlayerRuntimeBudgetAttribution player
                            ? Optional.of(player.playerId())
                            : Optional.empty(),
                    summary);
        }
        assertAll(
                () -> assertEquals(1, slot.diagnostics.breakerWriteIndex),
                () -> assertEquals(256, slot.diagnostics.breakerRing[0].runtimeTick()),
                () -> assertEquals(1, slot.diagnostics.breakerRing[1].runtimeTick()),
                () -> assertEquals(
                        257,
                        slot.diagnostics.breakerTotals[
                                RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED
                                        .ordinal()]));
    }

    private static PlayerRuntimeBudgetAttribution playerAttribution(int index) {
        return new PlayerRuntimeBudgetAttribution(
                SERVER_TOKEN, new RuntimePlayerId(new UUID(0, index + 1L)));
    }

    private static NonPlayerRuntimeBudgetAttribution automationAttribution() {
        return new NonPlayerRuntimeBudgetAttribution(
                SERVER_TOKEN, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION);
    }

    private static void assertAllCellsNull(RuntimeEvent[] events) {
        for (var event : events) {
            assertNull(event);
        }
    }

    private static void assertRealSlotCleanedToZero(ServerSlot slot) {
        assertAll(
                () -> assertEquals(ServerSlot.State.STOPPING, slot.state),
                () -> assertTrue(slot.queue.isEmpty()),
                () -> assertTrue(slot.eventIndex.isEmpty()),
                () -> assertTrue(slot.instances.isEmpty()),
                () -> assertTrue(slot.attributions.isEmpty()),
                () -> assertTrue(slot.leases.isEmpty()),
                () -> assertEquals(0, slot.deferredCount),
                () -> assertEquals(0, slot.committedPending),
                () -> assertEquals(0, slot.reservedPending),
                () -> assertEquals(0, slot.currentReservationCount),
                () -> assertNull(slot.currentReservationOwner),
                () -> assertNull(slot.currentEvent),
                () -> assertEquals(0, slot.rootAdmissionsThisTick),
                () -> assertEquals(0, slot.executionsThisTick),
                () -> assertEquals(0, slot.cancellationsThisTick),
                () -> assertEquals(0, slot.diagnostics.breakerWriteIndex));
        assertAllCellsNull(slot.deferred);
        assertAllCellsNull(slot.cleanupScratch);
        for (var diagnostic : slot.diagnostics.breakerRing) {
            assertNull(diagnostic);
        }
        for (var total : slot.diagnostics.breakerTotals) {
            assertEquals(0, total);
        }
    }

    private enum LineageProfile {
        ORDINARY,
        MAX_FRONTIER,
        DEPTH,
        LIFETIME
    }

    private static final class WorkloadBuilder {
        private final P5RuntimeLimits limits;
        private final RuntimeServerToken token;
        private final List<RuntimeEvent> trace = new ArrayList<>();
        private final List<RuntimeEvent> liveFrontier = new ArrayList<>();
        private final Set<EventId> terminalEventIds = new HashSet<>();
        private final List<SkillInstanceId> lineageOrder = new ArrayList<>();
        private final List<List<RuntimeEvent>> frontierByLineage = new ArrayList<>();
        private long nextEventId = 1;
        private long nextSequence = 1;
        private boolean maximumDelayAdded;
        private SkillInstanceId depthLineage;
        private SkillInstanceId maximumFrontierLineage;

        WorkloadBuilder(P5RuntimeLimits limits, RuntimeServerToken token) {
            this.limits = limits;
            this.token = token;
        }

        void addLineage(
                RuntimeBudgetAttribution attribution,
                int livePending,
                LineageProfile profile) {
            if (livePending <= 0 || livePending > limits.pendingEventsPerSkillInstance()) {
                throw new IllegalArgumentException("invalid modeled pending count");
            }
            var sequence = new RuntimeSkillInstanceSequence(nextSequence);
            var instanceId = new SkillInstanceId(new UUID(
                    0x5100_0000_0000_0000L + nextSequence, nextSequence));
            var reference = new SkillReference(
                    new SkillId(new UUID(
                            0x5200_0000_0000_0000L + nextSequence, nextSequence)),
                    new SkillRevision(0));
            var root = rootEvent(instanceId, sequence, reference, attribution);
            trace.add(root);
            var frontier = new ArrayList<RuntimeEvent>();
            frontier.add(root);
            switch (profile) {
                case ORDINARY -> expand(
                        frontier, root, livePending, false, instanceId, sequence, reference, attribution);
                case MAX_FRONTIER -> buildMaximumFrontier(
                        frontier, root, instanceId, sequence, reference, attribution);
                case DEPTH -> buildDepthFrontier(
                        frontier, root, livePending, instanceId, sequence, reference, attribution);
                case LIFETIME -> buildLifetimeFrontier(
                        frontier, root, instanceId, sequence, reference, attribution);
            }
            if (profile == LineageProfile.DEPTH) {
                depthLineage = instanceId;
            }
            if (profile == LineageProfile.MAX_FRONTIER
                    && attribution instanceof NonPlayerRuntimeBudgetAttribution) {
                maximumFrontierLineage = instanceId;
            }
            if (frontier.size() != livePending) {
                throw new IllegalStateException("profile did not produce exact live frontier");
            }
            lineageOrder.add(instanceId);
            frontierByLineage.add(List.copyOf(frontier));
            liveFrontier.addAll(frontier);
            nextSequence++;
        }

        private void buildMaximumFrontier(
                List<RuntimeEvent> frontier,
                RuntimeEvent root,
                SkillInstanceId instanceId,
                RuntimeSkillInstanceSequence sequence,
                SkillReference reference,
                RuntimeBudgetAttribution attribution) {
            expand(frontier, root, 32, false, instanceId, sequence, reference, attribution);
            var parents = List.copyOf(frontier.subList(0, 8));
            var includeMaximumDelay = !maximumDelayAdded
                    && attribution instanceof NonPlayerRuntimeBudgetAttribution;
            for (var index = 0; index < parents.size(); index++) {
                expand(
                        frontier,
                        parents.get(index),
                        index < 7 ? 32 : 8,
                        includeMaximumDelay && index == 0,
                        instanceId,
                        sequence,
                        reference,
                        attribution);
            }
            maximumDelayAdded |= includeMaximumDelay;
        }

        private void buildDepthFrontier(
                List<RuntimeEvent> frontier,
                RuntimeEvent root,
                int livePending,
                SkillInstanceId instanceId,
                RuntimeSkillInstanceSequence sequence,
                SkillReference reference,
                RuntimeBudgetAttribution attribution) {
            var parent = root;
            for (var index = 0; index < 31; index++) {
                expand(frontier, parent, 1, false, instanceId, sequence, reference, attribution);
                parent = frontier.getFirst();
            }
            expand(
                    frontier,
                    parent,
                    livePending,
                    false,
                    instanceId,
                    sequence,
                    reference,
                    attribution);
        }

        private void buildLifetimeFrontier(
                List<RuntimeEvent> frontier,
                RuntimeEvent root,
                SkillInstanceId instanceId,
                RuntimeSkillInstanceSequence sequence,
                SkillReference reference,
                RuntimeBudgetAttribution attribution) {
            expand(frontier, root, 1, false, instanceId, sequence, reference, attribution);
            for (var expansion = 0; expansion < 255; expansion++) {
                expand(
                        frontier,
                        frontier.getFirst(),
                        2,
                        false,
                        instanceId,
                        sequence,
                        reference,
                        attribution);
            }
        }

        private void expand(
                List<RuntimeEvent> frontier,
                RuntimeEvent parent,
                int childCount,
                boolean includeMaximumDelay,
                SkillInstanceId instanceId,
                RuntimeSkillInstanceSequence sequence,
                SkillReference reference,
                RuntimeBudgetAttribution attribution) {
            if (!frontier.remove(parent)
                    || childCount <= 0
                    || childCount > limits.directChildrenPerEvent()) {
                throw new IllegalStateException("invalid frontier expansion");
            }
            terminalEventIds.add(parent.eventId());
            for (var childSequence = 1; childSequence <= childCount; childSequence++) {
                var delay = childSequence <= limits.zeroDelayChildrenPerEvent()
                        ? 0
                        : Math.min(1, limits.maximumDelayTicks());
                if (includeMaximumDelay && childSequence == childCount) {
                    delay = limits.maximumDelayTicks();
                }
                var child = childEvent(
                        instanceId,
                        sequence,
                        reference,
                        attribution,
                        parent,
                        childSequence,
                        delay);
                trace.add(child);
                frontier.add(child);
            }
        }

        GeneratedWorkload build() {
            if (lineageOrder.size() < 3) {
                throw new IllegalStateException("workload needs cancellation, break, and survivor lineages");
            }
            return new GeneratedWorkload(
                    List.copyOf(trace),
                    List.copyOf(liveFrontier),
                    Set.copyOf(terminalEventIds),
                    lineageOrder.getFirst(),
                    depthLineage,
                    maximumFrontierLineage,
                    lineageOrder.get(1),
                    lineageOrder.get(2),
                    frontierByLineage.getFirst().getLast().eventId());
        }

        private RuntimeEvent rootEvent(
                SkillInstanceId instanceId,
                RuntimeSkillInstanceSequence sequence,
                SkillReference reference,
                RuntimeBudgetAttribution attribution) {
            var eventId = nextEventId++;
            return new RuntimeEvent(
                    new EventId(eventId),
                    instanceId,
                    sequence,
                    new RuntimeCancellationToken(token, instanceId),
                    Optional.empty(),
                    reference,
                    (int) ((eventId - 1L) % MagicSafetyCeilings.MAX_NODES),
                    0,
                    1,
                    Math.addExact(1L, limits.maximumDeadlineHorizonTicks()),
                    0,
                    0,
                    RuntimeSchedulePersistence.MEMORY_ONLY,
                    attribution,
                    origin(attribution),
                    Optional.empty(),
                    new RootTriggerCause(EVENT_KIND),
                    NoRuntimeExecutionData.INSTANCE);
        }

        private RuntimeEvent childEvent(
                SkillInstanceId instanceId,
                RuntimeSkillInstanceSequence sequence,
                SkillReference reference,
                RuntimeBudgetAttribution attribution,
                RuntimeEvent parent,
                int childSequence,
                int delay) {
            var eventId = nextEventId++;
            var createdTick = parent.scheduledRuntimeTick();
            var scheduledTick = Math.addExact(createdTick, delay);
            var requestedDeadline = Math.addExact(
                    createdTick, limits.maximumDeadlineHorizonTicks());
            return new RuntimeEvent(
                    new EventId(eventId),
                    instanceId,
                    sequence,
                    new RuntimeCancellationToken(token, instanceId),
                    Optional.of(parent.eventId()),
                    reference,
                    (int) ((eventId - 1L) % MagicSafetyCeilings.MAX_NODES),
                    createdTick,
                    scheduledTick,
                    Math.min(parent.deadlineRuntimeTick(), requestedDeadline),
                    parent.depth() + 1,
                    childSequence,
                    RuntimeSchedulePersistence.MEMORY_ONLY,
                    attribution,
                    origin(attribution),
                    Optional.empty(),
                    new ChildTriggerCause(EVENT_KIND),
                    NoRuntimeExecutionData.INSTANCE);
        }

        private RuntimeOrigin origin(RuntimeBudgetAttribution attribution) {
            if (attribution instanceof PlayerRuntimeBudgetAttribution player) {
                return new PlayerOrigin(
                        token, net.minecraft.world.level.Level.OVERWORLD, player.playerId());
            }
            return new ServerOrigin(token);
        }
    }

    private static final class WorkloadModel {
        private final P5RuntimeLimits limits;
        private final RuntimeServerToken token;
        private final PriorityQueue<RuntimeEvent> queue =
                new PriorityQueue<>(SkillRuntimeService.EVENT_ORDER);
        private final Map<EventId, RuntimeEvent> index = new HashMap<>();
        private final Map<SkillInstanceId, LineageAccounting> lineages = new HashMap<>();
        private final Map<RuntimeBudgetAttribution, AttributionAccounting> attributions =
                new HashMap<>();
        private final Set<SkillReference> modeledLeaseReferences = new HashSet<>();
        private final RuntimeEvent[] deferred =
                new RuntimeEvent[MagicSafetyCeilings.MAX_BUDGET_DEFERRED_EVENTS];
        private final RuntimeEvent[] cleanupScratch =
                new RuntimeEvent[MagicSafetyCeilings.MAX_BREAKER_CLEANUP_SCRATCH_EVENTS];
        private final Set<Long> observedDelays = new HashSet<>();
        private int deferredCount;
        private int committedPending;
        private int reservedPending;
        private int currentReservationCount;
        private int cancellationAttempts;
        private int lineageCleanupScans;
        private int maximumObservedDepth;
        private int maximumObservedDirectChildren;
        private int maximumObservedZeroDelayChildren;
        private int maximumDelayEventCount;

        WorkloadModel(P5RuntimeLimits limits, RuntimeServerToken token) {
            this.limits = limits;
            this.token = token;
        }

        void admit(GeneratedWorkload generated) {
            var traceIndex = new HashMap<EventId, RuntimeEvent>();
            var parentChildren = new HashMap<EventId, Integer>();
            var parentZeroDelayChildren = new HashMap<EventId, Integer>();
            var childSequences = new HashMap<EventId, Set<Integer>>();
            var roots = new HashMap<SkillInstanceId, Integer>();

            for (var event : generated.trace()) {
                assertEquals(token, event.cancellationToken().serverSlotToken());
                assertTrue(traceIndex.put(event.eventId(), event) == null);
                var lineage = lineages.get(event.skillInstanceId());
                if (lineage == null) {
                    lineage = new LineageAccounting(
                            event.skillInstanceId(),
                            event.skillInstanceSequence(),
                            event.skillReference(),
                            event.budgetAttribution());
                    lineages.put(event.skillInstanceId(), lineage);
                    assertTrue(modeledLeaseReferences.add(event.skillReference()));
                    var attribution = attributions.computeIfAbsent(
                            event.budgetAttribution(), AttributionAccounting::new);
                    attribution.activeInstances++;
                }
                assertEquals(lineage.sequence, event.skillInstanceSequence());
                assertEquals(lineage.reference, event.skillReference());
                assertEquals(lineage.attribution, event.budgetAttribution());
                lineage.createdEvents++;
                if (generated.terminalEventIds().contains(event.eventId())) {
                    lineage.terminalEvents++;
                }
                var delay = Math.subtractExact(
                        event.scheduledRuntimeTick(), event.createdRuntimeTick());
                observedDelays.add(delay);
                if (limits.maximumDelayTicks() > 1
                        && delay == limits.maximumDelayTicks()) {
                    maximumDelayEventCount++;
                }
                maximumObservedDepth = Math.max(maximumObservedDepth, event.depth());

                if (event.parentEventId().isEmpty()) {
                    roots.merge(event.skillInstanceId(), 1, Math::addExact);
                    assertEquals(0, event.depth());
                    assertEquals(0, event.childSequence());
                    assertEquals(0, event.createdRuntimeTick());
                    assertEquals(1, event.scheduledRuntimeTick());
                    assertEquals(
                            Math.addExact(1L, limits.maximumDeadlineHorizonTicks()),
                            event.deadlineRuntimeTick());
                    assertInstanceOf(RootTriggerCause.class, event.triggerCause());
                } else {
                    var parent = traceIndex.get(event.parentEventId().orElseThrow());
                    assertTrue(parent != null);
                    assertEquals(parent.skillInstanceId(), event.skillInstanceId());
                    assertEquals(parent.depth() + 1, event.depth());
                    assertEquals(parent.scheduledRuntimeTick(), event.createdRuntimeTick());
                    assertEquals(
                            Math.addExact(event.createdRuntimeTick(), delay),
                            event.scheduledRuntimeTick());
                    assertEquals(
                            Math.min(
                                    parent.deadlineRuntimeTick(),
                                    Math.addExact(
                                            event.createdRuntimeTick(),
                                            limits.maximumDeadlineHorizonTicks())),
                            event.deadlineRuntimeTick());
                    assertTrue(generated.terminalEventIds().contains(parent.eventId()));
                    assertInstanceOf(ChildTriggerCause.class, event.triggerCause());
                    var direct = parentChildren.merge(parent.eventId(), 1, Math::addExact);
                    var sequences = childSequences.computeIfAbsent(
                            parent.eventId(), ignored -> new HashSet<>());
                    assertTrue(sequences.add(event.childSequence()));
                    maximumObservedDirectChildren = Math.max(
                            maximumObservedDirectChildren, direct);
                    if (delay == 0) {
                        var zeroDelay = parentZeroDelayChildren.merge(
                                parent.eventId(), 1, Math::addExact);
                        maximumObservedZeroDelayChildren = Math.max(
                                maximumObservedZeroDelayChildren, zeroDelay);
                    }
                }
            }

            var frontierIds = new HashSet<EventId>();
            for (var position = 0; position < generated.liveFrontier().size(); position++) {
                var event = generated.liveFrontier().get(position);
                assertSame(event, traceIndex.get(event.eventId()));
                assertTrue(frontierIds.add(event.eventId()));
                assertFalse(generated.terminalEventIds().contains(event.eventId()));
                assertTrue(index.put(event.eventId(), event) == null);
                var lineage = lineages.get(event.skillInstanceId());
                lineage.committedPending++;
                var attribution = attributions.get(event.budgetAttribution());
                attribution.committedPending++;
                committedPending++;
                if ((position & 1) == 0) {
                    queue.add(event);
                } else {
                    deferred[deferredCount++] = event;
                }
            }
            for (var event : generated.trace()) {
                var terminal = generated.terminalEventIds().contains(event.eventId());
                assertEquals(!terminal, frontierIds.contains(event.eventId()));
                if (terminal) {
                    assertTrue(parentChildren.getOrDefault(event.eventId(), 0) > 0);
                }
            }

            for (var entry : lineages.entrySet()) {
                var lineage = entry.getValue();
                assertEquals(1, roots.getOrDefault(entry.getKey(), 0));
                assertEquals(
                        lineage.createdEvents,
                        lineage.terminalEvents + lineage.committedPending);
                assertTrue(lineage.committedPending <= limits.pendingEventsPerSkillInstance());
            }

            populateAttributionCapacity();
            assertTrue(lineages.size() <= limits.activeSkillInstancesPerServer());
            assertTrue(committedPending <= limits.pendingEventsPerServer());
            for (var attribution : attributions.values()) {
                assertTrue(attribution.activeInstances
                        <= limits.activeSkillInstancesPerAttribution());
                assertTrue(attribution.committedPending
                        <= limits.pendingEventsPerAttribution());
            }
            for (var direct : parentChildren.values()) {
                assertTrue(direct <= limits.directChildrenPerEvent());
            }
            for (var zeroDelay : parentZeroDelayChildren.values()) {
                assertTrue(zeroDelay <= limits.zeroDelayChildrenPerEvent());
            }
            assertUniqueMembership();
            assertScratchClear();
        }

        private void populateAttributionCapacity() {
            var playerCapacity = limits.runtimeBudgetAttributionStatesPerServer() - 1;
            for (var index = 0; index < playerCapacity; index++) {
                attributions.computeIfAbsent(playerAttribution(index), AttributionAccounting::new);
            }
            attributions.computeIfAbsent(automationAttribution(), AttributionAccounting::new);
            assertEquals(limits.runtimeBudgetAttributionStatesPerServer(), attributions.size());
        }

        CancellationExercise exerciseCancellationBudget(
                SkillRuntimeService service, EventId exactEventId) {
            var cancelledEvents = 0;
            var notPending = 0;
            for (var attempt = 0; attempt < limits.cancellationsPerTick(); attempt++) {
                assertTrue(service.cancellationBudgetAvailable(
                        attempt, limits.cancellationsPerTick()));
                cancellationAttempts++;
                if (attempt == 0) {
                    var exact = index.get(exactEventId);
                    assertTrue(exact != null);
                    removeExactMembership(exact);
                    terminalize(exact);
                    cancelledEvents++;
                } else {
                    notPending++;
                }
            }
            assertFalse(service.cancellationBudgetAvailable(
                    cancellationAttempts, limits.cancellationsPerTick()));
            assertInstanceOf(
                    RuntimeCancellationResult.WrongServer.class,
                    new RuntimeCancellationResult.WrongServer());
            assertEquals(
                    limits.cancellationsPerTick(),
                    new RuntimeCancellationResult.CancellationBudgetExceeded(
                                    limits.cancellationsPerTick())
                            .maximum());
            assertUniqueMembership();
            return new CancellationExercise(
                    cancellationAttempts, cancelledEvents, notPending);
        }

        BreakExercise breakSource(SkillInstanceId source) {
            var pendingBefore = pendingFor(source);
            var removedQueued = 0;
            var scratchCount = 0;
            while (!queue.isEmpty()) {
                var event = queue.remove();
                if (event.skillInstanceId().equals(source)) {
                    terminalize(event);
                    removedQueued++;
                } else {
                    cleanupScratch[scratchCount++] = event;
                }
            }
            for (var index = 0; index < scratchCount; index++) {
                var survivor = cleanupScratch[index];
                cleanupScratch[index] = null;
                queue.add(survivor);
            }

            var removedDeferred = 0;
            var write = 0;
            var oldCount = deferredCount;
            for (var read = 0; read < oldCount; read++) {
                var event = deferred[read];
                deferred[read] = null;
                if (event.skillInstanceId().equals(source)) {
                    terminalize(event);
                    removedDeferred++;
                } else {
                    deferred[write++] = event;
                }
            }
            deferredCount = write;
            assertEquals(pendingBefore, removedQueued + removedDeferred);
            assertFalse(lineages.containsKey(source));
            assertUniqueMembership();
            assertScratchClear();
            return new BreakExercise(
                    removedQueued, removedDeferred, removedQueued + removedDeferred);
        }

        DrainExercise drainToZero() {
            for (var index = 0; index < deferredCount; index++) {
                var event = deferred[index];
                deferred[index] = null;
                queue.add(event);
            }
            deferredCount = 0;
            assertUniqueMembership();

            RuntimeEvent previous = null;
            var digest = FNV_OFFSET_BASIS;
            var drained = 0;
            while (!queue.isEmpty()) {
                var event = queue.remove();
                if (previous != null) {
                    assertTrue(SkillRuntimeService.EVENT_ORDER.compare(previous, event) < 0);
                }
                digest = mix(digest, event.scheduledRuntimeTick());
                digest = mix(digest, event.eventId().value());
                digest = mix(digest, event.skillInstanceSequence().value());
                digest = mix(digest, event.nodeIndex());
                digest = mix(digest, event.childSequence());
                terminalize(event);
                previous = event;
                drained++;
            }

            assertAll(
                    () -> assertTrue(index.isEmpty()),
                    () -> assertTrue(lineages.isEmpty()),
                    () -> assertTrue(modeledLeaseReferences.isEmpty()),
                    () -> assertEquals(0, committedPending),
                    () -> assertEquals(0, reservedPending),
                    () -> assertEquals(0, currentReservationCount),
                    () -> assertTrue(lineageCleanupScans <= limits.activeSkillInstancesPerServer()));
            for (var attribution : attributions.values()) {
                assertEquals(0, attribution.activeInstances);
                assertEquals(0, attribution.committedPending);
                assertEquals(0, attribution.reservedPending);
            }
            attributions.clear();
            observedDelays.clear();
            var completedScans = lineageCleanupScans;
            var completedCancellations = cancellationAttempts;
            lineageCleanupScans = 0;
            cancellationAttempts = 0;
            maximumObservedDepth = 0;
            maximumObservedDirectChildren = 0;
            maximumObservedZeroDelayChildren = 0;
            maximumDelayEventCount = 0;
            return new DrainExercise(
                    digest, drained, completedScans, completedCancellations);
        }

        private void removeExactMembership(RuntimeEvent exact) {
            if (queue.remove(exact)) {
                return;
            }
            var found = -1;
            for (var index = 0; index < deferredCount; index++) {
                if (deferred[index] == exact) {
                    found = index;
                    break;
                }
            }
            assertTrue(found >= 0);
            var moved = deferredCount - found - 1;
            if (moved > 0) {
                System.arraycopy(deferred, found + 1, deferred, found, moved);
            }
            deferred[--deferredCount] = null;
        }

        private void terminalize(RuntimeEvent event) {
            assertSame(event, index.remove(event.eventId()));
            var lineage = lineages.get(event.skillInstanceId());
            assertTrue(lineage != null && lineage.committedPending > 0);
            var attribution = attributions.get(lineage.attribution);
            assertTrue(attribution != null && attribution.committedPending > 0);
            lineage.committedPending--;
            lineage.terminalEvents++;
            attribution.committedPending--;
            committedPending--;
            if (lineage.committedPending == 0) {
                assertSame(lineage, lineages.remove(lineage.id));
                assertTrue(modeledLeaseReferences.remove(lineage.reference));
                attribution.activeInstances--;
                lineageCleanupScans++;
            }
        }

        int pendingFor(SkillInstanceId instanceId) {
            var lineage = lineages.get(instanceId);
            return lineage == null ? 0 : lineage.committedPending;
        }

        int pendingFor(RuntimeBudgetAttribution attribution) {
            var accounting = attributions.get(attribution);
            return accounting == null ? 0 : accounting.committedPending;
        }

        int activeFor(RuntimeBudgetAttribution attribution) {
            var accounting = attributions.get(attribution);
            return accounting == null ? 0 : accounting.activeInstances;
        }

        int createdFor(SkillInstanceId instanceId) {
            return lineages.get(instanceId).createdEvents;
        }

        int descendantsFor(SkillInstanceId instanceId) {
            return Math.decrementExact(createdFor(instanceId));
        }

        int terminalFor(SkillInstanceId instanceId) {
            return lineages.get(instanceId).terminalEvents;
        }

        void assertUniqueMembership() {
            Set<RuntimeEvent> memberships = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var event : queue) {
                assertTrue(memberships.add(event));
            }
            for (var position = 0; position < deferredCount; position++) {
                assertTrue(memberships.add(deferred[position]));
            }
            assertEquals(index.size(), memberships.size());
            for (var event : index.values()) {
                assertTrue(memberships.contains(event));
            }
            for (var position = deferredCount; position < deferred.length; position++) {
                assertNull(deferred[position]);
            }
        }

        void assertScratchClear() {
            assertAllCellsNull(cleanupScratch);
        }

        void assertCompletelyZero() {
            assertAll(
                    () -> assertTrue(queue.isEmpty()),
                    () -> assertTrue(index.isEmpty()),
                    () -> assertTrue(lineages.isEmpty()),
                    () -> assertTrue(attributions.isEmpty()),
                    () -> assertTrue(modeledLeaseReferences.isEmpty()),
                    () -> assertTrue(observedDelays.isEmpty()),
                    () -> assertEquals(0, deferredCount),
                    () -> assertEquals(0, committedPending),
                    () -> assertEquals(0, reservedPending),
                    () -> assertEquals(0, currentReservationCount),
                    () -> assertEquals(0, cancellationAttempts),
                    () -> assertEquals(0, lineageCleanupScans),
                    () -> assertEquals(0, maximumObservedDepth),
                    () -> assertEquals(0, maximumObservedDirectChildren),
                    () -> assertEquals(0, maximumObservedZeroDelayChildren));
            assertEquals(0, maximumDelayEventCount);
            assertAllCellsNull(deferred);
            assertScratchClear();
        }
    }

    private static final class LineageAccounting {
        private final SkillInstanceId id;
        private final RuntimeSkillInstanceSequence sequence;
        private final SkillReference reference;
        private final RuntimeBudgetAttribution attribution;
        private int committedPending;
        private int createdEvents;
        private int terminalEvents;

        LineageAccounting(
                SkillInstanceId id,
                RuntimeSkillInstanceSequence sequence,
                SkillReference reference,
                RuntimeBudgetAttribution attribution) {
            this.id = id;
            this.sequence = sequence;
            this.reference = reference;
            this.attribution = attribution;
        }
    }

    private static final class AttributionAccounting {
        private final RuntimeBudgetAttribution attribution;
        private int activeInstances;
        private int committedPending;
        private int reservedPending;

        AttributionAccounting(RuntimeBudgetAttribution attribution) {
            this.attribution = attribution;
        }
    }

    private record GeneratedWorkload(
            List<RuntimeEvent> trace,
            List<RuntimeEvent> liveFrontier,
            Set<EventId> terminalEventIds,
            SkillInstanceId lifetimeLineage,
            SkillInstanceId depthLineage,
            SkillInstanceId maximumFrontierLineage,
            SkillInstanceId breakLineage,
            SkillInstanceId unrelatedLineage,
            EventId cancellationEventId) {}

    private record CancellationExercise(int attempts, int cancelledEvents, int notPending) {}

    private record BreakExercise(int removedQueued, int removedDeferred, int removedTotal) {}

    private record DrainExercise(
            long digest,
            int drainedEvents,
            int lineageCleanupScans,
            int cancellationAttempts) {}

    private static long mix(long digest, long value) {
        var mixed = digest;
        for (var shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= value >>> shift & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private static ServiceFixture serviceFixture() {
        var bus = BusBuilder.builder().build();
        var attachments = PlayerSkillAttachmentServiceTestSupport.createService();
        var store = SkillDefinitionStoreService.registerOn(bus, attachments);
        var policy = SkillSubmissionPolicyProvider.defaults();
        return new ServiceFixture(
                SkillRuntimeService.create(bus, store, policy), store, policy);
    }

    private record ServiceFixture(
            SkillRuntimeService nominal,
            SkillDefinitionStoreService store,
            SkillSubmissionPolicyProvider policy) {
        SkillRuntimeService with(
                RuntimeReferenceResolver resolver, RuntimeExecutionPort port) {
            return new SkillRuntimeService(
                    store, policy, new P5RuntimeProjector(), resolver, port);
        }
    }

    private record DeterministicMissingReferenceResolver(
            RuntimeReferenceResolutionOutcome outcome)
            implements RuntimeReferenceResolver {
        DeterministicMissingReferenceResolver {
            if (outcome == null
                    || outcome instanceof RuntimeReferenceResolutionOutcome.Resolved) {
                throw new IllegalArgumentException("expected a deterministic failure outcome");
            }
        }

        @Override
        public RuntimeReferenceResolutionOutcome resolve(
                MinecraftServer server, RuntimeEvent event) {
            if (server == null || event == null) {
                throw new NullPointerException("resolver arguments");
            }
            return outcome;
        }
    }

    private enum DeterministicSuccessExecutionPort implements RuntimeExecutionPort {
        INSTANCE;

        RuntimeExecutionBatch fixedBatch() {
            return new RuntimeExecutionBatch(
                    new RuntimePortOutcome.Completed(), RuntimeChildPlan.EMPTY);
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            if (event == null || context == null) {
                throw new NullPointerException("execution arguments");
            }
            return fixedBatch();
        }
    }

    private enum DeterministicRejectedExecutionPort implements RuntimeExecutionPort {
        INSTANCE;

        RuntimeExecutionBatch fixedBatch() {
            return new RuntimeExecutionBatch(
                    new RuntimePortOutcome.Rejected(
                            RuntimePortRejectionReason.PORT_UNAVAILABLE),
                    RuntimeChildPlan.EMPTY);
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            if (event == null || context == null) {
                throw new NullPointerException("execution arguments");
            }
            return fixedBatch();
        }
    }

    private record DeterministicRuntimeExceptionReferenceResolver(
            IllegalArgumentException primary)
            implements RuntimeReferenceResolver {
        DeterministicRuntimeExceptionReferenceResolver {
            if (primary == null) {
                throw new NullPointerException("primary");
            }
        }

        @Override
        public RuntimeReferenceResolutionOutcome resolve(
                MinecraftServer server, RuntimeEvent event) {
            throw primary;
        }
    }

    private record DeterministicErrorReferenceResolver(AssertionError primary)
            implements RuntimeReferenceResolver {
        DeterministicErrorReferenceResolver {
            if (primary == null) {
                throw new NullPointerException("primary");
            }
        }

        @Override
        public RuntimeReferenceResolutionOutcome resolve(
                MinecraftServer server, RuntimeEvent event) {
            throw primary;
        }
    }

    private record DeterministicOutOfMemoryReferenceResolver(OutOfMemoryError primary)
            implements RuntimeReferenceResolver {
        DeterministicOutOfMemoryReferenceResolver {
            if (primary == null) {
                throw new NullPointerException("primary");
            }
        }

        @Override
        public RuntimeReferenceResolutionOutcome resolve(
                MinecraftServer server, RuntimeEvent event) {
            throw primary;
        }
    }

    private record DeterministicRuntimeExceptionExecutionPort(
            IllegalStateException primary)
            implements RuntimeExecutionPort {
        DeterministicRuntimeExceptionExecutionPort {
            if (primary == null) {
                throw new NullPointerException("primary");
            }
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            throw primary;
        }
    }

    private record DeterministicErrorExecutionPort(AssertionError primary)
            implements RuntimeExecutionPort {
        DeterministicErrorExecutionPort {
            if (primary == null) {
                throw new NullPointerException("primary");
            }
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            throw primary;
        }
    }

    private record DeterministicOutOfMemoryExecutionPort(OutOfMemoryError primary)
            implements RuntimeExecutionPort {
        DeterministicOutOfMemoryExecutionPort {
            if (primary == null) {
                throw new NullPointerException("primary");
            }
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            throw primary;
        }
    }
}
