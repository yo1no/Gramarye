package com.yo1no.gramarye.magic.action.type;

import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;

/**
 * Pure and deterministic structural projection of one typed Action payload.
 *
 * <p>Implementations must not mutate the payload, access registries or runtime state, or perform
 * Codec conversion or execution.</p>
 */
@FunctionalInterface
public interface ActionPayloadInspector<P extends ActionPayload> {
    PayloadInspectionResult<ActionReferenceProjection> inspect(P payload);
}
