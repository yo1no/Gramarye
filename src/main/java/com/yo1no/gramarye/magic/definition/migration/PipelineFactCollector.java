package com.yo1no.gramarye.magic.definition.migration;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class PipelineFactCollector {
    private final List<SkillMigrationFact> facts = new ArrayList<>();
    private boolean truncated;

    void add(SkillMigrationFact fact) {
        Objects.requireNonNull(fact, "fact");
        if (facts.size() < MagicSafetyCeilings.MAX_PIPELINE_FACTS) {
            facts.add(fact);
        } else {
            truncated = true;
        }
    }

    PipelineFactReport report() {
        return new PipelineFactReport(facts, truncated);
    }
}
