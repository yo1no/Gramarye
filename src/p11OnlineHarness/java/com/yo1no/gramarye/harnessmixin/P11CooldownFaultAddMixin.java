package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11CooldownFaultProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
abstract class P11CooldownFaultAddMixin {
    @WrapOperation(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;onAddedToLevel()V"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownOriginalAdded(Entity entity, Operation<Void> original) {
        original.call(entity);
        P11CooldownFaultProbe.afterAdded((ServerLevel) (Object) this, entity);
    }
    @WrapMethod(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", require = 1, expect = 1, allow = 1)
    private boolean p11$cooldownOriginalAdd(Entity entity, Operation<Boolean> original) {
        boolean result = false; Throwable primary = null;
        try { result = original.call(entity); return result; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11CooldownFaultProbe.addReturned(entity, result, primary); }
    }
}
