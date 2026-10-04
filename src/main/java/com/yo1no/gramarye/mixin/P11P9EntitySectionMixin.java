package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11P9TrackingCleanup;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PersistentEntitySectionManager.class)
abstract class P11P9EntitySectionMixin<T extends EntityAccess> {
    @Shadow @Final private EntitySectionStorage<T> sectionStorage;

    @WrapMethod(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$trackingTerminals(ChunkPos position, Visibility visibility, Operation<Void> original) {
        P11P9TrackingCleanup.chunkStatus(sectionStorage, position, visibility, original);
    }
}
