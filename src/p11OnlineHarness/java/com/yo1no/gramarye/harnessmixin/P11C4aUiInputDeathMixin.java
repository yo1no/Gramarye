package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aUiInputProbe;
import com.yo1no.gramarye.P11C4aUiHeldInputProbe;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
abstract class P11C4aUiInputDeathMixin {
    @Shadow private int delayTicker;
    @Inject(method = "tick()V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void c4a$exactUnlock(CallbackInfo callback) {
        P11C4aUiInputProbe.deathTick((DeathScreen) (Object) this, delayTicker);
        P11C4aUiHeldInputProbe.deathTick((DeathScreen) (Object) this, delayTicker);
    }
}
