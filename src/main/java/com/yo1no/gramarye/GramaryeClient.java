package com.yo1no.gramarye;

import com.yo1no.gramarye.client.presentation.api.ClientProfileFactories;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.NewRegistryEvent;

@Mod(value = Gramarye.MOD_ID, dist = Dist.CLIENT)
public final class GramaryeClient {
    private final P8ClientPresentationLifecycle p8Lifecycle;

    public GramaryeClient(IEventBus modBus) {
        modBus.addListener(GramaryeClient::registerClientRegistry);
        var p8Execution = P8ClientPresentationExecution.production();
        var p8State = new P8ClientPresentationState(
                () -> Minecraft.getInstance().isSameThread(), p8Execution);
        p8Lifecycle = new P8ClientPresentationLifecycle(p8State, p8Execution, modBus);
        P8ClientPayloadDispatchFactory.installClient(p8State);
    }

    private static void registerClientRegistry(NewRegistryEvent event) {
        event.register(ClientProfileFactories.registry());
    }
}
