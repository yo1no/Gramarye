package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aMalformedWireProbe;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.P11ClientTransitionDispatch", remap = false)
abstract class P11C4aMalformedClientStateMixin {
    @Inject(method = "handle(Lcom/yo1no/gramarye/P11TransitionProtocol$State;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void malformed$state(@Coerce Object state, Connection connection, ICommonPacketListener listener, CallbackInfo callback) {
        P11C4aMalformedWireProbe.downstream(state, connection);
    }
}
