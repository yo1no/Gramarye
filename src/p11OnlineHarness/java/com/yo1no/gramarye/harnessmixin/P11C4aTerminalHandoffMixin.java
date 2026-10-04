package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aTerminalStatusProbe;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets="com.yo1no.gramarye.P11LiveTransitionService",remap=false)
abstract class P11C4aTerminalHandoffMixin {
    @WrapMethod(method="finish(Lcom/yo1no/gramarye/P11LiveTransitionService$Ticket;ZLjava/lang/Throwable;)V",require=1,expect=1,allow=1)
    private void terminal$finish(@Coerce Object ticket,boolean normal,Throwable primary,Operation<Void> original) throws Throwable {
        var scope=P11C4aTerminalStatusProbe.finishing(this); Throwable escaping=null;
        try { original.call(ticket,normal,primary); }
        catch(Throwable failure) { escaping=failure; throw failure; }
        finally { P11C4aTerminalStatusProbe.finishReturned(scope,escaping); }
    }
    @WrapOperation(method="finish(Lcom/yo1no/gramarye/P11LiveTransitionService$Ticket;ZLjava/lang/Throwable;)V",
            at=@At(value="INVOKE",target="Lcom/yo1no/gramarye/P11TransitionControl;beginHandoff(Lcom/yo1no/gramarye/P11TransitionControl$Listener;Lcom/yo1no/gramarye/P11TransitionProtocol$Scope;)Ljava/util/Optional;"),require=1,expect=1,allow=1)
    private Optional<?> terminal$begin(@Coerce Object owner,@Coerce Object listener,@Coerce Object target,Operation<Optional<?>> original) {
        return P11C4aTerminalStatusProbe.handoff(owner,listener,target,original);
    }
    @WrapOperation(method="finish(Lcom/yo1no/gramarye/P11LiveTransitionService$Ticket;ZLjava/lang/Throwable;)V",
            at=@At(value="INVOKE",target="Lcom/yo1no/gramarye/P11TransitionControl;completeHandoff(Lcom/yo1no/gramarye/P11TransitionControl$Handoff;)Ljava/util/Optional;"),require=1,expect=1,allow=1)
    private Optional<?> terminal$complete(@Coerce Object owner,@Coerce Object reservation,Operation<Optional<?>> original) {
        return P11C4aTerminalStatusProbe.complete(owner,reservation,original);
    }
}
