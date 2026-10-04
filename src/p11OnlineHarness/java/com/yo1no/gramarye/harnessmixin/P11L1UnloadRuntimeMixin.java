package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1NaturalUnloadProbe;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="com.yo1no.gramarye.SkillRuntimeService")
abstract class P11L1UnloadRuntimeMixin {
    @Inject(method="sweepActiveProjectileContinuations(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/ServerSlot;)V",
            at=@At("HEAD"),require=1,expect=1,allow=1)
    private static void l1$actualSweepSlot(MinecraftServer server,@Coerce Object slot,CallbackInfo callback) {
        P11L1NaturalUnloadProbe.sweep(server,slot);
    }
}
