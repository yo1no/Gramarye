package com.yo1no.gramarye;

import java.util.ArrayList;
import java.util.List;

/** Bounded scalar records only. This class cannot reach the fixture or the game. */
final class A05QualificationRecorder {
    static final int MAX_RECORDS = 4_096;
    final int caseId;
    final List<A05Observer.ReceiptObservation> receipts = new ArrayList<>();
    final List<A05Observer.OfferObservation> offers = new ArrayList<>();
    final List<A05Observer.SubmissionObservation> submissions = new ArrayList<>();
    int recorded;
    int overflow;
    int invalid;

    A05QualificationRecorder(int caseId) {
        this.caseId = caseId;
    }

    void receipt(A05Observer.ReceiptObservation observation) {
        if (observation == null) {
            invalid++;
        } else if (recorded == MAX_RECORDS) {
            overflow++;
        } else {
            receipts.add(observation);
            recorded++;
        }
    }

    void offer(A05Observer.OfferObservation observation) {
        if (observation == null) {
            invalid++;
        } else if (recorded == MAX_RECORDS) {
            overflow++;
        } else {
            offers.add(observation);
            recorded++;
        }
    }

    void submission(A05Observer.SubmissionObservation observation) {
        if (observation == null) {
            invalid++;
        } else if (recorded == MAX_RECORDS) {
            overflow++;
        } else {
            submissions.add(observation);
            recorded++;
        }
    }
}
