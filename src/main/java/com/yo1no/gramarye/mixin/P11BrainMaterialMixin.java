package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class P11BrainMaterialMixin {
    @WrapOperation(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;serializeStart(Lcom/mojang/serialization/DynamicOps;)Lcom/mojang/serialization/DataResult;"), require = 1, expect = 1)
    private DataResult<Tag> p11$brain(Brain<?> brain, DynamicOps<Tag> ops,
            Operation<DataResult<Tag>> original) {
        var result = original.call(brain, ops);
        P11NativeStorageBoundary.brainEncoded((LivingEntity) (Object) this,
                result.error().isEmpty() && result.result().isPresent());
        return result;
    }
}
