package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resolves one immutable catalog image. Results contain data only and cannot authorize publication. */
final class ScopeDResolver {
    private static final Comparator<SpellIdentity> ID_ORDER = Comparator.comparingLong(value -> value.id().value());
    private static final Comparator<SpellDefinition> DOCUMENT_ORDER =
            Comparator.comparing(value -> value.reference().identity(), ID_ORDER);

    private final Snapshot snapshot;
    private final ScopeDCatalog.Context context;
    private final ScopeDGraph graph;
    private int rootsChecked;
    private int steps;

    private ScopeDResolver(Snapshot snapshot, ScopeDCatalog.Context context, ScopeDGraph graph) {
        this.snapshot = snapshot;
        this.context = context;
        this.graph = graph;
    }

    static ScopeDResolver open(UUID source, ScopeDCatalogImage image, ScopeDCatalog.Context context) {
        var snapshot = new Snapshot(source, image.owner(), image.generation(), context.fingerprint());
        var graph = ScopeDGraph.build(image.owner(), image.generation(), context.descriptors(), image.currentBodies());
        return new ScopeDResolver(snapshot, context, graph);
    }

    Snapshot snapshot() {
        return snapshot;
    }

    synchronized Result resolve(Root root, CatalogGeneration expectedGeneration, ScopeDCatalog.Context expectedContext) {
        var rejection = check(root.exact(), expectedGeneration, expectedContext);
        if (rejection != null) {
            return rejection;
        }
        int start = steps;
        try {
            return switch (root) {
                case StandaloneSpellRoot spell -> {
                    var closure = capture(spell.reference());
                    yield new Resolved(snapshot, root, spellClosure(spell.reference(), closure), Optional.empty(),
                            new Work(1, closure.documents().size(), closure.edges(), 0, steps - start));
                }
                case ManifestEntryRoot entry -> {
                    var whole = capture(entry.reference());
                    var available = manifestAvailability(entry.reference(), whole);
                    var selected = available.entries().stream().filter(value -> value.entryId().equals(entry.entryId()))
                            .findFirst();
                    if (selected.isEmpty()) {
                        yield rejected(root.exact(), Reason.ENTRY_UNAVAILABLE);
                    }
                    var spell = selected.orElseThrow().spell();
                    var closure = capture(spell);
                    yield new Resolved(snapshot, root, spellClosure(spell, closure), Optional.of(available),
                            new Work(2, available.bindings().size() + closure.documents().size(),
                                    whole.edges() + closure.edges(), available.entries().size(), steps - start));
                }
            };
        } catch (ScopeDGraph.Rejected failure) {
            return graphRejected(root.exact(), failure);
        } catch (Capacity failure) {
            return rejected(root.exact(), failure.reason);
        }
    }

    synchronized ManifestResult resolveManifest(
            ManifestReference manifest, CatalogGeneration expectedGeneration, ScopeDCatalog.Context expectedContext) {
        var rejection = check(manifest, expectedGeneration, expectedContext);
        if (rejection != null) {
            return rejection;
        }
        int start = steps;
        try {
            var closure = capture(manifest);
            var available = manifestAvailability(manifest, closure);
            return new AvailableManifest(snapshot, available,
                    new Work(1, available.bindings().size(), closure.edges(), available.entries().size(), steps - start));
        } catch (ScopeDGraph.Rejected failure) {
            return graphRejected(manifest, failure);
        } catch (Capacity failure) {
            return rejected(manifest, failure.reason);
        }
    }

    private Rejected check(
            ExactReference root, CatalogGeneration expectedGeneration, ScopeDCatalog.Context expectedContext) {
        if (!snapshot.generation().equals(expectedGeneration)) {
            return rejected(root, Reason.STALE_GENERATION);
        }
        if (!context.equals(expectedContext)) {
            return rejected(root, Reason.CONTEXT_MISMATCH);
        }
        if (rootsChecked == MAX_COMPILER_RESOLVER_ROOTS) {
            return rejected(root, Reason.ROOT_LIMIT);
        }
        rootsChecked++;
        return null;
    }

    private ScopeDGraph.Closure capture(ExactReference reference) {
        if (steps == MAX_COMPILER_RESOLVER_WORK) {
            throw new Capacity(Reason.WORK_LIMIT);
        }
        try {
            // A retained body alone does not witness the identity routes of its historical closure.
            var closure = graph.closure(reference, MAX_COMPILER_RESOLVER_WORK - steps);
            steps += closure.work();
            return closure;
        } catch (ScopeDGraph.Rejected failure) {
            steps += failure.work();
            throw failure;
        }
    }

    private static SpellClosure spellClosure(SpellReference root, ScopeDGraph.Closure closure) {
        var spells = collectSpells(closure);
        return new SpellClosure(root, spells.bindings(), spells.documents(), orderedComponents(closure));
    }

    private ManifestAvailability manifestAvailability(ManifestReference reference, ScopeDGraph.Closure closure) {
        var spells = collectSpells(closure);
        var document = (SkillManifest) closure.documents().get(reference);
        // A successful graph closure already checked the exact Manifest body, required slot and M-DUP.
        if (!(document.body().entries() instanceof Known<List<ManifestEntry>> known)) {
            throw new IllegalStateException("Manifest graph accepted an unavailable entry slot");
        }
        int entries = known.value().size();
        // Charge binding all entries and the at-most-linear selection of a stable entry.
        consume(2 * entries);
        var bindings = new ArrayList<EntryBinding>(entries);
        for (var entry : known.value()) {
            var target = spells.bindings().get(entry.target().target());
            if (target == null) {
                throw new IllegalStateException("Manifest graph omitted an exact entry target");
            }
            bindings.add(new EntryBinding(entry.id(), target));
        }
        return new ManifestAvailability(document, List.copyOf(bindings), spells.bindings(), spells.documents(),
                orderedComponents(closure));
    }

    private void consume(int work) {
        if (work > MAX_COMPILER_RESOLVER_WORK - steps) {
            throw new Capacity(Reason.WORK_LIMIT);
        }
        steps += work;
    }

    private static SpellDocuments collectSpells(ScopeDGraph.Closure closure) {
        int size = closure.documents().size() - (closure.root() instanceof ManifestReference ? 1 : 0);
        if (size > MAX_COMPILER_EXACT_BINDINGS) {
            throw new Capacity(Reason.BINDING_LIMIT);
        }
        var bindings = new LinkedHashMap<SpellIdentity, SpellReference>();
        var documents = new LinkedHashMap<SpellReference, SpellDefinition>();
        closure.documents().values().stream().filter(SpellDefinition.class::isInstance)
                .map(SpellDefinition.class::cast).sorted(DOCUMENT_ORDER).forEachOrdered(spell -> {
                    var reference = spell.reference();
                    bindings.put(reference.identity(), reference);
                    documents.put(reference, spell);
                });
        return new SpellDocuments(Collections.unmodifiableMap(bindings), Collections.unmodifiableMap(documents));
    }

    private static List<List<SpellIdentity>> orderedComponents(ScopeDGraph.Closure closure) {
        return closure.cyclicComponents().stream()
                .map(component -> component.stream().sorted(ID_ORDER).toList())
                .sorted(Comparator.comparing(List::getFirst, ID_ORDER)).toList();
    }

    private Rejected rejected(ExactReference root, Reason reason) {
        return new Rejected(snapshot, root, reason, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private Rejected graphRejected(ExactReference root, ScopeDGraph.Rejected failure) {
        Reason reason = failure.reason() == ScopeDGraph.Reason.WORK_LIMIT ? Reason.WORK_LIMIT : Reason.STRUCTURE;
        var location = failure.location();
        Optional<StructuralIssues> issues = failure.reason() == ScopeDGraph.Reason.UNAVAILABLE_DOCUMENT
                ? location.flatMap(graph::find).map(document -> new StructuralIssues(document.reference(),
                        ScopeDData.validate(document, context.descriptors())))
                : Optional.empty();
        return new Rejected(snapshot, root, reason, Optional.of(failure.reason()), location, issues);
    }

    sealed interface Root permits StandaloneSpellRoot, ManifestEntryRoot {
        ExactReference exact();
    }

    record StandaloneSpellRoot(SpellReference reference) implements Root {
        StandaloneSpellRoot { Objects.requireNonNull(reference); }

        @Override
        public ExactReference exact() { return reference; }
    }

    record ManifestEntryRoot(ManifestReference reference, ManifestEntryId entryId) implements Root {
        ManifestEntryRoot {
            Objects.requireNonNull(reference);
            Objects.requireNonNull(entryId);
        }

        @Override
        public ExactReference exact() { return reference; }
    }

    record Snapshot(UUID source, SkillOwnerId owner, CatalogGeneration generation, String contextFingerprint) {
        Snapshot {
            Objects.requireNonNull(source);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(generation);
            Objects.requireNonNull(contextFingerprint);
        }
    }

    sealed interface Result permits Resolved, Rejected {}

    sealed interface ManifestResult permits AvailableManifest, Rejected {}

    record Resolved(Snapshot snapshot, Root root, SpellClosure selected,
                    Optional<ManifestAvailability> manifest, Work work) implements Result {}

    record AvailableManifest(Snapshot snapshot, ManifestAvailability manifest, Work work) implements ManifestResult {}

    /** A structural result. The compiler still checks every reachable Spell against its trusted frontend. */
    record ManifestAvailability(SkillManifest document, List<EntryBinding> entries,
                                Map<SpellIdentity, SpellReference> bindings, Map<SpellReference, SpellDefinition> documents,
                                List<List<SpellIdentity>> cyclicComponents) {
        ManifestAvailability {
            entries = List.copyOf(entries);
            bindings = orderedCopy(bindings);
            documents = orderedCopy(documents);
            cyclicComponents = componentCopy(cyclicComponents);
        }
    }

    record EntryBinding(ManifestEntryId entryId, SpellReference spell) {}

    record SpellClosure(SpellReference root, Map<SpellIdentity, SpellReference> bindings,
                        Map<SpellReference, SpellDefinition> documents, List<List<SpellIdentity>> cyclicComponents) {
        SpellClosure {
            bindings = orderedCopy(bindings);
            documents = orderedCopy(documents);
            cyclicComponents = componentCopy(cyclicComponents);
        }
    }

    private static <K, V> Map<K, V> orderedCopy(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static List<List<SpellIdentity>> componentCopy(List<List<SpellIdentity>> values) {
        return values.stream().map(List::copyOf).toList();
    }

    record Work(int closureTraversals, int exactBindingsChecked, int edgesVisited, int manifestEntriesChecked, int steps) {}

    enum Reason { STALE_GENERATION, CONTEXT_MISMATCH, ROOT_LIMIT, WORK_LIMIT, BINDING_LIMIT, ENTRY_UNAVAILABLE, STRUCTURE }

    record StructuralIssues(ExactReference document, Validation validation) {}

    record Rejected(Snapshot snapshot, ExactReference root, Reason reason, Optional<ScopeDGraph.Reason> graphReason,
                    Optional<Identity> location, Optional<StructuralIssues> issues) implements Result, ManifestResult {}

    private record SpellDocuments(Map<SpellIdentity, SpellReference> bindings,
                                  Map<SpellReference, SpellDefinition> documents) {}

    private static final class Capacity extends IllegalArgumentException {
        private final Reason reason;

        private Capacity(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }
    }
}
