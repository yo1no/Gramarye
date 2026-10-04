package com.yo1no.gramarye;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Reuses genuine two authenticated parent actors and the existing native credit early-task cause. */
public final class P11C4aMalformedServerProbe {
    private static Run active;
    private P11C4aMalformedServerProbe() { }
    static boolean started() { return active != null; }
    static void start(MinecraftServer server, ServerPlayer a, ServerPlayer b, Path output) throws IOException {
        require(P11C4aMalformedWireProbe.selected() && active == null && server.isSameThread()
                && server.isDedicatedServer() && server.usesAuthentication() && a != b && !a.getUUID().equals(b.getUUID())
                && a.isAlive() && b.isAlive() && !a.isFakePlayer() && !b.isFakePlayer()
                && server.getPlayerList().getPlayer(a.getUUID()) == a && server.getPlayerList().getPlayer(b.getUUID()) == b
                && P11NativeStorageBoundary.nativeDeliveryEligible(a) && P11NativeStorageBoundary.nativeDeliveryEligible(b),
                "MALFORMED_REAL_PARENT_ACTORS");
        var run = new Run(server, a, b, output); active = run;
        P11C4aMalformedWireProbe.arm(run.a, true);
        run.factory = count(run.a, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED);
        run.respawn = count(run.a, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER);
        run.loginFrames = count(run.a, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED);
        run.respawnFrames = count(run.a, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED);
        require(run.factory == 1 && run.loginFrames == 1 && run.respawn == 0 && run.respawnFrames == 0,
                "MALFORMED_INITIAL_ONLY_BASELINE");
        run.bEpoch = P11NativeStorageBoundary.diagnostics(server, b.getUUID()).sourceEpoch();
        run.stats = b.getStats(); run.advancements = b.getAdvancements();
        P11C4aEvidence.cue(output, "a-malformed-arm.ready"); P11C4aEvidence.cue(output, "b-malformed-arm.ready");
    }
    static boolean tick() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 2400, "MALFORMED_SERVER_DEADLINE");
        if (run.bLoggedOut) { return true; }
        if (run.qualified) { return false; }
        require(run.b.isConnected() && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && run.peer.connection.getConnection() == run.b && run.peer.isAlive(), "MALFORMED_CONTROL_DISTURBED");
        Path aOutput = P11C4aEvidence.root().resolve("client-a");
        if (run.stage == 0) {
            if (!P11C4aEvidence.receiptPresent(aOutput, "malformed-armed.json")
                    || !P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-b"), "malformed-armed.json")) { return false; }
            require(run.a.isConnected() && run.actor.isAlive()
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == run.actor
                    && run.a.getPacketListener() instanceof ServerGamePacketListenerImpl game
                    && game.player == run.actor, "MALFORMED_A_CHANGED_BEFORE_NATIVE_CAUSE");
            if (P11C4aMalformedWireProbe.config()) {
                P11C4aEvidence.write(run.output, "malformed-real-credit-prepared.json", P11C4aC6EarlyGateProbe.prepareActorless(run.actor));
                run.stage = 1; run.actor.connection.switchToConfig();
            } else {
                P11C4aMalformedWireProbe.enable(); run.stage = 2; run.actor.kill();
            }
            return false;
        }
        if (run.stage == 1) {
            if (!P11C4aEvidence.receiptPresent(aOutput, "malformed-config-terminal.json")) { return false; }
            require(run.a.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null,
                    "MALFORMED_INDEPENDENT_CONFIG_TERMINAL");
            P11C4aC6EarlyGateProbe.arm(run.actor, P11C4aC6EarlyGateProbe.Point.CONFIG_EARLY);
            P11C4aMalformedWireProbe.enable(); run.stage = 2;
            ((ServerConfigurationPacketListenerImpl) run.a.getPacketListener()).returnToWorld();
            return false;
        }
        if (P11C4aMalformedWireProbe.config() && run.gate == null && P11C4aC6EarlyGateProbe.returned()) {
            run.gate = P11C4aC6EarlyGateProbe.reportAndRelease();
            require(((Number) run.gate.get("actualFopAtGate")).longValue() > 0
                    && ((Number) run.gate.get("actualQctxAtGate")).longValue() == 0
                    && ((Number) run.gate.get("originalGateCalls")).intValue() == 1
                    && Boolean.TRUE.equals(run.gate.get("originalReturned")), "MALFORMED_CONFIG_NO_TRUE_NATIVE_CAUSE");
            P11C4aEvidence.write(run.output, "malformed-config-native-cause.json", run.gate);
        }
        if (run.a.isConnected() || run.server.getPlayerList().getPlayer(run.actor.getUUID()) != null
                || !P11C4aEvidence.receiptPresent(aOutput, "malformed-client-terminal.json")) { return false; }
        require(P11C4aMalformedWireProbe.terminalReady() && (!P11C4aMalformedWireProbe.config() || run.gate != null),
                "MALFORMED_SERVER_WIRE_TERMINAL");
        require(run.aLogouts == 1, "MALFORMED_ORIGINAL_A_LOGOUT_MISSING");
        require(count(run.a, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED) == run.factory
                && count(run.a, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER) == run.respawn
                && count(run.a, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == run.loginFrames
                && count(run.a, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED) == run.respawnFrames,
                "MALFORMED_NATIVE_BODY_OR_FRAME_DELTA");
        if (!run.peerCue) { run.peerCue = true; P11C4aEvidence.cue(run.output, "b-malformed-action.ready"); return false; }
        if (run.swings == 0) { return false; }
        require(run.swings == 1 && run.peer.getStats() == run.stats && run.peer.getAdvancements() == run.advancements
                && P11NativeStorageBoundary.diagnostics(run.server, run.peer.getUUID()).sourceEpoch() == run.bEpoch
                && count(run.b, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == 1
                && count(run.b, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER) == 0,
                "MALFORMED_CONTROL_NOT_UNCHANGED");
        var report = new LinkedHashMap<String, Object>(P11C4aMalformedWireProbe.report());
        report.put("status", "MALFORMED_NATIVE_WIRE_SUBSET_OBSERVED_REQUIRES_PEER_CLIENT_RECEIPT");
        report.put("actualNativeBodyDelta", 0); report.put("actualLoginFrameDelta", 0); report.put("actualRespawnFrameDelta", 0);
        report.put("controlOriginalSwingReturns", run.swings); report.put("aNativeAndWire", P11C4aNativeObservations.snapshot(run.a));
        report.put("bNativeAndWire", P11C4aNativeObservations.snapshot(run.b));
        P11C4aEvidence.write(run.output, "malformed-server-terminal.json", report);
        if (P11C4aMalformedWireProbe.config()) { P11C4aC6EarlyGateProbe.abort(); }
        run.server.saveEverything(false, true, true);
        run.qualified = true; P11C4aEvidence.cue(run.output, "b-malformed-finish.ready");
        return false;
    }
    public static void animate(ServerGamePacketListenerImpl listener) {
        var run = active;
        if (run != null && run.server.isSameThread() && run.peerCue && listener.getConnection() == run.b && listener.player == run.peer) { run.swings++; }
    }
    static void logout(ServerPlayer actor) {
        var run = active;
        require(run != null && run.server.isSameThread(), "MALFORMED_LOGOUT_OWNER");
        if (actor == run.actor) {
            require(++run.aLogouts == 1 && run.stage > 0, "MALFORMED_A_EXTRA_OR_EARLY_LOGOUT"); return;
        }
        require(actor == run.peer && run.qualified && !run.bLoggedOut, "MALFORMED_B_EARLY_OR_UNKNOWN_LOGOUT");
        run.bLoggedOut = true;
    }
    static void stopped(MinecraftServer server) throws IOException {
        var run = active;
        try {
            if (run != null && server == run.server && run.qualified && run.bLoggedOut) {
                Map<String,Object> terminal;
                try { terminal = P11C4aMalformedWireProbe.releaseWithTerminalReport(); }
                catch (RuntimeException | Error observerFailure) {
                    terminal = Map.of("status", "FAILED_FINAL_WIRE_OBSERVATION", "terminalRecheckPassed", false,
                            "observationCutoff", "SERVER_STOPPED_BEFORE_HELPER_RELEASE", "fullC4aAcceptance", false);
                }
                P11C4aEvidence.write(run.output, "malformed-stopped.json", terminal);
            }
        } finally { active = null; P11C4aMalformedWireProbe.release(); }
    }
    static void abort() {
        var run = active;
        try {
            if (run != null && !run.failureWritten) {
                run.failureWritten = true;
                var report = new LinkedHashMap<String, Object>(P11C4aMalformedWireProbe.report());
                report.put("status", "FIXED_DIAGNOSTIC_BEFORE_RELEASE_NOT_ACCEPTANCE");
                report.put("nativeStage", run.stage); report.put("nativeTicks", run.ticks);
                report.put("originalALogouts", run.aLogouts); report.put("qualified", run.qualified);
                report.put("exactAConnected", run.a.isConnected()); report.put("exactBConnected", run.b.isConnected());
                report.put("aNativeAndWire", P11C4aNativeObservations.snapshot(run.a));
                P11C4aEvidence.write(run.output, "malformed-failure-diagnostic.json", report);
            }
        } catch (IOException | RuntimeException | Error ignored) {
            // Secondary diagnostic cannot replace the original fixture failure or prevent native cleanup.
        } finally {
            active = null; P11C4aMalformedWireProbe.release(); P11C4aC6EarlyGateProbe.abort();
        }
    }
    private static long count(Connection c, P11C4aNativeObservations.Event event) { return P11C4aNativeObservations.count(c, event); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final Connection a, b; final Path output;
        int stage, ticks, swings, aLogouts; boolean peerCue, qualified, bLoggedOut, failureWritten;
        long factory, respawn, loginFrames, respawnFrames, bEpoch; Object stats, advancements; Map<String,Object> gate;
        Run(MinecraftServer server, ServerPlayer a, ServerPlayer b, Path output) {
            this.server = server; actor = a; peer = b; this.a = a.connection.getConnection(); this.b = b.connection.getConnection(); this.output = output;
        }
    }
}
