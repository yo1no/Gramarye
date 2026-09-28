package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.P11NativePresence;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class P11PlayerSlotMixin {
    @Inject(method = "getSlot(I)Lnet/minecraft/world/entity/SlotAccess;", at = @At("RETURN"),
            cancellable = true, require = 4, expect = 4, allow = 4)
    private void p11$menuSlot(int index, CallbackInfoReturnable<SlotAccess> callback) {
        callback.setReturnValue(P11NativePresence.slot((Player) (Object) this, index, callback.getReturnValue()));
    }
}
