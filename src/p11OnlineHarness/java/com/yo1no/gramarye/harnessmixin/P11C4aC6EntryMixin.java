package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aC6EarlyGateProbe;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** EXTERNAL DRAFT: exact current entry transport observation; no setter or actor lookup. */
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService$Entry", remap = false)
abstract class P11C4aC6EntryMixin implements P11C4aC6EarlyGateProbe.EntryConnection {
    @Shadow @Final private Connection connection;
    @Override public Connection p11$c6Connection() { return connection; }
}
