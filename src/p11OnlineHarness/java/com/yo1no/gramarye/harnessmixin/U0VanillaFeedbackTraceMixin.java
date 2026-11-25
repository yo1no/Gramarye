package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Only adopted closed translation keys, never rendered player text or component arguments. */
@Mixin(Gui.class)
abstract class U0VanillaFeedbackTraceMixin {
    @WrapMethod(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", require = 1, expect = 1, allow = 1)
    private void u0$feedback(Component component, boolean animateColor, Operation<Void> original) {
        long startedNanos = System.nanoTime();
        original.call(component, animateColor);
        long elapsedNanos = System.nanoTime() - startedNanos;
        try {
            if (component == null || !(component.getContents() instanceof TranslatableContents contents)) { return; }
            String observedKey = contents.getKey();
            if (observedKey == null) { return; }
            String key = switch (observedKey) {
                case "message.gramarye.cast.not_ready" -> "message.gramarye.cast.not_ready";
                case "message.gramarye.cast.cooldown_active" -> "message.gramarye.cast.cooldown_active";
                case "message.gramarye.cast.rate_limited" -> "message.gramarye.cast.rate_limited";
                case "message.gramarye.cast.unavailable" -> "message.gramarye.cast.unavailable";
                case "message.gramarye.cast.insufficient_mana" -> "message.gramarye.cast.insufficient_mana";
                default -> null;
            };
            if (key != null) {
                U0OrdinaryTrace.note("GUI_OVERLAY_KEY_RETURN", 0, 0, 0, 0, 0, 0, animateColor ? 1 : 0, 0, 0, key);
                U0OrdinaryTrace.note(elapsedNanos < 0 ? "INVALID_FEEDBACK_TIMING" : "FEEDBACK_GUI_NANOS",
                        elapsedNanos, 0, 0, 0, 0, 0, 0, 0, 0, key);
            }
        } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original GUI return is preserved. */ }
        }
    }
}
