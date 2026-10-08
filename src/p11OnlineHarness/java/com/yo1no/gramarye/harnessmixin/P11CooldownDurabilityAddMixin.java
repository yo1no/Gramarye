package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownDurabilityProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
abstract class P11CooldownDurabilityAddMixin {
    @WrapMethod(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", require = 1, expect = 1, allow = 1)
    private boolean p11$durabilityActualAdd(Entity entity, Operation<Boolean> original) {
        boolean result = false; Throwable primary = null;
        try { result = original.call(entity); return result; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11CooldownDurabilityProbe.addReturned(entity, result, primary); }
    }
}
