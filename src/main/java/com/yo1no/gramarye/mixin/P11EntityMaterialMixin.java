package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
abstract class P11EntityMaterialMixin {
    @WrapMethod(method = "saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;")
    private CompoundTag p11$serialize(CompoundTag target, Operation<CompoundTag> original) {
        return P11NativeStorageBoundary.serialize((Entity) (Object) this, target, original);
    }
}
