package com.yo1no.gramarye.magic.definition.scoped;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Absent;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Backend;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Block;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.BlockId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.CatalogGeneration;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.CreateSpell;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Descriptor;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.DescriptorContext;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.DescriptorDomain;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.DescriptorKey;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ExactReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ForkProvenance;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.FormalDocument;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Handler;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.HandlerId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Identity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Known;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestBody;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestDraft;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestEntry;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestEntryId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestIdentity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.ManifestRevision;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.RawField;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.RawLocation;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SkillManifest;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SkillManifestId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellBody;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellDefinition;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellDefinitionId;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellDraft;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellIdentity;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellIdentityReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellReference;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.SpellRevision;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.Unknown;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ScopeDGraphTest {
    static final SkillOwnerId OWNER = new SkillOwnerId(new UUID(0, 1));
    static final SkillOwnerId OTHER = new SkillOwnerId(new UUID(0, 2));
    static final UUID SOURCE = new UUID(0, 3);
    static final String BACKEND = "gramarye:test";
    static final String EVENT = "gramarye:event";
    static final DescriptorContext CONTEXT = new DescriptorContext(Set.of(
            new DescriptorKey(DescriptorDomain.BACKEND, BACKEND, 1),
            new DescriptorKey(DescriptorDomain.EVENT, EVENT, 1)), Set.of());

    @Test
    void standaloneDiamondUsesExactUniqueDependenciesAndDoesNotNeedManifest() {
        var a = spell(1, 0, identity(2), identity(3));
        var b = spell(2, 2, identity(4));
        var c = spell(3, 3, identity(4));
        var d = spell(4, 7);
        var graph = graph(11, a, b, c, d);

        var closure = graph.closure(a.reference());
        assertEquals(Set.of(a.reference(), b.reference(), c.reference(), d.reference()), closure.documents().keySet());
        assertTrue(closure.cyclicComponents().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> closure.documents().clear());
        assertEquals(closure.documents().keySet(), graph.exactRootUnion(List.of(a.reference(), b.reference(), b.reference())));
        assertEquals(ScopeDGraph.Reason.EXACT_ROOT_UNAVAILABLE, assertThrows(ScopeDGraph.Rejected.class,
                () -> graph.closure(new SpellReference(d.reference().identity(), new SpellRevision(6)))).reason());
    }

    @Test
    void selfAndMutualCyclesAreFiniteStructuralComponents() {
        var a = spell(1, 0, identity(1), identity(2));
        var b = spell(2, 0, identity(3));
        var c = spell(3, 0, identity(2));
        var graph = graph(1, a, b, c);
        var closure = graph.closure(a.reference());
        assertEquals(Set.of(Set.of(identity(1)), Set.of(identity(2), identity(3))), Set.copyOf(closure.cyclicComponents()));
        assertEquals(3, closure.documents().size());
    }

    @Test
    void newSpellRevisionRevalidatesTransitiveCallersAndAllManifestEntriesWithoutReplacingThem() {
        var oldD = spell(4, 0);
        var newD = spell(4, 2, identity(5));
        var b = spell(2, 0, identity(4));
        var a = spell(1, 0, identity(2));
        var e = spell(5, 0);
        var manifest = manifest(1, 0, identity(1), identity(5));
        var before = graph(5, a, b, oldD, e, manifest);
        var after = graph(6, a, b, newD, e, manifest);

        var result = ScopeDGraph.validateCandidate(before, after);
        assertEquals(Set.of(identity(1), identity(2), identity(4), manifest.reference().identity()), result.affected());
        assertEquals(Set.of(a.reference(), b.reference(), newD.reference(), manifest.reference()), result.revalidated());
        assertSame(a, after.find(identity(1)).orElseThrow());
        assertSame(manifest, after.find(manifest.reference().identity()).orElseThrow());
        assertTrue(before.closure(a.reference()).documents().containsKey(oldD.reference()));
        assertFalse(after.closure(a.reference()).documents().containsKey(oldD.reference()));
    }

    @Test
    void removalRevalidatesOldReverseDependentsAndBadManifestDoesNotPoisonIndependentSpell() {
        var a = spell(1, 0, identity(2));
        var b = spell(2, 0);
        var manifest = manifest(1, 0, identity(1), identity(3));
        var graph = graph(1, a, b, manifest);
        assertEquals(Set.of(a.reference(), b.reference()), graph.closure(a.reference()).documents().keySet());
        assertEquals(ScopeDGraph.Reason.MISSING_DEPENDENCY, assertThrows(ScopeDGraph.Rejected.class,
                () -> graph.closure(manifest.reference())).reason());
        assertEquals(ScopeDGraph.Reason.MISSING_DEPENDENCY, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.validateCandidate(graph(1, a, b), graph(2, a))).reason());

        var withoutEdge = spell(1, 1);
        var after = graph(2, withoutEdge);
        var affected = ScopeDGraph.validateCandidate(graph(1, a, b), after);
        assertEquals(Set.of(identity(1), identity(2)), affected.affected());
        assertEquals(Set.of(withoutEdge.reference()), affected.revalidated());

        var membershipRemoved = ScopeDGraph.validateCandidate(graph, graph(2, a, b));
        assertEquals(Set.of(manifest.reference().identity()), membershipRemoved.affected());
        assertTrue(membershipRemoved.revalidated().isEmpty());
    }

    @Test
    void staleCatalogContextChangeAndAttemptedRevisionRewriteFailClosed() {
        var a = spell(1, 0);
        var aChangedAtSameRevision = spell(1, 0, identity(2));
        var aUnchangedAtNewRevision = spell(1, 1);
        var base = graph(7, a);
        assertEquals(ScopeDGraph.Reason.STALE_OR_FOREIGN_BASE, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.validateCandidate(base, graph(9, a))).reason());
        assertEquals(ScopeDGraph.Reason.IMMUTABLE_BODY_CHANGED, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.validateCandidate(base, graph(8, aChangedAtSameRevision))).reason());
        assertEquals(ScopeDGraph.Reason.UNCHANGED_REVISION, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.validateCandidate(base, graph(8, aUnchangedAtNewRevision))).reason());
        var contextWithoutBackend = new DescriptorContext(Set.of(), Set.of());
        var changedContext = ScopeDGraph.build(OWNER, new CatalogGeneration(8), contextWithoutBackend,
                Map.of(a.reference().identity(), a));
        assertEquals(ScopeDGraph.Reason.UNAVAILABLE_DOCUMENT, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.validateCandidate(base, changedContext)).reason());
        assertEquals(Set.of(a.reference()), ScopeDGraph.validateCandidate(base, graph(8, a), true).revalidated());
    }

    @Test
    void foreignReferenceRejectionDoesNotExposeForeignContentsAndInputRoutesAreCopied() {
        var foreign = new SpellIdentity(OTHER, new SpellDefinitionId(987));
        var a = spell(1, 0, foreign);
        var routes = new LinkedHashMap<Identity, FormalDocument>();
        routes.put(identity(1), a);
        var graph = ScopeDGraph.build(OWNER, new CatalogGeneration(1), CONTEXT, routes);
        routes.clear();
        var rejected = assertThrows(ScopeDGraph.Rejected.class, () -> graph.closure(a.reference()));
        assertEquals(ScopeDGraph.Reason.FOREIGN_SCOPE, rejected.reason());
        assertEquals("FOREIGN_SCOPE", rejected.getMessage());
        assertThrows(ScopeDGraph.Rejected.class,
                () -> graph.closure(new SpellReference(foreign, new SpellRevision(23))));
        var wrongRoute = Map.<Identity, FormalDocument>of(identity(2), a);
        assertEquals(ScopeDGraph.Reason.INVALID_ROUTE, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.build(OWNER, new CatalogGeneration(1), CONTEXT, wrongRoute)).reason());
    }

    @Test
    void maxAdmittedRoutesAndEdgesAreTraversableWithoutExpandingCycles() {
        var atLimit = new LinkedHashMap<Identity, FormalDocument>();
        for (int index = 1; index <= ScopeDLimits.MAX_ROUTES; index++) {
            var next = identity(index == ScopeDLimits.MAX_ROUTES ? 1 : index + 1);
            var repeated = new SpellIdentity[ScopeDLimits.MAX_GRAPH_EDGES / ScopeDLimits.MAX_ROUTES];
            java.util.Arrays.fill(repeated, next);
            atLimit.put(identity(index), spell(index, 0, repeated));
        }
        var graph = ScopeDGraph.build(OWNER, new CatalogGeneration(1), CONTEXT, atLimit);
        var closure = graph.closure(atLimit.get(identity(1)).reference());
        assertEquals(ScopeDLimits.MAX_ROUTES, closure.documents().size());
        assertEquals(List.of(atLimit.keySet()), closure.cyclicComponents());
        assertEquals(closure.documents().keySet(),
                graph.exactRootUnion(atLimit.values().stream().map(FormalDocument::reference).toList()));
        var oversizedRoots = java.util.Collections.nCopies(ScopeDLimits.MAX_ROUTES + 1, closure.root());
        assertEquals(ScopeDGraph.Reason.ROOT_LIMIT, assertThrows(ScopeDGraph.Rejected.class,
                () -> graph.exactRootUnion(oversizedRoots)).reason());

        var extraEdge = new SpellIdentity[1 + ScopeDLimits.MAX_GRAPH_EDGES / ScopeDLimits.MAX_ROUTES];
        java.util.Arrays.fill(extraEdge, identity(2));
        atLimit.put(identity(1), spell(1, 1, extraEdge));
        assertEquals(ScopeDGraph.Reason.EDGE_LIMIT, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.build(OWNER, new CatalogGeneration(2), CONTEXT, atLimit)).reason());
        var extraRoute = ScopeDLimits.MAX_ROUTES + 1;
        atLimit.put(identity(extraRoute), spell(extraRoute, 0));
        assertEquals(ScopeDGraph.Reason.ROUTE_LIMIT, assertThrows(ScopeDGraph.Rejected.class,
                () -> ScopeDGraph.build(OWNER, new CatalogGeneration(2), CONTEXT, atLimit)).reason());
    }

    @Test
    void manifestForkRemapsOneCopyOfEveryTransitiveSpellAndKeepsExactProvenance() {
        var a = spell(1, 8, identity(3));
        var b = spell(2, 6, identity(3));
        var shared = spell(3, 5, identity(1));
        var manifest = manifest(1, 4, identity(1), identity(2));
        var bundle = ScopeDFork.exportBundle(graph(12, a, b, shared, manifest), SOURCE, OWNER, manifest.reference());
        assertEquals(manifest.reference(), bundle.root());
        assertEquals(4, bundle.identities().size());

        var ra = receiverSpell(21);
        var rb = receiverSpell(22);
        var rs = receiverSpell(23);
        var rm = new ManifestReference(new ManifestIdentity(OTHER, new SkillManifestId(21)), new ManifestRevision(9));
        var allocations = Map.<Identity, ExactReference>of(identity(1), ra, identity(2), rb,
                identity(3), rs, manifest.reference().identity(), rm);
        var imported = ScopeDFork.importBundle(bundle, OTHER, allocations);
        assertEquals(4, imported.size());
        assertEquals(List.of(new SpellIdentityReference(rs.identity())), imported.get(ra.identity()).dependencies());
        assertEquals(List.of(new SpellIdentityReference(rs.identity())), imported.get(rb.identity()).dependencies());
        assertEquals(List.of(new SpellIdentityReference(ra.identity()), new SpellIdentityReference(rb.identity())),
                imported.get(rm.identity()).dependencies());
        var importedManifest = (SkillManifest) imported.get(rm.identity());
        assertEquals(Optional.of(new ForkProvenance(SOURCE, manifest.reference(), new CatalogGeneration(12))),
                importedManifest.provenance());
        var receiverGraph = ScopeDGraph.build(OTHER, new CatalogGeneration(1), CONTEXT, imported);
        assertEquals(Set.of(ra, rb, rs, rm), receiverGraph.closure(rm).documents().keySet());
        var differentSource = ScopeDFork.exportBundle(graph(12, a, b, shared, manifest),
                new UUID(0, 4), OWNER, manifest.reference());
        var fromDifferentSource = ScopeDFork.importBundle(differentSource, OTHER, allocations);
        assertFalse(importedManifest.provenance().equals(fromDifferentSource.get(rm.identity()).provenance()));
    }

    @Test
    void standaloneForkAndAllocationNegativesNeverPartiallyRemap() {
        var a = spell(1, 1, identity(2));
        var b = spell(2, 1);
        var source = graph(1, a, b);
        var bundle = ScopeDFork.exportBundle(source, SOURCE, OWNER, a.reference());
        var ra = receiverSpell(10);
        var rb = receiverSpell(11);
        var remapped = ScopeDFork.importBundle(bundle, OTHER, Map.of(identity(1), ra, identity(2), rb));
        assertEquals(Set.of(ra.identity(), rb.identity()), remapped.keySet());
        assertThrows(ScopeDFork.Rejected.class, () -> ScopeDFork.exportBundle(source, SOURCE, OTHER, a.reference()));
        assertThrows(ScopeDGraph.Rejected.class, () -> ScopeDFork.exportBundle(graph(1, a), SOURCE, OWNER, a.reference()));
        assertEquals(ScopeDFork.Reason.INCOMPLETE_ALLOCATION, assertThrows(ScopeDFork.Rejected.class,
                () -> ScopeDFork.importBundle(bundle, OTHER, Map.of(identity(1), ra))).reason());
        assertEquals(ScopeDFork.Reason.INVALID_ALLOCATION, assertThrows(ScopeDFork.Rejected.class,
                () -> ScopeDFork.importBundle(bundle, OTHER, Map.of(identity(1), ra, identity(2), ra))).reason());
    }

    @Test
    void gameplayUnknownBlocksWholeClosureAndVisualRawCannotMoveDuringFork() throws IOException {
        var body = spellBody();
        var visualRaw = new SpellDefinition(new SpellReference(identity(1), new SpellRevision(1)),
                new SpellBody(body.backend(), body.activation(), body.settings(), body.handlers(),
                        new Unknown<>(raw(identity(1), RawField.APPEARANCE)), body.phenomenon()), Optional.empty());
        var unknownGameplay = new SpellDefinition(new SpellReference(identity(2), new SpellRevision(1)),
                new SpellBody(new Unknown<>(raw(identity(2), RawField.BACKEND)), body.activation(), body.settings(),
                        body.handlers(), body.appearance(), body.phenomenon()), Optional.empty());
        var manifest = manifest(3, 0, identity(1), identity(2));
        var source = graph(3, visualRaw, unknownGameplay, manifest);

        assertEquals(Set.of(visualRaw.reference()), source.closure(visualRaw.reference()).documents().keySet());
        assertEquals(ScopeDGraph.Reason.UNAVAILABLE_DOCUMENT, assertThrows(ScopeDGraph.Rejected.class,
                () -> source.closure(manifest.reference())).reason());
        assertEquals(ScopeDFork.Reason.UNSAFE_RAW_REMAP, assertThrows(ScopeDFork.Rejected.class,
                () -> ScopeDFork.exportBundle(source, SOURCE, OWNER, visualRaw.reference())).reason());
    }

    @Test
    void exactForkClosureOverBatchLimitIsRejectedBeforeReceiverAllocation() {
        var routes = new LinkedHashMap<Identity, FormalDocument>();
        for (int index = 1; index <= ScopeDLimits.MAX_BATCH; index++) {
            routes.put(identity(index), spell(index, 0, identity(index + 1)));
        }
        int finalId = ScopeDLimits.MAX_BATCH + 1;
        routes.put(identity(finalId), spell(finalId, 0));
        var source = ScopeDGraph.build(OWNER, new CatalogGeneration(1), CONTEXT, routes);
        var root = routes.get(identity(1)).reference();
        assertEquals(finalId, source.closure(root).documents().size());
        assertEquals(ScopeDFork.Reason.BATCH_LIMIT, assertThrows(ScopeDFork.Rejected.class,
                () -> ScopeDFork.exportBundle(source, SOURCE, OWNER, root)).reason());
    }

    @Test
    void draftSubmissionUsesTheAllocatedExactAddressAndRejectsStaleOrForeignRequests() {
        var a = spell(1, 3);
        var b = spell(2, 4);
        var graph = graph(8, a, b);
        var allocation = new SpellReference(identity(1), new SpellRevision(9));
        var draft = new SpellDraft(identity(1), Optional.of(new SpellRevision(3)), new CatalogGeneration(8),
                spellBody(identity(2)));
        var proposed = ScopeDSubmission.prepare(graph, OWNER, draft, allocation);
        assertEquals(allocation, proposed.reference());
        assertEquals(List.of(new SpellIdentityReference(identity(2))), proposed.dependencies());
        assertSame(a, graph.find(identity(1)).orElseThrow());
        assertThrows(ScopeDSubmission.Rejected.class, () -> ScopeDSubmission.prepare(graph, OTHER, draft, allocation));
        assertEquals(ScopeDSubmission.Reason.STALE_GENERATION, assertThrows(ScopeDSubmission.Rejected.class,
                () -> ScopeDSubmission.prepare(graph(9, a, b), OWNER, draft, allocation)).reason());
        assertEquals(ScopeDSubmission.Reason.STALE_REVISION, assertThrows(ScopeDSubmission.Rejected.class,
                () -> ScopeDSubmission.prepare(graph(8, b), OWNER, draft, allocation)).reason());
        assertEquals(ScopeDSubmission.Reason.NON_ADVANCING_ALLOCATION, assertThrows(ScopeDSubmission.Rejected.class,
                () -> ScopeDSubmission.prepare(graph, OWNER, draft, a.reference())).reason());

        var template = manifest(1, 1, identity(1), identity(2));
        var newManifest = new ManifestDraft(template.reference().identity(), Optional.empty(),
                new CatalogGeneration(8), template.body());
        assertEquals(template, ScopeDSubmission.prepare(graph, OWNER, newManifest, template.reference()));
    }

    @Test
    void freshAllocationRebindsUnallocatedDraftSelfButPreservesExistingIdentityEdges() {
        var prior = spell(2, 4);
        var graph = graph(8, prior);
        var draft = new SpellDraft(identity(1), Optional.empty(), new CatalogGeneration(8),
                spellBody(identity(1), identity(2)));
        var allocated = new SpellReference(identity(3), new SpellRevision(9));
        var submitted = ScopeDSubmission.prepare(graph, OWNER, draft, allocated);
        assertEquals(allocated, submitted.reference());
        assertEquals(List.of(new SpellIdentityReference(identity(3)), new SpellIdentityReference(identity(2))),
                submitted.dependencies());
        assertEquals(List.of(Set.of(identity(3))), graph(9, prior, submitted).closure(allocated).cyclicComponents());
        assertTrue(graph.find(identity(1)).isEmpty());
        assertEquals(ScopeDSubmission.Reason.ALLOCATION_ALREADY_CURRENT, assertThrows(ScopeDSubmission.Rejected.class,
                () -> ScopeDSubmission.prepare(graph, OWNER, draft, prior.reference())).reason());
        var guessedAllocation = new SpellDraft(identity(1), Optional.empty(), new CatalogGeneration(8),
                spellBody(allocated.identity()));
        assertEquals(ScopeDSubmission.Reason.CONFLICTING_DRAFT_REFERENCE, assertThrows(ScopeDSubmission.Rejected.class,
                () -> ScopeDSubmission.prepare(graph, OWNER, guessedAllocation, allocated)).reason());

        var template = manifest(1, 1, identity(2));
        var manifestDraft = new ManifestDraft(template.reference().identity(), Optional.empty(),
                new CatalogGeneration(8), template.body());
        var manifestAllocation = new ManifestReference(new ManifestIdentity(OWNER, new SkillManifestId(9)),
                new ManifestRevision(9));
        var submittedManifest = ScopeDSubmission.prepare(graph, OWNER, manifestDraft, manifestAllocation);
        assertEquals(manifestAllocation, submittedManifest.reference());
        assertEquals(template.dependencies(), submittedManifest.dependencies());
    }

    @Test
    void draftRebindingRejectsOpaqueLocationWithoutDiscardingTheDraftRaw() throws IOException {
        var material = raw(identity(1), RawField.APPEARANCE);
        var appearance = new Unknown<Descriptor>(material);
        var base = spellBody();
        var body = new SpellBody(base.backend(), base.activation(), base.settings(), base.handlers(),
                appearance, base.phenomenon());
        var draft = new SpellDraft(identity(1), Optional.empty(), new CatalogGeneration(8), body);
        var graph = graph(8);
        var allocated = new SpellReference(identity(3), new SpellRevision(9));
        assertEquals(ScopeDSubmission.Reason.UNSAFE_RAW_REBIND, assertThrows(ScopeDSubmission.Rejected.class,
                () -> ScopeDSubmission.prepare(graph, OWNER, draft, allocated)).reason());
        assertEquals(material, ((Unknown<Descriptor>) draft.body().appearance()).raw());
        var sameLocation = new SpellReference(identity(1), new SpellRevision(9));
        assertEquals(appearance, ScopeDSubmission.prepare(graph, OWNER, draft, sameLocation).body().appearance());
    }

    static ScopeDGraph graph(long generation, FormalDocument... documents) {
        var routes = new LinkedHashMap<Identity, FormalDocument>();
        for (var document : documents) {
            routes.put(document.reference().identity(), document);
        }
        return ScopeDGraph.build(OWNER, new CatalogGeneration(generation), CONTEXT, routes);
    }

    static SpellIdentity identity(long id) {
        return new SpellIdentity(OWNER, new SpellDefinitionId(id));
    }

    static SpellDefinition spell(long id, long revision, SpellIdentity... targets) {
        return new SpellDefinition(new SpellReference(identity(id), new SpellRevision(revision)),
                spellBody(targets), Optional.empty());
    }

    static SpellBody spellBody(SpellIdentity... targets) {
        var blocks = new ArrayList<Block>();
        for (int index = 0; index < targets.length; index++) {
            blocks.add(new CreateSpell(new BlockId(index + 1), new SpellIdentityReference(targets[index])));
        }
        var handlers = blocks.isEmpty() ? List.<Handler>of() : List.of(new Handler(new HandlerId(1), EVENT, blocks));
        var backend = new Backend(new Descriptor(BACKEND, 1, List.of()), "gramarye:category", List.of());
        return new SpellBody(new Known<>(backend), new Absent<>(), new Known<>(List.of()),
                new Known<>(handlers), new Absent<>(), new Absent<>());
    }

    static SkillManifest manifest(long id, long revision, SpellIdentity... targets) {
        var entries = new ArrayList<ManifestEntry>();
        for (int index = 0; index < targets.length; index++) {
            entries.add(new ManifestEntry(new ManifestEntryId(index + 1), new SpellIdentityReference(targets[index]),
                    "Entry " + index, "Group"));
        }
        var identity = new ManifestIdentity(OWNER, new SkillManifestId(id));
        return new SkillManifest(new ManifestReference(identity, new ManifestRevision(revision)),
                new ManifestBody(new Known<>(entries), "Manifest", true), Optional.empty());
    }

    private static SpellReference receiverSpell(long id) {
        return new SpellReference(new SpellIdentity(OTHER, new SpellDefinitionId(id)), new SpellRevision(9));
    }

    private static RawEnvelope raw(SpellIdentity identity, RawField field) throws IOException {
        return RawEnvelope.fromBytes(new RawLocation(identity, field), "gramarye:unknown", 1,
                new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                "{\"retained\":1.00}".getBytes(StandardCharsets.UTF_8));
    }
}
