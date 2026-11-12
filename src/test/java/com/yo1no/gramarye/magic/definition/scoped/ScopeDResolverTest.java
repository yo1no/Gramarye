package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDGraphTest.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ScopeDResolverTest {
    private static final CatalogGeneration GENERATION = new CatalogGeneration(9);
    private static final ScopeDCatalog.Context COMPATIBILITY_CONTEXT =
            new ScopeDCatalog.Context(CONTEXT, "0".repeat(64));

    @Test
    void diamondBindsEachExactDocumentOnceInStableIdentityOrder() {
        var root = spell(7, 3, identity(3), identity(1));
        var left = spell(3, 2, identity(4));
        var right = spell(1, 8, identity(4));
        var leaf = spell(4, 5);
        var resolver = open(root, left, right, leaf);
        var result = resolved(resolver, new ScopeDResolver.StandaloneSpellRoot(root.reference()));
        var reverse = resolved(open(leaf, right, left, root), result.root());

        assertEquals(result, reverse);
        assertEquals(new ScopeDResolver.Snapshot(SOURCE, OWNER, GENERATION, COMPATIBILITY_CONTEXT.fingerprint()),
                result.snapshot());
        assertEquals(root.reference(), result.selected().root());
        assertEquals(List.of(right.reference(), left.reference(), leaf.reference(), root.reference()),
                List.copyOf(result.selected().documents().keySet()));
        assertEquals(leaf.reference(), result.selected().bindings().get(identity(4)));
        assertEquals(1, result.work().closureTraversals());
        assertEquals(4, result.work().exactBindingsChecked());
        assertEquals(4, result.work().edgesVisited());
        assertTrue(result.selected().cyclicComponents().isEmpty());
        assertTrue(result.manifest().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.selected().bindings().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.selected().documents().clear());
    }

    @Test
    void newlyPublishedRouteCannotRewriteAnOldClosureOrReconstructAHistoricalRoot() {
        var oldRoot = spell(1, 3, identity(2));
        var oldTarget = spell(2, 4);
        var oldView = open(oldRoot, oldTarget);
        var oldAddress = new ScopeDResolver.StandaloneSpellRoot(oldRoot.reference());
        var oldClosure = resolved(oldView, oldAddress).selected();

        var currentTarget = spell(2, 6);
        var currentRoot = spell(1, 7, identity(2));
        var next = new CatalogGeneration(GENERATION.value() + 1);
        var view = ScopeDResolver.open(SOURCE, image(next, List.of(currentRoot, currentTarget),
                List.of(oldRoot, oldTarget)), COMPATIBILITY_CONTEXT);
        assertEquals(ScopeDResolver.Reason.STALE_GENERATION,
                rejection(view.resolve(oldAddress, GENERATION, COMPATIBILITY_CONTEXT)).reason());
        assertEquals(Optional.of(ScopeDGraph.Reason.EXACT_ROOT_UNAVAILABLE),
                rejection(view.resolve(oldAddress, next, COMPATIBILITY_CONTEXT)).graphReason());
        var newResult = assertInstanceOf(ScopeDResolver.Resolved.class, view.resolve(
                new ScopeDResolver.StandaloneSpellRoot(currentRoot.reference()), next, COMPATIBILITY_CONTEXT));
        assertEquals(oldTarget.reference(), oldClosure.bindings().get(identity(2)));
        assertEquals(currentTarget.reference(), newResult.selected().bindings().get(identity(2)));
    }

    @Test
    void missingForeignAndWrongContextReportTypedFailure() {
        var missing = spell(1, 1, identity(9));
        var foreignIdentity = new SpellIdentity(OTHER, new SpellDefinitionId(8));
        var foreign = spell(2, 1, foreignIdentity);
        var resolver = open(missing, foreign);
        var missingRoot = new ScopeDResolver.StandaloneSpellRoot(missing.reference());
        var foreignRoot = new ScopeDResolver.StandaloneSpellRoot(new SpellReference(foreignIdentity, new SpellRevision(1)));

        var dangling = rejection(resolver.resolve(missingRoot, GENERATION, COMPATIBILITY_CONTEXT));
        assertEquals(Optional.of(ScopeDGraph.Reason.MISSING_DEPENDENCY), dangling.graphReason());
        assertEquals(Optional.of(identity(9)), dangling.location());
        var badEdge = rejection(resolver.resolve(new ScopeDResolver.StandaloneSpellRoot(foreign.reference()),
                GENERATION, COMPATIBILITY_CONTEXT));
        assertEquals(Optional.of(ScopeDGraph.Reason.FOREIGN_SCOPE), badEdge.graphReason());
        assertEquals(Optional.of(foreignIdentity), badEdge.location());
        assertEquals(Optional.of(ScopeDGraph.Reason.FOREIGN_SCOPE),
                rejection(resolver.resolve(foreignRoot, GENERATION, COMPATIBILITY_CONTEXT)).graphReason());
        var wrongContext = new ScopeDCatalog.Context(CONTEXT, "1".repeat(64));
        assertEquals(ScopeDResolver.Reason.CONTEXT_MISMATCH,
                rejection(resolver.resolve(missingRoot, GENERATION, wrongContext)).reason());
    }

    @Test
    void manifestProvenanceKeepsWholeAvailabilitySeparateFromSelectedSpellClosure() {
        var a = spell(1, 3, identity(3));
        var b = spell(2, 4, identity(4));
        var aLeaf = spell(3, 5);
        var bLeaf = spell(4, 6);
        var manifest = manifest(8, 2, identity(2), identity(1));
        var resolver = open(a, b, aLeaf, bLeaf, manifest);
        var entryId = new ManifestEntryId(2);
        var entryRoot = new ScopeDResolver.ManifestEntryRoot(manifest.reference(), entryId);

        var selected = resolved(resolver, entryRoot);
        var standalone = resolved(resolver, new ScopeDResolver.StandaloneSpellRoot(a.reference()));
        var availability = selected.manifest().orElseThrow();
        assertEquals(entryRoot, selected.root());
        assertEquals(standalone.selected(), selected.selected());
        assertEquals(manifest, availability.document());
        assertEquals(List.of(new ScopeDResolver.EntryBinding(new ManifestEntryId(1), b.reference()),
                new ScopeDResolver.EntryBinding(entryId, a.reference())), availability.entries());
        assertEquals(Set.of(a.reference(), b.reference(), aLeaf.reference(), bLeaf.reference()),
                availability.documents().keySet());
        assertEquals(Set.of(a.reference(), aLeaf.reference()), selected.selected().documents().keySet());
        assertEquals(2, selected.work().closureTraversals());
        assertEquals(2, selected.work().manifestEntriesChecked());
        assertEquals(ScopeDResolver.Reason.ENTRY_UNAVAILABLE, rejection(resolver.resolve(
                new ScopeDResolver.ManifestEntryRoot(manifest.reference(), new ManifestEntryId(3)),
                GENERATION, COMPATIBILITY_CONTEXT)).reason());
    }

    @Test
    void aBadUnselectedManifestEntryOrDuplicateTargetCannotAffectStandaloneRoot() {
        var good = spell(1, 4);
        var unsupported = spell(2, 3);
        var body = unsupported.body();
        var unknownBackend = new Backend(new Descriptor(BACKEND, 73, List.of()), "gramarye:category", List.of());
        unsupported = new SpellDefinition(unsupported.reference(), new SpellBody(new Known<>(unknownBackend),
                body.activation(), body.settings(), body.handlers(), body.appearance(), body.phenomenon()), Optional.empty());
        var manifest = manifest(8, 2, identity(1), identity(2));
        var duplicate = manifest(9, 2, identity(1), identity(1));
        var resolver = open(good, unsupported, manifest, duplicate);

        var selected = new ScopeDResolver.ManifestEntryRoot(manifest.reference(), new ManifestEntryId(1));
        var rejected = rejection(resolver.resolve(selected, GENERATION, COMPATIBILITY_CONTEXT));
        assertEquals(Optional.of(ScopeDGraph.Reason.UNAVAILABLE_DOCUMENT), rejected.graphReason());
        var issue = rejected.issues().orElseThrow();
        assertEquals(unsupported.reference(), issue.document());
        assertEquals(List.of(new Issue(Code.UNSUPPORTED_DESCRIPTOR, "backend")), issue.validation().issues());
        assertFalse(issue.validation().valid());
        var duplicateResult = assertInstanceOf(ScopeDResolver.Rejected.class,
                resolver.resolveManifest(duplicate.reference(), GENERATION, COMPATIBILITY_CONTEXT));
        assertEquals(List.of(new Issue(Code.DUPLICATE_MANIFEST_SPELL, "entries[1]")),
                duplicateResult.issues().orElseThrow().validation().issues());
        assertEquals(Set.of(good.reference()), resolved(resolver,
                new ScopeDResolver.StandaloneSpellRoot(good.reference())).selected().documents().keySet());
    }

    @Test
    void coversEmptyAndLargestManifestWithoutInventingAnEntry() {
        var empty = manifest(1, 1);
        var spells = new ArrayList<FormalDocument>();
        var targets = new SpellIdentity[MAX_MANIFEST_ENTRIES];
        for (int index = 0; index < targets.length; index++) {
            targets[index] = identity(index + 1);
            spells.add(spell(index + 1, 5));
        }
        var full = manifest(2, 1, targets);
        spells.add(empty);
        spells.add(full);
        var resolver = open(spells.toArray(FormalDocument[]::new));

        var emptyResult = assertInstanceOf(ScopeDResolver.AvailableManifest.class,
                resolver.resolveManifest(empty.reference(), GENERATION, COMPATIBILITY_CONTEXT));
        assertTrue(emptyResult.manifest().entries().isEmpty());
        assertTrue(emptyResult.manifest().bindings().isEmpty());
        var fullResult = assertInstanceOf(ScopeDResolver.AvailableManifest.class,
                resolver.resolveManifest(full.reference(), GENERATION, COMPATIBILITY_CONTEXT));
        assertEquals(MAX_MANIFEST_ENTRIES, fullResult.manifest().entries().size());
        assertEquals(MAX_MANIFEST_ENTRIES, fullResult.manifest().bindings().size());
        assertEquals(MAX_MANIFEST_ENTRIES, fullResult.work().edgesVisited());
        var oversized = Arrays.copyOf(targets, targets.length + 1);
        oversized[targets.length] = identity(targets.length + 1);
        assertThrows(IllegalArgumentException.class, () -> manifest(3, 1, oversized));
    }

    @Test
    void exactBindingAndEdgeMaximaPreserveOneOrderedScc() {
        var full = new FormalDocument[MAX_COMPILER_EXACT_BINDINGS];
        for (int index = 0; index < full.length; index++) {
            var edges = new SpellIdentity[MAX_GRAPH_EDGES / MAX_COMPILER_EXACT_BINDINGS];
            Arrays.fill(edges, identity((index + 1) % full.length + 1));
            full[index] = spell(index + 1, 3, edges);
        }
        var root = new ScopeDResolver.StandaloneSpellRoot((SpellReference) full[0].reference());
        var result = resolved(open(full), root);
        assertEquals(MAX_COMPILER_EXACT_BINDINGS, result.selected().bindings().size());
        assertEquals(MAX_GRAPH_EDGES, result.work().edgesVisited());
        assertEquals(List.of(List.copyOf(result.selected().bindings().keySet())), result.selected().cyclicComponents());

        var extraRoute = Arrays.copyOf(full, full.length + 1);
        extraRoute[full.length] = spell(full.length + 1, 3);
        assertThrows(IllegalArgumentException.class, () -> open(extraRoute));
        var extraEdges = new SpellIdentity[MAX_GRAPH_EDGES / MAX_COMPILER_EXACT_BINDINGS + 1];
        Arrays.fill(extraEdges, identity(2));
        full[0] = spell(1, 3, extraEdges);
        assertEquals(ScopeDGraph.Reason.EDGE_LIMIT,
                assertThrows(ScopeDGraph.Rejected.class, () -> open(full)).reason());
    }

    @Test
    void sessionRootAndTraversalWorkLimitsCountAttemptsAndBothManifestTraversals() {
        var alone = spell(1, 3);
        var resolver = open(alone);
        var standalone = new ScopeDResolver.StandaloneSpellRoot(alone.reference());
        for (int checked = 0; checked < MAX_COMPILER_RESOLVER_ROOTS; checked++) {
            resolved(resolver, standalone);
        }
        assertEquals(ScopeDResolver.Reason.ROOT_LIMIT,
                rejection(resolver.resolve(standalone, GENERATION, COMPATIBILITY_CONTEXT)).reason());

        var large = new FormalDocument[MAX_ROUTES];
        int spells = large.length - 1;
        for (int index = 0; index < spells; index++) {
            var edges = new SpellIdentity[MAX_GRAPH_EDGES / MAX_ROUTES];
            Arrays.fill(edges, identity((index + 1) % spells + 1));
            large[index] = spell(index + 1, 3, edges);
        }
        var manifest = manifest(1, 3, identity(1));
        large[spells] = manifest;
        var repeated = open(large);
        var entry = new ScopeDResolver.ManifestEntryRoot(manifest.reference(), new ManifestEntryId(1));
        int work = resolved(repeated, entry).work().steps();
        assertTrue(work > 0);
        int accepted = MAX_COMPILER_RESOLVER_WORK / work;
        assertTrue(accepted < MAX_COMPILER_RESOLVER_ROOTS);
        for (int checked = 1; checked < accepted; checked++) {
            assertEquals(work, resolved(repeated, entry).work().steps());
        }
        assertEquals(ScopeDResolver.Reason.WORK_LIMIT,
                rejection(repeated.resolve(entry, GENERATION, COMPATIBILITY_CONTEXT)).reason());
    }

    private static ScopeDResolver.Resolved resolved(ScopeDResolver resolver, ScopeDResolver.Root root) {
        return assertInstanceOf(ScopeDResolver.Resolved.class, resolver.resolve(root, GENERATION, COMPATIBILITY_CONTEXT));
    }

    private static ScopeDResolver.Rejected rejection(ScopeDResolver.Result result) {
        return assertInstanceOf(ScopeDResolver.Rejected.class, result);
    }

    private static ScopeDResolver open(FormalDocument... current) {
        return ScopeDResolver.open(SOURCE, image(GENERATION, List.of(current), List.of()), COMPATIBILITY_CONTEXT);
    }

    private static ScopeDCatalogImage image(
            CatalogGeneration generation, List<FormalDocument> current, List<FormalDocument> historical) {
        Map<Identity, ExactReference> routes = new HashMap<>();
        Map<ExactReference, FormalDocument> revisions = new HashMap<>();
        Map<Identity, ScopeDCatalogImage.LocalHighWater> locals = new HashMap<>();
        current.forEach(document -> {
            var exact = document.reference();
            routes.put(exact.identity(), exact);
            revisions.put(exact, document);
            locals.put(exact.identity(), ScopeDCatalogImage.LocalHighWater.EMPTY);
        });
        historical.forEach(document -> revisions.put(document.reference(), document));
        return new ScopeDCatalogImage(OWNER, generation, routes, revisions, locals, Set.of());
    }
}
