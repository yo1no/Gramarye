package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aNativeSenderClientProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
abstract class P11C4aNativeSenderMinecraftMixin {
    @WrapMethod(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", require = 1, expect = 1, allow = 1)
    private void c4a$hiddenScreenDepth(Screen requested, Operation<Void> original) {
        Object token = P11C4aNativeSenderClientProbe.hiddenScreenEntering();
        try { original.call(requested); }
        finally { P11C4aNativeSenderClientProbe.hiddenScreenLeaving(token); }
    }

    @WrapOperation(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;respawn()V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$hiddenNativeSender(LocalPlayer player, Operation<Void> original) {
        P11C4aNativeSenderClientProbe.hidden(player, false);
        original.call(player);
        P11C4aNativeSenderClientProbe.hidden(player, true);
    }
}
