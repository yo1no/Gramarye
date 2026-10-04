package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/** Excluded integrated-host stop subset. Never grants a work, calls hurt, or halts on success. */
public final class P11L1HostStopProbe {
    private static volatile Run active;
    private P11L1HostStopProbe() { }
    static boolean selected() {
        return P11C4aScenario.MODE == P11C4aScenario.Mode.L1_HOST_STOP
                && P11C4aEvidence.property("case").equals("c4a-host-lan");
    }
    static boolean started() { return active != null; }
    static void start(MinecraftServer server, ServerPlayer host, ServerPlayer peer, Path output) throws IOException {
        require(selected() && active == null && server.isSameThread() && server.isSingleplayer()
                && !server.isDedicatedServer() && server.isPublished() && server.usesAuthentication()
                && host != peer && !host.isFakePlayer() && !peer.isFakePlayer()
                && server.isSingleplayerOwner(host.getGameProfile())
                && !server.isSingleplayerOwner(peer.getGameProfile()), "EXACT_TOPOLOGY");
        var r = new Run(server, host, peer, output);
        require(r.hostC.isMemoryConnection() && !r.peerC.isMemoryConnection() && r.peerC.isEncrypted()
                && r.body != null && r.peerBody != null && r.source.canCopy(r.body)
                && r.source.canCopy(r.peerBody) && work(r) == 0, "INITIAL_QUALIFIED_BODIES");
        current(r); active = r;
        var level = host.serverLevel();
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
            require(level.isLoaded(new BlockPos(x,99,z)), "ARENA_ALREADY_LOADED");
            level.setBlock(new BlockPos(x,99,z), Blocks.STONE.defaultBlockState(),3);
        }
        host.teleportTo(level,.5,100,.5,java.util.Set.of(),0,0);
        peer.teleportTo(level,2.5,100,.5,java.util.Set.of(),90,0);
        require(server.getScoreboard().getObjective("p11_l1") == null, "FRESH_OBJECTIVE");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"scoreboard objectives add p11_l1 dummy");
        P11C4aEvidence.cue(output,"host-l1-starter.ready");
        P11C4aEvidence.cue(output,"host-l1-peer-swing.ready");
    }
    static void tick() throws IOException {
        var r=active; require(r!=null && r.server.isSameThread() && ++r.ticks<=2400 && r.failure==null,"TICK_OR_OBSERVER");
        if(r.quitIntent) return;
        current(r);
        if(!r.firstCue) {
            if(!P11C4aEvidence.cuePresent(r.output.getParent().resolve("client-host"),"host-l1-starter-sent.ready")) return;
            var equipped=P11NativeStorageBoundary.diagnostics(r.server,r.host.getUUID()).equippedSlot0();
            if(equipped.equals("ABSENT") || equipped.equals("UNAVAILABLE") || r.peerSwings==0) return;
            require(r.peerSwings==1 && r.host.totalExperience==r.xpBefore,"BEFORE_FIRST_INPUT");
            r.firstCue=true; P11C4aEvidence.cue(r.output,"host-l1-cast-1.ready"); return;
        }
        if(r.accepted==1 && r.damageReturns==1 && !r.secondCue) {
            require(r.instances[0].work==null && r.instances[0].lease.pin.isClosed() && work(r)==0
                    && r.projectiles[0].isRemoved() && r.victim!=null && !r.victim.isAlive()
                    && r.host.totalExperience==r.xpBefore+10 && r.peer.totalExperience==r.peerXp,
                    "REAL_FIRST_REWARD_AND_TERMINAL");
            var award=r.server.getAdvancements().get(reward());
            require(award!=null && r.host.getAdvancements().getOrStartProgress(award).isDone()
                    && score(r)==1 && r.host.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN))==r.chickenBefore+1
                    && r.host.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS))==r.mobBefore+1,"REWARD_FIELDS");
            r.firstTerminal=true;
            P11C4aEvidence.write(r.output,"host-l1-reward.json",facts(r,"ORIGINAL_HOST_R_NATIVE_REWARD_TERMINAL"));
            // The second real cast has an empty upward ray, no motion/lifetime edits.
            r.host.teleportTo(r.host.serverLevel(),.5,100,.5,java.util.Set.of(),0,-70);
            r.secondCue=true; P11C4aEvidence.cue(r.output,"host-l1-cast-2.ready"); return;
        }
        if(r.accepted==2 && r.projectiles[1]!=null && r.projectiles[1].tickCount>0 && !r.quitCue) {
            open(r); require(r.firstTerminal && r.peerSwings==1 && r.peer.tickCount>r.peerTickBefore+20,
                    "HEALTHY_PEER_BEFORE_HOST_QUIT");
            r.quitCue=true;
            P11C4aEvidence.write(r.output,"host-l1-open-before-quit.json",facts(r,"ACTUAL_SECOND_OPEN_W_BEFORE_QUIT_CUE"));
            P11C4aEvidence.cue(r.output,"host-l1-quit.ready");
        }
    }
    /** Client thread publishes intent only. The original server boundary proves W, not this flag. */
    static void quitIntent(MinecraftServer exact) {
        var r=active; require(r!=null && r.server==exact && r.quitCue && !r.quitIntent,"QUIT_INTENT"); r.quitIntent=true;
    }
    public static void instance(ServerPlayer actor,Object value) {
        var r=active; if(r==null || actor!=r.host) return;
        observe(r,()->{ require(r.server.isSameThread() && !r.quitIntent && r.created<2 && value instanceof ServerSlot.InstanceState,"INSTANCE");
            r.instances[r.created++]=(ServerSlot.InstanceState)value; });
    }
    public static void accepted(MinecraftServer server,ServerPlayer actor,Object geometry,Object result) {
        var r=active; if(r==null || actor!=r.host) return;
        observe(r,()->{
            require(server==r.server && server.isSameThread() && r.accepted<2 && r.created==r.accepted+1
                    && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly && geometry instanceof CastGeometryExecutionDataV0,"REAL_ADMISSION");
            var instance=r.instances[r.accepted]; var accepted=(RuntimeAdmissionResult.AcceptedMemoryOnly)result;
            require(instance.id.equals(accepted.eventToken().skillInstanceId()) && instance.hasP9AuthenticatedActorWitness(actor)
                    && instance.work!=null && instance.work.qualifies(actor) && !instance.lease.pin.isClosed()
                    && work(r)==1 && r.source.body(actor)==r.body,"ACTUAL_WORK");
            r.accepted++;
            if(r.accepted!=1) return;
            var g=(CastGeometryExecutionDataV0)geometry;
            Vec3 p=new Vec3(g.originX(),g.originY(),g.originZ());
            Vec3 m=new Vec3(g.directionXQ15(),g.directionYQ15(),g.directionZQ15()).normalize().scale(1.5);
            for(int i=0;i<20;i++){ p=p.add(m); m=m.scale(.99).add(0,-.03,0); }
            var floor=BlockPos.containing(p.x,p.y-1,p.z);
            for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
                require(actor.serverLevel().isLoaded(floor.offset(x,0,z)),"TARGET_ALREADY_LOADED");
                actor.serverLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
            }
            var victim=EntityType.CHICKEN.create(actor.serverLevel()); require(victim!=null,"CHICKEN");
            victim.setNoAi(true); victim.setPersistenceRequired(); victim.addTag("p11_l1_target");
            victim.moveTo(p.x,floor.getY()+1,p.z,0,0); require(actor.serverLevel().addFreshEntity(victim),"CHICKEN_NATIVE_ADD"); r.victim=victim;
        });
    }
    public static void transferred(Object value,Object result) {
        var r=active; if(r==null || !(value instanceof P9StarterProjectile p) || p.getOwner()!=r.host) return;
        observe(r,()->{ require(r.server.isSameThread() && result==RuntimePermitTransferDisposition.TRANSFERRED
                    && r.accepted>0 && r.projectiles[r.accepted-1]==null,"TRANSFER");
            var permit=r.instances[r.accepted-1].activeProjectileContinuation;
            require(permit!=null && permit.plannedProjectileId.equals(p.getUUID()) && permit.state==RuntimeProjectileContinuationPermit.State.OPEN,"EXACT_OPEN");
            r.projectiles[r.accepted-1]=p; r.permits[r.accepted-1]=permit;
        });
    }
    public static void damage(LivingEntity target,DamageSource source,float amount,boolean result) {
        var r=active; if(r==null || target!=r.victim) return;
        observe(r,()->{ require(r.server.isSameThread() && ++r.damageReturns==1 && result && amount==4
                    && source.getEntity()==r.host && source.getDirectEntity()==r.projectiles[0]
                    && r.instances[0].work!=null && r.body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()]>0,"ORIGINAL_P6_HURT"); });
    }
    public static void animate(ServerGamePacketListenerImpl listener) {
        var r=active; if(r==null || listener!=r.peer.connection) return;
        observe(r,()->{require(r.server.isSameThread() && listener.player==r.peer && !r.quitIntent && ++r.peerSwings==1,"PEER_ORIGINAL_SWING");});
    }
    public static void boundary(MinecraftServer server,ServerPlayer actor,boolean stopping,boolean returned,boolean normal) {
        var r=active; if(r==null || server!=r.server || !stopping && actor!=r.host) return;
        observe(r, returned ? stopping ? "RUNTIME_STOP_RETURN_CHECK" : "LOGOUT_RETURN_CHECK"
                : stopping ? "RUNTIME_STOP_ENTRY_CHECK" : "LOGOUT_ENTRY_CHECK", ()->{
            if(!returned) {
                if(stopping) r.stopHookEntries++; else r.logoutHookEntries++;
                if(r.firstHookFacts==null) {
                    r.firstHookBoundary=stopping?"RUNTIME_STOPPING":"ORIGINAL_NORMAL_LOGOUT";
                    r.firstHookFacts=boundaryFacts(r);
                }
            }
            require(server.isSameThread() && r.quitIntent && r.quitCue,"NATIVE_BOUNDARY_WITHOUT_QUIT");
            if(!returned) {
                if(!r.boundaryObserved){open(r); r.boundaryObserved=true; r.firstBoundary=stopping?"RUNTIME_STOPPING":"ORIGINAL_NORMAL_LOGOUT";}
                if(stopping) r.stopEntries++; else r.logoutEntries++;
            } else {
                require(normal,"ORIGINAL_BOUNDARY_THROW");
                if(stopping) { r.stopReturns++; terminalWork(r); } else r.logoutReturns++;
            }
        });
    }
    /** Existing MinecraftServer.halt HEAD only; the integrated override can have done earlier work. */
    public static synchronized void originalBaseHalt(MinecraftServer server,boolean wait) {
        var r=active; if(r==null || server!=r.server || r.baseHaltFacts!=null) return;
        try {
            // running and quitIntent are volatile; server-thread identity is fixed. No off-thread source graph reads.
            r.baseHaltFacts=Map.of("observed",true,"serverRunning",server.isRunning(),
                    "owningThread",server.isSameThread(),"wait",wait,"quitIntent",r.quitIntent,
                    "isEarliestIntegratedHaltClaim",false);
        } catch(RuntimeException|Error ignoredDiagnostic) { r.baseHaltObservationFailed=true; }
    }
    public static void rootRetired(MinecraftServer server) {
        var r=active; if(r==null || r.server!=server) return;
        observe(r,()->{ require(server.isSameThread() && ++r.rootReturns==1,"ROOT_RETURN"); r.summary=P11NativeStorageBoundary.terminalDiagnostics(); });
        seal(r);
    }
    public static void beforeRoot(MinecraftServer server) {
        var r=active;if(r==null || r.server!=server)return;
        observe(r,()->{
            require(server.isSameThread() && r.quitIntent && r.stopReturns==1,"PRE_RETIREMENT");
            var facts=r.source.diagnostics(r.host.getUUID());
            for(String kind:java.util.List.of("PLAYER_DATA","LEVEL_PLAYER","STATISTICS","ADVANCEMENTS")) {
                var writer=facts.writers().stream().filter(w->w.kind().equals(kind)).findFirst().orElseThrow();
                require(!writer.dirty() && writer.terminal().equals("COMPLETED") && writer.encode().equals("SUCCEEDED")
                        && writer.write().equals("SUCCEEDED") && writer.close().equals("SUCCEEDED")
                        && (!(kind.equals("PLAYER_DATA") || kind.equals("LEVEL_PLAYER")) || writer.replace().equals("SUCCEEDED")),"CURRENT_ORIGINAL_WRITER_RECEIPTS");
            }
            r.writersCurrent=true;
        });
    }
    static void logout(ServerPlayer actor) {
        var r=active; if(r==null || actor.getServer()!=r.server) return;
        observe(r,()->{require(r.server.isSameThread() && r.quitIntent,"UNEXPECTED_LOGOUT");
            if(actor==r.host) r.hostLogouts++; else if(actor==r.peer) r.peerLogouts++; else require(false,"FOREIGN_LOGOUT");});
    }
    static void stopped(MinecraftServer server) {
        var r=active; if(r==null || r.server!=server) return;
        observe(r,()->{require(server.isSameThread() && ++r.stops==1,"STOP_EVENT");}); seal(r);
    }
    private static void seal(Run r) {
        if(r.written || r.stops!=1 || r.rootReturns!=1) return;
        try {
            require(r.failure==null && r.quitIntent && r.boundaryObserved && r.writersCurrent && r.stopEntries==1 && r.stopReturns==1
                    && r.hostLogouts==1 && r.peerLogouts==1 && r.server.getPlayerList().getPlayerCount()==0,"STOP_TERMINALS");
            terminalWork(r);
            var s=r.summary;
            require(s!=null && s==P11NativeStorageBoundary.terminalDiagnostics() && s.nativeStopNormal() && s.failures()==0
                    && s.resources().dirtyUuids()==0 && s.resources().inFlight()==0 && s.resources().sealedSnapshots()==0
                    && s.nativeResponsibilities().roots().size()==5 && s.nativeResponsibilities().roots().stream().allMatch(x->x.count()==0),"SOURCE_TERMINAL");
            r.physicalReadbackAttempted=true;
            var world=r.server.getWorldPath(LevelResource.ROOT);
            var pd=nbt(r.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(r.host.getUUID()+".dat"));
            var level=nbt(world.resolve("level.dat"));
            require(level.get("Data") instanceof CompoundTag data && data.get("Player") instanceof CompoundTag,"HOST_PLAYER_MATERIAL");
            var hp=level.getCompound("Data").getCompound("Player");
            require(savedPlayer(r,pd) && savedPlayer(r,hp),"BOTH_ORIGINAL_PLAYER_WRITERS");
            var peer=nbt(r.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(r.peer.getUUID()+".dat"));
            require(peer.getInt("XpTotal")==r.peerXp,"PEER_PHYSICAL_XP");
            var pa=json(world.resolve("advancements").resolve(r.host.getUUID()+".json"));
            require(pa.has(reward().toString()) && pa.getAsJsonObject(reward().toString()).get("done").getAsBoolean(),"PA_PHYSICAL");
            var stats=json(world.resolve("stats").resolve(r.host.getUUID()+".json")).getAsJsonObject("stats");
            require(stat(stats,"minecraft:killed","minecraft:chicken")==r.chickenBefore+1
                    && stat(stats,"minecraft:custom","minecraft:mob_kills")==r.mobBefore+1,"STATS_PHYSICAL");
            var peerStats=json(world.resolve("stats").resolve(r.peer.getUUID()+".json")).getAsJsonObject("stats");
            require(stat(peerStats,"minecraft:killed","minecraft:chicken")==r.peerChicken
                    && stat(peerStats,"minecraft:custom","minecraft:mob_kills")==r.peerMob,"PEER_STATS_UNCHANGED");
            var scoreboard=nbt(world.resolve("data/scoreboard.dat")).getCompound("data").getList("PlayerScores",10);
            int found=0; for(int i=0;i<scoreboard.size();i++) { var row=scoreboard.getCompound(i);
                if(row.getString("Objective").equals("p11_l1") && row.getString("Name").equals(r.host.getScoreboardName())) {
                    require(row.getInt("Score")==1,"SCOREBOARD_VALUE"); found++;
                }
            } require(found==1,"SCOREBOARD_PHYSICAL");
            r.physical=true; r.complete=true;
            var facts=facts(r,"ORIGINAL_HOST_QUIT_STOP_WORK_AND_BOTH_PLAYER_WRITERS");
            facts.put("nativeStopNormal",s.nativeStopNormal()); facts.put("dirtyUuids",s.resources().dirtyUuids()); facts.put("sourceFailures",s.failures());
            facts.put("allRootCounts",s.nativeResponsibilities().roots().stream().map(x->Map.of("kind",x.kind(),"count",x.count())).toList());
            P11C4aEvidence.write(r.output,"host-l1-stopped.json",facts); r.written=true;
        } catch(IOException|RuntimeException|Error failure) {
            r.sealFailure="STOP_PHYSICAL_OR_TERMINAL_UNPROVED";
            if(r.failure==null) r.failure=r.sealFailure;
            try { P11C4aEvidence.write(r.output,"host-l1-stop-failure.json",facts(r,"FAIL")); }
            catch(IOException|RuntimeException|Error secondary) { /* No replacement native failure or save. */ }
        }
    }
    static boolean complete() {var r=active; return r!=null && r.complete && r.failure==null && r.written;}
    private static void terminalWork(Run r) {
        require(r.instances[1]!=null && r.instances[1].work==null && r.instances[1].lease.pin.isClosed()
                && r.permits[1]!=null && r.permits[1].state==RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                && r.projectiles[1].isRemoved() && r.instances[1].activeProjectileContinuation==null
                && r.instances[1].p9Diagnostic!=null && r.instances[1].p9Diagnostic.terminalReason!=null && work(r)==0,"WORK_STOP_NOT_SYNTHETIC");
    }
    private static void open(Run r) {
        require(r.accepted==2 && r.instances[1].work!=null && !r.instances[1].lease.pin.isClosed()
                && r.permits[1].state==RuntimeProjectileContinuationPermit.State.OPEN && !r.projectiles[1].isRemoved()
                && r.host.serverLevel().getEntity(r.projectiles[1].getUUID())==r.projectiles[1] && work(r)==1,"ACTUAL_OPEN_W_AT_BOUNDARY");
    }
    private static void current(Run r) {
        require(r.server.isSameThread() && r.hostC.isConnected() && r.peerC.isConnected()
                && r.server.getPlayerList().getPlayer(r.host.getUUID())==r.host && r.server.getPlayerList().getPlayer(r.peer.getUUID())==r.peer
                && r.source.body(r.host)==r.body && r.source.body(r.peer)==r.peerBody && r.body.source.epoch()==r.epoch
                && r.source.canCopy(r.body) && r.source.canCopy(r.peerBody),"CURRENT_SAME_BODIES");
    }
    private static long work(Run r){return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()];}
    private static int score(Run r){var objective=r.server.getScoreboard().getObjective("p11_l1"); var score=objective==null?null:r.server.getScoreboard().getPlayerScoreInfo(r.host,objective); return score==null?0:score.value();}
    private static boolean savedPlayer(Run r,CompoundTag tag){
        if(!tag.hasUUID("UUID") || !tag.getUUID("UUID").equals(r.host.getUUID()) || tag.getInt("XpTotal")!=r.xpBefore+10
                || !tag.getCompound("recipeBook").getList("recipes",8).stream().anyMatch(x->x.getAsString().equals("minecraft:bread")))return false;
        int bread=0;var inv=tag.getList("Inventory",10); for(int i=0;i<inv.size();i++){var stack=ItemStack.parseOptional(r.server.registryAccess(),inv.getCompound(i));if(stack.is(Items.BREAD))bread+=stack.getCount();}return bread==1;
    }
    private static CompoundTag nbt(Path p)throws IOException{shape(p); return NbtIo.readCompressed(p,NbtAccounter.create(32L*1024*1024));}
    private static com.google.gson.JsonObject json(Path p)throws IOException{shape(p);return com.google.gson.JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private static void shape(Path p)throws IOException{require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p)&&Files.size(p)<=4L*1024*1024,"OWNED_FILE_SHAPE");}
    private static int stat(com.google.gson.JsonObject s,String group,String key){var g=s.getAsJsonObject(group);return g!=null&&g.has(key)?g.get(key).getAsInt():0;}
    private static ResourceLocation reward(){return ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering","l1_first_kill");}
    /** Fixed scalar snapshot at the first actual hook entry, before any positive assertion. */
    private static Map<String,Object> boundaryFacts(Run r) {
        var f=new LinkedHashMap<String,Object>();
        f.put("owningThread",r.server.isSameThread());
        f.put("serverRunning",r.server.isRunning()); f.put("quitIntent",r.quitIntent);
        // Do not invoke source/roster readers from a wrong-thread callback.
        if(!r.server.isSameThread()) return f;
        f.put("serverTick",r.server.getTickCount()); f.put("serverStopped",r.server.isStopped());
        f.put("quitCue",r.quitCue);
        var instance=r.instances[1]; var permit=r.permits[1]; var projectile=r.projectiles[1];
        f.put("hostConnectionConnected",r.hostC.isConnected());
        f.put("hostAcceptingMessages",r.host.connection.isAcceptingMessages());
        f.put("hostPacketListenerExact",r.hostC.getPacketListener()==r.host.connection);
        f.put("hostListenerPlayerExact",r.host.connection.player==r.host);
        f.put("hostRosterExact",r.server.getPlayerList().getPlayer(r.host.getUUID())==r.host);
        f.put("hostRemoved",r.host.isRemoved()); f.put("hostDeadOrDying",r.host.isDeadOrDying());
        f.put("hostHealthPositive",r.host.getHealth()>0.0F); f.put("hostDisconnectedFlag",r.host.hasDisconnected());
        f.put("sourceBodyExact",r.source.body(r.host)==r.body); f.put("accountCurrentExact",r.body.account.current==r.body);
        f.put("sourceCanCopy",r.source.canCopy(r.body)); f.put("bodyComplete",r.body.complete);
        f.put("bodyFault",r.body.fault.name()); f.put("cleanupUnknown",r.body.account.cleanupUnknown);
        f.put("bodyLogoutAttempted",r.body.logoutAttempted); f.put("bodyLogoutActive",r.body.logoutActive);
        f.put("workCount",work(r)); f.put("instancePresent",instance!=null);
        f.put("workPresent",instance!=null&&instance.work!=null);
        f.put("workQualifiesExactHost",instance!=null&&instance.work!=null&&instance.work.qualifies(r.host));
        f.put("instanceTerminal",instance!=null&&instance.terminal);
        f.put("instanceCancelled",instance!=null&&instance.cancellationRequested);
        f.put("instanceInFlight",instance!=null&&instance.inFlight);
        f.put("logoutState",instance==null?"ABSENT":instance.logoutState.name());
        f.put("logoutScopePresent",instance!=null&&instance.logoutScope!=null);
        f.put("pinClosed",instance!=null&&instance.lease.pin.isClosed());
        f.put("permitState",permit==null?"ABSENT":permit.state.name());
        f.put("projectileAge",projectile==null?-1:projectile.tickCount);
        f.put("projectileRemoved",projectile!=null&&projectile.isRemoved());
        f.put("loadedProjectileExact",projectile!=null&&r.host.serverLevel().getEntity(projectile.getUUID())==projectile);
        f.put("terminalReason",instance==null||instance.p9Diagnostic==null||instance.p9Diagnostic.terminalReason==null
                ?"ABSENT":instance.p9Diagnostic.terminalReason.name());
        return f;
    }
    private static Map<String,Object> facts(Run r,String status){var f=new LinkedHashMap<String,Object>();f.put("status",status);f.put("failure",r.failure==null?"NONE":r.failure);f.put("firstObserverFailure",r.firstObserverFailure==null?"NONE":r.firstObserverFailure);f.put("sealFailure",r.sealFailure==null?"NONE":r.sealFailure);f.put("firstActualHookBoundary",r.firstHookBoundary);f.put("firstActualHookFacts",r.firstHookFacts==null?Map.of("observed",false):r.firstHookFacts);f.put("actualLogoutHookEntries",r.logoutHookEntries);f.put("actualRuntimeStopHookEntries",r.stopHookEntries);f.put("physicalReadbackAttempted",r.physicalReadbackAttempted);f.put("originalBaseHalt",r.baseHaltFacts==null?Map.of("observed",false):r.baseHaltFacts);f.put("baseHaltObservationFailed",r.baseHaltObservationFailed);f.put("acceptedRealR",r.accepted);f.put("originalHurtReturns",r.damageReturns);f.put("peerOriginalSwings",r.peerSwings);f.put("firstNativeBoundary",r.firstBoundary);f.put("logoutEntries",r.logoutEntries);f.put("logoutReturns",r.logoutReturns);f.put("runtimeStopEntries",r.stopEntries);f.put("runtimeStopReturns",r.stopReturns);f.put("hostLogouts",r.hostLogouts);f.put("peerLogouts",r.peerLogouts);f.put("originalHostPlayerDatAndLevelPlayer",r.physical);f.put("hostIsDedicatedAuthProof",false);f.put("fullC4aMatrixClaim",false);f.put("normalLogoutExtendedWorkClaim",false);f.put("secondTerminalReason",r.instances[1]==null||r.instances[1].p9Diagnostic==null||r.instances[1].p9Diagnostic.terminalReason==null?"ABSENT":r.instances[1].p9Diagnostic.terminalReason.name());return f;}
    private static void observe(Run r,Runnable action){observe(r,"NATIVE_OBSERVER_FAILED",action);}
    private static void observe(Run r,String fixedFailure,Runnable action){try{action.run();}catch(RuntimeException|Error secondary){if(r.firstObserverFailure==null)r.firstObserverFailure=fixedFailure;if(r.failure==null)r.failure=fixedFailure;}}
    private static void require(boolean v,String code){P11C4aEvidence.require(v,"L1_HOST_"+code);}
    private static final class Run {
        final MinecraftServer server;final ServerPlayer host,peer;final Connection hostC,peerC;final Path output;
        final P11QualifiedSourceOwner source;final P11QualifiedSourceOwner.Body body,peerBody;final long epoch;
        final int xpBefore,peerXp,chickenBefore,mobBefore,peerChicken,peerMob,peerTickBefore;
        final ServerSlot.InstanceState[] instances=new ServerSlot.InstanceState[2];final P9StarterProjectile[] projectiles=new P9StarterProjectile[2];final RuntimeProjectileContinuationPermit[] permits=new RuntimeProjectileContinuationPermit[2];
        int created,accepted,ticks,damageReturns,peerSwings,hostLogouts,peerLogouts,stopEntries,stopReturns,logoutEntries,logoutReturns,stops,rootReturns;
        int stopHookEntries,logoutHookEntries; boolean physicalReadbackAttempted;
        String firstObserverFailure,sealFailure,firstHookBoundary="NOT_OBSERVED";
        Map<String,Object> firstHookFacts;
        volatile Map<String,Object> baseHaltFacts; volatile boolean baseHaltObservationFailed;
        boolean firstCue,firstTerminal,secondCue,quitCue,boundaryObserved,writersCurrent,written,physical,complete;volatile boolean quitIntent;String failure,firstBoundary="NOT_OBSERVED";LivingEntity victim;
        P11QualifiedSourceOwner.Summary summary;
        Run(MinecraftServer s,ServerPlayer h,ServerPlayer p,Path o){server=s;host=h;peer=p;output=o;hostC=h.connection.getConnection();peerC=p.connection.getConnection();source=P11NativeStorageBoundary.nativeSourceOwner(h);body=source==null?null:source.body(h);peerBody=source==null?null:source.body(p);epoch=body==null?0:body.source.epoch();xpBefore=h.totalExperience;peerXp=p.totalExperience;chickenBefore=h.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN));mobBefore=h.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));peerChicken=p.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.CHICKEN));peerMob=p.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));peerTickBefore=p.tickCount;}
    }
}
