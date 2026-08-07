package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class P4E1SourceAdmissionPreflightTest {
    private static final long FLOOR =
            MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES;

    @Test
    void qualifiedObservationCreatesTheOnlyCapabilityBeforeSourceAdmission() {
        var sourceWork = new SourceWorkLedger();
        var observation = observe(Long.toString(FLOOR));

        var result = evaluateThenAttemptSourceAdmission(observation, sourceWork);

        var qualified = assertInstanceOf(
                P4E1SourceAdmissionPreflight.Qualified.class, result);
        assertSame(observation, qualified.observation());
        for (var counter : P4E1AuditCounter.values()) {
            assertEquals(0L, qualified.budget().observed(counter), counter.name());
        }
        sourceWork.assertAll(1);
    }

    @Test
    void belowFloorShortCircuitsBeforeBudgetOrAnySourceWork() {
        assertIncomplete(
                observe(Long.toString(FLOOR - 1L)),
                P4E1SourceFailure.Code.HEAP_FLOOR_NOT_MET,
                "");
    }

    @Test
    void unverifiableShortCircuitsBeforeBudgetAndPreservesBoundedClass() {
        assertIncomplete(
                observe("not-a-number"),
                P4E1SourceFailure.Code.HEAP_FLOOR_UNVERIFIABLE,
                IllegalArgumentException.class.getName());
    }

    @Test
    void childDiagnosticReadsTheQualifiedBudgetAndIncompleteHasNoCapability() {
        var qualified = P4E1SourceAdmissionPreflight.evaluate(
                observe(Long.toString(FLOOR)));
        var qualifiedResult = assertInstanceOf(
                P4E1SourceAdmissionPreflight.Qualified.class, qualified);
        assertEquals(0L, P4E1HeapFloorProbeMain.observedBudgetedSourceWork(qualified));
        assertEquals(
                java.util.Optional.empty(),
                qualifiedResult.budget().checkpointSingle(
                        P4E1AuditCounter.DIRECTORY_ENTRIES,
                        P4E1AuditStage.DIRECTORY_ENTRIES,
                        1L));
        assertEquals(1L, P4E1HeapFloorProbeMain.observedBudgetedSourceWork(qualified));

        var incomplete = P4E1SourceAdmissionPreflight.evaluate(
                observe(Long.toString(FLOOR - 1L)));
        assertInstanceOf(P4E1SourceAdmissionPreflight.Incomplete.class, incomplete);
        assertEquals(0L, P4E1HeapFloorProbeMain.observedBudgetedSourceWork(incomplete));
    }

    private static void assertIncomplete(
            P4E1HeapFloorObservation observation,
            P4E1SourceFailure.Code expectedCode,
            String expectedExceptionClass) {
        var sourceWork = new SourceWorkLedger();
        var result = evaluateThenAttemptSourceAdmission(observation, sourceWork);

        var incomplete = assertInstanceOf(
                P4E1SourceAdmissionPreflight.Incomplete.class, result);
        assertSame(observation, incomplete.observation());
        assertEquals(expectedCode, incomplete.failure().code());
        assertEquals(P4E1AuditStage.HEAP_FLOOR_OBSERVATION,
                incomplete.failure().stage());
        assertEquals(expectedExceptionClass, incomplete.failure().exceptionClassName());
        sourceWork.assertAll(0);
    }

    private static P4E1SourceAdmissionPreflight.Result evaluateThenAttemptSourceAdmission(
            P4E1HeapFloorObservation observation,
            SourceWorkLedger sourceWork) {
        var result = P4E1SourceAdmissionPreflight.evaluate(observation);
        if (result instanceof P4E1SourceAdmissionPreflight.Qualified qualified) {
            sourceWork.recordAll(qualified.budget());
        }
        return result;
    }

    private static P4E1HeapFloorObservation observe(String value) {
        return P4E1HeapFloorObservation.observe(new P4E1HeapFloorObservation.Probe() {
            @Override
            public P4E1HeapFloorObservation.OptionValue maxHeapSizeOption() {
                return new P4E1HeapFloorObservation.OptionValue(value, "VM_CREATION");
            }

            @Override
            public long runtimeMaxMemoryBytes() {
                return 1L;
            }

            @Override
            public long heapUsageMaxBytes() {
                return 2L;
            }

            @Override
            public List<String> collectorNames() {
                return List.of("G1 Young Generation");
            }
        });
    }

    private enum SourceWorkKind {
        DIRECTORY_ENUMERATION,
        FILE_ATTRIBUTE_READS,
        FILE_OPENS,
        GZIP_PARSER_CALLS,
        NBT_SCANNER_CALLS,
        INTEGRATED_TRAVERSAL_CALLS,
        P4C_ADMISSION_CALLS,
        ONLINE_ATTACHMENT_READS,
        JOURNAL_CALLS,
        ROOT_CLAIMS,
        STORE_AUDIT_CALLS,
        RECLAIM_CALLS
    }

    private static final class SourceWorkLedger {
        private final EnumMap<SourceWorkKind, AtomicInteger> calls =
                new EnumMap<>(SourceWorkKind.class);

        private SourceWorkLedger() {
            for (var kind : SourceWorkKind.values()) {
                calls.put(kind, new AtomicInteger());
            }
        }

        private void recordAll(P4E1AuditBudget budget) {
            java.util.Objects.requireNonNull(budget, "qualified audit budget");
            for (var counter : calls.values()) {
                counter.incrementAndGet();
            }
        }

        private void assertAll(int expected) {
            for (var entry : calls.entrySet()) {
                assertEquals(expected, entry.getValue().get(), entry.getKey().name());
            }
        }
    }
}
