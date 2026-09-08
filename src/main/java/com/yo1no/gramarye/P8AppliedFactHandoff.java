package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import java.util.Objects;

/** Call-scoped handoff from finalized P6 applied truth to the root P8 owner. */
final class P8AppliedFactHandoff
        implements P6RuntimeExecutionBridge.AppliedFactObserver {
    private final P8ServerPresentationService service;
    private final RuntimeEvent event;
    private final RuntimeExecutionContext context;

    P8AppliedFactHandoff(
            P8ServerPresentationService service,
            RuntimeEvent event,
            RuntimeExecutionContext context) {
        this.service = Objects.requireNonNull(service, "service");
        this.event = Objects.requireNonNull(event, "event");
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void observe(P6RuntimeExecutionBridge.AppliedFact fact) {
        try {
            service.offerApplied(event, context, fact);
        } catch (RuntimeException ignored) {
            service.recordObserverRuntimeException();
        }
    }
}
