package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One isolated dedicated correctness smoke; it is not a production GameTest. */
@GameTestHolder("gramarye_p4_e0_research")
@PrefixGameTestTemplate(false)
public final class P4E0ResearchGameTestHolder {
    private P4E0ResearchGameTestHolder() {
    }

    @GameTest(
            templateNamespace = "gramarye_p4_e0_research",
            template = "p4_e0_research_smoke",
            timeoutTicks = 10_000)
    public static void runSyntheticResearchSmoke(GameTestHelper helper) {
        try {
            P4E0ResearchDedicatedCoordinator.run(helper.getLevel().getServer());
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("research dedicated fixture I/O failed", exception);
        }
    }
}
