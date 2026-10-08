package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.magic.network.P11CooldownInputObservation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Client-only observation of the unchanged real HUD draw, not a mirror/render inference. */
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7CooldownHud")
abstract class P11CooldownHudMixin {
    @WrapOperation(method = "lambda$register$0(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
            require = 1, expect = 1, allow = 1)
    private static int p11$originalCooldownDraw(GuiGraphics graphics, Font font, Component text,
            int x, int y, int color, boolean shadow, Operation<Integer> original) {
        int result = original.call(graphics, font, text, x, y, color, shadow);
        P11CooldownInputObservation.drawn(text);
        return result;
    }
}
