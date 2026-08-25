package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Synchronous package-owned P4-E1 audit, generation, and memory-only index coordinator. */
final class SkillRetentionRootAuditService {
    static record P4E3IndexTerminalObservation(
            P4E2QualificationFacade.E3IndexTerminal terminal,
            long generation) {
        P4E3IndexTerminalObservation {
            Objects.requireNonNull(terminal, "terminal");
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
        }
    }

    private final CallChainCurrentness minecraftCallChain =
            new CallChainCurrentness() {
                @Override
                public boolean sameThread(Object serverIdentity) {
                    return requireMinecraftServer(serverIdentity).isSameThread();
                }

                @Override
                public int currentTick(Object serverIdentity) {
                    return requireMinecraftServer(serverIdentity).getTickCount();
                }

                private MinecraftServer requireMinecraftServer(Object serverIdentity) {
                    if (!(serverIdentity instanceof MinecraftServer server)) {
                        throw new IllegalStateException("P4E1_COMPLETE_SERVER_TYPE_MISMATCH");
                    }
                    return server;
                }
            };
    private final CompleteCoordinate completeCoordinate = this::requireLifecycle;
    private final SkillDefinitionStoreService storeService;
    private final PlayerSkillAttachmentService attachmentService;
    private final IdentityHashMap<MinecraftServer, IndexSlot> index = new IdentityHashMap<>();
    private final ReferenceQueue<MinecraftServer> stoppedQueue = new ReferenceQueue<>();
    private final ArrayList<StoppedServerRef> stoppedServers = new ArrayList<>();

    SkillRetentionRootAuditService(
            SkillDefinitionStoreService storeService,
            PlayerSkillAttachmentService attachmentService) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.attachmentService = Objects.requireNonNull(attachmentService, "attachmentService");
    }

    SkillRetentionRootAuditResult audit(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        storeService.requireP4E1AuditLifecycle(server);
        expungeStoppedServers();
        requireNotStopped(server);

        var slot = index.get(server);
        if (slot == null) {
            slot = new IndexSlot(this, server);
            index.put(server, slot);
        }
        var lifecycle = slot.lifecycle;
        lifecycle.requireReservable(this, server);
        if (lifecycle.exhaustIfAtMaximum(this, server)) {
            return generationExhausted();
        }
        var reservedGeneration = lifecycle.generation(this, server) + 1L;
        try {
            return lifecycle.executeAccepted(
                    this, server, scope -> auditReserved(server, lifecycle, scope));
        } catch (RuntimeException exception) {
            return lifecycle.mapReservedRuntime(
                    this, server, reservedGeneration, exception);
        }
    }

    private SkillRetentionRootAuditResult auditReserved(
            MinecraftServer server, IndexLifecycle lifecycle, ReservationScope scope) {
        var reservedGeneration = scope.generation();
        SkillRetentionRootAuditResult.AuditSummary runtimeSummary = null;
        var runtimeStage = SkillRetentionRootAuditResult.Stage.RAW_ROOT_CAPTURE;
        P4E1AuditedCapture.Transfer transfer = null;
        try {
            var grouped = new P4E1GroupedStoreAudit(server);
            var captured = P4E1GlobalSourceCapture.capture(
                    server, storeService, attachmentService, grouped);
            if (captured instanceof P4E1GlobalSourceCapture.CaptureResult.Incomplete incomplete) {
                return finishIncomplete(
                        server,
                        scope,
                        fromSourceFailure(
                                incomplete.failure(),
                                incomplete.observedSummary(),
                                reservedGeneration));
            }
            if (captured instanceof P4E1GlobalSourceCapture.CaptureResult.OverLimit overLimit) {
                return finishIncomplete(
                        server,
                        scope,
                        overLimit(
                                overLimit.failure(),
                                overLimit.observedSummary(),
                                reservedGeneration));
            }

            var capturedReady = ((P4E1GlobalSourceCapture.CaptureResult.Captured) captured)
                    .capture();
            runtimeSummary = auditedSummary(
                    reservedGeneration,
                    capturedReady.summary(),
                    OptionalInt.empty(),
                    OptionalInt.empty());
            runtimeStage = SkillRetentionRootAuditResult.Stage.STORE_REFERENCE_OWNER_AUDIT;
            var groupedResult = grouped.audit(capturedReady);
            if (groupedResult instanceof P4E1GroupedStoreAudit.Result.Incomplete incomplete) {
                return finishIncomplete(
                        server,
                        scope,
                        fromGroupedIncomplete(incomplete, reservedGeneration));
            }
            if (groupedResult
                    instanceof P4E1GroupedStoreAudit.Result.ReconciliationRequired reconciliation) {
                return finishIncomplete(
                        server,
                        scope,
                        fromReconciliation(reconciliation, reservedGeneration));
            }

            transfer = ((P4E1GroupedStoreAudit.Result.Audited) groupedResult)
                    .capture()
                    .claim(grouped, this);
            runtimeSummary = completeSummary(transfer, reservedGeneration);
            runtimeStage = SkillRetentionRootAuditResult.Stage.FINAL_FRESHNESS;
            var freshnessInput = new FinalFreshnessInput(
                    server, lifecycle, scope.reservation, transfer);
            var freshness = P4E1FinalFreshness.verify(freshnessInput);
            if (freshness instanceof P4E1FinalFreshness.VerificationResult.Lost lost) {
                return finishIncomplete(
                        server,
                        scope,
                        freshnessLost(lost.code(), runtimeSummary));
            }

            var seal = ((P4E1FinalFreshness.VerificationResult.Verified) freshness).seal();
            runtimeStage = SkillRetentionRootAuditResult.Stage.INDEX_PUBLICATION;
            var published = prepareComplete(
                    server,
                    scope,
                    transfer,
                    reservedGeneration,
                    seal,
                    runtimeSummary);
            transfer = null;
            scope.publish(this, server, published);
            return published.result();
        } catch (RuntimeException exception) {
            var result = internalRuntimeFailure(
                    reservedGeneration, runtimeStage, runtimeSummary, exception);
            return finishIncomplete(server, scope, result);
        } finally {
            if (transfer != null) {
                transfer.discard(this);
            }
        }
    }

    P4E1CompleteRootHandoff consumeComplete(
            MinecraftServer server, SkillRetentionRootAuditResult.Complete complete) {
        return consumeCompleteAtCoordinate(
                this, server, complete, minecraftCallChain, completeCoordinate);
    }

    static P4E1CompleteRootHandoff consumeCompleteAtCoordinate(
            Object owner,
            Object server,
            SkillRetentionRootAuditResult.Complete complete,
            CallChainCurrentness callChain,
            CompleteCoordinate coordinate) {
        var permit = claimCompletePermit(complete);
        var activated = false;
        try {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(callChain, "callChain");
            Objects.requireNonNull(coordinate, "coordinate");
            permit.requireCallChain(owner, server, callChain);
            var lifecycle = Objects.requireNonNull(
                    coordinate.requireLifecycle(owner, server), "lifecycle");
            var handoff = lifecycle.activateClaimedPermit(
                    owner, server, permit, callChain);
            activated = true;
            return handoff;
        } finally {
            if (!activated) {
                permit.clearAfterClaim();
            }
        }
    }

    private static PermitCell claimCompletePermit(
            SkillRetentionRootAuditResult.Complete complete) {
        Objects.requireNonNull(complete, "complete");
        var claimed = complete.claimAuthority();
        if (!(claimed instanceof PermitCell permit)) {
            throw new IllegalStateException("P4E1_COMPLETE_AUTHORITY_TYPE_MISMATCH");
        }
        permit.markClaimed();
        return permit;
    }

    private IndexLifecycle requireLifecycle(Object owner, Object serverIdentity) {
        if (owner != this || !(serverIdentity instanceof MinecraftServer server)) {
            throw new IllegalStateException("P4E1_COMPLETE_INDEX_AUTHORITY_LOST");
        }
        expungeStoppedServers();
        requireNotStopped(server);
        var slot = index.get(server);
        if (slot == null) {
            throw new IllegalStateException("P4E1_COMPLETE_INDEX_AUTHORITY_LOST");
        }
        return slot.lifecycle;
    }

    InvalidationResult invalidateForReconciliation(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        storeService.requireP4E1AuditLifecycle(server);
        expungeStoppedServers();
        requireNotStopped(server);
        var slot = index.get(server);
        if (slot == null) {
            slot = new IndexSlot(this, server);
            index.put(server, slot);
        }
        return slot.lifecycle.invalidate(this, server);
    }

    boolean isReconciliationInvalidationCurrent(
            MinecraftServer server, InvalidationResult.Accepted accepted) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(accepted, "accepted");
        storeService.requireP4E1AuditLifecycle(server);
        expungeStoppedServers();
        requireNotStopped(server);
        var slot = index.get(server);
        return slot != null
                && slot.lifecycle.invalidationCurrent(
                        this, server, accepted.generation());
    }

    void removeServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        SkillDefinitionStoreService.requireServerThread(server);
        expungeStoppedServers();
        requireNotStopped(server);
        stoppedServers.add(new StoppedServerRef(server, stoppedQueue));
        var slot = index.remove(server);
        if (slot != null) {
            slot.lifecycle.remove(this, server);
        }
    }

    P4E3IndexTerminalObservation observeP4E3IndexTerminal(
            MinecraftServer exactServer) {
        Objects.requireNonNull(exactServer, "exactServer");
        storeService.requireP4E1AuditLifecycle(exactServer);
        expungeStoppedServers();
        requireNotStopped(exactServer);
        var slot = index.get(exactServer);
        if (slot == null) {
            throw new IllegalStateException("P4E3_INDEX_TERMINAL_NOT_AVAILABLE");
        }
        return slot.lifecycle.observeP4E3IndexTerminal(this, exactServer);
    }

    private PreparedComplete prepareComplete(
            MinecraftServer server,
            ReservationScope scope,
            P4E1AuditedCapture.Transfer transfer,
            long generation,
            P4E1FinalFreshness.FreshnessSeal seal,
            SkillRetentionRootAuditResult.AuditSummary summary) {
        if (!scope.isCurrent(this, server) || scope.generation() != generation) {
            throw new IllegalStateException("P4E1_INDEX_RESERVATION_LOST");
        }
        Objects.requireNonNull(summary, "summary");
        var publicationSources = new PublicationSource[transfer.sourceCount(this)];
        for (var index = 0; index < publicationSources.length; index++) {
            var source = transfer.sourceAt(this, index);
            publicationSources[index] = new PublicationSource(
                    source.family(),
                    source.kind(),
                    source.playerId(),
                    index,
                    source.claimStart(),
                    source.claimCount());
        }
        var expectedBacking = transfer.backingIdentity(this);
        var prepared = scope.prepareComplete(
                this,
                server,
                expectedBacking,
                publicationSources,
                Thread.currentThread(),
                server.getTickCount(),
                seal,
                summary);
        if (!scope.isCurrent(this, server)) {
            throw new IllegalStateException("P4E1_INDEX_RESERVATION_LOST");
        }
        transfer.releaseBacking(this, expectedBacking);
        return prepared;
    }

    private SkillRetentionRootAuditResult finishIncomplete(
            MinecraftServer server,
            ReservationScope scope,
            SkillRetentionRootAuditResult result) {
        return scope.finishIncomplete(this, server, result);
    }

    private SkillRetentionRootAuditResult.Incomplete generationExhausted() {
        return new SkillRetentionRootAuditResult.Incomplete(
                SkillRetentionRootAuditResult.IncompleteReason.GENERATION_EXHAUSTED,
                SkillRetentionRootAuditResult.Diagnostic.simple(
                        SkillRetentionRootAuditResult.Stage.INDEX_PUBLICATION),
                SkillRetentionRootAuditResult.AuditSummary.generationOnly(Long.MAX_VALUE));
    }

    private SkillRetentionRootAuditResult fromSourceFailure(
            P4E1SourceFailure failure,
            P4E1GlobalSourceCapture.ObservedSummary observed,
            long generation) {
        if (failure.code() == P4E1SourceFailure.Code.ROOT_CAPACITY_EXCEEDED) {
            return overLimit(failure, observed, generation);
        }
        return new SkillRetentionRootAuditResult.Incomplete(
                incompleteReason(failure.code()),
                diagnostic(failure),
                observedSummary(generation, observed));
    }

    private SkillRetentionRootAuditResult.OverLimit overLimit(
            P4E1SourceFailure failure,
            P4E1GlobalSourceCapture.ObservedSummary observed,
            long generation) {
        var counter = failure.counter().orElseThrow();
        return new SkillRetentionRootAuditResult.OverLimit(
                counter(counter),
                stage(failure.stage()),
                failure.observedAtLeast(),
                failure.maximum(),
                observedSummary(generation, observed));
    }

    private static SkillRetentionRootAuditResult.AuditSummary observedSummary(
            long generation, P4E1GlobalSourceCapture.ObservedSummary observed) {
        Objects.requireNonNull(observed, "observed");
        return new SkillRetentionRootAuditResult.AuditSummary(
                OptionalLong.of(generation),
                observed.selectedOwnerCount(),
                observed.onlineOwnerCount(),
                observed.integratedOwnerCount(),
                observed.diskOwnerCount(),
                observed.playerRootClaimCount(),
                observed.journalRootClaimCount(),
                observed.totalRawRootClaimCount(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                observed.sourceCount());
    }

    private SkillRetentionRootAuditResult.Incomplete fromGroupedIncomplete(
            P4E1GroupedStoreAudit.Result.Incomplete incomplete, long generation) {
        var reason = switch (incomplete.reason()) {
            case STORE_UNAVAILABLE -> SkillRetentionRootAuditResult.IncompleteReason.STORE_UNAVAILABLE;
            case JOURNAL_UNAVAILABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.JOURNAL_UNAVAILABLE;
            case JOURNAL_TARGET_INVALID ->
                    SkillRetentionRootAuditResult.IncompleteReason.JOURNAL_TARGET_INVALID;
            case INTERNAL_RUNTIME_FAILURE ->
                    SkillRetentionRootAuditResult.IncompleteReason.INTERNAL_RUNTIME_FAILURE;
        };
        var diagnostic = new SkillRetentionRootAuditResult.Diagnostic(
                stage(incomplete.stage()),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                incomplete.reference().isPresent()
                        ? OptionalInt.of(incomplete.globalOrdinal())
                        : OptionalInt.empty(),
                Optional.empty(),
                incomplete.exceptionClassName());
        return new SkillRetentionRootAuditResult.Incomplete(
                reason,
                diagnostic,
                auditedSummary(
                        generation,
                        incomplete.summary(),
                        incomplete.reference().isPresent()
                                ? OptionalInt.of(incomplete.distinctSkillIdCount())
                                : OptionalInt.empty(),
                        OptionalInt.empty()));
    }

    private SkillRetentionRootAuditResult.ReconciliationRequired fromReconciliation(
            P4E1GroupedStoreAudit.Result.ReconciliationRequired reconciliation,
            long generation) {
        var reason = switch (reconciliation.reason()) {
            case STORE_REFERENCE_MISSING ->
                    SkillRetentionRootAuditResult.ReconciliationReason.STORE_REFERENCE_MISSING;
            case STORE_OWNER_MISMATCH ->
                    SkillRetentionRootAuditResult.ReconciliationReason.STORE_OWNER_MISMATCH;
        };
        var disposition = switch (reconciliation.disposition()) {
            case ONLINE -> SkillRetentionRootAuditResult.Disposition.ONLINE;
            case DEFERRED_INTEGRATED ->
                    SkillRetentionRootAuditResult.Disposition.DEFERRED_INTEGRATED;
            case DEFERRED_OFFLINE ->
                    SkillRetentionRootAuditResult.Disposition.DEFERRED_OFFLINE;
        };
        return new SkillRetentionRootAuditResult.ReconciliationRequired(
                reason,
                disposition,
                reconciliation.staleObservedAtLeast(),
                reconciliation.playerId(),
                auditedSummary(
                        generation,
                        reconciliation.summary(),
                        OptionalInt.of(reconciliation.distinctSkillIdCount()),
                        OptionalInt.empty()));
    }

    private SkillRetentionRootAuditResult.Incomplete freshnessLost(
            P4E1FinalFreshness.FailureCode code,
            SkillRetentionRootAuditResult.AuditSummary summary) {
        var reason = switch (code) {
            case SERVER_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.SERVER_FRESHNESS_LOST;
            case CALL_CHAIN_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.CALL_CHAIN_FRESHNESS_LOST;
            case INDEX_RESERVATION_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.INDEX_RESERVATION_LOST;
            case STORE_SOURCE_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.STORE_SOURCE_FRESHNESS_LOST;
            case JOURNAL_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.JOURNAL_FRESHNESS_LOST;
            case JOURNAL_TARGET_PROOF_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.JOURNAL_TARGET_PROOF_LOST;
            case INVENTORY_PROVIDER_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason
                            .INVENTORY_PROVIDER_FRESHNESS_LOST;
            case DIRECTORY_RACE_DETECTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.DIRECTORY_RACE_DETECTED;
            case SELECTED_FILE_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.SELECTED_FILE_FRESHNESS_LOST;
            case ONLINE_SOURCE_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.ONLINE_SOURCE_FRESHNESS_LOST;
            case INTEGRATED_OWNER_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.INTEGRATED_OWNER_FRESHNESS_LOST;
        };
        return new SkillRetentionRootAuditResult.Incomplete(
                reason,
                SkillRetentionRootAuditResult.Diagnostic.simple(
                        SkillRetentionRootAuditResult.Stage.FINAL_FRESHNESS),
                summary);
    }

    private static SkillRetentionRootAuditResult.Incomplete internalRuntimeFailure(
            long generation,
            SkillRetentionRootAuditResult.Stage stage,
            SkillRetentionRootAuditResult.AuditSummary establishedSummary,
            RuntimeException exception) {
        return new SkillRetentionRootAuditResult.Incomplete(
                SkillRetentionRootAuditResult.IncompleteReason.INTERNAL_RUNTIME_FAILURE,
                new SkillRetentionRootAuditResult.Diagnostic(
                        Objects.requireNonNull(stage, "stage"),
                        Optional.empty(),
                        OptionalLong.empty(),
                        OptionalLong.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        P4E1SourceFailure.boundedExceptionClassName(exception)),
                establishedSummary != null
                        ? establishedSummary
                        : SkillRetentionRootAuditResult.AuditSummary.generationOnly(generation));
    }

    private SkillRetentionRootAuditResult.AuditSummary completeSummary(
            P4E1AuditedCapture.Transfer transfer, long generation) {
        var internal = transfer.summary(this);
        return auditedSummary(
                generation,
                internal,
                OptionalInt.of(transfer.distinctSkillIdCount(this)),
                OptionalInt.of(internal.rawClaims()));
    }

    private static SkillRetentionRootAuditResult.AuditSummary auditedSummary(
            long generation,
            P4E1GlobalSourceCapture.Summary internal,
            OptionalInt distinct,
            OptionalInt validClaims) {
        Objects.requireNonNull(internal, "internal");
        return new SkillRetentionRootAuditResult.AuditSummary(
                OptionalLong.of(generation),
                OptionalInt.of(internal.playerSources()),
                OptionalInt.of(internal.onlineSources()),
                OptionalInt.of(internal.integratedSources()),
                OptionalInt.of(Math.addExact(
                        internal.diskPrimarySources(), internal.diskOldSources())),
                OptionalInt.of(internal.playerRootClaims()),
                OptionalInt.of(internal.journalRootClaims()),
                OptionalInt.of(internal.rawClaims()),
                distinct,
                validClaims,
                OptionalInt.of(Math.addExact(
                        internal.playerSources(), internal.journalSources())));
    }

    private static SkillRetentionRootAuditResult.Diagnostic diagnostic(
            P4E1SourceFailure failure) {
        var hasCapacity = failure.counter().isPresent();
        return new SkillRetentionRootAuditResult.Diagnostic(
                stage(failure.stage()),
                failure.counter().map(SkillRetentionRootAuditService::counter),
                hasCapacity
                        ? OptionalLong.of(failure.observedAtLeast())
                        : OptionalLong.empty(),
                hasCapacity ? OptionalLong.of(failure.maximum()) : OptionalLong.empty(),
                failure.playerId().isPresent()
                        ? OptionalInt.of(failure.ordinal())
                        : OptionalInt.empty(),
                failure.playerId(),
                failure.exceptionClassName());
    }

    private static SkillRetentionRootAuditResult.IncompleteReason incompleteReason(
            P4E1SourceFailure.Code code) {
        return switch (code) {
            case HEAP_FLOOR_NOT_MET ->
                    SkillRetentionRootAuditResult.IncompleteReason.HEAP_FLOOR_NOT_MET;
            case HEAP_FLOOR_UNVERIFIABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.HEAP_FLOOR_UNVERIFIABLE;
            case STORE_UNAVAILABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.STORE_UNAVAILABLE;
            case JOURNAL_NOT_READY ->
                    SkillRetentionRootAuditResult.IncompleteReason.JOURNAL_NOT_READY;
            case JOURNAL_UNAVAILABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.JOURNAL_UNAVAILABLE;
            case JOURNAL_TARGET_INVALID ->
                    SkillRetentionRootAuditResult.IncompleteReason.JOURNAL_TARGET_INVALID;
            case INVENTORY_PROVIDER_MISSING ->
                    SkillRetentionRootAuditResult.IncompleteReason.INVENTORY_PROVIDER_MISSING;
            case COUNTER_CAPACITY_EXCEEDED ->
                    SkillRetentionRootAuditResult.IncompleteReason.COUNTER_CAPACITY_EXCEEDED;
            case ROOT_CAPACITY_EXCEEDED -> throw new IllegalArgumentException(
                    "root capacity maps to OverLimit");
            case DIRECTORY_UNREADABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.DIRECTORY_UNREADABLE;
            case DIRECTORY_TYPE_UNSUPPORTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.DIRECTORY_TYPE_UNSUPPORTED;
            case DIRECTORY_IDENTITY_UNAVAILABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.DIRECTORY_IDENTITY_UNAVAILABLE;
            case DIRECTORY_RACE_DETECTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.DIRECTORY_RACE_DETECTED;
            case PLAYERDATA_NAME_NONCANONICAL ->
                    SkillRetentionRootAuditResult.IncompleteReason.PLAYERDATA_NAME_NONCANONICAL;
            case PRIMARY_FILE_UNREADABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.PRIMARY_FILE_UNREADABLE;
            case PRIMARY_FILE_TYPE_UNSUPPORTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.PRIMARY_FILE_TYPE_UNSUPPORTED;
            case PRIMARY_FILE_IDENTITY_UNAVAILABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason.PRIMARY_FILE_IDENTITY_UNAVAILABLE;
            case PRIMARY_FILE_RACE_DETECTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.PRIMARY_FILE_RACE_DETECTED;
            case PLATFORM_READ_FAILURE_PROVEN ->
                    SkillRetentionRootAuditResult.IncompleteReason.PLATFORM_READ_FAILURE_PROVEN;
            case STRICT_GZIP_REJECTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.STRICT_GZIP_REJECTED;
            case STRICT_NBT_REJECTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.STRICT_NBT_REJECTED;
            case DATA_VERSION_MISSING ->
                    SkillRetentionRootAuditResult.IncompleteReason.DATA_VERSION_MISSING;
            case DATA_VERSION_WRONG_TYPE ->
                    SkillRetentionRootAuditResult.IncompleteReason.DATA_VERSION_WRONG_TYPE;
            case DATA_VERSION_NOT_CURRENT ->
                    SkillRetentionRootAuditResult.IncompleteReason.DATA_VERSION_NOT_CURRENT;
            case ATTACHMENT_ADMISSION_REJECTED ->
                    SkillRetentionRootAuditResult.IncompleteReason.ATTACHMENT_ADMISSION_REJECTED;
            case ATTACHMENT_QUARANTINED ->
                    SkillRetentionRootAuditResult.IncompleteReason.ATTACHMENT_QUARANTINED;
            case INTEGRATED_OWNER_IDENTITY_UNAVAILABLE ->
                    SkillRetentionRootAuditResult.IncompleteReason
                            .INTEGRATED_OWNER_IDENTITY_UNAVAILABLE;
            case INTEGRATED_OWNER_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason
                            .INTEGRATED_OWNER_FRESHNESS_LOST;
            case ONLINE_SOURCE_FRESHNESS_LOST ->
                    SkillRetentionRootAuditResult.IncompleteReason.ONLINE_SOURCE_FRESHNESS_LOST;
            case INTERNAL_RUNTIME_FAILURE ->
                    SkillRetentionRootAuditResult.IncompleteReason.INTERNAL_RUNTIME_FAILURE;
        };
    }

    private static SkillRetentionRootAuditResult.Counter counter(P4E1AuditCounter counter) {
        return switch (counter) {
            case DIRECTORY_ENTRIES -> SkillRetentionRootAuditResult.Counter.DIRECTORY_ENTRIES;
            case RELEVANT_RECORDS -> SkillRetentionRootAuditResult.Counter.RELEVANT_RECORDS;
            case COMPRESSED_BYTES_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.COMPRESSED_BYTES_PER_FILE;
            case DECOMPRESSED_BYTES_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.DECOMPRESSED_BYTES_PER_FILE;
            case CONTAINER_DEPTH_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.CONTAINER_DEPTH_PER_FILE;
            case COMPOUND_CONTAINERS_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.COMPOUND_CONTAINERS_PER_FILE;
            case COMPOUND_FIELD_ENTRIES_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE;
            case LIST_ELEMENTS_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.LIST_ELEMENTS_PER_FILE;
            case BYTE_ARRAY_ELEMENTS_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE;
            case INT_ARRAY_ELEMENTS_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.INT_ARRAY_ELEMENTS_PER_FILE;
            case LONG_ARRAY_ELEMENTS_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.LONG_ARRAY_ELEMENTS_PER_FILE;
            case MODIFIED_UTF8_BYTES_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.MODIFIED_UTF8_BYTES_PER_FILE;
            case SCALAR_TAGS_PER_FILE ->
                    SkillRetentionRootAuditResult.Counter.SCALAR_TAGS_PER_FILE;
            case COMPRESSED_BYTES_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.COMPRESSED_BYTES_TOTAL;
            case DECOMPRESSED_BYTES_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.DECOMPRESSED_BYTES_TOTAL;
            case COMPOUND_CONTAINERS_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.COMPOUND_CONTAINERS_TOTAL;
            case COMPOUND_FIELD_ENTRIES_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.COMPOUND_FIELD_ENTRIES_TOTAL;
            case LIST_ELEMENTS_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.LIST_ELEMENTS_TOTAL;
            case BYTE_ARRAY_ELEMENTS_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.BYTE_ARRAY_ELEMENTS_TOTAL;
            case INT_ARRAY_ELEMENTS_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.INT_ARRAY_ELEMENTS_TOTAL;
            case LONG_ARRAY_ELEMENTS_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.LONG_ARRAY_ELEMENTS_TOTAL;
            case MODIFIED_UTF8_BYTES_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.MODIFIED_UTF8_BYTES_TOTAL;
            case SCALAR_TAGS_TOTAL ->
                    SkillRetentionRootAuditResult.Counter.SCALAR_TAGS_TOTAL;
            case ATTACHMENT_ADMISSIONS ->
                    SkillRetentionRootAuditResult.Counter.ATTACHMENT_ADMISSIONS;
            case RAW_ROOT_CLAIMS -> SkillRetentionRootAuditResult.Counter.RAW_ROOT_CLAIMS;
        };
    }

    private static SkillRetentionRootAuditResult.Stage stage(P4E1AuditStage stage) {
        return switch (stage) {
            case HEAP_FLOOR_OBSERVATION ->
                    SkillRetentionRootAuditResult.Stage.HEAP_FLOOR_OBSERVATION;
            case JOURNAL_READINESS -> SkillRetentionRootAuditResult.Stage.JOURNAL_READINESS;
            case DIRECTORY_ENTRIES -> SkillRetentionRootAuditResult.Stage.DIRECTORY_ENTRIES;
            case SOURCE_SELECTION -> SkillRetentionRootAuditResult.Stage.SOURCE_SELECTION;
            case RELEVANT_RECORDS -> SkillRetentionRootAuditResult.Stage.RELEVANT_RECORDS;
            case PER_FILE_COMPRESSED -> SkillRetentionRootAuditResult.Stage.PER_FILE_COMPRESSED;
            case AGGREGATE_COMPRESSED_CHECKED_ADD ->
                    SkillRetentionRootAuditResult.Stage.AGGREGATE_COMPRESSED_CHECKED_ADD;
            case GZIP_FRAMING -> SkillRetentionRootAuditResult.Stage.GZIP_FRAMING;
            case PER_FILE_DECOMPRESSED ->
                    SkillRetentionRootAuditResult.Stage.PER_FILE_DECOMPRESSED;
            case AGGREGATE_DECOMPRESSED_CHECKED_ADD ->
                    SkillRetentionRootAuditResult.Stage.AGGREGATE_DECOMPRESSED_CHECKED_ADD;
            case COMPOUND_FIELD_CHECKPOINT ->
                    SkillRetentionRootAuditResult.Stage.COMPOUND_FIELD_CHECKPOINT;
            case DEPTH_CONTAINER_SCALAR_KIND ->
                    SkillRetentionRootAuditResult.Stage.DEPTH_CONTAINER_SCALAR_KIND;
            case LIST_LENGTH -> SkillRetentionRootAuditResult.Stage.LIST_LENGTH;
            case TYPED_ARRAY_LENGTH -> SkillRetentionRootAuditResult.Stage.TYPED_ARRAY_LENGTH;
            case MODIFIED_UTF_PREFIX -> SkillRetentionRootAuditResult.Stage.MODIFIED_UTF_PREFIX;
            case DATA_VERSION -> SkillRetentionRootAuditResult.Stage.DATA_VERSION;
            case P4C_ADMISSION -> SkillRetentionRootAuditResult.Stage.P4C_ADMISSION;
            case ATTACHMENT_ADMISSION_COUNTER ->
                    SkillRetentionRootAuditResult.Stage.ATTACHMENT_ADMISSION_COUNTER;
            case RAW_ROOT_CAPTURE -> SkillRetentionRootAuditResult.Stage.RAW_ROOT_CAPTURE;
            case STORE_REFERENCE_OWNER_AUDIT ->
                    SkillRetentionRootAuditResult.Stage.STORE_REFERENCE_OWNER_AUDIT;
        };
    }

    private static void requireReservable(IndexState state) {
        if (state instanceof AuditInProgress) {
            throw new IllegalStateException("P4E1_AUDIT_REENTRANT");
        }
        if (state instanceof CompleteIndexWithActiveLease) {
            throw new IllegalStateException("P4E1_COMPLETE_LEASE_ACTIVE");
        }
        if (state instanceof Removed) {
            throw new IllegalStateException("P4E1_SERVER_REMOVED");
        }
    }

    private static long generation(IndexState state) {
        return switch (state) {
            case NoEntry ignored -> 0L;
            case IncompleteState incomplete -> incomplete.generation;
            case AuditInProgress inProgress -> inProgress.generation;
            case CompleteIndex complete -> complete.generation;
            case CompleteIndexWithActiveLease active -> active.generation;
            case GenerationExhausted exhausted -> exhausted.generation;
            case Removed ignored -> throw new IllegalStateException("P4E1_SERVER_REMOVED");
        };
    }

    private static void discardAuthority(IndexState state) {
        if (state instanceof CompleteIndex complete) {
            var registration = complete.permitRegistration;
            try {
                if (registration != null) {
                    var permit = registration.get();
                    if (permit != null) {
                        permit.revoke();
                    }
                }
            } finally {
                try {
                    if (registration != null) {
                        registration.clear();
                    }
                } finally {
                    complete.backing.discard();
                }
            }
        }
    }

    private static void discardForRemoval(IndexState state) {
        if (state instanceof CompleteIndexWithActiveLease active) {
            try {
                active.lease.revoke();
            } finally {
                active.backing.discard();
            }
            return;
        }
        discardAuthority(state);
    }

    private void expungeStoppedServers() {
        StoppedServerRef cleared;
        while ((cleared = (StoppedServerRef) stoppedQueue.poll()) != null) {
            for (var index = stoppedServers.size() - 1; index >= 0; index--) {
                if (stoppedServers.get(index) == cleared) {
                    stoppedServers.remove(index);
                    break;
                }
            }
        }
    }

    private void requireNotStopped(MinecraftServer server) {
        for (var index = 0; index < stoppedServers.size(); index++) {
            var stopped = stoppedServers.get(index);
            if (stopped.get() == server) {
                throw new IllegalStateException("P4E1_SERVER_ALREADY_STOPPED");
            }
        }
    }

    private final class FinalFreshnessInput implements P4E1FinalFreshness.Input {
        private final MinecraftServer server;
        private final IndexLifecycle lifecycle;
        private final AuditInProgress reservation;
        private final P4E1AuditedCapture.Transfer transfer;

        private FinalFreshnessInput(
                MinecraftServer server,
                IndexLifecycle lifecycle,
                AuditInProgress reservation,
                P4E1AuditedCapture.Transfer transfer) {
            this.server = server;
            this.lifecycle = lifecycle;
            this.reservation = reservation;
            this.transfer = transfer;
        }

        @Override
        public boolean serviceCurrent() {
            return transfer.indexOwnerCurrent(SkillRetentionRootAuditService.this);
        }

        @Override
        public boolean serverCurrent() {
            if (!transfer.serverIdentityCurrent(SkillRetentionRootAuditService.this, server)) {
                return false;
            }
            try {
                storeService.requireP4E1AuditLifecycle(server);
                return true;
            } catch (SkillSubsystemLifecycleException exception) {
                return exception.code() == SkillSubsystemLifecycleException.Code.WRONG_THREAD;
            }
        }

        @Override
        public boolean callChainCurrent() {
            return transfer.callChainCurrent(SkillRetentionRootAuditService.this);
        }

        @Override
        public boolean playerListCurrent() {
            return transfer.playerListCurrent(SkillRetentionRootAuditService.this);
        }

        @Override
        public boolean reservationCurrent() {
            return lifecycle.reservationCurrent(
                    SkillRetentionRootAuditService.this, server, reservation);
        }

        @Override
        public boolean storeCurrent() {
            return transfer.storeCurrent(SkillRetentionRootAuditService.this, storeService);
        }

        @Override
        public P4E1PendingJournalObservation.Currentness journalCurrentness() {
            return transfer.journalCurrentness(
                    SkillRetentionRootAuditService.this, storeService);
        }

        @Override
        public boolean inventoryCurrent() {
            return transfer.inventoryCurrent(
                    SkillRetentionRootAuditService.this, attachmentService);
        }

        @Override
        public P4E1PlayerDataDirectorySnapshot.FinalVerificationResult directoryCurrentness() {
            return transfer.directoryCurrentness(SkillRetentionRootAuditService.this);
        }

        @Override
        public boolean onlineCurrent() {
            return transfer.onlineCurrent(
                    SkillRetentionRootAuditService.this, attachmentService);
        }

        @Override
        public boolean integratedAndArbitrationCurrent() {
            return transfer.integratedAndArbitrationCurrent(
                    SkillRetentionRootAuditService.this);
        }

        @Override
        public boolean reservationStillCurrent() {
            return reservationCurrent();
        }
    }

    /** Exact per-slot lifecycle engine used by production and bounded lifecycle tests. */
    static final class IndexLifecycle {
        private final Object ownerIdentity;
        private final Object serverIdentity;
        private final GenerationExhausted exhausted = new GenerationExhausted();
        private IndexState state = NoEntry.INSTANCE;

        IndexLifecycle(Object ownerIdentity, Object serverIdentity) {
            this(ownerIdentity, serverIdentity, 0L);
        }

        IndexLifecycle(Object ownerIdentity, Object serverIdentity, long initialGeneration) {
            this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity");
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            if (initialGeneration < 0L) {
                throw new IllegalArgumentException("initialGeneration must be non-negative");
            }
            if (initialGeneration > 0L) {
                state = new IncompleteState(initialGeneration);
            }
        }

        boolean execute(
                Object candidateOwner,
                Object candidateServer,
                AcceptedAction action) {
            return execute(candidateOwner, candidateServer, action, null);
        }

        boolean execute(
                Object candidateOwner,
                Object candidateServer,
                AcceptedAction action,
                PriorAuthorityDiscard priorAuthorityDiscard) {
            requireBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(action, "action");
            requireReservable(candidateOwner, candidateServer);
            if (exhaustIfAtMaximum(candidateOwner, candidateServer)) {
                return false;
            }
            var work = new AcceptedWork<Void>() {
                @Override
                public Void run(ReservationScope scope) {
                    action.run(scope);
                    return null;
                }
            };
            if (priorAuthorityDiscard == null) {
                executeAccepted(candidateOwner, candidateServer, work);
            } else {
                executeAccepted(
                        candidateOwner, candidateServer, work, priorAuthorityDiscard);
            }
            return true;
        }

        SkillRetentionRootAuditResult.Incomplete mapReservedRuntime(
                Object candidateOwner,
                Object candidateServer,
                long reservedGeneration,
                RuntimeException exception) {
            requireBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(exception, "exception");
            if (!(state instanceof IncompleteState incomplete)
                    || incomplete.generation != reservedGeneration) {
                throw exception;
            }
            return internalRuntimeFailure(
                    reservedGeneration,
                    SkillRetentionRootAuditResult.Stage.INDEX_PUBLICATION,
                    null,
                    exception);
        }

        InvalidationResult invalidate(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            requireReservable(candidateOwner, candidateServer);
            if (state instanceof GenerationExhausted) {
                return InvalidationResult.GenerationExhausted.INSTANCE;
            }
            var currentGeneration = SkillRetentionRootAuditService.generation(state);
            if (currentGeneration == Long.MAX_VALUE) {
                var oldState = state;
                state = exhausted;
                discardAuthority(oldState);
                return InvalidationResult.GenerationExhausted.INSTANCE;
            }
            var replacement = state instanceof CompleteIndex complete
                    ? complete.nextInvalidationState
                    : new IncompleteState(currentGeneration + 1L);
            var oldState = state;
            state = replacement;
            discardAuthority(oldState);
            return new InvalidationResult.Accepted(
                    SkillRetentionRootAuditService.generation(replacement));
        }

        void remove(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            if (state instanceof Removed) {
                throw new IllegalStateException("P4E1_SERVER_REMOVED");
            }
            var oldState = state;
            state = Removed.INSTANCE;
            discardForRemoval(oldState);
        }

        long generation(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return SkillRetentionRootAuditService.generation(state);
        }

        Object stateIdentity(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state;
        }

        boolean isNoEntry(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state instanceof NoEntry;
        }

        boolean isIncomplete(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state instanceof IncompleteState;
        }

        boolean isComplete(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state instanceof CompleteIndex;
        }

        boolean hasActiveLease(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state instanceof CompleteIndexWithActiveLease;
        }

        boolean isExhausted(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state instanceof GenerationExhausted;
        }

        boolean isRemoved(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state instanceof Removed;
        }

        P4E3IndexTerminalObservation observeP4E3IndexTerminal(
                Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return switch (state) {
                case CompleteIndex complete -> new P4E3IndexTerminalObservation(
                        P4E2QualificationFacade.E3IndexTerminal.COMPLETE_INDEX,
                        complete.generation);
                case IncompleteState incomplete -> new P4E3IndexTerminalObservation(
                        P4E2QualificationFacade.E3IndexTerminal.INCOMPLETE,
                        incomplete.generation);
                case NoEntry ignored -> throw indexTerminalNotAvailable();
                case AuditInProgress ignored -> throw indexTerminalNotAvailable();
                case CompleteIndexWithActiveLease ignored ->
                        throw indexTerminalNotAvailable();
                case GenerationExhausted ignored -> throw indexTerminalNotAvailable();
                case Removed ignored -> throw indexTerminalNotAvailable();
            };
        }

        private static IllegalStateException indexTerminalNotAvailable() {
            return new IllegalStateException("P4E3_INDEX_TERMINAL_NOT_AVAILABLE");
        }

        private IndexState state(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            return state;
        }

        private void requireReservable(Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            SkillRetentionRootAuditService.requireReservable(state);
        }

        private boolean exhaustIfAtMaximum(
                Object candidateOwner, Object candidateServer) {
            requireBinding(candidateOwner, candidateServer);
            requireReservable(candidateOwner, candidateServer);
            if (state instanceof GenerationExhausted) {
                return true;
            }
            if (SkillRetentionRootAuditService.generation(state) != Long.MAX_VALUE) {
                return false;
            }
            var oldState = state;
            state = exhausted;
            discardAuthority(oldState);
            return true;
        }

        private <T> T executeAccepted(
                Object candidateOwner,
                Object candidateServer,
                AcceptedWork<T> work) {
            requireBinding(candidateOwner, candidateServer);
            var oldState = state;
            return executeAccepted(
                    candidateOwner,
                    candidateServer,
                    work,
                    () -> discardAuthority(oldState));
        }

        private <T> T executeAccepted(
                Object candidateOwner,
                Object candidateServer,
                AcceptedWork<T> work,
                PriorAuthorityDiscard priorAuthorityDiscard) {
            requireBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(work, "work");
            Objects.requireNonNull(priorAuthorityDiscard, "priorAuthorityDiscard");
            requireReservable(candidateOwner, candidateServer);
            var currentGeneration = SkillRetentionRootAuditService.generation(state);
            if (currentGeneration == Long.MAX_VALUE) {
                throw new IllegalStateException("P4E1_GENERATION_MUST_BE_EXHAUSTED");
            }
            var reservedGeneration = currentGeneration + 1L;
            var fallback = new IncompleteState(reservedGeneration);
            var reservation = new AuditInProgress(reservedGeneration, fallback);
            var scope = new ReservationScope(
                    this,
                    candidateOwner,
                    candidateServer,
                    reservation,
                    fallback);
            state = reservation;
            try {
                priorAuthorityDiscard.discard();
                return work.run(scope);
            } finally {
                scope.preserveFallback();
            }
        }

        private boolean reservationCurrent(
                Object candidateOwner,
                Object candidateServer,
                AuditInProgress reservation) {
            requireBinding(candidateOwner, candidateServer);
            return state == Objects.requireNonNull(reservation, "reservation")
                    && reservation.generation == SkillRetentionRootAuditService.generation(state);
        }

        private void activateLease(
                Object candidateOwner,
                Object candidateServer,
                CompleteIndex expected,
                CompleteIndexWithActiveLease active) {
            requireBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(active, "active");
            if (state != expected || active.generation != expected.generation) {
                throw new IllegalStateException("P4E1_COMPLETE_INDEX_AUTHORITY_LOST");
            }
            state = active;
        }

        P4E1CompleteRootHandoff activateClaimedPermit(
                Object candidateOwner,
                Object candidateServer,
                PermitCell permit,
                CallChainCurrentness callChain) {
            requireBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(permit, "permit");
            Objects.requireNonNull(callChain, "callChain");
            var currentState = state;
            permit.requireState(
                    currentState, SkillRetentionRootAuditService.generation(currentState));
            if (!(currentState instanceof CompleteIndex completeState)) {
                throw new IllegalStateException("P4E1_COMPLETE_INDEX_AUTHORITY_LOST");
            }
            var completeTerminalState = new CompleteIndex(
                    completeState.generation,
                    completeState.backing,
                    null,
                    completeState.nextInvalidationState);
            var incompleteTerminalState = new IncompleteState(completeState.generation);
            var lease = new LeaseCell(
                    candidateOwner,
                    candidateServer,
                    Thread.currentThread(),
                    callChain.currentTick(candidateServer),
                    callChain,
                    this,
                    completeState.generation,
                    completeState.backing,
                    completeTerminalState,
                    incompleteTerminalState);
            var active = new CompleteIndexWithActiveLease(
                    completeState.generation, completeState.backing, lease);
            var handoff = new P4E1CompleteRootHandoff(lease);
            lease.bind(active, handoff);
            permit.clearAfterClaim();
            activateLease(candidateOwner, candidateServer, completeState, active);
            return handoff;
        }

        private boolean stateCurrent(
                Object candidateOwner,
                Object candidateServer,
                IndexState expected,
                long expectedGeneration) {
            requireBinding(candidateOwner, candidateServer);
            return state == Objects.requireNonNull(expected, "expected")
                    && SkillRetentionRootAuditService.generation(state) == expectedGeneration;
        }

        private boolean invalidationCurrent(
                Object candidateOwner,
                Object candidateServer,
                long expectedGeneration) {
            requireBinding(candidateOwner, candidateServer);
            return state instanceof IncompleteState incomplete
                    && incomplete.generation == expectedGeneration;
        }

        private void releaseLease(
                Object candidateOwner,
                Object candidateServer,
                CompleteIndexWithActiveLease expected,
                IndexState replacement) {
            requireBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(replacement, "replacement");
            if (state != expected
                    || expected.generation
                            != SkillRetentionRootAuditService.generation(replacement)) {
                throw new IllegalStateException("P4E1_COMPLETE_LEASE_NOT_CURRENT");
            }
            state = replacement;
        }

        private void requireBinding(Object candidateOwner, Object candidateServer) {
            if (ownerIdentity != Objects.requireNonNull(candidateOwner, "candidateOwner")) {
                throw new IllegalStateException("P4E1_INDEX_WRONG_SERVICE");
            }
            if (serverIdentity != Objects.requireNonNull(candidateServer, "candidateServer")) {
                throw new IllegalStateException("P4E1_INDEX_WRONG_SERVER");
            }
        }
    }

    @FunctionalInterface
    interface AcceptedAction {
        void run(ReservationScope scope);
    }

    @FunctionalInterface
    interface PriorAuthorityDiscard {
        void discard();
    }

    interface CallChainCurrentness {
        boolean sameThread(Object serverIdentity);

        int currentTick(Object serverIdentity);
    }

    interface CompleteCoordinate {
        IndexLifecycle requireLifecycle(Object ownerIdentity, Object serverIdentity);
    }

    @FunctionalInterface
    private interface AcceptedWork<T> {
        T run(ReservationScope scope);
    }

    static final class ReservationScope {
        private final IndexLifecycle lifecycle;
        private final Object ownerIdentity;
        private final Object serverIdentity;
        private final AuditInProgress reservation;
        private final IncompleteState fallback;

        private ReservationScope(
                IndexLifecycle lifecycle,
                Object ownerIdentity,
                Object serverIdentity,
                AuditInProgress reservation,
                IncompleteState fallback) {
            this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity");
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.reservation = Objects.requireNonNull(reservation, "reservation");
            this.fallback = Objects.requireNonNull(fallback, "fallback");
        }

        long generation() {
            return reservation.generation;
        }

        boolean isCurrent(Object candidateOwner, Object candidateServer) {
            requireScopeBinding(candidateOwner, candidateServer);
            return lifecycle.reservationCurrent(
                    candidateOwner, candidateServer, reservation);
        }

        void finishIncomplete(Object candidateOwner, Object candidateServer) {
            requireScopeBinding(candidateOwner, candidateServer);
            preserveFallback();
        }

        <T extends SkillRetentionRootAuditResult> T finishIncomplete(
                Object candidateOwner, Object candidateServer, T result) {
            requireScopeBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(result, "result");
            if (result instanceof SkillRetentionRootAuditResult.Complete) {
                throw new IllegalArgumentException("Complete requires atomic index publication");
            }
            preserveFallback();
            return result;
        }

        PreparedComplete prepareComplete(
                Object candidateOwner,
                Object candidateServer,
                P4E1RawClaimBuffer expectedBacking,
                PublicationSource[] publicationSources,
                Thread thread,
                int tick,
                P4E1FinalFreshness.FreshnessSeal seal,
                SkillRetentionRootAuditResult.AuditSummary summary) {
            requireScopeBinding(candidateOwner, candidateServer);
            if (!isCurrent(candidateOwner, candidateServer)) {
                throw new IllegalStateException("P4E1_INDEX_RESERVATION_LOST");
            }
            Objects.requireNonNull(expectedBacking, "expectedBacking");
            Objects.requireNonNull(publicationSources, "publicationSources");
            var indexedSources = new IndexedSource[publicationSources.length];
            for (var index = 0; index < publicationSources.length; index++) {
                var source = Objects.requireNonNull(
                        publicationSources[index], "publicationSource");
                indexedSources[index] = new IndexedSource(
                        source.family(),
                        source.kind(),
                        source.playerId(),
                        source.sourceOrdinal(),
                        source.claimStart(),
                        source.claimCount());
            }
            var backing = new IndexedBacking(expectedBacking, indexedSources);
            var permit = new PermitCell(
                    candidateOwner,
                    candidateServer,
                    thread,
                    tick,
                    reservation.generation,
                    seal);
            var permitRegistration = new WeakReference<>(permit);
            IndexState nextInvalidationState = reservation.generation == Long.MAX_VALUE
                    ? lifecycle.exhausted
                    : new IncompleteState(reservation.generation + 1L);
            var state = new CompleteIndex(
                    reservation.generation,
                    backing,
                    permitRegistration,
                    nextInvalidationState);
            permit.bind(state);
            var result = SkillRetentionRootAuditResult.complete(summary, permit);
            return new PreparedComplete(state, result);
        }

        void publish(
                Object candidateOwner, Object candidateServer, PreparedComplete prepared) {
            requireScopeBinding(candidateOwner, candidateServer);
            Objects.requireNonNull(prepared, "prepared");
            var complete = prepared.state();
            if (!isCurrent(candidateOwner, candidateServer)
                    || complete.generation != reservation.generation) {
                throw new IllegalStateException("P4E1_INDEX_RESERVATION_LOST");
            }
            lifecycle.state = complete;
        }

        private void preserveFallback() {
            requireScopeBinding(ownerIdentity, serverIdentity);
            if (lifecycle.state == reservation) {
                lifecycle.state = fallback;
            }
        }

        private void requireScopeBinding(Object candidateOwner, Object candidateServer) {
            lifecycle.requireBinding(candidateOwner, candidateServer);
            if (ownerIdentity != candidateOwner || serverIdentity != candidateServer) {
                throw new IllegalStateException("P4E1_INDEX_RESERVATION_BINDING_LOST");
            }
        }
    }

    private static final class IndexSlot {
        private final IndexLifecycle lifecycle;

        private IndexSlot(Object ownerIdentity, Object serverIdentity) {
            lifecycle = new IndexLifecycle(ownerIdentity, serverIdentity);
        }
    }

    private sealed interface IndexState permits NoEntry,
            IncompleteState,
            AuditInProgress,
            CompleteIndex,
            CompleteIndexWithActiveLease,
            GenerationExhausted,
            Removed {
    }

    private enum NoEntry implements IndexState {
        INSTANCE
    }

    private static final class IncompleteState implements IndexState {
        private final long generation;

        private IncompleteState(long generation) {
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
            this.generation = generation;
        }
    }

    private static final class AuditInProgress implements IndexState {
        private final long generation;
        private final IncompleteState fallback;

        private AuditInProgress(long generation, IncompleteState fallback) {
            this.generation = generation;
            this.fallback = Objects.requireNonNull(fallback, "fallback");
            if (fallback.generation != generation) {
                throw new IllegalArgumentException("fallback generation mismatch");
            }
        }
    }

    private static final class CompleteIndex implements IndexState {
        private final long generation;
        private final IndexedBacking backing;
        private final WeakReference<PermitCell> permitRegistration;
        private final IndexState nextInvalidationState;

        private CompleteIndex(
                long generation,
                IndexedBacking backing,
                WeakReference<PermitCell> permitRegistration,
                IndexState nextInvalidationState) {
            this.generation = generation;
            this.backing = Objects.requireNonNull(backing, "backing");
            this.permitRegistration = permitRegistration;
            this.nextInvalidationState = Objects.requireNonNull(
                    nextInvalidationState, "nextInvalidationState");
            if (SkillRetentionRootAuditService.generation(nextInvalidationState)
                    != (generation == Long.MAX_VALUE ? Long.MAX_VALUE : generation + 1L)) {
                throw new IllegalArgumentException("next invalidation generation mismatch");
            }
        }
    }

    private static final class CompleteIndexWithActiveLease implements IndexState {
        private final long generation;
        private final IndexedBacking backing;
        private final LeaseCell lease;

        private CompleteIndexWithActiveLease(
                long generation, IndexedBacking backing, LeaseCell lease) {
            this.generation = generation;
            this.backing = Objects.requireNonNull(backing, "backing");
            this.lease = Objects.requireNonNull(lease, "lease");
        }
    }

    private static final class GenerationExhausted implements IndexState {
        private final long generation = Long.MAX_VALUE;
    }

    private enum Removed implements IndexState {
        INSTANCE
    }

    private static final class IndexedBacking {
        private P4E1RawClaimBuffer claims;
        private IndexedSource[] sources;

        private IndexedBacking(P4E1RawClaimBuffer claims, IndexedSource[] sources) {
            this.claims = Objects.requireNonNull(claims, "claims");
            this.sources = Objects.requireNonNull(sources, "sources").clone();
        }

        private int size() {
            requireCurrent();
            return claims.size();
        }

        private SkillReference referenceAt(int index) {
            requireCurrent();
            return claims.referenceAt(index);
        }

        private void discard() {
            if (claims == null) {
                return;
            }
            try {
                claims.discard();
            } finally {
                claims = null;
                java.util.Arrays.fill(sources, null);
                sources = null;
            }
        }

        private void requireCurrent() {
            if (claims == null || sources == null) {
                throw new IllegalStateException("P4E1_INDEX_BACKING_DISCARDED");
            }
        }
    }

    record PublicationSource(
            P4E1RootSourceFamily family,
            P4E1GlobalSourceCapture.SourceKind kind,
            Optional<UUID> playerId,
            int sourceOrdinal,
            int claimStart,
            int claimCount) {
        PublicationSource {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(kind, "kind");
            playerId = Objects.requireNonNull(playerId, "playerId");
            if (sourceOrdinal < 0 || claimStart < 0 || claimCount < 0) {
                throw new IllegalArgumentException("invalid publication source range");
            }
            if ((family == P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT)
                    != playerId.isPresent()) {
                throw new IllegalArgumentException("publication source identity mismatch");
            }
        }
    }

    private record IndexedSource(
            P4E1RootSourceFamily family,
            P4E1GlobalSourceCapture.SourceKind kind,
            Optional<UUID> playerId,
            int sourceOrdinal,
            int claimStart,
            int claimCount) {
        private IndexedSource {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(kind, "kind");
            playerId = Objects.requireNonNull(playerId, "playerId");
            if (sourceOrdinal < 0 || claimStart < 0 || claimCount < 0) {
                throw new IllegalArgumentException("invalid indexed source range");
            }
            if ((family == P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT)
                    != playerId.isPresent()) {
                throw new IllegalArgumentException("indexed source identity mismatch");
            }
        }
    }

    /** Consume-first exact-coordinate authority shared by the production permit and JUnit. */
    static final class PermitBinding {
        private Object serviceIdentity;
        private Object serverIdentity;
        private Thread threadIdentity;
        private int tick;
        private final long generation;
        private Object stateIdentity;
        private boolean claimed;
        private boolean revoked;

        PermitBinding(
                Object serviceIdentity,
                Object serverIdentity,
                Thread threadIdentity,
                int tick,
                long generation) {
            this.serviceIdentity = Objects.requireNonNull(serviceIdentity, "serviceIdentity");
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.threadIdentity = Objects.requireNonNull(threadIdentity, "threadIdentity");
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
            this.tick = tick;
            this.generation = generation;
        }

        void bindState(Object stateIdentity, long stateGeneration) {
            if (this.stateIdentity != null || stateGeneration != generation || revoked) {
                throw new IllegalStateException("P4E1_COMPLETE_PERMIT_BINDING_MISMATCH");
            }
            this.stateIdentity = Objects.requireNonNull(stateIdentity, "stateIdentity");
        }

        void claim() {
            if (claimed) {
                throw new IllegalStateException("P4E1_COMPLETE_PERMIT_ALREADY_CLAIMED");
            }
            claimed = true;
        }

        void requireService(Object candidate) {
            requireClaimed();
            if (serviceIdentity != Objects.requireNonNull(candidate, "candidate")) {
                throw new IllegalStateException("P4E1_COMPLETE_WRONG_SERVICE");
            }
        }

        void requireServer(Object candidate) {
            requireClaimed();
            if (serverIdentity != Objects.requireNonNull(candidate, "candidate")) {
                throw new IllegalStateException("P4E1_COMPLETE_WRONG_SERVER");
            }
        }

        void requireThread(Thread candidate) {
            requireClaimed();
            if (threadIdentity != Objects.requireNonNull(candidate, "candidate")) {
                throw new IllegalStateException("P4E1_COMPLETE_WRONG_THREAD");
            }
        }

        void requireTick(int candidate) {
            requireClaimed();
            if (tick != candidate) {
                throw new IllegalStateException("P4E1_COMPLETE_TICK_LOST");
            }
        }

        void requireState(Object candidate, long candidateGeneration) {
            requireClaimed();
            if (revoked
                    || stateIdentity == null
                    || stateIdentity != Objects.requireNonNull(candidate, "candidate")
                    || candidateGeneration != generation) {
                throw new IllegalStateException("P4E1_COMPLETE_INDEX_AUTHORITY_LOST");
            }
        }

        void revoke() {
            revoked = true;
            clearReferences();
        }

        void clearAfterClaim() {
            requireClaimed();
            revoked = true;
            clearReferences();
        }

        boolean referencesCleared() {
            return serviceIdentity == null
                    && serverIdentity == null
                    && threadIdentity == null
                    && stateIdentity == null
                    && tick == -1;
        }

        private void requireClaimed() {
            if (!claimed) {
                throw new IllegalStateException("P4E1_COMPLETE_PERMIT_NOT_CLAIMED");
            }
        }

        private void clearReferences() {
            serviceIdentity = null;
            serverIdentity = null;
            threadIdentity = null;
            stateIdentity = null;
            tick = -1;
        }
    }

    private static final class PermitCell
            implements SkillRetentionRootAuditResult.CompleteAuthority {
        private final PermitBinding binding;
        private P4E1FinalFreshness.FreshnessSeal seal;
        private CompleteIndex state;

        private PermitCell(
                Object owner,
                Object server,
                Thread thread,
                int tick,
                long generation,
                P4E1FinalFreshness.FreshnessSeal seal) {
            binding = new PermitBinding(owner, server, thread, tick, generation);
            this.seal = Objects.requireNonNull(seal, "seal");
        }

        private void bind(CompleteIndex state) {
            if (this.state != null) {
                throw new IllegalStateException("P4E1_COMPLETE_PERMIT_BINDING_MISMATCH");
            }
            binding.bindState(state, state.generation);
            this.state = state;
        }

        private void markClaimed() {
            binding.claim();
        }

        private void requireService(Object candidate) {
            binding.requireService(candidate);
        }

        private void requireServer(Object candidate) {
            binding.requireServer(candidate);
        }

        private void requireThread(Thread candidate) {
            binding.requireThread(candidate);
        }

        private void requireTick(int candidate) {
            binding.requireTick(candidate);
        }

        private void requireCallChain(
                Object candidateOwner,
                Object candidateServer,
                CallChainCurrentness callChain) {
            Objects.requireNonNull(callChain, "callChain");
            requireService(candidateOwner);
            requireServer(candidateServer);
            requireThread(Thread.currentThread());
            if (!callChain.sameThread(candidateServer)) {
                throw new IllegalStateException("P4E1_COMPLETE_WRONG_THREAD");
            }
            requireTick(callChain.currentTick(candidateServer));
        }

        private void requireState(IndexState candidate, long candidateGeneration) {
            binding.requireState(candidate, candidateGeneration);
            if (seal == null || state != candidate) {
                throw new IllegalStateException("P4E1_COMPLETE_INDEX_AUTHORITY_LOST");
            }
        }

        private void clearAfterClaim() {
            binding.clearAfterClaim();
            seal = null;
            state = null;
        }

        private void revoke() {
            binding.revoke();
            seal = null;
            state = null;
        }
    }

    private static final class LeaseCell
            extends P4E1CompleteRootHandoff.SourceUnchangedLeaseAuthority {
        private Object owner;
        private Object server;
        private Thread thread;
        private int tick;
        private CallChainCurrentness callChain;
        private IndexLifecycle lifecycle;
        private final long generation;
        private IndexedBacking backing;
        private CompleteIndex completeTerminalState;
        private IncompleteState incompleteTerminalState;
        private IndexState selectedTerminalState;
        private CompleteIndexWithActiveLease activeState;
        private P4E1CompleteRootHandoff handoffIdentity;
        private boolean revoked;

        private LeaseCell(
                Object owner,
                Object server,
                Thread thread,
                int tick,
                CallChainCurrentness callChain,
                IndexLifecycle lifecycle,
                long generation,
                IndexedBacking backing,
                CompleteIndex completeTerminalState,
                IncompleteState incompleteTerminalState) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.server = Objects.requireNonNull(server, "server");
            this.thread = Objects.requireNonNull(thread, "thread");
            this.tick = tick;
            this.callChain = Objects.requireNonNull(callChain, "callChain");
            this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            this.generation = generation;
            this.backing = Objects.requireNonNull(backing, "backing");
            this.completeTerminalState = Objects.requireNonNull(
                    completeTerminalState, "completeTerminalState");
            this.incompleteTerminalState = Objects.requireNonNull(
                    incompleteTerminalState, "incompleteTerminalState");
            if (completeTerminalState.generation != generation
                    || incompleteTerminalState.generation != generation) {
                throw new IllegalArgumentException("P4E1_COMPLETE_LEASE_TERMINAL_MISMATCH");
            }
            selectedTerminalState = incompleteTerminalState;
        }

        private void bind(
                CompleteIndexWithActiveLease activeState,
                P4E1CompleteRootHandoff handoff) {
            if (this.activeState != null || activeState.generation != generation) {
                throw new IllegalStateException("P4E1_COMPLETE_LEASE_BINDING_MISMATCH");
            }
            this.activeState = Objects.requireNonNull(activeState, "activeState");
            this.handoffIdentity = Objects.requireNonNull(handoff, "handoff");
        }

        @Override
        public boolean isCurrent(P4E1CompleteRootHandoff handoff) {
            return !revoked
                    && owner != null
                    && server != null
                    && handoffIdentity == handoff
                    && Thread.currentThread() == thread
                    && callChain.sameThread(server)
                    && callChain.currentTick(server) == tick
                    && lifecycle.stateCurrent(owner, server, activeState, generation);
        }

        @Override
        public int size() {
            requireBound();
            return backing.size();
        }

        @Override
        public SkillReference referenceAt(int index) {
            requireBound();
            return backing.referenceAt(index);
        }

        @Override
        void markStoreSourceUnchanged(P4E1CompleteRootHandoff handoff) {
            if (!isCurrent(handoff)) {
                throw new IllegalStateException("P4E1_COMPLETE_LEASE_NOT_CURRENT");
            }
            if (selectedTerminalState == completeTerminalState) {
                throw new IllegalStateException(
                        "P4E1_COMPLETE_HANDOFF_SOURCE_UNCHANGED_ALREADY_MARKED");
            }
            if (selectedTerminalState != incompleteTerminalState) {
                throw new IllegalStateException("P4E1_COMPLETE_LEASE_NOT_CURRENT");
            }
            selectedTerminalState = completeTerminalState;
        }

        @Override
        public void release(P4E1CompleteRootHandoff handoff) {
            if (!isCurrent(handoff)) {
                throw new IllegalStateException("P4E1_COMPLETE_LEASE_NOT_CURRENT");
            }
            var terminalState = selectedTerminalState;
            var discardBacking = terminalState == incompleteTerminalState;
            lifecycle.releaseLease(owner, server, activeState, terminalState);
            try {
                if (discardBacking) {
                    backing.discard();
                }
            } finally {
                clear();
            }
        }

        private void revoke() {
            var handoff = handoffIdentity;
            try {
                if (handoff != null) {
                    handoff.forceInvalidate(this);
                }
            } finally {
                revoked = true;
                clear();
            }
        }

        private void requireBound() {
            if (revoked
                    || backing == null
                    || lifecycle == null
                    || !lifecycle.stateCurrent(owner, server, activeState, generation)) {
                throw new IllegalStateException("P4E1_COMPLETE_LEASE_NOT_CURRENT");
            }
        }

        private void clear() {
            owner = null;
            server = null;
            thread = null;
            tick = -1;
            callChain = null;
            lifecycle = null;
            backing = null;
            completeTerminalState = null;
            incompleteTerminalState = null;
            selectedTerminalState = null;
            activeState = null;
            handoffIdentity = null;
        }
    }

    private static final class StoppedServerRef extends WeakReference<MinecraftServer> {
        private StoppedServerRef(
                MinecraftServer server, ReferenceQueue<MinecraftServer> queue) {
            super(Objects.requireNonNull(server, "server"), Objects.requireNonNull(queue, "queue"));
        }
    }

    record PreparedComplete(
            CompleteIndex state, SkillRetentionRootAuditResult.Complete result) {
        PreparedComplete {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(result, "result");
        }
    }

    sealed interface InvalidationResult
            permits InvalidationResult.Accepted,
                    InvalidationResult.GenerationExhausted {
        record Accepted(long generation) implements InvalidationResult {
            public Accepted {
                if (generation <= 0L) {
                    throw new IllegalArgumentException("generation must be positive");
                }
            }
        }

        enum GenerationExhausted implements InvalidationResult {
            INSTANCE
        }
    }
}
