package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aUiHeldInputProbe;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class P11C4aUiHeldKeyboardMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$nativeKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo callback) {
        P11C4aUiHeldInputProbe.keyEvent(window, key, action);
    }
}
