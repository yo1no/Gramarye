package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownCloneProbe;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AttachmentInternals.class)
abstract class P11CooldownCloneCopyMixin {
    @WrapMethod(method = "copyEntityAttachments(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$cooldownCloneCopy(Entity from, Entity to, boolean death, Operation<Void> original) {
        Object observed = P11CooldownCloneProbe.copyEntered(from, to, death);
        boolean normal = false;
        try { original.call(from, to, death); normal = true; }
        finally { P11CooldownCloneProbe.copyReturned(observed, normal); }
    }
}
