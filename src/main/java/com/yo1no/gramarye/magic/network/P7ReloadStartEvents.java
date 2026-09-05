package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.Gramarye;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = Gramarye.MOD_ID)
final class P7ReloadStartEvents {
    private static final P7ReloadAdmissionGate RELOAD_GATE = P7NetworkComposition.reloadGate();

    private P7ReloadStartEvents() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent
    static void reloadStarted(AddReloadListenerEvent event) {
        RELOAD_GATE.requestReloadClose();
    }
}
