package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.Gramarye;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Gramarye.MOD_ID)
final class P7ServerLifecycleEvents {
    private static final P7ServerLifecycleCoordinator LIFECYCLE = P7NetworkComposition.lifecycle();

    private P7ServerLifecycleEvents() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent
    static void started(ServerStartedEvent event) {
        LIFECYCLE.start(event.getServer());
    }

    @SubscribeEvent
    static void stopping(ServerStoppingEvent event) {
        LIFECYCLE.stop(event.getServer());
    }

    @SubscribeEvent
    static void disconnected(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer actor) {
            LIFECYCLE.onDisconnect(actor.getServer(), actor);
        }
    }

    @SubscribeEvent
    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer actor) {
            LIFECYCLE.requestSync(actor.getServer(), actor.getUUID());
        }
    }

    @SubscribeEvent
    static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer actor) {
            LIFECYCLE.requestSync(actor.getServer(), actor.getUUID());
        }
    }

    @SubscribeEvent
    static void reloadCompleted(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            LIFECYCLE.onReloadComplete(event.getPlayerList().getServer());
        }
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        LIFECYCLE.tick(event.getServer());
    }
}
