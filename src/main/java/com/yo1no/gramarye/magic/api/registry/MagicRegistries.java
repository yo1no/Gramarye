package com.yo1no.gramarye.magic.api.registry;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Common-side bootstrap for Gramarye's code-defined descriptor registries. */
public final class MagicRegistries {
    public static final ResourceKey<Registry<TriggerType<?>>> TRIGGER_TYPE_REGISTRY_KEY =
            createRegistryKey("trigger_type");
    public static final ResourceKey<Registry<ActionType<?>>> ACTION_TYPE_REGISTRY_KEY =
            createRegistryKey("action_type");

    public static final DeferredRegister<TriggerType<?>> TRIGGER_TYPES =
            DeferredRegister.create(TRIGGER_TYPE_REGISTRY_KEY, Gramarye.MOD_ID);
    public static final DeferredRegister<ActionType<?>> ACTION_TYPES =
            DeferredRegister.create(ACTION_TYPE_REGISTRY_KEY, Gramarye.MOD_ID);

    private MagicRegistries() {
    }

    public static void register(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        modBus.addListener(MagicRegistries::registerCustomRegistries);
        TRIGGER_TYPES.register(modBus);
        ACTION_TYPES.register(modBus);
    }

    private static void registerCustomRegistries(NewRegistryEvent event) {
        event.create(new RegistryBuilder<>(TRIGGER_TYPE_REGISTRY_KEY));
        event.create(new RegistryBuilder<>(ACTION_TYPE_REGISTRY_KEY));
    }

    private static <T> ResourceKey<Registry<T>> createRegistryKey(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path));
    }
}
