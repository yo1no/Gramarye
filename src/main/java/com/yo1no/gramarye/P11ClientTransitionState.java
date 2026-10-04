package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

/** Client-thread, scalar-only projection. No server permission is produced here. */
final class P11ClientTransitionState {
    private State current;
    private State successor;
    private long successorRequest;
    private long lastSequence;
    private long request;
    private long frameTarget;
    private long appliedTarget;
    private long boundActor;
    private boolean pending;
    private boolean started;
    private boolean settled;
    private boolean configurationStarted;
    private boolean neutral;
    private boolean exhausted;

    P11ClientTransitionState() { this(0); }

    P11ClientTransitionState(long initialSequence) {
        if (initialSequence < 0) { throw new IllegalArgumentException("P11_NEGATIVE_SEQUENCE"); }
        lastSequence = initialSequence;
    }

    State current() { return current; }
    State successor() { return successor; }
    long successorRequest() { return successorRequest; }
    long request() { return request; }
    long frameTarget() { return frameTarget; }
    long boundActor() { return boundActor; }
    boolean pending() { return pending && !settled; }
    boolean blocksCast() { return current != null && !settled; }
    boolean settled() { return settled; }

    boolean accept(State state) {
        if (current == null) {
            if (!initial(state)) { return false; }
            install(state);
            return true;
        }
        if (state.connectionEpoch() != current.connectionEpoch()
                || state.sceneSerial() < current.sceneSerial()) { return false; }
        if (state.sceneSerial() > current.sceneSerial()) {
            if (!initial(state) || !nextActor(state)) { return false; }
            if (!settled) {
                if (current.outcome() == Outcome.FAULT || current.outcome() == Outcome.EXPIRED) {
                    return false;
                }
                if (successor == null) {
                    successor = state;
                    return true;
                }
                return false;
            }
            install(state);
            return true;
        }
        if (settled || state.kind() != current.kind()
                || state.statusVersion() <= current.statusVersion()
                || state.requestSeq() != request || !phaseMatches(state)) { return false; }
        if (started && (state.outcome() == Outcome.BINDING
                || state.outcome() == Outcome.PENDING
                || state.outcome() == Outcome.NOT_STARTED)) { return false; }
        if (state.outcome() == Outcome.NATIVE_FRAME && frameTarget != 0
                && frameTarget != state.targetActorGeneration()) { return false; }
        boolean wasRetry = retryAvailable();
        current = state;
        pending = state.outcome() != Outcome.BINDING || request != 0 || serverKind(state.kind());
        if (state.outcome() == Outcome.RUNNING || state.outcome() == Outcome.NATIVE_FRAME
                || state.outcome() == Outcome.UNKNOWN || state.outcome() == Outcome.COMPLETED) {
            started = true;
        }
        if (state.outcome() == Outcome.NATIVE_FRAME) {
            frameTarget = state.targetActorGeneration();
        }
        if (!wasRetry || !retryAvailable()) { neutral = false; }
        if (state.outcome() == Outcome.FAULT || state.outcome() == Outcome.EXPIRED) {
            successor = null;
            successorRequest = 0;
        } else if (state.outcome() == Outcome.NOT_APPLICABLE
                || state.outcome() == Outcome.SUPERSEDED) {
            settled = true;
            pending = false;
            successor = null;
            successorRequest = 0;
        }
        settleIfComplete();
        return true;
    }

    Request firstTry(Kind kind) {
        if (untriggeredSuccessor(kind) && appliedTarget == successor.actorGeneration()) {
            long sequence = nextSequence();
            if (sequence == 0) { return null; }
            successorRequest = sequence; // Original native trigger, never activation-driven.
            return new Request(successor.scope(), successor.connectionEpoch(), successor.sceneSerial(),
                    successor.actorGeneration(), sequence, Command.TRY, successor.kind());
        }
        if (current == null || settled || pending || current.kind() != kind
                || current.scope() != Scope.PLAY || current.outcome() != Outcome.BINDING
                || kind != Kind.DEATH && kind != Kind.END) { return null; }
        return newTry();
    }

    Request retry() {
        if (!retryAvailable() || !neutral) { return null; }
        return newTry();
    }

    private Request newTry() {
        long sequence = nextSequence();
        if (sequence == 0) { return null; }
        request = sequence;
        pending = true; // Before the caller can submit to transport or reenter a screen.
        started = false;
        neutral = false;
        frameTarget = 0;
        appliedTarget = 0;
        return request(Command.TRY);
    }

    private long nextSequence() {
        if (exhausted || lastSequence == Long.MAX_VALUE) {
            exhausted = true;
            return 0;
        }
        return ++lastSequence;
    }

    boolean untriggeredSuccessor(Kind kind) {
        return successor != null && successor.kind() == kind && successorRequest == 0
                && !settled && current.outcome() != Outcome.FAULT
                && current.outcome() != Outcome.EXPIRED;
    }

    Request status() {
        return current == null || settled || !pending ? null : request(Command.STATUS);
    }

    private Request request(Command command) {
        return new Request(current.scope(), current.connectionEpoch(), current.sceneSerial(),
                current.actorGeneration(), request, command, current.kind());
    }

    boolean retryAvailable() {
        return current != null && !settled && !exhausted
                && current.outcome() == Outcome.NOT_STARTED
                && current.requestSeq() == request
                && current.availability() == Availability.MAY_TRY
                && !(current.scope() == Scope.CONFIG && current.kind() == Kind.ENTER_CONFIG);
    }

    void activationNeutral() {
        if (retryAvailable()) { neutral = true; }
    }

    boolean retryArmed() { return retryAvailable() && neutral; }

    boolean expectFrame(long scene, long sequence, long target, boolean login) {
        return current != null && !settled && frameTarget > 0
                && scene == current.sceneSerial() && sequence == request
                && target == frameTarget && login == (current.kind() == Kind.JOIN
                        || current.kind() == Kind.RETURN_TO_WORLD);
    }

    void frameApplied(long scene, long sequence, long target, boolean login) {
        if (expectFrame(scene, sequence, target, login)) {
            appliedTarget = target;
            settleIfComplete();
        }
    }

    void configurationStarted() {
        if (current != null && current.kind() == Kind.ENTER_CONFIG && !settled) {
            configurationStarted = true;
            settleIfComplete();
        }
    }

    private void settleIfComplete() {
        if (current == null || current.outcome() != Outcome.COMPLETED) { return; }
        if (current.kind() == Kind.ENTER_CONFIG) {
            if (!configurationStarted || current.scope() != Scope.CONFIG) { return; }
            boundActor = 0;
        } else {
            if (appliedTarget == 0 || appliedTarget != frameTarget
                    || appliedTarget != current.targetActorGeneration()) { return; }
            boundActor = appliedTarget;
        }
        settled = true;
        pending = false;
        neutral = false;
        if (successor != null) {
            State next = successor;
            long retainedRequest = successorRequest;
            successor = null;
            successorRequest = 0;
            if (nextActor(next)) {
                install(next);
                if (retainedRequest != 0) {
                    request = retainedRequest;
                    pending = true;
                }
            }
        }
    }

    private boolean phaseMatches(State state) {
        if (state.scope() == current.scope()) {
            return state.actorGeneration() == current.actorGeneration();
        }
        if (current.scope() == Scope.CONFIG && state.scope() == Scope.PREPLAY) {
            return current.kind() == Kind.JOIN || current.kind() == Kind.RETURN_TO_WORLD;
        }
        return current.scope() == Scope.PLAY && state.scope() == Scope.CONFIG
                && current.kind() == Kind.ENTER_CONFIG && configurationStarted;
    }

    private boolean nextActor(State next) {
        if (next.scope() != Scope.PLAY) {
            return current.kind() == Kind.ENTER_CONFIG && configurationStarted
                    && next.kind() == Kind.RETURN_TO_WORLD;
        }
        long expected = settled ? boundActor : frameTarget;
        return expected > 0 && next.actorGeneration() == expected;
    }

    private static boolean initial(State state) {
        return state.requestSeq() == 0 && (state.outcome() == Outcome.BINDING
                || state.outcome() == Outcome.PENDING && serverKind(state.kind()));
    }

    private void install(State state) {
        current = state;
        successor = null;
        successorRequest = 0;
        request = state.requestSeq();
        frameTarget = 0;
        appliedTarget = 0;
        pending = state.outcome() != Outcome.BINDING || serverKind(state.kind());
        started = false;
        settled = false;
        configurationStarted = false;
        neutral = false;
        if (boundActor == 0 && state.scope() == Scope.PLAY) {
            boundActor = state.actorGeneration();
        }
    }
}
