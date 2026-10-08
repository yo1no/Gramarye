package com.yo1no.gramarye.harnessmixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Test-server construction only; original native login checks still apply to every mock. */
@Mixin(GameTestServer.class)
abstract class P11CooldownGameTestCapacityMixin {
    @ModifyArg(method = "initServer()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/gametest/framework/GameTestServer$1;<init>(Lnet/minecraft/gametest/framework/GameTestServer;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/PlayerDataStorage;I)V"),
            index = 4, require = 1, expect = 1, allow = 1)
    private int p11$mockCapacity(int original) {
        if (original != 1) { throw new AssertionError("unexpected native GameTest player capacity"); }
        return 256;
    }
}
