package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact source and extracted-method checks; not authenticated/native evidence. */
final class P11AcceptedWorkCleanupPendingTest {
    @TempDir Path temporary;

    @Test void closedCurrentConnectionRetainsButNeverAuthorizesExecution() throws Exception {
        String source = read("SkillRuntimeService.java");
        String model = MODEL + method(source, "private static WorkQualification actorQualification(")
                + method(source, "static boolean awaitingNativeCleanup(") + "}\n";
        Path java = temporary.resolve("CleanupModel.java");
        Files.writeString(java, model);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-proc:none", "-d", temporary.toString(), java.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {temporary.toUri().toURL()}, null)) {
            loader.loadClass("CleanupModel").getMethod("verify").invoke(null);
        }
    }

    @Test void pendingIsNotAPersistentLogoutGrantAndDoesNotRelaxNativeProof() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String actor = method(runtime, "private static WorkQualification actorQualification(");
        assertPending(actor);
        for (String token : new String[] {"instance.work != null", "!candidate.hasDisconnected()",
                "listener.player == candidate", "connection.getPacketListener() == listener",
                "!connection.isConnecting()", "!connection.isConnected()"}) {
            assertThrows(AssertionError.class, () -> assertPending(actor.replace(token, "REMOVED")), token);
        }
        String end = method(runtime, "void endNormalLogout(");
        before(end, "instance.logoutState = LogoutState.INVALID", "proof.confirms(scope.server, scope.actor)");
        before(end, "proof.confirms(scope.server, scope.actor)", "instance.logoutState = LogoutState.COMPLETE");
        assertFalse(actor.contains("logoutState = LogoutState") || actor.contains("new NormalLogoutScope"));
    }

    @Test void zeroWorldReturnIsRequiredBeforeSameEventDeferral() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String adapter = read("P6RuntimeExecutionPortAdapter.java");
        String invoke = method(runtime, "private DetachedInvocation invokeRuntimeBoundary(");
        before(invoke, "executionPort.execute(event, context)", "executionGuard.p9NoCommitSuspended");
        assertTrue(invoke.contains("executionGuard.p9WorldCommitEntered"));
        assertTrue(invoke.contains("batch.outcome() instanceof RuntimePortOutcome.Completed"));
        assertTrue(invoke.contains("!batch.children().children().isEmpty()"));
        before(adapter, "diagnostics.enterP9WorldCommit();", "return handoff.commitSpawn(command);");
        String damage = method(adapter, "public P6RuntimeExecutionBridge.CommitDisposition commitDamage(");
        before(damage, "diagnostics.enterP9WorldCommit();", "return handoff.commitDamage(command);");
        String suspend = method(runtime, "boolean suspendBeforeWorldCommit()");
        assertTrue(suspend.contains("!p9CleanupPendingObserved || p9WorldCommitEntered"));
        assertFalse(suspend.contains("ALLOWED"));
        String guard = method(runtime, "public RuntimeExecutionGuardDecision check()");
        assertTrue(guard.contains("p9CleanupPendingObserved = !p9WorldCommitEntered"));
        assertTrue(guard.contains("awaitingNativeCleanup(qualification) || awaitingNativeCleanup(actor)"));
        assertTrue(guard.contains("qualification != WorkQualification.INVALID && actor != WorkQualification.INVALID"));
        assertTrue(guard.contains("return RuntimeExecutionGuardDecision.CANCELLED;"));
        assertTrue(adapter.contains("P11NativeOperationBoundary.end(nativeOperation[0], normal)"));
    }

    @Test void sameEventAndDeadlineSurviveWithoutBudgetRefundOrSecondQueue() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String suspend = method(runtime, "private boolean suspendUnexecutedCurrent(");
        before(suspend, "eventQualification(server, slot, instance, event)", "slot.deferred[slot.deferredCount++] = event");
        before(suspend, "releaseCurrentReservation(slot, instance, attribution)", "instance.inFlight = false");
        assertTrue(suspend.contains("instance.p9SuspendedEventId = event.eventId()"));
        for (String forbidden : new String[] {"new RuntimeEvent", "scheduledRuntimeTick =", "deadlineRuntimeTick =",
                "executionsThisTick--", "releaseWork(", "lease.release(", "removeCommittedEvent(", ".queue.add("}) {
            assertFalse(suspend.contains(forbidden), forbidden);
        }
        String projectile = read("P9StarterProjectile.java");
        String tick = method(projectile, "public void tick()");
        before(tick, "if (tickCount > 100)", "SkillRuntimeService.awaitingNativeCleanup(");
        assertEquals(2, occurrences(projectile, "SkillRuntimeService.awaitingNativeCleanup("));
        assertFalse(projectile.contains("tickCount =") || projectile.contains("tickCount++"));
    }

    @Test void reservedIdentityIsReattachedAndEveryFailedReopenHasTerminalCleanup() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String opener = method(runtime, "RuntimeProjectileContinuationOpenResult openProjectileContinuation(");
        String reused = section(opener, "var suspended = instance.activeProjectileContinuation;", "if (reservation.capacity() < 1");
        for (String token : new String[] {"State.RESERVED", "suspended.sourceFamily.sourceEventId().equals(sourceEvent.eventId())",
                "suspended.exactReference.equals(sourceEvent.skillReference())", "suspended.deadlineRuntimeTick != sourceEvent.deadlineRuntimeTick()",
                "loadedEntityUuidExists(server, suspended.plannedProjectileId)", "reservation.attach(suspended)",
                "suspended.suspendedBeforeSpawn = false", "suspended, suspended.plannedProjectileId, this"}) {
            assertTrue(reused.contains(token), token);
        }
        assertFalse(reused.contains("new RuntimeProjectileContinuationPermit(") || reused.contains("reservedPending++"));
        String dispatch = method(runtime, "private void dispatchClaimed(");
        assertFailedReopen(dispatch);
        assertThrows(AssertionError.class, () -> assertFailedReopen(dispatch.replace("unopened.suspendedBeforeSpawn", "false")));
        assertThrows(AssertionError.class, () -> assertFailedReopen(dispatch.replace("slot.runtimeTick >= unopened.deadlineRuntimeTick", "false")));
    }

    @Test void resumedStagesDoNotEraseOrDuplicateOriginalDiagnosticHistory() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String resumed = method(runtime, "private static boolean resumingRecordedP9Stage(");
        for (String token : new String[] {"instance.p9SuspendedEventId.equals(event.eventId())", "slot.currentEvent != event",
                "!instance.inFlight", "trace.terminalPublished", "NODE0_MATCHED", "NODE1_MATCHED", "DAMAGE_RESOLVED",
                "hasP9S4DiagnosticCustody(slot, instance, event)", "trace.lastStageOrdinal > lastUnexecuted"}) {
            assertTrue(resumed.contains(token), token);
        }
        assertFalse(resumed.contains("lastStageOrdinal =") || resumed.contains("stageCount =") || resumed.contains(".record("));
        String terminal = method(runtime, "private static void terminalizeCurrent(");
        assertTrue(terminal.contains("instance.p9SuspendedEventId = null"));
        assertTrue(runtime.contains("stage.ordinal() <= lastStageOrdinal") || runtime.contains("ordinal <= lastStageOrdinal"));
    }

    @Test void actualSuspensionReopenAndStageBodiesPreserveCustodyAtBoundaryTicks() throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String opener = method(runtime, "RuntimeProjectileContinuationOpenResult openProjectileContinuation(");
        String retry = section(opener, "if (slot.runtimeTick >= sourceEvent.deadlineRuntimeTick())", "if (reservation.capacity() < 1");
        String dispatch = method(runtime, "private void dispatchClaimed(");
        String failed = section(dispatch, "var unopened = instance.activeProjectileContinuation;", "if (event.executionData() instanceof ProjectileHitExecutionDataV0)");
        String record = method(runtime.substring(runtime.indexOf("static final class P9ActiveDiagnostic")),
                "void record(P9RuntimeDiagnosticStage stage, long runtimeTick)");
        String generated = SCHEDULER_MODEL
                .replace("/* STAGE_ENUM */", method(runtime, "enum P9RuntimeDiagnosticStage"))
                .replace("/* TRACE_RECORD */", record)
                .replace("/* OPENER */", retry)
                .replace("/* FAILED_REOPEN */", failed)
                + method(runtime, "private boolean suspendUnexecutedCurrent(")
                + method(runtime, "private void releaseCurrentReservation(")
                + method(runtime, "private static boolean resumingRecordedP9Stage(") + "}\n";
        Path java = temporary.resolve("SchedulerModel.java");
        Files.writeString(java, generated);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-proc:none", "-d", temporary.toString(), java.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {temporary.toUri().toURL()}, null)) {
            loader.loadClass("SchedulerModel").getMethod("verify").invoke(null);
        }
    }

    private static void assertPending(String actor) {
        for (String token : new String[] {"instance.work != null", "!candidate.hasDisconnected()", "listener.player == candidate",
                "connection.getPacketListener() == listener", "!connection.isConnecting()", "!connection.isConnected()",
                "WorkQualification.NATIVE_CLEANUP_PENDING"}) { assertTrue(actor.contains(token), token); }
        before(actor, "!instance.work.qualifies(candidate)", "WorkQualification.NATIVE_CLEANUP_PENDING");
    }
    private static void assertFailedReopen(String dispatch) {
        before(dispatch, "unopened.suspendedBeforeSpawn", "terminalizeCurrent(slot, instance, event)");
        assertTrue(dispatch.contains("slot.runtimeTick >= unopened.deadlineRuntimeTick"));
        assertTrue(dispatch.contains("ProjectileClosureReason.DEADLINE_REACHED"));
    }
    private static int occurrences(String source, String token) { return source.split(java.util.regex.Pattern.quote(token), -1).length - 1; }
    private static void before(String source, String first, String second) {
        int a = source.indexOf(first), b = source.indexOf(second);
        assertTrue(a >= 0 && b > a, first + " -> " + second);
    }
    private static String section(String source, String start, String end) {
        int a = source.indexOf(start), b = source.indexOf(end, a + start.length());
        assertTrue(a >= 0 && b > a); return source.substring(a, b);
    }
    private static String method(String source, String signature) {
        int start = source.indexOf(signature); assertTrue(start >= 0, signature);
        int open = source.indexOf('{', start), depth = 1, end = open + 1;
        for (; depth > 0 && end < source.length(); end++) {
            if (source.charAt(end) == '{') depth++;
            if (source.charAt(end) == '}') depth--;
        }
        assertEquals(0, depth); return source.substring(start, end);
    }
    private static String read(String name) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) { root = root.getParent(); }
        if (root == null) { throw new IllegalStateException("project root not found"); }
        return Files.readString(root.resolve("src/main/java/com/yo1no/gramarye").resolve(name));
    }

    private static final String MODEL = """
            import java.util.*;
            public class CleanupModel {
              enum WorkQualification { EXECUTABLE, NATIVE_CLEANUP_PENDING, LOGOUT_IN_PROGRESS, INVALID }
              enum LogoutState { ONLINE, IN_PROGRESS, COMPLETE, INVALID }
              record ResourceLocation(int value) {}
              record Key(ResourceLocation location) {}
              record PlayerId(UUID value) {}
              record PlayerRuntimeBudgetAttribution(PlayerId playerId) {}
              static class Pin { boolean closed; boolean isClosed(){return closed;} }
              static class Lease { Pin pin=new Pin(); }
              static class Work { boolean valid=true; boolean qualifies(ServerPlayer a){return valid;} }
              static class Scope { boolean active=true; }
              static class ServerSlot { static class InstanceState {
                boolean terminal,cancellationRequested; Lease lease=new Lease(); Object attribution;
                LogoutState logoutState=LogoutState.ONLINE; Work work=new Work(); Scope logoutScope;
              } }
              static class Level { MinecraftServer server; Key key=new Key(new ResourceLocation(1));
                MinecraftServer getServer(){return server;} Key dimension(){return key;} }
              static class Roster { ServerPlayer current; ServerPlayer getPlayer(UUID id){return current;} }
              static class MinecraftServer { boolean main=true,running=true,stopped; Level level=new Level(); Roster roster=new Roster();
                MinecraftServer(){level.server=this;} boolean isSameThread(){return main;} boolean isRunning(){return running;}
                boolean isStopped(){return stopped;} Level getLevel(Key k){return level.key.equals(k)?level:null;}
                Roster getPlayerList(){return roster;} }
              static class Connection { boolean connecting,connected=true; Object current;
                boolean isConnecting(){return connecting;} boolean isConnected(){return connected;}
                Object getPacketListener(){return current;} }
              static class Listener { ServerPlayer player; Connection c=new Connection(); boolean waiting;
                boolean isAcceptingMessages(){return c.connected&&!waiting;} Connection getConnection(){return c;} }
              static class ServerPlayer { UUID id=UUID.randomUUID(); MinecraftServer server; Level level;
                boolean removed,dead,disconnected,alive=true; float health=20; Listener connection=new Listener();
                ServerPlayer(MinecraftServer s){server=s;level=s.level;connection.player=this;connection.c.current=connection;}
                UUID getUUID(){return id;} MinecraftServer getServer(){return server;} Level serverLevel(){return level;}
                boolean isDeadOrDying(){return dead;} float getHealth(){return health;} boolean isRemoved(){return removed;}
                boolean isAlive(){return alive;} boolean hasDisconnected(){return disconnected;} }
              static void need(boolean b){if(!b)throw new AssertionError();}
              static WorkQualification result(MinecraftServer s,ServerSlot.InstanceState i,ServerPlayer a){
                return actorQualification(s,i,a,s.level.key.location());}
              public static void verify(){
                for(int mask=0;mask<256;mask++){
                  var s=new MinecraftServer();var a=new ServerPlayer(s);s.roster.current=a;
                  var i=new ServerSlot.InstanceState();i.attribution=new PlayerRuntimeBudgetAttribution(new PlayerId(a.id));
                  a.connection.c.connected=false;
                  if((mask&1)!=0)i.work=null;if((mask&2)!=0)a.disconnected=true;
                  if((mask&4)!=0)a.connection.player=null;if((mask&8)!=0)a.connection.c.current=null;
                  if((mask&16)!=0)a.connection.c.connecting=true;if((mask&32)!=0)a.removed=true;
                  if((mask&64)!=0)s.roster.current=new ServerPlayer(s);if((mask&128)!=0)i.lease.pin.closed=true;
                  need(result(s,i,a)==(mask==0?WorkQualification.NATIVE_CLEANUP_PENDING:WorkQualification.INVALID));
                }
                var s=new MinecraftServer();var a=new ServerPlayer(s);s.roster.current=a;
                var i=new ServerSlot.InstanceState();i.attribution=new PlayerRuntimeBudgetAttribution(new PlayerId(a.id));
                need(result(s,i,a)==WorkQualification.EXECUTABLE);a.connection.waiting=true;
                need(result(s,i,a)==WorkQualification.INVALID);a.connection.waiting=false;a.connection.c.connected=false;
                i.work.valid=false;need(result(s,i,a)==WorkQualification.INVALID);i.work.valid=true;
                i.logoutState=LogoutState.IN_PROGRESS;need(result(s,i,a)==WorkQualification.INVALID);
                i.logoutScope=new Scope();need(result(s,i,a)==WorkQualification.LOGOUT_IN_PROGRESS);
                i.logoutState=LogoutState.COMPLETE;s.roster.current=null;a.removed=true;
                need(result(s,i,a)==WorkQualification.EXECUTABLE);i.logoutState=LogoutState.INVALID;
                need(result(s,i,a)==WorkQualification.INVALID);
                need(awaitingNativeCleanup(WorkQualification.NATIVE_CLEANUP_PENDING));
                need(awaitingNativeCleanup(WorkQualification.LOGOUT_IN_PROGRESS));
                need(!awaitingNativeCleanup(WorkQualification.EXECUTABLE));need(!awaitingNativeCleanup(WorkQualification.INVALID));
              }
            """;

    private static final String SCHEDULER_MODEL = """
            import java.util.*;
            public class SchedulerModel {
              /* STAGE_ENUM */
              enum WorkQualification { EXECUTABLE,NATIVE_CLEANUP_PENDING,LOGOUT_IN_PROGRESS,INVALID }
              enum ProjectileClosureReason { DEADLINE_REACHED,OWNER_INVALIDATED }
              enum RuntimeProjectileContinuationOpenRejectionReason { LIFECYCLE_UNAVAILABLE }
              static class RuntimeKernelException { enum Code { EVENT_INDEX_INVARIANT,RESERVATION_ACCOUNTING_INVARIANT,DEFERRED_BUFFER_OVERFLOW } }
              static RuntimeException kernel(RuntimeKernelException.Code c){return new IllegalStateException(c.name());}
              record EventId(long value) {}
              record CastGeometryExecutionDataV0(String dimension) {}
              record ProjectileHitExecutionDataV0() {}
              record RuntimeEvent(EventId eventId,Object executionData,long deadlineRuntimeTick,String skillReference) {}
              record SourceFamily(EventId sourceEventId) {}
              static class MinecraftServer {}
              static class RuntimeProjectileContinuationPermit {
                enum State { RESERVED,CLAIMED_PENDING_DAMAGE,CLOSED_NO_HIT }
                State state=State.RESERVED; boolean suspendedBeforeSpawn;
                String serverSlotToken="T",skillInstanceId="I",budgetAttribution="A",exactReference="R",dimension="D";
                SourceFamily sourceFamily=new SourceFamily(new EventId(1));long deadlineRuntimeTick=100;
                UUID permitId=UUID.randomUUID(),plannedProjectileId=UUID.randomUUID();
              }
              static class Trace { int lastStageOrdinal=-1,stageCount; boolean terminalPublished;
                int[] stageCodes=new int[32]; long[] stageTicks=new long[32];
                /* TRACE_RECORD */
              }
              static class ServerSlot {
                enum State { RUNNING,STOPPING } State state=State.RUNNING;
                String token="T",currentReservationOwner="I";long runtimeTick=10;
                int currentReservationCount=3,reservedPending=4,deferredCount,attempts=1;
                RuntimeEvent currentEvent;RuntimeEvent[] deferred=new RuntimeEvent[8];
                Map<EventId,RuntimeEvent> eventIndex=new HashMap<>();
                Map<UUID,RuntimeProjectileContinuationPermit> activeProjectileContinuations=new HashMap<>();
                static class AttributionState { int reservedPending=4; }
                static class InstanceState {
                  String id="I",attribution="A";boolean inFlight=true;int reservedPending=4;
                  EventId p9SuspendedEventId;RuntimeProjectileContinuationPermit activeProjectileContinuation;
                  Trace p9Diagnostic=new Trace();Object p9ActorWitness(){return this;}
                }
              }
              static class ChildReservation { RuntimeProjectileContinuationPermit permit;
                RuntimeProjectileContinuationPermit detachedPermit(){return permit;}
                void attach(RuntimeProjectileContinuationPermit p){if(permit!=null)throw new AssertionError();permit=p;} }
              interface RuntimeProjectileContinuationOpenResult {
                record Opened(RuntimeProjectileContinuationPermit permit,UUID id,SchedulerModel owner) implements RuntimeProjectileContinuationOpenResult {}
                record Rejected() implements RuntimeProjectileContinuationOpenResult {}
              }
              static RuntimeProjectileContinuationOpenResult continuationRejected(RuntimeProjectileContinuationOpenRejectionReason r){return new RuntimeProjectileContinuationOpenResult.Rejected();}
              boolean entityExists;WorkQualification qualification=WorkQualification.NATIVE_CLEANUP_PENDING;int closes;
              WorkQualification eventQualification(MinecraftServer s,ServerSlot slot,ServerSlot.InstanceState i,RuntimeEvent e){return qualification;}
              static boolean deadlineExpired(ServerSlot slot,RuntimeEvent e){return slot.runtimeTick>e.deadlineRuntimeTick();}
              boolean loadedEntityUuidExists(MinecraftServer s,UUID id){return entityExists;}
              boolean isCurrentP9AuthenticatedActor(MinecraftServer s,ServerSlot.InstanceState i,Object a,String d){return qualification==WorkQualification.EXECUTABLE;}
              static boolean hasP9S4DiagnosticCustody(ServerSlot slot,ServerSlot.InstanceState i,RuntimeEvent e){
                return e.executionData() instanceof ProjectileHitExecutionDataV0 && i.activeProjectileContinuation!=null
                  && i.activeProjectileContinuation.state==RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE;}
              void closeActiveP9ContinuationAndDiscard(MinecraftServer s,ServerSlot slot,ServerSlot.InstanceState i,ProjectileClosureReason r){
                var p=i.activeProjectileContinuation;if(p==null)return;closes++;
                if(p.state==RuntimeProjectileContinuationPermit.State.RESERVED){slot.reservedPending--;i.reservedPending--;}
                p.state=RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT;slot.activeProjectileContinuations.remove(p.permitId);i.activeProjectileContinuation=null;}
              RuntimeProjectileContinuationOpenResult reopen(MinecraftServer server,ServerSlot slot,ServerSlot.InstanceState instance,
                  RuntimeEvent sourceEvent,ChildReservation reservation){
                var geometry=(CastGeometryExecutionDataV0)sourceEvent.executionData();
                /* OPENER */
                throw new AssertionError("not a suspended reopen");
              }
              void finishRejectedReopen(MinecraftServer server,ServerSlot slot,ServerSlot.InstanceState instance){
                /* FAILED_REOPEN */
              }
              static void need(boolean b){if(!b)throw new AssertionError();}
              static void rejected(Runnable f){try{f.run();}catch(IllegalStateException e){return;}throw new AssertionError();}
              public static void verify(){
                for(boolean child:new boolean[]{false,true})for(WorkQualification q:WorkQualification.values()){
                  var m=new SchedulerModel();m.qualification=q;var s=new MinecraftServer();var slot=new ServerSlot();
                  var i=new ServerSlot.InstanceState();var a=new ServerSlot.AttributionState();
                  var e=new RuntimeEvent(new EventId(1),child?new ProjectileHitExecutionDataV0():new CastGeometryExecutionDataV0("D"),100,"R");
                  slot.currentEvent=e;slot.eventIndex.put(e.eventId(),e);var p=new RuntimeProjectileContinuationPermit();
                  if(child){p.state=RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE;slot.reservedPending=3;i.reservedPending=3;a.reservedPending=3;}
                  i.activeProjectileContinuation=p;slot.activeProjectileContinuations.put(p.permitId,p);
                  boolean held=m.suspendUnexecutedCurrent(s,slot,i,a,e);
                  need(held==(q!=WorkQualification.INVALID));need(slot.attempts==1);
                  if(held){need(slot.deferred[0]==e&&slot.eventIndex.get(e.eventId())==e&&!i.inFlight&&slot.currentEvent==null);
                    need(slot.reservedPending==(child?0:1));need(i.reservedPending==(child?0:1));need(a.reservedPending==(child?0:1));
                    need(i.activeProjectileContinuation==p&&p.suspendedBeforeSpawn==!child&&e.deadlineRuntimeTick()==100);
                  }else{need(slot.deferredCount==0&&i.activeProjectileContinuation==null);}
                }
                for(long tick:new long[]{99,100,101}){
                  var m=new SchedulerModel();m.qualification=WorkQualification.EXECUTABLE;var s=new MinecraftServer();var slot=new ServerSlot();slot.runtimeTick=tick;
                  var i=new ServerSlot.InstanceState();var p=new RuntimeProjectileContinuationPermit();p.suspendedBeforeSpawn=true;i.activeProjectileContinuation=p;
                  slot.activeProjectileContinuations.put(p.permitId,p);slot.reservedPending=1;i.reservedPending=1;
                  var e=new RuntimeEvent(new EventId(1),new CastGeometryExecutionDataV0("D"),100,"R");
                  var r=new ChildReservation();var outcome=m.reopen(s,slot,i,e,r);
                  if(tick<100){need(outcome instanceof RuntimeProjectileContinuationOpenResult.Opened&&r.permit==p&&!p.suspendedBeforeSpawn);}
                  else{need(outcome instanceof RuntimeProjectileContinuationOpenResult.Rejected&&r.permit==null);
                    m.finishRejectedReopen(s,slot,i);need(m.closes==1&&i.activeProjectileContinuation==null&&slot.reservedPending==0&&i.reservedPending==0);}
                }
                for(boolean child:new boolean[]{false,true}){
                  var slot=new ServerSlot();var i=new ServerSlot.InstanceState();
                  var e=new RuntimeEvent(new EventId(1),child?new ProjectileHitExecutionDataV0():new CastGeometryExecutionDataV0("D"),100,"R");
                  slot.currentEvent=e;i.p9SuspendedEventId=e.eventId();var p=new RuntimeProjectileContinuationPermit();i.activeProjectileContinuation=p;
                  if(child)p.state=RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE;
                  var stages=child?new P9RuntimeDiagnosticStage[]{P9RuntimeDiagnosticStage.NODE1_MATCHED,P9RuntimeDiagnosticStage.DAMAGE_RESOLVED}
                    :new P9RuntimeDiagnosticStage[]{P9RuntimeDiagnosticStage.NODE0_MATCHED,P9RuntimeDiagnosticStage.CONTINUATION_OPENED};
                  i.p9Diagnostic.record(stages[0],10);int count=i.p9Diagnostic.stageCount;
                  need(resumingRecordedP9Stage(slot,i,e,stages[0]));need(i.p9Diagnostic.stageCount==count);
                  rejected(()->i.p9Diagnostic.record(stages[0],11));
                  i.p9Diagnostic.record(stages[1],11);need(resumingRecordedP9Stage(slot,i,e,stages[0]));
                  if(child)need(resumingRecordedP9Stage(slot,i,e,stages[1]));
                  i.p9SuspendedEventId=new EventId(2);rejected(()->resumingRecordedP9Stage(slot,i,e,stages[0]));
                }
              }
            """;
}
