package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.P11ClientTransitions;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class P11ClientKeyboardMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$activationAction(long window, int key, int scanCode, int action,
            int modifiers, CallbackInfo ci) {
        P11ClientTransitions.keyboardAction(window, key, action);
    }
}
