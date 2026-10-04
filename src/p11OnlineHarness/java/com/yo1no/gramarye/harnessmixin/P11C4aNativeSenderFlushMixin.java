package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aNativeSenderProbe;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aNativeSenderFlushMixin {
    @Inject(method = "resumeFlushing()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void senders$originalFlushReturned(CallbackInfo callback) {
        P11C4aNativeSenderProbe.flushed((ServerCommonPacketListenerImpl) (Object) this);
    }
}
