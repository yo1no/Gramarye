package com.yo1no.gramarye;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/** Passing this early native task is only PENDING; it never grants a constructor/body permit. */
final class P11ConfigurationTask implements ConfigurationTask {
    static final Type TYPE = new Type("gramarye:p11_admission");
    private final ServerConfigurationPacketListenerImpl listener;

    P11ConfigurationTask(ServerConfigurationPacketListenerImpl listener) { this.listener = listener; }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        if (P11LiveTransitionBoundary.configurationTaskStarted(listener)
                == P11LiveTransitionBoundary.TaskDecision.FINISH) {
            P11ConfigurationBoundary.completeTask(listener);
        }
    }

    @Override
    public Type type() { return TYPE; }
}
