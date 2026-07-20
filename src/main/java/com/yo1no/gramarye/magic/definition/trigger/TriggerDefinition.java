package com.yo1no.gramarye.magic.definition.trigger;

/** A trigger payload that is either typed and resolved or preserved for later recovery. */
public sealed interface TriggerDefinition
        permits ResolvedTriggerDefinition, UnknownTriggerDefinition {
}
