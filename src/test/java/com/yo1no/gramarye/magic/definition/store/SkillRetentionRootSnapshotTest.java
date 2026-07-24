package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SkillRetentionRootSnapshotTest {
    private static final int MAX = MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM;
    private static final SkillReference ROOT = new SkillReference(
            StoreTestFixtures.skillId(1), StoreTestFixtures.revision(0));

    @Test
    void sealedVocabularyAndSingletonStatesAreExact() {
        assertEquals(
                java.util.Set.of(
                        SkillRetentionRootSnapshot.Complete.class,
                        SkillRetentionRootSnapshot.Incomplete.class,
                        SkillRetentionRootSnapshot.Truncated.class,
                        SkillRetentionRootSnapshot.OverLimit.class),
                java.util.Set.of(SkillRetentionRootSnapshot.class.getPermittedSubclasses()));
        assertArrayEquals(
                new SkillRetentionRootSnapshot.Incomplete[] {
                    SkillRetentionRootSnapshot.Incomplete.INSTANCE
                },
                SkillRetentionRootSnapshot.Incomplete.values());
        assertArrayEquals(
                new SkillRetentionRootSnapshot.Truncated[] {
                    SkillRetentionRootSnapshot.Truncated.INSTANCE
                },
                SkillRetentionRootSnapshot.Truncated.values());
    }

    @Test
    void factoryRejectsNullInputsAndElements() {
        assertThrows(
                NullPointerException.class,
                () -> SkillRetentionRootSnapshot.fromCompleteRoots(null));
        assertThrows(
                NullPointerException.class,
                () -> SkillRetentionRootSnapshot.fromCompleteRoots(
                        java.util.Arrays.asList(ROOT, null)));
    }

    @Test
    void emptyAndExactMaximumInputsProduceImmutableCompleteSnapshots() {
        var empty = assertInstanceOf(
                SkillRetentionRootSnapshot.Complete.class,
                SkillRetentionRootSnapshot.fromCompleteRoots(List.of()));
        var exactInput = new CountingIterable(MAX, ROOT);
        var exact = assertInstanceOf(
                SkillRetentionRootSnapshot.Complete.class,
                SkillRetentionRootSnapshot.fromCompleteRoots(exactInput));

        assertEquals(List.of(), empty.roots());
        assertEquals(MAX, exact.roots().size());
        assertEquals(MAX, exactInput.nextCalls());
        assertThrows(UnsupportedOperationException.class, () -> exact.roots().add(ROOT));
    }

    @Test
    void firstElementBeyondMaximumStopsCaptureAndRetainsNoPrefix() {
        var input = new CountingIterable(Integer.MAX_VALUE, ROOT);
        var overLimit = assertInstanceOf(
                SkillRetentionRootSnapshot.OverLimit.class,
                SkillRetentionRootSnapshot.fromCompleteRoots(input));

        assertEquals(MAX + 1, overLimit.observedAtLeast());
        assertEquals(MAX, overLimit.maximum());
        assertEquals(MAX + 1, input.nextCalls());
        assertTrue(java.util.Arrays.stream(overLimit.getClass().getRecordComponents())
                .noneMatch(component -> Iterable.class.isAssignableFrom(component.getType())
                        || List.class.isAssignableFrom(component.getType())));
    }

    @Test
    void duplicatesRetainOrderAndCountTowardTheRawBound() {
        var other = new SkillReference(
                StoreTestFixtures.skillId(2), StoreTestFixtures.revision(3));
        var complete = assertInstanceOf(
                SkillRetentionRootSnapshot.Complete.class,
                SkillRetentionRootSnapshot.fromCompleteRoots(List.of(ROOT, other, ROOT)));

        assertEquals(List.of(ROOT, other, ROOT), complete.roots());

        var duplicateFlood = new CountingIterable(MAX + 1, ROOT);
        assertInstanceOf(
                SkillRetentionRootSnapshot.OverLimit.class,
                SkillRetentionRootSnapshot.fromCompleteRoots(duplicateFlood));
        assertEquals(MAX + 1, duplicateFlood.nextCalls());
    }

    @Test
    void completeSnapshotIsDetachedFromItsMutableSourceCollection() {
        var source = new ArrayList<>(List.of(ROOT));
        var complete = assertInstanceOf(
                SkillRetentionRootSnapshot.Complete.class,
                SkillRetentionRootSnapshot.fromCompleteRoots(source));

        source.clear();

        assertEquals(List.of(ROOT), complete.roots());
    }

    @Test
    void completeHasPrivateConstructionIdentityEqualityAndBoundedToString() {
        var constructors = SkillRetentionRootSnapshot.Complete.class.getDeclaredConstructors();
        var first = (SkillRetentionRootSnapshot.Complete)
                SkillRetentionRootSnapshot.fromCompleteRoots(List.of(ROOT));
        var second = (SkillRetentionRootSnapshot.Complete)
                SkillRetentionRootSnapshot.fromCompleteRoots(List.of(ROOT));

        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertNotEquals(first, second);
        assertEquals("Complete[rootCount=1]", first.toString());
        assertTrue(first.toString().length() < 64);
    }

    @Test
    void overLimitRequiresCanonicalStrictlyExceededMetadata() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillRetentionRootSnapshot.OverLimit(MAX, MAX));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillRetentionRootSnapshot.OverLimit(-1, MAX));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillRetentionRootSnapshot.OverLimit(MAX + 1, MAX - 1));
        assertEquals(
                new SkillRetentionRootSnapshot.OverLimit(MAX + 1, MAX),
                new SkillRetentionRootSnapshot.OverLimit(MAX + 1, MAX));
    }

    @Test
    void iteratorRuntimeExceptionPropagatesWithoutProducingASnapshot() {
        var failure = new IllegalStateException("test-only iterator failure");
        Iterable<SkillReference> roots = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                throw failure;
            }

            @Override
            public SkillReference next() {
                throw new NoSuchElementException();
            }
        };

        org.junit.jupiter.api.Assertions.assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> SkillRetentionRootSnapshot.fromCompleteRoots(roots)));
    }

    private static final class CountingIterable implements Iterable<SkillReference> {
        private final int available;
        private final SkillReference value;
        private final AtomicInteger nextCalls = new AtomicInteger();

        private CountingIterable(int available, SkillReference value) {
            this.available = available;
            this.value = value;
        }

        @Override
        public Iterator<SkillReference> iterator() {
            return new Iterator<>() {
                private int position;

                @Override
                public boolean hasNext() {
                    return position < available;
                }

                @Override
                public SkillReference next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    position++;
                    nextCalls.incrementAndGet();
                    return value;
                }
            };
        }

        private int nextCalls() {
            return nextCalls.get();
        }
    }
}
