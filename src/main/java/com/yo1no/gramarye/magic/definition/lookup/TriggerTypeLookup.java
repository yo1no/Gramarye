package com.yo1no.gramarye.magic.definition.lookup;

import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Minimal registry-key boundary used by trigger definition resolution and encoding. */
public interface TriggerTypeLookup {
    Optional<TriggerType<?>> find(ResourceLocation typeId);

    Optional<ResourceLocation> keyOf(TriggerType<?> descriptor);
}
