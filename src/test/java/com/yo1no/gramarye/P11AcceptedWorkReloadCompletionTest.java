package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the actual shared cleanup bodies in a bounded shell; this is not native proof. */
final class P11AcceptedWorkReloadCompletionTest {
    @TempDir Path temporary;

    @Test void synchronousCompletionUsesTheActualSharedCleanupAndOriginalTerminalOwner() throws Exception {
        runModel("cleanup");
    }

    @Test void onlyExactCurrentReloadCancellationCanSkipLateDiagnosticsWithoutSuppressingP8() throws Exception {
        runModel("diagnostics");
    }

    @Test void cleanupFailuresPreserveThePrimaryAndDoNotReopenTheCloseBit() throws Exception {
        runModel("failures");
    }

    @Test void sourceContractKeepsOneRequestBitAndNoGeneralNestedDrainExemption() throws Exception {
        String source = read("SkillRuntimeService.java");
        assertEquals("void requestP9ReloadInvalidation() {\n        p9ReloadCloseRequested.set(true);\n    }",
                method(source, "void requestP9ReloadInvalidation()"));
        assertEquals(1, occurrences(source, "new AtomicBoolean("));
        String ordinary = method(source, "private void invalidateP9Work(");
        ordered(ordinary, "if (slot.dispatching)", "NESTED_DRAIN", "invalidateP9QueuedAndIndexedWork(");
        String complete = method(source, "void completeP9Reload(");
        ordered(complete, "!server.isSameThread()", "ProjectileClosureReason.RELOAD_INVALIDATED, true)",
                "slots.get(server) == slot && slot.state == ServerSlot.State.RUNNING", "p9ReloadCloseRequested.set(false)");
        assertTrue(method(source, "void handleRuntimePost(").contains("ProjectileClosureReason.RELOAD_INVALIDATED, false)"));
        String prepare = method(source, "private static void prepareP9ReloadInFlight(");
        ordered(prepare, "instance.cancellationRequested", "!= (instance.p9InFlightClosureReason", "throw kernel(",
                "instance.cancellationRequested = true", "instance.p9InFlightClosureReason =", "instance.clearP9AuthenticatedActorWitness()",
                "releaseCurrentReservationStatic(");
        assertFalse(prepare.contains("recordP9") || prepare.contains("new "));
        String late = method(source, "private boolean p9ReloadTerminatedAfterWorldCommit()");
        assertFalse(late.contains("p9Diagnostic") || late.contains("p9ReloadCloseRequested"));
        for (String name : new String[] {"private static int clearSlotNormal(", "private static void clearSlotAfterRuntimeException(",
                "static void clearSlotAfterError("}) {
            String cleanup = method(source, name);
            assertTrue(cleanup.substring(cleanup.indexOf('{') + 1).stripLeading().startsWith("clearP9InFlightClosureReason(slot);"));
        }
        ordered(method(source, "private static void terminalizeCurrent("), "instance.p9InFlightClosureReason = null",
                "instance.inFlight = false", "slot.currentEvent = null", "maybeRemoveInstance(");
        String wrapper = method(source, "private void invalidateP9WorkPreservingPrimary(");
        assertEquals(2, occurrences(wrapper, "catch ("));
        ordered(wrapper, "catch (RuntimeException primary)", "throw preserveRuntimeFault(slot, primary)",
                "catch (Error primary)", "throw preserveErrorFault(slot, primary)");
        assertFalse(wrapper.contains("addSuppressed") || wrapper.contains("catch (Throwable"));
        String strict = method(source, "void reportP9S4Stage(");
        assertFalse(strict.contains("p9ReloadTerminatedAfterWorldCommit"));
        String p8 = method(read("P8AppliedFactHandoff.java"), "public void observe(");
        ordered(p8, "reportP9AppliedStage()", "service.offerApplied(event, context, fact)");
    }

    private void runModel(String group) throws Exception {
        String runtime = read("SkillRuntimeService.java");
        String model = MODEL;
        for (String[] part : new String[][] {
                {"COMPLETE", "void completeP9Reload("}, {"INVALIDATE", "private void invalidateP9Work("},
                {"PREPARE", "private static void prepareP9ReloadInFlight("},
                {"SHARED", "private void invalidateP9QueuedAndIndexedWork("},
                {"PRESERVE", "private void invalidateP9WorkPreservingPrimary("},
                {"CLOSE_ALL", "private static int closeAllIndexedContinuations("},
                {"CLOSE", "private RuntimePermitCloseDisposition closeProjectileContinuationOnObservedThread("},
                {"REMOVE", "private static void removeCommittedEvent("},
                {"RELEASE", "private static void releaseCurrentReservationStatic("},
                {"TERMINAL", "private static void terminalizeCurrent("},
                {"MAYBE_REMOVE", "private static void maybeRemoveInstance("},
                {"EMPTY", "private static void removeEmptyInstances("},
                {"ATTRIBUTION", "private static ServerSlot.AttributionState requireAttribution("},
                {"RECORD", "private static void recordP9Terminal("},
                {"CLEAR_REASON", "private static void clearP9InFlightClosureReason("},
                {"RUNTIME_FAULT", "private static void enterFaultAfterRuntimeException("},
                {"ERROR_FAULT", "private static void enterFaultAfterError("},
                {"GUARD_DECISION", "static RuntimeExecutionGuardDecision runtimeExecutionGuardDecision("},
                {"REPORT", "void reportP9AppliedFactIfArmed()"},
                {"FINISH", "void finishP9DamageCommit()"},
                {"LATE", "private boolean p9ReloadTerminatedAfterWorldCommit()"}}) {
            model = model.replace("/* " + part[0] + " */", method(runtime, part[1]));
        }
        model = model.replace("/* P8_OBSERVE */", method(read("P8AppliedFactHandoff.java"), "public void observe("));
        Path file = temporary.resolve("ReloadCompletionModel.java");
        Files.writeString(file, model);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-proc:none", "--release", "21", "-d", temporary.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {temporary.toUri().toURL()}, null)) {
            loader.loadClass("ReloadCompletionModel").getMethod("verify", String.class).invoke(null, group);
        }
    }

    private static String read(String name) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        return Files.readString(root.resolve("src/main/java/com/yo1no/gramarye/" + name));
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
        for (String token : tokens) { int next = source.indexOf(token, cursor); assertTrue(next >= cursor, token); cursor = next + token.length(); }
    }
    private static int occurrences(String source, String token) { return source.split(java.util.regex.Pattern.quote(token), -1).length - 1; }

    private static final String MODEL = """
        import java.util.*;
        import java.util.concurrent.atomic.AtomicBoolean;
        public final class ReloadCompletionModel {
          enum ProjectileClosureReason { RELOAD_INVALIDATED, OWNER_INVALIDATED, SERVER_STOPPED, SPAWN_NOT_APPLIED }
          enum P9RuntimeCleanupDisposition { RELEASED }
          enum RuntimePermitCloseDisposition { CLOSED, ALREADY_CLOSED, REJECTED }
          enum RuntimePermitTransferDisposition { REJECTED }
          enum RuntimeExecutionGuardDecision { ALLOWED, CANCELLED, DEADLINE_EXCEEDED }
          enum P9RuntimeDiagnosticStage { CAST_PRESENTATION_OFFERED, DAMAGE_COMMIT_RESULT, HIT_PRESENTATION_OFFERED }
          static final class RuntimeKernelException extends RuntimeException {
            enum Code { NESTED_DRAIN, WRONG_THREAD_LIFECYCLE, BREAKER_SCRATCH_OVERFLOW, RESERVATION_ACCOUNTING_INVARIANT, EVENT_INDEX_INVARIANT, LEASE_ACCOUNTING_INVARIANT }
            final Code code; RuntimeKernelException(Code c){code=c;}
          }
          static RuntimeKernelException kernel(RuntimeKernelException.Code c){return new RuntimeKernelException(c);}
          static final class MinecraftServer {boolean main=true,running=true,stopped;boolean isSameThread(){return main;}boolean isRunning(){return running;}boolean isStopped(){return stopped;}}
          record SkillInstanceId(int value){} record EventId(int value){} record RuntimeServerToken(int value){} record RuntimeBudgetAttribution(int value){}
          record CastGeometryExecutionDataV0(){} record ProjectileHitExecutionDataV0(Object dimension,UUID permitId){}
          record RuntimeEvent(EventId eventId,SkillInstanceId skillInstanceId,Object skillReference,RuntimeBudgetAttribution budgetAttribution,Object executionData,int nodeIndex){}
          static boolean isP9ExecutionData(Object d){return d instanceof CastGeometryExecutionDataV0||d instanceof ProjectileHitExecutionDataV0;}
          static final class Pin {boolean closed;boolean isClosed(){return closed;}}
          static final class Lease {Object reference;Pin pin=new Pin();int releases;boolean release(){check(!pin.closed,"lease once");releases++;pin.closed=true;return true;}}
          static final class ServerSlot {
            enum State { RUNNING, STOPPING, FAULTED, REMOVED }
            State state=State.RUNNING;boolean dispatching=true,p9BatchContinuationCloseInProgress,p9ActiveIndexInvalidatedAfterError;
            RuntimeServerToken token=new RuntimeServerToken(1);RuntimeEvent currentEvent;SkillInstanceId currentReservationOwner;
            int currentReservationCount,committedPending,reservedPending,deferredCount;RuntimeEvent[] deferred=new RuntimeEvent[8],cleanupScratch=new RuntimeEvent[8];
            Queue<RuntimeEvent> queue=new ArrayDeque<>();Map<EventId,RuntimeEvent> eventIndex=new HashMap<>();
            Map<SkillInstanceId,InstanceState> instances=new LinkedHashMap<>();Map<UUID,RuntimeProjectileContinuationPermit> activeProjectileContinuations=new LinkedHashMap<>();
            Map<RuntimeBudgetAttribution,AttributionState> attributions=new HashMap<>();Map<Object,Lease> leases=new HashMap<>();
            ErrorCleanup p9ErrorCleanup=new ErrorCleanup();Throwable secondary;int faultCleanups;
            static final class AttributionState {int activeInstances,committedPending,reservedPending;}
            static final class InstanceState {
              SkillInstanceId id;RuntimeBudgetAttribution attribution;Lease lease=new Lease();boolean inFlight,terminal,cancellationRequested;
              int committedPending,reservedPending,workReleases,terminalObservations;Object witness=new Object(),p9SuspendedEventId;
              RuntimeProjectileContinuationPermit activeProjectileContinuation;ProjectileClosureReason p9InFlightClosureReason;Throwable diagnosticFailure;
              void clearP9AuthenticatedActorWitness(){witness=null;}void releaseWork(){check(workReleases==0,"work once");workReleases++;}
            }
          }
          static final class ErrorCleanup {int prepares;void prepare(MinecraftServer s){prepares++;}}
          static final class Projectile {boolean removed;int discards,clears;Throwable discardFailure;Runnable callback;
            void clearObservedHit(RuntimeProjectileContinuationPermit p){clears++;}boolean isRemoved(){return removed;}
            void discard(){discards++;if(callback!=null)callback.run();raise(discardFailure);removed=true;}
          }
          static final class RuntimeProjectileContinuationPermit {
            enum Mode {REAL} enum State {RESERVED,OPEN,CLAIMED_PENDING_DAMAGE,CLOSED_NO_HIT,CLOSED_AFTER_HIT}
            Mode mode=Mode.REAL;State state;RuntimeServerToken serverSlotToken;SkillInstanceId skillInstanceId;RuntimeBudgetAttribution budgetAttribution;
            UUID permitId=UUID.randomUUID();EventId heldChildEventId;ReloadCompletionModel owner;Projectile projectile=new Projectile();int closes;
            RuntimePermitCloseDisposition closeWithoutHit(MinecraftServer s,ProjectileClosureReason reason){closes++;return owner.closeProjectileContinuationOnObservedThread(s.isSameThread(),s,this,serverSlotToken,skillInstanceId,budgetAttribution,permitId,reason);}
          }
          final AtomicBoolean p9ReloadCloseRequested=new AtomicBoolean(true);
          final Map<MinecraftServer,ServerSlot> slots=new IdentityHashMap<>();final MinecraftServer server=new MinecraftServer();final ServerSlot slot=new ServerSlot();
          final RuntimeBudgetAttribution attr=new RuntimeBudgetAttribution(1);final ServerSlot.AttributionState account=new ServerSlot.AttributionState();
          ServerSlot.InstanceState current;RuntimeProjectileContinuationPermit currentPermit;int next=1;
          ReloadCompletionModel(int mode){
            slots.put(server,slot);slot.attributions.put(attr,account);
            current=instance();Object data=mode==2?new ProjectileHitExecutionDataV0("dimension",UUID.randomUUID()):new CastGeometryExecutionDataV0();
            RuntimeEvent event=event(current,data);slot.currentEvent=event;current.inFlight=true;
            reserve(current,2);slot.currentReservationCount=2;slot.currentReservationOwner=current.id;
            if(mode!=0){currentPermit=permit(current,mode==1?RuntimeProjectileContinuationPermit.State.RESERVED:RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE);currentPermit.heldChildEventId=event.eventId();
              if(mode==2){RuntimeEvent replacement=new RuntimeEvent(event.eventId(),current.id,current.lease.reference,attr,new ProjectileHitExecutionDataV0("dimension",currentPermit.permitId),1);slot.eventIndex.put(event.eventId(),replacement);slot.currentEvent=replacement;current.witness=null;}}
          }
          ServerSlot.InstanceState instance(){var i=new ServerSlot.InstanceState();i.id=new SkillInstanceId(next++);i.attribution=attr;i.lease.reference="ref-"+i.id.value();slot.instances.put(i.id,i);slot.leases.put(i.lease.reference,i.lease);account.activeInstances++;return i;}
          RuntimeEvent event(ServerSlot.InstanceState i,Object data){var e=new RuntimeEvent(new EventId(next++),i.id,i.lease.reference,attr,data,data instanceof ProjectileHitExecutionDataV0?1:0);slot.eventIndex.put(e.eventId(),e);i.committedPending++;account.committedPending++;slot.committedPending++;return e;}
          void reserve(ServerSlot.InstanceState i,int n){i.reservedPending+=n;account.reservedPending+=n;slot.reservedPending+=n;}
          RuntimeProjectileContinuationPermit permit(ServerSlot.InstanceState i,RuntimeProjectileContinuationPermit.State state){var p=new RuntimeProjectileContinuationPermit();p.owner=this;p.state=state;p.serverSlotToken=slot.token;p.skillInstanceId=i.id;p.budgetAttribution=attr;i.activeProjectileContinuation=p;slot.activeProjectileContinuations.put(p.permitId,p);if(state!=RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE)reserve(i,1);return p;}
          void extras(){var open=instance();permit(open,RuntimeProjectileContinuationPermit.State.OPEN);var queued=instance();slot.queue.add(event(queued,new CastGeometryExecutionDataV0()));var deferred=instance();slot.deferred[slot.deferredCount++]=event(deferred,new CastGeometryExecutionDataV0());var other=instance();slot.queue.add(event(other,"non-P9"));slot.deferred[slot.deferredCount++]=event(other,"non-P9");}
          static Projectile loadedProjectile(MinecraftServer s,RuntimeProjectileContinuationPermit p){return p.projectile;}
          static boolean removeExactQueuedOrDeferred(ServerSlot s,RuntimeEvent e){if(s.queue.remove(e))return true;for(int n=0;n<s.deferredCount;n++)if(s.deferred[n]==e){System.arraycopy(s.deferred,n+1,s.deferred,n,s.deferredCount-n-1);s.deferred[--s.deferredCount]=null;return true;}return false;}
          static void recordP9SpawnResult(Object... ignored){}
          static void materializeP9Terminal(ServerSlot s,ServerSlot.InstanceState i,ProjectileClosureReason r,P9RuntimeCleanupDisposition d){i.terminalObservations++;raise(i.diagnosticFailure);}
          RuntimeException preserveRuntimeFault(ServerSlot s,RuntimeException primary){enterFaultAfterRuntimeException(server,s);return primary;}
          Error preserveErrorFault(ServerSlot s,Error primary){enterFaultAfterError(s);return primary;}
          // Fault-cleanup leaves are intentionally injectable; the actual preservation wrappers are extracted.
          static void clearSlotAfterRuntimeException(MinecraftServer server,ServerSlot s){clearP9InFlightClosureReason(s);s.faultCleanups++;raise(s.secondary);}
          static void clearSlotAfterError(ServerSlot s){clearP9InFlightClosureReason(s);s.faultCleanups++;raise(s.secondary);}
          /* COMPLETE */
          /* INVALIDATE */
          /* PREPARE */
          /* SHARED */
          /* PRESERVE */
          /* CLOSE_ALL */
          /* CLOSE */
          /* REMOVE */
          /* RELEASE */
          /* TERMINAL */
          /* MAYBE_REMOVE */
          /* EMPTY */
          /* ATTRIBUTION */
          /* RECORD */
          /* CLEAR_REASON */
          /* RUNTIME_FAULT */
          /* ERROR_FAULT */
          static boolean deadlineExpired(ServerSlot slot,RuntimeEvent event){return false;}
          /* GUARD_DECISION */
          final class Guard {
            ReloadCompletionModel owner=ReloadCompletionModel.this;MinecraftServer server=owner.server;ServerSlot slot=owner.slot;
            ServerSlot.InstanceState instance=current;RuntimeEvent event=slot.currentEvent;Object p9Actor=new Object(),p9Dimension="dimension";
            boolean p9WorldCommitEntered=true,p9DamageCommitEntered=true,p9DamageCommitFinished,p9AppliedObservationArmed=true;int stages;
            void reportP9S4Stage(P9RuntimeDiagnosticStage stage){stages++;if(instance.activeProjectileContinuation==null)throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);}
            /* REPORT */
            /* FINISH */
            /* LATE */
          }
          static final class P6RuntimeExecutionBridge {record AppliedFact(){}}
          static final class Presentation {int offers,failures;void offerApplied(Object e,Object c,Object f){offers++;}void recordObserverRuntimeException(){failures++;}}
          final class Handoff {Guard guard;Presentation service=new Presentation();Object event=new Object(),context=new Object();Handoff(Guard g){guard=g;}void reportP9AppliedStage(){guard.reportP9AppliedFactIfArmed();}/* P8_OBSERVE */}
          static int assertions;
          static void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
          static void raise(Throwable value){if(value instanceof RuntimeException r)throw r;if(value instanceof Error e)throw e;}
          static void rejects(Runnable operation,RuntimeKernelException.Code code){try{operation.run();throw new AssertionError("expected "+code);}catch(RuntimeKernelException expected){check(expected.code==code,"exact rejection "+code);}}
          static void cleanup(){
            for(int mode=0;mode<3;mode++){
              var m=new ReloadCompletionModel(mode);m.extras();var original=m.slot.currentEvent;var lease=m.current.lease;
              for(var p:m.slot.activeProjectileContinuations.values())p.projectile.callback=()->check(m.current.cancellationRequested&&m.current.witness==null&&m.current.p9InFlightClosureReason==ProjectileClosureReason.RELOAD_INVALIDATED,"cancel/witness before original discard");
              m.completeP9Reload(m.server);
              check(!m.p9ReloadCloseRequested.get()&&m.slot.state==ServerSlot.State.RUNNING,"success reopens flag");
              check(m.current.witness==null&&m.current.cancellationRequested&&m.current.p9InFlightClosureReason==ProjectileClosureReason.RELOAD_INVALIDATED,"root/reserved/claimed witness sync");
              check(runtimeExecutionGuardDecision(m.slot,m.current,original)==RuntimeExecutionGuardDecision.CANCELLED,"original current guard remains cancelled after flag reset");
              check(m.slot.currentEvent==original&&m.slot.eventIndex.get(original.eventId())==original&&m.current.inFlight,"original current indexed custody");
              check(!lease.pin.closed&&m.current.workReleases==0&&m.slot.reservedPending==0&&m.account.reservedPending==0&&m.slot.currentReservationCount==0,"lease/W retained remainder released");
              check(m.slot.activeProjectileContinuations.isEmpty()&&m.current.activeProjectileContinuation==null&&!m.slot.p9BatchContinuationCloseInProgress,"permits synchronously closed");
              check(m.slot.queue.size()==1&&m.slot.deferredCount==1&&m.slot.committedPending==3&&m.account.committedPending==3&&m.slot.instances.size()==2,"non-P9 queue/deferred intact");
              if(mode!=0)check(m.currentPermit.closes==1&&m.currentPermit.projectile.discards==1&&m.currentPermit.state==(mode==1?RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT:RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT),"native close/discard once");
              prepareP9ReloadInFlight(m.slot);check(m.current.workReleases==0&&m.slot.reservedPending==0,"repeated prepare pair only is idempotent");
              terminalizeCurrent(m.slot,m.current,original);
              check(m.slot.currentEvent==null&&!m.current.inFlight&&m.current.p9InFlightClosureReason==null&&lease.pin.closed&&lease.releases==1&&m.current.workReleases==1,"original terminal once");
              check(m.slot.committedPending==2&&m.slot.instances.size()==1&&m.account.activeInstances==1,"other work remains");
              rejects(()->terminalizeCurrent(m.slot,m.current,original),RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
            }
            for(int n=0;n<15;n++){var m=new ReloadCompletionModel(0);switch(n){case 0->m.slot.dispatching=false;case 1->m.slot.state=ServerSlot.State.STOPPING;case 2->m.slot.p9BatchContinuationCloseInProgress=true;case 3->m.slot.currentEvent=null;case 4->m.slot.eventIndex.clear();case 5->m.slot.instances.clear();case 6->m.current.inFlight=false;case 7->m.current.terminal=true;case 8->m.current.lease.pin.closed=true;case 9->m.current.lease.reference="wrong";case 10->m.current.attribution=new RuntimeBudgetAttribution(99);case 11->m.current.cancellationRequested=true;case 12->m.current.p9InFlightClosureReason=ProjectileClosureReason.OWNER_INVALIDATED;case 13->m.current.p9InFlightClosureReason=ProjectileClosureReason.RELOAD_INVALIDATED;case 14->{var e=m.slot.currentEvent;var bad=new RuntimeEvent(e.eventId(),e.skillInstanceId(),e.skillReference(),e.budgetAttribution(),"non-P9",0);m.slot.currentEvent=bad;m.slot.eventIndex.put(bad.eventId(),bad);}}
              Object witness=m.current.witness;int reserved=m.slot.reservedPending;rejects(()->prepareP9ReloadInFlight(m.slot),RuntimeKernelException.Code.NESTED_DRAIN);check(m.current.witness==witness&&m.slot.reservedPending==reserved&&m.current.workReleases==0,"invalid prepare has no mutation "+n);}
            var ordinary=new ReloadCompletionModel(2);rejects(()->ordinary.invalidateP9Work(ordinary.server,ordinary.slot,ProjectileClosureReason.RELOAD_INVALIDATED),RuntimeKernelException.Code.NESTED_DRAIN);check(!ordinary.current.cancellationRequested,"ordinary nested still forbidden");
            var wrongThread=new ReloadCompletionModel(2);wrongThread.server.main=false;rejects(()->wrongThread.completeP9Reload(wrongThread.server),RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);check(!wrongThread.current.cancellationRequested,"wrong thread no mutation");
            var idle=new ReloadCompletionModel(0);terminalizeCurrent(idle.slot,idle.current,idle.slot.currentEvent);idle.slot.dispatching=false;idle.extras();idle.completeP9Reload(idle.server);check(!idle.p9ReloadCloseRequested.get()&&idle.slot.committedPending==2&&idle.slot.reservedPending==0,"original idle completion");
            var duplicate=new ReloadCompletionModel(2);duplicate.completeP9Reload(duplicate.server);rejects(()->duplicate.completeP9Reload(duplicate.server),RuntimeKernelException.Code.NESTED_DRAIN);check(duplicate.slot.state==ServerSlot.State.FAULTED,"duplicate public complete without new request not promised");
            var again=new ReloadCompletionModel(2);again.completeP9Reload(again.server);again.p9ReloadCloseRequested.set(true);again.completeP9Reload(again.server);check(again.currentPermit.closes==1&&again.currentPermit.projectile.discards==1&&again.current.workReleases==0,"new close request coalesces exact existing reload pair");
          }
          static void diagnostics(){
            for(boolean applied:new boolean[]{false,true}){var m=new ReloadCompletionModel(2);var g=m.new Guard();m.current.diagnosticFailure=new AssertionError("owned optional diagnostic");m.completeP9Reload(m.server);var h=m.new Handoff(g);if(applied)h.observe(new P6RuntimeExecutionBridge.AppliedFact());g.finishP9DamageCommit();check(g.p9DamageCommitFinished&&g.stages==0&&h.service.offers==(applied?1:0),"actual result preserved/P8 actual fact only");check(m.current.p9InFlightClosureReason==ProjectileClosureReason.RELOAD_INVALIDATED&&m.current.terminalObservations==1,"optional terminal fault no authority");}
            for(int n=0;n<23;n++){var m=new ReloadCompletionModel(2);var g=m.new Guard();m.completeP9Reload(m.server);switch(n){case 0->g.p9WorldCommitEntered=false;case 1->g.p9DamageCommitEntered=false;case 2->g.p9Actor=null;case 3->m.server.main=false;case 4->m.server.running=false;case 5->m.server.stopped=true;case 6->m.slots.clear();case 7->m.slot.state=ServerSlot.State.STOPPING;case 8->m.slot.dispatching=false;case 9->m.slot.currentEvent=null;case 10->m.slot.eventIndex.clear();case 11->m.slot.instances.clear();case 12->m.current.inFlight=false;case 13->m.current.terminal=true;case 14->m.current.cancellationRequested=false;case 15->m.current.p9InFlightClosureReason=null;case 16->m.current.p9InFlightClosureReason=ProjectileClosureReason.OWNER_INVALIDATED;case 17->m.current.lease.pin.closed=true;case 18->m.current.lease.reference="wrong";case 19->m.current.attribution=new RuntimeBudgetAttribution(99);case 20->g.p9Dimension="wrong";case 21->m.current.activeProjectileContinuation=m.currentPermit;case 22->m.slot.activeProjectileContinuations.put(m.currentPermit.permitId,m.currentPermit);}
              check(!g.p9ReloadTerminatedAfterWorldCommit(),"strict late predicate "+n);var h=m.new Handoff(g);if(n!=21){rejects(()->h.observe(new P6RuntimeExecutionBridge.AppliedFact()),RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);check(h.service.offers==0,"no diagnostic bypass on invalid owner "+n);}}
            var root=new ReloadCompletionModel(0);var rg=root.new Guard();root.completeP9Reload(root.server);check(!rg.p9ReloadTerminatedAfterWorldCommit(),"root never hit carveout");rejects(()->rg.reportP9AppliedFactIfArmed(),RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            var early=new ReloadCompletionModel(2);var eg=early.new Guard();eg.p9DamageCommitEntered=false;eg.p9WorldCommitEntered=false;early.completeP9Reload(early.server);rejects(()->eg.reportP9AppliedFactIfArmed(),RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
          }
          static void failures(){
            for(boolean raw:new boolean[]{false,true}){var m=new ReloadCompletionModel(2);Throwable primary=raw?new AssertionError("owned primary"):new IllegalStateException("owned primary");m.currentPermit.projectile.discardFailure=primary;m.slot.secondary=raw?new IllegalStateException("owned secondary"):new AssertionError("owned secondary");try{m.completeP9Reload(m.server);throw new AssertionError("missing primary");}catch(RuntimeException|Error actual){check(actual==primary,"primary identity");}check(m.p9ReloadCloseRequested.get()&&m.slot.state==ServerSlot.State.FAULTED&&m.slot.faultCleanups==1&&m.current.p9InFlightClosureReason==null,"failed cleanup cannot reopen / marker cleared before secondary");check(m.slot.p9ErrorCleanup.prepares==(raw?1:0)&&!m.slot.p9BatchContinuationCloseInProgress&&m.currentPermit.closes==1,"actual wrapper/discard finally");}
            var reentry=new ReloadCompletionModel(2);reentry.currentPermit.projectile.callback=()->{check(reentry.p9ReloadCloseRequested.get()&&reentry.current.cancellationRequested,"reentrant admission stays closed");rejects(()->reentry.invalidateP9Work(reentry.server,reentry.slot,ProjectileClosureReason.RELOAD_INVALIDATED),RuntimeKernelException.Code.NESTED_DRAIN);};reentry.completeP9Reload(reentry.server);check(reentry.currentPermit.projectile.discards==1,"callback no duplicate close");
            var stop=new ReloadCompletionModel(2);stop.currentPermit.projectile.callback=()->stop.slot.state=ServerSlot.State.STOPPING;stop.completeP9Reload(stop.server);check(stop.p9ReloadCloseRequested.get(),"callback stop cannot reopen admission");
          }
          public static void verify(String group){switch(group){case "cleanup"->cleanup();case "diagnostics"->diagnostics();case "failures"->failures();default->throw new AssertionError(group);}System.out.println(group+" actual-body assertions="+assertions);}
        }
        """;
}
