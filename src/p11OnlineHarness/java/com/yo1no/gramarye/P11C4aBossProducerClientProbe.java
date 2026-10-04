package com.yo1no.gramarye;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;

/** Exact owned fixture ID only; original native boss overlay already consumed each packet. */
public final class P11C4aBossProducerClientProbe {
    private static UUID id;
    private static Connection connection;
    private static int received;
    private static boolean completed;
    private P11C4aBossProducerClientProbe() { }

    public static void received(ClientPacketListener listener, ClientboundBossEventPacket packet) {
        if (!P11C4aEvidence.enabled() || P11C4aScenario.MODE != P11C4aScenario.Mode.UI_HELD
                || !P11C4aEvidence.property("role").equals("b")) { return; }
        var minecraft = Minecraft.getInstance();
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            public void add(UUID actual, Component name, float progress, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay,
                    boolean darken, boolean music, boolean fog) {
                if (id == null) {
                    if (!name.equals(Component.literal("gramarye_c4a_owned_boss_producer"))) { return; }
                    require(color == BossEvent.BossBarColor.BLUE && overlay == BossEvent.BossBarOverlay.PROGRESS
                            && !darken && !music && !fog, "BOSS_CLIENT_FIXTURE_HEADER");
                    id = actual; connection = listener.getConnection();
                }
                if (id.equals(actual)) { count(1); }
            }
            public void updateProgress(UUID actual, float progress) {
                if (actual.equals(id)) { require(progress == 0.5F, "BOSS_CLIENT_PROGRESS_VALUE"); count(2); }
            }
            public void remove(UUID actual) { if (actual.equals(id)) { count(3); } }
            private void count(int kind) {
                require(!completed && minecraft.isSameThread() && minecraft.getConnection() == listener
                        && listener.getConnection() == connection && connection.getPacketListener() == listener
                        && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection()
                        && minecraft.player != null && minecraft.player.isAlive() && minecraft.level != null,
                        "BOSS_CLIENT_EXACT_NATIVE_PEER");
                require(received < 5 && kind == switch (received) { case 0, 3 -> 1; case 1 -> 2; default -> 3; },
                        "BOSS_CLIENT_ORIGINAL_PACKET_SEQUENCE");
                received++;
                if (received == 5) {
                    try {
                        P11C4aEvidence.write(P11C4aEvidence.root().resolve("client-b"), "boss-producer-peer.json", java.util.Map.of(
                                "status", "ORIGINAL_CLIENT_BOSS_HANDLER_RETURNED_FOR_FIVE_OWNED_PACKETS",
                                "sameOwnedBossId", true, "nativeHandlerReturns", received,
                                "operations", java.util.List.of("ADD", "UPDATE_PROGRESS", "REMOVE", "ADD", "REMOVE"),
                                "connectionStillOriginalLivePlay", true, "fullC4aAcceptance", false));
                        completed = true;
                    } catch (IOException failure) { throw new IllegalStateException("BOSS_CLIENT_RECEIPT_IO", failure); }
                }
            }
        });
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
}
