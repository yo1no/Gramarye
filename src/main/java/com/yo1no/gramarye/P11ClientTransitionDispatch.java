package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Common-only port: registration never resolves a client class on a dedicated server. */
interface P11ClientTransitionPort {
    void handle(P11TransitionProtocol.State state, Connection connection, ICommonPacketListener listener);
}

final class P11ClientTransitionDispatch {
    private static volatile P11ClientTransitionPort installed;

    static synchronized void install(P11ClientTransitionPort port) {
        Objects.requireNonNull(port, "port");
        if (installed != null) { throw new IllegalStateException("P11_CLIENT_PORT_ALREADY_INSTALLED"); }
        installed = port;
    }

    static void handle(P11TransitionProtocol.State state, Connection connection,
            ICommonPacketListener listener) {
        var port = installed;
        if (port == null) { throw new IllegalStateException("P11_CLIENT_PORT_UNAVAILABLE"); }
        port.handle(state, connection, listener);
    }

    private P11ClientTransitionDispatch() { }
}
