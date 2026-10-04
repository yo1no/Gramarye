package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ServerHarness;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.yo1no.gramarye.P11FoundationService")
abstract class P11L1FoundationMixin {
    @WrapMethod(method = "stopExact(Lnet/minecraft/server/MinecraftServer;)V", require = 1, expect = 1, allow = 1)
    private void p11$l1Retired(MinecraftServer server, Operation<Void> original) {
com.yo1no.gramarye.P11L1HostStopProbe.beforeRoot(server);
original.call(server);
com.yo1no.gramarye.P11L1HostStopProbe.rootRetired(server);
P11L1ServerHarness.rootRetired(server);
    }
}
