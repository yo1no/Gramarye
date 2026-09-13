package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Process-isolated actual-client proof for the P9-S3 tracked projectile.
 *
 * <p>The controller creates only a throw-away integrated world and test-owned Store/runtime
 * fixture. It invokes the production P7 geometry, P5 scheduler, P6 transaction, world handoff,
 * registered entity, and renderer paths. It never constructs a permit, forces OPEN, or reports a
 * renderer callback as gameplay acknowledgement.</p>
 */
@EventBusSubscriber(
        modid = Gramarye.MOD_ID,
        value = Dist.CLIENT)
final class P9S3ClientRuntimeHarness {
    private static final long WORLD_SEED = 0x50395333434c4945L;
    private static final String WORLD_DIRECTORY = "p9-s3-client-runtime-world";
    private static final String STORE_NAME = "gramarye_skill_definitions";
    private static final int PHASE_DEADLINE_TICKS = 1_200;
    private static final double SAFE_FLIGHT_CLEARANCE = 16.0;
    private static final SkillId SKILL_ID = new SkillId(
            UUID.fromString("90330000-0000-4000-8000-000000000001"));
    private static final ResourceLocation PROJECTILE_TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "starter_projectile");
    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY =
            new SavedData.Factory<>(
                    () -> {
                        throw new IllegalStateException(
                                "client harness invoked the Store cache constructor");
                    },
                    (tag, provider) -> {
                        throw new IllegalStateException(
                                "client harness invoked the Store cache decoder");
                    });

    private static final List<String> MARKERS = new ArrayList<>(14);

    private static volatile Phase phase = Phase.BOOTSTRAP;
    private static volatile Throwable asynchronousFailure;
    private static volatile MinecraftServer fixtureServer;
    private static volatile IEventBus fixtureBus;
    private static volatile SavedData originalStore;
    private static volatile SkillRuntimeService runtime;
    private static volatile SkillReference submittedReference;
    private static volatile P9StarterProjectile serverProjectile;
    private static volatile P9StarterProjectile clientProjectile;
    private static volatile UUID projectileId;
    private static volatile ArmorStand target;
    private static volatile int targetInitialHealthBits;
    private static volatile boolean serverSetupReady;
    private static volatile boolean rootAdmitted;
    private static volatile boolean serverSpawnObserved;
    private static volatile boolean clientReceptionObserved;
    private static volatile boolean rendererSelectionObserved;
    private static volatile boolean actualEntityRenderStageObserved;
    private static volatile boolean collisionRequested;
    private static volatile boolean targetArmed;
    private static volatile boolean serverImpactObserved;
    private static volatile boolean serverLeaveObserved;
    private static volatile boolean serverTerminalRemovalObserved;
    private static volatile boolean targetHealthUnchanged;
    private static volatile boolean clientRemovalObserved;
    private static volatile boolean cleanupRequested;
    private static volatile boolean cleanupComplete;

    private static long phaseTicks;
    private static boolean impactMarkerWritten;
    private static boolean terminalRemovalMarkerWritten;
    private static boolean terminal;

    private P9S3ClientRuntimeHarness() {
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
                throw new IllegalStateException("phase tick deadline exceeded: " + phase);
            }
            requireNoAsynchronousFailure();
            switch (phase) {
                case BOOTSTRAP -> bootstrap(minecraft);
                case WAIT_FOR_WORLD -> waitForWorld(minecraft);
                case WAIT_FOR_SERVER_SETUP -> waitForServerSetup();
                case WAIT_FOR_SERVER_SPAWN -> waitForServerSpawn();
                case WAIT_FOR_CLIENT_RECEPTION -> waitForClientReception(minecraft);
                case WAIT_FOR_CLIENT_RENDER -> waitForClientRender();
                case WAIT_FOR_HIT_TERMINAL -> waitForHitTerminal();
                case WAIT_FOR_CLIENT_REMOVAL -> waitForClientRemoval(minecraft);
                case WAIT_FOR_CLEANUP -> waitForCleanup(minecraft);
                case TERMINAL -> {
                    // Terminal work stops the game loop in the same client tick.
                }
            }
        } catch (RuntimeException | Error failure) {
            fail(minecraft, failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerPostTick(ServerTickEvent.Post event) {
        var exactRuntime = runtime;
        var exactServer = fixtureServer;
        if (terminal
                || cleanupRequested
                || exactRuntime == null
                || exactServer == null
                || event.getServer() != exactServer) {
            return;
        }
        try {
            exactRuntime.handleRuntimePost(event);
            if (!serverSpawnObserved) {
                observeServerSpawn(exactServer);
            }
            if (collisionRequested && serverSpawnObserved && !targetArmed) {
                armCollisionTarget(exactServer);
            }
            observeServerTerminal(exactServer);
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof P9StarterProjectile projectile)) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            clientProjectile = projectile;
            clientReceptionObserved = true;
        } else {
            var existing = serverProjectile;
            if (existing != null && existing != projectile) {
                asynchronousFailure = new IllegalStateException(
                        "more than one server P9 projectile joined the harness world");
                return;
            }
            serverProjectile = projectile;
            projectileId = projectile.getUUID();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof P9StarterProjectile projectile)) {
            return;
        }
        var expectedId = projectileId;
        if (expectedId == null || !expectedId.equals(projectile.getUUID())) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            clientRemovalObserved = true;
        } else if (projectile == serverProjectile) {
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
    static void onAfterEntities(RenderLevelStageEvent event) {
        if (terminal
                || phase != Phase.WAIT_FOR_CLIENT_RENDER
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        try {
            var level = minecraft.level;
            var projectile = clientProjectile;
            var expectedId = projectileId;
            if (level == null
                    || projectile == null
                    || expectedId == null
                    || level.getEntity(projectile.getId()) != projectile
                    || !expectedId.equals(projectile.getUUID())
                    || projectile.getType() != P9StarterProjectileRegistration.type()
                    || !projectile.isAddedToLevel()
                    || projectile.isRemoved()) {
                return;
            }

            var renderer = minecraft.getEntityRenderDispatcher().getRenderer(projectile);
            if (!(renderer instanceof ThrownItemRenderer<?>)) {
                throw new IllegalStateException(
                        "registered projectile renderer is not ThrownItemRenderer");
            }
            rendererSelectionObserved = true;

            boolean enumerated = false;
            for (Entity candidate : level.entitiesForRendering()) {
                if (candidate == projectile) {
                    enumerated = true;
                    break;
                }
            }
            var cameraPosition = event.getCamera().getPosition();
            var sectionReady = level.isOutsideBuildHeight(projectile.getBlockY())
                    || event.getLevelRenderer().isSectionCompiled(projectile.blockPosition());
            if (enumerated
                    && sectionReady
                    && projectile.tickCount >= 2
                    && minecraft.getEntityRenderDispatcher().shouldRender(
                            projectile,
                            event.getFrustum(),
                            cameraPosition.x,
                            cameraPosition.y,
                            cameraPosition.z)) {
                // AFTER_ENTITIES is dispatched immediately after LevelRenderer's entity loop.
                // Rechecking that loop's predicates against the same tracked object proves this
                // exact production renderer participated in the actual render stage.
                actualEntityRenderStageObserved = true;
            }
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    private static void bootstrap(Minecraft minecraft) {
        require(minecraft.isSameThread(), "harness bootstrap is not on the client main thread");
        var type = P9StarterProjectileRegistration.type();
        require(
                BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(PROJECTILE_TYPE_ID),
                "registered projectile EntityType identity is not exact");
        marker("01 STARTUP_ENTITY_TYPE_REGISTERED id=" + PROJECTILE_TYPE_ID);

        transition(Phase.WAIT_FOR_WORLD);
        marker("02 FRESH_FIXED_SEED_WORLD_REQUESTED seed=" + WORLD_SEED);
        var settings = new LevelSettings(
                "P9-S3 Client Runtime Harness",
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

    private static void waitForWorld(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        if (server == null || minecraft.level == null || player == null) {
            return;
        }
        require(server.isRunning() && !server.isStopped(),
                "fresh integrated server is not running");
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        fixtureServer = server;
        marker("03 WORLD_AND_INTEGRATED_PLAYER_READY player=" + player.getUUID());
        transition(Phase.WAIT_FOR_SERVER_SETUP);
        UUID playerId = player.getUUID();
        server.execute(() -> prepareServerFixture(server, playerId));
    }

    private static void waitForServerSetup() {
        if (!serverSetupReady || !rootAdmitted) {
            return;
        }
        marker("04 CANONICAL_CONTENT_AND_DIRECT_RUNTIME_READY reference="
                + submittedReference);
        marker("05 AUTHENTICATED_ROOT_ADMITTED");
        transition(Phase.WAIT_FOR_SERVER_SPAWN);
    }

    private static void waitForServerSpawn() {
        if (!serverSpawnObserved) {
            return;
        }
        marker("06 SERVER_PROJECTILE_SPAWNED_EXACT_TYPE uuid=" + projectileId);
        transition(Phase.WAIT_FOR_CLIENT_RECEPTION);
    }

    private static void waitForClientReception(Minecraft minecraft) {
        var projectile = clientProjectile;
        var expectedId = projectileId;
        if (!clientReceptionObserved
                || projectile == null
                || expectedId == null
                || minecraft.level == null
                || minecraft.level.getEntity(projectile.getId()) != projectile) {
            return;
        }
        require(expectedId.equals(projectile.getUUID()),
                "client received a different projectile UUID");
        require(projectile.getClass() == P9StarterProjectile.class,
                "client projectile implementation class is not exact");
        require(projectile.getType() == P9StarterProjectileRegistration.type(),
                "client projectile EntityType is not exact");
        marker("07 CLIENT_ENTITY_RECEIVED_EXACT_TYPE entityId=" + projectile.getId());
        transition(Phase.WAIT_FOR_CLIENT_RENDER);
    }

    private static void waitForClientRender() {
        if (!rendererSelectionObserved || !actualEntityRenderStageObserved) {
            return;
        }
        marker("08 REGISTERED_THROWN_ITEM_RENDERER_SELECTED");
        marker("09 AFTER_ENTITIES_RENDER_STAGE_WITH_TRACKED_ENTITY");
        collisionRequested = true;
        transition(Phase.WAIT_FOR_HIT_TERMINAL);
    }

    private static void waitForHitTerminal() {
        if (serverImpactObserved && !impactMarkerWritten) {
            marker("10 SERVER_AUTHORITATIVE_ENTITY_HIT_OBSERVED target="
                    + target.getUUID());
            impactMarkerWritten = true;
        }
        if (serverTerminalRemovalObserved && !terminalRemovalMarkerWritten) {
            require(serverImpactObserved,
                    "server projectile terminalized without the controlled entity hit");
            marker("11 NEXT_DRAIN_TERMINAL_SERVER_REMOVAL_OBSERVED");
            terminalRemovalMarkerWritten = true;
            transition(Phase.WAIT_FOR_CLIENT_REMOVAL);
        }
    }

    private static void waitForClientRemoval(Minecraft minecraft) {
        if (!clientRemovalObserved) {
            return;
        }
        var expectedId = projectileId;
        require(expectedId != null, "terminal projectile UUID is missing");
        require(minecraft.level != null, "client level ended before tracking removal");
        require(minecraft.level.entitiesForRendering() != null,
                "client render entity collection is unavailable");
        require(clientProjectile.isRemoved(),
                "client leave event did not mark the projectile removed");
        marker("12 CLIENT_TRACKING_REMOVAL_OBSERVED uuid=" + expectedId);
        require(targetHealthUnchanged,
                "server target health changed during unavailable S3 damage");
        marker("13 HEALTH_UNCHANGED_AND_DAMAGE_UNAVAILABLE healthBits="
                + targetInitialHealthBits);
        cleanupRequested = true;
        transition(Phase.WAIT_FOR_CLEANUP);
        var server = fixtureServer;
        require(server != null, "fixture server disappeared before cleanup");
        server.execute(() -> cleanupServerFixture(server));
    }

    private static void waitForCleanup(Minecraft minecraft) {
        if (!cleanupComplete) {
            return;
        }
        marker("14 TERMINAL_PASS");
        pass(minecraft);
    }

    private static void prepareServerFixture(MinecraftServer server, UUID playerId) {
        try {
            require(server.isSameThread(), "fixture setup is not on the server thread");
            var actor = server.getPlayerList().getPlayer(playerId);
            requireCurrentActor(server, actor);
            var safeY = actor.getY() + SAFE_FLIGHT_CLEARANCE;
            require(server.overworld().isInWorldBounds(
                            net.minecraft.core.BlockPos.containing(
                                    actor.getX(), safeY, actor.getZ()))
                            && server.overworld().isLoaded(
                                    net.minecraft.core.BlockPos.containing(
                                            actor.getX(), safeY, actor.getZ())),
                    "controlled flight clearance is unavailable");
            actor.connection.teleport(
                    actor.getX(), safeY, actor.getZ(), 0.0F, 0.0F);
            requireCurrentActor(server, actor);

            var storage = server.overworld().getDataStorage();
            originalStore = storage.get(CACHE_HIT_ONLY, STORE_NAME);
            require(originalStore != null, "production Store SavedData is not installed");

            var bus = BusBuilder.builder().build();
            var attachments =
                    PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests();
            var store = SkillDefinitionStoreService.registerOn(
                    bus, attachments, (exactServer, exactActor) -> {});
            bus.start();
            bus.post(new ServerStartingEvent(server));
            fixtureBus = bus;

            var draft = P9StarterSkillContent.canonicalDraft(SKILL_ID);
            requireApplied(attachments.putDraft(actor, draft),
                    "canonical P9 Draft publication");
            var submission = SkillDefinitionSubmissionService.production(
                            attachments,
                            store.submissionPort(),
                            SkillSubmissionPolicyProvider.defaults(),
                            ProfileAvailabilityView.unknown())
                    .submit(actor, SKILL_ID);
            require(submission instanceof SkillSubmissionCompositionOutcome.Committed,
                    "canonical P9 submission did not commit");
            var reference = ((SkillSubmissionCompositionOutcome.Committed) submission)
                    .reference();
            requireApplied(
                    attachments.setEquipped(actor, 0, Optional.of(reference)),
                    "canonical P9 slot-0 equip");

            var directRuntime = new SkillRuntimeService(
                    store,
                    SkillSubmissionPolicyProvider.defaults(),
                    new P5RuntimeProjector(ProfileAvailabilityView.unknown()),
                    new P5LoadedReferenceResolver(),
                    new P6RuntimeExecutionPortAdapter(
                            P6RuntimeExecutionCapability.forRuntimeAdapter(),
                            P8ServerPresentationService.create()));
            directRuntime.handleRuntimeStarted(
                    new ServerStartedEvent(server), directQualificationLimits());
            runtime = directRuntime;
            submittedReference = reference;
            serverSetupReady = true;

            var ingress = new P7AuthenticatedPlayerCastIngress(
                    directRuntime, attachments, store);
            var admission = ingress.authorizeAndAdmit(
                    server,
                    actor,
                    0,
                    (exactServer, exactActor) -> {
                        require(exactServer == server && exactActor == actor,
                                "P7 fixture changed the authenticated actor identity");
                        return P7ServerAuthorizationBoundary.TargetDisposition.VALID;
                    });
            require(
                    admission == P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                    "production P7-to-P5 root admission was rejected: " + admission);
            rootAdmitted = true;
        } catch (RuntimeException | Error failure) {
            asynchronousFailure = failure;
        }
    }

    private static void observeServerSpawn(MinecraftServer server) {
        var projectile = serverProjectile;
        if (projectile == null) {
            return;
        }
        var level = projectile.level();
        var owner = projectile.getOwner();
        var ownerLevelExact = owner != null && level == owner.level();
        var loadedByIdExact = level.getEntity(projectile.getId()) == projectile;
        var typeExact = projectile.getType() == P9StarterProjectileRegistration.type();
        var retained = !projectile.isRemoved() && projectile.isAddedToLevel();
        var serverExact = projectile.getServer() == server;
        if (!ownerLevelExact || !loadedByIdExact || !typeExact || !retained || !serverExact) {
            throw new IllegalStateException(
                    "production spawn did not retain the exact loaded projectile"
                            + " ownerLevelExact=" + ownerLevelExact
                            + " loadedByIdExact=" + loadedByIdExact
                            + " typeExact=" + typeExact
                            + " retained=" + retained
                            + " serverExact=" + serverExact
                            + " removal=" + projectile.getRemovalReason());
        }
        serverSpawnObserved = true;
    }

    private static void armCollisionTarget(MinecraftServer server) {
        var projectile = serverProjectile;
        require(projectile != null && projectile.level() == server.overworld(),
                "server projectile is unavailable for controlled collision");
        var movement = projectile.getDeltaMovement();
        require(movement.lengthSqr() > 0.0 && Double.isFinite(movement.lengthSqr()),
                "server projectile has no finite active motion");
        var direction = movement.normalize();
        var candidate = EntityType.ARMOR_STAND.create(server.overworld());
        require(candidate != null, "controlled ArmorStand target creation failed");
        candidate.setNoGravity(true);
        candidate.setPos(
                projectile.getX() + direction.x * 3.0,
                projectile.getY() - candidate.getBbHeight() * 0.5,
                projectile.getZ() + direction.z * 3.0);
        require(server.overworld().isLoaded(candidate.blockPosition()),
                "controlled collision target position is not loaded");
        targetInitialHealthBits = Float.floatToIntBits(candidate.getHealth());
        require(server.overworld().addFreshEntity(candidate),
                "controlled ArmorStand target insertion failed");
        target = candidate;
        targetArmed = true;
    }

    private static void observeServerTerminal(MinecraftServer server) {
        if (!serverImpactObserved) {
            return;
        }
        var projectile = serverProjectile;
        var exactTarget = target;
        if (projectile == null || exactTarget == null) {
            throw new IllegalStateException("terminal observation lost fixture identities");
        }
        if (!serverLeaveObserved
                || !projectile.isRemoved()
                || projectile.level().getEntity(projectile.getId()) == projectile) {
            return;
        }
        require(exactTarget.isAddedToLevel() && !exactTarget.isRemoved(),
                "unavailable damage removed the controlled target");
        targetHealthUnchanged = Float.floatToIntBits(exactTarget.getHealth())
                == targetInitialHealthBits;
        serverTerminalRemovalObserved = true;
    }

    private static void cleanupServerFixture(MinecraftServer server) {
        Throwable cleanupFailure = null;
        var exactRuntime = runtime;
        runtime = null;
        if (exactRuntime != null) {
            try {
                exactRuntime.handleRuntimeStopped(new ServerStoppedEvent(server));
            } catch (RuntimeException | Error failure) {
                cleanupFailure = failure;
            }
        }
        var exactTarget = target;
        if (exactTarget != null && !exactTarget.isRemoved()) {
            try {
                exactTarget.discard();
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
        }
        var bus = fixtureBus;
        if (bus != null) {
            try {
                bus.post(new ServerStoppedEvent(server));
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
        }
        var original = originalStore;
        if (original != null) {
            try {
                server.overworld().getDataStorage().set(STORE_NAME, original);
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
        }
        if (cleanupFailure != null) {
            asynchronousFailure = cleanupFailure;
            return;
        }
        cleanupComplete = true;
    }

    private static P5RuntimeLimits directQualificationLimits() {
        return P5RuntimeLimits.fromRequested(new P5RuntimeRequestedLimits(
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SKILL_INSTANCE,
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_PLAYER,
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER,
                MagicSafetyCeilings.MAX_ACTIVE_SKILL_INSTANCES_PER_BUDGET_ATTRIBUTION,
                MagicSafetyCeilings.MAX_ACTIVE_LINEAGES_PER_SERVER,
                MagicSafetyCeilings.MAX_ROOT_ADMISSIONS_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_PLAYER_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_SERVER_PER_TICK,
                MagicSafetyCeilings.MAX_EVENTS_PER_LINEAGE,
                MagicSafetyCeilings.MAX_DEPTH_PER_LINEAGE,
                MagicSafetyCeilings.MAX_DIRECT_CHILDREN_PER_EVENT,
                MagicSafetyCeilings.MAX_ZERO_DELAY_CHILDREN_PER_EVENT,
                MagicSafetyCeilings.MAX_DELAY_TICKS,
                MagicSafetyCeilings.MAX_DEADLINE_HORIZON_TICKS,
                MagicSafetyCeilings.MAX_CANCELLATIONS_PER_TICK));
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
                "integrated ServerPlayer is not the exact live actor");
    }

    private static void requireApplied(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome>
                    result,
            String operation) {
        require(result instanceof PlayerSkillAttachmentService.Available<?> available
                        && available.value()
                                == PlayerSkillAttachmentService.Applied.INSTANCE,
                operation + " was not applied");
    }

    private static Throwable append(Throwable primary, Throwable addition) {
        if (primary == null) {
            return addition;
        }
        if (primary != addition) {
            primary.addSuppressed(addition);
        }
        return primary;
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
        Gramarye.LOGGER.info("P9-S3-CLIENT-RUNTIME {}", marker);
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
        Gramarye.LOGGER.error("P9-S3 client runtime harness failed", failure);
        try {
            writeResult("RESULT=FAIL");
        } finally {
            minecraft.stop();
        }
    }

    private static void writeResult(String terminalResult) {
        var configured = System.getProperty("gramarye.p9s3.clientHarnessResult");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("P9-S3 client harness result path is missing");
        }
        Path result = Path.of(configured).toAbsolutePath().normalize();
        var lines = new ArrayList<String>(MARKERS.size() + 2);
        lines.add("P9-S3-CLIENT-RUNTIME-HARNESS-V1");
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
                    "P9-S3 client harness result write failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private enum Phase {
        BOOTSTRAP,
        WAIT_FOR_WORLD,
        WAIT_FOR_SERVER_SETUP,
        WAIT_FOR_SERVER_SPAWN,
        WAIT_FOR_CLIENT_RECEPTION,
        WAIT_FOR_CLIENT_RENDER,
        WAIT_FOR_HIT_TERMINAL,
        WAIT_FOR_CLIENT_REMOVAL,
        WAIT_FOR_CLEANUP,
        TERMINAL
    }
}
