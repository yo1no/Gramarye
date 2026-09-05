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

        var identity = new P7SessionIdentity(
                queuedIntent.authenticatedPlayerId(),
                queuedIntent.connectionEpoch());
        var session = sessionService.currentSession(identity);
        if (session.isEmpty()) {
            return;
        }
        var intent = queuedIntent.intent();
        if (!serverAccess.running(server)) {
            resultSink.accept(P7AdmissionDispositionMapper.serverUnavailable(
                    identity,
                    intent.sequence(),
                    session.orElseThrow().admissionState().sequenceState().expectedNext()));
            return;
        }
        var actor = serverAccess.currentPlayer(server, identity.authenticatedPlayerId());
        if (actor == null
                || !serverAccess.currentConnectedPlayer(
                        server, actor, identity.authenticatedPlayerId())) {
            resultSink.accept(P7AdmissionDispositionMapper.disconnected(
                    identity, intent.sequence(), false));
            return;
        }
        if (!sessionService.admissionOpen(server)) {
            resultSink.accept(P7AdmissionDispositionMapper.reloadInProgress(
                    identity,
                    intent.sequence(),
                    session.orElseThrow().admissionState().sequenceState().expectedNext()));
            return;
        }

        var authoritativeTick = serverAccess.authoritativeTick(server);
        var transition = sessionService.transition(
                server, identity, authoritativeTick, intent.sequence());
        if (transition.isEmpty()) {
            return;
        }
        var decision = transition.orElseThrow();
        if (decision.disconnect()) {
            var result = P7AdmissionDispositionMapper.fromAdmissionSemantics(
                    identity, intent.sequence(), decision);
            resultSink.accept(result);
            sessionService.invalidateAfterRateLimit(server, identity);
            disconnectPort.disconnect(server, actor, identity);
            return;
        }
        if (decision.outcome() != CastIntentAdmissionSemantics.Outcome.ELIGIBLE) {
            resultSink.accept(P7AdmissionDispositionMapper.fromAdmissionSemantics(
                    identity, intent.sequence(), decision));
            return;
        }
        if (!actor.isAlive() || actor.isSpectator()) {
            resultSink.accept(P7AdmissionDispositionMapper.unauthorizedAfterConsumption(
                    identity, intent.sequence()));
            return;
        }

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
}
