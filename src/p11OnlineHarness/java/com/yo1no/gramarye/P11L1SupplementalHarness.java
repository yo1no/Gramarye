package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Three closed supplemental cases, separate from the natural L1 logout driver. */
final class P11L1SupplementalHarness {
    private static MinecraftServer server;
    private static ServerPlayer actor, peer;
    private static Path output;
    private static ServerSlot.InstanceState created, first, second;
    private static LivingEntity oldRevisionTarget;
    private static boolean armed, finished, secondCue, reloadOverlap, ackLogoutObserved;
    private static int accepted, ticks, originalTargetDiscards;
    private P11L1SupplementalHarness() {}

    static boolean selected() {
        return java.util.List.of("l1-revision", "l1-p8-send-fault", "l1-ack-fault")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }
    static boolean revision() { return "l1-revision".equals(System.getProperty("gramarye.p11.online.case", "")); }
    static boolean ack() { return "l1-ack-fault".equals(System.getProperty("gramarye.p11.online.case", "")); }

    static void arm(MinecraftServer exact, ServerPlayer a, ServerPlayer b, Path destination) throws IOException {
        require(selected() && !armed && exact.isSameThread(), "ARM_ONCE");
        server = exact; actor = a; peer = b; output = destination; armed = true;
        if (revision()) { P11L1RevisionProbe.arm(server, actor, peer); }
        else { P11L1PacketProbe.arm(server, actor, peer, ack() ? P11L1PacketProbe.Mode.ACK_SEND : P11L1PacketProbe.Mode.P8_SEND); }
        P11C4aEvidence.cue(output, "a-cast-1.ready");
    }

    static void instanceCreated(ServerPlayer a, Object value) {
        if (!armed || finished || a != actor) { return; }
        require(server.isSameThread() && created == null && value instanceof ServerSlot.InstanceState, "ACTUAL_INSTANCE_ONCE");
        created = (ServerSlot.InstanceState) value;
    }

    static void accepted(MinecraftServer exact, ServerPlayer a, Object geometry, Object result) {
        if (!armed || finished || a != actor) { return; }
        require(server == exact && server.isSameThread() && created != null
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly
                && geometry instanceof CastGeometryExecutionDataV0 && accepted < (revision() ? 2 : 1), "ORIGINAL_ACCEPTANCE");
        var instance = created; created = null; accepted++;
        if (accepted == 1) { first = instance; } else { second = instance; }
        if (revision()) { P11L1RevisionProbe.accepted(actor, instance, result); }
        else { P11L1PacketProbe.accepted(actor, instance, result); }
        var sample = (CastGeometryExecutionDataV0) geometry;
        Vec3 position = new Vec3(sample.originX(), sample.originY(), sample.originZ());
        Vec3 motion = new Vec3(sample.directionXQ15(), sample.directionYQ15(), sample.directionZQ15()).normalize().scale(1.5);
        for (int i = 0; i < 35; i++) { position = position.add(motion); motion = motion.scale(0.99).add(0, -0.03, 0); }
        var floor = BlockPos.containing(position.x, position.y - 1, position.z);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            require(actor.serverLevel().isLoaded(floor.offset(x, 0, z)), "NATURAL_TARGET_CHUNK_LOADED");
            actor.serverLevel().setBlock(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
        }
        if (revision() && accepted == 2) {
            peer.teleportTo(peer.serverLevel(), position.x, floor.getY() + 1, position.z, java.util.Set.of(), 0, 0);
        } else {
            var victim = EntityType.COW.create(actor.serverLevel());
            require(victim != null, "ORIGINAL_TARGET_CONSTRUCTOR");
            victim.setNoAi(true); victim.setPersistenceRequired();
            victim.moveTo(position.x, floor.getY() + 1, position.z, 0, 0);
            require(actor.serverLevel().addFreshEntity(victim), "ORIGINAL_TARGET_ADD");
            if (revision()) { oldRevisionTarget = victim; }
            if (!revision()) { P11L1PacketProbe.target(victim); }
            // Actual peer remains within normal P8 tracking range, off the flight ray.
            peer.teleportTo(peer.serverLevel(), position.x + 2, floor.getY() + 1, position.z, java.util.Set.of(), 0, 0);
        }
        if (revision() && accepted == 1) {
            try { P11C4aEvidence.cue(output, "a-resource-reload.ready"); }
            catch (IOException failure) { throw new IllegalStateException("L1_SUPPLEMENTAL_CUE_IO"); }
        }
    }

    static void transferred(Object projectile, Object disposition) {
        if (!armed || finished || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        if (revision()) { P11L1RevisionProbe.transferred(projectile); }
        else { P11L1PacketProbe.transferred(projectile); }
    }
    static void damageReturned(LivingEntity target, DamageSource cause, float amount, boolean result) {
        if (!armed || finished) { return; }
        if (revision()) { P11L1RevisionProbe.damageReturned(target, cause, amount, result); }
        else { P11L1PacketProbe.damageReturned(target, cause, amount, result); }
    }
    static boolean observeAckLogout(ServerPlayer actual) {
        if (!armed || !ack() || actual != actor || !P11L1PacketProbe.expectsOriginalAckClose()) { return false; }
        require(!ackLogoutObserved, "ORIGINAL_ACK_LOGOUT_ONCE"); ackLogoutObserved = true; return true;
    }
    static boolean complete() { return finished; }

    static void tick() throws IOException {
        require(armed && server.isSameThread() && ++ticks <= 2400, "DEADLINE_OR_THREAD");
        if (finished) { return; }
        if (revision()) {
            if (!reloadOverlap && first != null && P11C4aEvidence.receiptPresent(output.getParent().resolve("client-a"), "client-resource-reload-started.json")) {
                require(first.work != null && !first.lease.pin.isClosed(), "CLIENT_RELOAD_DID_NOT_OVERLAP_LIVE_OLD_WORK");
                reloadOverlap = true;
            }
            if (!secondCue && P11L1RevisionProbe.oldEffectCompleted() && first.work == null
                    && P11C4aEvidence.receiptPresent(output.getParent().resolve("client-a"), "client-resource-reload-completed.json")) {
                require(reloadOverlap, "NO_REAL_CLIENT_RELOAD_OVERLAP");
                require(oldRevisionTarget != null && oldRevisionTarget.isAlive() && !oldRevisionTarget.isRemoved()
                        && first.lease.pin.isClosed(), "OLD_WORK_TERMINAL_BEFORE_OWNED_TARGET_CLEANUP");
                oldRevisionTarget.discard(); originalTargetDiscards++;
                actor.teleportTo(actor.serverLevel(), 0.5, 100, 0.5, java.util.Set.of(), 0, 0);
                secondCue = true; P11C4aEvidence.cue(output, "a-cast-2.ready");
            }
            if (second == null || second.work != null) { return; }
            seal(P11L1RevisionProbe.finish());
        } else {
            if (!P11L1PacketProbe.readyToFinish() || ack() && !ackLogoutObserved) { return; }
            seal(P11L1PacketProbe.finish());
        }
    }

    private static void seal(Map<String, Object> probeFacts) throws IOException {
        server.getPlayerList().saveAll();
        var facts = new LinkedHashMap<String, Object>(probeFacts);
        facts.put("actualAcceptedWorks", accepted); facts.put("clientReloadOverlappedOldLiveWork", reloadOverlap);
        facts.put("originalOldTargetDiscardsAfterWorkTerminal", originalTargetDiscards);
        facts.put("normalSchedulerTicks", ticks); facts.put("serverStillRunning", server.isRunning() && !server.isStopped());
        facts.put("peerSameCurrentActor", server.getPlayerList().getPlayer(peer.getUUID()) == peer && peer.connection.getConnection().isConnected());
        var observation = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
        facts.put("sourceAfterOriginalSaveAll", P11C4aEvidence.sourceObservation(observation));
        require(server.isRunning() && !server.isStopped() && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && peer.connection.getConnection().isConnected(), "PEER_OR_SERVER_LOST");
        facts.put("writerDirtyClearedHere", !ack());
        facts.put("detachedDurabilityRequiresOriginalStop", ack());
        facts.put("actualOriginalAckFailureLogoutObserved", ackLogoutObserved);
        if (!ack()) {
            for (var writer : observation.writers()) { require(!writer.dirty(), "ORIGINAL_SAVE_LEFT_DIRTY_SOURCE"); }
        }
        P11C4aEvidence.write(output, "supplemental-result.json", facts);
        finished = true;
    }

    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "L1_SUPPLEMENTAL_" + code); }
}
