package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** Dedicated-only dispatcher for isolated R1 smoke and R2 Matrix-F observations. */
final class P4E0ResearchDedicatedCoordinator {
    private P4E0ResearchDedicatedCoordinator() {
    }

    static void run(MinecraftServer server) throws IOException {
        if (!(server instanceof GameTestServer)) {
            throw new IllegalStateException("research dedicated smoke did not run on GameTestServer");
        }
        var runMode = System.getProperty("gramarye.p4e0.research.runMode");
        if ("r2-combined".equals(runMode)) {
            P4E0ResearchR2DedicatedDriver.run(server);
            return;
        }
        if ("r2q-smoke".equals(runMode)) {
            P4E0R2QDedicatedDriver.run(server);
            return;
        }
        if ("r2q-formal".equals(runMode)) {
            P4E0R2QFormalDedicatedDriver.run(server);
            return;
        }
        if ("r2q-runner-smoke".equals(runMode)) {
            P4E0R2QFormalDedicatedDriver.runRunnerSmoke(server);
            return;
        }
        if (!"dedicated-smoke".equals(runMode)) {
            throw new IllegalStateException("research dedicated scenario is not selected");
        }
        var fixtureRoot = requiredPath("gramarye.p4e0.research.fixtureRoot");
        var reportRoot = requiredPath("gramarye.p4e0.research.reportRoot");
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
