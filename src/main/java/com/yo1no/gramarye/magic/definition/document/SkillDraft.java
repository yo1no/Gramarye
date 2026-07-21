package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Codec;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable editable snapshot. A draft is incomplete-capable and never executable. */
public record SkillDraft(
        int draftSchemaVersion,
        SkillId skillId,
        Optional<SkillRevision> baseRevision,
        List<DraftNode> nodes,
        AppearanceDocument appearance) {
    public static final int CURRENT_DRAFT_SCHEMA_VERSION = 0;
    public static final Codec<SkillDraft> CODEC = CanonicalDocumentCodecs.SKILL_DRAFT;

    public SkillDraft {
        if (draftSchemaVersion < 0) {
            throw new IllegalArgumentException("draftSchemaVersion must be non-negative");
        }
        Objects.requireNonNull(skillId, "skillId");
        baseRevision = Objects.requireNonNull(baseRevision, "baseRevision");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.size() > MagicSafetyCeilings.MAX_NODES) {
            throw new IllegalArgumentException("nodes exceeds hard ceiling: " + nodes.size());
        }
        Objects.requireNonNull(appearance, "appearance");
    }

    public SkillDraft withNodes(List<DraftNode> replacementNodes) {
        return new SkillDraft(draftSchemaVersion, skillId, baseRevision, replacementNodes, appearance);
    }
}
