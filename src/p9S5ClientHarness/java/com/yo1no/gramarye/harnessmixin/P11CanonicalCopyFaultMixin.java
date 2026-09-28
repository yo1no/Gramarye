package com.yo1no.gramarye.harnessmixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11CanonicalAdvancements;
import com.yo1no.gramarye.P11NativeCanonicalProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Excluded one-shot failure after actual codec copying, before original native reload mutation. */
@Mixin(value = P11CanonicalAdvancements.class, remap = false)
abstract class P11CanonicalCopyFaultMixin {
    @WrapMethod(method = "copy(Lcom/mojang/serialization/Codec;Ljava/lang/Object;)Ljava/lang/Object;",
            require = 1, expect = 1, allow = 1)
    private static Object p11$measureCopy(Codec<?> codec, Object input, Operation<Object> original) {
        var measurement = P11NativeCanonicalProbe.beginCopy(input);
        boolean normal = false;
        try { var result = original.call(codec, input); normal = true; return result; }
        finally { P11NativeCanonicalProbe.endCopy(measurement, normal); }
    }

    @WrapOperation(method = "copy(Lcom/mojang/serialization/Codec;Ljava/lang/Object;)Ljava/lang/Object;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"),
            require = 1, expect = 1, allow = 1)
    private static DataResult<Object> p11$encodedSize(Codec<Object> codec, DynamicOps<Object> ops,
            Object input, Operation<DataResult<Object>> original) {
        var result = original.call(codec, ops, input);
        result.result().ifPresent(P11NativeCanonicalProbe::copyEncoded);
        return result;
    }

    @Inject(method = "copy(Lcom/mojang/serialization/Codec;Ljava/lang/Object;)Ljava/lang/Object;",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void p11$afterActualCopy(Codec<?> codec, Object input,
            CallbackInfoReturnable<Object> callback) {
        P11NativeCanonicalProbe.afterCopy(input, callback.getReturnValue());
    }
}
