package com.yo1no.gramarye.magic.definition.resolution;

import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationFailure;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.UnknownTriggerDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.util.Objects;

/** Transient Trigger resolution state; no variant is itself runtime-executable. */
public sealed interface TriggerResolution
        permits TriggerResolution.Resolved,
                TriggerResolution.Unknown,
                TriggerResolution.MigrationFailed,
                TriggerResolution.DecodeFailed {
    record Resolved<P extends TriggerPayload>(
            DefinitionEnvelope sourceEnvelope,
            ResolvedTriggerDefinition<P> definition) implements TriggerResolution {
        public Resolved {
            Objects.requireNonNull(sourceEnvelope, "sourceEnvelope");
            Objects.requireNonNull(definition, "definition");
        }
    }

    record Unknown(UnknownTriggerDefinition definition) implements TriggerResolution {
        public Unknown {
            Objects.requireNonNull(definition, "definition");
        }
    }

    record MigrationFailed(
            DefinitionEnvelope originalEnvelope,
            TriggerType<?> descriptor,
            PayloadMigrationFailure failure) implements TriggerResolution {
        public MigrationFailed {
            Objects.requireNonNull(originalEnvelope, "originalEnvelope");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(failure, "failure");
        }
    }

    record DecodeFailed(
            DefinitionEnvelope originalEnvelope,
            TriggerType<?> descriptor,
            DefinitionFailure failure) implements TriggerResolution {
        public DecodeFailed {
            Objects.requireNonNull(originalEnvelope, "originalEnvelope");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(failure, "failure");
        }
    }
}
