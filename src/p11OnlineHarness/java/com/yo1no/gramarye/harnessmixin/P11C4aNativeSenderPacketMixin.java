package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aNativeSenderClientProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class P11C4aNativeSenderPacketMixin {
    @WrapOperation(method = "handlePlayerCombatKill(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;respawn()V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$immediateNativeSender(LocalPlayer player, Operation<Void> original) {
        P11C4aNativeSenderClientProbe.immediate(player, false);
        original.call(player);
        P11C4aNativeSenderClientProbe.immediate(player, true);
    }

    @Inject(method = "handleSetHealth(Lnet/minecraft/network/protocol/game/ClientboundSetHealthPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$healthReturned(ClientboundSetHealthPacket packet, CallbackInfo callback) {
        P11C4aNativeSenderClientProbe.healthApplied((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleGameEvent(Lnet/minecraft/network/protocol/game/ClientboundGameEventPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER),
            require = 1, expect = 1, allow = 1)
    private void c4a$actualGameEvent(ClientboundGameEventPacket packet, CallbackInfo callback) {
        P11C4aNativeSenderClientProbe.gameEvent((ClientPacketListener) (Object) this, packet);
    }
}
