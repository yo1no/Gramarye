package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/** One bounded E2 batch: callback capture followed by one lookup per distinct SkillId. */
final class P4E2GroupedStoreValidation
        implements PlayerSkillAttachmentService.OnlineReconciliationSink {
    private final SkillOwnerId expectedOwner;
    private final ArrayList<Route> routes = new ArrayList<>();
    private int nextLatestOrdinal;
    private int nextEquippedOrdinal;
    private boolean drained;

    P4E2GroupedStoreValidation(SkillOwnerId expectedOwner) {
        this.expectedOwner = Objects.requireNonNull(expectedOwner, "expectedOwner");
    }

    @Override
    public void latest(
            int ordinal,
            SkillId skillId,
            Optional<SkillReference> pointer,
            int mutationGeneration) {
        requireCollecting();
        Objects.requireNonNull(skillId, "skillId");
        pointer = Objects.requireNonNull(pointer, "pointer");
        if (ordinal != nextLatestOrdinal++ || mutationGeneration < 0) {
            throw new IllegalStateException("P4E2_LATEST_PROJECTION_ORDER_INVALID");
        }
        if (pointer.isPresent()) {
            var reference = pointer.orElseThrow();
            if (!reference.skillId().equals(skillId)) {
                throw new IllegalStateException("P4E2_LATEST_ROUTE_MISMATCH");
            }
            routes.add(new Route(RouteKind.LATEST, ordinal, reference));
        }
        requireRouteBound();
    }

    @Override
    public void equipped(int ordinal, int slot, SkillReference reference) {
        requireCollecting();
        Objects.requireNonNull(reference, "reference");
        if (ordinal != nextEquippedOrdinal++ || slot < 0) {
            throw new IllegalStateException("P4E2_EQUIPPED_PROJECTION_ORDER_INVALID");
        }
        routes.add(new Route(RouteKind.EQUIPPED, ordinal, reference));
        requireRouteBound();
    }

    Validated validate(StoreReadyWitness storeWitness) {
        Objects.requireNonNull(storeWitness, "storeWitness");
        return validate(storeWitness::observeExactHistory);
    }

    Validated validate(HistoryLookup historyLookup) {
        Objects.requireNonNull(historyLookup, "historyLookup");
        if (drained) {
            throw new IllegalStateException("P4E2_GROUPED_VALIDATION_ALREADY_CONSUMED");
        }
        drained = true;
        try {
            // Complete every allocation that enrolls an observation before the first lookup.
            var observations = new LinkedHashMap<SkillId, HistorySlot>();
            var orderedSlots = new ArrayList<HistorySlot>(routes.size());
            for (var route : routes) {
                var skillId = route.reference().skillId();
                if (!observations.containsKey(skillId)) {
                    var slot = new HistorySlot(skillId);
                    observations.put(skillId, slot);
                    orderedSlots.add(slot);
                }
            }
            var enrolledSlots = orderedSlots.toArray(HistorySlot[]::new);
            try {
                for (var index = 0; index < enrolledSlots.length; index++) {
                    var slot = enrolledSlots[index];
                    // Plain assignment immediately enrolls the returned observation for cleanup.
                    slot.observation = historyLookup.observeExactHistory(slot.skillId);
                    if (slot.observation == null) {
                        throw new NullPointerException("historyLookup returned null");
                    }
                }

                var staleLatest = new int[nextLatestOrdinal];
                var staleEquipped = new int[nextEquippedOrdinal];
                var staleLatestCount = 0;
                var staleEquippedCount = 0;
                var missingCount = 0;
                var ownerMismatchCount = 0;
                for (var route : routes) {
                    var slot = observations.get(route.reference().skillId());
                    var classification = slot.classify(expectedOwner, route.reference());
                    if (classification == Classification.VALID) {
                        continue;
                    }
                    if (route.kind() == RouteKind.LATEST) {
                        staleLatest[staleLatestCount++] = route.ordinal();
                    } else {
                        staleEquipped[staleEquippedCount++] = route.ordinal();
                    }
                    if (classification == Classification.OWNER_MISMATCH) {
                        ownerMismatchCount = Math.incrementExact(ownerMismatchCount);
                    } else {
                        missingCount = Math.incrementExact(missingCount);
                    }
                }
                return new Validated(
                        java.util.Arrays.copyOf(staleLatest, staleLatestCount),
                        java.util.Arrays.copyOf(staleEquipped, staleEquippedCount),
                        missingCount,
                        ownerMismatchCount,
                        enrolledSlots.length);
            } finally {
                // Index cleanup allocates no iterator and clears every successfully enrolled slot.
                for (var index = 0; index < enrolledSlots.length; index++) {
                    enrolledSlots[index].discard();
                    enrolledSlots[index] = null;
                }
            }
        } finally {
            routes.clear();
        }
    }

    @FunctionalInterface
    interface HistoryLookup {
        P4E1StoreHistoryObservation observeExactHistory(SkillId skillId);
    }

    private void requireCollecting() {
        if (drained) {
            throw new IllegalStateException("P4E2_GROUPED_VALIDATION_ALREADY_CONSUMED");
        }
    }

    private void requireRouteBound() {
        var maximum = Math.addExact(
                MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES,
                MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES);
        if (routes.size() > maximum
                || nextLatestOrdinal > MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES
                || nextEquippedOrdinal > MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES) {
            throw new IllegalStateException("P4E2_RECONCILIATION_ROUTE_LIMIT_EXCEEDED");
        }
    }

    record Validated(
            int[] staleLatestOrdinals,
            int[] staleEquippedOrdinals,
            int missingCount,
            int ownerMismatchCount,
            int distinctSkillIdCount) {
        Validated {
            staleLatestOrdinals = Objects.requireNonNull(
                            staleLatestOrdinals, "staleLatestOrdinals")
                    .clone();
            staleEquippedOrdinals = Objects.requireNonNull(
                            staleEquippedOrdinals, "staleEquippedOrdinals")
                    .clone();
            if (missingCount < 0 || ownerMismatchCount < 0 || distinctSkillIdCount < 0) {
                throw new IllegalArgumentException("validation counts must be non-negative");
            }
        }

        int staleLatestCount() {
            return staleLatestOrdinals.length;
        }

        int staleEquippedCount() {
            return staleEquippedOrdinals.length;
        }

        boolean hasStaleRoutes() {
            return staleLatestOrdinals.length != 0 || staleEquippedOrdinals.length != 0;
        }
    }

    sealed interface StoreObservation
            permits StoreObservation.Ready, StoreObservation.Unavailable {
        record Ready(StoreReadyWitness witness) implements StoreObservation {
            public Ready {
                Objects.requireNonNull(witness, "witness");
            }
        }

        enum Unavailable implements StoreObservation {
            INSTANCE
        }
    }

    /** Fresh E2-only exact Store/audit/composition witness; never reused as E1 authority. */
    static final class StoreReadyWitness {
        private SkillDefinitionStoreService owner;
        private MinecraftServer server;
        private GramaryeSkillSavedData adapter;
        private SkillSavedDataState.Ready ready;
        private SkillDefinitionStore store;
        private SkillRetentionRootAuditService audit;
        private P4E2OnlineReconciliationCoordinator coordinator;

        StoreReadyWitness(
                SkillDefinitionStoreService owner,
                MinecraftServer server,
                GramaryeSkillSavedData adapter,
                SkillSavedDataState.Ready ready,
                SkillDefinitionStore store,
                SkillRetentionRootAuditService audit,
                P4E2OnlineReconciliationCoordinator coordinator) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.server = Objects.requireNonNull(server, "server");
            this.adapter = Objects.requireNonNull(adapter, "adapter");
            this.ready = Objects.requireNonNull(ready, "ready");
            this.store = Objects.requireNonNull(store, "store");
            this.audit = Objects.requireNonNull(audit, "audit");
            this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        }

        P4E1StoreHistoryObservation observeExactHistory(SkillId skillId) {
            requireActive();
            return store.observeExactHistoryForRootAudit(skillId);
        }

        boolean isCurrent() {
            requireActive();
            return owner.isP4E2StoreReadyCurrent(server, this);
        }

        boolean matches(
                SkillDefinitionStoreService candidateOwner,
                MinecraftServer candidateServer,
                GramaryeSkillSavedData candidateAdapter,
                SkillRetentionRootAuditService candidateAudit,
                P4E2OnlineReconciliationCoordinator candidateCoordinator) {
            requireActive();
            return identitiesCurrent(new StoreCurrentnessFacts(
                    owner == candidateOwner,
                    server == candidateServer,
                    adapter == candidateAdapter,
                    adapter.state() == ready,
                    ready.store() == store,
                    audit == candidateAudit,
                    coordinator == candidateCoordinator));
        }

        static boolean identitiesCurrent(StoreCurrentnessFacts facts) {
            Objects.requireNonNull(facts, "facts");
            return facts.serviceIdentityCurrent()
                    && facts.serverIdentityCurrent()
                    && facts.adapterIdentityCurrent()
                    && facts.readyStateIdentityCurrent()
                    && facts.storeIdentityCurrent()
                    && facts.auditIdentityCurrent()
                    && facts.coordinatorIdentityCurrent();
        }

        void discard() {
            requireActive();
            owner = null;
            server = null;
            adapter = null;
            ready = null;
            store = null;
            audit = null;
            coordinator = null;
        }

        private void requireActive() {
            if (owner == null) {
                throw new IllegalStateException("P4E2_STORE_WITNESS_DISCARDED");
            }
        }

        record StoreCurrentnessFacts(
                boolean serviceIdentityCurrent,
                boolean serverIdentityCurrent,
                boolean adapterIdentityCurrent,
                boolean readyStateIdentityCurrent,
                boolean storeIdentityCurrent,
                boolean auditIdentityCurrent,
                boolean coordinatorIdentityCurrent) {
        }
    }

    private enum RouteKind {
        LATEST,
        EQUIPPED
    }

    private record Route(RouteKind kind, int ordinal, SkillReference reference) {
        private Route {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reference, "reference");
        }
    }

    private enum Classification {
        VALID,
        MISSING,
        OWNER_MISMATCH
    }

    private static final class HistorySlot {
        private final SkillId skillId;
        private P4E1StoreHistoryObservation observation;
        private Boolean ownerMatches;

        private HistorySlot(SkillId skillId) {
            this.skillId = Objects.requireNonNull(skillId, "skillId");
        }

        private Classification classify(SkillOwnerId owner, SkillReference reference) {
            if (observation instanceof P4E1StoreHistoryObservation.Absent) {
                return Classification.MISSING;
            }
            var present = (P4E1StoreHistoryObservation.Present) observation;
            if (ownerMatches == null) {
                ownerMatches = present.ownerMatches(owner);
            }
            if (!ownerMatches) {
                return Classification.OWNER_MISMATCH;
            }
            return present.contains(reference) ? Classification.VALID : Classification.MISSING;
        }

        private void discard() {
            if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                present.discard();
            }
            observation = null;
            ownerMatches = null;
        }
    }
}
