package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11KeepAliveBoundary;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No general listener setter: this write requires the private, live same-PLAY transfer. */
@Mixin(Connection.class)
abstract class P11KeepAliveConnectionMixin implements P11KeepAliveBoundary.ConnectionAccess {
    @Shadow private volatile PacketListener packetListener;
    @Unique private final P11KeepAliveBoundary.Guard p11$keepAlive = new P11KeepAliveBoundary.Guard();

    @Override
    public P11KeepAliveBoundary.Guard p11$keepAliveGuard() { return p11$keepAlive; }

    @Override
    public void p11$installParkedGame(P11KeepAliveBoundary.Transfer transfer) {
        packetListener = P11KeepAliveBoundary.transferTarget(transfer, (Connection) (Object) this);
    }

    @WrapOperation(method = "setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/network/Connection;packetListener:Lnet/minecraft/network/PacketListener;", opcode = Opcodes.PUTFIELD),
            require = 1, expect = 1, allow = 1)
    private void p11$nativePhaseField(Connection connection, PacketListener listener, Operation<Void> original) {
        var install = P11KeepAliveBoundary.beginProtocolInstall(connection, listener);
        try { original.call(connection, listener); }
        finally { P11KeepAliveBoundary.endProtocolInstall(install); }
    }

    @Inject(method = "doSendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void p11$currentScheduledChallenge(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo callback) {
        if (packet instanceof ClientboundKeepAlivePacket keepAlive
                && !P11KeepAliveBoundary.allowScheduledKeepAlive((Connection) (Object) this, keepAlive.getId())) {
            callback.cancel();
        }
    }
}
