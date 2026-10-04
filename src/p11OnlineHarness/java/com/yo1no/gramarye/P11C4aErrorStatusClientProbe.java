package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.connection.ConnectionUtils;

/** Only one original periodic STATUS body is perturbed. No additional packet or UI TRY is sent. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aErrorStatusClientProbe {
    private static volatile Run active;
    private static final ThreadLocal<Encode> ENCODE = new ThreadLocal<>();
    private P11C4aErrorStatusClientProbe() { }
    static void start(Connection connection, Path output, P11C4aNativeErrorProbe.Mode mode) {
        if (!P11C4aErrorStatusProbe.selected(mode)) { return; }
        require(active == null, "ERROR_STATUS_CLIENT_DUPLICATE");
        active = new Run(connection, output);
    }
    /** The parent error probe cannot finish before the terminal STATUS experiment has its own receipt. */
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        if (run == null) { return true; }
        require(minecraft.isSameThread() && run.failure == null && ++run.ticks <= 2400,
                "ERROR_STATUS_CLIENT_FAILED_OR_DEADLINE");
        if (run.finished) { return true; }
        // The real server writes this only after the gated original ingress observations.
        // In the raw-primary mode its original runServer close may precede this client tick.
        if (!P11C4aEvidence.receiptPresent(run.serverOutput, "native-error-status-server.json")) {
            require(run.connection.isConnected() && minecraft.getConnection() != null
                    && minecraft.getConnection().getConnection() == run.connection,
                    "ERROR_STATUS_CLIENT_EARLY_DISCONNECT");
            run.window = P11C4aEvidence.cuePresent(run.serverOutput, "a-error-status-window.ready");
            return false; // Missing server evidence never completes this experiment.
        }
        require(run.request != null && run.tryEncodes == 1 && run.same == 1 && run.mutations == 1,
                "ERROR_STATUS_CLIENT_MISSING_ORIGINAL_ENCODINGS");
        var current = minecraft.getConnection();
        var values = new LinkedHashMap<String,Object>();
        values.put("status", "ACTUAL_ORIGINAL_ENCODER_SEQUENCE_PERTURBATION_REQUIRES_SERVER_CLOSED_PROOF");
        values.put("actualOriginalTry", run.request); values.put("tryEncodes", run.tryEncodes);
        values.put("sameStatusEncodes", run.same); values.put("differentSequenceMutations", run.mutations);
        values.put("wireSequence", run.request.requestSeq() + 1);
        values.put("mutation", "ONE_ORIGINAL_36_BYTE_STATUS_BODY_SEQUENCE_ONLY_N_TO_N_PLUS_ONE");
        values.put("normalUiDifferentSequenceClaimed", false); values.put("controllerCounterWritten", false);
        values.put("additionalSenderInvoked", false);
        values.put("connectionOpenAtTerminalReadout", run.connection.isConnected());
        values.put("currentGameListenerPresentAtTerminalReadout", current != null);
        values.put("sameCurrentConnectionAtTerminalReadout", current != null && current.getConnection() == run.connection);
        values.put("serverReceiptPresenceIsSynchronizationOnly", true);
        P11C4aEvidence.write(run.output, "native-error-status-client.json", values);
        run.finished = true;
        return true;
    }
    public static Object begin(ChannelHandlerContext context, ConnectionProtocol protocol, Packet<?> packet) {
        var run = active;
        if (run == null || run.finished || protocol != ConnectionProtocol.PLAY
                || ConnectionUtils.getConnection(context) != run.connection
                || !(packet instanceof ServerboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionRequestPayload)) { return null; }
        var scope = new Encode(run, ENCODE.get()); ENCODE.set(scope); return scope;
    }
    public static void encoded(FriendlyByteBuf buffer, int start, Object value) {
        var scope = ENCODE.get();
        if (scope == null || !(value instanceof Request request)) { return; }
        var run = scope.run;
        if (scope.request != null || buffer.writerIndex() - start != 36 || buffer.getUnsignedByte(start) != 1
                || request.scope() != Scope.PLAY || request.kind() != Kind.DEATH
                || request.requestSeq() <= 0 || request.requestSeq() == Long.MAX_VALUE) {
            fail(run, "ERROR_STATUS_CLIENT_WIRE_SHAPE"); return;
        }
        scope.request = request;
        if (request.command() == Command.TRY) {
            if (run.request != null) { fail(run, "ERROR_STATUS_CLIENT_SECOND_TRY"); }
            return;
        }
        if (!run.window || run.mutations != 0) { return; }
        if (run.request == null || !same(run.request, request) || request.requestSeq() != run.request.requestSeq()) {
            fail(run, "ERROR_STATUS_CLIENT_WRONG_SCENE"); return;
        }
        if (run.same == 0) { scope.same = true; }
        else { buffer.setLong(start + 26, request.requestSeq() + 1); scope.mutated = true; }
    }
    public static void end(Object value, boolean normal) {
        if (!(value instanceof Encode scope)) { return; }
        if (scope.previous == null) { ENCODE.remove(); } else { ENCODE.set(scope.previous); }
        var run = scope.run;
        if (!normal || scope.request == null) { fail(run, "ERROR_STATUS_ORIGINAL_ENCODER_DID_NOT_RETURN"); return; }
        if (scope.request.command() == Command.TRY) { run.request = scope.request; run.tryEncodes++; }
        if (scope.same) { run.same++; }
        if (scope.mutated) { run.mutations++; }
    }
    static void release() { active = null; ENCODE.remove(); }
    private static boolean same(Request a, Request b) { return a.scope() == b.scope()
            && a.connectionEpoch() == b.connectionEpoch() && a.sceneSerial() == b.sceneSerial()
            && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind(); }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean ok, String code) { P11C4aEvidence.require(ok, code); }
    private static final class Run {
        final Connection connection; final Path output, serverOutput;
        volatile Request request; volatile String failure; volatile boolean window, finished;
        volatile int tryEncodes, same, mutations; int ticks;
        Run(Connection c, Path o) { connection=c; output=o; serverOutput=o.resolveSibling("server"); }
    }
    private static final class Encode {
        final Run run; final Encode previous; Request request; boolean same, mutated;
        Encode(Run r, Encode p) { run=r; previous=p; }
    }
}
