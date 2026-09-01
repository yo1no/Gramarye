package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Direct Attachment-backed account access for one server player. */
final class PlayerManaAccountAccess implements ManaAccountAccess {
    private final ServerPlayer player;

    PlayerManaAccountAccess(ServerPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public boolean isLogicThread() {
        return player.getServer().isSameThread();
    }

    @Override
    public UUID accountId() {
        return player.getUUID();
    }

    @Override
    public ManaAvailability availability() {
        return ManaAttachments.state(player).availability();
    }

    @Override
    public long balance() {
        return ManaAttachments.state(player).balance();
    }

    @Override
    public void writeBalance(long balance) {
        ManaAttachments.replace(player, ManaState.available(balance));
    }
}
