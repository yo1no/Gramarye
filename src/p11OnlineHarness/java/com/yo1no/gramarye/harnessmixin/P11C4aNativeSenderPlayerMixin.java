package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aNativeSenderProbe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
abstract class P11C4aNativeSenderPlayerMixin {
    @WrapMethod(method = "doTick()V", require = 1, expect = 1, allow = 1)
    private void c4a$nativeHealthBeforeReload(Operation<Void> original) {
        var actor = (ServerPlayer) (Object) this;
        boolean selected = P11C4aNativeSenderProbe.beforeDoTick(actor);
        boolean normal = false;
        try { original.call(); normal = true; }
        finally { P11C4aNativeSenderProbe.afterDoTick(actor, selected, normal); }
    }

    @WrapOperation(method = "doTick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 3, expect = 3, allow = 3)
    private void c4a$observeNativeHealth(ServerGamePacketListenerImpl listener, Packet<?> packet, Operation<Void> original) {
        original.call(listener, packet);
        P11C4aNativeSenderProbe.healthSent((ServerPlayer) (Object) this, packet);
    }
}
