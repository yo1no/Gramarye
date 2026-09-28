package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.client.server.IntegratedPlayerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;

/** Client-dist only; common server bytecode never refers to this target. */
@Mixin(IntegratedPlayerList.class)
abstract class P11IntegratedPlayerListMixin {
    @WrapMethod(method = "save(Lnet/minecraft/server/level/ServerPlayer;)V", require = 1, expect = 1)
    private void p11$save(ServerPlayer player, Operation<Void> original) {
        P11NativeStorageBoundary.integratedSave((PlayerList) (Object) this, player, original);
    }
    @WrapOperation(method = "save(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"), require = 1, expect = 1)
    private CompoundTag p11$hostMaterial(ServerPlayer player, CompoundTag target,
            Operation<CompoundTag> original) {
        return P11NativeStorageBoundary.hostCacheMaterial((PlayerList) (Object) this, player, target, original);
    }

    @WrapOperation(method = "save(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/server/IntegratedPlayerList;playerData:Lnet/minecraft/nbt/CompoundTag;", opcode = Opcodes.PUTFIELD), require = 1, expect = 1)
    private void p11$publish(IntegratedPlayerList list, CompoundTag material, Operation<Void> original) {
        P11NativeStorageBoundary.hostCacheAssignment(list, material, original);
    }
}
