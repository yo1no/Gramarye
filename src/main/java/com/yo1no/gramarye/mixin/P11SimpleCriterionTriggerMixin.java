package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import com.yo1no.gramarye.P11CanonicalAdvancements;
import java.util.function.Predicate;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps the original A predicate and captured listener traversal unchanged. */
@Mixin(SimpleCriterionTrigger.class)
abstract class P11SimpleCriterionTriggerMixin {
    @WrapMethod(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Predicate;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$capturedGeneration(ServerPlayer actor, Predicate<?> predicate, Operation<Void> original) {
        var scope = P11CanonicalAdvancements.beginCallbacks(actor.getAdvancements());
        try { original.call(actor, predicate); }
        finally { P11CanonicalAdvancements.endCallbacks(scope); }
    }

    @WrapOperation(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/CriterionTrigger$Listener;run(Lnet/minecraft/server/PlayerAdvancements;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$cause(CriterionTrigger.Listener<?> listener, PlayerAdvancements advancements,
            Operation<Void> original, @Local(argsOnly = true) ServerPlayer actor) {
        var scope = P11CanonicalAdvancements.beginListener(advancements);
        try { P11NativeOperationBoundary.criterion(actor, listener, advancements, original); }
        finally { P11CanonicalAdvancements.endListener(scope); }
    }
}
