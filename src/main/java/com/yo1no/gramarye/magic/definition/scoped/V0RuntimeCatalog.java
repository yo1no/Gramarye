package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.V0CatalogAuthority;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;

/** Capability-gated runtime view. The only writer and compiler work is on its cold lane. */
public final class V0RuntimeCatalog {
    private static final int MAX_LINEAGES = 4;
    private static final int MAX_PINS_PER_LEASE = 3;
    private static final int MAX_MANIFEST_ENTRIES = 8;
    private static final String CATEGORY = "gramarye:projectile";
    private static final String CAPABILITY = "gramarye:v0_projectile";
    private static final int MAX_PROGRAM_BYTES = 1_089;

    private final Object identity = new Object();
    private final V0CatalogAuthority authority;
    private final Path sourceRoot;
    private final ScopeDFrontend.Context context;
    private final boolean normal;
    private final AtomicReference<Thread> coldOwner = new AtomicReference<>();
    private final AtomicReference<Gate> gate = new AtomicReference<>(new Gate(PHASE_OPEN));
    private final AtomicReferenceArray<Lease> pins = new AtomicReferenceArray<>(MAX_LINEAGES + 1);
    private final Map<SkillOwnerId, Writer> writers = new HashMap<>();
    private Object sourceRootKey;
    private volatile long lastCaptureTicket;
    private boolean retired;
    private volatile boolean bootstrapStopped;

    V0RuntimeCatalog(V0CatalogAuthority authority, Path sourceRoot, ScopeDFrontend.Context context) {
        this(authority, sourceRoot, context, false);
    }

    private V0RuntimeCatalog(V0CatalogAuthority authority, Path sourceRoot, ScopeDFrontend.Context context,
                             boolean normal) {
        this.authority = Objects.requireNonNull(authority);
        this.sourceRoot = Objects.requireNonNull(sourceRoot);
        this.context = Objects.requireNonNull(context);
        this.normal = normal;
    }

    /** Pure composition: directory creation, recovery and compilation belong to the cold owner. */
    public static V0RuntimeCatalog openNormal(V0CatalogAuthority authority, Path qualifiedSource) {
        return new V0RuntimeCatalog(authority, qualifiedSource.toAbsolutePath().normalize(),
                V0NormalBootstrap.context(), true);
    }

    public BootstrapReadiness ensureNormalBootstrap(V0CatalogAuthority exactAuthority, SkillOwnerId owner) {
        if (!coldLane(exactAuthority) || retired || !normal || bootstrapStopped) {
            return BootstrapReadiness.rejected(Refusal.SLOT_STALE);
        }
        if (owner == null || owner.equals(V0NormalBootstrap.SEED)) {
            return BootstrapReadiness.rejected(Refusal.INVALID_SELECTION);
        }
        Gate expected = gate.get();
        if (expected.phase != PHASE_OPEN) {
            return BootstrapReadiness.rejected(Refusal.CATALOG_BUSY);
        }
        Gate writing = new Gate(PHASE_WRITING);
        if (!gate.compareAndSet(expected, writing)) {
            return BootstrapReadiness.rejected(Refusal.CATALOG_BUSY);
        }
        Writer active = null;
        try {
            initializeNormalSource();
            active = writer(V0NormalBootstrap.SEED);
            var seed = bootstrapDocument(active, Optional.empty(), null, writing);
            if (seed == null) {
                return BootstrapReadiness.rejected(Refusal.ROOT_UNAVAILABLE);
            }
            var seedReadback = active.catalog.captureRuntimeReadback(context);
            var provenance = new ForkProvenance(active.source, seed.reference(), seedReadback.image.generation());
            var bundle = active.catalog.exportFork(context.catalogContext(), seed.reference());
            var roster = normalRoster();
            if (roster.size() == ScopeDLimits.MAX_OWNERS && !roster.contains(owner)) {
                return BootstrapReadiness.rejected(Refusal.CAPACITY);
            }
            active = writer(owner);
            var player = bootstrapDocument(active, Optional.of(provenance), bundle, writing);
            if (player == null || bootstrapStopped || gate.get() != writing) {
                return BootstrapReadiness.rejected(Refusal.ROOT_UNAVAILABLE);
            }
            var witness = new BootstrapWitness(provenance, V0NormalBootstrap.digest(seed), active.source,
                    player.reference(), V0NormalBootstrap.digest(player));
            return new BootstrapReadiness(new Selection(false, player.reference().identity().id().value(), 0, witness), null);
        } catch (ScopeDGraph.Rejected | ScopeDFork.Rejected rejected) {
            return BootstrapReadiness.rejected(Refusal.ROOT_UNAVAILABLE);
        } catch (IOException unavailable) {
            fenceWriter(active, writing);
            return BootstrapReadiness.rejected(Refusal.SOURCE_UNPROVED);
        } catch (RuntimeException | Error failure) {
            fenceWriter(active, writing);
            throw failure;
        } finally {
            gate.compareAndSet(writing, new Gate(PHASE_OPEN));
        }
    }

    private SpellDefinition bootstrapDocument(Writer writer, Optional<ForkProvenance> provenance,
                                               ScopeDFork.Bundle bundle, Gate writing) throws IOException {
        if (!new ScopeDDrafts(writer.catalog.current().owner(), writer.storage).read().drafts().isEmpty()) {
            return null;
        }
        var current = writer.catalog.current();
        var pending = writer.catalog.recoverRuntimePreparation(context.catalogContext(), livePins(writer.proof));
        if (pending.isPresent()) {
            var prepared = pending.orElseThrow();
            var candidate = ScopeDCatalogImage.decode(current.owner(), prepared.binding().candidate().value(), prepared.bytes());
            // Provisioning can recover its original empty-to-singleton fork, never a foreign batch.
            if (!V0NormalBootstrap.genesis(current) || V0NormalBootstrap.singleton(candidate, provenance) == null) {
                return null;
            }
            publishNormal(writer, prepared, writing);
            current = writer.catalog.captureRuntimeReadback(context).image;
        }
        var existing = V0NormalBootstrap.singleton(current, provenance);
        if (existing != null) {
            writer.catalog.captureRuntimeReadback(context);
            return existing;
        }
        if (!V0NormalBootstrap.genesis(current) || bootstrapStopped || gate.get() != writing) {
            return null;
        }
        var reservation = writer.catalog.reserve();
        var prepared = bundle == null
                ? writer.catalog.prepare(reservation, context.catalogContext(),
                        List.of(V0NormalBootstrap.seed(reservation.newSpell(1))))
                : writer.catalog.prepareFork(reservation, context.catalogContext(), bundle);
        publishNormal(writer, prepared, writing);
        return V0NormalBootstrap.singleton(writer.catalog.captureRuntimeReadback(context).image, provenance);
    }

    private void publishNormal(Writer writer, ScopeDCatalog.Prepared prepared, Gate writing) throws IOException {
        var proof = writer.catalog.verifyCompatibility(prepared, context).proof()
                .orElseThrow(() -> new IOException("Normal catalog publication has no matching C proof"));
        writer.catalog.publishRuntime(prepared, context, proof, new WriterMutation(this, writing));
        writer.catalog.captureRuntimeReadback(context);
    }

    private boolean matchesBootstrap(Writer player, ScopeDCatalog.RuntimeReadback readback,
                                      BootstrapWitness witness) throws IOException {
        if (!normal || bootstrapStopped || !player.source.equals(witness.playerSource)
                || !witness.player.identity().owner().equals(readback.image.owner())
                || !witness.player.equals(readback.image.routes().get(witness.player.identity()))) {
            return false;
        }
        var document = readback.image.revisions().get(witness.player);
        if (!(document instanceof SpellDefinition spell)
                || !V0NormalBootstrap.matches(spell, Optional.of(witness.provenance))
                || !witness.playerDigest.equals(V0NormalBootstrap.digest(spell))) {
            return false;
        }
        var seed = writer(V0NormalBootstrap.SEED).catalog.captureRuntimeReadback(context);
        var source = V0NormalBootstrap.singleton(seed.image, Optional.empty());
        return seed.source.equals(witness.provenance.sourceId())
                && seed.image.generation().equals(witness.provenance.generation())
                && source != null && source.reference().equals(witness.provenance.source())
                && witness.seedDigest.equals(V0NormalBootstrap.digest(source));
    }

    private void initializeNormalSource() throws IOException {
        if (sourceRootKey != null) {
            requireSourceRoot();
            return;
        }
        Path namespace = sourceRoot.getParent();
        Path world = namespace == null ? null : namespace.getParent();
        if (world == null || !Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Normal catalog has no owned world directory");
        }
        if (Files.notExists(namespace, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(namespace);
            forceDirectory(world);
        }
        if (!Files.isDirectory(namespace, LinkOption.NOFOLLOW_LINKS)
                || !Files.getOwner(namespace, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(world, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Normal catalog namespace is not owned by its world");
        }
        if (Files.notExists(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(sourceRoot);
            forceDirectory(namespace);
        }
        if (!Files.isDirectory(sourceRoot, LinkOption.NOFOLLOW_LINKS)
                || !Files.getOwner(sourceRoot, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(world, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Normal catalog source is not owned by its world");
        }
        requireSourceRoot();
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }

    /** Only the trusted lifecycle coordinator may seal the temporary selector; normal C remains. */
    boolean stopBootstrap(V0CatalogAuthority exactAuthority) {
        if (!normal || retired || !ownerThread(exactAuthority)) {
            return false;
        }
        bootstrapStopped = true;
        return true;
    }

    public BootstrapCleanup cleanupNormalBootstrap(V0CatalogAuthority exactAuthority, SkillOwnerId owner,
                                             V0CatalogAuthority.BootstrapRetirementPermit permit) {
        return cleanupNormalBootstrap(exactAuthority, owner, permit, null).result();
    }

    public BootstrapCleanupReport cleanupNormalBootstrap(V0CatalogAuthority exactAuthority, SkillOwnerId owner,
            V0CatalogAuthority.BootstrapRetirementPermit permit, BootstrapRosterProof rosterProof) {
        BootstrapCleanup result = cleanupBootstrap(exactAuthority, owner, permit, rosterProof);
        BootstrapCleanupReceipt receipt = null;
        if (owner != null && !owner.equals(V0NormalBootstrap.SEED)
                && (result == BootstrapCleanup.REMOVED || result == BootstrapCleanup.NO_MATCH)) {
            try {
                Writer writer = writers.get(owner);
                if (writer != null && gate.get().phase == PHASE_OPEN && writer.proof.proved) {
                    var exact = writer.catalog.captureRuntimeReadback(context);
                    receipt = new BootstrapCleanupReceipt(identity, owner, exact.source,
                            exact.image.generation(), exact.physical.digest());
                }
            } catch (IOException unavailable) {
                // The completed operation remains its original fact, but no current receipt is manufactured.
            }
        }
        return new BootstrapCleanupReport(result, receipt);
    }

    private BootstrapCleanup cleanupBootstrap(V0CatalogAuthority exactAuthority, SkillOwnerId owner,
            V0CatalogAuthority.BootstrapRetirementPermit permit, BootstrapRosterProof rosterProof) {
        if (!coldLane(exactAuthority) || !normal || retired || owner == null
                || owner.equals(V0NormalBootstrap.SEED) && rosterProof == null
                || permit == null || !empty() || !permit.claim(exactAuthority, owner, this)) {
            return BootstrapCleanup.REFUSED;
        }
        bootstrapStopped = true;
        Gate expected = gate.get();
        if (expected.phase != PHASE_OPEN) {
            return BootstrapCleanup.REFUSED;
        }
        Gate writing = new Gate(PHASE_WRITING);
        if (!gate.compareAndSet(expected, writing)) {
            return BootstrapCleanup.REFUSED;
        }
        Writer active = null;
        try {
            requireSourceRoot();
            Set<SkillOwnerId> roster = normalRoster();
            if (!roster.contains(owner) || !roster.contains(V0NormalBootstrap.SEED)) {
                return BootstrapCleanup.REFUSED;
            }
            var seedWriter = writer(V0NormalBootstrap.SEED);
            var seedReadback = seedWriter.catalog.captureRuntimeReadback(context);
            var seed = V0NormalBootstrap.singleton(seedReadback.image, Optional.empty());
            if (seed == null || seedWriter.storage.hasPendingMaterial()
                    || !new ScopeDDrafts(V0NormalBootstrap.SEED, seedWriter.storage).read().drafts().isEmpty()) {
                return BootstrapCleanup.REFUSED;
            }
            var provenance = new ForkProvenance(seedWriter.source, seed.reference(), seedReadback.image.generation());
            if (owner.equals(V0NormalBootstrap.SEED)) {
                // The actual bounded admitted roster, not the set of currently online actors.
                for (SkillOwnerId admitted : roster) {
                    if (!admitted.equals(V0NormalBootstrap.SEED)) {
                        var other = writer(admitted);
                        var image = other.catalog.captureRuntimeReadback(context).image;
                        if (other.storage.hasPendingMaterial()
                                || !new ScopeDDrafts(admitted, other.storage).read().drafts().isEmpty()
                                || image.revisions().values().stream().anyMatch(document -> document.hasOpaqueRaw()
                                        || document.provenance().map(origin -> origin.sourceId().equals(seedWriter.source)
                                                || origin.source().identity().owner().equals(V0NormalBootstrap.SEED)).orElse(false))) {
                            return BootstrapCleanup.REFUSED;
                        }
                    }
                }
            }
            active = writer(owner);
            // Every admitted writer is already open before taking admission's existing lock.
            // Holding it through seed publication prevents a newly admitted copy escaping inventory.
            try (var admission = owner.equals(V0NormalBootstrap.SEED)
                    ? FileChannel.open(sourceRoot.resolve(".owner-admission.lock"),
                            StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS) : null;
                 var lock = admission == null ? null : admission.tryLock()) {
                if (admission != null && (lock == null || !normalRoster().equals(roster))) {
                    return BootstrapCleanup.REFUSED;
                }
                if (owner.equals(V0NormalBootstrap.SEED)
                        && !rosterProof.consume(this, exactAuthority, roster, seedReadback, seed)) {
                    return BootstrapCleanup.REFUSED;
                }
                return removeBootstrap(active, owner, seed, provenance, writing);
            }
        } catch (ScopeDGraph.Rejected | OverlappingFileLockException unavailable) {
            return BootstrapCleanup.REFUSED;
        } catch (IOException unavailable) {
            fenceWriter(active, writing);
            return BootstrapCleanup.SOURCE_UNPROVED;
        } catch (RuntimeException | Error failure) {
            fenceWriter(active, writing);
            throw failure;
        } finally {
            gate.compareAndSet(writing, new Gate(PHASE_OPEN));
        }
    }

    /** Read-only cold proof; seed authorization still requires its original root's main-owner drain proof. */
    public Optional<BootstrapRosterProof> prepareBootstrapSeedRetirement(V0CatalogAuthority exactAuthority,
            List<BootstrapCleanupReceipt> receipts) {
        if (!coldLane(exactAuthority) || !normal || retired || receipts == null || receipts.size() > 31
                || !empty() || gate.get().phase != PHASE_OPEN) {
            return Optional.empty();
        }
        Gate expected = gate.get();
        Gate observing = new Gate(PHASE_WRITING);
        if (!gate.compareAndSet(expected, observing)) { return Optional.empty(); }
        try {
            var roster = normalRoster();
            if (!roster.contains(V0NormalBootstrap.SEED)) { return Optional.empty(); }
            for (SkillOwnerId owner : roster) { writer(owner); }
            var byOwner = receiptOwners(receipts);
            if (byOwner == null || !receiptRosterMatches(roster, byOwner)) { return Optional.empty(); }
            try (var admission = FileChannel.open(sourceRoot.resolve(".owner-admission.lock"),
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var lock = admission.tryLock()) {
                if (lock == null || !normalRoster().equals(roster)) { return Optional.empty(); }
                var seedWriter = writers.get(V0NormalBootstrap.SEED);
                var seedReadback = seedWriter.catalog.captureRuntimeReadback(context);
                var seed = V0NormalBootstrap.singleton(seedReadback.image, Optional.empty());
                if (seed == null || !retirementImageSafe(seedWriter)
                        || !validateCleanupReceipts(byOwner, seedWriter.source) || gate.get() != observing) {
                    return Optional.empty();
                }
                return Optional.of(new BootstrapRosterProof(this, roster, byOwner, seedReadback.source,
                        seed.reference(), seedReadback.image.generation(), V0NormalBootstrap.digest(seed)));
            }
        } catch (IOException | ScopeDGraph.Rejected | OverlappingFileLockException unavailable) {
            return Optional.empty();
        } finally {
            gate.compareAndSet(observing, new Gate(PHASE_OPEN));
        }
    }

    private Map<SkillOwnerId, BootstrapCleanupReceipt> receiptOwners(List<BootstrapCleanupReceipt> receipts) {
        Map<SkillOwnerId, BootstrapCleanupReceipt> byOwner = new HashMap<>();
        for (var receipt : receipts) {
            if (receipt == null || receipt.catalogIdentity != identity || receipt.owner.equals(V0NormalBootstrap.SEED)
                    || byOwner.putIfAbsent(receipt.owner, receipt) != null) {
                return null;
            }
        }
        return Map.copyOf(byOwner);
    }

    private static boolean receiptRosterMatches(Set<SkillOwnerId> roster,
            Map<SkillOwnerId, BootstrapCleanupReceipt> receipts) {
        Set<SkillOwnerId> players = new HashSet<>(roster);
        players.remove(V0NormalBootstrap.SEED);
        return players.equals(receipts.keySet());
    }

    private boolean retirementImageSafe(Writer writer) throws IOException {
        var image = writer.catalog.captureRuntimeReadback(context).image;
        return !writer.storage.hasPendingMaterial()
                && new ScopeDDrafts(image.owner(), writer.storage).read().drafts().isEmpty()
                && image.revisions().values().stream().noneMatch(FormalDocument::hasOpaqueRaw);
    }

    private boolean validateCleanupReceipts(Map<SkillOwnerId, BootstrapCleanupReceipt> receipts, UUID seedSource)
            throws IOException {
        for (var entry : receipts.entrySet()) {
            Writer writer = writers.get(entry.getKey());
            if (writer == null || !writer.proof.proved || !retirementImageSafe(writer)) { return false; }
            var exact = writer.catalog.captureRuntimeReadback(context);
            var receipt = entry.getValue();
            if (receipt.catalogIdentity != identity || !receipt.source.equals(exact.source)
                    || !receipt.generation.equals(exact.image.generation())
                    || !receipt.digest.equals(exact.physical.digest())
                    || exact.image.revisions().values().stream().anyMatch(document -> document.provenance()
                            .map(origin -> origin.sourceId().equals(seedSource)
                                    || origin.source().identity().owner().equals(V0NormalBootstrap.SEED)).orElse(false))) {
                return false;
            }
        }
        return true;
    }

    private BootstrapCleanup removeBootstrap(Writer active, SkillOwnerId owner, SpellDefinition seed,
                                             ForkProvenance provenance, Gate writing) throws IOException {
        var image = active.catalog.captureRuntimeReadback(context).image;
        if (active.storage.hasPendingMaterial()
                || !new ScopeDDrafts(owner, active.storage).read().drafts().isEmpty()
                || image.revisions().values().stream().anyMatch(FormalDocument::hasOpaqueRaw)) {
            return BootstrapCleanup.REFUSED;
        }
        Set<Identity> removals = new HashSet<>();
        for (FormalDocument document : image.currentBodies().values()) {
            boolean originMatches = owner.equals(V0NormalBootstrap.SEED)
                    ? document.reference().equals(seed.reference())
                    : document.provenance().map(provenance::equals).orElse(false);
            if (originMatches) {
                Optional<ForkProvenance> origin = owner.equals(V0NormalBootstrap.SEED)
                        ? Optional.empty() : Optional.of(provenance);
                if (!(document instanceof SpellDefinition spell) || !V0NormalBootstrap.matches(spell, origin)
                        || !V0NormalBootstrap.digest(spell).equals(V0NormalBootstrap.digest(
                                new SpellDefinition(spell.reference(), V0NormalBootstrap.body(), origin)))) {
                    return BootstrapCleanup.REFUSED;
                }
                removals.add(spell.identity());
            }
        }
        if (removals.isEmpty()) {
            return BootstrapCleanup.NO_MATCH;
        }
        if (image.externalRoots().stream().anyMatch(reference -> removals.contains(reference.identity()))) {
            return BootstrapCleanup.REFUSED;
        }
        if (image.revisions().values().stream().anyMatch(document -> !removals.contains(document.identity())
                && document.dependencies().stream().anyMatch(reference -> removals.contains(reference.target())))) {
            return BootstrapCleanup.REFUSED;
        }
        var inventory = active.catalog.captureRoots(context.catalogContext(), List.of());
        var reservation = active.catalog.reserve();
        var prepared = active.catalog.prepare(reservation, context.catalogContext(), List.of(), removals, inventory);
        publishNormal(active, prepared, writing);
        var after = active.catalog.captureRuntimeReadback(context).image;
        return after.routes().keySet().stream().noneMatch(removals::contains)
                ? BootstrapCleanup.REMOVED : BootstrapCleanup.SOURCE_UNPROVED;
    }

    private Set<SkillOwnerId> normalRoster() throws IOException {
        requireSourceRoot();
        Set<SkillOwnerId> admitted = new HashSet<>();
        Set<SkillOwnerId> directories = new HashSet<>();
        int entries = 0;
        try (var stream = Files.newDirectoryStream(sourceRoot)) {
            for (Path entry : stream) {
                if (++entries > 2 * ScopeDLimits.MAX_OWNERS + 1) {
                    throw new IOException("Normal catalog roster exceeds capacity");
                }
                String name = entry.getFileName().toString();
                if (name.equals(".owner-admission.lock") && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                boolean marker = name.endsWith(".admitted");
                String principal = marker ? name.substring(0, name.length() - ".admitted".length()) : name;
                UUID id;
                try {
                    id = UUID.fromString(principal);
                } catch (IllegalArgumentException malformed) {
                    throw new IOException("Unrecognized normal catalog roster entry", malformed);
                }
                if (!id.toString().equals(principal)) {
                    throw new IOException("Noncanonical normal catalog principal");
                }
                var admittedOwner = new SkillOwnerId(id);
                if (marker && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) && Files.size(entry) == 0) {
                    admitted.add(admittedOwner);
                } else if (!marker && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    directories.add(admittedOwner);
                } else {
                    throw new IOException("Unrecognized normal catalog roster material");
                }
            }
        }
        if (!admitted.equals(directories) || admitted.size() > ScopeDLimits.MAX_OWNERS) {
            throw new IOException("Incomplete normal catalog roster");
        }
        return Set.copyOf(admitted);
    }

    public PrepareResult prepare(V0CatalogAuthority exactAuthority, long captureTicket,
                                 SkillOwnerId owner, Selection selection, int maxManifestEntries) {
        if (!coldLane(exactAuthority) || retired) {
            return reject(Refusal.SLOT_STALE);
        }
        if (owner == null || selection == null || maxManifestEntries < 1
                || maxManifestEntries > MAX_MANIFEST_ENTRIES || !authority.matchesCaptureLimits(maxManifestEntries)) {
            return reject(Refusal.INVALID_SELECTION);
        }
        if (normal && (owner.equals(V0NormalBootstrap.SEED)
                || selection.bootstrap != null && bootstrapStopped)) {
            return reject(Refusal.INVALID_SELECTION);
        }
        if (captureTicket <= lastCaptureTicket) {
            return reject(Refusal.TICKET_STALE);
        }
        lastCaptureTicket = captureTicket;
        Gate expected = gate.get();
        if (expected.phase != PHASE_OPEN) {
            return reject(expected.phase == PHASE_FAULTED ? Refusal.SOURCE_UNPROVED : Refusal.CATALOG_BUSY);
        }
        int slot = provisionalSlot();
        if (slot < 0) {
            return reject(Refusal.CATALOG_BUSY);
        }
        Writer writer = null;
        try {
            writer = writer(owner);
            ScopeDCatalog.RuntimeReadback exact = writer.catalog.captureRuntimeReadback(context);
            if (selection.bootstrap != null && !matchesBootstrap(writer, exact, selection.bootstrap)) {
                return reject(Refusal.ROOT_UNAVAILABLE);
            }
            var root = selection.resolve(exact.image, owner);
            if (root == null) {
                return reject(Refusal.ROOT_UNAVAILABLE);
            }
            if (normal && selection.bootstrap == null && exact.image.revisions().get(root.exact()).provenance()
                    .map(origin -> origin.source().identity().owner().equals(V0NormalBootstrap.SEED)).orElse(false)) {
                // A generic future policy is not a back door around the temporary selector's exact witness/barrier.
                return reject(Refusal.ROOT_UNAVAILABLE);
            }
            var projection = new ProjectionCheck(maxManifestEntries, normal);
            ScopeDCompiler.Result result = ScopeDCompiler.compileRuntime(exact.source, exact.image, root,
                    exact.image.generation(), context, projection);
            if (result instanceof ScopeDCompiler.Rejected rejected) {
                return reject(compilerRefusal(root, rejected.diagnostics()));
            }
            if (projection.refusal() != null) {
                return reject(projection.refusal());
            }
            var compiled = (ScopeDCompiler.Compiled) result;
            if (writer.catalog.captureRuntimeReadback(context).physical != exact.physical) {
                return reject(Refusal.HEAD_STALE);
            }
            var refs = exactPins(compiled);
            if (!exact.image.revisions().keySet().containsAll(refs)) {
                return reject(Refusal.SOURCE_UNPROVED);
            }
            var lease = new Lease(slot, writer.proof, refs, projection, exact);
            var candidate = new Candidate(identity, captureTicket, expected, lease);
            pins.set(slot, lease);
            // The sole cold lane owns insertion and physical sweep. A concurrent suspend can still
            // win this compare, in which case the complete provisional row is removed before return.
            if (!gate.compareAndSet(expected, expected)) {
                remove(lease, LEASESTATE_PROVISIONAL);
                return reject(Refusal.HEAD_STALE);
            }
            return candidate;
        } catch (ScopeDCatalog.CompatibilityRejected stale) {
            return reject(Refusal.HEAD_STALE);
        } catch (IOException unavailable) {
            fenceWriter(writer, expected);
            return reject(Refusal.SOURCE_UNPROVED);
        } catch (RuntimeException | Error failure) {
            fenceWriter(writer, expected);
            throw failure;
        }
    }

    public Acceptance compareAndAccept(V0CatalogAuthority.Reservation permit, Candidate candidate) {
        if (permit == null || candidate == null || candidate.catalogIdentity != identity
                || !permit.matches(authority, candidate) || !owns(candidate.lease, LEASESTATE_PROVISIONAL)) {
            return Acceptance.INVALID_RESERVATION;
        }
        if (gate.get() != candidate.expected || candidate.captureTicket != lastCaptureTicket
                || !candidate.lease.writer.proved) {
            return Acceptance.HEAD_STALE;
        }
        if (acceptedCount() >= MAX_LINEAGES) {
            return Acceptance.CATALOG_BUSY;
        }
        // The marker protects the complete row if terminal worker cleanup races the
        // publication stores. The gate CAS alone decides gameplay acceptance.
        Lease lease = candidate.lease;
        lease.accepting = true;
        try {
            if (!gate.compareAndSet(candidate.expected, candidate.committed)) {
                return Acceptance.CATALOG_BUSY;
            }
            lease.state = LEASESTATE_ACCEPTED;
            return Acceptance.COMMITTED;
        } finally {
            lease.accepting = false;
        }
    }

    public Disposition discard(V0CatalogAuthority exactAuthority, Candidate candidate) {
        if (!ownerThread(exactAuthority) || candidate == null || candidate.catalogIdentity != identity) {
            return Disposition.STALE_OR_RELEASED;
        }
        return remove(candidate.lease, LEASESTATE_PROVISIONAL);
    }

    public Disposition release(V0CatalogAuthority.ReleasePermit permit, Lease lease) {
        if (lease == null || permit == null || !permit.matches(authority, lease)
                || !owns(lease, LEASESTATE_ACCEPTED) && !owns(lease, LEASESTATE_RELEASING)) {
            return Disposition.STALE_OR_RELEASED;
        }
        lease.state = LEASESTATE_RELEASING;
        return remove(lease, LEASESTATE_RELEASING);
    }

    public boolean usable(V0CatalogAuthority exactAuthority, Lease lease) {
        if (!ownerThread(exactAuthority) || lease == null || !owns(lease, LEASESTATE_ACCEPTED)) {
            return false;
        }
        byte phase = gate.get().phase;
        return (phase == PHASE_OPEN || phase == PHASE_WRITING || phase == PHASE_FAULTED) && lease.writer.proved;
    }

    public Suspension suspend(V0CatalogAuthority exactAuthority) {
        if (exactAuthority != authority) {
            return null;
        }
        var suspension = new Suspension(identity);
        gate.getAndSet(suspension.closed);
        return suspension;
    }

    public boolean recoverAndReopen(V0CatalogAuthority exactAuthority, Suspension suspension) {
        if (!coldLane(exactAuthority) || retired || suspension == null || suspension.catalogIdentity != identity
                || gate.get() != suspension.closed || !empty()) {
            return false;
        }
        try {
            requireSourceRoot();
            for (var entry : writers.entrySet()) {
                Writer writer = entry.getValue();
                if (!writer.proof.proved) {
                    writer.storage.close();
                    Writer reopened = Writer.open(sourceRoot, entry.getKey());
                    entry.setValue(requireOriginalSource(writer.source, reopened));
                    writer = reopened;
                }
                recover(writer, suspension);
            }
        } catch (IOException unavailable) {
            for (Writer writer : writers.values()) {
                writer.proof.proved = false;
            }
            return false;
        }
        return gate.compareAndSet(suspension.closed, new Gate(PHASE_OPEN));
    }

    public Disposition retire(V0CatalogAuthority exactAuthority) {
        if (!coldLane(exactAuthority) || retired) {
            return Disposition.STALE_OR_RELEASED;
        }
        Gate predecessor = gate.getAndSet(new Gate(PHASE_STOPPED));
        if (predecessor.phase == PHASE_SUSPENDED) {
            // The cold lane calls retirement after the terminal mailbox handoff. P5 has
            // already consumed any delivered completion, so a remaining provisional row
            // belongs to the original cold request whose completion could not transfer.
            discardUndelivered();
        }
        if (!empty()) {
            return Disposition.CATALOG_BUSY;
        }
        retired = true;
        boolean failed = false;
        for (Writer writer : writers.values()) {
            writer.proof.proved = false;
            try {
                writer.storage.close();
            } catch (IOException closeFailure) {
                failed = true;
            }
        }
        writers.clear();
        return failed ? Disposition.FENCED : Disposition.RELEASED;
    }

    private void recover(Writer writer, Suspension suspension) throws IOException {
        var catalog = writer.catalog;
        var pending = catalog.recoverRuntimePreparation(context.catalogContext(), livePins(writer.proof));
        if (pending.isPresent()) {
            var prepared = pending.orElseThrow();
            var proof = catalog.verifyCompatibility(prepared, context).proof()
                    .orElseThrow(() -> new IOException("Runtime recovery has no matching C proof"));
            catalog.publishRuntime(prepared, context, proof, new WriterMutation(this, suspension.closed));
        }
        catalog.captureRuntimeReadback(context);
        writer.proof.proved = true;
    }

    ScopeDCatalogImage engineeringReadback(V0CatalogAuthority exactAuthority, SkillOwnerId owner)
            throws IOException {
        if (!coldLane(exactAuthority) || retired || owner == null) {
            return null;
        }
        Gate expected = gate.get();
        if (expected.phase != PHASE_OPEN) {
            return null;
        }
        var exact = writer(owner).catalog.captureRuntimeReadback(context);
        return gate.get() == expected ? exact.image : null;
    }

    // Physical mutation entry for the engineering-only assembly. No I/O on the RAM gate.
    boolean publishFixture(V0CatalogAuthority exactAuthority, SkillOwnerId owner, PublicationPlan fixture)
            throws IOException {
        return write(exactAuthority, owner, fixture);
    }

    boolean sweep(V0CatalogAuthority exactAuthority, SkillOwnerId owner) throws IOException {
        return write(exactAuthority, owner, null);
    }

    private boolean write(V0CatalogAuthority exactAuthority, SkillOwnerId owner, PublicationPlan fixture)
            throws IOException {
        if (!coldLane(exactAuthority) || retired) {
            return false;
        }
        Gate expected = gate.get();
        if (expected.phase != PHASE_OPEN) {
            return false;
        }
        Gate writing = new Gate(PHASE_WRITING);
        if (!gate.compareAndSet(expected, writing)) {
            return false;
        }
        Writer writer = null;
        try {
            writer = writer(owner);
            var catalog = writer.catalog;
            catalog.captureRuntimeReadback(context);
            var reservation = catalog.reserve();
            var prepared = fixture == null
                    ? catalog.prepareRuntimeSweep(reservation, context.catalogContext(), livePins(writer.proof))
                    : catalog.prepare(reservation, context.catalogContext(), fixture.material(reservation));
            var check = catalog.verifyCompatibility(prepared, context);
            if (check.proof().isEmpty()) {
                gate.compareAndSet(writing, new Gate(PHASE_OPEN));
                return false;
            }
            catalog.publishRuntime(prepared, context, check.proof().orElseThrow(), new WriterMutation(this, writing));
            catalog.captureRuntimeReadback(context);
            gate.compareAndSet(writing, new Gate(PHASE_OPEN));
            return true;
        } catch (IOException failure) {
            fenceWriter(writer, writing);
            throw failure;
        } catch (RuntimeException | Error failure) {
            fenceWriter(writer, writing);
            throw failure;
        }
    }

    private void discardUndelivered() {
        for (int index = 0; index < pins.length(); index++) {
            Lease lease = pins.get(index);
            if (lease != null && !lease.accepting) {
                remove(lease, LEASESTATE_PROVISIONAL);
            }
        }
    }

    private Writer writer(SkillOwnerId owner) throws IOException {
        requireSourceRoot();
        Writer writer = writers.get(owner);
        if (writer == null) {
            writer = Writer.open(sourceRoot, owner);
            writers.put(owner, writer);
        }
        return writer;
    }

    private void requireSourceRoot() throws IOException {
        try {
            BasicFileAttributes root = Files.readAttributes(sourceRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Object observed = root.fileKey();
            if (!root.isDirectory() || observed == null || sourceRootKey != null && !sourceRootKey.equals(observed)) {
                throw new IOException("Exact V0 catalog source root cannot be proved");
            }
            sourceRootKey = observed;
        } catch (IOException lostRoot) {
            for (Writer writer : writers.values()) {
                writer.proof.proved = false;
            }
            throw lostRoot;
        }
    }

    private static Writer requireOriginalSource(UUID exact, Writer reopened) throws IOException {
        if (exact.equals(reopened.source)) {
            return reopened;
        }
        var failure = new IOException("V0 recovery did not re-open the original source incarnation");
        try {
            reopened.storage.close();
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        throw failure;
    }

    private boolean coldLane(V0CatalogAuthority exactAuthority) {
        if (exactAuthority != authority || exactAuthority.isOwnerThread()) {
            return false;
        }
        Thread caller = Thread.currentThread();
        coldOwner.compareAndSet(null, caller);
        return coldOwner.get() == caller;
    }

    private boolean ownerThread(V0CatalogAuthority exactAuthority) {
        return exactAuthority == authority && exactAuthority.isOwnerThread();
    }

    private void fenceWriter(Writer writer, Gate expected) {
        if (writer != null) {
            writer.proof.proved = false;
        }
        gate.compareAndSet(expected, new Gate(PHASE_FAULTED));
    }

    private boolean owns(Lease lease, byte state) {
        return pins.get(lease.slot) == lease && lease.state == state;
    }

    private Disposition remove(Lease lease, byte state) {
        if (!owns(lease, state)) {
            return Disposition.STALE_OR_RELEASED;
        }
        if (!pins.compareAndSet(lease.slot, lease, null)) {
            return Disposition.CATALOG_BUSY;
        }
        lease.state = LEASESTATE_RELEASED;
        return Disposition.RELEASED;
    }

    private int provisionalSlot() {
        int available = -1;
        for (int index = 0; index < pins.length(); index++) {
            Lease lease = pins.get(index);
            if (lease == null) {
                available = index;
            } else if (lease.state == LEASESTATE_PROVISIONAL) {
                return -1;
            }
        }
        return available;
    }

    private int acceptedCount() {
        int count = 0;
        for (int index = 0; index < pins.length(); index++) {
            Lease lease = pins.get(index);
            if (lease != null && lease.state != LEASESTATE_PROVISIONAL) {
                count++;
            }
        }
        return count;
    }

    private boolean empty() {
        for (int index = 0; index < pins.length(); index++) {
            if (pins.get(index) != null) {
                return false;
            }
        }
        return true;
    }

    private Set<ExactReference> livePins(WriterProof proof) {
        Set<ExactReference> refs = new HashSet<>();
        for (int index = 0; index < pins.length(); index++) {
            Lease lease = pins.get(index);
            if (lease != null && lease.writer == proof) {
                refs.addAll(lease.refs);
            }
        }
        return Set.copyOf(refs);
    }

    private static Set<ExactReference> exactPins(ScopeDCompiler.Compiled program) {
        Set<ExactReference> refs = new HashSet<>();
        for (var binding : program.program().key().closure()) {
            refs.add(binding.reference());
        }
        program.manifest().ifPresent(origin -> refs.add(origin.manifest()));
        if (refs.isEmpty() || refs.size() > MAX_PINS_PER_LEASE) {
            throw new IllegalStateException("Unqualified V0 pin set");
        }
        return Set.copyOf(refs);
    }

    private static Refusal compilerRefusal(ScopeDResolver.Root root, ScopeDCompilerDiagnostics.Report report) {
        for (var row : report.entries()) {
            if (row.fatal()) {
                switch (row.code()) {
                    case SESSION_BUSY -> { return Refusal.CATALOG_BUSY; }
                    case CAPACITY -> { return Refusal.CAPACITY; }
                    case STALE_CONTEXT, STALE_GENERATION -> { return Refusal.HEAD_STALE; }
                    default -> { }
                }
            }
        }
        return root instanceof ScopeDResolver.ManifestEntryRoot ? Refusal.MANIFEST_UNAVAILABLE : Refusal.ROOT_UNAVAILABLE;
    }

    private static Rejected reject(Refusal reason) {
        return new Rejected(reason);
    }

    private static final byte PHASE_OPEN = 0;
    private static final byte PHASE_WRITING = 1;
    private static final byte PHASE_SUSPENDED = 2;
    private static final byte PHASE_FAULTED = 3;
    private static final byte PHASE_STOPPED = 4;

    private static final byte LEASESTATE_PROVISIONAL = 0;
    private static final byte LEASESTATE_ACCEPTED = 1;
    private static final byte LEASESTATE_RELEASING = 2;
    private static final byte LEASESTATE_RELEASED = 3;

    private static final class Gate {
        private final byte phase;

        private Gate(byte phase) {
            this.phase = phase;
        }
    }

    static final class WriterMutation {
        private final V0RuntimeCatalog owner;
        private final Gate exact;

        private WriterMutation(V0RuntimeCatalog owner, Gate exact) {
            this.owner = owner;
            this.exact = exact;
        }

        boolean current() {
            return owner.gate.get() == exact;
        }
    }

    private static final class Writer {
        private final WriterProof proof = new WriterProof();
        private final ScopeDStorage storage;
        private final ScopeDCatalog catalog;
        private final UUID source;

        private Writer(ScopeDStorage storage, ScopeDCatalog catalog, UUID source) {
            this.storage = storage;
            this.catalog = catalog;
            this.source = source;
        }

        private static Writer open(Path sourceRoot, SkillOwnerId owner) throws IOException {
            ScopeDStorage storage = ScopeDSource.open(sourceRoot, owner);
            try {
                return new Writer(storage, new ScopeDCatalog(owner, storage), storage.sourceId());
            } catch (IOException | RuntimeException | Error primary) {
                try {
                    storage.close();
                } catch (IOException closeFailure) {
                    primary.addSuppressed(closeFailure);
                }
                throw primary;
            }
        }
    }

    private static final class WriterProof {
        private volatile boolean proved = true;
    }

    static abstract class PublicationPlan {
        abstract List<FormalDocument> material(ScopeDCatalog.Reservation reservation) throws IOException;
    }

    static final class ProjectionCheck {
        private static final String LAUNCH = "launch_step_milli";
        private static final int ENGINEERING_LAUNCH_STEP_MILLI = 1_000;
        private static final int MAX_PROFILE_BYTES = 128;
        private static final int LINEAGE_HEADER = 108;
        private static final int MANIFEST_PROVENANCE = 24;
        private static final int CREATION_INPUT = 6;
        private static final int UNIT_BASE = 53;
        private static final int AUTHORED_ID = Long.BYTES;
        private static final int STATEMENT_BYTES = 11;
        private static final java.util.regex.Pattern PROFILE_ID =
                java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

        private final int entryCap;
        private final boolean normal;
        private Refusal refusal;
        private Unit root;
        private byte[] frontendDigest;
        private byte[] programDigest;
        private ManifestReference manifestReference;
        private long stableEntry;
        private int charge;
        private final int launchStepMilli = ENGINEERING_LAUNCH_STEP_MILLI;

        ProjectionCheck(int entryCap) {
            this(entryCap, false);
        }

        ProjectionCheck(int entryCap, boolean normal) {
            this.entryCap = entryCap;
            this.normal = normal;
        }

        Refusal refusal() {
            return refusal;
        }

        void qualify(ScopeDProgram.SpellProgram program, Optional<ScopeDCompiler.ManifestOrigin> manifest,
                     Map<SpellReference, ScopeDProgram.SpellUnit> whole) {
            if (program.key().closure().size() > 2) {
                refusal = Refusal.PROFILE_REJECTED;
                return;
            }
            try {
                if (manifest.isPresent()) {
                    var origin = manifest.orElseThrow();
                    if (origin.entries().size() > entryCap) {
                        refusal = Refusal.CAPACITY;
                        return;
                    }
                    for (var entry : origin.entries()) {
                        try {
                            projectRoot(entry.spell(), whole);
                        } catch (ProfileFailure unsupported) {
                            refusal = Refusal.MANIFEST_UNAVAILABLE;
                            return;
                        }
                    }
                }
                root = projectRoot(program.key().root(), whole);
                var selected = new HashSet<SpellReference>();
                selected.add(root.exact);
                if (root.hitCreate != null) {
                    selected.add(root.hitCreate.target.exact);
                }
                if (manifest.isPresent()) {
                    var origin = manifest.orElseThrow();
                    manifestReference = origin.manifest();
                    stableEntry = origin.entry().value();
                }
                charge = programCharge(manifest.isPresent(), unitCharge(root),
                        root.hitCreate == null ? 0 : unitCharge(root.hitCreate.target));
                if (program.key().closure().size() != selected.size()
                        || !program.key().closure().stream().allMatch(binding -> selected.contains(binding.reference()))) {
                    refusal = Refusal.PROFILE_REJECTED;
                    return;
                }
                for (var binding : program.key().closure()) {
                    Unit unit = root.exact.equals(binding.reference()) ? root : root.hitCreate.target;
                    unit.contentDigest = java.util.HexFormat.of().parseHex(binding.contentDigest());
                }
                // C key carries the exact content bindings. Domain separation binds their approved V0
                // projection, fixed engineering launch input and retained context without a wire codec.
                var identity = program.key().context();
                frontendDigest = java.util.HexFormat.of().parseHex(identity.catalogFingerprint());
                var digest = digest();
                digest.update("gramarye:scope-v/v0-projectile/1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                digest.update(frontendDigest);
                updateLong(digest, program.key().version());
                updateLong(digest, root.exact.identity().id().value());
                updateLong(digest, root.exact.revision().value());
                updateLong(digest, launchStepMilli);
                for (var binding : program.key().closure()) {
                    updateLong(digest, binding.reference().identity().id().value());
                    updateLong(digest, binding.reference().revision().value());
                    digest.update(java.util.HexFormat.of().parseHex(binding.contentDigest()));
                }
                programDigest = digest.digest();
            } catch (ProfileFailure unsupported) {
                refusal = unsupported.reason;
            } catch (ArithmeticException overflow) {
                refusal = Refusal.CAPACITY;
            }
        }

        private Unit projectRoot(SpellReference root, Map<SpellReference, ScopeDProgram.SpellUnit> whole)
                throws ProfileFailure {
            var lowered = whole.get(root);
            validateCommon(lowered);
            if (lowered.activation().isEmpty()) {
                throw new ProfileFailure(Refusal.NO_ACTIVATION);
            }
            var activation = lowered.activation().orElseThrow();
            if (!kind(activation.descriptor(), normal ? "gramarye:normal_cast" : "gramarye:v0_engineering")
                    || !activation.descriptor().arguments().isEmpty() || !launchFacts(activation.inputRequirements())) {
                throw new ProfileFailure(Refusal.PROFILE_REJECTED);
            }
            return project(lowered, whole, true);
        }

        private static Unit project(ScopeDProgram.SpellUnit lowered,
                                    Map<SpellReference, ScopeDProgram.SpellUnit> whole, boolean root)
                throws ProfileFailure {
            validateCommon(lowered);
            if (!root && lowered.activation().isPresent()) {
                throw new ProfileFailure(Refusal.PROFILE_REJECTED);
            }
            long onCreate = 0;
            long onHit = 0;
            Damage damage = null;
            Create create = null;
            int accountedInstructions = 0;
            for (var handler : lowered.handlers()) {
                String event = handler.event().tag();
                if (handler.event().version() != 1 || handler.start() != accountedInstructions) {
                    throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                }
                if (event.equals("gramarye:on_create") && onCreate == 0 && handler.start() == handler.end()
                        && handler.facts().isEmpty() && !hasAuthoredBlock(lowered, handler.id())) {
                    onCreate = handler.id().value();
                } else if (event.equals("gramarye:on_hit") && onHit == 0 && launchFacts(handler.facts())) {
                    onHit = handler.id().value();
                    int authored = 0;
                    for (int index = handler.start(); index < handler.end(); index++) {
                        if (++authored > 2) {
                            throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                        }
                        long block = blockId(lowered, handler.id(), index);
                        switch (lowered.instructions().get(index)) {
                            case ScopeDProgram.Effect effect -> {
                                if (authored != 1 || damage != null) {
                                    throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                                }
                                damage = new Damage(onHit, block, damageHp(effect));
                            }
                            case ScopeDProgram.CreateChild child -> {
                                if (!root || authored != 2 || damage == null || create != null
                                        || child.exactTarget().equals(lowered.reference()) || !launchFacts(child.inputs())) {
                                    throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                                }
                                var target = project(whole.get(child.exactTarget()), whole, false);
                                create = new Create(onHit, block, target);
                            }
                            default -> throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                        }
                    }
                } else {
                    throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                }
                accountedInstructions = handler.end();
            }
            if (accountedInstructions != lowered.instructions().size()
                    || lowered.cost().blocks() != (damage == null ? 0 : create == null ? 1 : 2)) {
                throw new ProfileFailure(Refusal.PROFILE_REJECTED);
            }
            return new Unit(lowered.reference(), root, onCreate, onHit, profile(lowered.appearance()), damage, create);
        }

        private static void validateCommon(ScopeDProgram.SpellUnit lowered) throws ProfileFailure {
            if (lowered == null || !kind(lowered.backend(), "gramarye:v0_projectile")
                    || !lowered.category().equals(CATEGORY)
                    || !lowered.backend().arguments().isEmpty()
                    || !lowered.capabilities().equals(List.of(CAPABILITY)) || !lowered.settings().isEmpty()
                    || lowered.inputs().size() != 1) {
                throw new ProfileFailure(Refusal.PROFILE_REJECTED);
            }
            var input = lowered.inputs().getFirst();
            if (!input.name().equals(LAUNCH) || input.field().type() != ValueType.INTEGER || !input.field().required()
                    || input.field().defaultValue().isPresent()) {
                throw new ProfileFailure(Refusal.PROFILE_REJECTED);
            }
        }

        private static boolean hasAuthoredBlock(ScopeDProgram.SpellUnit spell, HandlerId handler) {
            return spell.sourceMap().stream().anyMatch(range -> range.address().domain() == ScopeDProgram.SourceDomain.BLOCK
                    && range.address().handler().orElseThrow().equals(handler));
        }

        private static long blockId(ScopeDProgram.SpellUnit spell, HandlerId handler, int index) throws ProfileFailure {
            long block = 0;
            for (var range : spell.sourceMap()) {
                if (range.address().domain() == ScopeDProgram.SourceDomain.BLOCK
                        && range.address().handler().orElseThrow().equals(handler)
                        && range.firstInstruction() == index && range.endInstruction() == index + 1) {
                    if (block != 0) {
                        throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                    }
                    block = range.address().block().orElseThrow().value();
                }
            }
            if (block == 0) {
                throw new ProfileFailure(Refusal.PROFILE_REJECTED);
            }
            return block;
        }

        private static int damageHp(ScopeDProgram.Effect effect) throws ProfileFailure {
            var descriptor = effect.descriptor();
            if (!kind(descriptor, "gramarye:v0_hit_damage")
                    || effect.operation() != ScopeDFrontend.EffectOperation.DESCRIPTOR_EFFECT
                    || descriptor.arguments().size() != 1 || !effect.facts().isEmpty()) {
                throw new ProfileFailure(Refusal.PROFILE_REJECTED);
            }
            var amount = descriptor.arguments().getFirst();
            if (amount.name().equals("damage_hp") && amount.value() instanceof IntegerValue integer
                    && integer.value() >= 1 && integer.value() <= 4) {
                return (int) integer.value();
            }
            throw new ProfileFailure(Refusal.PROFILE_REJECTED);
        }

        private static Profile profile(ScopeDProgram.Appearance appearance) throws ProfileFailure {
            if (appearance.state() != ScopeDProgram.VisualState.KNOWN) {
                return null;
            }
            var descriptor = appearance.descriptor().orElseThrow();
            if (!kind(descriptor, "gramarye:v0_profile_triple") || descriptor.arguments().size() != 3) {
                return null;
            }
            String sound = profileId(descriptor, "sound_profile");
            String particle = profileId(descriptor, "particle_profile");
            String trail = profileId(descriptor, "trail_profile");
            return sound == null || particle == null || trail == null ? null : new Profile(sound, particle, trail);
        }

        private static String profileId(Descriptor descriptor, String name) throws ProfileFailure {
            for (var argument : descriptor.arguments()) {
                if (argument.name().equals(name) && argument.value() instanceof TextValue text) {
                    if (utf8(text.value()) > MAX_PROFILE_BYTES) {
                        throw new ProfileFailure(Refusal.PROFILE_REJECTED);
                    }
                    return PROFILE_ID.matcher(text.value()).matches() ? text.value() : null;
                }
            }
            return null;
        }

        private static int programCharge(boolean manifest, int rootBytes, int childBytes) {
            if (rootBytes < 0 || childBytes < 0) {
                throw new ArithmeticException("Negative V0 program charge component");
            }
            int lineageBytes = LINEAGE_HEADER + CREATION_INPUT + (manifest ? MANIFEST_PROVENANCE : 0);
            int bytes = Math.addExact(lineageBytes, Math.addExact(rootBytes, childBytes));
            if (bytes > MAX_PROGRAM_BYTES) {
                throw new ArithmeticException("V0 program charge exceeds capacity");
            }
            return bytes;
        }

        private static int unitCharge(Unit unit) {
            int result = UNIT_BASE;
            if (unit.onCreate != 0) {
                result = Math.addExact(result, AUTHORED_ID);
            }
            if (unit.onHit != 0) {
                result = Math.addExact(result, AUTHORED_ID);
            }
            if (unit.hitDamage != null) {
                result = Math.addExact(result, STATEMENT_BYTES);
            }
            if (unit.hitCreate != null) {
                result = Math.addExact(result, STATEMENT_BYTES);
            }
            if (unit.profile != null) {
                result = Math.addExact(result, 3 * Short.BYTES);
                result = Math.addExact(result, utf8(unit.profile.soundId));
                result = Math.addExact(result, utf8(unit.profile.particleId));
                result = Math.addExact(result, utf8(unit.profile.trailId));
            }
            return result;
        }

        private static int utf8(String value) {
            return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        private static boolean kind(Descriptor descriptor, String tag) {
            return descriptor.version() == 1 && descriptor.tag().equals(tag);
        }

        private static boolean launchFacts(List<ScopeDProgram.Fact> facts) {
            return facts.size() == 1 && facts.getFirst().name().equals(LAUNCH)
                    && facts.getFirst().type() == ValueType.INTEGER;
        }

        private static java.security.MessageDigest digest() {
            try {
                return java.security.MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException error) {
                throw new AssertionError(error);
            }
        }

        private static void updateLong(java.security.MessageDigest digest, long value) {
            for (int shift = 56; shift >= 0; shift -= 8) {
                digest.update((byte) (value >>> shift));
            }
        }
    }

    private static final class ProfileFailure extends Exception {
        private final Refusal reason;

        private ProfileFailure(Refusal reason) {
            this.reason = reason;
        }
    }

    public sealed interface PrepareResult permits Candidate, Rejected { }

    /** Actor-free hint, issued only after real receiver publication and durable readback. */
    public static final class BootstrapReadiness {
        private final Selection selection;
        private final Refusal refusal;

        private BootstrapReadiness(Selection selection, Refusal refusal) {
            this.selection = selection;
            this.refusal = refusal;
        }

        private static BootstrapReadiness rejected(Refusal refusal) {
            return new BootstrapReadiness(null, refusal);
        }

        public boolean ready() { return selection != null; }

        public Optional<Selection> selection() { return Optional.ofNullable(selection); }

        public Optional<Refusal> refusal() { return Optional.ofNullable(refusal); }
    }

    public static final class BootstrapCleanupReport {
        private final BootstrapCleanup result;
        private final BootstrapCleanupReceipt receipt;

        private BootstrapCleanupReport(BootstrapCleanup result, BootstrapCleanupReceipt receipt) {
            this.result = result; this.receipt = receipt;
        }

        public BootstrapCleanup result() { return result; }
        public Optional<BootstrapCleanupReceipt> receipt() { return Optional.ofNullable(receipt); }
    }

    /** No public data or constructor. Only a completed, P11-permitted cold operation can issue it. */
    public static final class BootstrapCleanupReceipt {
        private final Object catalogIdentity;
        private final SkillOwnerId owner;
        private final UUID source;
        private final CatalogGeneration generation;
        private final String digest;

        private BootstrapCleanupReceipt(Object catalogIdentity, SkillOwnerId owner, UUID source,
                CatalogGeneration generation, String digest) {
            this.catalogIdentity = catalogIdentity; this.owner = owner; this.source = source;
            this.generation = generation; this.digest = digest;
        }
    }

    /** Source-bound whole-roster evidence, revalidated under admission's lock before seed removal. */
    public static final class BootstrapRosterProof {
        private final V0RuntimeCatalog catalog;
        private final Set<SkillOwnerId> roster;
        private final Map<SkillOwnerId, BootstrapCleanupReceipt> receipts;
        private final UUID seedSource;
        private final SpellReference seed;
        private final CatalogGeneration seedGeneration;
        private final String seedDigest;
        private boolean issued;
        private boolean consumed;

        private BootstrapRosterProof(V0RuntimeCatalog catalog, Set<SkillOwnerId> roster,
                Map<SkillOwnerId, BootstrapCleanupReceipt> receipts, UUID seedSource, SpellReference seed,
                CatalogGeneration seedGeneration, String seedDigest) {
            this.catalog = catalog; this.roster = Set.copyOf(roster); this.receipts = Map.copyOf(receipts);
            this.seedSource = seedSource; this.seed = seed; this.seedGeneration = seedGeneration;
            this.seedDigest = seedDigest;
        }

        /** Sole consumer is the original root's retirement issuer; this never creates a catalog permit. */
        public synchronized boolean authorizeSeed(V0CatalogAuthority exact, V0RuntimeCatalog source) {
            if (issued || consumed || source != catalog || exact != catalog.authority || !exact.isOwnerThread()) {
                return false;
            }
            issued = true;
            return true;
        }

        private boolean consume(V0RuntimeCatalog source, V0CatalogAuthority exact,
                Set<SkillOwnerId> actualRoster, ScopeDCatalog.RuntimeReadback actualSeed, SpellDefinition seedBody)
                throws IOException {
            synchronized (this) {
                if (!issued || consumed || source != catalog || exact != catalog.authority || exact.isOwnerThread()) {
                    return false;
                }
                consumed = true;
            }
            return roster.equals(actualRoster) && receiptRosterMatches(actualRoster, receipts)
                    && seedSource.equals(actualSeed.source) && seed.equals(seedBody.reference())
                    && seedGeneration.equals(actualSeed.image.generation())
                    && seedDigest.equals(V0NormalBootstrap.digest(seedBody))
                    && catalog.validateCleanupReceipts(receipts, seedSource);
        }
    }

    private record BootstrapWitness(ForkProvenance provenance, String seedDigest, UUID playerSource,
                                    SpellReference player, String playerDigest) { }

    public static final class Suspension {
        private final Object catalogIdentity;
        private final Gate closed = new Gate(PHASE_SUSPENDED);

        private Suspension(Object catalogIdentity) {
            this.catalogIdentity = catalogIdentity;
        }
    }

    public static final class Candidate implements PrepareResult {
        private final Object catalogIdentity;
        private final long captureTicket;
        private final Gate expected;
        private final Gate committed = new Gate(PHASE_OPEN);
        private final Lease lease;

        private Candidate(Object catalogIdentity, long captureTicket, Gate expected, Lease lease) {
            this.catalogIdentity = catalogIdentity;
            this.captureTicket = captureTicket;
            this.expected = expected;
            this.lease = lease;
        }

        public Lease lease() {
            return lease;
        }
    }

    public static final class Rejected implements PrepareResult {
        private final Refusal reason;

        private Rejected(Refusal reason) {
            this.reason = reason;
        }

        public Refusal reason() {
            return reason;
        }
    }

    public static final class Selection {
        private final boolean manifest;
        private final long stableId;
        private final long entryId;
        private final BootstrapWitness bootstrap;

        private Selection(boolean manifest, long stableId, long entryId) {
            this(manifest, stableId, entryId, null);
        }

        private Selection(boolean manifest, long stableId, long entryId, BootstrapWitness bootstrap) {
            if (stableId <= 0 || manifest && entryId <= 0) {
                throw new IllegalArgumentException("V0 stable identities must be positive");
            }
            this.manifest = manifest;
            this.stableId = stableId;
            this.entryId = entryId;
            this.bootstrap = bootstrap;
        }

        public static Selection standalone(long spellId) {
            return new Selection(false, spellId, 0);
        }

        public static Selection manifest(long manifestId, long stableEntryId) {
            return new Selection(true, manifestId, stableEntryId);
        }

        private ScopeDResolver.Root resolve(ScopeDCatalogImage image, SkillOwnerId owner) {
            if (manifest) {
                var id = new ManifestIdentity(owner, new SkillManifestId(stableId));
                var exact = image.routes().get(id);
                return exact instanceof ManifestReference reference
                        ? new ScopeDResolver.ManifestEntryRoot(reference, new ManifestEntryId(entryId)) : null;
            }
            var id = new SpellIdentity(owner, new SpellDefinitionId(stableId));
            var exact = image.routes().get(id);
            return exact instanceof SpellReference reference ? new ScopeDResolver.StandaloneSpellRoot(reference) : null;
        }
    }

    public static final class Lease {
        private final int slot;
        private final WriterProof writer;
        private final Set<ExactReference> refs;
        private final UUID source;
        private final long generation;
        private final String readbackDigest;
        private final byte[] frontendDigest;
        private final byte[] programDigest;
        private final ManifestReference manifestReference;
        private final long stableEntry;
        private final int launchStepMilli;
        private final int charge;
        private final Unit root;
        private volatile byte state = LEASESTATE_PROVISIONAL;
        private volatile boolean accepting;

        private Lease(int slot, WriterProof writer, Set<ExactReference> refs,
                      ProjectionCheck projection, ScopeDCatalog.RuntimeReadback readback) {
            this.slot = slot;
            this.writer = writer;
            this.refs = refs;
            source = readback.source;
            generation = readback.image.generation().value();
            readbackDigest = readback.physical.digest();
            frontendDigest = projection.frontendDigest;
            programDigest = projection.programDigest;
            manifestReference = projection.manifestReference;
            stableEntry = projection.stableEntry;
            launchStepMilli = projection.launchStepMilli;
            charge = projection.charge;
            root = projection.root;
        }

        public int programChargeBytes() {
            return charge;
        }

        public Unit root() {
            return root;
        }
    }

    public static final class Unit {
        private final SpellReference exact;
        private byte[] contentDigest;
        private final boolean activation;
        private final long onCreate;
        private final long onHit;
        private final Profile profile;
        private final Damage hitDamage;
        private final Create hitCreate;

        private Unit(SpellReference exact, boolean activation, long onCreate, long onHit,
                     Profile profile, Damage hitDamage, Create hitCreate) {
            this.exact = exact;
            this.activation = activation;
            this.onCreate = onCreate;
            this.onHit = onHit;
            this.profile = profile;
            this.hitDamage = hitDamage;
            this.hitCreate = hitCreate;
        }

        public Optional<Profile> profile() {
            return Optional.ofNullable(profile);
        }

        public Optional<Damage> hitDamage() {
            return Optional.ofNullable(hitDamage);
        }

        public Optional<Create> hitCreate() {
            return Optional.ofNullable(hitCreate);
        }
    }

    public static final class Profile {
        private final String soundId;
        private final String particleId;
        private final String trailId;

        private Profile(String soundId, String particleId, String trailId) {
            this.soundId = soundId;
            this.particleId = particleId;
            this.trailId = trailId;
        }

        public String soundId() { return soundId; }

        public String particleId() { return particleId; }

        public String trailId() { return trailId; }
    }

    public static final class Damage {
        private final long handlerId;
        private final long blockId;
        private final int damageHp;

        private Damage(long handlerId, long blockId, int damageHp) {
            this.handlerId = handlerId;
            this.blockId = blockId;
            this.damageHp = damageHp;
        }

        public long handlerId() { return handlerId; }

        public long blockId() { return blockId; }

        public int damageHp() { return damageHp; }
    }

    public static final class Create {
        private final long handlerId;
        private final long blockId;
        private final Unit target;

        private Create(long handlerId, long blockId, Unit target) {
            this.handlerId = handlerId;
            this.blockId = blockId;
            this.target = target;
        }

        public long handlerId() { return handlerId; }

        public long blockId() { return blockId; }

        public Unit target() { return target; }
    }

    public enum Refusal {
        INVALID_SELECTION, SLOT_STALE, TICKET_STALE, SOURCE_UNPROVED,
        HEAD_STALE, CATALOG_BUSY, ROOT_UNAVAILABLE, MANIFEST_UNAVAILABLE,
        NO_ACTIVATION, PROFILE_REJECTED, CAPACITY, PREPARATION_FAULT
    }

    public enum Acceptance { COMMITTED, CATALOG_BUSY, HEAD_STALE, INVALID_RESERVATION }

    public enum BootstrapCleanup { REMOVED, NO_MATCH, REFUSED, SOURCE_UNPROVED }

    public enum Disposition { RELEASED, CATALOG_BUSY, STALE_OR_RELEASED, FENCED }
}
