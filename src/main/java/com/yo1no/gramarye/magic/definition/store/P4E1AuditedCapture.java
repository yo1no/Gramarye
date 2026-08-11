package com.yo1no.gramarye.magic.definition.store;

import java.util.ArrayList;
import java.util.Objects;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;

/** Same-tick, unpublished, single-use B2-A output; it is not a Complete permit. */
final class P4E1AuditedCapture {
    private P4E1GroupedStoreAudit ownerIdentity;
    private MinecraftServer serverIdentity;
    private Thread creationThreadIdentity;
    private int capturedTick;
    private PlayerList playerListIdentity;
    private P4E1HeapFloorObservation heapObservation;
    private P4E1GlobalSourceCapture.StoreReadyWitness storeWitness;
    private P4E1PendingJournalObservation.Ready journalWitness;
    private P4E1SourceInventory.Witness inventoryWitness;
    private P4E1PlayerDataDirectorySnapshot directoryWitness;
    private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
    private P4E1RawClaimBuffer claims;
    private ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources;
    private ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles;
    private P4E1GlobalSourceCapture.Summary summary;
    private int distinctSkillIdCount;
    private boolean consumed;

    P4E1AuditedCapture(
            P4E1GroupedStoreAudit ownerIdentity,
            MinecraftServer serverIdentity,
            Thread creationThreadIdentity,
            int capturedTick,
            PlayerList playerListIdentity,
            P4E1HeapFloorObservation heapObservation,
            P4E1GlobalSourceCapture.StoreReadyWitness storeWitness,
            P4E1PendingJournalObservation.Ready journalWitness,
            P4E1SourceInventory.Witness inventoryWitness,
            P4E1PlayerDataDirectorySnapshot directoryWitness,
            P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
            P4E1RawClaimBuffer claims,
            ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources,
            ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles,
            P4E1GlobalSourceCapture.Summary summary,
            int distinctSkillIdCount) {
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
        this.creationThreadIdentity = Objects.requireNonNull(
                creationThreadIdentity, "creationThreadIdentity");
        this.capturedTick = capturedTick;
        this.playerListIdentity = Objects.requireNonNull(playerListIdentity, "playerListIdentity");
        this.heapObservation = Objects.requireNonNull(heapObservation, "heapObservation");
        this.storeWitness = Objects.requireNonNull(storeWitness, "storeWitness");
        this.journalWitness = Objects.requireNonNull(journalWitness, "journalWitness");
        this.inventoryWitness = Objects.requireNonNull(inventoryWitness, "inventoryWitness");
        this.directoryWitness = Objects.requireNonNull(directoryWitness, "directoryWitness");
        this.integratedWitness = Objects.requireNonNull(integratedWitness, "integratedWitness");
        var exactClaims = Objects.requireNonNull(claims, "claims");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.selectedFiles = Objects.requireNonNull(selectedFiles, "selectedFiles");
        this.summary = Objects.requireNonNull(summary, "summary");
        if (distinctSkillIdCount < 0 || distinctSkillIdCount > exactClaims.size()) {
            throw new IllegalArgumentException("invalid distinct SkillId count");
        }
        exactClaims.markAudited();
        this.claims = exactClaims;
        this.distinctSkillIdCount = distinctSkillIdCount;
    }

    Transfer claim(P4E1GroupedStoreAudit owner) {
        return claim(owner, null);
    }

    Transfer claim(
            P4E1GroupedStoreAudit owner, SkillRetentionRootAuditService indexOwner) {
        requireUnconsumed();
        consumed = true;
        var moved = moveReferences();
        var accepted = false;
        try {
            requireExactBinding(owner, moved);
            if (indexOwner != null) {
                moved.bindIndexOwner(indexOwner);
            }
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
            selectedFiles.clear();
            ownerIdentity = null;
            serverIdentity = null;
            creationThreadIdentity = null;
            capturedTick = -1;
            playerListIdentity = null;
            heapObservation = null;
            storeWitness = null;
            journalWitness = null;
            inventoryWitness = null;
            directoryWitness = null;
            integratedWitness = null;
            claims = null;
            sources = null;
            selectedFiles = null;
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
                    playerListIdentity,
                    heapObservation,
                    storeWitness,
                    journalWitness,
                    inventoryWitness,
                    directoryWitness,
                    integratedWitness,
                    claims,
                    sources,
                    selectedFiles,
                    summary,
                    distinctSkillIdCount);
            return moved;
        } finally {
            try {
                if (moved == null) {
                    try {
                        P4E1GlobalSourceCapture.cleanupUnpublished(
                                claims,
                                sources,
                                journalWitness,
                                inventoryWitness,
                                storeWitness);
                    } finally {
                        selectedFiles.clear();
                    }
                }
            } finally {
                ownerIdentity = null;
                serverIdentity = null;
                creationThreadIdentity = null;
                capturedTick = -1;
                playerListIdentity = null;
                heapObservation = null;
                storeWitness = null;
                journalWitness = null;
                inventoryWitness = null;
                directoryWitness = null;
                integratedWitness = null;
                claims = null;
                sources = null;
                selectedFiles = null;
                summary = null;
                distinctSkillIdCount = 0;
            }
        }
    }

    /** Temporary same-backing transfer seam for future B2-B; it exposes no roots or Store state. */
    static final class Transfer {
        private P4E1GroupedStoreAudit ownerIdentity;
        private MinecraftServer serverIdentity;
        private Thread creationThreadIdentity;
        private int capturedTick;
        private PlayerList playerListIdentity;
        private P4E1HeapFloorObservation heapObservation;
        private P4E1GlobalSourceCapture.StoreReadyWitness storeWitness;
        private P4E1PendingJournalObservation.Ready journalWitness;
        private P4E1SourceInventory.Witness inventoryWitness;
        private P4E1PlayerDataDirectorySnapshot directoryWitness;
        private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
        private P4E1RawClaimBuffer claims;
        private ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources;
        private ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles;
        private P4E1GlobalSourceCapture.Summary summary;
        private int distinctSkillIdCount;
        private SkillRetentionRootAuditService indexOwnerIdentity;
        private boolean discarded;

        private Transfer(
                P4E1GroupedStoreAudit ownerIdentity,
                MinecraftServer serverIdentity,
                Thread creationThreadIdentity,
                int capturedTick,
                PlayerList playerListIdentity,
                P4E1HeapFloorObservation heapObservation,
                P4E1GlobalSourceCapture.StoreReadyWitness storeWitness,
                P4E1PendingJournalObservation.Ready journalWitness,
                P4E1SourceInventory.Witness inventoryWitness,
                P4E1PlayerDataDirectorySnapshot directoryWitness,
                P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
                P4E1RawClaimBuffer claims,
                ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources,
                ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles,
                P4E1GlobalSourceCapture.Summary summary,
                int distinctSkillIdCount) {
            this.ownerIdentity = ownerIdentity;
            this.serverIdentity = serverIdentity;
            this.creationThreadIdentity = creationThreadIdentity;
            this.capturedTick = capturedTick;
            this.playerListIdentity = playerListIdentity;
            this.heapObservation = heapObservation;
            this.storeWitness = storeWitness;
            this.journalWitness = journalWitness;
            this.inventoryWitness = inventoryWitness;
            this.directoryWitness = directoryWitness;
            this.integratedWitness = integratedWitness;
            this.claims = claims;
            this.sources = sources;
            this.selectedFiles = selectedFiles;
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

        int distinctSkillIdCount(SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return distinctSkillIdCount;
        }

        P4E1GlobalSourceCapture.Summary summary(SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return summary;
        }

        int sourceCount(SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return sources.size();
        }

        P4E1GlobalSourceCapture.SourceEntry sourceAt(
                SkillRetentionRootAuditService owner, int index) {
            requireIndexActive(owner);
            return sources.get(index);
        }

        P4E1RawClaimBuffer backingIdentity(SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return claims;
        }

        boolean indexOwnerCurrent(SkillRetentionRootAuditService candidate) {
            return !discarded
                    && claims != null
                    && indexOwnerIdentity == Objects.requireNonNull(candidate, "candidate");
        }

        boolean serverIdentityCurrent(
                SkillRetentionRootAuditService owner, MinecraftServer candidate) {
            requireIndexActive(owner);
            return serverIdentity == Objects.requireNonNull(candidate, "candidate");
        }

        boolean callChainCurrent(SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return Thread.currentThread() == creationThreadIdentity
                    && serverIdentity.isSameThread()
                    && serverIdentity.getTickCount() == capturedTick;
        }

        boolean playerListCurrent(SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return serverIdentity.getPlayerList() == playerListIdentity;
        }

        boolean storeCurrent(
                SkillRetentionRootAuditService owner,
                SkillDefinitionStoreService storeService) {
            requireIndexActive(owner);
            return storeWitness.isCurrent(
                    Objects.requireNonNull(storeService, "storeService"), serverIdentity);
        }

        P4E1PendingJournalObservation.Currentness journalCurrentness(
                SkillRetentionRootAuditService owner,
                SkillDefinitionStoreService storeService) {
            requireIndexActive(owner);
            var service = Objects.requireNonNull(storeService, "storeService");
            var currentness = journalWitness.currentness(
                    service.submissionPort(),
                    serverIdentity,
                    storeWitness,
                    service.installedAdapter(serverIdentity));
            return currentness == P4E1PendingJournalObservation.Currentness.CURRENT
                            && !P4E1GlobalSourceCapture.journalProofsCurrent(
                                    sources, journalWitness)
                    ? P4E1PendingJournalObservation.Currentness.TARGET_INVALID
                    : currentness;
        }

        boolean inventoryCurrent(
                SkillRetentionRootAuditService owner,
                PlayerSkillAttachmentService attachmentService) {
            requireIndexActive(owner);
            return inventoryWitness.isCurrent(
                    Objects.requireNonNull(attachmentService, "attachmentService"),
                    journalWitness);
        }

        P4E1PlayerDataDirectorySnapshot.FinalVerificationResult directoryCurrentness(
                SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return directoryWitness.verifyFinal(selectedFiles);
        }

        boolean onlineCurrent(
                SkillRetentionRootAuditService owner,
                PlayerSkillAttachmentService attachmentService) {
            requireIndexActive(owner);
            return P4E1GlobalSourceCapture.onlineSourcesCurrent(
                    serverIdentity,
                    playerListIdentity,
                    Objects.requireNonNull(attachmentService, "attachmentService"),
                    sources);
        }

        boolean integratedAndArbitrationCurrent(
                SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            return P4E1GlobalSourceCapture.integratedAndArbitrationCurrent(
                    serverIdentity,
                    directoryWitness,
                    integratedWitness,
                    sources,
                    selectedFiles);
        }

        void releaseBacking(
                SkillRetentionRootAuditService owner, P4E1RawClaimBuffer expectedBacking) {
            requireIndexActive(owner);
            if (claims != Objects.requireNonNull(expectedBacking, "expectedBacking")) {
                throw new IllegalStateException("P4E1_INDEX_BACKING_IDENTITY_MISMATCH");
            }
            discardWitnesses();
            claims.markIndexed();
            discarded = true;
            clearReferences();
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
                selectedFiles.clear();
                clearReferences();
            }
        }

        void discard(SkillRetentionRootAuditService owner) {
            requireIndexActive(owner);
            discard();
        }

        private void bindIndexOwner(SkillRetentionRootAuditService owner) {
            if (indexOwnerIdentity != null) {
                throw new IllegalStateException("P4E1_AUDITED_TRANSFER_INDEX_OWNER_ALREADY_BOUND");
            }
            indexOwnerIdentity = Objects.requireNonNull(owner, "owner");
        }

        private void discardWitnesses() {
            RuntimeException firstFailure = null;
            try {
                for (var index = 0; index < sources.size(); index++) {
                    var source = sources.get(index);
                    if (source.witness()
                            instanceof P4E1GlobalSourceCapture.SourceWitness.Online online) {
                        try {
                            online.service().discardOnlineRootWitness(online.handle());
                        } catch (RuntimeException exception) {
                            if (firstFailure == null) {
                                firstFailure = exception;
                            }
                        }
                    }
                }
            } finally {
                try {
                    try {
                        sources.clear();
                    } catch (RuntimeException exception) {
                        if (firstFailure == null) {
                            firstFailure = exception;
                        }
                    }
                } finally {
                    try {
                        try {
                            selectedFiles.clear();
                        } catch (RuntimeException exception) {
                            if (firstFailure == null) {
                                firstFailure = exception;
                            }
                        }
                    } finally {
                        try {
                            try {
                                journalWitness.discardWitness();
                            } catch (RuntimeException exception) {
                                if (firstFailure == null) {
                                    firstFailure = exception;
                                }
                            }
                        } finally {
                            try {
                                try {
                                    inventoryWitness.discard();
                                } catch (RuntimeException exception) {
                                    if (firstFailure == null) {
                                        firstFailure = exception;
                                    }
                                }
                            } finally {
                                try {
                                    storeWitness.discard();
                                } catch (RuntimeException exception) {
                                    if (firstFailure == null) {
                                        firstFailure = exception;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (firstFailure != null) {
                throw firstFailure;
            }
        }

        private void clearReferences() {
            ownerIdentity = null;
            indexOwnerIdentity = null;
            serverIdentity = null;
            creationThreadIdentity = null;
            capturedTick = -1;
            playerListIdentity = null;
            heapObservation = null;
            storeWitness = null;
            journalWitness = null;
            inventoryWitness = null;
            directoryWitness = null;
            integratedWitness = null;
            claims = null;
            sources = null;
            selectedFiles = null;
            summary = null;
            distinctSkillIdCount = 0;
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

        private void requireIndexActive(SkillRetentionRootAuditService owner) {
            if (discarded || claims == null) {
                throw new IllegalStateException("P4E1_AUDITED_TRANSFER_DISCARDED");
            }
            if (indexOwnerIdentity != Objects.requireNonNull(owner, "owner")) {
                throw new IllegalStateException("P4E1_AUDITED_TRANSFER_INDEX_OWNER_MISMATCH");
            }
        }
    }

}
