package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1OnlinePeerProbe;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets="com.yo1no.gramarye.P8PacketSubmission")
abstract class P11L1OnlinePeerP8Mixin {
    @WrapMethod(method="send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            require=1,expect=1,allow=1)
    private static void l1$originalP8Send(ServerPlayer recipient,CustomPacketPayload payload,Operation<Void> original) {
        boolean selected=P11L1OnlinePeerProbe.p8Selected(recipient,payload);Throwable primary=null;
        try { original.call(recipient,payload); }
        catch(RuntimeException|Error failure) { primary=failure;throw failure; }
        finally { P11L1OnlinePeerProbe.p8Finished(selected,primary); }
    }
}
