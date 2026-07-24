package com.yo1no.gramarye.magic.definition.migration;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
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

    /**
     * Appends another visible report in deterministic order without exposing the mutable
     * collector. Facts beyond the shared hard ceiling are omitted and mark the result truncated.
     */
    public PipelineFactReport append(PipelineFactReport other) {
        Objects.requireNonNull(other, "other");
        var maximum = MagicSafetyCeilings.MAX_PIPELINE_FACTS;
        var retained = new ArrayList<SkillMigrationFact>(Math.min(
                maximum,
                facts.size() + other.facts.size()));
        retained.addAll(facts);
        var remaining = maximum - retained.size();
        if (remaining > 0) {
            retained.addAll(other.facts.subList(0, Math.min(remaining, other.facts.size())));
        }
        var overflowed = other.facts.size() > remaining;
        return new PipelineFactReport(
                retained,
                truncated || other.truncated || overflowed);
    }
}
