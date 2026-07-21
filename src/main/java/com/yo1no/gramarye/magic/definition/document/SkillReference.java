package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import java.util.Objects;

/** Stable reference to one immutable skill revision. */
public record SkillReference(SkillId skillId, SkillRevision revision) {
    public static final Codec<SkillReference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    CanonicalDocumentCodecs.PERSISTED_SKILL_ID.fieldOf("skill_id").forGetter(SkillReference::skillId),
                    SkillRevision.CODEC.fieldOf("revision").forGetter(SkillReference::revision))
            .apply(instance, SkillReference::new));

    public SkillReference {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(revision, "revision");
    }
}
