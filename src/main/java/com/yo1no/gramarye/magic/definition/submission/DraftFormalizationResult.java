package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Objects;

/** Complete formalized nodes or an error-only result that exposes no partial list. */
sealed interface DraftFormalizationResult
        permits DraftFormalizationResult.Ready, DraftFormalizationResult.Invalid {
    ValidationResult report();

    record Ready(
            SkillSubmissionInput input,
            List<NodeDocument> nodes,
            ValidationResult report) implements DraftFormalizationResult {
        public Ready {
            Objects.requireNonNull(input, "input");
            nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
            Objects.requireNonNull(report, "report");
            if (report.hasErrors()) {
                throw new IllegalArgumentException("Ready formalization cannot contain an error");
            }
            if (input.draft().draftSchemaVersion() != SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "Ready formalization requires the current Draft schema");
            }
            if (nodes.size() != input.draft().nodes().size()) {
                throw new IllegalArgumentException(
                        "Ready formalization must contain every Draft node");
            }
        }
    }

    record Invalid(ValidationResult report) implements DraftFormalizationResult {
        public Invalid {
            Objects.requireNonNull(report, "report");
            if (!report.hasErrors()) {
                throw new IllegalArgumentException("Invalid formalization requires an error");
            }
        }
    }
}
