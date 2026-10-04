package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1HostStopProbe;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(targets="com.yo1no.gramarye.P11NativeStorageBoundary")
abstract class P11L1HostStopBoundaryMixin {
    @WrapMethod(method="normalLogout(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/network/DisconnectionDetails;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",require=1,expect=1,allow=1)
    private static void l1$logout(ServerGamePacketListenerImpl listener,DisconnectionDetails details,Operation<Void> caller,Operation<Void> original){
        var actor=listener.player;
        P11L1HostStopProbe.boundary(actor.getServer(),actor,false,false,false);
        boolean normal=false;
        try{original.call(listener,details,caller);normal=true;}
        finally{P11L1HostStopProbe.boundary(actor.getServer(),actor,false,true,normal);}
    }
}
