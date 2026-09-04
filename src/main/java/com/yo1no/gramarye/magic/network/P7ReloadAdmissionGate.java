package com.yo1no.gramarye.magic.network;

import net.minecraft.server.MinecraftServer;

/** Event-unwired S3 reload admission gate; production begins open. */
final class P7ReloadAdmissionGate {
    private State state = State.OPEN;

    boolean isOpen(MinecraftServer server) {
        requireServerThread(server);
        return state == State.OPEN;
    }

    void close(MinecraftServer server) {
        requireServerThread(server);
        state = State.RELOAD_IN_PROGRESS;
    }

    void open(MinecraftServer server) {
        requireServerThread(server);
        state = State.OPEN;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new P7SemanticInvariantException("reload gate requires the server thread");
        }
    }

    private enum State {
        OPEN,
        RELOAD_IN_PROGRESS
    }
}
