package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static org.junit.jupiter.api.Assertions.*;

import com.sun.management.ThreadMXBean;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ScopeDCompilerHeapTest {
    @TempDir Path root;

    @Test
    void isolatedFixedHeapRunsColdBuildRebuildAndPhysicalCandidateProof() throws Exception {
        Path output = root.resolve("compiler-heap.log");
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms48m", "-Xmx48m", "-XX:+UseG1GC", "-XX:+ExitOnOutOfMemoryError",
                "-cp", System.getProperty("java.class.path"),
                Child.class.getName(), root.toString()).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            fail("Scope D compiler fixed-heap child timed out");
        }
        String log = Files.readString(output);
        System.out.print(log);
        assertEquals(0, child.exitValue(), log);
        assertTrue(log.contains("dense-scc routes=96 edges=384"), log);
        assertTrue(log.contains("many-small routes=96 manifestEntries=24"), log);
        assertTrue(log.contains("few-large spells=6 expressions=576 inputs=144"), log);
        assertTrue(log.contains("coexist routes=32 retained=36"), log);
        assertTrue(log.contains("diagnostic-flood retained=64 omitted=32 fatals=96"), log);
    }

    public static final class Child {
        private static final SkillOwnerId OWNER = new SkillOwnerId(new UUID(0, 101));
        private static final UUID SOURCE = new UUID(0, 102);
        private static final CatalogGeneration GENERATION = new CatalogGeneration(11);
        private static final String CATEGORY = "gramarye:heap_category";
        private static final DescriptorKey BACKEND = new DescriptorKey(DescriptorDomain.BACKEND, "gramarye:heap_backend", 1);
        private static final DescriptorKey EVENT = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:heap_event", 1);
        private static final DescriptorKey EFFECT = new DescriptorKey(DescriptorDomain.EFFECT, "gramarye:heap_effect", 1);

        public static void main(String[] arguments) throws Exception {
            var context = context(EventOrigin.EXTERNAL);
            var allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            assertTrue(allocation.isThreadAllocatedMemorySupported());
            allocation.setThreadAllocatedMemoryEnabled(true);
            assertEquals(48L * 1024 * 1024, Runtime.getRuntime().maxMemory());
            denseScc(context, allocation);
            manySmall(context, allocation);
            physicalCandidate(context, allocation, Files.createDirectory(Path.of(arguments[0]).resolve("physical")));
            diagnosticFlood(context);
        }

        private static void denseScc(Context context, ThreadMXBean allocation) throws Exception {
            var documents = new ArrayList<FormalDocument>();
            for (int index = 1; index <= MAX_ROUTES; index++) {
                var blocks = new ArrayList<Block>();
                for (int edge = 1; edge <= MAX_GRAPH_EDGES / MAX_ROUTES; edge++) {
                    var target = reference((index + edge - 1) % MAX_ROUTES + 1);
                    blocks.add(new CreateSpell(new BlockId(edge), new SpellIdentityReference(target.identity())));
                }
                documents.add(spell(reference(index), List.of(), blocks, new Absent<>()));
            }
            var cold = measure(allocation, "cold-dense-scc", () -> compile(image(documents), reference(1), context));
            var rebuild = measure(allocation, "fresh-dense-scc", () -> compile(image(documents), reference(1), context));
            assertEquals(cold.program(), rebuild.program());
            assertEquals(1, cold.program().cost().cyclicComponents());
            assertEquals(MAX_ROUTES, cold.program().cost().spells());
            assertEquals(MAX_GRAPH_EDGES, cold.program().cost().edges());
            var rejected = assertInstanceOf(ScopeDCompiler.Rejected.class, ScopeDCompiler.compile(SOURCE, image(documents),
                    new ScopeDResolver.StandaloneSpellRoot(reference(1)), GENERATION, context(EventOrigin.CREATION)));
            assertTrue(rejected.diagnostics().entries().stream().anyMatch(entry -> entry.code()
                    == ScopeDCompilerDiagnostics.Code.RUNTIME_POLICY_REQUIRED));
            System.out.printf("dense-scc routes=%d edges=%d resolverSteps=%d feedbackSteps=%d rejectedCausalFatals=%d%n",
                    cold.program().cost().spells(), cold.program().cost().edges(), cold.resolverWork().steps(),
                    cold.program().cost().feedbackWork(), rejected.diagnostics().fatals());
        }

        private static void manySmall(Context context, ThreadMXBean allocation) throws Exception {
            var documents = new ArrayList<FormalDocument>();
            for (int index = 1; index < MAX_ROUTES; index++) {
                var targets = switch (index) {
                    case 1 -> List.of(reference(90), reference(91));
                    case 90, 91 -> List.of(reference(92));
                    default -> List.<SpellReference>of();
                };
                documents.add(spell(reference(index), List.of(), createBlocks(targets), new Absent<>()));
            }
            var manifest = new ManifestReference(new ManifestIdentity(OWNER, new SkillManifestId(1)), new ManifestRevision(1));
            var entries = new ArrayList<ManifestEntry>();
            for (int index = 1; index <= MAX_MANIFEST_ENTRIES; index++) {
                entries.add(new ManifestEntry(new ManifestEntryId(index), new SpellIdentityReference(reference(index).identity()),
                        "entry" + index, "heap"));
            }
            documents.add(new SkillManifest(manifest, new ManifestBody(new Known<>(entries), "heap manifest", false),
                    Optional.empty()));
            var image = image(documents);
            var verified = measure(allocation, "many-small-all-roots", () -> ScopeDCompiler.verifyCandidate(SOURCE, image, context));
            assertEquals(0, verified.fatals(), verified.toString());
            var selected = measure(allocation, "max-manifest-shared-diamond", () -> assertInstanceOf(ScopeDCompiler.Compiled.class,
                    ScopeDCompiler.compile(SOURCE, image, new ScopeDResolver.ManifestEntryRoot(manifest, new ManifestEntryId(1)),
                            GENERATION, context)));
            var standalone = compile(image, reference(1), context);
            assertEquals(standalone.program(), selected.program());
            assertEquals(4, selected.program().cost().spells());
            assertEquals(4, selected.program().cost().edges());
            var origin = selected.manifest().orElseThrow();
            assertEquals(MAX_MANIFEST_ENTRIES + 3, origin.wholeClosure().size());
            System.out.printf("many-small routes=%d manifestEntries=%d manifestClosure=%d selectedClosure=%d%n",
                    image.routes().size(), origin.entries().size(), origin.wholeClosure().size(), selected.program().cost().spells());
        }

        private static void physicalCandidate(Context context, ThreadMXBean allocation, Path directory) throws Exception {
            try (var storage = ScopeDStorage.open(directory, OWNER)) {
                var catalog = new ScopeDCatalog(OWNER, storage);
                var initial = catalog.reserve();
                var references = new ArrayList<SpellReference>();
                for (int ordinal = 1; ordinal <= MAX_BATCH; ordinal++) {
                    references.add(initial.newSpell(ordinal));
                }
                var original = new ArrayList<FormalDocument>();
                for (int index = 0; index < references.size(); index++) {
                    var targets = index == 0 ? references.subList(1, 6) : List.<SpellReference>of();
                    original.add(large(references.get(index), targets, 'a'));
                }
                var prepared = catalog.prepare(initial, context.catalogContext(), original);
                var proof = measure(allocation, "initial-physical-proof",
                        () -> catalog.verifyCompatibility(prepared, context).proof().orElseThrow());
                var oldImage = catalog.publish(prepared, context, proof);
                var root = new ScopeDResolver.StandaloneSpellRoot(references.getFirst());
                var oldProgram = measure(allocation, "few-large-cold", () -> assertInstanceOf(ScopeDCompiler.Compiled.class,
                        catalog.compile(root, oldImage.generation(), context))).program();
                var fresh = measure(allocation, "few-large-rebuild", () -> assertInstanceOf(ScopeDCompiler.Compiled.class,
                        catalog.compile(root, oldImage.generation(), context))).program();
                assertEquals(oldProgram, fresh);
                var cost = oldProgram.cost();
                assertEquals(6, cost.spells());
                assertEquals(6 * MAX_EXPRESSIONS, cost.units().expressions());
                assertEquals(6 * MAX_FRONTEND_FIELDS, cost.units().creationInputs());
                assertEquals(6 * MAX_BLOCKS, cost.units().blocks());
                assertEquals(0, cost.units().folded());
                System.out.printf("few-large spells=%d expressions=%d inputs=%d blocks=%d instructions=%d sourceEntries=%d retainedBoundBytes=%d%n",
                        cost.spells(), cost.units().expressions(), cost.units().creationInputs(), cost.units().blocks(),
                        cost.units().instructions(), cost.units().sourceEntries(), cost.units().retainedBytes());

                var update = catalog.reserve();
                var changed = new ArrayList<FormalDocument>();
                for (SpellReference reference : references.subList(1, 5)) {
                    changed.add(large(update.nextSpell(reference.identity()), List.of(), 'b'));
                }
                var next = catalog.prepare(update, context.catalogContext(), changed);
                measure(allocation, "candidate-old-and-prepared-image-proof",
                        () -> catalog.verifyCompatibility(next, context).proof().orElseThrow());
                assertFalse(Files.exists(directory.resolve("catalog.prepare")));
                catalog.stage(next, context.catalogContext());
                long oldBytes = Files.size(directory.resolve("catalog.current"));
                long stagedBytes = Files.size(directory.resolve("catalog.prepare"));
                assertEquals(oldImage.generation(), catalog.current().generation());
                assertTrue(oldBytes > MAX_CATALOG_BYTES / 2 && stagedBytes > oldBytes && stagedBytes <= MAX_CATALOG_BYTES);
                var recovered = catalog.revalidatePending(context.catalogContext()).orElseThrow();
                var pendingProof = measure(allocation, "coexistent-pending-image-reproof",
                        () -> catalog.verifyCompatibility(recovered, context).proof().orElseThrow());
                var newImage = catalog.publish(recovered, context, pendingProof);
                var newProgram = assertInstanceOf(ScopeDCompiler.Compiled.class,
                        catalog.compile(root, newImage.generation(), context)).program();
                assertNotEquals(oldProgram.key(), newProgram.key());
                assertEquals(references.get(1), oldProgram.key().closure().get(1).reference());
                assertEquals(changed.getFirst().reference(), newProgram.key().closure().get(1).reference());
                assertEquals(MAX_BATCH, newImage.routes().size());
                assertEquals(MAX_BATCH + changed.size(), newImage.revisions().size());
                System.out.printf("coexist routes=%d retained=%d oldBytes=%d stagedBytes=%d newBytes=%d%n",
                        newImage.routes().size(), newImage.revisions().size(), oldBytes, stagedBytes,
                        Files.size(directory.resolve("catalog.current")));
            }
        }

        private static void diagnosticFlood(Context context) throws Exception {
            var blocks = new ArrayList<Block>();
            for (int index = 1; index <= MAX_BLOCKS; index++) {
                blocks.add(new EffectBlock(new BlockId(index), new Descriptor(EFFECT.tag(), EFFECT.version(), List.of())));
            }
            var reference = reference(1);
            var failed = assertInstanceOf(ScopeDCompiler.Rejected.class, ScopeDCompiler.compile(SOURCE,
                    image(List.of(spell(reference, List.of(), blocks, new Absent<>()))),
                    new ScopeDResolver.StandaloneSpellRoot(reference), GENERATION, context));
            var report = failed.diagnostics();
            assertEquals(MAX_COMPILER_DIAGNOSTICS, report.entries().size());
            assertEquals(MAX_BLOCKS - MAX_COMPILER_DIAGNOSTICS, report.omitted());
            assertEquals(MAX_BLOCKS, report.fatals());
            System.out.printf("diagnostic-flood retained=%d omitted=%d fatals=%d%n",
                    report.entries().size(), report.omitted(), report.fatals());
        }

        private static Context context(EventOrigin origin) {
            var settings = new LinkedHashMap<String, Field>();
            var inputs = new LinkedHashMap<String, Field>();
            for (int index = 0; index < MAX_FRONTEND_FIELDS; index++) {
                settings.put("setting" + index, Field.optional(ValueType.BOOLEAN));
                inputs.put("input" + index, Field.defaulted(new BooleanValue(false)));
            }
            var backend = new BackendContract(BACKEND, CATEGORY, Schema.EMPTY, Set.of(), Set.of(), Set.of(),
                    new Schema(settings), new Schema(inputs), Set.of(EVENT), 1);
            var event = new EventContract(EVENT, Set.of(CATEGORY), Set.of(), Map.of(), origin,
                    Optional.of(new CreateContract(Set.of(), Map.of(), 1)), 1);
            var effect = new EffectContract(EFFECT, new Schema(Map.of("value", Field.required(ValueType.TEXT))),
                    Set.of(CATEGORY), Set.of(EVENT), Set.of(), Map.of(), Map.of(), Set.of(),
                    EffectOperation.DESCRIPTOR_EFFECT, 1);
            var snapshot = new Snapshot(List.of(backend, event, effect));
            return new Context(snapshot, snapshot.fullPolicy());
        }

        private static SpellDefinition large(SpellReference reference, List<SpellReference> targets, char fill) throws Exception {
            var settings = new ArrayList<Setting>();
            for (int index = 0; index < MAX_SETTINGS; index++) {
                var expression = new Select(ValueType.BOOLEAN, new CreationInput("input" + index, ValueType.BOOLEAN),
                        new Literal(new BooleanValue(true)), new Literal(new BooleanValue(false)));
                settings.add(new Setting("setting" + index, expression));
            }
            var blocks = createBlocks(targets);
            var argument = new Argument("value", new TextValue(String.valueOf(fill).repeat(MAX_TEXT_BYTES)));
            var effect = new Descriptor(EFFECT.tag(), EFFECT.version(), List.of(argument));
            while (blocks.size() < MAX_BLOCKS) {
                blocks.add(new EffectBlock(new BlockId(blocks.size() + 1), effect));
            }
            var visual = RawEnvelope.fromBytes(new RawLocation(reference.identity(), RawField.APPEARANCE),
                    "gramarye:heap_visual", 1, new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                    ("\"" + String.valueOf(fill).repeat(MAX_RAW_SLOT_BYTES - 2) + "\"").getBytes(StandardCharsets.UTF_8));
            return spell(reference, settings, blocks, new Unknown<>(visual));
        }

        private static ArrayList<Block> createBlocks(List<SpellReference> targets) {
            var blocks = new ArrayList<Block>();
            for (var target : targets) {
                blocks.add(new CreateSpell(new BlockId(blocks.size() + 1), new SpellIdentityReference(target.identity())));
            }
            return blocks;
        }

        private static SpellDefinition spell(SpellReference reference, List<Setting> settings, List<Block> blocks,
                                             Slot<Descriptor> appearance) {
            var backend = new Backend(new Descriptor(BACKEND.tag(), BACKEND.version(), List.of()), CATEGORY, List.of());
            var handlers = blocks.isEmpty() ? List.<Handler>of()
                    : List.of(new Handler(new HandlerId(1), EVENT.tag(), EVENT.version(), blocks));
            return new SpellDefinition(reference, new SpellBody(new Known<>(backend), new Absent<>(), new Known<>(settings),
                    new Known<>(handlers), appearance, new Absent<>()), Optional.empty());
        }

        private static SpellReference reference(long id) {
            return new SpellReference(new SpellIdentity(OWNER, new SpellDefinitionId(id)), new SpellRevision(1));
        }

        private static ScopeDCatalogImage image(List<? extends FormalDocument> documents) {
            var routes = new LinkedHashMap<Identity, ExactReference>();
            var revisions = new LinkedHashMap<ExactReference, FormalDocument>();
            var watermarks = new LinkedHashMap<Identity, ScopeDCatalogImage.LocalHighWater>();
            for (var document : documents) {
                var reference = document.reference();
                routes.put(reference.identity(), reference);
                revisions.put(reference, document);
                watermarks.put(reference.identity(), ScopeDCatalogImage.LocalHighWater.EMPTY);
            }
            return new ScopeDCatalogImage(OWNER, GENERATION, routes, revisions, watermarks, Set.of());
        }

        private static ScopeDCompiler.Compiled compile(ScopeDCatalogImage image, SpellReference root, Context context)
                throws Exception {
            return assertInstanceOf(ScopeDCompiler.Compiled.class, ScopeDCompiler.compile(SOURCE, image,
                    new ScopeDResolver.StandaloneSpellRoot(root), GENERATION, context));
        }

        private static <T> T measure(ThreadMXBean allocation, String name, Measured<T> operation) throws Exception {
            long start = System.nanoTime();
            long allocated = allocation.getCurrentThreadAllocatedBytes();
            T result = operation.run();
            long bytes = allocation.getCurrentThreadAllocatedBytes() - allocated;
            assertTrue(bytes > 0, name);
            System.out.printf("%s elapsedNanos=%d threadAllocatedBytes=%d usedHeapAfter=%d maxHeap=%d%n", name,
                    System.nanoTime() - start, bytes, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
                    Runtime.getRuntime().maxMemory());
            return result;
        }

        private interface Measured<T> {
            T run() throws Exception;
        }
    }
}
