package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1P8FallbackClientProbe;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.yo1no.gramarye.MinecraftP8ClientPresentationBackend")
abstract class P11L1P8FallbackBackendMixin {
    @Inject(method = "soundExists(Lnet/minecraft/resources/ResourceLocation;)Z", at = @At("RETURN"),
            require = 1, expect = 1, allow = 1)
    private void p11$originalAsset(ResourceLocation asset, CallbackInfoReturnable<Boolean> callback) {
        P11L1P8FallbackClientProbe.assetObserved(asset, callback.getReturnValueZ());
    }
}
