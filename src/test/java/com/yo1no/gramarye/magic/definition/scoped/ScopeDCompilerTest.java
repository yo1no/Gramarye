package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerDiagnostics.Code;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerFixtures.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDProgram.*;
import static org.junit.jupiter.api.Assertions.*;

final class ScopeDCompilerTest {
    @TempDir Path root;

    @Test
    void standaloneAndManifestShareSelectedProgramWithIndependentOrigin() throws Exception {
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var reservation = catalog.reserve();
            var selected = reservation.newSpell(1);
            var left = reservation.newSpell(2);
            var right = reservation.newSpell(3);
            var diamond = reservation.newSpell(4);
            var unused = reservation.newSpell(5);
            var manifestRef = reservation.newManifest(6);
            var settings = List.of(new Setting("flag", new Equal(new Literal(new IntegerValue(3)),
                    new Literal(new IntegerValue(3)))), new Setting("value", new Select(ValueType.INTEGER,
                    new Literal(new BooleanValue(true)), new CreationInput("seed", ValueType.INTEGER),
                    new Literal(new IntegerValue(99)))));
            var prepared = catalog.prepare(reservation, DEFAULT.catalogContext(), List.of(
                    spell(selected, settings, List.of(handler(42, List.of(effect(19), create(4, left.identity()),
                            create(5, right.identity())))), new Known<>(descriptor(ACTIVATE)), new Known<>(descriptor(APPEAR))),
                    spell(left, List.of(handler(1, List.of(create(1, diamond.identity()))))),
                    spell(right, List.of(handler(1, List.of(create(1, diamond.identity()))))),
                    spell(diamond, List.of()), spell(unused, List.of(handler(2, List.of(effect(18))))),
                    manifest(manifestRef, List.of(unused, selected))));
            assertFalse(storage.hasPendingMaterial());
            var proof = catalog.verifyCompatibility(prepared, DEFAULT).proof().orElseThrow();
            assertFalse(storage.hasPendingMaterial());
            var published = catalog.publish(prepared, DEFAULT, proof);
            var standalone = compiled(catalog, new ScopeDResolver.StandaloneSpellRoot(selected));
            var entry = compiled(catalog, new ScopeDResolver.ManifestEntryRoot(manifestRef, new ManifestEntryId(2)));
            assertEquals(standalone.program(), entry.program());
            assertEquals(List.of(selected, left, right, diamond),
                    entry.program().key().closure().stream().map(ContentBinding::reference).toList());
            var provenance = entry.manifest().orElseThrow();
            assertEquals(manifestRef, provenance.manifest());
            assertEquals(List.of(unused, selected), provenance.entries().stream().map(ScopeDResolver.EntryBinding::spell).toList());
            assertTrue(provenance.wholeClosure().contains(unused));
            assertTrue(standalone.manifest().isEmpty());
            var first = standalone.program().spells().getFirst();
            assertEquals(List.of(new BooleanValue(true)), first.constants());
            assertEquals(List.of(new Constant(0), new ReadInput("seed", ValueType.INTEGER)), first.instructions().subList(0, 2));
            assertEquals(2, first.cost().folded());
            assertEquals(List.of(new Fact("seed", ValueType.INTEGER)), first.activation().orElseThrow().inputRequirements());
            assertEquals(VisualState.KNOWN, first.appearance().state());
            assertInstanceOf(Effect.class, first.instructions().get(2));
            assertEquals(left, assertInstanceOf(CreateChild.class, first.instructions().get(3)).exactTarget());
            assertEquals(right, assertInstanceOf(CreateChild.class, first.instructions().get(4)).exactTarget());
            assertEquals(List.of(new BlockId(19), new BlockId(4), new BlockId(5)), first.sourceMap().stream()
                    .filter(range -> range.address().domain() == SourceDomain.BLOCK)
                    .map(range -> range.address().block().orElseThrow()).toList());
            assertThrows(UnsupportedOperationException.class, () -> first.instructions().clear());
            assertEquals(standalone, compiled(catalog, new ScopeDResolver.StandaloneSpellRoot(selected)));
            assertEquals(published.generation(), standalone.snapshot().generation());

            var next = catalog.reserve();
            var updatedDiamond = next.nextSpell(diamond.identity());
            publish(catalog, catalog.prepare(next, DEFAULT.catalogContext(),
                    List.of(spell(updatedDiamond, List.of(handler(2, List.of(effect(2))))))));
            var after = compiled(catalog, new ScopeDResolver.StandaloneSpellRoot(selected));
            assertNotEquals(standalone.program().key(), after.program().key());
            assertEquals(diamond, standalone.program().spells().getLast().reference());
            assertEquals(updatedDiamond, after.program().spells().getLast().reference());
            assertCode(catalog.compile(new ScopeDResolver.StandaloneSpellRoot(diamond), published.generation(), DEFAULT),
                    Code.STALE_GENERATION);
            assertCode(catalog.compile(new ScopeDResolver.StandaloneSpellRoot(diamond), catalog.current().generation(), DEFAULT),
                    Code.ROOT_UNAVAILABLE);
        }
    }

    @Test
    void invalidUnselectedEntryDeniesManifestAndFullCandidateButPreservesStandaloneProjection() throws Exception {
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var reservation = catalog.reserve();
            var good = reservation.newSpell(1);
            var bad = reservation.newSpell(2);
            var manifest = reservation.newManifest(3);
            var malformedEffect = new EffectBlock(new BlockId(1), descriptor(EFFECT));
            var prepared = catalog.prepare(reservation, DEFAULT.catalogContext(), List.of(spell(good, List.of()),
                    spell(bad, List.of(handler(1, List.of(malformedEffect)))), manifest(manifest, List.of(good, bad))));
            var report = catalog.verifyCompatibility(prepared, DEFAULT);
            assertTrue(report.proof().isEmpty());
            assertTrue(report.diagnostics().entries().stream().anyMatch(value -> value.code() == Code.ARGUMENT));
            assertFalse(storage.hasPendingMaterial());
            assertEquals(0, catalog.current().generation().value());
            // Inspect the immutable rejected candidate as pure compiler input without publishing it.
            var image = ScopeDCatalogImage.decode(OWNER, prepared.binding().candidate().value(), prepared.bytes());
            assertInstanceOf(ScopeDCompiler.Compiled.class, ScopeDCompiler.compile(storage.sourceId(), image,
                    new ScopeDResolver.StandaloneSpellRoot(good), image.generation(), DEFAULT));
            assertCode(ScopeDCompiler.compile(storage.sourceId(), image,
                    new ScopeDResolver.ManifestEntryRoot(manifest, new ManifestEntryId(1)), image.generation(), DEFAULT),
                    Code.ARGUMENT);
        }
    }

    @Test
    void effectEmissionCanCloseACausalCycleEvenThroughNormallyExternalEvent() throws Exception {
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var initial = catalog.reserve();
            var first = initial.newSpell(1);
            var second = initial.newSpell(2);
            publish(catalog, catalog.prepare(initial, DEFAULT.catalogContext(), List.of(
                    spell(first, List.of(handler(1, List.of(create(1, second.identity()))))),
                    spell(second, List.of(handler(1, CREATED, List.of(create(1, first.identity()))))))));
            assertEquals(1, compiled(catalog, new ScopeDResolver.StandaloneSpellRoot(first)).program().cost().cyclicComponents());
            var changed = catalog.reserve();
            var prepared = catalog.prepare(changed, DEFAULT.catalogContext(), List.of(spell(changed.nextSpell(first.identity()),
                    List.of(handler(1, List.of(create(1, second.identity()))),
                            handler(2, CREATED, List.of(new EffectBlock(new BlockId(2), descriptor(EMIT))))))));
            var result = catalog.verifyCompatibility(prepared, DEFAULT);
            assertTrue(result.proof().isEmpty());
            assertTrue(result.diagnostics().entries().stream().anyMatch(value -> value.code() == Code.RUNTIME_POLICY_REQUIRED));
            assertFalse(storage.hasPendingMaterial());
            assertEquals(1, catalog.current().generation().value());
        }
    }

    @Test
    void mandatoryCreationInputCannotReadAnOptionalMissingValueEvenInFoldedBranch() throws Exception {
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var reservation = catalog.reserve();
            var expression = new Select(ValueType.INTEGER, new Literal(new BooleanValue(true)),
                    new Literal(new IntegerValue(4)), new CreationInput("optional", ValueType.INTEGER));
            var prepared = catalog.prepare(reservation, DEFAULT.catalogContext(), List.of(spell(reservation.newSpell(1),
                    List.of(new Setting("value", expression)), List.of(), new Absent<>(), new Absent<>())));
            var invalid = catalog.verifyCompatibility(prepared, DEFAULT);
            assertTrue(invalid.proof().isEmpty());
            assertTrue(invalid.diagnostics().entries().stream().anyMatch(issue -> issue.code() == Code.CREATION_INPUT));
        }
    }

    @Test
    void boundedDiagnosticsKeepFatalWhenWarningsFillTheReport() throws Exception {
        var routes = new HashMap<Identity, ExactReference>();
        var revisions = new HashMap<ExactReference, FormalDocument>();
        var local = new HashMap<Identity, ScopeDCatalogImage.LocalHighWater>();
        for (int i = 1; i <= MAX_COMPILER_DIAGNOSTICS + 1; i++) {
            var reference = new SpellReference(new SpellIdentity(OWNER, new SpellDefinitionId(i)), new SpellRevision(1));
            boolean last = i > MAX_COMPILER_DIAGNOSTICS;
            var value = spell(reference, List.of(), last ? List.of(handler(1, List.of(
                    new EffectBlock(new BlockId(1), descriptor(EFFECT))))) : List.of(), new Absent<>(),
                    new Known<>(new Descriptor("gramarye:unknown_visual", 1, List.of())));
            routes.put(reference.identity(), reference);
            revisions.put(reference, value);
            local.put(reference.identity(), new ScopeDCatalogImage.LocalHighWater(last ? 1 : 0, last ? 1 : 0, 0));
        }
        var image = new ScopeDCatalogImage(OWNER, new CatalogGeneration(1), routes, revisions, local, java.util.Set.of());
        var report = ScopeDCompiler.verifyCandidate(new UUID(0, 5), image, DEFAULT);
        assertEquals(MAX_COMPILER_DIAGNOSTICS, report.entries().size());
        assertTrue(report.entries().stream().noneMatch(ScopeDCompilerDiagnostics.Entry::fatal));
        assertEquals(1, report.fatals());
        assertEquals(2, report.omitted());
        assertFalse(report.valid());
        assertEquals(report, ScopeDCompiler.verifyCandidate(new UUID(0, 5), image, DEFAULT));
    }

    @Test
    void pendingReopenRequiresNewProofAndExactTrustedContext() throws Exception {
        ScopeDCatalog.CompatibilityProof oldProof;
        ScopeDCatalog.ProofBinding binding;
        var changedContext = context(ScopeDFrontend.EventOrigin.EXTERNAL, 2);
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var reservation = catalog.reserve();
            var prepared = catalog.prepare(reservation, DEFAULT.catalogContext(), List.of(spell(reservation.newSpell(1), List.of())));
            binding = prepared.binding();
            oldProof = catalog.verifyCompatibility(prepared, DEFAULT).proof().orElseThrow();
            assertEquals(Code.PROOF_BINDING, assertThrows(ScopeDCatalog.CompatibilityRejected.class,
                    () -> catalog.publish(prepared, changedContext, oldProof)).code());
            assertEquals(Code.STALE_CONTEXT, assertThrows(ScopeDCatalog.CompatibilityRejected.class,
                    () -> catalog.verifyCompatibility(prepared, changedContext)).code());
            assertFalse(storage.hasPendingMaterial());
            catalog.stage(prepared, DEFAULT.catalogContext());
        }
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            assertThrows(IOException.class, () -> catalog.revalidatePending(changedContext.catalogContext()));
            var recovered = catalog.revalidatePending(DEFAULT.catalogContext()).orElseThrow();
            assertEquals(binding, recovered.binding());
            assertThrows(IOException.class, () -> catalog.publish(recovered, DEFAULT, oldProof));
            assertEquals(0, catalog.current().generation().value());
            publish(catalog, recovered);
            assertFalse(storage.hasPendingMaterial());
            assertEquals(1, catalog.current().generation().value());
            assertEquals(Code.STALE_GENERATION, assertThrows(ScopeDCatalog.CompatibilityRejected.class,
                    () -> catalog.verifyCompatibility(recovered, DEFAULT)).code());
        }
    }

    @Test
    void laterReservationAndForeignPhysicalSourceCannotUseOlderProof() throws Exception {
        try (var storage = ScopeDStorage.open(root, OWNER);
             var other = ScopeDStorage.open(java.nio.file.Files.createDirectory(root.resolve("other")), OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var differentSource = new ScopeDCatalog(OWNER, other);
            var first = catalog.reserve();
            var old = catalog.prepare(first, DEFAULT.catalogContext(), List.of(spell(first.newSpell(1), List.of())));
            var proof = catalog.verifyCompatibility(old, DEFAULT).proof().orElseThrow();
            assertThrows(IOException.class, () -> differentSource.verifyCompatibility(old, DEFAULT));
            assertThrows(IOException.class, () -> differentSource.publish(old, DEFAULT, proof));
            assertFalse(other.hasPendingMaterial());
            var latest = catalog.reserve();
            var replacement = catalog.prepare(latest, DEFAULT.catalogContext(), List.of(spell(latest.newSpell(1), List.of())));
            assertThrows(IOException.class, () -> catalog.verifyCompatibility(old, DEFAULT));
            assertThrows(IOException.class, () -> catalog.publish(old, DEFAULT, proof));
            assertThrows(IOException.class, () -> catalog.publish(replacement, DEFAULT, proof));
            assertFalse(storage.hasPendingMaterial());
            publish(catalog, replacement);
            assertEquals(1, catalog.current().generation().value());
        }
    }

    @Test
    void proofSuccessfulButPhysicalForceFaultFencesUntilSameCommitRecovery() throws Exception {
        ScopeDCatalog.ProofBinding binding;
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var reservation = catalog.reserve();
            var prepared = catalog.prepare(reservation, DEFAULT.catalogContext(), List.of(spell(reservation.newSpell(1), List.of())));
            binding = prepared.binding();
            var proof = catalog.verifyCompatibility(prepared, DEFAULT).proof().orElseThrow();
            catalog.stage(prepared, DEFAULT.catalogContext());
            // Disable only the physical force operation. The same M1 writer still installs the exact staged frame.
            var field = ScopeDStorage.class.getDeclaredField("directoryChannel");
            field.setAccessible(true);
            ((Channel) field.get(storage)).close();
            assertThrows(IOException.class, () -> catalog.publish(prepared, DEFAULT, proof));
            assertThrows(IOException.class, catalog::current);
        }
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            assertEquals(binding.candidate(), catalog.current().generation());
            assertFalse(storage.hasPendingMaterial());
            assertEquals(binding.transaction().value(), storage.current().transaction());
        }
    }

    @Test
    void expressionAndOrderedBlockMaximaArePreservedAndNextInputRejected() throws Exception {
        var expressions = new ArrayList<Setting>();
        Expression equal = new CreationInput("seed", ValueType.INTEGER);
        Expression deep = new Equal(equal, equal);
        for (int i = 0; i < MAX_EXPRESSION_DEPTH - 2; i++) {
            deep = new Equal(deep, new Literal(new BooleanValue(false)));
        }
        expressions.add(new Setting("flag", deep));
        var blocks = new ArrayList<Block>();
        for (int i = 1; i <= MAX_BLOCKS; i++) {
            blocks.add(effect(i));
        }
        try (var storage = ScopeDStorage.open(root, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var reservation = catalog.reserve();
            var ref = reservation.newSpell(1);
            var value = spell(ref, expressions, List.of(handler(1, blocks)), new Absent<>(), new Absent<>());
            publish(catalog, catalog.prepare(reservation, DEFAULT.catalogContext(), List.of(value)));
            var program = compiled(catalog, new ScopeDResolver.StandaloneSpellRoot(ref)).program();
            assertTrue(program.spells().getFirst().activation().isEmpty());
            assertEquals(MAX_BLOCKS, program.cost().units().blocks());
            assertEquals(MAX_BLOCKS + 3, program.cost().units().sourceEntries());
            var beyond = new Equal(deep, new Literal(new BooleanValue(false)));
            assertThrows(IllegalArgumentException.class, () -> spell(ref, List.of(new Setting("flag", beyond)),
                    List.of(), new Absent<>(), new Absent<>()));
            blocks.add(effect(MAX_BLOCKS + 1));
            assertThrows(IllegalArgumentException.class, () -> handler(1, blocks));
        }
    }

    private static ScopeDCompiler.Compiled compiled(ScopeDCatalog catalog, ScopeDResolver.Root root) throws IOException {
        var result = catalog.compile(root, catalog.current().generation(), DEFAULT);
        return assertInstanceOf(ScopeDCompiler.Compiled.class, result, result::toString);
    }

    private static void assertCode(ScopeDCompiler.Result result, Code code) {
        var rejected = assertInstanceOf(ScopeDCompiler.Rejected.class, result, result::toString);
        assertTrue(rejected.diagnostics().entries().stream().anyMatch(entry -> entry.code() == code), result::toString);
    }

    private static void publish(ScopeDCatalog catalog, ScopeDCatalog.Prepared prepared) throws IOException {
        var result = catalog.verifyCompatibility(prepared, DEFAULT);
        assertTrue(result.diagnostics().valid(), result.diagnostics()::toString);
        catalog.publish(prepared, DEFAULT, result.proof().orElseThrow());
    }
}
