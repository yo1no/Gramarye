package com.yo1no.gramarye;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared P8 limits seeded with the work already spent by the legacy drain. */
final class P8V0ResidualBudget {
    private PresentationBudget serverLogical;
    private PresentationBudget eventBuffer;
    private PresentationBudget evaluations;
    private PresentationBudget candidates;
    private final Map<UUID, PresentationBudget> sourceLogical;
    private final Map<V0SpellInstanceId, PresentationBudget> instanceLogical = new HashMap<>();
    private final List<P8Delivery> legacyAdmissions;

    P8V0ResidualBudget(PresentationBudget serverLogical, PresentationBudget eventBuffer,
            Map<UUID, PresentationBudget> sourceLogical, PresentationBudget evaluations,
            int legacyCandidateCount, List<P8Delivery> legacyAdmissions) {
        this.serverLogical = serverLogical;
        this.eventBuffer = eventBuffer;
        this.sourceLogical = new HashMap<>(sourceLogical);
        this.evaluations = evaluations;
        var usedCandidates = PresentationBudget.initial(PresentationBudget.Scope.CANDIDATE_DELIVERIES)
                .consume(PresentationCost.count(legacyCandidateCount));
        if (!accepted(usedCandidates)) {
            throw new IllegalArgumentException("legacy candidate count exceeds the shared P8 budget");
        }
        candidates = usedCandidates.nextState();
        this.legacyAdmissions = List.copyOf(legacyAdmissions);
    }

    boolean admitLogical(V0SpellInstanceId instance, UUID attribution, int bodyBytes) {
        // Reserve the right to scan an entire offer. Charge the actual scan after selection.
        if (!accepted(evaluations.consume(
                        PresentationCost.count(PresentationLimits.MAX_ONLINE_RECIPIENT_SCAN_PER_EVENT)))
                || !accepted(candidates.consume(PresentationCost.count(1L)))
                || !accepted(eventBuffer.consume(new PresentationCost(1L, bodyBytes)))) {
            return false;
        }
        var cost = PresentationCost.count(1L);
        var nextServer = serverLogical.consume(cost);
        var nextInstance = instanceLogical.getOrDefault(instance,
                PresentationBudget.initial(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SKILL)).consume(cost);
        var nextSource = sourceLogical.getOrDefault(attribution,
                PresentationBudget.initial(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SOURCE)).consume(cost);
        if (!accepted(nextServer) || !accepted(nextInstance) || !accepted(nextSource)) {
            return false;
        }
        serverLogical = nextServer.nextState();
        instanceLogical.put(instance, nextInstance.nextState());
        sourceLogical.put(attribution, nextSource.nextState());
        return true;
    }

    boolean retainSelection(int bodyBytes, P8RecipientSelection selection) {
        var nextEvaluations = evaluations.consume(PresentationCost.count(selection.evaluations()));
        if (!accepted(nextEvaluations)) {
            throw new IllegalStateException("V0 selection exceeded its prechecked evaluation budget");
        }
        evaluations = nextEvaluations.nextState();
        if (selection.recipients().isEmpty()) {
            return false;
        }
        var nextCandidates = candidates.consume(PresentationCost.count(selection.recipients().size()));
        var nextBuffer = eventBuffer.consume(new PresentationCost(1L, bodyBytes));
        if (!accepted(nextCandidates) || !accepted(nextBuffer)) {
            return false;
        }
        candidates = nextCandidates.nextState();
        eventBuffer = nextBuffer.nextState();
        return true;
    }

    List<P8Delivery> admitDeliveries(List<P8Delivery> candidates) {
        return P8DeliveryAdmission.admit(candidates, legacyAdmissions);
    }

    private static boolean accepted(PresentationBudget.Decision decision) {
        return decision.outcome() == PresentationBudget.Outcome.ACCEPTED;
    }
}
