package com.yo1no.gramarye.magic.network;

import java.util.Objects;

/** Immutable bounded semantic state for one authenticated connection epoch. */
final class P7ServerSessionState {
    private final P7SessionIdentity identity;
    private final CastIntentAdmissionSemantics.SessionState admissionState;
    private final P7ServerSyncState syncState;

    private P7ServerSessionState(
            P7SessionIdentity identity,
            CastIntentAdmissionSemantics.SessionState admissionState,
            P7ServerSyncState syncState) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.admissionState = Objects.requireNonNull(admissionState, "admissionState");
        this.syncState = Objects.requireNonNull(syncState, "syncState");
    }

    static P7ServerSessionState initial(P7SessionIdentity identity, long authoritativeTick) {
        return new P7ServerSessionState(
                identity,
                CastIntentAdmissionSemantics.SessionState.initial(authoritativeTick),
                P7ServerSyncState.initial(authoritativeTick));
    }

    P7SessionIdentity identity() {
        return identity;
    }

    CastIntentAdmissionSemantics.SessionState admissionState() {
        return admissionState;
    }

    P7ServerSessionState withAdmissionState(
            CastIntentAdmissionSemantics.SessionState nextAdmissionState) {
        return new P7ServerSessionState(identity, nextAdmissionState, syncState);
    }

    P7ServerSyncState syncState() {
        return syncState;
    }

    P7ServerSessionState withSyncState(P7ServerSyncState nextSyncState) {
        return new P7ServerSessionState(identity, admissionState, nextSyncState);
    }
}
