package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownFaultProbe;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Excluded invokers used only for the declared synchronous PREPARED negative callbacks. */
@Mixin(targets = "com.yo1no.gramarye.P9StarterProjectile")
abstract class P11CooldownFaultProjectileMixin implements P11CooldownFaultProbe.ProjectileCallbacks {
    @Override @Invoker("onHitEntity")
    public abstract void p11$cooldownEntityHit(EntityHitResult hit);
    @Override @Invoker("onHitBlock")
    public abstract void p11$cooldownBlockHit(BlockHitResult hit);
}
