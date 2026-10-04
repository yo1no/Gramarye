package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P11C4aUiInputProbe;

/** Excluded package-local payload reader. Never constructs a payload, sequence, sender or session. */
public final class P11C4aUiInputP9Observation {
    private P11C4aUiInputP9Observation() { }
    public static void submitted(Object value) {
        if (value instanceof CastIntentPayload payload) {
            var intent = payload.intent();
            P11C4aUiInputProbe.castSubmitted(intent.sequence(), intent.slot(), intent.presenceMask(),
                    intent.aimHint().isEmpty() && intent.entityHint().isEmpty());
        }
    }
}
