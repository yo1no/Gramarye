package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;

/** Test-only route through the real qualified heap preflight to a fresh audit budget. */
final class P4E1TestBudgets {
    private P4E1TestBudgets() {
    }

    static P4E1AuditBudget create() {
        var result = P4E1SourceAdmissionPreflight.evaluate(
                P4E1HeapFloorObservation.observe(new QualifiedProbe()));
        if (result instanceof P4E1SourceAdmissionPreflight.Qualified qualified) {
            return qualified.budget();
        }
        throw new AssertionError("exact-floor test observation did not qualify");
    }

    private static final class QualifiedProbe implements P4E1HeapFloorObservation.Probe {
        @Override
        public P4E1HeapFloorObservation.OptionValue maxHeapSizeOption() {
            return new P4E1HeapFloorObservation.OptionValue(
                    Long.toString(
                            MagicSafetyCeilings.MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES),
                    "TEST_INJECTION");
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
            return List.of("TEST_ONLY");
        }
    }
}
