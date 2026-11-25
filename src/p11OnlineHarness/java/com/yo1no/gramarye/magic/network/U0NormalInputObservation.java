package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.U0OrdinaryTrace;

/** Qualification-only same-package scalar reader. Never constructs or sends a payload. */
public final class U0NormalInputObservation {
    private U0NormalInputObservation() { }
    public static void submitted(Object payload) {
        if (payload instanceof CastCurrentSpellPayload actual) {
            U0OrdinaryTrace.note("N1_SEND_RETURN", actual.sequence(), 0, 0, 0, 0, 0, 0, 0, 0, "SOLE_POST_SENDER");
        } else {
            U0OrdinaryTrace.note("INVALID_N1_OBJECT", 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
        }
    }
}
