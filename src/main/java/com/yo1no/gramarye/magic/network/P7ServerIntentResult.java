package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.Optional;

/** Immutable scalar-only result produced on the server thread for a future S4 sink. */
record P7ServerIntentResult(
        P7SessionIdentity sessionIdentity,
        long receivedSequence,
        Optional<P7IntentFailureReason> failureReason,
        Optional<IntentAcknowledgement> acknowledgementCandidate,
        boolean sequenceConsumed,
        boolean disconnectRequested,
        boolean p5AdmissionAttempted,
        boolean p5AdmissionAttemptKnown,
        boolean p5AdmissionAccepted) {
    P7ServerIntentResult {
        Objects.requireNonNull(sessionIdentity, "sessionIdentity");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        acknowledgementCandidate = Objects.requireNonNull(
                acknowledgementCandidate, "acknowledgementCandidate");
        if (!p5AdmissionAttemptKnown
                && (p5AdmissionAttempted || p5AdmissionAccepted)) {
            throw new P7SemanticInvariantException(
                    "indeterminate P5 admission cannot claim an outcome");
        }
        if (p5AdmissionAccepted
                && (!p5AdmissionAttemptKnown || !p5AdmissionAttempted)) {
            throw new P7SemanticInvariantException(
                    "accepted P5 admission was not attempted");
        }
        if (failureReason.isEmpty() != p5AdmissionAccepted) {
            throw new P7SemanticInvariantException(
                    "only an accepted P5 admission has no failure reason");
        }
        if (acknowledgementCandidate.isPresent()
                && acknowledgementCandidate.orElseThrow().sequence() != receivedSequence) {
            throw new P7SemanticInvariantException(
                    "acknowledgement sequence differs from the received sequence");
        }
    }
}
