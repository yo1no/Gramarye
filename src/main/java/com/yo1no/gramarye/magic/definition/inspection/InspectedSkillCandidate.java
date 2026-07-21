package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import java.util.List;

/** Immutable transient seam output that keeps its projections paired with their source candidate. */
public record InspectedSkillCandidate(
        ResolvedSkillCandidate sourceCandidate,
        List<NodeReferenceProjection> nodes) {
    public InspectedSkillCandidate {
        sourceCandidate = InspectionContract.requireNonNull(sourceCandidate, "sourceCandidate");
        nodes = immutableNodes(nodes);
        InspectionContract.require(
                nodes.size() == sourceCandidate.nodes().size(),
                "projection count must match source candidate node count");
        for (var index = 0; index < nodes.size(); index++) {
            InspectionContract.require(
                    nodes.get(index).nodeIndex() == index,
                    "projection nodeIndex must equal its list position");
        }
    }

    private static List<NodeReferenceProjection> immutableNodes(List<NodeReferenceProjection> nodes) {
        InspectionContract.requireNonNull(nodes, "nodes");
        for (var node : nodes) {
            InspectionContract.requireNonNull(node, "node");
        }
        return List.copyOf(nodes);
    }
}
