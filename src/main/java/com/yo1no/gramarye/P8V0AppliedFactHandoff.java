package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0TerminalReceipt;
import java.util.Objects;

/** One observer call over the current accepted fact. */
final class P8V0AppliedFactHandoff {
    private final P8ServerPresentationService service;

    P8V0AppliedFactHandoff(P8ServerPresentationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    void offer(V0TerminalReceipt receipt, P9V0WorldEffectHandoff nativeCall) {
        try {
            var material = nativeCall.consumeApplied(receipt);
            if (material.isPresent()) {
                service.offerV0Applied(material.orElseThrow());
            }
        } catch (RuntimeException ignored) {
            service.recordObserverRuntimeException();
        }
    }
}
