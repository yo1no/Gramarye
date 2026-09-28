package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.Gramarye;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

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

    static ResourceLocation id() {
        return PLAYER_MANA_ID;
    }

    static AttachmentType<ManaState> type() {
        return PLAYER_MANA;
    }

    static ManaState state(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        var before = P11ManaMaterial.capture(player);
        var state = read(player, true);
        var after = P11ManaMaterial.capture(player);
        if (!before.sameState(after)) {
            com.yo1no.gramarye.P11NativeStorageBoundary.manaPublished(
                    player, P11ManaMaterial.publication(before, after));
        }
        return state;
    }

    static ManaState existing(ServerPlayer player) {
        return read(player, false);
    }

    private static ManaState read(ServerPlayer player, boolean installDefault) {
        return installDefault || player.hasData(PLAYER_MANA) ? player.getData(PLAYER_MANA) : null;
    }

    static void replace(ServerPlayer player, ManaState state) {
        var before = P11ManaMaterial.capture(player);
        Objects.requireNonNull(player, "player")
                .setData(PLAYER_MANA, Objects.requireNonNull(state, "state"));
        com.yo1no.gramarye.P11NativeStorageBoundary.manaPublished(
                player, P11ManaMaterial.publication(before, P11ManaMaterial.capture(player)));
    }
}
