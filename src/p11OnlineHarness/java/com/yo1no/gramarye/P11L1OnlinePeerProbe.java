package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Excluded one real online R against the other authenticated, native-PvP-eligible player. */
public final class P11L1OnlinePeerProbe {
    public interface PlayerAccess { int p11$l1SpawnInvulnerableTime(); }
    private static Run run;
    private P11L1OnlinePeerProbe() {}

    static void arm(MinecraftServer server,ServerPlayer actor,ServerPlayer peer,Path output) throws IOException {
        require(P11C4aEvidence.property("case").equals("l1-online-peer")
                && run==null && server.isSameThread() && actor!=peer && !actor.getUUID().equals(peer.getUUID())
                && actor.getServer()==server && peer.getServer()==server && actor.serverLevel()==peer.serverLevel(),"ARM_IDENTITIES");
        var owner=P11NativeStorageBoundary.nativeSourceOwner(actor);
        var peerOwner=P11NativeStorageBoundary.nativeSourceOwner(peer);
        var body=owner==null?null:owner.body(actor);var peerBody=peerOwner==null?null:peerOwner.body(peer);
        require(owner!=null && owner==peerOwner && body!=null && peerBody!=null && body!=peerBody
                && owner.canCopy(body) && owner.canSerialize(body) && owner.canCopy(peerBody) && owner.canSerialize(peerBody),
                "BOTH_HEALTHY_QUALIFIED_NATIVE_BODIES");
        var r=new Run(server,actor,peer,output,owner,body,peerBody);current(r);
        require(server.isPvpAllowed() && peer.canHarmPlayer(actor) && actor.canHarmPlayer(peer)
                && !peer.isCreative() && !peer.isSpectator() && !peer.getAbilities().invulnerable
                && peer.getArmorValue()==0 && peer.getAbsorptionAmount()==0 && peer.getActiveEffects().isEmpty()
                && peer.getHealth()>4 && peer.invulnerableTime==0 && count(r)==0,"UNCHANGED_NATIVE_PVP_AND_HEALTH_PRECONDITIONS");
        run=r;write(r,"online-peer-armed.json","ARMED_NOT_DAMAGE_OR_PRESENTATION_PROOF");
    }

    static void instanceCreated(ServerPlayer actor,Object value) {
        var r=run;if(r==null || r.complete || actor!=r.actor) { return; }
        observe(r,()->{
            require(r.castCued && r.instance==null && value instanceof ServerSlot.InstanceState,"ACTUAL_INSTANCE_ONCE");
            r.instance=(ServerSlot.InstanceState)value;
            require(r.instance.hasP9AuthenticatedActorWitness(actor) && r.instance.work==null,"ORIGINAL_WITNESS");
        });
    }

    static void accepted(MinecraftServer server,ServerPlayer actor,Object geometry,Object value) {
        var r=run;if(r==null || r.complete || actor!=r.actor) { return; }
        observe(r,()->{
            current(r);
            require(server==r.server && !r.accepted && value instanceof RuntimeAdmissionResult.AcceptedMemoryOnly
                    && geometry instanceof CastGeometryExecutionDataV0,"ONE_REAL_P7_P5_ACCEPTANCE");
            var result=(RuntimeAdmissionResult.AcceptedMemoryOnly)value;
            require(r.instance!=null && r.instance.id.equals(result.eventToken().skillInstanceId())
                    && r.instance.work!=null && r.instance.work.qualifies(actor) && !r.instance.lease.pin.isClosed()
                    && r.instance.logoutState==SkillRuntimeService.LogoutState.ONLINE && count(r)==1,"EXACT_LIVE_ACCEPTED_WORK");
            r.work=r.instance.work;r.accepted=true;r.acceptedTick=r.server.getTickCount();
            // Move only the consenting real peer by the ordinary API, not the projectile.
            // Native speed/drag/gravity below predict an ordinary target location; no hit is invoked.
            var sample=(CastGeometryExecutionDataV0)geometry;
            Vec3 position=new Vec3(sample.originX(),sample.originY(),sample.originZ());
            Vec3 motion=new Vec3(sample.directionXQ15(),sample.directionYQ15(),sample.directionZQ15()).normalize().scale(1.5);
            for(int i=0;i<15;i++) { position=position.add(motion);motion=motion.scale(0.99).add(0,-0.03,0); }
            var floor=BlockPos.containing(position.x,position.y-1,position.z);
            for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
                var p=floor.offset(x,0,z);
                require(actor.serverLevel().isLoaded(p) && actor.serverLevel().getBlockEntity(p)==null,"LOADED_TARGET_FLOOR");
                actor.serverLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);
            }
            r.peer.teleportTo(r.peer.serverLevel(),position.x,floor.getY()+1,position.z,java.util.Set.of(),180,0);
            r.peerTeleportReturns++;r.healthAtPlacement=r.peer.getHealth();
            require(r.healthAtPlacement==r.initialPeerHealth,"TELEPORT_DID_NOT_REPAIR_OR_DAMAGE_PEER");
        });
    }

    static void transferred(Object value,Object disposition) {
        var r=run;if(r==null || r.complete || disposition!=RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        observe(r,()->{
            current(r);require(r.accepted && r.projectile==null && value instanceof P9StarterProjectile,"ORIGINAL_TRANSFER_ONCE");
            r.projectile=(P9StarterProjectile)value;r.permit=r.instance.activeProjectileContinuation;
            require(r.permit!=null && r.permit.state==RuntimeProjectileContinuationPermit.State.OPEN
                    && r.projectile.getOwner()==r.actor && r.permit.qualifiedActor(r.server,r.projectile)==r.actor
                    && r.actor.serverLevel().getEntity(r.projectile.getUUID())==r.projectile,"EXACT_LIVE_OWNER_PROJECTILE");
            r.transfers++;
        });
    }

    static void claimed(Object disposition) {
        var r=run;if(r==null || r.complete || disposition!=RuntimePermitClaimDisposition.QUEUED) { return; }
        observe(r,()->{ current(r);require(r.accepted && r.claims==0,"ONE_NATURAL_CHILD");r.claims++; });
    }
    static void hitReturned(Object projectile,Entity actualTarget) {
        var r=run;if(r==null || r.complete || projectile!=r.projectile) { return; }
        observe(r,()->{ current(r);require(actualTarget==r.peer && r.claims==1 && r.collisions==0,"ACTUAL_OTHER_PLAYER_COLLISION");r.collisions++; });
    }
    static void damageEntering(LivingEntity target,DamageSource source) {
        var r=run;if(r==null || r.complete || source.getDirectEntity()!=r.projectile) { return; }
        observe(r,()->{
            current(r);require(target==r.peer && source.getEntity()==r.actor && r.projectile.getOwner()==r.actor
                    && r.claims==1 && r.collisions==1 && r.hurtEntries==0 && count(r)==1
                    && r.instance.logoutState==SkillRuntimeService.LogoutState.ONLINE
                    && r.work.qualifies(r.actor) && r.peer.canHarmPlayer(r.actor),"EXACT_ONLINE_ORIGINAL_DAMAGE_CAUSE");
            require(((PlayerAccess)r.peer).p11$l1SpawnInvulnerableTime()<=0 && r.peer.invulnerableTime==0
                    && r.peer.getHealth()==r.initialPeerHealth && r.peer.getAbsorptionAmount()==0
                    && r.peer.getArmorValue()==0 && r.peer.getActiveEffects().isEmpty(),"UNCHANGED_NATIVE_HURT_PRECONDITIONS");
            r.healthBeforeHurt=r.peer.getHealth();r.hurtEntries++;
        });
    }
    static void damageReturned(LivingEntity target,DamageSource source,float amount,boolean applied) {
        var r=run;if(r==null || r.complete || source.getDirectEntity()!=r.projectile) { return; }
        observe(r,()->{
            current(r);require(target==r.peer && source.getEntity()==r.actor && r.hurtEntries==1 && r.hurtReturns==0
                    && amount==4 && applied && r.peer.isAlive() && r.peer.getHealth()==r.healthBeforeHurt-4,
                    "ONE_NATIVE_HURT_FOUR_HEALTH_DELTA");
            r.hurtReturns++;r.healthAfterHurt=r.peer.getHealth();r.hurtTick=r.server.getTickCount();
        });
    }
    static void commitReturned(Object disposition) {
        var r=run;if(r==null || r.complete || !r.accepted) { return; }
        observe(r,()->{
            require(r.hurtReturns==1 && r.commits==0
                    && disposition==com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.CommitDisposition.APPLIED,
                    "ONE_ORIGINAL_P5_P6_APPLIED");r.commits++;
        });
    }

    public static boolean p8Selected(ServerPlayer recipient,CustomPacketPayload payload) {
        var r=run;
        if(r==null || r.complete || recipient!=r.peer || !(payload instanceof PresentationEventPayload event)
                || event.kind()!=PresentationEventKind.HIT || event.sourceSummary().targetEntityId().isEmpty()
                || event.sourceSummary().targetEntityId().getAsInt()!=r.peer.getId()) { return false; }
        final boolean[] selected={false};
        observe(r,()->{
            current(r);require(r.hurtReturns==1 && r.commits==1 && r.p8Entries==0 && event.sequence()>0
                    && event.sourceSummary().sourceEntityId().isPresent()
                    && event.sourceSummary().sourceEntityId().getAsInt()==r.actor.getId()
                    && event.dimension().equals(r.actor.serverLevel().dimension().location()),
                    "P8_AFTER_REAL_PEER_APPLIED_ONLY");
            r.p8Entries++;r.p8Sequence=event.sequence();selected[0]=true;
        });return selected[0];
    }
    public static void p8Finished(boolean selected,Throwable primary) {
        var r=run;if(r==null || !selected) { return; }
        observe(r,()->{ require(primary==null && r.p8Entries==1 && r.p8Returns==0,"ORIGINAL_P8_SEND_NORMAL_RETURN");r.p8Returns++; });
    }

    static void tick() throws IOException {
        var r=run;if(r==null || r.complete) { return; }
        require(r.server.isSameThread() && r.failure.equals("NONE") && ++r.ticks<=2400,"OBSERVER_OR_EPISODE_DEADLINE");current(r);
        if(!r.castCued) {
            // Native login grace is allowed to expire before R; no active work is delayed.
            if(((PlayerAccess)r.peer).p11$l1SpawnInvulnerableTime()>0 || ((PlayerAccess)r.actor).p11$l1SpawnInvulnerableTime()>0) { return; }
            require(r.peer.getHealth()==r.initialPeerHealth && r.peer.invulnerableTime==0,"HEALTHY_REAL_INPUT_START");
            r.castCued=true;write(r,"online-peer-input-ready.json","NATIVE_GRACE_FINISHED_BEFORE_R");
            P11C4aEvidence.cue(r.output,"a-cast-1.ready");return;
        }
        if(!r.accepted || r.instance.work!=null || r.p8Returns!=1) { return; }
        require(r.transfers==1 && r.claims==1 && r.collisions==1 && r.hurtEntries==1 && r.hurtReturns==1 && r.commits==1
                && r.peerTeleportReturns==1 && r.p8Entries==1 && count(r)==0
                && r.instance.lease.pin.isClosed() && r.instance.activeProjectileContinuation==null
                && r.instance.p9ActorWitness()==null && r.permit.state==RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT
                && r.projectile.isRemoved() && r.projectile.getOwner()==null
                && r.actor.serverLevel().getEntity(r.projectile.getUUID())==null
                && r.instance.p9Diagnostic.terminalReason==ProjectileClosureReason.DAMAGE_TERMINAL,"ALL_ORIGINAL_EFFECT_AND_WORK_TERMINALS");
        require(r.actor.getHealth()==r.initialActorHealth && r.actor.totalExperience==r.initialActorXp
                && r.peer.totalExperience==r.initialPeerXp && r.peer.isAlive(),"NO_SELF_HURT_OR_KILL_REWARD");
        r.complete=true;write(r,"online-peer-result.json","ONLINE_ACTUAL_OTHER_PLAYER_HURT_AND_P8_SUBMISSION_NOT_CLIENT_RENDER_PROOF");
    }
    static boolean complete() { return run!=null && run.complete && run.failure.equals("NONE"); }
    static String failureCode() { return run==null?"NONE":run.failure; }
    static void release() { run=null; }
    private static long count(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()]; }
    private static void current(Run r) {
        require(r.server.isSameThread() && live(r,r.actor,r.actorConnection) && live(r,r.peer,r.peerConnection)
                && r.actor.serverLevel()==r.peer.serverLevel() && r.server.isPvpAllowed()==r.pvp
                && r.actor.getTeam()==r.actorTeam && r.peer.getTeam()==r.peerTeam
                && r.actor.canHarmPlayer(r.peer) && r.peer.canHarmPlayer(r.actor)
                && r.owner.body(r.actor)==r.body && r.owner.body(r.peer)==r.peerBody
                && r.body.source.epoch()==r.actorEpoch && r.peerBody.source.epoch()==r.peerEpoch
                && r.owner.canCopy(r.body) && r.owner.canCopy(r.peerBody),"SAME_LIVE_AUTHENTICATED_ACTORS_CONNECTIONS_SOURCES_AND_PVP");
    }
    private static boolean live(Run r,ServerPlayer player,net.minecraft.network.Connection connection) {
        return player.getServer()==r.server && !player.isFakePlayer() && !player.isRemoved() && player.isAlive()
                && r.server.getPlayerList().getPlayer(player.getUUID())==player && player.connection.getConnection()==connection
                && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection()
                && connection.getPacketListener()==player.connection;
    }
    private static void observe(Run r,Runnable observation) { try { observation.run(); } catch(RuntimeException|Error ignored) { r.failure="ONLINE_PEER_OBSERVER_FAILURE"; } }
    private static void require(boolean condition,String code) { if(!condition) { throw new IllegalStateException("L1_ONLINE_PEER_"+code); } }
    private static void write(Run r,String file,String status) throws IOException {
        var f=new LinkedHashMap<String,Object>();f.put("status",status);f.put("failure",r.failure);f.put("actualP7P5Acceptance",r.accepted);
        f.put("sameLiveAuthenticatedActorsAndConnections",true);f.put("logoutUsedForEpisode",false);f.put("pvpUnmodified",r.pvp);
        f.put("teamMembershipUnchanged",true);f.put("nativeSpawnGraceRemaining",((PlayerAccess)r.peer).p11$l1SpawnInvulnerableTime());
        f.put("acceptedTick",r.acceptedTick);f.put("hurtTick",r.hurtTick);f.put("spawnTransfers",r.transfers);f.put("naturalCollisions",r.collisions);
        f.put("claims",r.claims);f.put("hurtEntries",r.hurtEntries);f.put("hurtReturns",r.hurtReturns);f.put("p5AppliedReturns",r.commits);
        f.put("healthBefore",r.healthBeforeHurt);f.put("healthAfter",r.healthAfterHurt);f.put("workRoots",count(r));
        f.put("originalPeerTeleportReturns",r.peerTeleportReturns);f.put("originalP8HitSendEntries",r.p8Entries);
        f.put("originalP8HitSendReturns",r.p8Returns);f.put("p8Sequence",r.p8Sequence);f.put("clientPacketReceiptClaim",false);
        f.put("clientRenderClaim",false);f.put("osInputClaim",false);f.put("nonlethalNoR1DeathClaim",true);P11C4aEvidence.write(r.output,file,f);
    }
    private static final class Run {
        final MinecraftServer server;final ServerPlayer actor,peer;final Path output;final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body,peerBody;final long actorEpoch,peerEpoch;
        final net.minecraft.network.Connection actorConnection,peerConnection;final net.minecraft.world.scores.Team actorTeam,peerTeam;
        final boolean pvp;final float initialActorHealth,initialPeerHealth;final int initialActorXp,initialPeerXp;
        ServerSlot.InstanceState instance;P11QualifiedSourceOwner.WorkReservation work;RuntimeProjectileContinuationPermit permit;P9StarterProjectile projectile;
        boolean castCued,accepted,complete;int ticks,acceptedTick=-1,hurtTick=-1,transfers,claims,collisions,hurtEntries,hurtReturns,commits,peerTeleportReturns,p8Entries,p8Returns;
        float healthAtPlacement,healthBeforeHurt,healthAfterHurt;long p8Sequence;String failure="NONE";
        Run(MinecraftServer s,ServerPlayer a,ServerPlayer b,Path o,P11QualifiedSourceOwner q,P11QualifiedSourceOwner.Body ab,P11QualifiedSourceOwner.Body bb) {
            server=s;actor=a;peer=b;output=o;owner=q;body=ab;peerBody=bb;actorEpoch=ab.source.epoch();peerEpoch=bb.source.epoch();
            actorConnection=a.connection.getConnection();peerConnection=b.connection.getConnection();actorTeam=a.getTeam();peerTeam=b.getTeam();pvp=s.isPvpAllowed();
            initialActorHealth=a.getHealth();initialPeerHealth=b.getHealth();initialActorXp=a.totalExperience;initialPeerXp=b.totalExperience;
        }
    }
}
