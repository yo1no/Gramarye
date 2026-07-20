package com.yo1no.gramarye.magic.action.type;

import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;

/**
 * Immutable, stateless descriptor for one action payload shape.
 *
 * <p>The descriptor's type ID is exclusively its registry key. Execution is intentionally outside
 * the P2-A descriptor boundary.</p>
 */
public interface ActionType<P extends ActionPayload> {
    /**
     * Returns the current data-format version for this descriptor's payload.
     *
     * <p>The value must be non-negative and is independent of skill revisions.</p>
     */
    int currentPayloadSchemaVersion();

    MapCodec<P> payloadCodec();

    ActionCapabilities capabilities();

    ValidationResult validate(P payload, ValidationContext context);
}
