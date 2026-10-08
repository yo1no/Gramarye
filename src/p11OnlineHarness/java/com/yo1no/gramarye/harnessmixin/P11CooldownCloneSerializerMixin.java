package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownCloneProbe;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the real serializer calls within the selected exact native copy count as copy evidence. */
@Mixin(targets = "com.yo1no.gramarye.P11CastCooldownAttachments$Serializer")
abstract class P11CooldownCloneSerializerMixin {
    @Inject(method = "write(Lcom/yo1no/gramarye/P11CastCooldownData;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/Tag;",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownActualCopyWrite(@Coerce Object data, HolderLookup.Provider provider,
            CallbackInfoReturnable<Tag> callback) {
        P11CooldownCloneProbe.serialized(data, callback.getReturnValue());
    }
    @Inject(method = "read(Lnet/neoforged/neoforge/attachment/IAttachmentHolder;Lnet/minecraft/nbt/Tag;Lnet/minecraft/core/HolderLookup$Provider;)Lcom/yo1no/gramarye/P11CastCooldownData;",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownActualCopyRead(IAttachmentHolder holder, Tag input, HolderLookup.Provider provider,
            CallbackInfoReturnable<Object> callback) {
        P11CooldownCloneProbe.deserialized(holder, input, callback.getReturnValue());
    }
}
