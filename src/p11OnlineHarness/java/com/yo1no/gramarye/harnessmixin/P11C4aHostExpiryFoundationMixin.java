package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aHostExpiryProbe;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only observations around the original private exact-server stop. */
@Mixin(targets = "com.yo1no.gramarye.P11FoundationService", remap = false)
abstract class P11C4aHostExpiryFoundationMixin {
    @Inject(method = "stopExact(Lnet/minecraft/server/MinecraftServer;)V", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1)
    private void expiry$foundationEntry(MinecraftServer server, CallbackInfo ci) {
        P11C4aHostExpiryProbe.foundationStopping(this, server);
    }

    @Inject(method = "stopExact(Lnet/minecraft/server/MinecraftServer;)V", at = @At("TAIL"),
            require = 1, expect = 1, allow = 1)
    private void expiry$foundationReturn(MinecraftServer server, CallbackInfo ci) {
        P11C4aHostExpiryProbe.foundationStopped(this, server);
    }
}
