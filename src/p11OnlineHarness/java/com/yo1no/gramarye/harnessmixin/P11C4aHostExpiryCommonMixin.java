package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aHostExpiryProbe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aHostExpiryCommonMixin implements P11C4aHostExpiryProbe.Fields {
    @Shadow private boolean keepAlivePending;
    @Shadow private long keepAliveChallenge;
    @Override public boolean expiry$pending() { return keepAlivePending; }
    @Override public long expiry$challenge() { return keepAliveChallenge; }

    @WrapOperation(method = "keepConnectionAlive()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;isSingleplayerOwner()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean host$nativeOwner(ServerCommonPacketListenerImpl listener, Operation<Boolean> original) {
        boolean owner = original.call(listener);
        P11C4aHostExpiryProbe.owner(listener, owner);
        return owner;
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void host$sendEntry(Packet<?> packet, CallbackInfo callback) {
        P11C4aHostExpiryProbe.sent((ServerCommonPacketListenerImpl) (Object) this, packet, false);
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void host$sendReturn(Packet<?> packet, CallbackInfo callback) {
        P11C4aHostExpiryProbe.sent((ServerCommonPacketListenerImpl) (Object) this, packet, true);
    }
    @WrapMethod(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V", require = 1, expect = 1, allow = 1)
    private void host$nativeAck(ServerboundKeepAlivePacket packet, Operation<Void> original) {
        var listener = (ServerCommonPacketListenerImpl) (Object) this;
        long selected = P11C4aHostExpiryProbe.beforeAck(listener, packet.getId());
        original.call(packet);
        P11C4aHostExpiryProbe.afterAck(listener, selected);
    }
    @WrapOperation(method = "onDisconnect(Lnet/minecraft/network/DisconnectionDetails;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;halt(Z)V"), require = 1, expect = 1, allow = 1)
    private void host$originalHaltCause(MinecraftServer server, boolean wait, Operation<Void> original) {
        var listener = (ServerCommonPacketListenerImpl) (Object) this;
        P11C4aHostExpiryProbe.haltCause(listener, server, wait, false);
        original.call(server, wait);
        P11C4aHostExpiryProbe.haltCause(listener, server, wait, true);
    }
}
