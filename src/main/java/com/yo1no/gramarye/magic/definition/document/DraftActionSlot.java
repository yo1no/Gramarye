package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.util.Objects;

/** Explicitly missing or present draft action envelope. */
public sealed interface DraftActionSlot permits DraftActionSlot.Missing, DraftActionSlot.Present {
    static DraftActionSlot missing() {
        return Missing.INSTANCE;
    }

    static DraftActionSlot present(DefinitionEnvelope definition) {
        return new Present(definition);
    }

    enum Missing implements DraftActionSlot {
        INSTANCE
    }

    record Present(DefinitionEnvelope definition) implements DraftActionSlot {
        public Present {
            Objects.requireNonNull(definition, "definition");
        }
    }
}
