package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
            scenario.startPresentation();
            helper.assertTrue(
                    observeBalance(scenario.actor())
                            == 0L,
                    "canonical node 0 must begin with the fresh available-zero mana truth");
            var accepted = scenario.admit();
            scenario.post();

            var root = scenario.port().event(0);
            var projectileId = plannedProjectileId(accepted, root);
            var projectile = scenario.requireProjectile(projectileId);
            var geometry = scenario.fixture().geometry();
            var castEvents = scenario.presentation().bufferedEventsForTesting();
            helper.assertTrue(
                    scenario.level().getEntity(projectileId) == projectile
                            && projectile.isAddedToLevel()
                            && !projectile.isRemoved()
                            && projectile.hasAuthenticatedCasterIdentity(scenario.actor())
                            && projectile.getOwner() == scenario.actor()
                            && close(projectile.getDeltaMovement().length(), 1.5),
                    "real P6 spawn must transfer the exact loaded entity, actor witness, and speed 1.5 launch");
            helper.assertTrue(
                    castEvents.size() == 1
                            && castEvents.getFirst().kind()
                                    == PresentationEventKind.CAST_RELEASE
                            && castEvents.getFirst().dimension().equals(geometry.dimension())
                            && samePosition(
                                    castEvents.getFirst().position(),
                                    geometry.originX(),
                                    geometry.originY(),
                                    geometry.originZ())
                            && castEvents.getFirst().direction().equals(
                                    expectedDirection(
                                            geometry.directionXQ15(),
                                            geometry.directionYQ15(),
                                            geometry.directionZQ15()))
                            && castEvents.getFirst().sourceSummary()
                                            .sourceEntityId()
                                            .orElseThrow()
                                    == scenario.actor().getId(),
                    "applied spawn must offer one CAST_RELEASE from the frozen node-0 geometry");

            var target = scenario.addTarget(projectile.position().add(
                    projectile.getDeltaMovement().scale(0.75)));
            var healthBefore = target.getHealth();
            var sweptMovement = projectile.getDeltaMovement();
            var impact = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .observeDamage(target);
            NeoForge.EVENT_BUS.register(impact);
            try {
                projectile.tick();
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

                var frozenHit = impact.hitLocation();
                target.setPos(target.position().add(2.0, 0.0, 0.0));
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
                        frozenHit,
                        sweptMovement,
                        scenario.port().runtimeTick(0));
                helper.assertTrue(
                        child.eventId().value() == root.eventId().value() + 1L
                                && !child.eventId().equals(accepted.eventToken().eventId()),
                        "the child must consume the held future identity, not reuse the root identity");

                var hitEvents = scenario.presentation().bufferedEventsForTesting();
                helper.assertTrue(
                        impact.damageCalls() == 1
                                && impact.hasExactDamageSource(projectile, scenario.actor())
                                && sameFloat(impact.damageAmount(), 4.0F)
                                && close(healthBefore - target.getHealth(), 4.0D),
                        "node 1 must make one indirect-magic 4.0F call with exact projectile/caster attribution and unarmored health loss");
                helper.assertTrue(
                        hitEvents.size() == 1
                                && hitEvents.getFirst().kind() == PresentationEventKind.HIT
                                && hitEvents.getFirst().dimension().equals(
                                        target.level().dimension().location())
                                && samePosition(
                                        hitEvents.getFirst().position(),
                                        frozenHit.x,
                                        frozenHit.y,
                                        frozenHit.z)
                                && hitEvents.getFirst().direction().equals(
                                        expectedDirection(
                                                q15(sweptMovement.x, sweptMovement.length()),
                                                q15(sweptMovement.y, sweptMovement.length()),
                                                q15(sweptMovement.z, sweptMovement.length())))
                                && !samePosition(
                                        hitEvents.getFirst().position(),
                                        target.getX(),
                                        target.getY(),
                                        target.getZ())
                                && hitEvents.getFirst().sourceSummary()
                                                .sourceEntityId()
                                                .orElseThrow()
                                        == scenario.actor().getId()
                                && hitEvents.getFirst().sourceSummary()
                                                .targetEntityId()
                                                .orElseThrow()
                                        == target.getId()
                                && scenario.presentation()
                                                .bufferedPresentationsForTesting()
                                                .getFirst()
                                                .sourceEventId()
                                        == child.eventId().value(),
                        "the applied fact must offer one HIT from the accepted frozen hit snapshot, independent of later target movement");
                helper.assertTrue(
                        projectile.isRemoved()
                                && observeBalance(scenario.actor())
                                        == 0L,
                        "successful node 0 and node 1 must remove the witness while preserving fresh zero mana");
                assertTerminalDiagnostic(
                        helper,
                        scenario,
                        accepted,
                        ProjectileClosureReason.DAMAGE_TERMINAL,
                        1, 2, 3, 4, 5, 6, 7, 8,
                        9, 10, 11, 12, 13, 14, 15, 16);

                projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
                projectile.tick();
                scenario.post();
                helper.assertTrue(
                        scenario.port().calls() == 2
                                && impact.damageCalls() == 1
                                && close(healthBefore - target.getHealth(), 4.0D)
                                && scenario.runtime().cancel(
                                                server(scenario), accepted.eventToken())
                                        instanceof RuntimeCancellationResult.NotPending
                                && scenario.runtime().cancel(
                                                server(scenario), accepted.cancellationToken())
                                        instanceof RuntimeCancellationResult.NotPending
                                && scenario.enterStopping() == 0,
                        "duplicate and late callbacks must cause no second damage/fact and leave zero continuation work");
            } finally {
                NeoForge.EVENT_BUS.unregister(impact);
            }
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
        exerciseAbsorptionAppliedTruth(helper, 0x9320L);
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
        exerciseDamageRejectionPolicies(helper, 0x9330L);
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
        exercisePresentationFailureIsolation(helper, 0x9340L);
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
        exerciseClaimedActorDimensionChange(helper, 0x9350L);
        exerciseDamageThrowableNoRetry(helper, 0x9360L);
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
                    "unstarted P8 must drop presentation while the cap and sixteen OPEN transfers remain unchanged");
            var closedWorkUnits = scenario.enterStopping();
            helper.assertTrue(
                    closedWorkUnits == 16
                            && projectiles.stream().filter(Entity::isRemoved).count() == 16L,
                    "bounded server-stop cleanup must report and terminate exactly 16 transfers");
        }
        helper.succeed();
    }

    private static void exerciseAbsorptionAppliedTruth(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            scenario.startPresentation();
            helper.assertTrue(
                    observeBalance(scenario.actor())
                            == 0L,
                    "absorption control must begin at the fresh zero balance");
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var maximumAbsorption = target.getAttribute(Attributes.MAX_ABSORPTION);
            helper.assertTrue(maximumAbsorption != null,
                    "controlled living target must expose the platform absorption attribute");
            maximumAbsorption.setBaseValue(4.0D);
            target.setAbsorptionAmount(4.0F);
            var healthBefore = target.getHealth();
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .observeDamage(target);
            NeoForge.EVENT_BUS.register(damage);
            try {
                projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
                scenario.post();
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
            var events = scenario.presentation().bufferedEventsForTesting();
            helper.assertTrue(scenario.port().calls() == 2,
                    "absorbed damage must consume exactly the root and held child");
            helper.assertTrue(damage.damageCalls() == 1,
                    "absorbed damage must make exactly one platform attempt");
            helper.assertTrue(
                    damage.hasExactDamageSource(projectile, scenario.actor())
                            && sameFloat(damage.damageAmount(), 4.0F),
                    "absorbed damage must retain the exact projectile/caster source and 4.0F amount");
            helper.assertTrue(sameFloat(target.getHealth(), healthBefore),
                    "full absorption must preserve raw target health");
            helper.assertTrue(sameFloat(target.getAbsorptionAmount(), 0.0F),
                    "the one accepted platform mutation must consume all four absorption points");
            helper.assertTrue(
                    events.size() == 1
                            && events.getFirst().kind() == PresentationEventKind.HIT,
                    "hurt true under full absorption must emit exactly one HIT");
            helper.assertTrue(projectile.isRemoved(),
                    "absorbed damage must terminal the inert projectile");
            helper.assertTrue(observeBalance(scenario.actor()) == 0L,
                    "absorbed zero-cost damage must make no mana mutation");
        }
    }

    private static void exerciseDamageRejectionPolicies(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            scenario.actor().getAbilities().instabuild = false;
            helper.assertTrue(
                    !scenario.actor().getAbilities().instabuild,
                    "invulnerability control requires a non-creative exact caster");
            target.setInvulnerable(true);
            var healthBefore = target.getHealth();
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .observeDamage(target);
            NeoForge.EVENT_BUS.register(damage);
            try {
                projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
                scenario.post();
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
            helper.assertTrue(scenario.port().calls() == 2,
                    "invulnerable damage must consume exactly the root and held child");
            helper.assertTrue(damage.damageCalls() == 0,
                    "ordinary invulnerability must reject before the incoming-damage event");
            helper.assertTrue(sameFloat(target.getHealth(), healthBefore),
                    "ordinary invulnerability must preserve health");
            helper.assertTrue(projectile.isRemoved(),
                    "invulnerability rejection must terminal the inert projectile");
            helper.assertTrue(
                    scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "invulnerability rejection must emit no HIT fact");
            assertTerminalDiagnostic(
                    helper,
                    scenario,
                    accepted,
                    ProjectileClosureReason.DAMAGE_TERMINAL,
                    1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 15, 16);
        }

        try (var scenario = new ProductionScenario(helper, fixtureId + 1L)) {
            scenario.startPresentation();
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var healthBefore = target.getHealth();
            var presentationSequenceBefore = scenario.presentation()
                    .presentationSequenceHighWaterForTesting();
            var cancellation = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .cancelDamage(target);
            NeoForge.EVENT_BUS.register(cancellation);
            try {
                projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
                scenario.post();
            } finally {
                NeoForge.EVENT_BUS.unregister(cancellation);
            }
            helper.assertTrue(
                    scenario.port().calls() == 2
                            && cancellation.damageCalls() == 1
                            && cancellation.hasExactDamageSource(
                                    projectile, scenario.actor())
                            && sameFloat(cancellation.damageAmount(), 4.0F)
                            && sameFloat(target.getHealth(), healthBefore)
                            && projectile.isRemoved()
                            && scenario.presentation()
                                            .presentationSequenceHighWaterForTesting()
                                    == presentationSequenceBefore
                            && scenario.presentation().bufferedEventsForTesting().stream()
                                    .noneMatch(event -> event.kind()
                                            == PresentationEventKind.HIT),
                    "a cancelled ordinary damage event must yield NOT_APPLIED with one attempt and no HIT");
        }

        var server = helper.getLevel().getServer();
        var priorPvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            try (var scenario = new ProductionScenario(helper, fixtureId + 2L)) {
                var accepted = scenario.admit();
                scenario.post();
                var projectile = scenario.requireProjectile(
                        plannedProjectileId(accepted, scenario.port().event(0)));
                var target = scenario.addPlayerTarget(projectile.position());
                var healthBefore = target.getHealth();
                projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
                scenario.post();
                helper.assertTrue(
                        scenario.port().calls() == 2
                                && sameFloat(target.getHealth(), healthBefore)
                                && projectile.isRemoved()
                                && scenario.presentation()
                                        .bufferedEventsForTesting()
                                        .isEmpty(),
                        "disabled PvP must preserve platform rejection and emit no HIT");
            }
        } finally {
            server.setPvpAllowed(priorPvp);
        }

        PlayerTeam team = null;
        var scoreboard = server.getScoreboard();
        var teamName = "p9s4" + Long.toHexString(fixtureId + 3L);
        try {
            server.setPvpAllowed(true);
            try (var scenario = new ProductionScenario(helper, fixtureId + 3L)) {
                var accepted = scenario.admit();
                scenario.post();
                var projectile = scenario.requireProjectile(
                        plannedProjectileId(accepted, scenario.port().event(0)));
                var target = scenario.addPlayerTarget(projectile.position());
                team = scoreboard.addPlayerTeam(teamName);
                team.setAllowFriendlyFire(false);
                scoreboard.addPlayerToTeam(scenario.actor().getScoreboardName(), team);
                scoreboard.addPlayerToTeam(target.getScoreboardName(), team);
                var healthBefore = target.getHealth();
                projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
                scenario.post();
                helper.assertTrue(
                        scenario.port().calls() == 2
                                && sameFloat(target.getHealth(), healthBefore)
                                && projectile.isRemoved()
                                && scenario.presentation()
                                        .bufferedEventsForTesting()
                                        .isEmpty(),
                        "same-team friendly-fire policy must reject without a HIT fact");
            }
        } finally {
            if (team != null && scoreboard.getPlayerTeam(teamName) == team) {
                scoreboard.removePlayerTeam(team);
            }
            server.setPvpAllowed(priorPvp);
        }

        exerciseIFrameRejection(helper, fixtureId + 4L);
        exercisePostResolveTargetInvalidation(helper, fixtureId + 5L);
    }

    private static void exerciseIFrameRejection(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            scenario.startPresentation();
            var firstAccepted = scenario.admit();
            scenario.post();
            var firstProjectile = scenario.requireProjectile(
                    plannedProjectileId(firstAccepted, scenario.port().event(0)));
            var target = scenario.addTarget(firstProjectile.position());
            var healthBefore = target.getHealth();
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .observeDamage(target);
            NeoForge.EVENT_BUS.register(damage);
            try {
                firstProjectile.onHitEntity(
                        new EntityHitResult(target, firstProjectile.position()));
                scenario.post();
                helper.assertTrue(
                        damage.damageCalls() == 1
                                && close(healthBefore - target.getHealth(), 4.0D)
                                && scenario.presentation().bufferedEventsForTesting().size()
                                        == 1
                                && scenario.presentation()
                                                .bufferedEventsForTesting()
                                                .getFirst()
                                                .kind()
                                        == PresentationEventKind.HIT,
                        "the iframe control requires one initial accepted damage and HIT");

                var secondAccepted = scenario.admit();
                scenario.post();
                var secondProjectile = scenario.requireProjectile(
                        plannedProjectileId(secondAccepted, scenario.port().event(2)));
                var sequenceBeforeDeniedHit = scenario.presentation()
                        .presentationSequenceHighWaterForTesting();
                secondProjectile.onHitEntity(
                        new EntityHitResult(target, secondProjectile.position()));
                scenario.post();
                helper.assertTrue(
                        scenario.port().calls() == 4
                                && damage.damageCalls() == 2
                                && close(healthBefore - target.getHealth(), 4.0D)
                                && firstProjectile.isRemoved()
                                && secondProjectile.isRemoved()
                                && scenario.presentation()
                                                .presentationSequenceHighWaterForTesting()
                                        == sequenceBeforeDeniedHit
                                && scenario.presentation().bufferedEventsForTesting().stream()
                                        .noneMatch(event -> event.kind()
                                                == PresentationEventKind.HIT),
                        "an equal second hit inside the platform iframe must return false without another health change or HIT");
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
        }
    }

    private static void exercisePostResolveTargetInvalidation(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var healthBefore = target.getHealth();
            scenario.port().invalidateTargetAfterNextResolvedDamage(target);
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .observeDamage(target);
            NeoForge.EVENT_BUS.register(damage);
            try {
                projectile.onHitEntity(
                        new EntityHitResult(target, projectile.position()));
                scenario.post();
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
            helper.assertTrue(
                    scenario.port().calls() == 2
                            && target.isRemoved()
                            && damage.damageCalls() == 0
                            && sameFloat(target.getHealth(), healthBefore)
                            && projectile.isRemoved()
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "a target invalidated after damage resolution must be cancelled by the next P5 guard without hurt or applied fact");
            assertTerminalDiagnostic(
                    helper,
                    scenario,
                    accepted,
                    ProjectileClosureReason.DAMAGE_TERMINAL,
                    1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 15, 16);
        }
    }

    private static void exercisePresentationFailureIsolation(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(
                helper, fixtureId, particleDisabledAppearance())) {
            scenario.startPresentation();
            helper.assertTrue(
                    observeBalance(scenario.actor()) == 0L,
                    "particle-disabled gameplay must begin with exact zero mana");
            var accepted = scenario.admit();
            scenario.post();
            var castEvents = scenario.presentation().bufferedEventsForTesting();
            helper.assertTrue(
                    castEvents.size() == 1
                            && castEvents.getFirst().kind()
                                    == PresentationEventKind.CAST_RELEASE
                            && castEvents.getFirst().appearance()
                                    .particleProfileId().isEmpty()
                            && castEvents.getFirst().appearance().soundProfileId()
                                    .equals(Optional.of(
                                            AppearanceSemantics.DEFAULT_SOUND_PROFILE))
                            && castEvents.getFirst().appearance().trailProfileId()
                                    .equals(Optional.of(
                                            AppearanceSemantics.DEFAULT_TRAIL_PROFILE)),
                    "the actual P9 root must disable only particles while retaining sound and trail defaults");
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var healthBefore = target.getHealth();
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .observeDamage(target);
            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            var primary = new IllegalStateException(
                    "P9_S4_EXPECTED_PRESENTATION_RUNTIME");
            scenario.port().failNextPresentationCaptureWith(primary);
            NeoForge.EVENT_BUS.register(damage);
            try {
                scenario.post();
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 2
                            && damage.damageCalls() == 1
                            && damage.hasExactDamageSource(projectile, scenario.actor())
                            && sameFloat(damage.damageAmount(), 4.0F)
                            && close(healthBefore - target.getHealth(), 4.0D)
                            && observeBalance(scenario.actor()) == 0L
                            && projectile.isRemoved()
                            && scenario.presentation().hasRuntimeDiagnosticForTesting(
                                    P8ServerRuntimeDiagnosticCode
                                            .OBSERVER_RUNTIME_EXCEPTION)
                            && scenario.presentation().bufferedEventsForTesting().stream()
                                    .noneMatch(event -> event.kind()
                                            == PresentationEventKind.HIT),
                    "observer RuntimeException must be isolated after one completed damage without HIT replay");
            assertTerminalDiagnostic(
                    helper,
                    scenario,
                    accepted,
                    ProjectileClosureReason.DAMAGE_TERMINAL,
                    1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16);
        }

        try (var scenario = new ProductionScenario(helper, fixtureId + 1L)) {
            scenario.startPresentation();
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var healthBefore = target.getHealth();
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .observeDamage(target);
            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            var primary = new AssertionError("P9_S4_EXPECTED_PRESENTATION_ERROR");
            scenario.port().failNextPresentationCaptureWith(primary);
            Error observed = null;
            NeoForge.EVENT_BUS.register(damage);
            try {
                scenario.post();
            } catch (Error failure) {
                observed = failure;
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
            scenario.post();
            helper.assertTrue(
                    observed == primary
                            && scenario.port().calls() == 2
                            && damage.damageCalls() == 1
                            && close(healthBefore - target.getHealth(), 4.0D)
                            && projectile.isRemoved()
                            && scenario.presentation().bufferedEventsForTesting().stream()
                                    .noneMatch(event -> event.kind()
                                            == PresentationEventKind.HIT),
                    "observer Error must propagate identically after damage while cleanup prevents replay");
            assertErrorDeferredDiagnostic(
                    helper,
                    scenario,
                    accepted,
                    1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16);
        }
    }

    private static void exerciseClaimedActorDimensionChange(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var healthBefore = target.getHealth();
            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            var end = scenario.level().getServer().getLevel(Level.END);
            helper.assertTrue(end != null,
                    "node-1 dimension control requires the loaded End level");
            helper.assertTrue(
                    scenario.changeActorDimension(end) == scenario.actor(),
                    "ordinary dimension travel must retain exact actor identity");
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 1
                            && sameFloat(target.getHealth(), healthBefore)
                            && projectile.isRemoved()
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "same actor in another dimension must terminal the claimed child before damage");
            helper.assertTrue(
                    scenario.changeActorDimension(scenario.level()) == scenario.actor(),
                    "dimension fixture must restore the exact actor to its original level");
            scenario.post();
            helper.assertTrue(
                    scenario.port().calls() == 1,
                    "returning to the frozen dimension must not revive terminal damage work");
        }
    }

    private static void exerciseDamageThrowableNoRetry(
            GameTestHelper helper, long fixtureId) {
        try (var scenario = new ProductionScenario(helper, fixtureId)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var healthBefore = target.getHealth();
            var primary = new IllegalStateException("P9_S4_EXPECTED_DAMAGE_RUNTIME");
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .failDamageWith(target, primary);
            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            RuntimeException observed = null;
            NeoForge.EVENT_BUS.register(damage);
            try {
                scenario.post();
            } catch (RuntimeException failure) {
                observed = failure;
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
            scenario.post();
            helper.assertTrue(
                    observed == primary
                            && scenario.port().calls() == 2
                            && damage.damageCalls() == 1
                            && sameFloat(target.getHealth(), healthBefore)
                            && projectile.isRemoved()
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "damage RuntimeException must propagate identically, terminal cleanup, and never retry");
            assertTerminalDiagnostic(
                    helper,
                    scenario,
                    accepted,
                    ProjectileClosureReason.RUNTIME_FAULT,
                    1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 15, 16);
        }

        try (var scenario = new ProductionScenario(helper, fixtureId + 1L)) {
            var accepted = scenario.admit();
            scenario.post();
            var projectile = scenario.requireProjectile(
                    plannedProjectileId(accepted, scenario.port().event(0)));
            var target = scenario.addTarget(projectile.position());
            var healthBefore = target.getHealth();
            var primary = new AssertionError("P9_S4_EXPECTED_DAMAGE_ERROR");
            var damage = new OneShotProjectileImpactCancellation()
                    .observeOnly()
                    .failDamageWith(target, primary);
            projectile.onHitEntity(new EntityHitResult(target, projectile.position()));
            Error observed = null;
            NeoForge.EVENT_BUS.register(damage);
            try {
                scenario.post();
            } catch (Error failure) {
                observed = failure;
            } finally {
                NeoForge.EVENT_BUS.unregister(damage);
            }
            scenario.post();
            helper.assertTrue(
                    observed == primary
                            && scenario.port().calls() == 2
                            && damage.damageCalls() == 1
                            && sameFloat(target.getHealth(), healthBefore)
                            && projectile.isRemoved()
                            && scenario.presentation().bufferedEventsForTesting().isEmpty(),
                    "damage Error must propagate identically after bounded invalidation and never retry");
            assertErrorDeferredDiagnostic(
                    helper,
                    scenario,
                    accepted,
                    1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 15, 16);
        }
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
            LivingEntity target,
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

    private static boolean sameFloat(float actual, float expected) {
        return Float.floatToIntBits(actual) == Float.floatToIntBits(expected);
    }

    private static void assertTerminalDiagnostic(
            GameTestHelper helper,
            ProductionScenario scenario,
            RuntimeAdmissionResult.AcceptedMemoryOnly accepted,
            ProjectileClosureReason terminalReason,
            int... expectedStages) {
        var diagnostic = scenario.runtime().p9TerminalDiagnosticForTesting(
                server(scenario), accepted.eventToken().skillInstanceId());
        helper.assertTrue(diagnostic != null,
                "ordinary P9 terminal must publish one bounded diagnostic");
        var expected = paddedStageCodes(expectedStages);
        helper.assertTrue(
                diagnostic.skillInstanceId().equals(
                                accepted.eventToken().skillInstanceId())
                        && diagnostic.stageCount() == expectedStages.length
                        && Arrays.equals(diagnostic.stageCodes(), expected)
                        && diagnostic.terminalReason() == terminalReason
                        && diagnostic.cleanupDisposition()
                                == P9RuntimeCleanupDisposition.RELEASED,
                "ordinary P9 terminal must preserve the exact ordered stage prefix and RELEASED disposition");
    }

    private static void assertErrorDeferredDiagnostic(
            GameTestHelper helper,
            ProductionScenario scenario,
            RuntimeAdmissionResult.AcceptedMemoryOnly accepted,
            int... expectedStages) {
        var diagnostic = scenario.runtime().p9ErrorDeferredDiagnosticForTesting(
                server(scenario), accepted.eventToken().skillInstanceId());
        helper.assertTrue(diagnostic != null,
                "controlled P9 Error must retain one bounded ERROR_DEFERRED trace");
        helper.assertTrue(
                diagnostic.stageCount == expectedStages.length
                        && Arrays.equals(
                                diagnostic.stageCodes,
                                paddedStageCodes(expectedStages))
                        && diagnostic.terminalReason
                                == ProjectileClosureReason.RUNTIME_FAULT
                        && diagnostic.cleanupDisposition
                                == P9RuntimeCleanupDisposition.ERROR_DEFERRED
                        && !diagnostic.terminalPublished,
                "controlled P9 Error must preserve the exact stage prefix without claiming RELEASED or a terminal-ring record");
        helper.assertTrue(
                scenario.runtime().p9TerminalDiagnosticForTesting(
                                server(scenario),
                                accepted.eventToken().skillInstanceId())
                        == null,
                "Error-deferred evidence must not materialize a terminal-ring record");
    }

    private static int[] paddedStageCodes(int[] stages) {
        if (stages.length > 16) {
            throw new IllegalArgumentException("P9 diagnostic stage prefix exceeds 16");
        }
        var padded = new int[16];
        System.arraycopy(stages, 0, padded, 0, stages.length);
        return padded;
    }

    private static AppearanceDocument particleDisabledAppearance() {
        return AppearanceDocument.decoded(new AppearanceDefinition(
                OptionalInt.empty(),
                OptionalInt.empty(),
                ProfileSelection.inherit(),
                ProfileSelection.disabled(),
                ProfileSelection.inherit(),
                OptionalInt.empty()));
    }

    private static boolean samePosition(
            PresentationPosition actual, double x, double y, double z) {
        return sameDouble(actual.x(), x)
                && sameDouble(actual.y(), y)
                && sameDouble(actual.z(), z);
    }

    private static PresentationDirection expectedDirection(int x, int y, int z) {
        return PresentationDirection.normalized(x, y, z).orElseThrow();
    }

    private static P8ServerPresentationService.PreparedCatalog emptyCatalog() {
        return P8ServerPresentationService.loadCandidateForTesting(
                MagicRegistries.profileTypeRegistry(), Map.of());
    }

    private static long observeBalance(ServerPlayer actor) {
        return P7ManaSnapshotBridge.observeBalance(runtimeCapability(), actor);
    }

    private static P6RuntimeExecutionCapability runtimeCapability() {
        return P6RuntimeExecutionCapability.forRuntimeAdapter();
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
            this(helper, fixtureId, AppearanceDocument.Default.INSTANCE);
        }

        @SuppressWarnings("removal")
        private ProductionScenario(
                GameTestHelper helper,
                long fixtureId,
                AppearanceDocument appearance) {
            this.helper = Objects.requireNonNull(helper, "helper");
            level = helper.getLevel();
            actor = helper.makeMockServerPlayerInLevel();
            actorId = actor.getUUID();
            P7S4LoginManaGameTests.P9GameTestFixture openedFixture = null;
            try {
                openedFixture = P7S4LoginManaGameTests.openP9GameTestFixture(
                        helper, actor, fixtureId, appearance);
                fixture = openedFixture;
                port = new RecordingProductionPort(actor);
                presentation = port.presentation();
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

        private void startPresentation() {
            port.startPresentation(level.getServer());
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

        private Cow addTarget(Vec3 position) {
            var target = new Cow(EntityType.COW, level);
            target.setNoAi(true);
            target.setPos(
                    position.x,
                    position.y - target.getBbHeight() * 0.5,
                    position.z);
            helper.assertTrue(level.addFreshEntity(target),
                    "controlled unarmored living target must be inserted into the actual ServerLevel");
            controlledEntities.add(target);
            return target;
        }

        @SuppressWarnings("removal")
        private ServerPlayer addPlayerTarget(Vec3 position) {
            var target = helper.makeMockServerPlayerInLevel();
            target.setPos(position.x, position.y - 0.5, position.z);
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

        private ServerPlayer changeActorDimension(ServerLevel destination) {
            var changed = actor.changeDimension(new DimensionTransition(
                    destination, actor, DimensionTransition.DO_NOTHING));
            actor.hasChangedDimension();
            helper.assertTrue(
                    changed == actor
                            && actor.serverLevel() == destination
                            && level.getServer().getPlayerList().getPlayer(actorId)
                                    == actor,
                    "actual dimension travel must preserve the test-owned actor");
            return actor;
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
                    if (entity instanceof ServerPlayer player
                            && level.getServer().getPlayerList().getPlayer(
                                            player.getUUID())
                                    == player) {
                        level.getServer().getPlayerList().remove(player);
                    } else if (!entity.isRemoved()) {
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

    private static final class RecordingProductionPort
            implements RuntimeExecutionPort, P8PresentationTransport {
        private P6RuntimeExecutionPortAdapter delegate;
        private final UUID readyPlayerId;
        private final P8ServerPresentationService presentation;
        private final List<RuntimeEvent> events = new ArrayList<>();
        private final List<Long> runtimeTicks = new ArrayList<>();
        private LivingEntity preCommitInvalidationTarget;
        private Throwable nextPresentationCaptureFailure;
        private boolean presentationActive;

        private RecordingProductionPort(ServerPlayer actor) {
            readyPlayerId = Objects.requireNonNull(actor, "actor").getUUID();
            presentation = new P8ServerPresentationService(this);
            delegate = new P6RuntimeExecutionPortAdapter(
                    runtimeCapability(), presentation);
        }

        private void invalidateTargetAfterNextResolvedDamage(LivingEntity target) {
            if (preCommitInvalidationTarget != null) {
                throw new IllegalStateException(
                        "P9 S4 target invalidation already armed");
            }
            preCommitInvalidationTarget = Objects.requireNonNull(target, "target");
            delegate = new P6RuntimeExecutionPortAdapter(
                    runtimeCapability(),
                    presentation,
                    this::executeWithPreCommitInvalidation,
                    ProductionP6RuntimeExecutionInputMapper.INSTANCE);
        }

        private void executeWithPreCommitInvalidation(
                P6RuntimeExecutionCapability capability,
                ServerPlayer actor,
                P6RuntimeExecutionBridge.Invocation input,
                P6RuntimeExecutionBridge.GuardPort guard,
                P6RuntimeExecutionBridge.WorldCommitPort commitPort,
                P6RuntimeExecutionBridge.AppliedFactObserver observer) {
            P6RuntimeExecutionBridge.execute(
                    capability,
                    actor,
                    input,
                    (point, stepIndex) -> {
                        var decision = guard.check(point, stepIndex);
                        if (input instanceof P6RuntimeExecutionBridge.DamageInvocation
                                && point
                                        == P6RuntimeExecutionBridge.GuardPoint.PRE_COMMIT
                                && decision
                                        == P6RuntimeExecutionBridge.GuardDecision.ALLOWED) {
                            var target = preCommitInvalidationTarget;
                            preCommitInvalidationTarget = null;
                            if (target != null) {
                                target.discard();
                            }
                        }
                        return decision;
                    },
                    commitPort,
                    observer);
        }

        private void startPresentation(MinecraftServer server) {
            if (presentationActive) {
                throw new IllegalStateException("P9 S4 presentation fixture already active");
            }
            presentation.startForTesting(server, emptyCatalog());
            presentationActive = true;
        }

        private void failNextPresentationCaptureWith(Throwable failure) {
            if (!(failure instanceof RuntimeException) && !(failure instanceof Error)) {
                throw new IllegalArgumentException(
                        "presentation failure must be unchecked");
            }
            nextPresentationCaptureFailure = Objects.requireNonNull(failure, "failure");
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

        private P8ServerPresentationService presentation() {
            return presentation;
        }

        @Override
        public Optional<P8RecipientIdentity> captureReadyIdentity(
                ServerPlayer player, long catalogGeneration) {
            if (!presentationActive || !player.getUUID().equals(readyPlayerId)) {
                return Optional.empty();
            }
            var failure = nextPresentationCaptureFailure;
            nextPresentationCaptureFailure = null;
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            return Optional.of(new P8RecipientIdentity(readyPlayerId, 1L));
        }

        @Override
        public boolean isCurrent(
                ServerPlayer player,
                P8RecipientIdentity identity,
                long catalogGeneration) {
            return presentationActive
                    && player.getUUID().equals(readyPlayerId)
                    && identity.playerId().equals(readyPlayerId)
                    && identity.connectionEpoch() == 1L;
        }

        @Override
        public P8PresentationSubmissionResult submit(
                P8RecipientIdentity identity, PresentationEvent event) {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(event, "event");
            return identity.playerId().equals(readyPlayerId)
                    ? P8PresentationSubmissionResult.SUBMITTED
                    : P8PresentationSubmissionResult.UNAVAILABLE;
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
        private LivingEntity damageTarget;
        private DamageSource damageSource;
        private Throwable damageFailure;
        private float damageAmount;
        private int damageCalls;
        private boolean cancelDamage;

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

        @SubscribeEvent
        public void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (event.getEntity() != damageTarget) {
                return;
            }
            damageCalls = Math.incrementExact(damageCalls);
            damageSource = event.getSource();
            damageAmount = event.getOriginalAmount();
            if (cancelDamage) {
                event.setCanceled(true);
            }
            if (damageFailure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (damageFailure instanceof Error error) {
                throw error;
            }
        }

        private OneShotProjectileImpactCancellation observeOnly() {
            cancel = false;
            return this;
        }

        private OneShotProjectileImpactCancellation observeDamage(
                LivingEntity target) {
            damageTarget = Objects.requireNonNull(target, "target");
            return this;
        }

        private OneShotProjectileImpactCancellation cancelDamage(
                LivingEntity target) {
            cancelDamage = true;
            return observeDamage(target);
        }

        private OneShotProjectileImpactCancellation failDamageWith(
                LivingEntity target, Throwable failure) {
            if (!(failure instanceof RuntimeException) && !(failure instanceof Error)) {
                throw new IllegalArgumentException("damage failure must be unchecked");
            }
            damageFailure = Objects.requireNonNull(failure, "failure");
            return observeDamage(target);
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

        private int damageCalls() {
            return damageCalls;
        }

        private float damageAmount() {
            return damageAmount;
        }

        private boolean hasExactDamageSource(
                P9StarterProjectile projectile, ServerPlayer actor) {
            return damageSource != null
                    && damageSource.is(DamageTypes.INDIRECT_MAGIC)
                    && damageSource.getDirectEntity() == projectile
                    && damageSource.getEntity() == actor;
        }
    }

    private enum TerminalImpact {
        BLOCK,
        SELF,
        NONLIVING,
        UNLOADED_LIVING
    }
}
