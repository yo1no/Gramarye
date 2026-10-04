package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aOldTickProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.yo1no.gramarye.P11LiveTransitionService",remap=false)
abstract class P11C4aOldTickGateMixin {
    @Inject(method="gate(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)Lcom/yo1no/gramarye/P11TransitionControl$Gate;",at=@At("RETURN"),require=1,expect=1,allow=1)
    private void oldTick$gate(@Coerce Object entry,CallbackInfoReturnable<Object> ci) { P11C4aOldTickProbe.gate(entry,ci.getReturnValue()); }
    @Inject(method="execute(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Lcom/yo1no/gramarye/P11TransitionControl$Drain;)V",at=@At("TAIL"),require=1,expect=1,allow=1)
    private void oldTick$executeReturned(@Coerce Object entry,@Coerce Object drain,CallbackInfo ci) { P11C4aOldTickProbe.executeReturned(entry); }
}
