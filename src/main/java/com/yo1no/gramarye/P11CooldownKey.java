package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillId;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Closed data routing, never an actor, catalog identity, or admission capability. */
sealed interface P11CooldownKey {
    Comparator<P11CooldownKey> ORDER = (left, right) -> {
        if (left instanceof LegacySkill a) {
            if (!(right instanceof LegacySkill b)) { return -1; }
            UUID x = a.skillId().value(), y = b.skillId().value();
            int high = Long.compareUnsigned(x.getMostSignificantBits(), y.getMostSignificantBits());
            return high != 0 ? high : Long.compareUnsigned(x.getLeastSignificantBits(), y.getLeastSignificantBits());
        }
        if (right instanceof LegacySkill) { return 1; }
        // The grammar is ASCII, so String's lexicographic order is exactly byte order.
        return ((LogicalPolicy) left).policyName().toString().compareTo(((LogicalPolicy) right).policyName().toString());
    };

    record LegacySkill(SkillId skillId) implements P11CooldownKey {
        public LegacySkill { Objects.requireNonNull(skillId, "skillId"); }
    }
    record LogicalPolicy(ResourceLocation policyName) implements P11CooldownKey {
        public LogicalPolicy {
            Objects.requireNonNull(policyName, "policyName");
            if (!validName(policyName.toString())) { throw new IllegalArgumentException("COOLDOWN_POLICY_KEY"); }
        }
    }

    static LegacySkill legacy(UUID id) { return new LegacySkill(new SkillId(id)); }

    static LogicalPolicy parsePolicy(String value) {
        if (!validName(value)) { return null; }
        int split = value.indexOf(':');
        return new LogicalPolicy(ResourceLocation.fromNamespaceAndPath(value.substring(0, split), value.substring(split + 1)));
    }

    private static boolean validName(String value) {
        if (value == null || value.length() < 3 || value.length() > 128) { return false; }
        int split = value.indexOf(':');
        if (split <= 0 || split == value.length() - 1 || value.indexOf(':', split + 1) >= 0) { return false; }
        for (int index = 0; index < value.length(); index++) {
            if (index == split) { continue; }
            char c = value.charAt(index);
            boolean common = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-';
            if (!common && !(index > split && c == '/')) { return false; }
        }
        return true;
    }
}
