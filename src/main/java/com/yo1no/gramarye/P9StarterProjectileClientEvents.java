package com.yo1no.gramarye;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only renderer binding for the normally tracked P9 projectile entity. */
@SuppressWarnings("removal")
@EventBusSubscriber(
        modid = Gramarye.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
final class P9StarterProjectileClientEvents {
    private P9StarterProjectileClientEvents() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                P9StarterProjectileRegistration.type(), ThrownItemRenderer::new);
    }
}
