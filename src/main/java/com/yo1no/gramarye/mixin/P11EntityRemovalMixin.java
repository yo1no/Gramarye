package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeCleanup;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
abstract class P11EntityRemovalMixin implements P11NativeCleanup.CallbackState {
    @Shadow @Final private EntityAccess entity;
    @Shadow @Final private PersistentEntitySectionManager<?> this$0;
    @Shadow private long currentSectionKey;
    @Shadow private EntitySection<?> currentSection;

    @Override
    public boolean p11$unchangedRemovalFrame(P11NativeCleanup.Scope scope) {
        return P11NativeCleanup.unchangedRemovalFrame(scope, this, currentSection, currentSectionKey);
    }

    @WrapMethod(method = "onRemove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$observedRemoval(Entity.RemovalReason reason, Operation<Void> original) {
        P11NativeCleanup.remove(entity, reason, (P11NativeCleanup.Manager) this$0, this,
                currentSection, currentSectionKey, original);
    }
}
