package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1TrackingBoundaryProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the actual native manager for one excluded controlled regression. */
@Mixin(ServerLevel.class)
abstract class P11L1TrackingLevelMixin implements P11L1TrackingBoundaryProbe.LevelAccess {
    @Override @Accessor("entityManager")
    public abstract PersistentEntitySectionManager<Entity> p11$l1EntityManager();
}
