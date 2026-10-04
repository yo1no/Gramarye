package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual extracted claim/custody bodies in a finite shell, not native execution. */
final class P11AcceptedWorkObservedHitTest {
    @TempDir Path temporary;

    @Test void actualClaimBodyRetainsOnlyOneObservedCandidateUntilLegalCurrentTickPublication() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String projectile = read("P9StarterProjectile.java");
        String model = MODEL.replace("/* CLAIM */", method(runtime,
                "private Optional<RuntimePermitClaimDisposition> claimProjectileHitInSlot("))
                .replace("/* OWNER_ENTRY */", method(runtime, "Optional<RuntimePermitClaimDisposition> claimObservedProjectileHit("))
                .replace("/* HAS_HIT */", method(projectile, "boolean hasObservedHit("))
                .replace("/* HAS_TARGET */", method(projectile, "boolean hasObservedTarget("))
                .replace("/* OBSERVE */", method(projectile, "void observeHitTick("))
                .replace("/* CLEAR */", method(projectile, "void clearObservedHit(RuntimeProjectileContinuationPermit permit)"))
                .replace("/* CLEAR_PRIVATE */", method(projectile, "private void clearObservedHit()"))
                .replace("/* ENTITY_HIT */", method(projectile, "protected void onHitEntity(")
                        .replace("SkillRuntimeService.WorkQualification", "WorkQualification"))
                .replace("/* BLOCK_HIT */", method(projectile, "protected void onHitBlock("))
                .replace("/* RESUME */", method(projectile, "void resumeObservedHit("))
                .replace("/* SUBMIT */", method(projectile, "private void submitObservedHit("));
        String tick = method(projectile, "public void tick()");
        model = model.replace("/* IMPACT_AND_TAIL_GUARD */", tick.substring(
                tick.indexOf("var hit = ProjectileUtil.getHitResultOnMoveVector"), tick.indexOf("checkInsideBlocks();")));
        Path source = temporary.resolve("ObservedHitModel.java"); Files.writeString(source, model);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-proc:none", "--release", "21", "-d", temporary.toString(), source.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {temporary.toUri().toURL()}, null)) {
            loader.loadClass("ObservedHitModel").getMethod("verify").invoke(null);
        }
    }

    @Test void preAdvanceResumePreservesPrimaryAndUsesOnlyBoundedExistingPermits() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String post = method(runtime, "void handleRuntimePost(");
        ordered(post, "if (p9ReloadCloseRequested.get())", "resumeObservedP9Hits(server, slot)", "advanceRuntimeTick(slot)");
        String scan = method(runtime, "private void resumeObservedP9Hits(");
        for (String exact : new String[] {"size() > 128", "List.copyOf(slot.activeProjectileContinuations.values())",
                "slot.activeProjectileContinuations.get(permit.permitId) != permit", "loadedProjectile(server, permit)",
                "projectile.resumeObservedHit(permit)"}) { assertTrue(scan.contains(exact), exact); }
        String region = post.substring(post.indexOf("resumeObservedP9Hits(server, slot)"), post.indexOf("advanceRuntimeTick(slot)"));
        ordered(region, "catch (RuntimeException primary)", "throw preserveRuntimeFault(slot, primary)",
                "catch (Error primary)", "slot.p9ErrorCleanup.prepare(server)", "throw preserveErrorFault(slot, primary)");
        assertFalse(method(runtime, "void endNormalLogout(").contains("resumeObserved"));
        assertFalse(scan.contains("new RuntimeEvent") || scan.contains(".queue") || scan.contains(".hurt(") || scan.contains("runtimeTick++"));
        String claim = method(runtime, "private Optional<RuntimePermitClaimDisposition> claimProjectileHitInSlot(");
        ordered(claim, "slot.runtimeTick >= permit.deadlineRuntimeTick", "livingTarget.isRemoved()",
                "validateChildShape(", "actorQualification(server, instance, actor, permit.dimension)",
                "return Optional.empty()", "qualification != WorkQualification.EXECUTABLE",
                "projectile.clearObservedHit(permit)", "var child = new RuntimeEvent(", "addCommittedEvent(");
        assertFalse(claim.contains("observedHitRuntimeTick,") || claim.contains("deadlineRuntimeTick ="));
    }

    @Test void pendingCollisionCannotPassThroughReplayImpactOrSurviveActualTerminal() throws Exception {
        String source = read("P9StarterProjectile.java");
        String tick = method(source, "public void tick()");
        ordered(tick, "EventHooks.onProjectileImpact(this, hit)", "onHitEntity(entityHit)",
                "if (observedHit != null) { return; }", "checkInsideBlocks()", "setPos(endpoint)");
        assertEquals(1, occurrences(tick, "EventHooks.onProjectileImpact("));
        String entity = method(source, "protected void onHitEntity(");
        assertFalse(entity.contains("awaitingNativeCleanup"));
        ordered(entity, "new ProjectileHitCandidateV0(", "observedTargetEntityId = target.getId()", "submitObservedHit(serverLevel)");
        String block = method(source, "protected void onHitBlock(");
        assertFalse(block.contains("awaitingNativeCleanup"));
        assertTrue(block.contains("terminate(ProjectileClosureReason.BLOCK_OR_INVALID_HIT)"));
        String resume = method(source, "void resumeObservedHit(");
        ordered(resume, "if (tickCount > 100)", "submitObservedHit(serverLevel)");
        for (String forbidden : new String[] {"EventHooks", "onHitEntity", "baseTick", "setPos", "applyGravity", "new ProjectileHitCandidateV0"}) {
            assertFalse(resume.contains(forbidden), forbidden);
        }
        for (String signature : new String[] {"public void onRemovedFromLevel()", "private void terminate(", "private void closeAndDiscard("}) {
            assertTrue(method(source, signature).contains("clearObservedHit()"), signature);
        }
        String submit = method(source, "private void submitObservedHit(");
        ordered(submit, "var candidate = observedHit", "continuationPermit.submitObservedHit(",
                "if (result.isEmpty()) { return; }", "clearObservedHit()", "locallyClaimedOrTerminal = true");
        ordered(submit, "catch (RuntimeException failure)", "clearObservedHit()", "bestEffortClose(",
                "bestEffortDiscard()", "throw failure", "catch (Error failure)", "clearObservedHit()", "throw failure");
    }

    private static String read(String name) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) { root = root.getParent(); }
        assertNotNull(root); return Files.readString(root.resolve("src/main/java/com/yo1no/gramarye/" + name));
    }
    private static String method(String source, String signature) {
        int start = source.indexOf(signature); assertTrue(start >= 0, signature);
        int open = source.indexOf('{', start), end = open + 1, depth = 1;
        while (depth > 0 && end < source.length()) {
            if (source.charAt(end) == '{') depth++;
            if (source.charAt(end) == '}') depth--;
            end++;
        }
        assertEquals(0, depth); return source.substring(start, end);
    }
    private static void ordered(String source, String... tokens) {
        int cursor = 0;
        for (String token : tokens) {
            int next = source.indexOf(token, cursor); assertTrue(next >= cursor, token); cursor = next + token.length();
        }
    }
    private static int occurrences(String source, String token) { return source.split(java.util.regex.Pattern.quote(token), -1).length - 1; }

    private static final String MODEL = """
        import java.util.*;
        import java.util.concurrent.atomic.AtomicBoolean;
        public final class ObservedHitModel {
          enum WorkQualification { EXECUTABLE, NATIVE_CLEANUP_PENDING, LOGOUT_IN_PROGRESS, INVALID }
          enum RuntimePermitClaimDisposition { QUEUED, REJECTED, DUPLICATE_OR_LATE }
          enum RuntimeSchedulePersistence { MEMORY_ONLY }
          enum ProjectileClosureReason {ENTITY_OR_LEVEL_REMOVED,BLOCK_OR_INVALID_HIT,AGE_EXHAUSTED,RUNTIME_FAULT,CLAIM_REJECTED}
          static final class MinecraftServer { boolean running=true, stopped; boolean isRunning(){return running;} boolean isStopped(){return stopped;} boolean isSameThread(){return true;} }
          static class Entity {
            UUID uuid=UUID.randomUUID(); int id=7; Level level; boolean removed,alive=true;
            UUID getUUID(){return uuid;} int getId(){return id;} Level level(){return level;} boolean isRemoved(){return removed;} boolean isAlive(){return alive;}boolean isAddedToLevel(){return true;}
          }
          static final class LivingEntity extends Entity {}
          static final class Actor extends Entity { Level serverLevel(){return level;} }
          static final class P9StarterProjectile extends Entity {
            ObservedHitModel owner;RuntimeProjectileContinuationPermit continuationPermit; Actor actor,authenticatedCasterIdentity;Object dimension="dimension";boolean locallyClaimedOrTerminal;
            ProjectileHitCandidateV0 observedHit; long observedHitRuntimeTick=-1; int observedTargetEntityId=-1;
            int tickCount=4,impactCalls,tailCalls,positionCalls,discardCalls,closeCalls;HitResult plannedHit;
            Actor actorWitness(RuntimeProjectileContinuationPermit permit){return permit==continuationPermit?actor:null;}
            boolean hasAuthenticatedCasterIdentity(Actor value){return actor==value;}
            boolean hasContinuationPermitIdentity(RuntimeProjectileContinuationPermit permit){return permit==continuationPermit;}
            /* HAS_HIT */
            /* HAS_TARGET */
            /* OBSERVE */
            /* CLEAR */
            /* CLEAR_PRIVATE */
            /* ENTITY_HIT */
            /* BLOCK_HIT */
            /* RESUME */
            /* SUBMIT */
            boolean canHitEntity(Entity e){return true;}
            Vec3 getDeltaMovement(){return new Vec3(1,0,0);}static int[] q15(Vec3 v){return new int[]{32767,0,0};}
            static boolean finite(Vec3 v){return true;}static boolean legalPosition(Vec3 v){return true;}
            void setPos(double x,double y,double z){positionCalls++;}void setDeltaMovement(Vec3 v){}void setNoGravity(boolean b){}
            void terminate(ProjectileClosureReason reason){clearObservedHit();locallyClaimedOrTerminal=true;continuationPermit.state=RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT;}
            void closeAndDiscard(ServerLevel level,ProjectileClosureReason reason){terminate(reason);discardCalls++;}
            void bestEffortClose(ServerLevel level,ProjectileClosureReason reason){closeCalls++;}
            void bestEffortDiscard(){discardCalls++;}
            void tickImpactSegment(){var movement=getDeltaMovement();/* IMPACT_AND_TAIL_GUARD */tailCalls++;}
          }
          static class Level {
            Map<UUID,Entity> entities=new HashMap<>(); boolean loaded=true,bounds=true,border=true;
            Entity getEntity(UUID id){return entities.get(id);} Dimension dimension(){return new Dimension();}boolean isClientSide(){return false;}
            boolean isInWorldBounds(Object pos){return bounds;} boolean isLoaded(Object pos){return loaded;}
            Level getWorldBorder(){return this;} boolean isWithinBounds(double x,double z){return border;}
          }
          static final class Dimension {Object location(){return "dimension";}}
          static final class ServerLevel extends Level {MinecraftServer server;MinecraftServer getServer(){return server;}}
          static class HitResult {enum Type{MISS,ENTITY,BLOCK}Type getType(){return Type.MISS;}}
          static final class EntityHitResult extends HitResult {Entity entity;EntityHitResult(Entity e){entity=e;}Entity getEntity(){return entity;}Vec3 getLocation(){return new Vec3(1,2,3);}Type getType(){return Type.ENTITY;}}
          static final class BlockHitResult extends HitResult {Type getType(){return Type.BLOCK;}}
          static final class Vec3 {static final Vec3 ZERO=new Vec3(0,0,0);final double x,y,z;Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}}
          static final class ProjectileUtil {static HitResult getHitResultOnMoveVector(P9StarterProjectile p,java.util.function.Predicate<Entity> f){return p.plannedHit;}}
          static final class EventHooks {static boolean onProjectileImpact(P9StarterProjectile p,HitResult h){p.impactCalls++;p.owner.qualification=WorkQualification.NATIVE_CLEANUP_PENDING;return false;}}
          record Id(long value){} record SourceFamilyKey(Integer skillInstanceId,int producerNodeIndex,int outputOrdinal,Id sourceEventId){}
          static final class Pin {boolean closed;boolean isClosed(){return closed;}}
          static final class Lease {Pin pin=new Pin();Object reference="ref",definition=new Object();}
          record PlayerId(UUID value){} record PlayerRuntimeBudgetAttribution(PlayerId playerId){}
          static final class Attribution {int reservedPending=1;}
          static final class Instance {Integer id=1;Object attribution;long sequence=1;boolean terminal,cancellationRequested,inFlight;Lease lease=new Lease();int reservedPending=1,lifetimeEvents;RuntimeProjectileContinuationPermit activeProjectileContinuation;}
          static final class Limits {int eventsPerSkillInstance(){return 512;}}
          static final class ServerSlot {
            enum State {RUNNING,STOPPING} State state=State.RUNNING;boolean dispatching;Object currentEvent;Object token="token";
            long runtimeTick=20,eventSequenceHighWater=5;int reservedPending=1;Limits limits=new Limits();
            Map<Integer,Instance> instances=new HashMap<>();Map<UUID,RuntimeProjectileContinuationPermit> activeProjectileContinuations=new HashMap<>();
            Map<Object,Attribution> attributions=new HashMap<>();Map<Id,RuntimeEvent> eventIndex=new HashMap<>();
          }
          static final class RuntimeProjectileContinuationPermit {
            ObservedHitModel owner;
            enum Mode {REAL} enum State {OPEN,CLAIMED_PENDING_DAMAGE,CLOSED_NO_HIT}
            Mode mode=Mode.REAL;State state=State.OPEN;Object serverSlotToken="token",exactReference="ref",budgetAttribution,dimension="dimension";
            int skillInstanceId=1,sourceDerivationDepth;long deadlineRuntimeTick=100;
            UUID permitId=UUID.randomUUID(),plannedProjectileId=UUID.randomUUID();Id heldChildEventId=new Id(5);
            SourceFamilyKey sourceFamily=new SourceFamilyKey(1,0,0,new Id(1));
            WorkQualification qualification(MinecraftServer s,P9StarterProjectile p){return owner.qualification;}
            Optional<RuntimePermitClaimDisposition> submitObservedHit(MinecraftServer s,P9StarterProjectile p,ProjectileHitCandidateV0 c){return owner.claimObservedProjectileHit(s,this,p,c);}
          }
          record ProjectileHitCandidateV0(UUID projectileId,UUID targetId,Object dimension,double hitX,double hitY,double hitZ,int directionXQ15,int directionYQ15,int directionZQ15){}
          static final class BlockPos {static Object containing(double x,double y,double z){return new Object();}}
          static final class ProjectileHitExecutionDataV0 {ProjectileHitExecutionDataV0(Object... values){}}
          static final class PlayerOrigin {PlayerOrigin(Object... values){}}
          static final class RuntimeEntityId {RuntimeEntityId(Object value){}}
          enum RuntimeEntityKind {LIVING_ENTITY}
          static final class EntityTarget {EntityTarget(Object... values){}}
          static final class ChildTriggerCause {ChildTriggerCause(Object value){}}
          static final class TriggerEventKind {TriggerEventKind(Object value){}}
          static final class P9StarterSkillContent {static final Object EFFECT_HIT_ID="hit";}
          static final class RuntimeCancellationToken {RuntimeCancellationToken(Object... values){}}
          static final class RuntimeChildSpec {Object[] values;RuntimeChildSpec(Object... values){this.values=values;}Object origin(){return values[3];}Object target(){return values[4];}Object triggerCause(){return values[5];}}
          static final class RuntimeEvent {long created,scheduled,deadline;RuntimeEvent(Object... values){created=(Long)values[7];scheduled=(Long)values[8];deadline=(Long)values[9];}}
          final AtomicBoolean p9ReloadCloseRequested=new AtomicBoolean();
          final MinecraftServer server=new MinecraftServer(); final ServerSlot slot=new ServerSlot(); final ServerLevel level=new ServerLevel();
          final Instance instance=new Instance(); final Attribution attribution=new Attribution(); final RuntimeProjectileContinuationPermit permit=new RuntimeProjectileContinuationPermit();
          final Actor actor=new Actor(); final P9StarterProjectile projectile=new P9StarterProjectile(); final LivingEntity target=new LivingEntity();
          ProjectileHitCandidateV0 candidate;WorkQualification qualification=WorkQualification.NATIVE_CLEANUP_PENDING;int queued,rejected;boolean shapeInvalid;Throwable primary;
          ObservedHitModel(){
            level.server=server;permit.owner=this;projectile.owner=this;actor.level=level;target.level=level;projectile.level=level;projectile.uuid=permit.plannedProjectileId;projectile.actor=actor;projectile.authenticatedCasterIdentity=actor;projectile.continuationPermit=permit;
            permit.budgetAttribution=new PlayerRuntimeBudgetAttribution(new PlayerId(actor.uuid));
            instance.attribution=permit.budgetAttribution;
            instance.activeProjectileContinuation=permit;slot.instances.put(1,instance);slot.activeProjectileContinuations.put(permit.permitId,permit);slot.attributions.put(permit.budgetAttribution,attribution);
            level.entities.put(projectile.uuid,projectile);level.entities.put(target.uuid,target);
            candidate=new ProjectileHitCandidateV0(projectile.uuid,target.uuid,"dimension",1,2,3,32767,0,0);
            projectile.observedHit=candidate;projectile.observedTargetEntityId=target.id;
          }
          Level levelForDimension(MinecraftServer s,Object d){return level;}
          WorkQualification actorQualification(MinecraftServer s,Instance i,Actor a,Object d){check(a==actor,"exact actor");return qualification;}
          static boolean awaitingNativeCleanup(WorkQualification q){return q==WorkQualification.NATIVE_CLEANUP_PENDING||q==WorkQualification.LOGOUT_IN_PROGRESS;}
          Optional<Object> validateChildShape(Object d,Object a,RuntimeChildSpec c){return shapeInvalid?Optional.of(new Object()):Optional.empty();}
          RuntimePermitClaimDisposition rejectClaimAndClose(MinecraftServer s,ServerSlot slot,Instance i,RuntimeProjectileContinuationPermit p,ProjectileHitCandidateV0 c){rejected++;p.state=RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT;projectile.clearObservedHit(p);return RuntimePermitClaimDisposition.REJECTED;}
          void addCommittedEvent(ServerSlot s,Instance i,Attribution a,RuntimeEvent e){if(primary instanceof RuntimeException r)throw r;if(primary instanceof Error e0)throw e0;check(projectile.observedHit==null,"clear before publish");queued++;s.eventIndex.put(permit.heldChildEventId,e);}
          void recordP9HitClaimResult(Object... values){}
          /* CLAIM */
          /* OWNER_ENTRY */
          Optional<RuntimePermitClaimDisposition> submitProjectileHit(MinecraftServer s,RuntimeProjectileContinuationPermit p,ProjectileHitCandidateV0 c,P9StarterProjectile observed){return claimProjectileHitInSlot(s,slot,p,c,observed);}
          Optional<RuntimePermitClaimDisposition> call(){return claimObservedProjectileHit(server,permit,projectile,candidate);}
          static void check(boolean good,String name){if(!good)throw new AssertionError(name);}
          public static void verify(){
            var same=new ObservedHitModel();var original=same.candidate;
            check(same.call().isEmpty(),"hold");check(same.projectile.observedHit==original&&same.projectile.observedHitRuntimeTick==20,"original custody");
            check(same.queued==0&&same.rejected==0&&same.slot.reservedPending==1&&same.instance.lifetimeEvents==0&&same.slot.eventIndex.isEmpty(),"hold no publication/account change");
            same.qualification=WorkQualification.LOGOUT_IN_PROGRESS;check(same.call().isEmpty(),"cleanup still hold");
            same.qualification=WorkQualification.EXECUTABLE;check(same.call().orElseThrow()==RuntimePermitClaimDisposition.QUEUED,"legal once");
            var event=same.slot.eventIndex.get(same.permit.heldChildEventId);check(event.created==20&&event.scheduled==20&&event.deadline==100,"ordinary unchanged schedule");
            check(same.queued==1&&same.slot.reservedPending==0&&same.instance.lifetimeEvents==1,"sole child accounting");
            check(same.call().orElseThrow()==RuntimePermitClaimDisposition.REJECTED&&same.queued==1&&same.permit.state==RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE,"duplicate cannot close genuine child");
            var later=new ObservedHitModel();later.call();later.slot.runtimeTick=25;check(later.call().isEmpty()&&later.projectile.observedHitRuntimeTick==20,"cross post no permission or capture rewrite");
            later.qualification=WorkQualification.EXECUTABLE;later.call();event=later.slot.eventIndex.get(later.permit.heldChildEventId);check(event.created==25&&event.scheduled==25&&event.deadline==100,"no backdate or deadline renewal");
            for(int n=0;n<12;n++){var bad=new ObservedHitModel();switch(n){case 0->bad.slot.runtimeTick=100;case 1->bad.instance.cancellationRequested=true;case 2->bad.p9ReloadCloseRequested.set(true);case 3->bad.target.removed=true;case 4->bad.target.alive=false;case 5->bad.level.loaded=false;case 6->bad.target.id++;case 7->bad.permit.exactReference="wrong";case 8->bad.instance.lease.pin.closed=true;case 9->bad.qualification=WorkQualification.INVALID;case 10->bad.shapeInvalid=true;case 11->bad.slot.state=ServerSlot.State.STOPPING;}
              check(bad.call().orElseThrow()==RuntimePermitClaimDisposition.REJECTED&&bad.queued==0&&bad.projectile.observedHit==null,"invalid before pending "+n);}
            var other=new ObservedHitModel();other.projectile.observedHit=new ProjectileHitCandidateV0(other.projectile.uuid,other.target.uuid,"dimension",1,2,3,32767,0,0);check(other.call().orElseThrow()==RuntimePermitClaimDisposition.REJECTED,"no replacement candidate");
            var error=new ObservedHitModel();error.qualification=WorkQualification.EXECUTABLE;error.primary=new IllegalStateException("owned-model-primary");try{error.call();throw new AssertionError("primary missing");}catch(IllegalStateException actual){check(actual==error.primary&&error.projectile.observedHit==null,"same primary and cleared custody");}
            var nativeHit=new ObservedHitModel();nativeHit.projectile.clearObservedHit();nativeHit.qualification=WorkQualification.EXECUTABLE;nativeHit.projectile.plannedHit=new EntityHitResult(nativeHit.target);
            nativeHit.projectile.tickImpactSegment();check(nativeHit.projectile.impactCalls==1&&nativeHit.projectile.tailCalls==0&&nativeHit.projectile.observedHit!=null&&nativeHit.queued==0,"actual captured uncancelled callback no pass through");
            var captured=nativeHit.projectile.observedHit;nativeHit.projectile.onHitEntity(new EntityHitResult(nativeHit.target));check(nativeHit.projectile.observedHit==captured,"duplicate callback cannot replace candidate");
            nativeHit.qualification=WorkQualification.EXECUTABLE;nativeHit.projectile.resumeObservedHit(nativeHit.permit);check(nativeHit.queued==1&&nativeHit.projectile.impactCalls==1&&nativeHit.projectile.tailCalls==0&&nativeHit.projectile.positionCalls==1,"actual resume no impact or flight replay");
            nativeHit.projectile.resumeObservedHit(nativeHit.permit);check(nativeHit.queued==1,"resume once");
            var block=new ObservedHitModel();block.projectile.clearObservedHit();block.projectile.plannedHit=new BlockHitResult();block.projectile.tickImpactSegment();check(block.projectile.impactCalls==1&&block.projectile.tailCalls==0&&block.projectile.locallyClaimedOrTerminal&&block.queued==0,"block remains native terminal");
            for(boolean raw:new boolean[]{false,true}){var failure=new ObservedHitModel();failure.projectile.clearObservedHit();failure.projectile.plannedHit=new EntityHitResult(failure.target);failure.projectile.tickImpactSegment();failure.qualification=WorkQualification.EXECUTABLE;failure.primary=raw?new AssertionError("owned-error"):new IllegalStateException("owned-runtime");try{failure.projectile.resumeObservedHit(failure.permit);throw new AssertionError("missing throw");}catch(RuntimeException|Error actual){check(actual==failure.primary&&failure.projectile.observedHit==null,"same native resume primary");check(raw?failure.projectile.discardCalls==0:failure.projectile.discardCalls==1,"original Error vs Runtime cleanup");}}
          }
        }
        """;
}
