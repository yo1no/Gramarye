package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1CapacityWorkProbe;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.yo1no.gramarye.P9StarterCommand")
abstract class P11L1CapacityStarterMixin {
    @Inject(method = "provision(Lnet/minecraft/commands/CommandSourceStack;)I", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$l1StarterReturned(CommandSourceStack source, CallbackInfoReturnable<Integer> callback) {
        P11L1CapacityWorkProbe.starterReturned(source, callback.getReturnValue());
    }
}
