package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Package-internal current physical Draft DTO; it is independent from SkillDocument. */
record PhysicalSkillDraft(
        int draftSchemaVersion,
        SkillId skillId,
        Optional<SkillRevision> baseRevision,
        List<PhysicalDraftNode> nodes,
        PhysicalTopAppearance appearance) {
    PhysicalSkillDraft {
        if (draftSchemaVersion < 0) {
            throw new IllegalArgumentException("draftSchemaVersion must be non-negative");
        }
        Objects.requireNonNull(skillId, "skillId");
        baseRevision = Objects.requireNonNull(baseRevision, "baseRevision");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.size() > MagicSafetyCeilings.MAX_NODES) {
            throw new IllegalArgumentException("physical Draft nodes exceed the hard ceiling");
        }
        Objects.requireNonNull(appearance, "appearance");
    }

    @Override
    public String toString() {
        return "PhysicalSkillDraft[draftSchemaVersion=" + draftSchemaVersion
                + ", nodeCount=" + nodes.size()
                + ", appearanceState=" + appearance.stateName() + "]";
    }
}

record PhysicalDraftNode(
        PhysicalDraftTriggerSlot trigger,
        PhysicalDraftActionSlot action,
        PhysicalAppearanceOverride appearanceOverride) {
    PhysicalDraftNode {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(appearanceOverride, "appearanceOverride");
    }
}

sealed interface PhysicalDraftTriggerSlot
        permits PhysicalDraftTriggerSlot.Missing, PhysicalDraftTriggerSlot.Present {
    enum Missing implements PhysicalDraftTriggerSlot {
        INSTANCE
    }

    record Present(PhysicalDefinitionEnvelope definition) implements PhysicalDraftTriggerSlot {
        public Present {
            Objects.requireNonNull(definition, "definition");
        }
    }
}

sealed interface PhysicalDraftActionSlot
        permits PhysicalDraftActionSlot.Missing, PhysicalDraftActionSlot.Present {
    enum Missing implements PhysicalDraftActionSlot {
        INSTANCE
    }

    record Present(PhysicalDefinitionEnvelope definition) implements PhysicalDraftActionSlot {
        public Present {
            Objects.requireNonNull(definition, "definition");
        }
    }
}
