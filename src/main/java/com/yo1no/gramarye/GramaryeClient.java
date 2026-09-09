package com.yo1no.gramarye;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = Gramarye.MOD_ID, dist = Dist.CLIENT)
public final class GramaryeClient {
    private final P8ClientPresentationLifecycle p8Lifecycle;

    public GramaryeClient(IEventBus modBus) {
        var p8State = new P8ClientPresentationState(
                () -> Minecraft.getInstance().isSameThread());
        p8Lifecycle = new P8ClientPresentationLifecycle(p8State, modBus);
        P8ClientPayloadDispatchFactory.installClient(p8State);
    }
}
