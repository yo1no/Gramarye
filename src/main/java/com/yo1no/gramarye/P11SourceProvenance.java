package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService.LatestStateView;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService.P11AttachmentSnapshot;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService.P11AttachmentReadResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Constructor-injected P4 view of the qualified-source owner's evidence, not a source factory.
 * Native selected-input producers are package restricted. The root reserves managed-account
 * responsibility before manage; this mirror neither admits accounts nor owns a second quota.
 */
public final class P11SourceProvenance {
    private final Map<UUID, Entry> managed = new HashMap<>();
    private final Lifetime lifetime = new Lifetime();
    private MinecraftServer server;
    private PublicationObserver publicationObserver;

    P11SourceProvenance() {
    }

    void started(MinecraftServer server) {
        if (this.server != null || !managed.isEmpty()) {
            throw new IllegalStateException("P11_PROVENANCE_ALREADY_STARTED");
        }
        this.server = Objects.requireNonNull(server, "server");
        lifetime.rotate();
    }

    /** Disabled slots retain only a fresh native-only scope, never managed-source authority. */
    void inactiveBoundary() {
        if (server != null || !managed.isEmpty()) {
            throw new IllegalStateException("P11_PROVENANCE_NOT_INACTIVE");
        }
        lifetime.rotate();
    }

    void publicationObserver(PublicationObserver observer) {
        if (publicationObserver != null) {
            throw new IllegalStateException("P11_PROVENANCE_OBSERVER_ALREADY_BOUND");
        }
        publicationObserver = Objects.requireNonNull(observer, "observer");
    }

    /** Acquisition by the sole root owner happens before a partial actor can write. */
    void manage(ServerPlayer actor, long sourceEpoch, long sourceVersion) {
        requireActor(actor);
        requireVersion(sourceEpoch, sourceVersion);
        var existing = managed.get(actor.getUUID());
        if (existing != null && existing.actor == actor && existing.epoch == sourceEpoch) {
            if (sourceVersion < existing.version) {
                throw new IllegalArgumentException("P11_SOURCE_VERSION_REGRESSION");
            }
            existing.version = sourceVersion;
            return;
        }
        managed.put(actor.getUUID(), new Entry(actor, sourceEpoch, sourceVersion));
    }

    /** Actual native read/absence scopes, not file existence or save completion, mint this. */
    SelectedInput persistedInput() {
        requireMain();
        return new SelectedInput(lifetime.domain, InputKind.PERSISTED, null);
    }

    SelectedInput absentInput() {
        requireMain();
        return new SelectedInput(lifetime.domain, InputKind.ABSENT, null);
    }

    SelectedInput volatileInput(Lineage lineage) {
        requireMain();
        Objects.requireNonNull(lineage, "lineage");
        if (lineage.domain != lifetime.domain) {
            throw new IllegalArgumentException("P11_SOURCE_LINEAGE_OWNER_MISMATCH");
        }
        return new SelectedInput(lifetime.domain, InputKind.VOLATILE, lineage);
    }

    /** Root establishes the exact selected native scope before Attachment callbacks can run. */
    boolean beginSelectedLoad(
            ServerPlayer actor,
            long sourceEpoch,
            long sourceVersion,
            SelectedInput selected) {
        requireActor(actor);
        Objects.requireNonNull(selected, "selected");
        var entry = managed.get(actor.getUUID());
        if (!matches(entry, actor, sourceEpoch, sourceVersion)
                || selected.domain != lifetime.domain || selected.consumed || entry.load != null) {
            return false;
        }
        selected.consumed = true;
        entry.current = null;
        entry.persisted = Optional.empty();
        entry.load = new PendingLoad(selected);
        return true;
    }

    /** The serializer supplies this closed token for its actual result before native installation. */
    boolean readCompleted(ServerPlayer actor, P11AttachmentReadResult readResult) {
        requireActor(actor);
        var entry = managed.get(actor.getUUID());
        if (entry == null || entry.actor != actor || entry.load == null
                || entry.load.readResult != null || readResult == null
                || !readResult.isBoundTo(actor)) {
            return false;
        }
        entry.load.readResult = readResult;
        return true;
    }

    /** Accept the real loaded state or an exact chain of legitimate callback publications. */
    boolean recordSelectedLoad(
            ServerPlayer actor,
            P11AttachmentSnapshot snapshot,
            long sourceEpoch,
            long sourceVersion,
            SelectedInput selected,
            P11AttachmentReadResult readResult) {
        requireActor(actor);
        Objects.requireNonNull(snapshot, "snapshot");
        var entry = managed.get(actor.getUUID());
        if (!matches(entry, actor, sourceEpoch, sourceVersion)
                || entry.load == null || entry.load.selected != selected
                || entry.load.readResult != readResult || readResult == null
                || !snapshot.belongsTo(this) || !snapshot.isCurrent(actor)) {
            return false;
        }
        materializeRead(entry, snapshot);
        boolean accepted = entry.current != null && entry.current.sameState(snapshot);
        entry.load = null;
        if (!accepted) {
            entry.current = null;
            entry.persisted = Optional.empty();
        }
        return accepted;
    }

    /** Captured alongside coherent immutable bytes; root still checks source e/v when selecting. */
    Optional<Lineage> captureLineage(ServerPlayer actor, long sourceEpoch, long sourceVersion) {
        requireActor(actor);
        var entry = managed.get(actor.getUUID());
        if (!matches(entry, actor, sourceEpoch, sourceVersion)
                || entry.load != null || entry.current == null || !entry.current.isCurrent(actor)) {
            return Optional.empty();
        }
        return Optional.of(new Lineage(lifetime.domain, actor.getUUID(),
                sourceEpoch, sourceVersion, states(entry.current), entry.persisted));
    }

    boolean advanceVersion(
            ServerPlayer actor, long sourceEpoch, long expectedVersion, long nextVersion) {
        requireActor(actor);
        var entry = managed.get(actor.getUUID());
        if (!matches(entry, actor, sourceEpoch, expectedVersion)
                || nextVersion <= expectedVersion) {
            return false;
        }
        entry.version = nextVersion;
        return true;
    }

    /** Invalidating evidence never turns a managed account back into a legacy bypass. */
    void invalidate(ServerPlayer actor) {
        requireActor(actor);
        var entry = managed.get(actor.getUUID());
        if (entry != null && entry.actor == actor) {
            entry.current = null;
            entry.persisted = Optional.empty();
            entry.load = null;
        }
    }

    /** Only the root may release after all of its data/source responsibilities terminate. */
    void release(ServerPlayer actor, long sourceEpoch) {
        requireActor(actor);
        var entry = managed.get(actor.getUUID());
        if (entry != null && entry.actor == actor && entry.epoch == sourceEpoch) {
            managed.remove(actor.getUUID());
        }
    }

    /** Called after the root's final save/terminal accounting; clearing is not saved proof. */
    void stopped(MinecraftServer exactServer) {
        if (server != exactServer) {
            throw new IllegalArgumentException("P11_PROVENANCE_WRONG_SERVER");
        }
        managed.clear();
        server = null;
        lifetime.rotate();
    }

    public Observation observe(ServerPlayer actor, P11AttachmentSnapshot snapshot) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.belongsTo(this) || !snapshot.isBoundTo(actor)) {
            return lifetime.unknownObservation();
        }
        if (server == null) {
            return lifetime.legacyObservation();
        }
        if (actor.getServer() != server) {
            return lifetime.unknownObservation();
        }
        requireMain();
        var entry = managed.get(actor.getUUID());
        if (entry == null) {
            return lifetime.legacyObservation();
        }
        if (entry.actor == actor) {
            materializeRead(entry, snapshot);
        }
        if (entry.actor != actor || entry.current == null
                || !entry.current.sameState(snapshot) || !snapshot.isCurrent(actor)) {
            return lifetime.unknownObservation();
        }
        return new Observation(Kind.CURRENT, entry.epoch, entry.version,
                entry.persisted, lifetime.domain);
    }

    /** Called only around this service's actual successful setData, for all publication owners. */
    public void published(
            ServerPlayer actor, P11AttachmentSnapshot before, P11AttachmentSnapshot after) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (server == null || actor.getServer() != server) {
            return;
        }
        requireMain();
        var entry = managed.get(actor.getUUID());
        if (entry == null || entry.actor != actor) {
            return;
        }
        materializeRead(entry, before);
        if (!before.belongsTo(this) || !after.belongsTo(this)
                || !before.isBoundTo(actor) || !after.isCurrent(actor)
                || entry.current == null || !entry.current.sameState(before)) {
            entry.current = null;
        } else {
            entry.current = after;
        }
        if (publicationObserver != null) {
            boolean completed = false;
            try {
                publicationObserver.published(actor, entry.epoch, entry.version);
                completed = true;
            } finally {
                if (!completed) {
                    entry.current = null;
                }
            }
        }
    }

    private void requireActor(ServerPlayer actor) {
        Objects.requireNonNull(actor, "actor");
        requireMain();
        if (actor.getServer() != server) {
            throw new IllegalArgumentException("P11_PROVENANCE_WRONG_SERVER");
        }
    }

    private void requireMain() {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("P11_PROVENANCE_NOT_ACTIVE_MAIN");
        }
    }

    private static boolean matches(Entry entry, ServerPlayer actor, long epoch, long version) {
        return entry != null && entry.actor == actor && entry.epoch == epoch
                && entry.version == version;
    }

    private static void requireVersion(long epoch, long version) {
        if (epoch <= 0 || version < 0) {
            throw new IllegalArgumentException("P11_PROVENANCE_INVALID_VERSION");
        }
    }

    private static Optional<List<LatestStateView>> states(P11AttachmentSnapshot snapshot) {
        return states(snapshot.latestStates());
    }

    private static Optional<List<LatestStateView>> states(
            PlayerSkillAttachmentService.Result<List<LatestStateView>> result) {
        return result instanceof PlayerSkillAttachmentService.Available<
                List<LatestStateView>> available
                        ? Optional.of(List.copyOf(available.value())) : Optional.empty();
    }

    private void materializeRead(Entry entry, P11AttachmentSnapshot snapshot) {
        if (entry.current != null || entry.load == null || entry.load.initialized
                || entry.load.readResult == null || !snapshot.belongsTo(this)
                || !entry.load.readResult.matches(snapshot)) {
            return;
        }
        entry.load.initialized = true;
        var selected = entry.load.selected;
        var loaded = states(entry.load.readResult.latestStates());
        if (selected.kind == InputKind.VOLATILE) {
            var lineage = selected.lineage;
            if (lineage == null || lineage.domain != lifetime.domain
                    || !lineage.uuid.equals(entry.actor.getUUID())
                    || !loaded.equals(lineage.latest)) {
                return;
            }
            entry.persisted = lineage.persisted;
        } else {
            entry.persisted = loaded;
        }
        entry.current = snapshot;
    }

    interface PublicationObserver {
        void published(ServerPlayer actor, long sourceEpoch, long sourceVersion);
    }

    private static final class Entry {
        private final ServerPlayer actor;
        private final long epoch;
        private long version;
        private P11AttachmentSnapshot current;
        private PendingLoad load;
        private Optional<List<LatestStateView>> persisted = Optional.empty();

        private Entry(ServerPlayer actor, long epoch, long version) {
            this.actor = actor;
            this.epoch = epoch;
            this.version = version;
        }
    }

    private static final class PendingLoad {
        private final SelectedInput selected;
        private P11AttachmentReadResult readResult;
        private boolean initialized;

        private PendingLoad(SelectedInput selected) {
            this.selected = selected;
        }
    }

    private enum InputKind { PERSISTED, ABSENT, VOLATILE }

    private static final class Domain {
    }

    /** Pure scope identity; it cannot mint a CURRENT observation or a native clear proof. */
    static final class Lifetime {
        private Domain domain = new Domain();

        void rotate() { domain = new Domain(); }

        Observation legacyObservation() {
            return new Observation(Kind.LEGACY_NATIVE, 0, 0, Optional.empty(), domain);
        }

        Observation unknownObservation() { return Observation.UNKNOWN; }
    }

    static final class SelectedInput {
        private final Domain domain;
        private final InputKind kind;
        private final Lineage lineage;
        private boolean consumed;

        private SelectedInput(Domain domain, InputKind kind, Lineage lineage) {
            this.domain = domain;
            this.kind = kind;
            this.lineage = lineage;
        }
    }

    /** Actor-free provenance paired with the root's one immutable selected-input material. */
    static final class Lineage {
        private final Domain domain;
        private final UUID uuid;
        private final long epoch;
        private final long version;
        private final Optional<List<LatestStateView>> latest;
        private final Optional<List<LatestStateView>> persisted;

        private Lineage(Domain domain, UUID uuid,
                long epoch, long version, Optional<List<LatestStateView>> latest,
                Optional<List<LatestStateView>> persisted) {
            this.domain = domain;
            this.uuid = uuid;
            this.epoch = epoch;
            this.version = version;
            this.latest = latest;
            this.persisted = persisted;
        }

        long sourceEpoch() { return epoch; }
        long sourceVersion() { return version; }
    }

    public enum Kind { LEGACY_NATIVE, UNKNOWN, CURRENT }

    public static final class Observation {
        private static final Observation UNKNOWN =
                new Observation(Kind.UNKNOWN, 0, 0, Optional.empty(), null);
        private final Kind kind;
        private final long sourceEpoch;
        private final long sourceVersion;
        private final Optional<List<LatestStateView>> persisted;
        private final Domain domain;

        private Observation(Kind kind, long sourceEpoch, long sourceVersion,
                Optional<List<LatestStateView>> persisted, Domain domain) {
            this.kind = kind;
            this.sourceEpoch = sourceEpoch;
            this.sourceVersion = sourceVersion;
            this.persisted = persisted;
            this.domain = domain;
        }

        public Kind kind() { return kind; }
        public long sourceEpoch() { return sourceEpoch; }
        public long sourceVersion() { return sourceVersion; }
        public Optional<List<LatestStateView>> persistedStates() { return persisted; }
        public boolean sameScope(Observation other) {
            return other != null && domain != null && kind == other.kind && domain == other.domain
                    && sourceEpoch == other.sourceEpoch && sourceVersion == other.sourceVersion;
        }
    }
}
