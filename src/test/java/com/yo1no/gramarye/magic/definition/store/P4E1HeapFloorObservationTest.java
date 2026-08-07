package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class P4E1HeapFloorObservationTest {
    private static final long FLOOR =
            MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES;

    @Test
    void effectiveMaxHeapSizeAloneControlsFloorMinusOneExactAndPlusOne() {
        assertEquals(
                P4E1HeapFloorStatus.HEAP_FLOOR_NOT_MET,
                observe(FLOOR - 1L, FLOOR + 10L).status());
        assertEquals(
                P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT,
                observe(FLOOR, FLOOR - 10L).status());
        assertEquals(
                P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT,
                observe(FLOOR + 1L, 1L).status());
    }

    @Test
    void successfulObservationQueriesEachCoordinateExactlyOnceAndCanonicalizesCollectors() {
        var probe = new CountingProbe(
                Long.toString(FLOOR),
                "VM_CREATION",
                FLOOR - 1L,
                FLOOR - 2L,
                List.of("G1 Old Generation", "G1 Young Generation"));

        var observation = P4E1HeapFloorObservation.observe(probe);

        assertEquals(P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT, observation.status());
        assertEquals(FLOOR, observation.effectiveMaxHeapSizeBytes());
        assertEquals(FLOOR - 1L, observation.runtimeMaxMemoryBytes());
        assertEquals(FLOOR - 2L, observation.heapUsageMaxBytes());
        assertEquals(List.of("G1"), observation.collectorFamilies());
        assertEquals("VM_CREATION", observation.maxHeapSizeOptionOrigin());
        assertEquals(1, probe.optionCalls.get());
        assertEquals(1, probe.runtimeCalls.get());
        assertEquals(1, probe.heapCalls.get());
        assertEquals(1, probe.collectorCalls.get());
    }

    @Test
    void malformedNegativeOverflowAndUnavailableCoordinatesAreUnverifiable() {
        for (var value : List.of("", "-1", "+1", "01", " 1", "1 ", "1a",
                "9223372036854775808")) {
            var observation = P4E1HeapFloorObservation.observe(new CountingProbe(
                    value, "VM_CREATION", FLOOR, FLOOR, List.of("G1 Young Generation")));
            assertEquals(
                    P4E1HeapFloorStatus.HEAP_FLOOR_UNVERIFIABLE,
                    observation.status(),
                    value);
        }

        var unavailable = new CountingProbe(
                Long.toString(FLOOR), "VM_CREATION", FLOOR, FLOOR, List.of("Copy"));
        unavailable.optionFailure = new IllegalStateException("test-only unavailable");
        var observation = P4E1HeapFloorObservation.observe(unavailable);
        assertEquals(P4E1HeapFloorStatus.HEAP_FLOOR_UNVERIFIABLE, observation.status());
        assertEquals(P4E1HeapFloorObservation.UNAVAILABLE,
                observation.effectiveMaxHeapSizeBytes());
        assertEquals(IllegalStateException.class.getName(),
                observation.exceptionClassName());
        assertEquals(0, unavailable.runtimeCalls.get());
        assertEquals(0, unavailable.heapCalls.get());
        assertEquals(0, unavailable.collectorCalls.get());
    }

    @Test
    void zeroAndLongMaximumAreCanonicalAuthorityValues() {
        assertEquals(
                P4E1HeapFloorStatus.HEAP_FLOOR_NOT_MET,
                observe(0L, Long.MAX_VALUE).status());
        assertEquals(
                P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT,
                observe(Long.MAX_VALUE, 0L).status());
    }

    @Test
    void nullOptionAndNullValueAreUnverifiableWithBoundedClassName() {
        var nullOption = new CountingProbe(
                Long.toString(FLOOR), "VM_CREATION", FLOOR, FLOOR, List.of("G1"));
        nullOption.returnNullOption = true;
        var absent = P4E1HeapFloorObservation.observe(nullOption);
        assertEquals(P4E1HeapFloorStatus.HEAP_FLOOR_UNVERIFIABLE, absent.status());
        assertEquals(NullPointerException.class.getName(), absent.exceptionClassName());

        var nullValue = P4E1HeapFloorObservation.observe(new CountingProbe(
                null, "VM_CREATION", FLOOR, FLOOR, List.of("G1")));
        assertEquals(P4E1HeapFloorStatus.HEAP_FLOOR_UNVERIFIABLE, nullValue.status());
        assertEquals(NullPointerException.class.getName(), nullValue.exceptionClassName());
    }

    @Test
    void diagnosticFailuresNeverChangeQualifiedAuthorityVerdict() {
        var probe = new CountingProbe(
                Long.toString(FLOOR), null, FLOOR, FLOOR, List.of("G1"));
        probe.runtimeFailure = new IllegalStateException("diagnostic only");
        probe.heapFailure = new IllegalArgumentException("diagnostic only");
        probe.collectorFailure = new IllegalStateException("diagnostic only");

        var observation = P4E1HeapFloorObservation.observe(probe);

        assertEquals(P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT, observation.status());
        assertEquals(FLOOR, observation.effectiveMaxHeapSizeBytes());
        assertEquals(P4E1HeapFloorObservation.UNAVAILABLE,
                observation.runtimeMaxMemoryBytes());
        assertEquals(P4E1HeapFloorObservation.UNAVAILABLE,
                observation.heapUsageMaxBytes());
        assertEquals(List.of(), observation.collectorFamilies());
        assertEquals(P4E1HeapFloorObservation.ORIGIN_UNAVAILABLE,
                observation.maxHeapSizeOptionOrigin());
        assertEquals("", observation.exceptionClassName());
    }

    @Test
    void errorEscapesWithoutBeingConvertedToUnverifiable() {
        var expected = new AssertionError("test-only Error");
        var probe = new CountingProbe(
                Long.toString(FLOOR), "VM_CREATION", FLOOR, FLOOR, List.of("G1"));
        probe.optionError = expected;
        assertSame(expected, assertThrows(AssertionError.class,
                () -> P4E1HeapFloorObservation.observe(probe)));
    }

    @Test
    void collectorFamiliesAreBoundedAndDoNotAffectFloorClassification() {
        var observation = P4E1HeapFloorObservation.observe(new CountingProbe(
                Long.toString(FLOOR),
                "ERGONOMIC",
                1L,
                2L,
                List.of(
                        "PS MarkSweep",
                        "PS Scavenge",
                        "Copy",
                        "MarkSweepCompact",
                        "ZGC Cycles",
                        "unknown test collector")));
        assertEquals(P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT, observation.status());
        assertEquals(List.of("OTHER", "PARALLEL", "SERIAL", "ZGC"),
                observation.collectorFamilies());
    }

    private static P4E1HeapFloorObservation observe(long configured, long runtime) {
        return P4E1HeapFloorObservation.observe(new CountingProbe(
                Long.toString(configured),
                "VM_CREATION",
                runtime,
                runtime,
                List.of("G1 Young Generation", "G1 Old Generation")));
    }

    private static final class CountingProbe implements P4E1HeapFloorObservation.Probe {
        private final String value;
        private final String origin;
        private final long runtime;
        private final long heap;
        private final List<String> collectors;
        private final AtomicInteger optionCalls = new AtomicInteger();
        private final AtomicInteger runtimeCalls = new AtomicInteger();
        private final AtomicInteger heapCalls = new AtomicInteger();
        private final AtomicInteger collectorCalls = new AtomicInteger();
        private RuntimeException optionFailure;
        private Error optionError;
        private RuntimeException runtimeFailure;
        private RuntimeException heapFailure;
        private RuntimeException collectorFailure;
        private boolean returnNullOption;

        private CountingProbe(
                String value,
                String origin,
                long runtime,
                long heap,
                List<String> collectors) {
            this.value = value;
            this.origin = origin;
            this.runtime = runtime;
            this.heap = heap;
            this.collectors = collectors;
        }

        @Override
        public P4E1HeapFloorObservation.OptionValue maxHeapSizeOption() {
            optionCalls.incrementAndGet();
            if (optionError != null) {
                throw optionError;
            }
            if (optionFailure != null) {
                throw optionFailure;
            }
            if (returnNullOption) {
                return null;
            }
            return new P4E1HeapFloorObservation.OptionValue(value, origin);
        }

        @Override
        public long runtimeMaxMemoryBytes() {
            runtimeCalls.incrementAndGet();
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            return runtime;
        }

        @Override
        public long heapUsageMaxBytes() {
            heapCalls.incrementAndGet();
            if (heapFailure != null) {
                throw heapFailure;
            }
            return heap;
        }

        @Override
        public List<String> collectorNames() {
            collectorCalls.incrementAndGet();
            if (collectorFailure != null) {
                throw collectorFailure;
            }
            return collectors;
        }
    }
}
