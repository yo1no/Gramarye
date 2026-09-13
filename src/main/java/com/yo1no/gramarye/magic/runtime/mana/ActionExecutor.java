package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;

interface ActionExecutor {
    ActionExecutorOutcome execute(ActionInvocation input);
}

sealed interface ActionExecutorOutcome
        permits ProducedActionRequest, NoActionRequest {}

record ProducedActionRequest(EffectRequest request) implements ActionExecutorOutcome {
    ProducedActionRequest {
        Objects.requireNonNull(request, "request");
    }
}

enum NoActionRequest implements ActionExecutorOutcome {
    INSTANCE
}
