package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.migration.OpaqueSkillDocumentMigrationFacade;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SkillDocumentStorePersistenceFacadeTest {
    private static final HolderLookup.Provider PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void opaqueHandleCopiesBothDirectionsEnforcesBoundsAndHasBoundedString() {
        var source = new byte[] {1, 2, 3};
        var handle = EncodedSkillDocument.copyOf(source);
        source[0] = 9;
        var exposed = handle.copyBytes();
        exposed[1] = 9;
        var destination = new byte[] {8, 8, 8, 8, 8};
        handle.copyInto(destination, 1);

        assertArrayEquals(new byte[] {1, 2, 3}, handle.copyBytes());
        assertArrayEquals(new byte[] {8, 1, 2, 3, 8}, destination);
        assertEquals(3, handle.byteCount());
        assertEquals(handle, EncodedSkillDocument.copyOf(new byte[] {1, 2, 3}));
        assertNotEquals(handle, EncodedSkillDocument.copyOf(new byte[] {1, 2, 4}));
        assertTrue(handle.toString().contains("byteCount=3"));
        assertFalse(handle.toString().contains("1, 2, 3"));
        assertTrue(handle.toString().length() < 96);
        assertEquals(
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES,
                EncodedSkillDocument.copyOf(
                                new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES])
                        .byteCount());
        assertThrows(IllegalArgumentException.class,
                () -> EncodedSkillDocument.copyOf(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> EncodedSkillDocument.copyOf(
                        new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1]));
        assertThrows(NullPointerException.class, () -> EncodedSkillDocument.copyOf(null));
        assertThrows(NullPointerException.class, () -> handle.copyInto(null, 0));
        assertThrows(IndexOutOfBoundsException.class,
                () -> handle.copyInto(new byte[2], 0));
        assertThrows(IndexOutOfBoundsException.class,
                () -> handle.copyInto(new byte[3], -1));
    }

    @Test
    void tokenizedMigrationInputCanOnlyBeMintedByDocumentPackageAndIsDefensive() {
        var source = new byte[] {1, 2, 3};
        var input = TokenizedSkillDocumentMigrationInput.copyOf(source);
        source[0] = 9;
        var first = input.copyBytes();
        first[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, input.copyBytes());
        assertEquals(3, input.byteCount());
        assertEquals(
                "TokenizedSkillDocumentMigrationInput[byteCount=3]",
                input.toString());
        assertTrue(input.toString().length() < 96);
        assertTrue(Arrays.stream(TokenizedSkillDocumentMigrationInput.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
        assertEquals(
                Set.of("byteCount", "copyBytes", "toString"),
                Arrays.stream(TokenizedSkillDocumentMigrationInput.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(method -> method.getName())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertTrue(Arrays.stream(TokenizedSkillDocumentMigrationInput.class.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .noneMatch(method -> Modifier.isPublic(method.getModifiers())));
        assertEquals(
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES,
                TokenizedSkillDocumentMigrationInput.copyOf(
                                new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES])
                        .byteCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> TokenizedSkillDocumentMigrationInput.copyOf(new byte[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> TokenizedSkillDocumentMigrationInput.copyOf(
                        new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1]));
        assertThrows(
                NullPointerException.class,
                () -> TokenizedSkillDocumentMigrationInput.copyOf(null));
    }

    @Test
    void currentFacadeRoundTripPreservesMixedFamiliesAndRegistryContext() {
        var json = new JsonObject();
        json.addProperty("secret_json", "preserve");
        var nbt = new CompoundTag();
        nbt.putInt("secret_nbt", 7);
        var triggerOps = RegistryOps.create(JsonOps.COMPRESSED, PROVIDER);
        var document = document(
                new Dynamic<>(triggerOps, json),
                new Dynamic<>(NbtOps.INSTANCE, nbt));

        var encodedResult = SkillDocumentStorePersistenceFacade.encodeCurrent(document);
        var encoded = assertInstanceOf(
                SkillDocumentStorePersistenceFacade.Encoded.class, encodedResult).document();
        var loaded = assertInstanceOf(
                SkillDocumentStorePersistenceFacade.Loaded.class,
                SkillDocumentStorePersistenceFacade.load(encoded, Optional.of(PROVIDER)));

        assertFalse(loaded.migrated());
        assertTrue(loaded.factReport().facts().isEmpty());
        assertFalse(loaded.factReport().truncated());
        assertEquals(document.skillId(), loaded.document().skillId());
        assertEquals(document.revision(), loaded.document().revision());
        assertEquals(
                new SerializedTreeContext(SerializedTreeFamily.JSON, true, true),
                context(loaded.document().nodes().get(0).trigger().copyRawPayload()));
        assertEquals(
                new SerializedTreeContext(SerializedTreeFamily.NBT, false, false),
                context(loaded.document().nodes().get(0).action().copyRawPayload()));
        assertEquals(json, loaded.document().nodes().get(0).trigger().copyRawPayload().getValue());
        assertEquals(nbt, loaded.document().nodes().get(0).action().copyRawPayload().getValue());
    }

    @Test
    void registryContextIsReboundOnlyWhenAProviderIsAvailable() {
        var document = document(
                new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, PROVIDER), new JsonObject()),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var encoded = assertInstanceOf(
                        SkillDocumentStorePersistenceFacade.Encoded.class,
                        SkillDocumentStorePersistenceFacade.encodeCurrent(document))
                .document();

        var rejected = assertInstanceOf(
                SkillDocumentStorePersistenceFacade.LoadRejected.class,
                SkillDocumentStorePersistenceFacade.load(encoded, Optional.empty()));

        assertEquals(
                SkillDocumentStorePersistenceFacade.FailureCode.REGISTRY_CONTEXT_UNAVAILABLE,
                rejected.failure().code());
        assertTrue(rejected.factReport().facts().isEmpty());
    }

    @Test
    void legacyOuterShapeIsMigratedBeforeCurrentPhysicalShapeDecode() throws Exception {
        var document = document(
                new Dynamic<>(JsonOps.INSTANCE, new JsonObject()),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var encoded = assertInstanceOf(
                        SkillDocumentStorePersistenceFacade.Encoded.class,
                        SkillDocumentStorePersistenceFacade.encodeCurrent(document))
                .document();
        var legacyRoot = assertInstanceOf(
                CompoundTag.class,
                StrictNbtTreeCodec.decode(
                        encoded.copyInternal(), MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES));
        legacyRoot.putInt("legacy_outer", 7);
        assertTrue(PhysicalSkillDocumentNbt.decode(legacyRoot).failureValue().isPresent(),
                "the current exact-shape decoder must reject the legacy-only field");
        var legacyBytes = StrictNbtTreeCodec.encode(
                legacyRoot, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);

        var loaded = assertInstanceOf(
                SkillDocumentStorePersistenceFacade.Loaded.class,
                SkillDocumentStorePersistenceFacade.loadWithMigration(
                        EncodedSkillDocument.fromInternal(legacyBytes),
                        Optional.empty(),
                        source -> migrateLegacyOuterField(source)));

        assertTrue(loaded.migrated());
        assertEquals(document, loaded.document());
    }

    @Test
    void migrationFailureOrExceptionInvokesNeitherCurrentHydrationNorPartialDecode() {
        var document = document(
                new Dynamic<>(JsonOps.INSTANCE, new JsonObject()),
                new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        var encoded = assertInstanceOf(
                        SkillDocumentStorePersistenceFacade.Encoded.class,
                        SkillDocumentStorePersistenceFacade.encodeCurrent(document))
                .document();

        for (var throwsException : List.of(false, true)) {
            var migrationCalls = new java.util.concurrent.atomic.AtomicInteger();
            var hydrationCalls = new java.util.concurrent.atomic.AtomicInteger();
            var result = SkillDocumentStorePersistenceFacade.loadWithMigration(
                    encoded,
                    Optional.empty(),
                    source -> {
                        migrationCalls.incrementAndGet();
                        if (throwsException) {
                            throw new IllegalStateException("secret-migration-message");
                        }
                        return new OpaqueSkillDocumentMigrationFacade.Failure(
                                OpaqueSkillDocumentMigrationFacade.FailureCode
                                        .MALFORMED_TOKENIZED_DOCUMENT,
                                Optional.empty(),
                                new PipelineFactReport(List.of(), false));
                    },
                    (bytes, provider) -> {
                        hydrationCalls.incrementAndGet();
                        throw new AssertionError("hydration must not run after migration failure");
                    });

            var rejected = assertInstanceOf(
                    SkillDocumentStorePersistenceFacade.LoadRejected.class, result);
            assertEquals(1, migrationCalls.get());
            assertEquals(0, hydrationCalls.get());
            assertTrue(rejected.failure().code()
                    == SkillDocumentStorePersistenceFacade.FailureCode.DOCUMENT_MIGRATION_FAILED
                    || rejected.failure().code()
                            == SkillDocumentStorePersistenceFacade.FailureCode
                                    .INTERNAL_CODEC_EXCEPTION);
            assertFalse(rejected.toString().contains("secret-migration-message"));
        }
    }

    @Test
    void tokenViewContainsNoRawBytesAndReinsertsEveryBoundContextExactly() throws Exception {
        var json = new JsonObject();
        json.addProperty("unique_raw_secret", "must-not-enter-migration");
        var nbt = new CompoundTag();
        nbt.putString("unique_nbt_secret", "must-not-enter-migration");
        var document = document(
                new Dynamic<>(RegistryOps.create(JsonOps.COMPRESSED, PROVIDER), json),
                new Dynamic<>(NbtOps.INSTANCE, nbt));
        var internal = success(SkillDocumentPersistenceBridge.encodeCurrent(document));
        var physical = physical(internal);
        var built = assertInstanceOf(
                LogicalSkillDocumentConformanceView.BuildResult.Success.class,
                LogicalSkillDocumentConformanceView.build(physical));

        assertEquals(2, built.table().size());
        assertInstanceOf(
                OpaqueRawTreeLocation.TriggerPayload.class,
                built.table().entryAt(0).location());
        assertInstanceOf(
                OpaqueRawTreeLocation.ActionPayload.class,
                built.table().entryAt(1).location());
        assertEquals(
                new SerializedTreeContext(SerializedTreeFamily.JSON, true, true),
                built.table().entryAt(0).rawTree().context());
        assertEquals(
                new SerializedTreeContext(SerializedTreeFamily.NBT, false, false),
                built.table().entryAt(1).rawTree().context());
        var rendered = built.logicalTree().toString();
        assertFalse(rendered.contains("unique_raw_secret"));
        assertFalse(rendered.contains("unique_nbt_secret"));
        assertFalse(rendered.contains("registry_context"));
        assertFalse(rendered.contains("compressed_maps"));

        var reinserted = assertInstanceOf(
                LogicalSkillDocumentConformanceView.ReinsertionResult.Success.class,
                LogicalSkillDocumentConformanceView.reinsert(
                        built.logicalTree(), built.table()));
        var physicalTag = success(PhysicalSkillDocumentNbt.encode(reinserted.document()));
        var currentBytes = StrictNbtTreeCodec.encode(
                physicalTag, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
        var hydrated = success(SkillDocumentPersistenceBridge.hydrateCurrentForInternalUse(
                currentBytes, Optional.of(PROVIDER)));
        assertEquals(json, hydrated.nodes().get(0).trigger().copyRawPayload().getValue());
        assertEquals(nbt, hydrated.nodes().get(0).action().copyRawPayload().getValue());
    }

    @Test
    void physicalExtractionRejectsEmptyAndOverLimitNodesBeforeTokenization() {
        var source = success(PhysicalSkillDocumentNbt.encode(physical(success(
                SkillDocumentPersistenceBridge.encodeCurrent(document(
                        new Dynamic<>(JsonOps.INSTANCE, new JsonObject()),
                        new Dynamic<>(NbtOps.INSTANCE, new CompoundTag())))))));

        var empty = source.copy();
        empty.put("nodes", new ListTag());
        assertInstanceOf(
                LogicalSkillDocumentConformanceView.BuildResult.Failure.class,
                LogicalSkillDocumentConformanceView.extractPhysical(empty));

        var template = ((CompoundTag) ((ListTag) source.get("nodes")).get(0)).copy();
        var oversizedNodes = new ListTag();
        for (var index = 0; index <= MagicSafetyCeilings.MAX_NODES; index++) {
            oversizedNodes.add(template.copy());
        }
        var oversized = source.copy();
        oversized.put("nodes", oversizedNodes);
        assertInstanceOf(
                LogicalSkillDocumentConformanceView.BuildResult.Failure.class,
                LogicalSkillDocumentConformanceView.extractPhysical(oversized));
    }

    @Test
    void unparsedAppearanceTokensAreOrderedLastPerNodeThenTopAndRawStaysOpaque() {
        var built = tokenizedAppearanceView();

        assertEquals(7, built.table().size());
        assertEquals(
                new OpaqueRawTreeLocation.TriggerPayload(0),
                built.table().entryAt(0).location());
        assertEquals(
                new OpaqueRawTreeLocation.ActionPayload(0),
                built.table().entryAt(1).location());
        assertEquals(
                new OpaqueRawTreeLocation.AppearanceOverride(0),
                built.table().entryAt(2).location());
        assertEquals(
                new OpaqueRawTreeLocation.TriggerPayload(1),
                built.table().entryAt(3).location());
        assertEquals(
                new OpaqueRawTreeLocation.ActionPayload(1),
                built.table().entryAt(4).location());
        assertEquals(
                new OpaqueRawTreeLocation.AppearanceOverride(1),
                built.table().entryAt(5).location());
        assertEquals(
                OpaqueRawTreeLocation.TopAppearance.INSTANCE,
                built.table().entryAt(6).location());

        var logical = built.logicalTree();
        assertSentinel(definition(logical, 0, "trigger"), "payload", 0);
        assertSentinel(definition(logical, 0, "action"), "payload", 1);
        assertSentinel(node(logical, 0), "appearance_override", 2);
        assertSentinel(definition(logical, 1, "trigger"), "payload", 3);
        assertSentinel(definition(logical, 1, "action"), "payload", 4);
        assertSentinel(node(logical, 1), "appearance_override", 5);
        assertSentinel(logical, "appearance", 6);
        var rendered = logical.toString();
        assertFalse(rendered.contains("override_zero_raw_secret"));
        assertFalse(rendered.contains("override_one_raw_secret"));
        assertFalse(rendered.contains("top_raw_secret"));
        assertFalse(rendered.contains("registry_context"));
        assertFalse(rendered.contains("compressed_maps"));
        assertFalse(rendered.contains("data"));
    }

    @Test
    void unparsedAppearanceTokenDeletionAndExchangeAreRejected() {
        var built = tokenizedAppearanceView();

        var deletedOverride = built.logicalTree();
        node(deletedOverride, 0).remove("appearance_override");
        assertTokenInvariant(deletedOverride, built.table());

        var deletedTop = built.logicalTree();
        deletedTop.put("appearance", new CompoundTag());
        assertTokenInvariant(deletedTop, built.table());

        var exchanged = built.logicalTree();
        var override = ((CompoundTag) node(exchanged, 0).get("appearance_override")).copy();
        var top = ((CompoundTag) exchanged.get("appearance")).copy();
        node(exchanged, 0).put("appearance_override", top);
        exchanged.put("appearance", override);
        assertTokenInvariant(exchanged, built.table());
    }

    @Test
    void tokenDeletionDuplicationExchangeTypeAndSchemaMutationAreRejected() {
        var built = tokenizedTwoNodeView();

        var deleted = built.logicalTree();
        definition(deleted, 0, "trigger").put("payload", new CompoundTag());
        assertTokenInvariant(deleted, built.table());

        var duplicated = built.logicalTree();
        definition(duplicated, 0, "action").put(
                "payload", definition(duplicated, 0, "trigger").get("payload").copy());
        assertTokenInvariant(duplicated, built.table());

        var exchanged = built.logicalTree();
        var first = definition(exchanged, 0, "trigger").get("payload").copy();
        var second = definition(exchanged, 0, "action").get("payload").copy();
        definition(exchanged, 0, "trigger").put("payload", second);
        definition(exchanged, 0, "action").put("payload", first);
        assertTokenInvariant(exchanged, built.table());

        var changedType = built.logicalTree();
        definition(changedType, 0, "trigger").putString("type", "test:changed");
        assertTokenInvariant(changedType, built.table());

        var changedSchema = built.logicalTree();
        definition(changedSchema, 0, "action").putInt("schema_version", 1);
        assertTokenInvariant(changedSchema, built.table());

        var relocated = built.logicalTree();
        var nodeZero = node(relocated, 0).copy();
        var nodeOne = node(relocated, 1).copy();
        nodes(relocated).set(0, nodeOne);
        nodes(relocated).set(1, nodeZero);
        assertTokenInvariant(relocated, built.table());
    }

    @Test
    void publicSurfaceHasNoCurrentOnlyLoadBypassOrMutablePhysicalTypes() {
        var methods = Arrays.stream(SkillDocumentStorePersistenceFacade.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .sorted()
                .toList();

        assertEquals(List.of("encodeCurrent", "load"), methods);
        assertFalse(Modifier.isPublic(SkillDocumentPersistenceBridge.class.getModifiers()));
        assertFalse(Modifier.isPublic(PhysicalSkillDocument.class.getModifiers()));
        assertFalse(Modifier.isPublic(RawTreeEnvelope.class.getModifiers()));
        assertFalse(Modifier.isPublic(OpaqueRawTreeTable.class.getModifiers()));
        assertFalse(Modifier.isPublic(OpaqueRawTreeLocation.class.getModifiers()));
        assertTrue(Arrays.stream(SkillDocumentStorePersistenceFacade.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("hydrateCurrent")
                        || method.getName().equals("decodeCurrent")
                        || method.getName().equals("loadCurrent")
                        || method.getName().equals("skipMigration")));
    }

    private static SkillDocument document(Dynamic<?> trigger, Dynamic<?> action) {
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(12),
                List.of(new NodeDocument(
                        new DefinitionEnvelope(
                                ResourceLocation.fromNamespaceAndPath("test", "trigger"), 0, trigger),
                        new DefinitionEnvelope(
                                ResourceLocation.fromNamespaceAndPath("test", "action"), 0, action),
                        AppearanceOverrideDocument.none())),
                AppearanceDocument.defaultAppearance());
    }

    private static OpaqueSkillDocumentMigrationFacade.Result migrateLegacyOuterField(
            TokenizedSkillDocumentMigrationInput source) {
        try {
            var logical = assertInstanceOf(
                    CompoundTag.class,
                    StrictNbtTreeCodec.decode(
                            ImmutableEncodedBytes.copyOf(source.copyBytes()),
                            MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES));
            assertEquals(7, logical.getInt("legacy_outer"));
            logical.remove("legacy_outer");
            var migrated = StrictNbtTreeCodec.encode(
                    logical, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
            return new OpaqueSkillDocumentMigrationFacade.Success(
                    OpaqueSkillDocumentMigrationFacade.MigratedTokenizedDocument.copyOf(
                            migrated.copyBytes()),
                    new PipelineFactReport(List.of(), false),
                    true);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static LogicalSkillDocumentConformanceView.BuildResult.Success tokenizedTwoNodeView() {
        var first = DocumentTestFixtures.node();
        var second = new NodeDocument(
                DocumentTestFixtures.envelope("trigger_two"),
                DocumentTestFixtures.envelope("action_two"),
                AppearanceOverrideDocument.none());
        var document = new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(2),
                List.of(first, second),
                AppearanceDocument.defaultAppearance());
        var physical = physical(success(SkillDocumentPersistenceBridge.encodeCurrent(document)));
        return assertInstanceOf(
                LogicalSkillDocumentConformanceView.BuildResult.Success.class,
                LogicalSkillDocumentConformanceView.build(physical));
    }

    private static LogicalSkillDocumentConformanceView.BuildResult.Success tokenizedAppearanceView() {
        var firstOverride = new JsonObject();
        firstOverride.addProperty("override_zero_raw_secret", "preserve");
        var secondOverride = new CompoundTag();
        secondOverride.putString("override_one_raw_secret", "preserve");
        var topAppearance = new JsonObject();
        topAppearance.addProperty("top_raw_secret", "preserve");
        var first = new NodeDocument(
                DocumentTestFixtures.envelope("trigger_one"),
                DocumentTestFixtures.envelope("action_one"),
                unparsedOverride(new Dynamic<>(JsonOps.INSTANCE, firstOverride)));
        var second = new NodeDocument(
                DocumentTestFixtures.envelope("trigger_two"),
                DocumentTestFixtures.envelope("action_two"),
                unparsedOverride(new Dynamic<>(NbtOps.INSTANCE, secondOverride)));
        var document = new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(3),
                List.of(first, second),
                unparsedAppearance(new Dynamic<>(JsonOps.INSTANCE, topAppearance)));
        var physical = physical(success(SkillDocumentPersistenceBridge.encodeCurrent(document)));
        return assertInstanceOf(
                LogicalSkillDocumentConformanceView.BuildResult.Success.class,
                LogicalSkillDocumentConformanceView.build(physical));
    }

    private static AppearanceDocument.Unparsed unparsedAppearance(Dynamic<?> raw) {
        return new AppearanceDocument.Unparsed(AppearanceRawSnapshot.capture(raw).getOrThrow());
    }

    private static AppearanceOverrideDocument.Unparsed unparsedOverride(Dynamic<?> raw) {
        return new AppearanceOverrideDocument.Unparsed(
                AppearanceRawSnapshot.capture(raw).getOrThrow());
    }

    private static void assertSentinel(
            CompoundTag parent,
            String field,
            int expectedTokenId) {
        var sentinel = assertInstanceOf(CompoundTag.class, parent.get(field));
        assertEquals(Set.of(LogicalSkillDocumentConformanceView.TOKEN_FIELD), sentinel.getAllKeys());
        assertEquals(
                expectedTokenId,
                sentinel.getInt(LogicalSkillDocumentConformanceView.TOKEN_FIELD));
    }

    private static void assertTokenInvariant(
            CompoundTag logical,
            OpaqueRawTreeTable table) {
        var failure = assertInstanceOf(
                LogicalSkillDocumentConformanceView.ReinsertionResult.Failure.class,
                LogicalSkillDocumentConformanceView.reinsert(logical, table));
        assertEquals(LogicalSkillDocumentConformanceView.FailureKind.TOKEN_INVARIANT, failure.kind());
    }

    private static CompoundTag definition(CompoundTag root, int nodeIndex, String side) {
        return (CompoundTag) node(root, nodeIndex).get(side);
    }

    private static CompoundTag node(CompoundTag root, int nodeIndex) {
        return (CompoundTag) nodes(root).get(nodeIndex);
    }

    private static ListTag nodes(CompoundTag root) {
        return (ListTag) root.get("nodes");
    }

    private static PhysicalSkillDocument physical(ImmutableEncodedBytes encoded) {
        try {
            var root = assertInstanceOf(
                    CompoundTag.class,
                    StrictNbtTreeCodec.decode(encoded, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES));
            return success(PhysicalSkillDocumentNbt.decode(root));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static SerializedTreeContext context(Dynamic<?> dynamic) {
        return SupportedDynamicTrees.contextOf(dynamic).getOrThrow();
    }

    private static <T> T success(SkillDocumentPersistenceResult<T> result) {
        return result.successValue().orElseThrow();
    }
}
