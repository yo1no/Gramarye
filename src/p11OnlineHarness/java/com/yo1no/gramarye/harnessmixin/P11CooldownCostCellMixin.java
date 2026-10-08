package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownCostProbe;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.yo1no.gramarye.P11CastCooldownService$Cell")
abstract class P11CooldownCostCellMixin implements P11CooldownCostProbe.CellCounts {
    @Shadow @Final private Map<UUID, ?> attempts;
    @Override public int p11$cooldownRetainedAttempts() { return attempts.size(); }
}
