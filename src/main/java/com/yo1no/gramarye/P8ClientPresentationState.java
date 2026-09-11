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
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceLocation;

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
            ProfileCatalogPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!connected
                || payload.bodySize()
                        > PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES) {
            return Optional.empty();
        }

        var incomingGeneration = payload.catalogGeneration();
        if (installedCatalog != null
                && incomingGeneration <= installedCatalog.snapshot().catalogGeneration()) {
            return Optional.empty();
        }

        var capturedConnection = connectionGeneration;
        var packetCharge = Math.addExact(
                (long) payload.bodySize(),
                PresentationLimits.PROFILE_CATALOG_PACKET_OVERHEAD_BYTES);
        if (catalogBindAttempt != null
                && catalogBindAttempt.connectionGeneration() == capturedConnection
                && incomingGeneration
                        <= catalogBindAttempt.catalog().catalogGeneration()) {
            return Optional.empty();
        }
        if (catalogMailbox != null) {
            if (catalogMailbox.connectionGeneration() != capturedConnection) {
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
                drainIdentity, capturedConnection, payload, packetCharge);
        var task = Optional.<P8ClientDispatchTask>of(new CatalogDrainTask(
                this, drainIdentity, capturedConnection));
        var nextCombinedQueuedCharge = Math.addExact(
                combinedQueuedCharge, packetCharge);
        catalogMailbox = replacement;
        combinedQueuedCharge = nextCombinedQueuedCharge;
        return task;
    }

    @Override
    public synchronized Optional<P8ClientDispatchTask> preparePresentationEvent(
            PresentationEventPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!connected) {
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

    void onConnectionOpened() {
        requireClientThread();
        final ConnectionTransitionAttempt attempt;
        synchronized (this) {
            clearConnectionScopedStateLocked();
            worldCounter = 0L;
            var canOpen = connectionCounter != Long.MAX_VALUE;
            if (!canOpen) {
                connected = false;
                connectionGeneration = connectionCounter;
            } else {
                connectionCounter++;
                connectionGeneration = connectionCounter;
                connected = false;
            }
            attempt = new ConnectionTransitionAttempt(
                    connectionGeneration, canOpen);
            connectionTransitionAttempt = attempt;
        }

        RuntimeException cleanupRuntimeFailure = null;
        Error cleanupError = null;
        try {
            execution.clearAll();
        } catch (RuntimeException failure) {
            cleanupRuntimeFailure = failure;
        } catch (Error failure) {
            cleanupError = failure;
        }
        synchronized (this) {
            if (connectionTransitionAttempt == attempt) {
                connectionTransitionAttempt = null;
                if (connectionGeneration == attempt.connectionGeneration()) {
                    connected = attempt.connectedAfterCleanup();
                }
            }
        }
        rethrowCleanupFailure(cleanupRuntimeFailure, cleanupError);
    }

    void onLoggedOut() {
        requireClientThread();
        final ConnectionTransitionAttempt attempt;
        synchronized (this) {
            clearConnectionScopedStateLocked();
            if (connectionCounter != Long.MAX_VALUE) {
                connectionCounter++;
            }
            connectionGeneration = connectionCounter;
            connected = false;
            worldCounter = 0L;
            attempt = new ConnectionTransitionAttempt(connectionGeneration, false);
            connectionTransitionAttempt = attempt;
        }

        RuntimeException cleanupRuntimeFailure = null;
        Error cleanupError = null;
        try {
            execution.clearAll();
        } catch (RuntimeException failure) {
            cleanupRuntimeFailure = failure;
        } catch (Error failure) {
            cleanupError = failure;
        }
        synchronized (this) {
            if (connectionTransitionAttempt == attempt) {
                connectionTransitionAttempt = null;
            }
        }
        rethrowCleanupFailure(cleanupRuntimeFailure, cleanupError);
    }

    void onWorldLoaded() {
        requireClientThread();
        final WorldTransitionAttempt attempt;
        synchronized (this) {
            if (!connected) {
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
            if (!connected) {
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
                            && connected
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
            CatalogDrainIdentity drainIdentity, long capturedConnection) {
        try {
            requireClientThread();
        } catch (RuntimeException | Error failure) {
            cancelCatalogDrain(drainIdentity);
            throw failure;
        }

        final CatalogBindAttempt attempt;
        synchronized (this) {
            var value = catalogMailbox;
            if (value == null || value.drainIdentity() != drainIdentity) {
                return;
            }
            try {
                if (!connected
                        || capturedConnection < 1L
                        || capturedConnection != connectionGeneration
                        || value.connectionGeneration() != capturedConnection) {
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

    private void cancelCatalogDrain(CatalogDrainIdentity drainIdentity) {
        synchronized (this) {
            if (catalogMailbox != null
                    && catalogMailbox.drainIdentity() == drainIdentity) {
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
        return connected
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
        connectionTransitionAttempt = null;
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
        if (connected
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
        return reservation.connectionGeneration() == connectionGeneration
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
        return reservation.connectionGeneration() == attempt.connectionGeneration()
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
                && connected
                && attempt.connected()
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
        return connected
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
            long connectionGeneration,
            ProfileCatalogPayload payload,
            long packetCharge) {
        private CatalogMailboxValue {
            Objects.requireNonNull(drainIdentity, "drainIdentity");
            Objects.requireNonNull(payload, "payload");
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
            long connectionGeneration, boolean connectedAfterCleanup) {}

    private record WorldTransitionAttempt(
            long connectionGeneration,
            long worldGeneration,
            boolean worldReadyAfterCleanup) {}

    private static final class CatalogDrainIdentity {}

    private record EventReservation(
            P8ClientPresentationState owner,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration,
            long catalogGeneration,
            long bodyBytes,
            long packetCharge) {
        private EventReservation {
            Objects.requireNonNull(owner, "owner");
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
            long capturedConnection) implements P8ClientDispatchTask {
        private CatalogDrainTask {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(drainIdentity, "drainIdentity");
        }

        @Override
        public void run() {
            owner.executeCatalogDrain(drainIdentity, capturedConnection);
        }

        @Override
        public void releaseAfterFailedEnqueue() {
            owner.cancelCatalogDrain(drainIdentity);
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
