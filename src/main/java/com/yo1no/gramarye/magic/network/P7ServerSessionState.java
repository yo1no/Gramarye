package com.yo1no.gramarye.magic.network;

import java.util.Objects;

/** Immutable bounded semantic state for one authenticated connection epoch. */
final class P7ServerSessionState {
    private final P7SessionIdentity identity;
    private final CastIntentAdmissionSemantics.SessionState admissionState;

    private P7ServerSessionState(
            P7SessionIdentity identity,
            CastIntentAdmissionSemantics.SessionState admissionState) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.admissionState = Objects.requireNonNull(admissionState, "admissionState");
    }

    static P7ServerSessionState initial(P7SessionIdentity identity, long authoritativeTick) {
        return new P7ServerSessionState(
                identity,
                CastIntentAdmissionSemantics.SessionState.initial(authoritativeTick));
    }

    P7SessionIdentity identity() {
        return identity;
    }

    CastIntentAdmissionSemantics.SessionState admissionState() {
        return admissionState;
    }

    P7ServerSessionState withAdmissionState(
            CastIntentAdmissionSemantics.SessionState nextAdmissionState) {
        return new P7ServerSessionState(identity, nextAdmissionState);
    }
}
