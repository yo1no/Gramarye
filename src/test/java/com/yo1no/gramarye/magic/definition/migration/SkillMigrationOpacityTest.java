package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class SkillMigrationOpacityTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void oneShellOnlyStepIsRepresentationIndependentAndPreservesOpaqueSlots() throws Exception {
        var step = new ShellOnlyStep(0);
        var plan = new SkillMigrationPlan(List.of(step));
        var jsonPayload = new JsonObject();
        jsonPayload.addProperty("json_secret", "unchanged");
        var jsonRoot = jsonDocument(jsonPayload);
        var jsonRegistryOps = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var nbtPayload = new CompoundTag();
        nbtPayload.putString("nbt_secret", "unchanged");
        var nbtRoot = nbtDocument(nbtPayload);
        var nbtRegistryOps = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);
        var token = new CompoundTag();
        token.putInt("__gramarye_opaque_raw_token_v0", 7);
        var tokenRoot = nbtDocument(token);

        var plainJson = migrated(new Dynamic<>(JsonOps.INSTANCE, jsonRoot), plan);
        var wrappedJson = migrated(new Dynamic<>(jsonRegistryOps, jsonRoot), plan);
        var plainNbt = migrated(new Dynamic<>(NbtOps.INSTANCE, nbtRoot), plan);
        var wrappedNbt = migrated(new Dynamic<>(nbtRegistryOps, nbtRoot), plan);
        var tokenized = assertInstanceOf(
                OpaqueSkillDocumentMigrationFacade.Success.class,
                OpaqueSkillDocumentMigrationFacade.migrateTo(
                        encode(tokenRoot),
                        plan,
                        1));

        assertJsonShell(plainJson.snapshot(), jsonPayload);
        assertJsonShell(wrappedJson.snapshot(), jsonPayload);
        assertNbtShell(plainNbt.snapshot(), nbtPayload);
        assertNbtShell(wrappedNbt.snapshot(), nbtPayload);
        var migratedTokenRoot = decode(tokenized.migratedDocument().copyBytes());
        assertNbtShellValue(migratedTokenRoot, token);
        assertSame(JsonOps.INSTANCE, plainJson.snapshot().copyRawDocument().getOps());
        assertSame(jsonRegistryOps, wrappedJson.snapshot().copyRawDocument().getOps());
        assertSame(NbtOps.INSTANCE, plainNbt.snapshot().copyRawDocument().getOps());
        assertSame(nbtRegistryOps, wrappedNbt.snapshot().copyRawDocument().getOps());
        assertEquals(5, step.calls());
        assertEquals(plainJson.facts(), wrappedJson.facts());
        assertEquals(plainJson.facts(), plainNbt.facts());
        assertEquals(plainJson.facts(), wrappedNbt.facts());
        assertEquals(plainJson.facts(), tokenized.factReport());
    }

    @Test
    void directRawIngressAndP4TokenViewShareShellStepAndFacts() throws Exception {
        var step = new ShellOnlyStep(0);
        var plan = new SkillMigrationPlan(List.of(step));
        var sourceDocument = P3B2TestFixtures.document(
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 0, 1),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 2));
        var raw = SkillDocumentWriter.write(sourceDocument, JsonOps.INSTANCE)
                .result()
                .orElseThrow();
        var resolver = new SkillCandidateResolver(
                new P3B2TestFixtures.CountingTriggerLookup(),
                new P3B2TestFixtures.CountingActionLookup(),
                plan);

        var rawSuccess = assertInstanceOf(
                SkillResolutionResult.Success.class,
                resolver.resolveFromRawTo(new Dynamic<>(JsonOps.INSTANCE, raw), 1));
        var token = new CompoundTag();
        token.putInt("__gramarye_opaque_raw_token_v0", 0);
        var p4Success = assertInstanceOf(
                OpaqueSkillDocumentMigrationFacade.Success.class,
                OpaqueSkillDocumentMigrationFacade.migrateTo(
                        encode(nbtDocument(token)),
                        plan,
                        1));

        assertEquals(1, rawSuccess.candidate().skillSchemaVersion());
        assertEquals(1, decode(p4Success.migratedDocument().copyBytes()).getInt("schema_version"));
        assertEquals(rawSuccess.candidate().pipelineFacts(), p4Success.factReport());
        assertEquals(2, step.calls());
    }

    private static Migrated migrated(Dynamic<?> source, SkillMigrationPlan plan) {
        var snapshot = RawSkillDocumentSnapshot.of(source);
        var success = assertInstanceOf(
                SkillMigrationResult.Success.class,
                SkillDocumentMigrator.migrateTo(snapshot, plan, 1));
        return new Migrated(success.migratedSnapshot(), success.factReport());
    }

    private static void assertJsonShell(RawSkillDocumentSnapshot snapshot, JsonElement expectedPayload) {
        var root = assertInstanceOf(JsonObject.class, snapshot.copyRawDocument().getValue());
        var trigger = root.getAsJsonArray("nodes")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("trigger");
        assertEquals(1, root.get("schema_version").getAsInt());
        assertTrue(root.get("outer_migrated").getAsBoolean());
        assertEquals("future:opaque", trigger.get("type").getAsString());
        assertEquals(27, trigger.get("schema_version").getAsInt());
        assertEquals(expectedPayload, trigger.get("payload"));
    }

    private static void assertNbtShell(RawSkillDocumentSnapshot snapshot, CompoundTag expectedPayload) {
        assertNbtShellValue(
                assertInstanceOf(CompoundTag.class, snapshot.copyRawDocument().getValue()),
                expectedPayload);
    }

    private static void assertNbtShellValue(CompoundTag root, CompoundTag expectedPayload) {
        var nodes = assertInstanceOf(ListTag.class, root.get("nodes"));
        var node = assertInstanceOf(CompoundTag.class, nodes.get(0));
        var trigger = assertInstanceOf(CompoundTag.class, node.get("trigger"));
        assertEquals(1, root.getInt("schema_version"));
        assertTrue(root.getBoolean("outer_migrated"));
        assertEquals("future:opaque", trigger.getString("type"));
        assertEquals(27, trigger.getInt("schema_version"));
        assertEquals(expectedPayload, trigger.get("payload"));
    }

    private static JsonObject jsonDocument(JsonElement payload) {
        var trigger = new JsonObject();
        trigger.addProperty("type", "future:opaque");
        trigger.addProperty("schema_version", 27);
        trigger.add("payload", payload.deepCopy());
        var node = new JsonObject();
        node.add("trigger", trigger);
        var nodes = new JsonArray();
        nodes.add(node);
        var root = new JsonObject();
        root.addProperty("schema_version", 0);
        root.add("nodes", nodes);
        return root;
    }

    private static CompoundTag nbtDocument(CompoundTag payload) {
        var trigger = new CompoundTag();
        trigger.putString("type", "future:opaque");
        trigger.putInt("schema_version", 27);
        trigger.put("payload", payload.copy());
        var node = new CompoundTag();
        node.put("trigger", trigger);
        var nodes = new ListTag();
        nodes.add(node);
        var root = new CompoundTag();
        root.putInt("schema_version", 0);
        root.put("nodes", nodes);
        return root;
    }

    private static byte[] encode(CompoundTag root) throws Exception {
        var output = new ByteArrayOutputStream();
        var data = new DataOutputStream(output);
        NbtIo.writeAnyTag(root, data);
        data.flush();
        return output.toByteArray();
    }

    private static CompoundTag decode(byte[] bytes) throws Exception {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return assertInstanceOf(
                    CompoundTag.class,
                    NbtIo.readAnyTag(input, NbtAccounter.unlimitedHeap()));
        }
    }

    private record Migrated(
            RawSkillDocumentSnapshot snapshot,
            PipelineFactReport facts) {
    }

    private static final class ShellOnlyStep implements SkillMigrationStep {
        private final int fromVersion;
        private int calls;

        private ShellOnlyStep(int fromVersion) {
            this.fromVersion = fromVersion;
        }

        @Override
        public int fromVersion() {
            return fromVersion;
        }

        @Override
        public int toVersion() {
            return fromVersion + 1;
        }

        @Override
        public DataResult<SkillMigrationStepOutput> migrate(
                Dynamic<?> defensiveLogicalDocumentCopy) {
            calls++;
            var migrated = setBoolean(
                    withVersion(defensiveLogicalDocumentCopy, toVersion()),
                    "outer_migrated",
                    true);
            return DataResult.success(new SkillMigrationStepOutput(migrated));
        }

        private int calls() {
            return calls;
        }
    }

    private static Dynamic<?> withVersion(Dynamic<?> input, int version) {
        return withVersionCaptured(input, version);
    }

    private static <T> Dynamic<T> withVersionCaptured(Dynamic<T> input, int version) {
        return input.set(
                "schema_version",
                new Dynamic<>(input.getOps(), input.getOps().createInt(version)));
    }

    private static Dynamic<?> setBoolean(Dynamic<?> input, String field, boolean value) {
        return setBooleanCaptured(input, field, value);
    }

    private static <T> Dynamic<T> setBooleanCaptured(Dynamic<T> input, String field, boolean value) {
        return input.set(field, new Dynamic<>(input.getOps(), input.getOps().createBoolean(value)));
    }
}
