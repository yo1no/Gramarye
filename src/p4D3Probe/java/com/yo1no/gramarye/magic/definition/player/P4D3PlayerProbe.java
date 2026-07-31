package com.yo1no.gramarye.magic.definition.player;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceRejectionCode;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.store.P4D3Hashing;
import com.yo1no.gramarye.magic.definition.store.P4D3StoreJournalFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Package bridge for exact playerdata fixtures without exposing production Attachment state. */
public final class P4D3PlayerProbe {
    public static final String ATTACHMENT_KEY = "gramarye:player_skills";
    public static final String WITNESS_KEY = "gramarye_p4_d3_witness";
    public static final int PREPARED_WITNESS = 0x4D3B00;
    public static final ResourceLocation TRIGGER_ID = id("p4_d3_submission_trigger");
    public static final ResourceLocation ACTION_ID = id("p4_d3_submission_action");

    private static final long PLAYERDATA_QUOTA_BYTES = 32L * 1_024L * 1_024L;

    private P4D3PlayerProbe() {
    }

    public enum AttachmentShape {
        EXPECTED,
        TARGET,
        THIRD,
        COMBINED_SELECTED,
        COMBINED_SUBMISSION
    }

    public static PlayerSkillAttachmentService newService() {
        return new PlayerSkillAttachmentService();
    }

    public static void install(ServerPlayer player, AttachmentShape shape) {
        player.setData(PlayerSkillAttachments.type(), ready(shape));
    }

    public static Path writePlayerdata(
            Path worldRoot, UUID playerId, AttachmentShape shape) throws IOException {
        var path = playerdata(worldRoot, playerId);
        Files.createDirectories(path.getParent());
        Files.deleteIfExists(path);
        Files.deleteIfExists(path.resolveSibling(playerId + ".dat_old"));
        var root = minimalPlayerdata(playerId, PREPARED_WITNESS);
        var attachments = new CompoundTag();
        attachments.put(ATTACHMENT_KEY, ready(shape).carrier().copyTag());
        root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        NbtIo.writeCompressed(root, path);
        return path;
    }

    public static DiskAttachment readPlayerdata(Path worldRoot, UUID playerId)
            throws IOException {
        var path = playerdata(worldRoot, playerId);
        var root = NbtIo.readCompressed(path, NbtAccounter.create(PLAYERDATA_QUOTA_BYTES));
        if (!root.hasUUID("UUID") || !root.getUUID("UUID").equals(playerId)
                || !(root.get(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                        instanceof CompoundTag attachments)
                || attachments.get(ATTACHMENT_KEY) == null) {
            throw new AssertionError("P4-D3 playerdata identity/Attachment is absent");
        }
        var attachment = attachments.get(ATTACHMENT_KEY);
        if (!(attachment instanceof CompoundTag compound)) {
            throw new AssertionError("P4-D3 player skill Attachment is not Ready-shaped");
        }
        var loaded = new PlayerSkillAttachmentPersistenceBridge().load(
                compound, Optional.empty());
        if (!(loaded instanceof PlayerSkillAttachmentPersistenceBridge.Loaded ready)) {
            throw new AssertionError("P4-D3 player skill Attachment failed strict restore");
        }
        return new DiskAttachment(
                ready.ready().latestStates().stream()
                        .map(state -> new Tuple(
                                state.skillId(), state.pointer(), state.mutationGeneration()))
                        .toList(),
                ready.ready().drafts().size(),
                P4D3Hashing.sha256(attachment),
                P4D3Hashing.sha256(path),
                Files.size(path),
                root.getCompound("NeoForgeData").getInt(WITNESS_KEY));
    }

    public static Tuple tuple(ServerPlayer player, SkillId skillId) {
        var result = new PlayerSkillAttachmentService().findLatestState(player, skillId);
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)) {
            throw new AssertionError("P4-D3 live Attachment is unavailable");
        }
        if (!(available.value() instanceof Optional<?> optional) || optional.isEmpty()) {
            return new Tuple(skillId, Optional.empty(), 0);
        }
        var view = (PlayerSkillAttachmentService.LatestStateView) optional.orElseThrow();
        return new Tuple(view.skillId(), view.pointer(), view.mutationGeneration());
    }

    public static Path playerdata(Path worldRoot, UUID playerId) {
        return worldRoot.resolve("playerdata").resolve(playerId + ".dat");
    }

    private static PlayerSkillAttachmentReady ready(AttachmentShape shape) {
        var drafts = shape == AttachmentShape.COMBINED_SUBMISSION
                ? List.of(draftEntry())
                : List.<PlayerDraftEntry>of();
        var latest = switch (shape) {
            case EXPECTED, COMBINED_SUBMISSION -> List.<PlayerLatestState>of();
            case TARGET -> List.of(new PlayerLatestState(
                    P4D3StoreJournalFixture.skillId(0),
                    Optional.of(P4D3StoreJournalFixture.target(0, 0)), 1));
            case THIRD -> List.of(new PlayerLatestState(
                    P4D3StoreJournalFixture.skillId(0),
                    Optional.of(P4D3StoreJournalFixture.target(0, 0)), 7));
            case COMBINED_SELECTED -> List.of(new PlayerLatestState(
                    P4D3StoreJournalFixture.skillId(0),
                    Optional.of(P4D3StoreJournalFixture.target(0, 1)), 2));
        };
        var built = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                drafts, latest, List.of(), PlayerSkillEditorState.empty());
        if (!(built instanceof PlayerSkillAttachmentBuildResult.Built success)) {
            throw new AssertionError("P4-D3 legal Ready fixture was rejected");
        }
        return success.ready();
    }

    private static PlayerDraftEntry draftEntry() {
        var draft = submissionDraft();
        var encoded = SkillDraftPersistenceFacade.encodeCurrent(draft);
        if (!(encoded instanceof SkillDraftPersistenceFacade.Encoded success)) {
            throw new AssertionError("P4-D3 complete submission Draft failed encode");
        }
        return new PlayerDraftEntry(draft.skillId(), draft, success.draft());
    }

    public static SkillDraft submissionDraft() {
        var trigger = new JsonObject();
        trigger.addProperty("value", 41);
        var action = new JsonObject();
        action.addProperty("value", 42);
        var node = new DraftNode(
                DraftTriggerSlot.present(new DefinitionEnvelope(
                        TRIGGER_ID, 0, new Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE,
                                trigger))),
                DraftActionSlot.present(new DefinitionEnvelope(
                        ACTION_ID, 0, new Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE,
                                action))),
                AppearanceOverrideDocument.none());
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                P4D3StoreJournalFixture.submissionSkillId(),
                Optional.empty(),
                List.of(node),
                new AppearanceDocument.Rejected(
                        AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED));
    }

    private static CompoundTag minimalPlayerdata(UUID playerId, int witness) {
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
        persistent.putInt(WITNESS_KEY, witness);
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    public record Tuple(
            SkillId skillId, Optional<SkillReference> pointer, int generation) {
        public Tuple {
            java.util.Objects.requireNonNull(skillId, "skillId");
            pointer = java.util.Objects.requireNonNull(pointer, "pointer");
            if (generation < 0 || pointer.filter(reference ->
                    !reference.skillId().equals(skillId)).isPresent()) {
                throw new IllegalArgumentException("P4-D3 player tuple is invalid");
            }
        }
    }

    public record DiskAttachment(
            List<Tuple> explicitLatest,
            int draftCount,
            String attachmentChecksum,
            String playerdataChecksum,
            long playerdataBytes,
            int witness) {
        public DiskAttachment {
            explicitLatest = List.copyOf(explicitLatest);
            P4D3Hashing.requireSha256(attachmentChecksum);
            P4D3Hashing.requireSha256(playerdataChecksum);
            if (draftCount < 0 || playerdataBytes <= 0) {
                throw new IllegalArgumentException("P4-D3 disk Attachment facts are invalid");
            }
        }

        public Tuple tuple(SkillId skillId) {
            return explicitLatest.stream()
                    .filter(tuple -> tuple.skillId().equals(skillId))
                    .findFirst()
                    .orElseGet(() -> new Tuple(skillId, Optional.empty(), 0));
        }
    }
}
