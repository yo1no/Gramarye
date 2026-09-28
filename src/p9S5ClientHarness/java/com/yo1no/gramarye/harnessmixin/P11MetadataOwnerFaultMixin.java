package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeMetadataProbe;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;

/** This excluded wrapper deliberately intercepts ONLY the owned test fault, after native finally. */
@Mixin(value = SkillSubmissionRecoveryService.class, remap = false)
abstract class P11MetadataOwnerFaultMixin {
    @WrapMethod(method = "onPlayerLoggedIn(Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$ownedFault(PlayerEvent.PlayerLoggedInEvent event, Operation<Void> original) {
        var owner = (SkillSubmissionRecoveryService) (Object) this;
        boolean selected = P11NativeMetadataProbe.begin(owner, event);
        try { original.call(event); }
        catch (RuntimeException failure) {
            if (!selected || !P11NativeMetadataProbe.isOwnedFault(failure)) { throw failure; }
            P11NativeMetadataProbe.afterUnwoundFault(owner);
        }
    }
}
