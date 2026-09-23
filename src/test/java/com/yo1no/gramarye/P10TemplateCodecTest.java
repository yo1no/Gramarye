package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class P10TemplateCodecTest {
    private static final String RESOURCE = "/data/gramarye/gramarye/skill_templates/starter_bolt_v0.json";
    private static final SkillId SKILL = new SkillId(UUID.fromString("00000000-0000-0000-0000-000000000068"));

    @Test
    void fixedProductionResourceIsIdentityFreeCurrentSchemaA() throws IOException {
        var bytes = builtInBytes();
        var decoded = accepted(bytes);
        assertEquals("gramarye:starter_bolt_v0", P10TemplateCodec.TEMPLATE_ID.toString());
        assertEquals("gramarye:gramarye/skill_templates/starter_bolt_v0.json",
                P10TemplateCodec.RESOURCE_ID.toString());
        assertEquals(bytes.length, decoded.rawBytes());
        assertEquals(2, decoded.body().nodes().size());
        assertInstanceOf(AppearanceDocument.Default.class, decoded.body().appearance());
        var damage = decoded.body().nodes().get(1).action();
        assertEquals("gramarye:damage", damage.typeId().toString());
        assertEquals(1, damage.schemaVersion());
        assertEquals(JsonParser.parseString("{\"magnitude\":4000,\"mana_cost\":0}"),
                damage.copyRawPayload().getValue());
        assertFalse(decoded.toString().contains("magnitude"));
    }

    @Test
    void materializationSuppliesIdentityAndPresentSlotsWithoutChangingBody() throws IOException {
        var body = accepted(builtInBytes()).body();
        var draft = body.materialize(SKILL, Optional.of(new SkillRevision(9)));
        assertEquals(SKILL, draft.skillId());
        assertEquals(Optional.of(new SkillRevision(9)), draft.baseRevision());
        for (var index = 0; index < body.nodes().size(); index++) {
            assertSame(body.nodes().get(index).trigger(),
                    assertInstanceOf(DraftTriggerSlot.Present.class, draft.nodes().get(index).trigger()).definition());
            assertSame(body.nodes().get(index).action(),
                    assertInstanceOf(DraftActionSlot.Present.class, draft.nodes().get(index).action()).definition());
        }
        assertEquals(body.nodes(), body.document(SKILL, new SkillRevision(10)).nodes());
        assertThrows(UnsupportedOperationException.class, () -> body.nodes().clear());
        var rawCopy = (JsonObject) body.nodes().get(1).action().copyRawPayload().getValue();
        rawCopy.addProperty("magnitude", 6000);
        assertEquals(4000, ((JsonObject) body.nodes().get(1).action().copyRawPayload().getValue())
                .get("magnitude").getAsInt());
    }

    @Test
    void payloadMeaningIsLeftToTheProductionResolver() throws IOException {
        for (var schema : new int[] {0, 1, 2}) {
            for (var magnitude : new int[] {4000, 4001, 5000}) {
                var root = builtIn();
                var action = root.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("action");
                action.addProperty("schema_version", schema);
                action.getAsJsonObject("payload").addProperty("magnitude", magnitude);
                assertEquals(schema, accepted(root).body().nodes().get(1).action().schemaVersion());
            }
        }
        var unknown = builtIn();
        firstTrigger(unknown).addProperty("type", "other:unknown");
        firstTrigger(unknown).add("payload", JsonParser.parseString(
                "{\"z\":[true,null,1e+03],\"a\":{\"unknown\":\"kept\"}}"));
        var body = accepted(unknown).body();
        var envelope = body.nodes().getFirst().trigger();
        assertEquals(firstTrigger(unknown).get("payload"), envelope.copyRawPayload().getValue());
        var encoded = SkillDocument.CODEC.encodeStart(JsonOps.INSTANCE,
                body.document(SKILL, new SkillRevision(0))).getOrThrow();
        assertEquals(firstTrigger(unknown).get("payload"), encoded.getAsJsonObject()
                .getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("trigger").get("payload"));
    }

    @Test
    void rawBytesAcceptLimitRejectLimitPlusOneAndNeverReadBeyondSentinel() throws IOException {
        var fixture = new String(builtInBytes(), StandardCharsets.UTF_8);
        var exact = fixture + " ".repeat(P10TemplateCodec.MAX_RAW_BYTES - fixture.length());
        assertEquals(P10TemplateCodec.MAX_RAW_BYTES, accepted(exact).rawBytes());
        rejected(exact + " ", P10TemplateCodec.DecodeReason.RAW_BYTES);
        var input = new CountingInput((exact + " ".repeat(1000)).getBytes(StandardCharsets.UTF_8));
        assertInstanceOf(P10TemplateCodec.Rejected.class, P10TemplateCodec.decode(input));
        assertEquals(P10TemplateCodec.MAX_RAW_BYTES + 1, input.count);
        assertFalse(input.closed);
    }

    @Test
    void depthIsCheckedBeforeConstructingNextValueAtTheWholeTemplateCoordinate() throws IOException {
        var exact = builtIn();
        firstTrigger(exact).add("payload", nested(27)); // payload root 5, leaf 32
        accepted(exact);
        firstTrigger(exact).add("payload", nested(28));
        rejected(exact, P10TemplateCodec.DecodeReason.DEPTH);
    }

    @Test
    void treeValueBudgetIsSharedAcrossAllNodesAndCheckedBeforeLimitPlusOne() throws IOException {
        var root = builtIn();
        var baseline = accepted(root).treeValues();
        var payload = new JsonArray();
        for (var index = 0; index < P10TemplateCodec.MAX_TREE_VALUES - baseline; index++) {
            payload.add(0);
        }
        firstTrigger(root).add("payload", payload);
        assertEquals(P10TemplateCodec.MAX_TREE_VALUES, accepted(root).treeValues());
        payload.add(0);
        rejected(root, P10TemplateCodec.DecodeReason.TREE_VALUES);
    }

    @Test
    void stringsAndKeysUseDecodedJavaUtf16LengthAndStopBeforeNextUnit() throws IOException {
        var root = builtIn();
        firstTrigger(root).add("payload", new JsonPrimitive("a".repeat(256)));
        accepted(root);
        firstTrigger(root).add("payload", new JsonPrimitive("a".repeat(257)));
        rejected(root, P10TemplateCodec.DecodeReason.STRING_LENGTH);
        firstTrigger(root).add("payload", new JsonPrimitive("😀".repeat(128)));
        accepted(root);
        firstTrigger(root).add("payload", new JsonPrimitive("😀".repeat(129)));
        rejected(root, P10TemplateCodec.DecodeReason.STRING_LENGTH);
        var payload = new JsonObject();
        payload.addProperty("a".repeat(256), 1);
        firstTrigger(root).add("payload", payload);
        accepted(root);
        payload.addProperty("b".repeat(257), 1);
        rejected(root, P10TemplateCodec.DecodeReason.STRING_LENGTH);
        var escaped = new String(builtInBytes(), StandardCharsets.UTF_8)
                .replace("\"payload\": {}", "\"payload\": \"" + "\\u0061".repeat(256) + "\"");
        accepted(escaped);
        rejected(escaped.replace("\\u0061\"", "\\u0061\\u0061\""), P10TemplateCodec.DecodeReason.STRING_LENGTH);
    }

    @Test
    void canonicalPayloadBytesAcceptLimitAndRejectLimitPlusOneWithoutRawLengthSubstitution() throws IOException {
        var root = builtIn();
        var exact = payloadOfSize(P10TemplateCodec.MAX_PAYLOAD_BYTES);
        firstTrigger(root).add("payload", exact);
        accepted(root);
        firstTrigger(root).add("payload", payloadOfSize(P10TemplateCodec.MAX_PAYLOAD_BYTES + 1));
        rejected(root, P10TemplateCodec.DecodeReason.PAYLOAD_BYTES);

        // JSON whitespace is stream data, not canonical payload bytes.
        firstTrigger(root).add("payload", new JsonObject());
        var padded = root.toString().replace("\"payload\":{}", "\"payload\":{"
                + " ".repeat(P10TemplateCodec.MAX_PAYLOAD_BYTES + 1) + "}");
        accepted(padded);
    }

    @Test
    void canonicalPayloadUsesNonLenientSerializeNullStrictUtf8AndOriginalNumberSpelling() throws IOException {
        var root = builtIn();
        firstTrigger(root).add("payload", JsonParser.parseString(
                "{\"z\":null,\"a\":[1.00,1e+03,\"\\u0000\",\"é\"]}"));
        var payload = accepted(root).body().nodes().getFirst().trigger().copyRawPayload().getValue();
        assertEquals("1.00", ((JsonObject) payload).getAsJsonArray("a").get(0).getAsNumber().toString());
        assertEquals("1e+03", ((JsonObject) payload).getAsJsonArray("a").get(1).getAsNumber().toString());
        assertTrue(((JsonObject) payload).get("z").isJsonNull());
    }

    @Test
    void malformedEncodingBomTrailingDuplicatesAndNonJsonLexemesAreRejected() throws IOException {
        var json = new String(builtInBytes(), StandardCharsets.UTF_8);
        rejected("\ufeff" + json, P10TemplateCodec.DecodeReason.UTF8);
        rejected(json + " {}", P10TemplateCodec.DecodeReason.JSON);
        rejected(json.replace("\"appearance\": {}", "\"appearance\": {},\"appearance\":{}"),
                P10TemplateCodec.DecodeReason.DUPLICATE_KEY);
        rejected(json.replace("\"payload\": {}", "\"payload\": {\"a\":1,\"a\":2}"),
                P10TemplateCodec.DecodeReason.DUPLICATE_KEY);
        for (var bad : new String[] {"NaN", "Infinity", "01", "-01", "1.", "1e", "+1", "[1,]", "{'x':1}"}) {
            rejected(json.replace("\"payload\": {}", "\"payload\": " + bad), P10TemplateCodec.DecodeReason.JSON);
        }
        rejected(json.replace("\"payload\": {}", "\"payload\": \"\\uD800\""), P10TemplateCodec.DecodeReason.UTF8);
        var invalid = new byte[] {(byte) 0xc3, 0x28};
        assertEquals(P10TemplateCodec.DecodeReason.UTF8,
                assertInstanceOf(P10TemplateCodec.Rejected.class,
                        P10TemplateCodec.decode(new ByteArrayInputStream(invalid))).reason());
    }

    @Test
    void allIdentityOuterNodeAndEnvelopeKeysAreClosedAndRequired() throws IOException {
        for (var field : new String[] {"schema_version", "template_id", "owner", "skill_id", "revision",
                "base_revision", "player", "session", "generation"}) {
            var root = builtIn();
            root.addProperty(field, 0);
            rejected(root, P10TemplateCodec.DecodeReason.SHELL);
        }
        for (var field : new String[] {"nodes", "appearance"}) {
            var root = builtIn();
            root.remove(field);
            rejected(root, P10TemplateCodec.DecodeReason.SHELL);
            root = builtIn();
            root.add(field, com.google.gson.JsonNull.INSTANCE);
            rejected(root, P10TemplateCodec.DecodeReason.SHELL);
        }
        var root = builtIn();
        root.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("identity", 1);
        rejected(root, P10TemplateCodec.DecodeReason.SHELL);
        for (var field : new String[] {"type", "schema_version", "payload"}) {
            root = builtIn();
            firstTrigger(root).remove(field);
            rejected(root, P10TemplateCodec.DecodeReason.SHELL);
            root = builtIn();
            firstTrigger(root).add(field, com.google.gson.JsonNull.INSTANCE);
            rejected(root, P10TemplateCodec.DecodeReason.SHELL);
        }
        root = builtIn();
        firstTrigger(root).addProperty("extra", true);
        rejected(root, P10TemplateCodec.DecodeReason.SHELL);
        for (var bad : new JsonElement[] {new JsonPrimitive(-1), new JsonPrimitive(1.5),
                new JsonPrimitive("1"), new JsonPrimitive(2147483648L)}) {
            root = builtIn();
            firstTrigger(root).add("schema_version", bad);
            rejected(root, P10TemplateCodec.DecodeReason.SHELL);
        }
    }

    @Test
    void genericNodeAdmissionRemainsDistinctFromExactTwoNodeProductSemantics() throws IOException {
        var root = builtIn();
        var source = root.getAsJsonArray("nodes").get(0);
        var nodes = new JsonArray();
        for (var index = 0; index < P10TemplateCodec.MAX_NODES; index++) {
            nodes.add(source.deepCopy());
        }
        root.add("nodes", nodes);
        assertEquals(P10TemplateCodec.MAX_NODES, accepted(root).body().nodes().size());
        nodes.add(source.deepCopy());
        rejected(root, P10TemplateCodec.DecodeReason.NODE_LIMIT);
        root.add("nodes", new JsonArray());
        assertEquals(0, accepted(root).body().nodes().size());
    }

    @Test
    void topAppearanceUsesExistingStrictCanonicalRulesWithoutTolerantFallback() throws IOException {
        var root = builtIn();
        root.add("appearance", JsonParser.parseString(
                "{\"primary_argb\":\"0xFFAABBCC\",\"sound_profile\":{\"mode\":\"disabled\"},\"intensity_milli\":1000}"));
        assertInstanceOf(AppearanceDocument.Decoded.class, accepted(root).body().appearance());
        for (var malformed : new String[] {"{\"unknown\":1}", "{\"primary_argb\":null}",
                "{\"primary_argb\":\"#FFAABBCC\"}", "{\"intensity_milli\":1.5}",
                "{\"sound_profile\":{\"mode\":\"disabled\",\"extra\":1}}",
                "{\"sound_profile\":{\"mode\":\"specified\"}}", "[]", "0"}) {
            root.add("appearance", JsonParser.parseString(malformed));
            rejected(root, P10TemplateCodec.DecodeReason.CANONICAL_APPEARANCE);
        }
    }

    @Test
    void overrideNoneRequiresOmissionAndMalformedOverridesNeverBecomeFallback() throws IOException {
        var root = builtIn();
        assertInstanceOf(AppearanceOverrideDocument.None.class,
                accepted(root).body().nodes().getFirst().appearanceOverride());
        var first = root.getAsJsonArray("nodes").get(0).getAsJsonObject();
        first.add("appearance_override", JsonParser.parseString("{\"intensity_milli\":1000}"));
        assertInstanceOf(AppearanceOverrideDocument.Decoded.class,
                accepted(root).body().nodes().getFirst().appearanceOverride());
        first.add("appearance_override", com.google.gson.JsonNull.INSTANCE);
        rejected(root, P10TemplateCodec.DecodeReason.SHELL);
        for (var malformed : new String[] {"{}", "[]", "{\"unknown\":1}",
                "{\"intensity_milli\":null}", "{\"particle_profile\":null}"}) {
            first.add("appearance_override", JsonParser.parseString(malformed));
            rejected(root, P10TemplateCodec.DecodeReason.CANONICAL_NODE);
        }
    }

    @Test
    void appearanceSubtreeBudgetsRemainSubjectToLocationEffectiveWholeDepth() throws IOException {
        var root = builtIn();
        root.add("appearance", nested(30)); // root 2, leaf 32; structurally bounded, canonically invalid
        rejected(root, P10TemplateCodec.DecodeReason.CANONICAL_APPEARANCE);
        root.add("appearance", nested(31));
        rejected(root, P10TemplateCodec.DecodeReason.DEPTH);
        root = builtIn();
        root.getAsJsonArray("nodes").get(0).getAsJsonObject().add("appearance_override", nested(28));
        rejected(root, P10TemplateCodec.DecodeReason.CANONICAL_NODE);
        root.getAsJsonArray("nodes").get(0).getAsJsonObject().add("appearance_override", nested(29));
        rejected(root, P10TemplateCodec.DecodeReason.DEPTH);
        root = builtIn();
        var values = new JsonArray();
        for (var index = 0; index < 1024; index++) {
            values.add(0);
        }
        root.add("appearance", values);
        rejected(root, P10TemplateCodec.DecodeReason.CANONICAL_APPEARANCE);
    }

    @Test
    void resourceIoFaultPropagatesSameObjectAndCodecDoesNotCloseCallerStream() throws IOException {
        var failure = new IOException("owned source failure");
        var input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IOException.class, () -> P10TemplateCodec.decode(input)));
        var normal = new CountingInput(builtInBytes());
        assertInstanceOf(P10TemplateCodec.Decoded.class, P10TemplateCodec.decode(normal));
        assertFalse(normal.closed);
        assertNotSame(accepted(builtInBytes()).body(), accepted(builtInBytes()).body());
    }

    private static JsonElement nested(int arrayCount) {
        JsonElement value = new JsonPrimitive(0);
        for (var index = 0; index < arrayCount; index++) {
            var array = new JsonArray();
            array.add(value);
            value = array;
        }
        return value;
    }

    private static JsonArray payloadOfSize(int size) {
        var result = new JsonArray();
        var used = 2;
        while (size - used > 259) {
            result.add("a".repeat(256));
            used += result.size() == 1 ? 258 : 259;
        }
        var remaining = size - used;
        if (remaining > 0) {
            result.add("a".repeat(remaining - (result.size() == 0 ? 2 : 3)));
        }
        assertEquals(size, result.toString().getBytes(StandardCharsets.UTF_8).length);
        return result;
    }

    private static JsonObject firstTrigger(JsonObject root) {
        return root.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("trigger");
    }

    private static byte[] builtInBytes() throws IOException {
        try (var resource = P10TemplateCodecTest.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) {
                throw new IOException("Missing fixed production template resource");
            }
            return resource.readAllBytes();
        }
    }

    private static JsonObject builtIn() throws IOException {
        return JsonParser.parseString(new String(builtInBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static P10TemplateCodec.Decoded accepted(JsonElement json) throws IOException {
        return accepted(json.toString());
    }

    private static P10TemplateCodec.Decoded accepted(String json) throws IOException {
        return accepted(json.getBytes(StandardCharsets.UTF_8));
    }

    private static P10TemplateCodec.Decoded accepted(byte[] bytes) throws IOException {
        return assertInstanceOf(P10TemplateCodec.Decoded.class,
                P10TemplateCodec.decode(new ByteArrayInputStream(bytes)));
    }

    private static void rejected(JsonElement json, P10TemplateCodec.DecodeReason reason) throws IOException {
        rejected(json.toString(), reason);
    }

    private static void rejected(String json, P10TemplateCodec.DecodeReason reason) throws IOException {
        assertEquals(reason, assertInstanceOf(P10TemplateCodec.Rejected.class,
                P10TemplateCodec.decode(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))).reason());
    }

    private static final class CountingInput extends InputStream {
        private final ByteArrayInputStream delegate;
        private int count;
        private boolean closed;

        private CountingInput(byte[] bytes) {
            delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            var value = delegate.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            var actual = delegate.read(bytes, offset, length);
            if (actual > 0) {
                count += actual;
            }
            return actual;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
