package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Finite engineering STATUS traffic. Every TRY still comes from the original native UI. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aC6ClientProbe {
    public enum Step { DEATH_FIRST, CURRENT_PRESSURE, FRESH_STATUS_BURST, RETRY_ONE, RETRY_TWO,
        CONFIG_RETRY, PREPLAY_STATUS, PREPLAY_ALIGN, PREPLAY_RETRY, OBSERVE_EXPIRY }
    private static Request lastOriginalTry;
    private static Run active;
    private P11C4aC6ClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, String role, Path output) throws IOException {
        var state = P11ClientTransitions.view();
        require(active == null && P11C4aEvidence.enabled() && minecraft.isSameThread()
                && List.of("a", "b", "host").contains(role) && connection.isConnected()
                && state != null && (lastOriginalTry == null
                    || lastOriginalTry.connectionEpoch() == state.connectionEpoch()), "C6_CLIENT_BASELINE");
        active = new Run(connection, role, output, P11C4aEvidence.root().resolve("server"), state);
        P11C4aEvidence.write(output, "c6-client-armed.json", Map.of("status", "ARMED_NOT_ACCEPTANCE",
                "hadOriginalTry", lastOriginalTry != null, "view", state, "counterSeeded", false));
    }

    /** Parent selects the next literal episode; a server file authorizes orchestration only. */
    static void select(Step step) {
        var run = active;
        require(run != null && Minecraft.getInstance().isSameThread() && run.step == null
                && !run.completed[step.ordinal()], "C6_CLIENT_SELECT_ONCE");
        run.step = step; run.stepTicks = 0; run.sends = 0; run.action = false;
        run.baselineSends = run.originalSends; run.mayTick = -1; run.lastMay = null;
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.step != null && run.failure == null
                && ++run.stepTicks <= 2400, "C6_CLIENT_OWNER_OR_DEADLINE");
        String leaf = switch (run.step) {
            case DEATH_FIRST -> "c6-death-first";
            case CURRENT_PRESSURE -> "c6-current-pressure";
            case FRESH_STATUS_BURST -> "c6-fresh-status-burst";
            case RETRY_ONE -> "c6-retry-one";
            case RETRY_TWO -> "c6-retry-two";
            case CONFIG_RETRY -> "c6-config-retry";
            case PREPLAY_STATUS -> "c6-preplay-status";
            case PREPLAY_ALIGN -> "c6-preplay-align";
            case PREPLAY_RETRY -> "c6-preplay-retry";
            case OBSERVE_EXPIRY -> "c6-expiry";
        };
        if (!P11C4aEvidence.cuePresent(run.serverOutput, run.role + '-' + leaf + ".ready")) { return false; }
        if (run.step == Step.OBSERVE_EXPIRY) {
            if (run.connection.isConnected() || !(minecraft.screen instanceof DisconnectedScreen)
                    || minecraft.player != null || minecraft.level != null) { return false; }
            return finish(run, leaf, "ACTUAL_NATIVE_DISCONNECTED_TERMINAL_SERVER_EXPIRY_PROOF_SEPARATE");
        }
        require(run.connection.isConnected() && currentListener(run) != null, "C6_CLIENT_EXACT_CURRENT_TRANSPORT");
        var current = P11ClientTransitions.view();
        require(current != null && run.wire != null && sameScene(current, run.wire), "C6_CLIENT_ROUTE_CHANGED");
        if (run.step == Step.CURRENT_PRESSURE) {
            // Positive current n was actually observed on wire; actorless server n0 is also valid.
            if (run.wire.requestSeq() == 0 && !serverKind(run.wire.kind())) { return false; }
            sendStatus(run, run.wire, run.wire.requestSeq());
            if (++run.sends < 64) { return false; }
            return finish(run, leaf, "64_ORIGINAL_TRANSPORT_CURRENT_STATUS_SENDS_NOT_BUCKET_PROOF");
        }
        if (run.step == Step.FRESH_STATUS_BURST || run.step == Step.PREPLAY_STATUS) {
            require(lastOriginalTry != null && lastOriginalTry.connectionEpoch() == current.connectionEpoch()
                    && lastOriginalTry.requestSeq() < Long.MAX_VALUE,
                    "C6_FRESH_STATUS_NO_ACTUAL_SENDER_BASELINE");
            // This is intentionally not a TRY or a private counter update. The production sender
            // can later emit this n itself; then cached NOT_STARTED must converge, not execute it.
            long n = lastOriginalTry.requestSeq() + 1;
            require(n > run.wire.requestSeq(), "C6_STATUS_NOT_FRESH");
            int count = run.step == Step.PREPLAY_STATUS ? 1 : 8;
            for (int i = 0; i < count; i++) { sendStatus(run, current, n); run.sends++; }
            return finish(run, leaf, "FRESH_STATUS_NATIVE_WIRE_SENDS_ACTUAL_BUCKET_RESULTS_REQUIRED");
        }
        if (!run.action) {
            if (run.step == Step.DEATH_FIRST) {
                if (current.scope() != Scope.PLAY || current.kind() != Kind.DEATH
                        || current.outcome() != Outcome.BINDING || current.requestSeq() != 0) { return false; }
                run.actionResponseBaseline = run.responseSerial;
                run.action = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN);
                if (run.action) {
                    require(run.originalSends == run.baselineSends + 1, "C6_FIRST_NATIVE_TRY_MISSING");
                    run.actionRequest = lastOriginalTry;
                }
                return false;
            }
            if (current.outcome() != Outcome.NOT_STARTED || current.availability() != Availability.MAY_TRY) { return false; }
            if (run.lastMay != current) { run.lastMay = current; run.mayTick = run.stepTicks; return false; }
            if (run.stepTicks <= run.mayTick + 1 || !P11ClientTransitions.retryArmed()) { return false; }
            var input = P11C4aClientInputProbe.snapshot(minecraft);
            if (!input.glfwActivationNeutral()) { return false; }
            run.neutral = input;
            run.actionResponseBaseline = run.responseSerial;
            run.action = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY);
            if (run.action) {
                require(run.originalSends == run.baselineSends + 1 && lastOriginalTry != null
                        && lastOriginalTry.requestSeq() > current.requestSeq(), "C6_NATIVE_RETRY_NO_NEW_SEND");
                run.actionRequest = lastOriginalTry;
            }
            return false;
        }
        if (run.wireSerial <= run.actionResponseBaseline || run.wire.requestSeq() != run.actionRequest.requestSeq()
                || run.wire.outcome() != Outcome.NOT_STARTED && run.wire.outcome() != Outcome.COMPLETED) { return false; }
        return finish(run, leaf, "ORIGINAL_NEUTRAL_RETRY_AND_MATCHING_NATIVE_RESPONSE");
    }

    public static void submitted(Object value) {
        if (!(value instanceof Request request) || request.command() != Command.TRY) { return; }
        if (!P11C4aEvidence.enabled()) { return; }
        lastOriginalTry = request; // One immutable actual production request, never a fabricated seed.
        var run = active;
        if (run != null && request.connectionEpoch() == run.binding.connectionEpoch()) { run.originalSends++; }
    }

    /** Original handler TAIL; raw response is labelled separately from installed client view. */
    public static void received(Object value, Connection connection, ICommonPacketListener listener) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof State state)
                || connection.getPacketListener() != listener || listener.getConnection() != connection) { return; }
        if (state.connectionEpoch() != run.binding.connectionEpoch()) { run.failure = "C6_OTHER_CONNECTION_EPOCH"; return; }
        if (run.responses.size() >= 512) { run.failure = "C6_CLIENT_OBSERVATION_BOUND"; return; }
        run.responseSerial++;
        if (state.sceneSerial() > run.wire.sceneSerial() || state.sceneSerial() == run.wire.sceneSerial()
                && state.statusVersion() >= run.wire.statusVersion()) { run.wire = state; run.wireSerial = run.responseSerial; }
        run.responses.add(Map.of("state", state, "installedViewIdentity", P11ClientTransitions.view() == state));
        if (state.outcome() == Outcome.EXPIRED && state.reason() == Reason.WAIT_EXPIRED) { run.expired = state; }
    }

    static void release() { active = null; lastOriginalTry = null; }
    static boolean active() { return active != null; }
    static boolean idle() { return active != null && active.step == null; }
    static boolean complete(Step step) { return active != null && active.completed[step.ordinal()]; }
    static boolean expiredObserved() { return active != null && active.expired != null; }
    private static void sendStatus(Run run, State route, long n) {
        var request = new Request(route.scope(), route.connectionEpoch(), route.sceneSerial(),
                route.actorGeneration(), n, Command.STATUS, route.kind());
        var listener = currentListener(run);
        require(listener != null && listener.protocol() == (route.scope() == Scope.CONFIG
                ? ConnectionProtocol.CONFIGURATION : ConnectionProtocol.PLAY), "C6_STATUS_PROTOCOL");
        listener.send(new ServerboundCustomPayloadPacket(new P11TransitionRequestPayload(request)));
        run.sent.add(request);
    }
    private static ICommonPacketListener currentListener(Run run) {
        return run.connection.getPacketListener() instanceof ICommonPacketListener listener
                && listener.getConnection() == run.connection ? listener : null;
    }
    private static boolean finish(Run run, String leaf, String status) throws IOException {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("step", run.step); values.put("stepTicks", run.stepTicks);
        values.put("engineeringStatusSends", run.sends); values.put("sentStatusRequests", List.copyOf(run.sent));
        values.put("actualProductionTrySendReturns", run.originalSends - run.baselineSends);
        values.put("actualTry", run.actionRequest); values.put("lastActualWireState", run.wire);
        values.put("responses", List.copyOf(run.responses)); values.put("neutralBeforeOriginalCallback", run.neutral);
        values.put("counterSeeded", false); values.put("physicalHeldKeyClaimed", false);
        if (run.step == Step.OBSERVE_EXPIRY) {
            values.put("expiredHandlerObserved", run.expired != null);
            values.put("expiredHandlerState", run.expired);
            values.put("nativeDisconnectedScreen", true); values.put("exactConnectionClosed", !run.connection.isConnected());
            values.put("nativePlayerPresent", false); values.put("nativeLevelPresent", false);
            values.put("serverExpiryInferredFromClientClosure", false);
        }
        values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, leaf + ".json", values);
        run.completed[run.step.ordinal()] = true; run.step = null; run.sent.clear(); run.responses.clear();
        return true;
    }
    private static boolean sameScene(State a, State b) { return a.scope() == b.scope()
            && a.connectionEpoch() == b.connectionEpoch() && a.sceneSerial() == b.sceneSerial()
            && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind(); }
    private static boolean serverKind(Kind kind) { return kind == Kind.JOIN || kind == Kind.RETURN_TO_WORLD || kind == Kind.ENTER_CONFIG; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output, serverOutput; final State binding;
        final boolean[] completed = new boolean[Step.values().length];
        final ArrayList<Request> sent = new ArrayList<>();
        final ArrayList<Map<String, Object>> responses = new ArrayList<>();
        State wire, lastMay, expired; Step step; Request actionRequest;
        int originalSends, baselineSends, stepTicks, sends, mayTick, responseSerial, wireSerial, actionResponseBaseline;
        boolean action; String failure; P11C4aClientInputProbe.Snapshot neutral;
        Run(Connection connection, String role, Path output, Path serverOutput, State binding) {
            this.connection = connection; this.role = role; this.output = output; this.serverOutput = serverOutput;
            this.binding = binding; wire = binding;
        }
    }
}
