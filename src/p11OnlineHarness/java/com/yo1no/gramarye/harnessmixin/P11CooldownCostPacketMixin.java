package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownCostProbe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Netty observes only scalar indices/times of this original codec, never payload contents. */
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7PayloadCodecSupport")
abstract class P11CooldownCostPacketMixin {
    @WrapMethod(method = "encodeSkillCooldownSnapshot(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lcom/yo1no/gramarye/magic/network/SkillCooldownSyncPayload;)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$cooldownOriginalEncode(RegistryFriendlyByteBuf buffer, @Coerce Object payload,
            Operation<Void> original) {
        var timing = P11CooldownCostProbe.encodeBefore(buffer);
        boolean normal = false;
        try { original.call(buffer, payload); normal = true; }
        finally { P11CooldownCostProbe.encodeAfter(timing, buffer, normal); }
    }
}
