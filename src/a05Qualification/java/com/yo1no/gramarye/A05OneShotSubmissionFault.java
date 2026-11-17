package com.yo1no.gramarye;

/** One named non-distribution negative boundary; its retained state is scalar only. */
final class A05OneShotSubmissionFault
        extends P8ServerPresentationService.NativeV0SubmissionBoundary {
    final RuntimeException plannedFault =
            new RuntimeException("A05_VALIDATED_PRE_NATIVE_SEND_BOUNDARY_FAULT");

    boolean armed;
    boolean consumed;
    int expectedNativeTick;
    long expectedRuntimeTick;
    long expectedInstanceId;
    long expectedLineageId;
    long expectedRequestId;
    long expectedSourceEventId;
    long expectedGeneration;
    long selectedEpoch;
    long otherEpoch;
    long rootSequence;
    long boundSequence;

    int expectCalls;
    int beforeSendCalls;
    int faultCount;
    int unarmedPasses;
    int unrelatedPasses;
    int otherRecipientPasses;
    int rejectedEvent;
    int rejectedGeneration;
    int rejectedRecipient;
    int rejectedSequence;
    int rejectedLink;
    int rejectedAdmission;
    int rejectedSecondUse;
    int rejectedExpectation;

    A05OneShotSubmissionFault() {}

    void expect(A05Observer.ReceiptObservation selectedConfirmedHit,
            long catalogGeneration, long selectedConnectionEpoch) {
        expectCalls++;
        if (expectCalls != 1 || armed || consumed) {
            rejectedExpectation++;
            throw new IllegalStateException("A05_BOUNDARY_EXPECT_REUSED");
        }
        if (selectedConfirmedHit == null || selectedConfirmedHit.nativeTick <= 0
                || selectedConfirmedHit.runtimeTick < 0 || selectedConfirmedHit.instanceId <= 0
                || selectedConfirmedHit.lineageId <= 0 || selectedConfirmedHit.requestId <= 0
                || selectedConfirmedHit.sourceEventId <= 0 || selectedConfirmedHit.statementOrdinal != 0
                || !selectedConfirmedHit.terminal.equals("CONFIRMED_APPLIED")
                || !selectedConfirmedHit.reason.equals("NONE")
                || catalogGeneration <= 0 || selectedConnectionEpoch <= 0
                || A05Qualification.recorder.overflow != 0 || A05Qualification.recorder.invalid != 0
                || A05Qualification.recorder.receipts.size() > A05QualificationRecorder.MAX_RECORDS
                || A05Qualification.recorder.offers.size() > A05QualificationRecorder.MAX_RECORDS
                || A05Qualification.recorder.submissions.size() > A05QualificationRecorder.MAX_RECORDS) {
            rejectedExpectation++;
            throw new IllegalArgumentException("A05_BOUNDARY_EXPECT_INVALID");
        }

        A05Observer.ReceiptObservation root = null;
        int rootReceipts = 0;
        int selectedReceipts = 0;
        int selectedTupleReceipts = 0;
        for (var receipt : A05Qualification.recorder.receipts) {
            if (receipt == selectedConfirmedHit) { selectedReceipts++; }
            if (receipt.nativeTick == selectedConfirmedHit.nativeTick
                    && receipt.runtimeTick == selectedConfirmedHit.runtimeTick
                    && receipt.instanceId == selectedConfirmedHit.instanceId
                    && receipt.requestId == selectedConfirmedHit.requestId
                    && receipt.sourceEventId == selectedConfirmedHit.sourceEventId
                    && receipt.statementOrdinal == selectedConfirmedHit.statementOrdinal) {
                selectedTupleReceipts++;
            }
            if (receipt.lineageId == selectedConfirmedHit.lineageId
                    && receipt.instanceId == selectedConfirmedHit.instanceId
                    && receipt.statementOrdinal == -1
                    && receipt.terminal.equals("CONFIRMED_APPLIED") && receipt.reason.equals("NONE")
                    && receipt.nativeTick > 0 && receipt.nativeTick < selectedConfirmedHit.nativeTick
                    && receipt.runtimeTick <= selectedConfirmedHit.runtimeTick) {
                rootReceipts++;
                root = receipt;
            }
        }
        if (selectedReceipts != 1 || selectedTupleReceipts != 1 || rootReceipts != 1 || root == null) {
            rejectedExpectation++;
            throw new IllegalArgumentException("A05_BOUNDARY_ORIGINAL_RECEIPTS_UNPROVED");
        }

        int selectedOffers = 0;
        int selectedLinks = 0;
        int rootOffers = 0;
        int rootLinks = 0;
        long observedRootSequence = 0L;
        for (var offer : A05Qualification.recorder.offers) {
            if (offer.instanceId == selectedConfirmedHit.instanceId
                    && offer.requestId == selectedConfirmedHit.requestId
                    && offer.sourceEventId == selectedConfirmedHit.sourceEventId
                    && offer.statementOrdinal == selectedConfirmedHit.statementOrdinal) {
                if (offer.phase.equals("LINK")) { selectedLinks++; }
                if (offer.phase.equals("OFFER_RETURN") && offer.kind.equals("HIT")
                        && offer.outcome.equals("ACCEPTED") && offer.generation == catalogGeneration
                        && offer.nativeTick == selectedConfirmedHit.nativeTick
                        && offer.runtimeTick == selectedConfirmedHit.runtimeTick) {
                    selectedOffers++;
                }
            }
            if (offer.instanceId == root.instanceId && offer.requestId == root.requestId
                    && offer.sourceEventId == root.sourceEventId && offer.statementOrdinal == -1
                    && offer.nativeTick == root.nativeTick && offer.runtimeTick == root.runtimeTick
                    && offer.kind.equals("CAST_RELEASE") && offer.generation == catalogGeneration) {
                if (offer.phase.equals("OFFER_RETURN") && offer.outcome.equals("ACCEPTED")) {
                    rootOffers++;
                }
                if (offer.phase.equals("LINK") && offer.outcome.equals("CREATED") && offer.sequence > 0) {
                    rootLinks++;
                    observedRootSequence = offer.sequence;
                }
            }
        }
        if (selectedOffers != 1 || selectedLinks != 0 || rootOffers != 1 || rootLinks != 1) {
            rejectedExpectation++;
            throw new IllegalArgumentException("A05_BOUNDARY_FRESH_STAGE_OR_ROOT_LINK_UNPROVED");
        }

        int rootAdmissions = 0;
        int validRootAdmissions = 0;
        for (var offer : A05Qualification.recorder.offers) {
            if (offer.instanceId == root.instanceId && offer.requestId == root.requestId
                    && offer.sourceEventId == root.sourceEventId && offer.statementOrdinal == -1
                    && offer.nativeTick == root.nativeTick && offer.runtimeTick == root.runtimeTick
                    && offer.kind.equals("CAST_RELEASE") && offer.generation == catalogGeneration
                    && offer.sequence == observedRootSequence && offer.phase.equals("DELIVERY_ADMISSION")) {
                rootAdmissions++;
                if (offer.outcome.equals("ACCEPTED") && offer.selectedRecipients == 2
                        && offer.candidateDeliveries == 2 && offer.admittedDeliveries == 2) {
                    validRootAdmissions++;
                }
            }
        }
        int rootSubmissions = 0;
        long firstEpoch = 0L;
        long secondEpoch = 0L;
        boolean validRootSubmissions = true;
        for (var submission : A05Qualification.recorder.submissions) {
            if (submission.generation == catalogGeneration && submission.sequence == observedRootSequence) {
                rootSubmissions++;
                validRootSubmissions &= submission.nativeTick == root.nativeTick
                        && submission.kind.equals("CAST_RELEASE") && submission.outcome.equals("SUBMITTED")
                        && submission.connectionEpoch > 0;
                if (rootSubmissions == 1) { firstEpoch = submission.connectionEpoch; }
                else if (rootSubmissions == 2) { secondEpoch = submission.connectionEpoch; }
            }
        }
        if (rootAdmissions != 1 || validRootAdmissions != 1 || rootSubmissions != 2
                || !validRootSubmissions || firstEpoch == secondEpoch
                || (selectedConnectionEpoch != firstEpoch && selectedConnectionEpoch != secondEpoch)) {
            rejectedExpectation++;
            throw new IllegalArgumentException("A05_BOUNDARY_TWO_NATIVE_BASELINE_RECIPIENTS_UNPROVED");
        }

        expectedNativeTick = selectedConfirmedHit.nativeTick;
        expectedRuntimeTick = selectedConfirmedHit.runtimeTick;
        expectedInstanceId = selectedConfirmedHit.instanceId;
        expectedLineageId = selectedConfirmedHit.lineageId;
        expectedRequestId = selectedConfirmedHit.requestId;
        expectedSourceEventId = selectedConfirmedHit.sourceEventId;
        expectedGeneration = catalogGeneration;
        selectedEpoch = selectedConnectionEpoch;
        otherEpoch = selectedConnectionEpoch == firstEpoch ? secondEpoch : firstEpoch;
        rootSequence = observedRootSequence;
        armed = true;
    }

    @Override
    void beforeSend(int nativeTick, long runtimeTick, long instanceId,
            long requestId, long sourceEventId, int statementOrdinal,
            long catalogGeneration, long presentationSequence,
            long connectionEpoch, PresentationEventKind kind) {
        beforeSendCalls++;
        if (!armed) {
            unarmedPasses++;
            return;
        }
        boolean touchesSelectedIdentity = requestId == expectedRequestId
                || sourceEventId == expectedSourceEventId
                || (kind == PresentationEventKind.HIT && statementOrdinal == 0
                        && instanceId == expectedInstanceId);
        if (!touchesSelectedIdentity) {
            unrelatedPasses++;
            return;
        }
        // A real baseline peer is never the selected negative recipient.
        if (connectionEpoch == otherEpoch) {
            otherRecipientPasses++;
            return;
        }
        if (kind != PresentationEventKind.HIT || statementOrdinal != 0
                || nativeTick != expectedNativeTick || runtimeTick != expectedRuntimeTick
                || instanceId != expectedInstanceId || requestId != expectedRequestId
                || sourceEventId != expectedSourceEventId) {
            rejectedEvent++;
            throw new IllegalArgumentException("A05_BOUNDARY_SELECTED_EVENT_MISMATCH");
        }
        if (connectionEpoch != selectedEpoch) {
            rejectedRecipient++;
            throw new IllegalArgumentException("A05_BOUNDARY_SELECTED_RECIPIENT_MISMATCH");
        }
        if (catalogGeneration != expectedGeneration) {
            rejectedGeneration++;
            throw new IllegalArgumentException("A05_BOUNDARY_SELECTED_GENERATION_MISMATCH");
        }
        if (presentationSequence <= 0) {
            rejectedSequence++;
            throw new IllegalArgumentException("A05_BOUNDARY_SELECTED_SEQUENCE_MISMATCH");
        }
        if (A05Qualification.recorder.overflow != 0 || A05Qualification.recorder.invalid != 0
                || A05Qualification.recorder.offers.size() > A05QualificationRecorder.MAX_RECORDS) {
            rejectedLink++;
            throw new IllegalStateException("A05_BOUNDARY_SCALAR_LINK_INCOMPLETE");
        }
        int links = 0;
        long actualSequence = 0L;
        for (var offer : A05Qualification.recorder.offers) {
            if (offer.phase.equals("LINK") && offer.nativeTick == expectedNativeTick
                    && offer.runtimeTick == expectedRuntimeTick && offer.instanceId == expectedInstanceId
                    && offer.requestId == expectedRequestId && offer.sourceEventId == expectedSourceEventId
                    && offer.statementOrdinal == 0 && offer.kind.equals("HIT")
                    && offer.generation == expectedGeneration && offer.outcome.equals("CREATED")
                    && offer.sequence > 0) {
                links++;
                actualSequence = offer.sequence;
            }
        }
        if (links != 1) {
            rejectedLink++;
            throw new IllegalStateException("A05_BOUNDARY_EXACT_ONE_LINK_UNPROVED");
        }
        if (presentationSequence != actualSequence) {
            rejectedSequence++;
            throw new IllegalArgumentException("A05_BOUNDARY_SELECTED_SEQUENCE_MISMATCH");
        }
        int admissions = 0;
        int validAdmissions = 0;
        for (var offer : A05Qualification.recorder.offers) {
            if (offer.phase.equals("DELIVERY_ADMISSION") && offer.nativeTick == expectedNativeTick
                    && offer.runtimeTick == expectedRuntimeTick && offer.instanceId == expectedInstanceId
                    && offer.requestId == expectedRequestId && offer.sourceEventId == expectedSourceEventId
                    && offer.statementOrdinal == 0 && offer.kind.equals("HIT")
                    && offer.generation == expectedGeneration && offer.sequence == actualSequence) {
                admissions++;
                if (offer.outcome.equals("ACCEPTED") && offer.selectedRecipients == 2
                        && offer.candidateDeliveries == 2 && offer.admittedDeliveries == 2) {
                    validAdmissions++;
                }
            }
        }
        if (admissions != 1 || validAdmissions != 1) {
            rejectedAdmission++;
            throw new IllegalStateException("A05_BOUNDARY_EXACT_TWO_RECIPIENT_ADMISSION_UNPROVED");
        }
        if (consumed) {
            rejectedSecondUse++;
            throw new IllegalStateException("A05_BOUNDARY_SELECTED_SEND_REUSED");
        }
        boundSequence = actualSequence;
        consumed = true;
        faultCount++;
        throw plannedFault;
    }
}
