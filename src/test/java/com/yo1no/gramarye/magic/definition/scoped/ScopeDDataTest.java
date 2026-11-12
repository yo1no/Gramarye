package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class ScopeDDataTest {
    private static final SkillOwnerId OWNER = new SkillOwnerId(new UUID(0, 1));
    private static final SkillOwnerId OTHER = new SkillOwnerId(new UUID(0, 2));
    private static final SpellIdentity SPELL = new SpellIdentity(OWNER, new SpellDefinitionId(1));
    private static final SpellIdentity TARGET = new SpellIdentity(OWNER, new SpellDefinitionId(2));
    private static final ManifestIdentity MANIFEST = new ManifestIdentity(OWNER, new SkillManifestId(1));
    private static final CatalogGeneration GENERATION = new CatalogGeneration(3);
    private static final Descriptor BACKEND = new Descriptor("gramarye:test_backend", 1, List.of());
    private static final Descriptor EFFECT = new Descriptor("gramarye:test_effect", 1, List.of());
    private static final DescriptorContext CONTEXT = new DescriptorContext(Set.of(
            new DescriptorKey(DescriptorDomain.BACKEND, BACKEND.tag(), BACKEND.version()),
            new DescriptorKey(DescriptorDomain.EVENT, "gramarye:test_event", 1),
            new DescriptorKey(DescriptorDomain.EFFECT, EFFECT.tag(), EFFECT.version())), Set.of());
    private static final SerializedTreeContext JSON = new SerializedTreeContext(SerializedTreeFamily.JSON, false, false);
    private static final SerializedTreeContext NBT = new SerializedTreeContext(SerializedTreeFamily.NBT, false, false);

    @Test
    void identityFamiliesAndOwnerDoNotCrossAndCountersCannotWrap() {
        assertNotEquals(SPELL, MANIFEST);
        assertNotEquals(SPELL, new SpellIdentity(OTHER, SPELL.id()));
        assertNotEquals(new SpellRevision(1), new ManifestRevision(1));
        assertEquals(new SpellRevision(2), new SpellRevision(1).successor());
        assertEquals(new ManifestRevision(2), new ManifestRevision(1).successor());
        assertEquals(new CatalogGeneration(4), GENERATION.successor());
        assertThrows(IllegalArgumentException.class, () -> new SpellDefinitionId(0));
        assertThrows(IllegalArgumentException.class, () -> new CatalogGeneration(-1));
        assertThrows(ArithmeticException.class, () -> new SpellRevision(Long.MAX_VALUE).successor());
        assertThrows(ArithmeticException.class, () -> new ManifestRevision(Long.MAX_VALUE).successor());
        assertThrows(ArithmeticException.class, () -> new CatalogGeneration(Long.MAX_VALUE).successor());
        assertEquals(List.of("target"), Arrays.stream(SpellIdentityReference.class.getRecordComponents())
                .map(component -> component.getName()).toList());
    }

    @Test
    void spellAndManifestAndTheirDraftsHaveIndependentDeterministicFrames() throws IOException {
        var setting = new Setting("distance", new Select(ValueType.INTEGER,
                new Equal(new CreationInput("enabled", ValueType.BOOLEAN), new Literal(new BooleanValue(true))),
                new Literal(new IntegerValue(11)), new Literal(new IntegerValue(3))));
        var blocks = new ArrayList<Block>(List.of(new CreateSpell(new BlockId(2), new SpellIdentityReference(TARGET)),
                new EffectBlock(new BlockId(1), EFFECT)));
        var spellBody = body(new Absent<>(), new Known<>(List.of(setting)), new Known<>(List.of(handler(blocks))),
                new Absent<>(), new Absent<>());
        blocks.clear();
        var spell = spell(spellBody);
        var spellDraft = new SpellDraft(SPELL, Optional.of(new SpellRevision(0)), GENERATION, spellBody);
        var manifest = manifest(List.of(entry(4, TARGET, "spell")));
        var manifestDraft = new ManifestDraft(MANIFEST, Optional.of(new ManifestRevision(0)), GENERATION, manifest.body());
        for (var document : List.of(spell, spellDraft, manifest, manifestDraft)) {
            var frame = ScopeDDataCodec.encode(document);
            assertEquals(document, ScopeDDataCodec.decode(frame));
            assertArrayEquals(frame, ScopeDDataCodec.encode(ScopeDDataCodec.decode(frame)));
        }
        assertEquals(List.of(new BlockId(2), new BlockId(1)), spell.blockIds());
        assertEquals(List.of(new SpellIdentityReference(TARGET)), spell.dependencies());
        assertTrue(ScopeDData.validate(spell, CONTEXT).valid());
        assertTrue(ScopeDData.validate(manifest, CONTEXT).valid());
        assertNotEquals(ScopeDDataCodec.encode(spell)[5], ScopeDDataCodec.encode(spellDraft)[5]);
        assertNotEquals(ScopeDDataCodec.encode(manifest)[5], ScopeDDataCodec.encode(manifestDraft)[5]);
    }

    @Test
    void legacyReadersStayExplicitAndLegacyBytesNeverDecodeAsNewFamily() throws IOException {
        var formalJson = """
                {"schema_version":0,"skill_id":"123e4567-e89b-12d3-a456-426614174000","revision":0,
                "nodes":[{"trigger":{"type":"test:trigger","schema_version":0,"payload":{}},
                "action":{"type":"test:action","schema_version":0,"payload":{}}}]}
                """;
        var draftJson = """
                {"draft_schema_version":0,"skill_id":"123e4567-e89b-12d3-a456-426614174000",
                "nodes":[{"trigger":{"state":"missing"},"action":{"state":"missing"}}]}
                """;
        var formal = ScopeDDataCodec.readLegacyDocument(new Dynamic<>(JsonOps.INSTANCE,
                JsonParser.parseString(formalJson))).getOrThrow().document();
        var draft = ScopeDDataCodec.readLegacyDraft(new Dynamic<>(JsonOps.INSTANCE,
                JsonParser.parseString(draftJson))).getOrThrow().draft();
        assertEquals(formal.skillId(), draft.skillId());
        assertEquals(0, formal.revision().value());
        assertThrows(IOException.class, () -> ScopeDDataCodec.decode(utf8(formalJson)));
        assertThrows(IOException.class, () -> ScopeDDataCodec.decode(utf8(draftJson)));
    }

    @Test
    void absentActivationIsDistinctFromMissingAndUnknownAndHookIsDraftOnly() throws IOException {
        var raw = raw(SPELL, RawField.PHENOMENON, JSON, utf8("{\"value\":1.00}"));
        var missingActivation = new SpellDraft(SPELL, Optional.empty(), GENERATION,
                body(new Missing<>(), new Known<>(List.of()), new Known<>(List.of()), new Absent<>(), new Unknown<>(raw)));
        assertEquals(missingActivation, ScopeDDataCodec.decode(ScopeDDataCodec.encode(missingActivation)));
        assertEquals(List.of(new Issue(Code.MISSING_REQUIRED, "activation"), new Issue(Code.HOOK_ONLY, "phenomenon")),
                ScopeDData.validate(missingActivation, CONTEXT).issues());
        assertThrows(IllegalArgumentException.class,
                () -> ScopeDData.formalize(missingActivation, new SpellReference(SPELL, new SpellRevision(1))));
        var knownHook = new SpellDraft(SPELL, Optional.empty(), GENERATION,
                body(new Absent<>(), new Known<>(List.of()), new Known<>(List.of()), new Absent<>(), new Known<>(EFFECT)));
        assertEquals(knownHook, ScopeDDataCodec.decode(ScopeDDataCodec.encode(knownHook)));
        assertEquals(List.of(new Issue(Code.HOOK_ONLY, "phenomenon")), ScopeDData.validate(knownHook, CONTEXT).issues());
        assertThrows(IOException.class, () -> ScopeDDataCodec.encode(spell(knownHook.body())));
        assertTrue(ScopeDData.validate(spell(emptyBody()), CONTEXT).valid());
    }

    @Test
    void manifestDraftKeepsBothDuplicateKindsInPlaceButFormalRejects() throws IOException {
        var entries = List.of(entry(2, SPELL, "first"), entry(2, TARGET, "other"), entry(4, SPELL, "last"));
        var draft = new ManifestDraft(MANIFEST, Optional.empty(), GENERATION,
                new ManifestBody(new Known<>(entries), "bundle", true));
        assertEquals(draft, ScopeDDataCodec.decode(ScopeDDataCodec.encode(draft)));
        assertEquals(entries, knownList(draft.body().entries()));
        assertEquals(List.of(new Issue(Code.DUPLICATE_MANIFEST_ENTRY_ID, "entries[1]"),
                        new Issue(Code.DUPLICATE_MANIFEST_SPELL, "entries[2]")),
                ScopeDData.validate(draft, CONTEXT).issues());
        assertThrows(IOException.class, () -> ScopeDDataCodec.encode(manifest(entries)));
        assertTrue(ScopeDData.validate(manifest(List.of(entry(2, SPELL, "first"))), CONTEXT).valid());
        var other = new SkillManifest(new ManifestReference(new ManifestIdentity(OWNER, new SkillManifestId(2)),
                new ManifestRevision(1)), new ManifestBody(new Known<>(List.of(entry(2, SPELL, "same target"))), "", false),
                Optional.empty());
        assertTrue(ScopeDData.validate(other, CONTEXT).valid());
    }

    @Test
    void duplicateEventAndLocalIdentitiesSurviveDraftWithBoundedDiagnostics() throws IOException {
        var first = handler(List.of(new EffectBlock(new BlockId(1), EFFECT)));
        var next = new Handler(first.id(), first.event(), first.blocks());
        var draft = new SpellDraft(SPELL, Optional.empty(), GENERATION, body(new Absent<>(), new Known<>(List.of()),
                new Known<>(List.of(first, next)), new Absent<>(), new Absent<>()));
        assertEquals(draft, ScopeDDataCodec.decode(ScopeDDataCodec.encode(draft)));
        assertEquals(List.of(new Issue(Code.DUPLICATE_HANDLER_ID, "handlers[1]"),
                        new Issue(Code.DUPLICATE_EVENT, "handlers[1]"),
                        new Issue(Code.DUPLICATE_BLOCK_ID, "handlers[1].blocks[0]")),
                ScopeDData.validate(draft, CONTEXT).issues());
        assertThrows(IOException.class, () -> ScopeDDataCodec.encode(spell(draft.body())));

        var many = Collections.nCopies(MAX_BLOCKS, new EffectBlock(new BlockId(1), EFFECT));
        var report = ScopeDData.validate(spell(body(new Absent<>(), new Known<>(List.of()),
                new Known<>(List.of(handler(many))), new Absent<>(), new Absent<>())), CONTEXT);
        assertEquals(MAX_DIAGNOSTICS, report.issues().size());
        assertTrue(report.truncated());
        assertFalse(report.valid());
    }

    @Test
    void descriptorAndOwnerValidationDoNotPromoteUnknownGameplayOrLeakForeignTargets() {
        var foreign = new SpellIdentity(OTHER, new SpellDefinitionId(700));
        var malicious = spell(body(new Absent<>(), new Known<>(List.of()), new Known<>(List.of(handler(List.of(
                new CreateSpell(new BlockId(1), new SpellIdentityReference(foreign)),
                new EffectBlock(new BlockId(2), new Descriptor("gramarye:unknown", 71, List.of())))))),
                new Absent<>(), new Absent<>()));
        var report = ScopeDData.validate(malicious, CONTEXT);
        assertEquals(List.of(new Issue(Code.FOREIGN_OWNER, "handlers[0].blocks[0]"),
                new Issue(Code.UNSUPPORTED_DESCRIPTOR, "handlers[0].blocks[1]")), report.issues());
        assertFalse(report.valid());
        assertFalse(report.toString().contains(foreign.owner().value().toString()));
        assertFalse(report.toString().contains("700"));
        assertFalse(report.toString().contains("gramarye:unknown"));
    }

    @Test
    void eventDescriptorVersionsRoundTripAndValidationUsesTheVersionedKey() throws IOException {
        var handler = new Handler(new HandlerId(1), "gramarye:test_event", 2, List.of());
        var document = spell(body(new Absent<>(), new Known<>(List.of()), new Known<>(List.of(handler)),
                new Absent<>(), new Absent<>()));
        assertEquals(document, ScopeDDataCodec.decode(ScopeDDataCodec.encode(document)));
        assertEquals(List.of(new Issue(Code.UNSUPPORTED_DESCRIPTOR, "handlers[0]")),
                ScopeDData.validate(document, CONTEXT).issues());
        var context = new DescriptorContext(Set.of(new DescriptorKey(DescriptorDomain.BACKEND, BACKEND.tag(), 1),
                new DescriptorKey(DescriptorDomain.EVENT, "gramarye:test_event", 2)), Set.of());
        assertTrue(ScopeDData.validate(document, context).valid());
    }

    @Test
    void unknownVisualRawIsIsolatedFromGameplayAndCannotBeReboundInAFork() throws IOException {
        var appearance = raw(SPELL, RawField.APPEARANCE, JSON, utf8("{}"));
        var document = spell(body(new Absent<>(), new Known<>(List.of()), new Known<>(List.of()),
                new Unknown<>(appearance), new Absent<>()));
        assertEquals(document, ScopeDDataCodec.decode(ScopeDDataCodec.encode(document)));
        assertTrue(ScopeDData.validate(document, CONTEXT).valid());
        assertEquals(List.of(new Issue(Code.UNKNOWN_VISUAL_FALLBACK, "appearance")),
                ScopeDData.validate(document, CONTEXT).issues());
        assertThrows(IllegalArgumentException.class, () -> spell(body(new Unknown<>(appearance),
                new Known<>(List.of()), new Known<>(List.of()), new Absent<>(), new Absent<>())));
        assertThrows(IllegalArgumentException.class, () -> document.remap(
                new SpellReference(new SpellIdentity(OTHER, SPELL.id()), new SpellRevision(1)),
                java.util.Map.of(), new ForkProvenance(new UUID(0, 33), document.reference(), GENERATION)));
    }

    @Test
    void mixedRegistryRawContextsAreByteExactAndDefensivelyHydrated() throws IOException {
        var provider = HolderLookup.Provider.create(Stream.empty());
        var jsonInput = JsonParser.parseString("{\"number\":1.00}").getAsJsonObject();
        var json = RawEnvelope.capture(new RawLocation(SPELL, RawField.ACTIVATION), "gramarye:unknown", 7,
                new Dynamic<>(RegistryOps.create(JsonOps.COMPRESSED, provider), jsonInput));
        var nbtInput = new CompoundTag();
        nbtInput.putByte("number", (byte) 3);
        var nbt = RawEnvelope.capture(new RawLocation(SPELL, RawField.PHENOMENON), "gramarye:unknown", 9,
                new Dynamic<>(RegistryOps.create(NbtOps.INSTANCE, provider), nbtInput));
        jsonInput.addProperty("added", true);
        nbtInput.putInt("number", 90);
        var draft = new SpellDraft(SPELL, Optional.empty(), GENERATION, body(new Unknown<>(json),
                new Known<>(List.of()), new Known<>(List.of()), new Absent<>(), new Unknown<>(nbt)));
        var loaded = assertInstanceOf(SpellDraft.class, ScopeDDataCodec.decode(ScopeDDataCodec.encode(draft)));
        assertEquals(draft, loaded);
        assertArrayEquals(utf8("{\"number\":1.00}"), json.copyBytes());
        assertThrows(IOException.class, () -> json.hydrate(Optional.empty()));
        var tree = json.hydrate(Optional.of(provider));
        assertEquals(json.context(), SupportedDynamicTrees.contextOf(tree).getOrThrow());
        ((JsonObject) tree.getValue()).addProperty("mutated", true);
        assertFalse(((JsonObject) json.hydrate(Optional.of(provider)).getValue()).has("mutated"));
        assertEquals((byte) 3, ((CompoundTag) nbt.hydrate(Optional.of(provider)).getValue()).getByte("number"));
        var exposed = nbt.copyBytes();
        exposed[0] = 0;
        assertNotEquals(0, nbt.copyBytes()[0]);
        assertFalse(ScopeDData.validate(loaded, CONTEXT).valid());
    }

    @Test
    void malformedRawTreeTypesDuplicatesAndExtraBytesFailClosed() throws IOException {
        for (var malformed : List.of("{\"x\":1,\"x\":2}", "{} {}", "{", "\"\u0001\"", "\"\\ud800\"")) {
            assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, JSON, utf8(malformed)), malformed);
        }
        assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, JSON, new byte[] {(byte) 0xff}));
        assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, NBT, new byte[] {0, 0}));
        assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, NBT, new byte[] {7, 127, -1, -1, -1}));
        var duplicate = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(duplicate)) {
            output.writeByte(10);
            for (int i = 0; i < 2; i++) {
                output.writeByte(1);
                output.writeUTF("duplicate");
                output.writeByte(i);
            }
            output.writeByte(0);
        }
        assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, NBT, duplicate.toByteArray()));
    }

    @Test
    void rawByteTreeCountAndDepthMaximumAndNextRejectBeforeRetention() throws IOException {
        assertEquals(MAX_RAW_SLOT_BYTES, raw(SPELL, RawField.ACTIVATION, JSON, jsonString(MAX_RAW_SLOT_BYTES)).byteCount());
        assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, JSON, jsonString(MAX_RAW_SLOT_BYTES + 1)));
        raw(SPELL, RawField.ACTIVATION, JSON, utf8(jsonArray(MAX_RAW_VALUES - 1)));
        assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, JSON, utf8(jsonArray(MAX_RAW_VALUES))));
        raw(SPELL, RawField.ACTIVATION, JSON, utf8(nested(MAX_RAW_DEPTH)));
        assertThrows(IOException.class, () -> raw(SPELL, RawField.ACTIVATION, JSON, utf8(nested(MAX_RAW_DEPTH + 1))));
    }

    @Test
    void documentAggregateRawByteAndSlotBoundsAreIndependent() throws IOException {
        var bytes = new ArrayList<UnknownBlock>();
        for (int i = 0; i < 4; i++) {
            var id = new BlockId(i + 1);
            int size = Math.min(MAX_RAW_SLOT_BYTES, MAX_RAW_BYTES - i * MAX_RAW_SLOT_BYTES);
            bytes.add(new UnknownBlock(id, raw(SPELL, new BlockPayload(new HandlerId(1), id), JSON, jsonString(size))));
        }
        var exact = unknownBlocksDraft(new ArrayList<>(bytes));
        assertEquals(exact, ScopeDDataCodec.decode(ScopeDDataCodec.encode(exact)));
        var next = new BlockId(5);
        bytes.add(new UnknownBlock(next, raw(SPELL, new BlockPayload(new HandlerId(1), next), JSON, utf8("1"))));
        assertThrows(IllegalArgumentException.class, () -> unknownBlocksDraft(new ArrayList<>(bytes)));

        var many = new ArrayList<Block>();
        for (int i = 1; i <= MAX_RAW_SLOTS + 1; i++) {
            var id = new BlockId(i);
            many.add(new UnknownBlock(id, raw(SPELL, new BlockPayload(new HandlerId(1), id), JSON, utf8("1"))));
        }
        var atSlotLimit = unknownBlocksDraft(many.subList(0, MAX_RAW_SLOTS));
        assertEquals(atSlotLimit, ScopeDDataCodec.decode(ScopeDDataCodec.encode(atSlotLimit)));
        assertThrows(IllegalArgumentException.class, () -> unknownBlocksDraft(many));
    }

    @Test
    void typedShapeAndEncodedFrameBoundsRejectPlusOne() throws IOException {
        var atLimit = largeDocument(MAX_DOCUMENT_BYTES);
        var frame = ScopeDDataCodec.encode(atLimit);
        assertEquals(MAX_DOCUMENT_BYTES, frame.length);
        assertEquals(atLimit, ScopeDDataCodec.decode(frame));
        assertThrows(IOException.class, () -> ScopeDDataCodec.encode(largeDocument(MAX_DOCUMENT_BYTES + 1)));
        assertThrows(IOException.class, () -> ScopeDDataCodec.decode(Arrays.copyOf(frame, frame.length + 1)));
        assertThrows(IllegalArgumentException.class, () -> new TextValue("ü".repeat(MAX_TEXT_BYTES / 2 + 1)));
        assertEquals(MAX_TEXT_BYTES, utf8(new TextValue("ü".repeat(MAX_TEXT_BYTES / 2)).value()).length);
        assertThrows(IllegalArgumentException.class,
                () -> new Equal(new Literal(new TextValue("1")), new Literal(new IntegerValue(1))));
        assertThrows(IllegalArgumentException.class, () -> handler(Collections.nCopies(MAX_BLOCKS + 1,
                new EffectBlock(new BlockId(1), EFFECT))));
    }

    @Test
    void independentTypedCollectionsAcceptMaximumAndRejectNext() throws IOException {
        var settings = IntStream.range(0, MAX_SETTINGS + 1)
                .mapToObj(index -> new Setting("setting_" + index, new Literal(new IntegerValue(index)))).toList();
        var handlers = IntStream.rangeClosed(1, MAX_HANDLERS + 1)
                .mapToObj(index -> new Handler(new HandlerId(index), "gramarye:event_" + index, List.of())).toList();
        var entries = IntStream.rangeClosed(1, MAX_MANIFEST_ENTRIES + 1)
                .mapToObj(index -> entry(index, new SpellIdentity(OWNER, new SpellDefinitionId(index)), "entry")).toList();
        var arguments = IntStream.range(0, MAX_ARGUMENTS + 1)
                .mapToObj(index -> new Argument("argument_" + index, new BooleanValue(true))).toList();
        var capabilities = IntStream.range(0, MAX_CAPABILITIES + 1)
                .mapToObj(index -> "gramarye:capability_" + index).toList();
        var accepted = new SpellDraft(SPELL, Optional.empty(), GENERATION, new SpellBody(new Known<>(new Backend(
                new Descriptor("gramarye:" + "a".repeat(MAX_SYMBOL_BYTES - "gramarye:".length()), 1,
                        arguments.subList(0, MAX_ARGUMENTS)),
                "gramarye:test_category", capabilities.subList(0, MAX_CAPABILITIES))), new Absent<>(),
                new Known<>(settings.subList(0, MAX_SETTINGS)), new Known<>(handlers.subList(0, MAX_HANDLERS)),
                new Absent<>(), new Absent<>()));
        assertEquals(accepted, ScopeDDataCodec.decode(ScopeDDataCodec.encode(accepted)));
        var maximumManifest = manifest(entries.subList(0, MAX_MANIFEST_ENTRIES));
        assertEquals(maximumManifest, ScopeDDataCodec.decode(ScopeDDataCodec.encode(maximumManifest)));
        assertThrows(IllegalArgumentException.class, () -> manifest(entries));
        assertThrows(IllegalArgumentException.class, () -> body(new Absent<>(), new Known<>(settings),
                new Known<>(List.of()), new Absent<>(), new Absent<>()));
        assertThrows(IllegalArgumentException.class, () -> body(new Absent<>(), new Known<>(List.of()),
                new Known<>(handlers), new Absent<>(), new Absent<>()));
        assertThrows(IllegalArgumentException.class, () -> new Descriptor(EFFECT.tag(), 1, arguments));
        assertThrows(IllegalArgumentException.class, () -> new Descriptor(
                "gramarye:" + "a".repeat(MAX_SYMBOL_BYTES - "gramarye:".length() + 1), 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Backend(BACKEND, "gramarye:category", capabilities));

        var contextEntries = IntStream.range(0, MAX_DESCRIPTOR_CONTEXT_ENTRIES + 1)
                .mapToObj(index -> new DescriptorKey(DescriptorDomain.EVENT, "gramarye:event_" + index, 1)).toList();
        assertEquals(MAX_DESCRIPTOR_CONTEXT_ENTRIES, new DescriptorContext(Set.copyOf(contextEntries
                .subList(0, MAX_DESCRIPTOR_CONTEXT_ENTRIES)), Set.of()).descriptors().size());
        assertThrows(IllegalArgumentException.class, () -> new DescriptorContext(Set.copyOf(contextEntries), Set.of()));
    }

    @Test
    void aggregateExpressionDepthAndBlockCapsApplyAcrossWholeSpell() throws IOException {
        var literal = new Literal(new BooleanValue(true));
        var expression = new Select(ValueType.BOOLEAN, literal, literal, literal);
        var settings = IntStream.range(0, MAX_SETTINGS).mapToObj(index -> new Setting("s_" + index, expression))
                .collect(Collectors.toCollection(ArrayList::new));
        assertEquals(MAX_EXPRESSIONS, 4 * settings.size());
        var exact = spell(body(new Absent<>(), new Known<>(settings), new Known<>(List.of()), new Absent<>(), new Absent<>()));
        assertEquals(exact, ScopeDDataCodec.decode(ScopeDDataCodec.encode(exact)));
        settings.set(0, new Setting("s_0", new Equal(literal, literal)));
        settings.set(1, new Setting("s_1", new Select(ValueType.BOOLEAN, new Equal(literal, literal), literal, literal)));
        assertThrows(IllegalArgumentException.class, () -> body(new Absent<>(), new Known<>(settings),
                new Known<>(List.of()), new Absent<>(), new Absent<>()));

        Expression deep = literal;
        for (int depth = 1; depth < MAX_EXPRESSION_DEPTH; depth++) {
            deep = new Equal(deep, literal);
        }
        var atDepth = spell(body(new Absent<>(), new Known<>(List.of(new Setting("deep", deep))),
                new Known<>(List.of()), new Absent<>(), new Absent<>()));
        assertEquals(atDepth, ScopeDDataCodec.decode(ScopeDDataCodec.encode(atDepth)));
        var tooDeep = new Equal(deep, literal);
        assertThrows(IllegalArgumentException.class, () -> body(new Absent<>(), new Known<>(List.of(new Setting("deep", tooDeep))),
                new Known<>(List.of()), new Absent<>(), new Absent<>()));

        var blocks = IntStream.rangeClosed(1, MAX_BLOCKS + 1)
                .mapToObj(index -> (Block) new EffectBlock(new BlockId(index), EFFECT)).toList();
        var first = handler(blocks.subList(0, MAX_BLOCKS / 2));
        var second = new Handler(new HandlerId(2), "gramarye:next", blocks.subList(MAX_BLOCKS / 2, MAX_BLOCKS));
        var maximumBlocks = spell(body(new Absent<>(), new Known<>(List.of()), new Known<>(List.of(first, second)),
                new Absent<>(), new Absent<>()));
        assertEquals(maximumBlocks, ScopeDDataCodec.decode(ScopeDDataCodec.encode(maximumBlocks)));
        var overflow = new Handler(second.id(), second.event(), blocks.subList(MAX_BLOCKS / 2, MAX_BLOCKS + 1));
        assertThrows(IllegalArgumentException.class, () -> body(new Absent<>(), new Known<>(List.of()),
                new Known<>(List.of(first, overflow)), new Absent<>(), new Absent<>()));
    }

    @Test
    void forkProvenanceKeepsTypedSourceOwnerAndGenerationAcrossStorage() throws IOException {
        var source = spell(emptyBody());
        var exactTarget = new SpellReference(new SpellIdentity(OTHER, new SpellDefinitionId(7)), new SpellRevision(2));
        var provenance = new ForkProvenance(new UUID(0, 17), source.reference(), GENERATION);
        var fork = source.remap(exactTarget, java.util.Map.of(), provenance);
        var decoded = assertInstanceOf(SpellDefinition.class, ScopeDDataCodec.decode(ScopeDDataCodec.encode(fork)));
        assertEquals(provenance, decoded.provenance().orElseThrow());
        assertEquals(exactTarget, decoded.reference());
        assertEquals(source.body(), decoded.body());
    }

    @Test
    void truncatedTrailingWrongFamilyAndUnsupportedVersionFramesAreRejected() throws IOException {
        var frame = ScopeDDataCodec.encode(spell(emptyBody()));
        for (int i = 0; i < frame.length; i++) {
            var truncated = Arrays.copyOf(frame, i);
            assertThrows(IOException.class, () -> ScopeDDataCodec.decode(truncated));
        }
        assertThrows(IOException.class, () -> ScopeDDataCodec.decode(Arrays.copyOf(frame, frame.length + 1)));
        for (int offset : List.of(4, 6, 7, 8)) {
            var unsupportedVersion = frame.clone();
            unsupportedVersion[offset] = 2;
            assertThrows(IOException.class, () -> ScopeDDataCodec.decode(unsupportedVersion));
        }
        var wrongRootFamily = frame.clone();
        wrongRootFamily[5] = 2;
        assertThrows(IOException.class, () -> ScopeDDataCodec.decode(wrongRootFamily));
        var wrongIdentityFamily = frame.clone();
        wrongIdentityFamily[9] = 2;
        assertThrows(IOException.class, () -> ScopeDDataCodec.decode(wrongIdentityFamily));
    }

    private static SpellDraft unknownBlocksDraft(List<Block> blocks) {
        return new SpellDraft(SPELL, Optional.empty(), GENERATION, body(new Absent<>(),
                new Known<>(List.of()), new Known<>(List.of(handler(blocks))), new Absent<>(), new Absent<>()));
    }

    private static SpellDefinition largeDocument(int length) throws IOException {
        var empty = sizedBlocks(0);
        var minimum = ScopeDDataCodec.encode(empty).length;
        return sizedBlocks(length - minimum);
    }

    private static SpellDefinition sizedBlocks(int contentBytes) {
        var blocks = new ArrayList<Block>();
        for (int i = 1; i <= MAX_BLOCKS; i++) {
            int first = Math.min(contentBytes, MAX_TEXT_BYTES);
            contentBytes -= first;
            int second = Math.min(contentBytes, MAX_TEXT_BYTES);
            contentBytes -= second;
            blocks.add(new EffectBlock(new BlockId(i), new Descriptor(EFFECT.tag(), EFFECT.version(), List.of(
                    new Argument("first", new TextValue("a".repeat(first))),
                    new Argument("second", new TextValue("b".repeat(second)))))));
        }
        assertEquals(0, contentBytes);
        return spell(body(new Absent<>(), new Known<>(List.of()), new Known<>(List.of(handler(blocks))),
                new Absent<>(), new Absent<>()));
    }

    private static SpellBody emptyBody() {
        return body(new Absent<>(), new Known<>(List.of()), new Known<>(List.of()), new Absent<>(), new Absent<>());
    }

    private static SpellBody body(Slot<Descriptor> activation, Slot<List<Setting>> settings,
                                  Slot<List<Handler>> handlers, Slot<Descriptor> appearance, Slot<Descriptor> phenomenon) {
        return new SpellBody(new Known<>(new Backend(BACKEND, "gramarye:test_category", List.of())), activation,
                settings, handlers, appearance, phenomenon);
    }

    private static SpellDefinition spell(SpellBody body) {
        return new SpellDefinition(new SpellReference(SPELL, new SpellRevision(1)), body, Optional.empty());
    }

    private static SkillManifest manifest(List<ManifestEntry> entries) {
        return new SkillManifest(new ManifestReference(MANIFEST, new ManifestRevision(1)),
                new ManifestBody(new Known<>(entries), "bundle", true), Optional.empty());
    }

    private static ManifestEntry entry(long id, SpellIdentity target, String label) {
        return new ManifestEntry(new ManifestEntryId(id), new SpellIdentityReference(target), label, "group");
    }

    private static Handler handler(List<? extends Block> blocks) {
        return new Handler(new HandlerId(1), "gramarye:test_event", List.copyOf(blocks));
    }

    private static RawEnvelope raw(Identity identity, RawPosition position, SerializedTreeContext context, byte[] bytes)
            throws IOException {
        return RawEnvelope.fromBytes(new RawLocation(identity, position), "gramarye:unknown", 2, context, bytes);
    }

    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static byte[] jsonString(int size) { return utf8("\"" + "x".repeat(size - 2) + "\""); }

    private static String jsonArray(int elements) {
        return "[" + String.join(",", Collections.nCopies(elements, "0")) + "]";
    }

    private static String nested(int depth) { return "[".repeat(depth - 1) + "0" + "]".repeat(depth - 1); }
}
