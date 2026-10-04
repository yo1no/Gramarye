package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Excluded ordinary CONFIG-to-game reset episode; no conflict/credit producer is created. */
public final class P11C4aConfigResetProbe {
    private static volatile Run active;
    private P11C4aConfigResetProbe() { }

    public interface Fields {
        long c4a$resetTime();
        boolean c4a$resetPending();
        long c4a$resetChallenge();
        int c4a$resetLatency();
    }
    public interface Transport { boolean c4a$resetAutoRead(); Object c4a$resetDecoder(); }
    public interface LockObservation { boolean c4a$resetLockHeldByCurrentThread(); }

    private record Frame(long time, boolean pending, long challenge, int latency, long phase) { }

    static void start(MinecraftServer server, ServerPlayer actor, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && active == null && server.isSameThread()
                && List.of("a", "b").contains(role) && actor.getServer() == server && !actor.isFakePlayer()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor), "RESET_REAL_ACTOR_REQUIRED");
        var connection = actor.connection.getConnection();
        require(connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection()
                && !server.isSingleplayerOwner(actor.getGameProfile()), "RESET_REMOTE_CONNECTION_REQUIRED");
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        require(body != null && body.actor == actor
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0,
                "RESET_ORIGINAL_ACTOR_NOT_QUIESCENT");
        active = new Run(server, actor, role, output);
        P11C4aEvidence.write(output, "config-reset-armed.json", report("ARMED_NOT_ACCEPTANCE"));
        P11C4aEvidence.cue(output, role + "-config-reset-arm.ready");
    }

    static boolean tick(ServerPlayer successor, Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 2400
                && run.failure == null && run.connection.isConnected(), "RESET_OWNER_OR_DEADLINE");
        if (!run.switched) {
            if (!P11C4aEvidence.receiptPresent(clientOutput, "config-reset-armed.json")) { return false; }
            run.switched = true;
            run.actor.connection.switchToConfig();
            return false;
        }
        if (!run.returnRequested) {
            if (run.enterCompleted == null || run.configChallenge == null || run.configSends == 0
                    || !P11C4aEvidence.receiptPresent(clientOutput, "config-reset-config-terminal.json")
                    || !P11C4aEvidence.receiptPresent(clientOutput, "config-reset-deferred.json")) { return false; }
            require(run.connection.getPacketListener() == run.configuration
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null
                    && frame(run.configuration).equals(run.configChallenge) && run.configChallenge.pending
                    && run.gameConstructors == 0 && run.installReturns == 0 && count(run,
                            P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == run.loginFramesBefore,
                    "RESET_CONFIG_NOT_GENUINELY_PENDING");
            run.returnRequested = true;
            run.configuration.returnToWorld();
            return false;
        }
        if (run.completed == null || run.gameAckReturns == 0
                || !P11C4aEvidence.receiptPresent(clientOutput, "config-reset.json")) { return false; }
        require(run.failure == null && run.gameConstructors == 1 && run.installReturns == 1
                && run.gameSends == 1 && run.gameAckReturns == 1 && run.oldConfigAckReturns == 0
                && run.game != null && run.connection.getPacketListener() == run.game
                && successor != null && successor != run.actor && ((ServerGamePacketListenerImpl) run.game).player == successor
                && successor.connection.getConnection() == run.connection
                && run.server.getPlayerList().getPlayer(successor.getUUID()) == successor
                && successor.getUUID().equals(run.actor.getUUID())
                && P11NativeStorageBoundary.nativeDeliveryEligible(successor)
                && count(run, P11C4aNativeObservations.Event.CONFIG_CALL_RETURN) == run.configReturnsBefore + 1
                && count(run, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED) == run.factoriesBefore + 1
                && count(run, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == run.loginFramesBefore + 1,
                "RESET_FINAL_NATIVE_CONTINUITY_MISSING");
        P11C4aEvidence.write(run.output, "config-reset.json", report("ACTUAL_ORDINARY_CONFIG_TO_GAME_RESET_AND_FRESH_ACK"));
        active = null;
        return true;
    }

    static void startParking(MinecraftServer server, ServerPlayer actor, String role, Path output) throws IOException {
        start(server, actor, role, output);
        active.parkingMode = true;
    }

    /** Reused original parking caller performs returnToWorld; this method observes and does not call it. */
    static boolean beforeParkingReturn(Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.parkingMode && run.server.isSameThread() && !run.returnRequested,
                "CP_RESET_RETURN_OWNER");
        if (run.enterCompleted == null || run.configChallenge == null || run.configSends == 0
                || !P11C4aEvidence.receiptPresent(clientOutput, "config-reset-config-terminal.json")
                || !P11C4aEvidence.receiptPresent(clientOutput, "config-reset-deferred.json")) { return false; }
        require(run.connection.getPacketListener() == run.configuration && run.configChallenge.pending
                && frame(run.configuration).equals(run.configChallenge) && run.gameConstructors == 0
                && run.installReturns == 0 && count(run, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == run.loginFramesBefore,
                "CP_RESET_CONFIG_PENDING_BEFORE_RETURN");
        var source = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
        var body = source == null ? null : source.nativeRecipient(run.actor);
        require(body != null && body.actor == run.actor
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0,
                "CP_RESET_ORIGINAL_RETURN_BEFORE_NEW_CONFLICT");
        run.returnRequested = true; return true;
    }

    static void finishParking(ServerPlayer successor) throws IOException {
        var run = active;
        require(run != null && run.parkingMode && run.server.isSameThread() && run.failure == null
                && run.game instanceof P11ParkingPacketListener && run.completed != null
                && run.completed.requestSeq() > 0 && run.gameConstructors == 1 && run.installReturns == 1
                && run.gameSends == 1 && run.gameAckReturns == 1 && run.oldConfigAckReturns == 0
                && successor != null && successor != run.actor
                && successor.connection.getConnection() == run.connection
                && run.connection.getPacketListener() == successor.connection
                && run.server.getPlayerList().getPlayer(successor.getUUID()) == successor
                && P11NativeStorageBoundary.nativeDeliveryEligible(successor)
                && count(run, P11C4aNativeObservations.Event.CONFIG_CALL_RETURN) == run.configReturnsBefore + 1
                && count(run, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED) == run.factoriesBefore + 1
                && count(run, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == run.loginFramesBefore + 1
                && count(run, P11C4aNativeObservations.Event.PARKING_RESUME_RETURN) == run.resumeReturnsBefore + 1
                && count(run, P11C4aNativeObservations.Event.PARKING_RESUME_THROW) == run.resumeThrowsBefore,
                "CP_RESET_FINAL_RESET_ACK_AND_NATIVE_RETRY");
        P11C4aEvidence.write(run.output, "config-reset.json", report("ACTUAL_CONFIG_TO_PARKING_PENDING_RESET_AND_NEW_ACK"));
        active = null;
    }

    /** Original Common ctor RETURN, before the game listener's protocol installation. */
    public static void constructed(ServerCommonPacketListenerImpl listener, CommonListenerCookie cookie) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection
                || (run.parkingMode ? !(listener instanceof P11ParkingPacketListener)
                        : !(listener instanceof ServerGamePacketListenerImpl))) { return; }
        var game = listener;
        require(run.returnRequested && run.server.isSameThread() && run.configChallenge != null
                && ++run.gameConstructors == 1 && run.connection.getPacketListener() == run.configuration,
                "RESET_UNEXPECTED_GAME_CONSTRUCTOR");
        run.game = game;
        run.constructed = frame(game);
        run.cookieLatency = cookie.latency();
        require(!run.constructed.pending && run.constructed.challenge == 0 && run.constructed.phase == 0
                && run.constructed.latency == run.cookieLatency
                && run.cookieLatency == run.configChallenge.latency
                && run.constructed.time >= run.configChallenge.time,
                "RESET_ORIGINAL_CONSTRUCTOR_DID_NOT_RESET");
    }

    public static void sending(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection
                || !(packet instanceof ClientboundKeepAlivePacket challenge)) { return; }
        require(run.server.isSameThread() && run.connection.getPacketListener() == listener
                && unlocked(run), "RESET_CHALLENGE_NOT_CURRENT_OR_LOCKED");
        var value = frame(listener);
        require(value.pending && value.challenge == challenge.getId() && value.time == value.challenge
                && value.phase > 0, "RESET_NOT_NATIVE_CHALLENGE_FIELDS");
        if (listener instanceof ServerConfigurationPacketListenerImpl configuration && !run.returnRequested) {
            run.configuration = configuration;
            run.configChallenge = value;
            run.configDecoder = transport(run).c4a$resetDecoder();
            require(run.configDecoder != null && transport(run).c4a$resetAutoRead(), "RESET_CONFIG_TRANSPORT_MISSING");
        } else if (listener == run.game && run.installReturns == 1) {
            require(run.gameChallenge == null && value.challenge != run.configChallenge.challenge
                    && value.time - run.installed.time >= 15000L, "RESET_FRESH_GAME_CHALLENGE_NOT_NATIVE_DUE");
            run.gameChallenge = value;
        }
    }

    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return; }
        if (packet instanceof ClientboundKeepAlivePacket challenge) {
            if (listener == run.configuration && run.configChallenge != null
                    && challenge.getId() == run.configChallenge.challenge) { run.configSends++; }
            else if (listener == run.game && run.gameChallenge != null
                    && challenge.getId() == run.gameChallenge.challenge) { run.gameSends++; }
            return;
        }
        if (!(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
        var state = payload.state();
        if (state.kind() == Kind.ENTER_CONFIG && state.scope() == Scope.CONFIG && state.outcome() == Outcome.COMPLETED) {
            if (state.equals(run.enterCompleted)) { run.repeatedEnterCompleted++; return; }
            require(run.enterCompleted == null && state.actorGeneration() == 0 && state.requestSeq() == 0,
                    "RESET_CONFIG_TERMINAL_MISMATCH");
            run.enterCompleted = state;
        } else if (state.kind() == Kind.RETURN_TO_WORLD && state.outcome() == Outcome.COMPLETED) {
            require(run.returnRequested && run.enterCompleted != null && (run.parkingMode ? state.requestSeq() > 0 : state.requestSeq() == 0)
                    && state.connectionEpoch() == run.enterCompleted.connectionEpoch()
                    && state.sceneSerial() > run.enterCompleted.sceneSerial(), "RESET_RETURN_CORRELATION_MISMATCH");
            run.completed = state;
        }
    }

    public static boolean beforeProtocol(Connection connection, ProtocolInfo<?> protocol, PacketListener listener) {
        var run = active;
        if (run == null || connection != run.connection || listener != run.game) { return false; }
        require(run.server.isSameThread() && run.gameConstructors == 1 && run.returnRequested
                && protocol.id() == ConnectionProtocol.PLAY && protocol.flow() == connection.getReceiving()
                && connection.getPacketListener() == run.configuration && unlocked(run), "RESET_WRONG_NATIVE_PROTOCOL_INSTALL");
        run.beforeInstall = frame(run.configuration);
        run.autoReadBefore = transport(run).c4a$resetAutoRead();
        require(run.beforeInstall.equals(run.configChallenge) && run.beforeInstall.pending,
                "RESET_OLD_PENDING_CHANGED_BEFORE_NATIVE_INSTALL");
        return true;
    }

    public static void afterProtocol(Connection connection, boolean observed, boolean normal) {
        var run = active;
        if (!observed || run == null || connection != run.connection) { return; }
        if (!normal) { run.failure = "RESET_ORIGINAL_PROTOCOL_INSTALL_THROW"; return; }
        require(run.server.isSameThread() && connection.getPacketListener() == run.game
                && connection.getInboundProtocol().id() == ConnectionProtocol.PLAY && unlocked(run),
                "RESET_NATIVE_PROTOCOL_NOT_CURRENT");
        run.installed = frame(run.game);
        run.autoReadAfter = transport(run).c4a$resetAutoRead();
        run.decoderChanged = run.configDecoder != transport(run).c4a$resetDecoder();
        require(++run.installReturns == 1 && !run.installed.pending && run.installed.challenge == 0
                && run.installed.time == run.constructed.time && run.installed.latency == run.cookieLatency
                && run.installed.phase == run.beforeInstall.phase + 1
                && frame(run.configuration).equals(run.beforeInstall)
                && run.decoderChanged && transport(run).c4a$resetDecoder() != null && run.autoReadAfter,
                "RESET_FIELDS_PHASE_DECODER_OR_READS");
    }

    public static void ackReturned(ServerCommonPacketListenerImpl listener, long id, boolean wasPending,
            long beforeChallenge, boolean normal) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return; }
        if (!normal) { run.failure = "RESET_NATIVE_ACK_THROW"; return; }
        if (listener == run.configuration && run.returnRequested && run.configChallenge != null
                && id == run.configChallenge.challenge) { run.oldConfigAckReturns++; run.failure = "RESET_OLD_CONFIG_ACK_SUBMITTED"; }
        if (listener != run.game || run.gameChallenge == null || id != run.gameChallenge.challenge) { return; }
        run.consumed = frame(listener);
        require(wasPending && beforeChallenge == id && !run.consumed.pending && unlocked(run)
                && run.connection.getPacketListener() == listener
                && run.consumed.time == run.gameChallenge.time && run.consumed.challenge == id
                && run.consumed.phase == run.installed.phase, "RESET_FRESH_GAME_ACK_NOT_CONSUMED");
        run.gameAckReturns++;
    }

    static Map<String, Object> pending() { return report("PENDING_NOT_ACCEPTANCE"); }
    static void abort() { active = null; }

    private static long count(Run run, P11C4aNativeObservations.Event event) {
        return P11C4aNativeObservations.count(run.connection, event);
    }
    private static Transport transport(Run run) {
        return (Transport) run.connection;
    }
    private static boolean unlocked(Run run) {
        return !((LockObservation) (Object)
                ((P11KeepAliveBoundary.ConnectionAccess) run.connection).p11$keepAliveGuard()).c4a$resetLockHeldByCurrentThread();
    }
    private static Frame frame(ServerCommonPacketListenerImpl listener) {
        var fields = (Fields) listener;
        return new Frame(fields.c4a$resetTime(), fields.c4a$resetPending(), fields.c4a$resetChallenge(), fields.c4a$resetLatency(),
                ((P11KeepAliveBoundary.CommonAccess) listener).p11$keepAlivePhase());
    }
    private static Object value(Frame frame) { return frame == null ? Map.of("observed", false) : frame; }
    private static Map<String, Object> report(String status) {
        var run = active;
        if (run == null) { return Map.of("active", false); }
        var out = new LinkedHashMap<String, Object>();
        out.put("status", status); out.put("failure", run.failure == null ? "NONE" : run.failure);
        out.put("configChallenge", value(run.configChallenge)); out.put("beforeNativeInstall", value(run.beforeInstall));
        out.put("originalGameConstructor", value(run.constructed)); out.put("afterNativeInstall", value(run.installed));
        out.put("freshGameChallenge", value(run.gameChallenge)); out.put("afterFreshGameAck", value(run.consumed));
        out.put("configSendReturns", run.configSends); out.put("gameConstructors", run.gameConstructors);
        out.put("nativeProtocolInstallReturns", run.installReturns); out.put("gameSendReturns", run.gameSends);
        out.put("freshGameAckReturns", run.gameAckReturns); out.put("oldConfigAckReturnsAfterReturn", run.oldConfigAckReturns);
        out.put("configDecoderReplaced", run.decoderChanged); out.put("autoReadBefore", run.autoReadBefore);
        out.put("autoReadAfter", run.autoReadAfter); out.put("cookieLatency", run.cookieLatency);
        out.put("enterConfig", run.enterCompleted); out.put("returnCompleted", run.completed); out.put("ticks", run.ticks);
        out.put("configToParkingQualified", run.parkingMode && run.completed != null && run.gameAckReturns == 1);
        out.put("pendingTransferQualified", false);
        out.put("wrongAckQualified", false); out.put("nativeTimeoutQualified", false);
        out.put("hostExemptionQualified", false); out.put("fullC4aAcceptance", false);
        out.put("repeatedExactEnterCompleted", run.repeatedEnterCompleted);
        out.put("nativeCallerBaselines", Map.of("configReturns", run.configReturnsBefore,
                "factoryChecks", run.factoriesBefore, "loginFrames", run.loginFramesBefore,
                "parkingResumeReturns", run.resumeReturnsBefore, "parkingResumeThrows", run.resumeThrowsBefore));
        out.put("nativeCallerObserved", Map.of("configReturns", count(run, P11C4aNativeObservations.Event.CONFIG_CALL_RETURN),
                "factoryChecks", count(run, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED),
                "loginFrames", count(run, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED),
                "parkingResumeReturns", count(run, P11C4aNativeObservations.Event.PARKING_RESUME_RETURN),
                "parkingResumeThrows", count(run, P11C4aNativeObservations.Event.PARKING_RESUME_THROW)));
        return out;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor; final Connection connection; final String role; final Path output;
        final long configReturnsBefore, factoriesBefore, loginFramesBefore, resumeReturnsBefore, resumeThrowsBefore;
        volatile String failure;
        volatile ServerConfigurationPacketListenerImpl configuration;
        volatile ServerCommonPacketListenerImpl game;
        volatile Frame configChallenge, beforeInstall, constructed, installed, gameChallenge;
        volatile Frame consumed;
        State enterCompleted, completed;
        Object configDecoder;
        boolean switched, autoReadBefore, autoReadAfter, decoderChanged, parkingMode;
        volatile boolean returnRequested;
        int ticks, gameConstructors, installReturns, configSends, gameSends, cookieLatency, repeatedEnterCompleted;
        volatile int oldConfigAckReturns, gameAckReturns;
        Run(MinecraftServer server, ServerPlayer actor, String role, Path output) {
            this.server = server; this.actor = actor; this.connection = actor.connection.getConnection(); this.role = role; this.output = output;
            configReturnsBefore = count(this, P11C4aNativeObservations.Event.CONFIG_CALL_RETURN);
            factoriesBefore = count(this, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED);
            loginFramesBefore = count(this, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED);
            resumeReturnsBefore = count(this, P11C4aNativeObservations.Event.PARKING_RESUME_RETURN);
            resumeThrowsBefore = count(this, P11C4aNativeObservations.Event.PARKING_RESUME_THROW);
        }
    }
}
