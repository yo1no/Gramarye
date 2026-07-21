package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;

/** Non-persistent facts about tolerant normalization of a draft. */
public record SkillDraftReadReport(List<ReadFact> facts, boolean truncated) {
    public SkillDraftReadReport {
        facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
        if (facts.size() > MagicSafetyCeilings.MAX_READ_REPORT_FACTS) {
            throw new IllegalArgumentException("facts exceeds hard ceiling: " + facts.size());
        }
    }
}
