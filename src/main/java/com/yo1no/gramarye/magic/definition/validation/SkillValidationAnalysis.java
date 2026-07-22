package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.inspection.InspectedSkillCandidate;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;
import java.util.Optional;

/** Immutable, non-persistent P3-B3-C analysis result. */
public final class SkillValidationAnalysis {
    private final ResolvedSkillCandidate sourceCandidate;
    private final Optional<InspectedSkillCandidate> inspection;
    private final ValidationResult report;

    SkillValidationAnalysis(
            ResolvedSkillCandidate sourceCandidate,
            Optional<InspectedSkillCandidate> inspection,
            ValidationResult report) {
        this.sourceCandidate = Objects.requireNonNull(sourceCandidate, "sourceCandidate");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
        this.report = Objects.requireNonNull(report, "report");

        var currentSchema = sourceCandidate.skillSchemaVersion()
                == SkillDocument.CURRENT_SCHEMA_VERSION;
        if (currentSchema != inspection.isPresent()) {
            throw new IllegalArgumentException(
                    "inspection presence must match current skill schema support");
        }
        if (inspection.isEmpty() && !report.hasErrors()) {
            throw new IllegalArgumentException("analysis without inspection must contain an error");
        }
        inspection.ifPresent(this::verifyInspectionPairing);
    }

    public ResolvedSkillCandidate sourceCandidate() {
        return sourceCandidate;
    }

    public Optional<InspectedSkillCandidate> inspection() {
        return inspection;
    }

    public ValidationResult report() {
        return report;
    }

    private void verifyInspectionPairing(InspectedSkillCandidate inspected) {
        if (inspected.sourceCandidate() != sourceCandidate) {
            throw new IllegalArgumentException("inspection must retain source candidate identity");
        }
        if (inspected.nodes().size() != sourceCandidate.nodes().size()) {
            throw new IllegalArgumentException("inspection node count must match source candidate");
        }
        for (var index = 0; index < inspected.nodes().size(); index++) {
            if (inspected.nodes().get(index).nodeIndex() != index) {
                throw new IllegalArgumentException(
                        "inspection nodeIndex must equal its list position");
            }
        }
    }
}
