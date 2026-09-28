package com.yo1no.gramarye;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Excluded C3 probe on the same authenticated actor after actual normal CONFIG removal. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P11NativePresenceProbe {
    private static Observation active;

    private P11NativePresenceProbe() {}

    /** Use before the original switchToConfig, making spectate an applicable native command. */
    static void prepare(MinecraftServer server, ServerPlayer actor) {
        require(System.getProperty(P11SourceWriterClientHarness.OUTPUT_PROPERTY) != null
                        && "presence-handoff".equals(System.getProperty("gramarye.p11.sourceWriter.case")),
                "presence probe is restricted to its explicitly selected engineering run");
        require(server.isSameThread() && actor.getServer() == server && !actor.isFakePlayer()
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actor.connection != null && actor.connection.getConnection().isConnected(),
                "presence preparation needs the actual current authenticated actor");
        require(actor.setGameMode(GameType.SPECTATOR), "normal online gamemode did not change");
    }

    static String run(MinecraftServer server, ServerPlayer actor) {
        require(active == null && server.isSameThread() && actor.getServer() == server
                        && actor.isRemoved() && server.getPlayerList().getPlayer(actor.getUUID()) == null
                        && P11NativeStorageBoundary.detachedPresence(actor),
                "presence probe requires actual normal logout provenance");
        var observation = new Observation(actor);
        active = observation;
        var position = actor.position();
        var mode = actor.gameMode.getGameModeForPlayer();
        var camera = actor.getCamera();
        var vehicle = EntityType.PIG.create(actor.serverLevel());
        require(vehicle != null, "native vehicle fixture was unavailable");
        try {
            observation.cancelMode = true;
            require(!actor.setGameMode(GameType.CREATIVE) && observation.modeEvents == 1,
                    "native mode cancellation was suppressed or changed");
            observation.cancelMode = false;
            require(!actor.setGameMode(GameType.CREATIVE) && observation.modeEvents == 2
                            && actor.gameMode.getGameModeForPlayer() == mode,
                    "detached mode mutation or missing native event");

            var messages = new Messages();
            var source = server.createCommandSourceStack().withEntity(actor).withLevel(actor.serverLevel())
                    .withPosition(position).withSource(messages);
            server.getCommands().performPrefixedCommand(source, "gamemode creative");
            require(messages.contains("gamemode unavailable") && actor.gameMode.getGameModeForPlayer() == mode,
                    "gamemode command did not report the actual unavailable outcome");
            messages.clear();

            observation.cancelTeleport = true;
            server.getCommands().performPrefixedCommand(source, "teleport @s ~1 ~ ~");
            require(observation.teleportEvents == 1 && !messages.contains("unavailable")
                            && actor.position().equals(position), "native teleport cancellation was reclassified");
            messages.clear();
            observation.cancelTeleport = false;
            server.getCommands().performPrefixedCommand(source, "teleport @s ~1 ~ ~");
            require(observation.teleportEvents == 2 && messages.contains("teleport unavailable")
                            && actor.position().equals(position), "teleport command lied or moved detached actor");
            messages.clear();
            require(!actor.teleportTo(actor.serverLevel(), position.x + 1, position.y, position.z,
                            Set.of(), actor.getYRot(), actor.getXRot()), "direct detached teleport succeeded");
            expectUnavailable(() -> actor.moveTo(position.x + 2, position.y, position.z));
            expectUnavailable(() -> actor.moveTo(position.x + 2, position.y, position.z, 30, 10));
            expectUnavailable(() -> actor.teleportRelative(1, 0, 0));
            expectUnavailable(() -> actor.setCamera(vehicle));
            server.getCommands().performPrefixedCommand(source, "spectate");
            require(messages.contains("camera unavailable") && actor.getCamera() == camera,
                    "spectate command lied or changed detached camera");

            observation.cancelMount = true;
            require(!actor.startRiding(vehicle, true) && observation.mountEvents == 1,
                    "native mount cancellation was suppressed");
            observation.cancelMount = false;
            require(!actor.startRiding(vehicle, true) && observation.mountEvents == 2
                            && actor.getVehicle() == null && vehicle.getPassengers().isEmpty(),
                    "detached ride mutated a native relation");

            observation.cancelTravel = true;
            require(actor.changeDimension(new DimensionTransition(actor.serverLevel(), position, Vec3.ZERO,
                            actor.getYRot(), actor.getXRot(), DimensionTransition.DO_NOTHING)) == null
                            && observation.travelEvents == 1, "native travel event cancellation was suppressed");
            observation.cancelTravel = false;
            require(actor.changeDimension(new DimensionTransition(actor.serverLevel(), position, Vec3.ZERO,
                            actor.getYRot(), actor.getXRot(), DimensionTransition.DO_NOTHING)) == null
                            && observation.travelEvents == 2, "native removed dimension result changed");

            for (int slot = 499; slot <= 503; slot++) {
                var access = actor.getSlot(slot);
                var prior = access.get().copy();
                require(!access.set(prior), "transient detached menu slot accepted a write: " + slot);
            }
            for (int slot : new int[] {0, 200}) {
                var access = actor.getSlot(slot);
                require(access.set(access.get().copy()), "persistent inventory branch was prohibited: " + slot);
            }
            require(actor.position().equals(position) && actor.getCamera() == camera
                            && actor.gameMode.getGameModeForPlayer() == mode,
                    "presence probe changed unsupported detached state");
            return "layer=ACTUAL_QUALIFIED_DETACHED_NATIVE_AND_COMMAND_CALLS\n"
                    + "modeEvents=" + observation.modeEvents + "\nteleportEvents=" + observation.teleportEvents
                    + "\nmountEvents=" + observation.mountEvents + "\ntravelEvents=" + observation.travelEvents
                    + "\npersistentInventoryWrites=true\ntransientSlotWrites=false\n"
                    + "positionCameraModeUnchanged=true\nnewCastAuthority=false\n";
        } finally { active = null; }
    }

    @SubscribeEvent static void mode(PlayerEvent.PlayerChangeGameModeEvent event) {
        var observation = active;
        if (observation == null || event.getEntity() != observation.actor) { return; }
        observation.modeEvents++;
        event.setNewGameMode(GameType.ADVENTURE);
        if (observation.cancelMode) { event.setCanceled(true); }
    }

    @SubscribeEvent static void teleport(EntityTeleportEvent.TeleportCommand event) {
        var observation = active;
        if (observation == null || event.getEntity() != observation.actor) { return; }
        observation.teleportEvents++;
        event.setTargetX(event.getTargetX() + 5);
        if (observation.cancelTeleport) { event.setCanceled(true); }
    }

    @SubscribeEvent static void mount(EntityMountEvent event) {
        var observation = active;
        if (observation == null || event.getEntityMounting() != observation.actor) { return; }
        observation.mountEvents++;
        if (observation.cancelMount) { event.setCanceled(true); }
    }

    @SubscribeEvent static void travel(EntityTravelToDimensionEvent event) {
        var observation = active;
        if (observation == null || event.getEntity() != observation.actor) { return; }
        observation.travelEvents++;
        if (observation.cancelTravel) { event.setCanceled(true); }
    }

    private static void expectUnavailable(Runnable action) {
        boolean refused = false;
        try { action.run(); }
        catch (P11NativePresence.Unavailable expected) { refused = true; }
        require(refused, "void detached native helper did not report unavailable");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static final class Observation {
        final ServerPlayer actor;
        int modeEvents, teleportEvents, mountEvents, travelEvents;
        boolean cancelMode, cancelTeleport, cancelMount, cancelTravel;
        Observation(ServerPlayer actor) { this.actor = actor; }
    }

    private static final class Messages implements CommandSource {
        private final List<String> values = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { values.add(message.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
        boolean contains(String text) { return values.stream().anyMatch(value -> value.contains(text)); }
        void clear() { values.clear(); }
    }
}
