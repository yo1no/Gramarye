package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Excluded one-shot pre-enqueue fault. Global control retirement is NOT peer-control isolation. */
public final class P11C4aSubmissionRejectProbe {
    private static volatile Run active;
    private static final ThreadLocal<Run> NETWORK = new ThreadLocal<>();
    private static final ThreadLocal<Run> INGRESS = new ThreadLocal<>();
    private P11C4aSubmissionRejectProbe() { }

    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_SUBMISSION_REJECT; }
    static void start(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        require(active == null && server.isSameThread() && server.isDedicatedServer()
                && actor != peer && actor.isAlive() && peer.isAlive()
                && actor.connection.getConnection().isEncrypted() && peer.connection.getConnection().isEncrypted()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                && P11NativeStorageBoundary.nativeDeliveryEligible(peer), "SUBMIT_REAL_PAIR");
        active = new Run(server, actor, peer, output);
        P11C4aEvidence.write(output, "submission-armed.json", snapshot(active, "ARMED_NOT_ACCEPTANCE"));
        P11C4aEvidence.cue(output, "a-submission.ready");
        P11C4aEvidence.cue(output, "b-submission.ready");
    }

    static boolean tick() throws IOException {
        var run = active; require(run != null && run.server.isSameThread(), "SUBMIT_TICK_OWNER");
        require(run.failure == null && ++run.ticks <= 2400, "SUBMIT_OBSERVER_OR_DEADLINE");
        if (!run.killed && P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"), "submission-armed.json")
                && P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-b"), "submission-peer-armed.json")) {
            run.killed = true;
            run.actor.hurt(run.actor.damageSources().genericKill(), Float.MAX_VALUE);
            require(run.actor.isDeadOrDying(), "SUBMIT_REAL_DEATH");
        }
        if (!run.channelReturned) { return false; }
        require(run.failure == null && run.injections == 1 && run.ingressThrows == 1 && run.dispatchThrows == 1
                && run.wakeupRetires == 1 && run.dispatcherRetires == 1 && run.nativeDisconnectEntries == 1
                && run.nativeDisconnectReturns == 1 && run.globalStopping && run.bothControlsUnknown && run.executions==0
                , "SUBMIT_ORIGINAL_RETIREMENT_OR_NATIVE_POLICY");
        require(!run.connection.isConnected() && run.peerConnection.isConnected()
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer && run.peer.isAlive()
                && P11NativeStorageBoundary.nativeDeliveryEligible(run.peer)
                && P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER) == 0
                && P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED) == 0,
                "SUBMIT_BODY_FRAME_OR_PEER_NATIVE_CHANGED");
        var source=P11NativeStorageBoundary.nativeSourceOwner(run.peer);
        var body=source==null?null:source.body(run.peer);
        require(body!=null && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()]==0,
                "SUBMIT_PEER_CUSTODY");
        require(run.actorBody!=null && run.actorHBefore==0
                && run.actorBody.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()]==0,
                "SUBMIT_ACTOR_CUSTODY_NOT_ZERO");
        if (!run.sealed) {
            run.sourceAfter=P11C4aEvidence.sourceObservation(source.diagnostics(run.peer.getUUID()));
            run.sealed=true;
            P11C4aEvidence.write(run.output,"submission-server.json",snapshot(run,"ACTUAL_PRE_ENQUEUE_REJECTION_AND_GLOBAL_CONTROL_RETIREMENT"));
            P11C4aEvidence.cue(run.output,"b-submission-swing.ready");
        }
        return P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-a"),"submission-client.json")
                && P11C4aEvidence.receiptPresent(run.output.resolveSibling("client-b"),"submission-peer-swing.json")
                && run.peerSwings==1;
    }

    public static Object channelEntered(Connection connection, Packet<?> packet) {
        var run=active;
        if(run==null || connection!=run.connection || !run.killed || !(packet instanceof ServerboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionRequestPayload payload) || payload.request().command()!=Command.TRY) return null;
        observe(run,()->{
            require(NETWORK.get()==null && run.networkEntries++==0 && !run.server.isSameThread()
                    && payload.request().kind()==Kind.DEATH && payload.request().scope()==Scope.PLAY
                    && payload.request().requestSeq()>0, "SUBMIT_NETWORK_REQUEST");
            run.request=payload.request(); run.packet=packet; NETWORK.set(run);
        });
        return run;
    }

    public static Object ingressEntered(Object service, Object rawRequest, Connection connection, ICommonPacketListener listener) {
        var run=NETWORK.get();
        if(run==null || connection!=run.connection) return null;
        observe(run,()->{
            require(INGRESS.get()==null && run.ingressEntries++==0 && rawRequest==run.request
                    && listener==run.actor.connection && connection.getPacketListener()==listener,
                    "SUBMIT_EXACT_INGRESS");
            run.service=service; INGRESS.set(run);
        });
        return run;
    }

    /** The only intentional fault: original tell is not invoked, so this exact task was never enqueued. */
    public static void beforeTell(Object service, MinecraftServer server, Runnable task) {
        var run=INGRESS.get(); if(run==null) return;
        require(run.failure==null && run.service==service && run.server==server && task instanceof TickTask
                && run.originalWakeupRequested && run.pumpRequests==1
                && run.injections++==0 && !server.isSameThread(), "SUBMIT_NOT_EXACT_PRE_ENQUEUE");
        throw run.rejection;
    }
    public static void pumpRequested(boolean originalResult) {
        var run=INGRESS.get();if(run==null)return;
        run.originalWakeupRequested=originalResult;
        if(++run.pumpRequests!=1 || !originalResult)run.failure="SUBMIT_COALESCED_NO_NEW_QUEUE_SLOT_UNPROVEN";
    }
    public static void executing(Object rawEntry) {
        var run=active;
        if(run!=null && rawEntry instanceof P11C4aNativeErrorProbe.EntryView entry
                && entry.error$connection()==run.connection)run.executions++;
    }

    public static void retired(boolean wakeup, boolean originalResult) {
        var run=INGRESS.get(); if(run==null) return;
        observe(run,()->{ require(run.injections==1 && originalResult,"SUBMIT_RETIRE_BEFORE_FAULT");
            if(wakeup)run.wakeupRetires++;else run.dispatcherRetires++; });
    }

    public static void ingressEnded(Object token, boolean stopping, List<?> entries, Throwable escaping) {
        if(!(token instanceof Run run))return;
        try {
            boolean a=false,b=false,noHeld=true;
            for(var raw:entries) if(raw instanceof P11C4aNativeErrorProbe.EntryView entry) {
                if(entry.error$connection()!=run.connection && entry.error$connection()!=run.peerConnection)continue;
                var control=(P11TransitionControl)entry.error$control();
                boolean unknown=control.disposition()==P11TransitionControl.Disposition.UNKNOWN;
                // Observe existing ledger state only; no invented wire UNKNOWN is required.
                noHeld &= !control.executableHeld();
                if(entry.error$connection()==run.connection)a=unknown;else b=unknown;
            }
            final boolean both=a&&b, clear=noHeld;
            observe(run,()->{
                require(INGRESS.get()==run && run.ingressThrows++==0
                        && retirementMatches(run.rejection,escaping,run.injections,stopping,both,
                                run.wakeupRetires,run.dispatcherRetires),"SUBMIT_PRIMARY_OR_GLOBAL_RETIREMENT");
                run.globalStopping=stopping;run.bothControlsUnknown=both;run.noHeldControls=clear;
            });
        } finally { INGRESS.remove(); }
    }
    public static void observerFailed(Object token) {
        if(token instanceof Run run)run.failure="SUBMIT_OBSERVER_FAILED";
        INGRESS.remove();
    }

    public static void dispatchThrown(Connection connection, Throwable escaping) {
        var run=NETWORK.get(); if(run==null || connection!=run.connection)return;
        observe(run,()->require(escaping==run.rejection && run.ingressThrows==1 && run.dispatchThrows++==0,
                "SUBMIT_NATIVE_DISPATCH_PRIMARY"));
    }

    public static boolean disconnectEntered(Connection connection, Component reason) {
        var run=NETWORK.get(); if(run==null || connection!=run.connection)return false;
        observe(run,()->require(run.dispatchThrows==1 && run.nativeDisconnectEntries++==0
                && shutdownReason(reason), "SUBMIT_WRONG_NATIVE_DISCONNECT"));
        return true;
    }
    public static void disconnectReturned(boolean selected) {
        var run=NETWORK.get(); if(run!=null && selected)observe(run,()->run.nativeDisconnectReturns++);
    }
    static boolean shutdownReason(Component reason) {
        return reason!=null && reason.getContents() instanceof TranslatableContents content
                && content.getKey().equals("multiplayer.disconnect.server_shutdown");
    }
    static boolean retirementMatches(Throwable owned,Throwable escaping,int injections,boolean stopping,
            boolean bothUnknown,int wakeup,int dispatcher) {
        return owned instanceof RejectedExecutionException && escaping==owned && injections==1
                && stopping && bothUnknown && wakeup==1 && dispatcher==1;
    }
    public static void channelEnded(Object token, boolean normal, Throwable escaping) {
        if(!(token instanceof Run run))return;
        try {
            observe(run,()->{
                require(NETWORK.get()==run && normal && escaping==null && run.dispatchThrows==1
                        && run.nativeDisconnectEntries==1 && run.nativeDisconnectReturns==1,
                        "SUBMIT_ORIGINAL_CHANNEL_CATCH_NOT_RETURNED");
                run.channelReturned=true;
            });
        } finally { NETWORK.remove(); }
    }
    public static void peerSwing(ServerPlayer actor) {
        var run=active;if(run==null || actor!=run.peer || !run.sealed)return;
        observe(run,()->require(++run.peerSwings==1 && actor.connection.getConnection()==run.peerConnection
                && run.peerConnection.isConnected(),"SUBMIT_PEER_NATIVE_SWING"));
    }
    static void stopped(MinecraftServer server) {
        var run=active;if(run==null || run.server!=server)return;
        try {P11C4aEvidence.write(run.output,"submission-stopped.json",snapshot(run,"ORIGINAL_SERVER_STOP_AFTER_SEPARATE_HARNESS_CLEANUP"));}
        catch(IOException|RuntimeException|Error ignored){run.failure="SUBMIT_STOP_EVIDENCE";}
        finally {active=null;}
    }
    private static void observe(Run run,Runnable action) {
        try {action.run();} catch(RuntimeException|Error secondary){run.failure="SUBMIT_OBSERVER_FAILED";}
    }
    private static Map<String,Object> snapshot(Run run,String status) {
        var m=new LinkedHashMap<String,Object>();
        m.put("status",status);m.put("failureCode",run.failure==null?"NONE":run.failure);
        m.put("request",run.request);m.put("preEnqueueInjections",run.injections);
        m.put("originalWakeupRequestCalls",run.pumpRequests);m.put("originalWakeupRequestResult",run.originalWakeupRequested);
        m.put("originalSelectedTellInvocations",0);m.put("postEnqueueAmbiguityClaimed",false);
        m.put("originalExecuteEntries",run.executions);m.put("actualActorTransitionCustodyBefore",run.actorHBefore);
        m.put("actualActorTransitionCustodyAfter",run.sealed ? run.actorBody.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] : -1);
        m.put("networkEntries",run.networkEntries);m.put("originalIngressThrowsSamePrimary",run.ingressThrows);
        m.put("originalDispatchThrowsSamePrimary",run.dispatchThrows);m.put("originalChannelNormalReturn",run.channelReturned);
        m.put("originalShutdownDisconnectEntries",run.nativeDisconnectEntries);m.put("originalShutdownDisconnectReturns",run.nativeDisconnectReturns);
        m.put("originalWakeupRetireReturns",run.wakeupRetires);m.put("originalDispatcherRetireReturns",run.dispatcherRetires);
        m.put("serviceStoppingObserved",run.globalStopping);m.put("bothOriginalControlsUnknown",run.bothControlsUnknown);
        m.put("neitherControlRetainsHeldBookkeeping",run.noHeldControls);m.put("peerOriginalSwingReturns",run.peerSwings);
        m.put("sourceAfterNativeNetworkPolicy",run.sourceAfter);
        m.put("peerControlIsolationClaimed",false);m.put("naturalExecutorRejectionClaimed",false);
        m.put("sourceDurabilityOrFullC4aAcceptance",false);return m;
    }
    private static void require(boolean value,String code){P11C4aEvidence.require(value,code);}
    private static final class Run {
        final MinecraftServer server;final ServerPlayer actor,peer;final Connection connection,peerConnection;final Path output;
        final P11QualifiedSourceOwner.Body actorBody;final long actorHBefore;
        final RejectedExecutionException rejection=new RejectedExecutionException("C4A_OWNED_PRE_ENQUEUE_REJECTION");
        volatile String failure;volatile boolean channelReturned,sealed,killed;
        Object service;Packet<?> packet;Request request;Map<String,Object> sourceAfter;
        volatile int injections,ingressThrows,dispatchThrows,wakeupRetires,dispatcherRetires,nativeDisconnectEntries,nativeDisconnectReturns,peerSwings,executions;
        int ticks,networkEntries,ingressEntries,pumpRequests;boolean globalStopping,bothControlsUnknown,noHeldControls,originalWakeupRequested;
        Run(MinecraftServer server,ServerPlayer actor,ServerPlayer peer,Path output) {
            this.server=server;this.actor=actor;this.peer=peer;this.output=output;
            connection=actor.connection.getConnection();peerConnection=peer.connection.getConnection();
            var source=P11NativeStorageBoundary.nativeSourceOwner(actor);actorBody=source==null?null:source.body(actor);
            actorHBefore=actorBody==null?-1:actorBody.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()];
        }
    }
}
