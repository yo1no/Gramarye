package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aNativeErrorProbe;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(MinecraftServer.class)
abstract class P11C4aNativeErrorServerMixin {
    @WrapOperation(method="runServer()V",at=@At(value="INVOKE",target="Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),require=4,expect=4,allow=4)
    private void error$serverCatch(Logger logger,String text,Throwable failure,Operation<Void> original) {
        P11C4aNativeErrorProbe.outer(failure,false);
        original.call(logger,text,failure);
    }
}
