package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aHeldClientProbe;
import com.yo1no.gramarye.P11C4aErrorStatusClientProbe;
import com.yo1no.gramarye.P11C4aTerminalStatusClientProbe;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
/** Client array only; opaque package-private request stays the original encoder input. */
@Mixin(targets = "com.yo1no.gramarye.P11TransitionWire", remap = false)
abstract class P11C4aHeldWireMixin {
    @WrapMethod(method = "encodeRequest(Lnet/minecraft/network/FriendlyByteBuf;Lcom/yo1no/gramarye/P11TransitionProtocol$Request;)V", require = 1, expect = 1, allow = 1)
    private static void held$wire(FriendlyByteBuf buffer, @Coerce Object request, Operation<Void> original) {
        int start = buffer.writerIndex();
        original.call(buffer, request);
        P11C4aHeldClientProbe.encoded(buffer, start, request);
        P11C4aErrorStatusClientProbe.encoded(buffer, start, request);
        P11C4aTerminalStatusClientProbe.encoded(buffer, start, request);
    }
}
