package com.yo1no.gramarye;

import com.yo1no.gramarye.client.presentation.api.ClientProfileFactories;
import com.yo1no.gramarye.client.presentation.api.ClientProfileFactory;
import com.yo1no.gramarye.client.presentation.api.ClientProfileFactoryRegistration;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import net.minecraft.resources.ResourceLocation;

/** Client-main S5 factory binding, execution budget, and active-resource owner. */
final class P8ClientPresentationExecution extends P8ClientPresentationExecutionPort {
    private static final ResourceLocation DEFAULT_SOUND = id("default_sound");
    private static final ResourceLocation DEFAULT_PARTICLE = id("default_particle");
    private static final ResourceLocation DEFAULT_TRAIL = id("default_trail");
    private static final int PARTICLE_EXPIRY_BUCKETS = 123;

    private final P8ClientRegistryView registries;
    private final P8ClientPresentationBackend backend;
    private final ArrayList<ActivePresentation> activePresentations = new ArrayList<>(
            Math.toIntExact(PresentationLimits.MAX_CLIENT_ACTIVE_PRESENTATIONS));
    private final ArrayList<P8ClientSoundHandle> pendingSoundStops = new ArrayList<>(
            Math.toIntExact(PresentationLimits.MAX_ACTIVE_SOUNDS));
    private final Map<Integer, Integer> trackedTrailOwners = newTrackedTrailOwnerIndex();
    private final long[] particleExpiryBuckets = new long[PARTICLE_EXPIRY_BUCKETS];
    private final LinkedHashSet<P8ClientDiagnosticKey> diagnostics = new LinkedHashSet<>();

    private BoundCatalog boundCatalog = BoundCatalog.empty();
    private volatile P8ClientTrailRenderSnapshot trailRenderSnapshot =
            P8ClientTrailRenderSnapshot.empty();
    private long tick;
    private long factoryCallsThisTick;
    private long particleStartsThisTick;
    private long liveParticleCredits;
    private long soundStartsThisTick;
    private long activeSoundHandles;
    private long activeTrails;
    private long totalTrailSegments;
    private long nextTrailSegmentOrdinal = 1L;
    private boolean trailSegmentOrdinalExhausted;
    private long suppressedDiagnostics;

    P8ClientPresentationExecution(
            P8ClientRegistryView registries, P8ClientPresentationBackend backend) {
        this.registries = Objects.requireNonNull(registries, "registries");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    static P8ClientPresentationExecution production() {
        return new P8ClientPresentationExecution(
                P8ClientRegistryView.production(),
                P8ClientPresentationBackend.production());
    }

    @Override
    P8ClientPreparedCatalog prepareCatalog(
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(resources, "resources");
        var generations = new ClientGenerations(
                connectionGeneration,
                worldGeneration,
                resourceGeneration,
                catalog.catalogGeneration());
        return new PreparedCatalog(bindCatalog(catalog, resources, generations));
    }

    @Override
    void publishCatalog(P8ClientPreparedCatalog preparedCatalog) {
        if (preparedCatalog instanceof PreparedCatalog prepared
                && prepared.owner == this) {
            boundCatalog = prepared.catalog;
        }
    }

    /** Direct-test convenience for a complete single-threaded prepare/activate cycle. */
    void replaceCatalog(
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration) {
        final P8ClientPreparedCatalog prepared;
        try {
            prepared = prepareCatalog(
                    catalog,
                    resources,
                    connectionGeneration,
                    worldGeneration,
                    resourceGeneration);
        } catch (Error failure) {
            clearActivePreservingParticleCreditsSuppressingFailures();
            trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
            throw failure;
        }
        clearActiveForCatalogReplacement();
        publishCatalog(prepared);
    }

    @Override
    void onClientTick(
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration,
            long catalogGeneration) {
        tick = (tick + 1L) % PARTICLE_EXPIRY_BUCKETS;
        factoryCallsThisTick = 0L;
        particleStartsThisTick = 0L;
        soundStartsThisTick = 0L;
        releaseParticleExpiryBucket();
        var current = new ClientGenerations(
                connectionGeneration,
                worldGeneration,
                resourceGeneration,
                Math.max(0L, catalogGeneration));
        try {
            pollPendingSoundStops();
            for (var index = activePresentations.size() - 1; index >= 0; index--) {
                var active = activePresentations.get(index);
                if (!active.generations().equals(current) || !active.tick(this)) {
                    activePresentations.remove(index);
                    active.close(this, false);
                }
            }
            publishTrailSnapshot();
        } catch (RuntimeException failure) {
            clearActivePreservingParticleCreditsSuppressingFailures();
            trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
            diagnostic(P8ClientDiagnosticCode.BACKEND_RUNTIME_EXCEPTION, null, null);
        } catch (Error failure) {
            clearActivePreservingParticleCreditsSuppressingFailures();
            trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
            throw failure;
        }
    }

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
        try {
            var result = presentCurrent(
                    payload,
                    catalog,
                    connectionGeneration,
                    worldGeneration,
                    resourceGeneration);
            publishTrailSnapshot();
            return result;
        } catch (RuntimeException failure) {
            clearActivePreservingParticleCreditsSuppressingFailures();
            trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
            diagnostic(P8ClientDiagnosticCode.BACKEND_RUNTIME_EXCEPTION, null, null);
            return P8ClientPresentationHandoffResult.UNAVAILABLE;
        } catch (Error failure) {
            clearActivePreservingParticleCreditsSuppressingFailures();
            trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
            throw failure;
        }
    }

    private P8ClientPresentationHandoffResult presentCurrent(
            PresentationEventPayload payload,
            P8ProfileCatalogSnapshot catalog,
            long connectionGeneration,
            long worldGeneration,
            long resourceGeneration) {
        var generations = new ClientGenerations(
                connectionGeneration,
                worldGeneration,
                resourceGeneration,
                payload.catalogGeneration());
        if (!boundCatalog.matches(generations, catalog)
                || !backend.matchesDimension(payload.dimension())) {
            diagnostic(P8ClientDiagnosticCode.STALE_OR_WRONG_DIMENSION, null, null);
            return P8ClientPresentationHandoffResult.UNAVAILABLE;
        }

        var input = new FactoryInput(payload, backend.recipientContext(payload));
        var selectedChannels = 0;
        var successfulChannels = 0;
        var workStarted = false;
        for (var channel : ProfileChannel.values()) {
            var selected = selection(payload.appearance(), channel);
            if (selected.isEmpty()) {
                continue;
            }
            selectedChannels++;
            var outcome = presentChannel(
                    selected.orElseThrow(), channel, input, generations);
            if (outcome.completed()) {
                successfulChannels++;
            }
            workStarted |= outcome.workStarted();
        }
        if (selectedChannels == 0 || successfulChannels == 0 && !workStarted) {
            return P8ClientPresentationHandoffResult.UNAVAILABLE;
        }
        return successfulChannels == selectedChannels
                ? P8ClientPresentationHandoffResult.PRESENTED
                : P8ClientPresentationHandoffResult.PARTIALLY_PRESENTED;
    }

    @Override
    void clearActive() {
        RuntimeException primaryCleanupRuntimeFailure = null;
        Error primaryCleanupFailure = null;
        try {
            closeEveryActive(false);
        } catch (RuntimeException failure) {
            primaryCleanupRuntimeFailure = failure;
        } catch (Error failure) {
            primaryCleanupFailure = failure;
        }
        try {
            pollPendingSoundStops();
        } catch (RuntimeException failure) {
            if (primaryCleanupRuntimeFailure == null) {
                primaryCleanupRuntimeFailure = failure;
            }
        } catch (Error failure) {
            if (primaryCleanupFailure == null) {
                primaryCleanupFailure = failure;
            }
        }
        clearParticleCredits();
        trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
        if (primaryCleanupFailure != null) {
            throw primaryCleanupFailure;
        }
        if (primaryCleanupRuntimeFailure != null) {
            throw primaryCleanupRuntimeFailure;
        }
    }

    @Override
    void clearActiveForCatalogReplacement() {
        clearActivePreservingParticleCredits();
    }

    @Override
    void clearAll() {
        RuntimeException primaryCleanupRuntimeFailure = null;
        Error primaryCleanupFailure = null;
        try {
            clearActive();
        } catch (RuntimeException failure) {
            primaryCleanupRuntimeFailure = failure;
        } catch (Error failure) {
            primaryCleanupFailure = failure;
        }
        boundCatalog = BoundCatalog.empty();
        diagnostics.clear();
        suppressedDiagnostics = 0L;
        if (primaryCleanupFailure != null) {
            throw primaryCleanupFailure;
        }
        if (primaryCleanupRuntimeFailure != null) {
            throw primaryCleanupRuntimeFailure;
        }
    }

    long factoryCallsThisTick() {
        return factoryCallsThisTick;
    }

    long particleStartsThisTick() {
        return particleStartsThisTick;
    }

    long liveParticleCredits() {
        return liveParticleCredits;
    }

    long soundStartsThisTick() {
        return soundStartsThisTick;
    }

    long activeSoundHandles() {
        return activeSoundHandles;
    }

    long activeTrails() {
        return activeTrails;
    }

    long totalTrailSegments() {
        return totalTrailSegments;
    }

    int activePresentationCount() {
        return activePresentations.size();
    }

    int retainedEvictionTargetCount() {
        var retained = 0;
        for (var active : activePresentations) {
            if (active instanceof ActiveInvocation invocation
                    && invocation.reservation.hasEvictionTarget()) {
                retained++;
            }
        }
        return retained;
    }

    int diagnosticCount() {
        return diagnostics.size();
    }

    long suppressedDiagnosticCount() {
        return suppressedDiagnostics;
    }

    P8ClientTrailRenderSnapshot trailRenderSnapshot() {
        return trailRenderSnapshot;
    }

    void onRenderRuntimeFailure(P8ClientTrailRenderSnapshot failedSnapshot) {
        Objects.requireNonNull(failedSnapshot, "failedSnapshot");
        try {
            clearTrailsForRenderFailure(failedSnapshot);
            diagnostic(P8ClientDiagnosticCode.BACKEND_RUNTIME_EXCEPTION, null, null);
        } catch (RuntimeException | Error cleanupFailure) {
            // A contained render RuntimeException remains the primary failure.
            clearFailedRenderSnapshot(failedSnapshot);
        }
    }

    Error renderErrorAfterCleanup(
            P8ClientTrailRenderSnapshot failedSnapshot, Error failure) {
        Objects.requireNonNull(failedSnapshot, "failedSnapshot");
        Objects.requireNonNull(failure, "failure");
        try {
            clearTrailsForRenderFailure(failedSnapshot);
        } catch (RuntimeException | Error cleanupFailure) {
            // Preserve the exact render Error without Throwable history.
            clearFailedRenderSnapshot(failedSnapshot);
        }
        return failure;
    }

    private void clearTrailsForRenderFailure(
            P8ClientTrailRenderSnapshot failedSnapshot) {
        if (trailRenderSnapshot != failedSnapshot) {
            return;
        }
        for (var index = activePresentations.size() - 1; index >= 0; index--) {
            var active = activePresentations.get(index);
            active.closeTrails(this, true);
            if (!active.hasWork()) {
                activePresentations.remove(index);
            }
        }
        trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
    }

    private void clearFailedRenderSnapshot(
            P8ClientTrailRenderSnapshot failedSnapshot) {
        if (trailRenderSnapshot == failedSnapshot) {
            trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
        }
    }

    void onEntityUnavailable(int entityId) {
        if (entityId < 1 || !trackedTrailOwners.containsKey(entityId)) {
            return;
        }
        Error primaryCleanupFailure = null;
        var changed = false;
        for (var index = activePresentations.size() - 1; index >= 0; index--) {
            var active = activePresentations.get(index);
            try {
                if (active.closeTrackedEntity(this, entityId)) {
                    changed = true;
                    if (!active.hasWork()) {
                        activePresentations.remove(index);
                        active.close(this, false);
                    }
                }
            } catch (Error failure) {
                if (primaryCleanupFailure == null) {
                    primaryCleanupFailure = failure;
                }
            }
        }
        if (changed) {
            publishTrailSnapshot();
        }
        if (primaryCleanupFailure != null) {
            throw primaryCleanupFailure;
        }
    }

    private void publishTrailSnapshot() {
        var strips = new ArrayList<P8ClientTrailStrip>();
        for (var active : activePresentations) {
            active.appendTrailStrips(strips);
        }
        trailRenderSnapshot = new P8ClientTrailRenderSnapshot(strips);
    }

    private OptionalLong reserveTrailSegmentOrdinal() {
        if (trailSegmentOrdinalExhausted) {
            return OptionalLong.empty();
        }
        var ordinal = nextTrailSegmentOrdinal;
        if (ordinal == Long.MAX_VALUE) {
            trailSegmentOrdinalExhausted = true;
        } else {
            nextTrailSegmentOrdinal++;
        }
        return OptionalLong.of(ordinal);
    }

    private boolean releaseOldestTrailSegment() {
        ActiveTrail oldestTrail = null;
        var oldestOrdinal = Long.MAX_VALUE;
        for (var active : activePresentations) {
            var candidate = active.oldestTrailSegment();
            if (candidate.isPresent()
                    && candidate.orElseThrow().oldestSegmentOrdinal() < oldestOrdinal) {
                oldestTrail = candidate.orElseThrow();
                oldestOrdinal = oldestTrail.oldestSegmentOrdinal();
            }
        }
        return oldestTrail != null && oldestTrail.removeOldestSegment(this);
    }

    private BoundCatalog bindCatalog(
            P8ProfileCatalogSnapshot catalog,
            P8ClientResourceIndex resources,
            ClientGenerations generations) {
        if (resources.omittedResources()) {
            diagnostic(P8ClientDiagnosticCode.RESOURCE_INDEX_TRUNCATED, null, null);
        }
        var assetProbe = new AssetProbe(backend, resources);
        var bound = new HashMap<ResourceLocation, BoundProfile<?>>();
        for (var entry : catalog.entries()) {
            if (entry.decodedProfile().isEmpty()) {
                diagnostic(
                        P8ClientDiagnosticCode.PROFILE_UNAVAILABLE,
                        entry.profileId(),
                        entry.typeId());
                continue;
            }
            var captured = bindCaptured(entry, entry.decodedProfile().orElseThrow());
            if (captured.isEmpty()) {
                diagnostic(
                        P8ClientDiagnosticCode.FACTORY_BINDING_MISMATCH,
                        entry.profileId(),
                        entry.clientFactoryId());
                continue;
            }
            var value = captured.orElseThrow();
            try {
                if (isAvailable(value, assetProbe)) {
                    bound.put(entry.profileId(), value);
                } else {
                    diagnostic(
                            P8ClientDiagnosticCode.ASSET_UNAVAILABLE,
                            entry.profileId(),
                            entry.clientFactoryId());
                }
            } catch (RuntimeException failure) {
                diagnostic(
                        P8ClientDiagnosticCode.FACTORY_RUNTIME_EXCEPTION,
                        entry.profileId(),
                        entry.clientFactoryId());
            }
        }
        return new BoundCatalog(
                catalog,
                generations,
                Map.copyOf(bound),
                assetProbe.freeze());
    }

    private static <C extends ProfileConfiguration> boolean isAvailable(
            BoundProfile<C> profile, AssetProbe assets) {
        return Objects.requireNonNull(
                        profile.factory().availability(profile.configuration(), assets),
                        "factory availability")
                == ClientProfileFactory.Availability.AVAILABLE;
    }

    private <C extends ProfileConfiguration> Optional<BoundProfile<?>> bindCaptured(
            P8ProfileCatalogEntry entry,
            P8DecodedProfileConfiguration<C> decoded) {
        var type = decoded.type();
        var registeredType = registries.profileType(entry.typeId());
        var registration = registries.factory(entry.clientFactoryId());
        if (registeredType.isEmpty()
                || registeredType.orElseThrow() != type
                || registration.isEmpty()
                || !entry.typeId().equals(registries.profileTypeId(type).orElse(null))
                || entry.channel() != type.channel()
                || entry.configurationVersion() != type.currentConfigurationVersion()
                || !entry.clientFactoryId().equals(type.clientFactoryKey().id())
                || !entry.clientFactoryId().equals(
                        registries.factoryId(registration.orElseThrow()).orElse(null))
                || registration.orElseThrow().key() != type.clientFactoryKey()) {
            return Optional.empty();
        }
        return Optional.of(bindChecked(
                entry,
                decoded,
                registration.orElseThrow(),
                decoded.estimatedCost()));
    }

    /** The sole unchecked seam follows all registry, wire, and key-identity checks. */
    @SuppressWarnings("unchecked")
    private static <C extends ProfileConfiguration> BoundProfile<C> bindChecked(
            P8ProfileCatalogEntry entry,
            P8DecodedProfileConfiguration<C> decoded,
            ClientProfileFactoryRegistration<?> registration,
            ProfileCost estimatedCost) {
        var typedRegistration = (ClientProfileFactoryRegistration<C>) registration;
        return new BoundProfile<>(
                entry.profileId(),
                entry.channel(),
                decoded.configuration(),
                typedRegistration.factory(),
                estimatedCost);
    }

    private ChannelOutcome presentChannel(
            ResourceLocation selectedId,
            ProfileChannel channel,
            FactoryInput input,
            ClientGenerations generations) {
        var selected = boundCatalog.find(selectedId, channel);
        if (selected.isPresent()) {
            var attempt = invokeCaptured(selected.orElseThrow(), input, generations);
            if (attempt.completed()) {
                return new ChannelOutcome(true, attempt.workStarted());
            }
            if (!attempt.fallbackEligible()
                    || selectedId.equals(defaultId(channel))) {
                return new ChannelOutcome(false, attempt.workStarted());
            }
            var fallback = boundCatalog.find(defaultId(channel), channel);
            if (fallback.isEmpty()) {
                return new ChannelOutcome(false, attempt.workStarted());
            }
            var fallbackAttempt = invokeCaptured(
                    fallback.orElseThrow(), input, generations);
            return new ChannelOutcome(
                    fallbackAttempt.completed(),
                    attempt.workStarted() || fallbackAttempt.workStarted());
        }

        diagnostic(P8ClientDiagnosticCode.PROFILE_UNAVAILABLE, selectedId, null);
        if (selectedId.equals(defaultId(channel))) {
            return new ChannelOutcome(false, false);
        }
        var fallback = boundCatalog.find(defaultId(channel), channel);
        if (fallback.isEmpty()) {
            return new ChannelOutcome(false, false);
        }
        var fallbackAttempt = invokeCaptured(fallback.orElseThrow(), input, generations);
        return new ChannelOutcome(fallbackAttempt.completed(), fallbackAttempt.workStarted());
    }

    private <C extends ProfileConfiguration> AttemptOutcome invokeCaptured(
            BoundProfile<C> profile,
            FactoryInput input,
            ClientGenerations generations) {
        var candidate = new PresentationOrdering.ClientPresentationCandidate(
                input.recipientContext().category(),
                input.recipientContext().squaredDistance(),
                input.sequence(),
                profile.channel(),
                profile.profileId());
        if (factoryCallsThisTick >= PresentationLimits.MAX_CLIENT_FACTORY_CALLS_PER_TICK) {
            diagnostic(P8ClientDiagnosticCode.BUDGET_EXHAUSTED, profile.profileId(), null);
            return new AttemptOutcome(AttemptDisposition.DROPPED, false);
        }
        var reservation = reserveActive(candidate);
        if (reservation == null) {
            diagnostic(P8ClientDiagnosticCode.BUDGET_EXHAUSTED, profile.profileId(), null);
            return new AttemptOutcome(AttemptDisposition.DROPPED, false);
        }
        factoryCallsThisTick++;
        var output = new FactoryOutput(
                this, profile, input, generations, candidate, reservation);
        try {
            var result = Objects.requireNonNull(
                    profile.factory().present(profile.configuration(), input, output),
                    "factory result");
            if (output.runtimeFailure()) {
                var work = output.rollback(true);
                diagnostic(
                        P8ClientDiagnosticCode.BACKEND_RUNTIME_EXCEPTION,
                        profile.profileId(),
                        null);
                return new AttemptOutcome(AttemptDisposition.DROPPED, work);
            }
            if (result == ClientProfileFactory.Result.PRESENTED) {
                return new AttemptOutcome(
                        AttemptDisposition.COMPLETED, output.publish());
            }
            var work = output.rollback(false);
            diagnostic(
                    P8ClientDiagnosticCode.FACTORY_UNAVAILABLE,
                    profile.profileId(),
                    null);
            return new AttemptOutcome(
                    AttemptDisposition.FALLBACK_ELIGIBLE, work);
        } catch (RuntimeException failure) {
            boolean work;
            try {
                work = output.rollback(true);
            } catch (RuntimeException | Error cleanupFailure) {
                // Preserve the contained extension RuntimeException as primary.
                work = false;
            }
            if (output.runtimeFailure()) {
                diagnostic(
                        P8ClientDiagnosticCode.BACKEND_RUNTIME_EXCEPTION,
                        profile.profileId(),
                        null);
                return new AttemptOutcome(AttemptDisposition.DROPPED, work);
            }
            diagnostic(
                    P8ClientDiagnosticCode.FACTORY_RUNTIME_EXCEPTION,
                    profile.profileId(),
                    null);
            return new AttemptOutcome(
                    AttemptDisposition.FALLBACK_ELIGIBLE, work);
        } catch (Error failure) {
            try {
                output.rollback(true);
            } catch (RuntimeException | Error cleanupFailure) {
                // Preserve the exact primary Error without Throwable history.
            }
            throw failure;
        }
    }

    private ActiveReservation reserveActive(
            PresentationOrdering.ClientPresentationCandidate incoming) {
        if (activePresentations.size() < PresentationLimits.MAX_CLIENT_ACTIVE_PRESENTATIONS) {
            return new ActiveReservation(null);
        }
        var candidates = new ArrayList<PresentationOrdering.ClientPresentationCandidate>(
                PresentationLimits.MAX_CLIENT_ACTIVE_SELECTION_INPUT);
        for (var active : activePresentations) {
            if (active.candidate().equals(incoming)) {
                return null;
            }
            candidates.add(active.candidate());
        }
        candidates.add(incoming);
        var selection = PresentationOrdering.selectActivePresentations(
                candidates,
                Math.toIntExact(PresentationLimits.MAX_CLIENT_ACTIVE_PRESENTATIONS));
        if (!selection.retained().contains(incoming)) {
            return null;
        }
        var dropped = selection.dropped().getFirst();
        for (var active : activePresentations) {
            if (active.candidate().equals(dropped)) {
                return new ActiveReservation(active);
            }
        }
        throw new IllegalStateException("active presentation eviction target is missing");
    }

    private void addActive(
            ActivePresentation presentation, ActiveReservation reservation) {
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(reservation, "reservation");
        reservation.requireActivated();
        if (activePresentations.size()
                >= PresentationLimits.MAX_CLIENT_ACTIVE_PRESENTATIONS) {
            throw new IllegalStateException("active presentation reservation was lost");
        }
        activePresentations.add(presentation);
        reservation.markPublished();
    }

    private boolean startParticle(
            ClientProfileFactory.Particle command,
            FactoryInput input,
            AssetSnapshot assets,
            long commandOrdinal,
            ActiveInvocation invocation) {
        if (!validParticle(command, input)
                || !assets.particleExists(command.particleTypeId())
                || particleStartsThisTick
                        >= PresentationLimits.MAX_ACTUAL_PARTICLE_STARTS_PER_TICK
                || liveParticleCredits >= PresentationLimits.MAX_LIVE_PARTICLE_CREDITS) {
            return false;
        }
        var preference = backend.particlePreference();
        var preferenceOrdinal = (input.sequence() & 3L) + (commandOrdinal & 3L);
        if (preference == P8ClientParticlePreference.MINIMAL
                        && (preferenceOrdinal & 3L) != 0L
                || preference == P8ClientParticlePreference.REDUCED
                        && (preferenceOrdinal & 1L) != 0L) {
            return false;
        }
        var prepared = backend.prepareParticle(command);
        if (prepared == null) {
            return false;
        }
        invocation.activateReservation(this);
        var expiryBucket = (int) ((tick % PARTICLE_EXPIRY_BUCKETS
                        + command.lifetimeTicks()
                        + 2L)
                % PARTICLE_EXPIRY_BUCKETS);
        particleStartsThisTick++;
        liveParticleCredits++;
        particleExpiryBuckets[expiryBucket]++;
        invocation.retainParticleLifetime(command.lifetimeTicks());
        try {
            prepared.start();
            // Submission occurs after the engine's tick and enters its pending-add queue.
            // Two conservative ticks prevent a live-credit refund before platform removal.
            return true;
        } catch (RuntimeException | Error failure) {
            // Once the platform call begins, a thrown failure cannot prove that no
            // particle entered the engine. Retain the bounded credit and owner
            // lifetime without retaining the platform particle itself.
            throw failure;
        }
    }

    private boolean startSound(
            ClientProfileFactory.Sound command,
            FactoryInput input,
            AssetSnapshot assets,
            ActiveInvocation invocation) {
        if (!validSound(command, input)
                || !assets.soundExists(command.soundEventId())
                || soundStartsThisTick >= PresentationLimits.MAX_SOUND_STARTS_PER_TICK
                || activeSoundHandles >= PresentationLimits.MAX_ACTIVE_SOUNDS) {
            return false;
        }
        var preferenceVolume = backend.soundVolume();
        if (!Float.isFinite(preferenceVolume) || preferenceVolume <= 0.0F) {
            return false;
        }
        if (command.volume() <= 0.0F) {
            return false;
        }
        var handle = Objects.requireNonNull(
                backend.prepareSound(command, input.visualSeed()), "sound handle");
        invocation.activateReservation(this);
        invocation.retainSound(handle);
        soundStartsThisTick++;
        activeSoundHandles++;
        try {
            handle.start();
            if (handle.active()) {
                return true;
            }
            invocation.releaseSound(handle);
            soundStartsThisTick--;
            activeSoundHandles--;
            return false;
        } catch (RuntimeException | Error failure) {
            // The handle was retained before the platform call, so rollback can
            // stop any partially submitted sound without losing ownership.
            throw failure;
        }
    }

    private boolean startTrail(
            ClientProfileFactory.Trail command,
            FactoryInput input,
            BoundProfile<?> profile,
            ClientGenerations generations,
            AssetSnapshot assets,
            ActiveInvocation invocation) {
        if (!validTrail(command, input)
                || !assets.particleExists(command.particleTypeId())
                || activeTrails >= PresentationLimits.MAX_ACTIVE_TRAILS) {
            return false;
        }
        var capacity = profile.estimatedCost().trailSegments();
        if (capacity < 1) {
            return false;
        }
        var interval = switch (backend.particlePreference()) {
            case FULL -> command.sampleIntervalTicks();
            case REDUCED -> Math.max(command.sampleIntervalTicks(), 2);
            case MINIMAL -> Math.max(command.sampleIntervalTicks(), 4);
        };
        var trail = new ActiveTrail(command, input, generations, capacity, interval);
        var initialPosition = trail.currentPosition(this);
        if (initialPosition.isEmpty()) {
            return false;
        }
        invocation.activateReservation(this);
        invocation.retainTrail(trail);
        activeTrails++;
        trail.markOwnerRetained();
        try {
            if (command.trackedEntityId().isPresent()) {
                registerTrackedTrail(command.trackedEntityId().orElseThrow());
                trail.markTrackedRegistered();
            }
            if (!trail.sample(this, invocation, initialPosition.orElseThrow())) {
                invocation.releaseTrail(trail);
                trail.close(this, false);
                return false;
            }
        } catch (RuntimeException | Error failure) {
            cleanupFailedTrailStart(invocation, trail);
            throw failure;
        }
        return true;
    }

    private void cleanupFailedTrailStart(
            ActiveInvocation invocation, ActiveTrail trail) {
        try {
            invocation.releaseTrail(trail);
        } catch (RuntimeException | Error cleanupFailure) {
            // Preserve the already-observed factory/backend failure.
        }
        try {
            trail.close(this, true);
        } catch (RuntimeException | Error cleanupFailure) {
            // Preserve the already-observed factory/backend failure.
        }
    }

    private void releaseParticleExpiryBucket() {
        var bucket = (int) (tick % PARTICLE_EXPIRY_BUCKETS);
        var released = particleExpiryBuckets[bucket];
        particleExpiryBuckets[bucket] = 0L;
        liveParticleCredits = Math.max(0L, liveParticleCredits - released);
    }

    private void closeEveryActive(boolean suppressAllFailures) {
        RuntimeException primaryCleanupRuntimeFailure = null;
        Error primaryCleanupFailure = null;
        for (var index = activePresentations.size() - 1; index >= 0; index--) {
            try {
                activePresentations.remove(index).close(this, suppressAllFailures);
            } catch (RuntimeException failure) {
                if (primaryCleanupRuntimeFailure == null) {
                    primaryCleanupRuntimeFailure = failure;
                }
            } catch (Error failure) {
                if (primaryCleanupFailure == null) {
                    primaryCleanupFailure = failure;
                }
            }
        }
        activeTrails = 0L;
        totalTrailSegments = 0L;
        trackedTrailOwners.clear();
        if (!suppressAllFailures && primaryCleanupFailure != null) {
            throw primaryCleanupFailure;
        }
        if (!suppressAllFailures && primaryCleanupRuntimeFailure != null) {
            throw primaryCleanupRuntimeFailure;
        }
    }

    private void clearActivePreservingParticleCreditsSuppressingFailures() {
        try {
            closeEveryActive(true);
        } catch (RuntimeException | Error cleanupFailure) {
            // Preserve the already-pending presentation failure.
        }
    }

    private void clearActivePreservingParticleCredits() {
        RuntimeException primaryCleanupRuntimeFailure = null;
        Error primaryCleanupFailure = null;
        try {
            closeEveryActive(false);
        } catch (RuntimeException failure) {
            primaryCleanupRuntimeFailure = failure;
        } catch (Error failure) {
            primaryCleanupFailure = failure;
        }
        trailRenderSnapshot = P8ClientTrailRenderSnapshot.empty();
        if (primaryCleanupFailure != null) {
            throw primaryCleanupFailure;
        }
        if (primaryCleanupRuntimeFailure != null) {
            throw primaryCleanupRuntimeFailure;
        }
    }

    private void clearParticleCredits() {
        java.util.Arrays.fill(particleExpiryBuckets, 0L);
        liveParticleCredits = 0L;
    }

    private void pollPendingSoundStops() {
        for (var index = pendingSoundStops.size() - 1; index >= 0; index--) {
            var handle = pendingSoundStops.get(index);
            final boolean active;
            try {
                active = handle.active();
            } catch (RuntimeException failure) {
                diagnostic(P8ClientDiagnosticCode.CLEANUP_RUNTIME_EXCEPTION, null, null);
                continue;
            }
            if (!active) {
                pendingSoundStops.remove(index);
                activeSoundHandles = Math.max(0L, activeSoundHandles - 1L);
            }
        }
    }

    private void retainPendingSoundStop(P8ClientSoundHandle handle) {
        if (pendingSoundStops.size() >= PresentationLimits.MAX_ACTIVE_SOUNDS) {
            throw new IllegalStateException("pending sound-stop owner exceeds P8 bound");
        }
        pendingSoundStops.add(handle);
    }

    private static Map<Integer, Integer> newTrackedTrailOwnerIndex() {
        var result = new HashMap<Integer, Integer>(128);
        // Force the bounded backing table to exist before any platform trail work.
        result.put(0, 0);
        result.remove(0);
        return result;
    }

    private void registerTrackedTrail(int entityId) {
        var boxedEntityId = Integer.valueOf(entityId);
        var existing = trackedTrailOwners.get(boxedEntityId);
        if (existing == null
                && trackedTrailOwners.size() >= PresentationLimits.MAX_ACTIVE_TRAILS) {
            throw new IllegalStateException("tracked trail index exceeds the active-trail bound");
        }
        trackedTrailOwners.put(
                boxedEntityId, existing == null ? 1 : Math.addExact(existing, 1));
    }

    private void unregisterTrackedTrail(int entityId) {
        var count = trackedTrailOwners.get(entityId);
        if (count == null) {
            return;
        }
        if (count == 1) {
            trackedTrailOwners.remove(entityId);
        } else {
            trackedTrailOwners.put(entityId, count - 1);
        }
    }

    private void diagnostic(
            P8ClientDiagnosticCode code,
            ResourceLocation first,
            ResourceLocation second) {
        var key = new P8ClientDiagnosticKey(code, first, second);
        if (diagnostics.contains(key)) {
            return;
        }
        if (diagnostics.size() < PresentationLimits.MAX_PROFILE_DIAGNOSTIC_KEYS) {
            diagnostics.add(key);
        } else if (suppressedDiagnostics != Long.MAX_VALUE) {
            suppressedDiagnostics++;
        }
    }

    private static boolean validParticle(
            ClientProfileFactory.Particle command, FactoryInput input) {
        return validAsset(command.particleTypeId())
                && validPosition(command.x(), command.y(), command.z(), input)
                && finiteInRange(command.velocityX(), -16.0D, 16.0D)
                && finiteInRange(command.velocityY(), -16.0D, 16.0D)
                && finiteInRange(command.velocityZ(), -16.0D, 16.0D)
                && finiteInRange(command.size(), 0.0D, 16.0D)
                && command.size() > 0.0F
                && validLifetime(command.lifetimeTicks());
    }

    private static boolean validSound(
            ClientProfileFactory.Sound command, FactoryInput input) {
        return validAsset(command.soundEventId())
                && validPosition(command.x(), command.y(), command.z(), input)
                && finiteInRange(command.volume(), 0.0D, 1.0D)
                && finiteInRange(command.pitch(), 0.5D, 2.0D);
    }

    private static boolean validTrail(
            ClientProfileFactory.Trail command, FactoryInput input) {
        return validAsset(command.particleTypeId())
                && validPosition(command.x(), command.y(), command.z(), input)
                && finiteInRange(command.size(), 0.0D, 16.0D)
                && command.size() > 0.0F
                && validLifetime(command.lifetimeTicks())
                && command.sampleIntervalTicks() >= 1
                && command.sampleIntervalTicks() <= 120
                && (command.trackedEntityId().isEmpty()
                        || command.trackedEntityId().orElseThrow() >= 1);
    }

    private static boolean validPosition(
            double x, double y, double z, FactoryInput input) {
        if (!PresentationPosition.isValid(x, y, z)) {
            return false;
        }
        var dx = x - input.x();
        var dy = y - input.y();
        var dz = z - input.z();
        return dx * dx + dy * dy + dz * dz
                <= PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED;
    }

    private static boolean finiteInRange(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private static boolean validLifetime(int lifetimeTicks) {
        return lifetimeTicks >= 1 && lifetimeTicks <= 120;
    }

    private static boolean validAsset(ResourceLocation id) {
        return id != null
                && id.toString().getBytes(StandardCharsets.UTF_8).length
                        <= PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES;
    }

    private static Optional<ResourceLocation> selection(
            PresentationAppearance appearance, ProfileChannel channel) {
        return switch (channel) {
            case SOUND -> appearance.soundProfileId();
            case PARTICLE -> appearance.particleProfileId();
            case TRAIL -> appearance.trailProfileId();
        };
    }

    private static ResourceLocation defaultId(ProfileChannel channel) {
        return switch (channel) {
            case SOUND -> DEFAULT_SOUND;
            case PARTICLE -> DEFAULT_PARTICLE;
            case TRAIL -> DEFAULT_TRAIL;
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private enum AttemptDisposition {
        COMPLETED,
        FALLBACK_ELIGIBLE,
        DROPPED
    }

    private record AttemptOutcome(
            AttemptDisposition disposition, boolean workStarted) {
        private AttemptOutcome {
            Objects.requireNonNull(disposition, "disposition");
        }

        private boolean completed() {
            return disposition == AttemptDisposition.COMPLETED;
        }

        private boolean fallbackEligible() {
            return disposition == AttemptDisposition.FALLBACK_ELIGIBLE;
        }
    }

    private record ChannelOutcome(boolean completed, boolean workStarted) {}

    private static final class ActiveReservation {
        private ActivePresentation evictionTarget;
        private boolean activated;
        private boolean published;

        private ActiveReservation(ActivePresentation evictionTarget) {
            this.evictionTarget = evictionTarget;
        }

        private void activate(P8ClientPresentationExecution owner) {
            Objects.requireNonNull(owner, "owner");
            if (activated) {
                return;
            }
            var target = evictionTarget;
            evictionTarget = null;
            if (target != null) {
                for (var index = owner.activePresentations.size() - 1; index >= 0; index--) {
                    if (owner.activePresentations.get(index) == target) {
                        owner.activePresentations.remove(index);
                        activated = true;
                        target.close(owner, false);
                        return;
                    }
                }
            }
            if (owner.activePresentations.size()
                    >= PresentationLimits.MAX_CLIENT_ACTIVE_PRESENTATIONS) {
                throw new IllegalStateException("active presentation reservation was lost");
            }
            activated = true;
        }

        private boolean hasEvictionTarget() {
            return evictionTarget != null;
        }

        private void requireActivated() {
            if (!activated || published) {
                throw new IllegalStateException("active presentation reservation is not open");
            }
        }

        private void markPublished() {
            requireActivated();
            published = true;
        }
    }

    private record ClientGenerations(
            long connection, long world, long resource, long catalog) {
        private ClientGenerations {
            if (connection < 0L || world < 0L || resource < 0L || catalog < 0L) {
                throw new IllegalArgumentException("client generations cannot be negative");
            }
        }
    }

    private record BoundProfile<C extends ProfileConfiguration>(
            ResourceLocation profileId,
            ProfileChannel channel,
            C configuration,
            ClientProfileFactory<C> factory,
            ProfileCost estimatedCost) {
        private BoundProfile {
            Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(factory, "factory");
            Objects.requireNonNull(estimatedCost, "estimatedCost");
        }
    }

    private record BoundCatalog(
            P8ProfileCatalogSnapshot source,
            ClientGenerations generations,
            Map<ResourceLocation, BoundProfile<?>> profiles,
            AssetSnapshot assets) {
        private BoundCatalog {
            Objects.requireNonNull(generations, "generations");
            profiles = Map.copyOf(Objects.requireNonNull(profiles, "profiles"));
            Objects.requireNonNull(assets, "assets");
        }

        private static BoundCatalog empty() {
            return new BoundCatalog(
                    null,
                    new ClientGenerations(0L, 0L, 0L, 0L),
                    Map.of(),
                    AssetSnapshot.empty());
        }

        private boolean matches(
                ClientGenerations current, P8ProfileCatalogSnapshot catalog) {
            return source == catalog
                    && generations.connection() == current.connection()
                    && generations.resource() == current.resource()
                    && generations.catalog() == current.catalog();
        }

        private Optional<BoundProfile<?>> find(
                ResourceLocation profileId, ProfileChannel channel) {
            var value = profiles.get(profileId);
            return value != null && value.channel() == channel
                    ? Optional.of(value)
                    : Optional.empty();
        }
    }

    private final class PreparedCatalog extends P8ClientPreparedCatalog {
        private final P8ClientPresentationExecution owner;
        private final BoundCatalog catalog;

        private PreparedCatalog(BoundCatalog prepared) {
            owner = P8ClientPresentationExecution.this;
            catalog = Objects.requireNonNull(prepared, "prepared");
        }
    }

    private static final class FactoryInput implements ClientProfileFactory.Input {
        private final PresentationEventPayload payload;
        private final P8ClientRecipientContext recipientContext;
        private final float directionX;
        private final float directionY;
        private final float directionZ;

        private FactoryInput(
                PresentationEventPayload payload,
                P8ClientRecipientContext recipientContext) {
            this.payload = Objects.requireNonNull(payload, "payload");
            this.recipientContext = Objects.requireNonNull(
                    recipientContext, "recipientContext");
            var direction = payload.direction();
            var x = direction.xQ15() / (double) PresentationLimits.MAX_DIRECTION_Q15;
            var y = direction.yQ15() / (double) PresentationLimits.MAX_DIRECTION_Q15;
            var z = direction.zQ15() / (double) PresentationLimits.MAX_DIRECTION_Q15;
            var magnitude = Math.hypot(Math.hypot(x, y), z);
            directionX = (float) (x / magnitude);
            directionY = (float) (y / magnitude);
            directionZ = (float) (z / magnitude);
        }

        @Override
        public int eventKindCode() {
            return payload.kind().wireCode();
        }

        @Override
        public OptionalInt sourceEntityId() {
            return payload.sourceSummary().sourceEntityId();
        }

        @Override
        public OptionalInt targetEntityId() {
            return payload.sourceSummary().targetEntityId();
        }

        @Override
        public double x() {
            return payload.position().x();
        }

        @Override
        public double y() {
            return payload.position().y();
        }

        @Override
        public double z() {
            return payload.position().z();
        }

        @Override
        public float directionX() {
            return directionX;
        }

        @Override
        public float directionY() {
            return directionY;
        }

        @Override
        public float directionZ() {
            return directionZ;
        }

        @Override
        public int primaryArgb() {
            return payload.appearance().primaryArgb();
        }

        @Override
        public int secondaryArgb() {
            return payload.appearance().secondaryArgb();
        }

        @Override
        public int intensity() {
            return payload.appearance().intensityMilli();
        }

        @Override
        public OptionalInt override(ResourceLocation key) {
            Objects.requireNonNull(key, "key");
            var value = payload.appearance().parameters().get(key);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }

        @Override
        public long visualSeed() {
            return payload.visualSeed();
        }

        @Override
        public long sequence() {
            return payload.sequence();
        }

        private P8ClientRecipientContext recipientContext() {
            return recipientContext;
        }
    }

    private static final class FactoryOutput implements ClientProfileFactory.Output {
        private final P8ClientPresentationExecution owner;
        private final BoundProfile<?> profile;
        private final FactoryInput input;
        private final ActiveInvocation invocation;
        private final ActiveReservation reservation;
        private int particleCommands;
        private int soundCommands;
        private int trailCommands;
        private boolean runtimeFailure;
        private boolean terminal;

        private FactoryOutput(
                P8ClientPresentationExecution owner,
                BoundProfile<?> profile,
                FactoryInput input,
                ClientGenerations generations,
                PresentationOrdering.ClientPresentationCandidate candidate,
                ActiveReservation reservation) {
            this.owner = owner;
            this.profile = profile;
            this.input = input;
            this.reservation = reservation;
            invocation = new ActiveInvocation(
                    candidate,
                    generations,
                    reservation,
                    Math.toIntExact(profile.estimatedCost().soundStarts()),
                    Math.toIntExact(profile.estimatedCost().trailStarts()));
        }

        @Override
        public boolean particle(ClientProfileFactory.Particle command) {
            Objects.requireNonNull(command, "command");
            if (terminal
                    || runtimeFailure
                    || profile.channel() != ProfileChannel.PARTICLE
                    || particleCommands >= profile.estimatedCost().particleStarts()
                    || command.lifetimeTicks() > profile.estimatedCost().lifetimeTicks()) {
                return false;
            }
            var commandOrdinal = particleCommands++;
            try {
                if (owner.startParticle(
                        command,
                        input,
                        owner.boundCatalog.assets(),
                        commandOrdinal,
                        invocation)) {
                    return true;
                }
                return false;
            } catch (RuntimeException failure) {
                runtimeFailure = true;
                return false;
            }
        }

        @Override
        public boolean sound(ClientProfileFactory.Sound command) {
            Objects.requireNonNull(command, "command");
            if (terminal
                    || runtimeFailure
                    || profile.channel() != ProfileChannel.SOUND
                    || soundCommands >= profile.estimatedCost().soundStarts()) {
                return false;
            }
            soundCommands++;
            try {
                return owner.startSound(
                        command, input, owner.boundCatalog.assets(), invocation);
            } catch (RuntimeException failure) {
                runtimeFailure = true;
                return false;
            }
        }

        @Override
        public boolean trail(ClientProfileFactory.Trail command) {
            Objects.requireNonNull(command, "command");
            if (terminal
                    || runtimeFailure
                    || profile.channel() != ProfileChannel.TRAIL
                    || trailCommands >= profile.estimatedCost().trailStarts()
                    || command.lifetimeTicks() > profile.estimatedCost().lifetimeTicks()) {
                return false;
            }
            trailCommands++;
            try {
                return owner.startTrail(
                        command,
                        input,
                        profile,
                        invocation.generations(),
                        owner.boundCatalog.assets(),
                        invocation);
            } catch (RuntimeException failure) {
                runtimeFailure = true;
                return false;
            }
        }

        private boolean runtimeFailure() {
            return runtimeFailure;
        }

        private boolean publish() {
            requireOpen();
            var work = invocation.hasWork();
            if (!work) {
                terminal = true;
                return false;
            }
            owner.addActive(invocation, reservation);
            terminal = true;
            return true;
        }

        private boolean rollback(boolean suppressAllFailures) {
            if (terminal) {
                return false;
            }
            var work = invocation.hasWork();
            RuntimeException primaryCleanupRuntimeFailure = null;
            Error primaryCleanupFailure = null;
            try {
                invocation.closeRetainedResources(owner, suppressAllFailures);
            } catch (RuntimeException failure) {
                primaryCleanupRuntimeFailure = failure;
            } catch (Error failure) {
                primaryCleanupFailure = failure;
            }
            if (invocation.hasParticleWork()) {
                try {
                    owner.addActive(invocation, reservation);
                } catch (RuntimeException cleanupFailure) {
                    if (primaryCleanupRuntimeFailure == null) {
                        primaryCleanupRuntimeFailure = cleanupFailure;
                    }
                } catch (Error cleanupFailure) {
                    if (!suppressAllFailures && primaryCleanupFailure == null) {
                        primaryCleanupFailure = cleanupFailure;
                    }
                }
            }
            terminal = true;
            if (!suppressAllFailures && primaryCleanupFailure != null) {
                throw primaryCleanupFailure;
            }
            if (primaryCleanupRuntimeFailure != null) {
                try {
                    owner.diagnostic(
                            P8ClientDiagnosticCode.CLEANUP_RUNTIME_EXCEPTION,
                            profile.profileId(),
                            null);
                } catch (RuntimeException | Error diagnosticFailure) {
                    if (!suppressAllFailures && primaryCleanupFailure == null) {
                        if (diagnosticFailure instanceof Error error) {
                            primaryCleanupFailure = error;
                        }
                    }
                }
            }
            if (!suppressAllFailures && primaryCleanupFailure != null) {
                throw primaryCleanupFailure;
            }
            return work;
        }

        private void requireOpen() {
            if (terminal) {
                throw new IllegalStateException("factory output is already terminal");
            }
        }
    }

    private interface ActivePresentation {
        PresentationOrdering.ClientPresentationCandidate candidate();

        ClientGenerations generations();

        boolean tick(P8ClientPresentationExecution owner);

        void close(P8ClientPresentationExecution owner, boolean suppressAllFailures);

        boolean closeTrackedEntity(P8ClientPresentationExecution owner, int entityId);

        void closeTrails(P8ClientPresentationExecution owner, boolean suppressAllFailures);

        boolean hasWork();

        void appendTrailStrips(List<P8ClientTrailStrip> target);

        Optional<ActiveTrail> oldestTrailSegment();
    }

    private static final class ActiveInvocation implements ActivePresentation {
        private final PresentationOrdering.ClientPresentationCandidate candidate;
        private final ClientGenerations generations;
        private final ActiveReservation reservation;
        private int particleTicksRemaining;
        private final ArrayList<P8ClientSoundHandle> sounds;
        private final ArrayList<ActiveTrail> trails;

        private ActiveInvocation(
                PresentationOrdering.ClientPresentationCandidate candidate,
                ClientGenerations generations,
                ActiveReservation reservation,
                int soundCapacity,
                int trailCapacity) {
            this.candidate = Objects.requireNonNull(candidate, "candidate");
            this.generations = Objects.requireNonNull(generations, "generations");
            this.reservation = Objects.requireNonNull(reservation, "reservation");
            this.sounds = new ArrayList<>(soundCapacity);
            this.trails = new ArrayList<>(trailCapacity);
        }

        @Override
        public PresentationOrdering.ClientPresentationCandidate candidate() {
            return candidate;
        }

        @Override
        public ClientGenerations generations() {
            return generations;
        }

        @Override
        public boolean tick(P8ClientPresentationExecution owner) {
            for (var index = sounds.size() - 1; index >= 0; index--) {
                var handle = sounds.get(index);
                final boolean active;
                try {
                    active = handle.active();
                } catch (RuntimeException failure) {
                    sounds.remove(index);
                    closeSound(owner, handle, false);
                    continue;
                }
                if (!active) {
                    sounds.remove(index);
                    closeSound(owner, handle, false);
                }
            }
            for (var index = trails.size() - 1; index >= 0; index--) {
                var trail = trails.get(index);
                try {
                    if (!trail.tick(owner, this)) {
                        trails.remove(index);
                        trail.close(owner, false);
                    }
                } catch (RuntimeException failure) {
                    trails.remove(index);
                    trail.close(owner, false);
                    owner.diagnostic(
                            P8ClientDiagnosticCode.BACKEND_RUNTIME_EXCEPTION,
                            candidate.profileId(),
                            null);
                }
            }
            if (particleTicksRemaining > 0) {
                particleTicksRemaining--;
            }
            return !sounds.isEmpty() || !trails.isEmpty() || particleTicksRemaining > 0;
        }

        @Override
        public void close(
                P8ClientPresentationExecution owner, boolean suppressAllFailures) {
            RuntimeException primaryCleanupRuntimeFailure = null;
            Error primaryCleanupFailure = null;
            try {
                closeRetainedResources(owner, suppressAllFailures);
            } catch (RuntimeException failure) {
                primaryCleanupRuntimeFailure = failure;
            } catch (Error failure) {
                primaryCleanupFailure = failure;
            }
            particleTicksRemaining = 0;
            if (!suppressAllFailures && primaryCleanupFailure != null) {
                throw primaryCleanupFailure;
            }
            if (!suppressAllFailures && primaryCleanupRuntimeFailure != null) {
                throw primaryCleanupRuntimeFailure;
            }
        }

        private void closeRetainedResources(
                P8ClientPresentationExecution owner, boolean suppressAllFailures) {
            RuntimeException primaryCleanupRuntimeFailure = null;
            Error primaryCleanupFailure = null;
            for (var handle : sounds) {
                try {
                    closeSound(owner, handle, suppressAllFailures);
                } catch (RuntimeException failure) {
                    if (primaryCleanupRuntimeFailure == null) {
                        primaryCleanupRuntimeFailure = failure;
                    }
                } catch (Error failure) {
                    if (primaryCleanupFailure == null) {
                        primaryCleanupFailure = failure;
                    }
                }
            }
            sounds.clear();
            try {
                closeTrails(owner, suppressAllFailures);
            } catch (RuntimeException failure) {
                if (primaryCleanupRuntimeFailure == null) {
                    primaryCleanupRuntimeFailure = failure;
                }
            } catch (Error failure) {
                if (primaryCleanupFailure == null) {
                    primaryCleanupFailure = failure;
                }
            }
            if (!suppressAllFailures && primaryCleanupFailure != null) {
                throw primaryCleanupFailure;
            }
            if (!suppressAllFailures && primaryCleanupRuntimeFailure != null) {
                throw primaryCleanupRuntimeFailure;
            }
        }

        @Override
        public boolean closeTrackedEntity(
                P8ClientPresentationExecution owner, int entityId) {
            var changed = false;
            for (var index = trails.size() - 1; index >= 0; index--) {
                var trail = trails.get(index);
                if (trail.tracks(entityId)) {
                    trails.remove(index);
                    trail.close(owner, false);
                    changed = true;
                }
            }
            return changed;
        }

        @Override
        public void closeTrails(
                P8ClientPresentationExecution owner, boolean suppressAllFailures) {
            RuntimeException primaryCleanupRuntimeFailure = null;
            Error primaryCleanupFailure = null;
            for (var trail : trails) {
                try {
                    trail.close(owner, suppressAllFailures);
                } catch (RuntimeException failure) {
                    if (primaryCleanupRuntimeFailure == null) {
                        primaryCleanupRuntimeFailure = failure;
                    }
                } catch (Error failure) {
                    if (primaryCleanupFailure == null) {
                        primaryCleanupFailure = failure;
                    }
                }
            }
            trails.clear();
            if (!suppressAllFailures && primaryCleanupFailure != null) {
                throw primaryCleanupFailure;
            }
            if (!suppressAllFailures && primaryCleanupRuntimeFailure != null) {
                throw primaryCleanupRuntimeFailure;
            }
        }

        @Override
        public boolean hasWork() {
            return particleTicksRemaining > 0 || !sounds.isEmpty() || !trails.isEmpty();
        }

        private void retainParticleLifetime(int lifetimeTicks) {
            particleTicksRemaining = Math.max(particleTicksRemaining, lifetimeTicks);
        }

        private void activateReservation(P8ClientPresentationExecution owner) {
            reservation.activate(owner);
        }

        private boolean hasParticleWork() {
            return particleTicksRemaining > 0;
        }

        private void retainSound(P8ClientSoundHandle handle) {
            sounds.add(Objects.requireNonNull(handle, "handle"));
        }

        private void releaseSound(P8ClientSoundHandle handle) {
            if (!sounds.remove(handle)) {
                throw new IllegalStateException("unowned sound handle release");
            }
        }

        private void retainTrail(ActiveTrail trail) {
            trails.add(Objects.requireNonNull(trail, "trail"));
        }

        private void releaseTrail(ActiveTrail trail) {
            if (!trails.remove(trail)) {
                throw new IllegalStateException("unowned trail release");
            }
        }

        @Override
        public void appendTrailStrips(List<P8ClientTrailStrip> target) {
            for (var trail : trails) {
                trail.appendRenderStrip(target);
            }
        }

        @Override
        public Optional<ActiveTrail> oldestTrailSegment() {
            ActiveTrail oldest = null;
            var ordinal = Long.MAX_VALUE;
            for (var trail : trails) {
                if (!trail.segments.isEmpty()
                        && trail.oldestSegmentOrdinal() < ordinal) {
                    oldest = trail;
                    ordinal = trail.oldestSegmentOrdinal();
                }
            }
            return Optional.ofNullable(oldest);
        }
    }

    private static final class ActiveTrail {
        private final ClientProfileFactory.Trail command;
        private final FactoryInput input;
        private final ClientGenerations generations;
        private final int capacity;
        private final int interval;
        private final ArrayDeque<TrailSegment> segments;
        private int age;
        private boolean ownerRetained;
        private boolean trackedRegistered;
        private boolean closed;

        private ActiveTrail(
                ClientProfileFactory.Trail command,
                FactoryInput input,
                ClientGenerations generations,
                int capacity,
                int interval) {
            this.command = command;
            this.input = input;
            this.generations = generations;
            this.capacity = capacity;
            this.interval = interval;
            segments = new ArrayDeque<>(capacity);
        }

        private void markOwnerRetained() {
            ownerRetained = true;
        }

        private void markTrackedRegistered() {
            trackedRegistered = true;
        }

        private boolean sample(
                P8ClientPresentationExecution owner,
                ActiveInvocation invocation) {
            var position = currentPosition(owner);
            if (position.isEmpty()) {
                return false;
            }
            return sample(owner, invocation, position.orElseThrow());
        }

        private boolean sample(
                P8ClientPresentationExecution owner,
                ActiveInvocation invocation,
                P8ClientPosition value) {
            var ordinal = owner.reserveTrailSegmentOrdinal();
            if (ordinal.isEmpty()) {
                return false;
            }
            var retainedSegment = new TrailSegment(ordinal.orElseThrow(), value);
            if (segments.size() == capacity) {
                removeOldestSegment(owner);
            } else if (owner.totalTrailSegments
                            >= PresentationLimits.MAX_TOTAL_TRAIL_SEGMENTS
                    && !owner.releaseOldestTrailSegment()) {
                return false;
            }
            owner.totalTrailSegments++;
            try {
                segments.addLast(retainedSegment);
            } catch (RuntimeException | Error failure) {
                owner.totalTrailSegments--;
                throw failure;
            }
            var remainingLifetime = Math.max(1, command.lifetimeTicks() - age);
            var particle = new ClientProfileFactory.Particle(
                    command.particleTypeId(),
                    value.x(),
                    value.y(),
                    value.z(),
                    0.0D,
                    0.0D,
                    0.0D,
                    command.argb(),
                    command.size(),
                    remainingLifetime);
            var sampleOrdinal = age / interval;
            owner.startParticle(
                    particle,
                    input,
                    owner.boundCatalog.assets(),
                    sampleOrdinal,
                    invocation);
            return true;
        }

        private boolean tick(
                P8ClientPresentationExecution owner,
                ActiveInvocation invocation) {
            if (closed) {
                return false;
            }
            age++;
            if (age >= command.lifetimeTicks()) {
                return false;
            }
            if (command.trackedEntityId().isPresent()
                    && currentPosition(owner).isEmpty()) {
                return false;
            }
            if (age % interval == 0) {
                sample(owner, invocation);
            }
            return true;
        }

        private Optional<P8ClientPosition> currentPosition(
                P8ClientPresentationExecution owner) {
            return command.trackedEntityId().isPresent()
                    ? owner.backend.trackedPosition(command.trackedEntityId().orElseThrow())
                    : Optional.of(new P8ClientPosition(
                            command.x(), command.y(), command.z()));
        }

        private boolean tracks(int entityId) {
            return command.trackedEntityId().isPresent()
                    && command.trackedEntityId().orElseThrow() == entityId;
        }

        private void appendRenderStrip(List<P8ClientTrailStrip> target) {
            if (!closed && !segments.isEmpty()) {
                var positions = new ArrayList<P8ClientPosition>(segments.size());
                for (var segment : segments) {
                    if (positions.isEmpty()
                            || !positions.getLast().equals(segment.position())) {
                        positions.add(segment.position());
                    }
                }
                target.add(new P8ClientTrailStrip(
                        positions,
                        command.argb(),
                        command.size(),
                        input.directionX(),
                        input.directionY(),
                        input.directionZ()));
            }
        }

        private long oldestSegmentOrdinal() {
            return segments.getFirst().ordinal();
        }

        private boolean removeOldestSegment(P8ClientPresentationExecution owner) {
            if (segments.isEmpty()) {
                return false;
            }
            segments.removeFirst();
            owner.totalTrailSegments--;
            return true;
        }

        private void close(
                P8ClientPresentationExecution owner, boolean suppressAllFailures) {
            if (closed) {
                return;
            }
            closed = true;
            if (ownerRetained) {
                owner.activeTrails = Math.max(0L, owner.activeTrails - 1L);
                ownerRetained = false;
            }
            if (trackedRegistered) {
                var trackedEntityId = command.trackedEntityId();
                if (trackedEntityId.isPresent()) {
                    owner.unregisterTrackedTrail(trackedEntityId.getAsInt());
                }
                trackedRegistered = false;
            }
            owner.totalTrailSegments = Math.max(
                    0L, owner.totalTrailSegments - segments.size());
            segments.clear();
        }

        private record TrailSegment(long ordinal, P8ClientPosition position) {
            private TrailSegment {
                if (ordinal < 1L) {
                    throw new IllegalArgumentException("trail segment ordinal must be positive");
                }
                Objects.requireNonNull(position, "position");
            }
        }
    }

    private static void closeSound(
            P8ClientPresentationExecution owner,
            P8ClientSoundHandle handle,
            boolean suppressAllFailures) {
        var cleanupRuntimeFailure = false;
        Error primaryCleanupFailure = null;
        try {
            handle.stop();
        } catch (RuntimeException failure) {
            cleanupRuntimeFailure = true;
        } catch (Error failure) {
            primaryCleanupFailure = failure;
        }
        var active = true;
        try {
            active = handle.active();
        } catch (RuntimeException failure) {
            cleanupRuntimeFailure = true;
        } catch (Error failure) {
            if (primaryCleanupFailure == null) {
                primaryCleanupFailure = failure;
            }
        }
        if (active) {
            owner.retainPendingSoundStop(handle);
        } else {
            owner.activeSoundHandles = Math.max(0L, owner.activeSoundHandles - 1L);
        }
        if (cleanupRuntimeFailure) {
            try {
                owner.diagnostic(
                        P8ClientDiagnosticCode.CLEANUP_RUNTIME_EXCEPTION, null, null);
            } catch (RuntimeException | Error diagnosticFailure) {
                if (diagnosticFailure instanceof Error error
                        && primaryCleanupFailure == null) {
                    primaryCleanupFailure = error;
                }
            }
        }
        if (!suppressAllFailures && primaryCleanupFailure != null) {
            throw primaryCleanupFailure;
        }
    }

    private static final class AssetProbe implements ClientProfileFactory.AssetView {
        private final P8ClientPresentationBackend backend;
        private final P8ClientResourceIndex resources;
        private final Map<AssetKey, Boolean> results = new HashMap<>();

        private AssetProbe(
                P8ClientPresentationBackend backend, P8ClientResourceIndex resources) {
            this.backend = backend;
            this.resources = resources;
        }

        @Override
        public boolean soundExists(ResourceLocation id) {
            return query(AssetKind.SOUND, id);
        }

        @Override
        public boolean particleExists(ResourceLocation id) {
            return query(AssetKind.PARTICLE, id);
        }

        @Override
        public boolean resourceExists(ResourceLocation id) {
            return query(AssetKind.RESOURCE, id);
        }

        private boolean query(AssetKind kind, ResourceLocation id) {
            if (!validAsset(id)) {
                return false;
            }
            var key = new AssetKey(kind, id);
            var existing = results.get(key);
            if (existing != null) {
                return existing;
            }
            if (results.size() >= PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES) {
                return false;
            }
            var result = switch (kind) {
                case SOUND -> backend.soundExists(id);
                case PARTICLE -> backend.particleExists(id);
                case RESOURCE -> backend.resourceExists(id, resources);
            };
            results.put(key, result);
            return result;
        }

        private AssetSnapshot freeze() {
            return new AssetSnapshot(Map.copyOf(results));
        }
    }

    private record AssetSnapshot(Map<AssetKey, Boolean> results) {
        private AssetSnapshot {
            results = Map.copyOf(Objects.requireNonNull(results, "results"));
        }

        private static AssetSnapshot empty() {
            return new AssetSnapshot(Map.of());
        }

        private boolean soundExists(ResourceLocation id) {
            return results.getOrDefault(new AssetKey(AssetKind.SOUND, id), false);
        }

        private boolean particleExists(ResourceLocation id) {
            return results.getOrDefault(new AssetKey(AssetKind.PARTICLE, id), false);
        }
    }

    private enum AssetKind {
        SOUND,
        PARTICLE,
        RESOURCE
    }

    private record AssetKey(AssetKind kind, ResourceLocation id) {
        private AssetKey {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }
}

interface P8ClientRegistryView {
    Optional<ProfileType<?>> profileType(ResourceLocation id);

    Optional<ResourceLocation> profileTypeId(ProfileType<?> type);

    Optional<ClientProfileFactoryRegistration<?>> factory(ResourceLocation id);

    Optional<ResourceLocation> factoryId(ClientProfileFactoryRegistration<?> registration);

    static P8ClientRegistryView production() {
        return ProductionP8ClientRegistryView.INSTANCE;
    }
}

enum ProductionP8ClientRegistryView implements P8ClientRegistryView {
    INSTANCE;

    @Override
    public Optional<ProfileType<?>> profileType(ResourceLocation id) {
        return MagicRegistries.profileTypeRegistry().getOptional(id);
    }

    @Override
    public Optional<ResourceLocation> profileTypeId(ProfileType<?> type) {
        return Optional.ofNullable(MagicRegistries.profileTypeRegistry().getKey(type));
    }

    @Override
    public Optional<ClientProfileFactoryRegistration<?>> factory(ResourceLocation id) {
        return ClientProfileFactories.registry().getOptional(id);
    }

    @Override
    public Optional<ResourceLocation> factoryId(
            ClientProfileFactoryRegistration<?> registration) {
        return Optional.ofNullable(ClientProfileFactories.registry().getKey(registration));
    }
}

interface P8ClientPresentationBackend {
    boolean matchesDimension(ResourceLocation dimension);

    P8ClientRecipientContext recipientContext(PresentationEventPayload payload);

    boolean soundExists(ResourceLocation id);

    boolean particleExists(ResourceLocation id);

    boolean resourceExists(ResourceLocation id, P8ClientResourceIndex resources);

    P8ClientParticlePreference particlePreference();

    float soundVolume();

    P8ClientSoundHandle prepareSound(ClientProfileFactory.Sound command, long visualSeed);

    P8ClientPreparedParticle prepareParticle(ClientProfileFactory.Particle command);

    Optional<P8ClientPosition> trackedPosition(int entityId);

    static P8ClientPresentationBackend production() {
        return MinecraftP8ClientPresentationBackend.INSTANCE;
    }
}

enum P8ClientParticlePreference {
    FULL,
    REDUCED,
    MINIMAL
}

interface P8ClientSoundHandle {
    void start();

    boolean active();

    void stop();
}

@FunctionalInterface
interface P8ClientPreparedParticle {
    void start();
}

record P8ClientPosition(double x, double y, double z) {
    P8ClientPosition {
        if (!PresentationPosition.isValid(x, y, z)) {
            throw new IllegalArgumentException("client position is outside P8 bounds");
        }
    }
}

record P8ClientRecipientContext(
        PresentationOrdering.RecipientCategory category, double squaredDistance) {
    P8ClientRecipientContext {
        Objects.requireNonNull(category, "category");
        if (!Double.isFinite(squaredDistance)
                || squaredDistance < 0.0D
                || squaredDistance > PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED) {
            throw new IllegalArgumentException("client recipient distance is outside P8 bounds");
        }
    }
}

record P8ClientTrailStrip(
        List<P8ClientPosition> positions,
        int argb,
        float size,
        float directionX,
        float directionY,
        float directionZ) {
    P8ClientTrailStrip {
        positions = List.copyOf(Objects.requireNonNull(positions, "positions"));
        if (positions.isEmpty()
                || positions.size() > PresentationLimits.MAX_PROFILE_TRAIL_SEGMENTS
                || !Float.isFinite(size)
                || size <= 0.0F
                || size > 16.0F
                || !Float.isFinite(directionX)
                || !Float.isFinite(directionY)
                || !Float.isFinite(directionZ)) {
            throw new IllegalArgumentException("trail render strip is outside P8 bounds");
        }
    }
}

record P8ClientTrailRenderSnapshot(List<P8ClientTrailStrip> strips) {
    private static final P8ClientTrailRenderSnapshot EMPTY =
            new P8ClientTrailRenderSnapshot(List.of());

    P8ClientTrailRenderSnapshot {
        strips = List.copyOf(Objects.requireNonNull(strips, "strips"));
        if (strips.size() > PresentationLimits.MAX_ACTIVE_TRAILS) {
            throw new IllegalArgumentException("trail render snapshot has too many owners");
        }
        long segments = 0L;
        for (var strip : strips) {
            segments = Math.addExact(segments, strip.positions().size());
        }
        if (segments > PresentationLimits.MAX_TOTAL_TRAIL_SEGMENTS) {
            throw new IllegalArgumentException("trail render snapshot exceeds segment bound");
        }
    }

    static P8ClientTrailRenderSnapshot empty() {
        return EMPTY;
    }
}

enum P8ClientDiagnosticCode {
    PROFILE_UNAVAILABLE,
    FACTORY_BINDING_MISMATCH,
    ASSET_UNAVAILABLE,
    FACTORY_UNAVAILABLE,
    FACTORY_RUNTIME_EXCEPTION,
    BACKEND_RUNTIME_EXCEPTION,
    CLEANUP_RUNTIME_EXCEPTION,
    RESOURCE_INDEX_TRUNCATED,
    BUDGET_EXHAUSTED,
    STALE_OR_WRONG_DIMENSION
}

record P8ClientDiagnosticKey(
        P8ClientDiagnosticCode code,
        ResourceLocation first,
        ResourceLocation second) {
    P8ClientDiagnosticKey {
        Objects.requireNonNull(code, "code");
        if (first != null && first.toString().getBytes(StandardCharsets.UTF_8).length > 128
                || second != null
                        && second.toString().getBytes(StandardCharsets.UTF_8).length > 128) {
            throw new IllegalArgumentException("client diagnostic ID exceeds P8 bounds");
        }
    }
}
