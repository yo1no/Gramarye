package com.yo1no.gramarye;

import java.util.Objects;
import java.util.Optional;
import static com.yo1no.gramarye.P11TransitionProtocol.*;

/**
 * One exact connection's finite control ledger. Every synchronized section only moves
 * internal immutable values. No executor, transport, native body, logger or callback lives
 * here. An Attempt is a foundation observation, never a native/P5/P7 execution capability.
 */
final class P11TransitionControl {
    enum Offer { RETAINED, COALESCED, BUSY, STALE, RATE_LIMITED, CLOCK_UNAVAILABLE, CLOSED }
    enum Disposition { OPEN, UNKNOWN, EXPIRED, EXHAUSTED, RETIRED }
    enum Gate { ALLOW, ACTIVE_OPERATION, ACTIVE_CONTEXT, ACTIVE_TRANSITION, NOT_APPLICABLE }
    enum Service { NONE, INBOX, NOTIFICATION }

    private final P11IdentityOwner.ConnectionKey connection;
    private final P11ControlBudgets.TokenBucket tryRate;
    private final P11ControlBudgets.TokenBucket statusRate;
    private final long admissionWaitMillis;
    private P11ControlBudgets.AdmissionWait waiting;
    private P11IdentityOwner.CapturedIdentity bound;
    private Listener listener;
    private long listenerSerial;
    private long sceneSerial;
    private long statusVersion;
    private long fence;
    private long lastClientSequence;
    private Scope scope;
    private Kind kind;
    private long originalActor;
    private boolean freshProven;
    private boolean settled;
    private boolean serverPending;
    private boolean configurationTaskPassed;
    private long serverRequestSequence;
    private boolean notification;
    private boolean preferNotification;
    private long exactRateRejected;
    private Request inbox;
    private Drain drain;
    private Attempt attempt;
    private Handoff handoff;
    private Successor successor;
    private State state;
    private Disposition disposition = Disposition.OPEN;

    P11TransitionControl(P11IdentityOwner.CapturedIdentity identity,
            P11StartupLimits limits, long nowMillis) {
        this(identity, limits, nowMillis, 0, 0, 0);
    }

    /** Exhaustion fixtures exercise the real transitions in a non-live identity domain. */
    static P11TransitionControl isolatedAtCounters(P11IdentityOwner.CapturedIdentity identity,
            P11StartupLimits limits, long nowMillis, long sequence, long scene, long version) {
        if (!identity.isolatedModel()) {
            throw new IllegalArgumentException("P11_LIVE_COUNTER_SEED_FORBIDDEN");
        }
        return new P11TransitionControl(identity, limits, nowMillis, sequence, scene, version);
    }

    private P11TransitionControl(P11IdentityOwner.CapturedIdentity identity,
            P11StartupLimits limits, long nowMillis, long sequence, long scene, long version) {
        bound = Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(limits, "limits");
        if (sequence < 0 || scene < 0 || version < 0) {
            throw new IllegalArgumentException("negative control counter");
        }
        connection = identity.connection();
        lastClientSequence = sequence;
        fence = sequence;
        sceneSerial = scene;
        statusVersion = version;
        tryRate = new P11ControlBudgets.TokenBucket(
                limits.tryBurst(), limits.tryRefillPerSecond(), nowMillis);
        statusRate = new P11ControlBudgets.TokenBucket(
                limits.statusBurst(), limits.statusRefillPerSecond(), nowMillis);
        admissionWaitMillis = limits.admissionWaitMillis();
        waiting = new P11ControlBudgets.AdmissionWait(admissionWaitMillis);
        listener = new Listener(++listenerSerial, identity.actorGeneration() == 0
                ? Scope.CONFIG : Scope.PLAY);
        // Validation precedes the irreversible claim. An actor/phase change or retirement
        // cannot mint another owner to reset this exact connection's counters or buckets.
        if (!identity.currentBinding() || !connection.claimControl()) {
            throw new IllegalStateException("P11_CONTROL_OWNER_ALREADY_CLAIMED_OR_STALE");
        }
    }

    synchronized Listener listener() { return listener; }
    synchronized Disposition disposition() { return disposition; }
    synchronized Optional<State> state() { return Optional.ofNullable(state); }
    synchronized long fence() { return fence; }

    synchronized boolean openPlayScene(P11IdentityOwner.CapturedIdentity actor, Kind nextKind) {
        if (nextKind != Kind.DEATH && nextKind != Kind.END) {
            return false;
        }
        if (!canOpen() || actor != bound || !sameConnection(actor) || !actor.currentBinding()
                || actor.actorGeneration() <= 0
                || listener.scope != Scope.PLAY) {
            return false;
        }
        bound = actor;
        return open(Scope.PLAY, nextKind, actor.actorGeneration(), true);
    }

    synchronized boolean openServerScene(Scope nextScope, Kind nextKind) {
        if (!canOpen() || !serverKind(nextKind)
                || (state != null && state.outcome() != Outcome.COMPLETED)
                || (nextKind == Kind.ENTER_CONFIG
                    ? nextScope != Scope.PLAY || bound.actorGeneration() <= 0
                    : nextScope == Scope.PLAY || bound.actorGeneration() != 0)
                || listener.scope != nextScope) {
            return false;
        }
        return open(nextScope, nextKind,
                nextScope == Scope.PLAY ? bound.actorGeneration() : 0, false);
    }

    private boolean canOpen() {
        return disposition == Disposition.OPEN && bound.currentBinding()
                && connection.currentConnection() && !held()
                && (state == null || settled) && successor == null;
    }

    private boolean open(Scope nextScope, Kind nextKind, long actor, boolean fresh) {
        if (sceneSerial == Long.MAX_VALUE || statusVersion == Long.MAX_VALUE
                || lastClientSequence == Long.MAX_VALUE) {
            exhaust();
            return false;
        }
        sceneSerial++;
        scope = nextScope;
        kind = nextKind;
        originalActor = actor;
        freshProven = fresh;
        settled = false;
        serverPending = !fresh;
        configurationTaskPassed = false;
        serverRequestSequence = 0;
        exactRateRejected = 0;
        if (nextKind == Kind.JOIN || nextKind == Kind.RETURN_TO_WORLD) {
            // A genuinely new admission episode, never a query/retry/phase handoff.
            waiting = new P11ControlBudgets.AdmissionWait(admissionWaitMillis);
        }
        return publish(0, fresh ? Outcome.BINDING : Outcome.PENDING,
                Availability.DISABLED, Reason.NONE, 0);
    }

    synchronized Offer offer(Listener expected, Request request, long nowMillis) {
        Objects.requireNonNull(request, "request");
        if (disposition != Disposition.OPEN) { return Offer.CLOSED; }
        if (!validListener(expected)) { return Offer.STALE; }
        if (matchesSuccessor(request)) { return offerSuccessor(request); }
        if (!matches(request)) { return Offer.STALE; }
        if (request.requestSeq() == 0) {
            // An initial server task is retained from inception, not inferred from empty inbox.
            notification = true;
            return Offer.COALESCED;
        }
        if (state != null && request.requestSeq() == state.requestSeq()) {
            notification = true;
            return Offer.COALESCED;
        }
        if (request.requestSeq() <= fence || request.requestSeq() <= lastClientSequence) {
            return Offer.STALE;
        }
        if (held()) {
            notification = true;
            return sameHeld(request) ? Offer.COALESCED : Offer.BUSY;
        }
        if (!freshProven && !retryableTerminal()) { return Offer.BUSY; }
        if (!bound.currentBinding()) { return Offer.STALE; }
        var rate = (request.command() == Command.TRY ? tryRate : statusRate).take(nowMillis);
        if (rate == P11ControlBudgets.RateResult.CLOCK_UNAVAILABLE) {
            return Offer.CLOCK_UNAVAILABLE;
        }
        if (rate == P11ControlBudgets.RateResult.RATE_LIMITED) {
            if (request.command() == Command.TRY) {
                exactRateRejected = request.requestSeq();
            }
            return Offer.RATE_LIMITED;
        }
        if (inbox != null && inbox.command() == Command.TRY) {
            return sameRequest(inbox, request) ? Offer.COALESCED : Offer.BUSY;
        }
        boolean replaced = inbox != null;
        inbox = request;
        if (request.command() == Command.TRY) {
            // Preserve the proven fresh/settled provenance while the request is HELD.
            freshProven = true;
            if (!publish(request.requestSeq(), Outcome.PENDING,
                    Availability.DISABLED, Reason.NONE, 0)) {
                return Offer.CLOSED;
            }
        }
        return replaced ? Offer.COALESCED : Offer.RETAINED;
    }

    /** The inactive successor may be visible, but the old RUNNING/held request still owns admission. */
    private Offer offerSuccessor(Request request) {
        if (!successor.actor.currentBinding() || request.requestSeq() <= fence
                || request.requestSeq() <= lastClientSequence) { return Offer.STALE; }
        // Coalesce only a notification, not an executable or a negative receipt. After
        // activation STATUS may prove this n was never retained; it cannot execute n.
        notification = true;
        return request.command() == Command.STATUS ? Offer.COALESCED : Offer.BUSY;
    }

    /** Poll values are actor-free; the caller must arrange its own bounded scheduler. */
    synchronized Optional<Drain> reserveDrain(Listener expected) {
        if (disposition != Disposition.OPEN || !validListener(expected)
                || drain != null || handoff != null || inbox == null) {
            return Optional.empty();
        }
        drain = new Drain(expected, inbox, false);
        inbox = null;
        return Optional.of(drain);
    }

    synchronized Optional<Drain> reserveServerDrain(Listener expected) {
        if (disposition != Disposition.OPEN || !validListener(expected)
                || !serverPending || drain != null || handoff != null || attempt != null
                || (scope == Scope.CONFIG && kind == Kind.ENTER_CONFIG)) {
            return Optional.empty();
        }
        var request = new Request(scope, connection.epoch(), sceneSerial,
                originalActor, serverRequestSequence, Command.STATUS, kind);
        drain = new Drain(expected, request, true);
        return Optional.of(drain);
    }

    /** Early CONFIG admission only advances the existing task, never starts an actor body. */
    synchronized boolean passConfigurationTask(Drain reserved, Gate gate, long nowMillis) {
        if (!validDrain(reserved) || reserved.began || configurationTaskPassed
                || scope != Scope.CONFIG || (kind != Kind.JOIN && kind != Kind.RETURN_TO_WORLD)
                || attempt != null || handoff != null || !bound.currentBinding()
                || (!reserved.serverIssued && reserved.request.command() != Command.TRY)) {
            return false;
        }
        if (gate != Gate.ALLOW) {
            begin(reserved, gate, nowMillis);
            return false;
        }
        long sequence = reserved.request.requestSeq();
        if (!reserved.serverIssued && (sequence <= fence || sequence <= lastClientSequence
                || (!freshProven && !retryableTerminal()))) { return false; }
        if (!publish(sequence, Outcome.PENDING, Availability.DISABLED, Reason.NONE, 0)) {
            return false;
        }
        serverRequestSequence = sequence;
        fence = Math.max(fence, sequence);
        lastClientSequence = Math.max(lastClientSequence, sequence);
        configurationTaskPassed = true;
        serverPending = true;
        freshProven = false;
        settled = false;
        reserved.began = true;
        return true;
    }

    synchronized boolean configurationTaskPassed() { return configurationTaskPassed; }

    synchronized Optional<State> processStatus(Drain reserved) {
        if (!validDrain(reserved) || reserved.serverIssued
                || reserved.request.command() != Command.STATUS) {
            return Optional.empty();
        }
        long n = reserved.request.requestSeq();
        // This drain itself holds a STATUS, never a TRY. Every other reservation is checked.
        if (attempt != null || serverPending || handoff != null
                || !bound.currentBinding()
                || (inbox != null && inbox.command() == Command.TRY)
                || n <= fence || n <= lastClientSequence
                || (!freshProven && !retryableTerminal())) {
            return Optional.ofNullable(state);
        }
        var reason = exactRateRejected == n ? Reason.CONTROL_RATE_LIMIT
                : Reason.CONTROL_DISPATCH_BUSY;
        recordNotStarted(n, reason, -1);
        return Optional.ofNullable(state);
    }

    /** Pure admission observation. It never calls a native body or grants a live permit. */
    synchronized Optional<Attempt> begin(Drain reserved, Gate gate, long nowMillis) {
        Objects.requireNonNull(gate, "gate");
        if (!validDrain(reserved) || reserved.began || attempt != null
                || (!reserved.serverIssued && reserved.request.command() != Command.TRY)
                || handoff != null || !bound.currentBinding()) {
            return Optional.empty();
        }
        long n = reserved.request.requestSeq();
        if (!reserved.serverIssued && (n <= fence || n <= lastClientSequence
                || (!freshProven && !retryableTerminal()))) {
            return Optional.empty();
        }
        if (gate != Gate.ALLOW) {
            if (gate == Gate.NOT_APPLICABLE) {
                fence = Math.max(fence, n);
                lastClientSequence = Math.max(lastClientSequence, n);
                freshProven = false;
                serverPending = false;
                settled = true;
                publish(n, Outcome.NOT_APPLICABLE, Availability.DISABLED,
                        Reason.NOT_APPLICABLE, 0);
            } else {
                var reason = switch (gate) {
                    case ACTIVE_OPERATION -> Reason.ACTIVE_OPERATION;
                    case ACTIVE_CONTEXT -> Reason.ACTIVE_CONTEXT;
                    case ACTIVE_TRANSITION -> Reason.ACTIVE_TRANSITION;
                    default -> throw new IllegalStateException("unreachable gate");
                };
                recordNotStarted(n, reason, nowMillis);
            }
            reserved.began = true;
            return Optional.empty();
        }
        if (!publish(n, Outcome.RUNNING, Availability.DISABLED, Reason.NONE, 0)) {
            return Optional.empty();
        }
        reserved.began = true;
        freshProven = false;
        serverPending = false;
        settled = false;
        lastClientSequence = Math.max(lastClientSequence, n);
        fence = Math.max(fence, n);
        attempt = new Attempt(reserved.request, bound.actor(), reserved.serverIssued);
        return Optional.of(attempt);
    }

    private void recordNotStarted(long n, Reason reason, long nowMillis) {
        // Fence and record become visible under the same monitor, before any notification.
        fence = Math.max(fence, n);
        lastClientSequence = Math.max(lastClientSequence, n);
        freshProven = false;
        serverPending = false;
        settled = true;
        if (scope != Scope.PLAY) { waiting.refused(nowMillis); }
        publish(n, Outcome.NOT_STARTED, Availability.WAIT_NOTIFY, reason, 0);
    }

    synchronized boolean finishDrain(Drain reserved) {
        if (drain != reserved || reserved == null) { return false; }
        if (!reserved.began
                && (reserved.serverIssued || reserved.request.command() == Command.TRY)) {
            return false;
        }
        drain = null;
        return true;
    }

    synchronized boolean submissionFailed(Drain reserved) {
        if (!validDrain(reserved)) { return false; }
        // Even a surprising scheduler failure cannot leave a later executable orphan.
        markUnknown(reserved.request.requestSeq());
        drain = null;
        return true;
    }

    /** Admission failed before any native frame/caller terminal; retain its non-retryable record. */
    synchronized boolean abortAdmission(Drain reserved) {
        if (disposition == Disposition.RETIRED || reserved == null || drain != reserved
                || reserved.listener != listener || !matches(reserved.request) || handoff != null
                || attempt != null && (attempt.frameActor != null || attempt.callerTerminal
                    || !sameRequest(attempt.request, reserved.request))) {
            return false;
        }
        boolean accepted = attempt != null;
        if (attempt != null) { attempt.finished = true; attempt = null; }
        drain = null;
        successor = null;
        serverPending = false;
        freshProven = false;
        settled = false;
        disposition = Disposition.UNKNOWN;
        long n = reserved.request.requestSeq();
        fence = Math.max(fence, n);
        lastClientSequence = Math.max(lastClientSequence, n);
        publish(n, accepted ? Outcome.FAULT : Outcome.UNKNOWN, Availability.DISABLED,
                accepted ? Reason.SOURCE_UNAVAILABLE : Reason.STATUS_UNAVAILABLE, 0);
        return true;
    }

    synchronized boolean nativeFrame(Attempt expected, P11IdentityOwner.CapturedIdentity next) {
        if (!validAttempt(expected) || kind == Kind.ENTER_CONFIG || !sameConnection(next)
                || !next.currentBinding()
                || next.actorGeneration() <= originalActor
                || (expected.callerTerminal && expected.targetGeneration != next.actorGeneration())
                || expected.frameActor != null) {
            return false;
        }
        expected.frameActor = next;
        settleIfComplete(expected);
        publish(expected.request.requestSeq(), expected.callerTerminal
                        ? Outcome.COMPLETED : Outcome.NATIVE_FRAME,
                Availability.DISABLED, Reason.NONE, next.actorGeneration());
        return true;
    }

    synchronized boolean callerCompleted(Attempt expected, long targetGeneration) {
        if (!validAttempt(expected) || expected.callerTerminal
                || (kind == Kind.ENTER_CONFIG ? targetGeneration != 0
                    : targetGeneration <= originalActor)
                || (expected.frameActor != null
                    && expected.frameActor.actorGeneration() != targetGeneration)) {
            return false;
        }
        if (expected.frameActor != null && !expected.frameActor.currentBinding()) {
            markUnknown(expected.request.requestSeq());
            return false;
        }
        expected.callerTerminal = true;
        expected.targetGeneration = targetGeneration;
        settleIfComplete(expected);
        // ENTER_CONFIG has no B frame: native caller return alone is not a CONFIG terminal.
        if (kind != Kind.ENTER_CONFIG || expected.finished) {
            publish(expected.request.requestSeq(), Outcome.COMPLETED,
                    Availability.DISABLED, Reason.NONE, targetGeneration);
        }
        return true;
    }

    synchronized boolean startConfigurationObserved(Attempt expected) {
        if (!validAttempt(expected) || kind != Kind.ENTER_CONFIG || expected.startConfiguration) {
            return false;
        }
        expected.startConfiguration = true;
        settleIfComplete(expected);
        if (expected.finished) {
            publish(expected.request.requestSeq(), Outcome.COMPLETED,
                    Availability.DISABLED, Reason.NONE, 0);
        }
        return true;
    }

    private void settleIfComplete(Attempt expected) {
        boolean matched = kind == Kind.ENTER_CONFIG
                ? expected.startConfiguration && scope == Scope.CONFIG
                : expected.frameActor != null
                    && expected.frameActor.actorGeneration() == expected.targetGeneration;
        if (!expected.callerTerminal || !matched) { return; }
        if (expected.frameActor != null) { bound = expected.frameActor; }
        expected.finished = true;
        attempt = null;
        settled = true;
    }

    synchronized boolean fault(Attempt expected, Reason reason) {
        if (!validAttempt(expected)
                || (reason != Reason.NATIVE_FAILURE && reason != Reason.SOURCE_UNAVAILABLE)) {
            return false;
        }
        expected.finished = true;
        attempt = null;
        successor = null;
        freshProven = false;
        settled = false;
        disposition = Disposition.UNKNOWN;
        return publish(expected.request.requestSeq(), Outcome.FAULT,
                Availability.DISABLED, reason, 0);
    }

    synchronized void observationLost() {
        if (disposition == Disposition.RETIRED || state == null) { return; }
        markUnknown(state.requestSeq());
    }

    private void markUnknown(long n) {
        disposition = Disposition.UNKNOWN;
        freshProven = false;
        settled = false;
        successor = null;
        if (n != 0 || serverKind(kind)) {
            publish(n, Outcome.UNKNOWN, Availability.DISABLED, Reason.STATUS_UNAVAILABLE, 0);
        }
    }

    synchronized Optional<Handoff> beginHandoff(Listener expected, Scope target) {
        Objects.requireNonNull(target, "target");
        if (!validListener(expected) || state == null) { return Optional.empty(); }
        boolean forward = listener.scope == Scope.CONFIG && target == Scope.PREPLAY
                && kind != Kind.ENTER_CONFIG
                || listener.scope == Scope.PREPLAY && target == Scope.PLAY
                || listener.scope == Scope.PLAY && target == Scope.CONFIG
                    && kind == Kind.ENTER_CONFIG && attempt != null;
        if (!validListener(expected) || disposition != Disposition.OPEN
                || handoff != null || !forward || listenerSerial == Long.MAX_VALUE) {
            return Optional.empty();
        }
        // Queued work transfers into this reservation before the old listener is retired.
        Request transfer = drain != null && !drain.began ? drain.request : inbox;
        boolean serverTransfer = drain != null && !drain.began && drain.serverIssued;
        handoff = new Handoff(target, transfer, serverTransfer);
        drain = null;
        inbox = null;
        listener = null;
        return Optional.of(handoff);
    }

    synchronized Optional<Listener> completeHandoff(Handoff reserved) {
        if (reserved == null || handoff != reserved || disposition != Disposition.OPEN
                || !connection.currentConnection()) {
            return Optional.empty();
        }
        listener = new Listener(++listenerSerial, reserved.target);
        if (scope == Scope.CONFIG && reserved.target == Scope.PREPLAY) {
            scope = Scope.PREPLAY;
        } else if (kind == Kind.ENTER_CONFIG && reserved.target == Scope.CONFIG) {
            scope = Scope.CONFIG;
            originalActor = 0;
        }
        if (reserved.request != null && !reserved.serverIssued) {
            var old = reserved.request;
            inbox = new Request(scope, connection.epoch(), sceneSerial, originalActor,
                    old.requestSeq(), old.command(), kind);
        }
        handoff = null;
        if (kind == Kind.ENTER_CONFIG && scope == Scope.CONFIG) {
            if (attempt != null && attempt.callerTerminal) {
                var completing = attempt;
                settleIfComplete(completing);
                publish(completing.request.requestSeq(), Outcome.COMPLETED,
                        Availability.DISABLED, Reason.NONE, 0);
            } else {
                publish(state.requestSeq(), Outcome.UNKNOWN,
                        Availability.DISABLED, Reason.STATUS_UNAVAILABLE, 0);
            }
        } else if (state != null) {
            publish(state.requestSeq(), state.outcome(), state.availability(),
                    state.reason(), state.targetActorGeneration());
        }
        return Optional.of(listener);
    }

    /** Same connection, exact newly installed actorless listener; no new control owner. */
    synchronized boolean bindActorlessHandoff(Handoff reserved,
            P11IdentityOwner.CapturedIdentity next) {
        if (reserved == null || handoff != reserved || !sameConnection(next)
                || !next.currentBinding() || next.actorGeneration() != 0
                || reserved.target == Scope.PLAY) { return false; }
        bound = next;
        return true;
    }

    synchronized P11IdentityOwner.CapturedIdentity binding() { return bound; }

    synchronized boolean executableHeld() { return held(); }

    /** CONFIG protocol completion can park after PREPLAY was already reserved. */
    synchronized boolean rebindParked(P11IdentityOwner.CapturedIdentity next) {
        if (disposition != Disposition.OPEN || scope != Scope.PREPLAY || held()
                || !sameConnection(next) || !next.currentBinding() || next.actorGeneration() != 0) {
            return false;
        }
        bound = next;
        return true;
    }

    synchronized boolean releaseCompletedDrain() {
        if (drain == null) { return true; }
        return drain.began && finishDrain(drain);
    }

    synchronized boolean hasService() { return hasService(true); }

    synchronized boolean hasService(boolean notificationsAllowed) {
        return disposition != Disposition.RETIRED && drain == null && handoff == null
                && (notificationsAllowed && notification || disposition == Disposition.OPEN && inbox != null);
    }

    synchronized boolean retainSuccessor(P11IdentityOwner.CapturedIdentity actor, Kind nextKind) {
        if (disposition != Disposition.OPEN || successor != null || attempt == null
                || attempt.frameActor != actor || !sameConnection(actor) || !actor.currentBinding()
                || (nextKind != Kind.DEATH && nextKind != Kind.END)) {
            return false;
        }
        if (sceneSerial == Long.MAX_VALUE || statusVersion == Long.MAX_VALUE
                || lastClientSequence == Long.MAX_VALUE) {
            exhaust();
            return false;
        }
        var binding = new State(Scope.PLAY, connection.epoch(), sceneSerial + 1, actor.actorGeneration(),
                0, ++statusVersion, nextKind, Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0);
        successor = new Successor(actor, binding);
        return true;
    }

    /** Ordered before B's original death/credits packet; does not replace the old receipt. */
    synchronized Optional<State> successorBinding() {
        return successor == null ? Optional.empty() : Optional.of(successor.binding);
    }

    synchronized boolean bindActorlessAfterConfiguration(
            P11IdentityOwner.CapturedIdentity authenticated) {
        if (disposition != Disposition.OPEN || !settled || held()
                || kind != Kind.ENTER_CONFIG || scope != Scope.CONFIG
                || !sameConnection(authenticated) || !authenticated.currentBinding()
                || authenticated.actorGeneration() != 0) {
            return false;
        }
        bound = authenticated;
        return true;
    }

    synchronized boolean activateSuccessor() {
        if (successor == null || !settled || held() || disposition != Disposition.OPEN
                || listener == null || listener.scope != Scope.PLAY) {
            return false;
        }
        var next = successor;
        if (!next.actor.currentBinding() || !connection.currentConnection()) {
            markUnknown(state.requestSeq());
            return false;
        }
        successor = null;
        bound = next.actor;
        // This scene was already published before B's native screen. Never allocate it again.
        sceneSerial = next.binding.sceneSerial();
        scope = Scope.PLAY;
        kind = next.binding.kind();
        originalActor = next.actor.actorGeneration();
        freshProven = true;
        settled = false;
        serverPending = false;
        configurationTaskPassed = false;
        serverRequestSequence = 0;
        exactRateRejected = 0;
        return publish(0, Outcome.BINDING, Availability.DISABLED, Reason.NONE, 0);
    }

    synchronized boolean refreshRetryAvailability(Listener expected, boolean clear, long nowMillis) {
        if (!validListener(expected) || disposition != Disposition.OPEN || held()
                || !bound.currentBinding() || !retryableTerminal() || !clear
                || state.availability() != Availability.WAIT_NOTIFY
                || tryRate.availability(nowMillis) != P11ControlBudgets.RateResult.ACCEPTED) {
            return false;
        }
        return publish(state.requestSeq(), Outcome.NOT_STARTED, Availability.MAY_TRY, Reason.NONE, 0);
    }

    synchronized boolean blockersEnded(Listener expected, boolean recheckedClear) {
        if (!validListener(expected) || disposition != Disposition.OPEN || held()
                || !bound.currentBinding() || !retryableTerminal() || !recheckedClear) {
            return false;
        }
        return publish(state.requestSeq(), Outcome.NOT_STARTED,
                Availability.MAY_TRY, Reason.NONE, 0);
    }

    synchronized P11ControlBudgets.WaitResult observeWait(long nowMillis) {
        if (scope == Scope.PLAY || disposition == Disposition.RETIRED
                || (settled && state != null && state.outcome() == Outcome.COMPLETED)) {
            // No active playerless admission episode remains for a caller to expire.
            return P11ControlBudgets.WaitResult.NOT_STARTED;
        }
        var result = waiting.observe(nowMillis);
        if (result == P11ControlBudgets.WaitResult.EXPIRED
                && disposition == Disposition.OPEN && scope != Scope.PLAY) {
            publish(state.requestSeq(), Outcome.EXPIRED,
                    Availability.DISABLED, Reason.WAIT_EXPIRED, 0);
            disposition = Disposition.EXPIRED;
            retireExecutable();
        }
        return result;
    }

    synchronized Service nextService() { return nextService(true); }

    synchronized Service nextService(boolean notificationsAllowed) {
        if (disposition == Disposition.RETIRED || drain != null || handoff != null) {
            return Service.NONE;
        }
        boolean hasInbox = disposition == Disposition.OPEN && inbox != null;
        if (notificationsAllowed && notification && (!hasInbox || preferNotification)) {
            preferNotification = false;
            return Service.NOTIFICATION;
        }
        if (hasInbox) {
            preferNotification = true;
            return Service.INBOX;
        }
        return Service.NONE;
    }

    synchronized Optional<State> takeNotification(Listener expected) {
        if (!validListener(expected) || !notification || disposition == Disposition.RETIRED) {
            return Optional.empty();
        }
        notification = false;
        return Optional.ofNullable(state);
    }

    synchronized void retire() {
        disposition = Disposition.RETIRED;
        retireExecutable();
        notification = false;
        listener = null;
        bound = null;
    }

    private void retireExecutable() {
        if (attempt != null) { attempt.finished = true; }
        inbox = null;
        drain = null;
        attempt = null;
        handoff = null;
        successor = null;
        freshProven = false;
        settled = false;
        serverPending = false;
        // This control owner owns no data roots. Retirement cannot release another ledger.
    }

    private boolean held() {
        return drain != null || attempt != null || handoff != null || serverPending
                || (inbox != null && inbox.command() == Command.TRY);
    }

    private boolean sameHeld(Request request) {
        return inbox != null && sameRequest(inbox, request)
                || drain != null && sameRequest(drain.request, request)
                || attempt != null && sameRequest(attempt.request, request);
    }

    private static boolean sameRequest(Request left, Request right) {
        return left.scope() == right.scope() && left.connectionEpoch() == right.connectionEpoch()
                && left.sceneSerial() == right.sceneSerial()
                && left.actorGeneration() == right.actorGeneration()
                && left.requestSeq() == right.requestSeq() && left.kind() == right.kind();
    }

    private boolean matches(Request request) {
        return state != null && request.scope() == scope
                && request.connectionEpoch() == connection.epoch()
                && request.sceneSerial() == sceneSerial
                && request.actorGeneration() == originalActor && request.kind() == kind;
    }

    private boolean matchesSuccessor(Request request) {
        if (successor == null) { return false; }
        var binding = successor.binding;
        return request.scope() == binding.scope() && request.connectionEpoch() == binding.connectionEpoch()
                && request.sceneSerial() == binding.sceneSerial()
                && request.actorGeneration() == binding.actorGeneration() && request.kind() == binding.kind();
    }

    private boolean validListener(Listener expected) {
        return expected != null && expected == listener && connection.currentConnection();
    }

    private boolean validDrain(Drain reserved) {
        return disposition == Disposition.OPEN && reserved != null && reserved == drain
                && validListener(reserved.listener) && matches(reserved.request);
    }

    private boolean validAttempt(Attempt expected) {
        return disposition == Disposition.OPEN && expected != null
                && connection.currentConnection() && attempt == expected && !expected.finished;
    }

    private boolean sameConnection(P11IdentityOwner.CapturedIdentity other) {
        return other != null && other.connection() == connection;
    }

    private boolean retryableTerminal() {
        return settled && state != null && state.outcome() == Outcome.NOT_STARTED;
    }

    private boolean publish(long request, Outcome outcome, Availability availability,
            Reason reason, long target) {
        if (statusVersion == Long.MAX_VALUE) { exhaust(); return false; }
        state = new State(scope, connection.epoch(), sceneSerial, originalActor,
                request, ++statusVersion, kind, outcome, availability, reason, target);
        notification = true;
        return true;
    }

    private void exhaust() {
        disposition = Disposition.EXHAUSTED;
        retireExecutable();
    }

    static final class Listener {
        private final long serial;
        private final Scope scope;
        private Listener(long serial, Scope scope) { this.serial = serial; this.scope = scope; }
        long serial() { return serial; }
        Scope scope() { return scope; }
    }

    static final class Drain {
        private final Listener listener;
        private final Request request;
        private final boolean serverIssued;
        private boolean began;
        private Drain(Listener listener, Request request, boolean serverIssued) {
            this.listener = listener; this.request = request; this.serverIssued = serverIssued;
        }
        Request request() { return request; }
    }

    static final class Attempt {
        private final Request request;
        private final P11IdentityOwner.ActorKey originalActor;
        private final boolean serverIssued;
        private boolean finished;
        private boolean callerTerminal;
        private boolean startConfiguration;
        private long targetGeneration;
        private P11IdentityOwner.CapturedIdentity frameActor;
        private Attempt(Request request, P11IdentityOwner.ActorKey originalActor, boolean serverIssued) {
            this.request = request; this.originalActor = originalActor; this.serverIssued = serverIssued;
        }
        long requestSeq() { return request.requestSeq(); }
        boolean serverIssued() { return serverIssued; }
    }

    static final class Handoff {
        private final Scope target;
        private final Request request;
        private final boolean serverIssued;
        private Handoff(Scope target, Request request, boolean serverIssued) {
            this.target = target; this.request = request; this.serverIssued = serverIssued;
        }
    }

    private record Successor(P11IdentityOwner.CapturedIdentity actor, State binding) { }
}
