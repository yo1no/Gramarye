package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aHostLeaveProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.P11ClientLeaveScreen", remap = false)
abstract class P11C4aHostLeaveScreenMixin {
    @Inject(method = "leave()V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void host$leaveEntry(CallbackInfo callback) { P11C4aHostLeaveProbe.nativeLeave(false); }
    @Inject(method = "leave()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void host$leaveReturn(CallbackInfo callback) { P11C4aHostLeaveProbe.nativeLeave(true); }
}
