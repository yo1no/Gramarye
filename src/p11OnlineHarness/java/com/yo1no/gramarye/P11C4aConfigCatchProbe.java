package com.yo1no.gramarye;
import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Excluded one genuine RETURN constructor-to-place gap; original CONFIG catch stays in charge. */
public final class P11C4aConfigCatchProbe {
    private static Run active;
    private P11C4aConfigCatchProbe() {}
    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_CONFIG_CATCH || constructorMode(); }
    private static boolean constructorMode() { return P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_CONSTRUCTOR_PRISTINE
            || P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_CONSTRUCTOR_ESCAPED; }
    private static boolean escapedMode() { return P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_CONSTRUCTOR_ESCAPED; }
    static boolean started() { return active != null; }
    static void start(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        require(selected() && active == null && server.isSameThread() && server.isDedicatedServer() && server.usesAuthentication()
                && actor != peer && !actor.getUUID().equals(peer.getUUID()) && actor.isAlive() && peer.isAlive()
                && actor.connection.getConnection().isEncrypted() && peer.connection.getConnection().isEncrypted()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor) && P11NativeStorageBoundary.nativeDeliveryEligible(peer),
                "CONFIG_CATCH_REAL_AUTHENTICATED_PAIR");
        active = new Run(server, actor, peer, output);
        require(count(active.a, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == 1
                && count(active.a, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER) == 0, "CONFIG_CATCH_INITIAL_ONLY");
        P11C4aEvidence.cue(output, "a-config-catch-arm.ready");
        P11C4aEvidence.cue(output, "b-config-catch-arm.ready");
    }
    static boolean tick() throws IOException {
        var r=active;require(r!=null && r.server.isSameThread() && r.failure==null && ++r.ticks<=2400,"CONFIG_CATCH_OWNER_OR_FAILURE");
        // Qualification already sealed the live-peer proof and cued its original departure.
        // A native channel can close before main observes PlayerLoggedOut; await that event.
        if(r.qualified)return r.bLoggedOut;
        require(r.b.isConnected() && r.server.getPlayerList().getPlayer(r.peer.getUUID())==r.peer && r.peer.isAlive()
                && r.peer.connection.getConnection()==r.b, "CONFIG_CATCH_PEER_CHANGED");
        var root=P11C4aEvidence.root();
        if(r.stage==0) {
            if(!P11C4aEvidence.receiptPresent(root.resolve("client-a"),"config-catch-armed.json")
                    || !P11C4aEvidence.receiptPresent(root.resolve("client-b"),"config-catch-armed.json"))return false;
            r.stage=1;r.actor.connection.switchToConfig();return false;
        }
        if(r.stage==1) {
            if(!P11C4aEvidence.receiptPresent(root.resolve("client-a"),"config-catch-config-terminal.json"))return false;
            require(r.a.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                    && r.server.getPlayerList().getPlayer(r.actor.getUUID())==null && r.aLogouts==1,
                    "CONFIG_CATCH_INDEPENDENT_CONFIG_TERMINAL");
            r.config=(ServerConfigurationPacketListenerImpl)r.a.getPacketListener();
            if(constructorMode()) {
                r.owner=P11NativeStorageBoundary.nativeSourceOwner(r.actor);r.previous=r.owner==null?null:r.owner.body(r.actor);
                require(r.previous!=null && r.previous.account.current==r.previous && r.owner.canSerialize(r.previous)
                        && r.previous.complete && r.previous.envelope!=null && !r.previous.account.cleanupUnknown
                        && r.previous.account.candidate==null && !r.previous.account.constructorFailed
                        && ((P11CanonicalAdvancements.Access)r.actor.getAdvancements()).p11$associatedPlayer()==r.actor,
                        "CONFIG_CONSTRUCTOR_QUALIFIED_DETACHED_PREDECESSOR");
                r.oldEpoch=r.previous.source.epoch();
            }
            r.stage=2;r.returnCalls++;r.config.returnToWorld();return false;
        }
        if(r.a.isConnected() || !r.callerReturned || !P11C4aEvidence.receiptPresent(root.resolve("client-a"),"config-catch-client-terminal.json"))return false;
        require(r.failure==null && r.factoryReturns==(constructorMode()?0:1) && r.injections==1 && r.placeCalls==0
                && r.logEntries==1 && r.logReturns==1 && r.invalidSends==1 && r.invalidSendReturns==1
                && r.disconnects==1 && r.disconnectReturns==1 && r.returnCalls==1 && r.aLogouts==1
                && r.finalState!=null && (r.finalState.outcome()==Outcome.FAULT || r.finalState.outcome()==Outcome.UNKNOWN)
                && r.finalState.availability()==Availability.DISABLED
                && r.server.getPlayerList().getPlayer(r.actor.getUUID())==null
                && count(r.a,P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED)==1
                && count(r.a,P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED)==0
                && count(r.a,P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED)==2,
                "CONFIG_CATCH_ORIGINAL_POLICY_NOT_PROVEN");
        if(constructorMode())require(r.withdrawalChecked && r.constructorAssociations==1
                && r.constructorCallbacks==(escapedMode()?1:0),"CONFIG_CONSTRUCTOR_WITHDRAWAL_NOT_PROVEN");
        if(!r.peerCue){r.peerCue=true;P11C4aEvidence.cue(r.output,"b-config-catch-action.ready");return false;}
        if(r.swings==0)return false;
        require(r.swings==1 && r.peer.getStats()==r.peerStats && r.peer.getAdvancements()==r.peerAdvancements
                && P11NativeStorageBoundary.diagnostics(r.server,r.peer.getUUID()).sourceEpoch()==r.peerEpoch,
                "CONFIG_CATCH_PEER_NOT_UNCHANGED");
        if(!r.qualified){
            r.qualified=true;P11C4aEvidence.write(r.output,"config-catch-server-terminal.json",report(r,"ORIGINAL_CONFIG_CATCH_SUBSET_NOT_FULL_ACCEPTANCE"));
            r.server.saveEverything(false,true,true);P11C4aEvidence.cue(r.output,"b-config-catch-finish.ready");
        }
        return false;
    }
    public static void gate(Object rawEntry) {
        var r=active;
        if(r==null || r.stage!=2 || !(rawEntry instanceof P11C4aNativeErrorProbe.EntryView entry) || entry.error$connection()!=r.a)return;
        observe(r,()->{require(r.control==null || r.control==entry.error$control(),"CONFIG_CATCH_CONTROL_CHANGED");
            r.control=(P11TransitionControl)entry.error$control();});
    }
    /** Called only after original getPlayerForLogin returned to the original CONFIG try. */
    public static void constructed(ServerConfigurationPacketListenerImpl listener, ServerPlayer candidate) {
        var r=active;if(r==null || listener!=r.config)return;
        require(!constructorMode(),"CONFIG_CONSTRUCTOR_INJECTION_NOT_REACHED");
        require(r.failure==null && r.server.isSameThread() && r.stage==2 && r.a.getPacketListener()==listener
                && r.a.isConnected() && candidate!=r.actor && candidate.getUUID().equals(r.actor.getUUID())
                && candidate.getServer()==r.server && r.server.getPlayerList().getPlayer(r.actor.getUUID())==null
                && P11LiveTransitionBoundary.currentNativeAttempt(listener)!=null && r.control!=null
                && r.factoryReturns++==0 && r.injections==0,"CONFIG_CATCH_NOT_REAL_FACTORY_TAIL");
        r.running=r.control.state().orElseThrow();
        require(r.running.scope()==Scope.PREPLAY && r.running.kind()==Kind.RETURN_TO_WORLD
                && r.running.outcome()==Outcome.RUNNING,"CONFIG_CATCH_NOT_ACTUAL_RUNNING");
        r.constructed=candidate;r.injections++;
        r.sourceAtConstructor=P11C4aEvidence.sourceObservation(P11NativeStorageBoundary.diagnostics(r.server,r.actor.getUUID()));
        throw r.fault;
    }
    /** Two exact locked constructor seams; no native source, callback or authority is synthesized. */
    public static void constructorAssociation(ServerPlayer candidate,boolean callback) {
        var r=active;if(r==null || !constructorMode() || candidate==r.actor || candidate.getServer()!=r.server
                || !candidate.getUUID().equals(r.actor.getUUID()))return;
        if(callback && !escapedMode())return;
        require(r.failure==null && r.server.isSameThread() && r.stage==2 && r.config!=null
                && r.a.getPacketListener()==r.config && r.a.isConnected() && r.actor.isRemoved()
                && r.server.getPlayerList().getPlayer(r.actor.getUUID())==null
                && P11LiveTransitionBoundary.currentNativeAttempt(r.config)!=null && r.control!=null
                && r.owner.body(r.actor)==r.previous && r.previous.account.current==r.previous
                && candidate.getAdvancements()==r.actor.getAdvancements() && candidate.getStats()==r.actor.getStats()
                && ((P11CanonicalAdvancements.Access)r.actor.getAdvancements()).p11$associatedPlayer()==candidate
                && r.owner.body(candidate)==null && r.injections==0,"CONFIG_CONSTRUCTOR_EXACT_NATIVE_ASSOCIATION");
        if(!callback) {
            require(r.constructorAssociations++==0 && r.constructed==null,"CONFIG_CONSTRUCTOR_ASSOCIATION_REPLAY");
            r.constructed=candidate;r.sourceAtAssociation=r.previous.source;
            if(escapedMode())return;
        } else {
            require(r.constructorAssociations==1 && r.constructed==candidate && r.constructorCallbacks++==0,
                    "CONFIG_CONSTRUCTOR_CALLBACK_ORDER");
        }
        r.running=r.control.state().orElseThrow();
        require(r.running.scope()==Scope.PREPLAY && r.running.kind()==Kind.RETURN_TO_WORLD
                && r.running.outcome()==Outcome.RUNNING,"CONFIG_CONSTRUCTOR_NOT_ACTUAL_RUNNING");
        r.sourceAtConstructor=P11C4aEvidence.sourceObservation(P11NativeStorageBoundary.diagnostics(r.server,r.actor.getUUID()));
        r.injections++;throw r.fault;
    }
    public static void place(ServerConfigurationPacketListenerImpl listener) {
        var r=active;if(r!=null && listener==r.config)r.placeCalls++;
    }
    public static void logged(ServerConfigurationPacketListenerImpl listener,Throwable failure,boolean returned) {
        var r=active;if(r==null || listener!=r.config)return;
        observe(r,()->{require(failure==r.fault && r.injections==1,"CONFIG_CATCH_PRIMARY_IDENTITY");
            if(returned)r.logReturns++;else r.logEntries++;});
    }
    public static void invalid(ServerConfigurationPacketListenerImpl listener,Connection connection,boolean reasonExact,boolean disconnect,boolean returned) {
        var r=active;if(r==null || listener!=r.config)return;
        observe(r,()->{require(connection==r.a && reasonExact && r.logReturns==1,"CONFIG_CATCH_INVALID_DATA_OR_ORDER");
            if(disconnect){if(returned)r.disconnectReturns++;else r.disconnects++;}
            else{if(returned)r.invalidSendReturns++;else r.invalidSends++;}});
    }
    /** Outer existing source scope has now closed; no retained ticket or replay. */
    public static void callerEnded(ServerConfigurationPacketListenerImpl listener,boolean normal,Throwable escaping) {
        var r=active;if(r==null || listener!=r.config || !r.server.isSameThread())return;
        observe(r,()->{
            require(normal && escaping==null && r.injections==1 && r.logEntries==1 && r.logReturns==1
                    && r.disconnectReturns==1 && !r.callerReturned && r.control!=null,"CONFIG_CATCH_ORIGINAL_CATCH_DID_NOT_RETURN");
            r.finalState=r.control.state().orElseThrow();
            require(faultTerminal(r.running,r.finalState),"CONFIG_CATCH_FALSE_COMPLETION_OR_F0");
            r.callerReturned=true;
            r.sourceAfterOuter=P11C4aEvidence.sourceObservation(P11NativeStorageBoundary.diagnostics(r.server,r.actor.getUUID()));
            if(constructorMode())verifyWithdrawal(r);
        });
        write(r,"config-catch-native.json","ORIGINAL_CONFIG_CATCH_NORMAL_RETURN_NOT_TRANSITION_COMPLETION");
    }
    public static void animate(ServerGamePacketListenerImpl listener) {
        var r=active;if(r!=null && r.peerCue && r.server.isSameThread() && listener.player==r.peer && listener.getConnection()==r.b)r.swings++;
    }
    static void logout(ServerPlayer actor) {
        var r=active;require(r!=null && actor.getServer()==r.server,"CONFIG_CATCH_LOGOUT_OWNER");
        if(actor==r.actor){require(++r.aLogouts==1 && r.stage==1,"CONFIG_CATCH_OLD_A_LOGOUT_ORDER");return;}
        require(actor==r.peer && r.qualified && !r.bLoggedOut,"CONFIG_CATCH_UNEXPECTED_LOGOUT");r.bLoggedOut=true;
    }
    static void stopped(MinecraftServer server) {
        var r=active;if(r!=null && r.server==server)write(r,"config-catch-stopped.json","ORIGINAL_STOP_OBSERVED_SOURCE_DURABILITY_SEPARATE");
        active=null;
    }
    static void abort(){var r=active;if(r!=null)write(r,"config-catch-failure.json","FIXED_FAILURE_DIAGNOSTIC_NOT_ACCEPTANCE");active=null;}
    static boolean faultTerminal(State running,State terminal){return same(running,terminal)
            && running.scope()==Scope.PREPLAY && running.kind()==Kind.RETURN_TO_WORLD && running.outcome()==Outcome.RUNNING
            && terminal.statusVersion()>running.statusVersion()
            && (terminal.outcome()==Outcome.FAULT || terminal.outcome()==Outcome.UNKNOWN)
            && terminal.availability()==Availability.DISABLED;}
    private static boolean same(State a,State b){return a!=null&&b!=null&&a.connectionEpoch()==b.connectionEpoch()&&a.sceneSerial()==b.sceneSerial()
            &&a.actorGeneration()==b.actorGeneration()&&a.requestSeq()==b.requestSeq()&&a.scope()==b.scope()&&a.kind()==b.kind();}
    private static void verifyWithdrawal(Run r){
        var receiver=((P11CanonicalAdvancements.Access)r.actor.getAdvancements()).p11$associatedPlayer();
        require(r.owner.body(r.actor)==r.previous && r.previous.account.current==r.previous
                && r.previous.source==r.sourceAtAssociation && r.previous.source.epoch()==r.oldEpoch
                && r.previous.account.candidate==null && r.owner.body(r.constructed)==null
                && r.previous.account.constructing==null && r.previous.account.login==null
                && r.actor.isRemoved() && r.previous.envelope!=null && !r.previous.account.cleanupUnknown
                && r.server.getPlayerList().getPlayer(r.actor.getUUID())==null
                && r.actor.getAdvancements()==r.previous.advancements && r.actor.getStats()==r.previous.stats,
                "CONFIG_CONSTRUCTOR_SOURCE_REPLACED_OR_A_REVIVED");
        if(escapedMode()) {
            require(receiver==r.constructed && r.previous.account.constructorFailed
                    && r.previous.account.partialActor==r.constructed && !r.owner.canSerialize(r.previous)
                    && r.previous.fault==P11QualifiedSourceOwner.Fault.PARTIAL,
                    "CONFIG_CONSTRUCTOR_UNKNOWN_CALLBACK_WITHDREW_OR_SERIALIZED");
        } else {
            require(receiver==r.actor && !r.previous.account.constructorFailed
                    && r.previous.account.partialActor==null && r.previous.complete && r.owner.canSerialize(r.previous),
                    "CONFIG_CONSTRUCTOR_PRISTINE_ASSOCIATION_NOT_WITHDRAWN");
        }
        r.withdrawalChecked=true;r.associationRestored=receiver==r.actor;
    }
    private static void observe(Run r,Runnable call){try{call.run();}catch(RuntimeException|Error secondary){r.failure="CONFIG_CATCH_OBSERVER_FAILED";}}
    private static void write(Run r,String leaf,String status){try{P11C4aEvidence.write(r.output,leaf,report(r,status));}
        catch(IOException|RuntimeException|Error secondary){r.failure="CONFIG_CATCH_EVIDENCE_FAILED";}}
    private static Map<String,Object> report(Run r,String status){
        var m=new LinkedHashMap<String,Object>();m.put("status",status);m.put("failure",r.failure==null?"NONE":r.failure);
        m.put("stage",r.stage);m.put("originalReturnToWorldCalls",r.returnCalls);m.put("originalConstructorReturns",r.factoryReturns);
        m.put("originalPlacementCalls",r.placeCalls);m.put("ownedRuntimeInjections",r.injections);
        m.put("samePrimaryOriginalCatchEntries",r.logEntries);m.put("originalLoggerReturns",r.logReturns);
        m.put("originalInvalidDataSendEntries",r.invalidSends);m.put("originalInvalidDataSendReturns",r.invalidSendReturns);
        m.put("originalInvalidDataDisconnectEntries",r.disconnects);m.put("originalInvalidDataDisconnectReturns",r.disconnectReturns);
        m.put("originalOuterCallerReturnedNormally",r.callerReturned);m.put("actualRunning",r.running);m.put("actualFinal",r.finalState);
        m.put("nativeALogouts",r.aLogouts);m.put("peerOriginalSwingReturns",r.swings);m.put("exactAConnected",r.a.isConnected());
        m.put("peerContinuationQualified",r.qualified);m.put("originalPeerLogoutObserved",r.bLoggedOut);
        m.put("constructedBInRoster",r.constructed!=null&&r.server.getPlayerList().getPlayer(r.actor.getUUID())==r.constructed);
        m.put("sourceAtConstructor",r.sourceAtConstructor);m.put("sourceAfterOuterFinally",r.sourceAfterOuter);
        m.put("constructorWithdrawalMode",constructorMode()?(escapedMode()?"CALLBACK_ESCAPED":"PRISTINE_ASSOCIATION"):"NOT_SELECTED");
        m.put("actualConstructorAssociationSeams",r.constructorAssociations);m.put("actualCallbackEntries",r.constructorCallbacks);
        m.put("exactWithdrawalCheckedAfterOuter",r.withdrawalChecked);m.put("associationRestoredToRemovedA",r.associationRestored);
        m.put("removedARevived",false);m.put("newSourceOrCompleteBClaimed",false);
        m.put("dataDurabilityOrHealthyF0Claimed",false);m.put("parkingCatchClaimed",false);m.put("fullC4aAcceptance",false);return m;
    }
    private static long count(Connection c,P11C4aNativeObservations.Event event){return P11C4aNativeObservations.count(c,event);}
    private static void require(boolean value,String code){P11C4aEvidence.require(value,code);}
    private static final class Fault extends RuntimeException{Fault(){super("C4A_OWNED_CONFIG_CATCH_FIXTURE");}}
    private static final class Run{
        final MinecraftServer server;final ServerPlayer actor,peer;final Connection a,b;final Path output;final Fault fault=new Fault();
        final Object peerStats,peerAdvancements;final long peerEpoch;
        ServerConfigurationPacketListenerImpl config;ServerPlayer constructed;P11TransitionControl control;State running,finalState;
        Map<String,Object> sourceAtConstructor,sourceAfterOuter;String failure;
        P11QualifiedSourceOwner owner;P11QualifiedSourceOwner.Body previous;Object sourceAtAssociation;long oldEpoch;
        int constructorAssociations,constructorCallbacks;boolean withdrawalChecked,associationRestored;
        int stage,ticks,returnCalls,factoryReturns,injections,placeCalls,logEntries,logReturns,invalidSends,invalidSendReturns,disconnects,disconnectReturns,aLogouts,swings;
        boolean callerReturned,peerCue,qualified,bLoggedOut;
        Run(MinecraftServer s,ServerPlayer a,ServerPlayer b,Path o){server=s;actor=a;peer=b;this.a=a.connection.getConnection();this.b=b.connection.getConnection();output=o;
            peerStats=b.getStats();peerAdvancements=b.getAdvancements();peerEpoch=P11NativeStorageBoundary.diagnostics(s,b.getUUID()).sourceEpoch();}
    }
}
