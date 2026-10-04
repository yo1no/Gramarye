package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ServerHarness;
import com.yo1no.gramarye.P11L1TerminalBoundaryProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.yo1no.gramarye.P9StarterProjectile")
abstract class P11L1ProjectileMixin {
    @WrapMethod(method = "tick()V", require = 1, expect = 1, allow = 1)
    private void p11$l1NaturalTick(Operation<Void> original) {
        boolean selected = P11L1TerminalBoundaryProbe.closeDiagnosticTickEntering(this), normal = false;
        try { original.call(); normal = true; }
        finally { P11L1TerminalBoundaryProbe.closeDiagnosticTickFinished(selected, normal); }
        com.yo1no.gramarye.P11L1ImpactCustodyProbe.tickReturned(this);
        P11L1ServerHarness.projectileTick(this);
    }
    @WrapMethod(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", require = 1, expect = 1, allow = 1)
    private void p11$l1NaturalCollision(EntityHitResult hit, Operation<Void> original) {
        original.call(hit);
        P11L1ServerHarness.hitReturned(this, hit.getEntity());
    }
}
