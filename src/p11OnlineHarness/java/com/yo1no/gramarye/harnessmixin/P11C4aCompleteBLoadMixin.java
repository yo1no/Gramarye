package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aCompleteBFaultProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Excluded read-only exact native load counter; no cancellation, data or receipt mutation. */
@Mixin(Entity.class)
abstract class P11C4aCompleteBLoadMixin {
    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$completeBLoad(CompoundTag input, CallbackInfo callback) {
        P11C4aCompleteBFaultProbe.loaded((Entity) (Object) this);
    }
}
