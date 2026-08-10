package com.yo1no.gramarye.magic.definition.store;

import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/** Same-tick, unpublished, single-use B2-A output; it is not a Complete permit. */
final class P4E1AuditedCapture {
    private P4E1GroupedStoreAudit ownerIdentity;
    private MinecraftServer serverIdentity;
    private Thread creationThreadIdentity;
    private int capturedTick;
    private P4E1HeapFloorObservation heapObservation;
    private P4E1GlobalSourceCapture.StoreReadyWitness storeWitness;
    private P4E1PendingJournalObservation.Ready journalWitness;
    private P4E1SourceInventory.Witness inventoryWitness;
    private P4E1PlayerDataDirectorySnapshot directoryWitness;
    private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
    private P4E1RawClaimBuffer claims;
    private ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources;
    private P4E1GlobalSourceCapture.Summary summary;
    private int distinctSkillIdCount;
    private boolean consumed;

    P4E1AuditedCapture(
            P4E1GroupedStoreAudit ownerIdentity,
            MinecraftServer serverIdentity,
            Thread creationThreadIdentity,
            int capturedTick,
            P4E1HeapFloorObservation heapObservation,
            P4E1GlobalSourceCapture.StoreReadyWitness storeWitness,
            P4E1PendingJournalObservation.Ready journalWitness,
            P4E1SourceInventory.Witness inventoryWitness,
            P4E1PlayerDataDirectorySnapshot directoryWitness,
            P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
            P4E1RawClaimBuffer claims,
            ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources,
            P4E1GlobalSourceCapture.Summary summary,
            int distinctSkillIdCount) {
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
        this.creationThreadIdentity = Objects.requireNonNull(
                creationThreadIdentity, "creationThreadIdentity");
        this.capturedTick = capturedTick;
        this.heapObservation = Objects.requireNonNull(heapObservation, "heapObservation");
        this.storeWitness = Objects.requireNonNull(storeWitness, "storeWitness");
        this.journalWitness = Objects.requireNonNull(journalWitness, "journalWitness");
        this.inventoryWitness = Objects.requireNonNull(inventoryWitness, "inventoryWitness");
        this.directoryWitness = Objects.requireNonNull(directoryWitness, "directoryWitness");
        this.integratedWitness = Objects.requireNonNull(integratedWitness, "integratedWitness");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.summary = Objects.requireNonNull(summary, "summary");
        if (distinctSkillIdCount < 0 || distinctSkillIdCount > claims.size()) {
            throw new IllegalArgumentException("invalid distinct SkillId count");
        }
        this.distinctSkillIdCount = distinctSkillIdCount;
    }

    Transfer claim(P4E1GroupedStoreAudit owner) {
        requireUnconsumed();
        consumed = true;
        var moved = moveReferences();
        var accepted = false;
        try {
            requireExactBinding(owner, moved);
            accepted = true;
            return moved;
        } finally {
            if (!accepted) {
                moved.discard();
            }
        }
    }

    void discard(P4E1GroupedStoreAudit owner) {
        requireUnconsumed();
        consumed = true;
        var moved = moveReferences();
        try {
            requireExactBinding(owner, moved);
        } finally {
            moved.discard();
        }
    }

    /**
     * Clears a just-created, unpublished capture when allocating its result wrapper failed.
     * This path must not allocate because it can run while propagating an {@link Error}.
     */
    void discardAfterResultPublicationFailure() {
        consumed = true;
        try {
            P4E1GlobalSourceCapture.cleanupUnpublished(
                    claims, sources, journalWitness, inventoryWitness, storeWitness);
        } finally {
            ownerIdentity = null;
            serverIdentity = null;
            creationThreadIdentity = null;
            capturedTick = -1;
            heapObservation = null;
            storeWitness = null;
            journalWitness = null;
            inventoryWitness = null;
            directoryWitness = null;
            integratedWitness = null;
            claims = null;
            sources = null;
            summary = null;
            distinctSkillIdCount = 0;
        }
    }

    private void requireExactBinding(P4E1GroupedStoreAudit owner, Transfer moved) {
        if (moved.ownerIdentity != Objects.requireNonNull(owner, "owner")) {
            throw new IllegalStateException("P4E1_AUDITED_CAPTURE_OWNER_MISMATCH");
        }
        owner.requireCaptureBinding(
                moved.serverIdentity, moved.creationThreadIdentity, moved.capturedTick);
    }

    private void requireUnconsumed() {
        if (consumed || claims == null) {
            throw new IllegalStateException("P4E1_AUDITED_CAPTURE_ALREADY_CONSUMED");
        }
    }

    private Transfer moveReferences() {
        Transfer moved = null;
        try {
            moved = new Transfer(
                    ownerIdentity,
                    serverIdentity,
                    creationThreadIdentity,
                    capturedTick,
                    heapObservation,
                    storeWitness,
                    journalWitness,
                    inventoryWitness,
                    directoryWitness,
                    integratedWitness,
                    claims,
                    sources,
                    summary,
                    distinctSkillIdCount);
            return moved;
        } finally {
            if (moved == null) {
                P4E1GlobalSourceCapture.cleanupUnpublished(
                        claims, sources, journalWitness, inventoryWitness, storeWitness);
            }
            ownerIdentity = null;
            serverIdentity = null;
            creationThreadIdentity = null;
            capturedTick = -1;
            heapObservation = null;
            storeWitness = null;
            journalWitness = null;
            inventoryWitness = null;
            directoryWitness = null;
            integratedWitness = null;
            claims = null;
            sources = null;
            summary = null;
            distinctSkillIdCount = 0;
        }
    }

    /** Temporary same-backing transfer seam for future B2-B; it exposes no roots or Store state. */
    static final class Transfer {
        private P4E1GroupedStoreAudit ownerIdentity;
        private MinecraftServer serverIdentity;
        private Thread creationThreadIdentity;
        private int capturedTick;
        private P4E1HeapFloorObservation heapObservation;
        private P4E1GlobalSourceCapture.StoreReadyWitness storeWitness;
        private P4E1PendingJournalObservation.Ready journalWitness;
        private P4E1SourceInventory.Witness inventoryWitness;
        private P4E1PlayerDataDirectorySnapshot directoryWitness;
        private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
        private P4E1RawClaimBuffer claims;
        private ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources;
        private P4E1GlobalSourceCapture.Summary summary;
        private int distinctSkillIdCount;
        private boolean discarded;

        private Transfer(
                P4E1GroupedStoreAudit ownerIdentity,
                MinecraftServer serverIdentity,
                Thread creationThreadIdentity,
                int capturedTick,
                P4E1HeapFloorObservation heapObservation,
                P4E1GlobalSourceCapture.StoreReadyWitness storeWitness,
                P4E1PendingJournalObservation.Ready journalWitness,
                P4E1SourceInventory.Witness inventoryWitness,
                P4E1PlayerDataDirectorySnapshot directoryWitness,
                P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
                P4E1RawClaimBuffer claims,
                ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources,
                P4E1GlobalSourceCapture.Summary summary,
                int distinctSkillIdCount) {
            this.ownerIdentity = ownerIdentity;
            this.serverIdentity = serverIdentity;
            this.creationThreadIdentity = creationThreadIdentity;
            this.capturedTick = capturedTick;
            this.heapObservation = heapObservation;
            this.storeWitness = storeWitness;
            this.journalWitness = journalWitness;
            this.inventoryWitness = inventoryWitness;
            this.directoryWitness = directoryWitness;
            this.integratedWitness = integratedWitness;
            this.claims = claims;
            this.sources = sources;
            this.summary = summary;
            this.distinctSkillIdCount = distinctSkillIdCount;
        }

        int distinctSkillIdCount(P4E1GroupedStoreAudit owner) {
            requireActive(owner);
            return distinctSkillIdCount;
        }

        P4E1GlobalSourceCapture.Summary summary(P4E1GroupedStoreAudit owner) {
            requireActive(owner);
            return summary;
        }

        void discard() {
            if (discarded || claims == null) {
                return;
            }
            discarded = true;
            try {
                P4E1GlobalSourceCapture.cleanupUnpublished(
                        claims, sources, journalWitness, inventoryWitness, storeWitness);
            } finally {
                ownerIdentity = null;
                serverIdentity = null;
                creationThreadIdentity = null;
                capturedTick = -1;
                heapObservation = null;
                storeWitness = null;
                journalWitness = null;
                inventoryWitness = null;
                directoryWitness = null;
                integratedWitness = null;
                claims = null;
                sources = null;
                summary = null;
                distinctSkillIdCount = 0;
            }
        }

        private void requireActive(P4E1GroupedStoreAudit owner) {
            if (discarded || claims == null) {
                throw new IllegalStateException("P4E1_AUDITED_TRANSFER_DISCARDED");
            }
            if (ownerIdentity != Objects.requireNonNull(owner, "owner")) {
                throw new IllegalStateException("P4E1_AUDITED_TRANSFER_OWNER_MISMATCH");
            }
            owner.requireCaptureBinding(serverIdentity, creationThreadIdentity, capturedTick);
        }
    }
}
