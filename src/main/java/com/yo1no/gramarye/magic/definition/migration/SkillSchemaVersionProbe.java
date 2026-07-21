package com.yo1no.gramarye.magic.definition.migration;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;

/** Reads only the exact non-negative skill-level schema version from a snapshotted root. */
public final class SkillSchemaVersionProbe {
    private static final BigInteger MAX_VERSION = BigInteger.valueOf(Integer.MAX_VALUE);

    private SkillSchemaVersionProbe() {
    }

    public static Result probe(RawSkillDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return probeCaptured(snapshot.copyRawDocument());
    }

    private static <T> Result probeCaptured(Dynamic<T> root) {
        var mapResult = root.getOps().getMap(root.getValue());
        if (mapResult.error().isPresent()) {
            return new Result.Failure(SkillMigrationFailure.of(SkillMigrationFailure.Code.INVALID_ROOT));
        }
        var field = findField(root.getOps(), mapResult.result().orElseThrow());
        if (field.isEmpty()) {
            return new Result.Failure(
                    SkillMigrationFailure.of(SkillMigrationFailure.Code.MISSING_SCHEMA_VERSION));
        }
        var exact = exactInteger(field.orElseThrow());
        if (exact.isEmpty()) {
            return new Result.Failure(
                    SkillMigrationFailure.of(SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION));
        }
        var value = exact.orElseThrow();
        if (value.signum() < 0 || value.compareTo(MAX_VERSION) > 0) {
            if (value.bitLength() < Long.SIZE) {
                return new Result.Failure(SkillMigrationFailure.forObserved(
                        SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION,
                        value.longValue()));
            }
            return new Result.Failure(
                    SkillMigrationFailure.of(SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION));
        }
        return new Result.Success(value.intValue());
    }

    private static <T> Optional<T> findField(DynamicOps<T> ops, MapLike<T> root) {
        var entries = root.entries().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            var key = ops.getStringValue(entry.getFirst());
            if (key.error().isEmpty() && "schema_version".equals(key.result().orElseThrow())) {
                return Optional.of(entry.getSecond());
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<BigInteger> exactInteger(T raw) {
        try {
            if (raw instanceof JsonPrimitive primitive && primitive.isNumber()) {
                return Optional.of(primitive.getAsBigDecimal().toBigIntegerExact());
            }
            if (raw instanceof ByteTag value) {
                return Optional.of(BigInteger.valueOf(value.getAsByte()));
            }
            if (raw instanceof ShortTag value) {
                return Optional.of(BigInteger.valueOf(value.getAsShort()));
            }
            if (raw instanceof IntTag value) {
                return Optional.of(BigInteger.valueOf(value.getAsInt()));
            }
            if (raw instanceof LongTag value) {
                return Optional.of(BigInteger.valueOf(value.getAsLong()));
            }
        } catch (ArithmeticException | NumberFormatException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        record Success(int schemaVersion) implements Result {
            public Success {
                if (schemaVersion < 0) {
                    throw new IllegalArgumentException("schemaVersion must be non-negative");
                }
            }
        }

        record Failure(SkillMigrationFailure failure) implements Result {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }
}
