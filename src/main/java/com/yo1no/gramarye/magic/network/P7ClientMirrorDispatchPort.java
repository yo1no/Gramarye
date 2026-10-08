package com.yo1no.gramarye.magic.network;

import net.minecraft.network.Connection;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

interface P7ClientMirrorDispatchPort {
    long captureDispatchGeneration(Connection sourceConnection, ICommonPacketListener sourceListener);

    void onIntentAcknowledgement(
            long dispatchGeneration, IntentAcknowledgement acknowledgement);

    void onPlayerManaSnapshot(
            long dispatchGeneration, PlayerManaSnapshot snapshot);

    void onSkillCooldownSnapshot(
            long dispatchGeneration, SkillCooldownSnapshot snapshot);
}
