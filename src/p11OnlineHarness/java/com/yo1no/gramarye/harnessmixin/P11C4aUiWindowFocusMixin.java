package com.yo1no.gramarye.harnessmixin;

import com.mojang.blaze3d.platform.Window;
import com.yo1no.gramarye.P11C4aUiContextProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Window.class)
abstract class P11C4aUiWindowFocusMixin implements P11C4aUiContextProbe.FocusInput {
    @Invoker("onFocus")
    public abstract void c4a$nativeFocus(long window, boolean focused);
}
