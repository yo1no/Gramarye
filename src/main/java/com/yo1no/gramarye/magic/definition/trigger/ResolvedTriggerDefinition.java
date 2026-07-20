package com.yo1no.gramarye.magic.definition.trigger;

import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** A trigger definition whose raw payload decoded completely with its registered descriptor. */
public record ResolvedTriggerDefinition<P extends TriggerPayload>(
        TriggerType<P> descriptor,
        int schemaVersion,
        P payload) implements TriggerDefinition {
    public ResolvedTriggerDefinition {
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
