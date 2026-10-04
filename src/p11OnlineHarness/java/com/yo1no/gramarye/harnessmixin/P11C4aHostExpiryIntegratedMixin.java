package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aHostExpiryProbe;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(IntegratedServer.class)
abstract class P11C4aHostExpiryIntegratedMixin {
    @Inject(method = "halt(Z)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void host$haltEntry(boolean wait, CallbackInfo callback) {
        P11C4aHostExpiryProbe.integratedHalt((IntegratedServer) (Object) this, wait, false);
    }
    @Inject(method = "halt(Z)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void host$haltReturn(boolean wait, CallbackInfo callback) {
        P11C4aHostExpiryProbe.integratedHalt((IntegratedServer) (Object) this, wait, true);
    }
}
