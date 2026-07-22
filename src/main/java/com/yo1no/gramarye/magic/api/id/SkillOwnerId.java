package com.yo1no.gramarye.magic.api.id;

import java.util.Objects;
import java.util.UUID;

/** Pure domain identity for the owner authorized to submit a skill. */
public record SkillOwnerId(UUID value) {
    public SkillOwnerId {
        Objects.requireNonNull(value, "value");
    }
}
