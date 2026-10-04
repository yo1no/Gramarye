package com.yo1no.gramarye;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Excluded single-callsite fault observer; never sends, hurts, disconnects, or retries. */
public final class P11L1PacketProbe {
    public enum Mode { P8_SEND, ACK_SEND }
    private static Run active;
    private P11L1PacketProbe() {}

    public static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Mode mode) {
        String expected = mode == Mode.P8_SEND ? "l1-p8-send-fault" : "l1-ack-fault";
        require(expected.equals(System.getProperty("gramarye.p11.online.case", ""))
                && active == null && server != null && server.isSameThread()
                && current(server, actor) && current(server, peer) && actor != peer
                && !actor.getUUID().equals(peer.getUUID()), "EXACT_CASE_AND_TWO_ACTORS");
        active = new Run(server, actor, peer, mode);
    }

    public static void accepted(ServerPlayer actor, Object observedInstance, Object result) {
        var run = active; if (run == null || actor != run.actor) { return; }
        require(run.server.isSameThread() && run.instance == null
                && observedInstance instanceof ServerSlot.InstanceState
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "REAL_ACCEPTANCE_ONCE");
        var instance = (ServerSlot.InstanceState) observedInstance;
        require(instance.id.equals(((RuntimeAdmissionResult.AcceptedMemoryOnly) result).eventToken().skillInstanceId())
                && instance.work != null && !instance.lease.pin.isClosed()
                && instance.hasP9AuthenticatedActorWitness(actor), "ACCEPTED_WORK_AND_PIN");
        run.instance = instance;
    }

    public static void target(LivingEntity victim) {
        var run = active; require(run != null && run.server.isSameThread() && run.victim == null
                && victim != null && victim.level().getServer() == run.server && victim.isAddedToLevel()
                && !victim.isRemoved() && victim != run.actor, "ACTUAL_TARGET"); run.victim = victim;
    }
    public static void transferred(Object value) {
        var run = active; if (run == null) { return; }
        require(run.server.isSameThread() && run.instance != null && run.projectile == null
                && value instanceof P9StarterProjectile, "ACTUAL_TRANSFER_ONCE");
        run.projectile = (P9StarterProjectile) value;
        require(run.projectile.getOwner() == run.actor && run.projectile.isAddedToLevel(), "TRANSFER_EXACT_A");
    }
    public static void damageReturned(LivingEntity target, DamageSource cause, float amount, boolean result) {
        var run = active;
        if (run == null || run.projectile == null || target != run.victim || cause.getDirectEntity() != run.projectile) { return; }
        require(run.server.isSameThread() && cause.getEntity() == run.actor && amount == 4
                && result && run.damageReturns++ == 0, "ORIGINAL_HURT_ONCE");
    }

    /** The original submitEvent has selected an actual current delivery before this scope. */
    public static boolean p8PolicyEntered(Object actualIdentity, Object value) {
        var run = active;
        if (run == null || run.mode != Mode.P8_SEND || !(value instanceof PresentationEvent event)
                || !(actualIdentity instanceof P8RecipientIdentity identity)
                || !identity.playerId().equals(run.peer.getUUID())
                || !selectedHit(run, event.kind(), event.sourceSummary())) { return false; }
        if (run.injections != 0) { run.duplicateEvent = true; return false; }
        require(run.server.isSameThread() && !run.p8Scope && run.injections == 0
                && run.damageReturns == 1, "P8_ACTUAL_APPLIED_HIT_FIRST");
        run.p8Scope = true; run.eventSequence = event.sequence();
        var source = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
        run.source = source; run.body = source == null ? null : source.body(run.actor);
        require(run.body != null && source.canCopy(run.body), "P8_EXISTING_QUALIFIED_SOURCE");
        run.epoch = run.body.source.epoch(); run.version = run.body.source.version();
        return true;
    }

    /** Unique original P8 send invoke, after its unmodified canSubmit guard. */
    public static void beforeP8Send(ServerPlayer recipient, CustomPacketPayload payload) {
        var run = active;
        if (run == null || run.mode != Mode.P8_SEND || !run.p8Scope || recipient != run.peer
                || !(payload instanceof PresentationEventPayload event)
                || event.sequence() != run.eventSequence || !selectedHit(run, event.kind(), event.sourceSummary())) { return; }
        require(run.server.isSameThread() && current(run.server, recipient)
                && recipient.connection.getConnection() == run.peerConnection && run.injections == 0,
                "P8_EXACT_NATIVE_SEND");
        run.injections++; throw run.primary;
    }

    /** Finally observer cannot replace a primary. Failure is retained as a bounded false fact. */
    public static void p8PolicyFinished(boolean selected, boolean normal, Throwable escaping) {
        if (!selected) { return; }
        var run = active; if (run == null) { return; }
        try {
            run.p8Scope = false;
            if (run.injections == 1) {
                run.policyReturns++;
                run.p8Normal = normal && escaping == null;
                run.sourceUnchanged = run.source.body(run.actor) == run.body && run.source.canCopy(run.body)
                        && run.body.source.epoch() == run.epoch && run.body.source.version() == run.version;
            }
        } catch (RuntimeException | Error observerFailure) { run.observerFailed = true; }
    }

    /** Exact original ACK accept wrapper, after the real accepted result exists. */
    public static boolean ackPolicyEntered(UUID actualActorId, long actualSequence) {
        var run = active;
        if (run == null || run.mode != Mode.ACK_SEND || run.instance == null || run.injections != 0
                || !run.actor.getUUID().equals(actualActorId)) { return false; }
        require(run.server.isSameThread() && !run.ackScope && current(run.server, run.actor)
                && actualSequence > 0, "ACK_SCOPE");
        run.ackSequence = actualSequence; run.ackScope = true; return true;
    }
    public static void beforeAcceptedAck(ServerPlayer actor, long actualSequence) {
        var run = active;
        if (run == null || run.mode != Mode.ACK_SEND || !run.ackScope || actor != run.actor) { return; }
        require(run.server.isSameThread() && current(run.server, actor)
                && actor.connection.getConnection() == run.actorConnection
                && actualSequence == run.ackSequence && run.injections == 0 && run.instance.work != null,
                "ACK_EXACT_ACCEPTED_SEND");
        run.injections++; throw run.primary;
    }
    public static void ackPolicyFinished(boolean selected, boolean normal, Throwable escaping) {
        if (!selected) { return; }
        var run = active; if (run == null) { return; }
        run.ackScope = false; run.policyReturns++;
        run.ackSamePrimary = !normal && escaping == run.primary;
    }

    /** Named local result only. Parent separately proves original logout, native outcome, files, and peer. */
    public static Map<String, Object> finish() {
        var run = active;
        require(run != null && run.server.isSameThread() && run.instance != null
                && run.injections == 1 && !run.observerFailed && !run.duplicateEvent && !run.p8Scope && !run.ackScope
                && run.policyReturns == 1 && run.instance.work == null && run.instance.lease.pin.isClosed()
                && run.damageReturns <= 1, "FAULT_POLICY_AND_WORK_TERMINAL");
        if (run.mode == Mode.P8_SEND) { require(run.p8Normal && run.sourceUnchanged && run.damageReturns == 1, "P8_LOCAL_POLICY"); }
        else { require(run.ackSamePrimary && run.ackSequence > 0, "ACK_ORIGINAL_PRIMARY_POLICY"); }
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "NAMED_ORIGINAL_PACKET_FAULT_POLICY_ONLY_NOT_FULL_L1_ACCEPTANCE");
        facts.put("mode", run.mode.name()); facts.put("injections", run.injections);
        facts.put("originalPolicyTerminals", run.policyReturns); facts.put("p8ReturnedNormally", run.p8Normal);
        facts.put("ackEscapingIdenticalPrimary", run.ackSamePrimary); facts.put("actualAckSequence", run.ackSequence);
        facts.put("originalHurtReturns", run.damageReturns); facts.put("p8SourceEpochVersionUnchanged", run.sourceUnchanged);
        facts.put("workAndPinTerminal", true); facts.put("source", P11C4aEvidence.sourceObservation(
                P11NativeStorageBoundary.diagnostics(run.server, run.actor.getUUID())));
        active = null; return Map.copyOf(facts);
    }
    public static void abort() { active = null; }
    public static boolean expectsOriginalAckClose() {
        return active != null && active.mode == Mode.ACK_SEND && active.injections == 1;
    }
    public static boolean readyToFinish() {
        return active != null && active.instance != null && active.instance.work == null
                && active.injections == 1 && active.policyReturns == 1;
    }
    private static boolean selectedHit(Run run, PresentationEventKind kind, PresentationSourceSummary summary) {
        return kind == PresentationEventKind.HIT && run.victim != null
                && summary.targetEntityId().isPresent() && summary.targetEntityId().getAsInt() == run.victim.getId();
    }
    private static boolean current(MinecraftServer server, ServerPlayer actor) {
        return actor != null && actor.getServer() == server && !actor.isFakePlayer() && !actor.isRemoved()
                && actor.isAlive() && actor.connection != null && actor.connection.getConnection().isConnected()
                && actor.connection.getConnection().getPacketListener() == actor.connection
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_PACKET_" + code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final Mode mode;
        final Connection actorConnection, peerConnection;
        final RuntimeException primary = new IllegalStateException("L1_OWNED_SINGLE_PACKET_SUBMISSION_FAULT");
        ServerSlot.InstanceState instance; LivingEntity victim; P9StarterProjectile projectile;
        P11QualifiedSourceOwner source; P11QualifiedSourceOwner.Body body;
        long epoch, version, eventSequence, ackSequence; int injections, policyReturns, damageReturns;
        boolean p8Scope, ackScope, p8Normal, ackSamePrimary, sourceUnchanged, observerFailed, duplicateEvent;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Mode mode) {
            this.server = server; this.actor = actor; this.peer = peer; this.mode = mode;
            actorConnection = actor.connection.getConnection(); peerConnection = peer.connection.getConnection();
        }
    }
}
