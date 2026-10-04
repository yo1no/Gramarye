package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Excluded closed non-L1 episodes. Original R creates work; native lifecycle ends it. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11L1LifecycleBoundaryProbe {
    enum Mode { ONE_WORK_DEATH, ONE_WORK_DIMENSION, ONE_WORK_CONFIG }
    private static Run run;
    private P11L1LifecycleBoundaryProbe() {}

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode)
            throws IOException {
        require(run == null && mode != null && server.isSameThread() && actor != peer
                && actor.getServer() == server && peer.getServer() == server
                && !actor.getUUID().equals(peer.getUUID()), "ARM_IDENTITY");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        require(body != null && owner.canSerialize(body) && owner.canCopy(body)
                && body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0, "REAL_QUALIFIED_WORK_FREE_SOURCE");
        if (mode == Mode.ONE_WORK_DEATH) {
            require(!server.isHardcore() && !server.getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_DO_IMMEDIATE_RESPAWN), "ORIGINAL_MANUAL_DEATH_FIXTURE");
        }
        run = new Run(server, actor, peer, output, mode, body);
        currentActor(run); peerCurrent(run);
        P11C4aEvidence.write(output, "lifecycle-armed.json", facts(run, "ARMED_NOT_QUALIFIED"));
        P11C4aEvidence.cue(output, "a-cast-1.ready");
    }

    static void instanceCreated(ServerPlayer actor, Object value) {
        var r = run;
        if (r == null || r.finished || actor != r.actor) { return; }
        try {
            require(r.server.isSameThread() && r.instance == null && value instanceof ServerSlot.InstanceState,
                    "ONE_ORIGINAL_INSTANCE");
            r.instance = (ServerSlot.InstanceState) value;
            require(r.instance.hasP9AuthenticatedActorWitness(actor) && r.instance.work == null,
                    "CONSTRUCTOR_EXACT_WITNESS");
        } catch (RuntimeException | Error ignored) { fail(r, "INSTANCE_OBSERVER"); }
    }

    static void accepted(MinecraftServer server, ServerPlayer actor, Object value) {
        var r = run;
        if (r == null || r.finished || actor != r.actor) { return; }
        try {
            require(server == r.server && server.isSameThread() && !r.accepted
                    && value instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "ACTUAL_ADMISSION_ONCE");
            var result = (RuntimeAdmissionResult.AcceptedMemoryOnly) value;
            require(r.instance != null && r.instance.id.equals(result.eventToken().skillInstanceId())
                    && r.instance.work != null && r.instance.work.qualifies(actor)
                    && !r.instance.lease.pin.isClosed() && count(r) == 1, "ACCEPTED_WORK_RECEIPT");
            r.work = r.instance.work; r.accepted = true;
        } catch (RuntimeException | Error ignored) { fail(r, "ADMISSION_OBSERVER"); }
    }

    static void transferred(Object value, Object disposition) {
        var r = run;
        if (r == null || r.finished || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        try {
            require(r.accepted && r.projectile == null && value instanceof P9StarterProjectile,
                    "ORIGINAL_TRANSFER_ONCE");
            var projectile = (P9StarterProjectile) value;
            var permit = r.instance.activeProjectileContinuation;
            require(permit != null && permit.plannedProjectileId.equals(projectile.getUUID())
                    && permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    && permit.qualifiedActor(r.server, projectile) == r.actor
                    && r.oldLevel.getEntity(projectile.getUUID()) == projectile, "EXACT_LOADED_OPEN");
            r.permit = permit; r.projectile = projectile;
        } catch (RuntimeException | Error ignored) { fail(r, "TRANSFER_OBSERVER"); }
    }

    static void claimed(Object disposition) {
        var r = run;
        if (r != null && !r.finished && disposition == RuntimePermitClaimDisposition.QUEUED) {
            r.claims++; fail(r, "OLD_WORK_CLAIMED_CHILD");
        }
    }

    static void damageEntering(DamageSource source) {
        var r = run;
        if (r != null && !r.finished && r.projectile != null && source.getDirectEntity() == r.projectile) {
            r.damageEntries++; fail(r, "OLD_PROJECTILE_ENTERED_HURT");
        }
    }

    static void experience(ServerPlayer recipient, int amount) {
        var r = run;
        if (r != null && !r.finished && amount != 0 && recipient.getUUID().equals(r.actor.getUUID())) {
            r.experienceEntries++; fail(r, "UNEXPECTED_REWARD_EXPERIENCE");
        }
    }

    /** Called from the existing ordinary server Post tick; never from inside P5 dispatch. */
    static void tick() throws IOException {
        var r = run;
        if (r == null || r.finished) { return; }
        require(r.server.isSameThread() && r.failure.equals("NONE") && ++r.ticks <= 2400, "OBSERVER_OR_CASE_DEADLINE");
        peerCurrent(r);
        if (!r.issued) {
            currentActor(r);
            if (!r.accepted || r.projectile == null) { return; }
            require(r.instance.work == r.work && count(r) == 1 && !r.projectile.isRemoved()
                    && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    && r.permit.qualifiedActor(r.server, r.projectile) == r.actor, "NATURAL_OPEN_BEFORE_BOUNDARY");
            if (r.projectile.tickCount == 0) { return; }
            r.openAge = r.projectile.tickCount;
            r.issued = true;
            P11C4aEvidence.write(r.output, "lifecycle-before-native.json", facts(r, "REAL_ACCEPTED_OPEN_BEFORE_ORIGINAL_BOUNDARY"));
            // No helper catch/replay around these original calls; their original primary escapes.
            switch (r.mode) {
                case ONE_WORK_DEATH -> r.actor.kill();
                case ONE_WORK_DIMENSION -> {
                    var target = r.server.getLevel(r.oldLevel.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
                    require(target != null && target != r.oldLevel, "NATIVE_DESTINATION_LEVEL");
                    r.destination = target;
                    var moved = r.actor.changeDimension(new DimensionTransition(target, r.actor,
                            DimensionTransition.PLACE_PORTAL_TICKET));
                    require(moved == r.actor && r.actor.serverLevel() == target
                            && !r.actor.isDeadOrDying(), "ORIGINAL_DIMENSION_CHANGE_RETURN");
                }
                case ONE_WORK_CONFIG -> r.actor.connection.switchToConfig();
            }
            r.originalReturned = true;
            r.logoutAtReturn = r.instance.logoutState.name();
            require(r.instance.logoutState != SkillRuntimeService.LogoutState.COMPLETE
                    && r.instance.logoutState != SkillRuntimeService.LogoutState.IN_PROGRESS
                    && r.instance.logoutScope == null, "NON_LOGOUT_MUST_NOT_GAIN_LOGOUT_PROOF");
            if (r.mode == Mode.ONE_WORK_DEATH) {
                require(r.deathEvents == 1 && r.actor.isDeadOrDying() && r.actor.getHealth() <= 0,
                        "ORIGINAL_DEATH_MUST_HAPPEN");
            } else if (r.mode == Mode.ONE_WORK_DIMENSION) {
                require(r.dimensionEvents == 1 && r.server.getLevel(r.oldLevel.dimension()) == r.oldLevel,
                        "ORIGINAL_DIMENSION_EVENT_OLD_LEVEL_REMAINS");
            } else {
                require(r.logoutEvents == 1 && r.actor.isRemoved() && r.actor.hasDisconnected()
                        && r.connection.isConnected() && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == null,
                        "ORIGINAL_CONFIG_REMOVE_NOT_DISCONNECT");
            }
            require(r.permit.qualification(r.server, r.projectile) == SkillRuntimeService.WorkQualification.INVALID,
                    "BOUNDARY_MUST_INVALIDATE_ORIGINAL_WORK");
            return;
        }
        if (!r.workTerminal) {
            if (count(r) != 0) { return; }
            terminal(r);
            r.workTerminal = true;
            P11C4aEvidence.write(r.output, "lifecycle-work-terminal.json", facts(r, "OLD_WORK_TERMINAL_BEFORE_RECOVERY"));
            P11C4aEvidence.cue(r.output, "a-lifecycle-terminal.ready");
        }
        if (r.mode == Mode.ONE_WORK_CONFIG && !r.returnRequested) {
            // This cue is a synchronization request; the client separately records its native
            // StartConfiguration handler and actual accepted ENTER_CONFIG COMPLETED state.
            if (!P11C4aEvidence.cuePresent(P11C4aEvidence.root().resolve("client-a"), "lifecycle-config-terminal.ready")) { return; }
            require(r.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                    && r.connection.isConnected() && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == null,
                    "ACTUAL_CONFIGURATION_BEFORE_INDEPENDENT_RETURN");
            var configuration = (ServerConfigurationPacketListenerImpl) r.connection.getPacketListener();
            require(configuration.getConnection() == r.connection && configuration.getMainThreadEventLoop() == r.server,
                    "EXACT_CONFIGURATION_LISTENER");
            r.returnRequested = true;
            configuration.returnToWorld();
            r.returnCallReturned = true;
            return;
        }
        var current = r.server.getPlayerList().getPlayer(r.actor.getUUID());
        if (r.mode == Mode.ONE_WORK_DIMENSION) {
            require(current == r.actor && current.serverLevel() == r.destination, "SAME_ACTOR_TRUE_DIMENSION");
        } else if (current == null || current == r.actor || r.successor == null) { return; }
        else { require(current == r.successor && r.successorEvents == 1, "TRUE_NATIVE_SUCCESSOR_EVENT"); }
        if (!P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-a"), "lifecycle-world-ready.json")) { return; }
        require(current != null && !current.isDeadOrDying() && !current.isRemoved()
                && current.connection.getConnection() == r.connection && r.connection.isConnected()
                && r.connection.getPacketListener() == current.connection && current.connection.player == current,
                "GENUINE_CURRENT_PLAY_AFTER_BOUNDARY");
        terminal(r);
        r.finished = true;
        P11C4aEvidence.write(r.output, "lifecycle-result.json", facts(r, "NATIVE_NAMED_NON_LOGOUT_BOUNDARY_OBSERVED"));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void death(LivingDeathEvent event) {
        var r = run;
        if (r == null || r.finished || event.getEntity() != r.actor) { return; }
        if (!r.issued || r.mode != Mode.ONE_WORK_DEATH || event.isCanceled()) { fail(r, "UNEXPECTED_DEATH"); return; }
        r.deathEvents++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        var r = run;
        if (r == null || r.finished || event.getEntity() != r.actor) { return; }
        if (!r.issued || r.mode != Mode.ONE_WORK_DIMENSION || r.destination == null
                || !event.getFrom().equals(r.oldLevel.dimension()) || !event.getTo().equals(r.destination.dimension())) {
            fail(r, "UNEXPECTED_DIMENSION"); return;
        }
        r.dimensionEvents++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var r = run;
        if (r == null || r.finished || event.getEntity() != r.actor) { return; }
        if (!r.issued || r.mode != Mode.ONE_WORK_CONFIG) { fail(r, "UNEXPECTED_LOGOUT"); return; }
        r.logoutEvents++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void respawn(PlayerEvent.PlayerRespawnEvent event) { successor(event, Mode.ONE_WORK_DEATH); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(PlayerEvent.PlayerLoggedInEvent event) { successor(event, Mode.ONE_WORK_CONFIG); }

    private static void successor(PlayerEvent event, Mode expected) {
        var r = run;
        if (r == null || r.finished || !(event.getEntity() instanceof ServerPlayer player)
                || !player.getUUID().equals(r.actor.getUUID())) { return; }
        if (r.mode != expected || !r.workTerminal || player == r.actor
                || player.getServer() != r.server || player.connection.getConnection() != r.connection) {
            fail(r, "UNEXPECTED_SUCCESSOR"); return;
        }
        r.successor = player; r.successorEvents++;
    }

    public static boolean beforeRelease(Object value) {
        var r = run;
        if (r == null || r.finished || value != r.instance || r.instance.work == null) { return false; }
        try {
            require(r.server.isSameThread() && r.issued && !r.releaseEntered
                    && r.instance.work == r.work && count(r) == 1 && r.instance.logoutScope == null,
                    "EXACT_NON_LOGOUT_RELEASE");
            r.releaseEntered = true; return true;
        } catch (RuntimeException | Error ignored) { fail(r, "RELEASE_ENTRY_OBSERVER"); return false; }
    }

    public static void afterRelease(boolean observed, boolean normal) {
        var r = run;
        if (!observed || r == null) { return; }
        try {
            require(normal && r.releaseEntered && !r.releaseReturned && r.instance.work == null && count(r) == 0,
                    "ORIGINAL_WORK_RELEASE_RETURN");
            r.releaseReturned = true;
        } catch (RuntimeException | Error ignored) { fail(r, "RELEASE_RETURN_OBSERVER"); }
    }

    static boolean complete() { return run != null && run.finished && run.failure.equals("NONE"); }
    static String failureCode() { return run == null ? "NOT_ARMED" : run.failure; }
    static void release() { run = null; }
    private static long count(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()]; }
    private static void currentActor(Run r) {
        require(!r.actor.isRemoved() && !r.actor.isDeadOrDying() && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor
                && r.connection.isConnected() && r.actor.connection.getConnection() == r.connection
                && r.connection.getPacketListener() == r.actor.connection, "EXACT_INITIAL_PLAY_ACTOR");
    }
    private static void peerCurrent(Run r) {
        require(r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer && !r.peer.isRemoved()
                && !r.peer.isDeadOrDying() && r.peer.connection.getConnection() == r.peerConnection
                && r.peerConnection.isConnected() && r.peerConnection.getPacketListener() == r.peer.connection,
                "INDEPENDENT_PEER_CHANGED");
    }
    private static void terminal(Run r) {
        require(r.failure.equals("NONE") && r.originalReturned && r.releaseReturned && count(r) == 0
                && r.instance.work == null && r.instance.logoutScope == null
                && r.instance.logoutState == SkillRuntimeService.LogoutState.INVALID
                && r.instance.p9ActorWitness() == null && r.instance.activeProjectileContinuation == null
                && r.instance.lease.pin.isClosed() && r.permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                && r.projectile.isRemoved() && r.claims == 0 && r.damageEntries == 0 && r.experienceEntries == 0,
                "EXACT_TERMINAL_NO_OLD_GAMEPLAY");
        require(r.instance.p9Diagnostic != null && r.instance.p9Diagnostic.terminalPublished
                && r.instance.p9Diagnostic.terminalReason == ProjectileClosureReason.OWNER_INVALIDATED,
                "OWNERSHIP_BOUNDARY_NOT_NATURAL_EXPIRY");
    }
    private static void fail(Run r, String code) { if (r.failure.equals("NONE")) { r.failure = code; } }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_LIFECYCLE_" + code); }

    private static LinkedHashMap<String, Object> facts(Run r, String status) {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", status); facts.put("mode", r.mode.name()); facts.put("failure", r.failure);
        facts.put("actualP7Acceptance", r.accepted); facts.put("naturalOpenAge", r.openAge);
        facts.put("originalBoundaryIssued", r.issued); facts.put("originalBoundaryReturned", r.originalReturned);
        facts.put("originalReleaseReturned", r.releaseReturned); facts.put("work", count(r));
        facts.put("nativeDeathEvents", r.deathEvents); facts.put("nativeDimensionEvents", r.dimensionEvents);
        facts.put("nativeConfigLogoutEvents", r.logoutEvents); facts.put("nativeSuccessorEvents", r.successorEvents);
        facts.put("logoutStateAtBoundaryReturn", r.logoutAtReturn); facts.put("workTerminalBeforeRecovery", r.workTerminal);
        facts.put("originalReturnToWorldIssued", r.returnRequested); facts.put("originalReturnToWorldReturned", r.returnCallReturned);
        facts.put("claims", r.claims); facts.put("oldProjectileDamageEntries", r.damageEntries);
        facts.put("experienceEntries", r.experienceEntries); facts.put("physicalOsOrPortalClaim", false);
        facts.put("terminalReason", r.instance == null || r.instance.p9Diagnostic == null
                || r.instance.p9Diagnostic.terminalReason == null ? "ABSENT" : r.instance.p9Diagnostic.terminalReason.name());
        facts.put("permitState", r.permit == null ? "ABSENT" : r.permit.state.name());
        facts.put("pinClosed", r.instance != null && r.instance.lease.pin.isClosed());
        return facts;
    }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final Connection connection, peerConnection;
        final ServerLevel oldLevel; final Path output; final Mode mode; final P11QualifiedSourceOwner.Body body;
        ServerLevel destination; ServerPlayer successor; ServerSlot.InstanceState instance;
        P11QualifiedSourceOwner.WorkReservation work; RuntimeProjectileContinuationPermit permit; P9StarterProjectile projectile;
        int ticks, openAge, deathEvents, dimensionEvents, logoutEvents, successorEvents, claims, damageEntries, experienceEntries;
        boolean accepted, issued, originalReturned, releaseEntered, releaseReturned, workTerminal;
        boolean returnRequested, returnCallReturned, finished;
        String failure = "NONE", logoutAtReturn = "UNOBSERVED";
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode, P11QualifiedSourceOwner.Body body) {
            this.server = server; this.actor = actor; this.peer = peer; this.output = output; this.mode = mode; this.body = body;
            this.connection = actor.connection.getConnection(); this.peerConnection = peer.connection.getConnection();
            this.oldLevel = actor.serverLevel();
        }
    }
}
