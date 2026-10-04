package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aNativeErrorProbe;
import java.util.UUID;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.yo1no.gramarye.P11LiveTransitionService$Entry",remap=false)
abstract class P11C4aNativeErrorEntryMixin implements P11C4aNativeErrorProbe.EntryView {
    @Shadow @Final private Connection connection;
    @Unique private Object error$control;
    @Inject(method="<init>(Lnet/minecraft/network/Connection;Ljava/util/UUID;Lcom/yo1no/gramarye/P11TransitionControl;Lcom/yo1no/gramarye/P11ControlBudgets$FairDispatcher$Member;)V",at=@At("RETURN"),require=1,expect=1,allow=1)
    private void error$constructed(Connection connection, UUID id, @Coerce Object control, @Coerce Object member, CallbackInfo ci) { error$control=control; }
    public Connection error$connection() { return connection; }
    public Object error$control() { return error$control; }
}
