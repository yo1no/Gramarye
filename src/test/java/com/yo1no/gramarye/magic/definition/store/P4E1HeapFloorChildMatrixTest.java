package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P4E1HeapFloorChildMatrixTest {
    private static final String ALIGNED_TO_FLOOR_CONTROL =
            "ALIGNED_TO_FLOOR_CONTROL";
    private static final String BELOW_FLOOR_CONTROL = "BELOW_FLOOR_CONTROL";
    private static final long FLOOR =
            MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES;
    private static final Duration CHILD_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_RESULT_BYTES = 4_096;
    private static final Set<String> RESULT_FIELDS = Set.of(
            "schema_version",
            "collector",
            "requested_heap_label",
            "effective_max_heap_size_bytes",
            "runtime_max_memory_bytes",
            "classification",
            "exception_class",
            "source_work_calls",
            "java_feature_version",
            "java_runtime_version",
            "vm_name",
            "vm_vendor",
            "vm_version",
            "heap_memory_usage_max_bytes",
            "max_heap_size_option_origin",
            "floor_bytes");

    @TempDir
    Path temporaryDirectory;

    private int resultSequence;

    @Test
    void lockedJava21CollectorMatrixUsesEffectiveMaxHeapSize() throws Exception {
        var g1 = run("QUALIFICATION", "1536m", "-XX:+UseG1GC");
        var parallel = run("QUALIFICATION", "1536m", "-XX:+UseParallelGC");
        var serial = run("QUALIFICATION", "1536m", "-XX:+UseSerialGC");
        var zgc = run("QUALIFICATION", "1536m", "-XX:+UseZGC");
        var aligned1535 = run(
                ALIGNED_TO_FLOOR_CONTROL, "1535m", "-XX:+UseG1GC");
        var below = run(BELOW_FLOOR_CONTROL, "1024m", "-XX:+UseG1GC");

        assertQualified(g1, "G1", "1536m");
        assertQualified(parallel, "PARALLEL", "1536m");
        assertQualified(serial, "SERIAL", "1536m");
        assertQualified(zgc, "ZGC", "1536m");
        assertEquals(ALIGNED_TO_FLOOR_CONTROL, aligned1535.controlRole());
        assertEquals("1535m", aligned1535.value("requested_heap_label"));
        assertEquals(FLOOR, aligned1535.longValue("effective_max_heap_size_bytes"));
        assertEquals(
                P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT.name(),
                aligned1535.value("classification"));
        assertEquals(0L, aligned1535.longValue("source_work_calls"));
        assertEquals(BELOW_FLOOR_CONTROL, below.controlRole());
        assertEquals("1024m", below.value("requested_heap_label"));
        assertEquals(
                1_073_741_824L,
                below.longValue("effective_max_heap_size_bytes"));
        assertTrue(below.longValue("effective_max_heap_size_bytes") < FLOOR);
        assertEquals(
                P4E1HeapFloorStatus.HEAP_FLOOR_NOT_MET.name(),
                below.value("classification"));
        assertEquals(0L, below.longValue("source_work_calls"));

        // Runtime.maxMemory is diagnostic only. These collectors report a lower value while the
        // effective HotSpot MaxHeapSize authority still qualifies at the exact floor.
        assertTrue(parallel.longValue("runtime_max_memory_bytes")
                < parallel.longValue("effective_max_heap_size_bytes"));
        assertTrue(serial.longValue("runtime_max_memory_bytes")
                < serial.longValue("effective_max_heap_size_bytes"));
    }

    private static void assertQualified(
            ChildResult result, String collector, String requestedHeapLabel) {
        assertEquals(0L, result.longValue("schema_version"));
        assertEquals(21L, result.longValue("java_feature_version"));
        assertEquals(requestedHeapLabel, result.value("requested_heap_label"));
        assertEquals(FLOOR, result.longValue("effective_max_heap_size_bytes"));
        assertEquals(FLOOR, result.longValue("floor_bytes"));
        assertEquals("VM_CREATION", result.value("max_heap_size_option_origin"));
        assertEquals(collector, result.value("collector"));
        assertEquals(
                P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT.name(),
                result.value("classification"));
        assertEquals("NONE", result.value("exception_class"));
        assertEquals(0L, result.longValue("source_work_calls"));
    }

    private ChildResult run(
            String controlRole, String maximumHeap, String collectorFlag) throws Exception {
        var executable = Path.of(
                System.getProperty("java.home"), "bin", operatingSystemJavaName());
        assertTrue(Files.isRegularFile(executable), executable.toString());
        var resultPath = temporaryDirectory.resolve("heap-result-" + resultSequence++ + ".txt");
        var process = new ProcessBuilder(
                executable.toString(),
                "-Xms512m",
                "-Xmx" + maximumHeap,
                "-XX:+ExitOnOutOfMemoryError",
                collectorFlag,
                "-cp",
                System.getProperty("java.class.path"),
                P4E1HeapFloorProbeMain.class.getName(),
                maximumHeap,
                resultPath.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(CHILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            throw new AssertionError("heap-coordinate child timed out: " + collectorFlag);
        }
        if (process.exitValue() != 0) {
            throw new AssertionError(
                    "heap-coordinate child exited " + process.exitValue() + ": " + collectorFlag);
        }
        return ChildResult.parse(controlRole, readBounded(resultPath, collectorFlag));
    }

    private static String readBounded(Path resultPath, String collectorFlag) throws IOException {
        var attributes = Files.readAttributes(
                resultPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new AssertionError(
                    "heap-coordinate child result is not a regular file: " + collectorFlag);
        }
        if (attributes.size() > MAX_RESULT_BYTES) {
            throw new AssertionError(
                    "heap-coordinate child result exceeded bound: " + collectorFlag);
        }
        try (var input = Files.newInputStream(resultPath)) {
            var bytes = input.readNBytes(MAX_RESULT_BYTES + 1);
            if (bytes.length > MAX_RESULT_BYTES || input.read() != -1) {
                throw new AssertionError(
                        "heap-coordinate child result exceeded bound: " + collectorFlag);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String operatingSystemJavaName() {
        return System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    }

    private record ChildResult(String controlRole, Map<String, String> values) {
        private ChildResult {
            if (controlRole.isBlank()) {
                throw new IllegalArgumentException("controlRole must be bounded");
            }
            values = Map.copyOf(values);
        }

        static ChildResult parse(String controlRole, String result) {
            var values = new HashMap<String, String>();
            for (var line : result.lines().toList()) {
                var separator = line.indexOf('=');
                if (separator <= 0 || separator == line.length() - 1) {
                    throw new AssertionError("malformed heap-coordinate field");
                }
                var previous = values.put(
                        line.substring(0, separator), line.substring(separator + 1));
                if (previous != null) {
                    throw new AssertionError("duplicate heap-coordinate field");
                }
            }
            assertEquals(RESULT_FIELDS, values.keySet());
            return new ChildResult(controlRole, values);
        }

        String value(String name) {
            var value = values.get(name);
            if (value == null) {
                throw new AssertionError("missing heap-coordinate field: " + name);
            }
            return value;
        }

        long longValue(String name) {
            return Long.parseLong(value(name));
        }
    }
}
