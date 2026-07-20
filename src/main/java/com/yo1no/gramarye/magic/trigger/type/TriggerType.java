package com.yo1no.gramarye.magic.trigger.type;

import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;

/**
 * Immutable, stateless descriptor for one trigger payload shape.
 *
 * <p>The descriptor's type ID is exclusively its registry key. Implementations must represent
 * ordinary invalid player data with {@link ValidationResult}, not exceptions.</p>
 */
public interface TriggerType<P extends TriggerPayload> {
    MapCodec<P> payloadCodec();

    TriggerCapabilities capabilities();

    ValidationResult validate(P payload, ValidationContext context);
}
