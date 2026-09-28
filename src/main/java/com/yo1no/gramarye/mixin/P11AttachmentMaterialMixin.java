package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AttachmentHolder.class)
abstract class P11AttachmentMaterialMixin {
    @WrapMethod(method = "deserializeAttachments(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V")
    private void p11$requiredLoad(HolderLookup.Provider provider, CompoundTag input,
            Operation<Void> original) {
        P11NativeStorageBoundary.readAttachments((AttachmentHolder) (Object) this,
                provider, input, original);
    }

    @WrapMethod(method = "serializeAttachments(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;")
    private CompoundTag p11$requiredSave(HolderLookup.Provider provider,
            Operation<CompoundTag> original) {
        return P11NativeStorageBoundary.writeAttachments((AttachmentHolder) (Object) this,
                provider, original);
    }
}
