package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aCompleteBFaultProbe;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import org.spongepowered.asm.mixin.Mixin;

/** Excluded exact copy-time presence observation; no default installation or original-policy change. */
@Mixin(AttachmentInternals.class)
abstract class P11C4aCompleteBAttachmentCopyMixin {
    @WrapMethod(method = "copyEntityAttachments(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)V", require = 1, expect = 1, allow = 1)
    private static void p11$completeBActualAttachmentCopy(Entity from, Entity to, boolean wasDeath, Operation<Void> original) {
        Object token = P11C4aCompleteBFaultProbe.attachmentCopyEntered(from, to, wasDeath);
        boolean normal = false;
        try { original.call(from, to, wasDeath); normal = true; }
        finally { P11C4aCompleteBFaultProbe.attachmentCopyEnded(token, normal); }
    }
}
