package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aConfigCatchProbe;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=P11NativeStorageBoundary.class,remap=false)
abstract class P11C4aConfigCatchStorageMixin {
    @WrapMethod(method="configurationFinished(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",require=1,expect=1,allow=1)
    private static void configCatch$outer(ServerConfigurationPacketListenerImpl listener,ServerboundFinishConfigurationPacket packet,Operation<Void> body,Operation<Void> original){
        boolean normal=false;Throwable primary=null;
        try{original.call(listener,packet,body);normal=true;}
        catch(RuntimeException|Error failure){primary=failure;throw failure;}
        finally{P11C4aConfigCatchProbe.callerEnded(listener,normal,primary);}
    }
}
