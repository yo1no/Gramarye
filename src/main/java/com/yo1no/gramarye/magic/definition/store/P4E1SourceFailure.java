package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded machine-readable failure emitted by P4-E1 source admission. */
record P4E1SourceFailure(
        Code code,
        P4E1AuditStage stage,
        Optional<P4E1AuditCounter> counter,
        long observedAtLeast,
        long maximum,
        int ordinal,
        Optional<UUID> playerId,
        String exceptionClassName) {
    private static final int MAX_EXCEPTION_CLASS_NAME_LENGTH = 160;

    P4E1SourceFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(counter, "counter");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        if (observedAtLeast < 0L || maximum < 0L || ordinal < 0) {
            throw new IllegalArgumentException("P4-E1 failure metadata must be non-negative");
        }
        if (exceptionClassName.length() > MAX_EXCEPTION_CLASS_NAME_LENGTH) {
            throw new IllegalArgumentException("exception class name is not bounded");
        }
    }

    static P4E1SourceFailure simple(Code code, P4E1AuditStage stage) {
        return new P4E1SourceFailure(
                code, stage, Optional.empty(), 0L, 0L, 0, Optional.empty(), "");
    }

    static P4E1SourceFailure forRoute(
            Code code, P4E1AuditStage stage, UUID playerId) {
        return new P4E1SourceFailure(
                code,
                stage,
                Optional.empty(),
                0L,
                0L,
                0,
                Optional.of(Objects.requireNonNull(playerId, "playerId")),
                "");
    }

    static P4E1SourceFailure capacity(P4E1AuditBudget.Exceeded exceeded) {
        Objects.requireNonNull(exceeded, "exceeded");
        return new P4E1SourceFailure(
                Code.COUNTER_CAPACITY_EXCEEDED,
                exceeded.stage(),
                Optional.of(exceeded.counter()),
                exceeded.observedAtLeast(),
                exceeded.maximum(),
                0,
                Optional.empty(),
                "");
    }

    static P4E1SourceFailure capacity(
            P4E1AuditCounter counter,
            P4E1AuditStage stage,
            long observedAtLeast,
            long maximum,
            UUID playerId) {
        return new P4E1SourceFailure(
                Code.COUNTER_CAPACITY_EXCEEDED,
                stage,
                Optional.of(Objects.requireNonNull(counter, "counter")),
                observedAtLeast,
                maximum,
                0,
                Optional.of(Objects.requireNonNull(playerId, "playerId")),
                "");
    }

    static P4E1SourceFailure runtime(
            Code code, P4E1AuditStage stage, RuntimeException exception) {
        Objects.requireNonNull(exception, "exception");
        var className = boundedExceptionClassName(exception);
        return new P4E1SourceFailure(
                code,
                stage,
                Optional.empty(),
                0L,
                0L,
                0,
                Optional.empty(),
                className);
    }

    static P4E1SourceFailure heapFloor(
            Code code, String exceptionClassName) {
        if (code != Code.HEAP_FLOOR_NOT_MET
                && code != Code.HEAP_FLOOR_UNVERIFIABLE) {
            throw new IllegalArgumentException("not a heap-floor failure code");
        }
        return new P4E1SourceFailure(
                code,
                P4E1AuditStage.HEAP_FLOOR_OBSERVATION,
                Optional.empty(),
                0L,
                0L,
                0,
                Optional.empty(),
                boundedExceptionClassName(exceptionClassName));
    }

    static String boundedExceptionClassName(RuntimeException exception) {
        Objects.requireNonNull(exception, "exception");
        return boundedExceptionClassName(exception.getClass().getName());
    }

    private static String boundedExceptionClassName(String className) {
        Objects.requireNonNull(className, "className");
        return className.length() <= MAX_EXCEPTION_CLASS_NAME_LENGTH
                ? className
                : className.substring(0, MAX_EXCEPTION_CLASS_NAME_LENGTH);
    }

    enum Code {
        HEAP_FLOOR_NOT_MET,
        HEAP_FLOOR_UNVERIFIABLE,
        COUNTER_CAPACITY_EXCEEDED,
        DIRECTORY_UNREADABLE,
        DIRECTORY_TYPE_UNSUPPORTED,
        DIRECTORY_IDENTITY_UNAVAILABLE,
        DIRECTORY_RACE_DETECTED,
        PLAYERDATA_NAME_NONCANONICAL,
        PRIMARY_FILE_UNREADABLE,
        PRIMARY_FILE_TYPE_UNSUPPORTED,
        PRIMARY_FILE_IDENTITY_UNAVAILABLE,
        PRIMARY_FILE_RACE_DETECTED,
        PLATFORM_READ_FAILURE_PROVEN,
        STRICT_GZIP_REJECTED,
        STRICT_NBT_REJECTED,
        DATA_VERSION_MISSING,
        DATA_VERSION_WRONG_TYPE,
        DATA_VERSION_NOT_CURRENT,
        ATTACHMENT_ADMISSION_REJECTED,
        ATTACHMENT_QUARANTINED,
        INTEGRATED_OWNER_IDENTITY_UNAVAILABLE,
        INTEGRATED_OWNER_FRESHNESS_LOST,
        ONLINE_SOURCE_FRESHNESS_LOST,
        INTERNAL_RUNTIME_FAILURE
    }
}
