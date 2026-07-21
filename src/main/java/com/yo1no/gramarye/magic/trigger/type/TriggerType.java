package com.yo1no.gramarye.magic.trigger.type;

import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Optional;

/**
 * Immutable, stateless descriptor for one trigger payload shape.
 *
 * <p>The descriptor's type ID is exclusively its registry key. Implementations must represent
 * ordinary invalid player data with {@link ValidationResult}, not exceptions.</p>
 */
public interface TriggerType<P extends TriggerPayload> {
    /**
     * Returns the current data-format version for this descriptor's payload.
     *
     * <p>The value must be non-negative and is independent of skill revisions.</p>
     */
    int currentPayloadSchemaVersion();

    /** Returns this descriptor's immutable adjacent payload-schema migration plan. */
    default PayloadMigrationPlan payloadMigrationPlan() {
        return PayloadMigrationPlan.empty();
    }

    /**
     * Returns the optional pure structural inspector for this payload shape.
     *
     * <p>An empty value means that this descriptor has not supplied an inspection contract; it
     * does not assert that the payload contains no references.</p>
     */
    default Optional<TriggerPayloadInspector<P>> payloadInspector() {
        return Optional.empty();
    }

    MapCodec<P> payloadCodec();

    TriggerCapabilities capabilities();

    ValidationResult validate(P payload, ValidationContext context);
}
