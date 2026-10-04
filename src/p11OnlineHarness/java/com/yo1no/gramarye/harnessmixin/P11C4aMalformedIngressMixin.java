package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aMalformedWireProbe;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionBoundary", remap = false)
abstract class P11C4aMalformedIngressMixin {
    @Inject(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void malformed$ingress(@Coerce Object request, Connection connection, ICommonPacketListener listener, CallbackInfo callback) {
        P11C4aMalformedWireProbe.downstream(request, connection);
    }
}
