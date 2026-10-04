package com.yo1no.gramarye;
import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.Connection;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.*;
@OnlyIn(Dist.CLIENT)
public final class P11C4aSubmissionClientProbe {
    private static Run active;
    private P11C4aSubmissionClientProbe() { }
    static void start(Minecraft minecraft,Connection connection,Path output) throws IOException {
        require(active==null && minecraft.isSameThread() && connection.isConnected() && connection.isEncrypted()
                && minecraft.player!=null && minecraft.player.isAlive() && minecraft.level!=null
                && minecraft.getConnection()!=null && minecraft.getConnection().getConnection()==connection,"SUBMIT_CLIENT_FIRST_PLAY");
        active=new Run(connection,output);
        P11C4aEvidence.write(output,"submission-armed.json",Map.of("status","ARMED_ORIGINAL_DEATH_BUTTON_ONLY"));
    }
    static boolean tick(Minecraft minecraft) throws IOException {
        var r=active;require(r!=null && minecraft.isSameThread() && r.failure==null && ++r.ticks<=2400,"SUBMIT_CLIENT_DEADLINE");
        var state=P11ClientTransitions.view();
        if(!r.clicked && state!=null && state.scope()==Scope.PLAY && state.kind()==Kind.DEATH
                && state.outcome()==Outcome.BINDING && state.requestSeq()==0) {
            r.clicked=P11C4aClientInputProbe.act(minecraft,P11C4aClientInputProbe.Action.DEATH_RESPAWN);
        }
        if(r.connection.isConnected() || !(minecraft.screen instanceof DisconnectedScreen)
                || minecraft.player!=null || minecraft.level!=null)return false;
        require(r.clicked && r.sends==1 && r.request!=null && r.completed==0 && r.frames==0
                && P11C4aNativeObservations.count(r.connection,P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN)==0,
                "SUBMIT_CLIENT_FALSE_COMPLETION_OR_NATIVE_BODY");
        if(!r.sealed) {
            r.sealed=true;
            P11C4aEvidence.write(r.output,"submission-client.json",Map.of(
                    "status","ACTUAL_ORIGINAL_CONNECTION_CLOSED_NO_RESPAWN_FRAME",
                    "request",r.request,"originalTrySendReturns",r.sends,"matchingCompleted",r.completed,
                    "matchingNativeFrame",r.frames,"nativeDisconnectedScreen",true,
                    "nativePlayerAndLevelAbsent",true,"nativeShutdownCauseInferred",false));
        }
        return true;
    }
    static void peer(Minecraft minecraft,Connection connection,Path output,Path serverOutput) throws IOException {
        if(!connection.isConnected())return;
        require(minecraft.player!=null && minecraft.player.isAlive() && minecraft.level!=null
                && minecraft.getConnection()!=null && minecraft.getConnection().getConnection()==connection
                && P11C4aNativeObservations.count(connection,P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN)==0,
                "SUBMIT_PEER_NATIVE_PLAY");
        if(P11C4aEvidence.cuePresent(serverOutput,"b-submission-swing.ready")
                && !P11C4aEvidence.receiptPresent(output,"submission-peer-swing.json")) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            P11C4aEvidence.write(output,"submission-peer-swing.json",Map.of(
                    "status","ORIGINAL_NATIVE_SWING_CALLED_SERVER_RETURN_REQUIRED_SEPARATELY",
                    "controlIsolationClaimed",false));
        }
    }
    public static void sent(Object raw) {
        var r=active;if(r==null || !(raw instanceof Request request) || request.command()!=Command.TRY)return;
        if(request.kind()!=Kind.DEATH || request.scope()!=Scope.PLAY || ++r.sends!=1)r.failure="SUBMIT_CLIENT_EXTRA_TRY";
        r.request=request;
    }
    public static void received(Object raw,Connection connection) {
        var r=active;if(r==null || connection!=r.connection || !(raw instanceof State s) || r.request==null)return;
        var q=r.request;
        if(s.connectionEpoch()!=q.connectionEpoch() || s.sceneSerial()!=q.sceneSerial()
                || s.actorGeneration()!=q.actorGeneration() || s.requestSeq()!=q.requestSeq()
                || s.kind()!=q.kind() || s.scope()!=q.scope())return;
        if(s.outcome()==Outcome.COMPLETED)r.completed++;
        if(s.outcome()==Outcome.NATIVE_FRAME)r.frames++;
    }
    static void release(){active=null;}
    private static void require(boolean b,String c){P11C4aEvidence.require(b,c);}
    private static final class Run {
        final Connection connection;final Path output;Request request;String failure;
        int ticks,sends,completed,frames;boolean clicked,sealed;
        Run(Connection connection,Path output){this.connection=connection;this.output=output;}
    }
}
