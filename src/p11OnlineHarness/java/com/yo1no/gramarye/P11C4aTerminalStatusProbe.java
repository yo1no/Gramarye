package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Two finite, excluded real-pipeline STATUS experiments. No state, permit, fence or root is written. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aTerminalStatusProbe {
    private static volatile Run active;
    private static final ThreadLocal<Ingress> INGRESS = new ThreadLocal<>();
    private P11C4aTerminalStatusProbe() { }
    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.HANDOFF_STATUS
            || P11C4aScenario.MODE == P11C4aScenario.Mode.UNPAIRED_COMPLETED; }
    static boolean handoffMode() { return P11C4aScenario.MODE == P11C4aScenario.Mode.HANDOFF_STATUS; }
    static boolean started() { return active != null; }
    static boolean allowsLogin(Connection connection, String role, int logins) {
        var r=active; return r!=null && r.handoff && r.parkingStarted && !r.qualified
                && connection==r.connection && role.equals("a") && logins==1;
    }
    static void start(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        exact(server,actor); exact(server,peer);
        require(selected() && active==null && actor!=peer && !actor.getUUID().equals(peer.getUUID()),"TERMINAL_STATUS_START");
        active=new Run(server,actor,peer,output);
        P11C4aEvidence.cue(output,"a-terminal-status-arm.ready");
        P11C4aEvidence.cue(output,"b-terminal-status-arm.ready");
    }
    static boolean tick(ServerPlayer current) throws IOException {
        var r=active;
        require(r!=null && r.server.isSameThread() && ++r.ticks<=2400 && r.failure==null,"TERMINAL_STATUS_OWNER_OR_FAILURE");
        if(r.logouts==2) { return true; }
        require(r.peer.connection.getConnection()==r.peerConnection && r.peerConnection.isConnected()
                && r.server.getPlayerList().getPlayer(r.peer.getUUID())==r.peer && r.peer.isAlive()
                && P11C4aNativeObservations.tries(r.peerConnection)==r.peerTries,"TERMINAL_STATUS_PEER_CHANGED");
        if(r.qualified) { return false; }
        if(!P11C4aEvidence.receiptPresent(r.client,"terminal-status-armed.json")
                || !P11C4aEvidence.receiptPresent(r.output.resolveSibling("client-b"),"terminal-status-armed.json")) { return false; }
        if(!r.startedNative) {
            r.startedNative=true;
            if(r.handoff) { r.parkingStarted=true; P11C4aParkingProbe.start(r.server,r.initial,"a",r.output); }
            else {
                r.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,r.server);
                r.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false,r.server);
                r.initial.kill();
            }
            return false;
        }
        if(r.handoff && !r.parkingDone) { r.parkingDone=P11C4aParkingProbe.tick(current,r.client); }
        if(!r.reloadReturned || r.handoff && !r.parkingDone
                || !P11C4aEvidence.receiptPresent(r.client,"terminal-status-client.json")) { return false; }
        exact(r.server,current);
        require(current!=r.initial && current.getUUID().equals(r.initial.getUUID())
                && current.connection.getConnection()==r.connection && current.getAdvancements()==r.canonical
                && current.getStats()==r.statistics && source(current).source.epoch()==r.epoch+1
                && r.request!=null && r.tries==1 && r.same>=1 && (r.handoff ? r.different==0 && r.completeCalls==1 && r.reservation==null : r.different==1)
                && !r.control.executableHeld() && r.control.state().orElseThrow().outcome()==Outcome.COMPLETED,
                "TERMINAL_STATUS_NATIVE_TERMINAL");
        require(r.server.saveEverything(true,false,false),"TERMINAL_STATUS_ORIGINAL_SAVE_FAILED");
        r.finalActor=current; r.qualified=true; restore(r);
        P11C4aEvidence.write(r.output,"terminal-status-subset.json",Map.of("status","NATIVE_STATUS_SUBSET_REQUIRES_SEPARATE_CLIENT_AND_SERVER_RECEIPTS",
                "handoff",r.handoff,"nativeSaveReturned",true,"sourceEpochBefore",r.epoch,"sourceEpochAfter",source(current).source.epoch(),
                "peerUnchanged",true,"fullC4aAcceptance",false));
        P11C4aEvidence.cue(r.output,"a-terminal-status-leave.ready");
        return false;
    }
    static void logout(ServerPlayer actor) throws IOException {
        var r=active;
        require(r!=null && r.server.isSameThread(),"TERMINAL_STATUS_LOGOUT_OWNER");
        if(r.handoff && !r.qualified && actor==r.initial && r.parkingStarted && !r.configurationLogout) {
            r.configurationLogout=true; return;
        }
        require(r.qualified && actor==(r.logouts==0?r.finalActor:r.peer) && ++r.logouts<=2,"TERMINAL_STATUS_UNEXPECTED_LOGOUT");
        if(r.logouts==1) { P11C4aEvidence.cue(r.output,"b-terminal-status-leave.ready"); }
    }
    static void stopped(MinecraftServer server) throws IOException {
        var r=active; if(r==null || r.server!=server) { return; }
        P11C4aEvidence.write(r.output,"terminal-status-stopped.json",Map.of("originalServerStopped",true,
                "qualifiedSubset",r.qualified,"finalNativeLogouts",r.logouts,"configurationLogout",r.configurationLogout,
                "failure",r.failure==null?"NONE":r.failure)); active=null;
    }
    static void abort() {
        var r=active; if(r==null) { return; }
        fail(r,"TERMINAL_STATUS_ABORT"); r.gate.completeExceptionally(new IllegalStateException(r.failure));
        if(r.server.isSameThread()) { restore(r); }
    }

    public static Object beginIngress(Object value, Connection connection, ICommonPacketListener listener) {
        var r=active;
        if(r==null || !r.startedNative || r.qualified || connection!=r.connection || !(value instanceof Request request)) { return null; }
        // ENTER_CONFIG and task n0 are not the selected positive-n parked Retry.
        if(request.kind()!=(r.handoff?Kind.RETURN_TO_WORLD:Kind.DEATH) || request.requestSeq()==0) { return null; }
        var scope=new Ingress(r,request,INGRESS.get()); INGRESS.set(scope);
        if(connection.getPacketListener()!=listener || listener.getConnection()!=connection
                || request.scope()!=(r.handoff?Scope.PREPLAY:Scope.PLAY)) { fail(r,"TERMINAL_STATUS_WRONG_ROUTE"); }
        if(request.command()==Command.TRY) {
            if(++r.tries!=1 || request.requestSeq()==Long.MAX_VALUE) { fail(r,"TERMINAL_STATUS_EXTRA_TRY"); }
            else { r.request=request; }
        }
        return scope;
    }
    public static Object beforeOffer(Object owner,Object request) {
        var s=INGRESS.get(); if(s==null || s.request!=request || !(owner instanceof P11TransitionControl control)) { return null; }
        if(s.run.control==null) { s.run.control=control; }
        if(s.run.control!=control) { fail(s.run,"TERMINAL_STATUS_CONTROL_CHANGED"); }
        return s;
    }
    public static void afterOffer(Object value,Object result) {
        if(!(value instanceof Ingress s)) { return; }
        var r=s.run;
        try {
            if(s.request.command()==Command.TRY) { require(result==P11TransitionControl.Offer.RETAINED,"TERMINAL_STATUS_TRY_NOT_RETAINED"); return; }
            if(!r.waiting || r.released) { return; }
            unchanged(r);
            require(same(r.request,s.request) && !s.offered,"TERMINAL_STATUS_WRONG_STATUS"); s.offered=true;
            if(s.request.requestSeq()==r.request.requestSeq()) {
                require(result==(r.handoff?P11TransitionControl.Offer.STALE:P11TransitionControl.Offer.COALESCED)
                        && ++r.same<=40,"TERMINAL_STATUS_SAME_RESULT");
            } else {
                require(!r.handoff && s.request.requestSeq()==r.request.requestSeq()+1
                        && result==P11TransitionControl.Offer.BUSY && ++r.different==1,"TERMINAL_STATUS_DIFFERENT_RESULT");
            }
        } catch(RuntimeException|Error failure) { fail(r,"TERMINAL_STATUS_OFFER_OBSERVER"); }
    }
    public static void endIngress(Object value,Throwable primary) {
        if(!(value instanceof Ingress s)) { return; }
        var r=s.run;
        try {
            if(s.previous==null) { INGRESS.remove(); } else { INGRESS.set(s.previous); }
            require(primary==null,"TERMINAL_STATUS_ORIGINAL_INGRESS_THROW");
            if(!r.waiting || r.released) { return; }
            require(s.offered && r.failure==null && !Thread.holdsLock(r.control),"TERMINAL_STATUS_INGRESS_OWNER");
            unchanged(r);
            if(r.same>=1 && (r.handoff || r.different==1)) {
                r.released=true; require(r.gate.complete(null),"TERMINAL_STATUS_GATE_ALREADY_TERMINAL");
            }
        } catch(RuntimeException|Error secondary) { fail(r,"TERMINAL_STATUS_INGRESS_OBSERVER"); r.gate.completeExceptionally(secondary); }
    }

    /** Original finish's unique PREPLAY→PLAY reservation returns before the finite native reload. */
    public static Optional<?> handoff(Object owner,Object listener,Object target,Operation<Optional<?>> original) {
        Optional<?> result=original.call(owner,listener,target);
        var r=active;
        if(r==null || !r.handoff || r.control!=owner || r.windowStarted) { return result; }
        require(r.server.isSameThread() && !Thread.holdsLock(owner) && target==Scope.PLAY
                && listener instanceof P11TransitionControl.Listener old && old.scope()==Scope.PREPLAY
                && result.isPresent() && r.request!=null && r.request.requestSeq()>0,"TERMINAL_STATUS_NOT_REAL_HANDOFF");
        r.reservation=result.get(); r.handoffCalls++;
        reload(r);
        return result; // Exact native Optional and reservation, never a replacement.
    }
    public static Optional<?> complete(Object owner,Object reservation,Operation<Optional<?>> original) {
        var r=active;
        boolean selected=r!=null && r.handoff && r.control==owner && r.reservation!=null;
        if(selected) { require(r.reservation==reservation && r.reloadReturned && r.released && ++r.completeCalls==1,"TERMINAL_STATUS_RESERVATION_CHANGED"); }
        Optional<?> result=original.call(owner,reservation);
        if(selected) {
            require(result.isPresent() && result.get() instanceof P11TransitionControl.Listener listener
                    && listener.scope()==Scope.PLAY,"TERMINAL_STATUS_ORIGINAL_HANDOFF_NOT_COMPLETE");
            r.reservation=null;
        }
        return result;
    }
    public static Object finishing(Object service) {
        var r=active;
        if(r==null || r.request==null || r.control==null || !r.server.isSameThread()) { return null; }
        require(service instanceof P11LiveTransitionService && (r.service==null || r.service==service),"TERMINAL_STATUS_SERVICE_CHANGED");
        r.service=(P11LiveTransitionService) service; return r;
    }
    public static void finishReturned(Object token,Throwable primary) {
        if(!(token instanceof Run r) || !r.handoff || !r.windowStarted || r.finishObserved) { return; }
        r.finishObserved=true;
        if(primary!=null || r.reservation!=null || r.completeCalls!=1) { fail(r,"TERMINAL_STATUS_ORIGINAL_FINISH_FAILED"); }
        r.reservation=null; // A real reservation is never retained beyond its original finish call.
    }
    /** The actual COMPLETED transport call returned; no in-execute wait for a not-yet-sent state. */
    public static void submitted(ServerCommonPacketListenerImpl listener,Packet<?> packet) {
        var r=active;
        if(r==null || r.handoff || r.windowStarted || r.request==null || listener.getConnection()!=r.connection
                || !(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
        var state=payload.state();
        if(state.outcome()!=Outcome.COMPLETED || state.kind()!=Kind.DEATH) { return; }
        require(r.control!=null && state.equals(r.control.state().orElse(null)) && matches(r.request,state),"TERMINAL_STATUS_COMPLETED_NOT_EXACT");
        r.completedSendReturns++; reload(r);
    }
    private static void reload(Run r) {
        require(!r.windowStarted && r.server.isSameThread() && !Thread.holdsLock(r.control),"TERMINAL_STATUS_RELOAD_OWNER");
        r.terminal=r.control.state().orElseThrow(); r.fence=r.control.fence(); r.held=r.control.executableHeld();
        r.current=r.server.getPlayerList().getPlayer(r.initial.getUUID()); exact(r.server,r.current);
        r.body=source(r.current); r.source=P11NativeStorageBoundary.nativeSourceOwner(r.current);
        require(r.service!=null && r.terminal.outcome()==Outcome.COMPLETED && matches(r.request,r.terminal)
                && (r.handoff ? r.control.listener()==null && r.held && roots(r)>0
                              : r.control.listener()!=null && !r.held && roots(r)==0 && r.completedSendReturns==1),"TERMINAL_STATUS_WRONG_TERMINAL_WINDOW");
        r.windowStarted=true; r.reloadActive=true; r.gate.orTimeout(30,TimeUnit.SECONDS);
        try {
            r.server.reloadResources(List.copyOf(r.server.getPackRepository().getSelectedIds())).join();
            r.reloadReturned=true;
            require(r.failure==null && r.released && r.sourceCompared && r.registrations==1 && r.applications==1
                    && r.managedEntries==1 && r.managedReturns==1,"TERMINAL_STATUS_RELOAD_WITHOUT_PROOF");
            unchanged(r); write(r);
        } catch(IOException failure) { throw new IllegalStateException("TERMINAL_STATUS_RECEIPT_FAILED",failure); }
        finally { r.reloadActive=false; }
    }
    @SubscribeEvent static void registration(AddReloadListenerEvent event) {
        var r=active; if(r==null || !r.reloadActive) { return; }
        synchronized(r) { if(active!=r || !r.reloadActive) { return; } require(++r.registrations==1,"TERMINAL_STATUS_DUPLICATE_REGISTRATION"); }
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier,ResourceManager resources,
                    ProfilerFiller preparation,ProfilerFiller application,java.util.concurrent.Executor background,java.util.concurrent.Executor game) {
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored->{
                    require(active==r && r.reloadActive && r.server.isSameThread() && r.inManaged && ++r.applications==1,"TERMINAL_STATUS_APPLY_OWNER");
                    unchanged(r); r.sourceBefore=sourceFacts(r); r.nativeBefore=nativeFacts(r); r.waiting=true;
                    try { P11C4aEvidence.cue(r.output,"a-terminal-status-window.ready"); }
                    catch(IOException failure) { r.gate.completeExceptionally(failure); }
                    return r.gate.thenRunAsync(()->{
                        require(r.server.isSameThread() && r.released && r.failure==null,"TERMINAL_STATUS_RELEASE_OWNER");
                        unchanged(r); r.sourceAfter=sourceFacts(r); r.nativeAfter=nativeFacts(r);
                        require(r.sourceBefore.equals(r.sourceAfter) && r.nativeBefore.equals(r.nativeAfter),"TERMINAL_STATUS_SOURCE_OR_NATIVE_MUTATED");
                        r.sourceCompared=true;
                    },game);
                },game);
            }
        });
    }
    public static void managed(MinecraftServer server,boolean entering,boolean normal) {
        var r=active; if(r==null || r.server!=server || !r.reloadActive) { return; }
        if(entering) { r.inManaged=true; r.managedEntries++; } else { r.inManaged=false; if(normal) { r.managedReturns++; } }
    }
    private static void unchanged(Run r) {
        require(r.terminal.equals(r.control.state().orElse(null)) && r.control.fence()==r.fence
                && r.control.executableHeld()==r.held && (!r.handoff || r.control.listener()==null),"TERMINAL_STATUS_RECORD_FENCE_HELD_CHANGED");
    }
    private static long roots(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()]; }
    private static Facts sourceFacts(Run r) {
        require(r.server.isSameThread() && r.source.body(r.current)==r.body && r.body.account.current==r.body
                && r.source.canCopy(r.body) && r.current.connection.getConnection()==r.connection,"TERMINAL_STATUS_SOURCE_CHANGED");
        return new Facts(r.body.source.epoch(),r.body.source.version(),r.body.complete,r.body.fault.name(),r.body.account.candidate!=null,
                Arrays.stream(r.body.account.nativeCounts).boxed().toList(),r.service.terminalFailureCount());
    }
    private static List<Long> nativeFacts(Run r) {
        return List.of(P11C4aNativeObservations.tries(r.connection),
                P11C4aNativeObservations.count(r.connection,P11C4aNativeObservations.Event.RESPAWN_TICKET_CHECKED),
                P11C4aNativeObservations.count(r.connection,P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER),
                P11C4aNativeObservations.count(r.connection,P11C4aNativeObservations.Event.RESPAWN_SEND_RETURN),
                P11C4aNativeObservations.count(r.connection,P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED),
                P11C4aNativeObservations.count(r.connection,P11C4aNativeObservations.Event.LOGIN_SEND_RETURN));
    }
    private static void write(Run r) throws IOException {
        var m=new LinkedHashMap<String,Object>(); m.put("status","ACTUAL_STATUS_NEGATIVES_AT_REAL_OWNER_WINDOW_NOT_FULL_ACCEPTANCE");
        m.put("handoff",r.handoff); m.put("actualRequest",r.request); m.put("terminalBeforeAndAfter",r.terminal);
        m.put("sameOffer",r.handoff?"STALE":"COALESCED"); m.put("sameOffers",r.same); m.put("differentOffers",r.different);
        m.put("differentOffer",r.handoff?"NOT_IN_THIS_CASE":"BUSY"); m.put("fenceBefore",r.fence); m.put("fenceAfter",r.control.fence());
        m.put("heldBefore",r.held); m.put("heldAfter",r.control.executableHeld());
        m.put("sourceBeforeStatus",r.sourceBefore); m.put("sourceAfterStatus",r.sourceAfter);
        m.put("nativeBeforeStatus",r.nativeBefore); m.put("nativeAfterStatus",r.nativeAfter);
        m.put("nativeCounterOrder",List.of("TRY","RESPAWN_TICKET","RESPAWN_CALL","RESPAWN_SEND","FACTORY_TICKET","LOGIN_SEND"));
        m.put("completedSendReturns",r.completedSendReturns); m.put("realHandoffReservations",r.handoffCalls);
        m.put("managedEntries",r.managedEntries); m.put("managedReturns",r.managedReturns);
        m.put("registrations",r.registrations); m.put("applications",r.applications); m.put("gateReleasedAfterFullIngress",r.released);
        m.put("sourceInterval","ACTUAL_APPLY_GATE_TO_GAME_CONTINUATION_NOT_ACROSS_WHOLE_RELOAD");
        m.put("nativePrimaryPolicyAltered",false); m.put("fullC4aAcceptance",false);
        P11C4aEvidence.write(r.output,"terminal-status-server.json",m);
    }
    private static boolean same(Request a,Request b) { return a!=null && a.scope()==b.scope() && a.connectionEpoch()==b.connectionEpoch()
            && a.sceneSerial()==b.sceneSerial() && a.actorGeneration()==b.actorGeneration() && a.kind()==b.kind(); }
    private static boolean matches(Request a,State b) { return a!=null && a.scope()==b.scope() && a.connectionEpoch()==b.connectionEpoch()
            && a.sceneSerial()==b.sceneSerial() && a.actorGeneration()==b.actorGeneration() && a.kind()==b.kind() && a.requestSeq()==b.requestSeq(); }
    private static void exact(MinecraftServer s,ServerPlayer a) { require(s.isSameThread() && a!=null && !a.isFakePlayer() && a.getServer()==s
            && s.getPlayerList().getPlayer(a.getUUID())==a && a.connection!=null && a.connection.getConnection().isConnected()
            && a.connection.getConnection().isEncrypted() && !a.connection.getConnection().isMemoryConnection()
            && P11NativeStorageBoundary.nativeDeliveryEligible(a),"TERMINAL_STATUS_REAL_ROSTER"); }
    private static P11QualifiedSourceOwner.Body source(ServerPlayer a) { var s=P11NativeStorageBoundary.nativeSourceOwner(a); var b=s==null?null:s.body(a);
        require(b!=null && b.actor==a && s.canCopy(b),"TERMINAL_STATUS_QUALIFIED_SOURCE"); return b; }
    private static void restore(Run r) { if(!r.handoff) { r.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(r.keep,r.server);
        r.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(r.immediate,r.server); } }
    private static void fail(Run r,String code) { if(r.failure==null) { r.failure=code; } }
    private static void require(boolean ok,String code) { P11C4aEvidence.require(ok,code); }
    private record Facts(long epoch,long version,boolean complete,String fault,boolean candidate,List<Long> nativeRoots,long terminalFailures) { }
    private static final class Ingress { final Run run; final Request request; final Ingress previous; boolean offered;
        Ingress(Run r,Request q,Ingress p) { run=r;request=q;previous=p; } }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer initial,peer; final Connection connection,peerConnection;
        final Path output,client; final Object canonical,statistics; final boolean handoff,keep,immediate; final long epoch,peerTries;
        final CompletableFuture<Void> gate=new CompletableFuture<>();
        volatile Request request; volatile P11TransitionControl control; volatile String failure; volatile boolean reloadActive,waiting,released;
        volatile int tries,same,different,registrations; State terminal; long fence; boolean held;
        P11QualifiedSourceOwner source; P11QualifiedSourceOwner.Body body; P11LiveTransitionService service; ServerPlayer current,finalActor;
        Object reservation; Facts sourceBefore,sourceAfter; List<Long> nativeBefore,nativeAfter;
        int ticks,logouts,handoffCalls,completeCalls,completedSendReturns,managedEntries,managedReturns,applications;
        volatile boolean startedNative,qualified;
        boolean parkingStarted,parkingDone,configurationLogout,windowStarted,inManaged,reloadReturned,sourceCompared,finishObserved;
        Run(MinecraftServer s,ServerPlayer a,ServerPlayer p,Path o) { server=s;initial=a;peer=p;output=o;client=o.resolveSibling("client-a");
            connection=a.connection.getConnection();peerConnection=p.connection.getConnection();canonical=a.getAdvancements();statistics=a.getStats();
            epoch=source(a).source.epoch();peerTries=P11C4aNativeObservations.tries(peerConnection);handoff=handoffMode();
            keep=s.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);immediate=s.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN); }
    }
}
