package com.yo1no.gramarye.magic.action.type;

import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Optional;

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
    default Optional<ActionPayloadInspector<P>> payloadInspector() {
        return Optional.empty();
    }

    MapCodec<P> payloadCodec();

    ActionCapabilities capabilities();

    /**
     * Validates this descriptor's typed payload using payload-relative issue paths.
     *
     * <p>An empty path denotes the payload root. Implementations must not prepend outer document
     * segments such as {@code nodes[index]}, {@code action}, or {@code payload}, and must not
     * produce paths that escape the payload root. P3-B3-C orchestration is responsible for adding
     * the {@code nodes[index].action.payload} prefix while preserving the configured path bounds.
     * The validation context remains pure policy data and contains no runtime state.</p>
     */
    ValidationResult validate(P payload, ValidationContext context);
}
