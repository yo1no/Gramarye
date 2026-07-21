package com.yo1no.gramarye.magic.definition.resolution;

import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.List;
import java.util.Objects;

/** Immutable, non-persistent result of attempting resolution for every document node. */
public record ResolvedSkillCandidate(
        int skillSchemaVersion,
        SkillReference skill,
        List<ResolvedNodeCandidate> nodes,
        AppearanceDocument appearance,
        SkillDocumentReadReport readReport,
        PipelineFactReport pipelineFacts) {
    public ResolvedSkillCandidate {
        if (skillSchemaVersion < 0) {
            throw new IllegalArgumentException("skillSchemaVersion must be non-negative");
        }
        Objects.requireNonNull(skill, "skill");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(readReport, "readReport");
        Objects.requireNonNull(pipelineFacts, "pipelineFacts");
        for (var index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).nodeIndex() != index) {
                throw new IllegalArgumentException(
                        "candidate nodeIndex must equal its list position");
            }
        }
    }
}
