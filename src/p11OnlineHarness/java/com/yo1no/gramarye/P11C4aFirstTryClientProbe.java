package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Real sender counter preparation plus one explicitly labelled late wire replay, client-only. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aFirstTryClientProbe {
    private static Run active;
    private P11C4aFirstTryClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, Path output) throws IOException {
        var state = P11ClientTransitions.view();
        require(P11C4aEvidence.enabled() && minecraft.isSameThread() && active == null
                && connection.isConnected() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == connection && state != null
                && state.scope() == Scope.PLAY && state.kind() == Kind.DEATH
                && state.outcome() == Outcome.COMPLETED && state.requestSeq() > 0 && state.requestSeq() < 40,
                "FIRST_TRY_CLIENT_START_NOT_COMPLETED");
        active = new Run(connection, output, P11C4aEvidence.root().resolve("server"), state.requestSeq());
        P11C4aEvidence.write(output, "first-try-armed.json", java.util.Map.of(
                "status", "ACTUAL_COMPLETED_COUNTER_NOT_SEEDED", "baselineActualSequence", state.requestSeq()));
        P11C4aEvidence.cue(output, "first-try-armed.ready");
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 2400
                && run.failure == null && run.connection.isConnected() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection, "FIRST_TRY_CLIENT_OWNER_OR_DEADLINE");
        var state = P11ClientTransitions.view();
        if (run.first41 == null) { return false; }
        if (!run.lateSent && run.notStarted41 != null) {
            require(state != null && sameScene(run.first41, state)
                    && state.requestSeq() == 41 && state.outcome() == Outcome.NOT_STARTED
                    && run.connection.getPacketListener() instanceof ICommonPacketListener,
                    "FIRST_TRY_LATE_NOT_SAME_CURRENT_SCENE");
            var listener = (ICommonPacketListener) run.connection.getPacketListener();
            require(listener.getConnection() == run.connection
                    && listener.protocol() == net.minecraft.network.ConnectionProtocol.PLAY,
                    "FIRST_TRY_LATE_LISTENER_NOT_CURRENT_PLAY");
            // This is a late retransmission experiment, not another original UI sender call.
            // The actual immutable request captured from production send is reused unchanged.
            listener.send(new ServerboundCustomPayloadPacket(new P11TransitionRequestPayload(run.first41)));
            run.lateSent = true;
            return false;
        }
        if (run.may41 != null && run.lateSent && !run.retryClicked
                && P11C4aEvidence.cuePresent(run.serverOutput, "first-try-late-checked.ready")) {
            if (run.ticks <= run.mayTick + 1 || !P11ClientTransitions.retryArmed()) { return false; }
            var input = P11C4aClientInputProbe.snapshot(minecraft);
            if (!input.glfwActivationNeutral()) { return false; }
            require(state == run.may41, "FIRST_TRY_MAY_NOT_CURRENT");
            run.neutral = input;
            run.retryClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY);
            if (run.retryClicked) { require(run.lastSequence == 42, "FIRST_TRY_RETRY_NOT_ORIGINAL_42"); }
            return false;
        }
        if (run.completed42 == null) { return false; }
        require(run.retryClicked && run.lateSent && run.originalSends == 42 - run.baseline
                && run.setupCompletions == 40 - run.baseline && state == run.completed42
                && minecraft.player != null && minecraft.player.isAlive()
                && P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN)
                    == run.beforeFrames + run.setupCompletions + 1, "FIRST_TRY_CLIENT_FINAL_COUNTS");
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_SENDER_40_THEN_41_STATUS_LATE_REPLAY_NEUTRAL_RETRY_42");
        values.put("baselineActualSequence", run.baseline); values.put("setupCompletedNativeSequences", run.setupCompletions);
        values.put("originalProductionTrySendReturns", run.originalSends); values.put("lastActualSequence", run.lastSequence);
        values.put("lateCaptured41NativeWireResends", 1); values.put("actual41", run.first41);
        values.put("notStarted41", run.notStarted41); values.put("waitNotification41", run.wait41);
        values.put("mayTry41", run.may41); values.put("completed42", run.completed42);
        values.put("neutralBeforeFreshRetry", run.neutral); values.put("inputKind", "CONTROLLED_ORIGINAL_WINDOW_CALLBACK");
        values.put("counterSeeded", false); values.put("physicalHeldKeyClaimed", false);
        values.put("visualAppearanceClaimed", false); values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "first-try.json", values);
        active = null;
        return true;
    }

    static void abort() { active = null; }

    /** Only normal production private send RETURN is counted; the one replay above is separate. */
    public static void submitted(Object value) {
        var run = active;
        if (run == null || !(value instanceof Request request) || request.command() != Command.TRY) { return; }
        var state = P11ClientTransitions.view();
        if (!Minecraft.getInstance().isSameThread() || state == null || request.scope() != Scope.PLAY
                || request.kind() != Kind.DEATH || !sameScene(request, state)
                || request.requestSeq() != run.lastSequence + 1 || request.requestSeq() > 42) {
            fail(run, "FIRST_TRY_CLIENT_ORIGINAL_SEQUENCE"); return;
        }
        run.originalSends++;
        run.lastSequence = request.requestSeq();
        if (request.requestSeq() == 41) { run.first41 = request; }
    }

    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof State state)
                || P11ClientTransitions.view() != state || state.scope() != Scope.PLAY || state.kind() != Kind.DEATH) { return; }
        if (state.requestSeq() == 41 && state.outcome() == Outcome.NOT_STARTED) {
            if (run.first41 == null || !sameScene(run.first41, state)
                    || !(state.availability() == Availability.WAIT_NOTIFY && state.reason() == Reason.CONTROL_DISPATCH_BUSY
                        || state.availability() == Availability.MAY_TRY && state.reason() == Reason.NONE)) {
                fail(run, "FIRST_TRY_CLIENT_REFUSAL_MISMATCH"); return;
            }
            if (state.availability() == Availability.WAIT_NOTIFY) { run.wait41 = state; }
            if (state.availability() == Availability.MAY_TRY) { run.may41 = state; run.mayTick = run.ticks; }
            run.notStarted41 = state;
        }
        if (state.outcome() == Outcome.COMPLETED) {
            if (state.equals(run.lastSetupCompleted)) { return; }
            if (state.requestSeq() <= 40 && state.requestSeq() == run.baseline + run.setupCompletions + 1) {
                run.setupCompletions++;
                run.lastSetupCompleted = state;
            } else if (state.requestSeq() == 42 && run.first41 != null && sameScene(run.first41, state)) {
                run.completed42 = state;
            } else { fail(run, "FIRST_TRY_CLIENT_COMPLETION_SEQUENCE"); }
        }
    }

    private static boolean sameScene(Request request, State state) {
        return request.scope() == state.scope() && request.connectionEpoch() == state.connectionEpoch()
                && request.sceneSerial() == state.sceneSerial() && request.actorGeneration() == state.actorGeneration()
                && request.kind() == state.kind();
    }

    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }

    private static final class Run {
        final Connection connection;
        final Path output, serverOutput;
        final long baseline, beforeFrames;
        long lastSequence;
        int ticks, originalSends, setupCompletions, mayTick;
        boolean lateSent, retryClicked;
        String failure;
        Request first41;
        State notStarted41, wait41, may41, completed42, lastSetupCompleted;
        P11C4aClientInputProbe.Snapshot neutral;
        Run(Connection connection, Path output, Path serverOutput, long baseline) {
            this.connection = connection; this.output = output; this.serverOutput = serverOutput;
            this.baseline = baseline; lastSequence = baseline;
            beforeFrames = P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        }
    }
}
