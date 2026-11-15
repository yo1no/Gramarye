package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

final class V0ManaAccountAccess implements ManaAccountAccess {
    final ServerPlayer exactActor;
    private P11ManaMaterial.State debitMaterial;

    V0ManaAccountAccess(ServerPlayer exactA) {
        this.exactActor = Objects.requireNonNull(exactA);
    }

    boolean debitMaterialCurrent() {
        return debitMaterial != null && debitMaterial.isCurrent(exactActor);
    }

    @Override
    public boolean isLogicThread() {
        return exactActor.getServer().isSameThread();
    }

    @Override
    public UUID accountId() {
        return exactActor.getUUID();
    }

    @Override
    public ManaAvailability availability() {
        ManaState state = ManaAttachments.existing(exactActor);
        return state == null ? ManaAvailability.UNAVAILABLE : state.availability();
    }

    @Override
    public long balance() {
        return Objects.requireNonNull(ManaAttachments.existing(exactActor)).balance();
    }

    @Override
    public void writeBalance(long balance) {
        ManaAttachments.replace(exactActor, ManaState.available(balance));
        if (debitMaterial == null) {
            debitMaterial = P11ManaMaterial.capture(exactActor);
        }
    }
}
