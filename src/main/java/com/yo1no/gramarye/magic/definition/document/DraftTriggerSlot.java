package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.util.Objects;

/** Explicitly missing or present draft trigger envelope. */
public sealed interface DraftTriggerSlot permits DraftTriggerSlot.Missing, DraftTriggerSlot.Present {
    static DraftTriggerSlot missing() {
        return Missing.INSTANCE;
    }

    static DraftTriggerSlot present(DefinitionEnvelope definition) {
        return new Present(definition);
    }

    enum Missing implements DraftTriggerSlot {
        INSTANCE
    }

    record Present(DefinitionEnvelope definition) implements DraftTriggerSlot {
        public Present {
            Objects.requireNonNull(definition, "definition");
        }
    }
}
