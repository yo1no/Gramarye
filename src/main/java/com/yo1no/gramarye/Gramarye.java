package com.yo1no.gramarye;

import com.mojang.logging.LogUtils;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.migration.DescriptorMigrationAudit;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillDraftCreationService;
import com.yo1no.gramarye.magic.definition.submission.SkillIdSource;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Gramarye.MOD_ID)
public final class Gramarye {
    public static final String MOD_ID = "gramarye";
    public static final String DATA_NAMESPACE = "gramarye";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final PlayerSkillAttachmentService playerSkillAttachmentService;
    private final SkillDefinitionStoreService skillDefinitionStoreService;
    private final SkillIdSource skillIdSource;
    private final SkillDraftCreationService skillDraftCreationService;
    private final SkillSubmissionPolicyProvider skillSubmissionPolicyProvider;
    private final SkillDefinitionSubmissionService skillDefinitionSubmissionService;
    private final SkillSubmissionRecoveryService skillSubmissionRecoveryService;

    public Gramarye(IEventBus modBus) {
        MagicRegistries.register(modBus);
        new DescriptorMigrationAudit().register(modBus);
        playerSkillAttachmentService = PlayerSkillAttachmentService.registerOn(modBus);
        skillDefinitionStoreService = SkillDefinitionStoreService.registerOn(
                NeoForge.EVENT_BUS);
        skillIdSource = SkillDraftCreationService.randomUuidSkillIdSource();
        skillDraftCreationService = new SkillDraftCreationService(
                playerSkillAttachmentService, skillIdSource);
        skillSubmissionPolicyProvider = SkillSubmissionPolicyProvider.defaults();
        skillDefinitionSubmissionService = SkillDefinitionSubmissionService.production(
                playerSkillAttachmentService,
                skillDefinitionStoreService.submissionPort(),
                skillSubmissionPolicyProvider);
        skillSubmissionRecoveryService = SkillSubmissionRecoveryService.create(
                playerSkillAttachmentService,
                skillDefinitionStoreService.submissionPort());
        skillSubmissionRecoveryService.registerOn(NeoForge.EVENT_BUS);
    }

    /** Returns the controlled server skill subsystem port held by this composition root. */
    public SkillDefinitionStoreService skillDefinitionStoreService() {
        return skillDefinitionStoreService;
    }
}
