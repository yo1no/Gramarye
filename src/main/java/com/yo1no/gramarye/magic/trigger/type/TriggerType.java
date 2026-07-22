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

    /**
     * Validates this descriptor's typed payload using payload-relative issue paths.
     *
     * <p>An empty path denotes the payload root. Implementations must not prepend outer document
     * segments such as {@code nodes[index]}, {@code trigger}, or {@code payload}, and must not
     * produce paths that escape the payload root. P3-B3-C orchestration is responsible for adding
     * the {@code nodes[index].trigger.payload} prefix while preserving the configured path bounds.
     * The validation context remains pure policy data and contains no runtime state.</p>
     */
    ValidationResult validate(P payload, ValidationContext context);
}
