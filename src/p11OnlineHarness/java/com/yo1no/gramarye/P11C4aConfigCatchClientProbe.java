package com.yo1no.gramarye;
import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Observe original CONFIG and native disconnect; B supplies one ordinary surviving-peer action. */
@OnlyIn(Dist.CLIENT)
final class P11C4aConfigCatchClientProbe {
    private static Run active;
    private P11C4aConfigCatchClientProbe() {}
    static boolean tick(Minecraft minecraft,Connection connection,String role,Path output,Path serverOutput) throws IOException {
        require(minecraft.isSameThread() && role.matches("[ab]"),"CONFIG_CATCH_CLIENT_OWNER");
        if(active==null){
            if(!P11C4aEvidence.cuePresent(serverOutput,role+"-config-catch-arm.ready"))return false;
            require(connection!=null && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection()
                    && minecraft.player!=null && minecraft.player.isAlive() && minecraft.level!=null
                    && minecraft.getConnection()!=null && minecraft.getConnection().getConnection()==connection,
                    "CONFIG_CATCH_CLIENT_REAL_PLAY");
            active=new Run(connection,minecraft.player,role);
            P11C4aEvidence.write(output,"config-catch-armed.json",Map.of("status","ACTUAL_INITIAL_PLAY_ARMED_NOT_ACCEPTANCE","role",role));
            return false;
        }
        var r=active;require(r.connection==connection && r.role.equals(role) && ++r.ticks<=2400,"CONFIG_CATCH_CLIENT_DEADLINE");
        if(role.equals("b")){
            if(r.departed)return minecraft.getConnection()==null && minecraft.player==null && minecraft.level==null;
            require(connection.isConnected() && minecraft.player==r.originalPlayer && minecraft.player.isAlive()
                    && minecraft.level!=null && minecraft.getConnection()!=null && minecraft.getConnection().getConnection()==connection
                    && count(connection,P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN)==1
                    && count(connection,P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN)==0,"CONFIG_CATCH_PEER_CLIENT_CHANGED");
            if(!r.swung && P11C4aEvidence.cuePresent(serverOutput,"b-config-catch-action.ready")){
                r.swung=true;minecraft.player.swing(InteractionHand.MAIN_HAND);
            }
            if(P11C4aEvidence.cuePresent(serverOutput,"b-config-catch-finish.ready")){
                require(r.swung,"CONFIG_CATCH_PEER_CLIENT_NO_ACTION");r.departed=true;
                P11C4aEvidence.write(output,"config-catch-peer-alive.json",Map.of("status","SAME_ORIGINAL_PLAY_PEER_ALIVE_AFTER_NATIVE_CONFIG_CATCH","originalSwingIssued",true));
                minecraft.level.disconnect();minecraft.disconnect(new TitleScreen());
            }
            return false;
        }
        if(connection.isConnected()){
            var state=P11ClientTransitions.view();
            if(!r.configReported && state!=null && state.scope()==Scope.CONFIG && state.kind()==Kind.ENTER_CONFIG
                    && state.outcome()==Outcome.COMPLETED){
                require(connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                        && minecraft.player==null && minecraft.level==null,"CONFIG_CATCH_CLIENT_CONFIG_TERMINAL");
                r.configReported=true;
                P11C4aEvidence.write(output,"config-catch-config-terminal.json",Map.of("status","INDEPENDENT_NATIVE_ENTER_CONFIG_COMPLETED","actualState",state));
            }
            return false;
        }
        if(!(minecraft.screen instanceof DisconnectedScreen) || minecraft.player!=null || minecraft.level!=null)return false;
        if(!P11C4aEvidence.receiptPresent(serverOutput,"config-catch-native.json"))return false;
        require(r.configReported && count(connection,P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN)==1
                && count(connection,P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN)==0,"CONFIG_CATCH_CLIENT_UNEXPECTED_FRAME");
        P11C4aEvidence.write(output,"config-catch-client-terminal.json",Map.of("status","ACTUAL_NATIVE_DISCONNECTED_SCREEN_CLOSED_CONNECTION_NULL_PLAYER_AND_WORLD",
                "initialLoginHandlerReturns",1,"successorLoginOrRespawnHandlerReturns",0,"lastFaultPayloadRequired",false,"fullC4aAcceptance",false));
        return true;
    }
    static void release(){active=null;}
    private static long count(Connection c,P11C4aNativeObservations.Event e){return P11C4aNativeObservations.count(c,e);}
    private static void require(boolean value,String code){P11C4aEvidence.require(value,code);}
    private static final class Run{
        final Connection connection;final LocalPlayer originalPlayer;final String role;int ticks;boolean configReported,swung,departed;
        Run(Connection c,LocalPlayer p,String r){connection=c;originalPlayer=p;role=r;}
    }
}
