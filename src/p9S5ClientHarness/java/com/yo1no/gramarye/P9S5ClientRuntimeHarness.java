package com.yo1no.gramarye;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Process-isolated actual-client proof for the P9-S5 normal-player composition path.
 *
 * <p>The harness creates one throw-away integrated world, sends the production command through
 * the client play connection, and submits casts only through the registered R key mapping. It
 * observes the resulting world and presentation effects without acquiring or modifying the
 * Store, Attachment, equipped reference, P7 ingress, or P5 runtime.</p>
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

    private static final List<String> MARKERS = new ArrayList<>(26);

    private static volatile Phase phase = Phase.BOOTSTRAP;
    private static volatile Throwable asynchronousFailure;
    private static volatile MinecraftServer firstServer;
    private static volatile MinecraftServer activeServer;
    private static volatile ServerPlayer firstServerPlayer;
    private static volatile UUID playerId;
    private static volatile int serverPreparationGeneration;
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
            require(next <= 2, "more than two starter commands reached the server");
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
            if (castOrdinal > 0
                    && serverProjectile != null
                    && target == null
                    && (castOrdinal == 2 || clientProjectile != null)) {
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
            require(castOrdinal == 1 || castOrdinal == 2,
                    "a projectile spawned from the suppressed or absent cast input");
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
                && phase == Phase.WAIT_FOR_FIRST_CAST
                && event.getSound() != null
                && event.getOriginalSound().getLocation().equals(SOUND_EVENT)) {
            p8SoundEvents++;
        }
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
        marker("01 PRODUCTION_R_KEY_MAPPING_REGISTERED key=" + CAST_KEY.getName());

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
            marker("26 TERMINAL_PASS");
            pass(minecraft);
        }
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
            serverPreparationGeneration = generation;
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
        exactFourDamageObserved = Float.floatToIntBits(exactTarget.getHealth())
                == Float.floatToIntBits(
                        Float.intBitsToFloat(targetInitialHealthBits) - 4.0F);
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
            minecraft.stop();
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
        TERMINAL
    }
}
