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
import java.util.Objects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
    private final P5ServerRuntimeConfig p5ServerRuntimeConfig;
    private final SkillRuntimeService skillRuntimeService;

    public Gramarye(IEventBus modBus, ModContainer exactContainer) {
        Objects.requireNonNull(modBus, "modBus");
        Objects.requireNonNull(exactContainer, "exactContainer");
        if (!MOD_ID.equals(exactContainer.getModId())) {
            throw new IllegalArgumentException("unexpected mod container");
        }
        var exactFacade = new P4E2QualificationFacade();
        MagicRegistries.register(modBus);
        new DescriptorMigrationAudit().register(modBus);
        playerSkillAttachmentService = PlayerSkillAttachmentService.registerOn(modBus);
        skillDefinitionStoreService = SkillDefinitionStoreService.registerOn(
                NeoForge.EVENT_BUS,
                playerSkillAttachmentService,
                exactFacade.storeView(),
                exactFacade.playerView());
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
                skillDefinitionStoreService.submissionPort(),
                skillDefinitionStoreService.onlineReconciliationDependency(),
                exactFacade.submissionView());
        skillSubmissionRecoveryService.registerOn(NeoForge.EVENT_BUS);
        p5ServerRuntimeConfig = new P5ServerRuntimeConfig(modBus, exactContainer);
        skillRuntimeService = SkillRuntimeService.create(
                NeoForge.EVENT_BUS,
                skillDefinitionStoreService,
                skillSubmissionPolicyProvider);
        NeoForge.EVENT_BUS.addListener(this::handleP5RuntimeStarted);
        exactContainer.registerExtensionPoint(P4E2QualificationFacade.class, exactFacade);
    }

    private void handleP5RuntimeStarted(ServerStartedEvent event) {
        var limits = p5ServerRuntimeConfig.snapshotForStarted();
        skillRuntimeService.handleRuntimeStarted(event, limits);
    }

    /** Returns the controlled server skill subsystem port held by this composition root. */
    public SkillDefinitionStoreService skillDefinitionStoreService() {
        return skillDefinitionStoreService;
    }
}
