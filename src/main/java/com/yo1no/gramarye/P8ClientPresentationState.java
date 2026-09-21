package com.yo1no.gramarye;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/**
 * Client-owned P8 catalog mirror, generation state, and bounded NETWORK-to-main
 * reservations. This common-safe owner intentionally has no client-class dependency.
 */
final class P8ClientPresentationState implements P8ClientPayloadDispatchPort {
    private final BooleanSupplier clientThreadCheck;
    private final P8ClientPresentationExecutionPort execution;
    private final Map<EventReservation, EventReservationOwner> eventReservations =
            new IdentityHashMap<>(
                    Math.toIntExact(PresentationLimits.MAX_CLIENT_PENDING_EVENTS));
    private final ArrayDeque<PendingEvent> pendingEvents = new ArrayDeque<>(
            Math.toIntExact(PresentationLimits.MAX_CLIENT_PENDING_EVENTS));

    private long connectionCounter;
    private long connectionGeneration;
    private boolean connected;
    private Connection p8TransportConnection;
    private ICommonPacketListener p8PlayListenerWitness;
    private long pendingLifecycleInvalidationGeneration;
    private PlayPhase playPhase = PlayPhase.EMPTY;
    private PlayEpochIdentity playEpochIdentity;
    private PublishedCleanupObligation publishedCleanupObligation;

    private long worldCounter;
    private long worldGeneration;
    private boolean worldReady;

    private long resourceCounter;
    private long resourceGeneration;
    private boolean resourceReady;
    private P8ClientResourceIndex resourceIndex = P8ClientResourceIndex.empty();

    private InstalledCatalog installedCatalog;
    private long lastAcceptedSequence;
    private CatalogMailboxValue catalogMailbox;
    private CatalogBindAttempt catalogBindAttempt;
    private ResourceApplyAttempt resourceApplyAttempt;
    private ConnectionTransitionAttempt connectionTransitionAttempt;
    private WorldTransitionAttempt worldTransitionAttempt;

    private long reservedEventCount;
    private long reservedEventBodyBytes;
    private long combinedQueuedCharge;
    private long inFlightCatalogBodyBytes;
    private long unavailableHandoffCount;

    P8ClientPresentationState(BooleanSupplier clientThreadCheck) {
        this(clientThreadCheck, UnavailableP8ClientPresentationExecution.INSTANCE);
    }

    P8ClientPresentationState(
            BooleanSupplier clientThreadCheck,
            P8ClientPresentationExecutionPort execution) {
        this.clientThreadCheck = Objects.requireNonNull(
                clientThreadCheck, "clientThreadCheck");
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    @Override
    public synchronized Optional<P8ClientDispatchTask> prepareProfileCatalog(
            Connection sourceConnection,
            ICommonPacketListener sourcePlayListener,
            ProfileCatalogPayload payload) {
        Objects.requireNonNull(sourceConnection, "sourceConnection");
        Objects.requireNonNull(sourcePlayListener, "sourcePlayListener");
        Objects.requireNonNull(payload, "payload");
        if (payload.bodySize()
                > PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES) {
            return Optional.empty();
        }
        if (!admitCatalogEpochLocked(sourceConnection, sourcePlayListener)) {
            return Optional.empty();
        }

        var incomingGeneration = payload.catalogGeneration();
        if (playPhase == PlayPhase.OPEN
                && installedCatalog != null
                && incomingGeneration <= installedCatalog.snapshot().catalogGeneration()) {
            return Optional.empty();
        }

        var capturedConnection = playPhase == PlayPhase.OPEN ? connectionGeneration : 0L;
        var packetCharge = Math.addExact(
                (long) payload.bodySize(),
                PresentationLimits.PROFILE_CATALOG_PACKET_OVERHEAD_BYTES);
        if (playPhase == PlayPhase.OPEN
                && catalogBindAttempt != null
                && catalogBindAttempt.connectionGeneration() == capturedConnection
                && incomingGeneration
                        <= catalogBindAttempt.catalog().catalogGeneration()) {
            return Optional.empty();
        }
        if (catalogMailbox != null) {
            if (catalogMailbox.epochIdentity() != playEpochIdentity
                    || catalogMailbox.connectionGeneration() != capturedConnection) {
                releaseCatalogMailboxLocked();
            } else {
                if (incomingGeneration <= catalogMailbox.payload().catalogGeneration()) {
                    return Optional.empty();
                }
                var chargeWithoutOld = checkedSubtractNonnegative(
                        combinedQueuedCharge,
                        catalogMailbox.packetCharge(),
                        "P8 queued charge");
                if (packetCharge
                        > checkedSubtractNonnegative(
                                PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES,
                                chargeWithoutOld,
                                "P8 queued capacity")) {
                    return Optional.empty();
                }
                var nextCombinedQueuedCharge = Math.addExact(
                        chargeWithoutOld, packetCharge);
                catalogMailbox = new CatalogMailboxValue(
                        catalogMailbox.drainIdentity(),
                        playEpochIdentity,
                        capturedConnection,
                        payload,
                        packetCharge);
                combinedQueuedCharge = nextCombinedQueuedCharge;
                return Optional.empty();
            }
        }

        if (packetCharge
                > checkedSubtractNonnegative(
                        PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES,
                        combinedQueuedCharge,
                        "P8 queued capacity")) {
            return Optional.empty();
        }
        var drainIdentity = new CatalogDrainIdentity();
        var replacement = new CatalogMailboxValue(
                drainIdentity,
                playEpochIdentity,
                capturedConnection,
                payload,
                packetCharge);
        var task = playPhase == PlayPhase.OPEN
                ? Optional.<P8ClientDispatchTask>of(new CatalogDrainTask(
                        this, drainIdentity, playEpochIdentity, capturedConnection))
                : Optional.<P8ClientDispatchTask>empty();
        var nextCombinedQueuedCharge = Math.addExact(
                combinedQueuedCharge, packetCharge);
        catalogMailbox = replacement;
        combinedQueuedCharge = nextCombinedQueuedCharge;
        return task;
    }

    @Override
    public synchronized Optional<P8ClientDispatchTask> preparePresentationEvent(
            Connection sourceConnection,
            ICommonPacketListener sourcePlayListener,
            PresentationEventPayload payload) {
        Objects.requireNonNull(sourceConnection, "sourceConnection");
        Objects.requireNonNull(sourcePlayListener, "sourcePlayListener");
        Objects.requireNonNull(payload, "payload");
        refreshSelectedTransportLocked();
        if (playPhase != PlayPhase.OPEN
                || !connected
                || p8TransportConnection != sourceConnection
                || p8PlayListenerWitness != sourcePlayListener
                || !isCurrentClientboundPlay(sourceConnection, sourcePlayListener)) {
            return Optional.empty();
        }

        var bodyBytes = (long) payload.bodySize();
        var packetCharge = Math.addExact(
                bodyBytes, PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES);
        if (reservedEventCount >= PresentationLimits.MAX_CLIENT_PENDING_EVENTS
                || bodyBytes
                        > checkedSubtractNonnegative(
                                PresentationLimits.MAX_CLIENT_PENDING_EVENT_BODY_BYTES,
                                reservedEventBodyBytes,
                                "P8 event body capacity")
                || packetCharge
                        > checkedSubtractNonnegative(
                                PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES,
                                combinedQueuedCharge,
                                "P8 queued capacity")) {
            return Optional.empty();
        }

        var reservation = new EventReservation(
                this,
                playEpochIdentity,
                connectionGeneration,
                worldGeneration,
                resourceGeneration,
                payload.catalogGeneration(),
                bodyBytes,
                packetCharge);
        var task = Optional.<P8ClientDispatchTask>of(
                new EventMainTask(this, payload, reservation));
        var nextEventCount = Math.addExact(reservedEventCount, 1L);
        var nextEventBodyBytes = Math.addExact(reservedEventBodyBytes, bodyBytes);
        var nextCombinedQueuedCharge = Math.addExact(combinedQueuedCharge, packetCharge);
        eventReservations.put(reservation, EventReservationOwner.NETWORK_TASK);
        reservedEventCount = nextEventCount;
        reservedEventBodyBytes = nextEventBodyBytes;
        combinedQueuedCharge = nextCombinedQueuedCharge;
        return task;
    }

    P8ClientConnectionOpenResult onConnectionOpened(
            Connection exactConnection, ICommonPacketListener exactPlayListener) {
        requireClientThread();
        Objects.requireNonNull(exactConnection, "exactConnection");
        Objects.requireNonNull(exactPlayListener, "exactPlayListener");
        ConnectionTransitionAttempt selectedAttempt = null;
        boolean serviceOnly = false;
        synchronized (this) {
            refreshSelectedTransportLocked();
            if (playPhase == PlayPhase.OPEN
                    && connected
                    && p8TransportConnection == exactConnection
                    && p8PlayListenerWitness == exactPlayListener
                    && isCurrentClientboundPlay(exactConnection, exactPlayListener)) {
                return new P8ClientConnectionOpenResult(
                        false,
                        0L,
                        Optional.empty(),
                        transferMaintenanceResultLocked(P8ClientCleanupDisposition.NONE));
            }
            if (!isCurrentClientboundPlay(exactConnection, exactPlayListener)) {
                serviceOnly = true;
            } else {
                if (connectionTransitionAttempt != null) {
                    throw new IllegalStateException(
                            "P8 PLAY publication cannot re-enter an active transport cleanup");
                }
                selectMainEpochLocked(exactConnection, exactPlayListener);
                servicePublishedInvalidationCounterLocked();
                if (playPhase == PlayPhase.RETIRING) {
                    serviceOnly = true;
                } else if (connectionCounter == Long.MAX_VALUE) {
                    retireSelectedEpochLocked(true);
                    serviceOnly = true;
                } else {
                    connectionCounter++;
                    var attemptGeneration = connectionCounter;
                    connected = false;
                    playPhase = PlayPhase.OPENING;
                    selectedAttempt = new ConnectionTransitionAttempt(
                            playEpochIdentity,
                            attemptGeneration,
                            publishedCleanupObligation);
                    connectionTransitionAttempt = selectedAttempt;
                }
            }
        }
        if (serviceOnly) {
            return new P8ClientConnectionOpenResult(
                    false,
                    0L,
                    Optional.empty(),
                    performTransportMaintenance());
        }
        final ConnectionTransitionAttempt attempt = selectedAttempt;

        RuntimeException cleanupRuntimeFailure = null;
        Error cleanupError = null;
        if (attempt.cleanupObligation() != null
                && !attempt.cleanupObligation().backendCleared()) {
            try {
                execution.clearAll();
            } catch (RuntimeException failure) {
                cleanupRuntimeFailure = failure;
            } catch (Error failure) {
                cleanupError = failure;
            }
        }
        final P8ClientConnectionOpenResult result;
        synchronized (this) {
            if (cleanupRuntimeFailure != null || cleanupError != null) {
                if (connectionTransitionAttempt == attempt) {
                    connectionTransitionAttempt = null;
                    if (playEpochIdentity == attempt.epochIdentity()
                            && playPhase == PlayPhase.OPENING) {
                        playPhase = PlayPhase.PREOPEN;
                    }
                }
                rethrowCleanupFailure(cleanupRuntimeFailure, cleanupError);
            }
            var cleanupDisposition = completeCleanupAttemptLocked(attempt);
            refreshSelectedTransportLocked();
            if (connectionTransitionAttempt != attempt
                    || playPhase != PlayPhase.OPENING
                    || playEpochIdentity != attempt.epochIdentity()
                    || !isSelectedCurrentPlayLocked()) {
                if (connectionTransitionAttempt == attempt) {
                    connectionTransitionAttempt = null;
                }
                result = new P8ClientConnectionOpenResult(
                        false,
                        0L,
                        Optional.empty(),
                        transferMaintenanceResultLocked(cleanupDisposition));
            } else {
                var drain = prepareOpenPublicationLocked(attempt.connectionGeneration());
                var maintenance = pendingMaintenanceResultLocked(cleanupDisposition);
                result = new P8ClientConnectionOpenResult(
                        true, attempt.connectionGeneration(), drain, maintenance);
                connectionGeneration = attempt.connectionGeneration();
                connected = true;
                playPhase = PlayPhase.OPEN;
                connectionTransitionAttempt = null;
                publishedCleanupObligation = null;
                pendingLifecycleInvalidationGeneration = 0L;
                worldCounter = 0L;
            }
        }
        return result;
    }

    P8ClientTransportMaintenanceResult onLoggedOut(
            @Nullable Connection expectedConnection,
            @Nullable ICommonPacketListener expectedPlayListener) {
        requireClientThread();
        synchronized (this) {
            refreshSelectedTransportLocked();
            if (hasExactLogoutAuthorityLocked(expectedConnection, expectedPlayListener)
                    || hasClosedTransportLogoutAuthorityLocked(
                            expectedConnection, expectedPlayListener)) {
                var retainFence = expectedConnection != null
                        && expectedPlayListener != null
                        && isCurrentClientboundPlay(
                                expectedConnection, expectedPlayListener);
                retireSelectedEpochLocked(retainFence);
            }
        }
        return performTransportMaintenance();
    }

    P8ClientTransportMaintenanceResult maintainTransportLiveness() {
        requireClientThread();
        synchronized (this) {
            refreshSelectedTransportLocked();
        }
        return performTransportMaintenance();
    }

    synchronized boolean isCurrentPublishedPlayGeneration(long expectedGeneration) {
        requireClientThread();
        return expectedGeneration > 0L
                && playPhase == PlayPhase.OPEN
                && connected
                && connectionGeneration == expectedGeneration
                && isSelectedCurrentPlayLocked();
    }

    private P8ClientTransportMaintenanceResult performTransportMaintenance() {
        final ConnectionTransitionAttempt attempt;
        synchronized (this) {
            refreshSelectedTransportLocked();
            servicePublishedInvalidationCounterLocked();
            if (publishedCleanupObligation == null) {
                return transferMaintenanceResultLocked(P8ClientCleanupDisposition.NONE);
            }
            if (connectionTransitionAttempt != null) {
                throw new IllegalStateException(
                        "P8 transport maintenance cannot re-enter an active transition");
            }
            attempt = new ConnectionTransitionAttempt(
                    playEpochIdentity, 0L, publishedCleanupObligation);
            connectionTransitionAttempt = attempt;
        }

        RuntimeException cleanupRuntimeFailure = null;
        Error cleanupError = null;
        if (!attempt.cleanupObligation().backendCleared()) {
            try {
                execution.clearAll();
            } catch (RuntimeException failure) {
                cleanupRuntimeFailure = failure;
            } catch (Error failure) {
                cleanupError = failure;
            }
        }
        synchronized (this) {
            if (cleanupRuntimeFailure != null || cleanupError != null) {
                if (connectionTransitionAttempt == attempt) {
                    connectionTransitionAttempt = null;
                }
                rethrowCleanupFailure(cleanupRuntimeFailure, cleanupError);
            }
            var disposition = completeCleanupAttemptLocked(attempt);
            if (connectionTransitionAttempt == attempt) {
                connectionTransitionAttempt = null;
            }
            var result = transferMaintenanceResultLocked(disposition);
            if (publishedCleanupObligation == attempt.cleanupObligation()
                    || publishedCleanupObligation != null
                            && publishedCleanupObligation.publishedGeneration()
                                    == attempt.cleanupObligation().publishedGeneration()
                            && publishedCleanupObligation.backendCleared()) {
                publishedCleanupObligation = null;
            }
            return result;
        }
    }

    void onWorldLoaded() {
        requireClientThread();
        final WorldTransitionAttempt attempt;
        synchronized (this) {
            if (playPhase != PlayPhase.OPEN
                    || !connected
                    || !isSelectedCurrentPlayLocked()) {
                return;
            }
            clearAllEventWorkLocked();
            var canLoad = worldCounter != Long.MAX_VALUE;
            if (!canLoad) {
                worldReady = false;
                worldGeneration = worldCounter;
            } else {
                worldCounter++;
                worldGeneration = worldCounter;
                worldReady = false;
            }
            attempt = new WorldTransitionAttempt(
                    connectionGeneration,
                    worldGeneration,
                    canLoad);
            worldTransitionAttempt = attempt;
        }

        RuntimeException cleanupRuntimeFailure = null;
        Error cleanupError = null;
        try {
            execution.clearActive();
        } catch (RuntimeException failure) {
            cleanupRuntimeFailure = failure;
        } catch (Error failure) {
            cleanupError = failure;
        }
        synchronized (this) {
            completeWorldTransitionLocked(attempt);
        }
        rethrowCleanupFailure(cleanupRuntimeFailure, cleanupError);
    }

    void onWorldUnloaded(boolean hasCurrentWorld) {
        requireClientThread();
        final WorldTransitionAttempt attempt;
        synchronized (this) {
            if (playPhase != PlayPhase.OPEN
                    || !connected
                    || !isSelectedCurrentPlayLocked()) {
                return;
            }
            clearAllEventWorkLocked();
            var canAdvance = worldCounter != Long.MAX_VALUE;
            if (canAdvance) {
                worldCounter++;
                worldGeneration = worldCounter;
                worldReady = false;
            } else {
                worldGeneration = worldCounter;
                worldReady = false;
            }
            attempt = new WorldTransitionAttempt(
                    connectionGeneration,
                    worldGeneration,
                    canAdvance && hasCurrentWorld);
            worldTransitionAttempt = attempt;
        }

        RuntimeException cleanupRuntimeFailure = null;
        Error cleanupError = null;
        try {
            execution.clearActive();
        } catch (RuntimeException failure) {
            cleanupRuntimeFailure = failure;
        } catch (Error failure) {
            cleanupError = failure;
        }
        synchronized (this) {
            completeWorldTransitionLocked(attempt);
        }
        rethrowCleanupFailure(cleanupRuntimeFailure, cleanupError);
    }

    void onResourceIndexApplied(P8ClientResourceIndex replacement) {
        requireClientThread();
        Objects.requireNonNull(replacement, "replacement");
        final ResourceApplyAttempt attempt;
        synchronized (this) {
            var exhausted = resourceCounter == Long.MAX_VALUE;
            var nextResourceGeneration = exhausted
                    ? resourceCounter
                    : resourceCounter + 1L;
            var bindingCatalog = catalogBindAttempt != null
                            && playPhase == PlayPhase.OPEN
                            && connected
                            && isSelectedCurrentPlayLocked()
                            && catalogBindAttempt.connectionGeneration()
                                    == connectionGeneration
                            && (installedCatalog == null
                                    || catalogBindAttempt.catalog().catalogGeneration()
                                            > installedCatalog.snapshot()
                                                    .catalogGeneration())
                    ? catalogBindAttempt.catalog()
                    : null;
            var catalogForEvaluation = bindingCatalog != null
                    ? bindingCatalog
                    : installedCatalog == null ? null : installedCatalog.snapshot();
            var replacementCatalog = catalogForEvaluation == null
                    ? null
                    : new InstalledCatalog(
                            catalogForEvaluation,
                            exhausted ? 0L : nextResourceGeneration);
            attempt = new ResourceApplyAttempt(
                    resourceCounter,
                    resourceGeneration,
                    resourceReady,
                    resourceIndex,
                    connectionGeneration,
                    connected,
                    worldGeneration,
                    worldReady,
                    installedCatalog == null
                            ? 0L
                            : installedCatalog.snapshot().catalogGeneration(),
                    catalogForEvaluation,
                    replacementCatalog,
                    replacement,
                    nextResourceGeneration,
                    exhausted);
            resourceApplyAttempt = attempt;
        }

        P8ClientPreparedCatalog preparedCatalog = null;
        try {
            if (!attempt.exhausted() && attempt.catalogForEvaluation() != null) {
                preparedCatalog = Objects.requireNonNull(
                        execution.prepareCatalog(
                        attempt.catalogForEvaluation(),
                        attempt.replacementIndex(),
                        attempt.connectionGeneration(),
                        attempt.worldGeneration(),
                        attempt.nextResourceGeneration()),
                        "prepared catalog");
            }
        } catch (RuntimeException | Error failure) {
            cleanupFailedResourceApply(attempt);
            if (failure instanceof Error) {
                cleanupExecutionAfterPreparationError();
            }
            throw failure;
        }
        synchronized (this) {
            if (!matchesResourceApplyAttemptLocked(attempt)) {
                if (resourceApplyAttempt == attempt) {
                    resourceApplyAttempt = null;
                }
                return;
            }
        }
        try {
            execution.clearActive();
        } catch (RuntimeException | Error failure) {
            cleanupFailedResourceApply(attempt);
            throw failure;
        }

        synchronized (this) {
            if (!matchesResourceApplyAttemptLocked(attempt)) {
                if (resourceApplyAttempt == attempt) {
                    resourceApplyAttempt = null;
                }
                return;
            }
            resourceApplyAttempt = null;
            clearAllEventWorkLocked();
            if (preparedCatalog != null) {
                execution.publishCatalog(preparedCatalog);
            }
            resourceCounter = attempt.nextResourceGeneration();
            resourceGeneration = attempt.nextResourceGeneration();
            resourceIndex = attempt.exhausted()
                    ? P8ClientResourceIndex.empty()
                    : attempt.replacementIndex();
            resourceReady = !attempt.exhausted();
            if (attempt.replacementCatalog() != null) {
                installedCatalog = attempt.replacementCatalog();
            }
        }
    }

    int onClientPostTick() {
        requireClientThread();
        final long currentConnection;
        final long currentWorld;
        final long currentResource;
        final long currentCatalog;
        synchronized (this) {
            currentConnection = connectionGeneration;
            currentWorld = worldGeneration;
            currentResource = resourceGeneration;
            currentCatalog = installedCatalog == null
                    ? 0L
                    : installedCatalog.snapshot().catalogGeneration();
        }
        try {
            execution.onClientTick(
                    currentConnection, currentWorld, currentResource, currentCatalog);
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                clearAllEventWorkLocked();
            }
            throw failure;
        }

        var applications = 0;
        while (applications < PresentationLimits.MAX_CLIENT_EVENT_APPLICATIONS_PER_TICK) {
            final PendingEvent pending;
            final P8ProfileCatalogSnapshot catalog;
            final P8ClientResourceIndex resources;
            final boolean eligible;
            synchronized (this) {
                var polled = pendingEvents.pollFirst();
                if (polled == null) {
                    break;
                }
                applications++;
                P8ClientPresentationState.PendingEvent selectedPending = polled;
                eligible = eventReservations.get(selectedPending.reservation())
                                == EventReservationOwner.PENDING
                        && matchesCurrentEventGenerationLocked(
                                selectedPending.payload(), selectedPending.reservation());
                if (!eligible) {
                    releaseEventReservationLocked(
                            selectedPending.reservation(), EventReservationOwner.PENDING);
                }
                catalog = eligible ? installedCatalog.snapshot() : null;
                resources = eligible ? resourceIndex : null;
                pending = selectedPending;
            }
            if (!eligible) {
                continue;
            }
            try {
                var result = execution.present(
                        pending.payload(),
                        catalog,
                        resources,
                        pending.reservation().connectionGeneration(),
                        pending.reservation().worldGeneration(),
                        pending.reservation().resourceGeneration());
                if (result == P8ClientPresentationHandoffResult.UNAVAILABLE) {
                    synchronized (this) {
                        unavailableHandoffCount = saturatingIncrement(
                                unavailableHandoffCount);
                    }
                }
            } catch (RuntimeException | Error failure) {
                synchronized (this) {
                    clearAllEventWorkLocked();
                }
                throw failure;
            } finally {
                synchronized (this) {
                    releaseEventReservationLocked(
                            pending.reservation(), EventReservationOwner.PENDING);
                }
            }
        }
        return applications;
    }

    synchronized boolean connected() {
        return connected;
    }

    synchronized long connectionGeneration() {
        return connectionGeneration;
    }

    synchronized long worldGeneration() {
        return worldGeneration;
    }

    synchronized boolean worldReady() {
        return worldReady;
    }

    synchronized long resourceGeneration() {
        return resourceGeneration;
    }

    synchronized boolean resourceReady() {
        return resourceReady;
    }

    synchronized P8ClientResourceIndex resourceIndexForTesting() {
        return resourceIndex;
    }

    synchronized P8ClientResourceIndex resourceApplyCandidateForTesting() {
        return resourceApplyAttempt == null
                ? null
                : resourceApplyAttempt.replacementIndex();
    }

    synchronized long installedCatalogGeneration() {
        return installedCatalog == null
                ? 0L
                : installedCatalog.snapshot().catalogGeneration();
    }

    synchronized long catalogEvaluationResourceGeneration() {
        return installedCatalog == null
                ? 0L
                : installedCatalog.resourceGeneration();
    }

    synchronized long lastAcceptedSequence() {
        return lastAcceptedSequence;
    }

    synchronized long reservedEventCount() {
        return reservedEventCount;
    }

    synchronized long reservedEventBodyBytes() {
        return reservedEventBodyBytes;
    }

    synchronized long combinedQueuedCharge() {
        return combinedQueuedCharge;
    }

    synchronized long inFlightCatalogBodyBytes() {
        return inFlightCatalogBodyBytes;
    }

    synchronized long inFlightCatalogPacketCharge() {
        return inFlightCatalogBodyBytes == 0L
                ? 0L
                : Math.addExact(
                        inFlightCatalogBodyBytes,
                        PresentationLimits.PROFILE_CATALOG_PACKET_OVERHEAD_BYTES);
    }

    synchronized int pendingEventCount() {
        return pendingEvents.size();
    }

    synchronized long pendingCatalogGeneration() {
        return catalogMailbox == null ? 0L : catalogMailbox.payload().catalogGeneration();
    }

    synchronized long unavailableHandoffCount() {
        return unavailableHandoffCount;
    }

    synchronized P8ProfileCatalogSnapshot installedCatalogSnapshot() {
        return installedCatalog == null ? null : installedCatalog.snapshot();
    }

    /** Package-private exhaustion seam; valid product code never calls this. */
    void setGenerationCountersForTest(
            long connectionCounter, long worldCounter, long resourceCounter) {
        requireClientThread();
        requireCounter(connectionCounter, "connectionCounter");
        requireCounter(worldCounter, "worldCounter");
        requireCounter(resourceCounter, "resourceCounter");
        synchronized (this) {
            if (!eventReservations.isEmpty()
                    || catalogMailbox != null
                    || catalogBindAttempt != null
                    || inFlightCatalogBodyBytes != 0L) {
                throw new IllegalStateException(
                        "cannot prime P8 counters while handoff work is reserved");
            }
            this.connectionCounter = connectionCounter;
            this.worldCounter = worldCounter;
            this.resourceCounter = resourceCounter;
        }
    }

    private void executeCatalogDrain(
            CatalogDrainIdentity drainIdentity,
            PlayEpochIdentity capturedEpoch,
            long capturedConnection) {
        try {
            requireClientThread();
        } catch (RuntimeException | Error failure) {
            cancelCatalogDrain(
                    drainIdentity, capturedEpoch, capturedConnection);
            throw failure;
        }

        final CatalogBindAttempt attempt;
        synchronized (this) {
            var value = catalogMailbox;
            if (value == null
                    || value.drainIdentity() != drainIdentity
                    || value.epochIdentity() != capturedEpoch
                    || value.connectionGeneration() != capturedConnection) {
                return;
            }
            try {
                if (playPhase != PlayPhase.OPEN
                        || !connected
                        || playEpochIdentity != capturedEpoch
                        || !isSelectedCurrentPlayLocked()
                        || capturedConnection < 1L
                        || capturedConnection != connectionGeneration) {
                    releaseCatalogMailboxLocked();
                    return;
                }
                var incoming = value.payload().snapshot();
                if (installedCatalog != null
                        && incoming.catalogGeneration()
                                <= installedCatalog.snapshot().catalogGeneration()) {
                    releaseCatalogMailboxLocked();
                    return;
                }

                var evaluatedResourceGeneration = resourceReady
                        ? resourceGeneration
                        : 0L;
                attempt = new CatalogBindAttempt(
                        value,
                        incoming,
                        resourceIndex,
                        connectionGeneration,
                        connected,
                        worldGeneration,
                        worldReady,
                        resourceGeneration,
                        resourceReady,
                        installedCatalog == null
                                ? 0L
                                : installedCatalog.snapshot().catalogGeneration(),
                        new InstalledCatalog(incoming, evaluatedResourceGeneration));
                transferCatalogMailboxToBindLocked(value, attempt);
            } catch (RuntimeException | Error failure) {
                if (catalogMailbox == value) {
                    releaseCatalogMailboxLocked();
                }
                throw failure;
            }
        }

        final P8ClientPreparedCatalog preparedCatalog;
        try {
            preparedCatalog = Objects.requireNonNull(
                    execution.prepareCatalog(
                    attempt.catalog(),
                    attempt.resourceIndex(),
                    attempt.connectionGeneration(),
                    attempt.worldGeneration(),
                    attempt.replacementCatalog().resourceGeneration()),
                    "prepared catalog");
        } catch (RuntimeException | Error failure) {
            cleanupFailedCatalogBind(attempt);
            if (failure instanceof Error) {
                cleanupExecutionAfterPreparationError();
            }
            throw failure;
        }

        synchronized (this) {
            if (!matchesCatalogBindAttemptLocked(attempt)) {
                releaseCatalogBindAttemptLocked(attempt);
                return;
            }
        }

        try {
            execution.clearActiveForCatalogReplacement();
        } catch (RuntimeException | Error failure) {
            cleanupFailedCatalogBind(attempt);
            throw failure;
        }

        synchronized (this) {
            if (!matchesCatalogBindAttemptLocked(attempt)) {
                releaseCatalogBindAttemptLocked(attempt);
                return;
            }
            try {
                clearEventWorkOutsideInstallableCatalogsLocked(
                        attempt.catalog().catalogGeneration());
                execution.publishCatalog(preparedCatalog);
                installedCatalog = attempt.replacementCatalog();
            } finally {
                releaseCatalogBindAttemptLocked(attempt);
            }
        }
    }

    private void cancelCatalogDrain(
            CatalogDrainIdentity drainIdentity,
            PlayEpochIdentity capturedEpoch,
            long capturedConnection) {
        synchronized (this) {
            if (catalogMailbox != null
                    && catalogMailbox.drainIdentity() == drainIdentity
                    && catalogMailbox.epochIdentity() == capturedEpoch
                    && catalogMailbox.connectionGeneration() == capturedConnection) {
                releaseCatalogMailboxLocked();
            }
        }
    }

    private void executeEventMain(
            PresentationEventPayload payload, EventReservation reservation) {
        var completed = false;
        try {
            requireClientThread();
            synchronized (this) {
                if (eventReservations.get(reservation)
                        != EventReservationOwner.NETWORK_TASK) {
                    completed = true;
                    return;
                }
                if (!matchesCurrentEventGenerationLocked(payload, reservation)) {
                    releaseEventReservationLocked(
                            reservation, EventReservationOwner.NETWORK_TASK);
                    completed = true;
                    return;
                }
                if (payload.sequence() <= lastAcceptedSequence) {
                    releaseEventReservationLocked(
                            reservation, EventReservationOwner.NETWORK_TASK);
                    completed = true;
                    return;
                }

                var pending = new PendingEvent(payload, reservation);
                // Order is consumed before S5 availability or pending admission.
                lastAcceptedSequence = payload.sequence();
                if (pendingEvents.size() >= PresentationLimits.MAX_CLIENT_PENDING_EVENTS) {
                    releaseEventReservationLocked(
                            reservation, EventReservationOwner.NETWORK_TASK);
                    completed = true;
                    return;
                }
                pendingEvents.addLast(pending);
                eventReservations.put(reservation, EventReservationOwner.PENDING);
                completed = true;
            }
        } finally {
            if (!completed) {
                releaseNetworkReservation(reservation);
            }
        }
    }

    private void releaseNetworkReservation(EventReservation reservation) {
        synchronized (this) {
            releaseEventReservationLocked(
                    reservation, EventReservationOwner.NETWORK_TASK);
        }
    }

    private boolean matchesCurrentEventGenerationLocked(
            PresentationEventPayload payload, EventReservation reservation) {
        return playPhase == PlayPhase.OPEN
                && connected
                && isSelectedCurrentPlayLocked()
                && reservation.epochIdentity() == playEpochIdentity
                && worldReady
                && resourceReady
                && reservation.connectionGeneration() == connectionGeneration
                && reservation.worldGeneration() == worldGeneration
                && reservation.resourceGeneration() == resourceGeneration
                && reservation.catalogGeneration() == payload.catalogGeneration()
                && installedCatalog != null
                && installedCatalog.snapshot().catalogGeneration()
                        == payload.catalogGeneration()
                && installedCatalog.resourceGeneration() == resourceGeneration;
    }

    private void clearConnectionScopedStateLocked() {
        releaseCatalogMailboxLocked();
        resourceApplyAttempt = null;
        worldTransitionAttempt = null;
        clearAllEventWorkLocked();
        installedCatalog = null;
        lastAcceptedSequence = 0L;
        unavailableHandoffCount = 0L;
        worldGeneration = 0L;
        worldReady = false;
    }

    private void completeWorldTransitionLocked(WorldTransitionAttempt attempt) {
        if (worldTransitionAttempt != attempt) {
            return;
        }
        worldTransitionAttempt = null;
        if (playPhase == PlayPhase.OPEN
                && connected
                && isSelectedCurrentPlayLocked()
                && connectionGeneration == attempt.connectionGeneration()
                && worldGeneration == attempt.worldGeneration()) {
            worldReady = attempt.worldReadyAfterCleanup();
        }
    }

    private void clearAllEventWorkLocked() {
        var eventCharge = Math.addExact(
                reservedEventBodyBytes,
                Math.multiplyExact(
                        reservedEventCount,
                        (long) PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES));
        var nextCombinedQueuedCharge = checkedSubtractNonnegative(
                combinedQueuedCharge, eventCharge, "P8 queued charge");
        pendingEvents.clear();
        eventReservations.clear();
        reservedEventCount = 0L;
        reservedEventBodyBytes = 0L;
        combinedQueuedCharge = nextCombinedQueuedCharge;
    }

    private void clearEventWorkOutsideInstallableCatalogsLocked(
            long retainedCatalogGeneration) {
        var pendingGeneration = catalogMailbox != null
                        && catalogMailbox.epochIdentity() == playEpochIdentity
                        && catalogMailbox.connectionGeneration() == connectionGeneration
                ? catalogMailbox.payload().catalogGeneration()
                : 0L;
        var releasedReservations = new ArrayList<EventReservation>();
        for (var reservation : eventReservations.keySet()) {
            if (!isInstallableEventReservationLocked(
                    reservation, retainedCatalogGeneration, pendingGeneration)) {
                releasedReservations.add(reservation);
            }
        }
        releaseEventReservationsLocked(releasedReservations);
        pendingEvents.removeIf(pending -> releasedReservations.stream()
                .anyMatch(released -> released == pending.reservation()));
    }

    private boolean isInstallableEventReservationLocked(
            EventReservation reservation,
            long retainedCatalogGeneration,
            long pendingCatalogGeneration) {
        return reservation.epochIdentity() == playEpochIdentity
                && reservation.connectionGeneration() == connectionGeneration
                && (reservation.catalogGeneration() == retainedCatalogGeneration
                        || reservation.catalogGeneration() == pendingCatalogGeneration);
    }

    private void clearEventWorkForCatalogBindLocked(CatalogBindAttempt attempt) {
        var releasedReservations = new ArrayList<EventReservation>();
        for (var reservation : eventReservations.keySet()) {
            if (matchesCatalogBindReservation(reservation, attempt)) {
                releasedReservations.add(reservation);
            }
        }
        releaseEventReservationsLocked(releasedReservations);
        pendingEvents.removeIf(pending -> releasedReservations.stream()
                .anyMatch(released -> released == pending.reservation()));
    }

    private static boolean matchesCatalogBindReservation(
            EventReservation reservation, CatalogBindAttempt attempt) {
        return reservation.epochIdentity()
                        == attempt.mailboxValue().epochIdentity()
                && reservation.connectionGeneration() == attempt.connectionGeneration()
                && reservation.catalogGeneration()
                        == attempt.catalog().catalogGeneration();
    }

    private void releaseEventReservationLocked(
            EventReservation reservation, EventReservationOwner expectedOwner) {
        var actualOwner = eventReservations.get(reservation);
        if (actualOwner == null
                || (expectedOwner != null && actualOwner != expectedOwner)) {
            return;
        }
        var nextEventCount = checkedSubtractNonnegative(
                reservedEventCount, 1L, "P8 event reservation count");
        var nextEventBodyBytes = checkedSubtractNonnegative(
                reservedEventBodyBytes,
                reservation.bodyBytes(),
                "P8 event reservation body bytes");
        var nextCombinedQueuedCharge = checkedSubtractNonnegative(
                combinedQueuedCharge,
                reservation.packetCharge(),
                "P8 queued charge");
        eventReservations.remove(reservation);
        reservedEventCount = nextEventCount;
        reservedEventBodyBytes = nextEventBodyBytes;
        combinedQueuedCharge = nextCombinedQueuedCharge;
    }

    private void releaseCatalogMailboxLocked() {
        if (catalogMailbox == null) {
            return;
        }
        var released = catalogMailbox;
        var nextCombinedQueuedCharge = checkedSubtractNonnegative(
                combinedQueuedCharge,
                released.packetCharge(),
                "P8 queued charge");
        catalogMailbox = null;
        combinedQueuedCharge = nextCombinedQueuedCharge;
    }

    private boolean matchesCatalogBindAttemptLocked(CatalogBindAttempt attempt) {
        return catalogBindAttempt == attempt
                && playPhase == PlayPhase.OPEN
                && connected
                && isSelectedCurrentPlayLocked()
                && attempt.connected()
                && attempt.mailboxValue().epochIdentity() == playEpochIdentity
                && connectionGeneration == attempt.connectionGeneration()
                && (installedCatalog == null
                        ? attempt.priorInstalledCatalogGeneration() == 0L
                        : installedCatalog.snapshot().catalogGeneration()
                                == attempt.priorInstalledCatalogGeneration());
    }

    private void releaseCatalogBindAttemptLocked(CatalogBindAttempt attempt) {
        if (attempt == null || catalogBindAttempt != attempt) {
            return;
        }
        if (inFlightCatalogBodyBytes != attempt.catalog().wireBodyBytes()) {
            throw new IllegalStateException(
                    "P8 in-flight catalog retention ledger diverged from its owner");
        }
        catalogBindAttempt = null;
        inFlightCatalogBodyBytes = 0L;
    }

    private boolean matchesResourceApplyAttemptLocked(ResourceApplyAttempt attempt) {
        return resourceApplyAttempt == attempt
                && resourceCounter == attempt.resourceCounter()
                && resourceGeneration == attempt.resourceGeneration()
                && resourceReady == attempt.resourceReady()
                && resourceIndex == attempt.resourceIndex()
                && connectionGeneration == attempt.connectionGeneration()
                && connected == attempt.connected()
                && (!attempt.connected()
                        || playPhase == PlayPhase.OPEN
                                && isSelectedCurrentPlayLocked())
                && worldGeneration == attempt.worldGeneration()
                && worldReady == attempt.worldReady()
                && (installedCatalog == null
                        ? attempt.priorInstalledCatalogGeneration() == 0L
                        : installedCatalog.snapshot().catalogGeneration()
                                == attempt.priorInstalledCatalogGeneration());
    }

    private void cleanupFailedCatalogBind(CatalogBindAttempt attempt) {
        try {
            synchronized (this) {
                if (catalogBindAttempt != attempt) {
                    return;
                }
                try {
                    if (!isCatalogBindInstalledLocked(attempt)) {
                        clearEventWorkForCatalogBindLocked(attempt);
                    }
                } finally {
                    releaseCatalogBindAttemptLocked(attempt);
                }
            }
        } catch (RuntimeException | Error ignored) {
            // The exact bind failure remains primary.
        }
    }

    private boolean isCatalogBindInstalledLocked(CatalogBindAttempt attempt) {
        return playPhase == PlayPhase.OPEN
                && connected
                && isSelectedCurrentPlayLocked()
                && attempt.mailboxValue().epochIdentity() == playEpochIdentity
                && connectionGeneration == attempt.connectionGeneration()
                && installedCatalog != null
                && installedCatalog.snapshot().catalogGeneration()
                        == attempt.catalog().catalogGeneration();
    }

    private void cleanupFailedResourceApply(ResourceApplyAttempt attempt) {
        try {
            synchronized (this) {
                if (resourceApplyAttempt != attempt) {
                    return;
                }
                resourceApplyAttempt = null;
                clearAllEventWorkLocked();
                if (attempt.exhausted()) {
                    resourceCounter = attempt.nextResourceGeneration();
                    resourceGeneration = attempt.nextResourceGeneration();
                    resourceIndex = P8ClientResourceIndex.empty();
                    resourceReady = false;
                    if (attempt.replacementCatalog() != null) {
                        installedCatalog = attempt.replacementCatalog();
                    }
                }
            }
        } catch (RuntimeException | Error ignored) {
            // The exact execution failure remains primary.
        }
    }

    private void cleanupExecutionAfterPreparationError() {
        try {
            execution.clearActive();
        } catch (RuntimeException | Error ignored) {
            // Preserve the exact preparation Error without retaining history.
        }
    }

    private void transferCatalogMailboxToBindLocked(
            CatalogMailboxValue value, CatalogBindAttempt attempt) {
        var retainedBodyBytes = (long) value.payload().bodySize();
        if (catalogMailbox != value
                || catalogBindAttempt != null
                || inFlightCatalogBodyBytes != 0L
                || retainedBodyBytes != attempt.catalog().wireBodyBytes()) {
            throw new IllegalStateException(
                    "P8 catalog mailbox-to-bind ownership transfer is invalid");
        }
        var nextCombinedQueuedCharge = checkedSubtractNonnegative(
                combinedQueuedCharge,
                value.packetCharge(),
                "P8 queued charge");
        catalogMailbox = null;
        combinedQueuedCharge = nextCombinedQueuedCharge;
        catalogBindAttempt = attempt;
        inFlightCatalogBodyBytes = retainedBodyBytes;
    }

    private void releaseEventReservationsLocked(
            List<EventReservation> releasedReservations) {
        if (releasedReservations.isEmpty()) {
            return;
        }
        long releasedCount = 0L;
        long releasedBodyBytes = 0L;
        long releasedPacketCharge = 0L;
        for (var reservation : releasedReservations) {
            if (!eventReservations.containsKey(reservation)) {
                throw new IllegalStateException("P8 event reservation owner is missing");
            }
            releasedCount = Math.addExact(releasedCount, 1L);
            releasedBodyBytes = Math.addExact(
                    releasedBodyBytes, reservation.bodyBytes());
            releasedPacketCharge = Math.addExact(
                    releasedPacketCharge, reservation.packetCharge());
        }
        var nextEventCount = checkedSubtractNonnegative(
                reservedEventCount, releasedCount, "P8 event reservation count");
        var nextEventBodyBytes = checkedSubtractNonnegative(
                reservedEventBodyBytes,
                releasedBodyBytes,
                "P8 event reservation body bytes");
        var nextCombinedQueuedCharge = checkedSubtractNonnegative(
                combinedQueuedCharge,
                releasedPacketCharge,
                "P8 queued charge");
        for (var reservation : releasedReservations) {
            eventReservations.remove(reservation);
        }
        reservedEventCount = nextEventCount;
        reservedEventBodyBytes = nextEventBodyBytes;
        combinedQueuedCharge = nextCombinedQueuedCharge;
    }

    private boolean admitCatalogEpochLocked(
            Connection incomingConnection,
            ICommonPacketListener incomingPlayListener) {
        refreshSelectedTransportLocked();
        if (!isCurrentClientboundPlay(incomingConnection, incomingPlayListener)) {
            return false;
        }
        if (p8TransportConnection == incomingConnection
                && p8PlayListenerWitness == incomingPlayListener) {
            return playPhase == PlayPhase.PREOPEN
                    || playPhase == PlayPhase.OPENING
                    || playPhase == PlayPhase.OPEN;
        }
        if (p8TransportConnection != null
                && p8TransportConnection != incomingConnection
                && p8TransportConnection.isConnected()) {
            throw new P8ClientDispatchUnavailableException();
        }
        retireSelectedEpochLocked(false);
        selectEpochLocked(incomingConnection, incomingPlayListener);
        return true;
    }

    private void selectMainEpochLocked(
            Connection incomingConnection,
            ICommonPacketListener incomingPlayListener) {
        if (p8TransportConnection == incomingConnection
                && p8PlayListenerWitness == incomingPlayListener) {
            return;
        }
        retireSelectedEpochLocked(false);
        selectEpochLocked(incomingConnection, incomingPlayListener);
    }

    private void selectEpochLocked(
            Connection connection, ICommonPacketListener playListener) {
        p8TransportConnection = connection;
        p8PlayListenerWitness = playListener;
        playEpochIdentity = new PlayEpochIdentity();
        connected = false;
        playPhase = PlayPhase.PREOPEN;
    }

    private void refreshSelectedTransportLocked() {
        var selectedConnection = p8TransportConnection;
        if (selectedConnection == null) {
            p8PlayListenerWitness = null;
            playEpochIdentity = null;
            connected = false;
            playPhase = PlayPhase.EMPTY;
            return;
        }
        if (!selectedConnection.isConnected()) {
            retireSelectedEpochLocked(false);
            p8TransportConnection = null;
            p8PlayListenerWitness = null;
            playEpochIdentity = null;
            playPhase = PlayPhase.EMPTY;
            return;
        }
        var selectedListener = p8PlayListenerWitness;
        if (selectedListener != null
                && isCurrentClientboundPlay(selectedConnection, selectedListener)) {
            return;
        }

        retireSelectedEpochLocked(false);
        var currentListener = selectedConnection.getPacketListener();
        if (currentListener instanceof ICommonPacketListener currentCommonListener
                && isCurrentClientboundPlay(
                        selectedConnection, currentCommonListener)) {
            selectEpochLocked(selectedConnection, currentCommonListener);
            return;
        }
        p8TransportConnection = selectedConnection;
        p8PlayListenerWitness = null;
        playEpochIdentity = null;
        connected = false;
        playPhase = PlayPhase.CONFIGURING;
    }

    private void retireSelectedEpochLocked(boolean retainTerminalFence) {
        var wasPublished = playPhase == PlayPhase.OPEN && connected;
        var retiredGeneration = wasPublished ? connectionGeneration : 0L;
        clearConnectionScopedStateLocked();
        connected = false;
        if (wasPublished) {
            recordPublishedInvalidationLocked(retiredGeneration);
            if (publishedCleanupObligation == null) {
                publishedCleanupObligation = new PublishedCleanupObligation(
                        retiredGeneration, false, false);
            } else if (publishedCleanupObligation.publishedGeneration()
                    != retiredGeneration) {
                throw new IllegalStateException(
                        "P8 cannot replace an unresolved published cleanup obligation");
            }
        }
        if (retainTerminalFence
                && p8TransportConnection != null
                && p8PlayListenerWitness != null) {
            playPhase = PlayPhase.RETIRING;
        } else {
            p8PlayListenerWitness = null;
            playEpochIdentity = null;
            playPhase = p8TransportConnection == null
                    ? PlayPhase.EMPTY
                    : PlayPhase.CONFIGURING;
        }
    }

    private void recordPublishedInvalidationLocked(long publishedGeneration) {
        if (publishedGeneration < 1L) {
            throw new IllegalStateException(
                    "P8 invalidation notification requires a published generation");
        }
        if (pendingLifecycleInvalidationGeneration == 0L) {
            pendingLifecycleInvalidationGeneration = publishedGeneration;
        } else if (pendingLifecycleInvalidationGeneration != publishedGeneration) {
            throw new IllegalStateException(
                    "P8 cannot overwrite an undelivered lifecycle invalidation");
        }
    }

    private void servicePublishedInvalidationCounterLocked() {
        var obligation = publishedCleanupObligation;
        if (obligation == null || obligation.counterServiced()) {
            return;
        }
        if (connectionCounter == obligation.publishedGeneration()
                && connectionCounter != Long.MAX_VALUE) {
            connectionCounter++;
        }
        publishedCleanupObligation = new PublishedCleanupObligation(
                obligation.publishedGeneration(), true, obligation.backendCleared());
    }

    private P8ClientCleanupDisposition completeCleanupAttemptLocked(
            ConnectionTransitionAttempt attempt) {
        var attemptedObligation = attempt.cleanupObligation();
        if (attemptedObligation == null) {
            return P8ClientCleanupDisposition.NONE;
        }
        if (attemptedObligation.backendCleared()) {
            return P8ClientCleanupDisposition.SUPERSEDED;
        }
        var currentObligation = publishedCleanupObligation;
        if (currentObligation == null
                || currentObligation.publishedGeneration()
                        != attemptedObligation.publishedGeneration()) {
            throw new IllegalStateException(
                    "P8 cleanup completed without its published obligation owner");
        }
        publishedCleanupObligation = new PublishedCleanupObligation(
                currentObligation.publishedGeneration(),
                currentObligation.counterServiced(),
                true);
        return P8ClientCleanupDisposition.CLEARED;
    }

    private Optional<P8ClientDispatchTask> prepareOpenPublicationLocked(
            long publishedGeneration) {
        var value = catalogMailbox;
        if (value == null) {
            return Optional.empty();
        }
        if (value.epochIdentity() != playEpochIdentity
                || value.connectionGeneration() != 0L) {
            releaseCatalogMailboxLocked();
            return Optional.empty();
        }
        var published = new CatalogMailboxValue(
                value.drainIdentity(),
                value.epochIdentity(),
                publishedGeneration,
                value.payload(),
                value.packetCharge());
        var task = new CatalogDrainTask(
                this,
                published.drainIdentity(),
                published.epochIdentity(),
                publishedGeneration);
        catalogMailbox = published;
        return Optional.of(task);
    }

    private P8ClientTransportMaintenanceResult pendingMaintenanceResultLocked(
            P8ClientCleanupDisposition disposition) {
        return new P8ClientTransportMaintenanceResult(
                disposition,
                pendingLifecycleInvalidationGeneration == 0L
                        ? OptionalLong.empty()
                        : OptionalLong.of(pendingLifecycleInvalidationGeneration));
    }

    private P8ClientTransportMaintenanceResult transferMaintenanceResultLocked(
            P8ClientCleanupDisposition disposition) {
        var result = pendingMaintenanceResultLocked(disposition);
        pendingLifecycleInvalidationGeneration = 0L;
        return result;
    }

    private boolean hasExactLogoutAuthorityLocked(
            @Nullable Connection expectedConnection,
            @Nullable ICommonPacketListener expectedPlayListener) {
        return expectedConnection != null
                && expectedPlayListener != null
                && expectedPlayListener.getConnection() == expectedConnection
                && p8TransportConnection == expectedConnection
                && p8PlayListenerWitness == expectedPlayListener;
    }

    private boolean hasClosedTransportLogoutAuthorityLocked(
            @Nullable Connection expectedConnection,
            @Nullable ICommonPacketListener expectedPlayListener) {
        return expectedConnection != null
                && expectedPlayListener == null
                && p8TransportConnection == expectedConnection
                && !expectedConnection.isConnected();
    }

    private boolean isSelectedCurrentPlayLocked() {
        return p8TransportConnection != null
                && p8PlayListenerWitness != null
                && isCurrentClientboundPlay(
                        p8TransportConnection, p8PlayListenerWitness);
    }

    private static boolean isCurrentClientboundPlay(
            Connection connection, ICommonPacketListener listener) {
        return listener.getConnection() == connection
                && listener.protocol() == ConnectionProtocol.PLAY
                && listener.flow() == PacketFlow.CLIENTBOUND
                && connection.getReceiving() == PacketFlow.CLIENTBOUND
                && connection.isConnected()
                && connection.getPacketListener() == listener;
    }

    private static long checkedSubtractNonnegative(
            long current, long released, String owner) {
        if (current < 0L || released < 0L || released > current) {
            throw new IllegalStateException(owner + " underflow");
        }
        return Math.subtractExact(current, released);
    }

    private void requireClientThread() {
        if (!clientThreadCheck.getAsBoolean()) {
            throw new IllegalStateException(
                    "P8 client presentation mutation requires the client main thread");
        }
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? value : value + 1L;
    }

    private static void rethrowCleanupFailure(
            RuntimeException runtimeFailure, Error error) {
        if (error != null) {
            throw error;
        }
        if (runtimeFailure != null) {
            throw runtimeFailure;
        }
    }

    private static void requireCounter(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private enum EventReservationOwner {
        NETWORK_TASK,
        PENDING
    }

    private enum PlayPhase {
        EMPTY,
        CONFIGURING,
        PREOPEN,
        OPENING,
        OPEN,
        RETIRING
    }

    private record InstalledCatalog(
            P8ProfileCatalogSnapshot snapshot, long resourceGeneration) {
        private InstalledCatalog {
            Objects.requireNonNull(snapshot, "snapshot");
            if (resourceGeneration < 0L) {
                throw new IllegalArgumentException(
                        "resourceGeneration cannot be negative");
            }
        }
    }

    private record CatalogMailboxValue(
            CatalogDrainIdentity drainIdentity,
            PlayEpochIdentity epochIdentity,
            long connectionGeneration,
            ProfileCatalogPayload payload,
            long packetCharge) {
        private CatalogMailboxValue {
            Objects.requireNonNull(drainIdentity, "drainIdentity");
            Objects.requireNonNull(epochIdentity, "epochIdentity");
            Objects.requireNonNull(payload, "payload");
            if (connectionGeneration < 0L || packetCharge < 0L) {
                throw new IllegalArgumentException(
                        "P8 catalog mailbox counters cannot be negative");
            }
        }
    }

    private record CatalogBindAttempt(
            CatalogMailboxValue mailboxValue,
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resourceIndex,
            long connectionGeneration,
            boolean connected,
            long worldGeneration,
            boolean worldReady,
            long resourceGeneration,
            boolean resourceReady,
            long priorInstalledCatalogGeneration,
            InstalledCatalog replacementCatalog) {
        private CatalogBindAttempt {
            Objects.requireNonNull(mailboxValue, "mailboxValue");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(resourceIndex, "resourceIndex");
            if (priorInstalledCatalogGeneration < 0L) {
                throw new IllegalArgumentException(
                        "priorInstalledCatalogGeneration cannot be negative");
            }
            Objects.requireNonNull(replacementCatalog, "replacementCatalog");
        }
    }

    private record ResourceApplyAttempt(
            long resourceCounter,
            long resourceGeneration,
            boolean resourceReady,
            P8ClientResourceIndex resourceIndex,
            long connectionGeneration,
            boolean connected,
            long worldGeneration,
            boolean worldReady,
            long priorInstalledCatalogGeneration,
            P8ProfileCatalogSnapshot catalogForEvaluation,
            InstalledCatalog replacementCatalog,
            P8ClientResourceIndex replacementIndex,
            long nextResourceGeneration,
            boolean exhausted) {
        private ResourceApplyAttempt {
            Objects.requireNonNull(resourceIndex, "resourceIndex");
            if (priorInstalledCatalogGeneration < 0L) {
                throw new IllegalArgumentException(
                        "priorInstalledCatalogGeneration cannot be negative");
            }
            Objects.requireNonNull(replacementIndex, "replacementIndex");
        }
    }

    private record ConnectionTransitionAttempt(
            @Nullable PlayEpochIdentity epochIdentity,
            long connectionGeneration,
            @Nullable PublishedCleanupObligation cleanupObligation) {
        private ConnectionTransitionAttempt {
            if (connectionGeneration < 0L) {
                throw new IllegalArgumentException(
                        "connectionGeneration cannot be negative");
            }
        }
    }

    private record WorldTransitionAttempt(
            long connectionGeneration,
            long worldGeneration,
            boolean worldReadyAfterCleanup) {}

    private static final class CatalogDrainIdentity {}

    private static final class PlayEpochIdentity {}

    private record PublishedCleanupObligation(
            long publishedGeneration,
            boolean counterServiced,
            boolean backendCleared) {
        private PublishedCleanupObligation {
            if (publishedGeneration < 1L) {
                throw new IllegalArgumentException(
                        "publishedGeneration must identify a published generation");
            }
        }
    }

    private record EventReservation(
            P8ClientPresentationState owner,
            PlayEpochIdentity epochIdentity,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration,
            long catalogGeneration,
            long bodyBytes,
            long packetCharge) {
        private EventReservation {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(epochIdentity, "epochIdentity");
        }

        private void releaseAfterFailedEnqueue() {
            owner.releaseNetworkReservation(this);
        }
    }

    private record PendingEvent(
            PresentationEventPayload payload, EventReservation reservation) {
        private PendingEvent {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(reservation, "reservation");
        }
    }

    private record CatalogDrainTask(
            P8ClientPresentationState owner,
            CatalogDrainIdentity drainIdentity,
            PlayEpochIdentity capturedEpoch,
            long capturedConnection) implements P8ClientDispatchTask {
        private CatalogDrainTask {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(drainIdentity, "drainIdentity");
            Objects.requireNonNull(capturedEpoch, "capturedEpoch");
            if (capturedConnection < 1L) {
                throw new IllegalArgumentException(
                        "capturedConnection must identify a published generation");
            }
        }

        @Override
        public void run() {
            owner.executeCatalogDrain(
                    drainIdentity, capturedEpoch, capturedConnection);
        }

        @Override
        public void releaseAfterFailedEnqueue() {
            owner.cancelCatalogDrain(
                    drainIdentity, capturedEpoch, capturedConnection);
        }
    }

    private record EventMainTask(
            P8ClientPresentationState owner,
            PresentationEventPayload payload,
            EventReservation reservation) implements P8ClientDispatchTask {
        private EventMainTask {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(reservation, "reservation");
        }

        @Override
        public void run() {
            owner.executeEventMain(payload, reservation);
        }

        @Override
        public void releaseAfterFailedEnqueue() {
            reservation.releaseAfterFailedEnqueue();
        }
    }
}

record P8ClientConnectionOpenResult(
        boolean opened,
        long publishedGeneration,
        Optional<P8ClientDispatchTask> catalogDrain,
        P8ClientTransportMaintenanceResult maintenance) {
    P8ClientConnectionOpenResult {
        Objects.requireNonNull(catalogDrain, "catalogDrain");
        Objects.requireNonNull(maintenance, "maintenance");
        if (opened) {
            if (publishedGeneration < 1L) {
                throw new IllegalArgumentException(
                        "an opened P8 epoch requires a published generation");
            }
        } else if (publishedGeneration != 0L || catalogDrain.isPresent()) {
            throw new IllegalArgumentException(
                    "a non-open P8 result cannot publish a generation or drain");
        }
    }
}

enum P8ClientCleanupDisposition {
    NONE,
    CLEARED,
    SUPERSEDED
}

record P8ClientTransportMaintenanceResult(
        P8ClientCleanupDisposition cleanupDisposition,
        OptionalLong invalidatedPublishedGeneration) {
    P8ClientTransportMaintenanceResult {
        Objects.requireNonNull(cleanupDisposition, "cleanupDisposition");
        Objects.requireNonNull(
                invalidatedPublishedGeneration, "invalidatedPublishedGeneration");
        if (invalidatedPublishedGeneration.isPresent()
                && invalidatedPublishedGeneration.getAsLong() < 1L) {
            throw new IllegalArgumentException(
                    "an invalidated P8 generation must have been published");
        }
    }
}

/** Immutable S4 resource-generation value; S5 will populate its bounded asset IDs. */
final class P8ClientResourceIndex {
    private static final P8ClientResourceIndex EMPTY =
            new P8ClientResourceIndex(List.of(), false);

    private final List<ResourceLocation> resourceIds;
    private final boolean omittedResources;

    P8ClientResourceIndex(List<ResourceLocation> resourceIds) {
        this(resourceIds, false);
    }

    P8ClientResourceIndex(
            List<ResourceLocation> resourceIds, boolean omittedResources) {
        Objects.requireNonNull(resourceIds, "resourceIds");
        if (resourceIds.size() > PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES) {
            throw new IllegalArgumentException("P8 client resource index exceeds its bound");
        }
        var ordered = new ArrayList<ResourceLocation>(resourceIds.size());
        for (var resourceId : resourceIds) {
            Objects.requireNonNull(resourceId, "resourceId");
            if (resourceId.toString().getBytes(StandardCharsets.UTF_8).length
                    > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
                throw new IllegalArgumentException(
                        "P8 client resource ID exceeds its UTF-8 bound");
            }
            ordered.add(resourceId);
        }
        ordered.sort(Comparator.comparing(ResourceLocation::toString));
        for (var index = 1; index < ordered.size(); index++) {
            if (ordered.get(index - 1).equals(ordered.get(index))) {
                throw new IllegalArgumentException(
                        "P8 client resource index contains a duplicate ID");
            }
        }
        this.resourceIds = List.copyOf(ordered);
        this.omittedResources = omittedResources;
    }

    static P8ClientResourceIndex empty() {
        return EMPTY;
    }

    List<ResourceLocation> resourceIds() {
        return resourceIds;
    }

    boolean omittedResources() {
        return omittedResources;
    }

    boolean contains(ResourceLocation resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        return Collections.binarySearch(
                        resourceIds,
                        resourceId,
                        Comparator.comparing(ResourceLocation::toString))
                >= 0;
    }
}

enum P8ClientPresentationHandoffResult {
    PRESENTED,
    PARTIALLY_PRESENTED,
    UNAVAILABLE
}

abstract class P8ClientPresentationExecutionPort {
    abstract P8ClientPreparedCatalog prepareCatalog(
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration);

    abstract void publishCatalog(P8ClientPreparedCatalog preparedCatalog);

    abstract void onClientTick(
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration,
            long catalogGeneration);

    abstract P8ClientPresentationHandoffResult present(
            PresentationEventPayload payload,
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration);

    abstract void clearActive();

    abstract void clearActiveForCatalogReplacement();

    abstract void clearAll();
}

abstract class P8ClientPreparedCatalog {}

final class UnavailableP8ClientPresentationExecution
        extends P8ClientPresentationExecutionPort {
    static final UnavailableP8ClientPresentationExecution INSTANCE =
            new UnavailableP8ClientPresentationExecution();

    private UnavailableP8ClientPresentationExecution() {}

    @Override
    P8ClientPreparedCatalog prepareCatalog(
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(resources, "resources");
        return UnavailableP8ClientPreparedCatalog.INSTANCE;
    }

    @Override
    void publishCatalog(P8ClientPreparedCatalog preparedCatalog) {}

    @Override
    void onClientTick(
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration,
            long catalogGeneration) {}

    @Override
    P8ClientPresentationHandoffResult present(
            PresentationEventPayload payload,
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(resources, "resources");
        return P8ClientPresentationHandoffResult.UNAVAILABLE;
    }

    @Override
    void clearActive() {}

    @Override
    void clearActiveForCatalogReplacement() {}

    @Override
    void clearAll() {}
}

final class UnavailableP8ClientPreparedCatalog extends P8ClientPreparedCatalog {
    static final UnavailableP8ClientPreparedCatalog INSTANCE =
            new UnavailableP8ClientPreparedCatalog();

    private UnavailableP8ClientPreparedCatalog() {}

}
