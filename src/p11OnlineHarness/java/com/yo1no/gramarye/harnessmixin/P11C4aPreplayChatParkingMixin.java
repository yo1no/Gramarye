package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aPreplayChatProbe;
import com.yo1no.gramarye.P11ParkingPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=P11ParkingPacketListener.class,remap=false)
abstract class P11C4aPreplayChatParkingMixin {
    @WrapMethod(method="handleChatSessionUpdate(Lnet/minecraft/network/protocol/game/ServerboundChatSessionUpdatePacket;)V",require=1,expect=1,allow=1)
    private void chat$dropChat(ServerboundChatSessionUpdatePacket packet,Operation<Void> original) {
        var listener=(P11ParkingPacketListener)(Object)this; long before=P11C4aPreplayChatProbe.beforeDrop(listener); boolean normal=false;
        try { original.call(packet); normal=true; } finally { P11C4aPreplayChatProbe.afterDrop(listener,before,true,normal); }
    }
    @WrapMethod(method="handleDebugSampleSubscription(Lnet/minecraft/network/protocol/game/ServerboundDebugSampleSubscriptionPacket;)V",require=1,expect=1,allow=1)
    private void chat$dropDebug(ServerboundDebugSampleSubscriptionPacket packet,Operation<Void> original) {
        var listener=(P11ParkingPacketListener)(Object)this; long before=P11C4aPreplayChatProbe.beforeDrop(listener); boolean normal=false;
        try { original.call(packet); normal=true; } finally { P11C4aPreplayChatProbe.afterDrop(listener,before,false,normal); }
    }
}
