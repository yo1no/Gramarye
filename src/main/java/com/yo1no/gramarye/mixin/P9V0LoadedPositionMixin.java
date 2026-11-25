package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11P9TrackingCleanup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the prospective locked NeoForge post-move chunk request has its result discarded. */
@Mixin(Entity.class)
abstract class P9V0LoadedPositionMixin implements EntityAccess {
    @WrapOperation(method = "setPosRaw(DDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"),
            require = 1, expect = 1, allow = 1)
    private LevelChunk v0$guardPositionChunk(Level world, int chunkX, int chunkZ, Operation<LevelChunk> original) {
        return P11P9TrackingCleanup.guardV0PositionChunk(this, world, chunkX, chunkZ)
                ? null : original.call(world, chunkX, chunkZ);
    }
}
