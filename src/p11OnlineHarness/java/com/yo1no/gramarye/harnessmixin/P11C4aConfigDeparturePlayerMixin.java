package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aConfigDepartureProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerPlayer;
@Mixin(ServerPlayer.class)
abstract class P11C4aConfigDeparturePlayerMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void departure$actualConstructor(CallbackInfo callback) {
        P11C4aConfigDepartureProbe.constructed((ServerPlayer) (Object) this);
    }
}
