package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

/** Package-confined preparation and readback. No server composition installs this owner in M1. */
final class ScopeDCatalog {
    private final SkillOwnerId owner;
    private final ScopeDStorage storage;
    private ScopeDStorage.Image readback;
    private ScopeDCatalogImage current;
    private Reservation active;

    ScopeDCatalog(SkillOwnerId owner, ScopeDStorage storage) throws IOException {
        this.owner = Objects.requireNonNull(owner);
        this.storage = Objects.requireNonNull(storage);
        if (!owner.equals(storage.owner())) {
            throw new IOException("Catalog principal and physical owner differ");
        }
        current();
    }

    synchronized ScopeDCatalogImage current() throws IOException {
        ScopeDStorage.Image physical = storage.current();
        if (readback != physical) {
            ScopeDCatalogImage decoded = physical.generation() == 0
                    ? ScopeDCatalogImage.empty(owner)
                    : ScopeDCatalogImage.decode(owner, physical.generation(), physical.payload());
            validateAllocatedMaterial(decoded, physical.transaction());
            current = decoded;
            readback = physical;
            active = null;
        }
        return current;
    }

    synchronized Reservation reserve() throws IOException {
        current();
        if (storage.hasPendingMaterial()) {
            throw new IOException("Pending catalog material requires recovery");
        }
        long serial = storage.reserve();
        ScopeDStorage.Image base = storage.current();
        active = new Reservation(owner, base, current.generation(), serial);
        return active;
    }

    synchronized Prepared prepare(Reservation reservation, Context context, List<FormalDocument> changes)
            throws IOException {
        return prepare(reservation, context, changes, Set.of(), null);
    }

    synchronized Prepared prepareDraft(Reservation reservation, Context context, Draft draft) throws IOException {
        ScopeDCatalogImage base = current();
        var graph = ScopeDGraph.build(owner, base.generation(), context.descriptors, base.currentBodies());
        FormalDocument document = switch (draft) {
            case SpellDraft spell -> ScopeDSubmission.prepare(graph, owner, spell,
                    spell.baseRevision().isPresent() ? reservation.nextSpell(spell.identity()) : reservation.newSpell(1));
            case ManifestDraft manifest -> ScopeDSubmission.prepare(graph, owner, manifest,
                    manifest.baseRevision().isPresent() ? reservation.nextManifest(manifest.identity()) : reservation.newManifest(1));
        };
        return prepare(reservation, context, List.of(document));
    }

    synchronized ScopeDFork.Bundle exportFork(Context context, ExactReference root) throws IOException {
        ScopeDCatalogImage base = current();
        var graph = ScopeDGraph.build(owner, base.generation(), context.descriptors, base.currentBodies());
        return ScopeDFork.exportBundle(graph, storage.sourceId(), owner, root);
    }

    synchronized Prepared prepareFork(Reservation reservation, Context context, ScopeDFork.Bundle bundle)
            throws IOException {
        Map<Identity, ExactReference> allocations = new HashMap<>();
        int ordinal = 0;
        for (Identity source : bundle.identities()) {
            int index = ++ordinal;
            allocations.put(source, source instanceof SpellIdentity ? reservation.newSpell(index) : reservation.newManifest(index));
        }
        return prepare(reservation, context, List.copyOf(ScopeDFork.importBundle(bundle, owner, allocations).values()));
    }

    synchronized Prepared prepareSweep(Reservation reservation, Context context, RootInventory inventory)
            throws IOException {
        return prepare(reservation, context, List.of(), Set.of(), Objects.requireNonNull(inventory));
    }

    /** The removal path is internal and has no production caller or user-facing delete operation. */
    synchronized Prepared prepare(
            Reservation reservation,
            Context context,
            List<FormalDocument> changes,
            Set<Identity> removals,
            RootInventory inventory) throws IOException {
        ScopeDCatalogImage base = current();
        if (reservation == null || reservation != active || reservation.base != storage.current()) {
            throw new IOException("Stale catalog reservation");
        }
        if (changes.size() > MAX_BATCH || removals.size() > MAX_BATCH - changes.size()) {
            throw new IOException("Catalog batch capacity");
        }
        active = null;
        Map<Identity, ExactReference> routes = new HashMap<>(base.routes());
        Map<ExactReference, FormalDocument> retained = new HashMap<>(base.revisions());
        Map<Identity, ScopeDCatalogImage.LocalHighWater> local = new HashMap<>(base.localHighWater());
        Set<Identity> changed = new HashSet<>();
        Set<Long> newIds = new HashSet<>();
        for (Identity identity : removals) {
            if (!owner.equals(identity.owner()) || routes.remove(identity) == null) {
                throw new IOException("Invalid removal identity");
            }
            local.remove(identity);
            changed.add(identity);
        }
        for (FormalDocument document : changes) {
            ExactReference reference = document.reference();
            Identity identity = reference.identity();
            if (!owner.equals(identity.owner()) || !changed.add(identity)
                    || ScopeDCatalogImage.revision(reference) != reservation.serial) {
                throw new IOException("Invalid, foreign, or repeated candidate identity");
            }
            ExactReference previous = routes.get(identity);
            if (previous == null) {
                long id = ScopeDCatalogImage.id(reference);
                if (id / ID_STRIDE != reservation.serial || id % ID_STRIDE < 1 || id % ID_STRIDE > MAX_BATCH
                        || !newIds.add(id)) {
                    throw new IOException("Identity was not reserved for this candidate");
                }
            }
            local.put(identity, advanceLocals(local.getOrDefault(identity, ScopeDCatalogImage.LocalHighWater.EMPTY),
                    retained.get(previous), document));
            routes.put(identity, reference);
            if (retained.putIfAbsent(reference, document) != null) {
                throw new IOException("Revision already allocated");
            }
        }
        Set<ExactReference> external = base.externalRoots();
        if (inventory != null) {
            if (inventory.base != reservation.base || !inventory.contextDigest.equals(context.fingerprint())
                    || storage.hasPendingMaterial()) {
                throw new IOException("Incomplete or stale root inventory");
            }
            external = inventory.exactRoots;
            Set<ExactReference> roots = new HashSet<>(routes.values());
            roots.addAll(external);
            if (!retained.keySet().containsAll(roots)) {
                throw new IOException("Required root material missing");
            }
            retained.keySet().retainAll(roots);
        }
        if (base.generation().value() == Long.MAX_VALUE) {
            throw new IOException("Catalog generation exhausted");
        }
        CatalogGeneration next = new CatalogGeneration(base.generation().value() + 1);
        ScopeDCatalogImage candidate = new ScopeDCatalogImage(owner, next, routes, retained, local, external);
        validateAllocatedMaterial(candidate, reservation.serial);
        ScopeDGraph oldGraph = ScopeDGraph.build(owner, base.generation(), context.descriptors, base.currentBodies());
        ScopeDGraph newGraph = ScopeDGraph.build(owner, next, context.descriptors, candidate.currentBodies());
        String contextDigest = context.fingerprint();
        ScopeDGraph.validateCandidate(oldGraph, newGraph, !reservation.base.contextDigest().equals(contextDigest));
        byte[] bytes = candidate.encode();
        ProofBinding binding = new ProofBinding(storage.sourceId(), owner, base.generation(), next,
                new PublicationId(reservation.serial), reservation.base.digest(), sha256(bytes), contextDigest, 1);
        return new Prepared(reservation.base, binding, bytes);
    }

    /** A complete inventory is captured by the sole owner against its exact current snapshot. */
    synchronized RootInventory captureRoots(Context context, List<ExactReference> rootsToCapture)
            throws IOException {
        ScopeDCatalogImage base = current();
        if (rootsToCapture.size() > MAX_ROUTES || storage.hasPendingMaterial()
                || base.revisions().values().stream().anyMatch(FormalDocument::hasOpaqueRaw)) {
            throw new IOException("Incomplete root source");
        }
        Set<ExactReference> roots = new HashSet<>(base.externalRoots());
        ScopeDGraph graph = ScopeDGraph.build(owner, base.generation(), context.descriptors, base.currentBodies());
        roots.addAll(graph.exactRootUnion(rootsToCapture));
        if (roots.size() > MAX_EXTERNAL_ROOTS || !base.revisions().keySet().containsAll(roots)) {
            throw new IOException("Missing exact root or external roots capacity");
        }
        return new RootInventory(readback, Set.copyOf(roots), context.fingerprint());
    }

    /** The production entry is closed until a real frontend is adopted in C. */
    synchronized ScopeDCatalogImage publish(Prepared prepared, Context currentContext, CompatibilityProof proof)
            throws IOException {
        if (proof == null || !proof.binding.equals(prepared.binding)) {
            throw new IOException("Compiler frontend compatibility proof required");
        }
        ScopeDStorage.Image staged = stage(prepared, currentContext);
        storage.commitPreparedImage(prepared.base, staged);
        return current();
    }

    /** A prepared image may be staged in isolation without authorizing current publication. */
    synchronized ScopeDStorage.Image stage(Prepared prepared, Context context) throws IOException {
        if (storage.current() != prepared.base || !context.fingerprint().equals(prepared.binding.contextDigest)
                || !storage.sourceId().equals(prepared.binding.source)) {
            throw new IOException("Stale base or frontend context");
        }
        Optional<ScopeDStorage.Image> pending = storage.pending();
        if (pending.isPresent()) {
            ScopeDStorage.Image image = pending.orElseThrow();
            if (image.transaction() != prepared.binding.transaction.value()
                    || !image.contextDigest().equals(prepared.binding.contextDigest)
                    || !sha256(image.payload()).equals(prepared.binding.contentDigest)) {
                throw new IOException("Pending transaction differs from the prepared image");
            }
            return image;
        }
        return storage.stage(prepared.base, prepared.binding.transaction.value(),
                prepared.binding.contextDigest, prepared.bytes);
    }

    synchronized Optional<Prepared> revalidatePending(Context context) throws IOException {
        ScopeDCatalogImage base = current();
        Optional<ScopeDStorage.Image> pending = storage.pending();
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        ScopeDStorage.Image image = pending.orElseThrow();
        byte[] bytes = image.payload();
        String fingerprint = context.fingerprint();
        if (!image.contextDigest().equals(fingerprint)) {
            throw new IOException("Pending image context is stale");
        }
        ScopeDCatalogImage candidate = ScopeDCatalogImage.decode(owner, image.generation(), bytes);
        validateAllocatedMaterial(candidate, image.transaction());
        for (var entry : candidate.revisions().entrySet()) {
            FormalDocument original = base.revisions().get(entry.getKey());
            if (original != null && !original.equals(entry.getValue())) {
                throw new IOException("Pending image changes an immutable retained revision");
            }
        }
        if (!candidate.externalRoots().containsAll(base.externalRoots())) {
            throw new IOException("Pending image omits retained external roots");
        }
        var oldGraph = ScopeDGraph.build(owner, base.generation(), context.descriptors, base.currentBodies());
        var newGraph = ScopeDGraph.build(owner, candidate.generation(), context.descriptors, candidate.currentBodies());
        ScopeDGraph.validateCandidate(oldGraph, newGraph, !readback.contextDigest().equals(fingerprint));
        var binding = new ProofBinding(storage.sourceId(), owner, base.generation(), candidate.generation(),
                new PublicationId(image.transaction()), readback.digest(), sha256(bytes), fingerprint, 1);
        return Optional.of(new Prepared(readback, binding, bytes));
    }

    private static ScopeDCatalogImage.LocalHighWater advanceLocals(
            ScopeDCatalogImage.LocalHighWater limits, FormalDocument previous, FormalDocument replacement) throws IOException {
        if (replacement instanceof SpellDefinition spell) {
            Set<Long> oldHandlers = previous instanceof SpellDefinition old ? values(old.handlerIds(), HandlerId::value) : Set.of();
            Set<Long> oldBlocks = previous instanceof SpellDefinition old ? values(old.blockIds(), BlockId::value) : Set.of();
            return new ScopeDCatalogImage.LocalHighWater(
                    advance(limits.handlers(), oldHandlers, values(spell.handlerIds(), HandlerId::value)),
                    advance(limits.blocks(), oldBlocks, values(spell.blockIds(), BlockId::value)), 0);
        }
        Set<Long> oldEntries = previous instanceof SkillManifest old ? values(old.entryIds(), ManifestEntryId::value) : Set.of();
        return new ScopeDCatalogImage.LocalHighWater(0, 0,
                advance(limits.entries(), oldEntries, values(((SkillManifest) replacement).entryIds(), ManifestEntryId::value)));
    }

    private static <T> Set<Long> values(List<T> ids, ToLongFunction<T> value) {
        return ids.stream().mapToLong(value).boxed().collect(Collectors.toUnmodifiableSet());
    }

    private static long advance(long watermark, Set<Long> existing, Set<Long> candidate) throws IOException {
        long result = watermark;
        for (long id : candidate) {
            if (!existing.contains(id) && id <= watermark) {
                throw new IOException("Retired local authoring identity cannot be reused");
            }
            result = Math.max(result, id);
        }
        return result;
    }

    private static void validateAllocatedMaterial(ScopeDCatalogImage image, long serial) throws IOException {
        for (var entry : image.revisions().entrySet()) {
            ExactReference exact = entry.getKey();
            long id = ScopeDCatalogImage.id(exact);
            long revision = ScopeDCatalogImage.revision(exact);
            if (id / ID_STRIDE < 1 || id % ID_STRIDE < 1 || id % ID_STRIDE > MAX_BATCH
                    || id / ID_STRIDE > revision || revision > serial) {
                throw new IOException("Revision outside owner allocation watermark");
            }
            ScopeDCatalogImage.LocalHighWater high = image.localHighWater().get(exact.identity());
            if (high != null && !advanceLocals(high, entry.getValue(), entry.getValue()).equals(high)) {
                throw new IOException("Local identity outside allocation watermark");
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Mandatory SHA-256 unavailable", unavailable);
        }
    }

    record Context(DescriptorContext descriptors, String policyDigest) {
        Context {
            Objects.requireNonNull(descriptors);
            if (policyDigest.length() != 64 || !policyDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid frontend context digest");
            }
        }

        String fingerprint() {
            var value = new StringBuilder(policyDigest).append('\n');
            descriptors.descriptors().stream().map(key -> key.domain() + ":" + key.tag() + ":" + key.version())
                    .sorted().forEach(key -> value.append(key).append('\n'));
            value.append('\n');
            descriptors.capabilities().stream().sorted().forEach(key -> value.append(key).append('\n'));
            return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    record ProofBinding(UUID source, SkillOwnerId owner, CatalogGeneration base, CatalogGeneration candidate,
                        PublicationId transaction, String predecessorDigest, String contentDigest, String contextDigest,
                        int verificationVersion) {}

    /** No issuer is installed or implemented in M1. The constructor remains closed to callers. */
    static final class CompatibilityProof {
        private final ProofBinding binding;

        private CompatibilityProof(ProofBinding binding) {
            this.binding = binding;
        }
    }

    static final class Prepared {
        private final ScopeDStorage.Image base;
        private final ProofBinding binding;
        private final byte[] bytes;

        private Prepared(ScopeDStorage.Image base, ProofBinding binding, byte[] bytes) {
            this.base = base;
            this.binding = binding;
            this.bytes = bytes;
        }

        ProofBinding binding() { return binding; }
        byte[] bytes() { return bytes.clone(); }
    }

    static final class Reservation {
        private final SkillOwnerId owner;
        private final ScopeDStorage.Image base;
        private final CatalogGeneration generation;
        private final long serial;

        private Reservation(SkillOwnerId owner, ScopeDStorage.Image base, CatalogGeneration generation, long serial) {
            this.owner = owner;
            this.base = base;
            this.generation = generation;
            this.serial = serial;
        }

        CatalogGeneration generation() { return generation; }

        SpellReference newSpell(int ordinal) {
            return nextSpell(new SpellIdentity(owner, new SpellDefinitionId(newId(ordinal))));
        }

        ManifestReference newManifest(int ordinal) {
            return nextManifest(new ManifestIdentity(owner, new SkillManifestId(newId(ordinal))));
        }

        SpellReference nextSpell(SpellIdentity identity) {
            return new SpellReference(identity, new SpellRevision(serial));
        }

        ManifestReference nextManifest(ManifestIdentity identity) {
            return new ManifestReference(identity, new ManifestRevision(serial));
        }

        private long newId(int ordinal) {
            if (ordinal < 1 || ordinal > MAX_BATCH) {
                throw new IllegalArgumentException("Invalid candidate ordinal");
            }
            return Math.addExact(Math.multiplyExact(serial, ID_STRIDE), ordinal);
        }
    }

    static final class RootInventory {
        private final ScopeDStorage.Image base;
        private final Set<ExactReference> exactRoots;
        private final String contextDigest;

        private RootInventory(ScopeDStorage.Image base, Set<ExactReference> exactRoots, String contextDigest) {
            this.base = base;
            this.exactRoots = exactRoots;
            this.contextDigest = contextDigest;
        }
    }
}
