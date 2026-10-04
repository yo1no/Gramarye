package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** EXTERNAL DRAFT: actual native XP reentrancy at one original C6 gate, never a fabricated gate/root. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aC6EarlyGateProbe {
    public enum Point { PLAY_FIRST, CONFIG_EARLY, PREPLAY_RETRY }
    public interface EntryConnection { Connection p11$c6Connection(); }
    private static Run active;
    private static CreditPlan prepared;
    private static final ThreadLocal<Call> CALL = new ThreadLocal<>();

    private P11C4aC6EarlyGateProbe() {}

    /** Two original holder fields are captured while A is genuinely present, never refreshed later. */
    static Map<String, Object> prepareActorless(ServerPlayer actor) {
        require(P11C4aEvidence.enabled() && actor.getServer().isSameThread() && active == null
                && prepared == null && CALL.get() == null && actor.isAlive()
                && actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor), "C6_CREDIT_PREPARE_LIVE_OWNER");
        var plan = new CreditPlan(actor);
        prepared = plan;
        plan.before = roots(actor, P11ControlBudgets.Root.NATIVE_CREDIT);
        Throwable primary = null;
        try {
            plan.early = creditCow(actor, 2);
            plan.retry = creditCow(actor, -2);
            plan.after = roots(actor, P11ControlBudgets.Root.NATIVE_CREDIT);
            require(plan.after >= plan.before + 6, "C6_TWO_NATIVE_CREDIT_HOLDERS");
            return Map.of("status", "TWO_NATIVE_CREDIT_HOLDERS_PREPARED_WHILE_LIVE_NOT_ACCEPTANCE",
                    "nativeTick", plan.tick, "nativeCreditBefore", plan.before, "nativeCreditAfter", plan.after,
                    "holders", 2, "ttlReset", false);
        } catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            if (primary != null) {
                try { releasePrepared(); } catch (RuntimeException | Error ignored) { }
            }
        }
    }

    private static Cow creditCow(ServerPlayer actor, int offset) {
        var cow = EntityType.COW.create(actor.serverLevel());
        require(cow != null, "C6_NATIVE_COW_UNAVAILABLE");
        cow.moveTo(actor.getX() + offset, actor.getY(), actor.getZ(), 0, 0);
        boolean accepted = false;
        try {
            require(actor.serverLevel().addFreshEntity(cow)
                    && cow.hurt(actor.damageSources().playerAttack(actor), 1.0F)
                    && cow.getLastHurtByMob() == actor && cow.getKillCredit() == actor
                    && cow.getLastDamageSource() != null && cow.getLastDamageSource().getEntity() == actor,
                    "C6_LIVE_NATIVE_CREDIT_FIELDS");
            accepted = true; return cow;
        } finally {
            if (!accepted && !cow.isRemoved()) {
                try { cow.discard(); } catch (RuntimeException | Error ignored) { }
            }
        }
    }

    static void arm(ServerPlayer exactRetainedActor, Point point) {
        require(P11C4aEvidence.enabled() && exactRetainedActor.getServer().isSameThread()
                && active == null && CALL.get() == null, "C6_GATE_ARM_OWNER");
        var connection = exactRetainedActor.connection.getConnection();
        require(connection.isConnected() && matches(point, connection), "C6_GATE_ARM_PHASE");
        var source = P11NativeStorageBoundary.nativeSourceOwner(exactRetainedActor);
        var body = source == null ? null : source.nativeRecipient(exactRetainedActor);
        require(body != null && body.actor == exactRetainedActor && source.canCopy(body), "C6_GATE_SOURCE");
        Cow holder = null;
        if (point != Point.PLAY_FIRST) {
            require(prepared != null && prepared.actor == exactRetainedActor && prepared.connection == connection,
                    "C6_ACTORLESS_NATIVE_CAUSE_NOT_PREPARED");
            holder = point == Point.CONFIG_EARLY ? prepared.early : prepared.retry;
            require(holder != null && holder.isAlive() && !holder.isRemoved()
                    && holder.getKillCredit() == exactRetainedActor, "C6_ORIGINAL_CREDIT_EXPIRED_BEFORE_ARM");
        }
        active = new Run(exactRetainedActor, connection, point, holder);
    }

    /** Original Operation is retained only in this synchronous native grant's call stack. */
    public static Object originalGate(Object service, Object entry, Point point, Operation<Object> original) {
        var run = active;
        if (run == null || !run.armed || run.point != point
                || !(entry instanceof EntryConnection observed) || observed.p11$c6Connection() != run.connection) {
            return original.call(service, entry);
        }
        require(run.actor.getServer().isSameThread() && matches(point, run.connection)
                && CALL.get() == null, "C6_GATE_CALL_OWNER");
        run.armed = false;
        var call = new Call(run, service, entry, original);
        CALL.set(call);
        boolean normal = false;
        try {
            // This XP mutation is an explicit fixture precondition. Only the refused transition
            // is required to have zero body side effects; the reward is not claimed side-effect free.
            if (point == Point.PLAY_FIRST) {
                AdvancementRewards.Builder.experience(run.amount).build().grant(run.actor);
            } else {
                consumeOriginalCredit(run);
            }
            require(call.calls == 1 && call.result == P11TransitionControl.Gate.ACTIVE_OPERATION,
                    "C6_GATE_NOT_ORIGINAL_ACTIVE_OPERATION");
            require(run.actor.totalExperience == run.xpBefore + run.amount, "C6_GATE_REWARD_TAIL");
            run.returned = true;
            normal = true;
            return call.result; // Exact original result, no boolean or enum substitution.
        } finally {
            CALL.remove();
            run.originalGateCalls = call.calls;
            run.normal = normal;
        }
    }

    private static void consumeOriginalCredit(Run run) {
        require(prepared != null && prepared.actor == run.actor && run.holder != null
                && run.holder.getKillCredit() == run.actor && run.holder.isAlive()
                && run.actor.getServer().getPlayerList().getPlayer(run.actor.getUUID()) == null,
                "C6_ORIGINAL_CREDIT_EXPIRED_BEFORE_CONSUMER");
        run.creditAgeTicks = Integer.toUnsignedLong(run.actor.getServer().getTickCount()) - prepared.tick;
        run.creditAtConsumer = roots(run.actor, P11ControlBudgets.Root.NATIVE_CREDIT);
        var loot = run.actor.getServer().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean before = loot.get();
        Throwable primary = null;
        run.deathArmed = true;
        try {
            loot.set(false, run.actor.getServer());
            require(run.holder.hurt(run.holder.damageSources().magic(), 1000.0F), "C6_ORIGINAL_LETHAL_HURT");
            require(run.deaths == 1 && run.creditFop > 0, "C6_ORIGINAL_CREDIT_CONSUMER_NOT_OBSERVED");
        } catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            run.deathArmed = false;
            try { loot.set(before, run.actor.getServer()); }
            catch (RuntimeException | Error secondary) { if (primary == null) { throw secondary; } }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void death(LivingDeathEvent event) {
        var call = CALL.get();
        if (call == null || !call.run.deathArmed || event.getEntity() != call.run.holder) { return; }
        var run = call.run;
        run.deathArmed = false;
        require(run.actor.getServer().isSameThread() && ++run.deaths == 1
                && matches(run.point, run.connection) && run.holder.getKillCredit() == run.actor,
                "C6_ACTUAL_DEATH_CONSUMER_OWNER");
        run.creditFop = roots(run.actor, P11ControlBudgets.Root.OPERATION);
        require(run.creditFop > 0 && roots(run.actor, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0,
                "C6_ACTUAL_DEATH_CREDIT_FOP");
        AdvancementRewards.Builder.experience(run.amount).build().grant(run.actor);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void xp(PlayerXpEvent.XpChange event) {
        var call = CALL.get();
        if (call == null || event.getEntity() != call.run.actor || event.getAmount() != call.run.amount) { return; }
        var run = call.run;
        require(run.actor.getServer().isSameThread() && ++call.calls == 1, "C6_GATE_CALLBACK_COUNT");
        var source = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
        var body = source == null ? null : source.nativeRecipient(run.actor);
        require(body != null && body.actor == run.actor, "C6_GATE_CALLBACK_SOURCE");
        run.fop = body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()];
        run.qctx = body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()];
        require(run.fop > 0 && run.qctx == 0, "C6_GATE_NOT_REAL_FOP");
        // If the original gate throws, the same throwable propagates through the original reward;
        // no catch, replay, replacement result, or manual root release is installed here.
        call.result = call.original.call(call.service, call.entry);
    }

    static Map<String, Object> reportAndRelease() {
        var run = active;
        require(run != null && run.actor.getServer().isSameThread() && CALL.get() == null,
                "C6_GATE_REPORT_OWNER");
        var report = new LinkedHashMap<String, Object>();
        report.put("point", run.point.name()); report.put("originalGateCalls", run.originalGateCalls);
        report.put("originalReturned", run.returned); report.put("nativeRewardReturned", run.normal);
        report.put("actualFopAtGate", run.fop); report.put("actualQctxAtGate", run.qctx);
        report.put("fixtureXpDelta", run.amount); report.put("transitionAcceptance", false);
        report.put("actualCreditDeathCallbacks", run.deaths); report.put("actualFopAtCreditDeath", run.creditFop);
        report.put("nativeCreditAtConsumer", run.creditAtConsumer); report.put("originalCreditAgeTicks", run.creditAgeTicks);
        report.put("ttlReset", false); report.put("detachedIdleGrant", false);
        active = null;
        return report;
    }

    static boolean returned() { var run = active; return run != null && run.returned && CALL.get() == null; }

    static void abort() {
        var run = active;
        require((run == null || run.actor.getServer().isSameThread()) && CALL.get() == null, "C6_GATE_ABORT_OWNER");
        active = null;
        releasePrepared();
    }

    private static void releasePrepared() {
        var plan = prepared;
        if (plan == null) { return; }
        require(plan.actor.getServer().isSameThread(), "C6_CREDIT_RELEASE_OWNER");
        prepared = null;
        if (plan.early != null && !plan.early.isRemoved()) { plan.early.discard(); }
        if (plan.retry != null && !plan.retry.isRemoved()) { plan.retry.discard(); }
    }

    @SubscribeEvent static void stopped(ServerStoppedEvent event) {
        if (prepared != null && prepared.actor.getServer() == event.getServer()) { abort(); }
    }

    private static long roots(ServerPlayer actor, P11ControlBudgets.Root root) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        require(body != null && body.actor == actor, "C6_CREDIT_EXACT_SOURCE");
        return body.account.nativeCounts[root.ordinal()];
    }

    private static boolean matches(Point point, Connection connection) {
        return point == Point.PLAY_FIRST
                ? connection.getPacketListener() instanceof net.minecraft.server.network.ServerGamePacketListenerImpl
                : point == Point.CONFIG_EARLY
                ? connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                : connection.getPacketListener() instanceof P11ParkingPacketListener;
    }

    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }

    private static final class Run {
        final ServerPlayer actor;
        final Connection connection;
        final Point point;
        final int amount, xpBefore;
        final Cow holder;
        boolean armed = true;
        boolean returned, normal, deathArmed;
        int originalGateCalls, deaths;
        long fop, qctx, creditFop, creditAtConsumer, creditAgeTicks;
        Run(ServerPlayer actor, Connection connection, Point point, Cow holder) {
            this.actor = actor; this.connection = connection; this.point = point;
            this.holder = holder;
            amount = point == Point.PLAY_FIRST ? 11 : point == Point.CONFIG_EARLY ? 31 : 37;
            xpBefore = actor.totalExperience;
        }
    }

    private static final class CreditPlan {
        final ServerPlayer actor; final Connection connection; final long tick;
        Cow early, retry; long before, after;
        CreditPlan(ServerPlayer actor) {
            this.actor = actor; connection = actor.connection.getConnection();
            tick = Integer.toUnsignedLong(actor.getServer().getTickCount());
        }
    }

    private static final class Call {
        final Run run;
        final Object service, entry;
        final Operation<Object> original;
        Object result;
        int calls;
        Call(Run run, Object service, Object entry, Operation<Object> original) {
            this.run = run; this.service = service; this.entry = entry; this.original = original;
        }
    }
}
