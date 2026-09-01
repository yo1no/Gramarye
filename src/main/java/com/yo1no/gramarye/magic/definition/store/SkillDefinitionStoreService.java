package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Public controlled port for the unique Overworld skill Store lifecycle. */
public final class SkillDefinitionStoreService {
    /** Stable platform storage name; the primary path appends exactly {@code .dat}. */
    static final String SAVED_DATA_NAME = "gramarye_skill_definitions";

    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new IllegalStateException(
                                "skill SavedData cache-hit constructor was invoked");
                    },
                    (tag, provider) -> {
                        throw new IllegalStateException(
                                "skill SavedData cache-hit deserializer was invoked");
                    });

    private final IdentityHashMap<MinecraftServer, InstalledMarker> installedServers =
            new IdentityHashMap<>();
    private final SkillDefinitionStoreSubmissionPort submissionPort =
            new SkillDefinitionStoreSubmissionPort(this);
    private final SkillRetentionRootAuditService rootAuditService;
    private final P4E2OnlineReconciliationCoordinator onlineReconciliationDependency;
    private final P4E2QualificationFacade.StoreView qualificationStoreView;

    SkillDefinitionStoreService() {
        rootAuditService = null;
        onlineReconciliationDependency = null;
        qualificationStoreView = null;
    }

    private SkillDefinitionStoreService(PlayerSkillAttachmentService attachmentService) {
        this(attachmentService, null, null);
    }

    private SkillDefinitionStoreService(
            PlayerSkillAttachmentService attachmentService,
            P4E2QualificationFacade.StoreView qualificationStoreView,
            P4E2QualificationFacade.PlayerView qualificationPlayerView) {
        Objects.requireNonNull(attachmentService, "attachmentService");
        rootAuditService = new SkillRetentionRootAuditService(this, attachmentService);
        onlineReconciliationDependency = new P4E2OnlineReconciliationCoordinator(
                this,
                attachmentService,
                rootAuditService,
                qualificationStoreView,
                qualificationPlayerView);
        this.qualificationStoreView = qualificationStoreView;
    }

    /** Creates the unique fully composed service and attaches its server lifecycle listeners. */
    public static SkillDefinitionStoreService registerOn(
            IEventBus gameBus, PlayerSkillAttachmentService attachmentService) {
        Objects.requireNonNull(gameBus, "gameBus");
        var service = new SkillDefinitionStoreService(
                Objects.requireNonNull(attachmentService, "attachmentService"));
        service.registerLifecycleListeners(gameBus);
        return service;
    }

    /**
     * Creates the unique composed service with the closed, owner-bound qualification views.
     * The views observe only an explicitly armed direct-qualification session.
     */
    public static SkillDefinitionStoreService registerOn(
            IEventBus gameBus,
            PlayerSkillAttachmentService attachmentService,
            P4E2QualificationFacade.StoreView qualificationStoreView,
            P4E2QualificationFacade.PlayerView qualificationPlayerView) {
        Objects.requireNonNull(gameBus, "gameBus");
        var service = new SkillDefinitionStoreService(
                Objects.requireNonNull(attachmentService, "attachmentService"),
                Objects.requireNonNull(qualificationStoreView, "qualificationStoreView"),
                Objects.requireNonNull(qualificationPlayerView, "qualificationPlayerView"));
        service.registerLifecycleListeners(gameBus);
        return service;
    }

    private void registerLifecycleListeners(IEventBus gameBus) {
        gameBus.addListener(this::onServerStarting);
        gameBus.addListener(this::onServerStopped);
    }

    /** Looks up one exact retained skill document without exposing the live Store. */
    public SkillSubsystemResult<Optional<SkillDocument>> find(
            MinecraftServer server,
            SkillReference reference) {
        return installedAdapter(server).find(reference);
    }

    /** Looks up the latest retained reference for one skill identity. */
    public SkillSubsystemResult<Optional<SkillReference>> latestReference(
            MinecraftServer server,
            SkillId skillId) {
        return latestReference(server, skillId, Thread.currentThread().threadId());
    }

    SkillSubsystemResult<Optional<SkillReference>> latestReference(
            MinecraftServer server,
            SkillId skillId,
            long observedThreadId) {
        requireServerThread(server, observedThreadId);
        return installedAdapter(server).latestReference(skillId);
    }

    /** Looks up the immutable committed owner binding for one skill identity. */
    public SkillSubsystemResult<Optional<SkillOwnerId>> ownerOf(
            MinecraftServer server,
            SkillId skillId) {
        return installedAdapter(server).ownerOf(skillId);
    }

    /** Counts committed skill histories owned by one principal. */
    public SkillSubsystemResult<Integer> committedSkillCount(
            MinecraftServer server,
            SkillOwnerId owner) {
        return installedAdapter(server).committedSkillCount(owner);
    }

    /** Acquires a controlled transient pin when the exact revision is retained. */
    public SkillSubsystemResult<Optional<ControlledSkillPin>> pin(
            MinecraftServer server,
            SkillReference reference) {
        var result = installedAdapter(server).pin(reference);
        return switch (result) {
            case SkillSubsystemResult.Available<Optional<SkillRevisionPin>> available ->
                    new SkillSubsystemResult.Available<>(
                            available.value().map(pin -> new ControlledSkillPin(server, pin)));
            case SkillSubsystemResult.Unavailable<Optional<SkillRevisionPin>> unavailable ->
                    new SkillSubsystemResult.Unavailable<>(unavailable.reason());
        };
    }

    /** Applies reclaim using only the caller-provided retention-root snapshot. */
    public SkillSubsystemResult<SkillReclaimResult> reclaim(
            MinecraftServer server,
            SkillRetentionRootSnapshot roots) {
        return installedAdapter(server).reclaim(roots);
    }

    /** Returns the unique narrow submission/journal port owned by this lifecycle service. */
    public SkillDefinitionStoreSubmissionPort submissionPort() {
        return submissionPort;
    }

    /** Returns the closed E2 continuation dependency of the fully composed service. */
    public P4E2OnlineReconciliationDependency onlineReconciliationDependency() {
        if (onlineReconciliationDependency == null) {
            throw new IllegalStateException("P4E2_STORE_SERVICE_NOT_COMPOSED");
        }
        return onlineReconciliationDependency;
    }

    /** Captures the exact package-private Ready identity tuple before any P4-E1 source work. */
    P4E1GlobalSourceCapture.StoreObservation observeP4E1StoreReady(
            MinecraftServer server) {
        requireServerThread(server);
        var adapter = installedAdapter(server);
        if (!(adapter.state() instanceof SkillSavedDataState.Ready ready)) {
            return P4E1GlobalSourceCapture.StoreObservation.Unavailable.INSTANCE;
        }
        var inner = ready.innerCarrier();
        var storeCarrier = ready.storeCarrier();
        if (inner.storeCarrier() != storeCarrier) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.CACHE_IDENTITY_MISMATCH);
        }
        return new P4E1GlobalSourceCapture.StoreObservation.Ready(
                new P4E1GlobalSourceCapture.StoreReadyWitness(
                        this,
                        server,
                        adapter,
                        ready,
                        ready.store(),
                        inner,
                        storeCarrier,
                        inner.pending()));
    }

    boolean isP4E1StoreReadyCurrent(
            MinecraftServer server,
            P4E1GlobalSourceCapture.StoreReadyWitness witness) {
        requireServerThread(server);
        Objects.requireNonNull(witness, "witness");
        witness.requireBinding(this, server);
        var adapter = installedAdapter(server);
        return witness.matches(adapter);
    }

    P4E2GroupedStoreValidation.StoreObservation observeP4E2StoreReady(
            MinecraftServer server,
            P4E2OnlineReconciliationCoordinator coordinator) {
        requireP4E2Composition(coordinator);
        requireServerThread(server);
        var adapter = installedAdapter(server);
        if (!(adapter.state() instanceof SkillSavedDataState.Ready ready)) {
            return P4E2GroupedStoreValidation.StoreObservation.Unavailable.INSTANCE;
        }
        return new P4E2GroupedStoreValidation.StoreObservation.Ready(
                new P4E2GroupedStoreValidation.StoreReadyWitness(
                        this,
                        server,
                        adapter,
                        ready,
                        ready.store(),
                        rootAuditService,
                        onlineReconciliationDependency));
    }

    boolean isP4E2StoreReadyCurrent(
            MinecraftServer server,
            P4E2GroupedStoreValidation.StoreReadyWitness witness) {
        requireServerThread(server);
        Objects.requireNonNull(witness, "witness");
        if (rootAuditService == null || onlineReconciliationDependency == null) {
            return false;
        }
        final GramaryeSkillSavedData adapter;
        try {
            adapter = installedAdapter(server);
        } catch (SkillSubsystemLifecycleException exception) {
            return switch (exception.code()) {
                case BOOTSTRAP_NOT_INSTALLED, OVERWORLD_UNAVAILABLE,
                        CACHE_IDENTITY_MISMATCH -> false;
                case WRONG_THREAD, BOOTSTRAP_ALREADY_INSTALLED,
                        JOURNAL_BOOTSTRAP_ALREADY_INSTALLED -> throw exception;
            };
        }
        return witness.matches(
                this,
                server,
                adapter,
                rootAuditService,
                onlineReconciliationDependency);
    }

    private void requireP4E2Composition(
            P4E2OnlineReconciliationCoordinator coordinator) {
        if (rootAuditService == null
                || onlineReconciliationDependency == null
                || onlineReconciliationDependency
                        != Objects.requireNonNull(coordinator, "coordinator")) {
            throw new IllegalStateException("P4E2_STORE_SERVICE_NOT_COMPOSED");
        }
    }

    /**
     * Reservation-before-source lifecycle gate for the memory-only P4-E1 audit owner.
     * This deliberately does not observe the adapter, SavedData state, Store, or journal.
     */
    void requireP4E1AuditLifecycle(MinecraftServer server) {
        requireServerThread(server);
        if (!installedServers.containsKey(server)) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.BOOTSTRAP_NOT_INSTALLED);
        }
    }

    void install(MinecraftServer server) {
        requireServerThread(server);
        if (installedServers.containsKey(server)) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.BOOTSTRAP_ALREADY_INSTALLED);
        }

        var loaded = SkillSavedDataPrimaryIngress.load(server);
        var adapter = switch (loaded) {
            case SkillSavedDataPrimaryLoadResult.Absent ignored -> GramaryeSkillSavedData.ready(
                    SkillSavedDataCarrierPersistenceBridge.createEmptyCurrent());
            case SkillSavedDataPrimaryLoadResult.Ready ready ->
                    GramaryeSkillSavedData.ready(ready.candidate());
            case SkillSavedDataPrimaryLoadResult.Failure failure ->
                    GramaryeSkillSavedData.quarantined(failure.failure());
        };

        var storage = requireOverworld(server).getDataStorage();
        storage.set(SAVED_DATA_NAME, adapter);
        var cached = storage.get(CACHE_HIT_ONLY_FACTORY, SAVED_DATA_NAME);
        if (cached != adapter) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.CACHE_IDENTITY_MISMATCH);
        }
        if (adapter.rewriteRequired()) {
            adapter.setDirty();
        }
        installedServers.put(server, InstalledMarker.INSTANCE);
    }

    void uninstall(MinecraftServer server) {
        requireServerThread(server);
        installedServers.remove(server);
    }

    private void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();
        install(server);
        submissionPort.bootstrapJournal(server);
        runP4E3StartupReclaim(server);
    }

    private void runP4E3StartupReclaim(MinecraftServer server) {
        var observationView = qualificationStoreView == null
                ? null
                : qualificationStoreView.e3StartupView();
        var recording = observationView != null && observationView.beginRecording(server);
        try {
            if (recording) {
                observationView.recordAuditInvocation(server);
            }
            var auditResult = rootAuditService.audit(server);
            if (recording) {
                var generation = auditResult.summary().indexGeneration();
                if (generation.isEmpty()) {
                    throw new IllegalStateException("P4E3_AUDIT_GENERATION_NOT_AVAILABLE");
                }
                observationView.recordAuditResult(
                        server, p4E3AuditVariant(auditResult), generation.getAsLong());
            }
            if (!(auditResult instanceof SkillRetentionRootAuditResult.Complete complete)) {
                if (recording) {
                    observationView.completeRecording(server);
                }
                return;
            }

            if (recording) {
                observationView.recordCompleteConsumeInvocation(server);
            }
            var handoff = rootAuditService.consumeComplete(server, complete);
            var normalTerminal = false;
            var sourceUnchanged = false;
            try {
                if (recording) {
                    observationView.recordSnapshotInvocation(server);
                }
                var snapshotResult = SkillRetentionRootSnapshot.fromCompleteRoots(handoff);
                if (recording) {
                    observationView.recordSnapshotResult(
                            server,
                            p4E3SnapshotVariant(snapshotResult),
                            snapshotResult instanceof SkillRetentionRootSnapshot.Complete snapshot
                                    ? snapshot.roots().size() : -1);
                }
                if (snapshotResult instanceof SkillRetentionRootSnapshot.Complete snapshot) {
                    var exactAdapter = recording ? installedAdapter(server) : null;
                    if (recording) {
                        observationView.recordReclaimInvocation(server, exactAdapter.isDirty());
                    }
                    var reclaimResult = this.reclaim(server, snapshot);
                    if (reclaimResult
                            instanceof SkillSubsystemResult.Available<SkillReclaimResult> available
                            && available.value() instanceof SkillReclaimResult.Completed completed
                            && completed.report().revisionsReclaimed() == 0) {
                        sourceUnchanged = true;
                    }
                    if (recording) {
                        recordP4E3ReclaimResult(observationView, server, reclaimResult);
                        observationView.recordDirtyAfter(server, exactAdapter.isDirty());
                    }
                }
                normalTerminal = true;
            } finally {
                try {
                    if (normalTerminal && sourceUnchanged) {
                        handoff.markStoreSourceUnchanged();
                    }
                } finally {
                    handoff.close();
                }
            }
            if (recording) {
                var terminal = rootAuditService.observeP4E3IndexTerminal(server);
                observationView.recordIndexTerminal(
                        server, terminal.terminal(), terminal.generation());
            }
            if (recording) {
                observationView.completeRecording(server);
            }
        } catch (RuntimeException | Error failure) {
            if (recording) {
                observationView.abortRecording(server);
            }
            throw failure;
        }
    }

    private static P4E2QualificationFacade.E3AuditVariant p4E3AuditVariant(
            SkillRetentionRootAuditResult result) {
        return switch (result) {
            case SkillRetentionRootAuditResult.Complete ignored ->
                    P4E2QualificationFacade.E3AuditVariant.COMPLETE;
            case SkillRetentionRootAuditResult.OverLimit ignored ->
                    P4E2QualificationFacade.E3AuditVariant.OVER_LIMIT;
            case SkillRetentionRootAuditResult.ReconciliationRequired ignored ->
                    P4E2QualificationFacade.E3AuditVariant.RECONCILIATION_REQUIRED;
            case SkillRetentionRootAuditResult.Incomplete incomplete -> switch (
                    incomplete.reason()) {
                case GENERATION_EXHAUSTED ->
                        P4E2QualificationFacade.E3AuditVariant.GENERATION_EXHAUSTED;
                case HEAP_FLOOR_NOT_MET,
                        HEAP_FLOOR_UNVERIFIABLE,
                        STORE_UNAVAILABLE,
                        JOURNAL_NOT_READY,
                        JOURNAL_UNAVAILABLE,
                        JOURNAL_TARGET_INVALID,
                        INVENTORY_PROVIDER_MISSING,
                        COUNTER_CAPACITY_EXCEEDED,
                        DIRECTORY_UNREADABLE,
                        DIRECTORY_TYPE_UNSUPPORTED,
                        DIRECTORY_IDENTITY_UNAVAILABLE,
                        DIRECTORY_RACE_DETECTED,
                        PLAYERDATA_NAME_NONCANONICAL,
                        PRIMARY_FILE_UNREADABLE,
                        PRIMARY_FILE_TYPE_UNSUPPORTED,
                        PRIMARY_FILE_IDENTITY_UNAVAILABLE,
                        PRIMARY_FILE_RACE_DETECTED,
                        PLATFORM_READ_FAILURE_PROVEN,
                        STRICT_GZIP_REJECTED,
                        STRICT_NBT_REJECTED,
                        DATA_VERSION_MISSING,
                        DATA_VERSION_WRONG_TYPE,
                        DATA_VERSION_NOT_CURRENT,
                        ATTACHMENT_ADMISSION_REJECTED,
                        ATTACHMENT_QUARANTINED,
                        INTEGRATED_OWNER_IDENTITY_UNAVAILABLE,
                        INTEGRATED_OWNER_FRESHNESS_LOST,
                        ONLINE_SOURCE_FRESHNESS_LOST,
                        SERVER_FRESHNESS_LOST,
                        CALL_CHAIN_FRESHNESS_LOST,
                        INDEX_RESERVATION_LOST,
                        STORE_SOURCE_FRESHNESS_LOST,
                        JOURNAL_FRESHNESS_LOST,
                        JOURNAL_TARGET_PROOF_LOST,
                        INVENTORY_PROVIDER_FRESHNESS_LOST,
                        SELECTED_FILE_FRESHNESS_LOST,
                        INTERNAL_RUNTIME_FAILURE ->
                                P4E2QualificationFacade.E3AuditVariant.INCOMPLETE;
            };
        };
    }

    private static P4E2QualificationFacade.E3SnapshotVariant p4E3SnapshotVariant(
            SkillRetentionRootSnapshot result) {
        return switch (result) {
            case SkillRetentionRootSnapshot.Complete ignored ->
                    P4E2QualificationFacade.E3SnapshotVariant.COMPLETE;
            case SkillRetentionRootSnapshot.Incomplete ignored ->
                    P4E2QualificationFacade.E3SnapshotVariant.INCOMPLETE;
            case SkillRetentionRootSnapshot.Truncated ignored ->
                    P4E2QualificationFacade.E3SnapshotVariant.TRUNCATED;
            case SkillRetentionRootSnapshot.OverLimit ignored ->
                    P4E2QualificationFacade.E3SnapshotVariant.OVER_LIMIT;
        };
    }

    private static void recordP4E3ReclaimResult(
            P4E2QualificationFacade.E3StartupView observationView,
            MinecraftServer server,
            SkillSubsystemResult<SkillReclaimResult> result) {
        switch (result) {
            case SkillSubsystemResult.Unavailable<SkillReclaimResult> ignored ->
                    observationView.recordReclaimResult(
                            server,
                            P4E2QualificationFacade.E3ReclaimVariant.UNAVAILABLE,
                            -1,
                            -1,
                            -1,
                            -1);
            case SkillSubsystemResult.Available<SkillReclaimResult> available -> {
                switch (available.value()) {
                    case SkillReclaimResult.Completed completed -> {
                        var report = completed.report();
                        var variant = report.revisionsReclaimed() == 0
                                ? P4E2QualificationFacade.E3ReclaimVariant.COMPLETED_ZERO
                                : P4E2QualificationFacade.E3ReclaimVariant.COMPLETED_POSITIVE;
                        observationView.recordReclaimResult(
                                server,
                                variant,
                                report.historiesScanned(),
                                report.revisionsScanned(),
                                report.historiesChanged(),
                                report.revisionsReclaimed());
                    }
                    case SkillReclaimResult.Rejected ignored ->
                            observationView.recordReclaimResult(
                                    server,
                                    P4E2QualificationFacade.E3ReclaimVariant.REJECTED,
                                    -1,
                                    -1,
                                    -1,
                                    -1);
                }
            }
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        var server = event.getServer();
        if (qualificationStoreView != null) {
            qualificationStoreView.clearOnServerStopped();
            qualificationStoreView.e3StartupView().clearOnServerStopped(server);
        }
        rootAuditService.removeServer(server);
        uninstall(server);
    }

    GramaryeSkillSavedData installedAdapter(MinecraftServer server) {
        requireServerThread(server);
        if (!installedServers.containsKey(server)) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.BOOTSTRAP_NOT_INSTALLED);
        }
        var cached = requireOverworld(server)
                .getDataStorage()
                .get(CACHE_HIT_ONLY_FACTORY, SAVED_DATA_NAME);
        if (!(cached instanceof GramaryeSkillSavedData adapter)) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.CACHE_IDENTITY_MISMATCH);
        }
        return adapter;
    }

    private static net.minecraft.server.level.ServerLevel requireOverworld(
            MinecraftServer server) {
        var overworld = server.overworld();
        if (overworld == null) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.OVERWORLD_UNAVAILABLE);
        }
        return overworld;
    }

    static void requireServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.WRONG_THREAD);
        }
    }

    static void requireServerThread(MinecraftServer server, long observedThreadId) {
        Objects.requireNonNull(server, "server");
        var decision = ProductThreadPrecondition.classify(
                server.getRunningThread().threadId(), observedThreadId);
        if (decision == ProductThreadPrecondition.Decision.WRONG_THREAD) {
            throw lifecycle(SkillSubsystemLifecycleException.Code.WRONG_THREAD);
        }
    }

    private static SkillSubsystemLifecycleException lifecycle(
            SkillSubsystemLifecycleException.Code code) {
        return new SkillSubsystemLifecycleException(code);
    }

    private enum InstalledMarker {
        INSTANCE
    }
}

/** Fixed-code programming/lifecycle failure; it retains no server, path, or raw state. */
final class SkillSubsystemLifecycleException extends IllegalStateException {
    private final Code code;

    SkillSubsystemLifecycleException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    Code code() {
        return code;
    }

    enum Code {
        WRONG_THREAD,
        BOOTSTRAP_ALREADY_INSTALLED,
        BOOTSTRAP_NOT_INSTALLED,
        OVERWORLD_UNAVAILABLE,
        CACHE_IDENTITY_MISMATCH,
        JOURNAL_BOOTSTRAP_ALREADY_INSTALLED
    }
}
