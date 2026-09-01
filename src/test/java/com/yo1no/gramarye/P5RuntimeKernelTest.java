package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Focused pure-Java execution-kernel boundary tests; no Minecraft server is constructed. */
final class P5RuntimeKernelTest {
    private static final RuntimeServerToken SERVER_TOKEN = new RuntimeServerToken(7);
    private static final SkillReference SKILL_REFERENCE = new SkillReference(
            new SkillId(new UUID(17, 23)), new SkillRevision(3));
    private static final TriggerEventKind EVENT_KIND = new TriggerEventKind(
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "p5_kernel_test"));
    private static final ResourceLocation PROJECTOR_TRIGGER_ID =
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "p5_projector_trigger");
    private static final ResourceLocation PROJECTOR_ACTION_ID =
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "p5_projector_action");

    @Test
    void runtimeProjectorUsesTypedUnavailabilityForReferenceMismatchAndRejection() {
        var projector = new P5RuntimeProjector();
        var context = new ValidationContext(MagicPolicyLimits.DEFAULTS);
        var rejectedDocument = new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                SKILL_REFERENCE.skillId(),
                SKILL_REFERENCE.revision(),
                List.of(),
                AppearanceDocument.Default.INSTANCE);
        var mismatchedDocument = new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                SKILL_REFERENCE.skillId(),
                new SkillRevision(SKILL_REFERENCE.revision().value() + 1),
                List.of(),
                AppearanceDocument.Default.INSTANCE);

        assertAll(
                () -> assertSame(
                        P5RuntimeProjector.Projection.Unavailable.INSTANCE,
                        projector.project(SKILL_REFERENCE, mismatchedDocument, context)),
                () -> assertSame(
                        P5RuntimeProjector.Projection.Unavailable.INSTANCE,
                        projector.project(SKILL_REFERENCE, rejectedDocument, context)));
    }

    @Test
    void runtimeProjectorProducesAvailableFromTheRealInjectedP3Pipeline() {
        var triggerDescriptor = new ProjectorTriggerDescriptor();
        var actionDescriptor = new ProjectorActionDescriptor();
        var projector = new P5RuntimeProjector(
                new SkillCandidateResolver(
                        new SingleTriggerLookup(PROJECTOR_TRIGGER_ID, triggerDescriptor),
                        new SingleActionLookup(PROJECTOR_ACTION_ID, actionDescriptor)),
                new SkillValidationAnalyzer(
                        new NodeProjectionResolver(), ProfileAvailabilityView.unknown()),
                new SkillDefinitionProjector());
        var context = new ValidationContext(MagicPolicyLimits.DEFAULTS);

        var available = assertInstanceOf(
                P5RuntimeProjector.Projection.Available.class,
                projector.project(SKILL_REFERENCE, projectableDocument(), context));

        assertAll(
                () -> assertEquals(SKILL_REFERENCE, available.definition().reference()),
                () -> assertEquals(1, available.definition().nodes().size()),
                () -> assertEquals(0, available.definition().nodes().getFirst().nodeIndex()),
                () -> assertSame(
                        triggerDescriptor,
                        available.definition().nodes().getFirst().trigger().descriptor()),
                () -> assertSame(
                        actionDescriptor,
                        available.definition().nodes().getFirst().action().descriptor()));
    }

    @Test
    void loadedReferenceFailureClassificationIsClosedAndPreservesExactReason() {
        var missingReasons = List.of(
                RuntimeReferenceFailureReason.MISSING,
                RuntimeReferenceFailureReason.MISSING_OR_UNLOADED,
                RuntimeReferenceFailureReason.UNLOADED);
        for (var reason : missingReasons) {
            var source = assertInstanceOf(
                    RuntimeReferenceResolutionOutcome.SourceMissing.class,
                    P5LoadedReferenceResolver.classifySourceFailure(reason));
            var target = assertInstanceOf(
                    RuntimeReferenceResolutionOutcome.TargetMissing.class,
                    P5LoadedReferenceResolver.classifyTargetFailure(reason));
            assertEquals(reason, source.reason());
            assertEquals(reason, target.reason());
        }

        var invalidReasons = List.of(
                RuntimeReferenceFailureReason.WRONG_SERVER,
                RuntimeReferenceFailureReason.DIMENSION_UNAVAILABLE,
                RuntimeReferenceFailureReason.WRONG_DIMENSION,
                RuntimeReferenceFailureReason.TYPE_MISMATCH);
        for (var reason : invalidReasons) {
            var source = assertInstanceOf(
                    RuntimeReferenceResolutionOutcome.InvalidRuntimeReference.class,
                    P5LoadedReferenceResolver.classifySourceFailure(reason));
            var target = assertInstanceOf(
                    RuntimeReferenceResolutionOutcome.InvalidRuntimeReference.class,
                    P5LoadedReferenceResolver.classifyTargetFailure(reason));
            assertEquals(reason, source.reason());
            assertEquals(reason, target.reason());
        }
    }

    @Test
    void eventPriorityQueueUsesTheExactFiveKeyTotalOrder() {
        var scheduled = childEvent(99, 9, 4, 9, 9, SERVER_TOKEN);
        var eventId = childEvent(1, 9, 5, 9, 9, SERVER_TOKEN);
        var instanceSequence = childEvent(2, 1, 5, 9, 9, SERVER_TOKEN);
        var nodeIndex = childEvent(2, 2, 5, 0, 9, SERVER_TOKEN);
        var firstChild = childEvent(2, 2, 5, 1, 1, SERVER_TOKEN);
        var secondChild = childEvent(2, 2, 5, 1, 2, SERVER_TOKEN);

        assertAll(
                () -> assertTrue(SkillRuntimeService.EVENT_ORDER.compare(scheduled, eventId) < 0),
                () -> assertTrue(SkillRuntimeService.EVENT_ORDER.compare(eventId, instanceSequence) < 0),
                () -> assertTrue(SkillRuntimeService.EVENT_ORDER.compare(instanceSequence, nodeIndex) < 0),
                () -> assertTrue(SkillRuntimeService.EVENT_ORDER.compare(nodeIndex, firstChild) < 0),
                () -> assertTrue(SkillRuntimeService.EVENT_ORDER.compare(firstChild, secondChild) < 0),
                () -> assertEquals(0, SkillRuntimeService.EVENT_ORDER.compare(
                        firstChild,
                        childEvent(2, 2, 5, 1, 1, SERVER_TOKEN))));

        var queue = new PriorityQueue<RuntimeEvent>(SkillRuntimeService.EVENT_ORDER);
        List.of(secondChild, eventId, firstChild, scheduled, nodeIndex, instanceSequence)
                .forEach(queue::add);
        var actual = new ArrayList<RuntimeEvent>();
        while (!queue.isEmpty()) {
            actual.add(queue.remove());
        }
        assertEquals(
                List.of(scheduled, eventId, instanceSequence, nodeIndex, firstChild, secondChild),
                actual);

        var expected = List.of(
                scheduled, eventId, instanceSequence, nodeIndex, firstChild, secondChild);
        for (var seed = 0; seed < 100; seed++) {
            var randomized = new ArrayList<>(expected);
            Collections.shuffle(randomized, new java.util.Random(seed));
            var randomizedQueue = new PriorityQueue<RuntimeEvent>(
                    SkillRuntimeService.EVENT_ORDER);
            randomized.forEach(randomizedQueue::add);
            var trace = new ArrayList<RuntimeEvent>();
            while (!randomizedQueue.isEmpty()) {
                trace.add(randomizedQueue.remove());
            }
            assertEquals(expected, trace, "five-key trace changed for insertion seed " + seed);
        }
    }

    @Test
    void rootAndChildEnvelopesUseCheckedRelativeTicksAndInclusiveDeadlines() {
        var rootSchedule = new RuntimeScheduleSpec(
                5, 9, RuntimeSchedulePersistence.MEMORY_ONLY);
        var rootBase = Math.addExact(41L, 1L);
        var rootScheduled = Math.addExact(rootBase, rootSchedule.delayTicks());
        var rootDeadline = Math.addExact(rootBase, rootSchedule.deadlineHorizonTicks());
        var root = rootEvent(1, 41, rootScheduled, rootDeadline, SERVER_TOKEN);

        var childCurrentTick = rootScheduled;
        var childScheduled = Math.addExact(childCurrentTick, 0L);
        var requestedChildDeadline = Math.addExact(childCurrentTick, 20L);
        var childDeadline = Math.min(root.deadlineRuntimeTick(), requestedChildDeadline);
        var child = event(
                2,
                1,
                childCurrentTick,
                childScheduled,
                childDeadline,
                1,
                1,
                1,
                SERVER_TOKEN);

        assertAll(
                () -> assertEquals(42, rootBase),
                () -> assertEquals(47, root.scheduledRuntimeTick()),
                () -> assertEquals(51, root.deadlineRuntimeTick()),
                () -> assertEquals(childCurrentTick, child.createdRuntimeTick()),
                () -> assertEquals(childCurrentTick, child.scheduledRuntimeTick()),
                () -> assertEquals(root.deadlineRuntimeTick(), child.deadlineRuntimeTick()),
                () -> new RuntimeExecutionOutcome.DeadlineExpired(rootDeadline, rootDeadline + 1),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RuntimeExecutionOutcome.DeadlineExpired(
                                rootDeadline, rootDeadline)),
                () -> assertThrows(
                        ArithmeticException.class,
                        () -> Math.addExact(Long.MAX_VALUE, 1L)));
    }

    @Test
    void zeroDelayWorkReentersThePriorityQueueWithoutRecursiveExecution() throws Exception {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        slot.runtimeTick = 10;
        var parent = childEvent(1, 1, 10, 0, 1, SERVER_TOKEN);
        var zeroDelayChild = childEvent(2, 1, 10, 1, 2, SERVER_TOKEN);
        slot.queue.add(parent);
        slot.deferred[0] = zeroDelayChild;
        slot.deferredCount = 1;

        SkillRuntimeService.finishDeferred(slot);

        assertAll(
                () -> assertEquals(0, slot.deferredCount),
                () -> assertNull(slot.deferred[0]),
                () -> assertSame(parent, slot.queue.remove()),
                () -> assertSame(zeroDelayChild, slot.queue.remove()),
                () -> assertNull(slot.currentEvent),
                () -> assertEquals(0, slot.executionsThisTick),
                () -> assertFalse(slot.dispatching));
    }

    @Test
    void executionPendingAndCancellationBoundariesAreExactAndTyped() {
        var limits = defaultLimits();
        var budget = new RuntimeExecutionBudget(
                limits.directChildrenPerEvent(),
                limits.zeroDelayChildrenPerEvent(),
                limits.descendantsPerSkillInstance(),
                limits.maximumDepth(),
                limits.maximumDelayTicks(),
                limits.maximumDeadlineHorizonTicks(),
                limits.pendingEventsPerSkillInstance() - 1,
                limits.pendingEventsPerAttribution() - 1,
                limits.pendingEventsPerServer() - 1);

        assertAll(
                () -> assertEquals(32, budget.directChildCapacity()),
                () -> assertEquals(16, budget.zeroDelayChildCapacity()),
                () -> new RuntimeAdmissionResult.ActiveLineageCapacityExceeded(128, 128),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RuntimeAdmissionResult.ActiveLineageCapacityExceeded(127, 128)),
                () -> new RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded(32, 32),
                () -> new RuntimeCancellationResult.CancellationBudgetExceeded(128),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RuntimeCancellationResult.CancellationBudgetExceeded(129)),
                () -> new RuntimeCancellationResult.CancelledSkillInstance(256),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RuntimeCancellationResult.CancelledSkillInstance(257)),
                () -> new RuntimeCancellationResult.CancellationRequested(255),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new RuntimeCancellationResult.CancellationRequested(256)));
    }

    @Test
    void exactQueuedOrDeferredRemovalModelsTheCancellationMembershipBoundary()
            throws Exception {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        var queued = childEvent(1, 1, 1, 0, 1, SERVER_TOKEN);
        var deferred = childEvent(2, 1, 1, 0, 2, SERVER_TOKEN);
        var survivor = childEvent(3, 2, 1, 0, 3, SERVER_TOKEN);
        slot.queue.add(queued);
        assertTrue(SkillRuntimeService.removeExactQueuedOrDeferred(slot, queued));
        assertFalse(SkillRuntimeService.removeExactQueuedOrDeferred(slot, queued));

        slot.deferred[0] = deferred;
        slot.deferred[1] = survivor;
        slot.deferredCount = 2;
        assertTrue(SkillRuntimeService.removeExactQueuedOrDeferred(slot, deferred));
        assertAll(
                () -> assertEquals(1, slot.deferredCount),
                () -> assertSame(survivor, slot.deferred[0]),
                () -> assertNull(slot.deferred[1]));
    }

    @Test
    void breakerSummariesEnforceProjectedOverflowAndBoundedCleanup() throws Exception {
        var inFlight = new RuntimeCircuitBreakerSummary(
                RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
                256,
                1,
                256,
                255,
                true);
        var execution = new RuntimeExecutionOutcome.CircuitBroken(inFlight);
        assertSame(inFlight, execution.summary());
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
                        RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
                        255,
                        1,
                        256,
                        0,
                        false));

        var rootSummary = new RuntimeCircuitBreakerSummary(
                RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                4_096,
                1,
                4_096,
                0,
                false);
        new RuntimeAdmissionResult.CircuitBroken(rootSummary);
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        SkillInstanceId last = null;
        for (var index = 0; index <= MagicSafetyCeilings.MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER;
                index++) {
            last = new SkillInstanceId(new UUID(31, index + 1L));
            SkillRuntimeService.recordBreaker(
                    slot,
                    last,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    rootSummary);
        }

        assertAll(
                () -> assertEquals(1, slot.diagnostics.breakerWriteIndex),
                () -> assertEquals(257, slot.diagnostics.breakerTotals[
                        RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED.ordinal()]),
                () -> assertEquals(256, slot.diagnostics.breakerRing.length),
                () -> assertEquals(new SkillInstanceId(new UUID(31, 257)),
                        slot.diagnostics.breakerRing[0].instanceId()));
    }

    @Test
    void childPlansAreAllOrNoneAtHardAndLowerEffectiveBoundaries() {
        var child = childSpec(SERVER_TOKEN, 1, 0, 1);
        var exactHard = new RuntimeChildPlan(Collections.nCopies(
                RuntimeChildPlan.PHYSICAL_MAXIMUM, child));
        assertEquals(RuntimeChildPlan.PHYSICAL_MAXIMUM, exactHard.children().size());

        var hardFailure = assertThrows(
                RuntimeKernelException.class,
                () -> new RuntimeChildPlan(Collections.nCopies(
                        RuntimeChildPlan.PHYSICAL_MAXIMUM + 1, child)));
        assertEquals(
                RuntimeKernelException.Code.CHILD_PLAN_HARD_CAPACITY_EXCEEDED,
                hardFailure.code());

        var lowerEffectivePlan = new RuntimeChildPlan(Collections.nCopies(5, child));
        var lowerEffectiveBudget = new RuntimeExecutionBudget(
                4, 2, 8, 1, 20, 20, 8, 8, 8);
        var rejection = new RuntimeExecutionOutcome.BudgetRejected(
                RuntimeBudgetRejectionReason.DIRECT_CHILD_LIMIT_EXCEEDED);
        assertAll(
                () -> assertEquals(5, lowerEffectivePlan.children().size()),
                () -> assertEquals(4, lowerEffectiveBudget.directChildCapacity()),
                () -> assertEquals(
                        RuntimeBudgetRejectionReason.DIRECT_CHILD_LIMIT_EXCEEDED,
                        rejection.reason()));
    }

    @Test
    void staleSlotTokensAreStructuralKernelFailuresBeforeDispatch() throws Exception {
        var staleToken = new RuntimeServerToken(SERVER_TOKEN.value() - 1);
        var event = childEvent(1, 1, 1, 0, 1, staleToken);
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        slot.eventIndex.put(event.eventId(), event);

        var primary = assertThrows(
                RuntimeKernelException.class,
                () -> SkillRuntimeService.verifyQueuedIdentity(slot, event));
        assertAll(
                () -> assertEquals(
                        RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT,
                        primary.code()),
                () -> assertEquals(primary.code().name(), primary.getMessage()));
    }

    @Test
    void runtimeExceptionFaultCleanupClearsTheWholeSlotBeforeRethrowBoundary() {
        var staleToken = new RuntimeServerToken(SERVER_TOKEN.value() - 1);
        var event = childEvent(1, 1, 1, 0, 1, staleToken);
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        slot.runtimeTick = 1;
        slot.queue.add(event);
        slot.eventIndex.put(event.eventId(), event);
        slot.committedPending = 1;
        slot.executionsThisTick = 3;
        slot.diagnostics.executionAttemptsThisTick = 4;
        SkillRuntimeService.enterFaultAfterRuntimeException(slot);

        assertAll(
                () -> assertEquals(ServerSlot.State.FAULTED, slot.state),
                () -> assertFalse(slot.dispatching),
                () -> assertTrue(slot.queue.isEmpty()),
                () -> assertTrue(slot.eventIndex.isEmpty()),
                () -> assertEquals(0, slot.committedPending),
                () -> assertEquals(3, slot.executionsThisTick),
                () -> assertEquals(4, slot.diagnostics.executionAttemptsThisTick));
    }

    @Test
    void expiryObservationRecomputesExactSchedulingLag() {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        slot.runtimeTick = 12;
        var expired = event(1, 1, 5, 5, 10, 0, 1, 1, SERVER_TOKEN);

        SkillRuntimeService.observeExpiry(slot, expired);

        assertAll(
                () -> assertEquals(1, slot.diagnostics.deadlineExpiredEventsThisTick),
                () -> assertEquals(1, slot.diagnostics.deadlineExpiredEventTotal),
                () -> assertEquals(2, slot.diagnostics.maximumDeadlineLatenessTicksThisTick),
                () -> assertEquals(7, slot.diagnostics.maximumLagTicksThisTick));
    }

    @Test
    void prospectivePlayerBreakerRetainsItsBoundedPlayerDiagnostic() {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        var player = new RuntimePlayerId(new UUID(73, 79));
        var prospectiveInstance = new SkillInstanceId(new UUID(83, 89));
        var summary = new RuntimeCircuitBreakerSummary(
                RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED,
                1_024,
                1,
                1_024,
                0,
                false);

        SkillRuntimeService.recordBreaker(
                slot,
                prospectiveInstance,
                Optional.empty(),
                Optional.empty(),
                Optional.of(player),
                summary);

        assertAll(
                () -> assertEquals(1, slot.diagnostics.breakerWriteIndex),
                () -> assertEquals(Optional.of(player),
                        slot.diagnostics.breakerRing[0].playerId()),
                () -> assertEquals(prospectiveInstance,
                        slot.diagnostics.breakerRing[0].instanceId()));
    }

    @Test
    void enteringStoppingClearsDiagnosticsAndRepeatedStoppingCannotRepopulateThem() {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        var instance = new SkillInstanceId(new UUID(97, 101));
        var summary = new RuntimeCircuitBreakerSummary(
                RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                4_096,
                1,
                4_096,
                0,
                false);
        SkillRuntimeService.recordBreaker(
                slot,
                instance,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                summary);
        slot.diagnostics.typedOutcomesThisTick = 5;
        slot.diagnostics.maximumLagTicksThisTick = 17;
        SkillRuntimeService.enterStopping(slot);
        SkillRuntimeService.enterStopping(slot);

        assertAll(
                () -> assertEquals(ServerSlot.State.STOPPING, slot.state),
                () -> assertEquals(0, slot.diagnostics.breakerWriteIndex),
                () -> assertTrue(Arrays.stream(slot.diagnostics.breakerRing)
                        .allMatch(value -> value == null)),
                () -> assertTrue(Arrays.stream(slot.diagnostics.breakerTotals)
                        .allMatch(value -> value == 0)),
                () -> assertEquals(0, slot.diagnostics.typedOutcomesThisTick),
                () -> assertEquals(0, slot.diagnostics.maximumLagTicksThisTick));
    }

    @Test
    void slotPhysicalStateAndErrorCleanupStayFixedAndBounded() throws Exception {
        var limits = defaultLimits();
        var slot = new ServerSlot(SERVER_TOKEN, limits);
        var event = childEvent(1, 1, 1, 0, 1, SERVER_TOKEN);

        assertAll(
                () -> assertEquals(ServerSlot.State.RUNNING, slot.state),
                () -> assertSame(SERVER_TOKEN, slot.token),
                () -> assertSame(limits, slot.limits),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_BUDGET_DEFERRED_EVENTS,
                        slot.deferred.length),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_BREAKER_CLEANUP_SCRATCH_EVENTS,
                        slot.cleanupScratch.length),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER,
                        slot.diagnostics.breakerRing.length),
                () -> assertEquals(RuntimeCircuitBreakReason.values().length,
                        slot.diagnostics.breakerTotals.length),
                () -> assertSame(SkillRuntimeService.EVENT_ORDER, slot.queue.comparator()),
                () -> assertTrue(slot.queue.isEmpty()),
                () -> assertTrue(slot.eventIndex.isEmpty()),
                () -> assertTrue(slot.instances.isEmpty()),
                () -> assertTrue(slot.attributions.isEmpty()),
                () -> assertTrue(slot.leases.isEmpty()));

        slot.queue.add(event);
        slot.eventIndex.put(event.eventId(), event);
        slot.deferred[0] = event;
        slot.cleanupScratch[0] = event;
        slot.deferredCount = 1;
        slot.currentEvent = event;
        slot.currentReservationCount = 1;
        slot.currentReservationOwner = event.skillInstanceId();
        slot.committedPending = 1;
        slot.reservedPending = 1;
        SkillRuntimeService.clearSlotAfterError(slot);

        assertAll(
                () -> assertTrue(slot.queue.isEmpty()),
                () -> assertTrue(slot.eventIndex.isEmpty()),
                () -> assertNull(slot.deferred[0]),
                () -> assertNull(slot.cleanupScratch[0]),
                () -> assertEquals(0, slot.deferredCount),
                () -> assertNull(slot.currentEvent),
                () -> assertEquals(0, slot.currentReservationCount),
                () -> assertNull(slot.currentReservationOwner),
                () -> assertEquals(0, slot.committedPending),
                () -> assertEquals(0, slot.reservedPending));
    }

    @Test
    void checkedPositiveHighWaterSuccessorIsFreshMonotonicAndNeverWraps() {
        assertAll(
                () -> assertEquals(1L,
                        SkillRuntimeService.checkedPositiveSuccessor(0).orElseThrow()),
                () -> assertEquals(42L,
                        SkillRuntimeService.checkedPositiveSuccessor(41).orElseThrow()),
                () -> assertEquals(Long.MAX_VALUE,
                        SkillRuntimeService.checkedPositiveSuccessor(Long.MAX_VALUE - 1)
                                .orElseThrow()),
                () -> assertTrue(SkillRuntimeService.checkedPositiveSuccessor(Long.MAX_VALUE)
                        .isEmpty()),
                () -> assertTrue(SkillRuntimeService.checkedPositiveSuccessor(-1).isEmpty()));

        long highWater = 0;
        var issued = new java.util.HashSet<Long>();
        for (var index = 0; index < 1_000; index++) {
            highWater = SkillRuntimeService.checkedPositiveSuccessor(highWater).orElseThrow();
            assertTrue(issued.add(highWater));
        }
        assertEquals(1_000L, highWater);
        assertFalse(issued.contains(1_001L));
    }

    @Test
    void tickAdvanceResetAndExhaustionPreserveQueuedKeys() {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        var queued = childEvent(7, 3, 19, 4, 2, SERVER_TOKEN);
        var zeroReferenceAttribution = new NonPlayerRuntimeBudgetAttribution(
                SERVER_TOKEN, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION);
        var zeroReferenceState = new ServerSlot.AttributionState(zeroReferenceAttribution);
        zeroReferenceState.executionEpoch = 19;
        slot.attributions.put(zeroReferenceAttribution, zeroReferenceState);
        slot.queue.add(queued);
        slot.runtimeTick = 18;
        slot.rootAdmissionsThisTick = 2;
        slot.executionsThisTick = 3;
        slot.cancellationsThisTick = 4;
        slot.diagnostics.executionAttemptsThisTick = 5;
        slot.diagnostics.deadlineExpiredEventTotal = 6;

        assertEquals(RuntimeTickAdvanceResult.ADVANCED,
                SkillRuntimeService.advanceRuntimeTick(slot));
        SkillRuntimeService.resetTickState(slot);

        assertAll(
                () -> assertEquals(19, slot.runtimeTick),
                () -> assertSame(queued, slot.queue.peek()),
                () -> assertEquals(19, queued.scheduledRuntimeTick()),
                () -> assertEquals(7, queued.eventId().value()),
                () -> assertEquals(0, slot.rootAdmissionsThisTick),
                () -> assertEquals(0, slot.executionsThisTick),
                () -> assertEquals(0, slot.cancellationsThisTick),
                () -> assertEquals(0, slot.diagnostics.executionAttemptsThisTick),
                () -> assertEquals(6, slot.diagnostics.deadlineExpiredEventTotal),
                () -> assertSame(zeroReferenceState,
                        slot.attributions.get(zeroReferenceAttribution)));

        assertEquals(RuntimeTickAdvanceResult.ADVANCED,
                SkillRuntimeService.advanceRuntimeTick(slot));
        SkillRuntimeService.resetTickState(slot);
        assertFalse(slot.attributions.containsKey(zeroReferenceAttribution),
                "zero-reference attribution must survive its tick and purge on the next tick");

        slot.runtimeTick = Long.MAX_VALUE;
        assertEquals(RuntimeTickAdvanceResult.EXHAUSTED,
                SkillRuntimeService.advanceRuntimeTick(slot));
        assertEquals(Long.MAX_VALUE, slot.runtimeTick);
    }

    @Test
    void childCanonicalizationUsesDelayNodeAndStableOriginalOrdinal() {
        var firstTie = childSpec(SERVER_TOKEN, 4, 2, 9);
        var secondTie = childSpec(SERVER_TOKEN, 4, 2, 10);
        var earlyNode = childSpec(SERVER_TOKEN, 1, 2, 9);
        var earliest = childSpec(SERVER_TOKEN, 9, 0, 9);
        var input = new ArrayList<>(List.of(firstTie, earliest, secondTie, earlyNode));
        Collections.shuffle(input, new java.util.Random(0x5A17));
        var firstOrdinal = input.indexOf(firstTie);
        var secondOrdinal = input.indexOf(secondTie);

        var ordered = SkillRuntimeService.canonicalize(input);

        assertAll(
                () -> assertSame(earliest, ordered.get(0)),
                () -> assertSame(earlyNode, ordered.get(1)),
                () -> assertTrue(ordered.indexOf(firstTie) < ordered.indexOf(secondTie)
                        == (firstOrdinal < secondOrdinal)),
                () -> assertEquals(input.size(), ordered.size()));

        var random = new java.util.Random(0xC0DEC0DEL);
        for (var round = 0; round < 100; round++) {
            var randomized = new ArrayList<RuntimeChildSpec>();
            for (var ordinal = 0; ordinal < 40; ordinal++) {
                randomized.add(childSpec(
                        SERVER_TOKEN, random.nextInt(8), random.nextInt(5), ordinal + 1));
            }
            var result = SkillRuntimeService.canonicalize(randomized);
            for (var index = 1; index < result.size(); index++) {
                var left = result.get(index - 1);
                var right = result.get(index);
                assertTrue(left.delayTicks() < right.delayTicks()
                        || left.delayTicks() == right.delayTicks()
                                && left.nodeIndex() <= right.nodeIndex());
            }
        }
    }

    @Test
    void offenderSelectionPrefersCountThenSmallestSequence() {
        assertAll(
                () -> assertTrue(SkillRuntimeService.preferOffender(8, 99, 7, 1)),
                () -> assertFalse(SkillRuntimeService.preferOffender(7, 1, 8, 99)),
                () -> assertTrue(SkillRuntimeService.preferOffender(8, 2, 8, 3)),
                () -> assertFalse(SkillRuntimeService.preferOffender(8, 3, 8, 2)),
                () -> assertFalse(SkillRuntimeService.preferOffender(8, 2, 8, 2)));
    }

    @Test
    void breakerTotalsSaturateAndDrainStopIsOncePerTickWithoutFabrication() {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        var instance = new SkillInstanceId(new UUID(101, 103));
        for (var reason : RuntimeCircuitBreakReason.values()) {
            slot.diagnostics.breakerTotals[reason.ordinal()] = Long.MAX_VALUE;
            var maximum = 10;
            SkillRuntimeService.recordBreaker(
                    slot,
                    instance,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    new RuntimeCircuitBreakerSummary(reason, maximum, 1, maximum, 0, false));
        }
        SkillRuntimeService.observeDrainStop(
                slot, RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED);
        SkillRuntimeService.observeDrainStop(
                slot, RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED);

        assertAll(
                () -> assertTrue(Arrays.stream(slot.diagnostics.breakerTotals)
                        .allMatch(value -> value == Long.MAX_VALUE)),
                () -> assertEquals(4, slot.diagnostics.breakerTripsThisTick),
                () -> assertEquals(1, slot.diagnostics.serverExecutionLimitReachedTickTotal),
                () -> assertTrue(slot.diagnostics.serverExecutionLimitReachedThisTick),
                () -> assertTrue(slot.queue.isEmpty()),
                () -> assertEquals(0, slot.diagnostics.executionAttemptsThisTick),
                () -> assertEquals(0, slot.diagnostics.portInvocationsThisTick));

        SkillRuntimeService.resetTickState(slot);
        slot.diagnostics.serverExecutionLimitReachedTickTotal = Long.MAX_VALUE;
        SkillRuntimeService.observeDrainStop(
                slot, RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED);
        assertEquals(Long.MAX_VALUE, slot.diagnostics.serverExecutionLimitReachedTickTotal);

        var expired = event(9, 1, 0, 0, 0, 0, 1, 1, SERVER_TOKEN);
        slot.runtimeTick = 1;
        slot.diagnostics.deadlineExpiredEventTotal = Long.MAX_VALUE;
        SkillRuntimeService.observeExpiry(slot, expired);
        assertEquals(Long.MAX_VALUE, slot.diagnostics.deadlineExpiredEventTotal);
    }

    @Test
    void deadlineExpiryIsStrictlyAfterTheInclusiveDeadline() {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        var event = childEvent(1, 1, 10, 0, 1, SERVER_TOKEN);
        slot.runtimeTick = event.deadlineRuntimeTick() - 1;
        assertFalse(SkillRuntimeService.deadlineExpired(slot, event));
        slot.runtimeTick = event.deadlineRuntimeTick();
        assertFalse(SkillRuntimeService.deadlineExpired(slot, event));
        slot.runtimeTick = event.deadlineRuntimeTick() + 1;
        assertTrue(SkillRuntimeService.deadlineExpired(slot, event));
        SkillRuntimeService.observeExpiry(slot, event);
        assertAll(
                () -> assertEquals(1, slot.diagnostics.deadlineExpiredEventsThisTick),
                () -> assertEquals(1, slot.diagnostics.maximumDeadlineLatenessTicksThisTick));
    }

    @Test
    void deadlineEqualityCanDeferUnchangedThenExpiresAtTheNextObservation() {
        var slot = new ServerSlot(SERVER_TOKEN, defaultLimits());
        var event = event(11, 1, 0, 5, 10, 0, 1, 1, SERVER_TOKEN);
        slot.runtimeTick = event.deadlineRuntimeTick();
        var eventId = event.eventId();
        var scheduled = event.scheduledRuntimeTick();
        var deadline = event.deadlineRuntimeTick();
        slot.deferred[0] = event;
        slot.deferredCount = 1;

        assertAll(
                () -> assertFalse(SkillRuntimeService.deadlineExpired(slot, event)),
                () -> assertEquals(5, SkillRuntimeService.schedulingLag(slot.runtimeTick, event)));
        SkillRuntimeService.finishDeferred(slot);
        assertAll(
                () -> assertSame(event, slot.queue.peek()),
                () -> assertEquals(eventId, event.eventId()),
                () -> assertEquals(scheduled, event.scheduledRuntimeTick()),
                () -> assertEquals(deadline, event.deadlineRuntimeTick()),
                () -> assertEquals(0, slot.deferredCount));

        slot.runtimeTick++;
        assertTrue(SkillRuntimeService.deadlineExpired(slot, event));
        SkillRuntimeService.observeExpiry(slot, event);
        assertAll(
                () -> assertEquals(6, slot.diagnostics.maximumLagTicksThisTick),
                () -> assertEquals(1, slot.diagnostics.deadlineExpiredEventsThisTick),
                () -> assertEquals(0, slot.diagnostics.executionAttemptsThisTick),
                () -> assertEquals(0, slot.diagnostics.portInvocationsThisTick),
                () -> assertEquals(0, slot.diagnostics.breakerTripsThisTick));
    }

    @Test
    void referenceAndPortMappingsAreClosedExactAndHaveExplicitModeledCardinality() {
        for (var reason : List.of(
                RuntimeReferenceFailureReason.MISSING,
                RuntimeReferenceFailureReason.MISSING_OR_UNLOADED,
                RuntimeReferenceFailureReason.UNLOADED)) {
            var source = assertInstanceOf(
                    RuntimeExecutionOutcome.SourceMissing.class,
                    SkillRuntimeService.referenceFailureOutcome(
                            new RuntimeReferenceResolutionOutcome.SourceMissing(reason)));
            var target = assertInstanceOf(
                    RuntimeExecutionOutcome.TargetMissing.class,
                    SkillRuntimeService.referenceFailureOutcome(
                            new RuntimeReferenceResolutionOutcome.TargetMissing(reason)));
            assertEquals(reason, source.reason());
            assertEquals(reason, target.reason());
        }
        for (var reason : List.of(
                RuntimeReferenceFailureReason.WRONG_SERVER,
                RuntimeReferenceFailureReason.DIMENSION_UNAVAILABLE,
                RuntimeReferenceFailureReason.WRONG_DIMENSION,
                RuntimeReferenceFailureReason.TYPE_MISMATCH)) {
            var outcome = assertInstanceOf(
                    RuntimeExecutionOutcome.InvalidRuntimeReference.class,
                    SkillRuntimeService.referenceFailureOutcome(
                            new RuntimeReferenceResolutionOutcome.InvalidRuntimeReference(reason)));
            assertEquals(reason, outcome.reason());
        }
        for (var reason : RuntimePortRejectionReason.values()) {
            var outcome = assertInstanceOf(
                    RuntimeExecutionOutcome.RejectedByExecutionPort.class,
                    SkillRuntimeService.portRejectionOutcome(new RuntimePortOutcome.Rejected(reason)));
            assertEquals(reason, outcome.reason());
        }

        // Detached component cardinalities: not a claim of an actual server-backed port invocation.
        var preClaim = new long[] {0, 0};
        var p5Terminal = new long[] {1, 0};
        var p6Terminal = new long[] {1, 1};
        assertAll(
                () -> assertArrayEquals(new long[] {0, 0}, preClaim),
                () -> assertArrayEquals(new long[] {1, 0}, p5Terminal),
                () -> assertArrayEquals(new long[] {1, 1}, p6Terminal));
    }

    @Test
    void attributionKindsKeepIndependentExactValueIdentity() {
        var sameValueToken = new RuntimeServerToken(SERVER_TOKEN.value());
        var otherToken = new RuntimeServerToken(SERVER_TOKEN.value() + 1);
        var playerId = new RuntimePlayerId(new UUID(107, 109));
        RuntimeBudgetAttribution player = new PlayerRuntimeBudgetAttribution(SERVER_TOKEN, playerId);
        RuntimeBudgetAttribution samePlayer = new PlayerRuntimeBudgetAttribution(sameValueToken, playerId);
        RuntimeBudgetAttribution otherServerPlayer =
                new PlayerRuntimeBudgetAttribution(otherToken, playerId);
        RuntimeBudgetAttribution automation = new NonPlayerRuntimeBudgetAttribution(
                SERVER_TOKEN, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION);

        assertAll(
                () -> assertEquals(player, samePlayer),
                () -> assertEquals(player.hashCode(), samePlayer.hashCode()),
                () -> assertFalse(player.equals(otherServerPlayer)),
                () -> assertFalse(player.equals(automation)),
                () -> assertSame(SERVER_TOKEN, player.server()),
                () -> assertSame(SERVER_TOKEN, automation.server()));
    }

    @Test
    void sameRevisionAcrossPlayersKeepsFreshIndependentModeledIdentities() {
        var firstPlayer = new RuntimePlayerId(new UUID(127, 131));
        var secondPlayer = new RuntimePlayerId(new UUID(137, 139));
        var firstAttribution = new PlayerRuntimeBudgetAttribution(SERVER_TOKEN, firstPlayer);
        var secondAttribution = new PlayerRuntimeBudgetAttribution(SERVER_TOKEN, secondPlayer);
        var sameRevisionValue = new SkillReference(
                SKILL_REFERENCE.skillId(), SKILL_REFERENCE.revision());
        var firstSequence = SkillRuntimeService.checkedPositiveSuccessor(0).orElseThrow();
        var secondSequence = SkillRuntimeService.checkedPositiveSuccessor(firstSequence)
                .orElseThrow();
        var laterSequence = SkillRuntimeService.checkedPositiveSuccessor(secondSequence)
                .orElseThrow();
        var firstId = new SkillInstanceId(new UUID(SERVER_TOKEN.value(), firstSequence));
        var secondId = new SkillInstanceId(new UUID(SERVER_TOKEN.value(), secondSequence));
        var laterId = new SkillInstanceId(new UUID(SERVER_TOKEN.value(), laterSequence));
        var firstEvent = playerRootEvent(1, firstId, firstSequence, SKILL_REFERENCE, firstPlayer);
        var secondEvent = playerRootEvent(
                2, secondId, secondSequence, sameRevisionValue, secondPlayer);
        var laterEvent = playerRootEvent(
                3, laterId, laterSequence, sameRevisionValue, firstPlayer);

        assertAll(
                () -> assertEquals(firstEvent.skillReference(), secondEvent.skillReference()),
                () -> assertEquals(firstEvent.skillReference(), laterEvent.skillReference()),
                () -> assertFalse(firstAttribution.equals(secondAttribution)),
                () -> assertFalse(firstEvent.skillInstanceId().equals(secondEvent.skillInstanceId())),
                () -> assertFalse(firstEvent.skillInstanceId().equals(laterEvent.skillInstanceId())),
                () -> assertEquals(firstSequence + 1L, secondSequence),
                () -> assertEquals(secondSequence + 1L, laterSequence),
                () -> assertEquals(
                        RuntimeBudgetDecision.DEFER_SKILL_INSTANCE_TICK_LIMIT,
                        SkillRuntimeService.executionDecision(
                                defaultLimits(), 64, 0, firstAttribution)),
                () -> assertEquals(
                        RuntimeBudgetDecision.EXECUTE,
                        SkillRuntimeService.executionDecision(
                                defaultLimits(), 0, 0, secondAttribution)));
        // Value-only continuation after modeled cancellation/break: the exact revision may be
        // reused, while the server-minted identity and ordering sequence remain fresh.
        assertFalse(firstEvent.cancellationToken().equals(laterEvent.cancellationToken()));
    }

    private static RuntimeChildSpec childSpec(
            RuntimeServerToken token,
            int nodeIndex,
            int delay,
            int horizon) {
        return new RuntimeChildSpec(
                nodeIndex,
                delay,
                horizon,
                new ServerOrigin(token),
                Optional.empty(),
                new ChildTriggerCause(EVENT_KIND),
                NoRuntimeExecutionData.INSTANCE);
    }

    private static RuntimeEvent rootEvent(
            long eventId,
            long created,
            long scheduled,
            long deadline,
            RuntimeServerToken token) {
        var instanceId = new SkillInstanceId(new UUID(token.value(), 1));
        return new RuntimeEvent(
                new EventId(eventId),
                instanceId,
                new RuntimeSkillInstanceSequence(1),
                new RuntimeCancellationToken(token, instanceId),
                Optional.empty(),
                SKILL_REFERENCE,
                0,
                created,
                scheduled,
                deadline,
                0,
                0,
                RuntimeSchedulePersistence.MEMORY_ONLY,
                new NonPlayerRuntimeBudgetAttribution(
                        token, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION),
                new ServerOrigin(token),
                Optional.empty(),
                new RootTriggerCause(EVENT_KIND),
                NoRuntimeExecutionData.INSTANCE);
    }

    private static RuntimeEvent playerRootEvent(
            long eventId,
            SkillInstanceId instanceId,
            long instanceSequence,
            SkillReference reference,
            RuntimePlayerId playerId) {
        return new RuntimeEvent(
                new EventId(eventId),
                instanceId,
                new RuntimeSkillInstanceSequence(instanceSequence),
                new RuntimeCancellationToken(SERVER_TOKEN, instanceId),
                Optional.empty(),
                reference,
                0,
                0,
                1,
                2,
                0,
                0,
                RuntimeSchedulePersistence.MEMORY_ONLY,
                new PlayerRuntimeBudgetAttribution(SERVER_TOKEN, playerId),
                new PlayerOrigin(
                        SERVER_TOKEN, net.minecraft.world.level.Level.OVERWORLD, playerId),
                Optional.empty(),
                new RootTriggerCause(EVENT_KIND),
                NoRuntimeExecutionData.INSTANCE);
    }

    private static RuntimeEvent childEvent(
            long eventId,
            long instanceSequence,
            long scheduled,
            int nodeIndex,
            int childSequence,
            RuntimeServerToken token) {
        return event(
                eventId,
                instanceSequence,
                0,
                scheduled,
                scheduled + 100,
                nodeIndex,
                childSequence,
                1,
                token);
    }

    private static RuntimeEvent event(
            long eventId,
            long instanceSequence,
            long created,
            long scheduled,
            long deadline,
            int nodeIndex,
            int childSequence,
            int depth,
            RuntimeServerToken token) {
        var instanceId = new SkillInstanceId(new UUID(token.value(), instanceSequence));
        return new RuntimeEvent(
                new EventId(eventId),
                instanceId,
                new RuntimeSkillInstanceSequence(instanceSequence),
                new RuntimeCancellationToken(token, instanceId),
                Optional.of(new EventId(9_999)),
                SKILL_REFERENCE,
                nodeIndex,
                created,
                scheduled,
                deadline,
                depth,
                childSequence,
                RuntimeSchedulePersistence.MEMORY_ONLY,
                new NonPlayerRuntimeBudgetAttribution(
                        token, NonPlayerRuntimeBudgetDomain.SERVER_AUTOMATION),
                new ServerOrigin(token),
                Optional.empty(),
                new ChildTriggerCause(EVENT_KIND),
                NoRuntimeExecutionData.INSTANCE);
    }

    private static SkillDocument projectableDocument() {
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                SKILL_REFERENCE.skillId(),
                SKILL_REFERENCE.revision(),
                List.of(new NodeDocument(
                        projectorEnvelope(PROJECTOR_TRIGGER_ID, 11),
                        projectorEnvelope(PROJECTOR_ACTION_ID, 13),
                        AppearanceOverrideDocument.none())),
                AppearanceDocument.defaultAppearance());
    }

    private static DefinitionEnvelope projectorEnvelope(
            ResourceLocation typeId, int value) {
        var payload = new JsonObject();
        payload.addProperty("value", value);
        return new DefinitionEnvelope(typeId, 0, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    private record ProjectorTriggerPayload(int value) implements TriggerPayload {
        private static final MapCodec<ProjectorTriggerPayload> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("value")
                                        .forGetter(ProjectorTriggerPayload::value))
                        .apply(instance, ProjectorTriggerPayload::new));
    }

    private record ProjectorActionPayload(int value) implements ActionPayload {
        private static final MapCodec<ProjectorActionPayload> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("value")
                                        .forGetter(ProjectorActionPayload::value))
                        .apply(instance, ProjectorActionPayload::new));
    }

    private static final class ProjectorTriggerDescriptor
            implements TriggerType<ProjectorTriggerPayload> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(ResourceLocation.fromNamespaceAndPath(
                        Gramarye.MOD_ID, "p5_projector_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));
        private static final TriggerReferenceProjection PROJECTION =
                new TriggerReferenceProjection(
                        SourceSelection.NONE, TargetSelection.NONE, false, List.of());

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return PayloadMigrationPlan.empty();
        }

        @Override
        public Optional<TriggerPayloadInspector<ProjectorTriggerPayload>> payloadInspector() {
            return Optional.of(payload -> new PayloadInspectionResult.Success<>(PROJECTION));
        }

        @Override
        public MapCodec<ProjectorTriggerPayload> payloadCodec() {
            return ProjectorTriggerPayload.CODEC;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                ProjectorTriggerPayload payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    private static final class ProjectorActionDescriptor
            implements ActionType<ProjectorActionPayload> {
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
        private static final ActionReferenceProjection PROJECTION =
                new ActionReferenceProjection(
                        SourceSelection.NONE, TargetSelection.NONE, List.of(), Set.of());

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return PayloadMigrationPlan.empty();
        }

        @Override
        public Optional<ActionPayloadInspector<ProjectorActionPayload>> payloadInspector() {
            return Optional.of(payload -> new PayloadInspectionResult.Success<>(PROJECTION));
        }

        @Override
        public MapCodec<ProjectorActionPayload> payloadCodec() {
            return ProjectorActionPayload.CODEC;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                ProjectorActionPayload payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    private record SingleTriggerLookup(
            ResourceLocation key, TriggerType<?> descriptor) implements TriggerTypeLookup {
        @Override
        public Optional<TriggerType<?>> find(ResourceLocation typeId) {
            return key.equals(typeId) ? Optional.of(descriptor) : Optional.empty();
        }

        @Override
        public Optional<ResourceLocation> keyOf(TriggerType<?> value) {
            return descriptor == value ? Optional.of(key) : Optional.empty();
        }
    }

    private record SingleActionLookup(
            ResourceLocation key, ActionType<?> descriptor) implements ActionTypeLookup {
        @Override
        public Optional<ActionType<?>> find(ResourceLocation typeId) {
            return key.equals(typeId) ? Optional.of(descriptor) : Optional.empty();
        }

        @Override
        public Optional<ResourceLocation> keyOf(ActionType<?> value) {
            return descriptor == value ? Optional.of(key) : Optional.empty();
        }
    }

    private static P5RuntimeLimits defaultLimits() {
        return P5RuntimeLimits.fromRequested(new P5RuntimeRequestedLimits(
                256,
                1_024,
                4_096,
                32,
                128,
                64,
                64,
                128,
                512,
                512,
                32,
                32,
                16,
                12_000,
                12_000,
                128));
    }

}
