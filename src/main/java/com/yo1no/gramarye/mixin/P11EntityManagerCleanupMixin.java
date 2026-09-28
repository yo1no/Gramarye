package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.P11NativeCleanup;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PersistentEntitySectionManager.class)
abstract class P11EntityManagerCleanupMixin<T extends EntityAccess> implements P11NativeCleanup.Manager {
    @Shadow @Final private Set<UUID> knownUuids;
    @Shadow @Final private EntityLookup<T> visibleEntityStorage;
    @Shadow protected abstract void removeSectionIfEmpty(long key, EntitySection<T> section);

    @Override
    @SuppressWarnings("unchecked") // The opaque scope came from this manager's own Callback<T>.
    public boolean p11$finishLeaveTail(P11NativeCleanup.Scope scope) {
        return P11NativeCleanup.finishLeaveTail(this, visibleEntityStorage, knownUuids, scope,
                (key, section) -> removeSectionIfEmpty(key, (EntitySection<T>) section));
    }
}
