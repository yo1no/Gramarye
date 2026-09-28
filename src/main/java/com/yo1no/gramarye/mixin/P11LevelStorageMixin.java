package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.P11NativeWorldAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
abstract class P11LevelStorageMixin implements P11NativeWorldAccess.ReadStorage {
    @Unique private P11NativeWorldAccess.ReadWitness p11$witness;
    @Override public P11NativeWorldAccess.ReadWitness p11$readWitness() { return p11$witness; }
    @Override public void p11$readWitness(P11NativeWorldAccess.ReadWitness witness) {
        p11$witness = witness;
    }

    @WrapMethod(method = "getDataTag(Z)Lcom/mojang/serialization/Dynamic;")
    private Dynamic<?> p11$read(boolean fallback, Operation<Dynamic<?>> original) {
        return P11NativeStorageBoundary.readLevel(
                (LevelStorageSource.LevelStorageAccess) (Object) this, fallback, original);
    }

    @WrapMethod(method = "saveDataTag(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/storage/WorldData;Lnet/minecraft/nbt/CompoundTag;)V")
    private void p11$world(RegistryAccess registries, WorldData data, CompoundTag player,
            Operation<Void> original) {
        P11NativeStorageBoundary.saveWorld((LevelStorageSource.LevelStorageAccess) (Object) this,
                registries, data, player, original);
    }

    @WrapMethod(method = "saveLevelData(Lnet/minecraft/nbt/CompoundTag;)V")
    private void p11$root(CompoundTag root, Operation<Void> original) {
        P11NativeStorageBoundary.saveLevelRoot(
                (LevelStorageSource.LevelStorageAccess) (Object) this, root, original);
    }
}
