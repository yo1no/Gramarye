package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Uses production control transitions with isolated identity fixtures, not native runtime proof. */
class P11TransitionControlTest {
    private static P11StartupLimits limits() {
        return new P11StartupLimits(4, 3, 30_000, 2, 2, 4, 4, 2, 2, 8_388_608, 3, 10_000);
    }

    private record Fixture(P11IdentityOwner identities, P11IdentityOwner.ModelConnection channel,
            P11IdentityOwner.CapturedIdentity actor, P11TransitionControl control) { }

    private static Fixture play(long sequence) {
        var fixture = unstarted(sequence, 0, 0);
        assertTrue(fixture.control.openPlayScene(fixture.actor, Kind.DEATH));
        return fixture;
    }

    private static Fixture unstarted(long sequence, long scene, long version) {
        var identities = P11IdentityOwner.isolatedModel(4);
        var channel = identities.modelConnection();
        var actor = identities.bindModel(identities.modelActor(UUID.randomUUID(), 7), channel).orElseThrow();
        var control = P11TransitionControl.isolatedAtCounters(actor, limits(), 0, sequence, scene, version);
        return new Fixture(identities, channel, actor, control);
    }

    private static Fixture actorless(Kind kind) {
        var identities = P11IdentityOwner.isolatedModel(4);
        var channel = identities.modelConnection();
        var identity = identities.modelActorless(UUID.randomUUID(), channel).orElseThrow();
        var control = new P11TransitionControl(identity, limits(), 0);
        assertTrue(control.openServerScene(Scope.CONFIG, kind));
        return new Fixture(identities, channel, identity, control);
    }

    private static Request request(P11TransitionControl control, long sequence, Command command) {
        var state = control.state().orElseThrow();
        return new Request(state.scope(), state.connectionEpoch(), state.sceneSerial(),
                state.actorGeneration(), sequence, command, state.kind());
    }

    private static Request successorRequest(State binding, long sequence, Command command) {
        return new Request(binding.scope(), binding.connectionEpoch(), binding.sceneSerial(),
                binding.actorGeneration(), sequence, command, binding.kind());
    }

    private static P11TransitionControl.Drain enqueue(P11TransitionControl control,
            long sequence, Command command, long time) {
        var offered = control.offer(control.listener(), request(control, sequence, command), time);
        assertTrue(offered == P11TransitionControl.Offer.RETAINED
                || offered == P11TransitionControl.Offer.COALESCED, offered.toString());
        return control.reserveDrain(control.listener()).orElseThrow();
    }

    private static void refuse(P11TransitionControl control, long sequence, long time) {
        var drain = enqueue(control, sequence, Command.TRY, time);
        assertTrue(control.begin(drain, P11TransitionControl.Gate.ACTIVE_OPERATION, time).isEmpty());
        assertTrue(control.finishDrain(drain));
        assertEquals(Outcome.NOT_STARTED, control.state().orElseThrow().outcome());
    }

    @Test
    void exactConnectionHasOneControlOwnerAcrossActorChangesAndRetirement() {
        var f = play(40);
        assertThrows(IllegalStateException.class, () -> new P11TransitionControl(f.actor, limits(), 0));
        assertThrows(IllegalStateException.class,
                () -> P11TransitionControl.isolatedAtCounters(f.actor, limits(), 0, 0, 0, 0));
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertThrows(IllegalStateException.class, () -> new P11TransitionControl(b, limits(), 0));
        f.control.retire();
        assertThrows(IllegalStateException.class, () -> new P11TransitionControl(b, limits(), 0));
        var actorless = f.identities.modelActorless(f.actor.uuid(), f.channel).orElseThrow();
        assertThrows(IllegalStateException.class, () -> new P11TransitionControl(actorless, limits(), 0));
        var reconnected = f.identities.modelActorless(f.actor.uuid(), f.identities.modelConnection()).orElseThrow();
        assertNotSame(f.actor.connection(), reconnected.connection());
        var replacement = new P11TransitionControl(reconnected, limits(), 0);
        assertTrue(replacement.openServerScene(Scope.CONFIG, Kind.JOIN));
    }

    @Test
    void sourceInspectionFailureBeforeBeginReleasesExactDrainAsUnknownNotHealthyRefusal() {
        var c = play(0).control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        assertTrue(c.abortAdmission(drain));
        var failed = c.state().orElseThrow();
        assertEquals(Outcome.UNKNOWN, failed.outcome());
        assertEquals(Availability.DISABLED, failed.availability());
        assertEquals(1, failed.requestSeq());
        assertEquals(1, c.fence());
        assertTrue(c.hasService(), "the exact unknown record must be deliverable after drain release");
        assertTrue(c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).isEmpty());
        assertFalse(c.abortAdmission(drain));
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
    }

    @Test
    void alreadyUnknownSourceGateCanStillReleaseItsUnbegunExactDrain() {
        var c = play(0).control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        c.observationLost();
        assertTrue(c.begin(drain, P11TransitionControl.Gate.NOT_APPLICABLE, 0).isEmpty());
        assertFalse(c.finishDrain(drain));
        assertFalse(c.submissionFailed(drain));
        assertTrue(c.abortAdmission(drain));
        assertEquals(Outcome.UNKNOWN, c.state().orElseThrow().outcome());
        assertEquals(1, c.state().orElseThrow().requestSeq());
        assertTrue(c.hasService());
    }

    @Test
    void custodyOrTicketFailureAfterBeginFaultsTheExactAttemptAndReleasesDrain() {
        var c = play(0).control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertTrue(c.abortAdmission(drain));
        assertEquals(Outcome.FAULT, c.state().orElseThrow().outcome());
        assertEquals(Reason.SOURCE_UNAVAILABLE, c.state().orElseThrow().reason());
        assertEquals(Availability.DISABLED, c.state().orElseThrow().availability());
        assertFalse(c.callerCompleted(attempt, 2));
        assertFalse(c.fault(attempt, Reason.NATIVE_FAILURE));
        assertTrue(c.hasService());
        assertTrue(c.releaseCompletedDrain());
    }

    @Test
    void admissionAbortCannotTargetNullForeignOrPreviouslyReleasedDrains() {
        var first = play(0).control;
        var second = play(0).control;
        var own = enqueue(first, 1, Command.TRY, 0);
        var foreign = enqueue(second, 1, Command.TRY, 0);
        var before = first.state().orElseThrow();
        assertFalse(first.abortAdmission(null));
        assertFalse(first.abortAdmission(foreign));
        assertEquals(before, first.state().orElseThrow());
        assertTrue(first.begin(own, P11TransitionControl.Gate.ACTIVE_OPERATION, 0).isEmpty());
        assertTrue(first.finishDrain(own));
        var refused = first.state().orElseThrow();
        assertFalse(first.abortAdmission(own));
        assertEquals(refused, first.state().orElseThrow());
        assertTrue(second.abortAdmission(foreign));
    }

    @Test
    void admissionAbortCannotReplaceANativeFrameOrRetiredOwnerTerminal() {
        var f = play(0);
        var drain = enqueue(f.control, 1, Command.TRY, 0);
        var attempt = f.control.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var replacement = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(f.control.nativeFrame(attempt, replacement));
        var framed = f.control.state().orElseThrow();
        assertFalse(f.control.abortAdmission(drain));
        assertEquals(framed, f.control.state().orElseThrow());
        f.control.retire();
        assertFalse(f.control.abortAdmission(drain));
    }

    @Test
    void exactAdmissionCleanupDoesNotRequireAStillLivePhysicalConnection() {
        var f = play(0);
        var drain = enqueue(f.control, 1, Command.TRY, 0);
        f.control.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertTrue(f.identities.retireConnection(f.actor));
        assertTrue(f.control.abortAdmission(drain));
        assertEquals(Outcome.FAULT, f.control.state().orElseThrow().outcome());
        assertFalse(f.control.abortAdmission(drain));
        assertTrue(f.control.begin(drain, P11TransitionControl.Gate.ALLOW, 0).isEmpty());
    }

    @Test
    void invalidOrStaleConstructionCannotConsumeTheCurrentConnectionClaim() {
        var identities = P11IdentityOwner.isolatedModel(1);
        var channel = identities.modelConnection();
        var a = identities.bindModel(identities.modelActor(UUID.randomUUID(), 7), channel).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> new P11TransitionControl(a, limits(), -1));
        var b = identities.bindModel(identities.modelActor(a.uuid(), 7), channel).orElseThrow();
        assertThrows(IllegalStateException.class, () -> new P11TransitionControl(a, limits(), 0));
        var owner = new P11TransitionControl(b, limits(), 0);
        assertTrue(owner.openPlayScene(b, Kind.DEATH));
        assertEquals(0, owner.fence());
    }

    @Test
    void concurrentConstructorsShareOneAtomicExactConnectionClaim() throws Exception {
        var identities = P11IdentityOwner.isolatedModel(1);
        var identity = identities.modelActorless(UUID.randomUUID(), identities.modelConnection()).orElseThrow();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    new P11TransitionControl(identity, limits(), 0);
                    return 1;
                } catch (IllegalStateException rejected) {
                    assertEquals("P11_CONTROL_OWNER_ALREADY_CLAIMED_OR_STALE", rejected.getMessage());
                    return 0;
                }
            };
            var first = workers.submit(create);
            var second = workers.submit(create);
            start.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void freshSceneAfterFortyUsesExactRateProofFenceThenExplicitNewRetry() {
        var f = play(38);
        var c = f.control;
        refuse(c, 39, 0);
        refuse(c, 40, 0);
        assertTrue(c.openPlayScene(f.actor, Kind.END));
        assertEquals(P11TransitionControl.Offer.RATE_LIMITED,
                c.offer(c.listener(), request(c, 41, Command.TRY), 0));
        var query = enqueue(c, 41, Command.STATUS, 0);
        var negative = c.processStatus(query).orElseThrow();
        assertEquals(Outcome.NOT_STARTED, negative.outcome());
        assertEquals(Reason.CONTROL_RATE_LIMIT, negative.reason());
        assertEquals(41, c.fence());
        assertTrue(c.finishDrain(query));
        assertEquals(P11TransitionControl.Offer.COALESCED,
                c.offer(c.listener(), request(c, 41, Command.TRY), 500));
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
        assertFalse(c.blockersEnded(c.listener(), false));
        assertTrue(c.blockersEnded(c.listener(), true));
        assertEquals(Availability.MAY_TRY, c.state().orElseThrow().availability());
        assertTrue(c.reserveDrain(c.listener()).isEmpty(), "notification never dispatches old TRY");
        var retry = enqueue(c, 42, Command.TRY, 500);
        var attempt = c.begin(retry, P11TransitionControl.Gate.ALLOW, 500).orElseThrow();
        assertEquals(42, attempt.requestSeq());
        assertTrue(c.begin(retry, P11TransitionControl.Gate.ALLOW, 500).isEmpty());
        assertEquals(Outcome.RUNNING, c.state().orElseThrow().outcome());
    }

    @Test
    void missingTryWithoutExactRateEvidenceUsesDispatchBusyNotAggregateRate() {
        var c = play(40).control;
        var query = enqueue(c, 41, Command.STATUS, 0);
        assertEquals(Reason.CONTROL_DISPATCH_BUSY, c.processStatus(query).orElseThrow().reason());
        assertEquals(41, c.fence());
        assertTrue(c.finishDrain(query));
        assertTrue(c.takeNotification(c.listener()).isPresent());
        // Failure to deliver this immutable value cannot erase the already-installed fence.
        assertEquals(41, c.fence());
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
    }

    @Test
    void inboxTryCannotBeReplacedBySameOrDifferentStatusOrTry() {
        var c = play(40).control;
        assertEquals(P11TransitionControl.Offer.RETAINED,
                c.offer(c.listener(), request(c, 41, Command.TRY), 0));
        assertEquals(Outcome.PENDING, c.state().orElseThrow().outcome());
        assertEquals(P11TransitionControl.Offer.COALESCED,
                c.offer(c.listener(), request(c, 41, Command.STATUS), 0));
        assertEquals(P11TransitionControl.Offer.BUSY,
                c.offer(c.listener(), request(c, 42, Command.STATUS), 0));
        assertEquals(P11TransitionControl.Offer.BUSY,
                c.offer(c.listener(), request(c, 42, Command.TRY), 0));
        assertEquals(40, c.fence());
        var drain = c.reserveDrain(c.listener()).orElseThrow();
        assertEquals(41, drain.request().requestSeq());
        assertEquals(41, c.begin(drain, P11TransitionControl.Gate.ALLOW, 0)
                .orElseThrow().requestSeq());
    }

    @Test
    void removedInboxHasVisibleDrainReservationAcrossRealThreadInterleaving() throws Exception {
        var c = play(40).control;
        var drain = enqueue(c, 41, Command.TRY, 0);
        var captured = c.listener();
        var reserved = new CountDownLatch(1);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var observed = workers.submit(() -> {
                assertTrue(reserved.await(5, TimeUnit.SECONDS));
                return c.offer(captured, request(c, 42, Command.STATUS), 0);
            });
            reserved.countDown();
            assertEquals(P11TransitionControl.Offer.BUSY, observed.get(5, TimeUnit.SECONDS));
        }
        assertEquals(40, c.fence());
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
        assertFalse(c.finishDrain(drain), "unbegun TRY cannot lose its only reservation");
        assertTrue(c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).isPresent());
        assertTrue(c.finishDrain(drain));
        assertFalse(c.finishDrain(drain));
    }

    @Test
    void nestedPumpDoesNotBeginSecondBodyAndUnknownCannotBecomeFresh() {
        var c = play(0).control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertEquals(P11TransitionControl.Offer.BUSY,
                c.offer(c.listener(), request(c, 2, Command.STATUS), 0));
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
        assertTrue(c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).isEmpty());
        assertTrue(c.finishDrain(drain));
        c.observationLost();
        assertEquals(P11TransitionControl.Disposition.UNKNOWN, c.disposition());
        assertEquals(Outcome.UNKNOWN, c.state().orElseThrow().outcome());
        assertFalse(c.callerCompleted(attempt, 2));
        assertFalse(c.blockersEnded(c.listener(), true));
        assertEquals(P11TransitionControl.Offer.CLOSED,
                c.offer(c.listener(), request(c, 2, Command.TRY), 1000));
        c.takeNotification(c.listener());
        assertEquals(P11TransitionControl.Service.NONE, c.nextService());
    }

    @Test
    void emptyCurrentLossAndSchedulerFailureNeverInventFreshNegativeProof() {
        var f = play(0);
        f.control.observationLost();
        assertEquals(P11TransitionControl.Disposition.UNKNOWN, f.control.disposition());
        assertNotEquals(Outcome.NOT_STARTED, f.control.state().orElseThrow().outcome());
        assertFalse(f.control.openPlayScene(f.actor, Kind.END));
        var c = play(0).control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        assertTrue(c.submissionFailed(drain));
        assertTrue(c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).isEmpty());
        assertFalse(c.submissionFailed(drain));
        assertEquals(Outcome.UNKNOWN, c.state().orElseThrow().outcome());
        c.takeNotification(c.listener());
        assertEquals(P11TransitionControl.Service.NONE, c.nextService());
    }

    @Test
    void serverRequestZeroIsRetainedAndNeverUsesFreshDeathProof() {
        var c = actorless(Kind.JOIN).control;
        assertEquals(Outcome.PENDING, c.state().orElseThrow().outcome());
        assertEquals(0, c.state().orElseThrow().requestSeq());
        assertEquals(P11TransitionControl.Offer.COALESCED,
                c.offer(c.listener(), request(c, 0, Command.STATUS), 0));
        assertEquals(P11TransitionControl.Offer.BUSY,
                c.offer(c.listener(), request(c, 1, Command.STATUS), 0));
        assertEquals(0, c.fence());
        var initial = c.reserveServerDrain(c.listener()).orElseThrow();
        assertFalse(c.finishDrain(initial));
        assertTrue(c.processStatus(initial).isEmpty());
        var attempt = c.begin(initial, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertTrue(attempt.serverIssued());
        assertEquals(0, attempt.requestSeq());
        assertTrue(c.reserveServerDrain(c.listener()).isEmpty());
    }

    @Test
    void phaseHandoffRetiresOldScheduleButRetainsZeroTaskAndOriginalWait() {
        var c = actorless(Kind.RETURN_TO_WORLD).control;
        var oldListener = c.listener();
        var first = c.reserveServerDrain(oldListener).orElseThrow();
        assertTrue(c.begin(first, P11TransitionControl.Gate.ACTIVE_CONTEXT, 100).isEmpty());
        assertTrue(c.finishDrain(first));
        assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(100));
        assertFalse(c.openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD),
                "a refused server task cannot be replaced to reset its wait or request0");
        var retry = enqueue(c, 1, Command.TRY, 1000);
        var transfer = c.beginHandoff(oldListener, Scope.PREPLAY).orElseThrow();
        assertTrue(c.begin(retry, P11TransitionControl.Gate.ALLOW, 1000).isEmpty());
        assertTrue(c.reserveDrain(oldListener).isEmpty());
        var nextListener = c.completeHandoff(transfer).orElseThrow();
        assertNotSame(oldListener, nextListener);
        assertTrue(c.completeHandoff(transfer).isEmpty());
        assertEquals(Scope.PREPLAY, c.state().orElseThrow().scope());
        assertEquals(P11TransitionControl.Offer.STALE,
                c.offer(oldListener, request(c, 2, Command.STATUS), 1000));
        var transferred = c.reserveDrain(nextListener).orElseThrow();
        assertEquals(1, transferred.request().requestSeq());
        assertTrue(c.begin(transferred, P11TransitionControl.Gate.ACTIVE_TRANSITION, 1000).isEmpty());
        assertTrue(c.finishDrain(transferred));
        assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(30_099));
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, c.observeWait(30_100));
        assertEquals(Outcome.EXPIRED, c.state().orElseThrow().outcome());
        assertEquals(P11TransitionControl.Offer.CLOSED,
                c.offer(nextListener, request(c, 2, Command.TRY), 31_000));
    }

    @Test
    void earlyConfigurationTaskPassRemainsPendingUntilFinalAdmission() {
        var f = actorless(Kind.JOIN);
        var c = f.control;
        var first = c.reserveServerDrain(c.listener()).orElseThrow();
        assertTrue(c.passConfigurationTask(first, P11TransitionControl.Gate.ALLOW, 100));
        assertEquals(Outcome.PENDING, c.state().orElseThrow().outcome());
        assertTrue(c.configurationTaskPassed());
        assertTrue(c.finishDrain(first));
        assertEquals(P11TransitionControl.Offer.BUSY,
                c.offer(c.listener(), request(c, 1, Command.TRY), 100));
        var handoff = c.beginHandoff(c.listener(), Scope.PREPLAY).orElseThrow();
        c.completeHandoff(handoff).orElseThrow();
        var finalCheck = c.reserveServerDrain(c.listener()).orElseThrow();
        assertTrue(c.begin(finalCheck, P11TransitionControl.Gate.ACTIVE_CONTEXT, 200).isEmpty());
        assertTrue(c.finishDrain(finalCheck));
        assertEquals(Outcome.NOT_STARTED, c.state().orElseThrow().outcome());
        assertEquals(Scope.PREPLAY, c.state().orElseThrow().scope());
        var parked = f.identities.modelActorless(f.actor.uuid(), f.channel).orElseThrow();
        assertTrue(c.rebindParked(parked));
        assertEquals(0, c.state().orElseThrow().requestSeq());
        assertTrue(c.blockersEnded(c.listener(), true));
        var manual = enqueue(c, 1, Command.TRY, 1000);
        assertTrue(c.begin(manual, P11TransitionControl.Gate.ALLOW, 1000).isPresent());
    }

    @Test
    void earlyTaskRetryKeepsItsExactSequenceAndOriginalAdmissionWait() {
        var c = actorless(Kind.RETURN_TO_WORLD).control;
        var initial = c.reserveServerDrain(c.listener()).orElseThrow();
        assertFalse(c.passConfigurationTask(initial, P11TransitionControl.Gate.ACTIVE_OPERATION, 100));
        assertFalse(c.configurationTaskPassed());
        assertTrue(c.finishDrain(initial));
        assertTrue(c.blockersEnded(c.listener(), true));
        var retry = enqueue(c, 41, Command.TRY, 500);
        assertTrue(c.passConfigurationTask(retry, P11TransitionControl.Gate.ALLOW, 500));
        assertTrue(c.finishDrain(retry));
        assertEquals(41, c.state().orElseThrow().requestSeq());
        c.completeHandoff(c.beginHandoff(c.listener(), Scope.PREPLAY).orElseThrow()).orElseThrow();
        var finalCheck = c.reserveServerDrain(c.listener()).orElseThrow();
        assertEquals(41, finalCheck.request().requestSeq());
        assertTrue(c.begin(finalCheck, P11TransitionControl.Gate.ACTIVE_CONTEXT, 1000).isEmpty());
        assertTrue(c.finishDrain(finalCheck));
        assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(30_099));
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, c.observeWait(30_100));
    }

    @Test
    void freshStatusAfterActorlessRefusalPreservesTheOriginalWaitInBothPhases() {
        for (var scope : new Scope[] { Scope.CONFIG, Scope.PREPLAY }) {
            var c = actorless(Kind.RETURN_TO_WORLD).control;
            var initial = c.reserveServerDrain(c.listener()).orElseThrow();
            assertFalse(c.passConfigurationTask(initial, P11TransitionControl.Gate.ACTIVE_OPERATION, 100));
            assertTrue(c.finishDrain(initial));
            assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(100));
            if (scope == Scope.PREPLAY) {
                c.completeHandoff(c.beginHandoff(c.listener(), Scope.PREPLAY).orElseThrow()).orElseThrow();
            }
            var query = enqueue(c, 41, Command.STATUS, 500);
            var repaired = c.processStatus(query).orElseThrow();
            assertEquals(scope, repaired.scope());
            assertEquals(41, repaired.requestSeq());
            assertEquals(Outcome.NOT_STARTED, repaired.outcome());
            assertEquals(Reason.CONTROL_DISPATCH_BUSY, repaired.reason());
            assertTrue(c.finishDrain(query));
            assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(501),
                    "STATUS's absent clock must not poison an already-started interval");
            assertTrue(c.blockersEnded(c.listener(), true));
            assertEquals(Availability.MAY_TRY, c.state().orElseThrow().availability());
            assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(30_099));
            assertEquals(P11ControlBudgets.WaitResult.EXPIRED, c.observeWait(30_100),
                    "STATUS, MAY_TRY and CONFIG-to-PREPLAY must not extend the first refusal at 100");
        }
    }

    @Test
    void enterConfigCallerReturnAndStartFrameDoNotPublishCompletedBeforeActualHandoff() {
        var f = unstarted(0, 0, 0);
        var c = f.control;
        assertTrue(c.openServerScene(Scope.PLAY, Kind.ENTER_CONFIG));
        var drain = c.reserveServerDrain(c.listener()).orElseThrow();
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertTrue(c.startConfigurationObserved(attempt));
        assertTrue(c.callerCompleted(attempt, 0));
        assertEquals(Outcome.RUNNING, c.state().orElseThrow().outcome());
        assertTrue(c.releaseCompletedDrain());
        var handoff = c.beginHandoff(c.listener(), Scope.CONFIG).orElseThrow();
        var actorless = f.identities.modelActorless(f.actor.uuid(), f.channel).orElseThrow();
        assertTrue(c.bindActorlessHandoff(handoff, actorless));
        assertTrue(c.completeHandoff(handoff).isPresent());
        assertEquals(Outcome.COMPLETED, c.state().orElseThrow().outcome());
        assertEquals(Scope.CONFIG, c.state().orElseThrow().scope());
        var terminal = c.takeNotification(c.listener()).orElseThrow();
        assertEquals(Kind.ENTER_CONFIG, terminal.kind());
        assertEquals(0, terminal.actorGeneration());
        assertEquals(f.actor.connectionEpoch(), terminal.connectionEpoch());
        assertTrue(c.reserveServerDrain(c.listener()).isEmpty(), "ACK completion must not create a RETURN task");
        assertFalse(c.configurationTaskPassed());
        assertFalse(c.hasService());
        for (int i = 0; i < 20; i++) {
            assertEquals(P11ControlBudgets.WaitResult.NOT_STARTED, c.observeWait(100_000 + i));
            assertEquals(terminal, c.state().orElseThrow(), "independent CONFIG terminal persists before a later return request");
        }
        assertTrue(c.bindActorlessAfterConfiguration(actorless));
        assertTrue(c.openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD));
        var returning = c.state().orElseThrow();
        assertEquals(terminal.connectionEpoch(), returning.connectionEpoch());
        assertEquals(terminal.sceneSerial() + 1, returning.sceneSerial());
        assertTrue(returning.statusVersion() > terminal.statusVersion());
        assertEquals(Kind.RETURN_TO_WORLD, returning.kind());
        assertEquals(Outcome.PENDING, returning.outcome());
    }

    @Test
    void actualConfigHandoffBeforeJavaCallerUnwindStillRequiresThatOriginalCallerTerminal() {
        var f = unstarted(0, 0, 0);
        var c = f.control;
        assertTrue(c.openServerScene(Scope.PLAY, Kind.ENTER_CONFIG));
        var drain = c.reserveServerDrain(c.listener()).orElseThrow();
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertTrue(c.startConfigurationObserved(attempt));
        var handoff = c.beginHandoff(c.listener(), Scope.CONFIG).orElseThrow();
        var actorless = f.identities.modelActorless(f.actor.uuid(), f.channel).orElseThrow();
        assertTrue(c.bindActorlessHandoff(handoff, actorless));
        assertTrue(c.completeHandoff(handoff).isPresent());
        assertEquals(Scope.CONFIG, c.state().orElseThrow().scope());
        assertNotEquals(Outcome.COMPLETED, c.state().orElseThrow().outcome());
        assertTrue(c.executableHeld(), "the original caller still owns the attempt when a later task races");
        assertFalse(c.bindActorlessAfterConfiguration(actorless));
        assertFalse(c.openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD));
        assertTrue(c.callerCompleted(attempt, 0));
        assertTrue(c.releaseCompletedDrain());
        assertFalse(c.executableHeld());
        assertEquals(Outcome.COMPLETED, c.state().orElseThrow().outcome());
        assertEquals(Kind.ENTER_CONFIG, c.state().orElseThrow().kind());
        assertTrue(c.reserveServerDrain(c.listener()).isEmpty());
    }

    @Test
    void serviceEligibilityObservationDoesNotConsumeAlternationOrNotification() {
        var c = play(0).control;
        c.offer(c.listener(), request(c, 1, Command.STATUS), 0);
        for (int i = 0; i < 100; i++) { assertTrue(c.hasService()); }
        assertEquals(P11TransitionControl.Service.INBOX, c.nextService());
        var drain = c.reserveDrain(c.listener()).orElseThrow();
        c.processStatus(drain);
        c.finishDrain(drain);
        for (int i = 0; i < 100; i++) { assertTrue(c.hasService()); }
        assertEquals(P11TransitionControl.Service.NOTIFICATION, c.nextService());
        assertTrue(c.takeNotification(c.listener()).isPresent());
        assertFalse(c.hasService());
    }

    @Test
    void completedPlayerlessAdmissionDoesNotExpireItsSuccessfulControlRecord() {
        var f = actorless(Kind.JOIN);
        var c = f.control;
        var initial = c.reserveServerDrain(c.listener()).orElseThrow();
        c.begin(initial, P11TransitionControl.Gate.ACTIVE_CONTEXT, 100);
        c.finishDrain(initial);
        var handoff = c.beginHandoff(c.listener(), Scope.PREPLAY).orElseThrow();
        c.completeHandoff(handoff).orElseThrow();
        var drain = enqueue(c, 1, Command.TRY, 1000);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 1000).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        c.nativeFrame(attempt, b);
        c.callerCompleted(attempt, b.actorGeneration());
        c.finishDrain(drain);
        var game = c.beginHandoff(c.listener(), Scope.PLAY).orElseThrow();
        c.completeHandoff(game).orElseThrow();
        assertEquals(P11ControlBudgets.WaitResult.NOT_STARTED, c.observeWait(100_000));
        assertEquals(Outcome.COMPLETED, c.state().orElseThrow().outcome());
        assertEquals(P11TransitionControl.Disposition.OPEN, c.disposition());
        assertTrue(c.openServerScene(Scope.PLAY, Kind.ENTER_CONFIG));
        assertEquals(P11ControlBudgets.WaitResult.NOT_STARTED, c.observeWait(100_000));
        var enter = c.reserveServerDrain(c.listener()).orElseThrow();
        var reconfig = c.begin(enter, P11TransitionControl.Gate.ALLOW, 100_000).orElseThrow();
        c.startConfigurationObserved(reconfig);
        c.completeHandoff(c.beginHandoff(c.listener(), Scope.CONFIG).orElseThrow()).orElseThrow();
        c.callerCompleted(reconfig, 0);
        var actorless = f.identities.modelActorless(f.actor.uuid(), f.channel).orElseThrow();
        assertTrue(c.bindActorlessAfterConfiguration(actorless));
        assertTrue(c.openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD));
        assertEquals(P11ControlBudgets.WaitResult.NOT_STARTED, c.observeWait(100_000));
        var returning = c.reserveServerDrain(c.listener()).orElseThrow();
        c.begin(returning, P11TransitionControl.Gate.ACTIVE_CONTEXT, 100_100);
        assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(100_101));
    }

    @Test
    void expectedFrameAndCallerAreSeparateAndSuccessorNeverStartsEarly() {
        var f = play(40);
        var c = f.control;
        var drain = enqueue(c, 41, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(attempt, b));
        assertTrue(c.retainSuccessor(b, Kind.END));
        assertFalse(c.activateSuccessor());
        assertFalse(c.callerCompleted(attempt, f.actor.actorGeneration()));
        assertTrue(c.callerCompleted(attempt, b.actorGeneration()));
        assertFalse(c.activateSuccessor(), "executing drain still owns exit membership");
        assertTrue(c.finishDrain(drain));
        assertTrue(c.activateSuccessor());
        assertEquals(Outcome.BINDING, c.state().orElseThrow().outcome());
        assertEquals(b.actorGeneration(), c.state().orElseThrow().actorGeneration());
        assertFalse(c.fault(attempt, Reason.NATIVE_FAILURE));
        assertEquals(41, c.fence());
        assertEquals(P11TransitionControl.Offer.STALE,
                c.offer(c.listener(), drain.request(), 1000));
    }

    @Test
    void successorReservesLargerBindingWithoutReplacingTheOldAttemptReceipt() {
        var f = play(40);
        var c = f.control;
        var drain = enqueue(c, 41, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(attempt, b));
        var old = c.state().orElseThrow();
        assertTrue(c.retainSuccessor(b, Kind.DEATH));
        var binding = c.successorBinding().orElseThrow();
        assertSame(old, c.state().orElseThrow());
        assertEquals(old.sceneSerial() + 1, binding.sceneSerial());
        assertEquals(old.statusVersion() + 1, binding.statusVersion());
        assertEquals(b.actorGeneration(), binding.actorGeneration());
        assertEquals(Outcome.BINDING, binding.outcome());
        assertEquals(0, binding.requestSeq());
        assertFalse(c.retainSuccessor(b, Kind.END));
        assertSame(binding, c.successorBinding().orElseThrow());
        assertTrue(c.callerCompleted(attempt, b.actorGeneration()));
        assertEquals(41, c.state().orElseThrow().requestSeq());
        assertEquals(old.sceneSerial(), c.state().orElseThrow().sceneSerial());
        assertTrue(c.state().orElseThrow().statusVersion() > binding.statusVersion());
        assertTrue(c.finishDrain(drain));
        assertTrue(c.activateSuccessor());
        assertEquals(binding.sceneSerial(), c.state().orElseThrow().sceneSerial());
        assertTrue(c.state().orElseThrow().statusVersion() > binding.statusVersion());
        assertTrue(c.successorBinding().isEmpty());
    }

    @Test
    void earlySuccessorTryIsNotRetainedAndStatusOnlyRepairsRealUnacceptedRequest() {
        var f = play(40);
        var c = f.control;
        var drain = enqueue(c, 41, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(attempt, b));
        assertTrue(c.retainSuccessor(b, Kind.END));
        var binding = c.successorBinding().orElseThrow();
        var firstTry = successorRequest(binding, 42, Command.TRY);
        assertEquals(P11TransitionControl.Offer.BUSY, c.offer(c.listener(), firstTry, 0));
        assertEquals(P11TransitionControl.Offer.BUSY, c.offer(c.listener(), firstTry, 0));
        assertEquals(P11TransitionControl.Offer.COALESCED,
                c.offer(c.listener(), successorRequest(binding, 42, Command.STATUS), 0));
        assertEquals(41, c.fence());
        assertEquals(Outcome.NATIVE_FRAME, c.state().orElseThrow().outcome());
        assertTrue(c.finishDrain(drain));
        assertTrue(c.reserveDrain(c.listener()).isEmpty(), "no future inbox exists even after old drain release");
        assertFalse(c.activateSuccessor(), "the old caller still owns its result");
        assertTrue(c.callerCompleted(attempt, b.actorGeneration()));
        assertTrue(c.activateSuccessor());
        assertEquals(Outcome.BINDING, c.state().orElseThrow().outcome());
        assertEquals(0, c.state().orElseThrow().requestSeq());
        assertTrue(c.reserveDrain(c.listener()).isEmpty(), "promotion does not replay first TRY");
        var query = enqueue(c, 42, Command.STATUS, 0);
        var negative = c.processStatus(query).orElseThrow();
        assertEquals(Outcome.NOT_STARTED, negative.outcome());
        assertEquals(Reason.CONTROL_DISPATCH_BUSY, negative.reason());
        assertTrue(c.begin(query, P11TransitionControl.Gate.ALLOW, 0).isEmpty());
        assertTrue(c.finishDrain(query));
        assertEquals(P11TransitionControl.Offer.COALESCED, c.offer(c.listener(), firstTry, 0));
        var retry = enqueue(c, 43, Command.TRY, 0);
        assertTrue(c.begin(retry, P11TransitionControl.Gate.ALLOW, 0).isPresent(),
                "busy early TRY did not reset or consume the existing TRY bucket");
    }

    @Test
    void faultDropsPublishedSuccessorAndNeverReplaysItsEarlyTrigger() {
        var f = play(0);
        var c = f.control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(attempt, b));
        assertTrue(c.retainSuccessor(b, Kind.END));
        var early = successorRequest(c.successorBinding().orElseThrow(), 2, Command.TRY);
        assertEquals(P11TransitionControl.Offer.BUSY, c.offer(c.listener(), early, 0));
        assertTrue(c.fault(attempt, Reason.NATIVE_FAILURE));
        assertTrue(c.successorBinding().isEmpty());
        assertFalse(c.activateSuccessor());
        assertEquals(P11TransitionControl.Offer.CLOSED, c.offer(c.listener(), early, 1));
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
    }

    @Test
    void actorlessJoinSuccessorKeepsItsReservedSceneThroughNativePlayHandoff() {
        var f = actorless(Kind.JOIN);
        var c = f.control;
        c.completeHandoff(c.beginHandoff(c.listener(), Scope.PREPLAY).orElseThrow()).orElseThrow();
        var drain = c.reserveServerDrain(c.listener()).orElseThrow();
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(attempt, b));
        assertTrue(c.retainSuccessor(b, Kind.DEATH));
        var reserved = c.successorBinding().orElseThrow();
        assertEquals(Scope.PLAY, reserved.scope());
        assertEquals(P11TransitionControl.Offer.BUSY,
                c.offer(c.listener(), successorRequest(reserved, 1, Command.TRY), 0));
        assertTrue(c.callerCompleted(attempt, b.actorGeneration()));
        assertTrue(c.finishDrain(drain));
        assertFalse(c.activateSuccessor(), "actual PLAY handoff is still absent");
        c.completeHandoff(c.beginHandoff(c.listener(), Scope.PLAY).orElseThrow()).orElseThrow();
        assertTrue(c.activateSuccessor());
        assertEquals(reserved.sceneSerial(), c.state().orElseThrow().sceneSerial());
        assertEquals(0, c.state().orElseThrow().requestSeq());
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
    }

    @Test
    void successorCounterReservationCannotWrapOrReuseTheReservedScene() {
        var f = unstarted(0, Long.MAX_VALUE - 2, 0);
        var c = f.control;
        assertTrue(c.openPlayScene(f.actor, Kind.DEATH));
        var drain = enqueue(c, 1, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(attempt, b));
        assertTrue(c.retainSuccessor(b, Kind.END));
        assertEquals(Long.MAX_VALUE, c.successorBinding().orElseThrow().sceneSerial());
        assertTrue(c.callerCompleted(attempt, b.actorGeneration()));
        assertTrue(c.finishDrain(drain));
        assertTrue(c.activateSuccessor());
        assertEquals(Long.MAX_VALUE, c.state().orElseThrow().sceneSerial());
        var nextDrain = enqueue(c, 2, Command.TRY, 0);
        var nextAttempt = c.begin(nextDrain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var target = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(nextAttempt, target));
        assertFalse(c.retainSuccessor(target, Kind.DEATH));
        assertEquals(P11TransitionControl.Disposition.EXHAUSTED, c.disposition());
        assertTrue(c.successorBinding().isEmpty());
    }

    @Test
    void retryAvailabilityUsesSameBucketWithoutConsumingTokensOrVersionChurn() {
        var c = play(0).control;
        refuse(c, 1, 0);
        refuse(c, 2, 0);
        assertEquals(P11TransitionControl.Offer.RATE_LIMITED,
                c.offer(c.listener(), request(c, 3, Command.TRY), 0));
        var status = enqueue(c, 3, Command.STATUS, 0);
        assertEquals(Reason.CONTROL_RATE_LIMIT, c.processStatus(status).orElseThrow().reason());
        assertTrue(c.finishDrain(status));
        assertFalse(c.refreshRetryAvailability(c.listener(), true, 499));
        assertFalse(c.refreshRetryAvailability(c.listener(), false, 500));
        assertTrue(c.refreshRetryAvailability(c.listener(), true, 500));
        long version = c.state().orElseThrow().statusVersion();
        assertEquals(Availability.MAY_TRY, c.state().orElseThrow().availability());
        assertFalse(c.refreshRetryAvailability(c.listener(), true, 501));
        assertEquals(version, c.state().orElseThrow().statusVersion());
        var retry = enqueue(c, 4, Command.TRY, 501);
        assertTrue(c.begin(retry, P11TransitionControl.Gate.ALLOW, 501).isPresent());
    }

    @Test
    void retryAvailabilityCannotClearHeldWorkOrUnknownClock() {
        var c = play(0).control;
        refuse(c, 1, 0);
        assertFalse(c.refreshRetryAvailability(c.listener(), true, -1));
        assertEquals(Availability.WAIT_NOTIFY, c.state().orElseThrow().availability());
        assertTrue(c.refreshRetryAvailability(c.listener(), true, 0));
        var pending = enqueue(c, 2, Command.TRY, 0);
        assertFalse(c.refreshRetryAvailability(c.listener(), true, 10_000));
        assertTrue(c.begin(pending, P11TransitionControl.Gate.ALLOW, 0).isPresent());
        assertFalse(c.refreshRetryAvailability(c.listener(), true, 10_000));
    }

    @Test
    void terminalBeforeFrameUsesOneFinalPublicationAndRejectsStaleActor() {
        var f = play(0);
        var c = f.control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.callerCompleted(attempt, b.actorGeneration()));
        long version = c.state().orElseThrow().statusVersion();
        assertTrue(c.nativeFrame(attempt, b));
        assertEquals(version + 1, c.state().orElseThrow().statusVersion());
        assertEquals(Outcome.COMPLETED, c.state().orElseThrow().outcome());
        assertTrue(c.finishDrain(drain));
        assertFalse(c.openPlayScene(f.actor, Kind.DEATH));
        assertTrue(c.openPlayScene(b, Kind.DEATH));
        var next = enqueue(c, 2, Command.TRY, 500);
        var current = c.begin(next, P11TransitionControl.Gate.ALLOW, 500).orElseThrow();
        var stale = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertFalse(c.nativeFrame(current, stale));
    }

    @Test
    void actorReplacementCannotFabricateFreshNegativeProofOrMayTry() {
        var f = play(40);
        var c = f.control;
        var query = enqueue(c, 41, Command.STATUS, 0);
        f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        c.processStatus(query);
        assertEquals(40, c.fence());
        assertEquals(Outcome.BINDING, c.state().orElseThrow().outcome());
        assertTrue(c.finishDrain(query));
        assertEquals(P11TransitionControl.Offer.STALE,
                c.offer(c.listener(), request(c, 42, Command.TRY), 500));
        assertFalse(c.blockersEnded(c.listener(), true));
    }

    @Test
    void expectedFrameCannotRebindSupersededActorAtCallerOrSuccessorCompletion() {
        var f = play(0);
        var c = f.control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var b = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertTrue(c.nativeFrame(attempt, b));
        assertTrue(c.retainSuccessor(b, Kind.END));
        var next = f.identities.bindModel(f.identities.modelActor(f.actor.uuid(), 7), f.channel).orElseThrow();
        assertFalse(c.callerCompleted(attempt, b.actorGeneration()));
        assertEquals(P11TransitionControl.Disposition.UNKNOWN, c.disposition());
        assertEquals(Outcome.UNKNOWN, c.state().orElseThrow().outcome());
        assertFalse(c.activateSuccessor());
        assertTrue(f.identities.current(next));

        var g = play(0);
        var d = enqueue(g.control, 1, Command.TRY, 0);
        var a = g.control.begin(d, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        var target = g.identities.bindModel(g.identities.modelActor(g.actor.uuid(), 7), g.channel).orElseThrow();
        assertTrue(g.control.nativeFrame(a, target));
        assertTrue(g.control.retainSuccessor(target, Kind.END));
        assertTrue(g.control.callerCompleted(a, target.actorGeneration()));
        assertTrue(g.control.finishDrain(d));
        g.identities.bindModel(g.identities.modelActor(g.actor.uuid(), 7), g.channel).orElseThrow();
        assertFalse(g.control.activateSuccessor());
        assertEquals(P11TransitionControl.Disposition.UNKNOWN, g.control.disposition());
    }

    @Test
    void enterConfigClosesWithoutActorFrameThenAcceptsExactActorlessReturnScene() {
        var f = unstarted(0, 0, 0);
        var c = f.control;
        assertTrue(c.openServerScene(Scope.PLAY, Kind.ENTER_CONFIG));
        var initial = c.reserveServerDrain(c.listener()).orElseThrow();
        var attempt = c.begin(initial, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertFalse(c.nativeFrame(attempt, f.actor));
        assertTrue(c.startConfigurationObserved(attempt));
        var handoff = c.beginHandoff(c.listener(), Scope.CONFIG).orElseThrow();
        c.completeHandoff(handoff).orElseThrow();
        assertTrue(c.callerCompleted(attempt, 0));
        var actorless = f.identities.modelActorless(f.actor.uuid(), f.channel).orElseThrow();
        assertTrue(c.bindActorlessAfterConfiguration(actorless));
        assertTrue(c.openServerScene(Scope.CONFIG, Kind.RETURN_TO_WORLD));
        assertEquals(0, c.state().orElseThrow().requestSeq());
        assertEquals(Outcome.PENDING, c.state().orElseThrow().outcome());
        assertEquals(f.actor.connectionEpoch(), c.state().orElseThrow().connectionEpoch());
    }

    @Test
    void counterExhaustionClosesAdmissionWithoutWrapOrNewSceneReset() {
        var f = unstarted(Long.MAX_VALUE, 0, 0);
        var maxSequence = f.control;
        assertFalse(maxSequence.openPlayScene(f.actor, Kind.DEATH));
        assertEquals(P11TransitionControl.Disposition.EXHAUSTED, maxSequence.disposition());
        var sceneFixture = unstarted(0, Long.MAX_VALUE, 0);
        assertFalse(sceneFixture.control.openPlayScene(sceneFixture.actor, Kind.DEATH));
        var statusFixture = unstarted(0, 0, Long.MAX_VALUE - 1);
        var maxStatus = statusFixture.control;
        assertTrue(maxStatus.openPlayScene(statusFixture.actor, Kind.DEATH));
        assertEquals(Long.MAX_VALUE, maxStatus.state().orElseThrow().statusVersion());
        assertEquals(P11TransitionControl.Offer.CLOSED,
                maxStatus.offer(maxStatus.listener(), request(maxStatus, 1, Command.TRY), 0));
        assertTrue(maxStatus.reserveDrain(maxStatus.listener()).isEmpty());
        assertEquals(P11TransitionControl.Disposition.EXHAUSTED, maxStatus.disposition());
    }

    @Test
    void notificationAndInboxAlternateAndTryReplacesOnlyPureStatus() {
        var c = play(0).control;
        assertEquals(P11TransitionControl.Offer.RETAINED,
                c.offer(c.listener(), request(c, 1, Command.STATUS), 0));
        assertEquals(P11TransitionControl.Offer.COALESCED,
                c.offer(c.listener(), request(c, 1, Command.TRY), 0));
        assertEquals(P11TransitionControl.Service.INBOX, c.nextService());
        var drain = c.reserveDrain(c.listener()).orElseThrow();
        assertEquals(Command.TRY, drain.request().command());
        c.begin(drain, P11TransitionControl.Gate.ACTIVE_OPERATION, 0);
        c.finishDrain(drain);
        c.offer(c.listener(), request(c, 2, Command.STATUS), 0);
        assertEquals(P11TransitionControl.Service.NOTIFICATION, c.nextService());
        assertTrue(c.takeNotification(c.listener()).isPresent());
        assertEquals(P11TransitionControl.Service.INBOX, c.nextService());
        var query = c.reserveDrain(c.listener()).orElseThrow();
        c.processStatus(query);
        c.finishDrain(query);
        assertEquals(2, c.fence());
    }

    @Test
    void stoppedConnectionAndSlotCannotUseOldScheduleAndRetirementIsIdempotent() {
        var f = play(0);
        var c = f.control;
        var drain = enqueue(c, 1, Command.TRY, 0);
        assertTrue(f.identities.retireConnection(f.actor));
        assertTrue(c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).isEmpty());
        c.retire();
        c.retire();
        assertTrue(c.reserveDrain(c.listener()).isEmpty());
        assertFalse(c.finishDrain(drain));
        assertEquals(P11TransitionControl.Disposition.RETIRED, c.disposition());
        var slot = new P11FoundationSlot(limits(), f.identities);
        assertEquals(limits(), slot.limits());
        slot.retire();
        slot.retire();
        assertTrue(slot.retired());
        assertFalse(f.identities.current(f.actor));
    }

    @Test
    void protocolRejectsIllegalRoutesAndOutcomeCrossFieldsWithoutCodec() {
        assertThrows(IllegalArgumentException.class,
                () -> new Request(Scope.PLAY, 1, 1, 1, 0, Command.TRY, Kind.DEATH));
        assertThrows(IllegalArgumentException.class,
                () -> new Request(Scope.CONFIG, 1, 1, 0, 1, Command.TRY, Kind.ENTER_CONFIG));
        assertThrows(IllegalArgumentException.class,
                () -> new Request(Scope.PREPLAY, 1, 1, 1, 1, Command.TRY, Kind.JOIN));
        for (var outcome : Outcome.values()) {
            if (outcome != Outcome.NOT_STARTED) {
                assertThrows(IllegalArgumentException.class,
                        () -> new State(Scope.PLAY, 1, 1, 1, 1, 1, Kind.DEATH,
                                outcome, Availability.MAY_TRY, Reason.NONE, 0));
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> new State(Scope.PLAY, 1, 1, 1, 1, 1, Kind.ENTER_CONFIG,
                        Outcome.NATIVE_FRAME, Availability.DISABLED, Reason.NONE, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new State(Scope.PLAY, 1, 1, 1, 1, 1, Kind.DEATH,
                        Outcome.NOT_STARTED, Availability.WAIT_NOTIFY, Reason.NONE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new State(Scope.PLAY, 1, 1, 1, 1, 1, Kind.DEATH,
                        Outcome.UNKNOWN, Availability.DISABLED, Reason.NATIVE_FAILURE, 0));
    }

    @Test
    void terminalDeliveryMaskRetainsRepeatedRequestZeroWithoutAdvancingTaskOrFence() {
        var c = actorless(Kind.JOIN).control;
        c.takeNotification(c.listener()).orElseThrow();
        var task = c.reserveServerDrain(c.listener()).orElseThrow();
        assertTrue(c.passConfigurationTask(task, P11TransitionControl.Gate.ALLOW, 0));
        assertTrue(c.finishDrain(task));
        var pending = c.state().orElseThrow();
        long fence = c.fence();
        for (int i = 0; i < 100; i++) {
            assertEquals(P11TransitionControl.Offer.COALESCED,
                    c.offer(c.listener(), request(c, 0, Command.STATUS), i));
            assertFalse(c.hasService(false));
            assertEquals(P11TransitionControl.Service.NONE, c.nextService(false));
            assertSame(pending, c.state().orElseThrow());
            assertEquals(fence, c.fence());
            assertTrue(c.configurationTaskPassed());
        }
        assertTrue(c.hasService());
        assertEquals(P11TransitionControl.Service.NOTIFICATION, c.nextService());
        assertSame(pending, c.takeNotification(c.listener()).orElseThrow());
        assertFalse(c.hasService());
    }

    @Test
    void terminalDeliveryMaskStillServicesInboxAndDoesNotSpendNotificationPreference() {
        var c = play(0).control;
        assertEquals(P11TransitionControl.Offer.RETAINED,
                c.offer(c.listener(), request(c, 1, Command.STATUS), 0));
        assertTrue(c.hasService(false));
        assertEquals(P11TransitionControl.Service.INBOX, c.nextService(false));
        var drain = c.reserveDrain(c.listener()).orElseThrow();
        c.processStatus(drain).orElseThrow();
        assertTrue(c.finishDrain(drain));
        for (int i = 0; i < 100; i++) {
            assertFalse(c.hasService(false));
            assertEquals(P11TransitionControl.Service.NONE, c.nextService(false));
        }
        assertEquals(P11TransitionControl.Offer.RETAINED,
                c.offer(c.listener(), request(c, 2, Command.STATUS), 1));
        assertTrue(c.hasService(false));
        // The masked observations did not consume the notification's earned next turn.
        assertEquals(P11TransitionControl.Service.NOTIFICATION, c.nextService(true));
        assertEquals(1, c.takeNotification(c.listener()).orElseThrow().requestSeq());
        assertEquals(P11TransitionControl.Service.INBOX, c.nextService(false));
        var next = c.reserveDrain(c.listener()).orElseThrow();
        c.processStatus(next).orElseThrow();
        assertTrue(c.finishDrain(next));
        assertEquals(2, c.fence());
    }

    @Test
    void terminalDeliveryMaskDoesNotExtendPlayerlessExpiryOrMakeFreshAuthority() {
        var c = actorless(Kind.JOIN).control;
        var task = c.reserveServerDrain(c.listener()).orElseThrow();
        assertTrue(c.begin(task, P11TransitionControl.Gate.ACTIVE_CONTEXT, 100).isEmpty());
        assertTrue(c.finishDrain(task));
        for (int time : new int[] {101, 1000, 30_099}) {
            assertEquals(P11TransitionControl.Offer.COALESCED,
                    c.offer(c.listener(), request(c, 0, Command.STATUS), time));
            assertEquals(P11ControlBudgets.WaitResult.WAITING, c.observeWait(time));
            assertFalse(c.hasService(false));
        }
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, c.observeWait(30_100));
        assertEquals(Outcome.EXPIRED, c.state().orElseThrow().outcome());
        assertFalse(c.hasService(false));
        assertTrue(c.hasService(true));
        assertEquals(Outcome.EXPIRED, c.takeNotification(c.listener()).orElseThrow().outcome());
        assertTrue(c.reserveServerDrain(c.listener()).isEmpty());
        c.retire();
        assertFalse(c.hasService(false));
        assertFalse(c.hasService(true));
        assertEquals(P11TransitionControl.Service.NONE, c.nextService(false));
        assertEquals(P11TransitionControl.Service.NONE, c.nextService(true));
    }

    @Test
    void enterConfigurationNotificationStaysRetainedUntilTrueActorlessHandoff() {
        var f = unstarted(0, 0, 0);
        var c = f.control;
        assertTrue(c.openServerScene(Scope.PLAY, Kind.ENTER_CONFIG));
        c.takeNotification(c.listener()).orElseThrow();
        var drain = c.reserveServerDrain(c.listener()).orElseThrow();
        var attempt = c.begin(drain, P11TransitionControl.Gate.ALLOW, 0).orElseThrow();
        assertEquals(Outcome.RUNNING, c.takeNotification(c.listener()).orElseThrow().outcome());
        assertTrue(c.startConfigurationObserved(attempt));
        assertTrue(c.callerCompleted(attempt, 0));
        assertTrue(c.releaseCompletedDrain());
        assertEquals(Outcome.RUNNING, c.state().orElseThrow().outcome());
        assertEquals(P11TransitionControl.Offer.COALESCED,
                c.offer(c.listener(), request(c, 0, Command.STATUS), 1));
        assertFalse(c.hasService(false));
        assertEquals(P11TransitionControl.Service.NONE, c.nextService(false));
        var handoff = c.beginHandoff(c.listener(), Scope.CONFIG).orElseThrow();
        var actorless = f.identities.modelActorless(f.actor.uuid(), f.channel).orElseThrow();
        assertTrue(c.bindActorlessHandoff(handoff, actorless));
        c.completeHandoff(handoff).orElseThrow();
        assertEquals(Scope.CONFIG, c.state().orElseThrow().scope());
        assertEquals(Outcome.COMPLETED, c.state().orElseThrow().outcome());
        assertTrue(c.hasService(true));
        assertEquals(0, c.takeNotification(c.listener()).orElseThrow().actorGeneration());
    }

    @Test
    void finishConfigurationMaskEndsBeforeFinalRefusalNotificationInPreplay() {
        var c = actorless(Kind.RETURN_TO_WORLD).control;
        var early = c.reserveServerDrain(c.listener()).orElseThrow();
        assertTrue(c.passConfigurationTask(early, P11TransitionControl.Gate.ALLOW, 0));
        assertTrue(c.finishDrain(early));
        c.takeNotification(c.listener()).orElseThrow();
        assertEquals(P11TransitionControl.Offer.COALESCED,
                c.offer(c.listener(), request(c, 0, Command.STATUS), 1));
        assertFalse(c.hasService(false));
        var handoff = c.beginHandoff(c.listener(), Scope.PREPLAY).orElseThrow();
        c.completeHandoff(handoff).orElseThrow();
        var finalCheck = c.reserveServerDrain(c.listener()).orElseThrow();
        assertTrue(c.begin(finalCheck, P11TransitionControl.Gate.ACTIVE_OPERATION, 2).isEmpty());
        assertTrue(c.finishDrain(finalCheck));
        assertTrue(c.hasService(true));
        var refused = c.takeNotification(c.listener()).orElseThrow();
        assertEquals(Scope.PREPLAY, refused.scope());
        assertEquals(Outcome.NOT_STARTED, refused.outcome());
        assertEquals(Availability.WAIT_NOTIFY, refused.availability());
        assertEquals(Reason.ACTIVE_OPERATION, refused.reason());
        assertEquals(0, refused.actorGeneration());
        assertEquals(0, refused.requestSeq());
    }
}
