package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aPreplayChatClientProbe;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
abstract class P11C4aPreplayChatPlayerMixin {
    @Inject(method="tick()V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void chat$actualPlayerTick(CallbackInfo callback) { P11C4aPreplayChatClientProbe.oldActorTick((LocalPlayer)(Object)this); }
}
