package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aServerTerminalProbe;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class P11C4aServerTerminalMixin {
    // Locked bytecode has four calls: one run catch and three copies of the finally stop catch.
    @WrapOperation(method = "runServer()V", at = @At(value = "INVOKE",
            target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),
            require = 4, expect = 4, allow = 4)
    private void c4a$originalCatch(Logger logger, String message, Throwable failure, Operation<Void> original) {
        P11C4aServerTerminalProbe.caught((MinecraftServer) (Object) this,
                "Exception stopping the server".equals(message), failure);
        original.call(logger, message, failure);
    }

    @Inject(method = "halt(Z)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$originalHalt(boolean wait, CallbackInfo callback) {
        P11C4aServerTerminalProbe.halt((MinecraftServer) (Object) this, wait);
    }
}
