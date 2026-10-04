package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aPreplayChatClientProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.DebugSampleSubscriber;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(DebugSampleSubscriber.class)
abstract class P11C4aPreplayChatDebugMixin {
    @Inject(method="tick()V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void chat$actualDebugTick(CallbackInfo callback) { P11C4aPreplayChatClientProbe.actorFunction(2); }
    @WrapOperation(method="sendSubscriptionRequestIfNeeded(Lnet/minecraft/util/debugchart/RemoteDebugSampleType;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1,expect=1,allow=1)
    private void chat$nativeDebug(ClientPacketListener listener,Packet<?> packet,Operation<Void> original) {
        P11C4aPreplayChatClientProbe.send(listener,packet,original);
    }
}
