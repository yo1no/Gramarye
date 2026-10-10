package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import com.yo1no.gramarye.magic.runtime.mana.P11ManaMaterial;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Internal locked-native callsites only. Installation is root-package restricted. This is a
 * delegate to the root's one foundation slot, not a service registry or an actor locator.
 * Public visibility is necessary after Mixin moves handlers into the named native classes.
 * No public method accepts scalar facts as a persisted/save grant.
 */
public final class P11NativeStorageBoundary {
    private static volatile P11FoundationService root;
    private static final ThreadLocal<LoadScope> LOAD = new ThreadLocal<>();
    private static final ThreadLocal<SerializeScope> SERIALIZE = new ThreadLocal<>();
    private static final ThreadLocal<WriteScope> WRITE = new ThreadLocal<>();
    private static final ThreadLocal<WriteScope> IO = new ThreadLocal<>();
    private static final ThreadLocal<CopyScope> COPY = new ThreadLocal<>();
    private static final ThreadLocal<LevelReadScope> LEVEL_READ = new ThreadLocal<>();
    private static final ThreadLocal<CacheScope> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<PrimaryReadRequest> PRIMARY_READ = new ThreadLocal<>();
    private static final ThreadLocal<ConfigurationScope> CONFIGURATION = new ThreadLocal<>();
    private static final ThreadLocal<MetadataManaObservation> METADATA_MANA = new ThreadLocal<>();
    private static final ThreadLocal<P11QualifiedSourceOwner.Body> SELECTED_SERIALIZE = new ThreadLocal<>();
    private static final ThreadLocal<MinecraftServer> STOP_SERVER = new ThreadLocal<>();
    private static final ThreadLocal<DetachedStopSaveRequest> DETACHED_STOP_SAVE = new ThreadLocal<>();
    private static final ThreadLocal<NormalLogoutProof> NORMAL_LOGOUT = new ThreadLocal<>();
    private static final ResourceLocation SKILLS = ResourceLocation.fromNamespaceAndPath("gramarye", "player_skills");
    private static final ResourceLocation MANA = ResourceLocation.fromNamespaceAndPath("gramarye", "player_mana");
    private static final ResourceLocation COOLDOWNS = P11CastCooldownAttachments.ID;
    private static long observerFailures;

    private P11NativeStorageBoundary() {}

    static void install(P11FoundationService exactRoot) {
        if (root != null && root != exactRoot) { throw new IllegalStateException("P11_ROOT_ALREADY_INSTALLED"); }
        root = exactRoot;
    }

    private static P11QualifiedSourceOwner owner(ServerPlayer player) {
        return root == null ? null : root.sourceOwner(player.getServer());
    }

    static P11QualifiedSourceOwner nativeSourceOwner(ServerPlayer actor) {
        return actor == null ? null : owner(actor);
    }

    /** Minted only for the sole recovery owner's private-constructor continuation. */
    public static MetadataLease retainMetadata(ServerPlayer actor,
            SkillSubmissionRecoveryService.MetadataContinuation continuation) {
        java.util.Objects.requireNonNull(continuation, "continuation");
        if (!continuation.authorizesRetention(actor)) { return null; }
        var source = nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        if (body == null || !source.canSerialize(body) || !nativeDeliveryEligible(actor)) { return null; }
        if (body.account.metadata != null) { throw unavailable(); }
        var witness = source.captureSelection(body);
        if (!source.retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)) { throw unavailable(); }
        var lease = new MetadataLease(source, body, witness, actor.connection.getConnection(), continuation);
        body.account.metadata = lease;
        return lease;
    }

    public static boolean metadataCurrent(MetadataLease lease) {
        return metadataSessionCurrent(lease)
                && lease.owner.metadataCurrent(lease.body, lease.version, lease.witness);
    }

    /** Completion observation only: never authorizes resume or refresh of a stale snapshot. */
    public static boolean metadataSessionCurrent(MetadataLease lease) {
        return lease != null && !lease.closed && lease.body.account.metadata == lease
                && lease.body.source.epoch() == lease.epoch && lease.owner.canSerialize(lease.body)
                && nativeDeliveryEligible(lease.body.actor)
                && lease.body.actor.connection.getConnection() == lease.connection;
    }

    /** Only a known completed owned publication may request this refresh, never UNKNOWN. */
    public static boolean refreshMetadata(MetadataLease lease) {
        if (lease == null || lease.closed || lease.body.account.metadata != lease
                || lease.body.source.epoch() != lease.epoch || !lease.owner.canSerialize(lease.body)
                || !nativeDeliveryEligible(lease.body.actor)
                || lease.body.actor.connection.getConnection() != lease.connection) { return false; }
        lease.witness = lease.owner.captureSelection(lease.body);
        lease.version = lease.body.source;
        return true;
    }

    public static void releaseMetadata(MetadataLease lease) {
        if (lease == null || lease.closed) { return; }
        lease.closed = true;
        if (lease.body.account.metadata == lease) { lease.body.account.metadata = null; }
        lease.owner.releaseNativeRoot(lease.body, P11ControlBudgets.Root.TRANSITION);
    }

    /** Excluded engineering companion may drive this same-owner entry, not mint its receipt. */
    static boolean resumeMetadataAfterCauseRemoved(ServerPlayer actor) {
        var owner = nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        return body != null && resumeMetadata(body.account.metadata);
    }

    private static boolean resumeMetadata(MetadataLease lease) {
        return metadataCurrent(lease) && lease.continuation.resume(lease);
    }

    /** Observation from the sole P7 sender; it does not mint or resume a continuation. */
    public static void metadataInitialSync(ServerPlayer actor, long epoch, long generation,
            SkillSubmissionRecoveryService.MetadataInitialStage stage) {
        try {
            var source = nativeSourceOwner(actor);
            var body = source == null ? null : source.body(actor);
            var lease = body == null ? null : body.account.metadata;
            if (metadataSessionCurrent(lease)) { lease.continuation.observeInitialSync(lease, epoch, generation, stage); }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    /** Call-local P7 observation; only the native Mana owner can attest its one publication. */
    public static MetadataManaObservation beginMetadataManaObservation(ServerPlayer actor, long epoch, long generation) {
        try {
            var source = nativeSourceOwner(actor);
            var body = source == null ? null : source.body(actor);
            var lease = body == null ? null : body.account.metadata;
            if (!metadataCurrent(lease) || !lease.continuation.matchesSession(lease, epoch, generation)) { return null; }
            var token = new MetadataManaObservation(lease, METADATA_MANA.get());
            METADATA_MANA.set(token);
            return token;
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
            return null;
        }
    }

    static void metadataManaPublished(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
            P11ReceiptLedger.Source before) {
        var token = METADATA_MANA.get();
        if (token == null || token.closed || token.lease.owner != owner || token.lease.body != body) { return; }
        if (token.publications != 0 || token.expected != before
                || body.source.epoch() != before.epoch() || before.version() == Long.MAX_VALUE
                || body.source.version() != before.version() + 1) {
            token.unexpected = true;
            return;
        }
        token.publications++;
        token.expected = body.source;
    }

    /** Only the sole cooldown owner can reach this exact typed publication completion. */
    static void metadataCooldownPublished(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
            P11ReceiptLedger.Source before) {
        var lease = body.account.metadata;
        if (lease == null || lease.closed || lease.owner != owner || lease.body != body
                || lease.version != before || body.source.epoch() != before.epoch()
                || before.version() == Long.MAX_VALUE || body.source.version() != before.version() + 1
                || !owner.metadataUnchangedExceptCooldown(body, lease.witness)) { return; }
        refreshMetadata(lease);
    }

    public static void endMetadataManaObservation(MetadataManaObservation token, boolean normal) {
        if (token == null || token.closed || METADATA_MANA.get() != token) { return; }
        try {
            var lease = token.lease;
            boolean known = normal && !token.unexpected && !lease.closed && lease.body.account.metadata == lease
                    && lease.body.source == token.expected
                    && lease.owner.metadataSkillsCurrent(lease.body, lease.witness)
                    && refreshMetadata(lease);
            if (!known) { lease.continuation.manaObservationFailed(lease); }
        } catch (RuntimeException | Error secondary) {
            token.lease.continuation.manaObservationFailed(token.lease);
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        } finally {
            token.closed = true;
            if (token.previous == null) { METADATA_MANA.remove(); } else { METADATA_MANA.set(token.previous); }
        }
    }

    public static final class MetadataManaObservation {
        private final MetadataLease lease;
        private final MetadataManaObservation previous;
        private P11ReceiptLedger.Source expected;
        private int publications;
        private boolean unexpected, closed;
        private MetadataManaObservation(MetadataLease lease, MetadataManaObservation previous) {
            this.lease = lease; this.previous = previous; expected = lease.version;
        }
    }

    public static final class MetadataLease {
        private final P11QualifiedSourceOwner owner;
        private final P11QualifiedSourceOwner.Body body;
        private final long epoch;
        private final Connection connection;
        private final SkillSubmissionRecoveryService.MetadataContinuation continuation;
        private P11ReceiptLedger.Source version;
        private P11QualifiedSourceOwner.SelectionWitness witness;
        private boolean closed;
        private MetadataLease(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                P11QualifiedSourceOwner.SelectionWitness witness, Connection connection,
                SkillSubmissionRecoveryService.MetadataContinuation continuation) {
            this.owner = owner; this.body = body; this.epoch = body.source.epoch();
            this.version = body.source; this.witness = witness; this.connection = connection;
            this.continuation = continuation;
        }
    }

    /** Exact canonical read, not an actor/source or writer grant. */
    public static boolean isManagedCanonicalAdvancements(PlayerAdvancements advancements, ServerPlayer actor) {
        var source = nativeSourceOwner(actor);
        return source != null && source.canonicalAssociated(advancements, actor);
    }

    /** Check transport before native send can enqueue on a closed connection. Prepared B is legal. */
    public static boolean nativeDeliveryEligible(ServerPlayer actor) {
        if (actor == null || actor.connection == null) { return false; }
        var listener = actor.connection;
        var connection = listener.getConnection();
        return connection != null && connection.isConnected()
                && connection.getPacketListener() == listener && listener.isAcceptingMessages();
    }

    public static boolean managedRecipeReceiver(ServerPlayer actor) {
        var source = nativeSourceOwner(actor);
        return source != null && (source.body(actor) != null
                || provisionalActor(actor));
    }

    public static boolean detachedPresence(ServerPlayer actor) {
        var source = nativeSourceOwner(actor);
        return source != null && source.detachedPresence(actor);
    }

    /** Called at the exact native listener/map-removal callsite, before destructive removal. */
    public static boolean preserveCanonical(ServerPlayer actor, Object canonical) {
        var source = nativeSourceOwner(actor);
        if (source == null) { return false; }
        var body = canonical instanceof PlayerAdvancements advancements
                ? source.canonicalAdvancements(advancements)
                : canonical instanceof ServerStatsCounter stats ? source.canonicalStats(stats) : null;
        return body != null && body.actor.getUUID().equals(actor.getUUID());
    }

    private static boolean provisionalActor(ServerPlayer actor) {
        var copy = COPY.get();
        if (copy != null && copy.associatedActor == actor) { return true; }
        var configuration = CONFIGURATION.get();
        var selection = configuration == null ? null : configuration.selection;
        return selection != null && !selection.closed && selection.associatedActor == actor;
    }

    static boolean provisionalAssociation(PlayerAdvancements advancements, ServerPlayer actor) {
        var copy = COPY.get();
        if (copy != null && copy.associatedActor == actor && copy.previous.advancements == advancements) { return true; }
        var configuration = CONFIGURATION.get();
        var selection = configuration == null ? null : configuration.selection;
        return selection != null && !selection.closed && selection.associatedActor == actor
                && selection.previous.advancements == advancements;
    }

    public static boolean observeAssociation(PlayerAdvancements advancements, ServerPlayer previous, ServerPlayer next) {
        var source = nativeSourceOwner(next);
        if (source == null) { return true; }
        var body = source.canonicalAdvancements(advancements);
        if (body == null || previous == next) { return true; }
        if (body.actor != previous) { return false; }
        var copy = COPY.get();
        if (copy != null && copy.owner == source && copy.old == previous && copy.association == null) {
            copy.associatedActor = next;
            copy.association = new P11ProvisionalAssociation(advancements, previous, next,
                    body.source, body.source.epoch(), body.source.version());
            return true;
        }
        var configuration = CONFIGURATION.get();
        var selection = configuration == null ? null : configuration.selection;
        if (selection != null && !selection.closed && selection.owner == source
                && selection.previous == body && selection.constructorStarted && selection.association == null) {
            selection.associatedActor = next;
            selection.association = new P11ProvisionalAssociation(advancements, previous, next,
                    body.source, body.source.epoch(), body.source.version());
            return true;
        }
        return false;
    }

    static void nativeEscape(ServerPlayer actor) {
        var copy = COPY.get();
        if (copy != null && copy.associatedActor == actor && copy.association != null) {
            copy.association.effectOrUnknownEscape();
        }
        var configuration = CONFIGURATION.get();
        var selection = configuration == null ? null : configuration.selection;
        if (selection != null && selection.associatedActor == actor && selection.association != null) {
            selection.association.effectOrUnknownEscape();
        }
    }

    /** Exact named constructor callback seam; absence of a log is not no-escape evidence. */
    public static void constructorCallback(ServerPlayer actor) { nativeEscape(actor); }

    /** Required native respawn material prefix, independent from callback/caller readiness. */
    public static void respawnMaterial(ServerPlayer player) {
        var copy = COPY.get();
        if (copy != null && copy.next != null && copy.next.actor == player) {
            copy.owner.callerComplete(copy.next);
        }
    }

    public static void preConstructorCleanupReturned(ServerPlayer old) {
        var copy = COPY.get();
        if (copy != null && copy.old == old) { copy.cleanupLegal = true; }
    }

    public static void nativeCleanupResult(ServerPlayer actor, P11NativeCleanup.Result result) {
        var source = nativeSourceOwner(actor);
        if (source != null && result != null && result.matches(actor)) {
            source.nativeCleanup(source.body(actor), result.structuralComplete());
        }
    }

    private static boolean withdrawAssociation(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body previous, P11ProvisionalAssociation association,
            ServerPlayer candidate, boolean cleanupLegal) {
        if (association == null || candidate == null || previous == null) { return false; }
        var canonical = previous.advancements;
        var receiver = ((P11CanonicalAdvancements.Access) canonical).p11$associatedPlayer();
        // Removed A needs its already established original logout envelope, never a newly
        // invented respawn capsule. An opaque/native teardown cannot establish this branch.
        return association.withdraw(canonical, receiver, previous.source,
                previous.source.epoch(), previous.source.version(),
                source.associationSourceCurrent(previous),
                cleanupLegal && (!previous.actor.isRemoved() || previous.envelope != null),
                () -> canonical.setPlayer(previous.actor));
    }

    private static P11QualifiedSourceOwner lifecycleOwner(ServerPlayer actor) {
        // Use the exact server/thread guard before inspecting the managed account map.
        var source = root == null ? null : root.writerOwner(actor.getServer());
        if (source != null) {
            requireManagedLifecycleAccess(source.hasAccount(actor.getUUID()), source.body(actor) != null);
        }
        return source;
    }

    /** Unmanaged UUIDs remain native; a managed UUID cannot fall through an unmatched hook. */
    static void requireManagedLifecycleAccess(boolean managed, boolean exactOwnerOrScope) {
        if (managed && !exactOwnerOrScope) { throw unavailable(); }
    }

    static P11QualifiedSourceOwner.Diagnostics diagnostics(MinecraftServer server, java.util.UUID playerId) {
        var source = root == null ? null : root.sourceOwner(server);
        if (source == null) { throw new IllegalStateException("P11_SOURCE_NOT_ACTIVE"); }
        return source.diagnostics(playerId);
    }

    static P11QualifiedSourceOwner.Summary terminalDiagnostics() {
        return root == null ? null : root.terminalSummary();
    }

    static long observerFailureCount() { return observerFailures; }

    /** Owns the factory-to-placement gap, including original catch/early-return/error paths. */
    public static void configurationFinished(ServerConfigurationPacketListenerImpl listener,
            ServerboundFinishConfigurationPacket packet, Operation<Void> original) {
        if (!listener.getMainThreadEventLoop().isSameThread()) { original.call(packet); return; }
        var previous = CONFIGURATION.get();
        var scope = new ConfigurationScope(listener, previous);
        CONFIGURATION.set(scope);
        try { P11LiveTransitionBoundary.configurationFinished(listener, packet, original); }
        finally {
            try { closeSelection(scope.selection); }
            finally {
                if (previous == null) { CONFIGURATION.remove(); } else { CONFIGURATION.set(previous); }
            }
        }
    }

    /** A newly admitted parked attempt uses only this synchronous factory-to-placement scope. */
    public static void parkedConfiguration(ServerCommonPacketListenerImpl listener, GameProfile profile,
            ClientInformation information, Runnable originalTail) {
        if (P11LiveTransitionBoundary.currentNativeAttempt(listener) == null
                || !listener.getMainThreadEventLoop().isSameThread()
                || !listener.getConnection().isConnected()
                || listener.getConnection().getPacketListener() != listener
                || !listener.getOwner().getId().equals(profile.getId())) { throw unavailable(); }
        java.util.Objects.requireNonNull(information, "information");
        java.util.Objects.requireNonNull(originalTail, "originalTail");
        var previous = CONFIGURATION.get();
        var scope = new ConfigurationScope(listener, previous);
        CONFIGURATION.set(scope);
        try { originalTail.run(); }
        finally {
            try { closeSelection(scope.selection); }
            finally {
                if (previous == null) { CONFIGURATION.remove(); } else { CONFIGURATION.set(previous); }
            }
        }
    }

    /** Select before the original constructor can install successor JSON owners in native maps. */
    public static ServerPlayer loginPlayer(net.minecraft.server.players.PlayerList list,
            GameProfile profile, ClientInformation information, Operation<ServerPlayer> original) {
        P11LiveTransitionBoundary.requireFactoryTicket(list, profile, information);
        var source = root == null ? null : root.writerOwner(list);
        var previous = source == null ? null : source.current(profile.getId());
        if (previous == null) {
            var actor = original.call(profile, information);
            P11LiveTransitionBoundary.expectedActor(actor);
            return actor;
        }
        var context = CONFIGURATION.get();
        if (context == null || context.selection != null
                || context.listener.getMainThreadEventLoop() != previous.actor.getServer()
                || !context.listener.getOwner().getId().equals(profile.getId())) { throw unavailable(); }
        for (var parent = context.previous; parent != null; parent = parent.previous) {
            if (parent.selection != null && parent.selection.owner == source
                    && parent.selection.previous.actor.getUUID().equals(profile.getId())) { throw unavailable(); }
        }
        var selection = new LoginSelection(source, previous);
        if (!source.retainNativeRoot(previous, P11ControlBudgets.Root.TRANSITION)) { throw unavailable(); }
        context.selection = selection;
        boolean normal = false;
        try {
            if (!source.canSerialize(previous)) { throw unavailable(); }
            source.flushIndependentBeforeLogin(previous);
            source.reconcileCooldown(previous);
            selection.version = previous.source;
            selection.lineage = source.lineage(previous).orElseThrow(P11NativeStorageBoundary::unavailable);
            selection.memory = source.seal(previous, selectedMaterial(previous));
            if (selection.memory != null) { selection.bodyMemory = source.sealForSelectedInput(selection.memory); }
            if (selection.bodyMemory == null) {
                source.release(selection.memory);
                selection.memory = null;
                if (!source.canSerialize(previous)) { throw unavailable(); }
                selection.primary = synchronousPrimary(source, previous);
            }
            selection.independent = source.beginLoginIndependent(profile.getId());
            source.constructorStarted(selection.independent);
            selection.constructorStarted = true;
            var actor = original.call(profile, information);
            P11LiveTransitionBoundary.expectedActor(actor);
            source.finishLoginIndependent(selection.independent, actor);
            // Constructor callbacks may publish or mutate native fields. Never select a stale
            // root merely because an e/v pair failed to observe an ordinary vanilla change.
            if (!source.canInspectConstructedPredecessor(previous) || previous.source != selection.version) { throw unavailable(); }
            var latest = selectedMaterial(previous);
            NbtUtils.addCurrentDataVersion(latest);
            var selected = selection.memory == null ? selection.primary.material : selection.memory.root;
            if (!selected.equals(latest) || !source.canInspectConstructedPredecessor(previous)
                    || previous.source != selection.version
                    || (selection.primary != null
                            && !source.completedPlayerWrite(previous, selection.primary.receipt))) {
                throw unavailable();
            }
            selection.actor = actor;
            normal = true;
            return actor;
        } finally {
            if (!normal) {
                closeSelection(selection);
                if (!selection.constructorStarted) { retainWithoutReplacingPrimary(source, previous); }
            }
        }
    }

    private static void closeSelection(LoginSelection selection) {
        if (selection == null || selection.closed) { return; }
        selection.closed = true;
        try {
            try { selection.owner.release(selection.bodyMemory); }
            finally {
                try { selection.owner.release(selection.memory); }
                finally {
                    if (selection.primary != null) { selection.primary.clear(); }
                    boolean withdrawn = !selection.consumed && withdrawAssociation(selection.owner,
                            selection.previous, selection.association, selection.associatedActor,
                            selection.previous.envelope != null);
                    selection.owner.abortLoginIndependent(selection.independent, selection.associatedActor, withdrawn);
                }
            }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        } finally {
            selection.owner.releaseNativeRoot(selection.previous, P11ControlBudgets.Root.TRANSITION);
        }
    }

    public static void constructed(ServerPlayer player) {
        var copy = COPY.get();
        if (copy != null && copy.old.getServer() == player.getServer()
                && copy.old.getUUID().equals(player.getUUID())) {
            var oldBody = copy.owner.body(copy.old);
            if (!copy.owner.canCopy(oldBody)) { throw unavailable(); }
            // Position callbacks in original respawn precede this constructor and may legally
            // publish new data. Capture the latest lineage before candidate custody changes.
            copy.lineage = copy.owner.lineage(oldBody).orElseThrow(P11NativeStorageBoundary::unavailable);
            copy.next = copy.owner.candidate(player);
        }
    }

    /** The unique native respawn NEW returned this B, including an ordinary T-full body. */
    public static void respawnConstructed(ServerPlayer player) {
        P11LiveTransitionBoundary.expectedActor(player);
        var copy = COPY.get();
        if (copy != null) {
            if (copy.next == null || copy.next.actor != player
                    || !P11LiveTransitionBoundary.adoptSourceBody(copy.next, copy.owner)) { throw unavailable(); }
        }
    }

    public static void place(MinecraftServer server, Connection connection, ServerPlayer player,
            CommonListenerCookie cookie, Operation<Void> original) {
        var source = root == null ? null : root.sourceOwner(server);
        // Constructor identity alone is not admission. This is the real configuration→PLAY call.
        var configuration = CONFIGURATION.get();
        boolean authenticated = connection.isConnected()
                && configuration != null && connection.getPacketListener() == configuration.listener
                && configuration.listener.getConnection() == connection
                && configuration.listener.getMainThreadEventLoop() == server
                && configuration.listener.getOwner().getId().equals(player.getUUID())
                && P11LiveTransitionBoundary.currentNativeAttempt(configuration.listener) != null;
        if (source == null || !authenticated) {
            if (source != null && source.account(player) != null) { throw unavailable(); }
            original.call(connection, player, cookie);
            return;
        }
        if (LOAD.get() != null || PRIMARY_READ.get() != null) { throw unavailable(); }
        var account = source.account(player);
        LoginSelection selection = null;
        LoadScope scope = null;
        boolean normal = false;
        try {
            if (account != null && account.current != null && account.current.actor != player) {
                var context = CONFIGURATION.get();
                selection = context == null ? null : context.selection;
                if (selection == null || selection.closed || selection.consumed
                        || selection.owner != source || selection.actor != player
                        || selection.previous != account.current || selection.version != account.current.source
                        || !source.canInspectConstructedPredecessor(account.current)
                        || connection.getPacketListener() != context.listener) { throw unavailable(); }
                selection.consumed = true;
            }
            var body = source.candidate(player);
            if (body == null) { original.call(connection, player, cookie); normal = true; return; }
            if (!P11LiveTransitionBoundary.adoptSourceBody(body, source)) { throw unavailable(); }
            scope = new LoadScope(source, body, selection == null ? null : selection.memory,
                    selection == null ? null : selection.bodyMemory,
                    selection == null ? null : selection.primary, selection == null ? null : selection.lineage);
            LOAD.set(scope);
            original.call(connection, player, cookie);
            normal = true;
        } finally {
            LOAD.remove();
            closeSelection(selection);
            if (scope != null && !normal) {
                lifecycleFailureWithoutReplacingPrimary(source, scope.body);
                faultLogoutAfterAttempt(source, scope.body);
            }
        }
    }

    public static Optional<CompoundTag> load(MinecraftServer server, ServerPlayer player,
            Operation<Optional<CompoundTag>> original) {
        var scope = LOAD.get();
        if (scope == null || scope.body.actor != player) {
            var source = owner(player);
            if (source != null && source.account(player) != null) { throw unavailable(); }
            return original.call(player);
        }
        if (scope.loadStarted) { throw unavailable(); }
        scope.loadStarted = true;
        if (scope.memory != null) {
            if (!scope.owner.selectedInputCurrent(scope.memory)
                    || !scope.owner.selectedInputCurrent(scope.bodyMemory)) { throw unavailable(); }
            scope.kind = P11QualifiedSourceOwner.InputKind.MEMORY;
            scope.body.inputKind = scope.kind;
            scope.input = root.provenance().volatileInput(scope.lineage);
            // The original coherent root and the added immutable copy were reserved before B
            // mutation. The caller view is never obtained by copying B's post-load aliases.
            var callerInput = scope.memory.root;
            var bodyInput = scope.bodyMemory.root;
            loadBody(player, bodyInput, () -> player.load(bodyInput));
            return Optional.of(callerInput);
        }
        if (scope.preparedPrimary != null) {
            // This explicitly selects the verified physical primary, never stale host cache.
            var request = scope.preparedPrimary;
            ((P11NativeWorldAccess.PlayerStorage) server.getPlayerList()).p11$loadPreparedPrimary(request);
            return request.result;
        }
        var result = original.call(player);
        if (result.isEmpty()) {
            if (scope.primary != ReadState.ABSENT || scope.backup != ReadState.ABSENT
                    || !hostAbsenceKnown(server, player)) {
                scope.owner.fail(scope.body, P11QualifiedSourceOwner.Fault.READ);
                throw unavailable();
            }
            scope.kind = P11QualifiedSourceOwner.InputKind.ABSENT;
            scope.body.inputKind = scope.kind;
            scope.input = root.provenance().absentInput();
            scope.owner.beginInput(scope.body, scope.input);
            scope.readResult = scope.owner.missingInput(scope.body);
            scope.owner.loaded(scope.body, scope.input, scope.readResult);
            scope.owner.loadReturned(scope.body, true);
        }
        return result;
    }

    private static CompoundTag selectedMaterial(P11QualifiedSourceOwner.Body body) {
        if (SELECTED_SERIALIZE.get() != null) { throw unavailable(); }
        SELECTED_SERIALIZE.set(body);
        try { return body.actor.saveWithoutId(new CompoundTag()); }
        finally { SELECTED_SERIALIZE.remove(); }
    }

    private static PrimaryReadRequest synchronousPrimary(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body previous) {
        if (PRIMARY_READ.get() != null || WRITE.get() != null || CACHE.get() != null) { throw unavailable(); }
        source.reconcileCooldown(previous);
        var request = new PrimaryReadRequest(source, previous);
        PRIMARY_READ.set(request);
        boolean complete = false;
        try {
            ((P11NativeWorldAccess.PlayerStorage) previous.actor.getServer().getPlayerList())
                    .p11$preparePrimary(request);
            if (request.stage != PrimaryStage.VERIFIED || request.material == null) { throw unavailable(); }
            complete = true;
            return request;
        } finally {
            PRIMARY_READ.remove();
            if (!complete) {
                request.clear();
                retainWithoutReplacingPrimary(source, previous);
            }
        }
    }

    /** Internal opaque bridge: invokes the original virtual PlayerList.save, never another writer. */
    public static void preparePrimary(PlayerList list, PlayerDataStorage storage,
            PrimaryReadRequest request, Operation<Void> originalSave) {
        if (request == null || PRIMARY_READ.get() != request || request.stage != PrimaryStage.CREATED
                || list != request.previous.actor.getServer().getPlayerList()
                || root == null || root.playerStorageOwner(storage, request.previous.actor) != request.owner
                || !((P11NativeWorldAccess.PlayerStorage) list).p11$independentOwnersMatch(request.previous.actor)
                || !request.owner.canSerialize(request.previous)) { throw unavailable(); }
        request.storage = storage;
        request.stage = PrimaryStage.SAVING;
        originalSave.call(request.previous.actor);
        if (request.duplicateWriter || !request.owner.completedPlayerWrite(request.previous, request.receipt)) {
            throw unavailable();
        }
        request.stage = PrimaryStage.READ_REQUESTED;
        ((P11NativeWorldAccess.PrimaryReader) storage).p11$readPrimary(request);
        if (request.stage != PrimaryStage.READ || request.material == null
                || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
        // Saving callbacks can change ordinary native fields without a Gramarye version bump.
        // Compare the real disk read to a fresh serializer result after the entire save tail.
        var latest = selectedMaterial(request.previous);
        NbtUtils.addCurrentDataVersion(latest);
        if (!request.material.equals(latest)
                || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
        request.witness = request.owner.captureSelection(request.previous);
        request.stage = PrimaryStage.VERIFIED;
    }

    /** Only the original private .dat reader may fill this root-owned request. No DFU/load/event here. */
    public static void readPrimary(PlayerDataStorage storage, PrimaryReadRequest request,
            Operation<Optional<CompoundTag>> originalRead) {
        if (request == null || PRIMARY_READ.get() != request || request.storage != storage
                || request.stage != PrimaryStage.READ_REQUESTED
                || root == null || root.playerStorageOwner(storage, request.previous.actor) != request.owner
                || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
        request.stage = PrimaryStage.READING;
        var result = originalRead.call(request.previous.actor, ".dat");
        if (!request.readEntered || request.readState != ReadState.READ || result.isEmpty()) { throw unavailable(); }
        request.material = result.orElseThrow();
        request.stage = PrimaryStage.READ;
    }

    /** The original public load supplies DFU, one body load, one event and its same caller result. */
    public static void loadPreparedPrimary(PlayerList list, PlayerDataStorage storage,
            PrimaryReadRequest request) {
        var scope = LOAD.get();
        if (request == null || scope == null || scope.preparedPrimary != request
                || !scope.loadStarted || request.stage != PrimaryStage.VERIFIED
                || request.storage != storage || list != scope.body.actor.getServer().getPlayerList()
                || root == null || root.playerStorageOwner(storage, scope.body.actor) != scope.owner
                || !request.owner.completedPlayerWrite(request.previous, request.receipt, request.witness)) { throw unavailable(); }
        request.stage = PrimaryStage.CONSUMING;
        var result = storage.load(scope.body.actor);
        if (request.material != null || scope.primary != ReadState.READ || !scope.bodyLoaded
                || result.isEmpty()) { throw unavailable(); }
        request.result = result;
        request.stage = PrimaryStage.CONSUMED;
    }

    private static boolean hostAbsenceKnown(MinecraftServer server, ServerPlayer player) {
        if (!server.isSingleplayerOwner(player.getGameProfile())) { return true; }
        return root.hostInputKind(server) == P11QualifiedSourceOwner.InputKind.ABSENT;
    }

    public static Optional<CompoundTag> loadPlayer(PlayerDataStorage storage, Player player,
            Operation<Optional<CompoundTag>> original) {
        var scope = playerLoadScope(storage, player, false);
        if (scope == null) { return original.call(player); }
        if (scope.diskLoadActive || scope.bodyLoaded) { throw unavailable(); }
        scope.diskLoadActive = true;
        try { return original.call(player); }
        finally { scope.diskLoadActive = false; }
    }

    private static LoadScope playerLoadScope(PlayerDataStorage storage, Player player,
            boolean withinDiskLoad) {
        var source = root == null ? null : root.playerStorageOwner(storage, player);
        if (source == null || !source.hasAccount(player.getUUID())) { return null; }
        var scope = LOAD.get();
        if (scope == null || scope.owner != source || scope.body.actor != player
                || !scope.loadStarted || scope.memory != null
                || (withinDiskLoad && !scope.diskLoadActive)) { throw unavailable(); }
        return scope;
    }

    public static Optional<CompoundTag> readPlayer(PlayerDataStorage storage, Player player,
            File directory, String suffix,
            Operation<Optional<CompoundTag>> original) {
        var request = PRIMARY_READ.get();
        if (request != null && request.stage == PrimaryStage.READING) {
            if (request.storage != storage || request.previous.actor != player || !".dat".equals(suffix)
                    || request.readEntered || root == null
                    || root.playerStorageOwner(storage, player) != request.owner
                    || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
            request.readEntered = true;
            var state = readState(directory, player, suffix);
            var result = original.call(player, suffix);
            request.readState = readResult(state, result);
            return result;
        }
        var scope = playerLoadScope(storage, player, true);
        if (scope == null) { return original.call(player, suffix); }
        if (scope.preparedPrimary != null) {
            var prepared = scope.preparedPrimary;
            if (!".dat".equals(suffix) || prepared.storage != storage
                    || prepared.stage != PrimaryStage.CONSUMING || prepared.material == null
                    || !prepared.owner.completedPlayerWrite(prepared.previous, prepared.receipt, prepared.witness)) { throw unavailable(); }
            var material = prepared.material;
            prepared.material = null;
            scope.primary = ReadState.READ;
            return Optional.of(material);
        }
        var state = readState(directory, player, suffix);
        var result = original.call(player, suffix);
        state = readResult(state, result);
        if (".dat".equals(suffix)) { scope.primary = state; }
        else if (".dat_old".equals(suffix)) { scope.backup = state; }
        else { throw unavailable(); }
        return result;
    }

    private static ReadState readState(File directory, Player player, String suffix) {
        var path = directory.toPath().resolve(player.getStringUUID() + suffix);
        try {
            var attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return attributes.isRegularFile() ? ReadState.PRESENT : ReadState.ERROR;
        } catch (NoSuchFileException missing) {
            return ReadState.ABSENT;
        } catch (IOException denied) {
            return ReadState.ERROR;
        }
    }

    private static ReadState readResult(ReadState state, Optional<CompoundTag> result) {
        return result.isPresent() ? state == ReadState.PRESENT ? ReadState.READ : ReadState.ERROR
                : state == ReadState.ABSENT ? ReadState.ABSENT : ReadState.ERROR;
    }

    public static void diskLoad(PlayerDataStorage storage, Player player, CompoundTag input,
            Operation<Void> original) {
        var scope = playerLoadScope(storage, player, true);
        if (scope == null) { original.call(player, input); return; }
        if (scope.primary != ReadState.READ) {
            scope.owner.fail(scope.body, scope.backup == ReadState.READ
                    ? P11QualifiedSourceOwner.Fault.FALLBACK : P11QualifiedSourceOwner.Fault.READ);
            throw unavailable();
        }
        scope.kind = P11QualifiedSourceOwner.InputKind.PRIMARY;
        scope.body.inputKind = scope.kind;
        scope.input = root.provenance().persistedInput();
        loadBody(scope.body.actor, input, () -> original.call(player, input));
    }

    public static void hostLoad(ServerPlayer player, CompoundTag input, Operation<Void> original) {
        var scope = LOAD.get();
        if (scope == null || scope.body.actor != player) { original.call(player, input); return; }
        if (root.hostInputKind(player.getServer()) != P11QualifiedSourceOwner.InputKind.HOST_PRIMARY) {
            scope.owner.fail(scope.body, P11QualifiedSourceOwner.Fault.READ);
            throw unavailable();
        }
        scope.kind = P11QualifiedSourceOwner.InputKind.HOST_PRIMARY;
        scope.body.inputKind = scope.kind;
        scope.input = root.provenance().persistedInput();
        loadBody(player, input, () -> original.call(player, input));
    }

    private static void loadBody(ServerPlayer player, CompoundTag input, Runnable original) {
        var scope = LOAD.get();
        if (scope == null || scope.body.actor != player || scope.bodyLoaded) { throw unavailable(); }
        scope.bodyLoaded = true;
        scope.expectedSkills = input.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(SKILLS.toString());
        scope.expectedMana = input.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(MANA.toString());
        scope.expectedCooldown = input.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(COOLDOWNS.toString());
        if (input.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                && !input.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY, Tag.TAG_COMPOUND)) { throw unavailable(); }
        scope.owner.beginInput(scope.body, scope.input);
        if (!scope.expectedSkills) {
            scope.readResult = scope.owner.missingInput(scope.body);
            scope.owner.loaded(scope.body, scope.input, scope.readResult);
        }
        boolean normal = false;
        try {
            original.run();
            normal = true;
        } finally {
            if (normal) {
                boolean complete = (!scope.expectedSkills || scope.skillsRead)
                        && (!scope.expectedMana || scope.manaRead)
                        && (!scope.expectedCooldown || scope.cooldownRead);
                scope.owner.loadReturned(scope.body, complete);
            } else { failWithoutReplacingPrimary(scope.owner, scope.body, P11QualifiedSourceOwner.Fault.MATERIAL); }
        }
    }

    public static void callerMaterial(ServerPlayer player) {
        var scope = LOAD.get();
        if (scope != null && scope.body.actor == player) { scope.owner.callerComplete(scope.body); }
    }

    public static void readAttachments(AttachmentHolder holder, HolderLookup.Provider provider,
            CompoundTag input, Operation<Void> original) {
        original.call(provider, input);
        var scope = LOAD.get();
        if (scope != null && holder == scope.body.actor && scope.expectedSkills && scope.skillsRead) {
            scope.owner.loaded(scope.body, scope.input, scope.readResult);
        }
    }

    public static void playerSkillsReadCompleted(IAttachmentHolder holder,
            PlayerSkillAttachmentService.P11AttachmentReadResult readResult) {
        var scope = LOAD.get();
        if (scope != null && holder == scope.body.actor) {
            scope.skillsRead = root.provenance().readCompleted(scope.body.actor, readResult);
            scope.readResult = readResult;
        }
        var copy = COPY.get();
        if (copy != null && copy.next != null && holder == copy.next.actor) {
            copy.readResult = readResult;
            copy.skillsRead = root.provenance().readCompleted(copy.next.actor, readResult);
        }
    }

    public static void manaReadCompleted(IAttachmentHolder holder, P11ManaMaterial.Read result) {
        var scope = LOAD.get();
        if (scope != null && holder == scope.body.actor) {
            scope.manaRead = !scope.manaReadObserved && scope.owner.manaRead(scope.body, result);
            scope.manaReadObserved = true;
        }
        var copy = COPY.get();
        if (copy != null && copy.next != null && holder == copy.next.actor) {
            copy.manaRead = !copy.manaReadObserved && copy.owner.manaRead(copy.next, result);
            copy.manaReadObserved = true;
        }
    }

    public static void manaPublished(ServerPlayer actor,
            P11ManaMaterial.Publication publication) {
        var source = owner(actor);
        var body = source == null ? null : source.body(actor);
        if (body != null) { source.manaPublication(body, publication); }
    }

    static void cooldownReadCompleted(IAttachmentHolder holder, P11CastCooldownMaterial.State result) {
        var scope = LOAD.get();
        if (scope != null && holder == scope.body.actor) {
            scope.cooldownRead = !scope.cooldownReadObserved && scope.owner.cooldownRead(scope.body, result);
            scope.cooldownReadObserved = true;
        }
        var copy = COPY.get();
        if (copy != null && copy.next != null && holder == copy.next.actor) {
            copy.cooldownRead = !copy.cooldownReadObserved && copy.owner.cooldownRead(copy.next, result);
            copy.cooldownReadObserved = true;
        }
    }

    static void cooldownPublished(P11CastCooldownMaterial.State before, P11CastCooldownMaterial.State after) {
        if (before == null || after == null || before.actor != after.actor) { throw unavailable(); }
        var source = owner(after.actor);
        var body = source == null ? null : source.body(after.actor);
        if (body == null) { throw unavailable(); }
        source.cooldownPublication(body, before, after);
    }

    static void cooldownWritten(P11CastCooldownMaterial.Write result) {
        var scope = SERIALIZE.get();
        if (scope != null && scope.attachments != null && scope.attachments.holder == scope.body.actor) {
            scope.attachments.cooldown = result;
        }
    }

    public static void playerSkillsWritten(
            PlayerSkillAttachmentService.P11AttachmentWriteResult result) {
        var scope = SERIALIZE.get();
        if (scope != null && scope.attachments != null
                && scope.attachments.holder == scope.body.actor) {
            scope.attachments.skills = result;
        }
    }

    public static void manaWritten(P11ManaMaterial.Write result) {
        var scope = SERIALIZE.get();
        if (scope != null && scope.attachments != null
                && scope.attachments.holder == scope.body.actor) {
            scope.attachments.mana = result;
        }
    }

    public static CompoundTag writeAttachments(AttachmentHolder holder,
            HolderLookup.Provider provider, Operation<CompoundTag> original) {
        var scope = SERIALIZE.get();
        if (scope == null) { return original.call(provider); }
        var previous = scope.attachments;
        var observed = new AttachmentWriteScope(holder);
        scope.attachments = observed;
        try {
            boolean exactHolder = holder == scope.body.actor;
            boolean skills = exactHolder && holder.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS));
            boolean mana = exactHolder && holder.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(MANA));
            boolean cooldown = exactHolder && holder.hasData(P11CastCooldownAttachments.TYPE);
            var result = original.call(provider);
            if (exactHolder) {
                scope.requiredComplete &= (!skills || (result != null && observed.skills != null
                        && observed.skills.matches(scope.body.actor, result.get(SKILLS.toString()))))
                        && (!mana || (result != null && observed.mana != null
                        && observed.mana.matches(scope.body.actor, result.get(MANA.toString()))))
                        && (!cooldown || (result != null && observed.cooldown != null
                        && observed.cooldown.matches(scope.body.actor, result.get(COOLDOWNS.toString()))));
            }
            return result;
        } finally { scope.attachments = previous; }
    }

    public static void brainEncoded(LivingEntity actor, boolean complete) {
        var scope = SERIALIZE.get();
        if (scope != null && scope.body.actor == actor) {
            scope.brainObserved = true;
            scope.requiredComplete &= complete;
        }
    }

    public static CompoundTag serialize(Entity entity, CompoundTag input, Operation<CompoundTag> original) {
        if (!(entity instanceof ServerPlayer actor)) { return original.call(input); }
        var source = owner(actor);
        var body = source == null ? null : source.body(actor);
        if (body == null) {
            if (source != null && source.account(actor) != null) { throw unavailable(); }
            return original.call(input);
        }
        boolean selectedComparison = SELECTED_SERIALIZE.get() == body
                && source.canInspectConstructedPredecessor(body);
        if (!selectedComparison) { source.reconcileCooldown(body); }
        if ((!source.canSerialize(body) && !selectedComparison) || SERIALIZE.get() != null) { throw unavailable(); }
        var scope = new SerializeScope(source, body);
        SERIALIZE.set(scope);
        boolean normal = false;
        long started = System.nanoTime();
        try {
            var result = original.call(input);
            if (!scope.requiredComplete || !scope.brainObserved || body.source != scope.version
                    || !(source.canSerialize(body) || (selectedComparison
                            && source.canInspectConstructedPredecessor(body)))) {
                source.fail(body, P11QualifiedSourceOwner.Fault.MATERIAL);
                throw unavailable();
            }
            var writer = WRITE.get();
            if (body.envelope != null && !body.logoutActive) {
                // Retained lifecycle fields never escape through an arbitrary serializer call.
                // Owned physical consumers only read; selected load copies under reservation.
                if ((writer == null || writer.body != body) && SELECTED_SERIALIZE.get() != body
                        && !detachedStopCacheEnvelope(body)) {
                    throw unavailable();
                }
                body.envelope.apply(result);
            }
            if (body.logoutActive && writer != null && writer.body == body
                    && writer.kind == P11ReceiptLedger.WriterKind.PLAYER_DATA) {
                body.pendingEnvelope = new P11QualifiedSourceOwner.Envelope(result);
            }
            if (writer != null && writer.body == body) { writer.material = result; }
            normal = true;
            return result;
        } finally {
            SERIALIZE.remove();
            source.serialized(System.nanoTime() - started);
            if (!normal) { failWithoutReplacingPrimary(source, body, P11QualifiedSourceOwner.Fault.MATERIAL); }
        }
    }

    /** The named whole game-listener exit, not removePlayerFromWorld's shared helper. */
    public static void normalLogout(ServerGamePacketListenerImpl listener,
            DisconnectionDetails details, Operation<Void> original) {
        var actor = listener.player;
        var foundation = root;
        var source = actor == null ? null : owner(actor);
        var body = source == null ? null : source.body(actor);
        NormalLogoutProof proof = null;
        SkillRuntimeService.NormalLogoutScope work = null;
        try {
            if (foundation != null && actor != null && actor.getServer().isSameThread()
                    && actor.connection == listener && listener.getConnection().getPacketListener() == listener
                    && source != null && source.canCopy(body) && !body.logoutAttempted
                    && !body.logoutActive && !actor.isFakePlayer() && actor.isAlive() && !actor.isRemoved()
                    && actor.getServer().getPlayerList().getPlayer(actor.getUUID()) == actor
                    && P11LiveTransitionBoundary.nativeContinuity(source) != null) {
                proof = new NormalLogoutProof(source, body, NORMAL_LOGOUT.get());
                NORMAL_LOGOUT.set(proof);
                work = foundation.beginNormalLogout(listener, actor);
            }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
        boolean normal = false;
        try { original.call(details); normal = true; }
        finally {
            try {
                if (foundation != null) { foundation.endNormalLogout(work, proof, normal); }
            } catch (RuntimeException | Error secondary) {
                if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
            } finally {
                if (proof != null) {
                    proof.closed = true;
                    if (NORMAL_LOGOUT.get() == proof) {
                        if (proof.previous == null) { NORMAL_LOGOUT.remove(); }
                        else { NORMAL_LOGOUT.set(proof.previous); }
                    }
                }
            }
        }
    }

    /** Only this call-local native wrapper can mint/complete it; P5 cannot cache the proof. */
    static final class NormalLogoutProof {
        private final P11QualifiedSourceOwner source;
        private final P11QualifiedSourceOwner.Body body;
        private final long epoch;
        private final NormalLogoutProof previous;
        private boolean removeObserved;
        private boolean wholeRemove;
        private boolean closed;

        private NormalLogoutProof(P11QualifiedSourceOwner source,
                P11QualifiedSourceOwner.Body body, NormalLogoutProof previous) {
            this.source = source; this.body = body; this.epoch = body.source.epoch(); this.previous = previous;
        }

        boolean confirms(MinecraftServer server, ServerPlayer exactA) {
            return !closed && NORMAL_LOGOUT.get() == this && removeObserved && wholeRemove
                    && body.actor == exactA && source.owns(server)
                    && P11LiveTransitionBoundary.nativeContinuity(source) != null
                    && body.source.epoch() == epoch && source.canCopy(body)
                    && !body.logoutActive && body.logoutAttempted && body.envelope != null
                    && server.getPlayerList().getPlayer(exactA.getUUID()) != exactA;
        }

        private void removed(P11QualifiedSourceOwner exactSource, P11QualifiedSourceOwner.Body exactBody,
                P11NativeCleanup.LogoutOutcome outcome) {
            if (closed || NORMAL_LOGOUT.get() != this || source != exactSource || body != exactBody) { return; }
            wholeRemove = !removeObserved && outcome == P11NativeCleanup.LogoutOutcome.WHOLE_NATIVE_COMPLETED;
            removeObserved = true;
        }
    }

    public static void remove(ServerPlayer player, Operation<Void> original) {
        var source = lifecycleOwner(player);
        var body = source == null ? null : source.body(player);
        if (body == null) { original.call(player); return; }
        if (body.logoutActive || body.logoutAttempted) { throw unavailable(); }
        if (!source.retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)) { throw unavailable(); }
        body.logoutAttempted = true;
        body.logoutActive = true;
        releaseMetadata(body.account.metadata);
        var cleanup = P11NativeCleanup.beginLogout(player);
        boolean normal = false;
        try { original.call(player); normal = true; }
        finally {
            var outcome = P11NativeCleanup.finishLogout(cleanup, normal);
            body.logoutActive = false;
            if (normal && body.pendingEnvelope != null) { body.envelope = body.pendingEnvelope; }
            body.pendingEnvelope = null;
            if (outcome != P11NativeCleanup.LogoutOutcome.WHOLE_NATIVE_COMPLETED) {
                lifecycleFailureWithoutReplacingPrimary(source, body);
            }
            var normalLogout = NORMAL_LOGOUT.get();
            if (normalLogout != null) { normalLogout.removed(source, body, outcome); }
            source.releaseNativeRoot(body, P11ControlBudgets.Root.TRANSITION);
        }
    }

    public static ServerPlayer respawn(PlayerList list, ServerPlayer old, boolean keepEverything,
            Entity.RemovalReason reason, Operation<ServerPlayer> original) {
        P11LiveTransitionBoundary.requireRespawnTicket(list, old, keepEverything);
        var source = lifecycleOwner(old);
        var body = source == null ? null : source.body(old);
        if (body == null) { return original.call(old, keepEverything, reason); }
        if (COPY.get() != null || !source.canCopy(body)) { throw unavailable(); }
        source.reconcileCooldown(body);
        var lineage = source.lineage(body).orElseThrow(P11NativeStorageBoundary::unavailable);
        var scope = new CopyScope(source, old, lineage);
        if (!source.retainNativeRoot(body, P11ControlBudgets.Root.TRANSITION)) { throw unavailable(); }
        COPY.set(scope);
        boolean normal = false;
        try {
            var result = original.call(old, keepEverything, reason);
            if (scope.next == null || scope.next.actor != result) { throw unavailable(); }
            source.callerComplete(scope.next);
            normal = true;
            return result;
        } finally {
            COPY.remove();
            if (!normal) {
                if (scope.next != null) {
                    lifecycleFailureWithoutReplacingPrimary(source, scope.next);
                    faultLogoutAfterAttempt(source, scope.next);
                } else {
                    try {
                        if (!withdrawAssociation(source, body, scope.association,
                                scope.associatedActor, scope.cleanupLegal)) {
                            source.constructorEscaped(body, scope.associatedActor);
                        }
                    }
                    catch (RuntimeException | Error secondary) {
                        if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
                    }
                }
            }
            source.releaseNativeRoot(body, P11ControlBudgets.Root.TRANSITION);
        }
    }

    public static void copy(ServerPlayer next, ServerPlayer old, boolean keepEverything,
            Operation<Void> original) {
        var scope = COPY.get();
        if (scope == null || scope.old != old || scope.next == null || scope.next.actor != next) {
            var source = root == null ? null : root.writerOwner(next.getServer());
            requireManagedLifecycleAccess(source != null && source.hasAccount(next.getUUID()), false);
            original.call(old, keepEverything); return;
        }
        if (scope.lineage == null) {
            scope.owner.fail(scope.next, P11QualifiedSourceOwner.Fault.MATERIAL);
            throw unavailable();
        }
        if (scope.association != null) { scope.association.copyStarted(); }
        var input = root.provenance().volatileInput(scope.lineage);
        scope.next.inputKind = P11QualifiedSourceOwner.InputKind.MEMORY;
        scope.owner.beginInput(scope.next, input);
        boolean skills = old.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS));
        boolean mana = old.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(MANA));
        boolean cooldown = old.hasData(P11CastCooldownAttachments.TYPE);
        if (!skills) { scope.readResult = scope.owner.missingInput(scope.next); }
        original.call(old, keepEverything);
        scope.owner.loaded(scope.next, input, scope.readResult);
        scope.owner.loadReturned(scope.next, (!skills || scope.skillsRead) && (!mana || scope.manaRead)
                && (!cooldown || scope.cooldownRead));
    }

    private static P11QualifiedSourceOwner.SourceUnavailable unavailable() {
        return new P11QualifiedSourceOwner.SourceUnavailable();
    }

    private static void failWithoutReplacingPrimary(P11QualifiedSourceOwner owner,
            P11QualifiedSourceOwner.Body body, P11QualifiedSourceOwner.Fault reason) {
        try { owner.fail(body, reason); }
        catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    private static void lifecycleFailureWithoutReplacingPrimary(P11QualifiedSourceOwner owner,
            P11QualifiedSourceOwner.Body body) {
        try { owner.nativeLifecycleFailed(body); }
        catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    /** A new exact-B native logout, only after the failed copy attempt has unwound. */
    private static void faultLogoutAfterAttempt(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body) {
        var actor = body.actor;
        var server = actor.getServer();
        if (COPY.get() != null || !body.complete || body.logoutAttempted || body.logoutActive
                || source.body(actor) != body || body.account.current != body
                || body.account.candidate != null || actor.isRemoved()
                || server.getPlayerList().getPlayer(actor.getUUID()) != actor
                || server.getPlayerList().getPlayers().stream().noneMatch(value -> value == actor)
                || actor.serverLevel().getEntity(actor.getId()) != actor
                || actor.serverLevel().getEntity(actor.getUUID()) != actor) { return; }
        try {
            // Do not use listener.player: the original caller has not assigned B yet.
            server.getPlayerList().remove(actor);
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    private static void retainWithoutReplacingPrimary(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body) {
        try { source.retainSynchronousDuty(body); }
        catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    public static void savePlayer(PlayerDataStorage storage, Player player, Operation<Void> original) {
        var source = root == null ? null : root.playerStorageOwner(storage, player);
        var body = source != null && player instanceof ServerPlayer actor ? source.body(actor) : null;
        boolean managed = source != null && player instanceof ServerPlayer actor && source.account(actor) != null;
        var previous = WRITE.get();
        var stop = DETACHED_STOP_SAVE.get();
        boolean stopWriter = stop != null && stop.entered && !stop.closed
                && stop.owner == source && stop.body == body && stop.storage == storage;
        if (stopWriter) {
            if (stop.playerWriterEntered) { stop.duplicateWriter = true; return; }
            stop.playerWriterEntered = true;
            if (!detachedStopRequestCurrent(stop)) { return; }
        }
        if (managed && previous != null) { return; }
        var request = PRIMARY_READ.get();
        boolean synchronous = request != null && request.stage == PrimaryStage.SAVING
                && request.previous == body && request.storage == storage;
        var receipt = !managed ? null : synchronous ? source.beginSynchronousPlayerWriter(body)
                : source.beginWriter(body, P11ReceiptLedger.WriterKind.PLAYER_DATA);
        if (stopWriter) { stop.receipt = receipt; }
        if (managed && receipt == null) { return; } // Do not truncate the caller's stats/PA tail.
        if (synchronous) {
            if (request.receipt != null) { request.duplicateWriter = true; }
            else { request.receipt = receipt; }
        }
        // An unmanaged original writer still masks a surrounding scope's callbacks.
        var scope = new WriteScope(source, body, receipt, previous);
        WRITE.set(scope);
        boolean normal = false;
        try { original.call(player); normal = true; }
        finally { scope.facts.nativeReturned(normal); finish(scope); }
    }

    public static WriteScope beginNbtWrite(CompoundTag material, Path path) {
        var writer = WRITE.get();
        boolean managed = writer != null && writer.receipt != null && !writer.independent
                && writer.material == material && !writer.finished;
        if (managed && (IO.get() != null
                || !writer.owner.mayWrite(writer.body, writer.receipt))) {
            writer.facts.rejected();
            throw unavailable();
        }
        var scope = WriteScope.io(material, managed ? writer : null, IO.get(), false);
        if (managed) {
            writer.incoming = path;
            String name = writer.kind == P11ReceiptLedger.WriterKind.LEVEL_PLAYER
                    ? "level.dat" : writer.body.actor.getStringUUID() + ".dat";
            writer.currentPath = path.resolveSibling(name).toAbsolutePath().normalize();
            writer.backupPath = path.resolveSibling(name + "_old").toAbsolutePath().normalize();
        }
        IO.set(scope);
        return scope;
    }

    public static void endNbtWrite(WriteScope scope, boolean normal) {
        endIo(scope, normal, false);
    }

    public static WriteScope beginNbtStream(CompoundTag material) {
        var previous = IO.get();
        var writer = previous != null && !previous.stream && previous.ioOnly
                && previous.material == material ? previous.ioWriter : null;
        var scope = WriteScope.io(material, writer, previous, true);
        IO.set(scope);
        return scope;
    }

    public static void endNbtStream(WriteScope scope, boolean normal) {
        endIo(scope, normal, true);
    }

    private static void endIo(WriteScope scope, boolean normal, boolean stream) {
        if (scope == null || scope.finished || !scope.ioOnly || scope.stream != stream
                || IO.get() != scope) { return; }
        scope.finished = true;
        if (scope.previous == null) { IO.remove(); } else { IO.set(scope.previous); }
        var writer = scope.ioWriter;
        if (writer != null && WRITE.get() == writer && !writer.finished) {
            if (stream) { writer.facts.nativeReturned(normal); }
            else { writer.facts.ioReturned(normal); }
        }
    }

    private static WriteScope observedIoWriter() {
        var scope = IO.get();
        var writer = scope == null || scope.finished ? null : scope.ioWriter;
        return writer != null && !writer.finished && WRITE.get() == writer ? writer : null;
    }

    public static void nbtBodyWritten(boolean normal) {
        var writer = observedIoWriter();
        if (writer != null) { writer.facts.nbtBody(normal); }
    }

    public static void nbtStreamClosed(boolean normal) {
        var writer = observedIoWriter();
        if (writer != null) { writer.facts.closed(normal); }
    }

    public static void stringEncodingFallback() {
        var writer = observedIoWriter();
        if (writer != null) { writer.facts.encoded(false); }
    }

    public static boolean beforeReplace(Path current, Path incoming, Path backup, boolean noRestore) {
        var scope = WRITE.get();
        if (scope == null || scope.finished || scope.receipt == null || scope.independent) { return true; }
        scope.observingReplace = scope.incoming != null && scope.incoming.equals(incoming);
        if (!scope.observingReplace) { return true; } // An unrelated original writer is not ours.
        boolean permitted = !noRestore && current.toAbsolutePath().normalize().equals(scope.currentPath)
                && backup.toAbsolutePath().normalize().equals(scope.backupPath)
                && scope.facts.readyToReplace() && IO.get() == null
                && scope.owner.mayWrite(scope.body, scope.receipt);
        if (!permitted) { scope.observingReplace = false; scope.facts.rejected(); }
        return permitted;
    }

    public static void replaceResult(boolean actualResult) {
        var scope = WRITE.get();
        if (scope == null || scope.finished || !scope.observingReplace) { return; }
        scope.observingReplace = false;
        scope.facts.replaced(actualResult);
    }

    public static WriteScope beginStats(ServerStatsCounter stats, MinecraftServer server, File file) {
        var source = root == null ? null : root.statsWriterOwner(stats, server, file);
        var body = source == null ? null : source.canonicalStats(stats);
        return independent(source, body, P11ReceiptLedger.WriterKind.STATISTICS,
                stats instanceof P11IndependentMaterialWitness witness ? witness : null);
    }

    public static WriteScope beginAdvancements(PlayerAdvancements advancements, ServerPlayer actor, Path file) {
        var source = root == null ? null : root.advancementWriterOwner(advancements, actor, file);
        var body = source == null ? null : source.canonicalAdvancements(advancements);
        return independent(source, body, P11ReceiptLedger.WriterKind.ADVANCEMENTS,
                advancements instanceof P11IndependentMaterialWitness witness ? witness : null);
    }

    public static void statsMutated(ServerStatsCounter stats, MinecraftServer server) {
        try {
            var source = root == null ? null : root.sourceOwner(server);
            var body = source == null ? null : source.canonicalStats(stats);
            if (body != null) { source.independentMutation(body, P11ReceiptLedger.WriterKind.STATISTICS); }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    public static void advancementsMutated(PlayerAdvancements advancements, ServerPlayer actor) {
        try {
            var source = owner(actor);
            var body = source == null ? null : source.canonicalAdvancements(advancements);
            if (body != null) { source.independentMutation(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS); }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    private static WriteScope independent(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body, P11ReceiptLedger.WriterKind kind,
            P11IndependentMaterialWitness witness) {
        var previous = WRITE.get();
        boolean managed = source != null && body != null;
        var receipt = managed && previous == null ? source.beginIndependentWriter(body, kind, witness) : null;
        var scope = new WriteScope(source, body, receipt, previous, kind, managed && receipt == null);
        // Blocked scopes execute no original body, so they must not disturb an outer writer.
        if (!scope.blocked) { WRITE.set(scope); }
        return scope;
    }

    public static boolean independentPermitted(WriteScope scope) {
        return scope == null || (scope.independent && !scope.blocked && !scope.finished && WRITE.get() == scope);
    }

    private static WriteScope observedIndependent() {
        var scope = WRITE.get();
        return scope != null && scope.independent && !scope.blocked && !scope.finished
                && scope.receipt != null ? scope : null;
    }

    public static void independentEncoded(boolean normal) {
        var scope = observedIndependent();
        if (scope != null) { scope.facts.encoded(normal); }
    }

    public static void independentWritten(boolean normal) {
        var scope = observedIndependent();
        if (scope != null) { scope.facts.written(normal); }
    }

    public static void independentClosed(boolean normal) {
        var scope = observedIndependent();
        if (scope != null) { scope.facts.closed(normal); }
    }

    public static void endIndependent(WriteScope scope, boolean normal) {
        if (scope == null || !scope.independent || scope.finished) { return; }
        if (scope.blocked) {
            if (!scope.owner.owns(scope.body.actor.getServer())) { return; }
            scope.finished = true;
            try { scope.owner.independentSkipped(scope.body, scope.kind); }
            catch (RuntimeException | Error secondary) {
                if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
            }
            return;
        }
        if (WRITE.get() != scope) { return; }
        scope.facts.nativeReturned(normal);
        finish(scope);
    }

    private static void finish(WriteScope scope) {
        if (scope == null || scope.finished || scope.ioOnly || WRITE.get() != scope) { return; }
        scope.finished = true;
        if (scope.previous == null) { WRITE.remove(); } else { WRITE.set(scope.previous); }
        if (scope.receipt == null) { return; }
        try {
            var source = scope.owner;
            var receipt = scope.receipt;
            var result = scope.facts.settle();
            source.physical(receipt, P11ReceiptLedger.PhysicalStep.ENCODE, result.encode());
            source.physical(receipt, P11ReceiptLedger.PhysicalStep.WRITE, result.write());
            source.physical(receipt, P11ReceiptLedger.PhysicalStep.CLOSE, result.close());
            if (receipt.kind() == P11ReceiptLedger.WriterKind.PLAYER_DATA
                    || receipt.kind() == P11ReceiptLedger.WriterKind.LEVEL_PLAYER) {
                source.physical(receipt, P11ReceiptLedger.PhysicalStep.REPLACE, result.replace());
            }
            source.finishWriter(scope.body, receipt, result.successful(), System.nanoTime() - scope.started);
        } catch (RuntimeException | Error secondary) {
            // This observation finalizer must not replace a primary native exception/Error.
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    public static CompoundTag hostCacheMaterial(net.minecraft.server.players.PlayerList list,
            ServerPlayer player, CompoundTag target,
            Operation<CompoundTag> original) {
        var source = root == null ? null : root.writerOwner(list, player);
        if (source == null || source.account(player) == null) { return original.call(player, target); }
        var body = source.body(player);
        if (synchronousCacheSkip(source, body)) { return null; }
        var scope = CACHE.get();
        if (scope == null || scope.list != list || scope.body != body || scope.receipt == null
                || !source.mayWrite(body, scope.receipt)) { return null; }
        var material = original.call(player, target);
        scope.material = material;
        source.physical(scope.receipt, P11ReceiptLedger.PhysicalStep.ENCODE,
                P11ReceiptLedger.Observation.SUCCEEDED);
        return material;
    }

    public static void hostCacheAssignment(net.minecraft.server.players.PlayerList list,
            CompoundTag material, Operation<Void> original) {
        var source = root == null ? null : root.writerOwner(list);
        var request = PRIMARY_READ.get();
        if (request != null && request.owner == source && request.stage == PrimaryStage.SAVING
                && list == request.previous.actor.getServer().getPlayerList()) { return; }
        var scope = CACHE.get();
        if (scope == null) {
            if (source != null && root.hostBody(source) != null) { return; }
            original.call(list, material); return;
        }
        if (scope.source != source || scope.list != list) { throw unavailable(); }
        if (scope.receipt == null || material == null || material != scope.material
                || !scope.source.mayWrite(scope.body, scope.receipt)) { return; }
        original.call(list, material);
        scope.assigned = true;
        scope.source.physical(scope.receipt, P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT,
                P11ReceiptLedger.Observation.SUCCEEDED);
    }

    public static void integratedSave(net.minecraft.server.players.PlayerList list,
            ServerPlayer player, Operation<Void> original) {
        var source = root == null ? null : root.writerOwner(list, player);
        var body = source == null ? null : source.body(player);
        if (body == null || !player.getServer().isSingleplayerOwner(player.getGameProfile())) {
            original.call(player); return;
        }
        if (synchronousCacheSkip(source, body)) {
            // Preserve old cache; only the original super PD/stats/PA writer tail runs.
            // There is no attempted cache writer and thus no invented cache failure receipt.
            original.call(player); return;
        }
        if (CACHE.get() != null) { return; }
        var receipt = source.beginWriter(body, P11ReceiptLedger.WriterKind.CACHE);
        var scope = new CacheScope(list, source, body, receipt);
        CACHE.set(scope);
        try { original.call(player); }
        finally {
            CACHE.remove();
            if (receipt != null) {
                try { source.finishWriter(body, receipt, scope.assigned, System.nanoTime() - scope.started); }
                catch (RuntimeException | Error secondary) {
                    if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
                }
            }
        }
    }

    private static boolean synchronousCacheSkip(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body) {
        var request = PRIMARY_READ.get();
        return request != null && request.owner == source && request.previous == body
                && request.stage == PrimaryStage.SAVING;
    }

    public static Dynamic<?> readLevel(LevelStorageSource.LevelStorageAccess storage,
            boolean fallback, Operation<Dynamic<?>> original) {
        var previous = LEVEL_READ.get();
        var scope = new LevelReadScope();
        LEVEL_READ.set(scope);
        var witness = (P11NativeWorldAccess.ReadStorage) storage;
        witness.p11$readWitness(null);
        try {
            var input = original.call(fallback);
            witness.p11$readWitness(new P11NativeWorldAccess.ReadWitness(input, !fallback, scope.envelope));
            return input;
        } finally {
            if (previous == null) { LEVEL_READ.remove(); } else { LEVEL_READ.set(previous); }
        }
    }

    public static void levelReadRoot(CompoundTag rootTag) {
        var scope = LEVEL_READ.get();
        if (scope != null) {
            scope.envelope = rootTag.contains("Data", Tag.TAG_COMPOUND)
                    && (!rootTag.getCompound("Data").contains("Player")
                            || rootTag.getCompound("Data").contains("Player", Tag.TAG_COMPOUND));
        }
    }

    public static void saveWorld(LevelStorageSource.LevelStorageAccess storage,
            RegistryAccess registries, WorldData data, CompoundTag player, Operation<Void> original) {
        var source = root == null ? null : root.writerOwner(storage, data);
        var body = source == null ? null : root.hostBody(source);
        if (body == null) { original.call(registries, data, player); return; }
        if (WRITE.get() != null) { return; }
        var receipt = source.beginWriter(body, P11ReceiptLedger.WriterKind.LEVEL_PLAYER);
        if (receipt == null) { return; }
        var scope = new WriteScope(source, body, receipt, null);
        WRITE.set(scope);
        boolean normal = false;
        try {
            var selected = body.actor.saveWithoutId(new CompoundTag());
            original.call(registries, data, selected);
            normal = true;
        } finally { scope.facts.nativeReturned(normal); finish(scope); }
    }

    public static void saveLevelRoot(LevelStorageSource.LevelStorageAccess storage,
            CompoundTag data, Operation<Void> original) {
        var scope = WRITE.get();
        var source = root == null ? null : root.writerOwner(storage, null);
        if (scope != null && scope.receipt != null && scope.owner != source) { throw unavailable(); }
        if (source == null) { original.call(data); return; }
        var body = root.hostBody(source);
        if (body == null) { original.call(data); return; }
        if (scope == null || scope.owner != source || scope.body != body
                || data.getCompound("Data").get("Player") != scope.material
                || !source.mayWrite(body, scope.receipt)) { return; }
        scope.material = data;
        original.call(data);
    }

    public static void stop(MinecraftServer server, Operation<Void> original) {
        var previous = STOP_SERVER.get();
        STOP_SERVER.set(server);
        boolean normal = false;
        try { original.call(); normal = true; }
        finally {
            try { if (root != null) { root.nativeStopTerminal(server, normal); } }
            finally { if (previous == null) { STOP_SERVER.remove(); } else { STOP_SERVER.set(previous); } }
        }
    }

    /** Only stop's original player-save return, before removeAll/world save; no actor save grant. */
    public static void flushDetachedPlayersAtStop(MinecraftServer server,
            LevelStorageSource.LevelStorageAccess storage) {
        if (server == null || STOP_SERVER.get() != server || root == null) { return; }
        try {
            var source = root.sourceOwner(server);
            if (source != null && root.writerOwner(storage, null) == source) {
                source.flushDetachedPlayersAtStop();
            }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    /** Package owner mints this only for an already retained dirty body in the exact stop scope. */
    static void saveDetachedAtStop(P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body) {
        if (body == null || root == null || STOP_SERVER.get() != body.actor.getServer()
                || root.sourceOwner(body.actor.getServer()) != source
                || DETACHED_STOP_SAVE.get() != null || WRITE.get() != null || CACHE.get() != null
                || PRIMARY_READ.get() != null || SERIALIZE.get() != null
                || !source.detachedStopCurrent(body, body.source)) { throw unavailable(); }
        source.reconcileCooldown(body);
        var request = new DetachedStopSaveRequest(source, body);
        DETACHED_STOP_SAVE.set(request);
        try {
            ((P11NativeWorldAccess.PlayerStorage) request.list).p11$saveDetachedAtStop(request);
            if (!request.completed) { throw unavailable(); }
        } finally {
            request.closed = true;
            DETACHED_STOP_SAVE.remove();
        }
    }

    /** Opaque one-use bridge to the original virtual save, never a load or a second physical writer. */
    public static void saveDetachedAtStop(PlayerList list, PlayerDataStorage storage,
            DetachedStopSaveRequest request, Operation<Void> originalSave) {
        if (request == null || DETACHED_STOP_SAVE.get() != request || request.entered || request.closed
                || request.list != list || !detachedStopRequestCurrent(request)
                || root.playerStorageOwner(storage, request.body.actor) != request.owner
                || !((P11NativeWorldAccess.PlayerStorage) list).p11$independentOwnersMatch(request.body.actor)) {
            throw unavailable();
        }
        request.storage = storage;
        request.entered = true;
        originalSave.call(request.body.actor);
        if (request.duplicateWriter || !request.playerWriterEntered || request.receipt == null
                || request.receipt.source() != request.version || !detachedStopRequestCurrent(request)
                || !request.owner.completedPlayerWrite(request.body, request.receipt)) { throw unavailable(); }
        request.completed = true;
    }

    private static boolean detachedStopRequestCurrent(DetachedStopSaveRequest request) {
        return request != null && DETACHED_STOP_SAVE.get() == request && !request.closed
                && STOP_SERVER.get() == request.server && root != null
                && request.server.getPlayerList() == request.list
                && root.writerOwner(request.list, request.body.actor) == request.owner
                && request.owner.detachedStopCurrent(request.body, request.version);
    }

    /** Cache is an envelope consumer only inside the exact stop-save request and its own receipt. */
    private static boolean detachedStopCacheEnvelope(P11QualifiedSourceOwner.Body body) {
        var request = DETACHED_STOP_SAVE.get();
        var cache = CACHE.get();
        return request != null && request.entered && request.body == body && detachedStopRequestCurrent(request)
                && WRITE.get() == null && cache != null && cache.list == request.list
                && cache.source == request.owner && cache.body == body && cache.receipt != null
                && cache.receipt.source() == request.version && request.owner.mayWrite(body, cache.receipt);
    }

    /** Exact original stop caller, before its unique world-lock close; not a public save grant. */
    public static void flushDetachedIndependentAtStop(MinecraftServer server,
            LevelStorageSource.LevelStorageAccess storage) {
        if (server == null || STOP_SERVER.get() != server || root == null) { return; }
        try {
            var source = root.sourceOwner(server);
            if (source != null && root.writerOwner(storage, null) == source) {
                source.flushDetachedIndependentAtStop();
            }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    private enum ReadState { UNOBSERVED, ABSENT, PRESENT, READ, ERROR }

    /** Private-ctor and call-local only. No public actor, material or writer accessor. */
    public static final class DetachedStopSaveRequest {
        private final P11QualifiedSourceOwner owner;
        private final P11QualifiedSourceOwner.Body body;
        private final P11ReceiptLedger.Source version;
        private final MinecraftServer server;
        private final PlayerList list;
        private PlayerDataStorage storage;
        private P11ReceiptLedger.PhysicalWriterReceipt receipt;
        private boolean entered, playerWriterEntered, duplicateWriter, completed, closed;

        private DetachedStopSaveRequest(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.owner = owner;
            this.body = body;
            version = body.source;
            server = body.actor.getServer();
            list = server.getPlayerList();
        }
    }
    private static final class ConfigurationScope {
        final ServerCommonPacketListenerImpl listener;
        final ConfigurationScope previous;
        LoginSelection selection;
        ConfigurationScope(ServerCommonPacketListenerImpl listener, ConfigurationScope previous) {
            this.listener = listener; this.previous = previous;
        }
    }

    /** Lives only within the original configuration-final caller; never a queue or account root. */
    private static final class LoginSelection {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body previous;
        P11ReceiptLedger.Source version;
        P11QualifiedSourceOwner.LoginIndependent independent;
        P11QualifiedSourceOwner.Sealed memory, bodyMemory;
        P11SourceProvenance.Lineage lineage;
        PrimaryReadRequest primary;
        ServerPlayer actor;
        ServerPlayer associatedActor;
        P11ProvisionalAssociation association;
        boolean consumed, closed, constructorStarted;
        LoginSelection(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body previous) {
            this.owner = owner; this.previous = previous; this.version = previous.source;
        }
    }

    private enum PrimaryStage { CREATED, SAVING, READ_REQUESTED, READING, READ, VERIFIED, CONSUMING, CONSUMED, CLOSED }

    /** Root-only minted, one call-local native save/read handoff. No public material accessor. */
    public static final class PrimaryReadRequest {
        private final P11QualifiedSourceOwner owner;
        private final P11QualifiedSourceOwner.Body previous;
        private PlayerDataStorage storage;
        private P11ReceiptLedger.PhysicalWriterReceipt receipt;
        private P11QualifiedSourceOwner.SelectionWitness witness;
        private PrimaryStage stage = PrimaryStage.CREATED;
        private ReadState readState = ReadState.UNOBSERVED;
        private boolean readEntered, duplicateWriter;
        private CompoundTag material;
        private Optional<CompoundTag> result;

        private PrimaryReadRequest(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body previous) {
            this.owner = owner; this.previous = previous;
        }

        private void clear() {
            material = null;
            result = null;
            stage = PrimaryStage.CLOSED;
        }
    }

    private static final class LoadScope {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body;
        final P11QualifiedSourceOwner.Sealed memory;
        final P11QualifiedSourceOwner.Sealed bodyMemory;
        final PrimaryReadRequest preparedPrimary;
        final P11SourceProvenance.Lineage lineage;
        P11SourceProvenance.SelectedInput input;
        PlayerSkillAttachmentService.P11AttachmentReadResult readResult;
        P11QualifiedSourceOwner.InputKind kind = P11QualifiedSourceOwner.InputKind.UNKNOWN;
        ReadState primary = ReadState.UNOBSERVED;
        ReadState backup = ReadState.UNOBSERVED;
        boolean loadStarted, bodyLoaded, diskLoadActive, expectedSkills, expectedMana,
                expectedCooldown, skillsRead, manaRead, manaReadObserved, cooldownRead, cooldownReadObserved;
        LoadScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                P11QualifiedSourceOwner.Sealed memory, P11QualifiedSourceOwner.Sealed bodyMemory,
                PrimaryReadRequest preparedPrimary, P11SourceProvenance.Lineage lineage) {
            this.owner = owner; this.body = body; this.memory = memory; this.bodyMemory = bodyMemory;
            this.preparedPrimary = preparedPrimary; this.lineage = lineage;
        }
    }

    private static final class SerializeScope {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body;
        final P11ReceiptLedger.Source version;
        AttachmentWriteScope attachments;
        boolean brainObserved;
        boolean requiredComplete = true;
        SerializeScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.owner = owner; this.body = body; this.version = body.source;
        }
    }

    private static final class AttachmentWriteScope {
        final AttachmentHolder holder;
        PlayerSkillAttachmentService.P11AttachmentWriteResult skills;
        P11ManaMaterial.Write mana;
        P11CastCooldownMaterial.Write cooldown;
        AttachmentWriteScope(AttachmentHolder holder) { this.holder = holder; }
    }

    private static final class CopyScope {
        final P11QualifiedSourceOwner owner;
        final ServerPlayer old;
        final P11QualifiedSourceOwner.Body previous;
        ServerPlayer associatedActor;
        P11ProvisionalAssociation association;
        P11SourceProvenance.Lineage lineage;
        P11QualifiedSourceOwner.Body next;
        PlayerSkillAttachmentService.P11AttachmentReadResult readResult;
        boolean skillsRead, manaRead, manaReadObserved, cooldownRead, cooldownReadObserved;
        boolean cleanupLegal;
        CopyScope(P11QualifiedSourceOwner owner, ServerPlayer old, P11SourceProvenance.Lineage lineage) {
            this.owner = owner; this.old = old; this.lineage = lineage;
            this.previous = owner.body(old);
        }
    }

    private static final class LevelReadScope { boolean envelope; }

    private static final class CacheScope {
        final net.minecraft.server.players.PlayerList list;
        final P11QualifiedSourceOwner source;
        final P11QualifiedSourceOwner.Body body;
        final P11ReceiptLedger.PhysicalWriterReceipt receipt;
        final long started = System.nanoTime();
        CompoundTag material;
        boolean assigned;
        CacheScope(net.minecraft.server.players.PlayerList list,
                P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body,
                P11ReceiptLedger.PhysicalWriterReceipt receipt) {
            this.list = list; this.source = source; this.body = body; this.receipt = receipt;
        }
    }

    /** Only actual original writer scopes can mint this token; it has no public constructor. */
    public static final class WriteScope {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body;
        final P11ReceiptLedger.PhysicalWriterReceipt receipt;
        final WriteScope previous;
        final P11ReceiptLedger.WriterKind kind;
        final P11NativeWriteFacts facts;
        final boolean independent;
        final boolean blocked;
        final boolean ioOnly;
        final boolean stream;
        final WriteScope ioWriter;
        final long started = System.nanoTime();
        CompoundTag material;
        Path incoming, currentPath, backupPath;
        boolean observingReplace, finished;
        private WriteScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                P11ReceiptLedger.PhysicalWriterReceipt receipt, WriteScope previous) {
            this(owner, body, receipt, previous, receipt == null
                    ? P11ReceiptLedger.WriterKind.PLAYER_DATA : receipt.kind(), false);
        }
        private WriteScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                P11ReceiptLedger.PhysicalWriterReceipt receipt, WriteScope previous,
                P11ReceiptLedger.WriterKind kind, boolean blocked) {
            this.owner = owner; this.body = body; this.receipt = receipt; this.previous = previous;
            this.kind = kind; this.blocked = blocked;
            independent = kind == P11ReceiptLedger.WriterKind.STATISTICS
                    || kind == P11ReceiptLedger.WriterKind.ADVANCEMENTS;
            facts = new P11NativeWriteFacts(kind == P11ReceiptLedger.WriterKind.STATISTICS
                    ? P11NativeWriteFacts.Kind.STATISTICS : kind == P11ReceiptLedger.WriterKind.ADVANCEMENTS
                            ? P11NativeWriteFacts.Kind.ADVANCEMENTS : P11NativeWriteFacts.Kind.NBT);
            ioOnly = false; stream = false; ioWriter = null;
        }
        private WriteScope(CompoundTag material, WriteScope writer, WriteScope previous, boolean stream) {
            this.material = material; this.ioWriter = writer; this.previous = previous; this.stream = stream;
            owner = null; body = null; receipt = null; kind = null; facts = null;
            independent = false; blocked = false; ioOnly = true;
        }
        private static WriteScope io(CompoundTag material, WriteScope writer, WriteScope previous, boolean stream) {
            return new WriteScope(material, writer, previous, stream);
        }
    }
}
