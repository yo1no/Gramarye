package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aConfigCatchProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="com.yo1no.gramarye.P11LiveTransitionService",remap=false)
abstract class P11C4aConfigCatchLiveMixin {
    @Inject(method="gate(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)Lcom/yo1no/gramarye/P11TransitionControl$Gate;",
            at=@At("RETURN"),require=1,expect=1,allow=1)
    private void configCatch$gate(@Coerce Object entry,CallbackInfoReturnable<?> cir){P11C4aConfigCatchProbe.gate(entry);}
}
