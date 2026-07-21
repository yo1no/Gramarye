package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;

final class ReadFactCollector {
    private final List<ReadFact> facts = new ArrayList<>();
    private boolean truncated;

    void add(ReadFact fact) {
        if (facts.size() < MagicSafetyCeilings.MAX_READ_REPORT_FACTS) {
            facts.add(fact);
        } else {
            truncated = true;
        }
    }

    void addAll(List<ReadFact> additions) {
        additions.forEach(this::add);
    }

    SkillDocumentReadReport documentReport() {
        return new SkillDocumentReadReport(facts, truncated);
    }

    SkillDraftReadReport draftReport() {
        return new SkillDraftReadReport(facts, truncated);
    }
}
