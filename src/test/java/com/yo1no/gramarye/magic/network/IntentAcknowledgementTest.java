package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class IntentAcknowledgementTest {
    @Test
    void dispositionVocabularyAndSemanticCodesAreExactAndClosed() {
        var exact = new IntentAcknowledgement.Disposition[] {
            IntentAcknowledgement.Disposition.ACCEPTED,
            IntentAcknowledgement.Disposition.REJECTED,
            IntentAcknowledgement.Disposition.DUPLICATE,
            IntentAcknowledgement.Disposition.STALE,
            IntentAcknowledgement.Disposition.SEQUENCE_GAP,
            IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED,
            IntentAcknowledgement.Disposition.RATE_LIMITED,
            IntentAcknowledgement.Disposition.SERVER_BUSY,
            IntentAcknowledgement.Disposition.UNAVAILABLE
        };

        assertArrayEquals(exact, IntentAcknowledgement.Disposition.values());
        assertEquals(9, Arrays.stream(exact)
                .map(IntentAcknowledgement.Disposition::semanticCode)
                .collect(Collectors.toSet())
                .size());
        for (var code = 0; code < exact.length; code++) {
            assertEquals(code, exact[code].semanticCode());
            assertEquals(exact[code],
                    IntentAcknowledgement.Disposition.fromSemanticCode(code)
                            .orElseThrow());
        }
        for (var raw : new int[] {Integer.MIN_VALUE, -1, 9, 255, Integer.MAX_VALUE}) {
            assertTrue(IntentAcknowledgement.Disposition.fromSemanticCode(raw).isEmpty());
        }
    }

    @Test
    void exactThreeFlagsUseBitsZeroThroughTwoOnly() {
        assertEquals(1, IntentAcknowledgement.HAS_EXPECTED_NEXT);
        assertEquals(2, IntentAcknowledgement.SEQUENCE_CONSUMED);
        assertEquals(4, IntentAcknowledgement.RESYNC_RECOMMENDED);
        assertEquals(0b00000111, IntentAcknowledgement.ALLOWED_FLAGS);

        for (var reserved : new int[] {1 << 3, 1 << 7, 1 << 8, -1}) {
            assertThrows(P7SemanticInvariantException.class,
                    () -> new IntentAcknowledgement(
                            1,
                            IntentAcknowledgement.Disposition.REJECTED,
                            reserved,
                            null));
        }
    }

    @Test
    void expectedNextPresenceAndPositiveRangeExactlyFollowItsFlag() {
        assertThrows(P7SemanticInvariantException.class,
                () -> new IntentAcknowledgement(
                        1,
                        IntentAcknowledgement.Disposition.REJECTED,
                        IntentAcknowledgement.HAS_EXPECTED_NEXT,
                        null));
        assertThrows(P7SemanticInvariantException.class,
                () -> new IntentAcknowledgement(
                        1,
                        IntentAcknowledgement.Disposition.REJECTED,
                        0,
                        2L));
        for (var invalidExpected : new long[] {0, -1, Long.MIN_VALUE}) {
            assertThrows(P7SemanticInvariantException.class,
                    () -> new IntentAcknowledgement(
                            1,
                            IntentAcknowledgement.Disposition.REJECTED,
                            IntentAcknowledgement.HAS_EXPECTED_NEXT,
                            invalidExpected));
        }

        var valid = new IntentAcknowledgement(
                1,
                IntentAcknowledgement.Disposition.REJECTED,
                IntentAcknowledgement.HAS_EXPECTED_NEXT,
                Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, valid.expectedNext().orElseThrow());
    }

    @Test
    void acceptedRequiresSequenceConsumedAndSupportsExhaustingOrAdvancingCases() {
        assertThrows(P7SemanticInvariantException.class,
                () -> new IntentAcknowledgement(
                        1, IntentAcknowledgement.Disposition.ACCEPTED, 0, null));

        var exhausting = new IntentAcknowledgement(
                Long.MAX_VALUE,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null);
        var advancing = new IntentAcknowledgement(
                1,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED
                        | IntentAcknowledgement.HAS_EXPECTED_NEXT,
                2L);

        assertTrue((exhausting.flags() & IntentAcknowledgement.SEQUENCE_CONSUMED) != 0);
        assertTrue(exhausting.expectedNext().isEmpty());
        assertEquals(2L, advancing.expectedNext().orElseThrow());
    }

    @Test
    void sequenceClassificationRateAndBusyRejectionsCannotClaimConsumption() {
        var dispositions = new IntentAcknowledgement.Disposition[] {
            IntentAcknowledgement.Disposition.DUPLICATE,
            IntentAcknowledgement.Disposition.STALE,
            IntentAcknowledgement.Disposition.SEQUENCE_GAP,
            IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED,
            IntentAcknowledgement.Disposition.RATE_LIMITED,
            IntentAcknowledgement.Disposition.SERVER_BUSY
        };
        for (var disposition : dispositions) {
            var requiresExpected = disposition == IntentAcknowledgement.Disposition.DUPLICATE
                    || disposition == IntentAcknowledgement.Disposition.STALE
                    || disposition == IntentAcknowledgement.Disposition.SEQUENCE_GAP;
            var flags = IntentAcknowledgement.SEQUENCE_CONSUMED
                    | (requiresExpected ? IntentAcknowledgement.HAS_EXPECTED_NEXT : 0);
            var expected = requiresExpected ? 1L : null;
            assertThrows(P7SemanticInvariantException.class,
                    () -> new IntentAcknowledgement(1, disposition, flags, expected));
        }
    }

    @Test
    void duplicateStaleAndGapRequireCurrentExpectedNext() {
        var dispositions = new IntentAcknowledgement.Disposition[] {
            IntentAcknowledgement.Disposition.DUPLICATE,
            IntentAcknowledgement.Disposition.STALE,
            IntentAcknowledgement.Disposition.SEQUENCE_GAP
        };
        for (var disposition : dispositions) {
            assertThrows(P7SemanticInvariantException.class,
                    () -> new IntentAcknowledgement(1, disposition, 0, null));
            var acknowledgement = new IntentAcknowledgement(
                    1,
                    disposition,
                    IntentAcknowledgement.HAS_EXPECTED_NEXT,
                    2L);
            assertEquals(2L, acknowledgement.expectedNext().orElseThrow());
            assertFalse((acknowledgement.flags()
                    & IntentAcknowledgement.SEQUENCE_CONSUMED) != 0);
        }
    }

    @Test
    void staleAndGapMayRecommendResynchronizationWithoutBeingConsumed() {
        for (var disposition : new IntentAcknowledgement.Disposition[] {
            IntentAcknowledgement.Disposition.STALE,
            IntentAcknowledgement.Disposition.SEQUENCE_GAP
        }) {
            var acknowledgement = new IntentAcknowledgement(
                    7,
                    disposition,
                    IntentAcknowledgement.HAS_EXPECTED_NEXT
                            | IntentAcknowledgement.RESYNC_RECOMMENDED,
                    8L);
            assertTrue((acknowledgement.flags()
                    & IntentAcknowledgement.RESYNC_RECOMMENDED) != 0);
            assertFalse((acknowledgement.flags()
                    & IntentAcknowledgement.SEQUENCE_CONSUMED) != 0);
        }
    }

    @Test
    void resyncRecommendationIsClosedToRepairAndSafeUnavailableDispositions() {
        assertDoesNotThrow(() -> new IntentAcknowledgement(
                7L,
                IntentAcknowledgement.Disposition.UNAVAILABLE,
                IntentAcknowledgement.RESYNC_RECOMMENDED,
                null));

        for (var disposition : new IntentAcknowledgement.Disposition[] {
            IntentAcknowledgement.Disposition.ACCEPTED,
            IntentAcknowledgement.Disposition.REJECTED,
            IntentAcknowledgement.Disposition.DUPLICATE,
            IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED,
            IntentAcknowledgement.Disposition.RATE_LIMITED,
            IntentAcknowledgement.Disposition.SERVER_BUSY
        }) {
            var baseFlags = disposition == IntentAcknowledgement.Disposition.ACCEPTED
                    ? IntentAcknowledgement.SEQUENCE_CONSUMED
                    : 0;
            var expectedNext = disposition == IntentAcknowledgement.Disposition.DUPLICATE
                    ? Long.valueOf(7L)
                    : null;
            if (expectedNext != null) {
                baseFlags |= IntentAcknowledgement.HAS_EXPECTED_NEXT;
            }
            var forbiddenFlags = baseFlags | IntentAcknowledgement.RESYNC_RECOMMENDED;
            assertThrows(
                    P7SemanticInvariantException.class,
                    () -> new IntentAcknowledgement(
                            7L, disposition, forbiddenFlags, expectedNext),
                    disposition.name());
        }
    }

    @Test
    void exhaustedDispositionHasNoExpectedNext() {
        assertThrows(P7SemanticInvariantException.class,
                () -> new IntentAcknowledgement(
                        Long.MAX_VALUE,
                        IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED,
                        IntentAcknowledgement.HAS_EXPECTED_NEXT,
                        Long.MAX_VALUE));
        assertThrows(P7SemanticInvariantException.class,
                () -> new IntentAcknowledgement(
                        Long.MAX_VALUE,
                        IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED,
                        IntentAcknowledgement.SEQUENCE_CONSUMED,
                        null));
        var exhausted = new IntentAcknowledgement(
                Long.MAX_VALUE,
                IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED,
                0,
                null);
        assertTrue(exhausted.expectedNext().isEmpty());
    }

    @Test
    void acknowledgementBodyIsExactlyTenOrEighteenBytesAndNeverExceedsHardCeiling() {
        var withoutExpected = new IntentAcknowledgement(
                0, IntentAcknowledgement.Disposition.REJECTED, 0, null);
        var withExpected = new IntentAcknowledgement(
                1,
                IntentAcknowledgement.Disposition.REJECTED,
                IntentAcknowledgement.HAS_EXPECTED_NEXT,
                2L);

        assertEquals(10, withoutExpected.encodedBodySize());
        assertEquals(P7NetworkBounds.ACTUAL_MAX_ACK_BODY_BYTES,
                withExpected.encodedBodySize());
        assertTrue(withExpected.encodedBodySize() <= P7NetworkBounds.MAX_S2C_ACK_BYTES);
        assertEquals(0, withoutExpected.sequence());
        assertEquals(IntentAcknowledgement.Disposition.REJECTED,
                withoutExpected.disposition());
    }

    @Test
    void allTwentyFailureReasonsHaveTheExactDispositionOrNoAckMapping() {
        assertArrayEquals(
                new P7IntentFailureReason[] {
                    P7IntentFailureReason.MALFORMED_PAYLOAD,
                    P7IntentFailureReason.PROTOCOL_VERSION_MISMATCH,
                    P7IntentFailureReason.UNAUTHENTICATED_SENDER,
                    P7IntentFailureReason.UNAUTHORIZED_INTENT,
                    P7IntentFailureReason.UNKNOWN_SKILL,
                    P7IntentFailureReason.UNKNOWN_ACTION,
                    P7IntentFailureReason.INVALID_TARGET,
                    P7IntentFailureReason.TARGET_UNAVAILABLE,
                    P7IntentFailureReason.INVALID_SEQUENCE,
                    P7IntentFailureReason.DUPLICATE_SEQUENCE,
                    P7IntentFailureReason.STALE_SEQUENCE,
                    P7IntentFailureReason.SEQUENCE_GAP,
                    P7IntentFailureReason.SEQUENCE_EXHAUSTED,
                    P7IntentFailureReason.RATE_LIMITED,
                    P7IntentFailureReason.SERVER_BUSY,
                    P7IntentFailureReason.P5_ADMISSION_REJECTED,
                    P7IntentFailureReason.P5_UNAVAILABLE,
                    P7IntentFailureReason.INTERNAL_SERVER_FAULT,
                    P7IntentFailureReason.DISCONNECTED,
                    P7IntentFailureReason.RELOAD_IN_PROGRESS
                },
                P7IntentFailureReason.values());

        for (var reason : P7IntentFailureReason.values()) {
            assertEquals(expectedDisposition(reason),
                    IntentAcknowledgement.dispositionFor(reason), reason.name());
        }
        assertThrows(P7SemanticInvariantException.class,
                () -> IntentAcknowledgement.dispositionFor(null));
    }

    @Test
    void failureVocabularyHasNoCatchAllFreeTextOrThrowableState() {
        assertEquals(20, P7IntentFailureReason.values().length);
        assertFalse(Arrays.stream(P7IntentFailureReason.values())
                .map(Enum::name)
                .anyMatch("UNKNOWN"::equals));
        assertEquals(0, Arrays.stream(P7IntentFailureReason.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .count());
        assertFalse(Throwable.class.isAssignableFrom(P7IntentFailureReason.class));
    }

    @Test
    void acknowledgementIsPackagePrivateImmutableAndNotANetworkPayload() {
        var typeModifiers = IntentAcknowledgement.class.getModifiers();
        var fields = IntentAcknowledgement.class.getDeclaredFields();
        var instanceFields = Arrays.stream(fields)
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertFalse(Modifier.isPublic(typeModifiers));
        assertFalse(Modifier.isProtected(typeModifiers));
        assertTrue(Modifier.isFinal(typeModifiers));
        assertEquals(Set.of("sequence", "disposition", "flags", "expectedNext"),
                instanceFields.stream().map(field -> field.getName())
                        .collect(Collectors.toSet()));
        assertTrue(instanceFields.stream().allMatch(field ->
                Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
        assertTrue(Arrays.stream(fields)
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers())));
        assertTrue(instanceFields.stream().noneMatch(field -> field.getType().isArray()
                || Collection.class.isAssignableFrom(field.getType())
                || Throwable.class.isAssignableFrom(field.getType())));
        assertEquals(0, IntentAcknowledgement.class.getInterfaces().length);
        assertFalse(Modifier.isPublic(
                IntentAcknowledgement.Disposition.class.getModifiers()));
    }

    @Test
    void sourceWireMappingsDoNotDependOnEnumOrdinal() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/"
                        + "IntentAcknowledgement.java"));
        assertFalse(Pattern.compile("\\.\\s*ordinal\\s*\\(").matcher(source).find());
        assertTrue(source.contains("switch (rawCode)"));
        assertTrue(source.contains("switch (reason)"));
    }

    private static Optional<IntentAcknowledgement.Disposition> expectedDisposition(
            P7IntentFailureReason reason) {
        return switch (reason) {
            case MALFORMED_PAYLOAD,
                    PROTOCOL_VERSION_MISMATCH,
                    UNAUTHENTICATED_SENDER,
                    INTERNAL_SERVER_FAULT,
                    DISCONNECTED -> Optional.empty();
            case UNAUTHORIZED_INTENT,
                    UNKNOWN_SKILL,
                    UNKNOWN_ACTION,
                    INVALID_TARGET,
                    TARGET_UNAVAILABLE,
                    INVALID_SEQUENCE,
                    P5_ADMISSION_REJECTED ->
                    Optional.of(IntentAcknowledgement.Disposition.REJECTED);
            case DUPLICATE_SEQUENCE ->
                    Optional.of(IntentAcknowledgement.Disposition.DUPLICATE);
            case STALE_SEQUENCE -> Optional.of(IntentAcknowledgement.Disposition.STALE);
            case SEQUENCE_GAP ->
                    Optional.of(IntentAcknowledgement.Disposition.SEQUENCE_GAP);
            case SEQUENCE_EXHAUSTED ->
                    Optional.of(IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED);
            case RATE_LIMITED ->
                    Optional.of(IntentAcknowledgement.Disposition.RATE_LIMITED);
            case SERVER_BUSY ->
                    Optional.of(IntentAcknowledgement.Disposition.SERVER_BUSY);
            case P5_UNAVAILABLE, RELOAD_IN_PROGRESS ->
                    Optional.of(IntentAcknowledgement.Disposition.UNAVAILABLE);
        };
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("project root is unavailable");
        }
        return current;
    }
}
