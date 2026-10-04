package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1NaturalUnloadProbe;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(P11NativeStorageBoundary.class)
abstract class P11L1UnloadLogoutMixin {
    @WrapMethod(method = "normalLogout(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/network/DisconnectionDetails;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",
            require = 1, expect = 1, allow = 1)
    private static void l1$wholeLogout(ServerGamePacketListenerImpl listener, DisconnectionDetails details,
            Operation<Void> nativeOriginal, Operation<Void> original) {
        boolean selected=P11L1NaturalUnloadProbe.logoutEntering(listener); Throwable primary=null;
        try { original.call(listener,details,nativeOriginal); }
        catch(RuntimeException|Error failure) { primary=failure; throw failure; }
        finally { P11L1NaturalUnloadProbe.logoutFinished(selected,primary); }
    }
}
