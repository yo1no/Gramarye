package com.yo1no.gramarye;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.HexFormat;
import java.util.jar.JarFile;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Process-isolated actual-client proof for the P9-S5 normal-player composition path.
 *
 * <p>The harness creates one throw-away integrated world, sends the production command through
 * the client play connection, and submits casts only through the registered R key mapping. It
 * observes world effects and, only in this excluded test source set, reads the existing P7
 * owner's Store/Attachment ports. It never mutates player state through those observation
 * ports, invokes ingress directly, or accesses/modifies private platform identities.</p>
 */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P9S5ClientRuntimeHarness {
    private static final long WORLD_SEED = 0x50395335434c4945L;
    private static final String WORLD_DIRECTORY = "p9-s5-client-runtime-world";
    private static final int PHASE_DEADLINE_TICKS = 1_200;
    private static final int SUPPRESSED_CAST_QUIET_TICKS = 20;
    private static final double SAFE_FLIGHT_CLEARANCE = 16.0;
    private static final String COMMAND = "gramarye starter";
    private static final String KEY_DESCRIPTION = "key.gramarye.cast";
    private static final String KEY_CATEGORY = "key.categories.gramarye";
    private static final InputConstants.Key CAST_KEY =
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R);
    private static final ResourceLocation PROJECTILE_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "starter_projectile");
    private static final ResourceLocation SOUND_EVENT =
            ResourceLocation.withDefaultNamespace("entity.experience_orb.pickup");

    private static final List<String> MARKERS = new ArrayList<>(36);
    private static final String TEMPLATE_PACK = "file/p10-owned-template";
    private static final String TEMPLATE_JSON = "data/gramarye/gramarye/skill_templates/starter_bolt_v0.json";

    private static volatile Phase phase = Phase.BOOTSTRAP;
    private static volatile Throwable asynchronousFailure;
    private static volatile MinecraftServer firstServer;
    private static volatile MinecraftServer activeServer;
    private static volatile ServerPlayer firstServerPlayer;
    private static volatile UUID playerId;
    private static volatile int serverPreparationGeneration;
    private static volatile long preparedACatalogGeneration;
    private static volatile long preparedBCatalogGeneration;
    private static volatile boolean bReloadAdmissionOpen;
    private static volatile String lastP7ReloadGateState = "UNOBSERVED";
    private static volatile boolean lastP7ReloadCloseRequested = true;
    private static volatile long lastP7ReloadGateTick = -1L;
    private static volatile int spoofedCommandEventCount;
    private static volatile boolean spoofedProvisionRejected;
    private static volatile int commandEventCount;
    private static volatile int commandCompletionCount;
    private static volatile int clientLoggingInCount;
    private static volatile int clientLoggingOutCount;
    private static volatile Connection latestLoginConnection;
    private static volatile ClientPacketListener latestLoginPlayListener;
    private static volatile boolean serverReconfigurationStarted;
    private static volatile boolean serverConfigurationRunStarted;
    private static volatile boolean reentryServerValidationScheduled;
    private static volatile boolean reentryServerValidationComplete;
    private static volatile boolean wrongThreadInputRejected;
    private static volatile int suppressionProbeIndex;
    private static volatile SuppressionProbe suppressionProbe;
    private static volatile boolean focusRestoredByRenderCallback;
    private static volatile boolean pauseTransitionObserved;
    private static volatile boolean preWorldSuppressedClickQueued;
    private static volatile boolean oldSessionSuppressedClickQueued;
    private static volatile int castOrdinal;
    private static volatile P9StarterProjectile serverProjectile;
    private static volatile P9StarterProjectile clientProjectile;
    private static volatile UUID projectileId;
    private static volatile Cow target;
    private static volatile int targetInitialHealthBits;
    private static volatile boolean serverImpactObserved;
    private static volatile boolean serverLeaveObserved;
    private static volatile boolean exactFourDamageObserved;
    private static volatile float expectedDamage = 4.0F;
    private static volatile int damageCalls;
    private static volatile boolean exactAttributionObserved;
    private static volatile boolean p10AsyncComplete;
    private static volatile boolean recoveryATerminal;
    private static volatile boolean recoveryBTerminal;
    private static volatile boolean oldAProvedBeforeBSave;
    private static volatile boolean reloadCleanupCast;
    private static volatile boolean activeAReloadCleanupComplete;
    private static volatile int activeAReloadDamageCalls;
    private static volatile UUID retiredAProjectileId;
    private static volatile P8ServerPresentationService wrongServerProbe;
    private static volatile boolean p8WrongThreadRejected;
    private static volatile boolean p8WrongServerRejected;
    private static SkillDefinitionStoreService observedStore;
    private static PlayerSkillAttachmentService observedAttachments;
    private static SkillRuntimeService observedRuntime;
    private static SkillReference revisionA;
    private static SkillReference revisionB;
    private static SkillReference revisionC;
    private static String revisionABytes;
    private static String revisionBBytes;
    private static String revisionCBytes;
    private static Path frozenProductionJar;
    private static String frozenProductionJarHash;
    private static volatile boolean firstTargetCleanupComplete;
    private static volatile int p8SoundEvents;
    private static volatile boolean p8AfterParticlesRenderObserved;

    private static P8ClientPresentationState presentationState;
    private static P8ClientPresentationExecution presentationExecution;
    private static Connection firstClientTransport;
    private static ClientPacketListener firstClientPlayListener;
    private static P8ProfileCatalogSnapshot firstCatalogSnapshot;
    private static long firstP8ConnectionGeneration;
    private static long firstCatalogGeneration;
    private static int firstLoginCount;
    private static int firstLogoutCount;
    private static long phaseTicks;
    private static int firstParticleBaseline;
    private static int firstParticleMaximum;
    private static boolean firstServerMarkerWritten;
    private static boolean firstClientMarkerWritten;
    private static boolean firstHitMarkerWritten;
    private static boolean firstDamageMarkerWritten;
    private static boolean firstSoundMarkerWritten;
    private static boolean firstParticleMarkerWritten;
    private static boolean firstRenderMarkerWritten;
    private static boolean secondServerMarkerWritten;
    private static boolean secondHitMarkerWritten;
    private static boolean secondDamageMarkerWritten;
    private static boolean terminal;

    private P9S5ClientRuntimeHarness() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onClientPostTick(ClientTickEvent.Post ignored) {
        if (terminal) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        try {
            phaseTicks++;
            if (phaseTicks > PHASE_DEADLINE_TICKS) {
                throw new IllegalStateException(
                        "phase tick deadline exceeded: " + phase + presentationReadiness());
            }
            requireNoAsynchronousFailure();
            sampleParticleCount(minecraft);
            switch (phase) {
                case BOOTSTRAP -> bootstrap(minecraft);
                case WAIT_FOR_PREWORLD_SUPPRESSION -> waitForPreworldSuppression(minecraft);
                case WAIT_FOR_FIRST_WORLD -> waitForFirstWorld(minecraft);
                case WAIT_FOR_PLAY_REENTRY -> waitForPlayReentry(minecraft);
                case WAIT_FOR_FIRST_COMMAND -> waitForFirstCommand(minecraft);
                case WAIT_FOR_SECOND_COMMAND -> waitForSecondCommand(minecraft);
                case WAIT_FOR_WRONG_THREAD_INPUT_REJECTION ->
                    waitForWrongThreadInputRejection(minecraft);
                case WAIT_FOR_SUPPRESSION_GATE -> waitForSuppressionGate(minecraft);
                case WAIT_FOR_SUPPRESSION_FLUSH -> waitForSuppressionFlush(minecraft);
                case WAIT_FOR_SUPPRESSED_CAST_QUIET -> waitForSuppressedCastQuiet(minecraft);
                case WAIT_FOR_FIRST_CAST -> waitForFirstCast(minecraft);
                case WAIT_FOR_FIRST_TARGET_CLEANUP -> waitForFirstTargetCleanup(minecraft);
                case WAIT_FOR_REOPEN_REQUEST -> requestReopen(minecraft);
                case WAIT_FOR_REOPENED_WORLD -> waitForReopenedWorld(minecraft);
                case WAIT_FOR_REOPEN_FLUSH -> waitForReopenFlush(minecraft);
                case WAIT_FOR_SECOND_CAST -> waitForSecondCast(minecraft);
                case WAIT_FOR_P10_A_RELOAD_PREPARATION -> waitForActiveAReloadPreparation(minecraft);
                case WAIT_FOR_P10_A_RELOAD_CAST -> waitForActiveAReloadCast(minecraft);
                case WAIT_FOR_P10_B_RELOAD -> waitForBReload(minecraft);
                case WAIT_FOR_P10_B_COMMAND -> waitForBCommand(minecraft);
                case WAIT_FOR_P10_B_CAST -> waitForBCast(minecraft);
                case WAIT_FOR_P10_B_SAVE -> waitForBSave(minecraft);
                case WAIT_FOR_P10_B_REOPEN_REQUEST -> requestBReopen(minecraft);
                case WAIT_FOR_P10_B_REOPENED -> waitForBReopened(minecraft);
                case WAIT_FOR_P10_C_RELOAD -> waitForCReload(minecraft);
                case WAIT_FOR_P10_C_COMMAND -> waitForCCommand(minecraft);
                case WAIT_FOR_P10_CONTROLS -> waitForP10Controls(minecraft);
                case TERMINAL -> {
                    // Terminal work stops the game loop in the same client tick.
                }
            }
        } catch (RuntimeException | Error failure) {
            fail(minecraft, failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onRenderFramePreArmIntertickFocus(RenderFrameEvent.Pre ignored) {
        if (terminal
                || phase != Phase.WAIT_FOR_SUPPRESSION_GATE
                || suppressionProbe != SuppressionProbe.INTERTICK_FOCUS) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread(),
                    "focus transition setup is off the client thread");
            minecraft.setWindowActive(false);
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderFramePre(RenderFrameEvent.Pre ignored) {
        if (terminal
                || phase != Phase.WAIT_FOR_SUPPRESSION_GATE
                || suppressionProbe != SuppressionProbe.INTERTICK_FOCUS) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread(),
                    "focus transition observation is off the client thread");
            require(!minecraft.isWindowActive(),
                    "inter-tick focus probe was restored before the product callback");
            minecraft.setWindowActive(true);
            focusRestoredByRenderCallback = true;
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPauseChanged(ClientPauseChangeEvent.Post event) {
        if (terminal
                || phase != Phase.WAIT_FOR_SUPPRESSION_GATE
                || suppressionProbe != SuppressionProbe.PAUSED
                || !event.isPaused()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread(),
                    "pause transition observation is off the client thread");
            require(minecraft.screen instanceof PauseScreen,
                    "pause transition did not originate from the actual pause screen");
            pauseTransitionObserved = true;
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        clientLoggingInCount = Math.incrementExact(clientLoggingInCount);
        latestLoginConnection = event.getConnection();
        latestLoginPlayListener = event.getPlayer().connection;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut ignored) {
        clientLoggingOutCount = Math.incrementExact(clientLoggingOutCount);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onCommand(CommandEvent event) {
        if (terminal || !COMMAND.equals(event.getParseResults().getReader().getString())) {
            return;
        }
        try {
            var source = event.getParseResults().getContext().getSource();
            var expectedServer = activeServer;
            var expectedPlayerId = playerId;
            var sourceEntity = source.getEntity();
            require(expectedServer != null
                            && expectedPlayerId != null
                            && source.getServer() == expectedServer
                            && sourceEntity instanceof ServerPlayer
                            && sourceEntity.getUUID().equals(expectedPlayerId)
                            && expectedServer.getPlayerList().getPlayer(expectedPlayerId) == sourceEntity,
                    "starter command source is not the exact normal integrated player");
            var player = (ServerPlayer) sourceEntity;
            if (source.source != player) {
                int next = spoofedCommandEventCount + 1;
                require(next == 1, "more than one spoofed starter command was observed");
                spoofedCommandEventCount = next;
                return;
            }
            int next = commandEventCount + 1;
            require(next <= 4, "more than four starter commands reached the server");
            commandEventCount = next;
            var parsedCommand = event.getParseResults().getContext().getLastChild().getCommand();
            require(parsedCommand != null,
                    "starter command parse did not retain its registered executor");
            event.getParseResults().getContext().getLastChild().withCommand(context -> {
                var result = parsedCommand.run(context);
                recordCommandCompletion(next, result);
                return result;
            });
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent
    static void onServerPreTick(ServerTickEvent.Pre event) {
        if (terminal || event.getServer() != activeServer) {
            return;
        }
        try {
            if (!reloadCleanupCast
                    && castOrdinal > 0
                    && serverProjectile != null
                    && target == null
                    && (castOrdinal >= 2 || clientProjectile != null)) {
                armCollisionTarget(event.getServer());
            }
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerPostTick(ServerTickEvent.Post event) {
        if (terminal || event.getServer() != activeServer) {
            return;
        }
        try {
            startConfigurationWhenAcknowledged(event.getServer());
            observeExactDamage();
            if (preparedBCatalogGeneration > 0L
                    && (phase == Phase.WAIT_FOR_P10_B_RELOAD
                            || phase == Phase.WAIT_FOR_P10_B_COMMAND)) {
                observeBReloadAdmission(event.getServer());
            }
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof P9StarterProjectile projectile)) {
            return;
        }
        try {
            require(castOrdinal >= 1 && castOrdinal <= 4,
                    "a projectile spawned from the suppressed or absent cast input");
            require(!activeAReloadCleanupComplete
                            || !projectile.getUUID().equals(retiredAProjectileId),
                    "reload-invalidated A projectile reappeared after terminal cleanup");
            require(projectile.getType() == P9StarterProjectileRegistration.type(),
                    "spawned projectile type is not the registered P9 type");
            if (event.getLevel().isClientSide()) {
                var existing = clientProjectile;
                require(existing == null || existing == projectile,
                        "more than one client projectile joined one cast");
                clientProjectile = projectile;
            } else {
                var existing = serverProjectile;
                require(existing == null || existing == projectile,
                        "more than one server projectile joined one cast");
                serverProjectile = projectile;
                projectileId = projectile.getUUID();
            }
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof P9StarterProjectile projectile)) {
            return;
        }
        var expectedId = projectileId;
        if (!event.getLevel().isClientSide()
                && expectedId != null
                && expectedId.equals(projectile.getUUID())
                && projectile == serverProjectile) {
            serverLeaveObserved = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onProjectileImpact(ProjectileImpactEvent event) {
        var exactProjectile = serverProjectile;
        var exactTarget = target;
        if (exactProjectile == null
                || exactTarget == null
                || event.getProjectile() != exactProjectile
                || event.getProjectile().level().isClientSide()
                || event.isCanceled()
                || !(event.getRayTraceResult() instanceof EntityHitResult entityHit)
                || entityHit.getEntity() != exactTarget) {
            return;
        }
        serverImpactObserved = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlaySound(PlaySoundEvent event) {
        if (!terminal
                && (phase == Phase.WAIT_FOR_FIRST_CAST || phase == Phase.WAIT_FOR_P10_B_CAST)
                && event.getSound() != null
                && event.getOriginalSound().getLocation().equals(SOUND_EVENT)) {
            p8SoundEvents++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!terminal && reloadCleanupCast
                && event.getSource().getDirectEntity() == serverProjectile) {
            activeAReloadDamageCalls++;
        }
        if (terminal || event.getEntity() != target) return;
        damageCalls++;
        exactAttributionObserved = event.getSource().getDirectEntity() == serverProjectile
                && event.getSource().getEntity() == activeServer.getPlayerList().getPlayer(playerId)
                && Float.floatToIntBits(event.getAmount()) == Float.floatToIntBits(expectedDamage);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onAfterParticles(RenderLevelStageEvent event) {
        if (!terminal
                && phase == Phase.WAIT_FOR_FIRST_CAST
                && event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES
                && particleCount(Minecraft.getInstance()) > firstParticleBaseline) {
            p8AfterParticlesRenderObserved = true;
        }
    }

    private static void bootstrap(Minecraft minecraft) {
        require(minecraft.isSameThread(), "harness bootstrap is not on the client thread");
        captureFrozenProductionJar();
        var forwarding = P8ClientPayloadDispatchFactory.production();
        presentationState = readField(
                forwarding, "delegate", P8ClientPresentationState.class);
        presentationExecution = readField(
                presentationState, "execution", P8ClientPresentationExecution.class);
        require(readField(
                        presentationExecution,
                        "backend",
                        P8ClientPresentationBackend.class)
                        == MinecraftP8ClientPresentationBackend.INSTANCE,
                "installed P8 backend is not the production Minecraft adapter");
        var matches = Arrays.stream(minecraft.options.keyMappings)
                .filter(mapping -> mapping.getName().equals(KEY_DESCRIPTION))
                .toList();
        require(matches.size() == 1, "production cast key mapping registration is not unique");
        var mapping = matches.getFirst();
        require(mapping.getKey().equals(CAST_KEY)
                        && mapping.getCategory().equals(KEY_CATEGORY)
                        && mapping.getKeyConflictContext() == KeyConflictContext.IN_GAME
                        && mapping.getKeyModifier() == KeyModifier.NONE
                        && mapping.isDefault(),
                "production cast key mapping contract is not exact");
        require(BuiltInRegistries.ENTITY_TYPE
                        .getKey(P9StarterProjectileRegistration.type())
                        .equals(PROJECTILE_TYPE_ID),
                "production projectile registration is not exact");
        marker("01 PRODUCTION_R_KEY_MAPPING_REGISTERED key=" + CAST_KEY.getName()
                + " productionJarSha256=" + frozenProductionJarHash);

        require(minecraft.level == null
                        && minecraft.player == null
                        && minecraft.getConnection() == null
                        && minecraft.getSingleplayerServer() == null,
                "pre-world suppression probe did not begin without a client context");
        KeyMapping.click(CAST_KEY);
        preWorldSuppressedClickQueued = true;
        transition(Phase.WAIT_FOR_PREWORLD_SUPPRESSION);
    }

    private static void waitForPreworldSuppression(Minecraft minecraft) {
        require(preWorldSuppressedClickQueued,
                "pre-world suppressed click was not queued");
        require(minecraft.level == null
                        && minecraft.player == null
                        && minecraft.getConnection() == null
                        && minecraft.getSingleplayerServer() == null
                        && serverProjectile == null
                        && castOrdinal == 0,
                "pre-world click escaped its absent world/player/connection gate");

        transition(Phase.WAIT_FOR_FIRST_WORLD);
        marker("02 FRESH_FIXED_SEED_WORLD_REQUESTED seed=" + WORLD_SEED);
        var settings = new LevelSettings(
                "P9-S5 Client Runtime Harness",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                false,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(
                WORLD_DIRECTORY,
                settings,
                new WorldOptions(WORLD_SEED, false, false),
                access -> access.registryOrThrow(
                                net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                minecraft.screen);
    }

    private static void waitForFirstWorld(Minecraft minecraft) {
        if (phaseTicks == 100L) {
            Gramarye.LOGGER.info("P9-S5 client readiness probe{}", presentationReadiness());
        }
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        var connection = minecraft.getConnection();
        if (server == null
                || minecraft.level == null
                || player == null
                || connection == null
                || connection.getCommands().getRoot().getChild("gramarye") == null) {
            return;
        }
        require(server.isRunning() && !server.isStopped(),
                "fresh integrated server is not running");
        if (serverPreparationGeneration == 0) {
            firstServer = server;
            activeServer = server;
            playerId = player.getUUID();
            writeOwnedPlayerInventory(playerId);
            serverPreparationGeneration = -1;
            server.execute(() -> prepareServerPlayer(server, player.getUUID(), 1));
            return;
        }
        if (serverPreparationGeneration != 1) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        var state = presentationState;
        if (state == null
                || !state.connected()
                || !state.worldReady()
                || !state.resourceReady()) {
            return;
        }
        if (!presentationReady()) {
            return;
        }
        long expectedCatalogGeneration = preparedACatalogGeneration;
        require(expectedCatalogGeneration > 0L,
                "supported A reload did not publish its actual server catalog generation");
        if (state.installedCatalogGeneration() < expectedCatalogGeneration) {
            return;
        }
        require(state.installedCatalogGeneration() == expectedCatalogGeneration,
                "client catalog advanced beyond the completed supported A reload");
        if (!p8WrongThreadRejected) {
            assertRootHandoffWrongThread(minecraft, server);
        }
        require(spoofedProvisionRejected && spoofedCommandEventCount == 1,
                "console/entity substitution was not directly rejected");
        var transport = connection.getConnection();
        var stateGeneration = state.connectionGeneration();
        var catalog = state.installedCatalogSnapshot();
        require(transport.isConnected()
                        && transport.getPacketListener() == connection
                        && stateGeneration == 1L
                        && state.isCurrentPublishedPlayGeneration(stateGeneration)
                        && catalog != null
                        && catalog.catalogGeneration() == expectedCatalogGeneration
                        && state.installedCatalogGeneration() == catalog.catalogGeneration()
                        && boundCatalogConnectionGeneration() == stateGeneration
                        && boundCatalogSnapshot() == catalog,
                "first actual-client PLAY epoch did not publish exact generation 1");
        require(clientLoggingInCount > 0
                        && latestLoginConnection == transport
                        && latestLoginPlayListener == connection,
                "first PLAY login witness does not identify the live connection/listener");
        firstClientTransport = transport;
        firstClientPlayListener = connection;
        firstCatalogSnapshot = catalog;
        firstP8ConnectionGeneration = stateGeneration;
        firstCatalogGeneration = catalog.catalogGeneration();
        firstLoginCount = clientLoggingInCount;
        firstLogoutCount = clientLoggingOutCount;
        marker("03 WORLD_AND_NORMAL_PLAYER_READY player=" + playerId
                + " permission=0 spoofedSourceRejected=true connectionGeneration="
                + stateGeneration + " catalogGeneration=" + firstCatalogGeneration);
        marker("04 SAME_CONNECTION_RECONFIGURATION_REQUESTED");
        transition(Phase.WAIT_FOR_PLAY_REENTRY);
        server.execute(() -> requestSameConnectionPlayReentry(server, player.getUUID()));
    }

    private static void requestSameConnectionPlayReentry(
            MinecraftServer server, UUID expectedPlayerId) {
        try {
            require(server.isSameThread(), "PLAY re-entry request is off the server thread");
            var actor = server.getPlayerList().getPlayer(expectedPlayerId);
            requireCurrentActor(server, actor);
            require(actor == firstServerPlayer,
                    "PLAY re-entry request did not use the original ServerPlayer");
            actor.connection.switchToConfig();
            serverReconfigurationStarted = true;
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    private static void startConfigurationWhenAcknowledged(MinecraftServer server) {
        if (phase != Phase.WAIT_FOR_PLAY_REENTRY
                || !serverReconfigurationStarted
                || serverConfigurationRunStarted) {
            return;
        }
        var expectedPlayerId = playerId;
        require(expectedPlayerId != null,
                "PLAY re-entry lost the normal-player identity");
        for (var connection : server.getConnection().getConnections()) {
            if (connection.getPacketListener()
                    instanceof ServerConfigurationPacketListenerImpl configuration
                    && configuration.getOwner().getId().equals(expectedPlayerId)) {
                require(connection.isConnected(),
                        "PLAY re-entry transport closed before configuration began");
                serverConfigurationRunStarted = true;
                configuration.startConfiguration();
                return;
            }
        }
    }

    private static void waitForPlayReentry(Minecraft minecraft) {
        if (!serverReconfigurationStarted) {
            return;
        }
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        var connection = minecraft.getConnection();
        if (server == null
                || minecraft.level == null
                || player == null
                || connection == null
                || connection == firstClientPlayListener
                || connection.getCommands().getRoot().getChild("gramarye") == null
                || !presentationReady()) {
            return;
        }
        var transport = connection.getConnection();
        var state = presentationState;
        var catalog = state.installedCatalogSnapshot();
        var stateGeneration = state.connectionGeneration();
        require(server == firstServer && server == activeServer,
                "PLAY re-entry replaced the integrated server");
        require(player.getUUID().equals(playerId),
                "PLAY re-entry replaced the normal-player identity");
        require(transport == firstClientTransport
                        && transport.isConnected()
                        && transport.getPacketListener() == connection,
                "PLAY re-entry did not retain the exact physical Connection");
        require(stateGeneration > firstP8ConnectionGeneration
                        && state.isCurrentPublishedPlayGeneration(stateGeneration),
                "PLAY re-entry did not publish a new current P8 connection generation");
        require(catalog != null
                        && catalog.catalogGeneration() == firstCatalogGeneration
                        && sameCatalogWireValues(catalog, firstCatalogSnapshot)
                        && state.installedCatalogGeneration() == firstCatalogGeneration
                        && state.catalogEvaluationResourceGeneration()
                                == state.resourceGeneration(),
                "same-generation server catalog was not accepted in the new PLAY domain: "
                        + "snapshot=" + (catalog == null
                                ? "null"
                                : catalog.catalogGeneration())
                        + ",installed=" + state.installedCatalogGeneration()
                        + ",expected=" + firstCatalogGeneration
                        + ",wireValuesEqual=" + (catalog != null
                                && sameCatalogWireValues(catalog, firstCatalogSnapshot))
                        + ",catalogResource="
                        + state.catalogEvaluationResourceGeneration()
                        + ",resource=" + state.resourceGeneration());
        require(boundCatalogConnectionGeneration() == stateGeneration
                        && boundCatalogSnapshot() == catalog,
                "P8 execution was not rebound to the new PLAY-domain catalog");
        require(clientLoggingInCount == Math.addExact(firstLoginCount, 1)
                        && clientLoggingOutCount == firstLogoutCount
                        && latestLoginConnection == transport
                        && latestLoginPlayListener == connection,
                "PLAY re-entry login/logout witness counts or identities are not exact");
        require(commandEventCount == 0 && commandCompletionCount == 0,
                "PLAY re-entry manufactured a provisioning command");
        if (!reentryServerValidationScheduled) {
            reentryServerValidationScheduled = true;
            server.execute(() -> validateReenteredServerPlayer(server, player.getUUID()));
            return;
        }
        if (!reentryServerValidationComplete) {
            return;
        }
        marker("05 SAME_CONNECTION_PLAY_REENTRY_READY oldConnectionGeneration="
                + firstP8ConnectionGeneration + " newConnectionGeneration="
                + stateGeneration + " catalogGeneration=" + firstCatalogGeneration
                + " sameGenerationCatalogAccepted=true logoutDelta=0");
        connection.sendCommand(COMMAND);
        transition(Phase.WAIT_FOR_FIRST_COMMAND);
    }

    private static void validateReenteredServerPlayer(
            MinecraftServer server, UUID expectedPlayerId) {
        try {
            require(server.isSameThread(), "PLAY re-entry validation is off the server thread");
            var actor = server.getPlayerList().getPlayer(expectedPlayerId);
            requireCurrentActor(server, actor);
            require(actor != firstServerPlayer,
                    "PLAY re-entry reused the removed ServerPlayer object");
            var commandSource = actor.createCommandSourceStack();
            require(!server.getWorldData().isAllowCommands()
                            && !server.getPlayerList().isOp(actor.getGameProfile())
                            && server.getProfilePermissions(actor.getGameProfile()) == 0
                            && commandSource.hasPermission(0)
                            && !commandSource.hasPermission(1),
                    "re-entered normal player unexpectedly has operator authority");
            reentryServerValidationComplete = true;
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    private static void waitForFirstCommand(Minecraft minecraft) {
        if (commandCompletionCount < 1) {
            return;
        }
        require(commandEventCount == 1, "first command completion count is not exact");
        marker("06 FIRST_NORMAL_PLAYER_COMMAND_COMPLETED");
        var connection = minecraft.getConnection();
        require(connection != null, "client play connection disappeared before idempotence");
        connection.sendCommand(COMMAND);
        transition(Phase.WAIT_FOR_SECOND_COMMAND);
    }

    private static void waitForSecondCommand(Minecraft minecraft) {
        if (commandCompletionCount < 2) {
            return;
        }
        require(commandEventCount == 2, "second command completion count is not exact");
        marker("07 SECOND_IDEMPOTENT_COMMAND_COMPLETED");
        marker("08 INPUT_SUPPRESSION_MATRIX_STARTED");
        var server = activeServer;
        require(server != null, "integrated server disappeared before wrong-thread probe");
        transition(Phase.WAIT_FOR_WRONG_THREAD_INPUT_REJECTION);
        server.execute(P9S5ClientRuntimeHarness::runWrongThreadInputProbe);
    }

    private static void runWrongThreadInputProbe() {
        try {
            require(activeServer != null && activeServer.isSameThread(),
                    "wrong-thread input probe is not on the integrated server thread");
            try {
                NeoForge.EVENT_BUS.post(new ClientPauseChangeEvent.Post(true));
                throw new IllegalStateException(
                        "wrong-thread input event returned without an invariant failure");
            } catch (RuntimeException | Error expected) {
                require(hasCause(
                                expected,
                                "com.yo1no.gramarye.magic.network."
                                        + "P7SemanticInvariantException",
                                "P9 cast input mutation is off the client thread"),
                        "wrong-thread input event did not preserve the exact invariant failure");
            }
            wrongThreadInputRejected = true;
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    private static void waitForWrongThreadInputRejection(Minecraft minecraft) {
        if (!wrongThreadInputRejected) {
            return;
        }
        requireNoSuppressedCast("wrong-thread input");
        suppressionProbeIndex = 0;
        startSuppressionProbe(minecraft);
    }

    private static void startSuppressionProbe(Minecraft minecraft) {
        var probes = SuppressionProbe.values();
        require(suppressionProbeIndex >= 0 && suppressionProbeIndex < probes.length,
                "input suppression probe index escaped its closed matrix");
        suppressionProbe = probes[suppressionProbeIndex];
        focusRestoredByRenderCallback = false;
        pauseTransitionObserved = false;
        switch (suppressionProbe) {
            case CHAT -> minecraft.setScreen(new ChatScreen(""));
            case INVENTORY -> minecraft.setScreen(new InventoryScreen(minecraft.player));
            case CONTROLS -> minecraft.setScreen(new ControlsScreen(null, minecraft.options));
            case NON_PAUSING -> minecraft.setScreen(new SuppressionScreen());
            case WINDOW_INACTIVE, INTERTICK_FOCUS -> minecraft.setWindowActive(false);
            case PAUSED -> minecraft.setScreen(new PauseScreen(true));
            case INTERTICK_SCREEN -> {
                minecraft.setScreen(new SuppressionScreen());
                KeyMapping.click(CAST_KEY);
                minecraft.setScreen(null);
                transition(Phase.WAIT_FOR_SUPPRESSION_GATE);
                return;
            }
        }
        KeyMapping.click(CAST_KEY);
        transition(Phase.WAIT_FOR_SUPPRESSION_GATE);
    }

    private static void waitForSuppressionGate(Minecraft minecraft) {
        requireNoSuppressedCast("suppression gate " + suppressionProbe);
        switch (suppressionProbe) {
            case CHAT -> {
                require(minecraft.screen instanceof ChatScreen,
                        "chat screen closed before the product input callback");
                minecraft.setScreen(null);
            }
            case INVENTORY -> {
                require(minecraft.screen instanceof CreativeModeInventoryScreen,
                        "creative inventory screen closed before the product input callback");
                minecraft.setScreen(null);
            }
            case CONTROLS -> {
                require(minecraft.screen instanceof ControlsScreen,
                        "Controls screen closed before the product input callback");
                minecraft.setScreen(null);
            }
            case NON_PAUSING -> {
                require(minecraft.screen instanceof SuppressionScreen
                                && !minecraft.isPaused(),
                        "custom non-pausing screen did not survive the product input callback");
                minecraft.setScreen(null);
            }
            case WINDOW_INACTIVE -> {
                require(!minecraft.isWindowActive(),
                        "inactive-window probe restored before the product input callback");
                minecraft.setWindowActive(true);
            }
            case PAUSED -> {
                if (!pauseTransitionObserved) {
                    return;
                }
                require(minecraft.screen instanceof PauseScreen && minecraft.isPaused(),
                        "actual pause screen did not survive the product input callback");
                minecraft.setScreen(null);
                KeyMapping.click(CAST_KEY);
            }
            case INTERTICK_SCREEN -> require(minecraft.screen == null,
                    "inter-tick screen transition retained its temporary screen");
            case INTERTICK_FOCUS -> {
                if (!focusRestoredByRenderCallback) {
                    return;
                }
                require(minecraft.isWindowActive(),
                        "inter-tick focus callback did not restore the window");
            }
        }
        transition(Phase.WAIT_FOR_SUPPRESSION_FLUSH);
    }

    private static void waitForSuppressionFlush(Minecraft minecraft) {
        requireNoSuppressedCast("suppression flush " + suppressionProbe);
        if (minecraft.screen != null
                || !minecraft.isWindowActive()
                || minecraft.isPaused()) {
            return;
        }
        suppressionProbeIndex++;
        if (suppressionProbeIndex < SuppressionProbe.values().length) {
            startSuppressionProbe(minecraft);
            return;
        }
        marker("09 INPUT_SUPPRESSION_MATRIX_PASSED chat=true inventory=true"
                + " controls=true nonPausing=true inactiveWindow=true pause=true"
                + " interTickScreen=true interTickFocus=true wrongThread=true"
                + " preWorld=true");
        transition(Phase.WAIT_FOR_SUPPRESSED_CAST_QUIET);
    }

    private static void requireNoSuppressedCast(String owner) {
        require(serverProjectile == null
                        && clientProjectile == null
                        && castOrdinal == 0
                        && serverImpactObserved == false
                        && p8SoundEvents == 0,
                owner + " admitted or delayed product work");
    }

    private static void waitForSuppressedCastQuiet(Minecraft minecraft) {
        require(serverProjectile == null
                        && clientProjectile == null
                        && serverImpactObserved == false
                        && p8SoundEvents == 0,
                "suppressed input produced delayed product work");
        if (phaseTicks < SUPPRESSED_CAST_QUIET_TICKS) {
            return;
        }
        if (!clientCastGatesOpen(minecraft)) {
            return;
        }
        firstParticleBaseline = particleCount(minecraft);
        firstParticleMaximum = firstParticleBaseline;
        castOrdinal = 1;
        queueCastClick(minecraft);
        marker("10 LATER_NEW_R_CLICK_QUEUED");
        transition(Phase.WAIT_FOR_FIRST_CAST);
    }

    private static void waitForFirstCast(Minecraft minecraft) {
        if (!firstServerMarkerWritten && serverProjectile != null) {
            marker("11 FIRST_SERVER_PROJECTILE_SPAWNED uuid=" + projectileId);
            firstServerMarkerWritten = true;
        }
        if (firstServerMarkerWritten && !firstClientMarkerWritten && clientProjectile != null) {
            require(projectileId != null
                            && projectileId.equals(clientProjectile.getUUID())
                            && clientProjectile.getType()
                                    == P9StarterProjectileRegistration.type(),
                    "client did not track the exact first server projectile");
            marker("12 FIRST_CLIENT_PROJECTILE_TRACKED entityId="
                    + clientProjectile.getId());
            firstClientMarkerWritten = true;
        }
        if (firstClientMarkerWritten && !firstHitMarkerWritten && serverImpactObserved) {
            marker("13 FIRST_SERVER_AUTHORITATIVE_HIT_OBSERVED target="
                    + target.getUUID());
            firstHitMarkerWritten = true;
        }
        if (firstHitMarkerWritten && !firstDamageMarkerWritten && exactFourDamageObserved) {
            marker("14 FIRST_EXACT_FOUR_DAMAGE_APPLIED beforeHealthBits="
                    + targetInitialHealthBits
                    + " afterHealthBits="
                    + Float.floatToIntBits(target.getHealth()));
            firstDamageMarkerWritten = true;
        }
        if (firstDamageMarkerWritten && !firstSoundMarkerWritten && p8SoundEvents > 0) {
            marker("15 FIRST_P8_SOUND_EVENT_OBSERVED count=" + p8SoundEvents);
            firstSoundMarkerWritten = true;
        }
        if (firstSoundMarkerWritten
                && !firstParticleMarkerWritten
                && firstParticleMaximum > firstParticleBaseline) {
            marker("16 FIRST_P8_PARTICLE_ENGINE_OBSERVED baseline="
                    + firstParticleBaseline + " maximum=" + firstParticleMaximum);
            firstParticleMarkerWritten = true;
        }
        if (firstParticleMarkerWritten
                && !firstRenderMarkerWritten
                && p8AfterParticlesRenderObserved) {
            marker("17 FIRST_P8_AFTER_PARTICLES_RENDER_OBSERVED");
            firstRenderMarkerWritten = true;
            var server = activeServer;
            var exactTarget = target;
            require(server != null && exactTarget != null,
                    "first cast target disappeared before persistence boundary");
            server.execute(() -> {
                try {
                    if (!exactTarget.isRemoved()) {
                        exactTarget.discard();
                    }
                    server.saveEverything(true, false, false);
                    firstTargetCleanupComplete = true;
                } catch (RuntimeException | Error failure) {
                    asynchronousFailure = failure;
                }
            });
            transition(Phase.WAIT_FOR_FIRST_TARGET_CLEANUP);
        }
    }

    private static void waitForFirstTargetCleanup(Minecraft minecraft) {
        if (!firstTargetCleanupComplete) {
            return;
        }
        var server = activeServer;
        require(server != null && server == minecraft.getSingleplayerServer(),
                "first integrated server identity changed before disconnect");
        resetCastObservation();
        require(clientCastGatesOpen(minecraft),
                "old-session suppression probe did not begin with open gates");
        KeyMapping.click(CAST_KEY);
        oldSessionSuppressedClickQueued = true;
        server.halt(false);
        minecraft.disconnect();
        require(minecraft.level == null
                        && minecraft.player == null
                        && minecraft.getSingleplayerServer() == null,
                "same-world disconnect did not clear client and server identities");
        marker("18 SAME_WORLD_DISCONNECT_COMPLETED");
        transition(Phase.WAIT_FOR_REOPEN_REQUEST);
    }

    private static void requestReopen(Minecraft minecraft) {
        require(minecraft.level == null && minecraft.getSingleplayerServer() == null,
                "world was not absent before reopen");
        marker("19 SAME_WORLD_REOPEN_REQUESTED directory=" + WORLD_DIRECTORY);
        transition(Phase.WAIT_FOR_REOPENED_WORLD);
        minecraft.createWorldOpenFlows().openWorld(
                WORLD_DIRECTORY,
                () -> asynchronousFailure = new IllegalStateException(
                        "same-world reopen returned to its failure callback"));
    }

    private static void waitForReopenedWorld(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        var connection = minecraft.getConnection();
        if (server == null
                || minecraft.level == null
                || player == null
                || connection == null
                || connection.getCommands().getRoot().getChild("gramarye") == null) {
            return;
        }
        require(server != firstServer, "reopen reused the stopped integrated server object");
        require(player.getUUID().equals(playerId),
                "same owned world reopened with a different normal player");
        if (serverPreparationGeneration == 1) {
            activeServer = server;
            serverPreparationGeneration = -2;
            server.execute(() -> prepareServerPlayer(server, player.getUUID(), 2));
            return;
        }
        if (serverPreparationGeneration != 2) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        require(commandEventCount == 2 && commandCompletionCount == 2,
                "reopen manufactured an additional provisioning command");
        require(spoofedProvisionRejected && spoofedCommandEventCount == 1,
                "reopen changed spoofed-source rejection evidence");
        marker("20 SAME_NORMAL_PLAYER_REOPENED_WITHOUT_REPROVISION player=" + playerId);
        transition(Phase.WAIT_FOR_REOPEN_FLUSH);
    }

    private static void waitForReopenFlush(Minecraft minecraft) {
        require(oldSessionSuppressedClickQueued
                        && commandEventCount == 2
                        && serverProjectile == null,
                "reopen retained old-session input or changed the command count");
        if (!clientCastGatesOpen(minecraft)) {
            return;
        }
        castOrdinal = 2;
        queueCastClick(minecraft);
        marker("21 NEW_SESSION_R_CLICK_QUEUED");
        transition(Phase.WAIT_FOR_SECOND_CAST);
    }

    private static void waitForSecondCast(Minecraft minecraft) {
        if (!secondServerMarkerWritten && serverProjectile != null) {
            marker("22 SECOND_SERVER_PROJECTILE_SPAWNED uuid=" + projectileId);
            secondServerMarkerWritten = true;
        }
        if (secondServerMarkerWritten && !secondHitMarkerWritten && serverImpactObserved) {
            marker("23 SECOND_SERVER_AUTHORITATIVE_HIT_OBSERVED target="
                    + target.getUUID());
            secondHitMarkerWritten = true;
        }
        if (secondHitMarkerWritten && !secondDamageMarkerWritten && exactFourDamageObserved) {
            marker("24 SECOND_EXACT_FOUR_DAMAGE_APPLIED beforeHealthBits="
                    + targetInitialHealthBits
                    + " afterHealthBits="
                    + Float.floatToIntBits(target.getHealth()));
            secondDamageMarkerWritten = true;
            require(commandEventCount == 2,
                    "second cast depended on an unapproved reprovisioning command");
            marker("25 NEW_SESSION_SEQUENCE_RESET_ACCEPTED projectile=" + projectileId
                    + " oldPendingDropped=true samePlayer=true");
            marker("26 P9_REGRESSION_COMPLETE");
            require(recoveryATerminal, "A persisted login recovery did not reach empty pending projection");
            marker("27 A_RECOVERY_TERMINAL_SAME_UUID reference=" + revisionA);
            p10AsyncComplete = false;
            transition(Phase.WAIT_FOR_P10_A_RELOAD_PREPARATION);
            activeServer.execute(() -> {
                try {
                    discardTarget();
                    resetCastObservation();
                    assertPlayerReference(activeServer, revisionA);
                    assertOldA(activeServer);
                    requireEmptyPendingRecovery(activeServer);
                    p10AsyncComplete = true;
                } catch (RuntimeException | Error failure) { asynchronousFailure = failure; }
            });
        }
    }

    private static void waitForActiveAReloadPreparation(Minecraft minecraft) {
        if (!p10AsyncComplete || !clientCastGatesOpen(minecraft) || !presentationReady()) return;
        p10AsyncComplete = false;
        expectedDamage = 4.0F;
        activeAReloadDamageCalls = 0;
        reloadCleanupCast = true;
        castOrdinal = 3;
        queueCastClick(minecraft);
        transition(Phase.WAIT_FOR_P10_A_RELOAD_CAST);
    }

    private static void waitForActiveAReloadCast(Minecraft minecraft) {
        if (serverProjectile == null || clientProjectile == null) return;
        require(projectileId.equals(clientProjectile.getUUID()),
                "reload control did not track its exact normal-R A projectile");
        var server = activeServer;
        var projectile = serverProjectile;
        transition(Phase.WAIT_FOR_P10_B_RELOAD);
        server.execute(() -> {
            try {
                require(server.isSameThread() && projectile == serverProjectile
                                && projectile.level() == server.overworld()
                                && projectile.isAddedToLevel() && !projectile.isRemoved()
                                && server.overworld().getEntity(projectile.getId()) == projectile
                                && projectile.hasAuthenticatedCasterIdentity(
                                        server.getPlayerList().getPlayer(playerId))
                                && target == null && activeAReloadDamageCalls == 0,
                        "reload must begin with the actual live no-target A projectile");
                // Read only existing product state; never mutate or claim this permit.
                var permit = readField(projectile, "continuationPermit",
                        RuntimeProjectileContinuationPermit.class);
                require(permit.mode == RuntimeProjectileContinuationPermit.Mode.REAL
                                && permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                                && permit.exactReference.equals(revisionA)
                                && permit.plannedProjectileId.equals(projectile.getUUID())
                                && observedRuntime.ownsOpenedContinuation(permit, projectile.getUUID()),
                        "normal R did not leave an indexed OPEN continuation pinned to exact A");
                requireEmptyPendingRecovery(server);
                var actor = server.getPlayerList().getPlayer(playerId);
                var draftBefore = attachmentValue(observedAttachments.findDraft(
                        actor, revisionA.skillId()));
                retiredAProjectileId = projectile.getUUID();
                long catalogBeforeB = observedPresentationOwner().catalogGenerationForTesting();
                require(catalogBeforeB > 0L, "B reload requires an active server catalog");
                reloadOwnedTemplate(server, 5_000L, () -> {
                    var diagnostic = observedRuntime.p9TerminalDiagnosticForTesting(
                            server, permit.skillInstanceId);
                    require(permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                                    && !observedRuntime.ownsOpenedContinuation(
                                            permit, projectile.getUUID())
                                    && diagnostic != null
                                    && diagnostic.exactReference().equals(revisionA)
                                    && diagnostic.terminalReason() == ProjectileClosureReason.RELOAD_INVALIDATED
                                    && diagnostic.cleanupDisposition() == P9RuntimeCleanupDisposition.RELEASED
                                    && projectile.isRemoved() && serverLeaveObserved
                                    && server.overworld().getEntity(projectile.getId()) != projectile
                                    && target == null && activeAReloadDamageCalls == 0,
                            "B reload must release exact active A as RELOAD_INVALIDATED without damage");
                    assertPlayerReference(server, revisionA);
                    assertOldA(server);
                    requireEmptyPendingRecovery(server);
                    require(draftBefore.equals(attachmentValue(observedAttachments.findDraft(
                                    actor, revisionA.skillId()))),
                            "B reload mutated the normal player's Draft");
                    requireValidation(server, "READY_CURRENT", "ACCEPTED");
                    long catalogAfterB = observedPresentationOwner().catalogGenerationForTesting();
                    require(catalogAfterB == Math.addExact(catalogBeforeB, 1L),
                            "B reload did not activate the exact next server catalog generation");
                    preparedBCatalogGeneration = catalogAfterB;
                    activeAReloadCleanupComplete = true;
                    p10AsyncComplete = true;
                });
            } catch (RuntimeException | Error failure) { asynchronousFailure = failure; }
        });
    }

    private static void waitForBReload(Minecraft minecraft) {
        if (!p10AsyncComplete || !clientCastGatesOpen(minecraft) || !presentationReady()) return;
        if (clientProjectile == null || !clientProjectile.isRemoved()
                || minecraft.level.getEntity(clientProjectile.getId()) == clientProjectile) return;
        require(activeAReloadCleanupComplete && activeAReloadDamageCalls == 0,
                "active A cleanup was not complete before the B mutation");
        resetCastObservation();
        castOrdinal = 0;
        reloadCleanupCast = false;
        marker("28 B_CURRENT_RELOAD_ZERO_PLAYER_MUTATION referenceA=" + revisionA
                + " activeACleanup=RELOAD_INVALIDATED disposition=RELEASED noDamage=true noGhost=true");
        minecraft.getConnection().sendCommand(COMMAND);
        transition(Phase.WAIT_FOR_P10_B_COMMAND);
    }

    private static void waitForBCommand(Minecraft minecraft) {
        if (commandCompletionCount != 3 || !clientCastGatesOpen(minecraft)
                || !presentationReady() || !bReloadAdmissionOpen) return;
        var state = presentationState;
        long expectedCatalogGeneration = preparedBCatalogGeneration;
        require(expectedCatalogGeneration > 0L,
                "B cast has no completed reload catalog generation");
        if (state.installedCatalogGeneration() < expectedCatalogGeneration) return;
        var catalog = state.installedCatalogSnapshot();
        require(catalog != null && catalog.catalogGeneration() == expectedCatalogGeneration
                        && state.installedCatalogGeneration() == expectedCatalogGeneration
                        && boundCatalogConnectionGeneration() == state.connectionGeneration()
                        && boundCatalogSnapshot() == catalog,
                "B cast is not bound to the exact completed reload catalog");
        marker("29 B_SUCCESSOR_NORMAL_COMMAND referenceB=" + revisionB);
        expectedDamage = 5.0F;
        p8SoundEvents = 0;
        castOrdinal = 4;
        queueCastClick(minecraft);
        transition(Phase.WAIT_FOR_P10_B_CAST);
    }

    /** Observes the real P7 owner after its normal-priority PostTick; never opens its gate. */
    private static void observeBReloadAdmission(MinecraftServer server) {
        require(server == activeServer && server.isSameThread(),
                "B admission observation must use the active server thread");
        try {
            var events = Class.forName(
                    "com.yo1no.gramarye.magic.network.P7ServerLifecycleEvents");
            var field = events.getDeclaredField("LIFECYCLE");
            field.setAccessible(true);
            var lifecycle = field.get(null);
            require(lifecycle != null && lifecycle.getClass().getName().equals(
                            "com.yo1no.gramarye.magic.network.P7ServerLifecycleCoordinator"),
                    "B admission observation did not reach the exact production lifecycle owner");
            var gate = readField(lifecycle, "reloadGate", Object.class);
            require(gate.getClass().getName().equals(
                            "com.yo1no.gramarye.magic.network.P7ReloadAdmissionGate"),
                    "B admission observation did not reach the exact production reload gate");
            var state = readField(gate, "state", Enum.class);
            require(state.name().equals("OPEN") || state.name().equals("RECONCILING"),
                    "P7 reload gate state vocabulary drifted");
            var requested = readField(gate, "closeRequested",
                    java.util.concurrent.atomic.AtomicBoolean.class).get();
            lastP7ReloadGateState = state.name();
            lastP7ReloadCloseRequested = requested;
            lastP7ReloadGateTick = server.getTickCount();
            bReloadAdmissionOpen = state.name().equals("OPEN") && !requested;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("fixed P7 reload readiness observation failed", failure);
        }
    }

    private static void waitForBCast(Minecraft minecraft) {
        if (!exactFourDamageObserved || p8SoundEvents == 0) return;
        require(damageCalls == 1 && exactAttributionObserved,
                "B did not produce exactly one attributed world damage call");
        marker("30 B_EXACT_FIVE_DAMAGE_APPLIED beforeHealthBits=" + targetInitialHealthBits
                + " afterHealthBits=" + Float.floatToIntBits(target.getHealth())
                + " p8SoundEvents=" + p8SoundEvents);
        p10AsyncComplete = false;
        transition(Phase.WAIT_FOR_P10_B_SAVE);
        activeServer.execute(() -> {
            try {
                assertOldA(activeServer);
                oldAProvedBeforeBSave = true;
                discardTarget();
                activeServer.saveEverything(true, false, false);
                p10AsyncComplete = true;
            } catch (RuntimeException | Error failure) { asynchronousFailure = failure; }
        });
    }

    private static void waitForBSave(Minecraft minecraft) {
        if (!p10AsyncComplete) return;
        require(oldAProvedBeforeBSave,
                "B disconnect requires the preceding exact old-A byte, owner and 4000 resolution proof");
        resetCastObservation();
        activeServer.halt(false);
        minecraft.disconnect();
        marker("31 B_SAVED_LOGOUT_SAME_WORLD player=" + playerId);
        transition(Phase.WAIT_FOR_P10_B_REOPEN_REQUEST);
    }

    private static void requestBReopen(Minecraft minecraft) {
        require(minecraft.level == null && minecraft.getSingleplayerServer() == null,
                "B disconnect did not clear the client world");
        transition(Phase.WAIT_FOR_P10_B_REOPENED);
        minecraft.createWorldOpenFlows().openWorld(WORLD_DIRECTORY,
                () -> asynchronousFailure = new IllegalStateException("B reopen failed"));
    }

    private static void waitForBReopened(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null || minecraft.getConnection() == null
                || minecraft.getConnection().getCommands().getRoot().getChild("gramarye") == null) return;
        require(minecraft.player.getUUID().equals(playerId), "B reopen changed the normal player UUID");
        if (serverPreparationGeneration == 2) {
            require(server != activeServer, "B reopen reused the old server");
            activeServer = server;
            serverPreparationGeneration = -3;
            server.execute(() -> prepareServerPlayer(server, playerId, 3));
            return;
        }
        if (serverPreparationGeneration != 3 || !clientCastGatesOpen(minecraft)) return;
        require(recoveryBTerminal, "B pending recovery has not reached the production empty projection");
        marker("32 B_RECOVERY_TERMINAL_SAME_UUID reference=" + revisionB);
        p10AsyncComplete = false;
        transition(Phase.WAIT_FOR_P10_C_RELOAD);
        server.execute(() -> {
            try {
                assertPlayerReference(server, revisionB);
                reloadOwnedTemplate(server, 4_000L, () -> {
                    assertPlayerReference(server, revisionB);
                    assertHeldRevision(server);
                    requireValidation(server, "READY_CURRENT", "ACCEPTED");
                    p10AsyncComplete = true;
                });
            } catch (RuntimeException | Error failure) { asynchronousFailure = failure; }
        });
    }

    private static void waitForCReload(Minecraft minecraft) {
        if (!p10AsyncComplete || !clientCastGatesOpen(minecraft) || !presentationReady()) return;
        minecraft.getConnection().sendCommand(COMMAND);
        transition(Phase.WAIT_FOR_P10_C_COMMAND);
    }

    private static void waitForCCommand(Minecraft minecraft) {
        if (commandCompletionCount != 4) return;
        marker("33 C_ROLLBACK_NEW_SUCCESSOR referenceC=" + revisionC + " originalA=" + revisionA);
        p10AsyncComplete = false;
        transition(Phase.WAIT_FOR_P10_CONTROLS);
        activeServer.execute(() -> exerciseInvalidOverrides(activeServer, 0));
    }

    private static void waitForP10Controls(Minecraft minecraft) {
        if (!p10AsyncComplete) return;
        marker("34 INVALID_OVERRIDE_MATRIX_RETAINS_VALID_LKG unknown=true migration=true"
                + " malformed=true future=true semantic=true");
        marker("35 OVERRIDE_REMOVAL_PUBLISHES_BUILTIN_CURRENT noPlayerMutation=true");
        require(p8WrongThreadRejected && p8WrongServerRejected && wrongServerProbe == null,
                "P8 root handoff negative controls did not complete with released fixture ownership");
        require(oldAProvedBeforeBSave,
                "same-JAR sequence omitted the required old-A exact resolution before B save/reconnect");
        require(frozenProductionJarHash.equals(productionJarHash(frozenProductionJar)),
                "production JAR changed during the A/B/C sequence");
        marker("36 TERMINAL_PASS productionJarSha256=" + frozenProductionJarHash);
        pass(minecraft);
    }

    private static void captureFrozenProductionJar() {
        var configured = System.getProperty("gramarye.p10.frozenJar");
        require(configured != null && !configured.isBlank(), "frozen production JAR path is missing");
        frozenProductionJar = Path.of(configured).toAbsolutePath().normalize();
        require(Files.isRegularFile(frozenProductionJar) && !Files.isSymbolicLink(frozenProductionJar),
                "frozen production JAR is not a regular owned artifact");
        var roots = Arrays.stream(System.getProperty("fml.modFolders", "")
                        .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(value -> value.startsWith("p9S5ClientRuntimeHarness%%"))
                .map(value -> Path.of(value.substring("p9S5ClientRuntimeHarness%%".length()))
                        .toAbsolutePath().normalize()).toList();
        require(roots.contains(frozenProductionJar), "production JAR is not in the actual loaded mod roots");
        var mainClass = "com/yo1no/gramarye/Gramarye.class";
        var runtimeRoots = Arrays.stream(System.getProperty("java.class.path", "")
                        .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize()).toList();
        for (var root : runtimeRoots) {
            require(!Files.isDirectory(root) || !Files.exists(root.resolve(mainClass)),
                    "JVM runtime classpath contains exploded production classes");
        }
        for (var root : roots) {
            if (!root.equals(frozenProductionJar)) {
                require(Files.isDirectory(root) || !Files.exists(root), "unexpected extra mod artifact");
                require(!Files.exists(root.resolve(mainClass)), "exploded production classes shadow the frozen JAR");
            }
        }
        try (var jar = new JarFile(frozenProductionJar.toFile());
                var loaded = Gramarye.class.getResourceAsStream("/" + mainClass)) {
            require(jar.getEntry("com/yo1no/gramarye/P9S5ClientRuntimeHarness.class") == null,
                    "test controller leaked into the production JAR");
            require(loaded != null, "loaded production class is missing");
            var entry = jar.getJarEntry(mainClass);
            require(entry != null, "production JAR has no root class");
            try (var archived = jar.getInputStream(entry)) {
                require(Arrays.equals(loaded.readAllBytes(), archived.readAllBytes()),
                        "loaded production root differs from the frozen JAR");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("frozen JAR load identity could not be observed", failure);
        }
        frozenProductionJarHash = productionJarHash(frozenProductionJar);
    }

    private static String productionJarHash(Path path) {
        try (var input = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("frozen JAR digest could not be observed", failure);
        }
    }

    /** Fixed read-only product-owner observation, confined to this excluded harness source set. */
    private static void observeExistingProductionPorts() {
        try {
            var field = P7ServerAuthorizationBoundary.class.getDeclaredField("installedRootIngress");
            field.setAccessible(true);
            var ingress = field.get(null);
            require(ingress != null && ingress.getClass() == P7AuthenticatedPlayerCastIngress.class,
                    "P10 observation did not reach the unique production P7 owner");
            var store = readField(ingress, "storeService", SkillDefinitionStoreService.class);
            var attachments = readField(ingress, "attachmentService", PlayerSkillAttachmentService.class);
            var runtime = readField(ingress, "runtimeService", SkillRuntimeService.class);
            require(observedStore == null || observedStore == store, "root Store port identity changed");
            require(observedAttachments == null || observedAttachments == attachments,
                    "root Attachment port identity changed");
            require(observedRuntime == null || observedRuntime == runtime,
                    "root runtime owner identity changed");
            observedStore = store;
            observedAttachments = attachments;
            observedRuntime = runtime;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("fixed read-only P7 owner observation failed", failure);
        }
    }

    private static void observeCommittedReference(MinecraftServer server, int ordinal) {
        var skillId = P9StarterSkillIdentityV0.forPlayer(playerId);
        var reference = storeValue(observedStore.latestReference(server, skillId)).orElseThrow();
        assertPlayerReference(server, reference);
        require(reference.skillId().equals(skillId), "starter changed deterministic lineage");
        if (ordinal == 1) {
            require(reference.revision().value() == 0, "A first revision is not zero");
            revisionA = reference;
            revisionABytes = documentBytes(storeValue(observedStore.find(server, reference)).orElseThrow());
            requireMagnitude(server, reference, 4_000L);
            requirePendingSubmission(server, Optional.empty(), reference);
        } else if (ordinal == 2) {
            require(reference.equals(revisionA), "repeat starter allocated another revision");
        } else if (ordinal == 3) {
            require(reference.revision().value() == revisionA.revision().value() + 1,
                    "B did not create the exact next revision");
            revisionB = reference;
            revisionBBytes = documentBytes(storeValue(observedStore.find(server, reference)).orElseThrow());
            requireMagnitude(server, reference, 5_000L);
            assertOldA(server);
            requirePendingSubmission(server, Optional.of(revisionA), reference);
        } else if (ordinal == 4) {
            require(reference.revision().value() == revisionB.revision().value() + 1
                    && !reference.equals(revisionA), "rollback moved latest backward instead of creating C");
            revisionC = reference;
            revisionCBytes = documentBytes(storeValue(observedStore.find(server, reference)).orElseThrow());
            requireMagnitude(server, reference, 4_000L);
            assertHeldRevision(server);
        }
    }

    private static void assertRootHandoffWrongThread(Minecraft minecraft, MinecraftServer server) {
        require(minecraft.isSameThread() && !server.isSameThread(),
                "P8 wrong-thread control must use the actual client thread and live server");
        var owner = observedPresentationOwner();
        long generation = owner.catalogGenerationForTesting();
        int activeEntries = owner.activeEntryCountForTesting();
        int activeBytes = owner.activeCatalogBodyBytesForTesting();
        int pendingEntries = owner.pendingEntryCountForTesting();
        int pendingBytes = owner.pendingCatalogBodyBytesForTesting();
        boolean rejected = false;
        try {
            owner.activateMatchingCandidateForRoot(server);
        } catch (IllegalStateException expected) {
            rejected = "P8 catalog lifecycle requires the server thread".equals(expected.getMessage());
        }
        require(rejected && generation > 0L
                        && owner.catalogGenerationForTesting() == generation
                        && owner.activeEntryCountForTesting() == activeEntries
                        && owner.activeCatalogBodyBytesForTesting() == activeBytes
                        && owner.pendingEntryCountForTesting() == pendingEntries
                        && owner.pendingCatalogBodyBytesForTesting() == pendingBytes,
                "actual root P8 wrong-thread call must fail before changing catalog or pending state");
        p8WrongThreadRejected = true;
    }

    private static P8ServerPresentationService observedPresentationOwner() {
        var adapter = readField(observedRuntime, "executionPort", RuntimeExecutionPort.class);
        require(adapter.getClass() == P6RuntimeExecutionPortAdapter.class,
                "P8 observation did not reach the exact production execution adapter");
        return readField(adapter, "presentationService", P8ServerPresentationService.class);
    }

    private static synchronized void startWrongServerProbe(MinecraftServer server) {
        if (terminal) return;
        require(server == firstServer && server.isSameThread() && wrongServerProbe == null,
                "wrong-server fixture must start once on the first real server thread");
        var prepared = P8ServerPresentationService.loadCandidateForTesting(
                MagicRegistries.profileTypeRegistry(), Map.of());
        // This bounded test owner is never registered with any event bus.
        var probe = P8ServerPresentationService.create();
        try {
            probe.startForTesting(server, prepared);
            wrongServerProbe = probe;
        } finally {
            if (wrongServerProbe != probe) probe.stopForTesting();
        }
    }

    private static synchronized void assertRootHandoffWrongServer(MinecraftServer server) {
        var probe = wrongServerProbe;
        require(probe != null && server != firstServer && server.isSameThread()
                        && server.isRunning() && !server.isStopped(),
                "wrong-server control must use the genuinely reopened live server on its own thread");
        long generation = probe.catalogGenerationForTesting();
        int activeEntries = probe.activeEntryCountForTesting();
        int activeBytes = probe.activeCatalogBodyBytesForTesting();
        int pendingEntries = probe.pendingEntryCountForTesting();
        int pendingBytes = probe.pendingCatalogBodyBytesForTesting();
        try {
            boolean rejected = false;
            try {
                probe.activateMatchingCandidateForRoot(server);
            } catch (IllegalStateException expected) {
                rejected = "P8 root full-sync requires the active server".equals(expected.getMessage());
            }
            require(rejected && generation == 1L
                            && probe.catalogGenerationForTesting() == generation
                            && probe.activeEntryCountForTesting() == activeEntries
                            && probe.activeCatalogBodyBytesForTesting() == activeBytes
                            && probe.pendingEntryCountForTesting() == pendingEntries
                            && probe.pendingCatalogBodyBytesForTesting() == pendingBytes,
                    "P8 wrong-server call must reject without changing its exact catalog coordinates");
            p8WrongServerRejected = true;
        } finally {
            stopWrongServerProbe();
        }
    }

    private static synchronized void stopWrongServerProbe() {
        var probe = wrongServerProbe;
        if (probe != null) {
            probe.stopForTesting();
            wrongServerProbe = null;
        }
    }

    private static void assertPlayerReference(MinecraftServer server, SkillReference expected) {
        var actor = server.getPlayerList().getPlayer(playerId);
        requireCurrentActor(server, actor);
        require(storeValue(observedStore.latestReference(server, expected.skillId())).equals(Optional.of(expected)),
                "authoritative latest changed unexpectedly");
        require(attachmentValue(observedAttachments.equippedAt(actor, 0)).equals(Optional.of(expected)),
                "slot zero does not contain the exact expected revision");
        require(storeValue(observedStore.ownerOf(server, expected.skillId()))
                .equals(Optional.of(new SkillOwnerId(playerId))), "Store owner changed");
    }

    private static void assertOldA(MinecraftServer server) {
        assertStoredRevision(server, revisionA, revisionABytes, 4_000L);
    }

    private static void assertHeldRevision(MinecraftServer server) {
        // Startup reclaim may discard unrooted A after its required pre-B-save proof.
        // Observe the actual current held revision; never manufacture a retention pin.
        var reference = revisionC != null ? revisionC : revisionB;
        require(reference != null && oldAProvedBeforeBSave,
                "post-B-recovery observation requires the preceding old-A proof and actual held revision");
        assertPlayerReference(server, reference);
        assertStoredRevision(server, reference,
                revisionC != null ? revisionCBytes : revisionBBytes,
                revisionC != null ? 4_000L : 5_000L);
    }

    private static void assertStoredRevision(MinecraftServer server, SkillReference reference,
            String expectedBytes, long expectedMagnitude) {
        var document = storeValue(observedStore.find(server, reference)).orElseThrow();
        require(documentBytes(document).equals(expectedBytes), "immutable exact document bytes changed");
        require(storeValue(observedStore.ownerOf(server, reference.skillId()))
                .equals(Optional.of(new SkillOwnerId(playerId))), "immutable exact owner changed");
        requireMagnitude(server, reference, expectedMagnitude);
    }

    private static void requireMagnitude(MinecraftServer server, SkillReference reference, long magnitude) {
        var document = storeValue(observedStore.find(server, reference)).orElseThrow();
        var projection = new P5RuntimeProjector(ProfileAvailabilityView.unknown()).project(
                reference, document, new ValidationContext(MagicPolicyLimits.DEFAULTS));
        require(projection instanceof P5RuntimeProjector.Projection.Available,
                "exact committed revision failed formal production resolution");
        var definition = ((P5RuntimeProjector.Projection.Available) projection).definition();
        require(P9StarterSkillContent.hasSupportedStarterGameplay(definition)
                && ((P9DamageActionPayloadV0) definition.nodes().get(1).action().payload()).magnitude() == magnitude,
                "exact revision resolved to the wrong damage meaning");
    }

    private static void requirePendingSubmission(
            MinecraftServer server, Optional<SkillReference> expectedBase, SkillReference targetReference) {
        var actor = server.getPlayerList().getPlayer(playerId);
        requireCurrentActor(server, actor);
        require(actor.getUUID().equals(playerId), "pending observation changed the normal player UUID");
        var pending = observedStore.submissionPort().observePendingRecovery(
                server, new SkillOwnerId(actor.getUUID()));
        require(pending instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available ready
                        && ready.chains().size() == 1,
                "real submission must leave exactly one pending chain for this normal player");
        var chain = ((SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available) pending)
                .chains().getFirst();
        require(chain.skillId().equals(targetReference.skillId()) && chain.steps().size() == 1,
                "real submission journal must name only its exact deterministic target route");
        var step = chain.steps().getFirst();
        require(step.expectedPointer().equals(expectedBase)
                        && step.targetPointer().equals(targetReference)
                        && PlayerSkillAttachmentService.isChangedGenerationSuccessor(
                                step.expectedGeneration(), step.targetGeneration()),
                "real submission journal must retain its exact base-to-target transition before logout");
    }

    private static void requireEmptyPendingRecovery(MinecraftServer server) {
        var pending = observedStore.submissionPort().observePendingRecovery(server, new SkillOwnerId(playerId));
        require(pending instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available ready
                && ready.chains().isEmpty(), "login persisted-readback recovery did not reach Available(empty)");
    }

    private static <T> T storeValue(SkillSubsystemResult<T> result) {
        require(result instanceof SkillSubsystemResult.Available<?>, "Store observation unavailable");
        return ((SkillSubsystemResult.Available<T>) result).value();
    }

    private static <T> T attachmentValue(PlayerSkillAttachmentService.Result<T> result) {
        require(result instanceof PlayerSkillAttachmentService.Available<?>, "Attachment observation unavailable");
        return ((PlayerSkillAttachmentService.Available<T>) result).value();
    }

    private static String documentBytes(SkillDocument document) {
        var encoded = SkillDocument.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, document).getOrThrow();
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.write((net.minecraft.nbt.CompoundTag) encoded,
                    new java.io.DataOutputStream(bytes));
            return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException failure) { throw new IllegalStateException("document byte observation failed", failure); }
    }

    private static void requireValidation(MinecraftServer server, String primary, String lastAttempt) {
        var actor = server.getPlayerList().getPlayer(playerId);
        requireCurrentActor(server, actor);
        observeExistingProductionPorts();
        var storeIdentity = observedStore;
        var submissionIdentity = observedStore.submissionPort();
        var attachmentIdentity = observedAttachments;
        var runtimeIdentity = observedRuntime;
        var skillId = P9StarterSkillIdentityV0.forPlayer(playerId);
        var owner = storeValue(observedStore.ownerOf(server, skillId));
        var latest = storeValue(observedStore.latestReference(server, skillId));
        var equipped = attachmentValue(observedAttachments.equippedAt(actor, 0));
        var draft = attachmentValue(observedAttachments.findDraft(actor, skillId));
        var pending = submissionIdentity.observePendingRecovery(server, new SkillOwnerId(playerId));
        require(pending instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available,
                "validate observation requires an available exact-owner pending projection");
        var observedTarget = revisionC != null ? revisionC
                : revisionB != null ? revisionB : revisionA;
        var observedTargetBytes = observedTarget == null ? null
                : documentBytes(storeValue(observedStore.find(server, observedTarget)).orElseThrow());
        var messages = new ArrayList<String>();
        var result = new int[] {Integer.MIN_VALUE};
        var observer = new CommandSource() {
            @Override public void sendSystemMessage(Component component) { messages.add(component.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        var source = server.createCommandSourceStack().withSource(observer)
                .withCallback((success, code) -> result[0] = code);
        require(source.hasPermission(2) && !actor.createCommandSourceStack().hasPermission(1),
                "operator diagnostics changed normal-player authority");
        server.getCommands().performPrefixedCommand(source, "skill validate template gramarye:starter_bolt_v0");
        require(result[0] == 1 && !messages.isEmpty()
                && messages.getFirst().startsWith(primary + " lastAttempt=" + lastAttempt + " "),
                "validate returned the wrong independent primary/latest-attempt axes: " + messages);
        require(messages.size() <= 17 && messages.stream().allMatch(value -> value.length() <= 1_024)
                && messages.stream().mapToInt(String::length).sum() <= 17_408, "validate exceeded output bounds");
        observeExistingProductionPorts();
        require(observedStore == storeIdentity && observedStore.submissionPort() == submissionIdentity
                        && observedAttachments == attachmentIdentity && observedRuntime == runtimeIdentity
                        && server.getPlayerList().getPlayer(playerId) == actor,
                "read-only validate replaced an exact authority or actor identity");
        require(owner.equals(storeValue(observedStore.ownerOf(server, skillId)))
                && latest.equals(storeValue(observedStore.latestReference(server, skillId)))
                && equipped.equals(attachmentValue(observedAttachments.equippedAt(actor, 0)))
                && draft.equals(attachmentValue(observedAttachments.findDraft(actor, skillId)))
                && pending.equals(submissionIdentity.observePendingRecovery(server, new SkillOwnerId(playerId))),
                "read-only validate mutated exact owner, player routes, or pending recovery steps");
        if (observedTarget != null) {
            var targetAfter = revisionC != null ? revisionC
                    : revisionB != null ? revisionB : revisionA;
            require(observedTarget.equals(targetAfter)
                            && observedTargetBytes.equals(documentBytes(
                                    storeValue(observedStore.find(server, observedTarget)).orElseThrow())),
                    "read-only validate changed the exact held reference or immutable document bytes");
        }
    }

    private static Path ownedTemplatePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("p10-owned-template").resolve(TEMPLATE_JSON);
    }

    private static com.google.gson.JsonObject templateJson(long magnitude) {
        try (var input = P9S5ClientRuntimeHarness.class.getResourceAsStream("/" + TEMPLATE_JSON)) {
            require(input != null, "built-in template resource is absent from candidate");
            var body = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            body.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("action")
                    .getAsJsonObject("payload").addProperty("magnitude", magnitude);
            return body;
        } catch (IOException failure) { throw new IllegalStateException("built-in fixture read failed", failure); }
    }

    private static void reloadOwnedTemplate(MinecraftServer server, long magnitude, Runnable accepted) {
        writeOwnedTemplate(server, templateJson(magnitude));
        reloadSelectedTemplate(server, accepted);
    }

    private static void writeOwnedTemplate(MinecraftServer server, com.google.gson.JsonObject body) {
        try {
            var packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("p10-owned-template");
            var path = ownedTemplatePath(server);
            require(!Files.isSymbolicLink(packRoot) && !Files.isSymbolicLink(path), "owned pack became a symlink");
            Files.createDirectories(path.getParent());
            Files.writeString(packRoot.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":"
                    + net.minecraft.SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA)
                    + ",\"description\":\"P10 owned same-candidate template\"}}", StandardCharsets.UTF_8);
            Files.writeString(path, body.toString(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new IllegalStateException("owned template write failed", failure); }
    }

    private static void reloadSelectedTemplate(MinecraftServer server, Runnable accepted) {
        server.getPackRepository().reload();
        var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        if (!selected.contains(TEMPLATE_PACK)) selected.add(TEMPLATE_PACK);
        require(server.getPackRepository().isAvailable(TEMPLATE_PACK), "owned external pack is not available");
        server.reloadResources(selected).whenComplete((ignored, failure) -> {
            if (failure != null) { asynchronousFailure = failure; return; }
            try { accepted.run(); }
            catch (RuntimeException | Error problem) { asynchronousFailure = problem; }
        });
    }

    private static void exerciseInvalidOverrides(MinecraftServer server, int index) {
        try {
            var kinds = List.of("UNKNOWN_TYPE", "MIGRATION_FAILED", "DECODE_FAILED", "FUTURE_SCHEMA", "SEMANTIC_INVALID");
            if (index == kinds.size()) {
                Files.delete(ownedTemplatePath(server));
                reloadSelectedTemplate(server, () -> {
                    assertPlayerReference(server, revisionC);
                    assertHeldRevision(server);
                    requireValidation(server, "READY_CURRENT", "ACCEPTED");
                    p10AsyncComplete = true;
                });
                return;
            }
            var body = templateJson(5_000L);
            var action = body.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("action");
            switch (index) {
                case 0 -> action.addProperty("type", "gramarye:missing_p10_type");
                case 1 -> action.addProperty("schema_version", 0);
                case 2 -> action.getAsJsonObject("payload").remove("mana_cost");
                case 3 -> action.addProperty("schema_version", 2);
                case 4 -> action.getAsJsonObject("payload").addProperty("magnitude", 4_500L);
                default -> throw new IllegalStateException("unknown negative control");
            }
            writeOwnedTemplate(server, body);
            reloadSelectedTemplate(server, () -> {
                assertPlayerReference(server, revisionC);
                assertHeldRevision(server);
                requireValidation(server, "READY_LKG", kinds.get(index));
                server.execute(() -> exerciseInvalidOverrides(server, index + 1));
            });
        } catch (IOException | RuntimeException | Error failure) { asynchronousFailure = failure; }
    }

    private static void discardTarget() {
        if (target != null && !target.isRemoved()) target.discard();
    }

    private static void prepareServerPlayer(
            MinecraftServer server, UUID expectedPlayerId, int generation) {
        try {
            require(server.isSameThread(), "normal-player setup is off the server thread");
            require(server.overworld().getSeed() == WORLD_SEED,
                    "integrated world seed is not exact");
            var actor = server.getPlayerList().getPlayer(expectedPlayerId);
            requireCurrentActor(server, actor);
            if (generation == 1) {
                firstServerPlayer = actor;
            }
            var commandSource = actor.createCommandSourceStack();
            require(!server.getWorldData().isAllowCommands()
                            && !server.getPlayerList().isOp(actor.getGameProfile())
                            && server.getProfilePermissions(actor.getGameProfile()) == 0
                            && commandSource.hasPermission(0)
                            && !commandSource.hasPermission(1),
                    "normal-player fixture unexpectedly has operator authority");
            var safeY = actor.getY() + SAFE_FLIGHT_CLEARANCE;
            var safePosition = net.minecraft.core.BlockPos.containing(
                    actor.getX(), safeY, actor.getZ());
            require(server.overworld().isInWorldBounds(safePosition)
                            && server.overworld().isLoaded(safePosition),
                    "safe flight position is unavailable");
            actor.connection.teleport(actor.getX(), safeY, actor.getZ(), 0.0F, 0.0F);
            requireCurrentActor(server, actor);
            if (generation == 1) {
                var callbackInvoked = new boolean[1];
                var commandResult = new int[] {Integer.MIN_VALUE};
                var spoofedSource = server.createCommandSourceStack()
                        .withEntity(actor)
                        .withCallback((success, result) -> {
                            callbackInvoked[0] = true;
                            commandResult[0] = result;
                        });
                require(spoofedSource.source != actor,
                        "negative command fixture did not preserve its nonplayer source");
                server.getCommands().performPrefixedCommand(spoofedSource, COMMAND);
                require(callbackInvoked[0]
                                && commandResult[0] == 0
                                && spoofedCommandEventCount == 1,
                        "console/entity-substituted starter command was not rejected");
                spoofedProvisionRejected = true;
            }
            observeExistingProductionPorts();
            if (generation == 1) {
                startWrongServerProbe(server);
                var presentationOwner = observedPresentationOwner();
                long catalogBeforeA = presentationOwner.catalogGenerationForTesting();
                require(catalogBeforeA > 0L,
                        "supported A reload did not start with an active server catalog");
                reloadOwnedTemplate(server, 4_000L, () -> {
                    requireValidation(server, "READY_CURRENT", "ACCEPTED");
                    require(observedPresentationOwner() == presentationOwner,
                            "supported A reload replaced the production P8 owner");
                    long catalogAfterA = presentationOwner.catalogGenerationForTesting();
                    require(catalogAfterA == Math.addExact(catalogBeforeA, 1L),
                            "supported A reload did not activate the exact next server catalog generation");
                    preparedACatalogGeneration = catalogAfterA;
                    serverPreparationGeneration = generation;
                });
            } else {
                if (generation == 2) assertRootHandoffWrongServer(server);
                requireEmptyPendingRecovery(server);
                if (generation == 2) recoveryATerminal = true;
                if (generation == 3) {
                    assertHeldRevision(server);
                    recoveryBTerminal = true;
                }
                serverPreparationGeneration = generation;
            }
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    private static void recordCommandCompletion(int ordinal, int result) {
        try {
            var server = activeServer;
            require(server != null && server.isSameThread(),
                    "starter command result callback is off the owning server thread");
            require(result == 1,
                    "starter command did not return exact success for ordinal " + ordinal);
            require(ordinal == commandCompletionCount + 1,
                    "starter command results completed out of order");
            observeCommittedReference(server, ordinal);
            commandCompletionCount = ordinal;
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    private static void armCollisionTarget(MinecraftServer server) {
        var projectile = serverProjectile;
        require(projectile != null && projectile.level() == server.overworld(),
                "server projectile is unavailable for controlled collision");
        var movement = projectile.getDeltaMovement();
        require(movement.lengthSqr() > 0.0 && Double.isFinite(movement.lengthSqr()),
                "server projectile has no finite active motion");
        var direction = movement.normalize();
        var candidate = EntityType.COW.create(server.overworld());
        require(candidate != null, "controlled Cow target creation failed");
        candidate.setNoAi(true);
        candidate.setNoGravity(true);
        candidate.setInvulnerable(false);
        candidate.setAbsorptionAmount(0.0F);
        candidate.setPos(
                projectile.getX() + direction.x * 3.0,
                projectile.getY() - candidate.getBbHeight() * 0.5,
                projectile.getZ() + direction.z * 3.0);
        require(server.overworld().isLoaded(candidate.blockPosition()),
                "controlled collision target position is not loaded");
        targetInitialHealthBits = Float.floatToIntBits(candidate.getHealth());
        require(server.overworld().addFreshEntity(candidate),
                "controlled unarmored Cow target insertion failed");
        target = candidate;
    }

    private static void observeExactDamage() {
        if (exactFourDamageObserved) {
            return;
        }
        var projectile = serverProjectile;
        var exactTarget = target;
        if (!serverImpactObserved
                || !serverLeaveObserved
                || projectile == null
                || exactTarget == null
                || !projectile.isRemoved()
                || projectile.level().getEntity(projectile.getId()) == projectile) {
            return;
        }
        require(exactTarget.isAddedToLevel() && !exactTarget.isRemoved(),
                "P6 damage removed the controlled target");
        exactFourDamageObserved = damageCalls == 1 && exactAttributionObserved
                && Float.floatToIntBits(exactTarget.getHealth())
                == Float.floatToIntBits(
                        Float.intBitsToFloat(targetInitialHealthBits) - expectedDamage);
    }

    private static void queueCastClick(Minecraft minecraft) {
        require(minecraft.isSameThread() && clientCastGatesOpen(minecraft),
                "client cast gates are not all open");
        minecraft.player.setYRot(0.0F);
        minecraft.player.setXRot(0.0F);
        KeyMapping.click(CAST_KEY);
    }

    private static boolean clientCastGatesOpen(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.player != null
                && minecraft.getConnection() != null
                && minecraft.screen == null
                && minecraft.isWindowActive()
                && !minecraft.isPaused();
    }

    private static void requireCurrentActor(MinecraftServer server, ServerPlayer actor) {
        require(actor != null
                        && actor.getServer() == server
                        && actor.serverLevel().getServer() == server
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actor.isAddedToLevel()
                        && !actor.isRemoved()
                        && actor.isAlive()
                        && actor.connection != null
                        && actor.connection.isAcceptingMessages(),
                "integrated ServerPlayer is not the exact live normal actor");
    }

    private static void sampleParticleCount(Minecraft minecraft) {
        if (phase != Phase.WAIT_FOR_FIRST_CAST || minecraft.level == null) {
            return;
        }
        firstParticleMaximum = Math.max(firstParticleMaximum, particleCount(minecraft));
    }

    private static int particleCount(Minecraft minecraft) {
        var count = new int[1];
        minecraft.particleEngine.iterateParticles(particle -> {
            if (particle.getClass().getEnclosingClass()
                    == MinecraftP8ClientPresentationBackend.class) {
                count[0]++;
            }
        });
        return count[0];
    }

    private static boolean presentationReady() {
        var state = presentationState;
        return state != null
                && state.connected()
                && state.worldReady()
                && state.resourceReady()
                && state.installedCatalogGeneration() > 0L
                && state.catalogEvaluationResourceGeneration()
                        == state.resourceGeneration()
                && state.installedCatalogSnapshot() != null
                && state.installedCatalogSnapshot().entries().size() >= 3;
    }

    private static boolean sameCatalogWireValues(
            P8ProfileCatalogSnapshot first,
            P8ProfileCatalogSnapshot second) {
        var firstEntries = first.entries();
        var secondEntries = second.entries();
        if (firstEntries.size() != secondEntries.size()
                || first.wireBodyBytes() != second.wireBodyBytes()) {
            return false;
        }
        for (int index = 0; index < firstEntries.size(); index++) {
            var firstEntry = firstEntries.get(index);
            var secondEntry = secondEntries.get(index);
            if (!firstEntry.profileId().equals(secondEntry.profileId())
                    || !firstEntry.typeId().equals(secondEntry.typeId())
                    || firstEntry.channel() != secondEntry.channel()
                    || !firstEntry.clientFactoryId().equals(
                            secondEntry.clientFactoryId())
                    || firstEntry.configurationVersion()
                            != secondEntry.configurationVersion()
                    || !firstEntry.retainedCanonicalConfigurationJson().equals(
                            secondEntry.retainedCanonicalConfigurationJson())
                    || firstEntry.envelopeBytes() != secondEntry.envelopeBytes()
                    || firstEntry.wireBodyBytes() != secondEntry.wireBodyBytes()) {
                return false;
            }
        }
        return true;
    }

    private static String presentationReadiness() {
        var state = presentationState;
        if (state == null) {
            return " presentationState=null";
        }
        var snapshot = state.installedCatalogSnapshot();
        return " presentationState[connected=" + state.connected()
                + ",worldReady=" + state.worldReady()
                + ",resourceReady=" + state.resourceReady()
                + ",connectionGeneration=" + state.connectionGeneration()
                + ",worldGeneration=" + state.worldGeneration()
                + ",resourceGeneration=" + state.resourceGeneration()
                + ",catalogGeneration=" + state.installedCatalogGeneration()
                + ",catalogResourceGeneration="
                + state.catalogEvaluationResourceGeneration()
                + ",catalogEntries=" + (snapshot == null ? 0 : snapshot.entries().size())
                + ",expectedBCatalogGeneration=" + preparedBCatalogGeneration
                + ",p7ReloadGateState=" + lastP7ReloadGateState
                + ",p7ReloadCloseRequested=" + lastP7ReloadCloseRequested
                + ",p7ReloadGateTick=" + lastP7ReloadGateTick
                + "]";
    }

    private static long boundCatalogConnectionGeneration() {
        var boundCatalog = readField(
                presentationExecution, "boundCatalog", Object.class);
        var generations = readField(boundCatalog, "generations", Object.class);
        return readField(generations, "connection", Long.class);
    }

    private static P8ProfileCatalogSnapshot boundCatalogSnapshot() {
        var boundCatalog = readField(
                presentationExecution, "boundCatalog", Object.class);
        return readField(boundCatalog, "source", P8ProfileCatalogSnapshot.class);
    }

    private static <T> T readField(Object owner, String name, Class<T> expectedType) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return expectedType.cast(field.get(owner));
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException(
                    "fixed harness observation bridge drifted: " + name, failure);
        }
    }

    private static void resetCastObservation() {
        serverProjectile = null;
        clientProjectile = null;
        projectileId = null;
        target = null;
        targetInitialHealthBits = 0;
        serverImpactObserved = false;
        serverLeaveObserved = false;
        exactFourDamageObserved = false;
        damageCalls = 0;
        exactAttributionObserved = false;
    }

    private static void requireNoAsynchronousFailure() {
        var failure = asynchronousFailure;
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("unexpected asynchronous failure", failure);
    }

    private static void transition(Phase replacement) {
        phase = replacement;
        phaseTicks = 0L;
    }

    private static void marker(String marker) {
        MARKERS.add(marker);
        Gramarye.LOGGER.info("P9-S5-CLIENT-RUNTIME {}", marker);
    }

    private static void pass(Minecraft minecraft) {
        terminal = true;
        phase = Phase.TERMINAL;
        try {
            stopWrongServerProbe();
            writeResult("RESULT=PASS");
        } finally {
            minecraft.stop();
        }
    }

    private static void fail(Minecraft minecraft, Throwable failure) {
        if (terminal) {
            return;
        }
        var failedPhase = phase;
        terminal = true;
        phase = Phase.TERMINAL;
        var message = failure.getMessage() == null
                ? ""
                : failure.getMessage().replace('\n', ' ').replace('\r', ' ');
        MARKERS.add("FAIL phase=" + failedPhase + " type="
                + failure.getClass().getName() + " message=" + message);
        Gramarye.LOGGER.error("P9-S5 client runtime harness failed", failure);
        try {
            writeResult("RESULT=FAIL");
        } finally {
            try {
                stopWrongServerProbe();
            } finally {
                minecraft.stop();
            }
        }
    }

    private static void writeResult(String terminalResult) {
        var configured = System.getProperty("gramarye.p9s5.clientHarnessResult");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("P9-S5 client harness result path is missing");
        }
        Path result = Path.of(configured).toAbsolutePath().normalize();
        var lines = new ArrayList<String>(MARKERS.size() + 2);
        lines.add("P9-S5-CLIENT-RUNTIME-HARNESS-V1");
        lines.addAll(MARKERS);
        lines.add(terminalResult);
        try {
            Files.createDirectories(result.getParent());
            Files.write(
                    result,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "P9-S5 client harness result write failed", failure);
        }
    }

    private static void writeOwnedPlayerInventory(UUID exactPlayerId) {
        var configured = System.getProperty(
                "gramarye.p9s5.clientHarnessOwnedPlayer");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "P9-S5 client harness owned-player inventory path is missing");
        }
        var uuid = Objects.requireNonNull(exactPlayerId, "exactPlayerId").toString();
        Path inventory = Path.of(configured).toAbsolutePath().normalize();
        var lines = List.of(
                "P9-S5-CLIENT-OWNED-PLAYER-V1",
                "uuid=" + uuid,
                "world=" + WORLD_DIRECTORY,
                "playerdata=" + uuid + ".dat",
                "playerdata_old=" + uuid + ".dat_old",
                "stats=" + uuid + ".json",
                "advancements=" + uuid + ".json");
        try {
            Files.createDirectories(inventory.getParent());
            Files.write(
                    inventory,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "P9-S5 client harness owned-player inventory write failed",
                    failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean hasCause(
            Throwable failure, String expectedClassName, String expectedMessage) {
        var current = failure;
        for (var depth = 0; current != null && depth < 16; depth++) {
            if (current.getClass().getName().equals(expectedClassName)
                    && expectedMessage.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class SuppressionScreen extends Screen {
        private SuppressionScreen() {
            super(Component.literal("P9-S5 input suppression probe"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private enum SuppressionProbe {
        CHAT,
        INVENTORY,
        CONTROLS,
        NON_PAUSING,
        WINDOW_INACTIVE,
        PAUSED,
        INTERTICK_SCREEN,
        INTERTICK_FOCUS
    }

    private enum Phase {
        BOOTSTRAP,
        WAIT_FOR_PREWORLD_SUPPRESSION,
        WAIT_FOR_FIRST_WORLD,
        WAIT_FOR_PLAY_REENTRY,
        WAIT_FOR_FIRST_COMMAND,
        WAIT_FOR_SECOND_COMMAND,
        WAIT_FOR_WRONG_THREAD_INPUT_REJECTION,
        WAIT_FOR_SUPPRESSION_GATE,
        WAIT_FOR_SUPPRESSION_FLUSH,
        WAIT_FOR_SUPPRESSED_CAST_QUIET,
        WAIT_FOR_FIRST_CAST,
        WAIT_FOR_FIRST_TARGET_CLEANUP,
        WAIT_FOR_REOPEN_REQUEST,
        WAIT_FOR_REOPENED_WORLD,
        WAIT_FOR_REOPEN_FLUSH,
        WAIT_FOR_SECOND_CAST,
        WAIT_FOR_P10_A_RELOAD_PREPARATION,
        WAIT_FOR_P10_A_RELOAD_CAST,
        WAIT_FOR_P10_B_RELOAD,
        WAIT_FOR_P10_B_COMMAND,
        WAIT_FOR_P10_B_CAST,
        WAIT_FOR_P10_B_SAVE,
        WAIT_FOR_P10_B_REOPEN_REQUEST,
        WAIT_FOR_P10_B_REOPENED,
        WAIT_FOR_P10_C_RELOAD,
        WAIT_FOR_P10_C_COMMAND,
        WAIT_FOR_P10_CONTROLS,
        TERMINAL
    }
}
