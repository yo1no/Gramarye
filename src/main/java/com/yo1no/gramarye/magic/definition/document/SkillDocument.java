package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Codec;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;

/** Immutable persistent truth for one formal skill revision; it is not an executable type. */
public record SkillDocument(
        int schemaVersion,
        SkillId skillId,
        SkillRevision revision,
        List<NodeDocument> nodes,
        AppearanceDocument appearance) {
    public static final int CURRENT_SCHEMA_VERSION = 0;
    public static final Codec<SkillDocument> CODEC = CanonicalDocumentCodecs.SKILL_DOCUMENT;

    public SkillDocument {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must be non-negative");
        }
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(revision, "revision");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.size() > MagicSafetyCeilings.MAX_NODES) {
            throw new IllegalArgumentException("nodes exceeds hard ceiling: " + nodes.size());
        }
        Objects.requireNonNull(appearance, "appearance");
    }
}
