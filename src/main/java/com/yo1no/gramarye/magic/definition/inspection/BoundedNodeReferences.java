package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import java.util.ArrayList;
import java.util.List;

/** Creates the bounded immutable reference state shared by both inspection sides. */
final class BoundedNodeReferences {
    private BoundedNodeReferences() {
    }

    static List<NodeReference> copyOf(List<NodeReference> references) {
        InspectionContract.requireNonNull(references, "references");
        var maximum = MagicSafetyCeilings.MAX_INSPECTED_REFERENCES_PER_SIDE;
        var copy = new ArrayList<NodeReference>(maximum);
        var iterator = references.iterator();
        var consumed = 0;
        while (iterator.hasNext()) {
            var reference = iterator.next();
            consumed++;
            if (consumed > maximum) {
                throw new InspectionContractViolationException(
                        new ValidationIssueMetadata.Limit(consumed, maximum));
            }
            copy.add(InspectionContract.requireNonNull(reference, "reference"));
        }
        return List.copyOf(copy);
    }
}
