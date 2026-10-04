package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11LivePlayAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** World-owned boss sets may still contain an actor after its native PLAY retirement. */
@Mixin(ServerBossEvent.class)
abstract class P11ServerBossEventMixin {
    @WrapOperation(method = "broadcast(Ljava/util/function/Function;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$broadcast(ServerGamePacketListenerImpl listener, Packet<?> packet, Operation<Void> original) {
        p11$sendToActiveListener(listener, packet, original);
    }

    @WrapOperation(method = "addPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$addPlayer(ServerGamePacketListenerImpl listener, Packet<?> packet, Operation<Void> original) {
        p11$sendToActiveListener(listener, packet, original);
    }

    @WrapOperation(method = "removePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$removePlayer(ServerGamePacketListenerImpl listener, Packet<?> packet, Operation<Void> original) {
        p11$sendToActiveListener(listener, packet, original);
    }

    @WrapOperation(method = "setVisible(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$setVisible(ServerGamePacketListenerImpl listener, Packet<?> packet, Operation<Void> original) {
        p11$sendToActiveListener(listener, packet, original);
    }

    @Unique
    private static void p11$sendToActiveListener(ServerGamePacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original) {
        // A prior End actor may share this listener with the later actor that entered CONFIG.
        // Do not require the boss-set recipient to equal listener.player; the exact listener retired.
        if (!((P11LivePlayAccess) listener).p11$configurationActorRetired()) {
            original.call(listener, packet);
        }
    }
}
