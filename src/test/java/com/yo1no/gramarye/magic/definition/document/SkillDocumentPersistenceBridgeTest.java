package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SkillDocumentPersistenceBridgeTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void mixedFamilyDocumentRoundTripPreservesEveryRawTreeAndContext() {
        var triggerJson = new JsonObject();
        triggerJson.addProperty("lexical", new java.math.BigDecimal("1.00"));
        triggerJson.add("nested", jsonObject("value", new JsonPrimitive(7)));

        var actionNbt = new CompoundTag();
        actionNbt.putByte("kind", (byte) 3);
        var actionNested = new CompoundTag();
        actionNested.putIntArray("values", new int[] {1, 2, 3});
        actionNbt.put("nested", actionNested);

        var topNbt = new CompoundTag();
        topNbt.putString("unknown_top", "preserve");
        var topList = new ListTag();
        topList.add(IntTag.valueOf(4));
        topList.add(IntTag.valueOf(5));
        topNbt.put("items", topList);

        var overrideJson = new JsonObject();
        overrideJson.addProperty("unknown_override", "preserve");
        overrideJson.add("nested", jsonObject("flag", new JsonPrimitive(true)));
        var overrideOps = RegistryOps.create(JsonOps.COMPRESSED, EMPTY_PROVIDER);

        var firstNode = new NodeDocument(
                envelope("unknown_trigger", new Dynamic<>(JsonOps.INSTANCE, triggerJson)),
                envelope("unknown_action", new Dynamic<>(NbtOps.INSTANCE, actionNbt)),
                new AppearanceOverrideDocument.Unparsed(AppearanceRawSnapshot.capture(
                        new Dynamic<>(overrideOps, overrideJson)).getOrThrow()));
        var secondNode = new NodeDocument(
                envelope("second_trigger", new Dynamic<>(NbtOps.INSTANCE, ByteTag.valueOf((byte) 9))),
                envelope("second_action", new Dynamic<>(JsonOps.COMPRESSED, new JsonArray())),
                AppearanceOverrideDocument.none());
        var document = new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(27),
                List.of(firstNode, secondNode),
                new AppearanceDocument.Unparsed(AppearanceRawSnapshot.capture(
                        new Dynamic<>(NbtOps.INSTANCE, topNbt)).getOrThrow()));

        var encoded = success(SkillDocumentPersistenceBridge.encodeCurrent(document));
        var physical = decodePhysical(encoded);
        assertEveryPayloadIsFamilyTagged(physical);

        var hydrated = success(SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                encoded, Optional.of(EMPTY_PROVIDER)));

        assertEquals(document.schemaVersion(), hydrated.schemaVersion());
        assertEquals(document.skillId(), hydrated.skillId());
        assertEquals(document.revision(), hydrated.revision());
        assertEquals(2, hydrated.nodes().size());
        assertEquals(ResourceLocation.fromNamespaceAndPath("unregistered", "unknown_trigger"),
                hydrated.nodes().get(0).trigger().typeId());
        assertEquals(ResourceLocation.fromNamespaceAndPath("unregistered", "second_trigger"),
                hydrated.nodes().get(1).trigger().typeId());

        assertRawTree(
                triggerJson,
                hydrated.nodes().get(0).trigger().copyRawPayload(),
                new SerializedTreeContext(SerializedTreeFamily.JSON, false, false));
        assertRawTree(
                actionNbt,
                hydrated.nodes().get(0).action().copyRawPayload(),
                new SerializedTreeContext(SerializedTreeFamily.NBT, false, false));
        assertRawTree(
                topNbt,
                assertInstanceOf(AppearanceDocument.Unparsed.class, hydrated.appearance())
                        .copyRawAppearance(),
                new SerializedTreeContext(SerializedTreeFamily.NBT, false, false));
        assertRawTree(
                overrideJson,
                assertInstanceOf(
                                AppearanceOverrideDocument.Unparsed.class,
                                hydrated.nodes().get(0).appearanceOverride())
                        .copyRawAppearance(),
                new SerializedTreeContext(SerializedTreeFamily.JSON, true, true));
    }

    @Test
    void decodedAppearanceDelegatesCanonicalShapeAndRoundTrips() {
        var definition = new AppearanceDefinition(
                OptionalInt.of(0xFF010203),
                OptionalInt.empty(),
                ProfileSelection.specified(ResourceLocation.fromNamespaceAndPath("test", "sound")),
                ProfileSelection.disabled(),
                ProfileSelection.inherit(),
                OptionalInt.of(1_000));
        var override = new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.of(0x7F112233),
                ProfileSelection.inherit(),
                ProfileSelection.inherit(),
                ProfileSelection.specified(ResourceLocation.fromNamespaceAndPath("test", "trail")),
                OptionalInt.of(0));
        var node = new NodeDocument(
                DocumentTestFixtures.envelope("trigger"),
                DocumentTestFixtures.envelope("action"),
                new AppearanceOverrideDocument.Decoded(override));
        var document = new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(3),
                List.of(node),
                new AppearanceDocument.Decoded(definition));

        var encoded = success(SkillDocumentPersistenceBridge.encodeCurrent(document));
        var physical = decodePhysical(encoded);
        var topWrapper = assertInstanceOf(CompoundTag.class, physical.get("appearance"));
        assertEquals("decoded", topWrapper.getString("state"));
        var topValue = assertInstanceOf(CompoundTag.class, topWrapper.get("value"));
        assertEquals("0xFF010203", topValue.getString("primary_argb"));
        assertEquals(1_000, topValue.getInt("intensity_milli"));
        var physicalDto = success(PhysicalSkillDocumentNbt.decode(physical));
        assertFalse(physicalDto.toString().contains(document.skillId().value().toString()));
        assertFalse(physicalDto.appearance().toString().contains("test:sound"));
        assertFalse(physicalDto.nodes().get(0).appearanceOverride().toString().contains("test:trail"));
        assertFalse(physicalDto.nodes().get(0).trigger().toString().contains("test:trigger"));

        var hydrated = success(SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                encoded, Optional.empty()));
        assertEquals(document, hydrated);
    }

    @Test
    void rejectedAppearanceFallsBackWithoutPersistingReasonOrOverrideField() {
        var node = new NodeDocument(
                DocumentTestFixtures.envelope("trigger"),
                DocumentTestFixtures.envelope("action"),
                new AppearanceOverrideDocument.Rejected(
                        AppearanceRejectionCode.NODE_LIMIT_EXCEEDED));
        var document = new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(4),
                List.of(node),
                new AppearanceDocument.Rejected(AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED));

        var encoded = success(SkillDocumentPersistenceBridge.encodeCurrent(document));
        var physical = decodePhysical(encoded);
        var top = assertInstanceOf(CompoundTag.class, physical.get("appearance"));
        assertEquals("default", top.getString("state"));
        var nodes = assertInstanceOf(ListTag.class, physical.get("nodes"));
        var physicalNode = assertInstanceOf(CompoundTag.class, nodes.get(0));
        assertFalse(physicalNode.contains("appearance_override"));
        var bytes = encoded.copyBytes();
        assertFalse(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("DEPTH_LIMIT_EXCEEDED"));
        assertFalse(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("NODE_LIMIT_EXCEEDED"));

        var hydrated = success(SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                encoded, Optional.empty()));
        assertInstanceOf(AppearanceDocument.Default.class, hydrated.appearance());
        assertInstanceOf(
                AppearanceOverrideDocument.None.class,
                hydrated.nodes().get(0).appearanceOverride());
    }

    @Test
    void registryContextRequiresProviderAtHydrationBoundary() {
        var ops = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var document = oneNodeDocument(
                new Dynamic<>(ops, jsonObject("value", new JsonPrimitive(1))),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var encoded = success(SkillDocumentPersistenceBridge.encodeCurrent(document));

        var failure = failure(SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                encoded, Optional.empty()));
        var unavailable = assertInstanceOf(
                SkillDocumentPersistenceFailure.RegistryContextUnavailable.class, failure);
        assertEquals(new SkillDocumentPersistenceLocation.TriggerPayload(0), unavailable.location());
    }

    @Test
    void wholeDocumentSharedNodeBudgetAcceptsExactMaximumAndRejectsNextNode() {
        var exactPayload = flatJsonArray(65_521);
        var exact = oneNodeDocument(
                new Dynamic<>(JsonOps.INSTANCE, exactPayload),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var exactEncoded = success(SkillDocumentPersistenceBridge.encodeCurrent(exact));

        var physical = decodePhysical(exactEncoded);
        var nodes = assertInstanceOf(ListTag.class, physical.get("nodes"));
        var trigger = assertInstanceOf(
                CompoundTag.class,
                assertInstanceOf(CompoundTag.class, nodes.get(0)).get("trigger"));
        var payload = assertInstanceOf(CompoundTag.class, trigger.get("payload"));
        var rawBytes = assertInstanceOf(ByteArrayTag.class, payload.get("data"));
        assertTrue(rawBytes.size() > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES);

        var over = oneNodeDocument(
                new Dynamic<>(JsonOps.INSTANCE, flatJsonArray(65_522)),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var overFailure = assertInstanceOf(
                SkillDocumentPersistenceFailure.DocumentBoundsExceeded.class,
                failure(SkillDocumentPersistenceBridge.encodeCurrent(over)));
        assertEquals(
                SkillDocumentPersistenceFailure.DocumentBoundKind.NODE_COUNT,
                overFailure.kind());
    }

    @Test
    void wholeDocumentSharedDepthUsesLogicalPayloadInsertionDepth() {
        var exact = oneNodeDocument(
                new Dynamic<>(JsonOps.INSTANCE, nestedJsonDepth(60)),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        assertTrue(SkillDocumentPersistenceBridge.encodeCurrent(exact).successValue().isPresent());

        var over = oneNodeDocument(
                new Dynamic<>(JsonOps.INSTANCE, nestedJsonDepth(61)),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var bounds = assertInstanceOf(
                SkillDocumentPersistenceFailure.DocumentBoundsExceeded.class,
                failure(SkillDocumentPersistenceBridge.encodeCurrent(over)));
        assertEquals(SkillDocumentPersistenceFailure.DocumentBoundKind.DEPTH, bounds.kind());
    }

    @Test
    void rawNbtByteArrayElementsShareTheLogicalDocumentNodeBudget() {
        var exact = oneNodeDocument(
                new Dynamic<>(NbtOps.INSTANCE, new ByteArrayTag(new byte[65_521])),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var exactEncoded = success(SkillDocumentPersistenceBridge.encodeCurrent(exact));
        assertTrue(SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                exactEncoded, Optional.empty()).successValue().isPresent());

        var over = oneNodeDocument(
                new Dynamic<>(NbtOps.INSTANCE, new ByteArrayTag(new byte[65_522])),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var bounds = assertInstanceOf(
                SkillDocumentPersistenceFailure.DocumentBoundsExceeded.class,
                failure(SkillDocumentPersistenceBridge.encodeCurrent(over)));
        assertEquals(SkillDocumentPersistenceFailure.DocumentBoundKind.NODE_COUNT, bounds.kind());
    }

    @Test
    void physicalDocumentEncodingIsDeterministic() {
        var trigger = jsonObject("z", new JsonPrimitive(1));
        trigger.add("a", jsonObject("nested", new JsonPrimitive("value")));
        var action = new CompoundTag();
        action.putLong("value", 42L);
        var document = oneNodeDocument(
                new Dynamic<>(JsonOps.INSTANCE, trigger),
                new Dynamic<>(NbtOps.INSTANCE, action));

        var first = success(SkillDocumentPersistenceBridge.encodeCurrent(document));
        var second = success(SkillDocumentPersistenceBridge.encodeCurrent(document));

        assertArrayEquals(first.copyBytes(), second.copyBytes());
    }

    @Test
    void malformedPhysicalFieldsRootAndEmptyNodesAreRejectedWithoutPartialDocument()
            throws IOException {
        var encoded = success(SkillDocumentPersistenceBridge.encodeCurrent(DocumentTestFixtures.document(
                AppearanceDocument.defaultAppearance())));
        var root = decodePhysical(encoded);

        var extraField = root.copy();
        extraField.putInt("unexpected", 1);
        assertMalformedNoPartial(encodePhysical(extraField));

        var wrongType = root.copy();
        wrongType.putString("revision", "0");
        assertMalformedNoPartial(encodePhysical(wrongType));

        var emptyNodes = root.copy();
        emptyNodes.put("nodes", new ListTag());
        assertMalformedNoPartial(encodePhysical(emptyNodes));

        var primitiveRoot = StrictNbtTreeCodec.encode(
                IntTag.valueOf(7), MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
        assertMalformedNoPartial(primitiveRoot);
    }

    @Test
    void noncurrentSchemaAndEmptyDomainNodesFailWithTypedResults() throws IOException {
        var noncurrentDomain = new SkillDocument(
                5,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(0),
                List.of(DocumentTestFixtures.node()),
                AppearanceDocument.defaultAppearance());
        assertInstanceOf(
                SkillDocumentPersistenceFailure.UnsupportedDocumentSchema.class,
                failure(SkillDocumentPersistenceBridge.encodeCurrent(noncurrentDomain)));

        var empty = new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(0),
                List.of(),
                AppearanceDocument.defaultAppearance());
        assertInstanceOf(
                SkillDocumentPersistenceFailure.EncodeFailed.class,
                failure(SkillDocumentPersistenceBridge.encodeCurrent(empty)));

        var current = success(SkillDocumentPersistenceBridge.encodeCurrent(
                DocumentTestFixtures.document(AppearanceDocument.defaultAppearance())));
        var physical = decodePhysical(current);
        physical.putInt("schema_version", 5);
        var hydrated = SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                encodePhysical(physical), Optional.empty());
        assertTrue(hydrated.successValue().isEmpty());
        assertInstanceOf(
                SkillDocumentPersistenceFailure.UnsupportedDocumentSchema.class,
                hydrated.failureValue().orElseThrow());
    }

    @Test
    void documentIngressChecksMaximumBeforeParsingAndRetainsNoPartial() {
        var exactLengthGarbage = ImmutableEncodedBytes.copyOf(
                new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES]);
        var exact = SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                exactLengthGarbage, Optional.empty());
        assertTrue(exact.successValue().isEmpty());
        assertInstanceOf(
                SkillDocumentPersistenceFailure.MalformedPhysicalDocument.class,
                exact.failureValue().orElseThrow());

        var overLengthGarbage = ImmutableEncodedBytes.copyOf(
                new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1]);
        var over = SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                overLengthGarbage, Optional.empty());
        assertTrue(over.successValue().isEmpty());
        var capacity = assertInstanceOf(
                SkillDocumentPersistenceFailure.DocumentEncodedCapacityExceeded.class,
                over.failureValue().orElseThrow());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES, capacity.maximum());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1L, capacity.observedAtLeast());
    }

    @Test
    void documentEncodingAllowsExactMaximumAndRejectsMaximumPlusOne() {
        var baseFirstLength = 150_000;
        var otherLength = 262_000;
        var base = success(SkillDocumentPersistenceBridge.encodeCurrent(
                fourStringPayloadDocument(baseFirstLength, otherLength)));
        var exactFirstLength = baseFirstLength
                + MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES
                - base.size();
        assertTrue(exactFirstLength < MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES - 2);

        var exact = success(SkillDocumentPersistenceBridge.encodeCurrent(
                fourStringPayloadDocument(exactFirstLength, otherLength)));
        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES, exact.size());

        var over = SkillDocumentPersistenceBridge.encodeCurrent(
                fourStringPayloadDocument(exactFirstLength + 1, otherLength));
        assertTrue(over.successValue().isEmpty());
        var capacity = assertInstanceOf(
                SkillDocumentPersistenceFailure.DocumentEncodedCapacityExceeded.class,
                over.failureValue().orElseThrow());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES, capacity.maximum());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1L, capacity.observedAtLeast());
    }

    @Test
    void internalCodecDiagnosticKeepsOnlyBoundedClassName() {
        var secret = "DO_NOT_RETAIN_THIS_MESSAGE";
        var failure = SkillDocumentPersistenceFailure.InternalCodecException.from(
                SkillDocumentPersistenceLocation.DocumentRoot.INSTANCE,
                new IllegalStateException(secret));

        assertEquals(IllegalStateException.class.getName(), failure.exceptionClassName());
        assertFalse(failure.toString().contains(secret));
        assertTrue(failure.exceptionClassName().length() <= MagicSafetyCeilings.MAX_STRING_LENGTH);
    }

    private static SkillDocument oneNodeDocument(Dynamic<?> triggerRaw, Dynamic<?> actionRaw) {
        var node = new NodeDocument(
                envelope("trigger", triggerRaw),
                envelope("action", actionRaw),
                AppearanceOverrideDocument.none());
        return new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(11),
                List.of(node),
                AppearanceDocument.defaultAppearance());
    }

    private static SkillDocument fourStringPayloadDocument(int firstLength, int otherLength) {
        var first = new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive("a".repeat(firstLength)));
        var other = new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive("b".repeat(otherLength)));
        var firstNode = new NodeDocument(
                envelope("trigger_one", first),
                envelope("action_one", other),
                AppearanceOverrideDocument.none());
        var secondNode = new NodeDocument(
                envelope("trigger_two", other),
                envelope("action_two", other),
                AppearanceOverrideDocument.none());
        return new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(12),
                List.of(firstNode, secondNode),
                AppearanceDocument.defaultAppearance());
    }

    private static DefinitionEnvelope envelope(String path, Dynamic<?> raw) {
        return new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("unregistered", path), 0, raw);
    }

    private static JsonObject jsonObject(String key, com.google.gson.JsonElement value) {
        var object = new JsonObject();
        object.add(key, value);
        return object;
    }

    private static JsonArray flatJsonArray(int elements) {
        var array = new JsonArray(elements);
        for (var index = 0; index < elements; index++) {
            array.add(0);
        }
        return array;
    }

    private static com.google.gson.JsonElement nestedJsonDepth(int depth) {
        com.google.gson.JsonElement current = new JsonPrimitive(0);
        for (var level = 1; level < depth; level++) {
            current = jsonObject("n", current);
        }
        return current;
    }

    private static void assertEveryPayloadIsFamilyTagged(CompoundTag physical) {
        var nodes = assertInstanceOf(ListTag.class, physical.get("nodes"));
        for (var rawNode : nodes) {
            var node = assertInstanceOf(CompoundTag.class, rawNode);
            for (var side : List.of("trigger", "action")) {
                var definition = assertInstanceOf(CompoundTag.class, node.get(side));
                var payload = assertInstanceOf(CompoundTag.class, definition.get("payload"));
                assertEquals(
                        java.util.Set.of("family", "registry_context", "compressed_maps", "data"),
                        payload.getAllKeys());
                assertInstanceOf(StringTag.class, payload.get("family"));
                assertInstanceOf(ByteTag.class, payload.get("registry_context"));
                assertInstanceOf(ByteTag.class, payload.get("compressed_maps"));
                assertInstanceOf(ByteArrayTag.class, payload.get("data"));
            }
        }
    }

    private static void assertRawTree(
            Object expectedValue,
            Dynamic<?> actual,
            SerializedTreeContext expectedContext) {
        assertEquals(expectedValue, actual.getValue());
        assertEquals(expectedContext, SupportedDynamicTrees.contextOf(actual).getOrThrow());
        assertNotEquals(
                expectedContext.family() == SerializedTreeFamily.JSON
                        ? SerializedTreeFamily.NBT
                        : SerializedTreeFamily.JSON,
                SupportedDynamicTrees.contextOf(actual).getOrThrow().family());
    }

    private static CompoundTag decodePhysical(ImmutableEncodedBytes encoded) {
        try {
            return assertInstanceOf(
                    CompoundTag.class,
                    StrictNbtTreeCodec.decode(encoded, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ImmutableEncodedBytes encodePhysical(net.minecraft.nbt.Tag physical)
            throws IOException {
        return StrictNbtTreeCodec.encode(physical, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
    }

    private static void assertMalformedNoPartial(ImmutableEncodedBytes encoded) {
        var result = SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                encoded, Optional.empty());
        assertTrue(result.successValue().isEmpty());
        assertInstanceOf(
                SkillDocumentPersistenceFailure.MalformedPhysicalDocument.class,
                result.failureValue().orElseThrow());
    }

    private static <T> T success(SkillDocumentPersistenceResult<T> result) {
        assertTrue(result.failureValue().isEmpty());
        return result.successValue().orElseThrow();
    }

    private static SkillDocumentPersistenceFailure failure(SkillDocumentPersistenceResult<?> result) {
        assertTrue(result.successValue().isEmpty());
        return result.failureValue().orElseThrow();
    }
}
