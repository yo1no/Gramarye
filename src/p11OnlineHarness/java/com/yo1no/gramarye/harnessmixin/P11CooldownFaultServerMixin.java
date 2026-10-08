package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11CooldownFaultProbe;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
abstract class P11CooldownFaultServerMixin {
    @WrapOperation(method = "runServer()V", at = @At(value = "INVOKE",
            target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"), require = 4, expect = 4, allow = 4)
    private void p11$cooldownOriginalOuter(Logger logger, String message, Throwable primary, Operation<Void> original) {
        P11CooldownFaultProbe.nativeOuter((MinecraftServer) (Object) this, "Exception stopping the server".equals(message), primary);
        original.call(logger, message, primary);
    }
}
