package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class SkillSchemaVersionProbeTest {
    @Test
    void acceptsJsonZeroAndExactIntegralFloatingValue() {
        assertEquals(0, success(jsonVersion(new JsonPrimitive(0))));
        assertEquals(2, success(jsonVersion(new JsonPrimitive(2.0))));
    }

    @Test
    void acceptsInRangeNbtLong() {
        var root = new CompoundTag();
        root.putLong("schema_version", Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, success(nbt(root)));
    }

    @Test
    void rejectsMissingNegativeFractionalStringAndJsonOverflow() {
        var missing = new JsonObject();
        var negative = jsonVersion(new JsonPrimitive(-1));
        var fractional = jsonVersion(new JsonPrimitive(2.5));
        var string = jsonVersion(new JsonPrimitive("2"));
        var overflow = jsonVersion(new JsonPrimitive(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)));

        assertFailure(missing, SkillMigrationFailure.Code.MISSING_SCHEMA_VERSION);
        assertFailure(negative, SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION);
        assertFailure(fractional, SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION);
        assertFailure(string, SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION);
        assertFailure(overflow, SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION);
    }

    @Test
    void rejectsNbtLongOverflowAndNonWhitelistedNumericTag() {
        var overflow = new CompoundTag();
        overflow.putLong("schema_version", (long) Integer.MAX_VALUE + 1L);
        var floating = new CompoundTag();
        floating.put("schema_version", FloatTag.valueOf(2.0F));

        assertFailure(nbtSnapshot(overflow), SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION);
        assertFailure(nbtSnapshot(floating), SkillMigrationFailure.Code.INVALID_SCHEMA_VERSION);
    }

    @Test
    void rejectsMalformedJsonAndNbtRootsWithoutProducingPartialSuccess() {
        var jsonFailure = SkillSchemaVersionProbe.probe(snapshot(new JsonArray()));
        var nbtFailure = SkillSchemaVersionProbe.probe(
                RawSkillDocumentSnapshot.of(new Dynamic<>(NbtOps.INSTANCE, new ListTag())));

        assertEquals(
                SkillMigrationFailure.Code.INVALID_ROOT,
                assertInstanceOf(SkillSchemaVersionProbe.Result.Failure.class, jsonFailure)
                        .failure().code());
        assertEquals(
                SkillMigrationFailure.Code.INVALID_ROOT,
                assertInstanceOf(SkillSchemaVersionProbe.Result.Failure.class, nbtFailure)
                        .failure().code());
    }

    private static int success(JsonObject root) {
        return success(snapshot(root));
    }

    private static int success(RawSkillDocumentSnapshot snapshot) {
        return assertInstanceOf(
                        SkillSchemaVersionProbe.Result.Success.class,
                        SkillSchemaVersionProbe.probe(snapshot))
                .schemaVersion();
    }

    private static RawSkillDocumentSnapshot nbt(CompoundTag root) {
        return nbtSnapshot(root);
    }

    private static JsonObject jsonVersion(JsonPrimitive version) {
        var root = new JsonObject();
        root.add("schema_version", version);
        return root;
    }

    private static RawSkillDocumentSnapshot snapshot(com.google.gson.JsonElement root) {
        return RawSkillDocumentSnapshot.of(new Dynamic<>(JsonOps.INSTANCE, root));
    }

    private static RawSkillDocumentSnapshot nbtSnapshot(CompoundTag root) {
        return RawSkillDocumentSnapshot.of(new Dynamic<>(NbtOps.INSTANCE, root));
    }

    private static void assertFailure(JsonObject root, SkillMigrationFailure.Code code) {
        assertFailure(snapshot(root), code);
    }

    private static void assertFailure(RawSkillDocumentSnapshot snapshot, SkillMigrationFailure.Code code) {
        var failed = assertInstanceOf(
                SkillSchemaVersionProbe.Result.Failure.class,
                SkillSchemaVersionProbe.probe(snapshot));
        assertEquals(code, failed.failure().code());
    }
}
