package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import java.util.UUID;

/** Stateless production adapter for server-generated candidate skill identities. */
final class RandomUuidSkillIdSource implements SkillIdSource {
    @Override
    public SkillId nextSkillId() {
        return new SkillId(UUID.randomUUID());
    }
}
