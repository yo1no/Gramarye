package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Excluded causal fixture. No field credit, cloud owner/effect/timer or control state is seeded. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aConfigParkingResetProbe {
    private static Run active;
    private P11C4aConfigParkingResetProbe() { }

    static void start(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, String role, Path output) {
        require(selected() && active == null && server.isSameThread() && actor != peer
                && actor.serverLevel() == peer.serverLevel() && actor.isAlive() && peer.isAlive()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                && actor.connection.getConnection().isEncrypted() && peer.connection.getConnection().isEncrypted()
                && !actor.isFakePlayer() && !peer.isFakePlayer(), "CP_RESET_REAL_TWO_ACTORS");
        var run = new Run(server, actor, peer, role, output); active = run;
        qualified(run);
        P11C4aConfigPrimaryProbe.armParkingReset(actor.connection.getConnection(), output, true);
        // Ordinary fixture blocks, in the real peer's entity-ticking neighbourhood; no forced chunks.
        var center = BlockPos.containing(peer.getX() + 8, peer.getY() + 3, peer.getZ());
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            actor.serverLevel().setBlockAndUpdate(center.offset(x, -1, z), Blocks.GLASS.defaultBlockState());
            for (int y = 0; y < 4; y++) actor.serverLevel().setBlockAndUpdate(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var position = actor.position(); float yaw = actor.getYRot(), pitch = actor.getXRot();
        ItemStack hand = actor.getItemInHand(InteractionHand.MAIN_HAND);
        var potion = new ItemStack(Items.LINGERING_POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.STRONG_HARMING));
        Throwable primary = null;
        try {
            actor.connection.teleport(center.getX() + .5, center.getY() + 1, center.getZ() + .5, yaw, 90);
            actor.setItemInHand(InteractionHand.MAIN_HAND, potion);
            run.throwing = true;
            Items.LINGERING_POTION.use(actor.serverLevel(), actor, InteractionHand.MAIN_HAND);
            require(run.projectile != null, "CP_RESET_ORIGINAL_THROW_DID_NOT_CREATE_PROJECTILE");
        } catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            run.throwing = false;
            try {
                actor.setItemInHand(InteractionHand.MAIN_HAND, hand);
                actor.connection.teleport(position.x, position.y, position.z, yaw, pitch);
            } catch (RuntimeException | Error secondary) { if (primary == null) { throw secondary; } }
        }
    }

    @SubscribeEvent static void joined(EntityJoinLevelEvent event) {
        var run = active;
        if (run == null || !run.throwing || event.getLevel() != run.actor.level()
                || !(event.getEntity() instanceof ThrownPotion potion)) { return; }
        require(run.server.isSameThread() && run.projectile == null && potion.getOwner() == run.actor
                && potion.getItem().is(Items.LINGERING_POTION), "CP_RESET_NATIVE_PROJECTILE_OWNER");
        run.projectile = potion;
    }

    /** Exact original makeAreaOfEffectCloud -> Level.addFreshEntity, original called once. */
    public static boolean cloudAdded(ThrownPotion potion, Level level, Entity entity, Operation<Boolean> original) {
        boolean added = original.call(level, entity);
        var run = active;
        if (run == null || potion != run.projectile) { return added; }
        require(run.server.isSameThread() && added && entity instanceof AreaEffectCloud
                && run.cloud == null && level == run.actor.level(), "CP_RESET_NATIVE_CLOUD_ADD");
        run.cloud = (AreaEffectCloud) entity;
        require(run.cloud.getOwner() == run.actor && run.cloud.getDuration() == 600
                && run.cloud.getRadius() == 3.0F && run.cloud.getRadiusPerTick() == -3.0F / 600.0F,
                "CP_RESET_ORIGINAL_CLOUD_MATERIAL");
        return added;
    }

    static boolean tick(ServerPlayer successor, Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 2400, "CP_RESET_TICK_OWNER");
        if (!run.armed) {
            require(run.ticks <= 100, "CP_RESET_PROJECTILE_IMPACT_BOUND");
            if (run.cloud == null) { return false; }
            usableCloud(run);
            P11C4aConfigResetProbe.startParking(run.server, run.actor, run.role, run.output);
            P11C4aParkingProbe.start(run.server, run.actor, run.role, run.output);
            run.armed = true;
        }
        if (!run.chickenPlaced && P11C4aEvidence.receiptPresent(clientOutput, "config-parking-finish-captured.json")) {
            usableCloud(run); qualified(run);
            require(run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null
                    && run.actor.connection.getConnection().getPacketListener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl,
                    "CP_RESET_CAPTURE_NOT_ACTORLESS_CONFIG");
            var chicken = EntityType.CHICKEN.create(run.actor.serverLevel());
            require(chicken != null && chicken.getHealth() == 4.0F, "CP_RESET_ORDINARY_CHICKEN");
            run.chicken = chicken; run.chickenPlaced = true;
            chicken.moveTo(run.cloud.getX(), run.cloud.getY(), run.cloud.getZ(), 0, 0);
            require(run.actor.serverLevel().addFreshEntity(chicken), "CP_RESET_CHICKEN_ADD");
        }
        if (!P11C4aParkingProbe.tick(successor, clientOutput)) { return false; }
        require(run.effects == 1 && run.finishCue && run.cloudAgeAtEffect > 0, "CP_RESET_CAUSE_OR_RELEASE_MISSING");
        P11C4aConfigResetProbe.finishParking(successor);
        P11C4aEvidence.write(run.output, "config-parking-reset.json", Map.of(
                "status", "ACTUAL_CONFIG_PENDING_TO_PARKING_RESET_THEN_FRESH_PARKED_ACK_AND_RETRY",
                "originalPotionUse", 1, "originalCloudEffects", run.effects, "cloudAgeAtEffect", run.cloudAgeAtEffect,
                "cloudRadiusAtEffect", run.cloudRadiusAtEffect, "cloudLifetimeUnmodified", true,
                "oldConfigAckReplayed", false, "fullC4aAcceptance", false));
        P11C4aConfigPrimaryProbe.finish(run.actor.connection.getConnection());
        abort(); return true;
    }

    /** Only the actual cloud's original effect call can arm the existing die-credit/reload fixture. */
    public static void effect(AreaEffectCloud cloud, MobEffect effect, Entity direct, Entity owner,
            LivingEntity victim, int amplifier, double multiplier, Operation<Void> original) {
        var run = active;
        if (run == null || cloud != run.cloud || victim != run.chicken) {
            original.call(effect, direct, owner, victim, amplifier, multiplier); return;
        }
        usableCloud(run);
        var before = qualified(run);
        require(before.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && before.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && before.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0,
                "CP_RESET_NO_BORROWED_CAUSE_BEFORE_ORIGINAL_EFFECT");
        require(run.chickenPlaced && ++run.effects == 1 && direct == cloud && owner == run.actor
                && amplifier == 1 && multiplier == .5 && effect == net.minecraft.world.effect.MobEffects.HARM.value()
                && victim.isAlive() && victim.getHealth() == 4, "CP_RESET_TRUE_CLOUD_EFFECT");
        run.cloudAgeAtEffect = cloud.tickCount; run.cloudRadiusAtEffect = cloud.getRadius();
        P11C4aParkingProbe.cloudCaller(victim,
                () -> original.call(effect, direct, owner, victim, amplifier, multiplier));
    }

    static void releaseFinish(Connection connection) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && connection == run.actor.connection.getConnection()
                && run.effects == 1 && !run.finishCue, "CP_RESET_FINISH_RELEASE_SCOPE");
        var body = qualified(run);
        require(body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] > 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0,
                "CP_RESET_RELEASE_NOT_GENUINE_CREDIT_FOP");
        run.finishCue = true;
        P11C4aEvidence.cue(run.output, run.role + "-config-parking-finish-release.ready");
    }

    static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        var run = active;
        require(run != null && event.getEntity() == run.chicken && event.getSource().getDirectEntity() == run.cloud
                && event.getSource().getEntity() == run.actor && run.chicken.getKillCredit() == run.actor,
                "CP_RESET_ACTUAL_INDIRECT_MAGIC_CREDIT");
    }

    private static P11QualifiedSourceOwner.Body qualified(Run run) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
        var body = source == null ? null : source.nativeRecipient(run.actor);
        require(body != null && body.actor == run.actor && body.complete && body == source.nativeRecipient(run.actor),
                "CP_RESET_CURRENT_QUALIFIED_A_REQUIRED");
        return body;
    }
    private static void usableCloud(Run run) {
        require(run.cloud != null && !run.cloud.isRemoved() && run.cloud.getOwner() == run.actor
                && run.cloud.getRadius() >= .5F && run.cloud.tickCount < 510
                && run.cloud.getDuration() == 600 && run.peer.isAlive()
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && run.peer.serverLevel() == run.cloud.level() && run.peer.distanceToSqr(run.cloud) < 32 * 32
                && run.peer.distanceToSqr(run.cloud) > 16 && run.actor.distanceToSqr(run.cloud) > 16,
                "CP_RESET_CLOUD_NATIVE_LIFETIME_OR_REAL_PEER");
    }
    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_PARKING_RESET; }
    @SubscribeEvent static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        var run = active;
        if (run != null && event.getServer() == run.server) { active = null; }
    }
    static void abort() {
        var run = active; active = null;
        if (run == null) { return; }
        if (run.cloud != null && !run.cloud.isRemoved()) { run.cloud.discard(); }
        if (run.projectile != null && !run.projectile.isRemoved()) { run.projectile.discard(); }
        if (run.chicken != null && !run.chicken.isRemoved()) { run.chicken.discard(); }
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final String role; final Path output;
        ThrownPotion projectile; AreaEffectCloud cloud; Chicken chicken;
        boolean throwing, armed, chickenPlaced, finishCue;
        int ticks, effects, cloudAgeAtEffect; float cloudRadiusAtEffect;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, String role, Path output) {
            this.server = server; this.actor = actor; this.peer = peer; this.role = role; this.output = output;
        }
    }
}
