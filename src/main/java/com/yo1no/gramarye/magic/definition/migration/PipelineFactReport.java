package com.yo1no.gramarye.magic.definition.migration;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;

/** Immutable, deterministic and non-persistent migration fact report. */
public record PipelineFactReport(List<SkillMigrationFact> facts, boolean truncated) {
    public PipelineFactReport {
        facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
        if (facts.size() > MagicSafetyCeilings.MAX_PIPELINE_FACTS) {
            throw new IllegalArgumentException("facts exceeds the pipeline fact ceiling");
        }
    }
}
