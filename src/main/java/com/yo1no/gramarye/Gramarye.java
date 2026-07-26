package com.yo1no.gramarye;

import com.mojang.logging.LogUtils;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.migration.DescriptorMigrationAudit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Gramarye.MOD_ID)
public final class Gramarye {
    public static final String MOD_ID = "gramarye";
    public static final String DATA_NAMESPACE = "gramarye";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final SkillDefinitionStoreService skillDefinitionStoreService;

    public Gramarye(IEventBus modBus) {
        MagicRegistries.register(modBus);
        new DescriptorMigrationAudit().register(modBus);
        skillDefinitionStoreService = SkillDefinitionStoreService.registerOn(
                NeoForge.EVENT_BUS);
    }

    /** Returns the controlled server skill subsystem port held by this composition root. */
    public SkillDefinitionStoreService skillDefinitionStoreService() {
        return skillDefinitionStoreService;
    }
}
