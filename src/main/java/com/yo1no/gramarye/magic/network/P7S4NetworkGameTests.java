package com.yo1no.gramarye.magic.network;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.Gramarye;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Bounded generated-player tests of the real recovery/E2, session and transport graph. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class P7S4NetworkGameTests {
    private P7S4NetworkGameTests() {}

    @GameTest(batch = "p7_s4_network", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void actualPostE2LoginOpensOneSessionAndSubmitsOneInitialFullSet(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var fixture = place(server, 1, "p7s4-live-login");
        runGuarded(fixture, () -> {
            var epoch = requireEpoch(fixture.playerId());
            helper.runAfterDelay(2, () -> runGuarded(fixture, () -> {
            assertFullSet(helper, drainP7(fixture.channel()), 1);
            var actor = fixture.current();
            P7NetworkComposition.lifecycle().onLoginReady(server, actor);
            P7NetworkComposition.lifecycle().tick(server);
            helper.assertTrue(requireEpoch(fixture.playerId()) == epoch,
                    "duplicate normal handoff must preserve the exact connection epoch");
            helper.assertTrue(drainP7(fixture.channel()).isEmpty(),
                    "ALREADY_ACTIVE must not request a duplicate initial full snapshot");
            fixture.close();
            helper.assertTrue(!hasNativeSession(fixture.playerId()),
                    "actual logout must remove the production session");
            helper.succeed();
            }));
        });
    }

    @GameTest(batch = "p7_s4_network", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void actualRespawnDimensionAndReconnectPreserveThenReplaceSessionIdentity(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var fixture = place(server, 2, "p7s4-live-life");
        runGuarded(fixture, () -> {
            var initialEpoch = requireEpoch(fixture.playerId());
            helper.runAfterDelay(2, () -> com.yo1no.gramarye.P7S4LoginManaGameTests.runCooldownFixtureAfterTick(
                    helper, () -> runGuarded(fixture, () -> {
            assertFullSet(helper, drainP7(fixture.channel()), 1);
            var before = fixture.current();
            var replacement = com.yo1no.gramarye.P7S4LoginManaGameTests.respawnNativeGameTestPlayer(before);
            helper.assertTrue(replacement != before && fixture.current() == replacement,
                    "actual respawn must replace the player object while keeping current UUID");
            helper.assertTrue(requireEpoch(fixture.playerId()) == initialEpoch,
                    "respawn must preserve the connection session epoch");
            var end = server.getLevel(Level.END);
            helper.assertTrue(end != null, "server-bound dimension fixture requires the End");
            replacement.changeDimension(new DimensionTransition(end, replacement, DimensionTransition.DO_NOTHING));
            replacement.hasChangedDimension();
            helper.assertTrue(replacement.serverLevel() == end
                            && requireEpoch(fixture.playerId()) == initialEpoch,
                    "dimension completion must preserve the exact connection epoch");
            P7NetworkComposition.lifecycle().tick(server);
            helper.assertTrue(drainP7(fixture.channel()).isEmpty(),
                    "respawn/dimension triggers must respect the existing twenty-tick cadence");
            helper.runAfterDelay(22, () -> runGuarded(fixture, () -> {
                assertFullSet(helper, drainP7(fixture.channel()), 2);
                fixture.close();
                helper.assertTrue(!hasNativeSession(fixture.playerId()),
                        "actual logout must invalidate the old epoch");
                var reconnected = place(server, 2, "p7s4-live-again");
                runGuarded(reconnected, () -> {
                    var newEpoch = requireEpoch(reconnected.playerId());
                    helper.assertTrue(newEpoch > initialEpoch,
                            "actual reconnect must allocate a newer epoch");
                    helper.runAfterDelay(2, () -> runGuarded(reconnected, () -> {
                        assertFullSet(helper, drainP7(reconnected.channel()), 1);
                        reconnected.close();
                        helper.succeed();
                    }));
                });
            }));
            }), () -> {}, primary -> cleanupFailure(fixture, primary)));
        });
    }

    private static void assertFullSet(
            GameTestHelper helper, List<CustomPacketPayload> payloads, long sequence) {
        helper.assertTrue(payloads.size() == 2
                        && payloads.get(0) instanceof PlayerManaSyncPayload
                        && payloads.get(1) instanceof SkillCooldownSyncPayload,
                "actual current connection must submit exactly mana then cooldown");
        var mana = ((PlayerManaSyncPayload) payloads.get(0)).snapshot();
        var cooldown = ((SkillCooldownSyncPayload) payloads.get(1)).snapshot();
        helper.assertTrue(mana.syncSequence() == sequence
                        && mana.availability() == PlayerManaSnapshot.Availability.AVAILABLE
                        && mana.balance() == 0L,
                "fresh authoritative mana snapshot must carry exact sequence and available zero");
        helper.assertTrue(cooldown.syncSequence() == sequence && cooldown.entries().isEmpty(),
                "production cooldown full snapshot must carry exact sequence and empty entries");
    }

    private static List<CustomPacketPayload> drainP7(EmbeddedChannel channel) {
        channel.runPendingTasks();
        var observation = (OriginalP7Writes) channel.pipeline().get("p7-stock-original-writes");
        if (observation == null) { throw new AssertionError("exact original P7 write observer missing"); }
        var payloads = List.copyOf(observation.payloads);
        observation.payloads.clear();
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(packet);
        }
        return payloads;
    }

    /** Typed observation before the genuine memory-channel encoder, never a substitute sender. */
    private static final class OriginalP7Writes extends ChannelOutboundHandlerAdapter {
        private final List<CustomPacketPayload> payloads = new ArrayList<>();

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            context.write(message, promise);
            if (message instanceof ClientboundCustomPayloadPacket custom
                    && (custom.payload() instanceof PlayerManaSyncPayload
                            || custom.payload() instanceof SkillCooldownSyncPayload
                            || custom.payload() instanceof IntentAckPayload)) {
                if (payloads.size() >= 64) { throw new AssertionError("bounded original P7 write observation exceeded"); }
                payloads.add(custom.payload());
            }
        }
    }

    /** Read-only stock-fixture check; no session opening, delivery or source permission. */
    public static boolean hasNativeSession(UUID playerId) {
        return nativeSession(playerId).isPresent();
    }

    private static long requireEpoch(UUID playerId) {
        return nativeSession(playerId).orElseThrow().connectionEpoch();
    }

    private static java.util.Optional<P7SessionIdentity> nativeSession(UUID playerId) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            throw new AssertionError("stock session observation requires the current server thread");
        }
        var actor = server.getPlayerList().getPlayer(playerId);
        if (actor == null || actor.connection == null) { return java.util.Optional.empty(); }
        // Stock fixture observation only, never a C2S UUID-only producer.
        return P7NetworkComposition.production().connectionEpochSource()
                .captureAuthenticatedSession(playerId, actor.connection.getConnection()).identity();
    }

    private static ConnectedPlayer place(MinecraftServer server, int suffix, String name) {
        var id = new UUID(0x7440000000004000L, 0x8000000000000000L + suffix);
        var actor = com.yo1no.gramarye.P7S4LoginManaGameTests.connectNativeGameTestPlayer(server, id, name);
        var channel = (EmbeddedChannel) actor.connection.getConnection().channel();
        channel.pipeline().addLast("p7-stock-original-writes", new OriginalP7Writes());
        var fixture = new ConnectedPlayer(server, id, channel);
        return fixture;
    }

    private static void runGuarded(ConnectedPlayer fixture, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | Error failure) {
            cleanupFailure(fixture, failure);
            throw failure;
        }
    }

    private static void cleanupFailure(ConnectedPlayer fixture, Throwable primary) {
        try {
            fixture.close();
        } catch (RuntimeException | Error cleanup) {
            if (cleanup != primary) {
                primary.addSuppressed(cleanup);
            }
        }
    }

    private record ConnectedPlayer(MinecraftServer server, UUID playerId, EmbeddedChannel channel)
            implements AutoCloseable {
        private ServerPlayer current() {
            var actor = server.getPlayerList().getPlayer(playerId);
            if (actor == null) {
                throw new AssertionError("generated player is no longer current");
            }
            return actor;
        }

        @Override
        public void close() {
            try {
                var actor = server.getPlayerList().getPlayer(playerId);
                if (actor != null) {
                    server.getPlayerList().remove(actor);
                }
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
