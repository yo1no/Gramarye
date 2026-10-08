package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.Gramarye;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P7ClientLifecycleEvents {
    private static final P7ClientMirror MIRROR =
            new P7ClientMirror(() -> Minecraft.getInstance().isSameThread());

    static {
        P7ClientMirrorDispatchFactory.installClient(MIRROR);
    }

    private P7ClientLifecycleEvents() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != event.getPlayer() || minecraft.getConnection() != event.getPlayer().connection
                || event.getConnection() != event.getPlayer().connection.getConnection()) { return; }
        MIRROR.onConnected(event.getConnection(), event.getPlayer().connection);
        P9ClientCastInput.onConnectionOpened();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getConnection() != null && event.getPlayer() != null
                && MIRROR.captureDispatchGeneration(event.getConnection(), event.getPlayer().connection) == 0
                && Minecraft.getInstance().getConnection() != event.getPlayer().connection) { return; }
        MIRROR.onDisconnected();
        P9ClientCastInput.onConnectionClosed();
    }

    @SubscribeEvent
    static void onClientPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != event.getNewPlayer() || minecraft.level != event.getNewPlayer().level()
                || minecraft.getConnection() != event.getNewPlayer().connection
                || event.getConnection() != event.getNewPlayer().connection.getConnection()) { return; }
        MIRROR.onPlayerContextReplaced(event.getConnection(), event.getNewPlayer().connection);
        P9ClientCastInput.onPlayerContextReplaced();
    }

    @SubscribeEvent
    static void onClientLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel) {
            P9ClientCastInput.onClientWorldLoaded();
        }
    }

    @SubscribeEvent
    static void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel && event.getLevel() == Minecraft.getInstance().level) {
            MIRROR.onClientWorldUnload();
            P9ClientCastInput.onClientWorldUnloaded();
        }
    }

    static P7ClientMirror mirror() { return MIRROR; }
}
