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
        var identities = P11IdentityOwner.isolatedModel(4);
        var channel = identities.modelConnection();
        var actor = identities.bindModel(identities.modelActor(UUID.randomUUID(), 7), channel).orElseThrow();
        var control = P11TransitionControl.isolatedAtCounters(actor, limits(), 0, sequence, 0, 0);
        assertTrue(control.openPlayScene(actor, Kind.DEATH));
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
        var f = play(0);
        var c = new P11TransitionControl(f.actor, limits(), 0);
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
        var f = play(0);
        var maxSequence = P11TransitionControl.isolatedAtCounters(
                f.actor, limits(), 0, Long.MAX_VALUE, 0, 0);
        assertFalse(maxSequence.openPlayScene(f.actor, Kind.DEATH));
        assertEquals(P11TransitionControl.Disposition.EXHAUSTED, maxSequence.disposition());
        var maxScene = P11TransitionControl.isolatedAtCounters(
                f.actor, limits(), 0, 0, Long.MAX_VALUE, 0);
        assertFalse(maxScene.openPlayScene(f.actor, Kind.DEATH));
        var maxStatus = P11TransitionControl.isolatedAtCounters(
                f.actor, limits(), 0, 0, 0, Long.MAX_VALUE - 1);
        assertTrue(maxStatus.openPlayScene(f.actor, Kind.DEATH));
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
}
