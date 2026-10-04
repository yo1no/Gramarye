package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aPreplayChatClientProbe;
import net.minecraft.client.multiplayer.PingDebugMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PingDebugMonitor.class)
abstract class P11C4aPreplayChatPingMixin {
    @Inject(method="tick()V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void chat$actualPingTick(CallbackInfo callback) { P11C4aPreplayChatClientProbe.actorFunction(1); }
}
