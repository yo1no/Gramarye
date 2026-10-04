package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Finite original UI plus explicitly labelled engineering wire input, never counter repair. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aRateFairClientProbe {
    private static Run active;
    private static int awaitArmTicks;
    private P11C4aRateFairClientProbe() { }

    static boolean tick(Minecraft minecraft, Connection connection, String role, Path output, Path serverOutput) throws IOException {
        if (active == null) {
            require(minecraft.isSameThread() && ++awaitArmTicks <= 24_000, "RF_CLIENT_ARM_DEADLINE");
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-rf-arm.ready")) { return false; }
            require(minecraft.isSameThread() && java.util.List.of("a", "b").contains(role)
                    && connection.isConnected() && !connection.isMemoryConnection()
                    && minecraft.player != null && minecraft.level != null, "RF_CLIENT_ARM");
            active = new Run(connection, role, output, serverOutput);
            report(active, "rf-armed", "ARMED_NO_ACCEPTANCE");
        }
        var run = active;
        require(minecraft.isSameThread() && ++run.ticks <= 2400 && run.connection == connection, "RF_CLIENT_OWNER_OR_DEADLINE");
        if (cue(run, "finish")) {
            require(connection.isConnected() && minecraft.level != null, "RF_ORIGINAL_DISCONNECT_CONTEXT");
            minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen());
            require(!connection.isConnected() && minecraft.player == null && minecraft.level == null
                    && minecraft.screen instanceof TitleScreen, "RF_ORIGINAL_DISCONNECT_RETURN");
            report(run, "rf-terminal", "ORIGINAL_CLIENT_DISCONNECT_RETURN_NO_NEW_RUNTIME_CLAIM");
            return true;
        }
        require(connection.isConnected() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == connection, "RF_EXACT_CLIENT_TRANSPORT");
        if (cue(run, "first") && !run.firstDone) {
            if (!run.firstClicked) {
                var view = P11ClientTransitions.view();
                if (view == null || view.kind() != Kind.DEATH || view.scope() != Scope.PLAY
                        || view.outcome() != Outcome.BINDING) { return false; }
                run.firstClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN);
                if (run.firstClicked) { require(run.originalSends == 1 && run.lastOriginal != null, "RF_FIRST_ORIGINAL_SEND"); }
                return false;
            }
            if (!matchingRefusal(run)) { return false; }
            run.firstDone = true; report(run, "rf-first", "ACTUAL_FIRST_UI_TRY_AND_RAW_MATCHING_REFUSAL");
        }
        if (cue(run, "fair") && !run.fairDone) {
            require(run.firstDone && run.wire != null && run.wire.scope() == Scope.PLAY
                    && run.wire.kind() == Kind.DEATH && run.wire.requestSeq() > 0, "RF_FAIR_ROUTE");
            run.fairTicks++;
            send(run, run.wire, run.wire.requestSeq(), Command.STATUS);
            if (run.role.equals("b") && run.freshStatus < 8 && run.fairTicks <= 8) {
                long previous = Math.max(run.engineeringN, run.wire.requestSeq());
                require(previous < Long.MAX_VALUE, "RF_WIRE_SEQUENCE_EXHAUSTED");
                run.engineeringN = previous + 1;
                send(run, run.wire, run.engineeringN, Command.STATUS);
                send(run, run.wire, run.wire.requestSeq(), Command.STATUS);
                run.freshStatus++;
            }
            if (run.fairTicks == 160) {
                run.fairDone = true; report(run, "rf-fair", "FINITE_ACTUAL_WIRE_PRESSURE_NOT_FAIRNESS_PROOF");
            }
            return false;
        }
        if (cue(run, "consume") && !run.consumeDone) {
            require(run.role.equals("a") && run.fairDone, "RF_CONSUME_ROLE");
            if (!run.consumeClicked) {
                var view = P11ClientTransitions.view();
                if (view == null || view.outcome() != Outcome.NOT_STARTED || view.availability() != Availability.MAY_TRY) { return false; }
                if (run.may != view) { run.may = view; run.mayTick = run.ticks; return false; }
                if (run.ticks <= run.mayTick + 1 || !P11ClientTransitions.retryArmed()
                        || !P11C4aClientInputProbe.snapshot(minecraft).glfwActivationNeutral()) { return false; }
                run.responseBaseline = run.responses;
                run.consumeClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY);
                if (run.consumeClicked) { require(run.originalSends == 2 && run.lastOriginal.requestSeq() > view.requestSeq(), "RF_FRESH_NATIVE_RETRY"); }
                return false;
            }
            if (run.responses <= run.responseBaseline || !matchingRefusal(run)) { return false; }
            run.consumeDone = true; report(run, "rf-consume", "ACTUAL_MAY_NEUTRAL_UI_TRY_AND_MATCHING_REFUSAL");
        }
        if (cue(run, "wire") && !run.wireDone) {
            require(run.role.equals("a") && run.consumeDone && run.lastOriginal != null, "RF_NEGATIVE_WIRE_CONTEXT");
            if (!run.wireSent) {
                require(run.lastOriginal.requestSeq() < Long.MAX_VALUE && matchingRefusal(run), "RF_NEGATIVE_WIRE_BASELINE");
                run.rateN = run.lastOriginal.requestSeq() + 1; run.responseBaseline = run.responses;
                send(run, run.wire, run.rateN, Command.TRY);
                send(run, run.wire, run.rateN, Command.STATUS);
                run.wireSent = true; run.wireTick = run.ticks; return false;
            }
            if (run.responses <= run.responseBaseline || run.wire == null || run.wire.requestSeq() != run.rateN
                    || run.wire.outcome() != Outcome.NOT_STARTED) {
                if (run.ticks - run.wireTick < 200) { return false; }
                run.wireDone = true; report(run, "rf-wire", "UNPROVEN_NO_MATCHING_RESPONSE_IN_FINITE_WINDOW"); return false;
            }
            run.wireDone = true; report(run, "rf-wire", "RAW_HANDLER_RESPONSE_ENGINEERING_FUTURE_N_NOT_UI_INSTALL_PROOF");
        }
        return false;
    }
    public static void received(Object supplied, Connection connection, ICommonPacketListener listener) {
        var run = active;
        if (run == null || run.connection != connection || connection.getPacketListener() != listener
                || !(supplied instanceof State state)) { return; }
        run.responses++;
        if (run.rateN > 0 && state.requestSeq() == run.rateN && state.outcome() == Outcome.NOT_STARTED
                && state.reason() == Reason.CONTROL_RATE_LIMIT) { run.rateReceipt = state; }
        if (run.wire == null || state.sceneSerial() > run.wire.sceneSerial()
                || state.sceneSerial() == run.wire.sceneSerial() && state.statusVersion() >= run.wire.statusVersion()) { run.wire = state; }
    }
    public static void submitted(Object supplied) {
        var run = active;
        if (run == null || !(supplied instanceof Request request) || request.command() != Command.TRY) { return; }
        run.lastOriginal = request; run.originalSends++;
    }
    static void release() { active = null; awaitArmTicks = 0; }
    private static boolean matchingRefusal(Run run) {
        return run.wire != null && run.lastOriginal != null && run.wire.requestSeq() == run.lastOriginal.requestSeq()
                && run.wire.connectionEpoch() == run.lastOriginal.connectionEpoch()
                && run.wire.sceneSerial() == run.lastOriginal.sceneSerial()
                && run.wire.actorGeneration() == run.lastOriginal.actorGeneration()
                && run.wire.outcome() == Outcome.NOT_STARTED;
    }
    private static void send(Run run, State state, long n, Command command) {
        require(run.connection.getPacketListener() instanceof ICommonPacketListener, "RF_NATIVE_CLIENT_LISTENER");
        var listener = (ICommonPacketListener) run.connection.getPacketListener();
        require(listener.getConnection() == run.connection && state.scope() == Scope.PLAY, "RF_NATIVE_CLIENT_ROUTE");
        var request = new Request(state.scope(), state.connectionEpoch(), state.sceneSerial(), state.actorGeneration(), n, command, state.kind());
        listener.send(new ServerboundCustomPayloadPacket(new P11TransitionRequestPayload(request)));
        if (command == Command.TRY) { run.engineeringTrySends++; } else { run.engineeringStatusSends++; }
    }
    private static boolean cue(Run run, String suffix) throws IOException { return P11C4aEvidence.cuePresent(run.serverOutput, run.role + "-rf-" + suffix + ".ready"); }
    private static void report(Run run, String leaf, String status) throws IOException {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("role", run.role); values.put("originalUiTrySendReturns", run.originalSends);
        values.put("engineeringTrySendReturns", run.engineeringTrySends); values.put("engineeringStatusSendReturns", run.engineeringStatusSends);
        values.put("lastOriginalTry", run.lastOriginal); values.put("rawState", run.wire);
        values.put("installedViewIsRawState", P11ClientTransitions.view() == run.wire);
        values.put("rawHandlerReturns", run.responses); values.put("fairClientTicks", run.fairTicks);
        values.put("rawRateReceiptObserved", run.rateReceipt != null); values.put("rawRateReceipt", run.rateReceipt);
        values.put("freshStatusIntents", run.freshStatus); values.put("rateSequence", run.rateN);
        values.put("physicalHeldKeyClaimed", false); values.put("privateCounterSeeded", false); values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, leaf + ".json", values);
    }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output, serverOutput;
        State wire, may, rateReceipt; Request lastOriginal; int ticks, originalSends, responses, responseBaseline, fairTicks, freshStatus, mayTick, wireTick;
        int engineeringTrySends, engineeringStatusSends; long engineeringN, rateN;
        boolean firstClicked, firstDone, fairDone, consumeClicked, consumeDone, wireSent, wireDone;
        Run(Connection connection, String role, Path output, Path serverOutput) {
            this.connection = connection; this.role = role; this.output = output; this.serverOutput = serverOutput;
            wire = P11ClientTransitions.view();
        }
    }
}
