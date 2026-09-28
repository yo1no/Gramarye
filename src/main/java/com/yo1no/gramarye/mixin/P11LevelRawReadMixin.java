package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelStorageSource.class)
abstract class P11LevelRawReadMixin {
    @WrapOperation(method = "readLevelDataTagFixed(Ljava/nio/file/Path;Lcom/mojang/datafixers/DataFixer;)Lcom/mojang/serialization/Dynamic;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelStorageSource;readLevelDataTagRaw(Ljava/nio/file/Path;)Lnet/minecraft/nbt/CompoundTag;"), require = 1, expect = 1)
    private static CompoundTag p11$read(Path path, Operation<CompoundTag> original) {
        var root = original.call(path);
        P11NativeStorageBoundary.levelReadRoot(root);
        return root;
    }
}
