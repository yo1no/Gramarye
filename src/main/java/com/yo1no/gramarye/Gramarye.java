package com.yo1no.gramarye;

import com.mojang.logging.LogUtils;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.migration.DescriptorMigrationAudit;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationPlan;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Gramarye.MOD_ID)
public final class Gramarye {
    public static final String MOD_ID = "gramarye";
    public static final String DATA_NAMESPACE = "gramarye";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Gramarye(IEventBus modBus) {
        MagicRegistries.register(modBus);
        new DescriptorMigrationAudit(SkillMigrationPlan.empty()).register(modBus);
    }
}
