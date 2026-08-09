package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Checkpoints 1-15 of the unpublished, read-only P4-E1-B1 global source capture. */
final class P4E1GlobalSourceCapture {
    private P4E1GlobalSourceCapture() {
    }

    static CaptureResult capture(
            MinecraftServer server,
            SkillDefinitionStoreService storeService,
            PlayerSkillAttachmentService attachmentService) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(storeService, "storeService");
        Objects.requireNonNull(attachmentService, "attachmentService");
        SkillDefinitionStoreService.requireServerThread(server);

        var preflight = P4E1SourceAdmissionPreflight.evaluate();
        if (preflight instanceof P4E1SourceAdmissionPreflight.Incomplete incomplete) {
            return new CaptureResult.Incomplete(incomplete.failure());
        }
        var qualified = (P4E1SourceAdmissionPreflight.Qualified) preflight;
        var budget = qualified.budget();

        var storeObservation = storeService.observeP4E1StoreReady(server);
        if (storeObservation instanceof StoreObservation.Unavailable) {
            return incomplete(P4E1SourceFailure.Code.STORE_UNAVAILABLE,
                    P4E1AuditStage.STORE_REFERENCE_OWNER_AUDIT);
        }
        var storeWitness = ((StoreObservation.Ready) storeObservation).witness();
        var submissionPort = storeService.submissionPort();
        var journalResult = submissionPort.observeP4E1Journal(server, storeWitness);
        if (journalResult instanceof P4E1PendingJournalObservation.Result.Incomplete failed) {
            storeWitness.discard();
            return incomplete(mapJournalFailure(failed.code()), P4E1AuditStage.JOURNAL_READINESS);
        }
        var journal = ((P4E1PendingJournalObservation.Result.Available) journalResult)
                .observation();

        var inventoryResult = P4E1SourceInventory.capture(attachmentService, journal);
        if (inventoryResult instanceof P4E1SourceInventory.Result.Missing) {
            discardJournal(submissionPort, server, storeWitness, journal);
            storeWitness.discard();
            return incomplete(P4E1SourceFailure.Code.INVENTORY_PROVIDER_MISSING,
                    P4E1AuditStage.JOURNAL_READINESS);
        }
        var inventory = ((P4E1SourceInventory.Result.Ready) inventoryResult).witness();

        var directoryResult = P4E1PlayerDataDirectorySnapshot.capture(
                P4E1PlayerDataDirectorySnapshot.resolveDirectory(server), budget);
        if (directoryResult instanceof P4E1PlayerDataDirectorySnapshot.CaptureResult.Failure
                failed) {
            discardPreSourceState(submissionPort, server, storeWitness, journal, inventory);
            return new CaptureResult.Incomplete(failed.failure());
        }
        var directory = ((P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready) directoryResult)
                .snapshot();

        ArrayList<OnlineIdentity> online;
        try {
            online = captureOnlineIdentities(
                    server,
                    attachmentService,
                    budget.maximum(P4E1AuditCounter.RELEVANT_RECORDS));
        } catch (RuntimeException exception) {
            discardPreSourceState(submissionPort, server, storeWitness, journal, inventory);
            throw exception;
        }

        final P4E1IntegratedSnapshotTraversal.Selection integrated;
        try {
            integrated = P4E1IntegratedSnapshotTraversal.captureForGlobal(server, budget);
        } catch (RuntimeException exception) {
            discardOnlineNew(attachmentService, online);
            discardPreSourceState(submissionPort, server, storeWitness, journal, inventory);
            throw exception;
        }
        if (integrated instanceof P4E1IntegratedSnapshotTraversal.Selection.Failure failed) {
            discardOnlineNew(attachmentService, online);
            discardPreSourceState(submissionPort, server, storeWitness, journal, inventory);
            return new CaptureResult.Incomplete(failed.failure());
        }

        var selected = arbitrate(directory, integrated, online);
        var buffer = new P4E1RawClaimBuffer();
        var sources = new ArrayList<SourceEntry>(selected.size() + 1);
        var unprocessedOnline = new ArrayList<>(online);
        var context = new CaptureContext(
                server,
                attachmentService,
                submissionPort,
                storeWitness,
                journal,
                inventory,
                directory,
                integrated,
                budget,
                buffer,
                sources,
                unprocessedOnline);
        try {
            for (var selectedSource : selected.values()) {
                var relevantExceeded = budget.checkpointSingle(
                        P4E1AuditCounter.RELEVANT_RECORDS,
                        P4E1AuditStage.RELEVANT_RECORDS,
                        1L);
                if (relevantExceeded.isPresent()) {
                    return context.fail(new CaptureResult.Incomplete(
                            P4E1SourceFailure.capacity(relevantExceeded.orElseThrow())));
                }
                var processed = processPlayerSource(context, selectedSource);
                if (processed != null) {
                    return context.fail(processed);
                }
            }

            var directoryVerification = directory.verifyUnchanged();
            if (directoryVerification
                    instanceof P4E1PlayerDataDirectorySnapshot.VerificationResult.Failure failed) {
                return context.fail(new CaptureResult.Incomplete(failed.failure()));
            }

            var journalResultCapture = processJournal(context);
            if (journalResultCapture != null) {
                return context.fail(journalResultCapture);
            }

            if (!unprocessedOnline.isEmpty()) {
                throw new IllegalStateException("P4E1_ONLINE_SOURCE_TRANSFER_MISMATCH");
            }
            var summary = Summary.from(sources, buffer.size());
            return new CaptureResult.Captured(new Captured(
                    server,
                    Thread.currentThread(),
                    qualified.observation(),
                    storeWitness,
                    journal,
                    inventory,
                    directory,
                    integrated,
                    buffer,
                    sources,
                    summary));
        } catch (RuntimeException exception) {
            var failure = P4E1SourceFailure.runtime(
                    P4E1SourceFailure.Code.INTERNAL_RUNTIME_FAILURE,
                    P4E1AuditStage.RAW_ROOT_CAPTURE,
                    exception);
            return context.fail(new CaptureResult.Incomplete(failure));
        }
    }

    private static TreeMap<UUID, SelectedPlayerSource> arbitrate(
            P4E1PlayerDataDirectorySnapshot directory,
            P4E1IntegratedSnapshotTraversal.Selection integrated,
            ArrayList<OnlineIdentity> online) {
        var selected = new TreeMap<UUID, SelectedPlayerSource>(Comparator.naturalOrder());
        for (var route : directory.records()) {
            if (selected.put(route.playerId(), new SelectedPlayerSource.Disk(route)) != null) {
                throw new IllegalStateException("P4E1_DUPLICATE_DISK_ROUTE");
            }
        }
        if (integrated instanceof P4E1IntegratedSnapshotTraversal.Selection.Integrated runtime) {
            selected.put(runtime.ownerId(), new SelectedPlayerSource.Integrated(runtime));
        }
        for (var identity : online) {
            selected.put(identity.playerId(), new SelectedPlayerSource.Online(identity));
        }
        return selected;
    }

    private static ArrayList<OnlineIdentity> captureOnlineIdentities(
            MinecraftServer server,
            PlayerSkillAttachmentService service,
            long relevantMaximum) {
        var liveView = server.getPlayerList().getPlayers();
        var observed = new ArrayList<OnlineIdentity>();
        var unique = new TreeMap<UUID, ServerPlayer>(Comparator.naturalOrder());
        var observationLimit = Math.toIntExact(Math.addExact(relevantMaximum, 1L));
        try {
            for (var player : liveView) {
                Objects.requireNonNull(player, "online player");
                if (player.getServer() != server) {
                    throw new IllegalStateException("P4E1_ONLINE_PLAYER_SERVER_MISMATCH");
                }
                var playerId = Objects.requireNonNull(player.getUUID(), "online player UUID");
                if (unique.put(playerId, player) != null) {
                    throw new IllegalStateException("P4E1_ONLINE_PLAYER_UUID_DUPLICATE");
                }
                observed.add(new OnlineIdentity(
                        playerId, player, service.observeOnlineForRootAudit(player)));
                if (observed.size() == observationLimit) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            discardOnlineNew(service, observed);
            throw exception;
        }
        observed.sort(Comparator.comparing(OnlineIdentity::playerId));
        return observed;
    }

    private static CaptureResult processPlayerSource(
            CaptureContext context, SelectedPlayerSource selected) {
        return switch (selected) {
            case SelectedPlayerSource.Online online -> processOnline(context, online.identity());
            case SelectedPlayerSource.Integrated integrated ->
                    processIntegrated(context, integrated.selection());
            case SelectedPlayerSource.Disk disk -> processDisk(context, disk.route());
        };
    }

    private static CaptureResult processOnline(
            CaptureContext context, OnlineIdentity identity) {
        var service = context.attachmentService();
        var handle = identity.handle();
        var state = service.onlineRootState(handle);
        if (state == PlayerSkillAttachmentService.OnlineRootAuditState.QUARANTINED) {
            service.onlineRootUnavailableReason(handle);
            service.discardOnlineRootProjection(handle);
            service.discardOnlineRootWitness(handle);
            context.unprocessedOnline().remove(identity);
            return new CaptureResult.Incomplete(P4E1SourceFailure.forRoute(
                    P4E1SourceFailure.Code.ATTACHMENT_QUARANTINED,
                    P4E1AuditStage.P4C_ADMISSION,
                    identity.playerId()));
        }
        var rootCount = service.onlineRootCount(handle);
        var sourceIndex = context.sources().size();
        var reservation = context.buffer().reserve(context.budget(), sourceIndex, rootCount);
        if (reservation instanceof P4E1RawClaimBuffer.ReservationResult.OverLimit over) {
            service.discardOnlineRootProjection(handle);
            service.discardOnlineRootWitness(handle);
            context.unprocessedOnline().remove(identity);
            return new CaptureResult.OverLimit(P4E1SourceFailure.rootCapacity(over.exceeded()));
        }
        var reserved = ((P4E1RawClaimBuffer.ReservationResult.Reserved) reservation)
                .reservation();
        service.drainOnlineRootProjection(handle, new ReservationSink(reserved));
        reserved.finish();
        context.sources().add(new SourceEntry(
                P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT,
                SourceKind.ONLINE,
                Optional.of(identity.playerId()),
                reserved.claimStart(),
                reserved.expectedCount(),
                new SourceWitness.Online(
                        service, identity.player(), identity.playerId(), handle)));
        context.unprocessedOnline().remove(identity);
        return null;
    }

    private static CaptureResult processIntegrated(
            CaptureContext context,
            P4E1IntegratedSnapshotTraversal.Selection.Integrated selection) {
        var traversed = selection.traverse(context.budget());
        if (traversed instanceof P4E1IntegratedSnapshotTraversal.TraversalResult.Failure failed) {
            return new CaptureResult.Incomplete(failed.failure());
        }
        var ready = (P4E1IntegratedSnapshotTraversal.TraversalResult.Ready) traversed;
        var freshness = selection.freshnessFailure(context.server());
        if (freshness.isPresent()) {
            return new CaptureResult.Incomplete(freshness.orElseThrow());
        }
        if (ready.attachment().isEmpty()) {
            addZeroSource(
                    context,
                    selection.ownerId(),
                    SourceKind.INTEGRATED_RUNTIME_SNAPSHOT,
                    new SourceWitness.Integrated(selection));
            return null;
        }
        var admitted = P4E1BoundPlayerSkillAttachmentAdmissionSource.admitIntegratedObservation(
                context.attachmentService(),
                ready.attachment().orElseThrow(),
                context.server().registryAccess());
        return processAdmitted(
                context,
                selection.ownerId(),
                SourceKind.INTEGRATED_RUNTIME_SNAPSHOT,
                new SourceWitness.Integrated(selection),
                admitted);
    }

    private static CaptureResult processDisk(
            CaptureContext context,
            P4E1PlayerDataDirectorySnapshot.RouteRecord route) {
        var selected = P4E1PlayerDataSourceSelector.select(
                route,
                context.budget(),
                P4E1PlayerDataFileReader.reader(
                        PlayerSkillAttachmentService
                                .maximumRootAuditAttachmentEncodedBytes()));
        if (selected instanceof P4E1PlayerDataSourceSelector.SelectionResult.Failure<?> failed) {
            return new CaptureResult.Incomplete(failed.failure());
        }
        if (selected instanceof P4E1PlayerDataSourceSelector.SelectionResult.Zero<?>) {
            throw new IllegalStateException("P4E1_SNAPSHOTTED_DISK_ROUTE_STABLY_ABSENT");
        }
        var ready = (P4E1PlayerDataSourceSelector.SelectionResult.Ready<
                P4E1PlayerDataNbtScanner.ScanResult.Ready>) selected;
        var kind = ready.source() == P4E1PlayerDataSourceSelector.SourceKind.PRIMARY
                ? SourceKind.DISK_PRIMARY
                : SourceKind.DISK_OLD;
        var attachment = ready.value().attachment();
        if (attachment instanceof P4E1PlayerDataNbtScanner.AttachmentObservation.Missing) {
            addZeroSource(context, route.playerId(), kind, SourceWitness.Disk.INSTANCE);
            return null;
        }
        if (attachment instanceof P4E1PlayerDataNbtScanner.AttachmentObservation.Oversize) {
            return new CaptureResult.Incomplete(P4E1SourceFailure.forRoute(
                    P4E1SourceFailure.Code.ATTACHMENT_QUARANTINED,
                    P4E1AuditStage.P4C_ADMISSION,
                    route.playerId()));
        }
        var admitted = P4E1BoundPlayerSkillAttachmentAdmissionSource.admitDiskObservation(
                context.attachmentService(),
                (P4E1PlayerDataNbtScanner.AttachmentObservation.Present) attachment,
                context.server().registryAccess());
        return processAdmitted(
                context,
                route.playerId(),
                kind,
                SourceWitness.Disk.INSTANCE,
                admitted);
    }

    private static CaptureResult processAdmitted(
            CaptureContext context,
            UUID playerId,
            SourceKind kind,
            SourceWitness witness,
            PlayerSkillAttachmentService.RootAuditAdmissionResult admitted) {
        var admissionExceeded = context.budget()
                .checkpointAttachmentAdmissionAfterInvocation(P4E1AuditStage.P4C_ADMISSION);
        if (admissionExceeded.isPresent()) {
            if (admitted instanceof PlayerSkillAttachmentService.RootAuditAdmitted ready) {
                context.attachmentService().discardRootProjection(ready);
            }
            return new CaptureResult.Incomplete(
                    P4E1SourceFailure.capacity(admissionExceeded.orElseThrow()));
        }
        if (admitted instanceof PlayerSkillAttachmentService.RootAuditRejected) {
            return new CaptureResult.Incomplete(P4E1SourceFailure.forRoute(
                    P4E1SourceFailure.Code.ATTACHMENT_ADMISSION_REJECTED,
                    P4E1AuditStage.P4C_ADMISSION,
                    playerId));
        }
        if (admitted instanceof PlayerSkillAttachmentService.RootAuditOversize) {
            return new CaptureResult.Incomplete(P4E1SourceFailure.forRoute(
                    P4E1SourceFailure.Code.ATTACHMENT_QUARANTINED,
                    P4E1AuditStage.P4C_ADMISSION,
                    playerId));
        }
        var ready = (PlayerSkillAttachmentService.RootAuditAdmitted) admitted;
        var count = context.attachmentService().rootCount(ready);
        var sourceIndex = context.sources().size();
        var reservation = context.buffer().reserve(context.budget(), sourceIndex, count);
        if (reservation instanceof P4E1RawClaimBuffer.ReservationResult.OverLimit over) {
            context.attachmentService().discardRootProjection(ready);
            return new CaptureResult.OverLimit(P4E1SourceFailure.rootCapacity(over.exceeded()));
        }
        var reserved = ((P4E1RawClaimBuffer.ReservationResult.Reserved) reservation)
                .reservation();
        context.attachmentService().drainRootProjection(ready, new ReservationSink(reserved));
        reserved.finish();
        context.sources().add(new SourceEntry(
                P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT,
                kind,
                Optional.of(playerId),
                reserved.claimStart(),
                reserved.expectedCount(),
                witness));
        return null;
    }

    private static CaptureResult processJournal(CaptureContext context) {
        var journal = context.journal();
        if (!context.submissionPort().isP4E1JournalWitnessCurrent(
                context.server(), context.storeWitness(), journal)) {
            return new CaptureResult.Incomplete(P4E1SourceFailure.simple(
                    P4E1SourceFailure.Code.JOURNAL_UNAVAILABLE,
                    P4E1AuditStage.JOURNAL_READINESS));
        }
        var count = journal.rootCount(
                context.submissionPort(), context.server(), context.storeWitness());
        var sourceIndex = context.sources().size();
        var reservation = context.buffer().reserve(context.budget(), sourceIndex, count);
        if (reservation instanceof P4E1RawClaimBuffer.ReservationResult.OverLimit over) {
            journal.discardRoots(
                    context.submissionPort(), context.server(), context.storeWitness());
            journal.discardWitness();
            context.journalConsumed = true;
            return new CaptureResult.OverLimit(P4E1SourceFailure.rootCapacity(over.exceeded()));
        }
        var reserved = ((P4E1RawClaimBuffer.ReservationResult.Reserved) reservation)
                .reservation();
        journal.drain(
                context.submissionPort(),
                context.server(),
                context.storeWitness(),
                reserved::appendJournal);
        reserved.finish();
        context.sources().add(new SourceEntry(
                P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL,
                SourceKind.PENDING_JOURNAL,
                Optional.empty(),
                reserved.claimStart(),
                reserved.expectedCount(),
                new SourceWitness.Journal(journal.proofIdentity())));
        context.journalConsumed = true;
        return null;
    }

    private static void addZeroSource(
            CaptureContext context,
            UUID playerId,
            SourceKind kind,
            SourceWitness witness) {
        var sourceIndex = context.sources().size();
        var reservation = context.buffer().reserve(context.budget(), sourceIndex, 0);
        var reserved = ((P4E1RawClaimBuffer.ReservationResult.Reserved) reservation)
                .reservation();
        reserved.finish();
        context.sources().add(new SourceEntry(
                P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT,
                kind,
                Optional.of(playerId),
                reserved.claimStart(),
                0,
                witness));
    }

    private static void discardOnlineNew(
            PlayerSkillAttachmentService service, ArrayList<OnlineIdentity> identities) {
        for (var identity : identities) {
            service.discardOnlineRootAuditHandle(identity.handle());
        }
        identities.clear();
    }

    private static void discardPreSourceState(
            SkillDefinitionStoreSubmissionPort port,
            MinecraftServer server,
            StoreReadyWitness storeWitness,
            P4E1PendingJournalObservation.Ready journal,
            P4E1SourceInventory.Witness inventory) {
        inventory.discard();
        discardJournal(port, server, storeWitness, journal);
        storeWitness.discard();
    }

    private static void discardJournal(
            SkillDefinitionStoreSubmissionPort port,
            MinecraftServer server,
            StoreReadyWitness storeWitness,
            P4E1PendingJournalObservation.Ready journal) {
        journal.discardForFailure(port, server, storeWitness);
    }

    private static P4E1SourceFailure.Code mapJournalFailure(
            P4E1PendingJournalObservation.FailureCode code) {
        return switch (code) {
            case JOURNAL_NOT_READY -> P4E1SourceFailure.Code.JOURNAL_NOT_READY;
            case JOURNAL_UNAVAILABLE -> P4E1SourceFailure.Code.JOURNAL_UNAVAILABLE;
            case JOURNAL_TARGET_INVALID -> P4E1SourceFailure.Code.JOURNAL_TARGET_INVALID;
        };
    }

    private static CaptureResult.Incomplete incomplete(
            P4E1SourceFailure.Code code, P4E1AuditStage stage) {
        return new CaptureResult.Incomplete(P4E1SourceFailure.simple(code, stage));
    }

    sealed interface CaptureResult {
        record Captured(P4E1GlobalSourceCapture.Captured capture) implements CaptureResult {
            public Captured {
                Objects.requireNonNull(capture, "capture");
            }
        }

        record Incomplete(P4E1SourceFailure failure) implements CaptureResult {
            public Incomplete {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record OverLimit(P4E1SourceFailure failure) implements CaptureResult {
            public OverLimit {
                Objects.requireNonNull(failure, "failure");
                if (failure.code() != P4E1SourceFailure.Code.ROOT_CAPACITY_EXCEEDED) {
                    throw new IllegalArgumentException("OverLimit requires root capacity failure");
                }
            }
        }
    }

    static final class Captured {
        private MinecraftServer serverIdentity;
        private Thread creationThreadIdentity;
        private P4E1HeapFloorObservation heapObservation;
        private StoreReadyWitness storeWitness;
        private P4E1PendingJournalObservation.Ready journalWitness;
        private P4E1SourceInventory.Witness inventoryWitness;
        private P4E1PlayerDataDirectorySnapshot directoryWitness;
        private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
        private P4E1RawClaimBuffer claims;
        private ArrayList<SourceEntry> sources;
        private Summary summary;
        private boolean consumed;

        private Captured(
                MinecraftServer serverIdentity,
                Thread creationThreadIdentity,
                P4E1HeapFloorObservation heapObservation,
                StoreReadyWitness storeWitness,
                P4E1PendingJournalObservation.Ready journalWitness,
                P4E1SourceInventory.Witness inventoryWitness,
                P4E1PlayerDataDirectorySnapshot directoryWitness,
                P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
                P4E1RawClaimBuffer claims,
                ArrayList<SourceEntry> sources,
                Summary summary) {
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.creationThreadIdentity = Objects.requireNonNull(
                    creationThreadIdentity, "creationThreadIdentity");
            this.heapObservation = Objects.requireNonNull(heapObservation, "heapObservation");
            this.storeWitness = Objects.requireNonNull(storeWitness, "storeWitness");
            this.journalWitness = Objects.requireNonNull(journalWitness, "journalWitness");
            this.inventoryWitness = Objects.requireNonNull(inventoryWitness, "inventoryWitness");
            this.directoryWitness = Objects.requireNonNull(directoryWitness, "directoryWitness");
            this.integratedWitness = Objects.requireNonNull(integratedWitness, "integratedWitness");
            this.claims = Objects.requireNonNull(claims, "claims");
            this.sources = Objects.requireNonNull(sources, "sources");
            this.summary = Objects.requireNonNull(summary, "summary");
        }

        Claimed claim() {
            requireNew();
            consumed = true;
            var moved = new Claimed(
                    serverIdentity,
                    creationThreadIdentity,
                    heapObservation,
                    storeWitness,
                    journalWitness,
                    inventoryWitness,
                    directoryWitness,
                    integratedWitness,
                    claims,
                    sources,
                    summary);
            clearReferences();
            return moved;
        }

        void discard() {
            requireNew();
            consumed = true;
            cleanup(claims, sources, journalWitness, inventoryWitness, storeWitness);
            clearReferences();
        }

        Summary summary() {
            requireNew();
            return summary;
        }

        private void requireNew() {
            if (consumed || claims == null) {
                throw new IllegalStateException("P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED");
            }
            if (Thread.currentThread() != creationThreadIdentity) {
                throw new IllegalStateException("P4E1_GLOBAL_CAPTURE_WRONG_THREAD");
            }
        }

        private void clearReferences() {
            serverIdentity = null;
            creationThreadIdentity = null;
            heapObservation = null;
            storeWitness = null;
            journalWitness = null;
            inventoryWitness = null;
            directoryWitness = null;
            integratedWitness = null;
            claims = null;
            sources = null;
            summary = null;
        }
    }

    static final class Claimed {
        private MinecraftServer serverIdentity;
        private Thread creationThreadIdentity;
        private P4E1HeapFloorObservation heapObservation;
        private StoreReadyWitness storeWitness;
        private P4E1PendingJournalObservation.Ready journalWitness;
        private P4E1SourceInventory.Witness inventoryWitness;
        private P4E1PlayerDataDirectorySnapshot directoryWitness;
        private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
        private P4E1RawClaimBuffer claims;
        private ArrayList<SourceEntry> sources;
        private Summary summary;
        private boolean discarded;

        private Claimed(
                MinecraftServer serverIdentity,
                Thread creationThreadIdentity,
                P4E1HeapFloorObservation heapObservation,
                StoreReadyWitness storeWitness,
                P4E1PendingJournalObservation.Ready journalWitness,
                P4E1SourceInventory.Witness inventoryWitness,
                P4E1PlayerDataDirectorySnapshot directoryWitness,
                P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
                P4E1RawClaimBuffer claims,
                ArrayList<SourceEntry> sources,
                Summary summary) {
            this.serverIdentity = serverIdentity;
            this.creationThreadIdentity = creationThreadIdentity;
            this.heapObservation = heapObservation;
            this.storeWitness = storeWitness;
            this.journalWitness = journalWitness;
            this.inventoryWitness = inventoryWitness;
            this.directoryWitness = directoryWitness;
            this.integratedWitness = integratedWitness;
            this.claims = claims;
            this.sources = sources;
            this.summary = summary;
        }

        void visitClaims(ClaimVisitor visitor) {
            requireActive();
            Objects.requireNonNull(visitor, "visitor");
            for (var index = 0; index < claims.size(); index++) {
                visitor.visit(
                        index,
                        claims.referenceAt(index),
                        claims.sourceTableIndexAt(index),
                        claims.sourceLocalOrdinalAt(index),
                        claims.claimKindAt(index),
                        claims.equippedSlotAt(index));
            }
        }

        int sourceCount() {
            requireActive();
            return sources.size();
        }

        SourceEntry sourceAt(int index) {
            requireActive();
            return sources.get(index);
        }

        Summary summary() {
            requireActive();
            return summary;
        }

        void discard() {
            requireActive();
            discarded = true;
            cleanup(claims, sources, journalWitness, inventoryWitness, storeWitness);
            serverIdentity = null;
            creationThreadIdentity = null;
            heapObservation = null;
            storeWitness = null;
            journalWitness = null;
            inventoryWitness = null;
            directoryWitness = null;
            integratedWitness = null;
            claims = null;
            sources = null;
            summary = null;
        }

        private void requireActive() {
            if (discarded || claims == null) {
                throw new IllegalStateException("P4E1_CLAIMED_CAPTURE_DISCARDED");
            }
            if (Thread.currentThread() != creationThreadIdentity) {
                throw new IllegalStateException("P4E1_CLAIMED_CAPTURE_WRONG_THREAD");
            }
        }
    }

    @FunctionalInterface
    interface ClaimVisitor {
        void visit(
                int globalOrdinal,
                SkillReference reference,
                int sourceTableIndex,
                int sourceLocalOrdinal,
                P4E1RawClaimBuffer.ClaimKind kind,
                int equippedSlot);
    }

    enum SourceKind {
        ONLINE,
        INTEGRATED_RUNTIME_SNAPSHOT,
        DISK_PRIMARY,
        DISK_OLD,
        PENDING_JOURNAL
    }

    record SourceEntry(
            P4E1RootSourceFamily family,
            SourceKind kind,
            Optional<UUID> playerId,
            int claimStart,
            int claimCount,
            SourceWitness witness) {
        SourceEntry {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(kind, "kind");
            playerId = Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(witness, "witness");
            if (claimStart < 0 || claimCount < 0) {
                throw new IllegalArgumentException("claim range must be non-negative");
            }
            if ((family == P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT)
                    != playerId.isPresent()) {
                throw new IllegalArgumentException("player source identity mismatch");
            }
        }
    }

    sealed interface SourceWitness {
        record Online(
                PlayerSkillAttachmentService service,
                ServerPlayer playerIdentity,
                UUID playerId,
                PlayerSkillAttachmentService.OnlineRootAuditHandle handle)
                implements SourceWitness {
            public Online {
                Objects.requireNonNull(service, "service");
                Objects.requireNonNull(playerIdentity, "playerIdentity");
                Objects.requireNonNull(playerId, "playerId");
                Objects.requireNonNull(handle, "handle");
            }
        }

        record Integrated(P4E1IntegratedSnapshotTraversal.Selection.Integrated selection)
                implements SourceWitness {
            public Integrated {
                Objects.requireNonNull(selection, "selection");
            }
        }

        enum Disk implements SourceWitness {
            INSTANCE
        }

        record Journal(JournalTargetAuditProof proof) implements SourceWitness {
            public Journal {
                Objects.requireNonNull(proof, "proof");
            }
        }
    }

    record Summary(
            int playerSources,
            int journalSources,
            int onlineSources,
            int integratedSources,
            int diskPrimarySources,
            int diskOldSources,
            int rawClaims) {
        private static Summary from(ArrayList<SourceEntry> sources, int rawClaims) {
            var player = 0;
            var journal = 0;
            var online = 0;
            var integrated = 0;
            var primary = 0;
            var old = 0;
            for (var source : sources) {
                if (source.family() == P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT) {
                    player++;
                } else {
                    journal++;
                }
                switch (source.kind()) {
                    case ONLINE -> online++;
                    case INTEGRATED_RUNTIME_SNAPSHOT -> integrated++;
                    case DISK_PRIMARY -> primary++;
                    case DISK_OLD -> old++;
                    case PENDING_JOURNAL -> { }
                }
            }
            return new Summary(player, journal, online, integrated, primary, old, rawClaims);
        }
    }

    sealed interface StoreObservation {
        record Ready(StoreReadyWitness witness) implements StoreObservation {
            public Ready {
                Objects.requireNonNull(witness, "witness");
            }
        }

        enum Unavailable implements StoreObservation {
            INSTANCE
        }
    }

    static final class StoreReadyWitness {
        private SkillDefinitionStoreService owner;
        private MinecraftServer serverIdentity;
        private GramaryeSkillSavedData adapterIdentity;
        private SkillSavedDataState.Ready savedDataReadyIdentity;
        private SkillDefinitionStore storeIdentity;
        private SkillSavedDataInnerCarrier innerCarrierIdentity;
        private EncodedSkillStoreCarrier storeCarrierIdentity;
        private OpaquePendingAttachmentUpdatesBlob pendingIdentity;

        StoreReadyWitness(
                SkillDefinitionStoreService owner,
                MinecraftServer serverIdentity,
                GramaryeSkillSavedData adapterIdentity,
                SkillSavedDataState.Ready savedDataReadyIdentity,
                SkillDefinitionStore storeIdentity,
                SkillSavedDataInnerCarrier innerCarrierIdentity,
                EncodedSkillStoreCarrier storeCarrierIdentity,
                OpaquePendingAttachmentUpdatesBlob pendingIdentity) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.adapterIdentity = Objects.requireNonNull(adapterIdentity, "adapterIdentity");
            this.savedDataReadyIdentity = Objects.requireNonNull(
                    savedDataReadyIdentity, "savedDataReadyIdentity");
            this.storeIdentity = Objects.requireNonNull(storeIdentity, "storeIdentity");
            this.innerCarrierIdentity = Objects.requireNonNull(
                    innerCarrierIdentity, "innerCarrierIdentity");
            this.storeCarrierIdentity = Objects.requireNonNull(
                    storeCarrierIdentity, "storeCarrierIdentity");
            this.pendingIdentity = Objects.requireNonNull(pendingIdentity, "pendingIdentity");
        }

        void requireBinding(SkillDefinitionStoreService candidate, MinecraftServer server) {
            requireActive();
            if (owner != Objects.requireNonNull(candidate, "candidate")) {
                throw new IllegalStateException("P4E1_STORE_WITNESS_OWNER_MISMATCH");
            }
            if (serverIdentity != Objects.requireNonNull(server, "server")) {
                throw new IllegalStateException("P4E1_STORE_WITNESS_SERVER_MISMATCH");
            }
        }

        boolean matches(GramaryeSkillSavedData adapter) {
            requireActive();
            return adapterIdentity == Objects.requireNonNull(adapter, "adapter")
                    && adapter.state() == savedDataReadyIdentity
                    && savedDataReadyIdentity.store() == storeIdentity
                    && savedDataReadyIdentity.innerCarrier() == innerCarrierIdentity
                    && savedDataReadyIdentity.storeCarrier() == storeCarrierIdentity
                    && innerCarrierIdentity.storeCarrier() == storeCarrierIdentity
                    && innerCarrierIdentity.pending() == pendingIdentity;
        }

        SkillSavedDataState.Ready savedDataReadyIdentity() {
            requireActive();
            return savedDataReadyIdentity;
        }

        void discard() {
            requireActive();
            owner = null;
            serverIdentity = null;
            adapterIdentity = null;
            savedDataReadyIdentity = null;
            storeIdentity = null;
            innerCarrierIdentity = null;
            storeCarrierIdentity = null;
            pendingIdentity = null;
        }

        private void requireActive() {
            if (owner == null) {
                throw new IllegalStateException("P4E1_STORE_WITNESS_DISCARDED");
            }
        }
    }

    private static void cleanup(
            P4E1RawClaimBuffer claims,
            ArrayList<SourceEntry> sources,
            P4E1PendingJournalObservation.Ready journal,
            P4E1SourceInventory.Witness inventory,
            StoreReadyWitness store) {
        for (var source : sources) {
            if (source.witness() instanceof SourceWitness.Online online) {
                online.service().discardOnlineRootWitness(online.handle());
            }
        }
        sources.clear();
        claims.discard();
        journal.discardWitness();
        inventory.discard();
        store.discard();
    }

    private record OnlineIdentity(
            UUID playerId,
            ServerPlayer player,
            PlayerSkillAttachmentService.OnlineRootAuditHandle handle) {
        private OnlineIdentity {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(handle, "handle");
        }
    }

    private sealed interface SelectedPlayerSource {
        record Online(OnlineIdentity identity) implements SelectedPlayerSource {
        }

        record Integrated(P4E1IntegratedSnapshotTraversal.Selection.Integrated selection)
                implements SelectedPlayerSource {
        }

        record Disk(P4E1PlayerDataDirectorySnapshot.RouteRecord route)
                implements SelectedPlayerSource {
        }
    }

    private static final class CaptureContext {
        private final MinecraftServer server;
        private final PlayerSkillAttachmentService attachmentService;
        private final SkillDefinitionStoreSubmissionPort submissionPort;
        private final StoreReadyWitness storeWitness;
        private final P4E1PendingJournalObservation.Ready journal;
        private final P4E1SourceInventory.Witness inventory;
        private final P4E1PlayerDataDirectorySnapshot directory;
        private final P4E1IntegratedSnapshotTraversal.Selection integrated;
        private final P4E1AuditBudget budget;
        private final P4E1RawClaimBuffer buffer;
        private final ArrayList<SourceEntry> sources;
        private final ArrayList<OnlineIdentity> unprocessedOnline;
        private boolean journalConsumed;

        private CaptureContext(
                MinecraftServer server,
                PlayerSkillAttachmentService attachmentService,
                SkillDefinitionStoreSubmissionPort submissionPort,
                StoreReadyWitness storeWitness,
                P4E1PendingJournalObservation.Ready journal,
                P4E1SourceInventory.Witness inventory,
                P4E1PlayerDataDirectorySnapshot directory,
                P4E1IntegratedSnapshotTraversal.Selection integrated,
                P4E1AuditBudget budget,
                P4E1RawClaimBuffer buffer,
                ArrayList<SourceEntry> sources,
                ArrayList<OnlineIdentity> unprocessedOnline) {
            this.server = server;
            this.attachmentService = attachmentService;
            this.submissionPort = submissionPort;
            this.storeWitness = storeWitness;
            this.journal = journal;
            this.inventory = inventory;
            this.directory = directory;
            this.integrated = integrated;
            this.budget = budget;
            this.buffer = buffer;
            this.sources = sources;
            this.unprocessedOnline = unprocessedOnline;
        }

        private MinecraftServer server() {
            return server;
        }

        private PlayerSkillAttachmentService attachmentService() {
            return attachmentService;
        }

        private SkillDefinitionStoreSubmissionPort submissionPort() {
            return submissionPort;
        }

        private StoreReadyWitness storeWitness() {
            return storeWitness;
        }

        private P4E1PendingJournalObservation.Ready journal() {
            return journal;
        }

        private P4E1AuditBudget budget() {
            return budget;
        }

        private P4E1RawClaimBuffer buffer() {
            return buffer;
        }

        private ArrayList<SourceEntry> sources() {
            return sources;
        }

        private ArrayList<OnlineIdentity> unprocessedOnline() {
            return unprocessedOnline;
        }

        private CaptureResult fail(CaptureResult failure) {
            for (var source : sources) {
                if (source.witness() instanceof SourceWitness.Online online) {
                    online.service().discardOnlineRootAuditHandle(online.handle());
                }
            }
            sources.clear();
            discardOnlineNew(attachmentService, unprocessedOnline);
            buffer.discard();
            if (!journalConsumed) {
                discardJournal(submissionPort, server, storeWitness, journal);
            }
            inventory.discard();
            storeWitness.discard();
            return failure;
        }
    }

    private static final class ReservationSink
            implements PlayerSkillAttachmentService.RootAuditSink {
        private final P4E1RawClaimBuffer.Reservation reservation;

        private ReservationSink(P4E1RawClaimBuffer.Reservation reservation) {
            this.reservation = Objects.requireNonNull(reservation, "reservation");
        }

        @Override
        public void latest(SkillReference reference) {
            reservation.appendLatest(reference);
        }

        @Override
        public void equipped(int slot, SkillReference reference) {
            reservation.appendEquipped(slot, reference);
        }
    }
}
