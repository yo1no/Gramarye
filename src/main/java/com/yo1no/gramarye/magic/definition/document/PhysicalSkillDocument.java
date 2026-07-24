package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Package-internal typed representation of one current family-tagged physical document. */
record PhysicalSkillDocument(
        int schemaVersion,
        SkillId skillId,
        SkillRevision revision,
        List<PhysicalNodeDocument> nodes,
        PhysicalTopAppearance appearance) {
    PhysicalSkillDocument {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must be non-negative");
        }
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(revision, "revision");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.isEmpty() || nodes.size() > MagicSafetyCeilings.MAX_NODES) {
            throw new IllegalArgumentException("physical document nodes must be within the hard count range");
        }
        Objects.requireNonNull(appearance, "appearance");
    }

    @Override
    public String toString() {
        return "PhysicalSkillDocument[schemaVersion=" + schemaVersion
                + ", nodeCount=" + nodes.size()
                + ", appearanceState=" + appearance.stateName() + "]";
    }
}

record PhysicalNodeDocument(
        PhysicalDefinitionEnvelope trigger,
        PhysicalDefinitionEnvelope action,
        PhysicalAppearanceOverride appearanceOverride) {
    PhysicalNodeDocument {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(appearanceOverride, "appearanceOverride");
    }

    @Override
    public String toString() {
        return "PhysicalNodeDocument[appearanceOverride=" + appearanceOverride.stateName() + "]";
    }
}

record PhysicalDefinitionEnvelope(
        ResourceLocation typeId,
        int schemaVersion,
        RawTreeEnvelope payload) {
    PhysicalDefinitionEnvelope {
        Objects.requireNonNull(typeId, "typeId");
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must be non-negative");
        }
        Objects.requireNonNull(payload, "payload");
    }

    @Override
    public String toString() {
        return "PhysicalDefinitionEnvelope[schemaVersion=" + schemaVersion
                + ", payload=" + payload + "]";
    }
}

sealed interface PhysicalTopAppearance
        permits PhysicalTopAppearance.Default,
                PhysicalTopAppearance.Decoded,
                PhysicalTopAppearance.Unparsed {
    String stateName();

    enum Default implements PhysicalTopAppearance {
        INSTANCE;

        @Override
        public String stateName() {
            return "default";
        }
    }

    record Decoded(AppearanceDefinition definition) implements PhysicalTopAppearance {
        public Decoded {
            Objects.requireNonNull(definition, "definition");
            if (definition.isEmpty()) {
                throw new IllegalArgumentException("decoded top appearance must not be empty");
            }
        }

        @Override
        public String stateName() {
            return "decoded";
        }

        @Override
        public String toString() {
            return "PhysicalTopAppearance.Decoded";
        }
    }

    record Unparsed(RawTreeEnvelope raw) implements PhysicalTopAppearance {
        public Unparsed {
            Objects.requireNonNull(raw, "raw");
        }

        @Override
        public String stateName() {
            return "unparsed";
        }

        @Override
        public String toString() {
            return "PhysicalTopAppearance.Unparsed";
        }
    }
}

sealed interface PhysicalAppearanceOverride
        permits PhysicalAppearanceOverride.None,
                PhysicalAppearanceOverride.Decoded,
                PhysicalAppearanceOverride.Unparsed {
    String stateName();

    enum None implements PhysicalAppearanceOverride {
        INSTANCE;

        @Override
        public String stateName() {
            return "none";
        }
    }

    record Decoded(AppearanceOverride override) implements PhysicalAppearanceOverride {
        public Decoded {
            Objects.requireNonNull(override, "override");
            if (override.isEmpty()) {
                throw new IllegalArgumentException("decoded appearance override must not be empty");
            }
        }

        @Override
        public String stateName() {
            return "decoded";
        }

        @Override
        public String toString() {
            return "PhysicalAppearanceOverride.Decoded";
        }
    }

    record Unparsed(RawTreeEnvelope raw) implements PhysicalAppearanceOverride {
        public Unparsed {
            Objects.requireNonNull(raw, "raw");
        }

        @Override
        public String stateName() {
            return "unparsed";
        }

        @Override
        public String toString() {
            return "PhysicalAppearanceOverride.Unparsed";
        }
    }
}
