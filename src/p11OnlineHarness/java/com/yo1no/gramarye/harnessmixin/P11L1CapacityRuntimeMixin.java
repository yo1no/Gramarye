package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1CapacityWorkProbe;
import java.util.IdentityHashMap;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Read only the existing exact runtime slot, without a production locator or mutation. */
@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11L1CapacityRuntimeMixin implements P11L1CapacityWorkProbe.RuntimeAccess {
    @Shadow @Final private IdentityHashMap<MinecraftServer, Object> slots;
    @Override public Object p11$l1CapacitySlot(MinecraftServer server) { return slots.get(server); }
}
