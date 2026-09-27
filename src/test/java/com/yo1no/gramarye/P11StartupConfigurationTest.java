package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.concurrent.SynchronizedConfig;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class P11StartupConfigurationTest {
    @Test
    void exactP11KeysDoNotExtendP5VocabularyOrInstallOperationalDefaults() {
        assertEquals(List.of(
                "p11.retention.maxUuids", "p11.control.maxWaitingConnections",
                "p11.control.admissionWaitMillis", "p11.control.tryBurst",
                "p11.control.tryRefillPerSecond", "p11.control.statusBurst",
                "p11.control.statusRefillPerSecond", "p11.control.mainQuantaPerTick",
                "p11.save.maxSealedSnapshots", "p11.save.maxSealedBytes",
                "p11.save.dirtyUuidAdmissionWatermark", "p11.save.oldestDirtyWarnMillis"),
                Arrays.stream(P11ConfigurationKey.values()).map(P11ConfigurationKey::rawPath).toList());
        assertEquals(16, P5RuntimeLimitKey.values().length);
        var state = emptyState();
        var rawSpec = new P5RawServerConfigSpec(state);
        var raw = CommentedConfig.inMemory();
        rawSpec.correct(raw);
        assertFalse(raw.contains("p11"));
        rawSpec.acceptRawConfig(raw);
        assertInstanceOf(P5RuntimeLimitLoadState.Requested.class, state.get().p5State());
        assertFailure(state.get(), P11ConfigurationKey.MAX_UUIDS,
                P11ConfigurationFailureReason.MISSING_REQUIRED_VALUE);
        assertEquals(4_096, P5ServerRuntimeConfig.snapshotCandidate(state.get().p5State())
                .pendingEventsPerServer());
        assertFalse(raw.contains("p11"));
    }

    @Test
    void rawReaderBuildsImmutableRequestedAndValidatedDiagnosticLimits() {
        var state = emptyState();
        var spec = new P5RawServerConfigSpec(state);
        var raw = diagnosticRawProfile();
        // This is the same typed raw-reader entry used by the platform's permitted LoadedConfig.
        // Do not invent an implementation of the sealed ILoadedConfig platform interface.
        spec.acceptRawConfig(raw);
        var limits = ready(state.get());
        assertAll(
                () -> assertEquals(4, limits.maxUuids()),
                () -> assertEquals(3, limits.maxWaitingConnections()),
                () -> assertEquals(30_000L, limits.admissionWaitMillis()),
                () -> assertEquals(2, limits.tryBurst()),
                () -> assertEquals(2, limits.tryRefillPerSecond()),
                () -> assertEquals(4, limits.statusBurst()),
                () -> assertEquals(4, limits.statusRefillPerSecond()),
                () -> assertEquals(2, limits.mainQuantaPerTick()),
                () -> assertEquals(2, limits.maxSealedSnapshots()),
                () -> assertEquals(8L * 1_024 * 1_024, limits.maxSealedBytes()),
                () -> assertEquals(3, limits.dirtyUuidAdmissionWatermark()),
                () -> assertEquals(10_000L, limits.oldestDirtyWarnMillis()));
        raw.set("p11.retention.maxUuids", 5);
        assertEquals(4, limits.maxUuids());
        assertSame(limits, ready(state.get()));
        spec.acceptRawConfig(raw);
        assertEquals(5, ready(state.get()).maxUuids());
        assertEquals(4, limits.maxUuids());
        spec.acceptConfig(null);
        assertEquals(P11ServerConfigCandidate.unavailable(), state.get());
        assertEquals(4, limits.maxUuids());
    }

    @Test
    void everyRequiredKeyRejectsMissingWrongTypeZeroAndNegativeWithoutCorrection() {
        var wrongTypes = List.of(1.0d, 1.0f, "1", true,
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        for (var key : P11ConfigurationKey.values()) {
            var missing = diagnosticRawProfile();
            missing.remove(key.rawPath());
            assertFailure(decode(missing), key, P11ConfigurationFailureReason.MISSING_REQUIRED_VALUE);
            assertFalse(missing.contains(key.rawPath()));
            for (var bad : wrongTypes) {
                var raw = diagnosticRawProfile();
                raw.set(key.rawPath(), bad);
                assertFailure(decode(raw), key, P11ConfigurationFailureReason.WRONG_VALUE_TYPE);
                assertSame(bad, raw.getRaw(key.rawPath()));
            }
            for (var bad : new long[] {0L, -1L, Long.MIN_VALUE}) {
                var raw = diagnosticRawProfile();
                raw.set(key.rawPath(), bad);
                assertFailure(decode(raw), key, P11ConfigurationFailureReason.BELOW_MINIMUM);
                assertEquals(bad, raw.<Long>getRaw(key.rawPath()));
            }
        }
    }

    @Test
    void intOverflowIsRejectedBeforeNarrowingAndIntegralLongValuesAreAccepted() {
        for (var key : P11ConfigurationKey.values()) {
            if (!key.requiresInt()) {
                continue;
            }
            for (var bad : new long[] {1L + Integer.MAX_VALUE, Long.MAX_VALUE}) {
                var raw = diagnosticRawProfile();
                raw.set(key.rawPath(), bad);
                assertFailure(decode(raw), key,
                        P11ConfigurationFailureReason.ABOVE_REPRESENTABLE_MAXIMUM);
                assertEquals(bad, raw.<Long>getRaw(key.rawPath()));
            }
        }
        var raw = diagnosticRawProfile();
        for (var key : P11ConfigurationKey.values()) {
            if (key.requiresInt()) {
                raw.set(key.rawPath(), (long) Integer.MAX_VALUE);
            }
        }
        raw.set("p11.save.maxSealedBytes", Long.MAX_VALUE);
        raw.set("p11.control.admissionWaitMillis", Long.MAX_VALUE);
        raw.set("p11.save.oldestDirtyWarnMillis", Long.MAX_VALUE);
        var limits = ready(decode(raw));
        assertAll(
                () -> assertEquals(Integer.MAX_VALUE, limits.maxUuids()),
                () -> assertEquals(Integer.MAX_VALUE, limits.tryRefillPerSecond()),
                () -> assertEquals(Integer.MAX_VALUE, limits.statusRefillPerSecond()),
                () -> assertEquals(Integer.MAX_VALUE, limits.maxSealedSnapshots()),
                () -> assertEquals(Long.MAX_VALUE, limits.maxSealedBytes()),
                () -> assertEquals(Long.MAX_VALUE, limits.admissionWaitMillis()),
                () -> assertEquals(Long.MAX_VALUE, limits.oldestDirtyWarnMillis()));
    }

    @Test
    void crossFieldFailureNamesBothKeysWithoutInventingUnusedClockConversionLimits() {
        var raw = diagnosticRawProfile();
        raw.set("p11.save.dirtyUuidAdmissionWatermark", 5);
        var invalid = assertFailure(decode(raw), P11ConfigurationKey.DIRTY_UUID_ADMISSION_WATERMARK,
                P11ConfigurationFailureReason.DIRTY_WATERMARK_EXCEEDS_RETENTION);
        assertEquals(Optional.of(P11ConfigurationKey.MAX_UUIDS), invalid.relatedKey());
        raw = diagnosticRawProfile();
        raw.set("p11.control.admissionWaitMillis", Long.MAX_VALUE);
        raw.set("p11.save.oldestDirtyWarnMillis", Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, ready(decode(raw)).admissionWaitMillis());
    }

    @Test
    void typedConstructorsCannotBypassPositiveLimitsOrCrossFieldValidation() {
        assertThrows(P11ConfigurationException.class,
                () -> new P11RequestedLimits(0, 1, 1, 1, 1, 1, 1, 1, 1, 1L, 1, 1L));
        assertThrows(P11ConfigurationException.class,
                () -> new P11StartupLimits(1, 1, 1, 1, 1, 1, 1, 1, 1, 1L, 2, 1L));
        assertEquals(Long.MAX_VALUE,
                new P11StartupLimits(1, 1, Long.MAX_VALUE, 1, 1, 1, 1, 1, 1, 1L, 1, 1L)
                        .admissionWaitMillis());
        var smallest = new P11RequestedLimits(1, 1, 1L, 1, 1, 1, 1, 1, 1, 1L, 1, 1L);
        assertEquals(1L, P11StartupLimits.fromRequested(smallest).admissionWaitMillis());
    }

    @Test
    void p5InvalidAndP11InvalidRemainIndependentWithinOneCandidate() {
        var raw = diagnosticRawProfile();
        raw.remove("runtime.cancellationsPerTick");
        var p5Invalid = decode(raw);
        assertInstanceOf(P5RuntimeLimitLoadState.Invalid.class, p5Invalid.p5State());
        assertInstanceOf(P11StartupLoadState.Ready.class, p5Invalid.p11State());
        raw = diagnosticRawProfile();
        raw.remove("p11.save.maxSealedBytes");
        var p11Invalid = decode(raw);
        assertInstanceOf(P5RuntimeLimitLoadState.Requested.class, p11Invalid.p5State());
        assertInstanceOf(P11StartupLoadState.Invalid.class, p11Invalid.p11State());
    }

    @Test
    void concurrentRawObservationAndAtomicPublicationNeverMixReloadTuples() throws Exception {
        var raw = new SynchronizedConfig();
        raw.putAll(diagnosticRawProfile());
        raw.set("runtime.cancellationsPerTick", 1);
        raw.set("p11.retention.maxUuids", 1);
        raw.set("p11.save.dirtyUuidAdmissionWatermark", 1);
        var state = emptyState();
        var spec = new P5RawServerConfigSpec(state);
        spec.acceptRawConfig(raw);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int index = 0; index < 1_000; index++) {
                    int value = index % 2 + 1;
                    raw.bulkCommentedUpdate(view -> {
                        view.set("runtime.cancellationsPerTick", value);
                        view.set("p11.retention.maxUuids", value);
                        return null;
                    });
                    spec.acceptRawConfig(raw);
                }
                return null;
            });
            start.countDown();
            for (int index = 0; index < 1_000; index++) {
                spec.acceptRawConfig(raw);
                var candidate = state.get();
                assertEquals(P5ServerRuntimeConfig.snapshotCandidate(candidate.p5State())
                        .cancellationsPerTick(), ready(candidate).maxUuids());
            }
            writer.get(10, TimeUnit.SECONDS);
        }
    }

    /** Explicit engineering input from §10.5, never installed as production defaults. */
    static CommentedConfig diagnosticRawProfile() {
        var raw = CommentedConfig.inMemory();
        new P5RawServerConfigSpec(emptyState()).correct(raw);
        raw.set("p11.retention.maxUuids", 4);
        raw.set("p11.control.maxWaitingConnections", 3);
        raw.set("p11.control.admissionWaitMillis", 30_000L);
        raw.set("p11.control.tryBurst", 2);
        raw.set("p11.control.tryRefillPerSecond", 2);
        raw.set("p11.control.statusBurst", 4);
        raw.set("p11.control.statusRefillPerSecond", 4);
        raw.set("p11.control.mainQuantaPerTick", 2);
        raw.set("p11.save.maxSealedSnapshots", 2);
        raw.set("p11.save.maxSealedBytes", 8L * 1_024 * 1_024);
        raw.set("p11.save.dirtyUuidAdmissionWatermark", 3);
        raw.set("p11.save.oldestDirtyWarnMillis", 10_000L);
        return raw;
    }

    private static P11ServerConfigCandidate decode(CommentedConfig raw) {
        var state = emptyState();
        new P5RawServerConfigSpec(state).acceptRawConfig(raw);
        return state.get();
    }

    private static P11StartupLimits ready(P11ServerConfigCandidate candidate) {
        return assertInstanceOf(P11StartupLoadState.Ready.class, candidate.p11State()).limits();
    }

    private static P11ConfigurationFailure assertFailure(P11ServerConfigCandidate candidate,
            P11ConfigurationKey key, P11ConfigurationFailureReason reason) {
        var failure = assertInstanceOf(P11StartupLoadState.Invalid.class, candidate.p11State()).failure();
        assertEquals(key, failure.key());
        assertEquals(reason, failure.reason());
        return failure;
    }

    private static AtomicReference<P11ServerConfigCandidate> emptyState() {
        return new AtomicReference<>(P11ServerConfigCandidate.unavailable());
    }
}
