package com.yo1no.gramarye.magic.definition.action;

/** An action payload that is either typed and resolved or preserved for later recovery. */
public sealed interface ActionDefinition
        permits ResolvedActionDefinition, UnknownActionDefinition {
}
