package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** Converts complete current-schema Draft slots into transient NodeDocuments. */
final class DraftFormalizer {
    DraftFormalizationResult formalize(DraftSubmissionPrecheck.Ready ready) {
        Objects.requireNonNull(ready, "ready");
        var collector = new ValidationCollector();
        for (var issue : ready.report().issues()) {
            collector.add(issue);
        }
        collector.inheritReportState(ready.report());

        var input = ready.input();
        var draftNodes = input.draft().nodes();
        var formalizedNodes = new ArrayList<NodeDocument>(draftNodes.size());
        for (var nodeIndex = 0; nodeIndex < draftNodes.size(); nodeIndex++) {
            var node = draftNodes.get(nodeIndex);
            Optional<DefinitionEnvelope> trigger = switch (node.trigger()) {
                case DraftTriggerSlot.Missing ignored -> {
                    collector.add(missingTrigger(nodeIndex));
                    yield Optional.empty();
                }
                case DraftTriggerSlot.Present present -> Optional.of(present.definition());
            };
            Optional<DefinitionEnvelope> action = switch (node.action()) {
                case DraftActionSlot.Missing ignored -> {
                    collector.add(missingAction(nodeIndex));
                    yield Optional.empty();
                }
                case DraftActionSlot.Present present -> Optional.of(present.definition());
            };
            if (trigger.isPresent() && action.isPresent()) {
                formalizedNodes.add(new NodeDocument(
                        trigger.orElseThrow(),
                        action.orElseThrow(),
                        node.appearanceOverride()));
            }
        }

        var report = collector.result();
        if (report.hasErrors()) {
            return new DraftFormalizationResult.Invalid(report);
        }
        return new DraftFormalizationResult.Ready(input, formalizedNodes, report);
    }

    private static ValidationIssue missingTrigger(int nodeIndex) {
        return new ValidationIssue(
                SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING,
                ValidationSeverity.ERROR,
                DraftSubmissionPaths.trigger(nodeIndex),
                ValidationIssueMetadata.none());
    }

    private static ValidationIssue missingAction(int nodeIndex) {
        return new ValidationIssue(
                SkillSubmissionIssueCodes.DRAFT_ACTION_MISSING,
                ValidationSeverity.ERROR,
                DraftSubmissionPaths.action(nodeIndex),
                ValidationIssueMetadata.none());
    }
}
