package com.yo1no.gramarye.magic.definition.store;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Dedicated-only full-size P4-A3 carrier verification with the real server registry provider. */
@GameTestHolder("gramarye_p4_a3")
@PrefixGameTestTemplate(false)
public final class P4A3CarrierGameTests {
    private P4A3CarrierGameTests() {
    }

    @GameTest(
            templateNamespace = "gramarye_p4_a3",
            template = "p4_a3_probe",
            timeoutTicks = 4_800)
    public static void fullSizeMixedRegistryContexts(GameTestHelper helper) {
        var summary = P4A3HeapProbeMain.runDedicated(
                helper.getLevel().registryAccess());
        System.out.println(summary.line());
        helper.succeed();
    }
}
