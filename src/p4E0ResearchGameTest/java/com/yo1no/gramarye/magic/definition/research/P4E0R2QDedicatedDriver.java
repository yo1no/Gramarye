package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** Dedicated-only dispatcher for the reduced, non-formal P4-E0-R2Q-A smoke. */
final class P4E0R2QDedicatedDriver {
    private P4E0R2QDedicatedDriver() {
    }

    static void run(MinecraftServer server) throws IOException {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException(
                    "R2Q dedicated smoke must run on the GameTest server thread");
        }
        P4E0R2QMain.runDedicatedSmoke(
                server,
                requiredPath("gramarye.p4e0.research.fixtureRoot"),
                requiredPath("gramarye.p4e0.research.reportRoot"));
    }

    private static Path requiredPath(String property) {
        var value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("R2Q dedicated path is absent: " + property);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
