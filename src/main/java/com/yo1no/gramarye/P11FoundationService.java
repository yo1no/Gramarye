package com.yo1no.gramarye;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Root-held foundation. Its source slot survives ServerStopping and the original native saves. */
final class P11FoundationService {
    private MinecraftServer server;
    private P11StartupLoadState startupState = P11StartupLoadState.Unavailable.INSTANCE;
    private P11FoundationSlot slot;
    private final P11SourceProvenance provenance;
    private final PlayerSkillAttachmentService attachments;
    private boolean nativeStopNormal;
    private P11QualifiedSourceOwner.Summary terminalSummary;
    private volatile PlayerStorageBinding playerStorageBinding;

    P11FoundationService(P11SourceProvenance provenance, PlayerSkillAttachmentService attachments) {
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.attachments = Objects.requireNonNull(attachments, "attachments");
        provenance.publicationObserver((actor, epoch, version) -> {
            var source = sourceOwner(actor.getServer());
            if (source != null) { source.publication(actor, epoch, version); }
        });
        P11NativeStorageBoundary.install(this);
        P11NativeOperationBoundary.install(this);
    }

    void started(ServerStartedEvent event, P11StartupLoadState snapshot) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(snapshot, "snapshot");
        var exact = event.getServer();
        if (!exact.isSameThread()) {
            throw new IllegalStateException("P11_START_WRONG_THREAD");
        }
        if (server != null) {
            throw new IllegalStateException("P11_SLOT_ALREADY_OBSERVED");
        }
        server = exact;
        nativeStopNormal = false;
        terminalSummary = null;
        startupState = snapshot;
        if (snapshot instanceof P11StartupLoadState.Ready ready) {
            if (!(exact instanceof P11NativeWorldAccess.ServerStorage)
                    || !(exact.getPlayerList() instanceof P11NativeWorldAccess.PlayerStorage)) {
                throw new IllegalStateException("P11_REQUIRED_NATIVE_HOOK_UNAVAILABLE");
            }
            slot = new P11FoundationSlot(ready.limits(),
                    new P11IdentityOwner(exact, ready.limits().maxUuids()));
            provenance.started(exact);
            slot.sources = new P11QualifiedSourceOwner(exact, slot.identities, slot.receipts,
                    slot.resources, ready.limits(), provenance, attachments);
            playerStorageBinding = new PlayerStorageBinding(exact,
                    (P11NativeWorldAccess.PlayerStorage) exact.getPlayerList(),
                    (P11NativeWorldAccess.ServerStorage) exact,
                    exact.getWorldData(),
                    exact.getWorldPath(LevelResource.PLAYER_STATS_DIR).toAbsolutePath().normalize(),
                    exact.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).toAbsolutePath().normalize(),
                    exact.getWorldPath(LevelResource.PLAYER_DATA_DIR).toAbsolutePath().normalize(),
                    exact.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(),
                    slot.sources);
        } else {
            provenance.inactiveBoundary();
        }
        // Invalid/missing P11 configuration is visible but does not break existing P5 gameplay.
        // Foundation existence is not a qualified source, writer, live control or RUNNING grant.
    }

    Optional<P11StartupLoadState> startupState(MinecraftServer exact) {
        return server != null && server == exact ? Optional.of(startupState) : Optional.empty();
    }

    void stopping(ServerStoppingEvent event) {
        var source = sourceOwner(event.getServer());
        if (source != null) { source.stopping(); }
    }
    void stopped(ServerStoppedEvent event) { stopExact(event.getServer()); }

    P11SourceProvenance provenance() { return provenance; }

    P11QualifiedSourceOwner sourceOwner(MinecraftServer exact) {
        return exact != null && server == exact && exact.isSameThread() && slot != null
                ? slot.sources : null;
    }

    /** Classify the actual target before a wrong-thread/server caller can look unmanaged. */
    P11QualifiedSourceOwner playerStorageOwner(PlayerDataStorage storage, Player player) {
        var binding = playerStorageBinding;
        if (binding == null) { return null; }
        boolean canonical = binding.storage.p11$ownsPlayerStorage(storage);
        boolean sameServer = player instanceof ServerPlayer actor && actor.getServer() == binding.server;
        boolean main = binding.server.isSameThread();
        if (!main) {
            // No account map or actor material is read off the owning server thread.
            requirePlayerStorageAccess(canonical, sameServer, false, false, false);
            return null;
        }
        boolean managed = binding.source.hasAccount(player.getUUID());
        boolean exactActor = sameServer && binding.source.body((ServerPlayer) player) != null;
        return requirePlayerStorageAccess(canonical, sameServer, true, managed, exactActor)
                ? binding.source : null;
    }

    /** Pure part of the native guard; a true result selects the current root, not save permission. */
    static boolean requirePlayerStorageAccess(boolean canonical, boolean sameServer,
            boolean main, boolean managed, boolean exactActor) {
        if (!main) {
            if (canonical || sameServer) { throw new P11QualifiedSourceOwner.SourceUnavailable(); }
            return false;
        }
        if (canonical != sameServer || (managed && (!canonical || !exactActor))) {
            throw new P11QualifiedSourceOwner.SourceUnavailable();
        }
        return canonical;
    }

    private record PlayerStorageBinding(MinecraftServer server,
            P11NativeWorldAccess.PlayerStorage storage,
            P11NativeWorldAccess.ServerStorage worldStorage, WorldData worldData,
            Path statsDirectory, Path advancementsDirectory, Path playerDirectory, Path worldDirectory,
            P11QualifiedSourceOwner source) {}

    P11QualifiedSourceOwner writerOwner(MinecraftServer exact) {
        var binding = playerStorageBinding;
        return binding != null && requireServerWriter(exact == binding.server,
                binding.server.isSameThread()) ? binding.source : null;
    }

    P11QualifiedSourceOwner writerOwner(ServerPlayer actor) {
        var binding = playerStorageBinding;
        if (binding == null) { return null; }
        boolean sameServer = actor.getServer() == binding.server;
        if (!requireServerWriter(sameServer, binding.server.isSameThread())) {
            if (binding.server.isSameThread() && binding.source.hasAccount(actor.getUUID())) {
                throw new P11QualifiedSourceOwner.SourceUnavailable();
            }
            return null;
        }
        if (binding.source.hasAccount(actor.getUUID()) && binding.source.body(actor) == null) {
            throw new P11QualifiedSourceOwner.SourceUnavailable();
        }
        return binding.source;
    }

    P11QualifiedSourceOwner writerOwner(PlayerList list) {
        var source = writerOwner(list.getServer());
        var binding = playerStorageBinding;
        if (source != null && binding.storage != list) {
            throw new P11QualifiedSourceOwner.SourceUnavailable();
        }
        return source;
    }

    P11QualifiedSourceOwner writerOwner(PlayerList list, ServerPlayer actor) {
        var binding = playerStorageBinding;
        if (binding == null) { return null; }
        boolean canonical = binding.storage == list;
        boolean sameServer = actor.getServer() == binding.server;
        if (!binding.server.isSameThread()) {
            requirePlayerStorageAccess(canonical, sameServer, false, false, false);
            return null;
        }
        boolean managed = binding.source.hasAccount(actor.getUUID());
        boolean exactActor = sameServer && binding.source.body(actor) != null;
        return requirePlayerStorageAccess(canonical, sameServer, true, managed, exactActor)
                ? binding.source : null;
    }

    P11QualifiedSourceOwner statsWriterOwner(ServerStatsCounter stats, MinecraftServer exact, File file) {
        var binding = playerStorageBinding;
        if (binding == null) { return null; }
        var path = file == null ? null : file.toPath();
        var pathId = P11NativeJsonPath.playerId(binding.statsDirectory, path);
        var protectedId = P11NativeJsonPath.protectedPlayerId(binding.statsDirectory,
                binding.advancementsDirectory, binding.playerDirectory, path);
        boolean levelTarget = P11NativeJsonPath.levelFile(binding.worldDirectory, path);
        boolean sameServer = exact == binding.server;
        if (!binding.server.isSameThread()) {
            // Canonical-file responsibility cannot be checked through the account map here.
            requireServerWriter(sameServer || protectedId.isPresent() || levelTarget, false);
            return null;
        }
        var body = binding.source.canonicalStats(stats);
        if (body != null) {
            requireIndependentWriterBinding(true, sameServer,
                    pathId.filter(body.actor.getUUID()::equals).isPresent());
            return binding.source;
        }
        if (protectedId.filter(binding.source::hasAccount).isPresent()
                || (levelTarget && binding.source.hostBody() != null)) {
            throw new P11QualifiedSourceOwner.SourceUnavailable();
        }
        return sameServer ? binding.source : null;
    }

    P11QualifiedSourceOwner advancementWriterOwner(PlayerAdvancements advancements,
            ServerPlayer actor, Path file) {
        var binding = playerStorageBinding;
        if (binding == null) { return null; }
        // PA.player is mutable. A foreign actor cannot prove the canonical instance is
        // unowned, and the bounded canonical map must never be inspected off-thread.
        requireServerWriter(true, binding.server.isSameThread());
        var pathId = P11NativeJsonPath.playerId(binding.advancementsDirectory, file);
        var protectedId = P11NativeJsonPath.protectedPlayerId(binding.statsDirectory,
                binding.advancementsDirectory, binding.playerDirectory, file);
        var body = binding.source.canonicalAdvancements(advancements);
        if (body != null) {
            requireIndependentWriterBinding(true, actor.getServer() == binding.server,
                    binding.source.canonicalAssociated(advancements, actor)
                            && pathId.filter(body.actor.getUUID()::equals).isPresent());
            return binding.source;
        }
        if (binding.source.hasAccount(actor.getUUID())
                || protectedId.filter(binding.source::hasAccount).isPresent()
                || (P11NativeJsonPath.levelFile(binding.worldDirectory, file)
                        && binding.source.hostBody() != null)) {
            throw new P11QualifiedSourceOwner.SourceUnavailable();
        }
        return actor.getServer() == binding.server ? binding.source : null;
    }

    P11QualifiedSourceOwner writerOwner(LevelStorageSource.LevelStorageAccess storage, WorldData data) {
        var binding = playerStorageBinding;
        if (binding == null) { return null; }
        return requireWorldWriter(binding.worldStorage.p11$ownsWorldStorage(storage), data != null,
                data == binding.worldData, binding.server.isSameThread()) ? binding.source : null;
    }

    static boolean requireServerWriter(boolean exactServer, boolean main) {
        if (exactServer && !main) { throw new P11QualifiedSourceOwner.SourceUnavailable(); }
        return exactServer;
    }

    static boolean requireWorldWriter(boolean exactStorage, boolean suppliedData,
            boolean exactData, boolean main) {
        if (exactStorage) {
            requireServerWriter(true, main);
            if (suppliedData && !exactData) { throw new P11QualifiedSourceOwner.SourceUnavailable(); }
            return true;
        }
        if (suppliedData && exactData) { throw new P11QualifiedSourceOwner.SourceUnavailable(); }
        return false;
    }

    static void requireIndependentWriterBinding(boolean canonicalInstance, boolean exactServer,
            boolean exactActor) {
        if (canonicalInstance && (!exactServer || !exactActor)) {
            throw new P11QualifiedSourceOwner.SourceUnavailable();
        }
    }

    P11QualifiedSourceOwner sourceOwner(LevelStorageSource.LevelStorageAccess storage, WorldData data) {
        if (server == null || !server.isSameThread() || slot == null
                || !(server instanceof P11NativeWorldAccess.ServerStorage access)
                || !access.p11$ownsWorldStorage(storage) || (data != null && data != server.getWorldData())) {
            return null;
        }
        return slot.sources;
    }

    P11QualifiedSourceOwner.Body hostBody(P11QualifiedSourceOwner source) {
        return source == null ? null : source.hostBody();
    }

    P11QualifiedSourceOwner.InputKind hostInputKind(MinecraftServer exact) {
        if (server != exact || !(server instanceof P11NativeWorldAccess.ServerStorage access)
                || !(server.getWorldData() instanceof P11NativeWorldAccess.ParsedWorld parsed)) {
            return P11QualifiedSourceOwner.InputKind.UNKNOWN;
        }
        if (parsed.p11$freshWorld()) { return P11QualifiedSourceOwner.InputKind.ABSENT; }
        var read = access.p11$worldReadWitness();
        var parsedWitness = parsed.p11$parsedInput();
        var input = parsedWitness == null ? null : parsedWitness.input;
        if (read == null || input == null || !read.validEnvelope
                || (read.input != input && read.input.getValue() != input.getValue())) {
            return P11QualifiedSourceOwner.InputKind.UNKNOWN;
        }
        if (!read.primary) { return P11QualifiedSourceOwner.InputKind.FALLBACK; }
        if (!(input.getValue() instanceof CompoundTag data)) { return P11QualifiedSourceOwner.InputKind.UNKNOWN; }
        if (!data.contains("Player")) {
            return read.playerPresent ? P11QualifiedSourceOwner.InputKind.ERROR
                    : P11QualifiedSourceOwner.InputKind.ABSENT;
        }
        return data.contains("Player", Tag.TAG_COMPOUND) && read.playerFingerprint != null
                && read.playerFingerprint.matches(data.getCompound("Player"))
                ? P11QualifiedSourceOwner.InputKind.HOST_PRIMARY
                : P11QualifiedSourceOwner.InputKind.ERROR;
    }

    void nativeStopTerminal(MinecraftServer exact, boolean normal) {
        if (server == exact) { nativeStopNormal = normal; }
    }

    P11QualifiedSourceOwner.Summary terminalSummary() { return terminalSummary; }

    private void stopExact(MinecraftServer exact) {
        if (server != exact) { return; }
        if (!exact.isSameThread()) {
            throw new IllegalStateException("P11_STOP_WRONG_THREAD");
        }
        if (slot != null) {
            if (slot.sources != null) {
                terminalSummary = slot.sources.retire(nativeStopNormal);
                provenance.stopped(exact);
            }
            slot.retire();
        } else {
            provenance.inactiveBoundary();
        }
        slot = null;
        playerStorageBinding = null;
        server = null;
        startupState = P11StartupLoadState.Unavailable.INSTANCE;
    }
}

/** The same limits/owners used by the live root can be exercised in an isolated identity domain. */
final class P11FoundationSlot {
    private final P11StartupLimits limits;
    final P11IdentityOwner identities;
    final P11ReceiptLedger receipts;
    final P11ControlBudgets.Resources resources;
    P11QualifiedSourceOwner sources;
    private boolean retired;

    P11FoundationSlot(P11StartupLimits limits, P11IdentityOwner identities) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.identities = Objects.requireNonNull(identities, "identities");
        receipts = new P11ReceiptLedger(identities);
        resources = new P11ControlBudgets.Resources(limits);
    }

    P11StartupLimits limits() { return limits; }
    boolean retired() { return retired; }

    void retire() {
        if (retired) { return; }
        retired = true;
        resources.retireSlot();
        receipts.stop();
        identities.stop();
    }
}
