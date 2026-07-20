package com.yo1no.gramarye.magic.definition.lookup;

import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Production trigger lookup that delegates directly to the formal custom registry. */
public final class RegistryTriggerTypeLookup implements TriggerTypeLookup {
    @Override
    public Optional<TriggerType<?>> find(ResourceLocation typeId) {
        return MagicRegistries.triggerTypeRegistry().getOptional(Objects.requireNonNull(typeId, "typeId"));
    }

    @Override
    public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
        return Optional.ofNullable(
                MagicRegistries.triggerTypeRegistry().getKey(Objects.requireNonNull(descriptor, "descriptor")));
    }
}
