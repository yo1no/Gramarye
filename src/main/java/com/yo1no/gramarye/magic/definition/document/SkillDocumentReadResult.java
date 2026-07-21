package com.yo1no.gramarye.magic.definition.document;

import java.util.Objects;

public record SkillDocumentReadResult(SkillDocument document, SkillDocumentReadReport report) {
    public SkillDocumentReadResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(report, "report");
    }
}
