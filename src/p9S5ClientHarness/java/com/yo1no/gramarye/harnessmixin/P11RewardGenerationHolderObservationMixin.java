package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11NativeRewardStopProbe;
import net.minecraft.server.level.GenerationChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Main-thread-only diagnostic identity, in the existing holder's own lifecycle. */
@Mixin(GenerationChunkHolder.class)
abstract class P11RewardGenerationHolderObservationMixin implements P11NativeRewardStopProbe.HolderObservation {
    @Unique private int p11$rewardStop;
    @Unique private long p11$rewardHolderOrdinal;

    @Override public long p11$rewardStopOrdinal(int stopOrdinal) {
        return p11$rewardStop == stopOrdinal ? p11$rewardHolderOrdinal : 0;
    }

    @Override public void p11$rewardStopOrdinal(int stopOrdinal, long ordinal) {
        p11$rewardHolderOrdinal = ordinal;
        p11$rewardStop = stopOrdinal;
    }
}
