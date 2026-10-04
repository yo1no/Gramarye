package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aNativeErrorProbe;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(BlockableEventLoop.class)
abstract class P11C4aNativeErrorTaskMixin {
    @WrapOperation(method="doRunTask(Ljava/lang/Runnable;)V",at=@At(value="INVOKE",
            target="Lorg/slf4j/Logger;error(Lorg/slf4j/Marker;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),require=1,expect=1,allow=1)
    private void error$taskCatch(Logger logger,Marker marker,String text,Object name,Object failure,Operation<Void> original) {
        if(failure instanceof Throwable throwable) { P11C4aNativeErrorProbe.outer(throwable,true); }
        original.call(logger,marker,text,name,failure);
    }
}
