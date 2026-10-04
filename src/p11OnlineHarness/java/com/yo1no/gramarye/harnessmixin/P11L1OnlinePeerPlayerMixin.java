package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1OnlinePeerProbe;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayer.class)
abstract class P11L1OnlinePeerPlayerMixin implements P11L1OnlinePeerProbe.PlayerAccess {
    @Accessor("spawnInvulnerableTime")
    public abstract int p11$l1SpawnInvulnerableTime();
}
