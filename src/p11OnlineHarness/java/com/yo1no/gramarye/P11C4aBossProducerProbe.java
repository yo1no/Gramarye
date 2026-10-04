package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.BossEvent;

/** Excluded, one ordinary boss instance and two actual authenticated native recipients. */
public final class P11C4aBossProducerProbe {
    private static final String NAME = "gramarye_c4a_owned_boss_producer";
    private static Run active;
    private P11C4aBossProducerProbe() { }

    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.UI_HELD; }

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) {
        require(selected() && P11C4aEvidence.enabled() && active == null && server.isSameThread()
                && actor != peer && actor.getServer() == server && peer.getServer() == server
                && !actor.isFakePlayer() && !peer.isFakePlayer()
                && actor.connection.getConnection().isConnected() && peer.connection.getConnection().isConnected()
                && actor.connection.getConnection().isEncrypted() && peer.connection.getConnection().isEncrypted()
                && !actor.connection.getConnection().isMemoryConnection() && !peer.connection.getConnection().isMemoryConnection()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer, "BOSS_ARM_EXACT_NATIVE_PLAYERS");
        var source = P11NativeStorageBoundary.nativeSourceOwner(peer);
        var body = source == null ? null : source.body(peer);
        require(body != null && body.complete && body.account.current == body && body.account.candidate == null,
                "BOSS_PEER_SOURCE_MISSING");
        active = new Run(server, actor, peer, output, source, body);
    }

    /** Original switchToConfig RETURN: setupOutbound(CONFIG) already completed, no Netty wait. */
    public static void switched(ServerGamePacketListenerImpl listener) {
        var run = active;
        if (run == null || listener != run.oldListener) { return; }
        try {
            require(run.server.isSameThread() && run.switchReturns++ == 0 && run.failure == null
                    && listener.player == run.actor && listener.getConnection() == run.connection
                    && run.connection.isConnected() && run.actor.isRemoved() && run.actor.hasDisconnected()
                    && ((P11LivePlayAccess) listener).p11$configurationActorRetired()
                    && protocol(run.connection) == ConnectionProtocol.CONFIGURATION,
                    "BOSS_ACTUAL_RETIRED_CONFIGURATION_INTERVAL");
            peer(run);
            run.oldListenerStillCurrent = run.connection.getPacketListener() == listener;
            run.boss = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.PROGRESS);
            run.bossId = run.boss.getId();
            run.operation = 1;
            run.boss.addPlayer(run.actor); run.boss.addPlayer(run.peer);
            members(run, true, true); counts(run, 1);
            run.operation = 2;
            run.boss.setProgress(0.5F);
            require(run.boss.getProgress() == 0.5F, "BOSS_PROGRESS_MUTATION_MISSING");
            members(run, true, true); counts(run, 2);
            run.operation = 3;
            run.boss.setVisible(false);
            require(!run.boss.isVisible(), "BOSS_HIDE_MUTATION_MISSING");
            members(run, true, true); counts(run, 3);
            run.operation = 4;
            run.boss.setVisible(true);
            require(run.boss.isVisible(), "BOSS_SHOW_MUTATION_MISSING");
            members(run, true, true); counts(run, 4);
            run.operation = 5;
            run.boss.removePlayer(run.actor); members(run, false, true);
            run.boss.removePlayer(run.peer); members(run, false, false); counts(run, 5);
            run.exercised = true;
        } catch (RuntimeException | Error failure) {
            run.failure = "BOSS_NATIVE_FIXTURE_OR_OBSERVER_FAILED";
        } finally {
            run.operation = 0;
            cleanup(run); // Only our local, unregistered boss. Never edits a player or native registry.
        }
    }

    /** Original common send HEAD/RETURN. Skipped retired producers must never reach this method. */
    public static void sent(ServerCommonPacketListenerImpl listener, Packet<?> packet, boolean returned) {
        var run = active;
        if (run == null || run.operation == 0 || !(packet instanceof ClientboundBossEventPacket boss)) { return; }
        try {
            Header header = header(boss);
            if (!header.id.equals(run.bossId)) { return; }
            require(run.server.isSameThread() && run.failure == null, "BOSS_SEND_OBSERVER_OWNER");
            int index = run.operation - 1;
            require(header.kind == expected(index), "BOSS_WRONG_ORIGINAL_PACKET_OPERATION");
            if (listener == run.oldListener) { if (returned) { run.aReturns++; } else { run.aEntries++; } }
            else if (listener == run.peer.connection && listener.getConnection() == run.peerConnection) {
                if (returned) { run.bReturns[index]++; } else { run.bEntries[index]++; }
            } else { throw new IllegalStateException("BOSS_FOREIGN_RECIPIENT"); }
        } catch (RuntimeException | Error secondary) { run.failure = "BOSS_SEND_OBSERVER_FAILED"; }
    }

    /** Parent already proves actual normal RETURN/Login; this adds the independent peer receipt. */
    static boolean finish(ServerPlayer successor) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.failure == null && ++run.waitTicks <= 2400
                && run.exercised && run.switchReturns == 1 && run.cleanupComplete,
                "BOSS_FINISH_INCOMPLETE_OR_FAILED");
        peer(run);
        require(successor != run.actor && successor.getUUID().equals(run.actor.getUUID())
                && successor.connection.getConnection() == run.connection && run.connection.isConnected()
                && run.connection.getPacketListener() == successor.connection
                && run.server.getPlayerList().getPlayer(successor.getUUID()) == successor
                && protocol(run.connection) == ConnectionProtocol.PLAY, "BOSS_ORIGINAL_RETURN_NOT_CURRENT");
        if (!P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-b"), "boss-producer-peer.json")) { return false; }
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_RETIRED_BOSS_PRODUCERS_PEER_DELIVERY_AND_NATIVE_RETURN_SUBSET");
        values.put("originalSwitchReturns", run.switchReturns);
        values.put("retiredListenerWasStillCurrentAtExercise", run.oldListenerStillCurrent);
        values.put("nativeProducerMethods", java.util.List.of("addPlayer", "broadcast", "setVisible", "removePlayer"));
        values.put("peerOriginalCommonSendEntries", run.bEntries); values.put("peerOriginalCommonSendReturns", run.bReturns);
        values.put("retiredOriginalCommonSendEntries", run.aEntries); values.put("retiredOriginalCommonSendReturns", run.aReturns);
        values.put("membershipAndVisibilityMutationsChecked", true); values.put("localUnregisteredBossEmpty", run.cleanupComplete);
        values.put("peerExactActorConnectionSourceEpochUnchanged", true); values.put("nativeReturnLoginObservedByParent", true);
        values.put("allBossRecipientsOrAllSchedulingPermutationsClaimed", false); values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "boss-producer.json", values);
        active = null;
        return true;
    }

    static void abort() { var run = active; if (run != null) { cleanup(run); active = null; } }

    private static void cleanup(Run run) {
        if (run.boss == null || run.boss.getPlayers().isEmpty()) { run.cleanupComplete = true; return; }
        // With no safe original destination, do not attempt a second failing network send.
        boolean safe = run.server.isSameThread() && run.boss.getPlayers().stream().allMatch(player ->
                ((P11LivePlayAccess) player.connection).p11$configurationActorRetired()
                        || player.connection.getConnection().isConnected()
                            && protocol(player.connection.getConnection()) == ConnectionProtocol.PLAY);
        if (!safe) { if (run.failure == null) { run.failure = "BOSS_LOCAL_CLEANUP_UNSAFE_NO_REPLAY"; } return; }
        try { run.boss.removeAllPlayers(); run.cleanupComplete = run.boss.getPlayers().isEmpty(); }
        catch (RuntimeException | Error secondary) { if (run.failure == null) { run.failure = "BOSS_LOCAL_CLEANUP_FAILED"; } }
    }

    private static void peer(Run run) {
        require(run.peer.connection.getConnection() == run.peerConnection && run.peerConnection.isConnected()
                && run.peerConnection.getPacketListener() == run.peer.connection && run.peer.isAlive()
                && protocol(run.peerConnection) == ConnectionProtocol.PLAY
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && run.source.body(run.peer) == run.peerBody && run.peerBody.account.current == run.peerBody
                && run.peerBody.account.candidate == null && run.source.diagnostics(run.peer.getUUID()).sourceEpoch() == run.peerEpoch
                && run.peer.getAdvancements() == run.advancements && run.peer.getStats() == run.stats,
                "BOSS_PEER_NATIVE_SOURCE_CHANGED");
    }
    private static void members(Run run, boolean actor, boolean peer) {
        require(run.boss.getPlayers().contains(run.actor) == actor && run.boss.getPlayers().contains(run.peer) == peer
                && run.boss.getPlayers().size() == (actor ? 1 : 0) + (peer ? 1 : 0), "BOSS_NATIVE_MEMBERSHIP_CHANGED");
    }
    private static void counts(Run run, int through) {
        require(run.failure == null && run.aEntries == 0 && run.aReturns == 0, "BOSS_RETIRED_NATIVE_SEND_REACHED");
        for (int i = 0; i < 5; i++) {
            require(run.bEntries[i] == (i < through ? 1 : 0) && run.bReturns[i] == (i < through ? 1 : 0),
                    "BOSS_PEER_NATIVE_SEND_COUNT");
        }
    }
    private static ConnectionProtocol protocol(Connection connection) {
        var encoder = connection.channel().pipeline().get(PacketEncoder.class);
        return encoder == null ? null : encoder.getProtocolInfo().id();
    }
    private static int expected(int index) { return switch (index) { case 0, 3 -> 1; case 1 -> 2; case 2, 4 -> 3; default -> -1; }; }
    private static Header header(ClientboundBossEventPacket packet) {
        Header[] result = new Header[1];
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            public void add(UUID id, Component name, float progress, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay,
                    boolean darken, boolean music, boolean fog) { result[0] = new Header(id, 1); }
            public void updateProgress(UUID id, float progress) { result[0] = new Header(id, 2); }
            public void remove(UUID id) { result[0] = new Header(id, 3); }
        });
        require(result[0] != null, "BOSS_UNEXPECTED_PACKET_KIND"); return result[0];
    }
    private record Header(UUID id, int kind) { }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final ServerGamePacketListenerImpl oldListener;
        final Connection connection, peerConnection; final Path output; final P11QualifiedSourceOwner source;
        final P11QualifiedSourceOwner.Body peerBody; final long peerEpoch;
        final net.minecraft.server.PlayerAdvancements advancements; final net.minecraft.stats.ServerStatsCounter stats;
        final int[] bEntries = new int[5], bReturns = new int[5];
        ServerBossEvent boss; UUID bossId; int operation, switchReturns, aEntries, aReturns, waitTicks;
        boolean exercised, cleanupComplete, oldListenerStillCurrent; String failure;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output,
                P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body) {
            this.server=server;this.actor=actor;this.peer=peer;this.output=output;this.source=source;peerBody=body;
            oldListener=actor.connection;connection=oldListener.getConnection();peerConnection=peer.connection.getConnection();
            peerEpoch=source.diagnostics(peer.getUUID()).sourceEpoch();advancements=peer.getAdvancements();stats=peer.getStats();
        }
    }
}
