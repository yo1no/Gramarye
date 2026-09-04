package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class P7AdmissionDispositionMapperTest {
    private static final P7SessionIdentity IDENTITY = new P7SessionIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000703"), 7L);

    @Test
    void allEightRootDispositionsMapWithoutOrdinalOrDefaultFallback() {
        var expected = new EnumMap<
                P7ServerAuthorizationBoundary.AdmissionDisposition,
                IntentAcknowledgement.Disposition>(
                P7ServerAuthorizationBoundary.AdmissionDisposition.class);
        expected.put(
                P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                IntentAcknowledgement.Disposition.ACCEPTED);
        expected.put(
                P7ServerAuthorizationBoundary.AdmissionDisposition.UNKNOWN_SKILL,
                IntentAcknowledgement.Disposition.REJECTED);
        expected.put(
                P7ServerAuthorizationBoundary.AdmissionDisposition.UNAUTHORIZED_INTENT,
                IntentAcknowledgement.Disposition.REJECTED);
        expected.put(
                P7ServerAuthorizationBoundary.AdmissionDisposition.INVALID_TARGET,
                IntentAcknowledgement.Disposition.REJECTED);
        expected.put(
                P7ServerAuthorizationBoundary.AdmissionDisposition.TARGET_UNAVAILABLE,
                IntentAcknowledgement.Disposition.REJECTED);
        expected.put(
                P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED,
                IntentAcknowledgement.Disposition.REJECTED);
        expected.put(
                P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE,
                IntentAcknowledgement.Disposition.UNAVAILABLE);

        for (var disposition : P7ServerAuthorizationBoundary.AdmissionDisposition.values()) {
            var result = P7AdmissionDispositionMapper.fromRootDisposition(
                    IDENTITY, 11L, disposition);
            assertTrue(result.sequenceConsumed(), disposition.name());
            if (disposition
                    == P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT) {
                assertTrue(result.acknowledgementCandidate().isEmpty());
            } else {
                assertEquals(
                        expected.get(disposition),
                        result.acknowledgementCandidate().orElseThrow().disposition());
            }
        }
    }

    @Test
    void P5AttemptAndAcceptanceFlagsRemainCoarseAndBounded() {
        var accepted = P7AdmissionDispositionMapper.fromRootDisposition(
                IDENTITY,
                1L,
                P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED);
        var rejected = P7AdmissionDispositionMapper.fromRootDisposition(
                IDENTITY,
                2L,
                P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED);
        var beforeP5 = P7AdmissionDispositionMapper.fromRootDisposition(
                IDENTITY,
                3L,
                P7ServerAuthorizationBoundary.AdmissionDisposition.UNKNOWN_SKILL);
        var unavailable = P7AdmissionDispositionMapper.fromRootDisposition(
                IDENTITY,
                4L,
                P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE);

        assertTrue(accepted.p5AdmissionAttemptKnown());
        assertTrue(accepted.p5AdmissionAttempted());
        assertTrue(accepted.p5AdmissionAccepted());
        assertTrue(rejected.p5AdmissionAttemptKnown());
        assertTrue(rejected.p5AdmissionAttempted());
        assertFalse(rejected.p5AdmissionAccepted());
        assertTrue(beforeP5.p5AdmissionAttemptKnown());
        assertFalse(beforeP5.p5AdmissionAttempted());
        assertFalse(beforeP5.p5AdmissionAccepted());
        assertFalse(unavailable.p5AdmissionAttemptKnown());
        assertFalse(unavailable.p5AdmissionAttempted());
        assertFalse(unavailable.p5AdmissionAccepted());
    }

    @Test
    void eligibleS1DecisionMustProceedToRootAuthorization() {
        var decision = CastIntentAdmissionSemantics.evaluate(
                CastIntentAdmissionSemantics.SessionState.initial(0L),
                IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L),
                0L,
                1L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.ELIGIBLE, decision.outcome());
        assertThrows(
                P7SemanticInvariantException.class,
                () -> P7AdmissionDispositionMapper.fromAdmissionSemantics(
                        IDENTITY, 1L, decision));
    }

    @Test
    void reloadResultIsUnconsumedUnavailableWithRepairScalar() {
        var current = CastIntentAdmissionSemantics.SessionState.initial(0L)
                .sequenceState()
                .expectedNext();
        var result = P7AdmissionDispositionMapper.reloadInProgress(
                IDENTITY, 1L, current);
        var acknowledgement = result.acknowledgementCandidate().orElseThrow();
        var serverUnavailable = P7AdmissionDispositionMapper.serverUnavailable(
                IDENTITY, 1L, current);
        var serverUnavailableAcknowledgement =
                serverUnavailable.acknowledgementCandidate().orElseThrow();

        assertEquals(
                P7IntentFailureReason.RELOAD_IN_PROGRESS,
                result.failureReason().orElseThrow());
        assertEquals(
                IntentAcknowledgement.Disposition.UNAVAILABLE,
                acknowledgement.disposition());
        assertFalse(result.sequenceConsumed());
        assertTrue(acknowledgement.expectedNext().isPresent());
        assertEquals(
                0,
                acknowledgement.flags() & IntentAcknowledgement.RESYNC_RECOMMENDED);
        assertEquals(
                P7IntentFailureReason.P5_UNAVAILABLE,
                serverUnavailable.failureReason().orElseThrow());
        assertEquals(
                IntentAcknowledgement.Disposition.UNAVAILABLE,
                serverUnavailableAcknowledgement.disposition());
        assertFalse(serverUnavailable.sequenceConsumed());
        assertTrue(serverUnavailableAcknowledgement.expectedNext().isPresent());
        assertTrue((serverUnavailableAcknowledgement.flags()
                & IntentAcknowledgement.RESYNC_RECOMMENDED) != 0);
    }
}
