package com.yo1no.gramarye.magic.network;

import java.util.Objects;

/** Server-thread owner of P7 session admission, authorization, and coarse results. */
final class P7ServerAuthorizationDispatcher {
    private final P7ServerSessionService sessionService;
    private final P7ServerAccess serverAccess;
    private final P7AdvisoryTargetValidator targetValidator;
    private final P7ServerIntentResultSink resultSink;
    private final P7ServerDisconnectPort disconnectPort;

    P7ServerAuthorizationDispatcher(
            P7ServerSessionService sessionService,
            P7ServerAccess serverAccess,
            P7AdvisoryTargetValidator targetValidator,
            P7ServerIntentResultSink resultSink,
            P7ServerDisconnectPort disconnectPort) {
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
        this.serverAccess = Objects.requireNonNull(serverAccess, "serverAccess");
        this.targetValidator = Objects.requireNonNull(targetValidator, "targetValidator");
        this.resultSink = Objects.requireNonNull(resultSink, "resultSink");
        this.disconnectPort = Objects.requireNonNull(disconnectPort, "disconnectPort");
    }

    void dispatch(P7QueuedCastIntent queuedIntent) {
        Objects.requireNonNull(queuedIntent, "queuedIntent");
        var server = serverAccess.currentServer();
        if (server == null) {
            return;
        }
        if (!serverAccess.sameThread(server)) {
            throw new P7SemanticInvariantException(
                    "server authorization dispatch requires the server thread");
        }

        var identity = queuedIntent.sessionIdentity();
        if (!sessionService.isCurrentServer(server, identity.serverGeneration())) {
            return;
        }
        var session = sessionService.currentSession(identity);
        if (session.isEmpty()) {
            return;
        }
        long sequence = queuedIntent.sequence();
        if (!serverAccess.running(server)) {
            resultSink.accept(P7AdmissionDispositionMapper.serverUnavailable(
                    identity,
                    sequence,
                    session.orElseThrow().admissionState().sequenceState().expectedNext()));
            return;
        }
        var actor = serverAccess.currentPlayer(server, identity.authenticatedPlayerId());
        if (actor == null
                || !sessionService.matchesCurrentActor(server, identity, actor)) {
            resultSink.accept(P7AdmissionDispositionMapper.disconnected(
                    identity, sequence, false));
            return;
        }
        if (!sessionService.admissionOpen(server)) {
            resultSink.accept(P7AdmissionDispositionMapper.reloadInProgress(
                    identity,
                    sequence,
                    session.orElseThrow().admissionState().sequenceState().expectedNext()));
            return;
        }

        var authoritativeTick = serverAccess.authoritativeTick(server);
        var transition = sessionService.transition(
                server, identity, authoritativeTick, sequence);
        if (transition.isEmpty()) {
            return;
        }
        var decision = transition.orElseThrow();
        if (decision.disconnect()) {
            var result = P7AdmissionDispositionMapper.fromAdmissionSemantics(
                    identity, sequence, decision);
            resultSink.accept(result);
            disconnectPort.disconnect(server, actor, identity);
            return;
        }
        if (decision.outcome() != CastIntentAdmissionSemantics.Outcome.ELIGIBLE) {
            resultSink.accept(P7AdmissionDispositionMapper.fromAdmissionSemantics(
                    identity, sequence, decision));
            feedbackNormal(queuedIntent, server, actor,
                    decision.outcome() == CastIntentAdmissionSemantics.Outcome.RATE_LIMITED
                            ? P7ServerAuthorizationBoundary.NormalFeedback.RATE_LIMITED
                            : P7ServerAuthorizationBoundary.NormalFeedback.UNAVAILABLE);
            return;
        }
        if (!actor.isAlive() || actor.isSpectator()) {
            resultSink.accept(P7AdmissionDispositionMapper.unauthorizedAfterConsumption(
                    identity, sequence));
            feedbackNormal(queuedIntent, server, actor,
                    P7ServerAuthorizationBoundary.NormalFeedback.UNAVAILABLE);
            return;
        }

        if (queuedIntent.operation() instanceof P7CastOperation.NormalSpell) {
            dispatchNormal(server, actor, identity, sequence);
            return;
        }

        var intent = queuedIntent.intent();
        var aimHint = intent.aimHint().orElse(null);
        var entityHint = intent.entityHint().orElse(null);
        var validator = targetValidator;
        P7ServerAuthorizationBoundary.AdvisoryTargetCheck targetCheck =
                (currentServer, currentActor) -> validator.validate(
                        currentServer, currentActor, aimHint, entityHint);
        var disposition = Objects.requireNonNull(
                P7ServerAuthorizationBoundary.dispatch(
                        server, actor, intent.slot(), targetCheck),
                "root ingress disposition");
        resultSink.accept(P7AdmissionDispositionMapper.fromRootDisposition(
                identity, intent.sequence(), disposition));
    }

    private void dispatchNormal(net.minecraft.server.MinecraftServer server,
            net.minecraft.server.level.ServerPlayer actor, P7SessionIdentity identity, long sequence) {
        var receipt = P7ServerAuthorizationBoundary.normalReceipt(identity, sequence);
        var outcome = P7ServerAuthorizationBoundary.dispatchNormal(server, actor, receipt);
        if (outcome == P7ServerAuthorizationBoundary.NormalIngressDisposition.PENDING) {
            if (!receipt.deferred()) {
                throw new P7SemanticInvariantException("normal pending result has no bounded P5 capture");
            }
            return;
        }
        if (receipt.deferred()) {
            throw new P7SemanticInvariantException("normal deferred capture returned an immediate result");
        }
        receipt.immediateCompleted();
        if (serverAccess.currentPlayer(server, identity.authenticatedPlayerId()) != actor
                || !sessionService.matchesCurrentActor(server, identity, actor)) { return; }
        var disposition = switch (outcome) {
            case NOT_READY, UNAVAILABLE -> P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
            case CAPACITY, COOLDOWN, INSUFFICIENT_MANA ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case REJECTED -> P7ServerAuthorizationBoundary.AdmissionDisposition.UNAUTHORIZED_INTENT;
            case PENDING -> throw new P7SemanticInvariantException("pending normal outcome was not deferred");
        };
        resultSink.accept(P7AdmissionDispositionMapper.fromRootDisposition(identity, sequence, disposition));
        var feedback = switch (outcome) {
            case NOT_READY -> P7ServerAuthorizationBoundary.NormalFeedback.NOT_READY;
            case CAPACITY -> P7ServerAuthorizationBoundary.NormalFeedback.CAPACITY;
            case COOLDOWN -> P7ServerAuthorizationBoundary.NormalFeedback.COOLDOWN_ACTIVE;
            case INSUFFICIENT_MANA -> P7ServerAuthorizationBoundary.NormalFeedback.INSUFFICIENT_MANA;
            case UNAVAILABLE, REJECTED -> P7ServerAuthorizationBoundary.NormalFeedback.UNAVAILABLE;
            case PENDING -> throw new P7SemanticInvariantException("pending normal outcome has no immediate feedback");
        };
        P7NetworkComposition.normalFeedback(actor, feedback);
    }

    private void feedbackNormal(P7QueuedCastIntent queued,
            net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerPlayer actor,
            P7ServerAuthorizationBoundary.NormalFeedback feedback) {
        if (queued.operation() instanceof P7CastOperation.NormalSpell
                && serverAccess.currentPlayer(server, queued.sessionIdentity().authenticatedPlayerId()) == actor
                && sessionService.matchesCurrentActor(server, queued.sessionIdentity(), actor)) {
            P7NetworkComposition.normalFeedback(actor, feedback);
        }
    }
}
