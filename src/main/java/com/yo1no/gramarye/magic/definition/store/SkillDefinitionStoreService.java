package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
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

    SkillDefinitionStoreService() {
    }

    /** Creates the unique service instance and attaches its two server lifecycle listeners. */
    public static SkillDefinitionStoreService registerOn(IEventBus gameBus) {
        Objects.requireNonNull(gameBus, "gameBus");
        var service = new SkillDefinitionStoreService();
        gameBus.addListener(service::onServerStarting);
        gameBus.addListener(service::onServerStopped);
        return service;
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
    }

    private void onServerStopped(ServerStoppedEvent event) {
        uninstall(event.getServer());
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
