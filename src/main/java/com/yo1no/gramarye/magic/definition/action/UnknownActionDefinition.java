package com.yo1no.gramarye.magic.definition.action;

import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import java.util.Objects;

/** An unresolved action definition that retains its complete original envelope. */
public record UnknownActionDefinition(
        DefinitionEnvelope envelope,
        DefinitionFailure failure) implements ActionDefinition {
    public UnknownActionDefinition {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(failure, "failure");
    }
}
