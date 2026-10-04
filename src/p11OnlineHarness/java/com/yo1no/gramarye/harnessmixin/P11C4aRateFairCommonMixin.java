package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRateFairProbe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aRateFairCommonMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void rf$state(Packet<?> packet, CallbackInfo ci) {
        try { P11C4aRateFairProbe.sent((ServerCommonPacketListenerImpl) (Object) this, packet); }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
    }
}
