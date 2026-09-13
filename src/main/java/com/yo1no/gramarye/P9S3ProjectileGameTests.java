package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Direct server-world proof for the P9-S3 projectile continuation lifecycle. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class P9S3ProjectileGameTests {
    private P9S3ProjectileGameTests() {}

    @GameTest(
            batch = "p9_s3_chain",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 240)
    @SuppressWarnings("removal")
    public static void realSpawnTransferHitAndNextDrainUseTheHeldChild(GameTestHelper helper) {
        try (var scenario = new ProductionScenario(helper, 0x9301L)) {
            var accepted = scenario.admit();
            scenario.post();

            var root = scenario.port().event(0);
            var projectileId = plannedProjectileId(accepted, root);
            var projectile = scenario.requireProjectile(projectileId);
            helper.assertTrue(
                    scenario.level().getEntity(projectileId) == projectile
                            && projectile.isAddedToLevel()
                            && !projectile.isRemoved()
                            && projectile.hasAuthenticatedCasterIdentity(scenario.actor())
                            && projectile.getOwner() == scenario.actor()
                            && close(projectile.getDeltaMovement().length(), 1.5),
                    "real P6 spawn must transfer the exact loaded entity, actor witness, and speed 1.5 launch");

            var target = scenario.addTarget(projectile.position().add(
                    projectile.getDeltaMovement().scale(0.5)));
            var healthBefore = target.getHealth();
            var sweptMovement = projectile.getDeltaMovement();
            var impact = new OneShotProjectileImpactCancellation().observeOnly();
            NeoForge.EVENT_BUS.register(impact);
            try {
                projectile.tick();
            } finally {
                NeoForge.EVENT_BUS.unregister(impact);
            }

            helper.assertTrue(
                    impact.hitTarget(target) && scenario.port().calls() == 1,
                    "the actual platform sweep must classify the exact target without invoking node 1 inline");
            helper.assertTrue(
                    scenario.level().getEntity(projectileId) == projectile
                            && projectile.isAddedToLevel()
                            && !projectile.isRemoved()
                            && projectile.getDeltaMovement().lengthSqr() == 0.0
                            && projectile.isNoGravity(),
                    "accepted claim must retain one loaded inert exact-witness projectile");

            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            helper.assertTrue(
                    scenario.port().calls() == 1
                            && scenario.level().getEntity(projectileId) == projectile,
                    "duplicate callback must remain inert without another publication");

            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 2,
                    "the held child must become eligible exactly on the next P5 drain");
            var child = scenario.port().event(1);
            assertExactHeldChild(
                    helper,
                    root,
                    child,
                    projectile,
                    target,
                    impact.hitLocation(),
                    sweptMovement,
                    scenario.port().runtimeTick(0));
            helper.assertTrue(
                    child.eventId().value() == root.eventId().value() + 1L
                            && !child.eventId().equals(accepted.eventToken().eventId()),
                    "the child must consume the held future identity, not reuse the root identity");
            helper.assertTrue(
                    projectile.isRemoved()
                            && target.getHealth() == healthBefore
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "S3 terminal consumption must remove the witness without S4 damage or P8 facts");

            projectile.tick();
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 2
                            && target.getHealth() == healthBefore
                            && scenario.runtime().cancel(
                                            server(scenario), accepted.eventToken())
                                    instanceof RuntimeCancellationResult.NotPending
                            && scenario.runtime().cancel(
                                            server(scenario), accepted.cancellationToken())
                                    instanceof RuntimeCancellationResult.NotPending
                            && scenario.enterStopping() == 0,
                    "late work must not revive, and consumed child/index/pending/instance/lease ownership must leave zero stoppable continuation work");
        }
        helper.succeed();
    }

    @GameTest(
            batch = "p9_s3_insertion",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 240)
    @SuppressWarnings("removal")
    public static void falseAndThrowingInsertionNeverOpenOrPublish(GameTestHelper helper) {
        try (var scenario = new ProductionScenario(helper, 0x9302L)) {
            var accepted = scenario.admit();
            var rootId = accepted.eventToken().eventId();
            var projectileId = new UUID(
                    ~accepted.eventToken().serverSlotToken().value(),
                    Math.addExact(rootId.value(), 1L));
            var cancellation = new OneShotProjectileJoinCancellation();
            NeoForge.EVENT_BUS.register(cancellation);
            try {
                scenario.post();
            } finally {
                NeoForge.EVENT_BUS.unregister(cancellation);
            }
            helper.assertTrue(
                    cancellation.fired()
                            && scenario.port().calls() == 1
                            && noLoadedEntity(scenario.level(), projectileId)
                            && countProjectiles(scenario.level()) == 0
                            && scenario.runtime().cancel(server(scenario), accepted.eventToken())
                                    instanceof RuntimeCancellationResult.NotPending
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "cancelled actual addFreshEntity must return false and close without OPEN or facts");
        }

        try (var scenario = new ProductionScenario(helper, 0x9303L)) {
            var accepted = scenario.admit();
            var root = accepted.eventToken().eventId();
            var projectileId = new UUID(
                    ~accepted.eventToken().serverSlotToken().value(),
                    Math.addExact(root.value(), 1L));
            var primary = new IllegalStateException("P9_S3_EXPECTED_INSERTION_RUNTIME");
            var listener = new OneShotProjectileJoinFailure(primary);
            NeoForge.EVENT_BUS.register(listener);
            RuntimeException observed = null;
            try {
                scenario.post();
            } catch (RuntimeException failure) {
                observed = failure;
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            helper.assertTrue(
                    observed == primary && listener.fired(),
                    "throwing real insertion must propagate the identical RuntimeException once");
            helper.assertTrue(
                    noLoadedEntity(scenario.level(), projectileId)
                            && countProjectiles(scenario.level()) == 0
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "throwing insertion must leave no loaded projectile, retry, or P8 fact");
        }
        helper.succeed();
    }

    @GameTest(
            batch = "p9_s3_impacts",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 360)
    @SuppressWarnings("removal")
    public static void cancelledSweepContinuesAndInvalidImpactsTerminal(GameTestHelper helper) {
        try (var scenario = new ProductionScenario(helper, 0x930BL)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addDeflectingTarget(projectile.position().add(
                    projectile.getDeltaMovement().scale(0.5)));
            helper.assertTrue(
                    target.deflection(projectile) != ProjectileDeflection.NONE,
                    "the real living target must expose a platform deflection policy");
            var positionBefore = projectile.position();
            var movementBefore = projectile.getDeltaMovement();
            var cancellation = new OneShotProjectileImpactCancellation();
            NeoForge.EVENT_BUS.register(cancellation);
            try {
                projectile.tick();
            } finally {
                NeoForge.EVENT_BUS.unregister(cancellation);
            }
            helper.assertTrue(
                    cancellation.fired()
                            && cancellation.hitTarget(target)
                            && !projectile.isRemoved()
                            && close(projectile.position(), positionBefore.add(movementBefore))
                            && close(
                                    projectile.getDeltaMovement(),
                                    movementBefore.scale(0.99).add(0.0, -0.03, 0.0))
                            && scenario.port().calls() == 1,
                    "hook-first entity sweep cancellation must preserve direct air drag, gravity, and active flight");

            target.setPos(projectile.position().add(
                    projectile.getDeltaMovement().scale(0.5)).subtract(0.0, 0.5, 0.0));
            projectile.tick();
            helper.assertTrue(
                    !projectile.isRemoved()
                            && projectile.getDeltaMovement().lengthSqr() == 0.0
                            && projectile.isNoGravity()
                            && scenario.port().calls() == 1,
                    "first later noncancelled sweep must classify even a deflecting living target as the claim and become inert");
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 2 && projectile.isRemoved(),
                    "cancel-then-claim path must consume only the one held child next drain");
        }

        exerciseActualBlockSweep(helper, 0x9315L);
        exerciseOwnerGraceAndReturnToOwnerTerminal(helper, 0x9318L);
        for (var impact : TerminalImpact.values()) {
            exerciseTerminalImpact(helper, 0x9310L + impact.ordinal(), impact);
        }
        helper.succeed();
    }

    @GameTest(
            batch = "p9_s3_transfer",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 260)
    @SuppressWarnings("removal")
    public static void reservedClaimAndWrongTransferWitnessesAreOneShot(GameTestHelper helper) {
        exerciseWrongLoadedObjectTransfer(helper, 0x9304L);
        exerciseSameUuidActorReplacementBeforeTransfer(helper, 0x9305L);
        helper.succeed();
    }

    @GameTest(
            batch = "p9_s3_lifecycle",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 420)
    @SuppressWarnings("removal")
    public static void replacementRemovalAndDeadlineCloseWithoutDamage(GameTestHelper helper) {
        exerciseOpenActorReplacement(helper, 0x9306L);
        exerciseClaimedActorReplacement(helper, 0x9307L);
        exerciseClaimedEntityRemoval(helper, 0x9308L);
        exerciseOpenDeadline(helper, 0x9309L);
        exerciseOpenReload(helper, 0x9314L);
        exerciseExactRangeTerminal(helper, 0x9316L);
        exerciseAgeTerminalBeforeSweep(helper, 0x9317L);
        helper.succeed();
    }

    @GameTest(
            batch = "p9_s3_cap",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 320)
    @SuppressWarnings("removal")
    public static void sixteenthOpenPermitIsThePerPlayerMaximum(GameTestHelper helper) {
        try (var scenario = new ProductionScenario(helper, 0x930AL)) {
            for (var index = 0; index < 17; index++) {
                scenario.admit();
            }
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 17,
                    "all admitted roots must reach the production adapter exactly once");
            var projectiles = projectiles(scenario.level());
            helper.assertTrue(
                    projectiles.size() == 16
                            && projectiles.stream().allMatch(projectile ->
                                    projectile.isAddedToLevel()
                                            && !projectile.isRemoved()
                                            && projectile.hasAuthenticatedCasterIdentity(
                                                    scenario.actor())),
                    "the seventeenth same-player continuation must spawn nothing beyond cap 16");
            helper.assertTrue(
                    scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "the cap rejection and sixteen OPEN transfers must emit no S4 facts");
            var closedWorkUnits = scenario.enterStopping();
            helper.assertTrue(
                    closedWorkUnits == 16
                            && projectiles.stream().filter(Entity::isRemoved).count() == 16L,
                    "bounded server-stop cleanup must report and terminate exactly 16 transfers");
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static void exerciseWrongLoadedObjectTransfer(
            GameTestHelper helper, long fixtureId) {
        var server = helper.getLevel().getServer();
        var actor = helper.makeMockServerPlayerInLevel();
        P7S4LoginManaGameTests.P9GameTestFixture fixture = null;
        SkillRuntimeService runtime = null;
        Throwable primary = null;
        try {
            fixture = P7S4LoginManaGameTests.openP9GameTestFixture(
                    helper, actor, fixtureId);
            var geometry = fixture.geometry();
            var target = new ArmorStand(
                    actor.serverLevel(), actor.getX() + 1.0, actor.getY(), actor.getZ());
            helper.assertTrue(actor.serverLevel().addFreshEntity(target),
                    "pre-transfer claim control requires one real loaded living target");
            var port = new WrongLoadedObjectTransferPort(
                    helper, server, actor, geometry, target);
            runtime = fixture.startRuntime(port);
            assertAccepted(helper, runtime.admitAuthenticatedPlayerCast(
                    server, actor, fixture.reference(), geometry));
            runtime.handleRuntimePost(post(server));
            helper.assertTrue(port.completed(),
                    "wrong-object transfer control must complete inside the root dispatch");
            target.discard();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            cleanupManual(server, actor.getUUID(), runtime, fixture, primary);
        }
    }

    @SuppressWarnings("removal")
    private static void exerciseSameUuidActorReplacementBeforeTransfer(
            GameTestHelper helper, long fixtureId) {
        var server = helper.getLevel().getServer();
        var actor = helper.makeMockServerPlayerInLevel();
        P7S4LoginManaGameTests.P9GameTestFixture fixture = null;
        SkillRuntimeService runtime = null;
        Throwable primary = null;
        try {
            fixture = P7S4LoginManaGameTests.openP9GameTestFixture(
                    helper, actor, fixtureId);
            var geometry = fixture.geometry();
            var port = new ReplacedActorTransferPort(helper, server, actor, geometry);
            runtime = fixture.startRuntime(port);
            assertAccepted(helper, runtime.admitAuthenticatedPlayerCast(
                    server, actor, fixture.reference(), geometry));
            runtime.handleRuntimePost(post(server));
            helper.assertTrue(
                    port.completed()
                            && port.replacement() != actor
                            && port.replacement().getUUID().equals(actor.getUUID())
                            && server.getPlayerList().getPlayer(actor.getUUID())
                                    == port.replacement(),
                    "same-UUID replacement must be real and rejected before transfer");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            cleanupManual(server, actor.getUUID(), runtime, fixture, primary);
        }
    }

    private static void exerciseOpenActorReplacement(GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var replacement = scenario.replaceActor();
            scenario.post();
            helper.assertTrue(
                    replacement != scenario.actor()
                            && projectile.isRemoved()
                            && scenario.port().calls() == 1,
                    "same-UUID replacement while OPEN must close without child execution");
        }
    }

    private static void exerciseActualBlockSweep(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var blockPosition = BlockPos.containing(projectile.position()).east(2);
            projectile.setDeltaMovement(
                    Vec3.atCenterOf(blockPosition).subtract(projectile.position()));
            helper.assertTrue(
                    scenario.level().setBlockAndUpdate(
                            blockPosition, Blocks.STONE.defaultBlockState()),
                    "block-sweep control must install one real collidable block");
            var observation = new OneShotProjectileImpactCancellation().observeOnly();
            NeoForge.EVENT_BUS.register(observation);
            try {
                projectile.tick();
            } finally {
                NeoForge.EVENT_BUS.unregister(observation);
                scenario.level().setBlockAndUpdate(
                        blockPosition, Blocks.AIR.defaultBlockState());
            }
            scenario.post();
            helper.assertTrue(
                    observation.fired()
                            && observation.hitType() == HitResult.Type.BLOCK
                            && projectile.isRemoved()
                            && scenario.port().calls() == 1
                            && scenario.runtime().cancel(
                                            server(scenario), accepted.cancellationToken())
                                    instanceof RuntimeCancellationResult.NotPending,
                    "one uncancelled platform block sweep must dispatch directly to terminal block handling without a child");
        }
    }

    private static void exerciseOwnerGraceAndReturnToOwnerTerminal(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var actor = scenario.actor();
            var initialPosition = projectile.position();
            var initialMovement = projectile.getDeltaMovement();
            var ownerEnvelope = projectile.getBoundingBox()
                    .expandTowards(initialMovement)
                    .inflate(1.0);
            helper.assertTrue(
                    actor.isPickable()
                            && actor.canBeHitByProjectile()
                            && ownerEnvelope.intersects(actor.getBoundingBox()),
                    "owner-grace control must begin inside the locked platform owner envelope");

            var graceObservation = new OneShotProjectileImpactCancellation().observeOnly();
            NeoForge.EVENT_BUS.register(graceObservation);
            try {
                projectile.tick();
            } finally {
                NeoForge.EVENT_BUS.unregister(graceObservation);
            }
            helper.assertTrue(
                    !graceObservation.fired()
                            && !projectile.isRemoved()
                            && close(projectile.position(), initialPosition.add(initialMovement))
                            && scenario.port().calls() == 1,
                    "the initial owner-overlapping sweep must retain platform owner grace and active flight");

            var actorEye = actor.getEyePosition();
            var departureStart = actorEye.add(4.0, 0.0, 0.0);
            projectile.setPos(departureStart);
            projectile.setDeltaMovement(0.25, 0.0, 0.0);
            projectile.tick();
            helper.assertTrue(
                    !projectile.isRemoved()
                            && close(
                                    projectile.position(),
                                    departureStart.add(0.25, 0.0, 0.0))
                            && scenario.port().calls() == 1,
                    "a sweep wholly outside the owner envelope must transition the inherited leftOwner state");

            var returnStart = actorEye.add(3.0, 0.0, 0.0);
            projectile.setPos(returnStart);
            projectile.setDeltaMovement(actorEye.subtract(returnStart));
            var returnObservation = new OneShotProjectileImpactCancellation().observeOnly();
            NeoForge.EVENT_BUS.register(returnObservation);
            try {
                projectile.tick();
            } finally {
                NeoForge.EVENT_BUS.unregister(returnObservation);
            }
            helper.assertTrue(
                    returnObservation.hitTarget(actor)
                            && projectile.isRemoved()
                            && scenario.port().calls() == 1,
                    "after leftOwner, the real return sweep must admit the owner and classify it as terminal self collision");
            scenario.post();
            helper.assertTrue(
                    scenario.runtime().cancel(server(scenario), accepted.eventToken())
                                    instanceof RuntimeCancellationResult.NotPending
                            && scenario.runtime().cancel(
                                            server(scenario), accepted.cancellationToken())
                                    instanceof RuntimeCancellationResult.NotPending
                            && scenario.presentation().bufferedEventsForTesting().isEmpty()
                            && scenario.enterStopping() == 0,
                    "owner/self terminal classification must publish no child or presentation and retain no continuation work");
        }
    }

    private static void exerciseTerminalImpact(
            GameTestHelper helper, long fixtureId, TerminalImpact impact) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            switch (impact) {
                case BLOCK -> projectile.onHitBlock(new BlockHitResult(
                        projectile.position(),
                        Direction.UP,
                        BlockPos.containing(projectile.position()),
                        false));
                case SELF -> projectile.onHitEntity(new EntityHitResult(
                        scenario.actor(), projectile.position()));
                case NONLIVING -> {
                    var item = scenario.addNonliving(projectile.position());
                    projectile.onHitEntity(new EntityHitResult(item, projectile.position()));
                }
                case UNLOADED_LIVING -> {
                    var unloaded = new ArmorStand(
                            scenario.level(),
                            projectile.getX(),
                            projectile.getY(),
                            projectile.getZ());
                    projectile.onHitEntity(new EntityHitResult(
                            unloaded, projectile.position()));
                }
            }
            scenario.post();
            helper.assertTrue(
                    projectile.isRemoved()
                            && scenario.port().calls() == 1
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "terminal impact " + impact
                            + " must close without child, damage, or P8 fact");
        }
    }

    private static void exerciseClaimedActorReplacement(GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var health = target.getHealth();
            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            scenario.replaceActor();
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 1
                            && projectile.isRemoved()
                            && target.getHealth() == health,
                    "claim-to-next-drain actor replacement must terminal without P6 damage");
        }
    }

    private static void exerciseClaimedEntityRemoval(GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var health = target.getHealth();
            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            projectile.discard();
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 1
                            && projectile.isRemoved()
                            && target.getHealth() == health,
                    "claimed witness removal before drain must cancel the child without damage");
        }
    }

    private static void exerciseOpenDeadline(GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            for (var elapsed = 0; elapsed < 99; elapsed++) {
                scenario.post();
            }
            helper.assertTrue(
                    !projectile.isRemoved() && scenario.port().calls() == 1,
                    "OPEN must remain live while the P5 runtime tick is strictly before deadline");
            scenario.post();
            helper.assertTrue(
                    projectile.isRemoved()
                            && scenario.port().calls() == 1
                            && scenario.runtime().cancel(
                                            server(scenario), accepted.cancellationToken())
                                    instanceof RuntimeCancellationResult.NotPending
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "deadline-equality sweep must close and release the exact instance/lease");
        }
    }

    private static void exerciseOpenReload(GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            scenario.runtime().requestP9ReloadInvalidation();
            helper.assertTrue(
                    scenario.runtime().admitAuthenticatedPlayerCast(
                                    scenario.level().getServer(),
                                    scenario.actor(),
                                    scenario.fixture().reference(),
                                    scenario.fixture().geometry())
                            instanceof RuntimeAdmissionResult.ServerStopping,
                    "reload close-request must reject new P9 work before cleanup");
            scenario.runtime().completeP9Reload(scenario.level().getServer());
            scenario.post();
            helper.assertTrue(
                    projectile.isRemoved()
                            && scenario.port().calls() == 1
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "reload completion must close OPEN once without child, damage, or P8 fact");
        }
    }

    private static void exerciseExactRangeTerminal(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            helper.assertTrue(
                    close(projectile.getDeltaMovement().length(), 1.5),
                    "range control must first observe the exact production launch speed");
            var origin = projectile.position();
            projectile.setDeltaMovement(0.0, 63.75, 0.0);
            projectile.tick();
            var beforeFinalSegment = projectile.position();
            helper.assertTrue(
                    !projectile.isRemoved()
                            && close(beforeFinalSegment, origin.add(0.0, 63.75, 0.0)),
                    "flight must remain OPEN immediately before the exact final range segment");
            var fluidCell = BlockPos.containing(beforeFinalSegment);
            var below = fluidCell.below();
            var west = fluidCell.west();
            var north = fluidCell.north();
            var south = fluidCell.south();
            var east = fluidCell.east();
            var eastBelow = east.below();
            var fluidBefore = scenario.level().getBlockState(fluidCell);
            var belowBefore = scenario.level().getBlockState(below);
            var westBefore = scenario.level().getBlockState(west);
            var northBefore = scenario.level().getBlockState(north);
            var southBefore = scenario.level().getBlockState(south);
            var eastBefore = scenario.level().getBlockState(east);
            var eastBelowBefore = scenario.level().getBlockState(eastBelow);
            try {
                scenario.level().setBlockAndUpdate(fluidCell, Blocks.WATER.defaultBlockState());
                scenario.level().setBlockAndUpdate(below, Blocks.STONE.defaultBlockState());
                scenario.level().setBlockAndUpdate(west, Blocks.STONE.defaultBlockState());
                scenario.level().setBlockAndUpdate(north, Blocks.STONE.defaultBlockState());
                scenario.level().setBlockAndUpdate(south, Blocks.STONE.defaultBlockState());
                scenario.level().setBlockAndUpdate(
                        east,
                        Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
                scenario.level().setBlockAndUpdate(
                        eastBelow, Blocks.STONE.defaultBlockState());

                var movementBeforeBaseTick = projectile.getDeltaMovement();
                projectile.tick();
                var finalDisplacement = projectile.position().subtract(beforeFinalSegment);
                helper.assertTrue(
                        movementBeforeBaseTick.x == 0.0
                                && StrictMath.abs(finalDisplacement.x) > 1.0E-7
                                && close(finalDisplacement.length(), 0.25)
                                && projectile.isRemoved()
                                && scenario.port().calls() == 1
                                && scenario.runtime().cancel(
                                                server(scenario), accepted.eventToken())
                                        instanceof RuntimeCancellationResult.NotPending
                                && scenario.enterStopping() == 0,
                        "the second sweep must consume the post-baseTick flowing-water vector, clamp its actual displacement to 0.25, terminate at accumulated range 64, and release all continuation work");
            } finally {
                scenario.level().setBlockAndUpdate(fluidCell, fluidBefore);
                scenario.level().setBlockAndUpdate(below, belowBefore);
                scenario.level().setBlockAndUpdate(west, westBefore);
                scenario.level().setBlockAndUpdate(north, northBefore);
                scenario.level().setBlockAndUpdate(south, southBefore);
                scenario.level().setBlockAndUpdate(east, eastBefore);
                scenario.level().setBlockAndUpdate(eastBelow, eastBelowBefore);
            }
        }
    }

    private static void exerciseAgeTerminalBeforeSweep(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addDeflectingTarget(projectile.position().add(
                    projectile.getDeltaMovement().scale(0.5)));
            projectile.tickCount = 99;
            var hundredthSweep = new OneShotProjectileImpactCancellation();
            NeoForge.EVENT_BUS.register(hundredthSweep);
            try {
                scenario.level().tickNonPassenger(projectile);
            } finally {
                NeoForge.EVENT_BUS.unregister(hundredthSweep);
            }
            helper.assertTrue(
                    hundredthSweep.hitTarget(target)
                            && target.isAlive()
                            && !projectile.isRemoved()
                            && scenario.port().calls() == 1,
                    "platform age 100 must retain the one-hundredth legal cancelled sweep");

            target.setPos(projectile.position().add(
                    projectile.getDeltaMovement().scale(0.5)));
            var position = projectile.position();
            var observation = new OneShotProjectileImpactCancellation().observeOnly();
            NeoForge.EVENT_BUS.register(observation);
            try {
                scenario.level().tickNonPassenger(projectile);
            } finally {
                NeoForge.EVENT_BUS.unregister(observation);
            }
            helper.assertTrue(
                    !observation.fired()
                            && target.isAlive()
                            && projectile.isRemoved()
                            && close(projectile.position(), position)
                            && scenario.port().calls() == 1
                            && scenario.runtime().cancel(
                                            server(scenario), accepted.cancellationToken())
                                    instanceof RuntimeCancellationResult.NotPending
                            && scenario.enterStopping() == 0,
                    "platform age 101 must terminate after exactly 100 eligible sweeps and before further movement, publication, or retained continuation work");
        }
    }

    private static void assertExactHeldChild(
            GameTestHelper helper,
            RuntimeEvent root,
            RuntimeEvent child,
            P9StarterProjectile projectile,
            ArmorStand target,
            Vec3 hitLocation,
            Vec3 sweptMovement,
            long claimRuntimeTick) {
        helper.assertTrue(
                child.skillInstanceId().equals(root.skillInstanceId())
                        && child.skillInstanceSequence().equals(root.skillInstanceSequence())
                        && child.cancellationToken().equals(root.cancellationToken())
                        && child.parentEventId().equals(Optional.of(root.eventId()))
                        && child.skillReference().equals(root.skillReference())
                        && child.nodeIndex() == 1
                        && child.createdRuntimeTick() == claimRuntimeTick
                        && child.scheduledRuntimeTick() == claimRuntimeTick
                        && child.deadlineRuntimeTick() == root.deadlineRuntimeTick()
                        && child.depth() == 1
                        && child.childSequence() == 1
                        && child.persistence() == RuntimeSchedulePersistence.MEMORY_ONLY
                        && child.budgetAttribution().equals(root.budgetAttribution())
                        && child.origin().equals(root.origin())
                        && child.triggerCause() instanceof ChildTriggerCause,
                "queued node 1 must preserve exact instance/reference/lease lineage coordinates");
        helper.assertTrue(
                child.target().orElseThrow() instanceof EntityTarget entityTarget
                        && entityTarget.entity().value().equals(target.getUUID())
                        && entityTarget.dimension().equals(target.level().dimension())
                        && entityTarget.expectedKind() == RuntimeEntityKind.LIVING_ENTITY,
                "queued node 1 must bind the exact loaded living target");
        helper.assertTrue(
                child.executionData() instanceof ProjectileHitExecutionDataV0 hit
                        && hit.projectileId().equals(projectile.getUUID())
                        && hit.targetId().equals(target.getUUID())
                        && hit.dimension().equals(projectile.level().dimension().location())
                        && sameDouble(hit.hitX(), hitLocation.x)
                        && sameDouble(hit.hitY(), hitLocation.y)
                        && sameDouble(hit.hitZ(), hitLocation.z)
                        && hit.directionXQ15() == q15(sweptMovement.x, sweptMovement.length())
                        && hit.directionYQ15() == q15(sweptMovement.y, sweptMovement.length())
                        && hit.directionZQ15() == q15(sweptMovement.z, sweptMovement.length())
                        && hit.sourceDerivationDepth() == 0
                        && hit.sourceFamily().skillInstanceId().equals(root.skillInstanceId())
                        && hit.sourceFamily().sourceEventId().equals(root.eventId())
                        && hit.sourceFamily().producerNodeIndex() == 0
                        && hit.sourceFamily().outputOrdinal() == 0,
                "P5 must construct the exact direct-source hit execution data");
    }

    private static RuntimeAdmissionResult.AcceptedMemoryOnly assertAccepted(
            GameTestHelper helper, RuntimeAdmissionResult admission) {
        helper.assertTrue(
                admission instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                "controlled P9 root must be admitted by the real P5 owner");
        return (RuntimeAdmissionResult.AcceptedMemoryOnly) admission;
    }

    private static UUID plannedProjectileId(
            RuntimeAdmissionResult.AcceptedMemoryOnly accepted, RuntimeEvent root) {
        if (!root.eventId().equals(accepted.eventToken().eventId())) {
            throw new AssertionError("recorded root event does not match admission identity");
        }
        return new UUID(
                ~accepted.eventToken().serverSlotToken().value(),
                Math.addExact(root.eventId().value(), 1L));
    }

    private static ServerTickEvent.Post post(MinecraftServer server) {
        return new ServerTickEvent.Post(() -> true, server);
    }

    private static MinecraftServer server(ProductionScenario scenario) {
        return scenario.level().getServer();
    }

    private static List<P9StarterProjectile> projectiles(ServerLevel level) {
        var result = new ArrayList<P9StarterProjectile>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof P9StarterProjectile projectile && !projectile.isRemoved()) {
                result.add(projectile);
            }
        }
        return List.copyOf(result);
    }

    private static int countProjectiles(ServerLevel level) {
        return projectiles(level).size();
    }

    private static boolean noLoadedEntity(ServerLevel level, UUID entityId) {
        var entity = level.getEntity(entityId);
        return entity == null || entity.isRemoved();
    }

    private static boolean close(double actual, double expected) {
        return Double.isFinite(actual)
                && Double.isFinite(expected)
                && StrictMath.abs(actual - expected) <= 1.0E-9;
    }

    private static boolean close(Vec3 actual, Vec3 expected) {
        return close(actual.x, expected.x)
                && close(actual.y, expected.y)
                && close(actual.z, expected.z);
    }

    private static boolean sameDouble(double actual, double expected) {
        return Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected);
    }

    private static int q15(double component, double length) {
        var encoded = StrictMath.rint(component / length * 32_767.0);
        return (int) Math.max(-32_767.0, Math.min(32_767.0, encoded));
    }

    private static void cleanupManual(
            MinecraftServer server,
            UUID actorId,
            SkillRuntimeService runtime,
            P7S4LoginManaGameTests.P9GameTestFixture fixture,
            Throwable primary) {
        Throwable cleanup = null;
        if (runtime != null) {
            try {
                runtime.handleRuntimeStopped(new ServerStoppedEvent(server));
            } catch (RuntimeException | Error failure) {
                cleanup = failure;
            }
        }
        try {
            removeCurrentPlayer(server, actorId);
        } catch (RuntimeException | Error failure) {
            cleanup = append(cleanup, failure);
        }
        if (fixture != null) {
            try {
                fixture.close();
            } catch (RuntimeException | Error failure) {
                cleanup = append(cleanup, failure);
            }
        }
        if (cleanup != null) {
            if (primary != null) {
                if (cleanup != primary) {
                    primary.addSuppressed(cleanup);
                }
            } else {
                rethrow(cleanup);
            }
        }
    }

    private static void removeCurrentPlayer(MinecraftServer server, UUID actorId) {
        var current = server.getPlayerList().getPlayer(actorId);
        if (current != null) {
            server.getPlayerList().remove(current);
        }
    }

    private static Throwable append(Throwable current, Throwable additional) {
        if (current == null) {
            return additional;
        }
        if (current != additional) {
            current.addSuppressed(additional);
        }
        return current;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("unexpected checked P9 GameTest failure", failure);
    }

    private static final class ProductionScenario implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final UUID actorId;
        private final ServerPlayer actor;
        private final P7S4LoginManaGameTests.P9GameTestFixture fixture;
        private final P8ServerPresentationService presentation;
        private final RecordingProductionPort port;
        private final List<Entity> controlledEntities = new ArrayList<>();
        private SkillRuntimeService runtime;

        @SuppressWarnings("removal")
        private ProductionScenario(GameTestHelper helper, long fixtureId) {
            this.helper = Objects.requireNonNull(helper, "helper");
            level = helper.getLevel();
            actor = helper.makeMockServerPlayerInLevel();
            actorId = actor.getUUID();
            P7S4LoginManaGameTests.P9GameTestFixture openedFixture = null;
            try {
                openedFixture = P7S4LoginManaGameTests.openP9GameTestFixture(
                        helper, actor, fixtureId);
                fixture = openedFixture;
                presentation = P8ServerPresentationService.create();
                port = new RecordingProductionPort(presentation);
                runtime = fixture.startRuntime(port);
            } catch (RuntimeException | Error failure) {
                if (openedFixture != null) {
                    try {
                        openedFixture.close();
                    } catch (RuntimeException | Error cleanup) {
                        if (cleanup != failure) {
                            failure.addSuppressed(cleanup);
                        }
                    }
                }
                removeCurrentPlayer(level.getServer(), actorId);
                throw failure;
            }
        }

        private RuntimeAdmissionResult.AcceptedMemoryOnly admit() {
            return assertAccepted(helper, runtime.admitAuthenticatedPlayerCast(
                    level.getServer(), actor, fixture.reference(), fixture.geometry()));
        }

        private void post() {
            runtime.handleRuntimePost(P9S3ProjectileGameTests.post(level.getServer()));
        }

        private P9StarterProjectile requireProjectile(UUID projectileId) {
            var entity = level.getEntity(projectileId);
            helper.assertTrue(entity instanceof P9StarterProjectile,
                    "planned UUID must resolve to the production projectile type");
            return (P9StarterProjectile) entity;
        }

        private ArmorStand addTarget(Vec3 position) {
            var target = new ArmorStand(level, position.x, position.y - 0.5, position.z);
            helper.assertTrue(level.addFreshEntity(target),
                    "controlled living target must be inserted into the actual ServerLevel");
            controlledEntities.add(target);
            return target;
        }

        private Breeze addDeflectingTarget(Vec3 position) {
            var target = new Breeze(EntityType.BREEZE, level);
            target.setNoAi(true);
            target.setPos(position.x, position.y - 0.5, position.z);
            helper.assertTrue(level.addFreshEntity(target),
                    "controlled deflecting living target must be inserted into the actual ServerLevel");
            controlledEntities.add(target);
            return target;
        }

        private ItemEntity addNonliving(Vec3 position) {
            var item = new ItemEntity(
                    level,
                    position.x,
                    position.y,
                    position.z,
                    new ItemStack(Items.STONE));
            helper.assertTrue(level.addFreshEntity(item),
                    "controlled nonliving target must be loaded in the actual ServerLevel");
            controlledEntities.add(item);
            return item;
        }

        private ServerPlayer replaceActor() {
            var current = level.getServer().getPlayerList().getPlayer(actorId);
            helper.assertTrue(current != null,
                    "same-UUID replacement control requires the current actor");
            var replacement = level.getServer().getPlayerList().respawn(
                    current, false, Entity.RemovalReason.KILLED);
            replacement.connection.player = replacement;
            helper.assertTrue(
                    replacement != current
                            && replacement.getUUID().equals(actorId)
                            && level.getServer().getPlayerList().getPlayer(actorId)
                                    == replacement,
                    "platform respawn must install a distinct same-UUID actor");
            return replacement;
        }

        private void stop() {
            if (runtime != null) {
                var stopping = runtime;
                runtime = null;
                stopping.handleRuntimeStopped(new ServerStoppedEvent(level.getServer()));
            }
        }

        private int enterStopping() {
            return runtime().enterStoppingForTesting(level.getServer());
        }

        private SkillRuntimeService runtime() {
            return Objects.requireNonNull(runtime, "runtime");
        }

        private ServerPlayer actor() {
            return actor;
        }

        private ServerLevel level() {
            return level;
        }

        private P8ServerPresentationService presentation() {
            return presentation;
        }

        private P7S4LoginManaGameTests.P9GameTestFixture fixture() {
            return fixture;
        }

        private RecordingProductionPort port() {
            return port;
        }

        @Override
        public void close() {
            Throwable failure = null;
            try {
                stop();
            } catch (RuntimeException | Error cleanup) {
                failure = cleanup;
            }
            for (var entity : controlledEntities) {
                try {
                    if (!entity.isRemoved()) {
                        entity.discard();
                    }
                } catch (RuntimeException | Error cleanup) {
                    failure = append(failure, cleanup);
                }
            }
            try {
                removeCurrentPlayer(level.getServer(), actorId);
            } catch (RuntimeException | Error cleanup) {
                failure = append(failure, cleanup);
            }
            try {
                fixture.close();
            } catch (RuntimeException | Error cleanup) {
                failure = append(failure, cleanup);
            }
            if (failure != null) {
                rethrow(failure);
            }
        }
    }

    private static final class RecordingProductionPort implements RuntimeExecutionPort {
        private final P6RuntimeExecutionPortAdapter delegate;
        private final List<RuntimeEvent> events = new ArrayList<>();
        private final List<Long> runtimeTicks = new ArrayList<>();

        private RecordingProductionPort(P8ServerPresentationService presentation) {
            delegate = new P6RuntimeExecutionPortAdapter(
                    P6RuntimeExecutionCapability.forRuntimeAdapter(), presentation);
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            events.add(event);
            runtimeTicks.add(context.currentRuntimeTick());
            return delegate.execute(event, context);
        }

        private int calls() {
            return events.size();
        }

        private RuntimeEvent event(int index) {
            return events.get(index);
        }

        private long runtimeTick(int index) {
            return runtimeTicks.get(index);
        }
    }

    private static final class WrongLoadedObjectTransferPort implements RuntimeExecutionPort {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final ServerPlayer actor;
        private final CastGeometryExecutionDataV0 geometry;
        private final ArmorStand target;
        private boolean completed;

        private WrongLoadedObjectTransferPort(
                GameTestHelper helper,
                MinecraftServer server,
                ServerPlayer actor,
                CastGeometryExecutionDataV0 geometry,
                ArmorStand target) {
            this.helper = helper;
            this.server = server;
            this.actor = actor;
            this.geometry = geometry;
            this.target = target;
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            var opened = requireOpened(context);
            var permit = opened.permit();
            var candidate = new ProjectileHitCandidateV0(
                    opened.plannedProjectileId(),
                    target.getUUID(),
                    geometry.dimension(),
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    32_767,
                    0,
                    0);
            helper.assertTrue(
                    permit.state == RuntimeProjectileContinuationPermit.State.RESERVED
                            && permit.claimLoadedEntityHit(server, candidate)
                                    == RuntimePermitClaimDisposition.REJECTED
                            && permit.state
                                    == RuntimeProjectileContinuationPermit.State.RESERVED,
                    "claim before real transfer must reject with zero permit mutation");

            var loaded = new P9StarterProjectile(
                    P9StarterProjectileRegistration.type(),
                    actor.serverLevel(),
                    actor,
                    opened,
                    geometry);
            var wrongObject = new P9StarterProjectile(
                    P9StarterProjectileRegistration.type(),
                    actor.serverLevel(),
                    actor,
                    opened,
                    geometry);
            helper.assertTrue(actor.serverLevel().addFreshEntity(loaded)
                            && actor.serverLevel().getEntity(opened.plannedProjectileId())
                                    == loaded,
                    "wrong-object transfer control requires the exact planned entity loaded");
            helper.assertTrue(
                    opened.transferAfterAppliedSpawn(server, wrongObject)
                                    == RuntimePermitTransferDisposition.REJECTED
                            && permit.state
                                    == RuntimeProjectileContinuationPermit.State.RESERVED
                            && opened.transferAfterAppliedSpawn(server, loaded)
                                    == RuntimePermitTransferDisposition.ALREADY_TRANSFERRED,
                    "a rejected wrong-object transfer must consume the one-shot aggregate only");
            helper.assertTrue(
                    permit.closeWithoutHit(server, ProjectileClosureReason.SPAWN_NOT_APPLIED)
                                    == RuntimePermitCloseDisposition.CLOSED
                            && permit.state
                                    == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                            && permit.claimLoadedEntityHit(server, candidate)
                                    == RuntimePermitClaimDisposition.DUPLICATE_OR_LATE
                            && permit.closeWithoutHit(
                                            server, ProjectileClosureReason.SPAWN_NOT_APPLIED)
                                    == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                    "terminal rejected transfer must remain idempotent and nonpublishing");
            loaded.discard();
            wrongObject.discard();
            completed = true;
            return P6RuntimeExecutionPortAdapter.completedEmpty();
        }

        private boolean completed() {
            return completed;
        }
    }

    private static final class ReplacedActorTransferPort implements RuntimeExecutionPort {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final ServerPlayer original;
        private final CastGeometryExecutionDataV0 geometry;
        private ServerPlayer replacement;
        private boolean completed;

        private ReplacedActorTransferPort(
                GameTestHelper helper,
                MinecraftServer server,
                ServerPlayer original,
                CastGeometryExecutionDataV0 geometry) {
            this.helper = helper;
            this.server = server;
            this.original = original;
            this.geometry = geometry;
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            var opened = requireOpened(context);
            replacement = server.getPlayerList().respawn(
                    original, false, Entity.RemovalReason.KILLED);
            replacement.connection.player = replacement;
            helper.assertTrue(
                    replacement != original
                            && replacement.getUUID().equals(original.getUUID())
                            && server.getPlayerList().getPlayer(original.getUUID()) == replacement,
                    "transfer replacement control requires actual same-UUID actor B");
            var projectile = new P9StarterProjectile(
                    P9StarterProjectileRegistration.type(),
                    replacement.serverLevel(),
                    replacement,
                    opened,
                    geometry);
            helper.assertTrue(replacement.serverLevel().addFreshEntity(projectile),
                    "replacement-witness control requires the exact planned entity loaded");
            helper.assertTrue(
                    opened.transferAfterAppliedSpawn(server, projectile)
                                    == RuntimePermitTransferDisposition.REJECTED
                            && opened.permit().state
                                    == RuntimeProjectileContinuationPermit.State.RESERVED
                            && opened.transferAfterAppliedSpawn(server, projectile)
                                    == RuntimePermitTransferDisposition.ALREADY_TRANSFERRED,
                    "same-UUID actor B must not replace actor A or receive a transfer retry");
            helper.assertTrue(
                    opened.permit().closeWithoutHit(
                                    server, ProjectileClosureReason.OWNER_INVALIDATED)
                            == RuntimePermitCloseDisposition.CLOSED,
                    "P5 must retain responsibility for closing the rejected RESERVED permit");
            projectile.discard();
            completed = true;
            return P6RuntimeExecutionPortAdapter.completedEmpty();
        }

        private ServerPlayer replacement() {
            return replacement;
        }

        private boolean completed() {
            return completed;
        }
    }

    private static RuntimeProjectileContinuationOpenResult.Opened requireOpened(
            RuntimeExecutionContext context) {
        var result = context.projectileContinuationOpener()
                .openProjectileContinuation(ActionOutputKind.PROJECTILE, 0);
        if (result instanceof RuntimeProjectileContinuationOpenResult.Opened opened) {
            return opened;
        }
        throw new AssertionError("real root dispatch did not produce a RESERVED continuation");
    }

    private static final class OneShotProjectileJoinFailure {
        private final RuntimeException failure;
        private boolean fired;

        private OneShotProjectileJoinFailure(RuntimeException failure) {
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        @SubscribeEvent
        public void onEntityJoin(EntityJoinLevelEvent event) {
            if (!fired && event.getEntity() instanceof P9StarterProjectile) {
                fired = true;
                throw failure;
            }
        }

        private boolean fired() {
            return fired;
        }
    }

    private static final class OneShotProjectileJoinCancellation {
        private boolean fired;

        @SubscribeEvent
        public void onEntityJoin(EntityJoinLevelEvent event) {
            if (!fired && event.getEntity() instanceof P9StarterProjectile) {
                fired = true;
                event.setCanceled(true);
            }
        }

        private boolean fired() {
            return fired;
        }
    }

    private static final class OneShotProjectileImpactCancellation {
        private boolean cancel = true;
        private boolean fired;
        private HitResult hit;

        @SubscribeEvent
        public void onProjectileImpact(ProjectileImpactEvent event) {
            if (!fired && event.getProjectile() instanceof P9StarterProjectile) {
                fired = true;
                hit = event.getRayTraceResult();
                if (cancel) {
                    event.setCanceled(true);
                }
            }
        }

        private OneShotProjectileImpactCancellation observeOnly() {
            cancel = false;
            return this;
        }

        private boolean fired() {
            return fired;
        }

        private HitResult.Type hitType() {
            return hit == null ? HitResult.Type.MISS : hit.getType();
        }

        private boolean hitTarget(Entity expected) {
            return hit instanceof EntityHitResult entityHit
                    && entityHit.getEntity() == expected;
        }

        private Vec3 hitLocation() {
            return Objects.requireNonNull(hit, "observed projectile impact").getLocation();
        }
    }

    private enum TerminalImpact {
        BLOCK,
        SELF,
        NONLIVING,
        UNLOADED_LIVING
    }
}
