package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityLookup.class)
abstract class P11EntityLookupMixin {
    @WrapOperation(method = "remove(Lnet/minecraft/world/level/entity/EntityAccess;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, expect = 1, allow = 1)
    private Object p11$exactUuid(Map<?, ?> values, Object key, Operation<Object> original,
            @Local(argsOnly = true) EntityAccess actor) {
        if (!(actor instanceof ServerPlayer)) { return original.call(values, key); }
        return values.get(key) == actor ? original.call(values, key) : null;
    }

    @WrapOperation(method = "remove(Lnet/minecraft/world/level/entity/EntityAccess;)V",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;remove(I)Ljava/lang/Object;"),
            require = 1, expect = 1, allow = 1)
    private Object p11$exactId(Int2ObjectMap<?> values, int key, Operation<Object> original,
            @Local(argsOnly = true) EntityAccess actor) {
        if (!(actor instanceof ServerPlayer)) { return original.call(values, key); }
        return values.get(key) == actor ? original.call(values, key) : null;
    }
}
