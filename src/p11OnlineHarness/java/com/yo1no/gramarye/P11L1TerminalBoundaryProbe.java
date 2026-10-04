package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Three excluded cases; real R creates work and all terminal policy remains original. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11L1TerminalBoundaryProbe {
    enum Mode { DEADLINE, SPAWN_CALLBACK_REMOVE, LOGOUT_CLEANUP_FAULT }
    private static Run run;
    private static CloseDiagnostic closeDiagnostic;
    private P11L1TerminalBoundaryProbe() {}

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode)
            throws IOException {
        require(run == null && mode != null && server.isSameThread() && actor != peer
                && actor.getServer() == server && peer.getServer() == server
                && !actor.getUUID().equals(peer.getUUID()), "ARM_IDENTITY");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        require(body != null && owner.canCopy(body) && owner.canSerialize(body)
                && body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0
                && !body.logoutAttempted && !body.account.cleanupUnknown, "FRESH_QUALIFIED_SOURCE");
        var r = new Run(server, actor, peer, output, mode, owner, body);
        current(r); run = r;
        if (mode == Mode.DEADLINE) { waterArena(r); }
        write(r, "terminal-boundary-armed.json", "ARMED_NOT_QUALIFIED");
        P11C4aEvidence.cue(output, "a-cast-1.ready");
    }

    /** Still source-water in an already-loaded isolated arena; never changes entity physics. */
    private static void waterArena(Run r) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 13; z++) for (int y = 73; y <= 105; y++) {
            var pos = new BlockPos(x, y, z);
            require(r.level.isLoaded(pos) && r.level.isInWorldBounds(pos), "WATER_ARENA_ALREADY_LOADED");
            var block = x == -4 || x == 4 || z == -4 || z == 13 || y == 73
                    ? Blocks.GLASS.defaultBlockState() : y == 105 ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
            r.level.setBlock(pos, block, 3);
        }
        r.level.setBlock(new BlockPos(7, 100, -1), Blocks.STONE.defaultBlockState(), 3);
        r.actor.teleportTo(r.level, 0.5, 101, 0.5, java.util.Set.of(), 0, 0);
        r.peer.teleportTo(r.level, 7.5, 101, -0.5, java.util.Set.of(), 90, 0);
        r.waterPrepared = true;
    }

    static void instanceCreated(ServerPlayer actor, Object value) {
        closeDiagnosticCreated(actor, value);
        var r = run; if (r == null || r.finished || actor != r.actor) { return; }
        observe(r, () -> {
            require(r.instance == null && value instanceof ServerSlot.InstanceState, "INSTANCE_ONCE");
            r.instance = (ServerSlot.InstanceState) value;
            require(r.instance.hasP9AuthenticatedActorWitness(actor) && r.instance.work == null, "ORIGINAL_INSTANCE_WITNESS");
        });
    }

    static void accepted(MinecraftServer server, ServerPlayer actor, Object result) {
        closeDiagnosticAccepted(server, actor, result);
        var r = run; if (r == null || r.finished || actor != r.actor) { return; }
        observe(r, () -> {
            require(server == r.server && !r.accepted && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "ORIGINAL_ACCEPTANCE_ONCE");
            var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) result;
            require(r.instance != null && r.instance.id.equals(accepted.eventToken().skillInstanceId())
                    && r.instance.work != null && r.instance.work.qualifies(actor) && count(r) == 1,
                    "EXACT_ACCEPTED_WORK");
            r.accepted = true; r.work = r.instance.work;
        });
    }

    static void transferred(Object value, Object disposition) {
        closeDiagnosticTransferred(value, disposition);
        var r = run; if (r == null || r.finished || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        observe(r, () -> {
            require(r.mode != Mode.SPAWN_CALLBACK_REMOVE && r.accepted && r.projectile == null
                    && value instanceof P9StarterProjectile, "OPEN_ONCE");
            r.projectile = (P9StarterProjectile) value; r.permit = r.instance.activeProjectileContinuation;
            require(r.permit != null && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    && r.permit.qualifiedActor(r.server, r.projectile) == r.actor
                    && r.level.getEntity(r.projectile.getUUID()) == r.projectile, "EXACT_LOADED_OPEN");
            r.lastPosition = r.projectile.position(); r.transfers++;
        });
    }

    static void projectileTick(Object value) {
        var r = run; if (r == null || r.finished || value != r.projectile || r.mode != Mode.DEADLINE) { return; }
        observe(r, () -> {
            if (r.projectile.isRemoved()) { return; }
            require(r.waterPrepared && r.projectile.isInWater() && r.projectile.tickCount <= 100,
                    "NATIVE_WET_FLIGHT_WITHIN_ORIGINAL_AGE");
            r.wetTicks++; r.travel += r.projectile.position().distanceTo(r.lastPosition);
            r.lastPosition = r.projectile.position();
            require(Double.isFinite(r.travel) && r.travel < 64, "ORIGINAL_RANGE_NOT_REACHED");
        });
    }

    /** Observer after the unique ORIGINAL onAddedToLevel return. Discard is the chosen fixture callback. */
    public static void afterAdded(ServerLevel level, Entity entity) {
        var r = run;
        if (r == null || r.finished || r.mode != Mode.SPAWN_CALLBACK_REMOVE || !(entity instanceof P9StarterProjectile)) { return; }
        require(r.failure.equals("NONE") && r.accepted && r.instance.activeProjectileContinuation != null,
                "REMOVE_CALLBACK_ACCEPTED_WORK");
        var permit = r.instance.activeProjectileContinuation;
        if (!permit.plannedProjectileId.equals(entity.getUUID())) { return; }
        require(level == r.level && level.getServer().isSameThread() && r.projectile == null
                && permit.state == RuntimeProjectileContinuationPermit.State.RESERVED
                && permit.qualifiedActor(r.server, (P9StarterProjectile) entity) == r.actor
                && level.getEntity(entity.getUUID()) == entity && entity.isAddedToLevel() && !entity.isRemoved(),
                "SUCCESSFUL_ORIGINAL_ADD_CALLBACK");
        r.projectile = (P9StarterProjectile) entity; r.permit = permit; r.addCallbacks++;
        entity.discard(); // Original removal, no permit/source/queue mutation or replacement entity.
        r.discardReturned = true;
        require(entity.isRemoved() && level.getEntity(entity.getUUID()) == null, "ORIGINAL_DISCARD_RETURN");
    }

    public static void addReturned(Entity entity, boolean result, Throwable primary) {
        var r = run; if (r == null || entity != r.projectile || r.mode != Mode.SPAWN_CALLBACK_REMOVE) { return; }
        observe(r, () -> { require(primary == null && result && r.discardReturned, "ORIGINAL_ADD_STILL_TRUE"); r.addTrue++; });
    }

    public static void transferReturned(Object projectile, Object result, Throwable primary) {
        var r = run; if (r == null || projectile != r.projectile || r.mode != Mode.SPAWN_CALLBACK_REMOVE) { return; }
        observe(r, () -> {
            require(primary == null && result == RuntimePermitTransferDisposition.REJECTED && r.addTrue == 1,
                    "ORIGINAL_REMOVED_TRANSFER_REJECTED"); r.rejectedTransfers++;
        });
    }

    public static void runtimeFault(Object slot, RuntimeException primary, boolean after, RuntimeException returned) {
        var r = run; if (r == null || r.finished || r.mode != Mode.SPAWN_CALLBACK_REMOVE || !r.discardReturned) { return; }
        observe(r, () -> {
            require(slot instanceof ServerSlot && r.rejectedTransfers == 1 && primary != null,
                    "ORIGINAL_P5_RUNTIME_FAULT");
            if (r.primary == null) { r.primary = primary; }
            require(r.primary == primary, "FAULT_PRIMARY_CHANGED");
            if (after) {
                require(returned == primary && ((ServerSlot) slot).state == ServerSlot.State.FAULTED, "ORIGINAL_FAULT_RETURN");
                r.runtimeFaultReturns++;
            }
        });
    }

    public static void postThrew(MinecraftServer server, Throwable primary) {
        var r = run; if (r == null || server != r.server || r.mode != Mode.SPAWN_CALLBACK_REMOVE) { return; }
        observe(r, () -> { require(primary == r.primary && r.runtimeFaultReturns > 0, "ORIGINAL_POST_PRIMARY"); r.postThrows++; });
    }

    /** Read-only exact original sweep observation; no invocation or clock advance by this helper. */
    public static boolean sweepEntering(MinecraftServer server, Object value) {
        var r = run; if (r == null || r.finished || server != r.server || r.mode != Mode.DEADLINE || r.permit == null) { return false; }
        if (!(value instanceof ServerSlot slot) || slot.runtimeTick < r.permit.deadlineRuntimeTick) { return false; }
        final boolean[] selected = {false};
        observe(r, () -> {
            require(slot.runtimeTick == r.permit.deadlineRuntimeTick && r.logoutNormal == 1 && r.wetTicks > 0
                    && r.instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE
                    && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN && count(r) == 1
                    && !r.projectile.isRemoved() && r.level.getEntity(r.projectile.getUUID()) == r.projectile
                    && r.projectile.tickCount <= 100 && r.travel < 64, "EXACT_ORIGINAL_DEADLINE_SWEEP");
            r.sweepTick = slot.runtimeTick; r.sweepAge = r.projectile.tickCount; selected[0] = true;
        });
        return selected[0];
    }

    public static void sweepReturned(boolean selected, Throwable primary) {
        var r = run; if (r == null || !selected) { return; }
        observe(r, () -> {
            require(primary == null && r.instance.p9Diagnostic != null
                    && r.instance.p9Diagnostic.terminalReason == ProjectileClosureReason.DEADLINE_REACHED,
                    "ORIGINAL_DEADLINE_TERMINAL_REASON"); r.deadlineReturns++;
        });
    }

    public static boolean logoutEntering(ServerGamePacketListenerImpl listener) {
        var r = run; if (r == null || r.finished || listener != r.actor.connection || !r.closeRequested) { return false; }
        final boolean[] selected = {false};
        observe(r, () -> { require(!r.inLogout && r.logoutEntries == 0 && listener.player == r.actor, "EXACT_WHOLE_LOGOUT_ENTRY");
            r.inLogout = true; r.logoutEntries++; selected[0] = true; });
        return selected[0];
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void leave(EntityLeaveLevelEvent event) {
        var r = run;
        if (r == null || r.mode != Mode.LOGOUT_CLEANUP_FAULT || event.getEntity() != r.actor || !r.inLogout) { return; }
        require(r.server.isSameThread() && r.leaveFaults == 0 && r.accepted && r.permit != null
                && r.instance.logoutState == SkillRuntimeService.LogoutState.IN_PROGRESS,
                "FAULT_ONLY_INSIDE_ORIGINAL_NORMAL_LOGOUT");
        r.leaveFaults++; throw r.ownedLeaveFault;
    }

    public static void logoutFinished(boolean selected, Throwable primary) {
        var r = run; if (r == null || !selected) { return; }
        r.inLogout = false;
        observe(r, () -> {
            if (r.mode == Mode.DEADLINE) {
                require(primary == null && r.instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE
                        && r.instance.logoutScope == null && r.work.qualifies(r.actor), "WHOLE_NORMAL_LOGOUT_COMPLETE");
                r.logoutNormal++;
            } else {
                require(r.mode == Mode.LOGOUT_CLEANUP_FAULT && primary == r.ownedLeaveFault && r.leaveFaults == 1
                        && r.instance.logoutState == SkillRuntimeService.LogoutState.INVALID && r.instance.logoutScope == null
                        && r.body.account.cleanupUnknown && !r.owner.canCopy(r.body)
                        && r.body.envelope == null && r.body.pendingEnvelope == null,
                        "FAILED_LOGOUT_MUST_NOT_QUALIFY");
                r.primary = primary; r.logoutThrows++;
            }
            r.logoutReceiptPending = true; // Original connection tick can still own its monitor.
        });
    }

    public static void connectedCatch(Connection connection, Throwable primary) {
        var r = run; if (r == null || connection != r.connection || r.mode != Mode.LOGOUT_CLEANUP_FAULT) { return; }
        observe(r, () -> { require(primary == r.primary && r.logoutThrows == 1 && !r.inLogout, "EXACT_NATIVE_CONNECTED_CATCH"); r.connectedCatch++; });
    }
    public static void connectedTickReturned() {
        var r = run; if (r != null && r.connectedCatch == 1) { r.connectedTickNormal = true; }
    }
    public static void outerCaught(MinecraftServer server, boolean stopping, Throwable primary) {
        var r = run; if (r == null || server != r.server || r.finished || r.primary == null) { return; }
        observe(r, () -> {
            boolean same = primary == r.primary;
            if (primary instanceof net.minecraft.ReportedException && primary.getCause() == r.primary) { same = true; }
            require(!stopping && same, "ORIGINAL_OUTER_PRIMARY_OR_REPORTED_WRAPPER");
            r.outerCatch++; r.errorReceiptPending = true;
        });
    }

    static void tick() throws IOException {
        flushCloseDiagnostic();
        var r = run; if (r == null || r.finished) { return; }
        flushReceipts(r); // Ordinary Post callback, outside native connection/field monitors.
        require(r.server.isSameThread() && r.failure.equals("NONE") && ++r.ticks <= 2400, "OBSERVER_OR_CASE_DEADLINE");
        if (!r.accepted || r.mode == Mode.SPAWN_CALLBACK_REMOVE) { return; }
        if (!r.closeRequested && r.projectile != null && r.projectile.tickCount > 0) {
            require(r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN && count(r) == 1
                    && r.permit.qualifiedActor(r.server, r.projectile) == r.actor, "OPEN_BEFORE_REAL_CLIENT_LEAVE");
            r.closeRequested = true; P11C4aEvidence.cue(r.output, "a-terminal-close.ready"); return;
        }
        if (r.mode == Mode.DEADLINE && r.deadlineReturns == 1) { finish(r, "ORIGINAL_DEADLINE_CLOSED_WORK"); }
        if (r.mode == Mode.LOGOUT_CLEANUP_FAULT && r.connectedTickNormal && count(r) == 0) {
            require(r.outerCatch == 0 && r.peerConnection.isConnected()
                    && r.peer.connection.getConnection() == r.peerConnection
                    && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer,
                    "CONNECTED_ERROR_ROUTE_PEER_CONTINUES");
            finish(r, "FAILED_LOGOUT_CONNECTED_NATIVE_CATCH_WORK_CLOSED");
        }
    }

    static void rootRetired(MinecraftServer server) {
        if (closeDiagnostic != null && closeDiagnostic.server == server) { flushCloseDiagnostic(); }
        var r = run; if (r == null || server != r.server || r.finished) { return; }
        observe(r, () -> {
            flushReceipts(r);
            require(r.outerCatch == 1 && (r.mode == Mode.SPAWN_CALLBACK_REMOVE ? r.postThrows == 1
                    : r.logoutThrows == 1 && !r.connectedTickNormal), "ORIGINAL_ERROR_STOP_ROUTE");
            finish(r, "ORIGINAL_ERROR_STOP_WORK_TERMINAL_NOT_NORMAL_STOP");
        });
        if (!r.failure.equals("NONE")) {
            try { flushReceipts(r); } catch (IOException ignored) { /* Preserve original stop. */ }
        }
    }

    static void claimed(Object disposition) {
        var r = run; if (r != null && !r.finished && disposition == RuntimePermitClaimDisposition.QUEUED) { r.claims++; fail(r); }
    }
    static void damageEntering(DamageSource source) {
        var r = run; if (r != null && !r.finished && r.projectile != null && source.getDirectEntity() == r.projectile) { r.damage++; fail(r); }
    }
    static boolean closeRequested() { return run != null && run.closeRequested; }
    static boolean complete() { return run != null && run.finished && run.failure.equals("NONE"); }
    static boolean nativeErrorStop() { return run != null && run.outerCatch != 0; }
    static String failureCode() { return run == null ? "NOT_ARMED" : run.failure; }
    static void release() { run = null; closeDiagnostic = null; }

    /** Fixed observational snapshots only; no qualification or lifecycle mutation. */
    private static boolean closeDiagnosticSelected() {
        return switch (System.getProperty("gramarye.p11.online.case", "")) {
            case "l1-work-deadline", "l1-logout-cleanup-fault", "l1-natural-unload" -> true;
            default -> false;
        };
    }

    private static void closeDiagnosticCreated(ServerPlayer actor, Object value) {
        if (!closeDiagnosticSelected() || closeDiagnostic != null) { return; }
        try {
            if (actor == null || !actor.getServer().isSameThread() || !(value instanceof ServerSlot.InstanceState instance)
                    || !instance.hasP9AuthenticatedActorWitness(actor)) { return; }
            var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
            var body = owner == null ? null : owner.body(actor);
            if (body != null) { closeDiagnostic = new CloseDiagnostic(actor, instance, owner, body); }
        } catch (RuntimeException | Error ignoredDiagnostic) { /* Never replaces original admission. */ }
    }

    private static void closeDiagnosticAccepted(MinecraftServer server, ServerPlayer actor, Object result) {
        var d = closeDiagnostic;
        if (d == null || actor != d.actor || server != d.server) { return; }
        try {
            d.accepted = server.isSameThread() && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly accepted
                    && d.instance.id.equals(accepted.eventToken().skillInstanceId()) && d.instance.work != null;
        } catch (RuntimeException | Error ignoredDiagnostic) { d.failed = true; }
    }

    private static void closeDiagnosticTransferred(Object value, Object disposition) {
        var d = closeDiagnostic;
        if (d == null || !d.accepted || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        try {
            var permit = d.instance.activeProjectileContinuation;
            if (value instanceof P9StarterProjectile projectile && permit != null
                    && projectile.hasContinuationPermitIdentity(permit) && permit.plannedProjectileId.equals(projectile.getUUID())) {
                d.permit = permit; d.projectile = projectile;
            }
        } catch (RuntimeException | Error ignoredDiagnostic) { d.failed = true; }
    }

    public static boolean closeDiagnosticTickEntering(Object projectile) {
        var d = closeDiagnostic;
        if (d == null || !d.accepted || projectile != d.projectile || d.tickEntry != null) { return false; }
        try {
            if (!d.server.isSameThread() || d.connection.isConnected()) { return false; }
            d.tickBeforeLogout = d.logoutEntry == null;
            d.tickEntry = closeSnapshot(d, false, false);
            return true;
        } catch (RuntimeException | Error ignoredDiagnostic) { d.failed = true; return false; }
    }

    public static void closeDiagnosticTickFinished(boolean selected, boolean normal) {
        var d = closeDiagnostic;
        if (!selected || d == null) { return; }
        try { d.tickReturn = closeSnapshot(d, true, normal); }
        catch (RuntimeException | Error ignoredDiagnostic) { d.failed = true; }
    }

    public static boolean closeDiagnosticLogoutEntering(ServerGamePacketListenerImpl listener) {
        var d = closeDiagnostic;
        if (d == null || !d.accepted || listener != d.listener || d.logoutEntry != null) { return false; }
        try {
            if (!d.server.isSameThread()) { return false; }
            d.logoutEntry = closeSnapshot(d, false, false); return true;
        } catch (RuntimeException | Error ignoredDiagnostic) { d.failed = true; return false; }
    }

    public static void closeDiagnosticLogoutFinished(boolean selected, boolean normal) {
        var d = closeDiagnostic;
        if (!selected || d == null) { return; }
        try { d.logoutReturn = closeSnapshot(d, true, normal); }
        catch (RuntimeException | Error ignoredDiagnostic) { d.failed = true; }
    }

    private static java.util.Map<String, Object> closeSnapshot(CloseDiagnostic d, boolean returned, boolean normal) {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("serverTick", d.server.getTickCount()); facts.put("owningThread", d.server.isSameThread());
        facts.put("originalReturnedOrThrew", returned); facts.put("originalNormal", returned && normal);
        facts.put("connectionConnected", d.connection.isConnected()); facts.put("acceptingMessages", d.listener.isAcceptingMessages());
        facts.put("sameActorListener", d.actor.connection == d.listener); facts.put("listenerPlayerExact", d.listener.player == d.actor);
        facts.put("packetListenerExact", d.connection.getPacketListener() == d.listener);
        facts.put("rosterExact", d.server.getPlayerList().getPlayer(d.actor.getUUID()) == d.actor);
        facts.put("actorRemoved", d.actor.isRemoved()); facts.put("actorDeadOrDying", d.actor.isDeadOrDying());
        facts.put("actorHealthPositive", d.actor.getHealth() > 0); facts.put("workPresent", d.instance.work != null);
        facts.put("workQualifiesExactActor", d.instance.work != null && d.instance.work.qualifies(d.actor));
        facts.put("logoutState", d.instance.logoutState.name()); facts.put("logoutScopePresent", d.instance.logoutScope != null);
        facts.put("pinClosed", d.instance.lease.pin.isClosed()); facts.put("sourceBodyExact", d.owner.body(d.actor) == d.body);
        facts.put("accountCurrentExact", d.body.account.current == d.body); facts.put("sourceCanCopy", d.owner.canCopy(d.body));
        facts.put("bodyComplete", d.body.complete); facts.put("bodyFault", d.body.fault.name());
        facts.put("cleanupUnknown", d.body.account.cleanupUnknown); facts.put("envelopePresent", d.body.envelope != null);
        facts.put("bodyLogoutAttempted", d.body.logoutAttempted); facts.put("bodyLogoutActive", d.body.logoutActive);
        facts.put("permitState", d.permit == null ? "ABSENT" : d.permit.state.name());
        facts.put("projectileAge", d.projectile == null ? -1 : d.projectile.tickCount);
        facts.put("projectileRemoved", d.projectile != null && d.projectile.isRemoved());
        facts.put("sameActorProjectileLevel", d.projectile != null && d.projectile.level() == d.actor.level());
        facts.put("loadedProjectileExact", d.projectile != null && d.projectile.level() instanceof ServerLevel level
                && level.getServer() == d.server && d.server.getLevel(level.dimension()) == level
                && level.getEntity(d.projectile.getUUID()) == d.projectile);
        facts.put("terminalReason", d.instance.p9Diagnostic == null || d.instance.p9Diagnostic.terminalReason == null
                ? "ABSENT" : d.instance.p9Diagnostic.terminalReason.name());
        return java.util.Map.copyOf(facts);
    }

    /** Only ordinary Post or original root retirement flushes; never connection/tick-field monitors. */
    private static void flushCloseDiagnostic() {
        var d = closeDiagnostic;
        if (d == null || d.written || !d.server.isSameThread() || d.tickReturn == null && d.logoutReturn == null) { return; }
        try {
            var facts = new LinkedHashMap<String, Object>();
            facts.put("status", "FIXED_ORIGINAL_CLOSE_BOUNDARIES_NOT_ACCEPTANCE"); facts.put("observerFailed", d.failed);
            facts.put("actualAcceptedWork", d.accepted); facts.put("firstClosedTickBeforeWholeLogoutEntry", d.tickBeforeLogout);
            facts.put("tickEntry", d.tickEntry == null ? java.util.Map.of("status", "UNOBSERVED") : d.tickEntry);
            facts.put("tickReturn", d.tickReturn == null ? java.util.Map.of("status", "UNOBSERVED") : d.tickReturn);
            facts.put("wholeLogoutEntry", d.logoutEntry == null ? java.util.Map.of("status", "UNOBSERVED") : d.logoutEntry);
            facts.put("wholeLogoutReturn", d.logoutReturn == null ? java.util.Map.of("status", "UNOBSERVED") : d.logoutReturn);
            d.written = true;
            P11C4aEvidence.write(P11C4aEvidence.root().resolve("server"), "client-close-diagnostic.json", facts);
        } catch (IOException | RuntimeException | Error ignoredDiagnostic) { d.failed = true; }
    }

    private static final class CloseDiagnostic {
        final MinecraftServer server; final ServerPlayer actor; final ServerGamePacketListenerImpl listener;
        final Connection connection; final ServerSlot.InstanceState instance;
        final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body body;
        RuntimeProjectileContinuationPermit permit; P9StarterProjectile projectile;
        java.util.Map<String, Object> tickEntry, tickReturn, logoutEntry, logoutReturn;
        boolean accepted, tickBeforeLogout, failed, written;
        CloseDiagnostic(ServerPlayer actor, ServerSlot.InstanceState instance,
                P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.actor = actor; this.server = actor.getServer(); this.listener = actor.connection;
            this.connection = listener.getConnection(); this.instance = instance; this.owner = owner; this.body = body;
        }
    }

    private static void finish(Run r, String status) throws IOException {
        require(r.failure.equals("NONE") && r.instance != null && r.permit != null && count(r) == 0
                && r.instance.work == null && r.instance.p9ActorWitness() == null && r.instance.logoutScope == null
                && r.instance.activeProjectileContinuation == null && r.instance.lease.pin.isClosed()
                && r.permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                && r.projectile.isRemoved() && r.claims == 0 && r.damage == 0
                && r.actor.totalExperience == r.xp && r.actor.getScore() == r.score, "REAL_TERMINAL_ZERO_EFFECTS");
        if (r.mode == Mode.SPAWN_CALLBACK_REMOVE) {
            require(r.addCallbacks == 1 && r.addTrue == 1 && r.rejectedTransfers == 1 && r.transfers == 0
                    && r.runtimeFaultReturns > 0, "REAL_ADD_REMOVE_REJECT_FAULT");
        }
        write(r, "terminal-boundary-result.json", status); r.finished = true;
    }
    private static long count(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()]; }
    private static void current(Run r) {
        require(r.server.isSameThread() && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor
                && r.connection.isConnected() && r.connection.getPacketListener() == r.actor.connection
                && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer && r.peer.connection.getConnection().isConnected(),
                "GENUINE_CURRENT_ACTORS");
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_TERMINAL_" + code); }
    private static void fail(Run r) { if (r.failure.equals("NONE")) { r.failure = "TERMINAL_BOUNDARY_OBSERVER"; } }
    @FunctionalInterface private interface Observation { void run() throws IOException; }
    private static void observe(Run r, Observation observation) {
        try { observation.run(); } catch (RuntimeException | Error | IOException ignored) { fail(r); }
    }
    private static void flushReceipts(Run r) throws IOException {
        if (r.logoutReceiptPending) {
            r.logoutReceiptPending = false;
            write(r, "terminal-boundary-logout.json", "ORIGINAL_WHOLE_LOGOUT_FINALLY_OBSERVED");
        }
        if (r.errorReceiptPending) {
            r.errorReceiptPending = false;
            write(r, "terminal-boundary-native-error.json", "ORIGINAL_NATIVE_ERROR_RECEIVER");
        }
        if (!r.failure.equals("NONE") && !r.failureWritten) {
            r.failureWritten = true; write(r, "terminal-boundary-failure.json", "FAIL");
        }
    }
    private static void write(Run r, String leaf, String status) throws IOException {
        var f = new LinkedHashMap<String, Object>();
        f.put("status", status); f.put("mode", r.mode.name()); f.put("failure", r.failure);
        f.put("accepted", r.accepted); f.put("workNow", count(r)); f.put("nativeOpenTransfers", r.transfers);
        f.put("onAddedReturns", r.addCallbacks); f.put("originalAddTrue", r.addTrue); f.put("originalDiscardReturn", r.discardReturned);
        f.put("originalRejectedTransfer", r.rejectedTransfers); f.put("originalRuntimeFaultReturns", r.runtimeFaultReturns);
        f.put("originalPostPrimaryThrows", r.postThrows); f.put("wholeLogoutEntries", r.logoutEntries);
        f.put("wholeLogoutNormal", r.logoutNormal); f.put("wholeLogoutSamePrimaryThrows", r.logoutThrows);
        f.put("ownedLeaveFaults", r.leaveFaults); f.put("cleanupUnknown", r.body.account.cleanupUnknown);
        f.put("originalConnectedCatch", r.connectedCatch); f.put("connectedTickNormal", r.connectedTickNormal);
        f.put("originalRunServerCatch", r.outerCatch); f.put("wetNativeTicks", r.wetTicks);
        f.put("observedNativeTravel", r.travel); f.put("originalDeadline", r.permit == null ? -1 : r.permit.deadlineRuntimeTick);
        f.put("actualDeadlineSweepTick", r.sweepTick); f.put("actualDeadlineSweepAge", r.sweepAge);
        f.put("originalDeadlineSweepReturns", r.deadlineReturns); f.put("claims", r.claims); f.put("hurtEntries", r.damage);
        f.put("pinClosed", r.instance != null && r.instance.lease.pin.isClosed());
        f.put("terminalReason", r.instance == null || r.instance.p9Diagnostic == null || r.instance.p9Diagnostic.terminalReason == null
                ? "ABSENT" : r.instance.p9Diagnostic.terminalReason.name());
        f.put("originalErrorPolicyReplaced", false); f.put("physicalUnloadClaim", false); f.put("physicalOsInputClaim", false);
        P11C4aEvidence.write(r.output, leaf, f);
    }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final ServerLevel level; final Connection connection, peerConnection;
        final Path output; final Mode mode; final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body body;
        final int xp, score; final RuntimeException ownedLeaveFault = new RuntimeException("P11_L1_OWNED_LEAVE_FAULT");
        ServerSlot.InstanceState instance; P11QualifiedSourceOwner.WorkReservation work;
        P9StarterProjectile projectile; RuntimeProjectileContinuationPermit permit; Vec3 lastPosition; Throwable primary;
        int ticks, transfers, addCallbacks, addTrue, rejectedTransfers, runtimeFaultReturns, postThrows;
        int logoutEntries, logoutNormal, logoutThrows, leaveFaults, connectedCatch, outerCatch, wetTicks, sweepAge, deadlineReturns, claims, damage;
        long sweepTick = -1; double travel; boolean accepted, waterPrepared, discardReturned, inLogout, closeRequested, connectedTickNormal, finished;
        boolean logoutReceiptPending, errorReceiptPending, failureWritten;
        String failure = "NONE";
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode,
                P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.server = server; this.actor = actor; this.peer = peer; this.output = output; this.mode = mode;
            this.owner = owner; this.body = body; this.level = actor.serverLevel(); this.connection = actor.connection.getConnection();
            this.peerConnection = peer.connection.getConnection();
            this.xp = actor.totalExperience; this.score = actor.getScore();
        }
    }
}
