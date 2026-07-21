package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import java.util.Optional;

/** Immutable payload-relative declaration of a reference to another node. */
public record NodeReference(
        int referencedNodeIndex,
        ReferenceRole role,
        ValidationPath payloadPath,
        Optional<ActionOutputKind> requiredOutputKind) {
    public NodeReference {
        role = InspectionContract.requireNonNull(role, "role");
        payloadPath = InspectionContract.requireNonNull(payloadPath, "payloadPath");
        requiredOutputKind = InspectionContract.requireNonNull(
                requiredOutputKind, "requiredOutputKind");
    }
}
