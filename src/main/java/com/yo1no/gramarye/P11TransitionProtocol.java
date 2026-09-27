package com.yo1no.gramarye;

import java.util.Objects;

/** Closed semantic values only: this slice registers no transport or native caller. */
final class P11TransitionProtocol {
    enum Scope { CONFIG, PREPLAY, PLAY }
    enum Command { TRY, STATUS }
    enum Kind { DEATH, END, JOIN, RETURN_TO_WORLD, ENTER_CONFIG }
    enum Outcome {
        BINDING, PENDING, NOT_STARTED, RUNNING, NATIVE_FRAME, COMPLETED,
        NOT_APPLICABLE, FAULT, UNKNOWN, SUPERSEDED, EXPIRED
    }
    enum Availability { DISABLED, WAIT_NOTIFY, MAY_TRY }
    enum Reason {
        NONE, ACTIVE_OPERATION, ACTIVE_CONTEXT, ACTIVE_TRANSITION, NOT_APPLICABLE,
        SCENE_CHANGED, SOURCE_UNAVAILABLE, NATIVE_FAILURE, STATUS_UNAVAILABLE,
        WAIT_EXPIRED, CONTROL_EXHAUSTED, CONTROL_RATE_LIMIT, CONTROL_DISPATCH_BUSY
    }

    record Request(Scope scope, long connectionEpoch, long sceneSerial,
            long actorGeneration, long requestSeq, Command command, Kind kind) {
        Request {
            Objects.requireNonNull(command, "command");
            route(scope, connectionEpoch, sceneSerial, actorGeneration, kind);
            if (requestSeq < 0 || (command == Command.TRY && requestSeq == 0)
                    || (requestSeq == 0 && !serverKind(kind))
                    || (scope == Scope.CONFIG && kind == Kind.ENTER_CONFIG
                        && command != Command.STATUS)) {
                throw new IllegalArgumentException("P11_INVALID_REQUEST");
            }
        }
    }

    record State(Scope scope, long connectionEpoch, long sceneSerial,
            long actorGeneration, long requestSeq, long statusVersion, Kind kind,
            Outcome outcome, Availability availability, Reason reason,
            long targetActorGeneration) {
        State {
            route(scope, connectionEpoch, sceneSerial, actorGeneration, kind);
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(reason, "reason");
            if (requestSeq < 0 || statusVersion <= 0 || targetActorGeneration < 0
                    || (requestSeq == 0 && outcome != Outcome.BINDING && !serverKind(kind))) {
                throw new IllegalArgumentException("P11_INVALID_STATE_RANGE");
            }
            boolean valid = switch (outcome) {
                case BINDING -> requestSeq == 0 && disabled(availability, reason)
                        && targetActorGeneration == 0;
                case PENDING, RUNNING -> disabled(availability, reason)
                        && targetActorGeneration == 0;
                case NOT_STARTED -> targetActorGeneration == 0
                        && ((availability == Availability.MAY_TRY && reason == Reason.NONE)
                        || (availability == Availability.WAIT_NOTIFY && refusal(reason)));
                case NATIVE_FRAME -> kind != Kind.ENTER_CONFIG
                        && disabled(availability, reason) && targetActorGeneration > 0;
                case COMPLETED -> disabled(availability, reason)
                        && (kind == Kind.ENTER_CONFIG
                        ? targetActorGeneration == 0 : targetActorGeneration > 0);
                case NOT_APPLICABLE -> availability == Availability.DISABLED
                        && reason == Reason.NOT_APPLICABLE && targetActorGeneration == 0;
                case SUPERSEDED -> availability == Availability.DISABLED
                        && reason == Reason.SCENE_CHANGED && targetActorGeneration == 0;
                case FAULT -> availability == Availability.DISABLED
                        && (reason == Reason.SOURCE_UNAVAILABLE || reason == Reason.NATIVE_FAILURE)
                        && targetActorGeneration == 0;
                case UNKNOWN -> availability == Availability.DISABLED
                        && reason == Reason.STATUS_UNAVAILABLE && targetActorGeneration == 0;
                case EXPIRED -> availability == Availability.DISABLED
                        && (reason == Reason.WAIT_EXPIRED || reason == Reason.CONTROL_EXHAUSTED)
                        && targetActorGeneration == 0;
            };
            if (!valid || (scope == Scope.CONFIG && kind == Kind.ENTER_CONFIG
                    && (outcome == Outcome.BINDING || outcome == Outcome.PENDING
                    || outcome == Outcome.RUNNING || outcome == Outcome.NATIVE_FRAME))) {
                throw new IllegalArgumentException("P11_INVALID_STATE_COMBINATION");
            }
        }
    }

    static boolean serverKind(Kind kind) {
        return kind == Kind.JOIN || kind == Kind.RETURN_TO_WORLD || kind == Kind.ENTER_CONFIG;
    }

    static boolean refusal(Reason reason) {
        return reason == Reason.ACTIVE_OPERATION || reason == Reason.ACTIVE_CONTEXT
                || reason == Reason.ACTIVE_TRANSITION || reason == Reason.CONTROL_RATE_LIMIT
                || reason == Reason.CONTROL_DISPATCH_BUSY;
    }

    private static boolean disabled(Availability availability, Reason reason) {
        return availability == Availability.DISABLED && reason == Reason.NONE;
    }

    private static void route(Scope scope, long connection, long scene, long actor, Kind kind) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(kind, "kind");
        boolean actorValid = scope == Scope.PLAY ? actor > 0 : actor == 0;
        boolean kindValid = scope == Scope.PLAY
                ? kind == Kind.DEATH || kind == Kind.END || kind == Kind.ENTER_CONFIG
                : kind == Kind.JOIN || kind == Kind.RETURN_TO_WORLD
                    || (scope == Scope.CONFIG && kind == Kind.ENTER_CONFIG);
        if (connection <= 0 || scene <= 0 || !actorValid || !kindValid) {
            throw new IllegalArgumentException("P11_INVALID_ROUTE");
        }
    }

    private P11TransitionProtocol() { }
}
