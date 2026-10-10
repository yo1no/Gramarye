package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11D3ServerHarness;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(targets = "com.yo1no.gramarye.P11FoundationService")
abstract class P11D3FoundationMixin {
    @WrapMethod(method = "stopExact(Lnet/minecraft/server/MinecraftServer;)V", require = 1, expect = 1, allow = 1)
    private void p11$d3Terminal(MinecraftServer server, Operation<Void> original) {
        original.call(server); P11D3ServerHarness.rootRetired(server);
    }
}
