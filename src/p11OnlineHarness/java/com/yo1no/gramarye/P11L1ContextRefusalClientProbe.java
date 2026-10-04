package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Original actorless CONFIG Retry callback; no request construction or sender call. */
public final class P11L1ContextRefusalClientProbe {
    private static Run active;
    private P11L1ContextRefusalClientProbe() { }
    static void arm(Minecraft minecraft, Connection oldConnection, Path output) {
        require(P11L1ContextRefusalProbe.selected() && minecraft.isSameThread() && active == null
                && oldConnection != null && !oldConnection.isConnected() && minecraft.player == null
                && minecraft.getConnection() == null, "ACTUAL_OLD_CONNECTION_CLOSED");
        active = new Run(oldConnection, output);
    }
    public static void accepted(Object value, Connection connection, ICommonPacketListener listener) {
        var r = active;
        if (r == null || !(value instanceof State state) || state.kind() != Kind.JOIN
                || P11ClientTransitions.view() != state) { return; }
        try {
            require(Minecraft.getInstance().isSameThread() && connection != r.oldConnection && connection.isConnected()
                    && connection.getPacketListener() == listener && listener.getConnection() == connection
                    && (r.connection == null || r.connection == connection), "NEW_CURRENT_NATIVE_CONNECTION");
            r.connection = connection;
            if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
                require(state.scope() == Scope.CONFIG && state.actorGeneration() == 0 && state.requestSeq() == 0
                        && state.reason() == Reason.ACTIVE_OPERATION && r.submits == 0 && r.logins == 0,
                        "ACTUAL_ACTORLESS_INITIAL_REFUSAL");
                r.wait = state;
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
                require(r.wait != null && sameScene(r.wait, state) && state.requestSeq() == 0 && r.submits == 0
                        && state.statusVersion() > r.wait.statusVersion(), "SAME_UNSTARTED_SCENE_MAY");
                r.may = state;
            } else if (state.outcome() == Outcome.NATIVE_FRAME) {
                require(r.retry != null && sameScene(r.wait, state) && state.requestSeq() == 1 && ++r.markers == 1,
                        "ONE_ORIGINAL_LOGIN_MARKER");
                r.marker = state;
            } else if (state.outcome() == Outcome.COMPLETED) {
                require(r.marker != null && sameScene(r.wait, state) && state.requestSeq() == 1
                        && state.targetActorGeneration() == r.marker.targetActorGeneration()
                        && state.statusVersion() > r.marker.statusVersion(), "FULL_MATCHING_COMPLETION");
                r.completed = state;
            }
        } catch (RuntimeException | Error failure) { r.failure = "CLIENT_STATE_OBSERVER"; }
    }
    public static void submitted(Object value) {
        var r = active;
        if (r == null || !(value instanceof Request request) || request.command() != Command.TRY) { return; }
        try {
            require(r.callbackActive && r.may != null && request.scope() == Scope.CONFIG && request.kind() == Kind.JOIN
                    && request.requestSeq() == 1 && request.connectionEpoch() == r.may.connectionEpoch()
                    && request.sceneSerial() == r.may.sceneSerial() && request.actorGeneration() == 0
                    && ++r.submits == 1, "ORIGINAL_FRESH_RETRY_ONE");
            r.retry = request;
        } catch (RuntimeException | Error failure) { r.failure = "CLIENT_SENDER_OBSERVER"; }
    }
    static void loginReturned(net.minecraft.client.multiplayer.ClientPacketListener listener) {
        var r = active; if (r == null || listener.getConnection() != r.connection) { return; }
        require(r.marker != null && r.submits == 1 && ++r.logins == 1, "ORIGINAL_LOGIN_AFTER_MARKER");
    }
    /** Parent calls in both CONNECTING and PLAY; native Login may arrive before COMPLETED. */
    static void tick(Minecraft minecraft) throws IOException {
        var r = active; if (r == null || r.complete) { return; }
        require(minecraft.isSameThread() && ++r.ticks <= 2400 && r.failure.equals("NONE"), "BOUNDED_NATIVE_FLOW");
        if (r.connection == null) { return; }
        require(r.connection.isConnected(), "NO_PREMATURE_CLOSE");
        if (r.may != null && !r.clicked && P11C4aEvidence.cuePresent(r.output.resolveSibling("server"), "a-context-retry.ready")) {
            require(P11ClientTransitions.view() == r.may && minecraft.player == null && minecraft.getConnection() == null,
                    "STILL_ACTORLESS_BEFORE_FRESH_RETRY");
            if (!P11C4aClientInputProbe.snapshot(minecraft).glfwActivationNeutral() || !P11ClientTransitions.retryArmed()) { return; }
            r.callbackActive = true;
            try { r.clicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY); }
            finally { r.callbackActive = false; }
            if (r.clicked) { require(r.submits == 1 && r.retry != null, "CALLBACK_SENT_ORIGINAL_RETRY"); }
        }
        if (r.completed == null || r.logins != 1) { return; }
        require(r.clicked && r.submits == 1 && r.markers == 1 && minecraft.player != null
                && minecraft.getConnection() == minecraft.player.connection
                && minecraft.getConnection().getConnection() == r.connection
                && P11ClientTransitions.view() == r.completed && !P11ClientTransitions.blocksCast(), "REAL_LOGIN_AND_FULL_TERMINAL");
        var f = new LinkedHashMap<String,Object>(); f.put("status", "ACTUAL_CONFIG_WAIT_ZERO_THEN_FRESH_RETRY_ONE");
        f.put("wait", r.wait); f.put("mayTry", r.may); f.put("retry", r.retry); f.put("marker", r.marker); f.put("completed", r.completed);
        f.put("originalTryReturns", r.submits); f.put("nativeLoginReturns", r.logins); f.put("nativeFrameMarkers", r.markers);
        f.put("originalNeutralRetryCallback", true); f.put("physicalOsInputClaim", false); f.put("failure", r.failure);
        P11C4aEvidence.write(r.output, "work-context-client.json", f); r.complete = true;
    }
    private static boolean sameScene(State a, State b) {
        return a.connectionEpoch() == b.connectionEpoch() && a.sceneSerial() == b.sceneSerial()
                && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind();
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_CONTEXT_CLIENT_" + code); }
    private static final class Run {
        final Connection oldConnection; final Path output; Connection connection;
        State wait, may, marker, completed; Request retry; int ticks, submits, markers, logins;
        boolean callbackActive, clicked, complete; String failure = "NONE";
        Run(Connection old, Path out) { oldConnection=old; output=out; }
    }
}
