package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/** Two real accepted works; the only causes produced here are original reload or halt. */
public final class P11L1WorkBoundaryProbe {
    enum Mode { TWO_WORK_RELOAD, TWO_WORK_STOP }
    private static Run run;
    private P11L1WorkBoundaryProbe() {}

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode)
            throws IOException {
        require(run == null && mode != null && server.isSameThread() && actor != peer && actor.getServer() == server
                && peer.getServer() == server && !actor.getUUID().equals(peer.getUUID()), "ARM_IDENTITY");
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        require(body != null && source.canSerialize(body) && source.canCopy(body)
                && body.account.current == body && source.body(peer) != null, "QUALIFIED_REAL_ACCOUNTS");
        require(body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0, "INITIAL_WORK_NOT_ZERO");
        run = new Run(server, actor, peer, output, mode, source, body);
        current(run);
        require(otherRoots(run) == 0, "INITIAL_NATIVE_CAUSE_NOT_ZERO");
        P11C4aEvidence.write(output, "two-work-armed.json", facts(run, "ARMED_NOT_QUALIFIED"));
        P11C4aEvidence.cue(output, "a-cast-1.ready");
    }

    /** Called by the existing genuine instance-constructor observer, before its positive-case filter. */
    static void instanceCreated(ServerPlayer actor, Object value) {
        var r = run;
        if (r == null || r.finished || actor != r.actor) { return; }
        try {
            require(r.server.isSameThread() && !r.actionStarted && r.created < 2
                    && value instanceof ServerSlot.InstanceState, "UNEXPECTED_INSTANCE");
            var instance = (ServerSlot.InstanceState) value;
            require(instance.hasP9AuthenticatedActorWitness(actor) && instance.work == null,
                    "CONSTRUCTOR_WITNESS_OR_EARLY_RESERVATION");
            r.instances[r.created++] = instance;
        } catch (RuntimeException | Error ignored) { fail(r, "INSTANCE_OBSERVER"); }
    }

    /** Receives the unchanged original P7-to-P5 admission result; never calls admission. */
    static void accepted(MinecraftServer server, ServerPlayer actor, Object result) {
        var r = run;
        if (r == null || r.finished || actor != r.actor) { return; }
        try {
            require(server == r.server && server.isSameThread() && !r.actionStarted && r.accepted < 2,
                    "ADMISSION_ORDER");
            require(result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "SECOND_ADMISSION_NOT_ACCEPTED");
            var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) result;
            var instance = r.instances[r.accepted];
            require(r.created == r.accepted + 1 && instance != null
                    && instance.id.equals(accepted.eventToken().skillInstanceId()) && instance.work != null
                    && instance.work.qualifies(actor) && !instance.lease.pin.isClosed()
                    && instance.logoutState == SkillRuntimeService.LogoutState.ONLINE, "ACCEPTED_EXACT_WORK");
            current(r);
            r.work[r.accepted] = instance.work;
            r.acceptanceCounts[r.accepted] = workCount(r);
            r.retainedCounts[r.accepted] = r.source.diagnostics(actor.getUUID()).resources().retainedUuids();
            require(r.acceptanceCounts[r.accepted] == r.accepted + 1, "SAME_ACCOUNT_WORK_COUNT");
            var root = r.body.account.nativeRoots[P11ControlBudgets.Root.WORK.ordinal()];
            require(root != null, "ACTUAL_AGGREGATE_WORK_RESERVATION");
            if (r.accepted == 0) { r.aggregateWorkRoot = root; }
            if (r.accepted == 1) {
                require(r.instances[0] != instance && !r.instances[0].id.equals(instance.id)
                        && r.work[0] != instance.work && r.instances[0].lease == instance.lease
                        && r.aggregateWorkRoot == root, "TWO_WORK_ONE_ACCOUNT_AND_REVISION_LEASE");
            }
            r.accepted++;
        } catch (RuntimeException | Error ignored) { fail(r, "ADMISSION_OBSERVER"); }
    }

    static void transferred(Object value, Object disposition) {
        var r = run;
        if (r == null || r.finished || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        try {
            require(value instanceof P9StarterProjectile && r.server.isSameThread(), "TRANSFER_TYPE");
            var projectile = (P9StarterProjectile) value;
            int slot = -1;
            for (int i = 0; i < r.accepted; i++) {
                var permit = r.instances[i].activeProjectileContinuation;
                if (permit != null && permit.plannedProjectileId.equals(projectile.getUUID())) { slot = i; }
            }
            require(slot >= 0 && r.projectiles[slot] == null && !r.actionStarted, "TRANSFER_EXACT_INSTANCE");
            var permit = r.instances[slot].activeProjectileContinuation;
            require(permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    && projectile.getOwner() == r.actor && permit.qualifiedActor(r.server, projectile) == r.actor
                    && r.actor.serverLevel().getEntity(projectile.getUUID()) == projectile, "ORIGINAL_OPEN_PROJECTILE");
            r.permits[slot] = permit;
            r.projectiles[slot] = projectile;
        } catch (RuntimeException | Error ignored) { fail(r, "TRANSFER_OBSERVER"); }
    }

    static void claimed(Object disposition) {
        var r = run;
        if (r != null && !r.finished && disposition == RuntimePermitClaimDisposition.QUEUED) {
            r.claims++; fail(r, "EMPTY_RAY_CLAIMED_A_CHILD");
        }
    }

    static void damageEntering(DamageSource source) {
        var r = run;
        if (r == null || r.finished) { return; }
        if (r.projectiles[0] != null && source.getDirectEntity() == r.projectiles[0]
                || r.projectiles[1] != null && source.getDirectEntity() == r.projectiles[1]) {
            r.damageEntries++; fail(r, "EMPTY_RAY_ENTERED_DAMAGE");
        }
    }

    static void experience(ServerPlayer actor, int amount) {
        var r = run;
        if (r != null && !r.finished && actor == r.actor && amount != 0) {
            r.experienceEntries++; fail(r, "EMPTY_RAY_EXPERIENCE_CHANGED");
        }
    }

    /** Normal server Post tick only; no held callback, sleep, queue surgery or clock adjustment. */
    static void tick() throws IOException {
        var r = run;
        if (r == null || r.finished) { return; }
        require(r.server.isSameThread(), "TICK_THREAD");
        require(r.failure.equals("NONE"), r.failure);
        require(++r.ticks <= 160, "NATURAL_OVERLAP_OR_TERMINAL_NOT_OBSERVED");
        if (r.actionStarted) { return; }
        current(r);
        require(r.actor.totalExperience == r.initialExperience && r.actor.getScore() == r.initialScore,
                "UNEXPECTED_REWARD_BEFORE_BOUNDARY");
        if (r.accepted == 1 && r.projectiles[0] != null && !r.secondCue) {
            open(r, 0);
            if (r.projectiles[0].tickCount == 0) { return; }
            r.secondCue = true;
            P11C4aEvidence.cue(r.output, "a-cast-2.ready");
        }
        if (r.accepted != 2 || r.projectiles[1] == null) { return; }
        open(r, 0); open(r, 1);
        require(workCount(r) == 2 && otherRoots(r) == 0 && r.releases == 0, "TWO_OPEN_NO_OTHER_CAUSE");
        r.openAges[0] = r.projectiles[0].tickCount;
        r.openAges[1] = r.projectiles[1].tickCount;
        r.releaseSequence[0] = workCount(r);
        r.actionStarted = true;
        P11C4aEvidence.write(r.output, "two-work-before-boundary.json", facts(r, "TWO_GENUINE_OPEN_WORKS"));
        if (r.mode == Mode.TWO_WORK_RELOAD) {
            r.reloadCalls++;
            var future = r.server.reloadResources(r.server.getPackRepository().getSelectedIds());
            r.reloadReturned = true;
            require(future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled(), "ORIGINAL_RELOAD_NOT_COMPLETED");
            terminalWork(r);
            current(r);
            r.finished = true;
            P11C4aEvidence.write(r.output, "two-work-result.json", facts(r, "NATIVE_TWO_WORK_RELOAD_OBSERVED"));
        } else {
            r.haltCalls++;
            r.server.halt(false);
            r.haltReturned = true;
        }
    }

    /** Hooked only around InstanceState.releaseWork. Observer failures never replace original failure. */
    public static int beforeRelease(Object value) {
        var r = run;
        if (r == null || r.finished) { return -1; }
        int slot = index(r, value);
        if (slot < 0 || r.instances[slot].work == null) { return -1; }
        try {
            require(r.server.isSameThread() && r.actionStarted && r.accepted == 2 && r.releases < 2
                    && !r.releaseEntered[slot] && r.instances[slot].work == r.work[slot], "RELEASE_ENTRY_IDENTITY");
            r.releaseEntered[slot] = true;
            r.releaseBefore[slot] = workCount(r);
            require(r.releaseBefore[slot] == 2 - r.releases, "RELEASE_BEFORE_COUNT");
            return slot;
        } catch (RuntimeException | Error ignored) { fail(r, "RELEASE_ENTRY_OBSERVER"); return -1; }
    }

    public static void afterRelease(int slot, boolean normal) {
        var r = run;
        if (r == null || slot < 0 || slot >= 2) { return; }
        try {
            require(normal && r.releaseEntered[slot] && !r.releaseReturned[slot]
                    && r.instances[slot].work == null, "RELEASE_ORIGINAL_RETURN");
            r.releaseAfter[slot] = workCount(r);
            require(r.releaseAfter[slot] == 1 - r.releases, "RELEASE_AFTER_COUNT");
            var root = r.body.account.nativeRoots[P11ControlBudgets.Root.WORK.ordinal()];
            require(r.releases == 0 ? root == r.aggregateWorkRoot : root == null,
                    "AGGREGATE_ROOT_MUST_SURVIVE_FIRST_RELEASE_ONLY");
            r.releaseReturned[slot] = true;
            r.releases++;
            r.releaseSequence[r.releases] = r.releaseAfter[slot];
        } catch (RuntimeException | Error ignored) { fail(r, "RELEASE_RETURN_OBSERVER"); }
    }

    static void rootRetired(MinecraftServer exact) {
        var r = run;
        if (r == null || r.finished || exact != r.server) { return; }
        try {
            require(r.mode == Mode.TWO_WORK_STOP && r.actionStarted && r.haltCalls == 1 && r.haltReturned,
                    "UNEXPECTED_ROOT_RETIREMENT");
            terminalWork(r);
            var summary = P11NativeStorageBoundary.terminalDiagnostics();
            require(summary != null && summary.nativeStopNormal() && summary.failures() == 0
                    && summary.nativeResponsibilities().roots().stream().filter(value -> value.kind().equals("WORK"))
                            .mapToLong(value -> value.count()).sum() == 0, "ORIGINAL_STOP_SOURCE_TERMINAL");
            r.finished = true;
            P11C4aEvidence.write(r.output, "two-work-result.json", facts(r, "NATIVE_TWO_WORK_STOP_OBSERVED"));
        } catch (RuntimeException | Error | IOException ignored) {
            fail(r, "ROOT_RETIREMENT_OBSERVER");
            try { P11C4aEvidence.write(r.output, "two-work-failure.json", facts(r, "FAIL")); }
            catch (IOException ignoredWrite) { /* Fixed facts only; no secondary replaces native stop. */ }
        }
    }

    static boolean complete() { return run != null && run.finished && run.failure.equals("NONE"); }
    static boolean stopRequested() { return run != null && run.mode == Mode.TWO_WORK_STOP && run.haltCalls == 1; }
    static String failureCode() { return run == null ? "NOT_ARMED" : run.failure; }
    static void release() { run = null; }

    private static void current(Run r) {
        require(r.server.isSameThread() && r.actor.connection.getConnection() == r.connection
                && r.connection.isConnected() && r.connection.getPacketListener() == r.actor.connection
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor && !r.actor.isRemoved()
                && r.peer.connection.getConnection() == r.peerConnection && r.peerConnection.isConnected()
                && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer
                && r.source.body(r.actor) == r.body && r.body.account.current == r.body
                && r.body.source.epoch() == r.epoch && r.source.canSerialize(r.body), "EXACT_CURRENT_ACTORS_AND_SOURCE");
    }

    private static void open(Run r, int i) {
        require(r.projectiles[i] != null && !r.projectiles[i].isRemoved() && r.instances[i].work == r.work[i]
                && r.instances[i].activeProjectileContinuation == r.permits[i]
                && r.permits[i].state == RuntimeProjectileContinuationPermit.State.OPEN
                && r.permits[i].qualifiedActor(r.server, r.projectiles[i]) == r.actor
                && r.actor.serverLevel().getEntity(r.projectiles[i].getUUID()) == r.projectiles[i],
                "NATURAL_OPEN_WORK_LOST_BEFORE_BOUNDARY");
    }

    private static void terminalWork(Run r) {
        require(r.failure.equals("NONE") && r.releases == 2 && workCount(r) == 0
                && r.claims == 0 && r.damageEntries == 0 && r.experienceEntries == 0
                && r.actor.totalExperience == r.initialExperience && r.actor.getScore() == r.initialScore,
                "WORK_TERMINAL_OR_ZERO_EFFECTS");
        for (int i = 0; i < 2; i++) {
            require(r.instances[i].work == null && r.instances[i].p9ActorWitness() == null
                    && r.instances[i].logoutScope == null && r.instances[i].logoutState == SkillRuntimeService.LogoutState.INVALID
                    && r.instances[i].activeProjectileContinuation == null && r.instances[i].lease.pin.isClosed()
                    && r.permits[i].state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                    && r.projectiles[i].isRemoved(), "ORIGINAL_PERMIT_PIN_WITNESS_CLEANUP");
        }
    }

    private static int index(Run r, Object value) {
        for (int i = 0; i < 2; i++) { if (r.instances[i] == value) { return i; } }
        return -1;
    }
    private static long workCount(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()]; }
    private static long otherRoots(Run r) {
        return r.body.account.nativeCounts[P11ControlBudgets.Root.NATIVE_CREDIT.ordinal()]
                + r.body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()]
                + r.body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()];
    }
    private static void fail(Run r, String code) { if (r.failure.equals("NONE")) { r.failure = code; } }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_TWO_WORK_" + code); }

    private static LinkedHashMap<String, Object> facts(Run r, String status) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", status); result.put("mode", r.mode.name()); result.put("failure", r.failure);
        result.put("actualAcceptedWorks", r.accepted); result.put("createdInstances", r.created);
        result.put("workAtAccept", List.of(r.acceptanceCounts[0], r.acceptanceCounts[1]));
        result.put("retainedUuidAtAccept", List.of(r.retainedCounts[0], r.retainedCounts[1]));
        result.put("retainedUuidCountsAreGlobalDiagnostics", true);
        result.put("sameAccountAndSharedRevisionLease", r.accepted == 2 && r.instances[0].lease == r.instances[1].lease);
        result.put("sameAggregateWorkReservation", r.accepted == 2 && r.aggregateWorkRoot != null);
        result.put("naturalOpenAges", List.of(r.openAges[0], r.openAges[1]));
        result.put("releaseBeforeByInstance", List.of(r.releaseBefore[0], r.releaseBefore[1]));
        result.put("releaseAfterByInstance", List.of(r.releaseAfter[0], r.releaseAfter[1]));
        result.put("observedReleaseSequence", List.of(r.releaseSequence[0], r.releaseSequence[1], r.releaseSequence[2]));
        result.put("originalReleaseReturns", r.releases); result.put("workNow", workCount(r));
        result.put("permitStates", java.util.Arrays.stream(r.permits).map(value -> value == null ? "ABSENT" : value.state.name()).toList());
        result.put("pinClosed", java.util.Arrays.stream(r.instances).map(value -> value != null && value.lease.pin.isClosed()).toList());
        result.put("witnessCleared", java.util.Arrays.stream(r.instances).map(value -> value != null && value.p9ActorWitness() == null).toList());
        result.put("projectileRemoved", java.util.Arrays.stream(r.projectiles).map(value -> value != null && value.isRemoved()).toList());
        result.put("originalReloadCalls", r.reloadCalls); result.put("reloadReturned", r.reloadReturned);
        result.put("originalHaltCalls", r.haltCalls); result.put("haltReturned", r.haltReturned);
        result.put("queuedHitChildren", r.claims); result.put("nativeDamageEntries", r.damageEntries);
        result.put("experienceEntries", r.experienceEntries); result.put("physicalInputClaim", false);
        result.put("excludedRetainedInstanceLimit", 2); result.put("coversDeathDimensionConfig", false);
        return result;
    }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final Connection connection, peerConnection;
        final Path output; final Mode mode; final P11QualifiedSourceOwner source; final P11QualifiedSourceOwner.Body body;
        final long epoch; final int initialExperience, initialScore;
        final ServerSlot.InstanceState[] instances = new ServerSlot.InstanceState[2];
        final P11QualifiedSourceOwner.WorkReservation[] work = new P11QualifiedSourceOwner.WorkReservation[2];
        final RuntimeProjectileContinuationPermit[] permits = new RuntimeProjectileContinuationPermit[2];
        final P9StarterProjectile[] projectiles = new P9StarterProjectile[2];
        final long[] acceptanceCounts = new long[2], releaseBefore = new long[2], releaseAfter = new long[2];
        final long[] releaseSequence = { -1, -1, -1 };
        final int[] retainedCounts = new int[2], openAges = new int[2];
        final boolean[] releaseEntered = new boolean[2], releaseReturned = new boolean[2];
        P11ControlBudgets.Resources.RootReservation aggregateWorkRoot;
        int created, accepted, releases, ticks, reloadCalls, haltCalls, claims, damageEntries, experienceEntries;
        boolean secondCue, actionStarted, reloadReturned, haltReturned, finished;
        String failure = "NONE";
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output, Mode mode,
                P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body) {
            this.server = server; this.actor = actor; this.peer = peer; this.output = output; this.mode = mode;
            this.connection = actor.connection.getConnection(); this.peerConnection = peer.connection.getConnection();
            this.source = source; this.body = body; this.epoch = body.source.epoch();
            this.initialExperience = actor.totalExperience; this.initialScore = actor.getScore();
        }
    }
}
