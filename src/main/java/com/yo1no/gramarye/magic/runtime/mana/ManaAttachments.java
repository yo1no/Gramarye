package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.Gramarye;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = Gramarye.MOD_ID)
final class ManaAttachments {
    private static final ResourceLocation PLAYER_MANA_ID =
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_mana");
    private static final AttachmentType<ManaState> PLAYER_MANA =
            AttachmentType.<ManaState>builder(ManaState::freshDefault)
                    .serialize(ManaAttachmentSerializer.INSTANCE)
                    .copyOnDeath()
                    .copyHandler(ManaLifecycle::copy)
                    .build();

    private ManaAttachments() {}

    @SubscribeEvent
    private static void register(RegisterEvent event) {
        event.register(
                NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                PLAYER_MANA_ID,
                () -> PLAYER_MANA);
    }

    static AttachmentType<ManaState> type() {
        return PLAYER_MANA;
    }

    static ManaState state(ServerPlayer player) {
        return Objects.requireNonNull(player, "player").getData(PLAYER_MANA);
    }

    static void replace(ServerPlayer player, ManaState state) {
        Objects.requireNonNull(player, "player")
                .setData(PLAYER_MANA, Objects.requireNonNull(state, "state"));
    }
}
