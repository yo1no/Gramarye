package com.yo1no.gramarye.magic.definition.action;

import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** An action definition whose raw payload decoded completely with its registered descriptor. */
public record ResolvedActionDefinition<P extends ActionPayload>(
        ActionType<P> descriptor,
        int schemaVersion,
        P payload) implements ActionDefinition {
    public ResolvedActionDefinition {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(payload, "payload");
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must not be negative");
        }
        if (descriptor.currentPayloadSchemaVersion() != schemaVersion) {
            throw new IllegalArgumentException("schemaVersion must match the descriptor's current payload schema");
        }
    }

    /** Runs semantic validation explicitly; Codec decode never calls this method. */
    public ValidationResult validate(ValidationContext context) {
        return descriptor.validate(payload, Objects.requireNonNull(context, "context"));
    }
}
