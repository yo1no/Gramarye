package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.util.List;
import java.util.Objects;

final class SkillHistorySnapshot {
    private final SkillId skillId;
    private final SkillOwnerId owner;
    private final List<SkillRevisionSnapshot> revisions;

    SkillHistorySnapshot(
            SkillId skillId,
            SkillOwnerId owner,
            List<SkillRevisionSnapshot> revisions) {
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions"));
    }

    SkillId skillId() {
        return skillId;
    }

    SkillOwnerId owner() {
        return owner;
    }

    List<SkillRevisionSnapshot> revisions() {
        return revisions;
    }

    @Override
    public String toString() {
        return "SkillHistorySnapshot[skillId=" + skillId
                + ", revisionCount=" + revisions.size() + "]";
    }
}
