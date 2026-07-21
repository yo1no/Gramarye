package com.yo1no.gramarye.magic.trigger.type;

import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;

/**
 * Pure and deterministic structural projection of one typed Trigger payload.
 *
 * <p>Implementations must not mutate the payload, access registries or runtime state, or perform
 * Codec conversion or execution.</p>
 */
@FunctionalInterface
public interface TriggerPayloadInspector<P extends TriggerPayload> {
    PayloadInspectionResult<TriggerReferenceProjection> inspect(P payload);
}
