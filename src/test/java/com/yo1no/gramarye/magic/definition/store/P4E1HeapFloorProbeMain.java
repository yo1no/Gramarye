package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Fresh-JVM test probe for the effective MaxHeapSize coordinate. */
public final class P4E1HeapFloorProbeMain {
    private static final int RESULT_SCHEMA_VERSION = 0;
    private static final int MAX_RESULT_BYTES = 4_096;

    private P4E1HeapFloorProbeMain() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "heap probe requires a requested-heap label and result path");
        }
        var requestedHeapLabel = canonical(arguments[0], "requested heap label");
        var resultPath = Path.of(arguments[1]);
        if (Files.exists(resultPath)) {
            throw new IllegalStateException("heap probe result path already exists");
        }

        P4E1SourceAdmissionPreflight.Result preflight =
                P4E1SourceAdmissionPreflight.evaluate();
        var observation = preflight.observation();
        var sourceWorkCalls = observedBudgetedSourceWork(preflight);
        if (preflight instanceof P4E1SourceAdmissionPreflight.Qualified qualified) {
            if (qualified.budget().observed(P4E1AuditCounter.DIRECTORY_ENTRIES) != 0L) {
                throw new IllegalStateException("fresh source-admission budget is not empty");
            }
        }
        var result = new StringBuilder()
                .append("schema_version=").append(RESULT_SCHEMA_VERSION).append('\n')
                .append("collector=")
                .append(canonicalOrUnavailable(
                        String.join(",", observation.collectorFamilies()), "collector"))
                .append('\n')
                .append("requested_heap_label=").append(requestedHeapLabel).append('\n')
                .append("effective_max_heap_size_bytes=")
                .append(observation.effectiveMaxHeapSizeBytes())
                .append('\n')
                .append("runtime_max_memory_bytes=")
                .append(observation.runtimeMaxMemoryBytes())
                .append('\n')
                .append("classification=").append(observation.status().name()).append('\n')
                .append("exception_class=")
                .append(observation.exceptionClassName().isEmpty()
                        ? "NONE"
                        : canonical(observation.exceptionClassName(), "exception class"))
                .append('\n')
                .append("source_work_calls=").append(sourceWorkCalls).append('\n')
                .append("java_feature_version=").append(Runtime.version().feature()).append('\n')
                .append("java_runtime_version=")
                .append(canonical(
                        System.getProperty("java.runtime.version"), "Java runtime version"))
                .append('\n')
                .append("vm_name=")
                .append(canonical(System.getProperty("java.vm.name"), "VM name"))
                .append('\n')
                .append("vm_vendor=")
                .append(canonical(System.getProperty("java.vm.vendor"), "VM vendor"))
                .append('\n')
                .append("vm_version=")
                .append(canonical(System.getProperty("java.vm.version"), "VM version"))
                .append('\n')
                .append("heap_memory_usage_max_bytes=")
                .append(observation.heapUsageMaxBytes())
                .append('\n')
                .append("max_heap_size_option_origin=")
                .append(canonical(
                        observation.maxHeapSizeOptionOrigin(), "MaxHeapSize option origin"))
                .append('\n')
                .append("floor_bytes=")
                .append(MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES)
                .append('\n')
                .toString();
        var bytes = result.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RESULT_BYTES) {
            throw new IllegalStateException("heap probe result exceeds its byte bound");
        }
        Files.write(
                resultPath,
                bytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private static String canonical(String value, String fieldName) {
        if (value == null || value.isEmpty() || value.length() > 160) {
            throw new IllegalStateException("non-canonical " + fieldName);
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character == '\n' || character == '\r' || character == '=') {
                throw new IllegalStateException("non-canonical " + fieldName);
            }
        }
        return value;
    }

    private static String canonicalOrUnavailable(String value, String fieldName) {
        return value.isEmpty() ? "UNAVAILABLE" : canonical(value, fieldName);
    }

    static long observedBudgetedSourceWork(
            P4E1SourceAdmissionPreflight.Result preflight) {
        if (!(preflight instanceof P4E1SourceAdmissionPreflight.Qualified qualified)) {
            return 0L;
        }
        var observed = 0L;
        for (var counter : P4E1AuditCounter.values()) {
            observed = Math.addExact(observed, qualified.budget().observed(counter));
        }
        return observed;
    }
}
