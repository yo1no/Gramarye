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
    private final Map<EventReservation, EventReservationOwner> eventReservations =
            new IdentityHashMap<>();
    private final ArrayDeque<PendingEvent> pendingEvents = new ArrayDeque<>();

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

    private long reservedEventCount;
    private long reservedEventBodyBytes;
    private long combinedQueuedCharge;
    private long unavailableHandoffCount;

    P8ClientPresentationState(BooleanSupplier clientThreadCheck) {
        this.clientThreadCheck = Objects.requireNonNull(
                clientThreadCheck, "clientThreadCheck");
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
        if (catalogMailbox != null) {
            if (catalogMailbox.connectionGeneration() != capturedConnection) {
                releaseCatalogMailboxLocked();
            } else {
                if (incomingGeneration <= catalogMailbox.payload().catalogGeneration()) {
                    return Optional.empty();
                }
                var chargeWithoutOld = combinedQueuedCharge - catalogMailbox.packetCharge();
                if (packetCharge
                        > PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES
                                - chargeWithoutOld) {
                    return Optional.empty();
                }
                catalogMailbox = new CatalogMailboxValue(
                        catalogMailbox.drainIdentity(),
                        capturedConnection,
                        payload,
                        packetCharge);
                combinedQueuedCharge = chargeWithoutOld + packetCharge;
                return Optional.empty();
            }
        }

        if (packetCharge
                > PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES
                        - combinedQueuedCharge) {
            return Optional.empty();
        }
        var drainIdentity = new CatalogDrainIdentity();
        catalogMailbox = new CatalogMailboxValue(
                drainIdentity, capturedConnection, payload, packetCharge);
        combinedQueuedCharge += packetCharge;
        return Optional.of(new CatalogDrainTask(
                this, drainIdentity, capturedConnection));
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
                        > PresentationLimits.MAX_CLIENT_PENDING_EVENT_BODY_BYTES
                                - reservedEventBodyBytes
                || packetCharge
                        > PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES
                                - combinedQueuedCharge) {
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
        eventReservations.put(reservation, EventReservationOwner.NETWORK_TASK);
        reservedEventCount++;
        reservedEventBodyBytes += bodyBytes;
        combinedQueuedCharge += packetCharge;
        return Optional.of(new EventMainTask(this, payload, reservation));
    }

    void onConnectionOpened() {
        requireClientThread();
        synchronized (this) {
            clearConnectionScopedStateLocked();
            worldCounter = 0L;
            if (connectionCounter == Long.MAX_VALUE) {
                connected = false;
                connectionGeneration = connectionCounter;
                return;
            }
            connectionCounter++;
            connectionGeneration = connectionCounter;
            connected = true;
        }
    }

    void onLoggedOut() {
        requireClientThread();
        synchronized (this) {
            clearConnectionScopedStateLocked();
            if (connectionCounter != Long.MAX_VALUE) {
                connectionCounter++;
            }
            connectionGeneration = connectionCounter;
            connected = false;
            worldCounter = 0L;
        }
    }

    void onWorldLoaded() {
        requireClientThread();
        synchronized (this) {
            if (!connected) {
                return;
            }
            clearAllEventWorkLocked();
            if (worldCounter == Long.MAX_VALUE) {
                worldReady = false;
                worldGeneration = worldCounter;
                return;
            }
            worldCounter++;
            worldGeneration = worldCounter;
            worldReady = true;
        }
    }

    void onWorldUnloaded(boolean hasCurrentWorld) {
        requireClientThread();
        synchronized (this) {
            if (!connected) {
                return;
            }
            clearAllEventWorkLocked();
            if (worldCounter != Long.MAX_VALUE) {
                worldCounter++;
            } else {
                worldGeneration = worldCounter;
                worldReady = false;
                return;
            }
            worldGeneration = worldCounter;
            worldReady = hasCurrentWorld;
        }
    }

    void onResourceIndexApplied(P8ClientResourceIndex replacement) {
        requireClientThread();
        Objects.requireNonNull(replacement, "replacement");
        synchronized (this) {
            clearAllEventWorkLocked();
            resourceReady = false;
            resourceIndex = P8ClientResourceIndex.empty();
            if (resourceCounter == Long.MAX_VALUE) {
                resourceGeneration = resourceCounter;
                if (installedCatalog != null) {
                    installedCatalog = new InstalledCatalog(
                            installedCatalog.snapshot(), 0L);
                }
                return;
            }
            resourceCounter++;
            resourceGeneration = resourceCounter;
            resourceIndex = replacement;
            resourceReady = true;
            if (installedCatalog != null) {
                installedCatalog = new InstalledCatalog(
                        installedCatalog.snapshot(), resourceGeneration);
            }
        }
    }

    int onClientPostTick() {
        requireClientThread();
        synchronized (this) {
            var applications = 0;
            while (applications < PresentationLimits.MAX_CLIENT_EVENT_APPLICATIONS_PER_TICK) {
                var pending = pendingEvents.pollFirst();
                if (pending == null) {
                    break;
                }
                applications++;
                try {
                    if (eventReservations.get(pending.reservation())
                                    == EventReservationOwner.PENDING
                            && matchesCurrentEventGenerationLocked(
                                    pending.payload(), pending.reservation())) {
                        var result = P8ClientPresentationHandoff.offerUnavailable(
                                pending.payload(),
                                installedCatalog.snapshot(),
                                resourceIndex);
                        if (result == P8ClientPresentationHandoffResult.S5_UNAVAILABLE) {
                            unavailableHandoffCount = saturatingIncrement(
                                    unavailableHandoffCount);
                        }
                    }
                } finally {
                    releaseEventReservationLocked(
                            pending.reservation(), EventReservationOwner.PENDING);
                }
            }
            return applications;
        }
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
            if (!eventReservations.isEmpty() || catalogMailbox != null) {
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

        synchronized (this) {
            var value = catalogMailbox;
            if (value == null || value.drainIdentity() != drainIdentity) {
                return;
            }
            catalogMailbox = null;
            try {
                if (!connected
                        || capturedConnection < 1L
                        || capturedConnection != connectionGeneration
                        || value.connectionGeneration() != capturedConnection) {
                    return;
                }
                var incoming = value.payload().snapshot();
                if (installedCatalog != null
                        && incoming.catalogGeneration()
                                <= installedCatalog.snapshot().catalogGeneration()) {
                    return;
                }

                clearEventWorkFromOtherCatalogsLocked(incoming.catalogGeneration());
                installedCatalog = new InstalledCatalog(
                        incoming, resourceReady ? resourceGeneration : 0L);
            } finally {
                releaseCatalogChargeLocked(value);
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

                // Order is consumed before S5 availability or pending admission.
                lastAcceptedSequence = payload.sequence();
                if (pendingEvents.size() >= PresentationLimits.MAX_CLIENT_PENDING_EVENTS) {
                    releaseEventReservationLocked(
                            reservation, EventReservationOwner.NETWORK_TASK);
                    completed = true;
                    return;
                }
                pendingEvents.addLast(new PendingEvent(payload, reservation));
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
        clearAllEventWorkLocked();
        installedCatalog = null;
        lastAcceptedSequence = 0L;
        unavailableHandoffCount = 0L;
        worldGeneration = 0L;
        worldReady = false;
    }

    private void clearAllEventWorkLocked() {
        pendingEvents.clear();
        var reservations = new ArrayList<>(eventReservations.keySet());
        for (var reservation : reservations) {
            releaseEventReservationLocked(reservation, null);
        }
    }

    private void clearEventWorkFromOtherCatalogsLocked(long retainedCatalogGeneration) {
        pendingEvents.removeIf(pending ->
                pending.reservation().catalogGeneration() != retainedCatalogGeneration);
        var reservations = new ArrayList<>(eventReservations.keySet());
        for (var reservation : reservations) {
            if (reservation.catalogGeneration() != retainedCatalogGeneration) {
                releaseEventReservationLocked(reservation, null);
            }
        }
    }

    private void releaseEventReservationLocked(
            EventReservation reservation, EventReservationOwner expectedOwner) {
        var actualOwner = eventReservations.get(reservation);
        if (actualOwner == null
                || (expectedOwner != null && actualOwner != expectedOwner)) {
            return;
        }
        eventReservations.remove(reservation);
        reservedEventCount--;
        reservedEventBodyBytes -= reservation.bodyBytes();
        combinedQueuedCharge -= reservation.packetCharge();
    }

    private void releaseCatalogMailboxLocked() {
        if (catalogMailbox == null) {
            return;
        }
        var released = catalogMailbox;
        catalogMailbox = null;
        releaseCatalogChargeLocked(released);
    }

    private void releaseCatalogChargeLocked(CatalogMailboxValue released) {
        combinedQueuedCharge -= released.packetCharge();
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
    private static final P8ClientResourceIndex EMPTY = new P8ClientResourceIndex(List.of());

    private final List<ResourceLocation> resourceIds;

    P8ClientResourceIndex(List<ResourceLocation> resourceIds) {
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
    }

    static P8ClientResourceIndex empty() {
        return EMPTY;
    }

    List<ResourceLocation> resourceIds() {
        return resourceIds;
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

/** Exact typed S4 terminal while no S5 factory/execution owner exists. */
enum P8ClientPresentationHandoffResult {
    S5_UNAVAILABLE
}

final class P8ClientPresentationHandoff {
    private P8ClientPresentationHandoff() {
        throw new AssertionError("no instances");
    }

    static P8ClientPresentationHandoffResult offerUnavailable(
            PresentationEventPayload payload,
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(resources, "resources");
        return P8ClientPresentationHandoffResult.S5_UNAVAILABLE;
    }
}
