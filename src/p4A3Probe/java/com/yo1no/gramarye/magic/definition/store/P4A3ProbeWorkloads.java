package com.yo1no.gramarye.magic.definition.store;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.P4A3ProbeDocumentSupport;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.submission.P4A3ProbePlanFactory;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

/** Deterministic test-only P4-A3 Store workloads; no probe performs runtime size search. */
final class P4A3ProbeWorkloads {
    static final int MANY_SMALL_HISTORY_COUNT = 4_095;
    static final int MANY_SMALL_REVISION_COUNT = 32_767;
    static final int LARGE_HISTORY_COUNT = 8;
    static final int LARGE_REVISIONS_PER_HISTORY = 8;

    // Fixed fixture dimensions are calibrated by the phase-local tests and full probes.
    static final int MANY_SMALL_PADDING_BYTES = 728;
    static final int NEAR_ENTRY_PADDING_BYTES = 259_000;
    static final int MIXED_PADDING_BYTES = 147_261;

    private static final long SKILL_ID_BASE = 0x4A_30_00_00L;
    private static final long OWNER_ID_BASE = 0x4A_31_00_00L;
    private static final ResourceLocation TRIGGER_TYPE = id("p4_a3_trigger");
    private static final ResourceLocation ACTION_TYPE = id("p4_a3_action");

    private P4A3ProbeWorkloads() {
    }

    static WorkloadShape shape(String workload) {
        return switch (workload) {
            case "many-small" -> new WorkloadShape(
                    workload,
                    MANY_SMALL_HISTORY_COUNT,
                    MANY_SMALL_REVISION_COUNT,
                    MANY_SMALL_PADDING_BYTES,
                    66_062_342,
                    false);
            case "near-entry" -> new WorkloadShape(
                    workload,
                    LARGE_HISTORY_COUNT,
                    LARGE_HISTORY_COUNT * LARGE_REVISIONS_PER_HISTORY,
                    NEAR_ENTRY_PADDING_BYTES,
                    66_367_484,
                    false);
            case "mixed" -> new WorkloadShape(
                    workload,
                    LARGE_HISTORY_COUNT,
                    LARGE_HISTORY_COUNT * LARGE_REVISIONS_PER_HISTORY,
                    MIXED_PADDING_BYTES,
                    66_060_348,
                    false);
            case "dedicated-mixed" -> new WorkloadShape(
                    workload,
                    LARGE_HISTORY_COUNT,
                    LARGE_HISTORY_COUNT * LARGE_REVISIONS_PER_HISTORY,
                    MIXED_PADDING_BYTES,
                    66_060_348,
                    true);
            default -> throw new IllegalArgumentException("unknown P4-A3 workload: " + workload);
        };
    }

    static P4A3ProbeWorkload create(
            String workload,
            Optional<HolderLookup.Provider> provider) {
        return switch (workload) {
            case "many-small" -> manySmall();
            case "near-entry" -> nearEntry();
            case "mixed" -> mixed(Optional.empty());
            case "dedicated-mixed" -> mixed(Optional.of(
                    provider.orElseThrow(() -> new IllegalArgumentException(
                            "dedicated mixed workload requires a registry provider"))));
            default -> throw new IllegalArgumentException("unknown P4-A3 workload: " + workload);
        };
    }

    static P4A3ProbeWorkload smallDeterminismFixture() {
        var shared = List.of(new NodeDocument(
                envelope("small_trigger", json(JsonOps.INSTANCE, 8, (byte) 0x11)),
                envelope("small_action", nbt(NbtOps.INSTANCE, 8, (byte) 0x22)),
                AppearanceOverrideDocument.none()));
        return existingWorkload(
                "determinism",
                2,
                2,
                shared,
                AppearanceDocument.defaultAppearance(),
                Set.of(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                        new SerializedTreeContext(SerializedTreeFamily.NBT, false, false)),
                false);
    }

    private static P4A3ProbeWorkload manySmall() {
        var sharedNodes = List.of(new NodeDocument(
                new DefinitionEnvelope(
                        TRIGGER_TYPE,
                        0,
                        json(JsonOps.INSTANCE, MANY_SMALL_PADDING_BYTES, (byte) 0x31)),
                new DefinitionEnvelope(
                        ACTION_TYPE,
                        0,
                        json(JsonOps.INSTANCE, MANY_SMALL_PADDING_BYTES, (byte) 0x32)),
                AppearanceOverrideDocument.none()));

        var histories = new ArrayList<SkillHistorySnapshot>(MANY_SMALL_HISTORY_COUNT);
        for (var historyIndex = 0; historyIndex < MANY_SMALL_HISTORY_COUNT; historyIndex++) {
            var revisionCount = historyIndex < 7 ? 9 : 8;
            histories.add(history(
                    historyIndex,
                    revisionCount,
                    sharedNodes,
                    AppearanceDocument.defaultAppearance()));
        }
        var store = restored(histories);
        var proposedId = skillId(MANY_SMALL_HISTORY_COUNT);
        var proposedOwner = ownerId(16);
        var proposedDocument = document(
                proposedId,
                0,
                smallPlanNodes(Optional.empty()),
                AppearanceDocument.defaultAppearance());
        var plan = P4A3ProbePlanFactory.forNew(proposedOwner, proposedDocument);
        return new P4A3ProbeWorkload(
                "many-small",
                store,
                plan,
                MANY_SMALL_HISTORY_COUNT,
                MANY_SMALL_REVISION_COUNT,
                MANY_SMALL_HISTORY_COUNT + 1,
                MANY_SMALL_REVISION_COUNT + 1,
                new SkillReference(skillId(0), new SkillRevision(8)),
                new SkillReference(
                        skillId(MANY_SMALL_HISTORY_COUNT - 1),
                        new SkillRevision(7)),
                Set.of(new SerializedTreeContext(
                        SerializedTreeFamily.JSON, false, false)),
                false);
    }

    private static P4A3ProbeWorkload nearEntry() {
        var sharedNodes = List.of(
                new NodeDocument(
                        envelope("near_json_0", json(
                                JsonOps.INSTANCE,
                                NEAR_ENTRY_PADDING_BYTES,
                                (byte) 0x41)),
                        envelope("near_nbt_0", nbt(
                                NbtOps.INSTANCE,
                                NEAR_ENTRY_PADDING_BYTES,
                                (byte) 0x42)),
                        AppearanceOverrideDocument.none()),
                new NodeDocument(
                        envelope("near_json_1", json(
                                JsonOps.INSTANCE,
                                NEAR_ENTRY_PADDING_BYTES,
                                (byte) 0x43)),
                        envelope("near_nbt_1", nbt(
                                NbtOps.INSTANCE,
                                NEAR_ENTRY_PADDING_BYTES,
                                (byte) 0x44)),
                        AppearanceOverrideDocument.none()));
        return existingWorkload(
                "near-entry",
                LARGE_HISTORY_COUNT,
                LARGE_REVISIONS_PER_HISTORY,
                sharedNodes,
                AppearanceDocument.defaultAppearance(),
                Set.of(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                        new SerializedTreeContext(SerializedTreeFamily.NBT, false, false)),
                false);
    }

    private static P4A3ProbeWorkload mixed(
            Optional<HolderLookup.Provider> provider) {
        var registry = provider.isPresent();
        DynamicOps<JsonElement> firstJson = registry
                ? RegistryOps.create(JsonOps.INSTANCE, provider.orElseThrow())
                : JsonOps.INSTANCE;
        DynamicOps<Tag> firstNbt = registry
                ? RegistryOps.create(NbtOps.INSTANCE, provider.orElseThrow())
                : NbtOps.INSTANCE;
        DynamicOps<JsonElement> compressedJson = registry
                ? RegistryOps.create(JsonOps.COMPRESSED, provider.orElseThrow())
                : JsonOps.COMPRESSED;
        DynamicOps<Tag> topNbt = registry
                ? RegistryOps.create(NbtOps.INSTANCE, provider.orElseThrow())
                : NbtOps.INSTANCE;

        var sharedNodes = List.of(
                new NodeDocument(
                        envelope("mixed_json_registry", json(
                                firstJson, MIXED_PADDING_BYTES, (byte) 0x51)),
                        envelope("mixed_nbt_registry", nbt(
                                firstNbt, MIXED_PADDING_BYTES, (byte) 0x52)),
                        P4A3ProbeDocumentSupport.unparsedOverride(json(
                                compressedJson, MIXED_PADDING_BYTES, (byte) 0x53))),
                new NodeDocument(
                        envelope("mixed_json_plain", json(
                                JsonOps.INSTANCE, MIXED_PADDING_BYTES, (byte) 0x54)),
                        envelope("mixed_nbt_plain", nbt(
                                NbtOps.INSTANCE, MIXED_PADDING_BYTES, (byte) 0x55)),
                        P4A3ProbeDocumentSupport.unparsedOverride(json(
                                JsonOps.COMPRESSED, MIXED_PADDING_BYTES, (byte) 0x56))));
        var appearance = P4A3ProbeDocumentSupport.unparsedAppearance(nbt(
                topNbt, MIXED_PADDING_BYTES, (byte) 0x57));
        var contexts = registry
                ? Set.of(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, true, false),
                        new SerializedTreeContext(SerializedTreeFamily.JSON, true, true),
                        new SerializedTreeContext(SerializedTreeFamily.NBT, true, false),
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, true),
                        new SerializedTreeContext(SerializedTreeFamily.NBT, false, false))
                : Set.of(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, true),
                        new SerializedTreeContext(SerializedTreeFamily.NBT, false, false));
        return existingWorkload(
                registry ? "dedicated-mixed" : "mixed",
                LARGE_HISTORY_COUNT,
                LARGE_REVISIONS_PER_HISTORY,
                sharedNodes,
                appearance,
                contexts,
                registry);
    }

    private static P4A3ProbeWorkload existingWorkload(
            String name,
            int historyCount,
            int revisionsPerHistory,
            List<NodeDocument> sharedNodes,
            AppearanceDocument appearance,
            Set<SerializedTreeContext> contexts,
            boolean registryContextsRequired) {
        var histories = new ArrayList<SkillHistorySnapshot>(historyCount);
        for (var index = 0; index < historyCount; index++) {
            histories.add(history(index, revisionsPerHistory, sharedNodes, appearance));
        }
        var store = restored(histories);
        var targetId = skillId(0);
        var targetOwner = ownerId(0);
        var latest = new SkillReference(targetId, new SkillRevision(revisionsPerHistory - 1));
        var proposed = document(
                targetId,
                revisionsPerHistory,
                smallPlanNodes(Optional.empty()),
                AppearanceDocument.defaultAppearance());
        var plan = P4A3ProbePlanFactory.forExisting(targetOwner, latest, proposed);
        return new P4A3ProbeWorkload(
                name,
                store,
                plan,
                historyCount,
                historyCount * revisionsPerHistory,
                historyCount,
                historyCount * revisionsPerHistory + 1,
                latest,
                new SkillReference(
                        skillId(historyCount - 1),
                        new SkillRevision(revisionsPerHistory - 1)),
                contexts,
                registryContextsRequired);
    }

    private static SkillHistorySnapshot history(
            int historyIndex,
            int revisionCount,
            List<NodeDocument> sharedNodes,
            AppearanceDocument appearance) {
        var skillId = skillId(historyIndex);
        var revisions = new ArrayList<SkillRevisionSnapshot>(revisionCount);
        for (var revisionValue = 0; revisionValue < revisionCount; revisionValue++) {
            var revision = new SkillRevision(revisionValue);
            revisions.add(new SkillRevisionSnapshot(
                    revision,
                    new SkillDocument(
                            SkillDocument.CURRENT_SCHEMA_VERSION,
                            skillId,
                            revision,
                            sharedNodes,
                            appearance)));
        }
        return new SkillHistorySnapshot(
                skillId,
                ownerId(historyIndex / 256),
                revisions);
    }

    private static SkillDefinitionStore restored(List<SkillHistorySnapshot> histories) {
        return switch (SkillDefinitionStore.restore(
                new SkillDefinitionStoreSnapshot(histories))) {
            case SkillDefinitionStoreRestoreResult.Restored restored -> restored.store();
            case SkillDefinitionStoreRestoreResult.Rejected rejected ->
                    throw new AssertionError("probe fixture restore rejected: " + rejected.failure());
        };
    }

    private static List<NodeDocument> smallPlanNodes(
            Optional<HolderLookup.Provider> ignoredProvider) {
        return List.of(new NodeDocument(
                envelope("plan_trigger", json(JsonOps.INSTANCE, 8, (byte) 0x61)),
                envelope("plan_action", json(JsonOps.INSTANCE, 8, (byte) 0x62)),
                AppearanceOverrideDocument.none()));
    }

    private static SkillDocument document(
            SkillId skillId,
            int revision,
            List<NodeDocument> nodes,
            AppearanceDocument appearance) {
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                skillId,
                new SkillRevision(revision),
                nodes,
                appearance);
    }

    private static DefinitionEnvelope envelope(String path, Dynamic<?> raw) {
        return new DefinitionEnvelope(id(path), 0, raw);
    }

    private static Dynamic<JsonElement> json(
            DynamicOps<JsonElement> ops,
            int paddingBytes,
            byte fill) {
        var object = new JsonObject();
        object.addProperty("marker", Byte.toUnsignedInt(fill));
        object.addProperty("padding", Character.toString((char) ('a' + (fill & 15)))
                .repeat(paddingBytes));
        return new Dynamic<>(ops, object);
    }

    private static Dynamic<Tag> nbt(
            DynamicOps<Tag> ops,
            int paddingBytes,
            byte fill) {
        var compound = new CompoundTag();
        compound.putByte("marker", fill);
        var character = Character.toString((char) ('a' + (fill & 15)));
        var remaining = paddingBytes;
        var index = 0;
        while (remaining > 0) {
            var length = Math.min(60_000, remaining);
            compound.putString("padding_" + index, character.repeat(length));
            remaining -= length;
            index++;
        }
        return new Dynamic<>(ops, compound);
    }

    private static SkillId skillId(long offset) {
        return new SkillId(new UUID(0x5044_4133_0000_0000L, SKILL_ID_BASE + offset));
    }

    private static SkillOwnerId ownerId(long offset) {
        return new SkillOwnerId(new UUID(0x5044_4133_0000_0001L, OWNER_ID_BASE + offset));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    record WorkloadShape(
            String name,
            int historyCount,
            int revisionCount,
            int paddingBytes,
            int expectedBaseBlobBytes,
            boolean requiresRegistryProvider) {
    }
}

record P4A3ProbeWorkload(
        String name,
        SkillDefinitionStore store,
        SkillSubmissionPlan plan,
        int expectedHistoryCount,
        int expectedRevisionCount,
        int expectedProspectiveHistoryCount,
        int expectedProspectiveRevisionCount,
        SkillReference expectedFirstLatest,
        SkillReference expectedLastLatest,
        Set<SerializedTreeContext> expectedContexts,
        boolean registryContextsRequired) {
    P4A3ProbeWorkload {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(store, "store");
        java.util.Objects.requireNonNull(plan, "plan");
        java.util.Objects.requireNonNull(expectedFirstLatest, "expectedFirstLatest");
        java.util.Objects.requireNonNull(expectedLastLatest, "expectedLastLatest");
        expectedContexts = Set.copyOf(java.util.Objects.requireNonNull(
                expectedContexts, "expectedContexts"));
    }
}
