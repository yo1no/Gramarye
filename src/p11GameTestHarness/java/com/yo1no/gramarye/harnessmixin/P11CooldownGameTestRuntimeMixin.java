package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownGameTestHarness;
import java.util.IdentityHashMap;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Excluded read-only view of the exact original runtime, never a replacement slot. */
@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11CooldownGameTestRuntimeMixin implements P11CooldownGameTestHarness.RuntimeAccess {
    @Override
    @Accessor("slots")
    public abstract IdentityHashMap<MinecraftServer, ?> p11$gameTestSlots();
}
