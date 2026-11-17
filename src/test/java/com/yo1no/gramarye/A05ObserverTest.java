package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class A05ObserverTest {
    @Test
    void recorderFailuresCannotEscapeReceiptOfferOrSubmission() {
        for (var failure : new Throwable[] {new IllegalStateException("recorder"), new AssertionError("recorder")}) {
            var calls = new int[3];
            var observer = new A05Observer(receipt -> {
                calls[0]++;
                if (failure instanceof Error error) { throw error; }
                throw (RuntimeException) failure;
            }, offer -> {
                calls[1]++;
                if (failure instanceof Error error) { throw error; }
                throw (RuntimeException) failure;
            }, submission -> {
                calls[2]++;
                if (failure instanceof Error error) { throw error; }
                throw (RuntimeException) failure;
            });
            assertDoesNotThrow(() -> observer.receipt(10, 8, 3, 2, 1, 4, 0, "CONFIRMED_APPLIED", "NONE"));
            assertDoesNotThrow(() -> observer.offer("OFFER_RETURN", 10, 8, 3, 1, 4, 0,
                    "HIT", 5, 0, 0, 0, 0, "ACCEPTED"));
            assertDoesNotThrow(() -> observer.submission(10, 5, 6, "HIT", 7, "SUBMITTED"));
            assertEquals(1, calls[0]);
            assertEquals(1, calls[1]);
            assertEquals(1, calls[2]);
        }
    }

    @Test
    void scalarSnapshotsKeepReceiptEventAndNativeSendJoinIndependent() {
        var receipts = new ArrayList<A05Observer.ReceiptObservation>();
        var offers = new ArrayList<A05Observer.OfferObservation>();
        var submissions = new ArrayList<A05Observer.SubmissionObservation>();
        var observer = new A05Observer(receipts::add, offers::add, submissions::add);
        observer.receipt(10, 8, 3, 2, 1, 4, 0, "CONFIRMED_APPLIED", "NONE");
        observer.offer("OFFER_RETURN", 10, 8, 3, 1, 4, 0, "HIT", 5, 0, 0, 0, 0, "ACCEPTED");
        observer.offer("LINK", 10, 8, 3, 1, 4, 0, "HIT", 5, 6, 0, 0, 0, "CREATED");
        observer.submission(10, 5, 6, "HIT", 7, "SUBMITTED");
        var receipt = receipts.getFirst();
        var offered = offers.getFirst();
        var linked = offers.getLast();
        var sent = submissions.getFirst();
        assertEquals(receipt.requestId, offered.requestId);
        assertEquals(receipt.sourceEventId, offered.sourceEventId);
        assertEquals(receipt.statementOrdinal, offered.statementOrdinal);
        assertEquals(receipt.instanceId, linked.instanceId);
        assertEquals(0, offered.sequence);
        assertEquals(linked.generation, sent.generation);
        assertEquals(linked.sequence, sent.sequence);
        assertEquals(linked.kind, sent.kind);
        assertEquals("SUBMITTED", sent.outcome);
        assertEquals(7, sent.connectionEpoch);
        assertSame(receipt, receipts.getFirst());
    }
}
