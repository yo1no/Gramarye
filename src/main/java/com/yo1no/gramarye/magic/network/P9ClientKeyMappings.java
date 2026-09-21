package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.Gramarye;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Client-only registration owner for the single P9 cast key mapping. */
@SuppressWarnings("removal")
@EventBusSubscriber(
        modid = Gramarye.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
final class P9ClientKeyMappings {
    private P9ClientKeyMappings() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        P9ClientCastInput.registerKeyMapping(event);
    }
}
