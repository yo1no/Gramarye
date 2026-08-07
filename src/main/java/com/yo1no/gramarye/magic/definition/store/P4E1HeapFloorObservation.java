package com.yo1no.gramarye.magic.definition.store;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;

/**
 * One immutable observation of the effective HotSpot {@code MaxHeapSize} coordinate.
 *
 * <p>{@link Runtime#maxMemory()} and heap {@code MemoryUsage.max} are retained only as bounded
 * diagnostics. Neither participates in the floor decision.</p>
 */
final class P4E1HeapFloorObservation {
    static final long UNAVAILABLE = -1L;
    static final String ORIGIN_UNAVAILABLE = "UNAVAILABLE";

    private final long effectiveMaxHeapSizeBytes;
    private final long runtimeMaxMemoryBytes;
    private final long heapUsageMaxBytes;
    private final List<String> collectorFamilies;
    private final String maxHeapSizeOptionOrigin;
    private final P4E1HeapFloorStatus status;
    private final String exceptionClassName;

    private P4E1HeapFloorObservation(
            long effectiveMaxHeapSizeBytes,
            long runtimeMaxMemoryBytes,
            long heapUsageMaxBytes,
            List<String> collectorFamilies,
            String maxHeapSizeOptionOrigin,
            P4E1HeapFloorStatus status,
            String exceptionClassName) {
        this.effectiveMaxHeapSizeBytes = effectiveMaxHeapSizeBytes;
        this.runtimeMaxMemoryBytes = runtimeMaxMemoryBytes;
        this.heapUsageMaxBytes = heapUsageMaxBytes;
        this.collectorFamilies = List.copyOf(collectorFamilies);
        this.maxHeapSizeOptionOrigin = Objects.requireNonNull(
                maxHeapSizeOptionOrigin, "maxHeapSizeOptionOrigin");
        this.status = Objects.requireNonNull(status, "status");
        this.exceptionClassName = Objects.requireNonNull(
                exceptionClassName, "exceptionClassName");
    }

    static P4E1HeapFloorObservation observe() {
        return observe(SystemProbe.INSTANCE);
    }

    static P4E1HeapFloorObservation observe(Probe probe) {
        Objects.requireNonNull(probe, "probe");
        final OptionValue option;
        final long effectiveMaxHeapSizeBytes;
        final P4E1HeapFloorStatus status;
        try {
            option = Objects.requireNonNull(
                    probe.maxHeapSizeOption(), "maxHeapSizeOption");
            effectiveMaxHeapSizeBytes = parseCanonicalNonNegativeLong(option.value());
            status = effectiveMaxHeapSizeBytes
                    < MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES
                            ? P4E1HeapFloorStatus.HEAP_FLOOR_NOT_MET
                            : P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT;
        } catch (RuntimeException exception) {
            return new P4E1HeapFloorObservation(
                    UNAVAILABLE,
                    UNAVAILABLE,
                    UNAVAILABLE,
                    List.of(),
                    ORIGIN_UNAVAILABLE,
                    P4E1HeapFloorStatus.HEAP_FLOOR_UNVERIFIABLE,
                    P4E1SourceFailure.boundedExceptionClassName(exception));
        }

        return new P4E1HeapFloorObservation(
                effectiveMaxHeapSizeBytes,
                diagnosticLong(probe::runtimeMaxMemoryBytes),
                diagnosticLong(probe::heapUsageMaxBytes),
                diagnosticCollectors(probe),
                diagnosticOrigin(option.origin()),
                status,
                "");
    }

    long effectiveMaxHeapSizeBytes() {
        return effectiveMaxHeapSizeBytes;
    }

    long runtimeMaxMemoryBytes() {
        return runtimeMaxMemoryBytes;
    }

    long heapUsageMaxBytes() {
        return heapUsageMaxBytes;
    }

    List<String> collectorFamilies() {
        return collectorFamilies;
    }

    String maxHeapSizeOptionOrigin() {
        return maxHeapSizeOptionOrigin;
    }

    P4E1HeapFloorStatus status() {
        return status;
    }

    String exceptionClassName() {
        return exceptionClassName;
    }

    private static long diagnosticLong(LongDiagnostic diagnostic) {
        try {
            return diagnostic.read();
        } catch (RuntimeException exception) {
            return UNAVAILABLE;
        }
    }

    private static List<String> diagnosticCollectors(Probe probe) {
        try {
            return canonicalCollectorFamilies(probe.collectorNames());
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private static String diagnosticOrigin(String origin) {
        try {
            return canonicalOrigin(origin);
        } catch (RuntimeException exception) {
            return ORIGIN_UNAVAILABLE;
        }
    }

    private static long parseCanonicalNonNegativeLong(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > 1 && value.charAt(0) == '0') {
            throw new IllegalArgumentException("non-canonical MaxHeapSize");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character < '0' || character > '9') {
                throw new IllegalArgumentException("non-canonical MaxHeapSize");
            }
        }
        return Long.parseLong(value);
    }

    private static String canonicalOrigin(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > 32) {
            throw new IllegalArgumentException("invalid MaxHeapSize option origin");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z') && character != '_') {
                throw new IllegalArgumentException("invalid MaxHeapSize option origin");
            }
        }
        return value;
    }

    private static List<String> canonicalCollectorFamilies(List<String> names) {
        Objects.requireNonNull(names, "names");
        var families = new TreeSet<String>();
        for (var name : names) {
            Objects.requireNonNull(name, "collector name");
            var normalized = name.toUpperCase(Locale.ROOT);
            if (normalized.contains("G1")) {
                families.add("G1");
            } else if (normalized.contains("PARALLEL") || normalized.startsWith("PS ")) {
                families.add("PARALLEL");
            } else if (normalized.equals("COPY")
                    || normalized.contains("MARKSWEEPCOMPACT")
                    || normalized.contains("SERIAL")) {
                families.add("SERIAL");
            } else if (normalized.contains("ZGC")
                    || normalized.startsWith("Z GARBAGE")
                    || normalized.startsWith("ZOLD")
                    || normalized.startsWith("ZYOUNG")) {
                families.add("ZGC");
            } else {
                families.add("OTHER");
            }
        }
        return List.copyOf(new ArrayList<>(families));
    }

    interface Probe {
        OptionValue maxHeapSizeOption();

        long runtimeMaxMemoryBytes();

        long heapUsageMaxBytes();

        List<String> collectorNames();
    }

    @FunctionalInterface
    private interface LongDiagnostic {
        long read();
    }

    record OptionValue(String value, String origin) {
    }

    private enum SystemProbe implements Probe {
        INSTANCE;

        @Override
        public OptionValue maxHeapSizeOption() {
            HotSpotDiagnosticMXBean diagnostic = ManagementFactory.getPlatformMXBean(
                    HotSpotDiagnosticMXBean.class);
            if (diagnostic == null) {
                throw new IllegalStateException("HotSpot diagnostic MXBean is unavailable");
            }
            VMOption option = diagnostic.getVMOption("MaxHeapSize");
            if (option == null) {
                throw new IllegalStateException("MaxHeapSize option is unavailable");
            }
            var value = option.getValue();
            String origin;
            try {
                origin = option.getOrigin().name();
            } catch (RuntimeException exception) {
                origin = ORIGIN_UNAVAILABLE;
            }
            return new OptionValue(value, origin);
        }

        @Override
        public long runtimeMaxMemoryBytes() {
            return Runtime.getRuntime().maxMemory();
        }

        @Override
        public long heapUsageMaxBytes() {
            return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        }

        @Override
        public List<String> collectorNames() {
            return ManagementFactory.getGarbageCollectorMXBeans().stream()
                    .map(bean -> bean.getName())
                    .sorted()
                    .toList();
        }
    }
}
