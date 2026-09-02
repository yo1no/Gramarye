package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EffectTraceTest {
    @Test
    void acceptsStableContiguousZeroBasedOrder() {
        EffectTrace trace = EffectTestFixtures.trace(
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.STEP_APPLIED,
                EffectTraceStage.TERMINAL_SUCCEEDED);
        assertEquals(List.of(0, 1, 2, 3), trace.entries().stream()
                .map(EffectTraceEntry::sequence)
                .toList());
        assertEquals(0, trace.entries().get(2).stepIndex());
        assertEquals(
                EffectTraceEntry.NOT_APPLICABLE_STEP_INDEX,
                trace.entries().getFirst().stepIndex());
    }

    @Test
    void acceptsMaximumThirtyTwoAndRejectsThirtyThree() {
        List<EffectTraceEntry> maximum = new ArrayList<>();
        for (int sequence = 0; sequence < P6EffectBounds.MAX_TRACE_ENTRIES; sequence++) {
            maximum.add(EffectTraceEntry.withoutStep(
                    sequence, EffectTraceStage.REQUEST_VALIDATED));
        }
        assertEquals(32, new EffectTrace(maximum).entries().size());
        maximum.add(EffectTraceEntry.withoutStep(32, EffectTraceStage.TERMINAL_REJECTED));
        assertThrows(IllegalArgumentException.class, () -> new EffectTrace(maximum));
    }

    @Test
    void rejectsSequenceGapAndDuplicate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectTrace(List.of(
                        EffectTraceEntry.withoutStep(0, EffectTraceStage.REQUEST_VALIDATED),
                        EffectTraceEntry.withoutStep(2, EffectTraceStage.TERMINAL_REJECTED))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectTrace(List.of(
                        EffectTraceEntry.withoutStep(0, EffectTraceStage.REQUEST_VALIDATED),
                        EffectTraceEntry.withoutStep(0, EffectTraceStage.TERMINAL_REJECTED))));
    }

    @Test
    void rejectsNullListEntryAndInvalidStepAbsence() {
        assertThrows(NullPointerException.class, () -> new EffectTrace(null));
        assertThrows(
                NullPointerException.class,
                () -> new EffectTrace(java.util.Arrays.asList(
                        EffectTraceEntry.withoutStep(
                                0, EffectTraceStage.REQUEST_VALIDATED),
                        null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectTraceEntry(
                        0,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceEntry.NOT_APPLICABLE_STEP_INDEX));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectTraceEntry(
                        0, EffectTraceStage.TERMINAL_SUCCEEDED, 0));
    }

    @Test
    void entryCoordinatesUseExactBoundsAndSingleAbsenceSentinel() {
        assertEquals(
                P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN - 1,
                EffectTraceEntry.forStep(
                                0,
                                EffectTraceStage.STEP_APPLIED,
                                P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN - 1)
                        .stepIndex());
        assertThrows(
                IllegalArgumentException.class,
                () -> EffectTraceEntry.forStep(
                        0,
                        EffectTraceStage.STEP_APPLIED,
                        P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectTraceEntry(
                        -1,
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceEntry.NOT_APPLICABLE_STEP_INDEX));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectTraceEntry(
                        0,
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceEntry.NOT_APPLICABLE_STEP_INDEX - 1));
        assertThrows(
                NullPointerException.class,
                () -> new EffectTraceEntry(
                        0, null, EffectTraceEntry.NOT_APPLICABLE_STEP_INDEX));
    }

    @Test
    void entrySchemaIsExactBoundedScalarVocabulary() {
        assertArrayEquals(
                new Class<?>[] {int.class, EffectTraceStage.class, int.class},
                java.util.Arrays.stream(EffectTraceEntry.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getType)
                        .toArray(Class<?>[]::new));
    }

    @Test
    void traceDefensivelyCopiesAndReturnsImmutableEntries() {
        List<EffectTraceEntry> original = new ArrayList<>();
        original.add(EffectTraceEntry.withoutStep(
                0, EffectTraceStage.TERMINAL_REJECTED));
        EffectTrace trace = new EffectTrace(original);
        original.clear();
        assertEquals(1, trace.entries().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> trace.entries().add(EffectTraceEntry.withoutStep(
                        1, EffectTraceStage.TERMINAL_REJECTED)));
    }

    @Test
    void traceEntryHasNoFreeFormThrowableOrLiveObjectField() {
        for (Field field : EffectTraceEntry.class.getDeclaredFields()) {
            String name = field.getType().getName();
            if (name.equals(String.class.getName())
                    || Throwable.class.isAssignableFrom(field.getType())
                    || name.startsWith("net.minecraft.")
                    || name.startsWith("net.neoforged.")) {
                throw new AssertionError(name);
            }
        }
    }
}
