package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

final class SkillRevisionSnapshot {
    private final SkillRevision revision;
    private final SkillDocument document;

    SkillRevisionSnapshot(SkillRevision revision, SkillDocument document) {
        this.revision = Objects.requireNonNull(revision, "revision");
        this.document = Objects.requireNonNull(document, "document");
    }

    SkillRevision revision() {
        return revision;
    }

    SkillDocument document() {
        return document;
    }

    @Override
    public String toString() {
        return "SkillRevisionSnapshot[reference="
                + new SkillReference(document.skillId(), revision) + "]";
    }
}
