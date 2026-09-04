package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class P7ServerSequenceRateIntegrationTest {
    @Test
    void globalBusyPrecedesAndPreservesPlayerRateAndSequenceState() {
        var session = CastIntentAdmissionSemantics.SessionState.initial(0L);
        var global = IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L);
        for (var index = 0; index < P7NetworkBounds.MAX_GLOBAL_WORK_UNITS_PER_TICK; index++) {
            global = global.consume(0L).nextState();
        }

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 0L, 1L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.SERVER_BUSY, decision.outcome());
        assertEquals(session, decision.nextSessionState());
        assertEquals(global, decision.nextGlobalBudget());
        assertFalse(decision.sequenceConsumed());
        assertFalse(decision.disconnect());
    }

    @Test
    void exactNextConsumesBeforeLaterRootAuthorization() {
        var decision = CastIntentAdmissionSemantics.evaluate(
                CastIntentAdmissionSemantics.SessionState.initial(0L),
                IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L),
                0L,
                1L);
        var rejectedLater = P7AdmissionDispositionMapper.fromRootDisposition(
                new P7SessionIdentity(
                        java.util.UUID.fromString(
                                "00000000-0000-0000-0000-000000000709"),
                        1L),
                1L,
                P7ServerAuthorizationBoundary.AdmissionDisposition.UNKNOWN_SKILL);

        assertEquals(CastIntentAdmissionSemantics.Outcome.ELIGIBLE, decision.outcome());
        assertTrue(decision.sequenceConsumed());
        assertTrue(rejectedLater.sequenceConsumed());
    }

    @Test
    void exhaustedTokenBucketRecordsEightStrikesThenDisconnects() {
        var session = CastIntentAdmissionSemantics.SessionState.initial(0L);
        var global = IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L);
        for (var sequence = 1L; sequence <= 8L; sequence++) {
            var accepted = CastIntentAdmissionSemantics.evaluate(
                    session, global, 0L, sequence);
            session = accepted.nextSessionState();
            global = accepted.nextGlobalBudget();
        }

        for (var strike = 1; strike <= 8; strike++) {
            var decision = CastIntentAdmissionSemantics.evaluate(
                    session, global, 0L, 9L);
            session = decision.nextSessionState();
            global = decision.nextGlobalBudget();
            assertEquals(CastIntentAdmissionSemantics.Outcome.RATE_LIMITED, decision.outcome());
            assertEquals(strike == 8, decision.disconnect());
            assertFalse(decision.sequenceConsumed());
        }
    }

    @Test
    void tickRegressionCommitsNoPartialTransition() {
        var session = CastIntentAdmissionSemantics.SessionState.initial(10L);
        var global = IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 10L);

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 9L, 1L);

        assertEquals(
                CastIntentAdmissionSemantics.Outcome.INTERNAL_SERVER_FAULT,
                decision.outcome());
        assertEquals(session, decision.nextSessionState());
        assertEquals(global, decision.nextGlobalBudget());
        assertFalse(decision.sequenceConsumed());
    }

    @Test
    void rateAndSequenceStateRemainIsolatedAcrossSessions() {
        var first = CastIntentAdmissionSemantics.SessionState.initial(0L);
        var second = CastIntentAdmissionSemantics.SessionState.initial(0L);
        var global = IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L);

        var firstDecision = CastIntentAdmissionSemantics.evaluate(first, global, 0L, 1L);

        assertEquals(2L, firstDecision.nextSessionState()
                .sequenceState().expectedNext().orElseThrow());
        assertEquals(1L, second.sequenceState().expectedNext().orElseThrow());
        assertEquals(7, firstDecision.nextSessionState().tokenBucket().tokens());
        assertEquals(8, second.tokenBucket().tokens());
    }
}
