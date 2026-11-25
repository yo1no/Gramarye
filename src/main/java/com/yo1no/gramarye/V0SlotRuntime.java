package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.V0PlayWitness;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;

/** Main-thread substate of the one P5 slot. The off-thread reload path only closes the RAM gate. */
final class V0SlotRuntime {
    private static final int PREPARATION_TICKS = 8;
    private static final int MAX_LOGICAL_PROGRAM_BYTES = 1_089;
    // The already-qualified V0 engineering profile provides this exact binder value to C.
    private static final int LAUNCH_STEP_MILLI = 1_000;
    private static final ResourceLocation SPAWN = ResourceLocation.fromNamespaceAndPath("gramarye", "spawn_projectile");
    private static final ResourceLocation DAMAGE = ResourceLocation.fromNamespaceAndPath("gramarye", "damage");

    private final SkillRuntimeService runtime;
    private final V0CatalogAuthority authority;
    private final V0SlotBinding binding;
    private final V0RuntimeCatalog catalog;
    private final V0ColdMailbox mailbox;
    private final V0StartupLimits limits;
    private final Lineage[] lineages;
    private final V0P5Queue queue;
    private final UUID[] attemptedAttributions;
    private final Registry<DimensionType> dimensionTypes;
    private final Registry<LevelStem> levelStems;
    private final P11CastCooldownService cooldowns;
    private final NormalSpellPolicy normalActivation;
    private final Map<SkillOwnerId, V0RuntimeCatalog.Selection> normalSelections = new HashMap<>();
    private long provisioningTicket;
    private SkillOwnerId provisioningOwner;
    private boolean bootstrapStopped;

    private PendingCapture pending;
    private DispatchClaim currentDispatch;
    private V0RuntimeCatalog.Candidate pendingDiscard;
    private long ticketHighWater;
    private long lineageHighWater;
    private long instanceHighWater;
    private long carrierHighWater;
    private long ownerQueueOrder;
    private long attemptTick = -1;
    private long acceptedTick = -1;
    private long lastExpiredTicket;
    private int attemptCount;
    private boolean accepting;
    private boolean retirementSent;
    private volatile boolean stopped;
    private V0TerminalReason stopReason;
    private volatile V0RuntimeCatalog.Suspension suspended;
    private V0RuntimeCatalog.Suspension installedReload;
    private WeakReference<ReloadableServerResources> reloadSource;
    private long legacyEventOrder;
    private long currentTick;
    private long requestHighWater;
    private int dispatchedThisTick;

    V0SlotRuntime(V0SlotStartup startup, V0SlotBinding binding) {
        Objects.requireNonNull(startup, "startup");
        this.runtime = Objects.requireNonNull(startup.runtime(), "runtime");
        this.authority = Objects.requireNonNull(startup.authority(), "authority");
        if (!(startup.v0State() instanceof V0StartupLoadState.Ready ready)
                || !startup.server().isSameThread() || !authority.isOwnerThread()) {
            throw new IllegalArgumentException("V0 slot requires its ready server owner");
        }
        this.binding = Objects.requireNonNull(binding, "binding");
        if (binding.normalPolicy() != (startup.profile() == V0StartupProfile.Kind.NORMAL)) {
            throw new IllegalArgumentException("V0 profile and binding disagree");
        }
        cooldowns = runtime.v0Cooldowns();
        normalActivation = binding.activationPolicy();
        if (binding.normalPolicy()) {
            if (cooldowns == null || normalActivation == null) {
                throw new IllegalArgumentException("Normal V0 requires its formal owners");
            }
            normalSelections.putAll(normalActivation.roots());
        }
        catalog = binding.catalog();
        mailbox = binding.mailbox();
        limits = ready.limits();
        lineages = new Lineage[limits.maxLineagesPerServer()];
        queue = new V0P5Queue(limits.maxLineagesPerServer());
        attemptedAttributions = new UUID[limits.rootAttemptsPerTick()];
        var registries = startup.server().registryAccess();
        dimensionTypes = registries.registryOrThrow(Registries.DIMENSION_TYPE);
        levelStems = registries.registryOrThrow(Registries.LEVEL_STEM);
    }

    boolean hasAuthority(V0CatalogAuthority supplied) {
        return supplied == authority;
    }

    boolean normalPolicy() { return binding.normalPolicy(); }

    boolean sealBootstrapForSunset(V0CatalogAuthority exact, V0SlotBinding exactBinding) {
        requireMain();
        if (exact != authority || exactBinding != binding || !normalPolicy() || !normalActivation.bootstrap()
                || stopped || suspended != null) { return false; }
        bootstrapStopped = true;
        normalSelections.clear();
        if (pending != null) {
            var rejected = pending;
            pending = null;
            completeNormal(rejected, V0Admission.SLOT_STALE);
        }
        return true;
    }

    boolean bootstrapDrainedForSunset(V0CatalogAuthority exact, V0SlotBinding exactBinding) {
        requireMain();
        return exact == authority && exactBinding == binding && normalPolicy() && normalActivation.bootstrap()
                && bootstrapStopped && !stopped && suspended == null && pending == null
                && provisioningTicket == 0 && provisioningOwner == null && pendingDiscard == null
                && !accepting && currentDispatch == null && !hasLiveLineages()
                && mailbox.idleForBootstrapRetirement();
    }

    long reserveBootstrapCleanupTicket(V0CatalogAuthority exact, V0SlotBinding exactBinding) {
        requireMain();
        if (!bootstrapDrainedForSunset(exact, exactBinding) || ticketHighWater == Long.MAX_VALUE) { return 0; }
        return ++ticketHighWater;
    }

    P7ServerAuthorizationBoundary.NormalIngressDisposition reserveNormal(ServerPlayer actor,
            V0PlayWitness witness, P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body,
            SkillOwnerId owner, P7ServerAuthorizationBoundary.NormalReceipt receipt, long runtimeTick) {
        requireMain();
        if (!normalPolicy() || stopped || suspended != null || bootstrapStopped && normalActivation.bootstrap()) {
            return P7ServerAuthorizationBoundary.NormalIngressDisposition.UNAVAILABLE;
        }
        var selection = normalSelections.get(owner);
        if (selection == null) {
            if (!normalActivation.bootstrap()) {
                return P7ServerAuthorizationBoundary.NormalIngressDisposition.NOT_READY;
            }
            reserveProvision(owner);
            return P7ServerAuthorizationBoundary.NormalIngressDisposition.NOT_READY;
        }
        if (!reserveCapture(actor, witness, source, body, owner, selection, runtimeTick)) {
            return P7ServerAuthorizationBoundary.NormalIngressDisposition.CAPACITY;
        }
        if (!receipt.defer()) { throw new IllegalStateException("Normal capture result join was already used"); }
        pending.normalReceipt = receipt;
        binding.wakeNormalCold();
        return P7ServerAuthorizationBoundary.NormalIngressDisposition.PENDING;
    }

    private boolean reserveProvision(SkillOwnerId owner) {
        if (stopped || suspended != null || bootstrapStopped || provisioningTicket != 0 || pending != null
                || pendingDiscard != null || normalSelections.size() >= 31 || ticketHighWater == Long.MAX_VALUE) {
            return false;
        }
        var request = new V0ProvisionRequest(authority, ticketHighWater + 1, owner);
        if (!mailbox.submitProvision(request)) { return false; }
        ticketHighWater = request.ticket();
        provisioningTicket = request.ticket();
        provisioningOwner = owner;
        binding.wakeNormalCold();
        return true;
    }

    private void acceptProvisionCompletion() {
        var result = mailbox.takeProvisionCompletion().orElse(null);
        if (result == null) { return; }
        boolean matches = result.authority() == authority && result.ticket() == provisioningTicket
                && result.owner().equals(provisioningOwner);
        if (!matches) { return; }
        provisioningTicket = 0;
        provisioningOwner = null;
        if (!stopped && suspended == null && !bootstrapStopped && result.result().ready()
                && normalSelections.size() < 31) {
            normalSelections.put(result.owner(), result.result().selection().orElseThrow());
        }
    }

    boolean reserveCapture(ServerPlayer actor, V0PlayWitness witness, P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body, SkillOwnerId owner, V0RuntimeCatalog.Selection selection,
            long runtimeTick) {
        requireMain();
        if (stopped || suspended != null || source == null) {
            return false;
        }
        if (attemptTick != runtimeTick) {
            Arrays.fill(attemptedAttributions, null);
            attemptTick = runtimeTick;
            attemptCount = 0;
        }
        var attribution = actor.getUUID();
        if (attemptCount == attemptedAttributions.length) {
            return false;
        }
        for (int index = 0; index < attemptCount; index++) {
            if (attemptedAttributions[index].equals(attribution)) {
                return false;
            }
        }
        attemptedAttributions[attemptCount++] = attribution;
        if (pending != null || pendingDiscard != null || ticketHighWater == Long.MAX_VALUE
                || runtimeTick > Long.MAX_VALUE - PREPARATION_TICKS) {
            return false;
        }
        var geometry = RootGeometry.sample(actor, normalPolicy());
        if (geometry == null || !geometry.loaded()) {
            return false;
        }
        var request = new V0CaptureRequest(authority, ticketHighWater + 1, owner, selection,
                runtimeTick + PREPARATION_TICKS, limits.maxManifestEntries());
        long rootManaFee = normalPolicy() ? normalActivation.manaFee()
                : binding.isZeroFeeSelection(selection) ? 0L : limits.positiveRootFee();
        var captured = new PendingCapture(request, actor, actor.connection, witness, source, body, body.source,
                geometry, rootManaFee);
        if (!mailbox.submit(request)) {
            return false;
        }
        ticketHighWater = request.ticket();
        pending = captured;
        return true;
    }

    void maintain(long runtimeTick, long legacyEventOrder) {
        requireMain();
        this.legacyEventOrder = legacyEventOrder;
        if (currentTick != runtimeTick) {
            currentTick = runtimeTick;
            dispatchedThisTick = 0;
        }
        applyReopenAcknowledgment();
        if (normalPolicy()) {
            if (suspended != null) {
                normalSelections.clear();
                provisioningTicket = 0;
                provisioningOwner = null;
            }
            acceptProvisionCompletion();
        }
        if (pending != null && (runtimeTick > pending.request.inclusivePreparationDeadlineTick()
                || suspended != null)) {
            // Retain only the timeout coordinate. The worker still owns the mailbox occupancy.
            if (suspended == null) {
                lastExpiredTicket = pending.request.ticket();
            }
            var expired = pending;
            pending = null;
            completeNormal(expired, suspended == null ? V0Admission.DEADLINE_EXPIRED : V0Admission.SLOT_STALE);
        }
        if (pendingDiscard != null && settleDiscard(pendingDiscard)) {
            pendingDiscard = null;
        }
        for (int index = 0; index < lineages.length; index++) {
            var lineage = lineages[index];
            if (lineage != null) {
                reconcileCarrierTerminal(lineage.rootBinding);
                reconcileCarrierTerminal(lineage.childBinding);
                if (lineage.releasing || suspended != null || runtimeTick > lineage.deadline
                        || lineage.logoutState == SkillRuntimeService.LogoutState.INVALID
                        || lineage.rootDone && (!lineage.childPublished || lineage.childDone)) {
                    settleLineage(index, lineage);
                }
            }
        }
    }

    V0CaptureCompletion takeCompletion() {
        requireMain();
        // One pending-discard cell must be available for any result that fails admission.
        return pendingDiscard == null ? mailbox.takeCompletion().orElse(null) : null;
    }

    V0Admission acceptCompletion(V0CaptureCompletion completion, long runtimeTick) {
        requireMain();
        if (completion.authority() != authority) {
            return V0Admission.SLOT_STALE;
        }
        var capture = pending;
        boolean matches = capture != null && capture.request.ticket() == completion.ticket();
        if (matches) {
            pending = null;
        }
        if (!(completion.result() instanceof V0RuntimeCatalog.Candidate candidate)) {
            if (matches) { completeNormal(capture, V0Admission.REJECTED); }
            return V0Admission.REJECTED;
        }
        if (accepting) {
            throw new IllegalStateException("Reentrant V0 completion acceptance");
        }
        accepting = true;
        boolean committed = false;
        int reservedCell = -1;
        long reservedLineage = 0;
        P11QualifiedSourceOwner.WorkReservation work = null;
        Lineage staged = null;
        boolean installationStarted = false;
        boolean sourceProved = false;
        boolean casStarted = false;
        boolean casRefused = false;
        Throwable primaryFailure = null;
        try {
            if (stopped || suspended != null) {
                return outcome(capture, matches, V0Admission.SLOT_STALE);
            }
            if (completion.ticket() == lastExpiredTicket
                    || (matches && runtimeTick > capture.request.inclusivePreparationDeadlineTick())) {
                return outcome(capture, matches, V0Admission.DEADLINE_EXPIRED);
            }
            if (!matches) {
                return outcome(capture, matches, V0Admission.SLOT_STALE);
            }
            if (!current(capture)) {
                return outcome(capture, matches, V0Admission.ACTOR_STALE);
            }
            var lease = candidate.lease();
            if (acceptedTick == runtimeTick || lease.programChargeBytes() <= 0
                    || lease.programChargeBytes() > MAX_LOGICAL_PROGRAM_BYTES
                    || lineageHighWater == Long.MAX_VALUE || instanceHighWater > Long.MAX_VALUE - 2
                    || carrierHighWater > Long.MAX_VALUE - 2
                    || runtimeTick >= Long.MAX_VALUE - limits.lineageDeadlineTicks()) {
                return outcome(capture, matches, V0Admission.CAPACITY);
            }
            int cell = availableCell(capture.actor.getUUID());
            if (cell < 0 || ownerQueueOrder == Long.MAX_VALUE) {
                return outcome(capture, matches, V0Admission.CAPACITY);
            }
            long queueOrder = ownerQueueOrder + 1;
            long nextLineage = lineageHighWater + 1;
            long dueTick = runtimeTick + 1;
            if (!queue.reserve(cell, nextLineage)) {
                throw new IllegalStateException("V0 lineage storage and queue reservation disagree");
            }
            reservedCell = cell;
            reservedLineage = nextLineage;
            if (!queue.publish(cell, nextLineage, V0P5Queue.ROOT_RELEASE, dueTick, legacyEventOrder, queueOrder)
                    || !queue.claimCleanup(cell, nextLineage, V0P5Queue.ROOT_LEASE_CLEANUP)) {
                throw new IllegalStateException("V0 root reservation could not reserve its fixed event/cleanup roles");
            }
            var lineage = new Lineage(this, cell, capture, lease, dueTick + limits.lineageDeadlineTicks(), nextLineage,
                    instanceHighWater + 1, carrierHighWater + 1);
            staged = lineage;
            work = runtime.reserveV0Work(capture.actor, limits.maxLineagesPerServer());
            if (work == null) {
                return outcome(capture, matches, V0Admission.WORK_UNAVAILABLE);
            }
            lineage.work = work;
            var reservation = authority.reserve(candidate);
            if (normalPolicy()) {
                if (bootstrapStopped && normalActivation.bootstrap() || !runtime.normalV0ManaAvailable(capture.actor)) {
                    return outcome(capture, matches, V0Admission.WORK_UNAVAILABLE);
                }
                var preparation = cooldowns.prepareV0PolicyAdmission(capture.geometry.level.getServer(), capture.actor,
                        normalActivation.key(), normalActivation.cooldownTicks(), runtimeTick, lineage.deadline,
                        limits.lineageDeadlineTicks(), lineage.cooldownReceipt, work);
                if (preparation instanceof P11CastCooldownService.Rejected rejected) {
                    capture.feedback = switch (rejected.reason()) {
                        case ACTIVE, PENDING, RECOVERY -> P7ServerAuthorizationBoundary.NormalFeedback.COOLDOWN_ACTIVE;
                        default -> P7ServerAuthorizationBoundary.NormalFeedback.UNAVAILABLE;
                    };
                    return outcome(capture, matches, V0Admission.WORK_UNAVAILABLE);
                }
                if (preparation instanceof P11CastCooldownService.Prepared prepared) {
                    lineage.cooldownPreparation = prepared;
                    installationStarted = true;
                    if (!cooldowns.installPending(prepared)) {
                        return outcome(capture, matches, V0Admission.WORK_UNAVAILABLE);
                    }
                    var ownSource = cooldowns.claimPendingSource(prepared, capture.source, capture.body, capture.sourceProof);
                    if (ownSource == null) { return outcome(capture, matches, V0Admission.ACTOR_STALE); }
                    capture.sourceProof = ownSource;
                }
            }
            if (!current(capture) || !work.qualifies(capture.actor) || suspended != null) {
                return outcome(capture, matches, V0Admission.ACTOR_STALE);
            }
            sourceProved = true;
            if (ownerQueueOrder != queueOrder - 1) {
                return outcome(capture, matches, V0Admission.CAPACITY);
            }
            if (lineage.cooldownReceipt != null) { lineage.cooldownReceipt.beginPublication(); }
            casStarted = true;
            switch (catalog.compareAndAccept(reservation, candidate)) {
                case CATALOG_BUSY:
                    casRefused = true;
                    return outcome(capture, matches, V0Admission.CATALOG_BUSY);
                case HEAD_STALE, INVALID_RESERVATION:
                    casRefused = true;
                    return outcome(capture, matches, V0Admission.REJECTED);
                case COMMITTED: break;
            }
            // The CAS linearized acceptance. These stores publish only preallocated cells.
            lineages[cell] = lineage;
            queue.activateReserved(cell);
            lineageHighWater = lineage.lineage.value();
            instanceHighWater += 2;
            carrierHighWater += 2;
            acceptedTick = runtimeTick;
            ownerQueueOrder = queueOrder;
            committed = true;
            if (lineage.cooldownReceipt != null) { lineage.cooldownReceipt.published(); }
            return outcome(capture, matches, V0Admission.ACCEPTED);
        } catch (RuntimeException | Error primary) {
            primaryFailure = primary;
            throw primary;
        } finally {
            try {
                if (!committed) {
                    Throwable cleanupFailure = null;
                    try {
                        if (staged != null && staged.cooldownReceipt != null) {
                            if (!installationStarted || sourceProved && (!casStarted || casRefused)) {
                                staged.cooldownReceipt.revoke();
                            } else { staged.cooldownReceipt.unknown(); }
                            if (staged.cooldownPreparation != null) { staged.cooldownPreparation.settle(); }
                        }
                    } catch (RuntimeException | Error failure) {
                        cleanupFailure = failure;
                    }
                    try {
                        undoReservation(reservedCell, reservedLineage, candidate, work);
                    } catch (RuntimeException | Error failure) {
                        if (cleanupFailure == null) { cleanupFailure = failure; }
                        else { suppress(cleanupFailure, failure); }
                    }
                    if (cleanupFailure != null) { throwUnchecked(cleanupFailure); }
                }
            } catch (RuntimeException | Error cleanup) {
                if (primaryFailure == null) { primaryFailure = cleanup; throw cleanup; }
                suppress(primaryFailure, cleanup);
            } finally {
                accepting = false;
                try {
                    handoffRetirementIfSafe();
                } catch (RuntimeException | Error cleanup) {
                    if (primaryFailure == null) { primaryFailure = cleanup; throw cleanup; }
                    suppress(primaryFailure, cleanup);
                } finally {
                    if (matches) {
                        try { completeNormal(capture, capture.outcome); }
                        catch (RuntimeException | Error delivery) {
                            if (primaryFailure == null) { throw delivery; }
                            suppress(primaryFailure, delivery);
                        }
                    }
                }
            }
        }
    }

    private static V0Admission outcome(PendingCapture capture, boolean matches, V0Admission result) {
        if (matches) { capture.outcome = result; }
        return result;
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            try { primary.addSuppressed(secondary); }
            catch (RuntimeException | Error suppressionFailure) { /* Preserve the original primary. */ }
        }
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) { throw runtimeFailure; }
        throw (Error) failure;
    }

    private static void completeNormal(PendingCapture capture, V0Admission result) {
        var receipt = capture.normalReceipt;
        capture.normalReceipt = null;
        if (receipt == null || result == null) { return; }
        var disposition = switch (result) {
            case ACCEPTED -> P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED;
            case REJECTED -> P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            default -> P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
        };
        receipt.complete(capture.geometry.level.getServer(), capture.actor, disposition, capture.feedback);
    }

    int readyEvent(long runtimeTick, boolean v0BudgetAvailable, RuntimeEvent eligibleLegacyHead) {
        requireMain();
        return stopped || suspended != null || currentDispatch != null
                ? -1 : queue.ready(runtimeTick, v0BudgetAvailable, eligibleLegacyHead);
    }

    DispatchClaim claimReadyEvent(int eventCell, long runtimeTick) {
        requireMain();
        if (stopped || suspended != null || currentDispatch != null || eventCell < 0) {
            return null;
        }
        int lineageSlot = queue.lineageSlot(eventCell);
        var lineage = lineageSlot < lineages.length ? lineages[lineageSlot] : null;
        if (lineage == null || lineage.releasing) {
            return null;
        }
        if (runtimeTick > lineage.deadline || !catalog.usable(authority, lineage.lease.exactLease())) {
            settleLineage(lineageSlot, lineage);
            return null;
        }
        long sequence = queue.sequence(eventCell);
        if (sequence <= 0) {
            return null;
        }
        int kind = queue.role(eventCell);
        var instance = kind < V0P5Queue.CHILD_RELEASE ? lineage.root : lineage.child;
        var claim = new DispatchClaim(eventCell, lineageSlot, lineage.lineage,
                new V0EventId(sequence), instance, kind, lineage.deadline);
        if (!queue.claim(eventCell, lineage.lineage.value(), sequence, runtimeTick)) {
            return null;
        }
        currentDispatch = claim;
        return claim;
    }

    boolean finishClaim(DispatchClaim claim) {
        requireMain();
        if (claim == null || currentDispatch != claim
                || !queue.finish(claim.eventCell, claim.lineage.value(), claim.event.value())) {
            return false;
        }
        currentDispatch = null;
        var lineage = lineages[claim.lineageSlot];
        if (lineage != null && lineage.lineage == claim.lineage && lineage.releasing) {
            settleLineage(claim.lineageSlot, lineage);
        }
        handoffRetirementIfSafe();
        return true;
    }

    boolean authorizeChild(int lineageSlot, V0LineageId lineage, V0EventId causingHit) {
        requireMain();
        return !stopped && suspended == null && queue.reserveChild(lineageSlot, lineage.value(), causingHit.value());
    }

    boolean publishFollowUp(int lineageSlot, V0LineageId lineage, int role, long dueTick,
            long runtimeTick, long lastReservedLegacyId) {
        requireMain();
        if (stopped || suspended != null || ownerQueueOrder == Long.MAX_VALUE
                || (role == V0P5Queue.CHILD_RELEASE && dueTick <= runtimeTick)) {
            return false;
        }
        var current = lineageSlot < 0 || lineageSlot >= lineages.length ? null : lineages[lineageSlot];
        if (current == null || !current.lineage.equals(lineage) || current.releasing || dueTick > current.deadline) {
            return false;
        }
        long sequence = ownerQueueOrder + 1;
        if (!queue.publish(lineageSlot, lineage.value(), role, dueTick, lastReservedLegacyId, sequence)) {
            return false;
        }
        ownerQueueOrder = sequence;
        return true;
    }

    void beginNormalLogout(SkillRuntimeService.NormalLogoutScope scope,
            ServerGamePacketListenerImpl listener, ServerPlayer actor) {
        requireMain();
        Objects.requireNonNull(scope, "logout scope");
        if (pending != null && pending.actor == actor && pending.listener == listener) {
            // The original cold request still occupies its mailbox until its owner completes.
            pending = null;
        }
        for (var lineage : lineages) {
            if (lineage != null && lineage.logoutState == SkillRuntimeService.LogoutState.ONLINE
                    && lineage.capture.actor == actor && lineage.capture.listener == listener
                    && actor.connection == listener && listener.player == actor && usable(lineage)
                    && lineage.work != null && lineage.work.qualifies(actor)) {
                lineage.logoutScope = scope;
                lineage.logoutState = SkillRuntimeService.LogoutState.IN_PROGRESS;
            }
        }
    }

    void endNormalLogout(SkillRuntimeService.NormalLogoutScope scope, boolean proved) {
        requireMain();
        if (scope == null) { return; }
        for (var lineage : lineages) {
            if (lineage != null && lineage.logoutScope == scope) {
                lineage.logoutState = SkillRuntimeService.LogoutState.INVALID;
            }
        }
        boolean evaluated = false;
        try {
            if (proved) {
                for (var lineage : lineages) {
                    if (lineage != null && lineage.logoutScope == scope && usable(lineage)
                            && lineage.work != null && lineage.work.qualifies(lineage.capture.actor)) {
                        lineage.logoutState = SkillRuntimeService.LogoutState.COMPLETE;
                    }
                }
            }
            evaluated = true;
        } finally {
            for (var lineage : lineages) {
                if (lineage != null && lineage.logoutScope == scope) {
                    if (!evaluated) { lineage.logoutState = SkillRuntimeService.LogoutState.INVALID; }
                    lineage.logoutScope = null;
                }
            }
        }
    }

    void resumeObservedHits(long tick) {
        requireMain();
        for (var lineage : lineages) {
            if (lineage != null) {
                resumeObservedHit(lineage, lineage.rootBinding, lineage.rootImpact, tick);
                resumeObservedHit(lineage, lineage.childBinding, lineage.childImpact, tick);
            }
        }
    }

    private void resumeObservedHit(Lineage lineage, SkillRuntimeService.V0CarrierBinding carrier,
            Impact impact, long tick) {
        if (impact == null || carrier.claimed() || carrier.closing()) {
            return;
        }
        var projectile = carrier.carrier();
        var level = lineage.capture.geometry.level;
        if (tick == impact.observedTick && tick == currentTick && projectile != null
                && projectile.level() == level && projectile.isAddedToLevel() && !projectile.isRemoved()
                && level.getEntity(projectile.getId()) == projectile
                && P9V0LoadedBlocks.dryCell(level, BlockPos.containing(impact.location))
                && qualification(lineage) == V0WorkQualification.EXECUTABLE
                && eligibleTarget(carrier, impact.target) && publishImpact(carrier, lineage, tick)) {
            carrier.resumeImpact();
            return;
        }
        closeCarrier(carrier, V0TerminalReason.OWNER_INVALIDATED);
    }

    void nativeCarrierTerminal(SkillRuntimeService.V0CarrierBinding carrier, V0TerminalReason reason) {
        requireMain();
        var lineage = exactLineage(carrier);
        if (lineage == null) { return; }
        if (carrier.child) {
            if (lineage.childTerminal != null) { return; }
            lineage.childTerminal = reason;
            lineage.childDone = true;
            lineage.childImpact = null;
        } else {
            if (lineage.rootTerminal != null) { return; }
            lineage.rootTerminal = reason;
            lineage.rootDone = true;
            lineage.rootImpact = null;
        }
    }

    private void reconcileCarrierTerminal(SkillRuntimeService.V0CarrierBinding carrier) {
        var lineage = exactLineage(carrier);
        var projectile = carrier.carrier();
        if (lineage == null || !carrier.closing() || projectile == null) { return; }
        var dispatch = currentDispatch;
        int releaseRole = carrier.child ? V0P5Queue.CHILD_RELEASE : V0P5Queue.ROOT_RELEASE;
        if (dispatch != null && dispatch.lineage == carrier.lineage && dispatch.role == releaseRole) {
            // Synchronous close during native add does not prove that insertion has finished.
            return;
        }
        if (!projectile.isRemoved() && (projectile.v0EverInserted() || projectile.isAddedToLevel())) {
            return;
        }
        int hitCleanup = carrier.child ? 3 : 0;
        if (!queue.settleCleanup(carrier.cell, carrier.lineage.value(), hitCleanup)
                || !queue.settleCleanup(carrier.cell, carrier.lineage.value(), hitCleanup + 1)) {
            throw new IllegalStateException("V0 terminal carrier lost its exact cleanup ownership");
        }
        carrier.settleClosed();
    }

    private void closeCarrier(SkillRuntimeService.V0CarrierBinding carrier, V0TerminalReason reason) {
        if (exactLineage(carrier) == null) { return; }
        var projectile = carrier.carrier();
        if (projectile == null) {
            if (!carrier.closing()) {
                nativeCarrierTerminal(carrier, reason);
                carrier.settleClosed();
            }
            return;
        }
        projectile.closeV0(reason);
        reconcileCarrierTerminal(carrier);
    }

    V0CatalogAuthority authority() { return authority; }
    int presentationOfferLimit() { return 4 * limits.maxLineagesPerServer(); }
    int presentationRecipientLimit() { return limits.presentationRecipientsPerOffer(); }
    int maxCarrierAge() { return limits.projectileMaxAgeTicks(); }
    int maxCarrierRange() { return limits.projectileMaxRangeBlocks(); }

    private Lineage exactLineage(SkillRuntimeService.V0CarrierBinding carrier) {
        if (!authority.isOwnerThread() || carrier.cell < 0 || carrier.cell >= lineages.length) { return null; }
        var current = lineages[carrier.cell];
        return current != null && current.lineage == carrier.lineage
                && (carrier.child ? current.childBinding : current.rootBinding) == carrier ? current : null;
    }

    boolean ownsNativeCarrier(SkillRuntimeService.V0CarrierBinding carrier) {
        var lineage = exactLineage(carrier);
        return lineage != null && usable(lineage);
    }

    private boolean usable(Lineage lineage) {
        var server = lineage.capture.geometry.level.getServer();
        return !stopped && suspended == null && !lineage.releasing && currentTick <= lineage.deadline
                && runtime.v0Active(this, server) && catalog.usable(authority, lineage.lease.exactLease());
    }

    private ServerPlayer onlineActor(Lineage lineage) {
        var capture = lineage.capture;
        return lineage.logoutState == SkillRuntimeService.LogoutState.ONLINE
                && capture.actor.connection == capture.listener && capture.actor.serverLevel() == capture.geometry.level
                && runtime.v0CurrentPlay(this, authority, capture.geometry.level.getServer(), capture.witness, capture.actor)
                ? capture.actor : null;
    }

    ServerPlayer onlineActor(DispatchClaim claim, ServerPlayer exactActor) {
        var lineage = lineages[claim.lineageSlot];
        return currentDispatch == claim && lineage != null && lineage.capture.actor == exactActor
                ? onlineActor(lineage) : null;
    }

    ServerPlayer qualifiedNativeActor(SkillRuntimeService.V0CarrierBinding carrier) {
        var lineage = exactLineage(carrier);
        return lineage != null && qualification(lineage) == V0WorkQualification.EXECUTABLE
                ? lineage.capture.actor : null;
    }

    V0WorkQualification nativeQualification(SkillRuntimeService.V0CarrierBinding carrier) {
        var lineage = exactLineage(carrier);
        return lineage == null ? V0WorkQualification.INVALID : qualification(lineage);
    }

    private V0WorkQualification qualification(Lineage lineage) {
        var capture = lineage.capture;
        var actor = capture.actor;
        var level = capture.geometry.level;
        var server = level.getServer();
        if (!usable(lineage) || lineage.work == null || actor.serverLevel() != level
                || server.getLevel(level.dimension()) != level || actor.getHealth() <= 0.0F
                || lineage.logoutState == SkillRuntimeService.LogoutState.INVALID) {
            return V0WorkQualification.INVALID;
        }
        if (lineage.logoutState == SkillRuntimeService.LogoutState.IN_PROGRESS) {
            return V0WorkQualification.PENDING_NATIVE_CLEANUP;
        }
        if (!lineage.work.qualifies(actor)) { return V0WorkQualification.INVALID; }
        if (lineage.logoutState == SkillRuntimeService.LogoutState.COMPLETE || onlineActor(lineage) == actor) {
            return V0WorkQualification.EXECUTABLE;
        }
        var connection = capture.listener.getConnection();
        return actor.connection == capture.listener && !actor.hasDisconnected() && !actor.isRemoved()
                && capture.listener.player == actor && connection.getPacketListener() == capture.listener
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && !connection.isConnecting()
                && !connection.isConnected() ? V0WorkQualification.PENDING_NATIVE_CLEANUP : V0WorkQualification.INVALID;
    }

    boolean presentationContextCurrent(DispatchClaim claim, long tick) {
        var lineage = lineages[claim.lineageSlot];
        return authority.isOwnerThread() && !stopped && suspended == null && currentDispatch == claim
                && currentTick == tick && lineage != null && claim.lineage == lineage.lineage
                && runtime.v0Active(this, lineage.capture.geometry.level.getServer());
    }

    boolean frameActive(DispatchClaim claim, SkillRuntimeService.V0CarrierBinding carrier,
            long runtimeTick, ServerPlayer exactA) {
        var lineage = exactLineage(carrier);
        return currentDispatch == claim && currentTick == runtimeTick && lineage != null
                && lineage.capture.actor == exactA && !carrier.closing()
                && qualification(lineage) == V0WorkQualification.EXECUTABLE;
    }

    boolean eligibleTarget(SkillRuntimeService.V0CarrierBinding carrier, LivingEntity target) {
        var lineage = exactLineage(carrier);
        if (lineage == null || lineage.attribution.equals(target.getUUID())) { return false; }
        var level = lineage.capture.geometry.level;
        return P9StarterProjectile.eligibleV0Target(level, target)
                && (!(target instanceof ServerPlayer player)
                        || runtime.v0TargetPlay(this, authority, level.getServer(), player));
    }

    boolean reserveNativeCarrier(SkillRuntimeService.V0CarrierBinding carrier, P9StarterProjectile projectile) {
        var lineage = exactLineage(carrier);
        var claim = currentDispatch;
        if (lineage == null || claim == null || claim.lineage != lineage.lineage
                || claim.role != (carrier.child ? V0P5Queue.CHILD_RELEASE : V0P5Queue.ROOT_RELEASE)
                || projectile.level() != lineage.capture.geometry.level || !usable(lineage)) {
            return false;
        }
        int hitCleanup = carrier.child ? 3 : 0;
        long exact = carrier.lineage.value();
        if (!queue.claimCleanup(carrier.cell, exact, hitCleanup)) {
            throw new IllegalStateException("V0 exact hit cleanup reservation was already assigned");
        }
        if (!queue.claimCleanup(carrier.cell, exact, hitCleanup + 1)) {
            queue.settleCleanup(carrier.cell, exact, hitCleanup);
            throw new IllegalStateException("V0 exact terminal cleanup reservation was already assigned");
        }
        return true;
    }

    private boolean publishNativeCreate(SkillRuntimeService.V0CarrierBinding carrier, Lineage lineage) {
        if (!usable(lineage)) { return false; }
        int role = carrier.child ? V0P5Queue.CHILD_CREATE : V0P5Queue.ROOT_CREATE;
        return publishFollowUp(carrier.cell, carrier.lineage, role, currentTick, currentTick,
                runtime.v0LastReservedRuntimeEvent(this, lineage.capture.geometry.level.getServer()));
    }

    V0ClaimDisposition captureNativeImpact(SkillRuntimeService.V0CarrierBinding carrier, EntityHitResult hit) {
        var lineage = exactLineage(carrier);
        if (lineage == null || currentTick >= lineage.deadline || !(hit.getEntity() instanceof LivingEntity target)
                || !eligibleTarget(carrier, target) || (carrier.child ? lineage.childImpact : lineage.rootImpact) != null) {
            return V0ClaimDisposition.REJECTED;
        }
        var permission = qualification(lineage);
        if (permission != V0WorkQualification.PENDING_NATIVE_CLEANUP && permission != V0WorkQualification.EXECUTABLE) {
            return V0ClaimDisposition.REJECTED;
        }
        var impact = new Impact(target, hit.getLocation(), currentTick);
        if (permission == V0WorkQualification.EXECUTABLE && !publishImpact(carrier, lineage, currentTick)) {
            return V0ClaimDisposition.REJECTED;
        }
        if (carrier.child) { lineage.childImpact = impact; } else { lineage.rootImpact = impact; }
        return permission == V0WorkQualification.PENDING_NATIVE_CLEANUP
                ? V0ClaimDisposition.PENDING : V0ClaimDisposition.QUEUED;
    }

    private boolean publishImpact(SkillRuntimeService.V0CarrierBinding carrier, Lineage lineage, long tick) {
        int role = carrier.child ? V0P5Queue.CHILD_HIT : V0P5Queue.ROOT_HIT;
        return tick < lineage.deadline && publishFollowUp(carrier.cell, carrier.lineage, role, tick + 1, tick,
                runtime.v0LastReservedRuntimeEvent(this, lineage.capture.geometry.level.getServer()));
    }

    boolean hasDispatchBudget(long tick) {
        return currentTick == tick && dispatchedThisTick < 6 * limits.maxLineagesPerServer();
    }

    void dispatch(int eventCell, long tick, V0P6StatementAdapter adapter) {
        var claim = claimReadyEvent(eventCell, tick);
        if (claim == null) { return; }
        dispatchedThisTick++;
        var lineage = lineages[claim.lineageSlot];
        try {
            switch (claim.role) {
                case V0P5Queue.ROOT_RELEASE -> dispatchRelease(claim, lineage, lineage.rootBinding, adapter);
                case V0P5Queue.CHILD_RELEASE -> dispatchRelease(claim, lineage, lineage.childBinding, adapter);
                case V0P5Queue.ROOT_HIT -> dispatchHit(claim, lineage, lineage.rootBinding, lineage.rootImpact, adapter);
                case V0P5Queue.CHILD_HIT -> dispatchHit(claim, lineage, lineage.childBinding, lineage.childImpact, adapter);
                case V0P5Queue.ROOT_CREATE, V0P5Queue.CHILD_CREATE -> { /* adopted creation handlers are empty */ }
                default -> throw new IllegalStateException("Unknown reserved V0 event role");
            }
        } catch (RuntimeException | Error primary) {
            try {
                if (claim.role == V0P5Queue.ROOT_RELEASE && lineage.cooldownReceipt != null) {
                    lineage.cooldownReceipt.unknown();
                }
                lineage.releasing = true;
                finishChecked(claim);
            } catch (RuntimeException | Error cleanup) {
                // Native/P6 original is the primary failure.
            }
            throw primary;
        }
        finishChecked(claim);
        if (lineage.rootDone && (!lineage.childPublished || lineage.childDone)) {
            settleLineage(claim.lineageSlot, lineage);
        }
    }

    private void finishChecked(DispatchClaim claim) {
        if (!finishClaim(claim)) { throw new IllegalStateException("V0 dispatch lost its exact claim"); }
    }

    private V0RuntimeCatalog.Unit unit(Lineage lineage, boolean child) {
        var root = lineage.lease.exactLease().root();
        return child ? root.hitCreate().orElseThrow().target() : root;
    }

    private V0SpawnGeometry geometry(Lineage lineage, boolean child) {
        if (child) { return lineage.childSpawn; }
        var root = lineage.capture.geometry;
        return new V0SpawnGeometry(root.level, root.origin, root.directionXQ15, root.directionYQ15,
                root.directionZQ15, LAUNCH_STEP_MILLI);
    }

    private void dispatchRelease(DispatchClaim claim, Lineage lineage, SkillRuntimeService.V0CarrierBinding carrier,
            V0P6StatementAdapter adapter) {
        var geometry = geometry(lineage, carrier.child);
        long fee = carrier.child ? 0L : lineage.capture.rootManaFee;
        if (geometry == null || qualification(lineage) != V0WorkQualification.EXECUTABLE
                || requestHighWater == Long.MAX_VALUE || (fee > 0L && onlineActor(lineage) == null)) {
            closeCarrier(carrier, V0TerminalReason.REJECTED_BEFORE_COMMIT);
            return;
        }
        var origin = geometry.origin();
        var command = new P6RuntimeExecutionBridge.SpawnProjectileInvocation(SPAWN, ++requestHighWater,
                claim.event.value(), geometry.level().dimension().location(), origin.x, origin.y, origin.z,
                geometry.directionXQ15(), geometry.directionYQ15(), geometry.directionZQ15(), 0,
                fee);
        var frame = new V0NativeCallFrame(this, claim, carrier, lineage.work, lineage.capture.actor,
                geometry, null, null, unit(lineage, carrier.child), command, -1, currentTick);
        var receipt = invoke(adapter, frame, command);
        if (receipt.terminal() == P6RuntimeExecutionBridge.V0Terminal.CONFIRMED_APPLIED
                && carrier.opened() && publishNativeCreate(carrier, lineage)) {
            P11CastCooldownService.ArmPreparation arm = null;
            if (!carrier.child && lineage.cooldownPreparation != null) {
                if (lineage.cooldownReceipt.mayArm()) {
                    arm = cooldowns.prepareArm(lineage.cooldownPreparation, lineage.work,
                            lineage.capture.actor, currentTick);
                }
                if (arm == null || !lineage.cooldownReceipt.mayArm()
                        || !carrier.opened() || qualification(lineage) != V0WorkQualification.EXECUTABLE) {
                    lineage.cooldownReceipt.revoke();
                    closeCarrier(carrier, V0TerminalReason.FAULT);
                    lineage.cooldownPreparation.settle();
                    return;
                }
            }
            // All source, clock and allocation work is complete. These two stores publish
            // the one true release; no observer can execute OPEN without its ARM fact.
            if (arm != null) { lineage.cooldownReceipt.armed(arm.releasedAt()); }
            carrier.publishOpen();
            if (arm != null) { cooldowns.completeArm(arm); }
            return;
        }
        if (!carrier.child && lineage.cooldownReceipt != null) {
            if (receipt.terminal() == P6RuntimeExecutionBridge.V0Terminal.OUTCOME_UNKNOWN) {
                lineage.cooldownReceipt.unknown();
            } else { lineage.cooldownReceipt.revoke(); }
        }
        closeCarrier(carrier, switch (receipt.terminal()) {
            case REJECTED_BEFORE_COMMIT -> V0TerminalReason.REJECTED_BEFORE_COMMIT;
            case CONFIRMED_NOT_APPLIED, COMPENSATED_NO_PRIMARY_MUTATION -> V0TerminalReason.CONFIRMED_NOT_APPLIED;
            case CONFIRMED_APPLIED, CONFIRMED_APPLIED_WITH_FAILURE -> V0TerminalReason.FAULT;
            case OUTCOME_UNKNOWN -> V0TerminalReason.OUTCOME_UNKNOWN;
        });
        if (!carrier.child && lineage.cooldownPreparation != null) { lineage.cooldownPreparation.settle(); }
    }

    private void dispatchHit(DispatchClaim claim, Lineage lineage, SkillRuntimeService.V0CarrierBinding carrier,
            Impact impact, V0P6StatementAdapter adapter) {
        var unit = unit(lineage, carrier.child);
        var damage = unit.hitDamage().orElse(null);
        var geometry = geometry(lineage, carrier.child);
        if (impact == null || geometry == null || !carrier.claimed()
                || qualification(lineage) != V0WorkQualification.EXECUTABLE
                || !eligibleTarget(carrier, impact.target)) {
            closeCarrier(carrier, V0TerminalReason.OWNER_INVALIDATED);
            return;
        }
        if (damage == null) {
            closeCarrier(carrier, V0TerminalReason.NATURAL_HIT);
            return;
        }
        if (requestHighWater == Long.MAX_VALUE) {
            closeCarrier(carrier, V0TerminalReason.REJECTED_BEFORE_COMMIT);
            return;
        }
        var command = new P6RuntimeExecutionBridge.DamageInvocation(DAMAGE, ++requestHighWater, claim.event.value(),
                impact.target.getUUID(), Math.multiplyExact((long) damage.damageHp(), 1_000L), 0L);
        var frame = new V0NativeCallFrame(this, claim, carrier, lineage.work, lineage.capture.actor,
                geometry, impact.target, impact.location, unit, command, 0, currentTick);
        var receipt = invoke(adapter, frame, command);
        if (!carrier.child && receipt.terminal() == P6RuntimeExecutionBridge.V0Terminal.CONFIRMED_APPLIED
                && unit.hitCreate().isPresent() && onlineActor(lineage) != null) {
            scheduleChild(claim, lineage, impact, geometry);
        }
        closeCarrier(carrier, switch (receipt.terminal()) {
            case CONFIRMED_APPLIED, CONFIRMED_NOT_APPLIED -> V0TerminalReason.NATURAL_HIT;
            case REJECTED_BEFORE_COMMIT -> V0TerminalReason.REJECTED_BEFORE_COMMIT;
            case OUTCOME_UNKNOWN -> V0TerminalReason.OUTCOME_UNKNOWN;
            case CONFIRMED_APPLIED_WITH_FAILURE, COMPENSATED_NO_PRIMARY_MUTATION -> V0TerminalReason.FAULT;
        });
    }

    private void scheduleChild(DispatchClaim claim, Lineage lineage, Impact impact, V0SpawnGeometry parent) {
        if (!usable(lineage) || currentTick >= lineage.deadline || !authorizeChild(claim.lineageSlot, claim.lineage, claim.event)) {
            return;
        }
        var direction = new Vec3(parent.directionXQ15(), parent.directionYQ15(), parent.directionZQ15()).normalize();
        var origin = impact.location.add(direction.scale(0.10));
        lineage.childSpawn = new V0SpawnGeometry(parent.level(), origin, parent.directionXQ15(), parent.directionYQ15(),
                parent.directionZQ15(), parent.launchStepMilli());
        lineage.childPublished = publishFollowUp(claim.lineageSlot, claim.lineage, V0P5Queue.CHILD_RELEASE,
                currentTick + 1, currentTick, runtime.v0LastReservedRuntimeEvent(this, parent.level().getServer()));
        if (!lineage.childPublished) { lineage.childSpawn = null; }
    }

    private static P6RuntimeExecutionBridge.V0TerminalReceipt invoke(V0P6StatementAdapter adapter,
            V0NativeCallFrame frame, P6RuntimeExecutionBridge.Invocation command) {
        return adapter.execute(frame.actor(), command, frame.statementOrdinal(), new V0P6StatementGuard(frame),
                adapter.nativeHandoff(frame));
    }

    synchronized void invalidateForReload() {
        if (!stopped) {
            suspended = catalog.suspend(authority);
            installedReload = null;
            reloadSource = null;
        }
    }

    boolean ownsReloadRegistries(RegistryAccess registries) {
        return registries.registry(Registries.DIMENSION_TYPE).orElse(null) == dimensionTypes
                && registries.registry(Registries.LEVEL_STEM).orElse(null) == levelStems;
    }

    synchronized void recordReloadSource(ReloadableServerResources resources) {
        if (!stopped && suspended != null) {
            reloadSource = new WeakReference<>(Objects.requireNonNull(resources, "reload resources"));
            installedReload = null;
        }
    }

    synchronized void recordInstalledReload(ReloadableServerResources installed) {
        requireMain();
        if (reloadSource != null && reloadSource.get() == installed) {
            installedReload = suspended;
            reloadSource = null;
        }
    }

    private synchronized void applyReopenAcknowledgment() {
        if (stopped || suspended == null || installedReload != suspended) {
            return;
        }
        if (mailbox.takeRecoveryAcknowledgment(suspended)) {
            installedReload = null;
            suspended = null;
        } else if (pending == null && pendingDiscard == null && !hasLiveLineages()) {
            mailbox.handoffRecovery(suspended);
        }
    }

    private boolean hasLiveLineages() {
        for (var lineage : lineages) {
            if (lineage != null) {
                return true;
            }
        }
        return false;
    }

    void stop() {
        stop(V0TerminalReason.STOP);
    }

    void stop(V0TerminalReason reason) {
        requireMain();
        boolean firstClose;
        synchronized (this) {
            firstClose = !stopped;
            if (firstClose) {
                suspended = catalog.suspend(authority);
                installedReload = null;
                reloadSource = null;
                stopReason = Objects.requireNonNull(reason, "stop reason");
                stopped = true;
            }
        }
        if (firstClose) {
            mailbox.fence();
            binding.close();
            // The original recipient is no longer current; no completion may move to a successor.
            pending = null;
            normalSelections.clear();
            provisioningTicket = 0;
            provisioningOwner = null;
            mailbox.takeProvisionCompletion();
            mailbox.takeBootstrapCleanupCompletion();
            var outstanding = mailbox.takeCompletion().orElse(null);
            if (outstanding != null && outstanding.result() instanceof V0RuntimeCatalog.Candidate candidate) {
                undoCandidate(candidate, null);
            }
        }
        maintain(Long.MAX_VALUE, legacyEventOrder);
        handoffRetirementIfSafe();
    }

    private void handoffRetirementIfSafe() {
        if (stopped && !retirementSent && !accepting && currentDispatch == null
                && pendingDiscard == null && !hasLiveLineages()) {
            mailbox.handoffRetirement();
            retirementSent = true;
        }
    }

    private boolean current(PendingCapture capture) {
        var actor = capture.actor;
        return !actor.isRemoved() && actor.isAlive() && actor.connection == capture.listener
                && actor.serverLevel() == capture.geometry.level
                && runtime.currentV0Actor(this, authority, capture.witness, capture.source, capture.body,
                        capture.sourceProof, actor)
                && capture.geometry.loaded();
    }

    private int availableCell(UUID attribution) {
        int empty = -1;
        int active = 0;
        for (int index = 0; index < lineages.length; index++) {
            var lineage = lineages[index];
            if (lineage == null) {
                if (empty < 0) {
                    empty = index;
                }
            } else if (lineage.attribution.equals(attribution)) {
                active++;
            }
        }
        return active < limits.maxLineagesPerAttribution() ? empty : -1;
    }

    private void undoReservation(int cell, long lineage, V0RuntimeCatalog.Candidate candidate,
            P11QualifiedSourceOwner.WorkReservation work) {
        Throwable failure = null;
        try {
            if (cell >= 0 && !queue.rollback(cell, lineage)) {
                throw new IllegalStateException("V0 provisional queue reservation could not be rolled back");
            }
        } catch (RuntimeException | Error primary) {
            failure = primary;
        }
        try {
            undoCandidate(candidate, work);
        } catch (RuntimeException | Error secondary) {
            if (failure == null) { failure = secondary; }
            else { suppress(failure, secondary); }
        }
        if (failure != null) { throwUnchecked(failure); }
    }

    private void undoCandidate(V0RuntimeCatalog.Candidate candidate,
            P11QualifiedSourceOwner.WorkReservation work) {
        Throwable failure = null;
        try {
            if (work != null) {
                work.release();
            }
        } catch (RuntimeException | Error primary) {
            failure = primary;
        }
        try {
            if (!settleDiscard(candidate)) {
                if (pendingDiscard != null && pendingDiscard != candidate) {
                    throw new IllegalStateException("V0 provisional cleanup capacity is exhausted");
                }
                pendingDiscard = candidate;
            }
        } catch (RuntimeException | Error secondary) {
            if (failure == null) { failure = secondary; }
            else { suppress(failure, secondary); }
        }
        if (failure != null) { throwUnchecked(failure); }
    }

    private boolean settleDiscard(V0RuntimeCatalog.Candidate candidate) {
        return switch (catalog.discard(authority, candidate)) {
            case RELEASED, STALE_OR_RELEASED -> true;
            case CATALOG_BUSY, FENCED -> false;
        };
    }

    private void settleLineage(int index, Lineage lineage) {
        lineage.releasing = true;
        long exact = lineage.lineage.value();
        queue.fence(index, exact);
        var reason = stopped ? stopReason : suspended != null ? V0TerminalReason.RELOAD
                : currentTick > lineage.deadline ? V0TerminalReason.DEADLINE_EXPIRED
                : V0TerminalReason.OWNER_INVALIDATED;
        try {
            closeCarrier(lineage.rootBinding, reason);
        } catch (RuntimeException | Error primary) {
            try {
                closeCarrier(lineage.childBinding, reason);
            } catch (RuntimeException | Error ignored) {
                // The first native cleanup failure keeps its identity.
            }
            throw primary;
        }
        closeCarrier(lineage.childBinding, reason);
        if (!queue.onlyLeaseCleanupRemains(index, exact)) {
            return;
        }
        if (lineage.cooldownReceipt != null) {
            if (lineage.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING
                    || lineage.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.UNPUBLISHED) {
                lineage.cooldownReceipt.revoke();
            }
            if (lineage.cooldownPreparation != null) { lineage.cooldownPreparation.settle(); }
        }
        if (lineage.work != null) {
            lineage.work.release();
            lineage.work = null;
        }
        if (lineage.release == null) {
            lineage.release = authority.permitRelease(lineage.lease.exactLease());
        }
        switch (catalog.release(lineage.release, lineage.lease.exactLease())) {
            case RELEASED, STALE_OR_RELEASED -> {
                if (!queue.settleCleanup(index, exact, V0P5Queue.ROOT_LEASE_CLEANUP)
                        || !queue.close(index, exact)) {
                    throw new IllegalStateException("V0 final lineage cleanup lost its exact queue owner");
                }
                lineages[index] = null;
            }
            case CATALOG_BUSY, FENCED -> { /* The fixed original cleanup row remains occupied. */ }
        }
    }

    private void requireMain() {
        if (!authority.isOwnerThread()) {
            throw new IllegalStateException("Wrong thread for V0 P5 slot");
        }
    }

    private static final class PendingCapture {
        private final V0CaptureRequest request;
        private final ServerPlayer actor;
        private final ServerGamePacketListenerImpl listener;
        private final V0PlayWitness witness;
        private final P11QualifiedSourceOwner source;
        private final P11QualifiedSourceOwner.Body body;
        private P11ReceiptLedger.Source sourceProof;
        private final RootGeometry geometry;
        private final long rootManaFee;
        private P7ServerAuthorizationBoundary.NormalReceipt normalReceipt;
        private P7ServerAuthorizationBoundary.NormalFeedback feedback =
                P7ServerAuthorizationBoundary.NormalFeedback.UNAVAILABLE;
        private V0Admission outcome;

        private PendingCapture(V0CaptureRequest request, ServerPlayer actor,
                ServerGamePacketListenerImpl listener, V0PlayWitness witness,
                P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body,
                P11ReceiptLedger.Source sourceProof, RootGeometry geometry, long rootManaFee) {
            this.request = request;
            this.actor = actor;
            this.listener = listener;
            this.witness = witness;
            this.source = source;
            this.body = body;
            this.sourceProof = sourceProof;
            this.geometry = geometry;
            this.rootManaFee = rootManaFee;
        }
    }

    private static final class Lineage {
        private final UUID attribution;
        private final PendingCapture capture;
        private final V0LeaseToken lease;
        private final V0LineageId lineage;
        private final long deadline;
        private final V0SpellInstanceId root;
        private final V0SpellInstanceId child;
        private final V0CarrierBindingId rootCarrier;
        private final V0CarrierBindingId childCarrier;
        private P11QualifiedSourceOwner.WorkReservation work;
        private final V0ReleaseReceipt cooldownReceipt;
        private P11CastCooldownService.Prepared cooldownPreparation;
        private V0CatalogAuthority.ReleasePermit release;
        private boolean releasing;

        private final SkillRuntimeService.V0CarrierBinding rootBinding;
        private final SkillRuntimeService.V0CarrierBinding childBinding;
        private Impact rootImpact;
        private Impact childImpact;
        private V0SpawnGeometry childSpawn;
        private boolean childPublished;
        private boolean rootDone;
        private boolean childDone;
        private V0TerminalReason rootTerminal;
        private V0TerminalReason childTerminal;
        private SkillRuntimeService.NormalLogoutScope logoutScope;
        private SkillRuntimeService.LogoutState logoutState = SkillRuntimeService.LogoutState.ONLINE;

        private Lineage(V0SlotRuntime owner, int cell, PendingCapture capture,
                V0RuntimeCatalog.Lease lease, long deadline,
                long lineage, long instance, long carrier) {
            this.attribution = capture.actor.getUUID();
            this.capture = capture;
            cooldownReceipt = owner.normalPolicy()
                    ? new V0ReleaseReceipt(owner.authority.cooldownAttemptId(capture.request.ticket())) : null;
            this.lease = new V0LeaseToken(lease);
            this.lineage = new V0LineageId(lineage);
            this.deadline = deadline;
            this.root = new V0SpellInstanceId(instance);
            this.child = new V0SpellInstanceId(instance + 1);
            this.rootCarrier = new V0CarrierBindingId(carrier);
            this.childCarrier = new V0CarrierBindingId(carrier + 1);
            rootBinding = SkillRuntimeService.reserveV0Carrier(owner, cell, this.lineage, rootCarrier, false);
            childBinding = SkillRuntimeService.reserveV0Carrier(owner, cell, this.lineage, childCarrier, true);
        }
    }

    /** Scalar facts of this exact root attempt; UUID equality alone conveys no authority. */
    private static final class V0ReleaseReceipt implements P11CastCooldownService.ReleaseReceipt {
        private final UUID attempt;
        private P11CastCooldownService.ReleaseFact fact = P11CastCooldownService.ReleaseFact.UNPUBLISHED;
        private long releasedAt = -1;
        private boolean revoked;

        private V0ReleaseReceipt(UUID attempt) {
            this.attempt = Objects.requireNonNull(attempt, "attempt");
        }

        @Override public UUID attemptId() { return attempt; }
        @Override public P11CastCooldownService.ReleaseFact fact() { return fact; }
        @Override public long releasedAt() { return releasedAt; }

        private void beginPublication() {
            if (revoked || fact != P11CastCooldownService.ReleaseFact.UNPUBLISHED) {
                throw new IllegalStateException("V0 cooldown publication identity lost");
            }
            fact = P11CastCooldownService.ReleaseFact.UNKNOWN;
        }

        private void published() {
            if (revoked || fact != P11CastCooldownService.ReleaseFact.UNKNOWN) {
                throw new IllegalStateException("V0 cooldown publication fact lost");
            }
            fact = P11CastCooldownService.ReleaseFact.PENDING;
        }

        private boolean mayArm() { return !revoked && fact == P11CastCooldownService.ReleaseFact.PENDING; }
        private void armed(long tick) { releasedAt = tick; fact = P11CastCooldownService.ReleaseFact.ARM; }
        private void revoke() {
            revoked = true;
            if (fact != P11CastCooldownService.ReleaseFact.ARM) { fact = P11CastCooldownService.ReleaseFact.NO_RELEASE; }
        }
        private void unknown() {
            revoked = true;
            if (fact != P11CastCooldownService.ReleaseFact.ARM
                    && fact != P11CastCooldownService.ReleaseFact.NO_RELEASE) {
                fact = P11CastCooldownService.ReleaseFact.UNKNOWN;
            }
        }
    }

    private static final class Impact {
        private final LivingEntity target;
        private final Vec3 location;
        private final long observedTick;

        private Impact(LivingEntity target, Vec3 location, long observedTick) {
            this.target = target;
            this.location = location;
            this.observedTick = observedTick;
        }
    }

    static final class DispatchClaim {
        private final int eventCell;
        final int lineageSlot;
        final V0LineageId lineage;
        final V0EventId event;
        final V0SpellInstanceId instance;
        final int role;
        final long deadline;

        private DispatchClaim(int eventCell, int lineageSlot, V0LineageId lineage,
                V0EventId event, V0SpellInstanceId instance, int role, long deadline) {
            this.eventCell = eventCell;
            this.lineageSlot = lineageSlot;
            this.lineage = lineage;
            this.event = event;
            this.instance = instance;
            this.role = role;
            this.deadline = deadline;
        }
    }

    private static final class RootGeometry {
        private final ServerLevel level;
        private final Vec3 origin;
        private final int directionXQ15;
        private final int directionYQ15;
        private final int directionZQ15;
        private final boolean normalProfile;

        private RootGeometry(ServerLevel level, Vec3 origin, int directionXQ15,
                int directionYQ15, int directionZQ15, boolean normalProfile) {
            this.level = level;
            this.origin = origin;
            this.directionXQ15 = directionXQ15;
            this.directionYQ15 = directionYQ15;
            this.directionZQ15 = directionZQ15;
            this.normalProfile = normalProfile;
        }

        private static RootGeometry sample(ServerPlayer actor, boolean normalProfile) {
            var level = actor.serverLevel();
            var eye = actor.getEyePosition();
            var look = actor.getLookAngle();
            double lengthSq = look.lengthSqr();
            if (!finite(eye) || !Double.isFinite(lengthSq) || lengthSq <= 0.0) {
                return null;
            }
            var direction = look.scale(1.0 / StrictMath.sqrt(lengthSq));
            var origin = eye.add(direction.scale(0.10)).add(0.0, -0.10, 0.0);
            int x = quantize(direction.x);
            int y = quantize(direction.y);
            int z = quantize(direction.z);
            if (!finite(origin) || (x == 0 && y == 0 && z == 0)) {
                return null;
            }
            return new RootGeometry(level, origin, x, y, z, normalProfile);
        }

        private boolean loaded() {
            if ((!normalProfile && origin.y < level.getMinBuildHeight() + 64.0)
                    || !level.getWorldBorder().isWithinBounds(origin.x, origin.z)) {
                return false;
            }
            var pos = BlockPos.containing(origin);
            if (!level.isInWorldBounds(pos)) {
                return false;
            }
            var chunks = level.getChunkSource();
            var key = new ChunkPos(pos);
            var holder = chunks.chunkMap.getVisibleChunkIfPresent(key.toLong());
            if (holder == null || !(holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk full)
                    || full.getLevel() != level || !full.getPos().equals(key)
                    || !full.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                    || !chunks.hasChunk(key.x, key.z) || chunks.getChunkNow(key.x, key.z) != full) {
                return false;
            }
            var block = full.getBlockState(pos);
            return (block.isAir() || block.is(Blocks.STONE)) && full.getFluidState(pos).isEmpty();
        }

        private static boolean finite(Vec3 vector) {
            return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
        }

        private static int quantize(double component) {
            return (int) Math.max(-32_767.0, Math.min(32_767.0, StrictMath.rint(component * 32_767.0)));
        }
    }
}
