package com.yo1no.gramarye.magic.definition.player;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.store.P4C2StoreProbe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Deterministic three-world playerdata fixture construction. */
final class P4C2FixtureBuilder {
    static final int PRESERVED_PAYLOAD_BYTES = 16_777_211;
    static final int OVERSIZE_PAYLOAD_BYTES = 16_777_212;
    static final long PRESERVED_ATTACHMENT_BYTES = 16_777_216L;
    static final long OVERSIZE_ATTACHMENT_BYTES = 16_777_217L;

    static final SkillId READY_DRAFT_ID = new SkillId(
            UUID.fromString("c2b10000-0000-4000-8000-000000000001"));
    static final SkillId READY_EMPTY_ID = new SkillId(
            UUID.fromString("c2b10000-0000-4000-8000-000000000002"));
    static final SkillId READY_OTHER_ID = new SkillId(
            UUID.fromString("c2b10000-0000-4000-8000-000000000003"));
    static final SkillId READY_INITIAL_EDITOR_ID = new SkillId(
            UUID.fromString("c2b10000-0000-4000-8000-000000000004"));
    static final SkillId READY_FINAL_EDITOR_ID = new SkillId(
            UUID.fromString("c2b10000-0000-4000-8000-000000000005"));
    static final SkillReference READY_INITIAL_REFERENCE =
            new SkillReference(READY_DRAFT_ID, new SkillRevision(4));
    static final SkillReference READY_FINAL_REFERENCE =
            new SkillReference(READY_DRAFT_ID, new SkillRevision(5));
    static final SkillReference READY_OTHER_REFERENCE =
            new SkillReference(READY_OTHER_ID, new SkillRevision(8));

    private P4C2FixtureBuilder() {
    }

    static void prepareWorlds(
            Path readyGameDirectory,
            Path preservedGameDirectory,
            Path oversizeGameDirectory) throws IOException {
        prepareReady(readyGameDirectory);
        preparePreserved(preservedGameDirectory);
        prepareOversize(oversizeGameDirectory);
    }

    static PlayerSkillAttachmentReady readyState(boolean finalState) {
        var draft = readyDraft(finalState ? 2 : 1);
        var encodedResult = SkillDraftPersistenceFacade.encodeCurrent(draft);
        if (!(encodedResult instanceof SkillDraftPersistenceFacade.Encoded encoded)) {
            throw new AssertionError("P4-C2 Ready Draft did not encode");
        }
        var present = finalState ? READY_FINAL_REFERENCE : READY_INITIAL_REFERENCE;
        var drafts = List.of(new PlayerDraftEntry(
                draft.skillId(), draft, encoded.draft()));
        var latest = List.of(
                new PlayerLatestState(
                        READY_DRAFT_ID, Optional.of(present), finalState ? 2 : 1),
                new PlayerLatestState(READY_EMPTY_ID, Optional.empty(), 3));
        var equipped = List.of(
                new EquippedSkillReference(1, present),
                new EquippedSkillReference(8, READY_OTHER_REFERENCE));
        var editor = new PlayerSkillEditorState(
                Optional.of(finalState
                        ? READY_FINAL_EDITOR_ID : READY_INITIAL_EDITOR_ID),
                OptionalInt.of(finalState ? 254 : 255));
        var rebuilt = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                drafts, latest, equipped, editor);
        if (!(rebuilt instanceof PlayerSkillAttachmentBuildResult.Built built)) {
            throw new AssertionError("P4-C2 Ready fixture failed universal rebuild");
        }
        return built.ready();
    }

    /** Derives the sole READY Store truth from the actual initial and final Attachment states. */
    static P4C2StoreProbe.ReadyStoreTruth readyStoreTruth() {
        return readyStoreTruth(readyState(false), readyState(true));
    }

    static P4C2StoreProbe.StoreFacts requireReadyPrimary(Path worldRoot) {
        return P4C2StoreProbe.verifyReadyCanonical(worldRoot, readyStoreTruth());
    }

    static P4C2StoreProbe.StoreFacts requireReadyLive(MinecraftServer server) {
        return P4C2StoreProbe.requireReadyLive(server, readyStoreTruth());
    }

    static SkillDraft readyDraft(int value) {
        var json = new JsonObject();
        json.addProperty("family", "registry-json-trigger");
        json.addProperty("value", value);
        var nbt = new CompoundTag();
        nbt.putString("family", "nbt-action");
        nbt.putInt("value", value * 11);
        var trigger = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath(
                        "gramarye", "p4_c2_registry_trigger"),
                0,
                new Dynamic<>(RegistryOps.create(
                        JsonOps.COMPRESSED, RegistryAccess.EMPTY), json));
        var action = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath(
                        "gramarye", "p4_c2_nbt_action"),
                0,
                new Dynamic<>(NbtOps.INSTANCE, nbt));
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                READY_DRAFT_ID,
                Optional.of(new SkillRevision(value)),
                List.of(new DraftNode(
                        new DraftTriggerSlot.Present(trigger),
                        new DraftActionSlot.Present(action),
                        AppearanceOverrideDocument.None.INSTANCE)),
                AppearanceDocument.Default.INSTANCE);
    }

    static byte[] payload(int length) {
        if (length != PRESERVED_PAYLOAD_BYTES && length != OVERSIZE_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("P4-C2 payload length is not authoritative");
        }
        var payload = new byte[length];
        for (var index = 0; index < payload.length; index++) {
            payload[index] = (byte) (0x5A ^ index * 31 ^ index >>> 8);
        }
        return payload;
    }

    static long exactCount(Tag tag) {
        try {
            return switch (AttachmentTagSize.measure(tag)) {
                case AttachmentTagSizeResult.WithinLimit within -> within.exactByteCount();
                case AttachmentTagSizeResult.Exceeded exceeded -> exceeded.observedAtLeast();
            };
        } catch (IOException exception) {
            throw new AssertionError("P4-C2 production Attachment count failed", exception);
        }
    }

    private static void prepareReady(Path gameDirectory) throws IOException {
        var worldRoot = preparePlayerDirectory(gameDirectory, P4C2ProbeCase.READY);
        var initialState = readyState(false);
        var finalState = readyState(true);
        var storeTruth = readyStoreTruth(initialState, finalState);
        var source = initialState.carrier().copyTag();
        var expected = finalState.carrier().copyTag();
        var sourceBytes = exactCount(source);
        var expectedBytes = exactCount(expected);
        var playerdata = writePlayerdata(
                worldRoot, P4C2ProbeCase.READY, source,
                P4C2FixtureManifest.PREPARED_WITNESS);
        P4C2StoreProbe.prepareReady(worldRoot, storeTruth);
        P4C2FixtureManifest.first(
                P4C2ProbeCase.READY,
                sourceBytes,
                expectedBytes,
                P4C2Hashing.sha256(source),
                P4C2Hashing.sha256(expected),
                P4C2Hashing.sha256(expected),
                finalState.drafts().size(),
                finalState.latestStates().size(),
                finalState.equipped().size(),
                P4C2Hashing.sha256(playerdata),
                Files.size(playerdata),
                null).write(worldRoot);
    }

    private static P4C2StoreProbe.ReadyStoreTruth readyStoreTruth(
            PlayerSkillAttachmentReady initialState,
            PlayerSkillAttachmentReady finalState) {
        var references = new ArrayList<SkillReference>();
        var latestRouteCount = 0;
        var latestReferenceCount = 0;
        var equippedReferenceCount = 0;
        for (var state : List.of(initialState, finalState)) {
            latestRouteCount = Math.addExact(latestRouteCount, state.latestStates().size());
            for (var latest : state.latestStates()) {
                if (latest.pointer().isPresent()) {
                    references.add(latest.pointer().orElseThrow());
                    latestReferenceCount = Math.addExact(latestReferenceCount, 1);
                }
            }
            for (var equipped : state.equipped()) {
                references.add(equipped.reference());
                equippedReferenceCount = Math.addExact(equippedReferenceCount, 1);
            }
        }

        var distinctReferences = new ArrayList<>(new LinkedHashSet<>(references));
        distinctReferences.sort(Comparator
                .comparing((SkillReference reference) -> reference.skillId().value())
                .thenComparingInt(reference -> reference.revision().value()));
        var documents = distinctReferences.stream()
                .map(P4C2FixtureBuilder::readyStoreDocument)
                .toList();
        return new P4C2StoreProbe.ReadyStoreTruth(
                new SkillOwnerId(P4C2ProbeCase.READY.playerId()),
                documents,
                references,
                latestRouteCount,
                latestReferenceCount,
                equippedReferenceCount);
    }

    private static SkillDocument readyStoreDocument(SkillReference reference) {
        var revision = reference.revision().value();
        var triggerPayload = new JsonObject();
        triggerPayload.addProperty("family", "ready-store-json-trigger");
        triggerPayload.addProperty("revision", revision);
        var actionPayload = new CompoundTag();
        actionPayload.putString("family", "ready-store-nbt-action");
        actionPayload.putInt("revision", revision);
        var node = new NodeDocument(
                new DefinitionEnvelope(
                        ResourceLocation.fromNamespaceAndPath(
                                "gramarye", "p4_c2_ready_store_trigger"),
                        0,
                        new Dynamic<>(JsonOps.INSTANCE, triggerPayload)),
                new DefinitionEnvelope(
                        ResourceLocation.fromNamespaceAndPath(
                                "gramarye", "p4_c2_ready_store_action"),
                        0,
                        new Dynamic<>(NbtOps.INSTANCE, actionPayload)),
                AppearanceOverrideDocument.None.INSTANCE);
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                reference.skillId(),
                reference.revision(),
                List.of(node),
                AppearanceDocument.Default.INSTANCE);
    }

    private static void preparePreserved(Path gameDirectory) throws IOException {
        var worldRoot = preparePlayerDirectory(
                gameDirectory, P4C2ProbeCase.PRESERVED_RAW);
        var expectedStore = P4C2StoreProbe.readExpected(worldRoot);
        var primary = worldRoot.resolve("data")
                .resolve("gramarye_skill_definitions.dat");
        if (!Files.isRegularFile(primary)
                || !P4C2Hashing.sha256(primary)
                        .equals(expectedStore.sourcePrimaryChecksum())) {
            throw new AssertionError("prepare-worlds did not preserve the P4-B full primary");
        }
        var payload = payload(PRESERVED_PAYLOAD_BYTES);
        var source = new ByteArrayTag(payload);
        var count = exactCount(source);
        if (count != PRESERVED_ATTACHMENT_BYTES) {
            throw new AssertionError("exact PreservedRaw coordinate changed");
        }
        var payloadChecksum = P4C2Hashing.sha256(payload);
        var sourceChecksum = P4C2Hashing.sha256(source);
        var playerdata = writePlayerdata(
                worldRoot, P4C2ProbeCase.PRESERVED_RAW, source,
                P4C2FixtureManifest.PREPARED_WITNESS);
        P4C2FixtureManifest.first(
                P4C2ProbeCase.PRESERVED_RAW,
                count, count, sourceChecksum, sourceChecksum, payloadChecksum,
                P4C2FixtureManifest.UNAVAILABLE_COUNT,
                P4C2FixtureManifest.UNAVAILABLE_COUNT,
                P4C2FixtureManifest.UNAVAILABLE_COUNT,
                P4C2Hashing.sha256(playerdata), Files.size(playerdata), expectedStore)
                .write(worldRoot);
    }

    private static void prepareOversize(Path gameDirectory) throws IOException {
        var worldRoot = preparePlayerDirectory(gameDirectory, P4C2ProbeCase.OVERSIZE);
        var payload = payload(OVERSIZE_PAYLOAD_BYTES);
        var source = new ByteArrayTag(payload);
        var sourceCount = exactCount(source);
        if (sourceCount != OVERSIZE_ATTACHMENT_BYTES) {
            throw new AssertionError("exact oversize coordinate changed");
        }
        var marker = PlayerSkillAttachmentMarker.freshTag();
        var markerCount = exactCount(marker);
        var playerdata = writePlayerdata(
                worldRoot, P4C2ProbeCase.OVERSIZE, source,
                P4C2FixtureManifest.PREPARED_WITNESS);
        P4C2FixtureManifest.first(
                P4C2ProbeCase.OVERSIZE,
                sourceCount, markerCount,
                P4C2Hashing.sha256(source), P4C2Hashing.sha256(marker),
                P4C2Hashing.sha256(payload),
                P4C2FixtureManifest.UNAVAILABLE_COUNT,
                P4C2FixtureManifest.UNAVAILABLE_COUNT,
                P4C2FixtureManifest.UNAVAILABLE_COUNT,
                P4C2Hashing.sha256(playerdata), Files.size(playerdata), null)
                .write(worldRoot);
    }

    private static Path preparePlayerDirectory(
            Path gameDirectory, P4C2ProbeCase probeCase) throws IOException {
        var worldRoot = P4C2FixtureManifest.worldRoot(gameDirectory);
        var playerdata = P4C2FixtureManifest.playerdata(worldRoot, probeCase);
        Files.createDirectories(playerdata.getParent());
        Files.deleteIfExists(playerdata);
        Files.deleteIfExists(playerdata.resolveSibling(probeCase.playerId() + ".dat_old"));
        Files.deleteIfExists(worldRoot.resolve(P4C2FixtureManifest.FILE_NAME));
        return worldRoot;
    }

    private static Path writePlayerdata(
            Path worldRoot,
            P4C2ProbeCase probeCase,
            Tag attachment,
            int witness) throws IOException {
        var root = minimalCurrentPlayerdata(probeCase.playerId(), witness);
        var attachments = new CompoundTag();
        attachments.put(P4C2FixtureManifest.ATTACHMENT_KEY, attachment);
        root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        var path = P4C2FixtureManifest.playerdata(worldRoot, probeCase);
        NbtIo.writeCompressed(root, path);
        return path;
    }

    private static CompoundTag minimalCurrentPlayerdata(UUID playerId, int witness) {
        var root = NbtUtils.addCurrentDataVersion(new CompoundTag());
        root.putUUID("UUID", playerId);
        root.put("Pos", doubles(0.5, 80.0, 0.5));
        root.put("Motion", doubles(0.0, 0.0, 0.0));
        root.put("Rotation", floats(0.0F, 0.0F));
        root.putString("Dimension", "minecraft:overworld");
        root.putFloat("Health", 20.0F);
        root.putShort("Air", (short) 300);
        root.putBoolean("OnGround", true);
        root.putInt("foodLevel", 20);
        root.putFloat("foodSaturationLevel", 5.0F);
        root.put("Inventory", new ListTag());
        root.put("EnderItems", new ListTag());
        var abilities = new CompoundTag();
        abilities.putFloat("flySpeed", 0.05F);
        abilities.putFloat("walkSpeed", 0.1F);
        root.put("abilities", abilities);
        var persistent = new CompoundTag();
        persistent.putInt(P4C2FixtureManifest.WITNESS_KEY, witness);
        root.put("NeoForgeData", persistent);
        return root;
    }

    private static ListTag doubles(double... values) {
        var list = new ListTag();
        for (var value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    private static ListTag floats(float... values) {
        var list = new ListTag();
        for (var value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }
}
