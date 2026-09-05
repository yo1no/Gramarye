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
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn ignored) {
        MIRROR.onConnected();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut ignored) {
        MIRROR.onDisconnected();
    }

    @SubscribeEvent
    static void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            MIRROR.onClientWorldUnload();
        }
    }
}
