package com.yo1no.gramarye;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One process-isolated actual-platform P8-S2 reload publication test. */
@GameTestHolder("gramarye_p8_s2_reload")
@PrefixGameTestTemplate(false)
public final class P8S2ReloadGameTests {
    private P8S2ReloadGameTests() {}

    @GameTest(
            batch = "p8_s2_reload_negative",
            template = "p8_s2_reload_probe",
            timeoutTicks = 600)
    public static void globalFailureAfterP8StagingPreservesActivePublication(
            GameTestHelper helper) {
        P8S2ReloadFailureHarness.exerciseActualReload(helper.getLevel().getServer());
        helper.succeed();
    }
}
