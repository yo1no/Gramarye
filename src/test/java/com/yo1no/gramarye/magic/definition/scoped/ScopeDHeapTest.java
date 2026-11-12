package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDCatalogTest.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static org.junit.jupiter.api.Assertions.*;

final class ScopeDHeapTest {
    @TempDir Path root;

    @Test
    void fixedHeapChildMeasuresGraphAndEncodedCatalogWork() throws Exception {
        Path output = root.resolve("heap.log");
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms32m", "-Xmx32m", "-XX:+ExitOnOutOfMemoryError", "-cp", System.getProperty("java.class.path"),
                Child.class.getName(), root.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(60, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            fail("Scope D fixed-heap child timed out");
        }
        String log = Files.readString(output);
        assertEquals(0, child.exitValue(), log);
        assertTrue(log.contains("routes=96 edges=384"), log);
        assertTrue(log.contains("storage routes=64 retained=96"), log);
        System.out.print(log);
    }

    public static final class Child {
        public static void main(String[] arguments) throws Exception {
            Path root = Path.of(arguments[0]);
            for (String mode : List.of("cold", "warm")) {
                long start = System.nanoTime();
                graph();
                System.out.printf("%s routes=%d edges=%d elapsedNanos=%d maxHeap=%d%n", mode,
                        MAX_ROUTES, MAX_GRAPH_EDGES, System.nanoTime() - start, Runtime.getRuntime().maxMemory());
            }
            long start = System.nanoTime();
            Path directory = Files.createDirectory(root.resolve("physical"));
            try (var storage = ScopeDStorage.open(directory, OWNER)) {
                var catalog = new ScopeDCatalog(OWNER, storage);
                var ids = new ArrayList<SpellIdentity>();
                for (int batch = 0; batch < 2; batch++) {
                    var allocation = catalog.reserve();
                    var changes = new ArrayList<FormalDocument>();
                    for (int index = 1; index <= MAX_BATCH; index++) {
                        var reference = allocation.newSpell(index);
                        ids.add(reference.identity());
                        changes.add(large(reference, 'a'));
                    }
                    completeInIsolation(catalog, storage, catalog.prepare(allocation, CONTEXT, changes));
                }
                var allocation = catalog.reserve();
                var changes = new ArrayList<FormalDocument>();
                for (SpellIdentity identity : ids.subList(0, MAX_BATCH)) {
                    changes.add(large(allocation.nextSpell(identity), 'b'));
                }
                var prepared = catalog.prepare(allocation, CONTEXT, changes);
                catalog.stage(prepared, CONTEXT);
                long old = Files.size(directory.resolve("catalog.current"));
                long staged = Files.size(directory.resolve("catalog.prepare"));
                assertTrue(old < staged && staged <= MAX_CATALOG_BYTES);
                completeInIsolation(catalog, storage, prepared);
                var current = catalog.current();
                assertEquals(2 * MAX_BATCH, current.routes().size());
                assertEquals(3 * MAX_BATCH, current.revisions().size());
                System.out.printf("storage routes=%d retained=%d oldBytes=%d stagedBytes=%d elapsedNanos=%d%n",
                        current.routes().size(), current.revisions().size(), old, staged, System.nanoTime() - start);
            }
            try (var storage = ScopeDStorage.open(directory, OWNER)) {
                assertEquals(3 * MAX_BATCH, new ScopeDCatalog(OWNER, storage).current().revisions().size());
            }
        }

        private static void graph() {
            var documents = new HashMap<Identity, FormalDocument>();
            for (int index = 0; index < MAX_ROUTES; index++) {
                var identity = identity(index);
                var blocks = new ArrayList<Block>();
                for (int edge = 1; edge <= 4; edge++) {
                    blocks.add(new CreateSpell(new BlockId(edge), new SpellIdentityReference(identity(index + edge))));
                }
                documents.put(identity, spell(new SpellReference(identity, new SpellRevision(1)),
                        List.of(new Handler(new HandlerId(1), "gramarye:test_event", blocks))));
            }
            var base = ScopeDGraph.build(OWNER, new CatalogGeneration(1), CONTEXT.descriptors(), documents);
            var next = ScopeDGraph.build(OWNER, new CatalogGeneration(2), CONTEXT.descriptors(), documents);
            assertEquals(MAX_ROUTES, ScopeDGraph.validateCandidate(base, next, true).revalidated().size());
            var closure = next.closure(documents.get(identity(0)).reference());
            assertEquals(MAX_ROUTES, closure.documents().size());
            assertEquals(1, closure.cyclicComponents().size());
        }

        private static SpellIdentity identity(int index) {
            return new SpellIdentity(OWNER, new SpellDefinitionId(index % MAX_ROUTES + 1));
        }

        private static SpellDefinition large(SpellReference reference, char fill) throws Exception {
            var original = spell(reference, List.of());
            var body = original.body();
            var raw = RawEnvelope.fromBytes(new RawLocation(reference.identity(), RawField.APPEARANCE),
                    "gramarye:visual_test", 1, new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                    ("\"" + String.valueOf(fill).repeat(MAX_RAW_SLOT_BYTES - 2) + "\"")
                            .getBytes(StandardCharsets.UTF_8));
            return new SpellDefinition(reference, new SpellBody(body.backend(), body.activation(), body.settings(),
                    body.handlers(), new Unknown<>(raw), body.phenomenon()), Optional.empty());
        }
    }
}
