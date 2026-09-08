package com.yo1no.gramarye.magic.api.registry;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
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
    public static final ResourceKey<Registry<ProfileType<?>>> PROFILE_TYPE_REGISTRY_KEY =
            createRegistryKey("profile_type");

    public static final DeferredRegister<TriggerType<?>> TRIGGER_TYPES =
            DeferredRegister.create(TRIGGER_TYPE_REGISTRY_KEY, Gramarye.MOD_ID);
    public static final DeferredRegister<ActionType<?>> ACTION_TYPES =
            DeferredRegister.create(ACTION_TYPE_REGISTRY_KEY, Gramarye.MOD_ID);
    public static final DeferredRegister<ProfileType<?>> PROFILE_TYPES =
            DeferredRegister.create(PROFILE_TYPE_REGISTRY_KEY, Gramarye.MOD_ID);

    static {
        BuiltInProfileTypes.register(PROFILE_TYPES);
    }

    private static Registry<TriggerType<?>> triggerTypeRegistry;
    private static Registry<ActionType<?>> actionTypeRegistry;
    private static Registry<ProfileType<?>> profileTypeRegistry;

    private MagicRegistries() {
    }

    public static void register(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        modBus.addListener(MagicRegistries::registerCustomRegistries);
        TRIGGER_TYPES.register(modBus);
        ACTION_TYPES.register(modBus);
        PROFILE_TYPES.register(modBus);
    }

    /** Returns the formal trigger descriptor registry after {@link NewRegistryEvent}. */
    public static Registry<TriggerType<?>> triggerTypeRegistry() {
        if (triggerTypeRegistry == null) {
            throw new IllegalStateException("Trigger type registry is not available before NewRegistryEvent");
        }
        return triggerTypeRegistry;
    }

    /** Returns the formal action descriptor registry after {@link NewRegistryEvent}. */
    public static Registry<ActionType<?>> actionTypeRegistry() {
        if (actionTypeRegistry == null) {
            throw new IllegalStateException("Action type registry is not available before NewRegistryEvent");
        }
        return actionTypeRegistry;
    }

    /** Returns the startup-frozen Profile descriptor registry after {@link NewRegistryEvent}. */
    public static Registry<ProfileType<?>> profileTypeRegistry() {
        if (profileTypeRegistry == null) {
            throw new IllegalStateException(
                    "Profile type registry is not available before NewRegistryEvent");
        }
        return profileTypeRegistry;
    }

    private static void registerCustomRegistries(NewRegistryEvent event) {
        triggerTypeRegistry = event.create(new RegistryBuilder<>(TRIGGER_TYPE_REGISTRY_KEY));
        actionTypeRegistry = event.create(new RegistryBuilder<>(ACTION_TYPE_REGISTRY_KEY));
        profileTypeRegistry = event.create(new RegistryBuilder<>(PROFILE_TYPE_REGISTRY_KEY)
                .maxId(63)
                .sync(false)
                .onBake(BuiltInProfileTypes::validateRegistry));
    }

    private static <T> ResourceKey<Registry<T>> createRegistryKey(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path));
    }
}
