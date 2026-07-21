package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Codec;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.util.Objects;

/** One persistent skill node. Its zero-based list position is its only node index. */
public record NodeDocument(
        DefinitionEnvelope trigger,
        DefinitionEnvelope action,
        AppearanceOverrideDocument appearanceOverride) {
    public static final Codec<NodeDocument> CODEC = CanonicalDocumentCodecs.NODE;

    public NodeDocument {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(appearanceOverride, "appearanceOverride");
    }
}
