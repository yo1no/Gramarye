package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/** Dedicated two-peer C6 episode. Cues schedule actions; original observers decide the facts. */
final class P11C4aC6Coordinator {
    private static volatile Run active;
    private P11C4aC6Coordinator() { }

    static boolean tick(MinecraftServer server, ServerPlayer a, ServerPlayer b, Path output) throws IOException {
        if (active == null) {
            P11C4aEvidence.require(server.isDedicatedServer() && a != null && b != null,
                    "C6_DEDICATED_REAL_PAIR_REQUIRED");
            active = new Run(server, a, b, output);
            P11C4aC6NativeProbe.start(server, a, b, output);
            cue("a", "arm"); cue("b", "arm");
        }
        var run = active;
        P11C4aEvidence.require(server == run.server && server.isSameThread() && ++run.ticks <= 3600,
                "C6_SERVER_OWNER_OR_DEADLINE");
        if (run.stage < 7) {
            P11C4aEvidence.require(a != null && b != null && run.a.isConnected() && run.b.isConnected(),
                    "C6_PAIR_LOST_BEFORE_CAPACITY_EPISODE");
        }
        switch (run.stage) {
            case 0 -> {
                if (!receipt("a", "c6-client-armed") || !receipt("b", "c6-client-armed")) { return false; }
                P11C4aC6EarlyGateProbe.arm(a, P11C4aC6EarlyGateProbe.Point.PLAY_FIRST);
                a.kill(); cue("a", "death-first"); run.stage++;
            }
            case 1 -> {
                if (!receipt("a", "c6-death-first") || !P11C4aC6EarlyGateProbe.returned()) { return false; }
                P11C4aEvidence.write(output, "c6-a-real-first-refusal.json", P11C4aC6EarlyGateProbe.reportAndRelease());
                P11C4aC6EarlyGateProbe.arm(b, P11C4aC6EarlyGateProbe.Point.PLAY_FIRST);
                b.kill(); cue("b", "death-first"); run.stage++;
            }
            case 2 -> {
                if (!receipt("b", "c6-death-first") || !P11C4aC6EarlyGateProbe.returned()) { return false; }
                P11C4aEvidence.write(output, "c6-b-real-first-refusal.json", P11C4aC6EarlyGateProbe.reportAndRelease());
                cue("a", "current-pressure"); cue("b", "current-pressure"); run.stage++;
            }
            case 3 -> {
                if (!both("c6-current-pressure")) { return false; }
                P11C4aC6NativeProbe.write("c6-fairness-observations.json");
                cue("a", "fresh-status-burst"); cue("b", "fresh-status-burst"); run.stage++;
            }
            case 4 -> {
                if (!both("c6-fresh-status-burst")) { return false; }
                cue("a", "retry-one"); cue("b", "retry-one"); run.stage++;
            }
            case 5 -> {
                if (!both("c6-retry-one")) { return false; }
                cue("a", "retry-two"); cue("b", "retry-two"); run.stage++;
            }
            case 6 -> {
                if (!both("c6-retry-two") || !a.isAlive() || !b.isAlive() || !quiescent(a) || !quiescent(b)) { return false; }
                P11C4aC6NativeProbe.write("c6-bucket-observations.json");
                run.retainedA = a; run.currentB = b;
                P11C4aEvidence.write(output, "c6-live-credit-holders.json", P11C4aC6EarlyGateProbe.prepareActorless(a));
                P11C4aParkingProbe.start(server, a, "a", output);
                run.stage++;
            }
            case 7 -> {
                // C6 overlay intentionally never calls baseline parking finish/manual-admit.
                if (!run.a.isConnected()) {
                    P11C4aEvidence.require(run.expired != null && run.failedRetry != null && run.firstFinal != null,
                            "C6_LEFT_WITHOUT_ORIGINAL_EXPIRY");
                    if (!P11C4aC6ExpiryProbe.terminal()) { return false; }
                    if (!run.serverExpirySealed) {
                        P11C4aParkingProbe.finishC6Wait();
                        P11C4aC6NativeProbe.write("c6-final-observations.json");
                        run.serverExpirySealed = true;
                    }
                    if (!receipt("a", "c6-expiry")) { return false; }
                    P11C4aEvidence.write(output, "c6-episode-result.json", Map.of(
                            "status", "NATIVE_EPISODE_FINISHED_REQUIRES_OBSERVATION_REVIEW",
                            "firstFinalRefusal", run.firstFinal, "failedRetry", run.failedRetry,
                            "expired", run.expired, "onlyNewCapacityEntrantClosedFirst", run.bClosed,
                            "repeatedExactFailedRefusal", run.repeatedFailedRefusal,
                            "fullC4aAcceptance", false));
                    P11C4aParkingProbe.abort(); P11C4aC6EarlyGateProbe.abort(); P11C4aC6NativeProbe.release();
                    run.stage++; return true;
                }
                P11C4aParkingProbe.tick(a, P11C4aEvidence.root().resolve("client-a"));
            }
            case 8 -> { return true; }
            default -> throw new IllegalStateException("C6_STAGE_OUT_OF_RANGE");
        }
        return false;
    }

    /** Called before baseline marks returnRequested. Never replays the original handler. */
    static boolean beforeReturn(ServerPlayer retained) throws IOException {
        var run = active;
        P11C4aEvidence.require(run != null && retained == run.retainedA
                && run.a.getPacketListener() instanceof ServerConfigurationPacketListenerImpl,
                "C6_EXISTING_A_NOT_CONFIG");
        if (!run.bSwitch) {
            run.bSwitch = true;
            P11C4aEvidence.write(run.output, "c6-before-new-capacity-entrant.json", Map.of(
                    "status", "A_ACTUAL_CONFIG_HELD_BEFORE_B_NATIVE_SWITCH", "AConnected", run.a.isConnected()));
            cue("b", "capacity-close");
            run.currentB.connection.switchToConfig();
            return false;
        }
        if (run.b.isConnected()) { return false; }
        if (!run.bClosed) {
            run.bClosed = true;
            P11C4aEvidence.require(run.a.isConnected()
                    && run.server.getPlayerList().getPlayer(run.retainedA.getUUID()) == null,
                    "C6_EXISTING_WAITING_A_DISTURBED");
            P11C4aC6NativeProbe.write("c6-capacity-observations.json");
            P11C4aC6EarlyGateProbe.arm(retained, P11C4aC6EarlyGateProbe.Point.CONFIG_EARLY);
        }
        return true;
    }

    static void pendingConfiguration() throws IOException {
        var run = active;
        if (run != null && !run.earlyReported && P11C4aC6EarlyGateProbe.returned()) {
            run.earlyReported = true;
            P11C4aEvidence.write(run.output, "c6-actual-early-refusal.json", P11C4aC6EarlyGateProbe.reportAndRelease());
            cue("a", "config-retry");
        }
    }

    static boolean finalRequest(State state) {
        var run = active;
        return run != null && run.configTry != null && state.scope() == Scope.PREPLAY
                && state.requestSeq() == run.configTry.requestSeq()
                && state.connectionEpoch() == run.configTry.connectionEpoch()
                && state.sceneSerial() == run.configTry.sceneSerial();
    }

    static void ingress(Object value, Connection connection) {
        var run = active;
        if (run == null || run.stage != 7 || connection != run.a || !(value instanceof Request request)) { return; }
        if (request.command() == Command.STATUS) {
            if (request.scope() == Scope.PREPLAY && run.configTry != null
                    && request.requestSeq() > run.configTry.requestSeq()) {
                P11C4aEvidence.require(request.requestSeq() == run.configTry.requestSeq() + 1
                        || run.preplayTry != null && request.requestSeq() == run.preplayTry.requestSeq(),
                        "C6_UNEXPECTED_FRESH_STATUS");
                if (run.statusRequest == null) { run.statusRequest = request; }
            }
            return;
        }
        if (request.scope() == Scope.CONFIG) {
            P11C4aEvidence.require(run.configTry == null && run.earlyReported, "C6_CONFIG_EXTRA_TRY");
            run.configTry = request;
        } else if (request.scope() == Scope.PREPLAY) {
            if (run.statusRequest != null && request.requestSeq() == run.statusRequest.requestSeq()) {
                P11C4aEvidence.require(run.alignTry == null, "C6_DUPLICATE_ALIGN_TRY"); run.alignTry = request; return;
            }
            P11C4aEvidence.require(run.preplayTry == null && run.preplayArmed && run.configTry != null
                    && request.requestSeq() > run.configTry.requestSeq(), "C6_PREPLAY_EXTRA_OR_OLD_TRY");
            run.preplayTry = request;
        }
    }

    static boolean laterState(State state) {
        var run = active;
        if (run == null || run.stage != 7) { return false; }
        if (state.scope() == Scope.CONFIG && state.kind() == Kind.RETURN_TO_WORLD) { return true; }
        if (state.scope() != Scope.PREPLAY || state.kind() != Kind.RETURN_TO_WORLD) { return false; }
        if (state.outcome() == Outcome.EXPIRED) {
            P11C4aEvidence.require(state.reason() == Reason.WAIT_EXPIRED, "C6_WRONG_EXPIRY"); run.expired = state; return true;
        }
        if (run.firstFinal == null) { return false; } // Baseline assertions establish the true final managedBlock refusal.
        if (state.equals(run.firstFinal)) { return true; }
        if (state.equals(run.failedRetry)) {
            if (run.repeatedFailedRefusal != Long.MAX_VALUE) { run.repeatedFailedRefusal++; }
            return true; // Same proved observation is not a new admission after its native caller was released.
        }
        if (run.statusRequest != null && state.requestSeq() == run.statusRequest.requestSeq()
                && state.outcome() == Outcome.NOT_STARTED) {
            P11C4aEvidence.require(state.availability() == Availability.MAY_TRY && state.reason() == Reason.NONE
                    || state.availability() == Availability.WAIT_NOTIFY
                        && state.reason() == Reason.CONTROL_DISPATCH_BUSY, "C6_STATUS_REPAIR_REASON");
            run.statusRepair = state; return true;
        }
        if (run.preplayTry != null && state.requestSeq() == run.preplayTry.requestSeq()
                && state.outcome() == Outcome.NOT_STARTED) {
            if (state.availability() == Availability.WAIT_NOTIFY) {
                P11C4aEvidence.require(state.reason() == Reason.ACTIVE_OPERATION
                        && P11C4aC6EarlyGateProbe.returned(), "C6_FAILED_RETRY_NOT_REAL_GATE");
                run.failedRetry = state;
            }
            return true;
        }
        return false;
    }

    static void firstFinal(State state) { if (active != null && active.firstFinal == null) { active.firstFinal = state; } }

    /** Baseline caller/root returned. Consume genuine credit before its native TTL; ACK remains mandatory later. */
    static void parkedReady() throws IOException {
        var run = active;
        if (!run.statusCued) { run.statusCued = true; cue("a", "preplay-status"); return; }
        if (!receipt("a", "c6-preplay-status") || run.statusRepair == null) { return; }
        if (!run.alignCued) { run.alignCued = true; cue("a", "preplay-align"); return; }
        if (!receipt("a", "c6-preplay-align")) { return; }
        if (!run.preplayArmed) {
            P11C4aC6EarlyGateProbe.arm(run.retainedA, P11C4aC6EarlyGateProbe.Point.PREPLAY_RETRY);
            run.preplayArmed = true; cue("a", "preplay-retry");
        } else if (run.failedRetry != null && !run.failedReported && P11C4aC6EarlyGateProbe.returned()) {
            run.failedReported = true;
            P11C4aEvidence.write(run.output, "c6-actual-failed-preplay-retry.json", P11C4aC6EarlyGateProbe.reportAndRelease());
            P11C4aC6NativeProbe.write("c6-wait-after-failed-retry.json"); cue("a", "expiry");
        }
    }

    static boolean capacityCloseExpected(Connection connection) { return active != null && active.bSwitch && connection == active.b; }
    static boolean expiryExpected(Connection connection) { return active != null && active.failedReported && connection == active.a; }
    private static boolean quiescent(ServerPlayer actor) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        return P11NativeStorageBoundary.nativeDeliveryEligible(actor) && body != null
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0;
    }
    private static boolean both(String leaf) throws IOException { return receipt("a", leaf) && receipt("b", leaf); }
    private static boolean receipt(String role, String leaf) throws IOException {
        return P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-" + role), leaf + ".json");
    }
    private static void cue(String role, String leaf) throws IOException { P11C4aEvidence.cue(active.output, role + "-c6-" + leaf + ".ready"); }
    private static final class Run {
        final MinecraftServer server; final Connection a, b; final Path output;
        volatile int stage; int ticks; ServerPlayer retainedA, currentB;
        volatile boolean bSwitch, bClosed, earlyReported, preplayArmed, failedReported;
        boolean statusCued, alignCued, serverExpirySealed;
        long repeatedFailedRefusal;
        volatile Request configTry, statusRequest, alignTry, preplayTry;
        State firstFinal, statusRepair, failedRetry, expired;
        Run(MinecraftServer server, ServerPlayer a, ServerPlayer b, Path output) {
            this.server = server; this.a = a.connection.getConnection(); this.b = b.connection.getConnection(); this.output = output;
        }
    }
}
