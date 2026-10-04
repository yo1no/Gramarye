package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11ClientTransitions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class P11ClientPlayerMixin {
    @WrapMethod(method = "respawn()V", require = 1, expect = 1, allow = 1)
    private void p11$respawnIntent(Operation<Void> original) {
        if (!P11ClientTransitions.replaceRespawn((LocalPlayer) (Object) this)) {
            original.call();
        }
    }

    @WrapOperation(method = "handleConfusionTransitionEffect(Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;isPauseScreen()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$preserveStatus(Screen screen, Operation<Boolean> original) {
        return original.call(screen) || P11ClientTransitions.protectScreen(screen);
    }

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true,
            require = 1, expect = 1, allow = 1)
    private void p11$noStalePreplayTick(CallbackInfo ci) {
        if (!P11ClientTransitions.allowActorFunctions(((LocalPlayer) (Object) this).connection)) {
            ci.cancel();
        }
    }
}
