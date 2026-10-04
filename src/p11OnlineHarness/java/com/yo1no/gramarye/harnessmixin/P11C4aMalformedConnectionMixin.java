package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aMalformedWireProbe;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(Connection.class)
abstract class P11C4aMalformedConnectionMixin {
    @Shadow private boolean handlingFault;
    @WrapMethod(method = "exceptionCaught(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Throwable;)V", require = 1, expect = 1, allow = 1)
    private void malformed$failure(ChannelHandlerContext context, Throwable failure, Operation<Void> original) {
        var connection = (Connection) (Object) this;
        P11C4aMalformedWireProbe.FailureCall token = null;
        try { token = P11C4aMalformedWireProbe.exceptionEntered(connection, failure, handlingFault); }
        catch (RuntimeException | Error observerFailure) { P11C4aMalformedWireProbe.exceptionObservationFailed(); }
        boolean normal = false;
        try { original.call(context, failure); normal = true; }
        finally {
            try { P11C4aMalformedWireProbe.exceptionEnded(token, normal); }
            catch (RuntimeException | Error observerFailure) { P11C4aMalformedWireProbe.exceptionObservationFailed(); }
        }
    }
}
