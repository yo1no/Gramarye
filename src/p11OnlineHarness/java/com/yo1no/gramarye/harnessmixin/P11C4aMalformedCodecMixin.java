package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aMalformedWireProbe;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets = "com.yo1no.gramarye.P11TransitionWire", remap = false)
abstract class P11C4aMalformedCodecMixin {
    @WrapMethod(method = "encodeRequest(Lnet/minecraft/network/FriendlyByteBuf;Lcom/yo1no/gramarye/P11TransitionProtocol$Request;)V", require = 1, expect = 1, allow = 1)
    private static void malformed$request(FriendlyByteBuf buffer, @Coerce Object value, Operation<Void> original) {
        int start = buffer.writerIndex(); original.call(buffer, value);
        P11C4aMalformedWireProbe.encoded(buffer, value, start, 36);
    }
    @WrapMethod(method = "encodeState(Lnet/minecraft/network/FriendlyByteBuf;Lcom/yo1no/gramarye/P11TransitionProtocol$State;)V", require = 1, expect = 1, allow = 1)
    private static void malformed$state(FriendlyByteBuf buffer, @Coerce Object value, Operation<Void> original) {
        int start = buffer.writerIndex(); original.call(buffer, value);
        P11C4aMalformedWireProbe.encoded(buffer, value, start, 54);
    }
    @WrapMethod(method = "decodeRequest(Lnet/minecraft/network/FriendlyByteBuf;)Lcom/yo1no/gramarye/P11TransitionProtocol$Request;", require = 1, expect = 1, allow = 1)
    @Coerce
    private static Object malformed$readRequest(FriendlyByteBuf buffer, Operation<Object> original) {
        boolean selected = P11C4aMalformedWireProbe.decodeEntered(buffer, 36); Throwable primary = null;
        try { return original.call(buffer); }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11C4aMalformedWireProbe.decodeEnded(selected, primary); }
    }
    @WrapMethod(method = "decodeState(Lnet/minecraft/network/FriendlyByteBuf;)Lcom/yo1no/gramarye/P11TransitionProtocol$State;", require = 1, expect = 1, allow = 1)
    @Coerce
    private static Object malformed$readState(FriendlyByteBuf buffer, Operation<Object> original) {
        boolean selected = P11C4aMalformedWireProbe.decodeEntered(buffer, 54); Throwable primary = null;
        try { return original.call(buffer); }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11C4aMalformedWireProbe.decodeEnded(selected, primary); }
    }
}
