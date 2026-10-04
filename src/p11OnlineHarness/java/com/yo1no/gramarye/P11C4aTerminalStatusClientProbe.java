package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.connection.ConnectionUtils;

/** Original UI sender/periodic STATUS only; the omission and one n→n+1 body edit are named faults. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aTerminalStatusClientProbe {
    private static volatile Run active;
    private static final ThreadLocal<Encode> ENCODE=new ThreadLocal<>();
    private static int awaitTicks;
    private P11C4aTerminalStatusClientProbe() { }
    static boolean allowsLogin(Connection connection,String role,int logins) {
        var r=active; return r!=null && r.handoff && r.parkingStarted && !r.parkingDone
                && connection==r.connection && role.equals("a") && logins==1;
    }
    static boolean tick(Minecraft minecraft,Connection connection,String role,Path output,Path serverOutput) throws IOException {
        require(minecraft.isSameThread() && P11C4aTerminalStatusProbe.selected() && ++awaitTicks<=24000,"TERMINAL_STATUS_CLIENT_OWNER");
        if(active==null) {
            if(!P11C4aEvidence.cuePresent(serverOutput,role+"-terminal-status-arm.ready")) { return false; }
            require(java.util.List.of("a","b").contains(role) && minecraft.player!=null && minecraft.level!=null
                    && connection.isConnected() && !connection.isMemoryConnection(),"TERMINAL_STATUS_CLIENT_REAL_PLAY");
            active=new Run(connection,role,output,serverOutput,minecraft.player);
            P11C4aEvidence.write(output,"terminal-status-armed.json",Map.of("status","OBSERVERS_ARMED_NOT_ACCEPTANCE","role",role));
        }
        var r=active;
        require(r.connection==connection && ++r.ticks<=2400 && r.failure==null,"TERMINAL_STATUS_CLIENT_FAILED");
        if(r.leaving) {
            if(connection.isConnected() || minecraft.level!=null || minecraft.player!=null) { return false; }
            P11C4aEvidence.write(output,"terminal-status-client-terminal.json",Map.of("originalDisconnectObserved",true,
                    "leaveCallback",r.role.equals("a")&&!r.handoff,"role",r.role)); return true;
        }
        require(connection.isConnected(),"TERMINAL_STATUS_CLIENT_EARLY_CLOSE");
        if(role.equals("b")) {
            require(minecraft.player==r.initial && minecraft.player.isAlive(),"TERMINAL_STATUS_CLIENT_PEER_CHANGED");
            if(P11C4aEvidence.cuePresent(serverOutput,"b-terminal-status-leave.ready")) { normalLeave(minecraft,r); }
            return false;
        }
        r.window=P11C4aEvidence.cuePresent(serverOutput,"a-terminal-status-window.ready");
        if(r.handoff) {
            if(!r.parkingStarted) {
                if(!P11C4aEvidence.cuePresent(serverOutput,"a-parking-arm.ready")) { return false; }
                r.parkingStarted=true; P11C4aParkingClientProbe.start(minecraft,connection,role,output,serverOutput);
            }
            if(!r.parkingDone) { r.parkingDone=P11C4aParkingClientProbe.tick(minecraft); }
        } else if(!r.clicked) {
            var state=P11ClientTransitions.view();
            if(state!=null && state.kind()==Kind.DEATH && state.scope()==Scope.PLAY && state.outcome()==Outcome.BINDING) {
                r.clicked=P11C4aClientInputProbe.act(minecraft,P11C4aClientInputProbe.Action.DEATH_RESPAWN);
            }
            return false;
        }
        if(!r.reported && P11C4aEvidence.receiptPresent(serverOutput,"terminal-status-server.json")
                && (!r.handoff || r.parkingDone)) {
            var state=P11ClientTransitions.view();
            require(r.request!=null && r.tryEncodes==1 && r.same==1 && state!=null
                    && matches(r.request,state) && state.outcome()==Outcome.COMPLETED
                    && minecraft.player!=null && minecraft.player!=r.initial && minecraft.player.isAlive()
                    && minecraft.player.connection.getConnection()==connection
                    && (r.handoff ? r.mutations==0 && r.omissions==0 && !P11ClientTransitions.blocksCast()
                                  : r.mutations==1 && r.omissions==1 && r.controller!=null && r.statusSamples>=2
                                    && unpaired(r.controller,r) && nativeFrames(r)==r.beforeFrames+1),"TERMINAL_STATUS_CLIENT_MISSING_NATIVE_FACTS");
            var values=new LinkedHashMap<String,Object>();
            values.put("status","ACTUAL_ORIGINAL_STATUS_ENCODINGS_AND_NATIVE_FRAME_COMPONENT_NEGATIVE");
            values.put("handoff",r.handoff); values.put("actualRequest",r.request); values.put("actualCompleted",state);
            values.put("tryEncodes",r.tryEncodes); values.put("sameStatusEncodes",r.same); values.put("differentSequenceBodyMutations",r.mutations);
            values.put("exactControllerMarkerOmissions",r.omissions); values.put("nativeRespawnHandlerDelta",nativeFrames(r)-r.beforeFrames);
            values.put("originalPeriodicStatusSamples",r.statusSamples); values.put("pendingAtReadout",r.controller!=null&&r.controller.pending());
            values.put("blocksCastAtReadout",P11ClientTransitions.blocksCast()); values.put("actualNewAliveLocalPlayer",true);
            values.put("markerFault",r.handoff?"NONE":"ONE_EXACT_NATIVE_FRAME_CONTROLLER_DELIVERY_OMITTED_NATIVE_RESPAWN_UNCHANGED");
            values.put("differentSequence",r.handoff?"NOT_IN_THIS_CASE":"ONE_ORIGINAL_36_BYTE_STATUS_BODY_N_TO_N_PLUS_ONE");
            values.put("normalHiddenHistoryClaimed",false); values.put("newSenderInvoked",false); values.put("controllerFieldsWritten",false);
            values.put("fullC4aAcceptance",false);
            P11C4aEvidence.write(output,"terminal-status-client.json",values); r.reported=true;
        }
        if(!r.reported || !P11C4aEvidence.cuePresent(serverOutput,"a-terminal-status-leave.ready")) { return false; }
        if(r.handoff) { normalLeave(minecraft,r); return false; }
        require(unpaired(r.controller,r),"TERMINAL_STATUS_PENDING_CLEARED_BEFORE_LEAVE");
        if(!r.leaveOpened) { r.leaveOpened=P11C4aClientInputProbe.act(minecraft,P11C4aClientInputProbe.Action.STATUS_ESCAPE); }
        else if(P11C4aClientInputProbe.act(minecraft,P11C4aClientInputProbe.Action.LEAVE_CONFIRM)) { r.leaving=true; }
        return false;
    }
    /** Called before the original controller receiver only; never skips packet/native Respawn handlers. */
    public static boolean omit(Object value,Connection connection,ICommonPacketListener listener) {
        var r=active;
        if(r==null || r.handoff || !r.role.equals("a") || connection!=r.connection
                || !(value instanceof State state) || state.outcome()!=Outcome.NATIVE_FRAME) { return false; }
        require(Minecraft.getInstance().isSameThread() && connection.isConnected() && connection.getPacketListener()==listener
                && listener.getConnection()==connection && r.clicked && r.request!=null && matches(r.request,state)
                && r.omissions==0 && nativeFrames(r)==r.beforeFrames,"TERMINAL_STATUS_OMISSION_NOT_EXACT");
        r.omissions=1; return true;
    }
    /** Reads only the actual existing controller at the original periodic status() return. */
    public static void status(Object controller,Object value) {
        var r=active;
        if(r==null || r.handoff || !r.role.equals("a") || !r.window || !(value instanceof Request request)
                || !(controller instanceof P11ClientTransitionState actual) || request.command()!=Command.STATUS) { return; }
        if(!matches(r.request,actual.current()) || actual.current().outcome()!=Outcome.COMPLETED) { return; }
        require(same(r.request,request) && request.requestSeq()==r.request.requestSeq() && unpaired(actual,r),"TERMINAL_STATUS_ACTUAL_CONTROLLER_CHANGED");
        if(r.controller==null) { r.controller=actual; } else { require(r.controller==actual,"TERMINAL_STATUS_CONTROLLER_REPLACED"); }
        require(++r.statusSamples<=40,"TERMINAL_STATUS_EXCESS_PERIODIC_STATUS");
    }
    public static Object begin(ChannelHandlerContext context,ConnectionProtocol protocol,Packet<?> packet) {
        var r=active;
        if(r==null || !r.role.equals("a") || r.reported || protocol!=ConnectionProtocol.PLAY
                || ConnectionUtils.getConnection(context)!=r.connection || !(packet instanceof ServerboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionRequestPayload)) { return null; }
        var e=new Encode(r,ENCODE.get()); ENCODE.set(e); return e;
    }
    public static void encoded(FriendlyByteBuf buffer,int start,Object value) {
        var e=ENCODE.get(); if(e==null || !(value instanceof Request request)) { return; }
        var r=e.run;
        // Task/ENTER_CONFIG n0 frames remain wholly native and are not this experiment.
        if(request.kind()!=(r.handoff?Kind.RETURN_TO_WORLD:Kind.DEATH) || request.requestSeq()==0) { e.ignored=true; return; }
        if(e.request!=null || buffer.writerIndex()-start!=36 || buffer.getUnsignedByte(start)!=1
                || request.scope()!=(r.handoff?Scope.PREPLAY:Scope.PLAY) || request.requestSeq()==Long.MAX_VALUE) {
            fail(r,"TERMINAL_STATUS_ENCODER_SHAPE"); return;
        }
        e.request=request;
        if(request.command()==Command.TRY) { if(r.request!=null) { fail(r,"TERMINAL_STATUS_SECOND_CLIENT_TRY"); } return; }
        if(!r.window || r.same==1 && (r.handoff || r.mutations==1)) { return; }
        if(!same(r.request,request) || request.requestSeq()!=r.request.requestSeq()
                || !r.handoff && (r.controller==null || r.statusSamples==0)) { fail(r,"TERMINAL_STATUS_ENCODER_NOT_REAL_PERIODIC"); return; }
        if(r.same==0) { e.same=true; }
        else { buffer.setLong(start+26,request.requestSeq()+1); e.mutated=true; }
    }
    public static void end(Object value,boolean normal) {
        if(!(value instanceof Encode e)) { return; }
        if(e.previous==null) { ENCODE.remove(); } else { ENCODE.set(e.previous); }
        var r=e.run;
        if(!normal || !e.ignored && e.request==null) { fail(r,"TERMINAL_STATUS_ORIGINAL_ENCODER_THROW_OR_MISSING"); return; }
        if(e.ignored) { return; }
        if(e.request.command()==Command.TRY) { r.request=e.request; r.tryEncodes++; }
        if(e.same) { r.same++; } if(e.mutated) { r.mutations++; }
    }
    private static boolean unpaired(P11ClientTransitionState state,Run r) {
        return state!=null && matches(r.request,state.current()) && state.current().outcome()==Outcome.COMPLETED
                && state.pending() && !state.settled() && state.blocksCast() && !state.retryArmed()
                && state.frameTarget()==0 && state.boundActor()==r.request.actorGeneration()
                && state.request()==r.request.requestSeq() && P11ClientTransitions.view()==state.current()
                && P11ClientTransitions.blocksCast() && !P11ClientTransitions.retryArmed();
    }
    private static void normalLeave(Minecraft minecraft,Run r) {
        require(minecraft.level!=null,"TERMINAL_STATUS_NORMAL_LEAVE_NO_WORLD");
        minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); r.leaving=true;
    }
    private static long nativeFrames(Run r) { return P11C4aNativeObservations.count(r.connection,P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN); }
    private static boolean same(Request a,Request b) { return a!=null && a.scope()==b.scope() && a.connectionEpoch()==b.connectionEpoch()
            && a.sceneSerial()==b.sceneSerial() && a.actorGeneration()==b.actorGeneration() && a.kind()==b.kind(); }
    private static boolean matches(Request a,State b) { return a!=null && b!=null && a.scope()==b.scope() && a.connectionEpoch()==b.connectionEpoch()
            && a.sceneSerial()==b.sceneSerial() && a.actorGeneration()==b.actorGeneration() && a.kind()==b.kind() && a.requestSeq()==b.requestSeq(); }
    static void release() { active=null; ENCODE.remove(); P11C4aParkingClientProbe.release(); }
    private static void fail(Run r,String code) { if(r.failure==null) { r.failure=code; } }
    private static void require(boolean ok,String code) { P11C4aEvidence.require(ok,code); }
    private static final class Run {
        final Connection connection; final String role; final Path output,serverOutput; final LocalPlayer initial;
        final boolean handoff; final long beforeFrames;
        volatile Request request; volatile String failure; volatile boolean window,reported; volatile int tryEncodes,same,mutations,omissions,statusSamples;
        volatile P11ClientTransitionState controller; int ticks; boolean clicked,parkingStarted,parkingDone,leaveOpened,leaving;
        Run(Connection c,String role,Path o,Path s,LocalPlayer a) { connection=c;this.role=role;output=o;serverOutput=s;initial=a;
            handoff=P11C4aTerminalStatusProbe.handoffMode();beforeFrames=nativeFrames(this); }
    }
    private static final class Encode { final Run run; final Encode previous; Request request; boolean same,mutated,ignored;
        Encode(Run r,Encode p) {run=r;previous=p;} }
}
