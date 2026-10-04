package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aFirstTryClientProbe;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P11ClientTransitions", remap = false)
abstract class P11C4aFirstTryClientMixin {
    @Inject(method = "handle(Lcom/yo1no/gramarye/P11TransitionProtocol$State;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$firstTryState(@Coerce Object state, Connection connection,
            ICommonPacketListener listener, CallbackInfo callback) {
        P11C4aFirstTryClientProbe.accepted(state, connection);
    }

    @Inject(method = "send(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$firstTrySend(@Coerce Object request, CallbackInfo callback) {
        P11C4aFirstTryClientProbe.submitted(request);
    }
}
