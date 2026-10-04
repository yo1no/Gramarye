package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aConfigParkingResetClientProbe;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Client-array only; composes with existing PacketUtils wrappers without replacing their tasks. */
@Mixin(PacketUtils.class)
abstract class P11C4aConfigParkingFinishMixin {
    @WrapOperation(method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/BlockableEventLoop;executeIfPossible(Ljava/lang/Runnable;)V"),
            require = 1, expect = 1, allow = 1)
    private static void reset$actualTask(BlockableEventLoop<?> executor, Runnable task, Operation<Void> original,
            Packet<?> packet, PacketListener listener, BlockableEventLoop<?> owner) {
        original.call(executor, P11C4aConfigParkingResetClientProbe.scheduled(packet, listener, owner, task));
    }
}
