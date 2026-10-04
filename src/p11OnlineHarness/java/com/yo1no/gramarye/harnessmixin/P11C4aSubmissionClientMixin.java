package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aSubmissionClientProbe;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.yo1no.gramarye.P11ClientTransitions",remap=false)
abstract class P11C4aSubmissionClientMixin {
    @Inject(method="send(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;)V",at=@At("TAIL"),require=1,expect=1,allow=1)
    private static void submit$send(@Coerce Object request,CallbackInfo ci){P11C4aSubmissionClientProbe.sent(request);}
    @Inject(method="handle(Lcom/yo1no/gramarye/P11TransitionProtocol$State;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V",at=@At("TAIL"),require=1,expect=1,allow=1)
    private static void submit$state(@Coerce Object state,Connection connection,ICommonPacketListener listener,CallbackInfo ci){P11C4aSubmissionClientProbe.received(state,connection);}
}
