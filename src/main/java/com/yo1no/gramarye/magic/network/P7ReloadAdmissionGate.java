package com.yo1no.gramarye.magic.network;

import net.minecraft.server.MinecraftServer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only the monotonic close request may be changed off the server thread. */
final class P7ReloadAdmissionGate {
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private State state = State.OPEN;

    boolean isOpen(MinecraftServer server) {
        requireServerThread(server);
        return state == State.OPEN && !closeRequested.get();
    }

    void requestReloadClose() {
        closeRequested.set(true);
    }

    boolean beginReconciliation(MinecraftServer server) {
        requireServerThread(server);
        state = State.RECONCILING;
        var observed = closeRequested.getAndSet(false);
        state = State.RECONCILING;
        return observed;
    }

    void close(MinecraftServer server) {
        requireServerThread(server);
        state = State.RECONCILING;
    }

    void open(MinecraftServer server) {
        requireServerThread(server);
        if (!closeRequested.get()) {
            state = State.OPEN;
        }
    }

    void reset(MinecraftServer server) {
        requireServerThread(server);
        closeRequested.set(false);
        state = State.OPEN;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new P7SemanticInvariantException("reload gate requires the server thread");
        }
    }

    private enum State {
        OPEN,
        RECONCILING
    }
}
