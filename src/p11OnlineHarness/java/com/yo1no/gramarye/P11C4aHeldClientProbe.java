package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.connection.ConnectionUtils;

/** Client-only: one original periodic STATUS encoder n→n+1 edit per real held window. No added send. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aHeldClientProbe {
    private static volatile Run active;
    private static final ThreadLocal<Encode> ENCODE = new ThreadLocal<>();
    private static int awaitTicks;
    private P11C4aHeldClientProbe() { }

    static boolean tick(Minecraft minecraft, Connection connection, String role, Path output, Path serverOutput) throws IOException {
        require(minecraft.isSameThread() && P11C4aHeldProbe.selected() && ++awaitTicks <= 24_000, "HELD_CLIENT_OWNER_OR_DEADLINE");
        if (active == null) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role.equals("a") ? "a-held-prepare-1.ready" : "b-held-arm.ready")) { return false; }
            require(java.util.List.of("a", "b").contains(role) && connection.isConnected() && !connection.isMemoryConnection()
                    && minecraft.player != null && minecraft.level != null, "HELD_CLIENT_ACTUAL_PLAY");
            active = new Run(connection, role, output, serverOutput);
        }
        var run = active;
        require(run.connection == connection && ++run.ticks <= 3600 && run.failure == null, "HELD_CLIENT_EXACT_C_OR_FAILURE");
        if (P11C4aEvidence.cuePresent(serverOutput, role + "-held-finish.ready")) {
            require(connection.isConnected() && minecraft.level != null && (role.equals("b") || run.completed == 3), "HELD_CLIENT_EARLY_FINISH");
            minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen());
            require(!connection.isConnected() && minecraft.level == null && minecraft.player == null, "HELD_CLIENT_NATIVE_DISCONNECT_RETURN");
            P11C4aEvidence.write(output, "held-client-terminal.json", Map.of("originalDisconnectReturned", true,
                    "windowsCompleted", run.completed, "role", role)); return true;
        }
        require(connection.isConnected() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == connection, "HELD_CLIENT_TRANSPORT_CHANGED");
        if (role.equals("b")) { require(minecraft.player != null && minecraft.player.isAlive(), "HELD_CLIENT_PEER_DIED"); return false; }
        if (run.episode == null || run.episode.done) {
            int next = run.completed + 1;
            if (next > 3 || !P11C4aEvidence.cuePresent(serverOutput, "a-held-prepare-" + next + ".ready")) { return false; }
            require(minecraft.player != null && minecraft.player.isAlive(), "HELD_CLIENT_PREPARE_ALIVE");
            run.episode = new Episode(next, P11C4aNativeObservations.count(connection,
                    P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN));
            P11C4aEvidence.write(output, "held-armed-" + next + ".json", Map.of("status", "ACTUAL_PLAY_PREARM_NOT_ACCEPTANCE", "index", next));
        }
        var e = run.episode;
        if (e.failure != null) { throw new IllegalStateException(e.failure); }
        e.window = P11C4aEvidence.cuePresent(serverOutput, "a-held-window-" + e.index + ".ready");
        if (!e.clicked) {
            var view = P11ClientTransitions.view();
            if (view == null || view.scope() != Scope.PLAY || view.kind() != Kind.DEATH || view.outcome() != Outcome.BINDING) { return false; }
            e.clicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN);
            return false;
        }
        var state = P11ClientTransitions.view();
        if (state == null || state.outcome() != Outcome.COMPLETED || state.kind() != Kind.DEATH) { return false; }
        require(e.request != null && sameScene(e.request, state) && state.requestSeq() == e.request.requestSeq()
                && e.tryEncodes == 1 && e.unchangedStatuses >= 1 && e.mutations == 1
                && minecraft.player != null && minecraft.player.isAlive()
                && P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) == e.frames + 1,
                "HELD_CLIENT_TERMINAL_COUNTS");
        var map = new LinkedHashMap<String, Object>();
        map.put("status", "ONE_ORIGINAL_DEATH_TRY_PERIODIC_STATUS_NEGATIVES_AND_ONE_NATIVE_FRAME");
        map.put("index", e.index); map.put("actualRequest", e.request); map.put("completed", state);
        map.put("normalTryEncodes", e.tryEncodes); map.put("normalUnchangedStatusEncodes", e.unchangedStatuses);
        map.put("differentSequenceWireMutations", e.mutations); map.put("mutatedSequence", e.request.requestSeq() + 1);
        map.put("mutation", "ORIGINAL_STATUS_ENCODED_36_BYTE_BODY_SEQUENCE_ONLY");
        map.put("originalStatusEncoderInputUnchanged", true); map.put("controllerCounterWritten", false);
        map.put("normalUiDifferentSequenceClaimed", false); map.put("physicalHeldKeyClaimed", false);
        P11C4aEvidence.write(output, "held-client-" + e.index + ".json", map);
        e.done = true; run.completed++; return false;
    }

    public static Object begin(ChannelHandlerContext context, ConnectionProtocol protocol, Packet<?> packet) {
        var run = active;
        if (run == null || !run.role.equals("a") || run.episode == null || run.episode.done
                || protocol != ConnectionProtocol.PLAY || ConnectionUtils.getConnection(context) != run.connection
                || !(packet instanceof ServerboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionRequestPayload)) { return null; }
        var scope = new Encode(run.episode, ENCODE.get()); ENCODE.set(scope); return scope;
    }

    /** After the genuine fixed-body encoder returns. No request is constructed or sent here. */
    public static void encoded(FriendlyByteBuf buffer, int start, Object value) {
        var scope = ENCODE.get();
        if (scope == null || !(value instanceof Request request)) { return; }
        var e = scope.episode;
        if (scope.request != null || buffer.writerIndex() - start != 36 || buffer.getUnsignedByte(start) != 1
                || request.scope() != Scope.PLAY || request.kind() != Kind.DEATH || request.requestSeq() == Long.MAX_VALUE) {
            fail(e, "HELD_CLIENT_ENCODER_SHAPE"); return;
        }
        scope.request = request;
        if (request.command() == Command.TRY) {
            if (e.request != null) { fail(e, "HELD_CLIENT_SECOND_TRY"); }
            return;
        }
        if (!e.window) { return; }
        if (e.request == null || !sameScene(e.request, request) || request.requestSeq() != e.request.requestSeq()) {
            fail(e, "HELD_CLIENT_STATUS_ROUTE"); return;
        }
        if (e.unchangedStatuses == 0) { scope.countSame = true; }
        else if (e.mutations == 0) {
            buffer.setLong(start + 26, request.requestSeq() + 1);
            scope.mutated = true;
        }
    }

    public static void end(Object value, boolean normal) {
        if (!(value instanceof Encode scope)) { return; }
        if (scope.previous == null) { ENCODE.remove(); } else { ENCODE.set(scope.previous); }
        var e = scope.episode;
        if (!normal) { fail(e, "HELD_ORIGINAL_PACKET_ENCODER_THROW"); return; }
        if (scope.request == null) { fail(e, "HELD_P11_ENCODER_NOT_OBSERVED"); return; }
        if (scope.request.command() == Command.TRY) { e.request = scope.request; e.tryEncodes++; }
        if (scope.countSame) { e.unchangedStatuses++; }
        if (scope.mutated) { e.mutations++; }
    }

    static void release() { active = null; ENCODE.remove(); }
    private static boolean sameScene(Request a, Request b) { return a.scope() == b.scope() && a.connectionEpoch() == b.connectionEpoch()
            && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind(); }
    private static boolean sameScene(Request a, State b) { return a.scope() == b.scope() && a.connectionEpoch() == b.connectionEpoch()
            && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind(); }
    private static void fail(Episode e, String code) { if (e.failure == null) { e.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output, serverOutput;
        volatile Episode episode; volatile String failure; int ticks, completed;
        Run(Connection c, String r, Path o, Path s) { connection = c; role = r; output = o; serverOutput = s; }
    }
    private static final class Episode {
        final int index; final long frames; volatile Request request; volatile String failure;
        volatile boolean window, done; boolean clicked; volatile int tryEncodes, unchangedStatuses, mutations;
        Episode(int index, long frames) { this.index = index; this.frames = frames; }
    }
    private static final class Encode {
        final Episode episode; final Encode previous; Request request; boolean countSame, mutated;
        Encode(Episode episode, Encode previous) { this.episode = episode; this.previous = previous; }
    }
}
