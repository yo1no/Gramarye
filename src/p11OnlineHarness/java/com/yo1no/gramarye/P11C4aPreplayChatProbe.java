package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Exact-C native parking drop and later original secure-chat validation observations. */
public final class P11C4aPreplayChatProbe {
    private static volatile Run active;
    private P11C4aPreplayChatProbe() { }
    static boolean selected() { return lateKey() || P11C4aScenario.MODE == P11C4aScenario.Mode.QUEUED_BENIGN; }
    static boolean lateKey() { return P11C4aScenario.MODE == P11C4aScenario.Mode.LATE_KEY; }
    static void start(MinecraftServer server,ServerPlayer actor,ServerPlayer peer,Path output) {
        P11C4aEvidence.require(selected() && active == null && server.isSameThread() && server.isDedicatedServer()
                && actor != peer && actor.connection.getConnection().isEncrypted()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                && P11NativeStorageBoundary.nativeDeliveryEligible(peer), "CHAT_SERVER_REAL_AUTHENTICATED_ROSTER");
        active = new Run(server,actor,peer,output);
    }
    public static long beforeDrop(P11ParkingPacketListener listener) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return -1; }
        if (run.connection.getPacketListener() != listener) {
            run.failure = "CHAT_DROP_NOT_CURRENT_ACTORLESS_PARKING";
        }
        return listener.benignDrops();
    }
    public static void afterDrop(P11ParkingPacketListener listener,long before,boolean chat,boolean normal) {
        var run = active; if (run == null || before < 0) { return; }
        if (!normal || listener.benignDrops() != before + 1 || run.connection.getPacketListener() != listener) {
            run.failure = "CHAT_NATIVE_BENIGN_DROP_NOT_EXACT";
        }
        if (chat) { run.chatDrops++; } else { run.debugDrops++; }
    }
    /** Native original validate returned a session; no data/session/key fields inspected. */
    public static void validated(ServerGamePacketListenerImpl listener) {
        var run = active; if (run == null || listener.getConnection() != run.connection) { return; }
        if (!run.preplaySealed || run.connection.getPacketListener() != listener || listener.player == run.actor
                || run.server.getPlayerList().getPlayer(run.actor.getUUID()) != listener.player) {
            run.failure = "CHAT_VALIDATION_NOT_NEW_ORIGINAL_GAME_LISTENER";
        }
        run.validations++;
    }
    public static void reset(ServerGamePacketListenerImpl listener) {
        var run = active; if (run != null && listener.getConnection() == run.connection) { run.resets++; }
    }
    /** Called from the existing parking producer just before issuing its ordinary manual Retry cue. */
    static boolean beforeRetry() throws IOException {
        var run = active; if (run == null) { return true; }
        healthy(run);
        if (!P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"),"preplay-chat-observed.json")) { return false; }
        if (!lateKey()) {
            P11C4aEvidence.require(run.chatDrops <= 1 && run.debugDrops <= 1, "CHAT_MORE_THAN_ONE_QUEUED_NATIVE_PACKET");
            if (run.chatDrops < 1 || run.debugDrops < 1) { return false; } // Client send RETURN is not server receipt.
        }
        if (!run.preplaySealed) {
            P11C4aEvidence.require(run.connection.getPacketListener() instanceof P11ParkingPacketListener
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null
                    && run.chatDrops == (lateKey() ? 0 : 1) && run.debugDrops == (lateKey() ? 0 : 1)
                    && run.validations == 0 && run.resets == 0, "CHAT_PREPLAY_DROP_OR_ZERO_VALIDATION_MISMATCH");
            run.preplaySealed = true;
            P11C4aEvidence.write(run.output,"preplay-chat-parking.json",report(run,"ACTUAL_PREPLAY_BENIGN_NATIVE_DROP_NO_PLAYER_OR_CHAT_VALIDATION"));
        }
        return true;
    }
    static boolean finish(ServerPlayer successor) throws IOException {
        var run = active; P11C4aEvidence.require(run != null,"CHAT_SERVER_FINISH_OWNER"); healthy(run);
        if (!P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"),"preplay-chat-client.json")
                || run.validations == 0 || run.resets == 0) { return false; }
        P11C4aEvidence.require(run.preplaySealed && run.validations == 1 && run.resets == 1
                && successor != null && successor != run.actor && successor.connection.getConnection() == run.connection
                && run.connection.getPacketListener() == successor.connection
                && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == successor
                && P11NativeStorageBoundary.nativeDeliveryEligible(successor), "CHAT_ORIGINAL_NEW_LOGIN_VALIDATION_NOT_EXACT");
        // resetPlayerChatState appends this publication to the original FutureChain.
        // Its normal return is not proof that the queued native player callback ran yet.
        if (successor.getChatSession() == null) { return false; }
        if (!run.finished) {
            run.finished = true;
            P11C4aEvidence.write(run.output,"preplay-chat.json",report(run,"ACTUAL_NEW_LOGIN_NATIVE_SIGNATURE_VALIDATION_AND_CHAT_RESET"));
        }
        return true;
    }
    static void abort() { active = null; }
    private static void healthy(Run run) {
        P11C4aEvidence.require(run.failure == null && run.server.isSameThread() && ++run.ticks <= 2400
                && run.connection.isConnected() && run.server.isRunning() && run.peer.isAlive()
                && run.peer.connection.getConnection() == run.peerConnection && run.peerConnection.isConnected()
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer,"CHAT_SERVER_OWNER_PEER_OR_DEADLINE");
    }
    private static Map<String,Object> report(Run run,String status) {
        var values = new LinkedHashMap<String,Object>(); values.put("status",status); values.put("lateKeyMode",lateKey());
        values.put("originalParkingChatDropReturns",run.chatDrops); values.put("originalParkingDebugDropReturns",run.debugDrops);
        values.put("originalNewGameValidationReturns",run.validations); values.put("originalNewGameResetReturns",run.resets);
        values.put("peerSameLiveActorAndConnection",true); values.put("fullC4aAcceptance",false); return values;
    }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor,peer; final Connection connection,peerConnection; final Path output;
        volatile String failure; volatile int chatDrops,debugDrops; int ticks,validations,resets; boolean preplaySealed,finished;
        Run(MinecraftServer server,ServerPlayer actor,ServerPlayer peer,Path output) {
            this.server=server;this.actor=actor;this.peer=peer;this.output=output;
            connection=actor.connection.getConnection();peerConnection=peer.connection.getConnection();
        }
    }
}
