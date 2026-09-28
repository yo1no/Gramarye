package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.P11NativePresence;
import com.yo1no.gramarye.P11NativeCleanup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class P11EntityPresenceMixin implements P11NativeCleanup.EntityCallbackState {
    @Shadow private EntityInLevelCallback levelCallback;

    @Override
    public boolean p11$hasRemovalCallback(EntityInLevelCallback expected) {
        return levelCallback == expected;
    }
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isPassenger()Z"),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private void p11$ridePresence(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> callback) {
        if (P11NativePresence.denied((Entity) (Object) this, "ride")) { callback.setReturnValue(false); }
    }

    @Inject(method = "moveTo(DDDFF)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$movePresence(double x, double y, double z, float yaw, float pitch, CallbackInfo callback) {
        P11NativePresence.require((Entity) (Object) this, "move");
    }
}
