package com.yo1no.gramarye.magic.definition.lookup;

import com.yo1no.gramarye.magic.action.type.ActionType;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Minimal registry-key boundary used by action definition resolution and encoding. */
public interface ActionTypeLookup {
    Optional<ActionType<?>> find(ResourceLocation typeId);

    Optional<ResourceLocation> keyOf(ActionType<?> descriptor);
}
