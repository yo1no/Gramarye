package com.yo1no.gramarye;

import java.util.function.Consumer;

/** Closed, read-only scalar observations; production always uses NOOP. */
final class A05Observer {
    static final A05Observer NOOP = new A05Observer(null, null, null);

    private final Consumer<ReceiptObservation> receipts;
    private final Consumer<OfferObservation> offers;
    private final Consumer<SubmissionObservation> submissions;

    A05Observer(Consumer<ReceiptObservation> receipts, Consumer<OfferObservation> offers,
            Consumer<SubmissionObservation> submissions) {
        this.receipts = receipts;
        this.offers = offers;
        this.submissions = submissions;
    }

    void receipt(int nativeTick, long runtimeTick, long instanceId, long lineageId,
            long requestId, long sourceEventId, int statementOrdinal, String terminal, String reason) {
        try {
            if (receipts != null) {
                receipts.accept(new ReceiptObservation(nativeTick, runtimeTick, instanceId, lineageId,
                        requestId, sourceEventId, statementOrdinal, terminal, reason));
            }
        } catch (RuntimeException | Error ignoredObservationFailure) {
            // Observation cannot change the already fixed receipt or its original consumer.
        }
    }

    void offer(String phase, int nativeTick, long runtimeTick, long instanceId,
            long requestId, long sourceEventId, int statementOrdinal, String kind,
            long generation, long sequence, int selectedRecipients, int candidateDeliveries,
            int admittedDeliveries, String outcome) {
        try {
            if (offers != null) {
                offers.accept(new OfferObservation(phase, nativeTick, runtimeTick, instanceId,
                        requestId, sourceEventId, statementOrdinal, kind, generation, sequence,
                        selectedRecipients, candidateDeliveries, admittedDeliveries, outcome));
            }
        } catch (RuntimeException | Error ignoredObservationFailure) {
            // Original offer, selection and budget decisions remain owned by P8.
        }
    }

    void submission(int nativeTick, long generation, long sequence, String kind,
            long connectionEpoch, String outcome) {
        try {
            if (submissions != null) {
                submissions.accept(new SubmissionObservation(nativeTick, generation, sequence,
                        kind, connectionEpoch, outcome));
            }
        } catch (RuntimeException | Error ignoredObservationFailure) {
            // Original transport return or Throwable is never replaced by an observer failure.
        }
    }

    static final class ReceiptObservation {
        final int nativeTick;
        final long runtimeTick;
        final long instanceId;
        final long lineageId;
        final long requestId;
        final long sourceEventId;
        final int statementOrdinal;
        final String terminal;
        final String reason;

        ReceiptObservation(int nativeTick, long runtimeTick, long instanceId, long lineageId,
                long requestId, long sourceEventId, int statementOrdinal, String terminal, String reason) {
            this.nativeTick = nativeTick;
            this.runtimeTick = runtimeTick;
            this.instanceId = instanceId;
            this.lineageId = lineageId;
            this.requestId = requestId;
            this.sourceEventId = sourceEventId;
            this.statementOrdinal = statementOrdinal;
            this.terminal = terminal;
            this.reason = reason;
        }
    }

    static final class OfferObservation {
        final String phase;
        final int nativeTick;
        final long runtimeTick;
        final long instanceId;
        final long requestId;
        final long sourceEventId;
        final int statementOrdinal;
        final String kind;
        final long generation;
        final long sequence;
        final int selectedRecipients;
        final int candidateDeliveries;
        final int admittedDeliveries;
        final String outcome;

        OfferObservation(String phase, int nativeTick, long runtimeTick, long instanceId,
                long requestId, long sourceEventId, int statementOrdinal, String kind,
                long generation, long sequence, int selectedRecipients, int candidateDeliveries,
                int admittedDeliveries, String outcome) {
            this.phase = phase;
            this.nativeTick = nativeTick;
            this.runtimeTick = runtimeTick;
            this.instanceId = instanceId;
            this.requestId = requestId;
            this.sourceEventId = sourceEventId;
            this.statementOrdinal = statementOrdinal;
            this.kind = kind;
            this.generation = generation;
            this.sequence = sequence;
            this.selectedRecipients = selectedRecipients;
            this.candidateDeliveries = candidateDeliveries;
            this.admittedDeliveries = admittedDeliveries;
            this.outcome = outcome;
        }
    }

    static final class SubmissionObservation {
        final int nativeTick;
        final long generation;
        final long sequence;
        final String kind;
        final long connectionEpoch;
        final String outcome;

        SubmissionObservation(int nativeTick, long generation, long sequence, String kind,
                long connectionEpoch, String outcome) {
            this.nativeTick = nativeTick;
            this.generation = generation;
            this.sequence = sequence;
            this.kind = kind;
            this.connectionEpoch = connectionEpoch;
            this.outcome = outcome;
        }
    }
}
