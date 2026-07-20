package com.yo1no.gramarye.magic.definition.lookup;

import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Production action lookup that delegates directly to the formal custom registry. */
public final class RegistryActionTypeLookup implements ActionTypeLookup {
    @Override
    public Optional<ActionType<?>> find(ResourceLocation typeId) {
        return MagicRegistries.actionTypeRegistry().getOptional(Objects.requireNonNull(typeId, "typeId"));
    }

    @Override
    public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
        return Optional.ofNullable(
                MagicRegistries.actionTypeRegistry().getKey(Objects.requireNonNull(descriptor, "descriptor")));
    }
}
