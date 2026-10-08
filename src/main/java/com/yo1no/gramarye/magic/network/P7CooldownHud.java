package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncReason;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client-only readout. It neither advances time nor participates in input/admission. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P7CooldownHud {
    private P7CooldownHud() {}

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "slot0_cooldown"), (graphics, delta) -> {
                    var minecraft = Minecraft.getInstance();
                    if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) { return; }
                    var text = label(P7ClientLifecycleEvents.mirror().cooldownSnapshot());
                    graphics.drawString(minecraft.font, text, 8, graphics.guiHeight() - 52, 0xFFFFFF, true);
                });
    }

    static Component label(Optional<SkillCooldownSnapshot> observed) {
        if (observed.isEmpty()) { return Component.translatable("hud.gramarye.cooldown.syncing"); }
        var snapshot = observed.orElseThrow();
        var slot = snapshot.entries().stream().filter(entry -> entry.slot() == 0).findFirst();
        if (slot.isEmpty()) {
            return Component.translatable(snapshot.sourceEpoch() == 0 || snapshot.sourceReason() == SyncReason.EQUIPMENT_UNKNOWN
                    ? "hud.gramarye.cooldown.unavailable" : "hud.gramarye.cooldown.unequipped");
        }
        var entry = slot.orElseThrow();
        return switch (entry.state()) {
            case READY -> Component.translatable("hud.gramarye.cooldown.ready");
            case ACTIVE -> Component.translatable("hud.gramarye.cooldown.active", entry.remainingTicks());
            case PENDING -> Component.translatable("hud.gramarye.cooldown.pending");
            case RECOVERY_REQUIRED -> Component.translatable("hud.gramarye.cooldown.recovery");
            case UNAVAILABLE -> Component.translatable("hud.gramarye.cooldown.unavailable");
        };
    }
}
