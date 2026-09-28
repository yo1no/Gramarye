package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11NativeCanonicalProbe;
import com.yo1no.gramarye.P11NativeDeliveryProbe;
import com.yo1no.gramarye.P11NativeRewardProbe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Excluded one-shot fault at the real transport entry, not a fake listener or packet consumer. */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11CanonicalSendFaultMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1)
    private void p11$sendFault(Packet<?> packet, CallbackInfo callback) {
        P11NativeDeliveryProbe.beforeSend((ServerCommonPacketListenerImpl) (Object) this, packet);
        P11NativeCanonicalProbe.beforeSend((ServerCommonPacketListenerImpl) (Object) this, packet);
        P11NativeRewardProbe.beforeRecipeSend((ServerCommonPacketListenerImpl) (Object) this, packet);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"),
            require = 1, expect = 1, allow = 1)
    private void p11$submitted(Packet<?> packet, CallbackInfo callback) {
        P11NativeCanonicalProbe.afterSend((ServerCommonPacketListenerImpl) (Object) this, packet);
        P11NativeDeliveryProbe.afterSend((ServerCommonPacketListenerImpl) (Object) this, packet);
        P11NativeRewardProbe.afterRecipeSend((ServerCommonPacketListenerImpl) (Object) this, packet);
    }
}
