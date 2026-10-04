package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aPreplayChatClientProbe;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AccountProfileKeyPairManager.class)
abstract class P11C4aPreplayChatRefreshMixin {
    @Inject(method="shouldRefreshKeyPair()Z",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void chat$actualRefresh(CallbackInfoReturnable<Boolean> callback) { P11C4aPreplayChatClientProbe.actorFunction(0); }
}
