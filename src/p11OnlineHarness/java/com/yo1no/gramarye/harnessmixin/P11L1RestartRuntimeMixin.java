package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1RestartProbe;
import java.util.IdentityHashMap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11L1RestartRuntimeMixin {
    @Shadow @Final private IdentityHashMap<MinecraftServer, ?> slots;

    @Inject(method = "handleRuntimeStarted(Lnet/neoforged/neoforge/event/server/ServerStartedEvent;Lcom/yo1no/gramarye/P5RuntimeLimits;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1ActualStarted(ServerStartedEvent event, @Coerce Object limits, CallbackInfo callback) {
        P11L1RestartProbe.runtimeStarted(this, event.getServer(), slots.get(event.getServer()));
        com.yo1no.gramarye.P11CooldownL1Probe.runtimeStarted(event.getServer(), slots.get(event.getServer()));
    }
}
