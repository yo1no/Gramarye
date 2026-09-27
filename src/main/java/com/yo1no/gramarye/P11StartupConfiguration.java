package com.yo1no.gramarye;

import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import java.util.Objects;
import java.util.Optional;

/** Configuration candidates, not source, writer, control, or gameplay authority. */
record P11ServerConfigCandidate(P5RuntimeLimitLoadState p5State, P11StartupLoadState p11State) {
    P11ServerConfigCandidate {
        Objects.requireNonNull(p5State, "p5State");
        Objects.requireNonNull(p11State, "p11State");
    }

    static P11ServerConfigCandidate unavailable() {
        return new P11ServerConfigCandidate(
                P5RuntimeLimitLoadState.Unavailable.INSTANCE,
                P11StartupLoadState.Unavailable.INSTANCE);
    }
}

record P11StartupSnapshot(P5RuntimeLimits p5Limits, P11StartupLoadState p11State) {
    P11StartupSnapshot {
        Objects.requireNonNull(p5Limits, "p5Limits");
        Objects.requireNonNull(p11State, "p11State");
    }
}

sealed interface P11StartupLoadState {
    record Ready(P11StartupLimits limits) implements P11StartupLoadState {
        public Ready {
            Objects.requireNonNull(limits, "limits");
        }
    }

    record Invalid(P11ConfigurationFailure failure) implements P11StartupLoadState {
        public Invalid {
            Objects.requireNonNull(failure, "failure");
        }
    }

    enum Unavailable implements P11StartupLoadState {
        INSTANCE
    }
}

record P11RequestedLimits(
        int maxUuids,
        int maxWaitingConnections,
        long admissionWaitMillis,
        int tryBurst,
        int tryRefillPerSecond,
        int statusBurst,
        int statusRefillPerSecond,
        int mainQuantaPerTick,
        int maxSealedSnapshots,
        long maxSealedBytes,
        int dirtyUuidAdmissionWatermark,
        long oldestDirtyWarnMillis) {
    P11RequestedLimits {
        P11ConfigurationValidation.requireValid(new long[] {
            maxUuids, maxWaitingConnections, admissionWaitMillis, tryBurst, tryRefillPerSecond,
            statusBurst, statusRefillPerSecond, mainQuantaPerTick, maxSealedSnapshots,
            maxSealedBytes, dirtyUuidAdmissionWatermark, oldestDirtyWarnMillis
        });
    }
}

/**
 * Wait and warning intervals stay in monotonic milliseconds, as consumed by the
 * foundation clocks. There is no nanos conversion or artificial MAX_VALUE / 1_000_000 cap.
 * Refill saturates before multiplication; elapsed and reservation arithmetic are checked
 * against the actual current operands by their owners, not guessed from configuration.
 */
record P11StartupLimits(
        int maxUuids,
        int maxWaitingConnections,
        long admissionWaitMillis,
        int tryBurst,
        int tryRefillPerSecond,
        int statusBurst,
        int statusRefillPerSecond,
        int mainQuantaPerTick,
        int maxSealedSnapshots,
        long maxSealedBytes,
        int dirtyUuidAdmissionWatermark,
        long oldestDirtyWarnMillis) {
    P11StartupLimits {
        P11ConfigurationValidation.requireValid(new long[] {
            maxUuids, maxWaitingConnections, admissionWaitMillis, tryBurst, tryRefillPerSecond,
            statusBurst, statusRefillPerSecond, mainQuantaPerTick, maxSealedSnapshots,
            maxSealedBytes, dirtyUuidAdmissionWatermark, oldestDirtyWarnMillis
        });
    }

    static P11StartupLimits fromRequested(P11RequestedLimits limits) {
        Objects.requireNonNull(limits, "limits");
        return new P11StartupLimits(
                limits.maxUuids(), limits.maxWaitingConnections(), limits.admissionWaitMillis(),
                limits.tryBurst(), limits.tryRefillPerSecond(), limits.statusBurst(),
                limits.statusRefillPerSecond(), limits.mainQuantaPerTick(),
                limits.maxSealedSnapshots(), limits.maxSealedBytes(),
                limits.dirtyUuidAdmissionWatermark(), limits.oldestDirtyWarnMillis());
    }

}

enum P11ConfigurationKey {
    MAX_UUIDS("p11.retention.maxUuids", true),
    MAX_WAITING_CONNECTIONS("p11.control.maxWaitingConnections", true),
    ADMISSION_WAIT_MILLIS("p11.control.admissionWaitMillis", false),
    TRY_BURST("p11.control.tryBurst", true),
    TRY_REFILL_PER_SECOND("p11.control.tryRefillPerSecond", true),
    STATUS_BURST("p11.control.statusBurst", true),
    STATUS_REFILL_PER_SECOND("p11.control.statusRefillPerSecond", true),
    MAIN_QUANTA_PER_TICK("p11.control.mainQuantaPerTick", true),
    MAX_SEALED_SNAPSHOTS("p11.save.maxSealedSnapshots", true),
    MAX_SEALED_BYTES("p11.save.maxSealedBytes", false),
    DIRTY_UUID_ADMISSION_WATERMARK("p11.save.dirtyUuidAdmissionWatermark", true),
    OLDEST_DIRTY_WARN_MILLIS("p11.save.oldestDirtyWarnMillis", false);

    private final String rawPath;
    private final boolean intValue;

    P11ConfigurationKey(String rawPath, boolean intValue) {
        this.rawPath = rawPath;
        this.intValue = intValue;
    }

    String rawPath() {
        return rawPath;
    }

    boolean requiresInt() {
        return intValue;
    }
}

enum P11ConfigurationFailureReason {
    MISSING_REQUIRED_VALUE,
    WRONG_VALUE_TYPE,
    BELOW_MINIMUM,
    ABOVE_REPRESENTABLE_MAXIMUM,
    DIRTY_WATERMARK_EXCEEDS_RETENTION
}

record P11ConfigurationFailure(
        P11ConfigurationFailureReason reason,
        P11ConfigurationKey key,
        Optional<P11ConfigurationKey> relatedKey) {
    P11ConfigurationFailure {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(relatedKey, "relatedKey");
    }
}

final class P11ConfigurationException extends IllegalArgumentException {
    private final P11ConfigurationFailure failure;

    P11ConfigurationException(P11ConfigurationFailure failure) {
        super("P11_STARTUP_CONFIG: " + Objects.requireNonNull(failure, "failure").reason()
                + " key=" + failure.key().rawPath());
        this.failure = failure;
    }

    P11ConfigurationFailure failure() {
        return failure;
    }
}

final class P11ConfigurationValidation {
    private static final P11ConfigurationKey[] ORDERED_KEYS = P11ConfigurationKey.values();

    private P11ConfigurationValidation() {
    }

    static P11StartupLoadState decode(UnmodifiableCommentedConfig raw) {
        Objects.requireNonNull(raw, "raw");
        var values = new long[ORDERED_KEYS.length];
        for (var key : ORDERED_KEYS) {
            if (!raw.contains(key.rawPath())) {
                return invalid(P11ConfigurationFailureReason.MISSING_REQUIRED_VALUE, key);
            }
            Object value = raw.getRaw(key.rawPath());
            if (value instanceof Integer integer) {
                values[key.ordinal()] = integer.longValue();
            } else if (value instanceof Long integral) {
                values[key.ordinal()] = integral;
            } else {
                return invalid(P11ConfigurationFailureReason.WRONG_VALUE_TYPE, key);
            }
            var failure = valueFailure(key, values[key.ordinal()]);
            if (failure.isPresent()) {
                return new P11StartupLoadState.Invalid(failure.get());
            }
        }
        try {
            var requested = new P11RequestedLimits(
                    (int) values[0], (int) values[1], values[2], (int) values[3],
                    (int) values[4], (int) values[5], (int) values[6], (int) values[7],
                    (int) values[8], values[9], (int) values[10], values[11]);
            return new P11StartupLoadState.Ready(P11StartupLimits.fromRequested(requested));
        } catch (P11ConfigurationException invalid) {
            return new P11StartupLoadState.Invalid(invalid.failure());
        }
    }

    static void requireValid(long[] values) {
        Objects.requireNonNull(values, "values");
        if (values.length != ORDERED_KEYS.length) {
            throw new IllegalArgumentException("P11_STARTUP_CONFIG_INCOMPLETE_TUPLE");
        }
        for (var key : ORDERED_KEYS) {
            var failure = valueFailure(key, values[key.ordinal()]);
            if (failure.isPresent()) {
                throw new P11ConfigurationException(failure.get());
            }
        }
        if (values[P11ConfigurationKey.DIRTY_UUID_ADMISSION_WATERMARK.ordinal()]
                > values[P11ConfigurationKey.MAX_UUIDS.ordinal()]) {
            throw new P11ConfigurationException(new P11ConfigurationFailure(
                    P11ConfigurationFailureReason.DIRTY_WATERMARK_EXCEEDS_RETENTION,
                    P11ConfigurationKey.DIRTY_UUID_ADMISSION_WATERMARK,
                    Optional.of(P11ConfigurationKey.MAX_UUIDS)));
        }
    }

    private static Optional<P11ConfigurationFailure> valueFailure(
            P11ConfigurationKey key, long value) {
        P11ConfigurationFailureReason reason;
        if (value < 1L) {
            reason = P11ConfigurationFailureReason.BELOW_MINIMUM;
        } else if (key.requiresInt() && value > Integer.MAX_VALUE) {
            reason = P11ConfigurationFailureReason.ABOVE_REPRESENTABLE_MAXIMUM;
        } else {
            return Optional.empty();
        }
        return Optional.of(new P11ConfigurationFailure(reason, key, Optional.empty()));
    }

    private static P11StartupLoadState.Invalid invalid(
            P11ConfigurationFailureReason reason, P11ConfigurationKey key) {
        return new P11StartupLoadState.Invalid(
                new P11ConfigurationFailure(reason, key, Optional.empty()));
    }
}
