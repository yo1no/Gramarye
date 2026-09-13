package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Common-side registration owner for the one nonpersistent P9 projectile type. */
final class P9StarterProjectileRegistration {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gramarye.MOD_ID);
    private static final DeferredHolder<EntityType<?>, EntityType<P9StarterProjectile>>
            STARTER_PROJECTILE = ENTITY_TYPES.register(
                    "starter_projectile",
                    key -> EntityType.Builder
                            .<P9StarterProjectile>of(
                                    P9StarterProjectile::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .noSave()
                            .noSummon()
                            .setTrackingRange(4)
                            .setUpdateInterval(10)
                            .setShouldReceiveVelocityUpdates(true)
                            .build(key.toString()));

    private P9StarterProjectileRegistration() {
    }

    static void register(IEventBus modBus) {
        ENTITY_TYPES.register(Objects.requireNonNull(modBus, "modBus"));
    }

    static EntityType<P9StarterProjectile> type() {
        return STARTER_PROJECTILE.get();
    }
}
