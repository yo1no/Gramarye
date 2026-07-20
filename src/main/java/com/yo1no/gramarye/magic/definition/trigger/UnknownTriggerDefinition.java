package com.yo1no.gramarye.magic.definition.trigger;

import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import java.util.Objects;

/** An unresolved trigger definition that retains its complete original envelope. */
public record UnknownTriggerDefinition(
        DefinitionEnvelope envelope,
        DefinitionFailure failure) implements TriggerDefinition {
    public UnknownTriggerDefinition {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(failure, "failure");
    }
}
