package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownFaultProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.yo1no.gramarye.P11QualifiedSourceOwner")
abstract class P11CooldownFaultSourceMixin implements P11CooldownFaultProbe.SourceFailures {
    @Shadow private long failures;
    @Override public long p11$cooldownSourceFailures() { return failures; }
}
