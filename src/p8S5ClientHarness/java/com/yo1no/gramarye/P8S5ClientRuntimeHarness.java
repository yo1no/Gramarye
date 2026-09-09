package com.yo1no.gramarye;

import com.yo1no.gramarye.client.presentation.api.ClientProfileFactories;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Process-isolated actual-client P8-S5 qualification harness.
 *
 * <p>The harness owns no product state. It observes the installed S4 owner through one fixed,
 * read-only reflection bridge, submits two real clientbound PLAY payloads from the integrated
 * server, and terminates the throw-away client after bounded lifecycle checks.
 */
@EventBusSubscriber(
        modid = Gramarye.MOD_ID,
        value = Dist.CLIENT)
final class P8S5ClientRuntimeHarness {
    private static final long WORLD_SEED = 0x50385335434c4945L;
    private static final String WORLD_DIRECTORY = "p8-s5-client-runtime-world";
    private static final int PHASE_DEADLINE_TICKS = 1_200;
    private static final long EXPECTED_DEFAULT_PARTICLE_STARTS = 9L;
    private static final ResourceLocation SOUND_EVENT =
            ResourceLocation.withDefaultNamespace("entity.experience_orb.pickup");
    private static final ResourceLocation DEFAULT_SOUND = id("default_sound");
    private static final ResourceLocation DEFAULT_PARTICLE = id("default_particle");
    private static final ResourceLocation DEFAULT_TRAIL = id("default_trail");

    private static final List<String> MARKERS = new ArrayList<>(18);

    private static volatile Phase phase = Phase.BOOTSTRAP;
    private static volatile Throwable serverFailure;
    private static volatile long serverSubmittedSequence;
    private static volatile boolean serverWorldSeedVerified;
    private static volatile int playSoundEvents;
    private static volatile int playSoundSourceEvents;
    private static volatile boolean firstRenderObserved;
    private static volatile boolean secondRenderObserved;

    private static P8ClientPresentationState state;
    private static P8ClientPresentationExecution execution;
    private static CompletableFuture<Void> reloadFuture;
    private static long phaseTicks;
    private static long catalogGeneration;
    private static long connectionGenerationBeforeReload;
    private static long worldGenerationBeforeReload;
    private static long resourceGenerationBeforeReload;
    private static long connectionGenerationBeforeDisconnect;
    private static long resourceGenerationBeforeDisconnect;
    private static boolean firstExecutionObserved;
    private static boolean secondExecutionObserved;
    private static boolean reloadTransitionObserved;
    private static boolean logoutTransitionObserved;
    private static boolean disconnectPlatformCleanupObserved;
    private static long firstFactoryCalls;
    private static long firstParticleStarts;
    private static long firstSoundStarts;
    private static long secondFactoryCalls;
    private static long secondParticleStarts;
    private static long secondSoundStarts;
    private static int firstParticleBaseline;
    private static int firstParticleMaximum;
    private static int secondParticleBaseline;
    private static int secondParticleMaximum;
    private static boolean terminal;

    private P8S5ClientRuntimeHarness() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onClientPostTickBeforeProduct(ClientTickEvent.Post ignored) {
        if (terminal || phase != Phase.WAIT_FOR_RELOAD || state == null) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        try {
            requireNoServerFailure();
            var currentResourceGeneration = state.resourceGeneration();
            if (reloadTransitionObserved) {
                require(
                        resourceGenerationBeforeReload != Long.MAX_VALUE
                                && currentResourceGeneration
                                        == resourceGenerationBeforeReload + 1L,
                        "latched resource generation changed before reload completion");
                return;
            }
            if (currentResourceGeneration == resourceGenerationBeforeReload) {
                // Preserve a live cleanup target until the exact resource-generation
                // publication. This runs before the product's NORMAL post-tick listener,
                // so generation-mismatch aging cannot manufacture the required zeros.
                requireLiveVisualWork("pending resource reload");
                return;
            }
            requireReloadTransitionCleanup();
            reloadTransitionObserved = true;
        } catch (RuntimeException | Error failure) {
            fail(minecraft, failure);
        }
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
                throw new IllegalStateException("phase tick deadline exceeded: " + phase);
            }
            sampleParticleCount(minecraft);
            switch (phase) {
                case BOOTSTRAP -> bootstrap(minecraft);
                case WAIT_FOR_WORLD_AND_CATALOG -> waitForWorldAndCatalog(minecraft);
                case WAIT_FOR_FIRST_SUBMISSION ->
                        waitForSubmission(1L, Phase.WAIT_FOR_FIRST_EXECUTION);
                case WAIT_FOR_FIRST_EXECUTION -> waitForFirstExecution(minecraft);
                case WAIT_FOR_RELOAD -> waitForReload(minecraft);
                case WAIT_FOR_SECOND_SUBMISSION ->
                        waitForSubmission(2L, Phase.WAIT_FOR_SECOND_EXECUTION);
                case WAIT_FOR_SECOND_EXECUTION -> waitForSecondExecution(minecraft);
                case WAIT_FOR_DISCONNECT -> waitForDisconnect(minecraft);
                case TERMINAL -> {
                    // Terminal work stops the game loop in the same client tick.
                }
            }
        } catch (RuntimeException | Error failure) {
            fail(minecraft, failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlaySound(PlaySoundEvent event) {
        if (!terminal
                && event.getOriginalSound().getLocation().equals(SOUND_EVENT)
                && event.getSound() != null
                && isExecutionPhase()) {
            playSoundEvents++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlaySoundSource(PlaySoundSourceEvent event) {
        if (!terminal
                && event.getSound().getLocation().equals(SOUND_EVENT)
                && isExecutionPhase()) {
            playSoundSourceEvents++;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut ignored) {
        if (terminal || phase != Phase.WAIT_FOR_DISCONNECT) {
            return;
        }
        requireNoServerFailure();
        requireDisconnectTransitionCleanup();
        logoutTransitionObserved = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onAfterParticles(RenderLevelStageEvent event) {
        if (terminal || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        var currentExecution = execution;
        if (currentExecution == null
                || currentExecution.trailRenderSnapshot().strips().isEmpty()) {
            return;
        }
        if (phase == Phase.WAIT_FOR_FIRST_SUBMISSION
                || phase == Phase.WAIT_FOR_FIRST_EXECUTION) {
            firstRenderObserved = true;
        } else if (phase == Phase.WAIT_FOR_SECOND_SUBMISSION
                || phase == Phase.WAIT_FOR_SECOND_EXECUTION) {
            secondRenderObserved = true;
        }
    }

    private static void bootstrap(Minecraft minecraft) {
        require(minecraft.isSameThread(), "harness bootstrap is not on the client main thread");
        var forwarding = P8ClientPayloadDispatchFactory.production();
        state = readField(
                forwarding, "delegate", P8ClientPresentationState.class);
        execution = readField(
                state, "execution", P8ClientPresentationExecution.class);
        var backend = readField(
                execution, "backend", P8ClientPresentationBackend.class);
        require(
                backend == MinecraftP8ClientPresentationBackend.INSTANCE,
                "installed S5 backend is not the production Minecraft adapter");
        requireBuiltInFactory("sound");
        requireBuiltInFactory("particle");
        requireBuiltInFactory("trail");
        marker("01 STARTUP_FACTORIES_AND_PRODUCTION_BACKEND_OK");

        transition(Phase.WAIT_FOR_WORLD_AND_CATALOG);
        marker("02 FRESH_FIXED_SEED_WORLD_REQUESTED seed=" + WORLD_SEED);
        var settings = new LevelSettings(
                "P8-S5 Client Runtime Harness",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
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

    private static void waitForWorldAndCatalog(Minecraft minecraft) {
        requireNoServerFailure();
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.getSingleplayerServer() == null
                || !state.connected()
                || !state.worldReady()
                || !state.resourceReady()
                || state.installedCatalogGeneration() < 1L
                || state.catalogEvaluationResourceGeneration()
                        != state.resourceGeneration()) {
            return;
        }
        catalogGeneration = state.installedCatalogGeneration();
        require(
                state.connectionGeneration() > 0L
                        && state.worldGeneration() > 0L
                        && state.resourceGeneration() > 0L,
                "ready client retained a non-positive lifecycle generation");
        require(
                state.installedCatalogSnapshot() != null,
                "catalog generation has no installed snapshot");
        require(
                state.installedCatalogSnapshot().entries().size() >= 3,
                "installed catalog omitted the required defaults");
        require(
                execution.activePresentationCount() == 0
                        && execution.liveParticleCredits() == 0L
                        && execution.activeSoundHandles() == 0L
                        && execution.activeTrails() == 0L,
                "fresh runtime began with active P8 presentation work");
        require(
                minecraft.options.particles().get() == ParticleStatus.ALL,
                "isolated client did not retain the default ALL particle preference");
        firstParticleBaseline = particleCount(minecraft);
        require(
                firstParticleBaseline == 0,
                "fresh ParticleEngine retained a P8 backend particle");
        firstParticleMaximum = firstParticleBaseline;
        marker("03 WORLD_CONNECTION_RESOURCE_AND_CATALOG_READY catalogGeneration="
                + catalogGeneration
                + " resourceGeneration="
                + state.resourceGeneration());
        submitEventFromIntegratedServer(minecraft, 1L);
        transition(Phase.WAIT_FOR_FIRST_SUBMISSION);
    }

    private static void waitForSubmission(long sequence, Phase next) {
        requireNoServerFailure();
        // Packet submission precedes the server's volatile acknowledgement. Latch any
        // same-tick S5 evidence before a following client tick resets its counters.
        observeExecution(sequence, sequence == 1L);
        if (serverSubmittedSequence < sequence) {
            return;
        }
        if (sequence == 1L) {
            require(
                    serverWorldSeedVerified,
                    "integrated server did not verify the fixed world seed");
        }
        marker(sequence == 1L
                ? "04 FIRST_EVENT_SERVER_PLAY_PACKET_SUBMITTED fixedSeedVerified=true"
                : "11 SECOND_EVENT_SERVER_PLAY_PACKET_SUBMITTED");
        transition(next);
    }

    private static void waitForFirstExecution(Minecraft minecraft) {
        requireNoServerFailure();
        observeExecution(1L, true);
        if (!firstExecutionObserved
                || playSoundEvents != 1
                || firstParticleMaximum <= firstParticleBaseline
                || !firstRenderObserved) {
            return;
        }
        marker("05 FIRST_EVENT_S4_TO_S5_FACTORY_BACKEND_EXECUTED factories="
                + firstFactoryCalls
                + " particleStarts="
                + firstParticleStarts
                + " soundStarts="
                + firstSoundStarts);
        marker("06 FIRST_PLAY_SOUND_EVENT_OBSERVED sourceCallbacks="
                + playSoundSourceEvents);
        marker("07 FIRST_PARTICLE_ENGINE_COUNT_OBSERVED baseline="
                + firstParticleBaseline
                + " maximum="
                + firstParticleMaximum);
        marker("08 FIRST_AFTER_PARTICLES_RENDER_OBSERVED");

        requireLiveVisualWork("resource reload");
        var activeBeforeReload = execution.activePresentationCount();
        var particleCreditsBeforeReload = execution.liveParticleCredits();
        var trailSegmentsBeforeReload = execution.totalTrailSegments();
        connectionGenerationBeforeReload = state.connectionGeneration();
        worldGenerationBeforeReload = state.worldGeneration();
        resourceGenerationBeforeReload = state.resourceGeneration();
        reloadFuture = minecraft.reloadResourcePacks();
        marker("09 CLIENT_RESOURCE_RELOAD_REQUESTED resourceGeneration="
                + resourceGenerationBeforeReload
                + " activeBefore="
                + activeBeforeReload
                + " particleCreditsBefore="
                + particleCreditsBeforeReload
                + " trailSegmentsBefore="
                + trailSegmentsBeforeReload);
        transition(Phase.WAIT_FOR_RELOAD);
    }

    private static void waitForReload(Minecraft minecraft) {
        requireNoServerFailure();
        if (!reloadTransitionObserved) {
            if (reloadFuture != null && reloadFuture.isDone()) {
                reloadFuture.join();
                throw new IllegalStateException(
                        "client reload completed without publishing a P8 resource transition");
            }
            return;
        }
        require(reloadFuture != null, "client reload future is missing");
        if (!reloadFuture.isDone()) {
            return;
        }
        reloadFuture.join();
        requireReloadPlatformCleanup(minecraft);
        // SoundManager stop visibility is conservative. Waiting only for that external
        // acknowledgement cannot conceal failure of transition-time owned-state cleanup,
        // which was asserted at HIGHEST before the product's NORMAL client-tick listener.
        if (execution.activeSoundHandles() != 0L) {
            return;
        }
        requireReloadStableState();
        var currentResourceGeneration = state.resourceGeneration();
        secondParticleBaseline = particleCount(minecraft);
        require(
                secondParticleBaseline == 0,
                "resource reload retained a P8 backend particle in ParticleEngine");
        marker("10 CLIENT_RESOURCE_RELOAD_CLEANUP_AND_REBIND_OBSERVED oldGeneration="
                + resourceGenerationBeforeReload
                + " newGeneration="
                + currentResourceGeneration
                + " conservativeParticleCredits="
                + execution.liveParticleCredits());
        secondParticleMaximum = secondParticleBaseline;
        submitEventFromIntegratedServer(minecraft, 2L);
        transition(Phase.WAIT_FOR_SECOND_SUBMISSION);
    }

    private static void waitForSecondExecution(Minecraft minecraft) {
        requireNoServerFailure();
        observeExecution(2L, false);
        if (!secondExecutionObserved
                || playSoundEvents != 2
                || secondParticleMaximum <= secondParticleBaseline
                || !secondRenderObserved) {
            return;
        }
        marker("12 SECOND_EVENT_S4_TO_S5_FACTORY_BACKEND_EXECUTED factories="
                + secondFactoryCalls
                + " particleStarts="
                + secondParticleStarts
                + " soundStarts="
                + secondSoundStarts);
        marker("13 SECOND_PLAY_SOUND_EVENT_OBSERVED totalSourceCallbacks="
                + playSoundSourceEvents);
        marker("14 SECOND_PARTICLE_ENGINE_COUNT_OBSERVED baseline="
                + secondParticleBaseline
                + " maximum="
                + secondParticleMaximum);
        marker("15 SECOND_AFTER_PARTICLES_RENDER_OBSERVED");
        requireLiveVisualWork("disconnect");
        connectionGenerationBeforeDisconnect = state.connectionGeneration();
        resourceGenerationBeforeDisconnect = state.resourceGeneration();
        require(
                connectionGenerationBeforeDisconnect != Long.MAX_VALUE,
                "fresh client exhausted its connection generation");
        marker("16 DISCONNECT_REQUESTED activeBefore="
                + execution.activePresentationCount()
                + " particleCreditsBefore="
                + execution.liveParticleCredits()
                + " trailSegmentsBefore="
                + execution.totalTrailSegments());
        transition(Phase.WAIT_FOR_DISCONNECT);
        var integratedServer = minecraft.getSingleplayerServer();
        require(integratedServer != null, "disconnect lost the isolated integrated server");
        integratedServer.halt(false);
        minecraft.disconnect();
        observeDisconnectPlatformCleanup(minecraft);
    }

    private static void waitForDisconnect(Minecraft minecraft) {
        requireNoServerFailure();
        if (minecraft.level != null) {
            return;
        }
        if (!disconnectPlatformCleanupObserved) {
            observeDisconnectPlatformCleanup(minecraft);
        }
        // As with reload, only the real sound engine's eventual stop acknowledgement
        // may lag the synchronous LoggingOut and Minecraft.disconnect boundaries.
        if (execution.activeSoundHandles() != 0L) {
            return;
        }
        requireDisconnectStableState(minecraft);
        marker("17 DISCONNECT_CONNECTION_WORLD_AND_ACTIVE_CLEANUP_OBSERVED");
        marker("18 TERMINAL_PASS");
        pass(minecraft);
    }

    private static void observeExecution(long sequence, boolean first) {
        var acceptedSequence = state.lastAcceptedSequence();
        require(
                acceptedSequence <= sequence,
                "client accepted an unexpected presentation sequence");
        if (acceptedSequence != sequence
                || execution.factoryCallsThisTick() != 3L
                || execution.particleStartsThisTick()
                        != EXPECTED_DEFAULT_PARTICLE_STARTS
                || execution.liveParticleCredits()
                        != EXPECTED_DEFAULT_PARTICLE_STARTS
                || execution.soundStartsThisTick() != 1L
                || execution.activeSoundHandles() != 1L
                || execution.activeTrails() != 1L
                || execution.totalTrailSegments() != 1L
                || execution.activePresentationCount() != 3
                || execution.trailRenderSnapshot().strips().size() != 1) {
            return;
        }
        if (first) {
            firstExecutionObserved = true;
            firstFactoryCalls = execution.factoryCallsThisTick();
            firstParticleStarts = execution.particleStartsThisTick();
            firstSoundStarts = execution.soundStartsThisTick();
        } else {
            secondExecutionObserved = true;
            secondFactoryCalls = execution.factoryCallsThisTick();
            secondParticleStarts = execution.particleStartsThisTick();
            secondSoundStarts = execution.soundStartsThisTick();
        }
    }

    private static void submitEventFromIntegratedServer(
            Minecraft minecraft, long sequence) {
        var server = minecraft.getSingleplayerServer();
        var clientPlayer = minecraft.player;
        require(server != null && clientPlayer != null, "integrated player is unavailable");
        UUID playerId = clientPlayer.getUUID();
        long expectedCatalogGeneration = catalogGeneration;
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null || player.hasDisconnected()) {
                    throw new IllegalStateException("integrated ServerPlayer is unavailable");
                }
                if (sequence == 1L) {
                    if (server.overworld().getSeed() != WORLD_SEED) {
                        throw new IllegalStateException(
                                "integrated server world seed is not exact");
                    }
                    serverWorldSeedVerified = true;
                }
                var appearance = defaultAppearance();
                var creation = PresentationEvent.createServer(
                        expectedCatalogGeneration,
                        PresentationEventKind.CAST_RELEASE.wireCode(),
                        OptionalInt.of(player.getId()),
                        OptionalInt.of(player.getId()),
                        player.serverLevel().dimension().location(),
                        player.getX(),
                        player.getY() + 1.0D,
                        player.getZ(),
                        0.0D,
                        1.0D,
                        0.0D,
                        appearance,
                        sequence);
                if (!(creation instanceof AcceptedPresentationEvent accepted)) {
                    throw new IllegalStateException("server rejected harness presentation event");
                }
                var payload = new PresentationEventPayload(accepted.event());
                if (!P8PacketSubmission.canSubmit(player, payload)) {
                    throw new IllegalStateException("clientbound PLAY channel is unavailable");
                }
                int encodedBytes = P8PacketSubmission.measureClientboundPlayPacket(
                        player,
                        payload,
                        PresentationLimits.MAX_EVENT_PACKET_CHARGE_BYTES);
                if (encodedBytes != accepted.event().packetCharge()) {
                    throw new IllegalStateException("clientbound PLAY packet charge drifted");
                }
                PacketDistributor.sendToPlayer(player, payload);
                serverSubmittedSequence = sequence;
            } catch (RuntimeException | Error failure) {
                serverFailure = failure;
            }
        });
    }

    private static EffectiveAppearance defaultAppearance() {
        return new EffectiveAppearance(
                0xffffcc44,
                0xff44ccff,
                1_000,
                selected(ProfileChannel.SOUND, DEFAULT_SOUND),
                selected(ProfileChannel.PARTICLE, DEFAULT_PARTICLE),
                selected(ProfileChannel.TRAIL, DEFAULT_TRAIL),
                Map.of());
    }

    private static ResolvedProfile selected(
            ProfileChannel channel, ResourceLocation profileId) {
        return new ResolvedProfile(
                channel, Optional.of(profileId), ProfileResolutionReason.SELECTED);
    }

    private static void sampleParticleCount(Minecraft minecraft) {
        if (minecraft.level == null) {
            return;
        }
        int count = particleCount(minecraft);
        if (phase == Phase.WAIT_FOR_FIRST_SUBMISSION
                || phase == Phase.WAIT_FOR_FIRST_EXECUTION) {
            firstParticleMaximum = Math.max(firstParticleMaximum, count);
        } else if (phase == Phase.WAIT_FOR_SECOND_SUBMISSION
                || phase == Phase.WAIT_FOR_SECOND_EXECUTION) {
            secondParticleMaximum = Math.max(secondParticleMaximum, count);
        }
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

    private static void requireBuiltInFactory(String path) {
        var id = id(path);
        var registration = ClientProfileFactories.registry()
                .getOptional(id)
                .orElseThrow(() -> new IllegalStateException(
                        "missing built-in client Profile factory: " + id));
        require(registration.key().id().equals(id), "built-in factory key mismatch: " + id);
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

    private static void requireNoServerFailure() {
        var failure = serverFailure;
        if (failure != null) {
            throw new IllegalStateException("integrated-server submission failed", failure);
        }
    }

    private static void requireLiveVisualWork(String boundary) {
        require(
                execution.activePresentationCount() >= 2
                        && execution.liveParticleCredits() > 0L
                        && execution.activeTrails() == 1L
                        && execution.totalTrailSegments() > 0L
                        && !execution.trailRenderSnapshot().strips().isEmpty(),
                boundary + " began without live P8 visual ownership");
    }

    private static void requireReloadTransitionCleanup() {
        requireReloadStableStateExceptSound();
    }

    private static void requireReloadPlatformCleanup(Minecraft minecraft) {
        requireReloadStableStateExceptSound();
        require(
                particleCount(minecraft) == 0,
                "completed resource reload retained a P8 backend particle in ParticleEngine");
    }

    private static void requireReloadStableState() {
        requireReloadStableStateExceptSound();
        require(
                execution.activeSoundHandles() == 0L,
                "resource reload retained a live P8 sound handle");
    }

    private static void requireReloadStableStateExceptSound() {
        require(
                resourceGenerationBeforeReload != Long.MAX_VALUE
                        && state.resourceGeneration()
                                == resourceGenerationBeforeReload + 1L,
                "one client reload did not advance the P8 resource generation exactly once");
        require(
                state.connected()
                        && state.connectionGeneration() == connectionGenerationBeforeReload
                        && state.worldReady()
                        && state.worldGeneration() == worldGenerationBeforeReload
                        && state.resourceReady()
                        && state.installedCatalogGeneration() == catalogGeneration
                        && state.catalogEvaluationResourceGeneration()
                                == state.resourceGeneration()
                        && state.lastAcceptedSequence() == 1L
                        && state.unavailableHandoffCount() == 0L,
                "completed resource reload did not preserve and rebind lifecycle truth");
        require(
                execution.activePresentationCount() == 0
                        && execution.activeTrails() == 0L
                        && execution.totalTrailSegments() == 0L
                        && execution.liveParticleCredits() == 0L
                        && execution.trailRenderSnapshot().strips().isEmpty(),
                "completed resource reload retained transition-scoped P8 work");
    }

    private static void requireDisconnectTransitionCleanup() {
        require(
                !state.connected()
                        && state.connectionGeneration()
                                == connectionGenerationBeforeDisconnect + 1L
                        && !state.worldReady()
                        && state.worldGeneration() == 0L
                        && state.resourceReady()
                        && state.resourceGeneration() == resourceGenerationBeforeDisconnect
                        && state.installedCatalogGeneration() == 0L
                        && state.catalogEvaluationResourceGeneration() == 0L
                        && state.lastAcceptedSequence() == 0L
                        && state.reservedEventCount() == 0L
                        && state.reservedEventBodyBytes() == 0L
                        && state.combinedQueuedCharge() == 0L
                        && state.pendingEventCount() == 0
                        && state.pendingCatalogGeneration() == 0L
                        && state.unavailableHandoffCount() == 0L,
                "LoggingOut did not synchronously clear P8 connection/world truth");
        require(
                execution.activePresentationCount() == 0
                        && execution.activeTrails() == 0L
                        && execution.totalTrailSegments() == 0L
                        && execution.liveParticleCredits() == 0L
                        && execution.diagnosticCount() == 0
                        && execution.suppressedDiagnosticCount() == 0L
                        && execution.trailRenderSnapshot().strips().isEmpty(),
                "LoggingOut did not synchronously clear owned P8 execution state");
    }

    private static void observeDisconnectPlatformCleanup(Minecraft minecraft) {
        require(
                logoutTransitionObserved,
                "Minecraft.disconnect completed without the P8 LoggingOut boundary");
        requireDisconnectTransitionCleanup();
        require(minecraft.level == null, "Minecraft.disconnect retained its client level");
        require(
                particleCount(minecraft) == 0,
                "Minecraft.disconnect retained a P8 backend particle in ParticleEngine");
        disconnectPlatformCleanupObserved = true;
    }

    private static void requireDisconnectStableState(Minecraft minecraft) {
        requireDisconnectTransitionCleanup();
        require(
                disconnectPlatformCleanupObserved && minecraft.level == null,
                "disconnect platform cleanup was not observed");
        require(
                execution.activeSoundHandles() == 0L,
                "disconnect retained a live P8 sound handle");
        require(
                particleCount(minecraft) == 0,
                "disconnect retained a P8 backend particle in ParticleEngine");
    }

    private static boolean isExecutionPhase() {
        return phase == Phase.WAIT_FOR_FIRST_SUBMISSION
                || phase == Phase.WAIT_FOR_FIRST_EXECUTION
                || phase == Phase.WAIT_FOR_SECOND_SUBMISSION
                || phase == Phase.WAIT_FOR_SECOND_EXECUTION;
    }

    private static void transition(Phase replacement) {
        phase = replacement;
        phaseTicks = 0L;
    }

    private static void marker(String marker) {
        MARKERS.add(marker);
        Gramarye.LOGGER.info("P8-S5-CLIENT-RUNTIME {}", marker);
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
        Gramarye.LOGGER.error("P8-S5 client runtime harness failed", failure);
        try {
            writeResult("RESULT=FAIL");
        } finally {
            minecraft.stop();
        }
    }

    private static void writeResult(String terminalResult) {
        var configured = System.getProperty("gramarye.p8s5.clientHarnessResult");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("P8-S5 client harness result path is missing");
        }
        Path result = Path.of(configured).toAbsolutePath().normalize();
        var lines = new ArrayList<String>(MARKERS.size() + 2);
        lines.add("P8-S5-CLIENT-RUNTIME-HARNESS-V1");
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
            throw new IllegalStateException("P8-S5 client harness result write failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private enum Phase {
        BOOTSTRAP,
        WAIT_FOR_WORLD_AND_CATALOG,
        WAIT_FOR_FIRST_SUBMISSION,
        WAIT_FOR_FIRST_EXECUTION,
        WAIT_FOR_RELOAD,
        WAIT_FOR_SECOND_SUBMISSION,
        WAIT_FOR_SECOND_EXECUTION,
        WAIT_FOR_DISCONNECT,
        TERMINAL
    }
}
