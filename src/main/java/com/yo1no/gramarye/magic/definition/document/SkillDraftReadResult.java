package com.yo1no.gramarye.magic.definition.document;

import java.util.Objects;

public record SkillDraftReadResult(SkillDraft draft, SkillDraftReadReport report) {
    public SkillDraftReadResult {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(report, "report");
    }
}
