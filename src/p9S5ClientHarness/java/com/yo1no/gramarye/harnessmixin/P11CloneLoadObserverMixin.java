package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11NativeCloneProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Excluded exact native read counter, no branch, cancellation, input or receipt mutation. */
@Mixin(Entity.class)
abstract class P11CloneLoadObserverMixin {
    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1)
    private void p11$nativeLoad(CompoundTag input, CallbackInfo callback) {
        P11NativeCloneProbe.loaded((Entity) (Object) this);
    }
}
