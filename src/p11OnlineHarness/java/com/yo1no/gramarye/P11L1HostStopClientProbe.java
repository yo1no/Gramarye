package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

/** Callback input into the original host menu and P9 sender, not physical OS input. */
public final class P11L1HostStopClientProbe {
    private static Run active;
    private static boolean ready;
    private static long mana,cooldown;
    private P11L1HostStopClientProbe() { }
    public static void inputReady(boolean value){if(P11L1HostStopProbe.selected())ready=value;}
    public static void metadata(boolean isMana,long sequence){if(!P11L1HostStopProbe.selected())return;if(isMana)mana=sequence;else cooldown=sequence;}
    public static void submitted(long sequence,int slot,int mask,boolean hintsAbsent){
        var r=active;if(r==null)return;
        require(r.host && r.armed && sequence>r.lastSequence && slot==0 && mask==0 && hintsAbsent
                && r.sends+1==r.casts,"ORIGINAL_HOST_P9_SEND");
        r.armed=false;r.sends++;r.lastSequence=sequence;
    }
    /** Called with the existing parent's nativeCall=true, before its generic disconnect assertion. */
    static boolean tick(Minecraft mc,Connection connection,String role,Path output)throws IOException{
        require(P11L1HostStopProbe.selected() && mc.isSameThread(),"CLIENT_SELECTION");
        var r=active;
        if(r==null){
            require(connection!=null && connection.isConnected() && mc.player!=null && mc.level!=null
                    && mc.getConnection()!=null && mc.getConnection().getConnection()==connection
                    && (role.equals("host") || role.equals("b")),"CLIENT_ARM");
            active=r=new Run(connection,role,output,mc.getSingleplayerServer());
            require(r.host==connection.isMemoryConnection() && (r.host?r.retained!=null:r.retained==null && connection.isEncrypted()),"CLIENT_TOPOLOGY");
        }
        require(++r.ticks<=2400,"CLIENT_FINITE_WINDOW");
        if(r.quitReturned){
            require(r.host && !r.connection.isConnected() && r.retained.isShutdown() && mc.player==null && mc.level==null
                    && mc.getConnection()==null && mc.getSingleplayerServer()==null && mc.screen instanceof TitleScreen
                    && P11L1HostStopProbe.complete(),"HOST_ORIGINAL_QUIT_TERMINAL");
            if(!r.terminalWritten){write(r,"ORIGINAL_HOST_MENU_QUIT_RETURN_WITH_DATA_PROOF");r.terminalWritten=true;}
            return P11C4aEvidence.receiptPresent(r.serverOutput.getParent().resolve("client-b"),"host-l1-terminal.json");
        }
        if(!r.connection.isConnected()){
            require(!r.host && r.swings==1,"EARLY_NATIVE_CLOSE");
            if(mc.player!=null || mc.level!=null || mc.getConnection()!=null
                    || !P11C4aEvidence.receiptPresent(r.serverOutput,"host-l1-stopped.json"))return false;
            if(!r.terminalWritten){write(r,"AUTHENTICATED_PEER_NATIVE_TERMINAL_AFTER_HOST_QUIT");r.terminalWritten=true;}
            return true;
        }
        require(mc.player!=null && mc.getConnection()!=null && mc.getConnection().getConnection()==r.connection,"CURRENT_CONNECTION");
        if(!r.host){
            if(r.swings==0 && cue(r,"host-l1-peer-swing.ready") && mc.screen==null){
                mc.player.swing(InteractionHand.MAIN_HAND);r.swings++;
                P11C4aEvidence.cue(r.output,"host-l1-peer-swing-return.ready");
            }return false;
        }
        if(!r.starter && cue(r,"host-l1-starter.ready") && mc.screen==null){
            mc.getConnection().sendCommand("gramarye starter");r.starter=true;
            P11C4aEvidence.cue(r.output,"host-l1-starter-sent.ready");return false;
        }
        if(r.casts<2 && cue(r,"host-l1-cast-"+(r.casts+1)+".ready") && mc.screen==null){
            if(!r.focusRequested){GLFW.glfwFocusWindow(mc.getWindow().getWindow());r.focusRequested=true;return false;}
            if(!mc.isWindowActive() || !ready || mana<=0 || cooldown<=0 || mc.getOverlay()!=null)return false;
            require(r.starter && !r.armed && r.sends==r.casts,"CAST_COUNT");r.armed=true;r.casts++;
            key(mc,GLFW.GLFW_KEY_R);r.focusRequested=false;return false;
        }
        if(r.casts==2 && r.sends==2 && cue(r,"host-l1-quit.ready")){
            if(mc.getOverlay()!=null)return false;
            if(mc.screen==null){key(mc,GLFW.GLFW_KEY_ESCAPE);return false;}
            if(!(mc.screen instanceof PauseScreen pause) || !pause.showsPauseMenu())return false;
            require(!mc.isPaused() && mc.getSingleplayerServer()==r.retained && r.retained.isPublished(),"PUBLISHED_MENU_NOT_WORLD_PAUSE");
            Button selected=null;
            for(var child:pause.children())if(child instanceof Button b && b.visible && b.active
                    && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("menu.returnToMenu")){
                require(selected==null,"UNIQUE_NATIVE_QUIT_BUTTON");selected=b;
            }
            if(selected==null)return false;
            require(mc.mouseHandler instanceof P11C4aClientInputProbe.MouseInput,"ORIGINAL_MOUSE_HOOK");
            P11L1HostStopProbe.quitIntent(r.retained);
            var mouse=(P11C4aClientInputProbe.MouseInput)mc.mouseHandler;var window=mc.getWindow();
            mouse.p11$move(window.getWindow(),(selected.getX()+selected.getWidth()/2.0)*window.getScreenWidth()/window.getGuiScaledWidth(),
                    (selected.getY()+selected.getHeight()/2.0)*window.getScreenHeight()/window.getGuiScaledHeight());
            mouse.p11$press(window.getWindow(),GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_PRESS,0);
            mouse.p11$press(window.getWindow(),GLFW.GLFW_MOUSE_BUTTON_LEFT,GLFW.GLFW_RELEASE,0);
            require(!r.connection.isConnected() && r.retained.isShutdown() && mc.getSingleplayerServer()==null
                    && mc.player==null && mc.level==null && mc.screen instanceof TitleScreen,"ORIGINAL_MENU_QUIT_RETURN");
            r.quitReturned=true;
        }return false;
    }
    private static void key(Minecraft mc,int key){long window=mc.getWindow().getWindow();int scan=GLFW.glfwGetKeyScancode(key);mc.keyboardHandler.keyPress(window,key,scan,GLFW.GLFW_PRESS,0);mc.keyboardHandler.keyPress(window,key,scan,GLFW.GLFW_RELEASE,0);}
    private static boolean cue(Run r,String leaf)throws IOException{return P11C4aEvidence.cuePresent(r.serverOutput,leaf);}
    private static void write(Run r,String status)throws IOException{var f=new LinkedHashMap<String,Object>();f.put("status",status);f.put("role",r.host?"host":"b");f.put("originalP9Sends",r.sends);f.put("originalRCallbacks",r.casts);f.put("peerOriginalSwingReturns",r.swings);f.put("originalMenuQuitReturned",r.quitReturned);f.put("connectionClosed",!r.connection.isConnected());f.put("hostOnlineHasJoinedClaim",false);f.put("physicalOsInputClaim",false);P11C4aEvidence.write(r.output,"host-l1-terminal.json",f);}
    private static void require(boolean v,String code){P11C4aEvidence.require(v,"L1_HOST_"+code);}
    private static final class Run{final Connection connection;final boolean host;final Path output,serverOutput;final IntegratedServer retained;int ticks,casts,sends,swings;long lastSequence;boolean starter,armed,focusRequested,quitReturned,terminalWritten;Run(Connection c,String role,Path o,IntegratedServer s){connection=c;host=role.equals("host");output=o;serverOutput=o.getParent().resolve("server");retained=s;}}
}
