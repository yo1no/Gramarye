package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.U0BoundedEntityQuery;
import com.yo1no.gramarye.U0BoundedQueryAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelEntityGetterAdapter.class)
abstract class U0SectionQueryMixin implements U0BoundedQueryAccess {
    @Shadow @Final private EntitySectionStorage<?> sectionStorage;

    @Override
    public void gramarye$u0Collect(U0BoundedEntityQuery.Permit permit) {
        U0BoundedEntityQuery.collect(permit, this, sectionStorage);
    }
}
