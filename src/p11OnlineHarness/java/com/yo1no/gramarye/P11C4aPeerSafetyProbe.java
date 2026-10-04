package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Excluded ordinary world fixture and bounded death observations; no immunity or transition authority. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aPeerSafetyProbe {
    private static Run active;

    private P11C4aPeerSafetyProbe() { }

    /** Called once by the existing authenticated-roster owner, before its first baseline scene. */
    static void install(MinecraftServer server, ServerPlayer first, ServerPlayer peer, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && active == null && server.isSameThread(), "PEER_FIXTURE_OWNER");
        require(first != peer && !first.getUUID().equals(peer.getUUID())
                && first.getServer() == server && peer.getServer() == server
                && server.getPlayerList().getPlayer(first.getUUID()) == first
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && !first.isFakePlayer() && !peer.isFakePlayer()
                && first.connection.getConnection().isConnected() && peer.connection.getConnection().isConnected()
                && P11NativeStorageBoundary.nativeDeliveryEligible(first)
                && P11NativeStorageBoundary.nativeDeliveryEligible(peer), "PEER_FIXTURE_ACTUAL_ROSTER");
        require(peer.isAlive() && peer.fallDistance == 0.0F, "PEER_FIXTURE_ALREADY_UNSAFE");
        var level = peer.serverLevel();
        int floorY = Math.max(64, peer.blockPosition().getY() + 4);
        var minimum = new BlockPos(peer.blockPosition().getX() + 16, floorY, peer.blockPosition().getZ() + 16);
        var maximum = minimum.offset(6, 4, 6);
        require(floorY > level.getMinBuildHeight() && maximum.getY() < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(minimum) && level.getWorldBorder().isWithinBounds(maximum),
                "PEER_FIXTURE_WORLD_BOUNDS");
        require(level.getEntities((Entity) null, new AABB(minimum.getX(), minimum.getY(), minimum.getZ(),
                maximum.getX() + 1.0, maximum.getY() + 1.0, maximum.getZ() + 1.0)).isEmpty(),
                "PEER_FIXTURE_EXISTING_ENTITY");
        for (var position : BlockPos.betweenClosed(minimum, maximum)) {
            require(level.getBlockState(position).isAir() && level.getBlockEntity(position) == null,
                    "PEER_FIXTURE_NOT_EMPTY_AIR");
        }
        var run = new Run(server, first, peer, output);
        active = run;
        float healthBefore = peer.getHealth();
        int placed = 0;
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 7; z++) {
                    if (x != 0 && x != 6 && y != 0 && y != 4 && z != 0 && z != 6) { continue; }
                    var block = x == 3 && y == 4 && z == 3 ? Blocks.SEA_LANTERN : Blocks.GLASS;
                    require(level.setBlock(minimum.offset(x, y, z), block.defaultBlockState(), 3),
                            "PEER_FIXTURE_NATIVE_BLOCK_WRITE");
                    placed++;
                }
            }
        }
        // This is the original same-world connection teleport, not a respawn, source repair or status effect.
        double targetX = minimum.getX() + 3.5, targetY = floorY + 1.0, targetZ = minimum.getZ() + 3.5;
        peer.connection.teleport(targetX, targetY, targetZ, peer.getYRot(), peer.getXRot());
        require(placed == 170 && peer.getX() == targetX && peer.getY() == targetY && peer.getZ() == targetZ
                && peer.getHealth() == healthBefore && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && peer.connection.getConnection() == run.peerConnection, "PEER_FIXTURE_NATIVE_TELEPORT_RESULT");
        P11C4aEvidence.write(output, "peer-safety.json", Map.of(
                "status", "ORDINARY_ENCLOSURE_AND_NATIVE_TELEPORT_NOT_IMMUNITY",
                "role", "b", "placedBlocks", placed, "glassBlocks", placed - 1, "seaLanternBlocks", 1,
                "interiorAirBlocks", 75, "floorY", floorY, "healthBefore", healthBefore,
                "healthAfter", peer.getHealth(), "sameActorAndConnection", true));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void death(LivingDeathEvent event) {
        var run = active;
        if (run == null || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.getServer() != run.server || !run.server.isSameThread() || player.connection == null) { return; }
        var connection = player.connection.getConnection();
        boolean first = connection == run.firstConnection && player.getUUID().equals(run.firstId);
        boolean peer = connection == run.peerConnection && player.getUUID().equals(run.peerId);
        if (!first && !peer || first && run.firstObserved || peer && run.peerObserved) { return; }
        if (first) { run.firstObserved = true; } else { run.peerObserved = true; }
        var source = event.getSource();
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "FIRST_UNCANCELLED_NATIVE_LIVING_DEATH_EVENT_NOT_TERMINAL_PROOF");
        values.put("role", first ? "a-or-host" : "b");
        values.put("damageType", source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("UNREGISTERED"));
        values.put("directEntityType", entityType(source.getDirectEntity()));
        values.put("causingEntityType", entityType(source.getEntity()));
        values.put("dimension", player.level().dimension().location().toString());
        values.put("y", player.getY());
        values.put("health", player.getHealth());
        values.put("fallDistance", player.fallDistance);
        values.put("onGround", player.onGround());
        values.put("connected", connection.isConnected());
        values.put("sameRosterActor", run.server.getPlayerList().getPlayer(player.getUUID()) == player);
        values.put("serverTick", run.server.getTickCount());
        values.put("immediateRespawnRule", run.server.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN));
        try { P11C4aEvidence.write(run.output, first ? "first-actor-death.json" : "peer-death.json", values); }
        catch (IOException failure) { run.diagnosticWriteFailed = true; }
    }

    static void requireHealthy() {
        var run = active;
        require(run == null || !run.diagnosticWriteFailed, "PEER_DEATH_DIAGNOSTIC_WRITE_FAILED");
    }

    static void release() { active = null; }

    private static String entityType(Entity entity) {
        return entity == null ? "NONE" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }

    private static final class Run {
        final MinecraftServer server;
        final Connection firstConnection, peerConnection;
        final UUID firstId, peerId;
        final Path output;
        boolean firstObserved, peerObserved, diagnosticWriteFailed;
        Run(MinecraftServer server, ServerPlayer first, ServerPlayer peer, Path output) {
            this.server = server; this.output = output;
            firstConnection = first.connection.getConnection(); peerConnection = peer.connection.getConnection();
            firstId = first.getUUID(); peerId = peer.getUUID();
        }
    }
}
