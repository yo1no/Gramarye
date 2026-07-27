package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
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
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SkillDraftPersistenceFacadeTest {
    private static final SkillId SKILL_ID =
            new SkillId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private static final HolderLookup.Provider PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void persistedCaptureRejectsBeforeCopyAndDefensivelyIsolatesAcceptedBytes() {
        var calls = new AtomicInteger();
        var over = SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES + 1,
                () -> {
                    calls.incrementAndGet();
                    return new byte[1];
                });
        var capacity = assertInstanceOf(
                SkillDraftPersistenceFacade.CapacityFailure.class,
                assertInstanceOf(
                                SkillDraftPersistenceFacade.CaptureRejected.class, over)
                        .failure());

        var source = new byte[] {1, 2, 3};
        var captured = assertInstanceOf(
                SkillDraftPersistenceFacade.Captured.class,
                SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                        source.length,
                        () -> source));
        source[0] = 99;
        var firstCopy = captured.draft().copyBytes();
        firstCopy[1] = 88;
        var exactBytes = new byte[MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES];
        var exact = assertInstanceOf(
                SkillDraftPersistenceFacade.Captured.class,
                SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                        exactBytes.length,
                        () -> exactBytes));

        assertAll(
                () -> assertEquals(0, calls.get()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES + 1L,
                        capacity.observedAtLeast()),
                () -> assertArrayEquals(new byte[] {1, 2, 3}, captured.draft().copyBytes()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES,
                        exact.draft().byteCount()),
                () -> assertFalse(captured.draft().toString().contains(
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING)),
                () -> assertInstanceOf(
                        SkillDraftPersistenceFacade.CaptureRejected.class,
                        SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                                SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                                2,
                                () -> new byte[3])));
    }

    @Test
    void persistedCaptureAcceptsTheExactDraftEntryMaximum() {
        var maximum = MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES;
        var source = new byte[maximum];
        source[maximum - 1] = 7;

        var captured = assertInstanceOf(
                SkillDraftPersistenceFacade.Captured.class,
                SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                        source.length,
                        () -> source));
        source[maximum - 1] = 0;

        assertAll(
                () -> assertEquals(maximum, captured.draft().byteCount()),
                () -> assertEquals(7, captured.draft().copyBytes()[maximum - 1]));
    }

    @Test
    void currentRoundTripPreservesEmptyDraftAndBaseRevision() {
        var draft = new SkillDraft(
                0,
                SKILL_ID,
                Optional.of(new SkillRevision(17)),
                List.of(),
                AppearanceDocument.defaultAppearance());

        var encoded = encoded(draft);
        var loaded = loaded(encoded, Optional.empty());

        assertAll(
                () -> assertEquals(draft, loaded.draft()),
                () -> assertFalse(loaded.physicalMigrated()),
                () -> assertFalse(loaded.logicalMigrated()),
                () -> assertEquals(
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                        encoded.draftEncoding()),
                () -> assertTrue(encoded.byteCount() > 0));
    }

    @Test
    void currentRoundTripPreservesAllSlotStatesAndMixedFamilyContexts() {
        var json = new JsonObject();
        json.addProperty("json_secret", "preserve");
        var nbt = new CompoundTag();
        nbt.putInt("nbt_secret", 7);
        var jsonRegistryCompressed = RegistryOps.create(JsonOps.COMPRESSED, PROVIDER);
        var nbtRegistry = RegistryOps.create(NbtOps.INSTANCE, PROVIDER);

        var missingMissing = new DraftNode(
                DraftTriggerSlot.missing(),
                DraftActionSlot.missing(),
                AppearanceOverrideDocument.none());
        var presentMissing = new DraftNode(
                DraftTriggerSlot.present(envelope(
                        "trigger_json", new Dynamic<>(jsonRegistryCompressed, json))),
                DraftActionSlot.missing(),
                AppearanceOverrideDocument.none());
        var missingPresent = new DraftNode(
                DraftTriggerSlot.missing(),
                DraftActionSlot.present(envelope(
                        "action_nbt", new Dynamic<>(nbtRegistry, nbt))),
                unparsedOverride(new Dynamic<>(NbtOps.INSTANCE, nbt)));
        var presentPresent = new DraftNode(
                DraftTriggerSlot.present(envelope(
                        "trigger_plain", new Dynamic<>(JsonOps.INSTANCE, new JsonObject()))),
                DraftActionSlot.present(envelope(
                        "action_plain", new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()))),
                AppearanceOverrideDocument.none());
        var draft = new SkillDraft(
                0,
                SKILL_ID,
                Optional.empty(),
                List.of(missingMissing, presentMissing, missingPresent, presentPresent),
                unparsedAppearance(new Dynamic<>(JsonOps.INSTANCE, json)));

        var loaded = loaded(encoded(draft), Optional.of(PROVIDER)).draft();
        var loadedTrigger = ((DraftTriggerSlot.Present) loaded.nodes().get(1).trigger())
                .definition().copyRawPayload();
        var loadedAction = ((DraftActionSlot.Present) loaded.nodes().get(2).action())
                .definition().copyRawPayload();

        assertAll(
                () -> assertEquals(draft, loaded),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, true, true),
                        SupportedDynamicTrees.contextOf(loadedTrigger).getOrThrow()),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.NBT, true, false),
                        SupportedDynamicTrees.contextOf(loadedAction).getOrThrow()),
                () -> assertInstanceOf(DraftTriggerSlot.Missing.class,
                        loaded.nodes().get(0).trigger()),
                () -> assertInstanceOf(DraftActionSlot.Missing.class,
                        loaded.nodes().get(0).action()));
    }

    @Test
    void physicalV0UsesExactFieldsAndStrictDuplicateScanRunsBeforeMaterialization()
            throws Exception {
        var encoded = encoded(simpleDraft());
        var root = PhysicalSkillDraftNbt.decodeEncoded(encoded.copyInternalBytes());
        var physical = PhysicalSkillDraftNbt.decode(root);
        var nodes = assertInstanceOf(ListTag.class, root.get("nodes"));
        var node = assertInstanceOf(CompoundTag.class, nodes.get(0));

        assertAll(
                () -> assertEquals(
                        java.util.Set.of(
                                "draft_schema_version", "skill_id", "nodes", "appearance"),
                        root.getAllKeys()),
                () -> assertEquals(java.util.Set.of("trigger", "action"), node.getAllKeys()),
                () -> assertEquals(SKILL_ID, physical.skillId()),
                () -> assertInstanceOf(StringTag.class, root.get("skill_id")));

        var duplicate = addDuplicateRootInt(
                encoded.copyBytes(), "draft_schema_version", 0);
        var captured = assertInstanceOf(
                SkillDraftPersistenceFacade.Captured.class,
                SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                        duplicate.length,
                        () -> duplicate));
        var rejected = assertInstanceOf(
                SkillDraftPersistenceFacade.LoadRejected.class,
                SkillDraftPersistenceFacade.loadAlwaysMigrating(
                        captured.draft(), Optional.empty()));
        assertEquals(
                SkillDraftPersistenceFacade.FailureCode.DRAFT_DECODE_FAILED,
                rejected.failure().code());
    }

    @Test
    void physicalMigrationIsIndependentAlwaysAppliedAndErrorIsNotCaught() {
        var current = encoded(simpleDraft());
        var legacy = assertInstanceOf(
                SkillDraftPersistenceFacade.Captured.class,
                SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                        "legacy_family_v0", current.byteCount(), current::copyBytes)).draft();
        var plan = DraftPersistenceMigration.Plan.of(List.of(
                new DraftPersistenceMigration.Step(
                        "legacy_family_v0",
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                        source -> source)));

        var loaded = assertInstanceOf(
                SkillDraftPersistenceFacade.Loaded.class,
                SkillDraftPersistenceBridge.loadAlwaysMigrating(
                        legacy,
                        Optional.empty(),
                        plan,
                        SkillDraftLogicalMigration.Plans.production()));
        var missing = assertInstanceOf(
                SkillDraftPersistenceFacade.LoadRejected.class,
                SkillDraftPersistenceBridge.loadAlwaysMigrating(
                        legacy,
                        Optional.empty(),
                        DraftPersistenceMigration.Plan.of(List.of()),
                        SkillDraftLogicalMigration.Plans.production()));
        var publicUnsupported = assertInstanceOf(
                SkillDraftPersistenceFacade.LoadRejected.class,
                SkillDraftPersistenceFacade.loadAlwaysMigrating(legacy, Optional.empty()));
        var runtime = assertInstanceOf(
                DraftPersistenceMigration.ExceptionFailure.class,
                DraftPersistenceMigration.migrate(
                        legacy,
                        DraftPersistenceMigration.Plan.of(List.of(
                                new DraftPersistenceMigration.Step(
                                        "legacy_family_v0",
                                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                                        source -> {
                                            throw new IllegalStateException("must-not-leak");
                                        })))));

        assertAll(
                () -> assertTrue(loaded.physicalMigrated()),
                () -> assertEquals(simpleDraft(), loaded.draft()),
                () -> assertEquals(
                        SkillDraftPersistenceFacade.FailureCode.DRAFT_PHYSICAL_MIGRATION_FAILED,
                        missing.failure().code()),
                () -> assertEquals(
                        SkillDraftPersistenceFacade.FailureCode.DRAFT_PHYSICAL_MIGRATION_FAILED,
                        publicUnsupported.failure().code()),
                () -> assertEquals(
                        IllegalStateException.class.getName(), runtime.exceptionClassName()),
                () -> assertFalse(runtime.exceptionClassName().contains("must-not-leak")),
                () -> assertThrows(AssertionError.class, () -> DraftPersistenceMigration.migrate(
                        legacy,
                        DraftPersistenceMigration.Plan.of(List.of(
                                new DraftPersistenceMigration.Step(
                                        "legacy_family_v0",
                                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                                        source -> {
                                            throw new AssertionError("passthrough");
                                        }))))));
    }

    @Test
    void logicalMigrationSeesOnlyTokensAndReinsertionRejectsRawMutationOrRelocation()
            throws Exception {
        var encoded = encoded(simpleDraft());
        var physical = PhysicalSkillDraftNbt.decode(
                PhysicalSkillDraftNbt.decodeEncoded(encoded.copyInternalBytes()));
        var built = assertInstanceOf(
                LogicalSkillDraftConformanceView.Built.class,
                LogicalSkillDraftConformanceView.build(physical));
        var logical = built.logicalTree();
        var payloadParent = definition(logical, 0, "action");
        assertInstanceOf(StringTag.class, payloadParent.get("payload"));

        var plan = SkillDraftLogicalMigration.Plan.of(List.of(
                new SkillDraftLogicalMigration.Step(0, 1, source -> {
                    assertInstanceOf(StringTag.class,
                            definition(source, 0, "action").get("payload"));
                    source.putInt("draft_schema_version", 1);
                    return source;
                })));
        var migrated = assertInstanceOf(
                SkillDraftLogicalMigration.Success.class,
                SkillDraftLogicalMigration.migrate(logical, plan, 1));
        var reinserted = assertInstanceOf(
                LogicalSkillDraftConformanceView.Reinserted.class,
                LogicalSkillDraftConformanceView.reinsert(migrated.draft(), built.table()));
        var runtime = assertInstanceOf(
                SkillDraftLogicalMigration.ExceptionFailure.class,
                SkillDraftLogicalMigration.migrate(
                        logical,
                        SkillDraftLogicalMigration.Plan.of(List.of(
                                new SkillDraftLogicalMigration.Step(0, 1, source -> {
                                    throw new IllegalArgumentException("must-not-leak");
                                }))),
                        1));

        var tokenMutated = logical.copy();
        definition(tokenMutated, 0, "action").putString("payload", "wrong-token");
        var relocated = logical.copy();
        ((ListTag) relocated.get("nodes")).clear();

        assertAll(
                () -> assertEquals(1, reinserted.draft().draftSchemaVersion()),
                () -> assertEquals(
                        IllegalArgumentException.class.getName(), runtime.exceptionClassName()),
                () -> assertFalse(runtime.exceptionClassName().contains("must-not-leak")),
                () -> assertInstanceOf(
                        LogicalSkillDraftConformanceView.ReinsertionFailure.class,
                        LogicalSkillDraftConformanceView.reinsert(tokenMutated, built.table())),
                () -> assertInstanceOf(
                        LogicalSkillDraftConformanceView.ReinsertionFailure.class,
                        LogicalSkillDraftConformanceView.reinsert(relocated, built.table())),
                () -> assertInstanceOf(
                        SkillDraftLogicalMigration.Failure.class,
                        SkillDraftLogicalMigration.migrate(
                                logical,
                                SkillDraftLogicalMigration.Plan.of(List.of()),
                                1)),
                () -> assertThrows(AssertionError.class, () -> SkillDraftLogicalMigration.migrate(
                        logical,
                        SkillDraftLogicalMigration.Plan.of(List.of(
                                new SkillDraftLogicalMigration.Step(0, 1, source -> {
                                    throw new AssertionError("passthrough");
                                }))),
                        1)));
    }

    @Test
    void inputAndEncodedHandleMutationNeverAliasesLoadedState() {
        var draft = simpleDraft();
        var encoded = encoded(draft);
        var first = encoded.copyBytes();
        first[0] = 0;
        var loaded = loaded(encoded, Optional.empty()).draft();

        assertAll(
                () -> assertEquals(draft, loaded),
                () -> assertNotEquals(0, Byte.toUnsignedInt(encoded.copyBytes()[0])),
                () -> assertTrue(encoded.toString().length() < 96));
    }

    private static SkillDraft simpleDraft() {
        return new SkillDraft(
                0,
                SKILL_ID,
                Optional.empty(),
                List.of(new DraftNode(
                        DraftTriggerSlot.missing(),
                        DraftActionSlot.present(envelope(
                                "action", new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()))),
                        AppearanceOverrideDocument.none())),
                AppearanceDocument.defaultAppearance());
    }

    private static DefinitionEnvelope envelope(String path, Dynamic<?> payload) {
        return new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", path), 0, payload);
    }

    private static AppearanceDocument.Unparsed unparsedAppearance(Dynamic<?> raw) {
        return new AppearanceDocument.Unparsed(AppearanceRawSnapshot.capture(raw).getOrThrow());
    }

    private static AppearanceOverrideDocument.Unparsed unparsedOverride(Dynamic<?> raw) {
        return new AppearanceOverrideDocument.Unparsed(
                AppearanceRawSnapshot.capture(raw).getOrThrow());
    }

    private static SkillDraftPersistenceFacade.EncodedSkillDraft encoded(SkillDraft draft) {
        return assertInstanceOf(
                SkillDraftPersistenceFacade.Encoded.class,
                SkillDraftPersistenceFacade.encodeCurrent(draft)).draft();
    }

    private static SkillDraftPersistenceFacade.Loaded loaded(
            SkillDraftPersistenceFacade.EncodedSkillDraft encoded,
            Optional<HolderLookup.Provider> provider) {
        return assertInstanceOf(
                SkillDraftPersistenceFacade.Loaded.class,
                SkillDraftPersistenceFacade.loadAlwaysMigrating(encoded, provider));
    }

    private static CompoundTag definition(CompoundTag root, int nodeIndex, String side) {
        var nodes = (ListTag) root.get("nodes");
        var node = (CompoundTag) nodes.get(nodeIndex);
        var slot = (CompoundTag) node.get(side);
        return (CompoundTag) slot.get("definition");
    }

    private static byte[] addDuplicateRootInt(byte[] encoded, String name, int value)
            throws Exception {
        assertEquals(Tag.TAG_END, Byte.toUnsignedInt(encoded[encoded.length - 1]));
        var output = new ByteArrayOutputStream(encoded.length + name.length() + 8);
        output.write(encoded, 0, encoded.length - 1);
        try (var data = new DataOutputStream(output)) {
            data.writeByte(Tag.TAG_INT);
            data.writeUTF(name);
            data.writeInt(value);
            data.writeByte(Tag.TAG_END);
        }
        return output.toByteArray();
    }
}
