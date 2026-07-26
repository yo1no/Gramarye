package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/** Server-thread-confined controlled handle for one transient exact-revision pin. */
public final class ControlledSkillPin implements AutoCloseable {
    private final MinecraftServer server;
    private final SkillRevisionPin delegate;

    ControlledSkillPin(MinecraftServer server, SkillRevisionPin delegate) {
        this.server = Objects.requireNonNull(server, "server");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Returns the exact committed revision protected by this handle. */
    public SkillReference reference() {
        requireServerThread();
        return delegate.reference();
    }

    /** Returns whether this handle has released its transient pin. */
    public boolean isClosed() {
        requireServerThread();
        return delegate.isClosed();
    }

    /** Releases the pin; repeated calls remain idempotent through the underlying handle. */
    @Override
    public void close() {
        requireServerThread();
        delegate.close();
    }

    private void requireServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "controlled skill pin operation must run on the server thread");
        }
    }
}
