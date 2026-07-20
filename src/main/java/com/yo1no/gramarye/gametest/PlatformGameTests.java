package com.yo1no.gramarye.gametest;

import com.yo1no.gramarye.Gramarye;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlatformGameTests {
    private PlatformGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void dedicatedServerLoads(GameTestHelper helper) {
        helper.succeed();
    }
}
