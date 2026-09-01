package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/** Checkpoints 1-15 of the unpublished, read-only P4-E1-B1 global source capture. */
final class P4E1GlobalSourceCapture {
    private P4E1GlobalSourceCapture() {
    }

    static CaptureResult capture(
            MinecraftServer server,
            SkillDefinitionStoreService storeService,
            PlayerSkillAttachmentService attachmentService,
            P4E1GroupedStoreAudit owner) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(storeService, "storeService");
        Objects.requireNonNull(attachmentService, "attachmentService");
        Objects.requireNonNull(owner, "owner");
        SkillDefinitionStoreService.requireServerThread(server);
        owner.requireCaptureBinding(server, Thread.currentThread(), server.getTickCount());

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
        var preSource = new PreSourceOwnership(
                server, attachmentService, submissionPort, storeWitness);
        try {
            var journalResult = submissionPort.observeP4E1Journal(server, storeWitness);
            if (journalResult instanceof P4E1PendingJournalObservation.Result.Incomplete failed) {
                return incomplete(
                        mapJournalFailure(failed.code()), P4E1AuditStage.JOURNAL_READINESS);
            }
            var journal = ((P4E1PendingJournalObservation.Result.Available) journalResult)
                    .observation();
            preSource.retainJournal(journal);

            var inventoryResult = P4E1SourceInventory.capture(attachmentService, journal);
            if (inventoryResult instanceof P4E1SourceInventory.Result.Missing) {
                return incomplete(
                        P4E1SourceFailure.Code.INVENTORY_PROVIDER_MISSING,
                        P4E1AuditStage.JOURNAL_READINESS);
            }
            var inventory = ((P4E1SourceInventory.Result.Ready) inventoryResult).witness();
            preSource.retainInventory(inventory);

            var directoryResult = P4E1PlayerDataDirectorySnapshot.capture(
                    P4E1PlayerDataDirectorySnapshot.resolveDirectory(server), budget);
            if (directoryResult instanceof P4E1PlayerDataDirectorySnapshot.CaptureResult.Failure
                    failed) {
                return new CaptureResult.Incomplete(failed.failure());
            }
            var directory = ((P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready) directoryResult)
                    .snapshot();

            var playerListIdentity = Objects.requireNonNull(
                    server.getPlayerList(), "server PlayerList");
            var onlineCapture = captureOnlineIdentities(
                    server,
                    playerListIdentity,
                    attachmentService,
                    budget.maximum(P4E1AuditCounter.RELEVANT_RECORDS));
            var online = onlineCapture.identities();
            preSource.retainOnline(online);

            var integrated = P4E1IntegratedSnapshotTraversal.captureForGlobal(server, budget);
            if (integrated instanceof P4E1IntegratedSnapshotTraversal.Selection.Failure failed) {
                return new CaptureResult.Incomplete(failed.failure());
            }
            if (onlineCapture.relevantCapacityGuaranteed()) {
                return onlineRelevantCapacityFailure(
                        budget, onlineCapture.initialLiveSize());
            }

            var selected = arbitrate(directory, integrated, online);
            var buffer = new P4E1RawClaimBuffer();
            var sources = new ArrayList<SourceEntry>(selected.size() + 1);
            var selectedFiles = new ArrayList<
                    P4E1PlayerDataSourceSelector.SelectedFileWitness>(selected.size());
            var unprocessedOnline = new ArrayList<>(online);
            var integratedOwnerCount = selectedIntegratedOwnerCount(integrated, online);
            var diskOwnerCount = Math.subtractExact(
                    Math.subtractExact(selected.size(), online.size()), integratedOwnerCount);
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
                    selectedFiles,
                    unprocessedOnline,
                    selected.size(),
                    online.size(),
                    integratedOwnerCount,
                    diskOwnerCount);
            preSource.transferOwnership();
            try {
                for (var selectedSource : selected.values()) {
                    var relevantExceeded = budget.checkpointSingle(
                            P4E1AuditCounter.RELEVANT_RECORDS,
                            P4E1AuditStage.RELEVANT_RECORDS,
                            1L);
                    if (relevantExceeded.isPresent()) {
                        return context.fail(new CaptureResult.Incomplete(
                                P4E1SourceFailure.capacity(
                                        relevantExceeded.orElseThrow())));
                    }
                    var processed = processPlayerSource(context, selectedSource);
                    if (processed != null) {
                        return context.fail(processed);
                    }
                }
                context.markPlayerRootsComplete();

                var directoryVerification = directory.verifyUnchanged();
                if (directoryVerification
                        instanceof P4E1PlayerDataDirectorySnapshot.VerificationResult.Failure
                                failed) {
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
                var published = new CaptureResult.Captured(new Captured(
                        owner,
                        server,
                        Thread.currentThread(),
                        server.getTickCount(),
                        playerListIdentity,
                        qualified.observation(),
                        storeWitness,
                        journal,
                        inventory,
                        directory,
                        integrated,
                        buffer,
                        sources,
                        selectedFiles,
                        summary));
                context.transferOwnership();
                return published;
            } catch (RuntimeException exception) {
                var failure = P4E1SourceFailure.runtime(
                        P4E1SourceFailure.Code.INTERNAL_RUNTIME_FAILURE,
                        P4E1AuditStage.RAW_ROOT_CAPTURE,
                        exception);
                return context.fail(new CaptureResult.Incomplete(failure));
            } finally {
                context.discardIfOwned();
            }
        } finally {
            preSource.discardIfOwned();
        }
    }

    private static TreeMap<UUID, SelectedPlayerSource> arbitrate(
            P4E1PlayerDataDirectorySnapshot directory,
            P4E1IntegratedSnapshotTraversal.Selection integrated,
            ArrayList<OnlineIdentity> online) {
        var selected = new TreeMap<UUID, SelectedPlayerSource>(Comparator.naturalOrder());
        var directoryRecords = directory.records();
        for (var index = 0; index < directoryRecords.size(); index++) {
            var route = directoryRecords.get(index);
            if (selected.put(route.playerId(), new SelectedPlayerSource.Disk(route)) != null) {
                throw new IllegalStateException("P4E1_DUPLICATE_DISK_ROUTE");
            }
        }
        if (integrated instanceof P4E1IntegratedSnapshotTraversal.Selection.Integrated runtime) {
            selected.put(runtime.ownerId(), new SelectedPlayerSource.Integrated(runtime));
        }
        for (var index = 0; index < online.size(); index++) {
            var identity = online.get(index);
            selected.put(identity.playerId(), new SelectedPlayerSource.Online(identity));
        }
        return selected;
    }

    private static OnlineIdentityCapture captureOnlineIdentities(
            MinecraftServer server,
            PlayerList playerListIdentity,
            PlayerSkillAttachmentService service,
            long relevantMaximum) {
        if (server.getPlayerList()
                != Objects.requireNonNull(playerListIdentity, "playerListIdentity")) {
            throw new IllegalStateException("P4E1_PLAYER_LIST_IDENTITY_CHANGED");
        }
        var liveView = playerListIdentity.getPlayers();
        var initialLiveSize = liveView.size();
        var observed = new ArrayList<OnlineIdentity>();
        var unique = new TreeMap<UUID, ServerPlayer>(Comparator.naturalOrder());
        var observationLimit = Math.toIntExact(Math.addExact(relevantMaximum, 1L));
        var complete = false;
        PlayerSkillAttachmentService.OnlineRootAuditHandle pendingHandle = null;
        try {
            for (var index = 0; index < initialLiveSize; index++) {
                var player = liveView.get(index);
                Objects.requireNonNull(player, "online player");
                if (player.getServer() != server) {
                    throw new IllegalStateException("P4E1_ONLINE_PLAYER_SERVER_MISMATCH");
                }
                var playerId = Objects.requireNonNull(player.getUUID(), "online player UUID");
                if (unique.put(playerId, player) != null) {
                    throw new IllegalStateException("P4E1_ONLINE_PLAYER_UUID_DUPLICATE");
                }
                pendingHandle = service.observeOnlineForRootAudit(player);
                observed.add(new OnlineIdentity(playerId, player, pendingHandle));
                pendingHandle = null;
                if (observed.size() == observationLimit) {
                    break;
                }
            }
            if (server.getPlayerList() != playerListIdentity
                    || liveView.size() != initialLiveSize) {
                throw new IllegalStateException("P4E1_ONLINE_PLAYER_LIST_CHANGED_DURING_CAPTURE");
            }
            observed.sort(Comparator.comparing(OnlineIdentity::playerId));
            var result = new OnlineIdentityCapture(
                    observed, initialLiveSize, observationLimit);
            complete = true;
            return result;
        } finally {
            try {
                if (pendingHandle != null) {
                    try {
                        service.discardOnlineRootAuditHandle(pendingHandle);
                    } catch (RuntimeException ignored) {
                        // The local owner drops the handle reference below.
                    }
                }
            } finally {
                pendingHandle = null;
                if (!complete) {
                    discardOnlineNewSafely(service, observed);
                }
            }
        }
    }

    static CaptureResult.Incomplete onlineRelevantCapacityFailure(
            P4E1AuditBudget budget, long exactInitialLiveSize) {
        Objects.requireNonNull(budget, "budget");
        var counter = P4E1AuditCounter.RELEVANT_RECORDS;
        var maximum = budget.maximum(counter);
        if (exactInitialLiveSize <= maximum) {
            throw new IllegalArgumentException(
                    "online live size does not prove relevant-record excess");
        }
        var remaining = Math.subtractExact(maximum, budget.observed(counter));
        var firstExcess = budget.checkpointSingle(
                counter,
                P4E1AuditStage.RELEVANT_RECORDS,
                Math.addExact(remaining, 1L));
        if (firstExcess.isEmpty()) {
            throw new IllegalStateException(
                    "online relevant-record excess was not observed");
        }
        return new CaptureResult.Incomplete(
                P4E1SourceFailure.capacity(firstExcess.orElseThrow()));
    }

    private static int selectedIntegratedOwnerCount(
            P4E1IntegratedSnapshotTraversal.Selection integrated,
            ArrayList<OnlineIdentity> online) {
        if (!(integrated
                instanceof P4E1IntegratedSnapshotTraversal.Selection.Integrated selected)) {
            return 0;
        }
        for (var index = 0; index < online.size(); index++) {
            if (online.get(index).playerId().equals(selected.ownerId())) {
                return 0;
            }
        }
        return 1;
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
        context.selectedFiles().add(ready.witness());
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
        var ownsProjection = true;
        try {
            var count = context.attachmentService().rootCount(ready);
            var sourceIndex = context.sources().size();
            var reservation = context.buffer().reserve(context.budget(), sourceIndex, count);
            if (reservation instanceof P4E1RawClaimBuffer.ReservationResult.OverLimit over) {
                context.attachmentService().discardRootProjection(ready);
                ownsProjection = false;
                return new CaptureResult.OverLimit(
                        P4E1SourceFailure.rootCapacity(over.exceeded()));
            }
            var reserved = ((P4E1RawClaimBuffer.ReservationResult.Reserved) reservation)
                    .reservation();
            // The service consumes and clears this capability before issuing any callback.
            var sink = new ReservationSink(reserved);
            ownsProjection = false;
            context.attachmentService().drainRootProjection(
                    ready, sink);
            reserved.finish();
            context.sources().add(new SourceEntry(
                    P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT,
                    kind,
                    Optional.of(playerId),
                    reserved.claimStart(),
                    reserved.expectedCount(),
                    witness));
            return null;
        } finally {
            if (ownsProjection) {
                try {
                    context.attachmentService().discardRootProjection(ready);
                } catch (RuntimeException ignored) {
                    // The local owner drops the capability reference below.
                }
            }
        }
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
        context.recordJournalRootCount(count);
        var sourceIndex = context.sources().size();
        var reservation = context.buffer().reserve(context.budget(), sourceIndex, count);
        if (reservation instanceof P4E1RawClaimBuffer.ReservationResult.OverLimit over) {
            journal.discardRoots(
                    context.submissionPort(), context.server(), context.storeWitness());
            context.journalConsumed = true;
            return new CaptureResult.OverLimit(P4E1SourceFailure.rootCapacity(over.exceeded()));
        }
        var reserved = ((P4E1RawClaimBuffer.ReservationResult.Reserved) reservation)
                .reservation();
        // The journal transitions to witness-only before issuing any callback.
        P4E1PendingJournalObservation.TargetSink sink = reserved::appendJournal;
        journal.drain(
                context.submissionPort(),
                context.server(),
                context.storeWitness(),
                sink);
        context.journalConsumed = true;
        reserved.finish();
        context.sources().add(new SourceEntry(
                P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL,
                SourceKind.PENDING_JOURNAL,
                Optional.empty(),
                reserved.claimStart(),
                reserved.expectedCount(),
                new SourceWitness.Journal(journal.proofIdentity())));
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

    private static void discardOnlineNewSafely(
            PlayerSkillAttachmentService service, ArrayList<OnlineIdentity> identities) {
        try {
            for (var index = 0; index < identities.size(); index++) {
                var identity = identities.get(index);
                try {
                    service.discardOnlineRootAuditHandle(identity.handle());
                } catch (RuntimeException ignored) {
                    // The local list drops the witness even if its lifecycle was already consumed.
                }
            }
        } finally {
            identities.clear();
        }
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

        record Incomplete(P4E1SourceFailure failure, ObservedSummary observedSummary)
                implements CaptureResult {
            public Incomplete {
                Objects.requireNonNull(failure, "failure");
                Objects.requireNonNull(observedSummary, "observedSummary");
            }

            Incomplete(P4E1SourceFailure failure) {
                this(failure, ObservedSummary.empty());
            }
        }

        record OverLimit(P4E1SourceFailure failure, ObservedSummary observedSummary)
                implements CaptureResult {
            public OverLimit {
                Objects.requireNonNull(failure, "failure");
                Objects.requireNonNull(observedSummary, "observedSummary");
                if (failure.code() != P4E1SourceFailure.Code.ROOT_CAPACITY_EXCEEDED) {
                    throw new IllegalArgumentException("OverLimit requires root capacity failure");
                }
            }

            OverLimit(P4E1SourceFailure failure) {
                this(failure, ObservedSummary.empty());
            }
        }
    }

    record ObservedSummary(
            OptionalInt selectedOwnerCount,
            OptionalInt onlineOwnerCount,
            OptionalInt integratedOwnerCount,
            OptionalInt diskOwnerCount,
            OptionalInt playerRootClaimCount,
            OptionalInt journalRootClaimCount,
            OptionalInt totalRawRootClaimCount,
            OptionalInt sourceCount) {
        ObservedSummary {
            requireNonNegative(selectedOwnerCount, "selectedOwnerCount");
            requireNonNegative(onlineOwnerCount, "onlineOwnerCount");
            requireNonNegative(integratedOwnerCount, "integratedOwnerCount");
            requireNonNegative(diskOwnerCount, "diskOwnerCount");
            requireNonNegative(playerRootClaimCount, "playerRootClaimCount");
            requireNonNegative(journalRootClaimCount, "journalRootClaimCount");
            requireNonNegative(totalRawRootClaimCount, "totalRawRootClaimCount");
            requireNonNegative(sourceCount, "sourceCount");
        }

        static ObservedSummary empty() {
            var absent = OptionalInt.empty();
            return new ObservedSummary(
                    absent, absent, absent, absent, absent, absent, absent, absent);
        }

        private static void requireNonNegative(OptionalInt value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isPresent() && value.getAsInt() < 0) {
                throw new IllegalArgumentException(name + " must be non-negative when present");
            }
        }
    }

    static final class Captured {
        private P4E1GroupedStoreAudit ownerIdentity;
        private MinecraftServer serverIdentity;
        private Thread creationThreadIdentity;
        private int capturedTick;
        private PlayerList playerListIdentity;
        private P4E1HeapFloorObservation heapObservation;
        private StoreReadyWitness storeWitness;
        private P4E1PendingJournalObservation.Ready journalWitness;
        private P4E1SourceInventory.Witness inventoryWitness;
        private P4E1PlayerDataDirectorySnapshot directoryWitness;
        private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
        private P4E1RawClaimBuffer claims;
        private ArrayList<SourceEntry> sources;
        private ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles;
        private Summary summary;
        private boolean consumed;

        private Captured(
                P4E1GroupedStoreAudit ownerIdentity,
                MinecraftServer serverIdentity,
                Thread creationThreadIdentity,
                int capturedTick,
                PlayerList playerListIdentity,
                P4E1HeapFloorObservation heapObservation,
                StoreReadyWitness storeWitness,
                P4E1PendingJournalObservation.Ready journalWitness,
                P4E1SourceInventory.Witness inventoryWitness,
                P4E1PlayerDataDirectorySnapshot directoryWitness,
                P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
                P4E1RawClaimBuffer claims,
                ArrayList<SourceEntry> sources,
                ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles,
                Summary summary) {
            this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity");
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.creationThreadIdentity = Objects.requireNonNull(
                    creationThreadIdentity, "creationThreadIdentity");
            this.capturedTick = capturedTick;
            this.playerListIdentity = Objects.requireNonNull(
                    playerListIdentity, "playerListIdentity");
            this.heapObservation = Objects.requireNonNull(heapObservation, "heapObservation");
            this.storeWitness = Objects.requireNonNull(storeWitness, "storeWitness");
            this.journalWitness = Objects.requireNonNull(journalWitness, "journalWitness");
            this.inventoryWitness = Objects.requireNonNull(inventoryWitness, "inventoryWitness");
            this.directoryWitness = Objects.requireNonNull(directoryWitness, "directoryWitness");
            this.integratedWitness = Objects.requireNonNull(integratedWitness, "integratedWitness");
            this.claims = Objects.requireNonNull(claims, "claims");
            this.sources = Objects.requireNonNull(sources, "sources");
            this.selectedFiles = Objects.requireNonNull(selectedFiles, "selectedFiles");
            this.summary = Objects.requireNonNull(summary, "summary");
        }

        Claimed claim(
                P4E1GroupedStoreAudit owner,
                ProductThreadPrecondition.Decision decision) {
            requireUnconsumed();
            Objects.requireNonNull(decision, "decision");
            consumed = true;
            Claimed moved = null;
            try {
                moved = new Claimed(
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
                        summary);
            } finally {
                if (moved == null) {
                    try {
                        cleanupUnpublished(
                                claims,
                                sources,
                                journalWitness,
                                inventoryWitness,
                                storeWitness);
                    } finally {
                        clearReferences();
                    }
                } else {
                    clearReferences();
                }
            }
            var accepted = false;
            try {
                moved.requireActive(owner, decision);
                accepted = true;
                return moved;
            } finally {
                if (!accepted) {
                    moved.discardAfterFailedClaim();
                }
            }
        }

        void discard() {
            requireUnconsumed();
            consumed = true;
            var correctThread = Thread.currentThread() == creationThreadIdentity;
            try {
                cleanupUnpublished(
                        claims, sources, journalWitness, inventoryWitness, storeWitness);
            } finally {
                clearReferences();
            }
            if (!correctThread) {
                throw new IllegalStateException("P4E1_GLOBAL_CAPTURE_WRONG_THREAD");
            }
        }

        Summary summary() {
            requireUnconsumed();
            if (Thread.currentThread() != creationThreadIdentity) {
                throw new IllegalStateException("P4E1_GLOBAL_CAPTURE_WRONG_THREAD");
            }
            return summary;
        }

        private void requireUnconsumed() {
            if (consumed || claims == null) {
                throw new IllegalStateException("P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED");
            }
        }

        private void clearReferences() {
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
        }
    }

    static final class Claimed {
        private P4E1GroupedStoreAudit ownerIdentity;
        private MinecraftServer serverIdentity;
        private Thread creationThreadIdentity;
        private int capturedTick;
        private PlayerList playerListIdentity;
        private P4E1HeapFloorObservation heapObservation;
        private StoreReadyWitness storeWitness;
        private P4E1PendingJournalObservation.Ready journalWitness;
        private P4E1SourceInventory.Witness inventoryWitness;
        private P4E1PlayerDataDirectorySnapshot directoryWitness;
        private P4E1IntegratedSnapshotTraversal.Selection integratedWitness;
        private P4E1RawClaimBuffer claims;
        private ArrayList<SourceEntry> sources;
        private ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles;
        private Summary summary;
        private boolean discarded;

        private Claimed(
                P4E1GroupedStoreAudit ownerIdentity,
                MinecraftServer serverIdentity,
                Thread creationThreadIdentity,
                int capturedTick,
                PlayerList playerListIdentity,
                P4E1HeapFloorObservation heapObservation,
                StoreReadyWitness storeWitness,
                P4E1PendingJournalObservation.Ready journalWitness,
                P4E1SourceInventory.Witness inventoryWitness,
                P4E1PlayerDataDirectorySnapshot directoryWitness,
                P4E1IntegratedSnapshotTraversal.Selection integratedWitness,
                P4E1RawClaimBuffer claims,
                ArrayList<SourceEntry> sources,
                ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles,
                Summary summary) {
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
        }

        void visitClaims(P4E1GroupedStoreAudit owner, ClaimVisitor visitor) {
            requireBoundActive(owner);
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

        int sourceCount(P4E1GroupedStoreAudit owner) {
            requireBoundActive(owner);
            return sources.size();
        }

        SourceEntry sourceAt(P4E1GroupedStoreAudit owner, int index) {
            requireBoundActive(owner);
            return sources.get(index);
        }

        Summary summary(P4E1GroupedStoreAudit owner) {
            requireBoundActive(owner);
            return summary;
        }

        P4E1GroupedStoreAudit.RawInput rawInput(P4E1GroupedStoreAudit owner) {
            requireBoundActive(owner);
            return new P4E1GroupedStoreAudit.RawInput(owner, claims, sources, summary);
        }

        boolean journalProofMatches(
                P4E1GroupedStoreAudit owner, JournalTargetAuditProof proof) {
            requireBoundActive(owner);
            return journalWitness.proofIdentity() == Objects.requireNonNull(proof, "proof");
        }

        boolean storeCurrent(P4E1GroupedStoreAudit owner) {
            requireBoundActive(owner);
            try {
                return storeWitness.owner.isP4E1StoreReadyCurrent(
                        serverIdentity, storeWitness);
            } catch (SkillSubsystemLifecycleException exception) {
                return switch (exception.code()) {
                    case BOOTSTRAP_NOT_INSTALLED, OVERWORLD_UNAVAILABLE,
                            CACHE_IDENTITY_MISMATCH -> false;
                    case WRONG_THREAD, BOOTSTRAP_ALREADY_INSTALLED,
                            JOURNAL_BOOTSTRAP_ALREADY_INSTALLED -> throw exception;
                };
            }
        }

        P4E1PendingJournalObservation.Currentness journalCurrentness(
                P4E1GroupedStoreAudit owner) {
            requireBoundActive(owner);
            var service = storeWitness.owner;
            return journalWitness.currentness(
                    service.submissionPort(),
                    serverIdentity,
                    storeWitness,
                    service.installedAdapter(serverIdentity));
        }

        P4E1StoreHistoryObservation observeExactHistory(
                P4E1GroupedStoreAudit owner, SkillId skillId) {
            requireBoundActive(owner);
            return storeWitness.storeIdentity.observeExactHistoryForRootAudit(
                    Objects.requireNonNull(skillId, "skillId"));
        }

        P4E1AuditedCapture moveToAudited(
                P4E1GroupedStoreAudit owner, int distinctSkillIdCount) {
            requireBoundActive(owner);
            if (distinctSkillIdCount < 0 || distinctSkillIdCount > claims.size()) {
                throw new IllegalArgumentException("invalid distinct SkillId count");
            }
            discarded = true;
            P4E1AuditedCapture moved = null;
            try {
                moved = new P4E1AuditedCapture(
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
                if (moved == null) {
                    cleanupUnpublished(
                            claims,
                            sources,
                            journalWitness,
                            inventoryWitness,
                            storeWitness);
                }
                clearReferences();
            }
        }

        void discard(P4E1GroupedStoreAudit owner) {
            requireBoundActive(owner);
            discarded = true;
            try {
                cleanupUnpublished(
                        claims, sources, journalWitness, inventoryWitness, storeWitness);
            } finally {
                clearReferences();
            }
        }

        void discardIfActive(P4E1GroupedStoreAudit owner) {
            if (discarded || claims == null) {
                return;
            }
            if (ownerIdentity != Objects.requireNonNull(owner, "owner")) {
                throw new IllegalStateException("P4E1_CLAIMED_CAPTURE_OWNER_MISMATCH");
            }
            discarded = true;
            try {
                cleanupUnpublished(
                        claims, sources, journalWitness, inventoryWitness, storeWitness);
            } finally {
                clearReferences();
            }
        }

        private void discardAfterFailedClaim() {
            discarded = true;
            try {
                cleanupUnpublished(
                        claims, sources, journalWitness, inventoryWitness, storeWitness);
            } finally {
                clearReferences();
            }
        }

        private void clearReferences() {
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
        }

        private void requireActive(
                P4E1GroupedStoreAudit owner,
                ProductThreadPrecondition.Decision decision) {
            Objects.requireNonNull(decision, "decision");
            requireClaimedOwner(owner);
            owner.requireCaptureBinding(
                    serverIdentity, creationThreadIdentity, capturedTick, decision);
        }

        private void requireBoundActive(P4E1GroupedStoreAudit owner) {
            requireClaimedOwner(owner);
            owner.requireCaptureBinding(serverIdentity, creationThreadIdentity, capturedTick);
        }

        private void requireClaimedOwner(P4E1GroupedStoreAudit owner) {
            if (discarded || claims == null) {
                throw new IllegalStateException("P4E1_CLAIMED_CAPTURE_DISCARDED");
            }
            if (ownerIdentity != Objects.requireNonNull(owner, "owner")) {
                throw new IllegalStateException("P4E1_CLAIMED_CAPTURE_OWNER_MISMATCH");
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
            int playerRootClaims,
            int journalRootClaims,
            int rawClaims) {
        Summary {
            if (playerSources < 0
                    || journalSources < 0
                    || onlineSources < 0
                    || integratedSources < 0
                    || diskPrimarySources < 0
                    || diskOldSources < 0
                    || playerRootClaims < 0
                    || journalRootClaims < 0
                    || rawClaims < 0
                    || Math.addExact(
                                    Math.addExact(onlineSources, integratedSources),
                                    Math.addExact(diskPrimarySources, diskOldSources))
                            != playerSources
                    || Math.addExact(playerRootClaims, journalRootClaims) != rawClaims) {
                throw new IllegalArgumentException("invalid P4-E1 capture summary");
            }
        }

        private static Summary from(ArrayList<SourceEntry> sources, int rawClaims) {
            var player = 0;
            var journal = 0;
            var online = 0;
            var integrated = 0;
            var primary = 0;
            var old = 0;
            var playerClaims = 0;
            var journalClaims = 0;
            for (var index = 0; index < sources.size(); index++) {
                var source = sources.get(index);
                if (source.family() == P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT) {
                    player++;
                    playerClaims = Math.addExact(playerClaims, source.claimCount());
                } else {
                    journal++;
                    journalClaims = Math.addExact(journalClaims, source.claimCount());
                }
                switch (source.kind()) {
                    case ONLINE -> online++;
                    case INTEGRATED_RUNTIME_SNAPSHOT -> integrated++;
                    case DISK_PRIMARY -> primary++;
                    case DISK_OLD -> old++;
                    case PENDING_JOURNAL -> { }
                }
            }
            if (Math.addExact(playerClaims, journalClaims) != rawClaims) {
                throw new IllegalStateException("P4E1_CAPTURE_SUMMARY_ROOT_COUNT_MISMATCH");
            }
            return new Summary(
                    player,
                    journal,
                    online,
                    integrated,
                    primary,
                    old,
                    playerClaims,
                    journalClaims,
                    rawClaims);
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

        boolean isCurrent(
                SkillDefinitionStoreService candidate, MinecraftServer candidateServer) {
            requireActive();
            if (owner != Objects.requireNonNull(candidate, "candidate")
                    || serverIdentity
                            != Objects.requireNonNull(candidateServer, "candidateServer")) {
                return false;
            }
            try {
                return candidate.isP4E1StoreReadyCurrent(candidateServer, this);
            } catch (SkillSubsystemLifecycleException exception) {
                return switch (exception.code()) {
                    case BOOTSTRAP_NOT_INSTALLED, OVERWORLD_UNAVAILABLE,
                            CACHE_IDENTITY_MISMATCH -> false;
                    case WRONG_THREAD, BOOTSTRAP_ALREADY_INSTALLED,
                            JOURNAL_BOOTSTRAP_ALREADY_INSTALLED -> throw exception;
                };
            }
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

    static void cleanupUnpublished(
            P4E1RawClaimBuffer claims,
            ArrayList<SourceEntry> sources,
            P4E1PendingJournalObservation.Ready journal,
            P4E1SourceInventory.Witness inventory,
            StoreReadyWitness store) {
        try {
            for (var index = 0; index < sources.size(); index++) {
                var source = sources.get(index);
                if (source.witness() instanceof SourceWitness.Online online) {
                    try {
                        online.service().discardOnlineRootWitness(online.handle());
                    } catch (RuntimeException ignored) {
                        // Unpublished cleanup must not replace the primary terminal result.
                    }
                }
            }
        } finally {
            sources.clear();
            try {
                try {
                    claims.discard();
                } catch (RuntimeException ignored) {
                    // The owning handle drops the backing reference below after lifecycle misuse.
                }
            } finally {
                try {
                    try {
                        journal.discardWitness();
                    } catch (RuntimeException ignored) {
                        // The owning handle drops the witness reference below after misuse.
                    }
                } finally {
                    try {
                        try {
                            inventory.discard();
                        } catch (RuntimeException ignored) {
                            // The owning handle drops the witness reference below after misuse.
                        }
                    } finally {
                        try {
                            store.discard();
                        } catch (RuntimeException ignored) {
                            // The owning handle drops the witness reference below after misuse.
                        }
                    }
                }
            }
        }
    }

    static boolean onlineSourcesCurrent(
            MinecraftServer server,
            PlayerList playerListIdentity,
            PlayerSkillAttachmentService attachmentService,
            ArrayList<SourceEntry> sources) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerListIdentity, "playerListIdentity");
        Objects.requireNonNull(attachmentService, "attachmentService");
        Objects.requireNonNull(sources, "sources");
        if (server.getPlayerList() != playerListIdentity) {
            return false;
        }

        var expected = new TreeMap<UUID, SourceWitness.Online>(Comparator.naturalOrder());
        for (var source : sources) {
            if (source.witness() instanceof SourceWitness.Online online) {
                var playerId = source.playerId().orElse(null);
                if (source.kind() != SourceKind.ONLINE
                        || playerId == null
                        || !playerId.equals(online.playerId())
                        || online.service() != attachmentService
                        || expected.put(playerId, online) != null) {
                    return false;
                }
            }
        }

        var current = new TreeMap<UUID, ServerPlayer>(Comparator.naturalOrder());
        for (var player : playerListIdentity.getPlayers()) {
            if (player == null || player.getServer() != server) {
                return false;
            }
            var playerId = player.getUUID();
            if (playerId == null
                    || current.put(playerId, player) != null
                    || current.size() > sources.size()) {
                return false;
            }
        }
        if (!current.keySet().equals(expected.keySet())) {
            return false;
        }
        for (var entry : expected.entrySet()) {
            var online = entry.getValue();
            var player = current.get(entry.getKey());
            if (player != online.playerIdentity()
                    || !attachmentService.isOnlineRootWitnessCurrent(
                            online.handle(), player)) {
                return false;
            }
        }
        return true;
    }

    static boolean journalProofsCurrent(
            ArrayList<SourceEntry> sources,
            P4E1PendingJournalObservation.Ready journal) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(journal, "journal");
        var journalSources = 0;
        for (var source : sources) {
            if (source.witness() instanceof SourceWitness.Journal proof) {
                journalSources++;
                if (source.kind() != SourceKind.PENDING_JOURNAL
                        || proof.proof() != journal.proofIdentity()) {
                    return false;
                }
            }
        }
        return journalSources == 1;
    }

    static boolean integratedAndArbitrationCurrent(
            MinecraftServer server,
            P4E1PlayerDataDirectorySnapshot directory,
            P4E1IntegratedSnapshotTraversal.Selection integrated,
            ArrayList<SourceEntry> sources,
            ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(integrated, "integrated");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(selectedFiles, "selectedFiles");

        final Optional<UUID> exactIntegratedOwner;
        if (integrated instanceof P4E1IntegratedSnapshotTraversal.Selection.Integrated selected) {
            if (!selected.isCurrent(server)) {
                return false;
            }
            exactIntegratedOwner = Optional.of(selected.ownerId());
        } else if (integrated instanceof P4E1IntegratedSnapshotTraversal.Selection.Disk disk) {
            if (!disk.isCurrent(server)) {
                return false;
            }
            exactIntegratedOwner = Optional.empty();
        } else {
            return false;
        }

        var selectedByOwner = new TreeMap<
                UUID, P4E1PlayerDataSourceSelector.SelectedFileWitness>(
                        Comparator.naturalOrder());
        for (var selected : selectedFiles) {
            if (selectedByOwner.put(selected.playerId(), selected) != null) {
                return false;
            }
        }

        var playerSources = new TreeMap<UUID, SourceEntry>(Comparator.naturalOrder());
        for (var source : sources) {
            if (source.family() != P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT) {
                continue;
            }
            var playerId = source.playerId().orElse(null);
            if (playerId == null || playerSources.put(playerId, source) != null) {
                return false;
            }
            var diskSource = source.kind() == SourceKind.DISK_PRIMARY
                    || source.kind() == SourceKind.DISK_OLD;
            if (diskSource) {
                var selectedFile = selectedByOwner.remove(playerId);
                var expectedKind = source.kind() == SourceKind.DISK_PRIMARY
                        ? P4E1PlayerDataSourceSelector.SourceKind.PRIMARY
                        : P4E1PlayerDataSourceSelector.SourceKind.OLD;
                if (selectedFile == null || selectedFile.source() != expectedKind) {
                    return false;
                }
            } else if (selectedByOwner.containsKey(playerId)) {
                return false;
            }
        }

        for (var route : directory.records()) {
            var source = playerSources.get(route.playerId());
            if (source == null) {
                return false;
            }
            if (source.kind() == SourceKind.ONLINE) {
                continue;
            }
            if (exactIntegratedOwner.filter(route.playerId()::equals).isPresent()) {
                if (source.kind() != SourceKind.INTEGRATED_RUNTIME_SNAPSHOT) {
                    return false;
                }
            } else if (source.kind() != SourceKind.DISK_PRIMARY
                    && source.kind() != SourceKind.DISK_OLD) {
                return false;
            }
        }

        if (exactIntegratedOwner.isPresent()) {
            var source = playerSources.get(exactIntegratedOwner.orElseThrow());
            if (source == null
                    || (source.kind() != SourceKind.ONLINE
                            && source.kind() != SourceKind.INTEGRATED_RUNTIME_SNAPSHOT)) {
                return false;
            }
        }
        return selectedByOwner.isEmpty();
    }

    private record OnlineIdentityCapture(
            ArrayList<OnlineIdentity> identities,
            int initialLiveSize,
            int observationLimit) {
        private OnlineIdentityCapture {
            Objects.requireNonNull(identities, "identities");
            if (initialLiveSize < 0
                    || observationLimit <= 0
                    || identities.size() != Math.min(initialLiveSize, observationLimit)) {
                throw new IllegalArgumentException("invalid online identity capture boundary");
            }
        }

        private boolean relevantCapacityGuaranteed() {
            return initialLiveSize >= observationLimit;
        }
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

    /** Owns the partial witness tuple until the full raw-capture context takes it over. */
    private static final class PreSourceOwnership {
        private final MinecraftServer server;
        private final PlayerSkillAttachmentService attachmentService;
        private final SkillDefinitionStoreSubmissionPort submissionPort;
        private final StoreReadyWitness storeWitness;
        private P4E1PendingJournalObservation.Ready journal;
        private P4E1SourceInventory.Witness inventory;
        private ArrayList<OnlineIdentity> online;
        private boolean ownsState = true;

        private PreSourceOwnership(
                MinecraftServer server,
                PlayerSkillAttachmentService attachmentService,
                SkillDefinitionStoreSubmissionPort submissionPort,
                StoreReadyWitness storeWitness) {
            this.server = server;
            this.attachmentService = attachmentService;
            this.submissionPort = submissionPort;
            this.storeWitness = storeWitness;
        }

        private void retainJournal(P4E1PendingJournalObservation.Ready retained) {
            journal = Objects.requireNonNull(retained, "retained");
        }

        private void retainInventory(P4E1SourceInventory.Witness retained) {
            inventory = Objects.requireNonNull(retained, "retained");
        }

        private void retainOnline(ArrayList<OnlineIdentity> retained) {
            online = Objects.requireNonNull(retained, "retained");
        }

        private void transferOwnership() {
            if (!ownsState || journal == null || inventory == null || online == null) {
                throw new IllegalStateException("P4E1_PRE_SOURCE_OWNERSHIP_TRANSFER_MISMATCH");
            }
            ownsState = false;
        }

        private void discardIfOwned() {
            if (!ownsState) {
                return;
            }
            ownsState = false;
            try {
                if (online != null) {
                    discardOnlineNewSafely(attachmentService, online);
                }
            } finally {
                online = null;
                try {
                    if (journal != null) {
                        try {
                            journal.discardForFailure(submissionPort, server, storeWitness);
                        } catch (RuntimeException ignored) {
                            // The owner drops its final reference below.
                        }
                    }
                } finally {
                    journal = null;
                    try {
                        if (inventory != null) {
                            try {
                                inventory.discard();
                            } catch (RuntimeException ignored) {
                                // The owner drops its final reference below.
                            }
                        }
                    } finally {
                        inventory = null;
                        try {
                            storeWitness.discard();
                        } catch (RuntimeException ignored) {
                            // The owner drops its final reference below.
                        }
                    }
                }
            }
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
        private final ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles;
        private final ArrayList<OnlineIdentity> unprocessedOnline;
        private final int selectedOwnerCount;
        private final int onlineOwnerCount;
        private final int integratedOwnerCount;
        private final int diskOwnerCount;
        private boolean playerRootsComplete;
        private int playerRootClaimCount;
        private boolean journalRootsObserved;
        private int journalRootClaimCount;
        private boolean journalConsumed;
        private boolean ownsState = true;

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
                ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles,
                ArrayList<OnlineIdentity> unprocessedOnline,
                int selectedOwnerCount,
                int onlineOwnerCount,
                int integratedOwnerCount,
                int diskOwnerCount) {
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
            this.selectedFiles = selectedFiles;
            this.unprocessedOnline = unprocessedOnline;
            this.selectedOwnerCount = selectedOwnerCount;
            this.onlineOwnerCount = onlineOwnerCount;
            this.integratedOwnerCount = integratedOwnerCount;
            this.diskOwnerCount = diskOwnerCount;
            if (selectedOwnerCount < 0
                    || onlineOwnerCount < 0
                    || integratedOwnerCount < 0
                    || diskOwnerCount < 0
                    || Math.addExact(
                            Math.addExact(onlineOwnerCount, integratedOwnerCount),
                            diskOwnerCount) != selectedOwnerCount) {
                throw new IllegalArgumentException("P4E1_CAPTURE_OWNER_COUNTS_INVALID");
            }
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

        private ArrayList<P4E1PlayerDataSourceSelector.SelectedFileWitness> selectedFiles() {
            return selectedFiles;
        }

        private ArrayList<OnlineIdentity> unprocessedOnline() {
            return unprocessedOnline;
        }

        private void markPlayerRootsComplete() {
            if (playerRootsComplete) {
                throw new IllegalStateException("P4E1_PLAYER_ROOT_COUNT_ALREADY_COMPLETE");
            }
            playerRootClaimCount = buffer.size();
            playerRootsComplete = true;
        }

        private void recordJournalRootCount(int count) {
            if (!playerRootsComplete || journalRootsObserved || count < 0) {
                throw new IllegalStateException("P4E1_JOURNAL_ROOT_COUNT_OBSERVATION_MISMATCH");
            }
            journalRootClaimCount = count;
            journalRootsObserved = true;
        }

        private CaptureResult fail(CaptureResult failure) {
            Objects.requireNonNull(failure, "failure");
            var observed = observedSummary();
            return switch (failure) {
                case CaptureResult.Incomplete incomplete ->
                        new CaptureResult.Incomplete(incomplete.failure(), observed);
                case CaptureResult.OverLimit overLimit ->
                        new CaptureResult.OverLimit(overLimit.failure(), observed);
                case CaptureResult.Captured ignored ->
                        throw new IllegalArgumentException("captured result is not a failure");
            };
        }

        private ObservedSummary observedSummary() {
            var playerRoots = playerRootsComplete
                    ? OptionalInt.of(playerRootClaimCount)
                    : OptionalInt.empty();
            var journalRoots = journalRootsObserved
                    ? OptionalInt.of(journalRootClaimCount)
                    : OptionalInt.empty();
            var totalRoots = playerRootsComplete && journalRootsObserved
                    ? OptionalInt.of(Math.addExact(
                            playerRootClaimCount, journalRootClaimCount))
                    : OptionalInt.empty();
            return new ObservedSummary(
                    OptionalInt.of(selectedOwnerCount),
                    OptionalInt.of(onlineOwnerCount),
                    OptionalInt.of(integratedOwnerCount),
                    OptionalInt.of(diskOwnerCount),
                    playerRoots,
                    journalRoots,
                    totalRoots,
                    OptionalInt.of(Math.addExact(selectedOwnerCount, 1)));
        }

        private void transferOwnership() {
            if (!ownsState || !unprocessedOnline.isEmpty()) {
                throw new IllegalStateException("P4E1_CAPTURE_OWNERSHIP_TRANSFER_MISMATCH");
            }
            ownsState = false;
        }

        private void discardIfOwned() {
            if (!ownsState) {
                return;
            }
            ownsState = false;
            try {
                discardOnlineHandles();
            } finally {
                sources.clear();
                selectedFiles.clear();
                unprocessedOnline.clear();
                try {
                    try {
                        buffer.discard();
                    } catch (RuntimeException ignored) {
                        // Ownership is dropped below without replacing the primary terminal.
                    }
                } finally {
                    try {
                        try {
                            if (journalConsumed) {
                                journal.discardWitness();
                            } else {
                                discardJournal(submissionPort, server, storeWitness, journal);
                            }
                        } catch (RuntimeException ignored) {
                            // Ownership is dropped below without replacing the primary terminal.
                        }
                    } finally {
                        try {
                            try {
                                inventory.discard();
                            } catch (RuntimeException ignored) {
                                // Ownership is dropped below without replacing the primary terminal.
                            }
                        } finally {
                            try {
                                storeWitness.discard();
                            } catch (RuntimeException ignored) {
                                // Ownership is dropped below without replacing the primary terminal.
                            }
                        }
                    }
                }
            }
        }

        private void discardOnlineHandles() {
            try {
                for (var sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
                    var source = sources.get(sourceIndex);
                    if (source.witness() instanceof SourceWitness.Online online) {
                        try {
                            online.service().discardOnlineRootAuditHandle(online.handle());
                        } catch (RuntimeException ignored) {
                            // Ownership is dropped by the surrounding context.
                        }
                    }
                }
            } finally {
                for (var identityIndex = 0;
                        identityIndex < unprocessedOnline.size();
                        identityIndex++) {
                    var identity = unprocessedOnline.get(identityIndex);
                    var alreadyDiscarded = false;
                    for (var sourceIndex = 0;
                            sourceIndex < sources.size();
                            sourceIndex++) {
                        var source = sources.get(sourceIndex);
                        if (source.witness() instanceof SourceWitness.Online online
                                && online.handle() == identity.handle()) {
                            alreadyDiscarded = true;
                            break;
                        }
                    }
                    if (alreadyDiscarded) {
                        continue;
                    }
                    try {
                        attachmentService.discardOnlineRootAuditHandle(identity.handle());
                    } catch (RuntimeException ignored) {
                        // Ownership is dropped by the surrounding context.
                    }
                }
            }
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
