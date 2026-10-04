package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aTerminalStatusClientProbe;
import com.yo1no.gramarye.P11ClientTransitions;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
/** Client distribution only. The sole omission is a labelled controller-delivery fault. */
@Mixin(value=P11ClientTransitions.class,remap=false)
abstract class P11C4aTerminalClientMixin {
    @WrapMethod(method="handle(Lcom/yo1no/gramarye/P11TransitionProtocol$State;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V",require=1,expect=1,allow=1)
    private static void terminal$marker(@Coerce Object state,Connection connection,ICommonPacketListener listener,Operation<Void> original) {
        if(P11C4aTerminalStatusClientProbe.omit(state,connection,listener)) { return; }
        original.call(state,connection,listener);
    }
    @WrapOperation(method="tick(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Post;)V",
            at=@At(value="INVOKE",target="Lcom/yo1no/gramarye/P11ClientTransitionState;status()Lcom/yo1no/gramarye/P11TransitionProtocol$Request;"),require=1,expect=1,allow=1)
    @Coerce private static Object terminal$status(@Coerce Object state,Operation<Object> original) {
        Object result=original.call(state);
        P11C4aTerminalStatusClientProbe.status(state,result);
        return result;
    }
}
