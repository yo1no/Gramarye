package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Excluded original player-ticket departure experiment, separate from controlled visibility. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11L1NaturalUnloadProbe {
    private static Run run;
    private P11L1NaturalUnloadProbe() {}

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        require(run == null && server.isSameThread() && actor != peer && actor.getServer() == server
                && actor.serverLevel() == peer.serverLevel(), "ARM_IDENTITY");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        require(body != null && owner.canCopy(body) && owner.canSerialize(body)
                && body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0, "FRESH_QUALIFIED_SOURCE");
        var spawn = new ChunkPos(actor.serverLevel().getSharedSpawnPos());
        var target = new ChunkPos(spawn.x + 512, spawn.z + 512);
        var r = new Run(server, actor, peer, output, body, target);
        require(far(r, peer.chunkPosition()) && far(r, spawn) && !r.level.getForcedChunks().contains(target.toLong())
                && r.level.getWorldBorder().isWithinBounds(new BlockPos(target.getMiddleBlockX(), 100, target.getMiddleBlockZ())),
                "FAR_FROM_ORIGINAL_SPAWN_PEER_AND_FORCED_CHUNK");
        run = r;
        // Ordinary server teleport. Do not request/load a chunk or modify its tickets.
        actor.teleportTo(r.level, target.getMinBlockX() + 8.5, 101, target.getMinBlockZ() + 3.5,
                java.util.Set.of(), 0, 0);
        r.teleportReturns = 1;
        write(r, "natural-unload-armed.json", "ARMED_NORMAL_TELEPORT_NOT_UNLOAD_PROOF");
    }

    private static boolean far(Run r, ChunkPos other) {
        int distance = Math.max(r.server.getPlayerList().getViewDistance(), r.server.getPlayerList().getSimulationDistance()) + 4;
        return Math.max(Math.abs(r.chunk.x - other.x), Math.abs(r.chunk.z - other.z)) > distance;
    }

    private static void prepareWater(Run r) {
        for (int x = 3; x <= 13; x++) for (int z = 0; z <= 15; z++) for (int y = 73; y <= 105; y++) {
            var pos = new BlockPos(r.chunk.getMinBlockX() + x, y, r.chunk.getMinBlockZ() + z);
            require(r.level.isLoaded(pos) && r.level.isInWorldBounds(pos) && r.level.getBlockEntity(pos) == null
                    && (r.level.getBlockState(pos).isAir() || r.level.getBlockState(pos).is(Blocks.WATER)),
                    "EMPTY_ALREADY_LOADED_WATER_FIXTURE");
        }
        for (int x = 3; x <= 13; x++) for (int z = 0; z <= 15; z++) for (int y = 73; y <= 105; y++) {
            var pos = new BlockPos(r.chunk.getMinBlockX() + x, y, r.chunk.getMinBlockZ() + z);
            var block = x == 3 || x == 13 || z == 0 || z == 15 || y == 73 ? Blocks.GLASS.defaultBlockState()
                    : y == 105 ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
            r.level.setBlock(pos, block, 3);
        }
        r.water = true;
    }

    static void instanceCreated(ServerPlayer actor, Object value) {
        var r = run; if (r == null || actor != r.actor || r.complete) { return; }
        observe(r, () -> {
            require(r.castCued && r.instance == null && value instanceof ServerSlot.InstanceState, "ORIGINAL_INSTANCE_ONCE");
            r.instance = (ServerSlot.InstanceState) value;
            require(r.instance.hasP9AuthenticatedActorWitness(actor) && r.instance.work == null, "EXACT_WITNESS_BEFORE_PUBLICATION");
        });
    }

    static void accepted(MinecraftServer server, ServerPlayer actor, Object result) {
        var r = run; if (r == null || actor != r.actor || r.complete) { return; }
        observe(r, () -> {
            require(server == r.server && !r.accepted && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "ONE_ACTUAL_ACCEPTANCE");
            var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) result;
            require(r.instance != null && r.instance.id.equals(accepted.eventToken().skillInstanceId())
                    && r.instance.work != null && r.instance.work.qualifies(actor) && count(r) == 1, "EXACT_ACCEPTED_WORK");
            r.work = r.instance.work; r.accepted = true; r.acceptedServerTick = r.server.getTickCount();
        });
    }

    static void transferred(Object value, Object disposition) {
        var r = run; if (r == null || r.complete || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        observe(r, () -> {
            require(r.accepted && r.projectile == null && value instanceof P9StarterProjectile, "ONE_NATIVE_TRANSFER");
            r.projectile = (P9StarterProjectile) value; r.permit = r.instance.activeProjectileContinuation;
            require(r.slot != null && r.slot.token.equals(r.permit.serverSlotToken)
                    && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    && r.permit.qualifiedActor(r.server, r.projectile) == r.actor
                    && r.projectile.chunkPosition().equals(r.chunk)
                    && r.level.getEntity(r.projectile.getUUID()) == r.projectile, "ACTUAL_OPEN_IN_LOADED_WATER");
            r.last = r.projectile.position();
        });
    }

    /** Read actual P5 slot on its unchanged original sweep; no tick/deadline modification. */
    public static void sweep(MinecraftServer server, Object value) {
        var r = run; if (r == null || r.complete || server != r.server) { return; }
        observe(r, () -> {
            require(server.isSameThread() && value instanceof ServerSlot, "ORIGINAL_SLOT_THREAD");
            var slot = (ServerSlot) value;
            require(r.slot == null || r.slot == slot, "NO_NEW_RUNTIME_SLOT"); r.slot = slot;
        });
    }

    static void projectileTick(Object value) {
        var r = run; if (r == null || r.complete || value != r.projectile || r.projectile.isRemoved()) { return; }
        observe(r, () -> {
            require(r.projectile.isInWater() && r.projectile.chunkPosition().equals(r.chunk), "ORIGINAL_WET_FLIGHT_STAYS_IN_SELECTED_CHUNK");
            r.wetTicks++; r.travel += r.projectile.position().distanceTo(r.last); r.last = r.projectile.position();
            require(Double.isFinite(r.travel) && r.travel < 64 && r.projectile.tickCount <= 100, "ORIGINAL_RANGE_AGE_NOT_REACHED");
        });
    }

    public static boolean logoutEntering(ServerGamePacketListenerImpl listener) {
        var r = run; if (r == null || r.complete || listener != r.listener || !r.closeRequested) { return false; }
        final boolean[] selected = {false};
        observe(r, () -> {
            require(r.logoutEntries == 0 && listener.player == r.actor && r.instance.work == r.work && count(r) == 1,
                    "ONE_ORIGINAL_NORMAL_LOGOUT");
            r.logoutEntries++; selected[0] = true;
        });
        return selected[0];
    }

    public static void logoutFinished(boolean selected, Throwable primary) {
        var r = run; if (r == null || !selected) { return; }
        observe(r, () -> {
            require(primary == null && r.instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE
                    && r.instance.logoutScope == null && r.work.qualifies(r.actor) && count(r) == 1
                    && r.actor.isRemoved() && !r.listener.getConnection().isConnected(), "WHOLE_LOGOUT_COMPLETE_BEFORE_TRACKING_LOSS");
            r.logoutReturns++; r.logoutTick = r.server.getTickCount(); r.logoutReceiptPending = true;
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void trackingLeft(EntityLeaveLevelEvent event) {
        var r=run;
        if(r==null || r.complete || event.getEntity()!=r.projectile || event.getLevel()!=r.level) { return; }
        observe(r, () -> {
            // Native onTrackingEnd calls P9.onRemovedFromLevel before this event;
            // post-traversal physical cleanup has not necessarily happened yet.
            require(r.server.isSameThread() && r.logoutReturns==1 && r.trackingEvents==0
                    && r.slot!=null && r.slot.runtimeTick<r.permit.deadlineRuntimeTick
                    && r.permit.state==RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                    && r.instance.p9Diagnostic!=null
                    && r.instance.p9Diagnostic.terminalReason==ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED
                    && count(r)==0, "ACTUAL_TRACKING_EVENT_BEFORE_UNCHANGED_DEADLINE");
            r.trackingEvents++;r.trackingTick=r.server.getTickCount();r.terminalRuntimeTick=r.slot.runtimeTick;
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void physicalUnload(ChunkEvent.Unload event) {
        var r = run;
        if (r == null || r.complete || event.getLevel() != r.level || !event.getChunk().getPos().equals(r.chunk)) { return; }
        observe(r, () -> {
            require(r.server.isSameThread() && r.closeRequested && r.logoutReturns == 1 && r.unloadEvents == 0,
                    "ONE_ACTUAL_NATIVE_CHUNK_UNLOAD_AFTER_LOGOUT");
            r.unloadEvents++; r.physicalUnloadTick = r.server.getTickCount();
        });
    }

    static void tick() throws IOException {
        var r = run; if (r == null || r.complete) { return; }
        require(r.server.isSameThread() && r.failure.equals("NONE") && ++r.ticks <= 2400, "OBSERVER_OR_BOUNDED_PHYSICAL_WAIT");
        require(r.peer.getServer() == r.server && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer
                && r.peer.connection.getConnection() == r.peerConnection && r.peerConnection.isConnected() && far(r, r.peer.chunkPosition()),
                "EXACT_UNAFFECTED_DISTANT_PEER");
        if (r.logoutReceiptPending) { r.logoutReceiptPending = false; write(r, "natural-unload-logout.json", "ORIGINAL_WHOLE_NORMAL_LOGOUT_RETURN"); }
        var manager = ((P11L1TrackingBoundaryProbe.LevelAccess) r.level).p11$l1EntityManager();
        if (!r.castCued) {
            require(r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor && r.actor.connection == r.listener
                    && r.listener.getConnection().isConnected(), "LIVE_SETUP_ACTOR");
            if (!r.actor.chunkPosition().equals(r.chunk) || !manager.canPositionTick(r.chunk)
                    || r.level.getChunkSource().getChunkNow(r.chunk.x, r.chunk.z) == null) { return; }
            if (!r.water) {
                prepareWater(r);
                r.actor.teleportTo(r.level, r.chunk.getMinBlockX()+8.5, 101, r.chunk.getMinBlockZ()+3.5,
                        java.util.Set.of(), 0, 0);
                r.teleportReturns++; r.lastTeleportTick=r.server.getTickCount(); return;
            }
            // All setup delay is BEFORE actual R/work; the native POST_TELEPORT timeout is 5.
            if (r.server.getTickCount()-r.lastTeleportTick <= 5) { return; }
            require(r.actor.getY()>95 && r.actor.getY()<104 && r.actor.isInWater(), "NATIVE_SUBMERGED_START_POSITION");
            r.castCued = true;
            write(r, "natural-unload-fixture.json", "ORIGINAL_PLAYER_LOADED_FAR_WATER_BEFORE_ADMISSION");
            P11C4aEvidence.cue(r.output, "a-cast-1.ready"); return;
        }
        if (!r.accepted || r.projectile == null) { return; }
        if (!r.closeRequested && r.wetTicks > 0) {
            require(r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN && count(r) == 1
                    && r.permit.qualifiedActor(r.server, r.projectile) == r.actor, "ACTUAL_OPEN_BEFORE_ORIGINAL_CLIENT_LEAVE");
            r.closeRequested = true; P11C4aEvidence.cue(r.output, "a-unload-close.ready"); return;
        }
        if (!r.trackingTerminal && r.permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT) {
            require(r.logoutReturns == 1 && r.trackingEvents == 1 && r.terminalRuntimeTick < r.permit.deadlineRuntimeTick
                    && r.instance.p9Diagnostic != null
                    && r.instance.p9Diagnostic.terminalReason == ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED,
                    "TRACKING_LOSS_NOT_DEADLINE_OR_OTHER_TERMINAL");
            require(r.projectile.isRemoved() && !manager.isLoaded(r.projectile.getUUID())
                    && manager.getEntityGetter().get(r.projectile.getUUID()) == null && r.projectile.getOwner() == null
                    && r.instance.work == null && r.instance.activeProjectileContinuation == null
                    && r.instance.p9ActorWitness() == null && r.instance.lease.pin.isClosed()
                    && count(r) == 0 && !r.work.qualifies(r.actor), "ACTUAL_NATIVE_OWNERSHIP_AND_WORK_RELEASED");
            r.trackingTerminal = true;
            write(r, "natural-unload-tracking.json", "ACTUAL_LOADED_ONLY_TERMINAL_BEFORE_ORIGINAL_DEADLINE");
        }
        if (!r.trackingTerminal || r.unloadEvents != 1) { return; }
        require(r.claims == 0 && r.hurts == 0 && r.actor.totalExperience == r.initialExperience
                && r.actor.getScore() == r.initialScore && !r.listener.getConnection().isConnected()
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == null
                && r.level.getChunkSource().getChunkNow(r.chunk.x, r.chunk.z) == null
                && !manager.isLoaded(r.projectile.getUUID()), "PHYSICAL_UNLOAD_NO_EFFECT_OR_RESURRECTION");
        r.complete = true; write(r, "natural-unload-result.json", "ACTUAL_TICKET_DEPARTURE_TRACKING_TERMINAL_AND_NATIVE_CHUNK_UNLOAD");
    }

    static void claimed(Object disposition) { var r=run; if(r!=null && !r.complete && disposition==RuntimePermitClaimDisposition.QUEUED) { r.claims++; r.failure="UNEXPECTED_CHILD"; } }
    static void damageEntering(DamageSource source) { var r=run; if(r!=null && r.projectile!=null && source.getDirectEntity()==r.projectile) { r.hurts++; r.failure="UNEXPECTED_HURT"; } }
    static boolean closeRequested() { return run != null && run.closeRequested; }
    static boolean complete() { return run != null && run.complete && run.failure.equals("NONE"); }
    static String failureCode() { return run == null ? "NONE" : run.failure; }
    static void release() { run = null; }
    private static long count(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()]; }
    private static void observe(Run r,Runnable body) { try { body.run(); } catch(RuntimeException|Error ignored) { r.failure="NATURAL_UNLOAD_OBSERVER_FAILURE"; } }
    private static void require(boolean condition,String code) { if(!condition) { throw new IllegalStateException("L1_UNLOAD_"+code); } }
    private static void write(Run r,String name,String status) throws IOException {
        var f=new LinkedHashMap<String,Object>(); f.put("status",status); f.put("failure",r.failure);
        f.put("controlledVisibilityChange",false); f.put("ticketMutation",false); f.put("forceLoad",false); f.put("nativeHeapGcClaim",false);
        f.put("originalTeleportReturns",r.teleportReturns); f.put("waterPrepared",r.water); f.put("accepted",r.accepted);
        f.put("teleportTick",r.teleportTick); f.put("lastTeleportTick",r.lastTeleportTick); f.put("acceptedServerTick",r.acceptedServerTick); f.put("logoutTick",r.logoutTick);
        f.put("trackingTerminalTick",r.trackingTick); f.put("physicalUnloadTick",r.physicalUnloadTick);
        f.put("physicalUnloadWithin100Guaranteed",false); f.put("runtimeTerminalTick",r.terminalRuntimeTick);
        f.put("originalDeadline",r.permit==null?-1:r.permit.deadlineRuntimeTick); f.put("wholeLogoutEntries",r.logoutEntries);
        f.put("wholeLogoutReturns",r.logoutReturns); f.put("actualTrackingLeaveEvents",r.trackingEvents); f.put("actualChunkUnloadEvents",r.unloadEvents); f.put("workCount",count(r));
        f.put("projectileAge",r.projectile==null?-1:r.projectile.tickCount); f.put("wetNativeTicks",r.wetTicks); f.put("travel",r.travel);
        f.put("claims",r.claims); f.put("hurts",r.hurts); P11C4aEvidence.write(r.output,name,f);
    }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor,peer; final ServerLevel level; final Path output; final ChunkPos chunk;
        final P11QualifiedSourceOwner.Body body; final ServerGamePacketListenerImpl listener; final net.minecraft.network.Connection peerConnection;
        final int teleportTick,initialExperience,initialScore;
        ServerSlot slot; ServerSlot.InstanceState instance; P11QualifiedSourceOwner.WorkReservation work;
        P9StarterProjectile projectile; RuntimeProjectileContinuationPermit permit; Vec3 last;
        boolean water,castCued,accepted,closeRequested,logoutReceiptPending,trackingTerminal,complete;
        int ticks,teleportReturns,logoutEntries,logoutReturns,unloadEvents,trackingEvents,wetTicks,claims,hurts,lastTeleportTick;
        int acceptedServerTick=-1,logoutTick=-1,trackingTick=-1,physicalUnloadTick=-1; long terminalRuntimeTick=-1; double travel;
        String failure="NONE";
        Run(MinecraftServer s,ServerPlayer a,ServerPlayer p,Path o,P11QualifiedSourceOwner.Body b,ChunkPos c) {
            server=s;actor=a;peer=p;output=o;body=b;chunk=c;level=a.serverLevel();listener=a.connection;
            peerConnection=p.connection.getConnection();teleportTick=s.getTickCount();initialExperience=a.totalExperience;initialScore=a.getScore();
        }
    }
}
