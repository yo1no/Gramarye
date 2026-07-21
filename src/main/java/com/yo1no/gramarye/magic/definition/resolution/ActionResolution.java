package com.yo1no.gramarye.magic.definition.resolution;

import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.action.UnknownActionDefinition;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationFailure;
import java.util.Objects;

/** Transient Action resolution state; no variant is itself runtime-executable. */
public sealed interface ActionResolution
        permits ActionResolution.Resolved,
                ActionResolution.Unknown,
                ActionResolution.MigrationFailed,
                ActionResolution.DecodeFailed {
    record Resolved<P extends ActionPayload>(
            DefinitionEnvelope sourceEnvelope,
            ResolvedActionDefinition<P> definition) implements ActionResolution {
        public Resolved {
            Objects.requireNonNull(sourceEnvelope, "sourceEnvelope");
            Objects.requireNonNull(definition, "definition");
        }
    }

    record Unknown(UnknownActionDefinition definition) implements ActionResolution {
        public Unknown {
            Objects.requireNonNull(definition, "definition");
        }
    }

    record MigrationFailed(
            DefinitionEnvelope originalEnvelope,
            ActionType<?> descriptor,
            PayloadMigrationFailure failure) implements ActionResolution {
        public MigrationFailed {
            Objects.requireNonNull(originalEnvelope, "originalEnvelope");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(failure, "failure");
        }
    }

    record DecodeFailed(
            DefinitionEnvelope originalEnvelope,
            ActionType<?> descriptor,
            DefinitionFailure failure) implements ActionResolution {
        public DecodeFailed {
            Objects.requireNonNull(originalEnvelope, "originalEnvelope");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(failure, "failure");
        }
    }
}
