package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aPortalClientProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe exactly one original scheduled call; never invoke a private consumer from a fixture. */
@Mixin(LocalPlayer.class)
abstract class P11C4aPortalPlayerMixin {
    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;handleConfusionTransitionEffect(Z)V"),
            require = 1, expect = 1, allow = 1)
    private void portal$confusion(LocalPlayer actor, boolean value, Operation<Void> original) {
        Object token = P11C4aPortalClientProbe.beforeConfusion(actor, value);
        boolean normal = false;
        try { original.call(actor, value); normal = true; }
        finally { P11C4aPortalClientProbe.afterConfusion(token, normal); }
    }

    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;processPortalCooldown()V"),
            require = 1, expect = 1, allow = 1)
    private void portal$cooldown(LocalPlayer actor, Operation<Void> original) {
        int before = actor.getPortalCooldown();
        boolean normal = false;
        try { original.call(actor); normal = true; }
        finally { P11C4aPortalClientProbe.cooldown(actor, before, normal); }
    }

    @WrapOperation(method = "handleConfusionTransitionEffect(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"),
            require = 1, expect = 1, allow = 1)
    private void portal$close(Minecraft minecraft, Screen next, Operation<Void> original) {
        P11C4aPortalClientProbe.portalClose(next, false);
        original.call(minecraft, next);
        P11C4aPortalClientProbe.portalClose(next, true);
    }
}
