package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** Dedicated-only coordinator for one bounded, synthetic R1 observation. */
final class P4E0ResearchDedicatedCoordinator {
    private P4E0ResearchDedicatedCoordinator() {
    }

    static void run(MinecraftServer server) throws IOException {
        if (!(server instanceof GameTestServer)) {
            throw new IllegalStateException("research dedicated smoke did not run on GameTestServer");
        }
        var fixtureRoot = requiredPath("gramarye.p4e0.research.fixtureRoot");
        var reportRoot = requiredPath("gramarye.p4e0.research.reportRoot");
        if (!"dedicated-smoke".equals(
                System.getProperty("gramarye.p4e0.research.runMode"))) {
            throw new IllegalStateException("research dedicated scenario is not selected");
        }
        P4E0ResearchMain.markDedicatedRunning(reportRoot);
        P4E0ResearchMain.runDedicated(fixtureRoot, reportRoot);
    }

    private static Path requiredPath(String property) {
        var value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("research dedicated path property is absent");
        }
        return Path.of(value);
    }
}
