package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;

interface ActionExecutor {
    ActionExecutorOutcome execute(DamageActionInvocation input);
}

sealed interface ActionExecutorOutcome
        permits ProducedActionRequest, NoActionRequest {}

record ProducedActionRequest(DamageEffectRequest request) implements ActionExecutorOutcome {
    ProducedActionRequest {
        Objects.requireNonNull(request, "request");
    }
}

enum NoActionRequest implements ActionExecutorOutcome {
    INSTANCE
}
