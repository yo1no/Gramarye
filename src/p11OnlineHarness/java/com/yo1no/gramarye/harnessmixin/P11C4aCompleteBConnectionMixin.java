package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yo1no.gramarye.P11C4aCompleteBFaultProbe;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the unchanged native connected catch, never substitute its policy or force close timing. */
@Mixin(ServerConnectionListener.class)
abstract class P11C4aCompleteBConnectionMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/Connection;setReadOnly()V"), require = 1, expect = 1, allow = 1)
    private void completeB$connectedCatch(Connection connection, Operation<Void> original, @Local Exception exception) {
        original.call(connection);
        P11C4aCompleteBFaultProbe.connectedCatchReturned((ServerConnectionListener) (Object) this, connection, exception);
    }

    @Inject(method = "tick()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void completeB$connectedTickReturned(CallbackInfo ci) {
        P11C4aCompleteBFaultProbe.connectedTickReturned((ServerConnectionListener) (Object) this);
    }
}
