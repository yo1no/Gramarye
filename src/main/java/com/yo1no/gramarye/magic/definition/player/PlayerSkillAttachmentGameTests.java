package com.yo1no.gramarye.magic.definition.player;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentSync;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Normal GameTests for the registered P4-C2-A player Attachment lifecycle. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerSkillAttachmentGameTests {
    private static final String SERIALIZED_ATTACHMENT_KEY = "gramarye:player_skills";
    private static final String WITNESS_KEY = "gramarye_p4_c2_a_witness";
    private static final int WITNESS_VALUE = 0x4C2A;
    private static final long PLAYERDATA_READ_QUOTA_BYTES = 32L * 1024L * 1024L;
    private static final UUID PERSISTENCE_PLAYER_ID =
            UUID.fromString("c2a00000-0000-4000-8000-000000000001");
    private static final UUID DRAFT_ID =
            UUID.fromString("c2a00000-0000-4000-8000-000000000002");
    private static final SkillId GENERATION_BOUNDARY_SKILL_ID = new SkillId(
            UUID.fromString("c2a00000-0000-4000-8000-000000000003"));
    private static final SkillReference GENERATION_BOUNDARY_FIRST = new SkillReference(
            GENERATION_BOUNDARY_SKILL_ID, new SkillRevision(20));
    private static final SkillReference GENERATION_BOUNDARY_SECOND = new SkillReference(
            GENERATION_BOUNDARY_SKILL_ID, new SkillRevision(21));
    private static final SkillId EXPLICIT_EMPTY_SKILL_ID = new SkillId(
            UUID.fromString("c2a00000-0000-4000-8000-000000000004"));
    private static final SkillReference EXPLICIT_EMPTY_REFERENCE = new SkillReference(
            EXPLICIT_EMPTY_SKILL_ID, new SkillRevision(30));

    private PlayerSkillAttachmentGameTests() {
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void registeredAttachmentPersistsThroughActualPlayerdataSaveAndReload(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var playerList = server.getPlayerList();
        var service = new PlayerSkillAttachmentService();
        ConnectedPlayer connected = null;
        try {
            helper.assertTrue(server.isSameThread(),
                    "P4-C2-A GameTest must execute on the server thread");
            removeOnlinePlayer(server, PERSISTENCE_PLAYER_ID);
            deletePlayerdata(server, PERSISTENCE_PLAYER_ID);
            assertRegistrationAndFreshDefaults(helper, server);
            exerciseMissingDraftRejection(server, service);
            exerciseMissingTransitionPublication(server, service);
            exerciseMaximumRootProjection(server, service);
            exerciseBoundedMutations(server, service);

            connected = placePlayer(server, PERSISTENCE_PLAYER_ID, "p4c2-persist");
            var player = connected.player();
            helper.assertFalse(player.hasData(PlayerSkillAttachments.type()),
                    "missing playerdata must not install the Attachment");
            assertAvailableEquals(service.findDraft(player, new SkillId(DRAFT_ID)),
                    Optional.empty(), "missing Draft read");
            assertAvailableEquals(service.draftCount(player), 0, "missing Draft count");
            assertAvailableEquals(service.equippedAt(player, 3),
                    Optional.empty(), "missing equipped read");
            assertAvailableEquals(service.editorState(player),
                    new PlayerSkillAttachmentService.EditorStateView(
                            Optional.empty(), OptionalInt.empty()),
                    "missing editor read");
            assertAvailableEquals(service.rootProjection(player),
                    new PlayerSkillAttachmentService.PlayerSkillRootProjection(List.of()),
                    "missing roots");
            assertAvailableEquals(service.ownerId(player),
                    new SkillOwnerId(PERSISTENCE_PLAYER_ID), "owner derivation");
            assertNoOp(service.removeDraft(player, new SkillId(DRAFT_ID)),
                    "missing Draft removal");
            assertNoOp(service.setEquipped(player, 3, Optional.empty()),
                    "missing equipped removal");
            assertNoOp(service.setEditorState(
                            player,
                            new PlayerSkillAttachmentService.EditorStateView(
                                    Optional.empty(), OptionalInt.empty())),
                    "missing empty editor mutation");
            var missingNoOp = prepared(service.prepareLatestTransition(
                    player,
                    new SkillId(DRAFT_ID),
                    Optional.empty(),
                    0,
                    Optional.empty()));
            helper.assertTrue(missingNoOp.isNoOp(),
                    "implicit empty latest state must prepare a no-op");
            assertNoOp(service.publishPreparedTransition(player, missingNoOp),
                    "missing no-op publication");
            helper.assertFalse(player.hasData(PlayerSkillAttachments.type()),
                    "all missing reads/no-ops must leave the Attachment map untouched");

            var draft = mixedFamilyDraft();
            var reference = new SkillReference(draft.skillId(), new SkillRevision(9));
            var preparedWhileMissing = prepared(service.prepareLatestTransitionToCurrent(
                    player, draft.skillId(), reference));
            helper.assertFalse(preparedWhileMissing.isNoOp(),
                    "missing changed prepare must prebuild a replacement without installing it");
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(player, preparedWhileMissing),
                    PlayerSkillAttachmentService.TransitionCurrentness.CURRENT,
                    "Missing prepared transition currentness");
            helper.assertFalse(player.hasData(PlayerSkillAttachments.type()),
                    "prepare/currentness must leave the missing Attachment map untouched");
            assertApplied(service.putDraft(player, draft), "Draft publication");
            helper.assertTrue(player.hasData(PlayerSkillAttachments.type()),
                    "first changed mutation must install one complete Ready state");
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(player, preparedWhileMissing),
                    PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED,
                    "Missing token invalidated currentness");
            assertMutationRejected(
                    service.publishPreparedTransition(player, preparedWhileMissing),
                    PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED,
                    "missing token invalidated by Draft publication");
            var draftReadyIdentity = requireReady(service.observe(player));
            assertNoOp(service.putDraft(player, draft), "canonical same-Draft publication");
            helper.assertTrue(requireReady(service.observe(player)) == draftReadyIdentity,
                    "same Draft no-op must preserve the exact Ready identity");
            var transition = prepared(service.prepareLatestTransitionToCurrent(
                    player, draft.skillId(), reference));
            helper.assertFalse(transition.isNoOp(),
                    "empty-to-present latest transition must be changed");
            helper.assertTrue(transition.owner().equals(new SkillOwnerId(player.getUUID()))
                            && transition.skillId().equals(draft.skillId())
                            && transition.expectedPointer().isEmpty()
                            && transition.expectedGeneration() == 0
                            && transition.targetPointer().equals(Optional.of(reference))
                            && transition.targetGeneration() == 1,
                    "prepared token must expose only its exact bounded public values");
            var beforeCurrentness = requireReady(service.observe(player));
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(player, transition),
                    PlayerSkillAttachmentService.TransitionCurrentness.CURRENT,
                    "present prepared transition currentness");
            helper.assertTrue(requireReady(service.observe(player)) == beforeCurrentness,
                    "currentness check must not replace the Ready state");
            assertApplied(service.publishPreparedTransition(player, transition),
                    "latest transition publication after currentness check");
            assertApplied(service.setEquipped(player, 3, Optional.of(reference)),
                    "equipped publication");
            var explicitPresent = prepared(service.prepareLatestTransition(
                    player,
                    EXPLICIT_EMPTY_SKILL_ID,
                    Optional.empty(),
                    0,
                    Optional.of(EXPLICIT_EMPTY_REFERENCE)));
            assertApplied(service.publishPreparedTransition(player, explicitPresent),
                    "explicit-empty route creation");
            var explicitRemoved = prepared(service.prepareLatestTransition(
                    player,
                    EXPLICIT_EMPTY_SKILL_ID,
                    Optional.of(EXPLICIT_EMPTY_REFERENCE),
                    1,
                    Optional.empty()));
            assertApplied(service.publishPreparedTransition(player, explicitRemoved),
                    "explicit-empty route removal");
            var explicitEmpty = requireLatest(
                    service.findLatestState(player, EXPLICIT_EMPTY_SKILL_ID));
            helper.assertTrue(explicitEmpty.pointer().isEmpty()
                            && explicitEmpty.mutationGeneration() == 2,
                    "explicit empty latest state must retain its non-zero generation");
            var explicitEmptyTarget = new SkillReference(
                    EXPLICIT_EMPTY_SKILL_ID, new SkillRevision(31));
            var explicitEmptyPrepared = prepared(
                    service.prepareLatestTransitionToCurrent(
                            player, EXPLICIT_EMPTY_SKILL_ID, explicitEmptyTarget));
            helper.assertTrue(explicitEmptyPrepared.expectedPointer().isEmpty()
                            && explicitEmptyPrepared.expectedGeneration() == 2
                            && explicitEmptyPrepared.targetGeneration() == 3,
                    "prepare-to-current must retain explicit-empty generation state");

            assertTransitionRejected(
                    service.prepareLatestTransition(
                            player,
                            draft.skillId(),
                            Optional.of(reference),
                            0,
                            Optional.of(reference)),
                    PlayerSkillAttachmentService.TransitionRejectionCode
                            .GENERATION_MISMATCH,
                    "generation mismatch");
            assertTransitionRejected(
                    service.prepareLatestTransition(
                            player,
                            draft.skillId(),
                            Optional.empty(),
                            1,
                            Optional.of(reference)),
                    PlayerSkillAttachmentService.TransitionRejectionCode
                            .POINTER_MISMATCH,
                    "pointer mismatch");
            assertTransitionRejected(
                    service.prepareLatestTransition(
                            player,
                            draft.skillId(),
                            Optional.of(reference),
                            1,
                            Optional.of(new SkillReference(
                                    new SkillId(new UUID(0L, 0xBADL)),
                                    new SkillRevision(1)))),
                    PlayerSkillAttachmentService.TransitionRejectionCode
                            .TARGET_ROUTE_MISMATCH,
                    "target route mismatch");
            assertTransitionRejected(
                    service.prepareLatestTransitionToCurrent(
                            player,
                            draft.skillId(),
                            new SkillReference(
                                    new SkillId(new UUID(0L, 0xBADL)),
                                    new SkillRevision(1))),
                    PlayerSkillAttachmentService.TransitionRejectionCode
                            .TARGET_ROUTE_MISMATCH,
                    "prepare-to-current target route mismatch");
            var samePointer = prepared(service.prepareLatestTransitionToCurrent(
                    player, draft.skillId(), reference));
            var samePointerIdentity = requireReady(service.observe(player));
            helper.assertTrue(samePointer.isNoOp()
                            && samePointer.targetGeneration() == 1,
                    "same-pointer transition must remain a generation-preserving no-op");
            assertNoOp(service.publishPreparedTransition(player, samePointer),
                    "same-pointer publication");
            helper.assertTrue(requireReady(service.observe(player)) == samePointerIdentity,
                    "same-pointer publication must preserve the exact Ready identity");

            drainOutbound(connected.channel());
            var replacementReference = new SkillReference(
                    draft.skillId(), new SkillRevision(10));
            var presentToMissing = prepared(service.prepareLatestTransition(
                    player,
                    draft.skillId(),
                    Optional.of(reference),
                    1,
                    Optional.of(replacementReference)));
            var sameUuidMissing = unplacedPlayer(
                    server, player.getUUID(), "p4c2-same-uuid-missing");
            assertMutationRejected(
                    service.publishPreparedTransition(sameUuidMissing, presentToMissing),
                    PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED,
                    "Present token against same-UUID Missing holder");
            helper.assertFalse(sameUuidMissing.hasData(PlayerSkillAttachments.type()),
                    "stale-token Missing rejection must not install an Attachment");
            var sameUuidQuarantined = unplacedPlayer(
                    server, player.getUUID(), "p4c2-same-uuid-quarantine");
            loadAttachmentFixture(sameUuidQuarantined, ByteTag.valueOf((byte) 19));
            assertUnavailable(
                    service.checkPreparedTransitionCurrent(
                            sameUuidQuarantined, presentToMissing),
                    PlayerSkillAttachmentService.UnavailableReason
                            .PRESERVED_RAW_QUARANTINE,
                    "currentness against same-UUID quarantined holder");
            assertUnavailable(
                    service.prepareLatestTransitionToCurrent(
                            sameUuidQuarantined, draft.skillId(), replacementReference),
                    PlayerSkillAttachmentService.UnavailableReason
                            .PRESERVED_RAW_QUARANTINE,
                    "prepare-to-current against quarantined holder");
            assertUnavailable(
                    service.publishPreparedTransition(
                            sameUuidQuarantined, presentToMissing),
                    PlayerSkillAttachmentService.UnavailableReason
                            .PRESERVED_RAW_QUARANTINE,
                    "Present token against same-UUID quarantined holder");

            var draftInvalidated = prepared(service.prepareLatestTransition(
                    player,
                    draft.skillId(),
                    Optional.of(reference),
                    1,
                    Optional.of(replacementReference)));
            var temporaryDraft = emptyDraft(
                    new SkillId(UUID.fromString(
                            "c2a00000-0000-4000-8000-000000000005")),
                    2);
            assertApplied(service.putDraft(player, temporaryDraft),
                    "intermediate Draft publication");
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(player, draftInvalidated),
                    PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED,
                    "Draft mutation invalidates prepared currentness");
            assertMutationRejected(
                    service.publishPreparedTransition(player, draftInvalidated),
                    PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED,
                    "Draft mutation invalidates prepared transition");
            assertApplied(service.removeDraft(player, temporaryDraft.skillId()),
                    "intermediate Draft cleanup");

            var equippedInvalidated = prepared(service.prepareLatestTransition(
                    player,
                    draft.skillId(),
                    Optional.of(reference),
                    1,
                    Optional.of(replacementReference)));
            assertApplied(service.setEquipped(player, 4, Optional.of(reference)),
                    "intermediate equipped publication");
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(player, equippedInvalidated),
                    PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED,
                    "equipped mutation invalidates prepared currentness");
            assertMutationRejected(
                    service.publishPreparedTransition(player, equippedInvalidated),
                    PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED,
                    "equipped mutation invalidates prepared transition");
            assertApplied(service.setEquipped(player, 4, Optional.empty()),
                    "intermediate equipped cleanup");

            var editor = new PlayerSkillAttachmentService.EditorStateView(
                    Optional.of(draft.skillId()), OptionalInt.of(0));
            var staleTransition = prepared(service.prepareLatestTransition(
                    player,
                    draft.skillId(),
                    Optional.of(reference),
                    1,
                    Optional.of(replacementReference)));
            assertApplied(service.setEditorState(player, editor), "editor publication");
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(player, staleTransition),
                    PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED,
                    "editor mutation invalidates prepared currentness");
            assertMutationRejected(
                    service.publishPreparedTransition(player, staleTransition),
                    PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED,
                    "editor mutation invalidates prepared transition");
            var replacementTransition = prepared(service.prepareLatestTransition(
                    player,
                    draft.skillId(),
                    Optional.of(reference),
                    1,
                    Optional.of(replacementReference)));
            assertApplied(service.publishPreparedTransition(player, replacementTransition),
                    "replacement latest publication");
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(player, replacementTransition),
                    PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED,
                    "published token is no longer current");
            assertMutationRejected(
                    service.publishPreparedTransition(player, replacementTransition),
                    PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED,
                    "second changed-token publication");
            var wrongPlayer = unplacedPlayer(
                    server,
                    UUID.fromString("c2a00000-0000-4000-8000-000000000012"),
                    "p4c2-wrong");
            var wrongPlayerToken = prepared(service.prepareLatestTransition(
                    player,
                    draft.skillId(),
                    Optional.of(replacementReference),
                    2,
                    Optional.of(replacementReference)));
            assertCurrentness(
                    service.checkPreparedTransitionCurrent(wrongPlayer, wrongPlayerToken),
                    PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED,
                    "wrong-player currentness");
            assertMutationRejected(
                    service.publishPreparedTransition(wrongPlayer, wrongPlayerToken),
                    PlayerSkillAttachmentService.MutationRejectionCode.WRONG_PLAYER,
                    "wrong-player publication");
            helper.assertFalse(wrongPlayer.hasData(PlayerSkillAttachments.type()),
                    "wrong-player rejection must not install an Attachment");
            connected.channel().runPendingTasks();
            connected.channel().runScheduledPendingTasks();
            var unexpectedPacket = connected.channel().readOutbound();
            if (unexpectedPacket != null) {
                ReferenceCountUtil.release(unexpectedPacket);
                throw new AssertionError(
                        "non-synced Attachment mutation emitted an outbound packet");
            }

            player.getPersistentData().putInt(WITNESS_KEY, WITNESS_VALUE);
            var observedBeforeSave = service.observe(player);
            var readyBeforeSave = requireReady(observedBeforeSave);
            playerList.saveAll();
            var disk = readPlayerdata(server, PERSISTENCE_PLAYER_ID);
            helper.assertTrue(
                    disk.get("NeoForgeData") instanceof CompoundTag neoForgeData
                            && neoForgeData.getInt(WITNESS_KEY) == WITNESS_VALUE,
                    "synchronous save must persist this run's independent witness");
            var diskAttachment = attachmentValue(disk);
            helper.assertTrue(
                    diskAttachment.equals(readyBeforeSave.carrier().copyTag()),
                    "synchronous save must persist the exact prebuilt canonical carrier");

            playerList.remove(player);
            connected.channel().finishAndReleaseAll();
            connected = placePlayer(server, PERSISTENCE_PLAYER_ID, "p4c2-reload");
            var reloaded = connected.player();
            helper.assertTrue(reloaded.hasData(PlayerSkillAttachments.type()),
                    "actual reload must retain a present registered Attachment");
            assertAvailableEquals(service.findDraft(reloaded, draft.skillId()),
                    Optional.of(draft), "Draft reload");
            assertAvailableEquals(service.equippedAt(reloaded, 3),
                    Optional.of(reference), "equipped reload");
            assertAvailableEquals(service.editorState(reloaded), editor, "editor reload");
            var latest = requireLatest(service.findLatestState(reloaded, draft.skillId()));
            helper.assertTrue(latest.pointer().equals(Optional.of(replacementReference))
                            && latest.mutationGeneration() == 2,
                    "latest pointer and int generation must round-trip");
            var reloadedExplicitEmpty = requireLatest(
                    service.findLatestState(reloaded, EXPLICIT_EMPTY_SKILL_ID));
            helper.assertTrue(reloadedExplicitEmpty.pointer().isEmpty()
                            && reloadedExplicitEmpty.mutationGeneration() == 2,
                    "explicit empty latest state must round-trip without becoming implicit");
            assertAvailableEquals(service.rootProjection(reloaded),
                    new PlayerSkillAttachmentService.PlayerSkillRootProjection(
                            List.of(replacementReference, reference)),
                    "latest/equipped root projection reload");
            helper.succeed();
        } finally {
            removeOnlinePlayer(server, PERSISTENCE_PLAYER_ID);
            if (connected != null) {
                connected.channel().finishAndReleaseAll();
            }
            deletePlayerdata(server, PERSISTENCE_PLAYER_ID);
        }
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 300)
    public static void registeredQuarantineAndCopyLifecycleRemainTotal(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var service = new PlayerSkillAttachmentService();
        var keepInventory = server.overworld().getGameRules()
                .getRule(GameRules.RULE_KEEPINVENTORY);
        var originalKeepInventory = keepInventory.get();
        try {
            var variants = LifecycleVariant.values();
            for (var index = 0; index < variants.length; index++) {
                var variant = variants[index];
                var playerId = new UUID(
                        0xC2A0000000000000L,
                        0x8000000000000100L + index);
                removeOnlinePlayer(server, playerId);
                deletePlayerdata(server, playerId);
                var fixture = fixtureTag(variant);
                writePlayerdataFixture(server, playerId, fixture);
                var connected = placePlayer(server, playerId, "p4c2-q" + index);
                try {
                    var current = connected.player();
                    helper.assertTrue(current.hasData(PlayerSkillAttachments.type()),
                            "registered serializer must install every delivered fixture");
                    var loadedState = current.getData(PlayerSkillAttachments.type());
                    assertVariant(helper, service, current, loadedState, fixture, variant);
                    if (variant == LifecycleVariant.READY_GENERATION_BOUNDARY) {
                        fixture = advanceToGenerationMaximum(service, current);
                        loadedState = current.getData(PlayerSkillAttachments.type());
                        assertVariant(
                                helper, service, current, loadedState, fixture, variant);
                    }

                    keepInventory.set((index & 1) != 0, server);
                    var afterDeath = server.getPlayerList().respawn(
                            current, false, Entity.RemovalReason.KILLED);
                    afterDeath.connection.player = afterDeath;
                    var deathState = afterDeath.getData(PlayerSkillAttachments.type());
                    helper.assertTrue(deathState != loadedState,
                            "copyOnDeath must rebuild a fresh Attachment state");
                    assertVariant(helper, service, afterDeath, deathState, fixture, variant);

                    var afterEnd = server.getPlayerList().respawn(
                            afterDeath, true, Entity.RemovalReason.CHANGED_DIMENSION);
                    afterEnd.connection.player = afterEnd;
                    var endState = afterEnd.getData(PlayerSkillAttachments.type());
                    helper.assertTrue(endState != deathState,
                            "End-equivalent non-death clone must rebuild a fresh state");
                    assertVariant(helper, service, afterEnd, endState, fixture, variant);
                } finally {
                    removeOnlinePlayer(server, playerId);
                    connected.channel().finishAndReleaseAll();
                    deletePlayerdata(server, playerId);
                }
            }
            helper.succeed();
        } finally {
            keepInventory.set(originalKeepInventory, server);
        }
    }

    private static void assertRegistrationAndFreshDefaults(
            GameTestHelper helper, MinecraftServer server) {
        var type = PlayerSkillAttachments.type();
        helper.assertTrue(
                "gramarye:player_skills".equals(
                        net.neoforged.neoforge.registries.NeoForgeRegistries
                                .ATTACHMENT_TYPES.getKey(type).toString()),
                "registered Attachment must use its stable ID");
        helper.assertFalse(AttachmentSync.SYNCED_ATTACHMENT_TYPES.containsValue(type),
                "permanent player skill Attachment must not register a sync handler");
        var firstHolder = unplacedPlayer(
                server,
                UUID.fromString("c2a00000-0000-4000-8000-000000000010"),
                "p4c2-fresh-a");
        var secondHolder = unplacedPlayer(
                server,
                UUID.fromString("c2a00000-0000-4000-8000-000000000011"),
                "p4c2-fresh-b");
        var first = firstHolder.getData(type);
        var second = secondHolder.getData(type);
        helper.assertTrue(first instanceof PlayerSkillAttachmentReady
                        && second instanceof PlayerSkillAttachmentReady,
                "registered default supplier must produce Ready states");
        helper.assertTrue(first != second,
                "different missing holders must receive fresh Ready outer identities");
        helper.assertTrue(
                ((PlayerSkillAttachmentReady) first).carrier().copyTag().equals(
                        ((PlayerSkillAttachmentReady) second).carrier().copyTag()),
                "fresh defaults must carry the same complete canonical empty value");
    }

    private static SkillDraft mixedFamilyDraft() {
        var route = new SkillId(DRAFT_ID);
        var json = new JsonObject();
        json.addProperty("family", "json-trigger");
        var nbt = new CompoundTag();
        nbt.putString("family", "nbt-action");
        var trigger = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("gramarye", "p4_c2_a_trigger"),
                0,
                new Dynamic<>(JsonOps.INSTANCE, json));
        var action = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("gramarye", "p4_c2_a_action"),
                0,
                new Dynamic<>(NbtOps.INSTANCE, nbt));
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                route,
                Optional.of(new SkillRevision(4)),
                List.of(new DraftNode(
                        new DraftTriggerSlot.Present(trigger),
                        new DraftActionSlot.Present(action),
                        AppearanceOverrideDocument.None.INSTANCE)),
                AppearanceDocument.Default.INSTANCE);
    }

    private static void exerciseMissingDraftRejection(
            MinecraftServer server, PlayerSkillAttachmentService service) {
        var player = unplacedPlayer(
                server,
                UUID.fromString("c2a00000-0000-4000-8000-000000000021"),
                "p4c2-draft-reject");
        var route = new SkillId(
                UUID.fromString("c2a00000-0000-4000-8000-000000000022"));
        var noncurrent = new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION + 1,
                route,
                Optional.empty(),
                List.of(),
                AppearanceDocument.Default.INSTANCE);
        assertMutationRejected(
                service.putDraft(player, noncurrent),
                PlayerSkillAttachmentService.MutationRejectionCode
                        .DRAFT_PERSISTENCE_REJECTED,
                "missing Draft encode rejection");
        if (player.hasData(PlayerSkillAttachments.type())) {
            throw new AssertionError(
                    "rejected Draft encoding installed a missing Attachment");
        }
    }

    private static void exerciseMissingTransitionPublication(
            MinecraftServer server, PlayerSkillAttachmentService service) {
        var player = unplacedPlayer(
                server,
                UUID.fromString("c2a00000-0000-4000-8000-000000000023"),
                "p4c2-missing-transition");
        var route = new SkillId(
                UUID.fromString("c2a00000-0000-4000-8000-000000000024"));
        var reference = new SkillReference(route, new SkillRevision(1));
        var install = prepared(service.prepareLatestTransition(
                player, route, Optional.empty(), 0, Optional.of(reference)));
        if (player.hasData(PlayerSkillAttachments.type())) {
            throw new AssertionError("changed prepare installed a missing Attachment");
        }
        assertApplied(service.publishPreparedTransition(player, install),
                "changed Missing transition publication");
        var installed = requireLatest(service.findLatestState(player, route));
        if (!installed.pointer().equals(Optional.of(reference))
                || installed.mutationGeneration() != 1) {
            throw new AssertionError("changed Missing transition installed the wrong state");
        }
        assertMutationRejected(
                service.publishPreparedTransition(player, install),
                PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED,
                "second Missing-token publication");

        var remove = prepared(service.prepareLatestTransition(
                player, route, Optional.of(reference), 1, Optional.empty()));
        assertApplied(service.publishPreparedTransition(player, remove),
                "explicit empty latest publication");
        var explicitEmpty = requireLatest(service.findLatestState(player, route));
        if (explicitEmpty.pointer().isPresent()
                || explicitEmpty.mutationGeneration() != 2) {
            throw new AssertionError("explicit empty latest state lost its generation");
        }
        var sameEmpty = prepared(service.prepareLatestTransition(
                player, route, Optional.empty(), 2, Optional.empty()));
        var identity = requireReady(service.observe(player));
        assertNoOp(service.publishPreparedTransition(player, sameEmpty),
                "explicit empty same-pointer publication");
        if (requireReady(service.observe(player)) != identity) {
            throw new AssertionError("explicit empty no-op changed Ready identity");
        }
        if (!requireRootProjection(service.rootProjection(player)).references().isEmpty()) {
            throw new AssertionError("explicit empty latest state entered retention roots");
        }
    }

    private static void exerciseMaximumRootProjection(
            MinecraftServer server, PlayerSkillAttachmentService service) {
        var player = unplacedPlayer(
                server,
                UUID.fromString("c2a00000-0000-4000-8000-000000000025"),
                "p4c2-max-roots");
        var latestReferences = new java.util.ArrayList<SkillReference>();
        for (var index = 0; index < 256; index++) {
            var route = new SkillId(new UUID(0xC2A2000000000000L, index + 1L));
            var reference = new SkillReference(route, new SkillRevision(index));
            latestReferences.add(reference);
            var transition = prepared(service.prepareLatestTransition(
                    player, route, Optional.empty(), 0, Optional.of(reference)));
            assertApplied(service.publishPreparedTransition(player, transition),
                    "maximum latest root " + index);
        }
        for (var slot = 0; slot < 64; slot++) {
            assertApplied(service.setEquipped(
                            player, slot, Optional.of(latestReferences.get(slot))),
                    "maximum equipped root " + slot);
        }

        var expected = new java.util.ArrayList<>(latestReferences);
        expected.addAll(latestReferences.subList(0, 64));
        var projection = requireRootProjection(service.rootProjection(player));
        if (!projection.references().equals(expected)
                || projection.references().size() != 256 + 64) {
            throw new AssertionError(
                    "maximum root projection lost order, bounds, or cross-category duplicates");
        }
        try {
            projection.references().add(latestReferences.get(0));
        } catch (UnsupportedOperationException expectedFailure) {
            return;
        }
        throw new AssertionError("root projection exposed a mutable list");
    }

    private static void exerciseBoundedMutations(
            MinecraftServer server, PlayerSkillAttachmentService service) {
        var player = unplacedPlayer(
                server,
                UUID.fromString("c2a00000-0000-4000-8000-000000000020"),
                "p4c2-bounds");
        for (var index = 0; index < 32; index++) {
            assertApplied(
                    service.putDraft(player, emptyDraft(new SkillId(
                            new UUID(0xC2A0000000000000L, index + 1L)), 0)),
                    "bounded Draft add " + index);
        }
        assertAvailableEquals(service.draftCount(player), 32, "exact Draft count");
        var beforeOverflow = requireReady(service.observe(player));
        assertMutationRejected(
                service.putDraft(
                        player,
                        emptyDraft(new SkillId(
                                new UUID(0xC2A0000000000000L, 33L)), 0)),
                PlayerSkillAttachmentService.MutationRejectionCode.DRAFT_LIMIT_REACHED,
                "thirty-third Draft route");
        if (requireReady(service.observe(player)) != beforeOverflow) {
            throw new AssertionError("Draft overflow changed the admitted Ready identity");
        }

        var existingRoute = new SkillId(new UUID(0xC2A0000000000000L, 1L));
        assertApplied(service.putDraft(player, emptyDraft(existingRoute, 1)),
                "replace existing Draft at exact count");
        var replacementIdentity = requireReady(service.observe(player));
        assertNoOp(service.putDraft(player, emptyDraft(existingRoute, 1)),
                "same replacement Draft");
        if (requireReady(service.observe(player)) != replacementIdentity) {
            throw new AssertionError("same Draft no-op changed Ready identity");
        }
        assertNoOp(
                service.removeDraft(
                        player, new SkillId(new UUID(0xC2A0000000000000L, 99L))),
                "remove absent Draft");
        assertApplied(service.removeDraft(player, existingRoute), "remove existing Draft");
        assertAvailableEquals(service.draftCount(player), 31, "Draft count after removal");

        var staleReference = new SkillReference(
                new SkillId(new UUID(0xC2A0000000000000L, 0xEE)),
                new SkillRevision(17));
        assertApplied(service.setEquipped(player, 63, Optional.of(staleReference)),
                "upper equipped slot");
        assertApplied(service.setEquipped(player, 0, Optional.of(staleReference)),
                "lower equipped slot");
        assertNoOp(service.setEquipped(player, 0, Optional.of(staleReference)),
                "same equipped reference");
        assertApplied(service.setEquipped(player, 0, Optional.empty()),
                "equipped removal");
        assertAvailableEquals(service.equippedAt(player, 63),
                Optional.of(staleReference), "stale equipped reference");
        assertIllegalArgument(() -> service.equippedAt(player, -1),
                "negative equipped slot");
        assertIllegalArgument(() -> service.setEquipped(player, 64, Optional.empty()),
                "equipped slot above boundary");

        var staleEditor = new PlayerSkillAttachmentService.EditorStateView(
                Optional.of(new SkillId(new UUID(0xC2A0000000000000L, 0xFF))),
                OptionalInt.of(255));
        assertApplied(service.setEditorState(player, staleEditor), "stale editor metadata");
        var editorIdentity = requireReady(service.observe(player));
        assertNoOp(service.setEditorState(player, staleEditor), "same editor metadata");
        if (requireReady(service.observe(player)) != editorIdentity) {
            throw new AssertionError("same editor no-op changed Ready identity");
        }
        assertAvailableEquals(service.editorState(player), staleEditor,
                "stale editor preservation");
        var selectedDraft = emptyDraft(staleEditor.selectedDraft().orElseThrow(), 2);
        assertApplied(service.putDraft(player, selectedDraft),
                "selected Draft publication");
        assertApplied(service.removeDraft(player, selectedDraft.skillId()),
                "selected Draft removal");
        assertAvailableEquals(service.editorState(player), staleEditor,
                "Draft removal must preserve stale editor metadata");
        assertAvailableEquals(service.findLatestState(player, staleReference.skillId()),
                Optional.empty(), "Draft/equipped/editor mutations do not create latest state");
        assertAvailableEquals(service.rootProjection(player),
                new PlayerSkillAttachmentService.PlayerSkillRootProjection(
                        List.of(staleReference)),
                "per-player roots exclude Draft and editor state");
        assertIllegalArgument(
                () -> new PlayerSkillAttachmentService.EditorStateView(
                        Optional.empty(), OptionalInt.of(256)),
                "editor index above hard boundary");
    }

    private static SkillDraft emptyDraft(SkillId route, int baseRevision) {
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                route,
                Optional.of(new SkillRevision(baseRevision)),
                List.of(),
                AppearanceDocument.Default.INSTANCE);
    }

    private static ConnectedPlayer placePlayer(
            MinecraftServer server, UUID playerId, String name) {
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(playerId, name), false);
        var player = new ServerPlayer(
                server,
                server.overworld(),
                cookie.gameProfile(),
                cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return new ConnectedPlayer(player, channel);
    }

    private static ServerPlayer unplacedPlayer(
            MinecraftServer server, UUID playerId, String name) {
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(playerId, name), false);
        return new ServerPlayer(
                server,
                server.overworld(),
                cookie.gameProfile(),
                cookie.clientInformation());
    }

    private static void writePlayerdataFixture(
            MinecraftServer server, UUID playerId, Tag attachment) {
        var fixture = unplacedPlayer(server, playerId, "p4c2-fixture");
        var root = fixture.saveWithoutId(new CompoundTag());
        putAttachmentFixture(root, attachment);
        var path = playerdataPath(server, playerId);
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(root, path);
        } catch (IOException exception) {
            throw new AssertionError("failed to write P4-C2-A playerdata fixture", exception);
        }
    }

    private static void loadAttachmentFixture(ServerPlayer player, Tag attachment) {
        var root = player.saveWithoutId(new CompoundTag());
        putAttachmentFixture(root, attachment);
        player.load(root);
    }

    private static void putAttachmentFixture(CompoundTag root, Tag attachment) {
        var attachments = new CompoundTag();
        attachments.put(SERIALIZED_ATTACHMENT_KEY, attachment.copy());
        root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
    }

    private static CompoundTag readPlayerdata(
            MinecraftServer server, UUID playerId) {
        try {
            return NbtIo.readCompressed(
                    playerdataPath(server, playerId),
                    NbtAccounter.create(PLAYERDATA_READ_QUOTA_BYTES));
        } catch (IOException exception) {
            throw new AssertionError("failed to strict-read P4-C2-A playerdata", exception);
        }
    }

    private static Tag attachmentValue(CompoundTag playerdata) {
        if (!(playerdata.get(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                        instanceof CompoundTag attachments)
                || attachments.get(SERIALIZED_ATTACHMENT_KEY) == null) {
            throw new AssertionError("saved playerdata has no player skill Attachment");
        }
        return attachments.get(SERIALIZED_ATTACHMENT_KEY);
    }

    private static Path playerdataPath(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(playerId + ".dat");
    }

    private static void deletePlayerdata(MinecraftServer server, UUID playerId) {
        var primary = playerdataPath(server, playerId);
        var old = primary.resolveSibling(playerId + ".dat_old");
        try {
            Files.deleteIfExists(primary);
            Files.deleteIfExists(old);
        } catch (IOException exception) {
            throw new AssertionError("failed to clean P4-C2-A playerdata fixture", exception);
        }
    }

    private static void removeOnlinePlayer(MinecraftServer server, UUID playerId) {
        var online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            server.getPlayerList().remove(online);
        }
    }

    private static void drainOutbound(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(message);
        }
    }

    private static void assertVariant(
            GameTestHelper helper,
            PlayerSkillAttachmentService service,
            ServerPlayer player,
            PlayerSkillAttachmentState state,
            Tag fixture,
            LifecycleVariant variant) {
        switch (variant) {
            case PRESERVED_BYTE, PRESERVED_LIST -> {
                helper.assertTrue(state instanceof PlayerSkillAttachmentPreservedRaw preserved
                                && preserved.copyRaw().equals(fixture),
                        "wrong-root fixture must remain structural PreservedRaw");
                var result = service.draftCount(player);
                helper.assertTrue(
                        result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable
                                && unavailable.reason()
                                        == PlayerSkillAttachmentService.UnavailableReason
                                                .PRESERVED_RAW_QUARANTINE,
                        "controlled service must expose preserved quarantine as unavailable");
                assertUnavailable(
                        service.rootProjection(player),
                        PlayerSkillAttachmentService.UnavailableReason
                                .PRESERVED_RAW_QUARANTINE,
                        "preserved quarantine roots");
            }
            case MARKER -> {
                helper.assertTrue(state instanceof PlayerSkillAttachmentOversizeMarker,
                        "exact quarantine marker must remain OversizeMarker");
                var result = service.draftCount(player);
                helper.assertTrue(
                        result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable
                                && unavailable.reason()
                                        == PlayerSkillAttachmentService.UnavailableReason
                                                .OVERSIZE_QUARANTINE,
                        "controlled service must expose oversize quarantine as unavailable");
                assertUnavailable(
                        service.rootProjection(player),
                        PlayerSkillAttachmentService.UnavailableReason
                                .OVERSIZE_QUARANTINE,
                        "oversize quarantine roots");
                helper.assertTrue(PlayerSkillAttachmentMarker.isExact(
                                PlayerSkillAttachmentSerializer.INSTANCE.write(state, player.registryAccess())),
                        "OversizeMarker clone must retain the exact canonical marker");
            }
            case READY, READY_GENERATION_BOUNDARY -> {
                helper.assertTrue(state instanceof PlayerSkillAttachmentReady ready
                                && ready.carrier().copyTag().equals(fixture),
                        "Ready clone must retain the complete canonical carrier");
                assertAvailableEquals(service.draftCount(player), 0,
                        "Ready clone controlled access");
            }
        }
    }

    private static Tag fixtureTag(LifecycleVariant variant) {
        return switch (variant) {
            case PRESERVED_BYTE -> ByteTag.valueOf((byte) 7);
            case PRESERVED_LIST -> {
                var list = new ListTag();
                list.add(IntTag.valueOf(11));
                yield list;
            }
            case MARKER -> PlayerSkillAttachmentMarker.freshTag();
            case READY -> PlayerSkillAttachmentPersistenceBridge
                    .freshEmptyReady().carrier().copyTag();
            case READY_GENERATION_BOUNDARY -> {
                var rebuilt = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        List.of(new PlayerLatestState(
                                GENERATION_BOUNDARY_SKILL_ID,
                                Optional.of(GENERATION_BOUNDARY_FIRST),
                                Integer.MAX_VALUE - 1)),
                        List.of(),
                        PlayerSkillEditorState.empty());
                if (!(rebuilt instanceof PlayerSkillAttachmentBuildResult.Built built)) {
                    throw new AssertionError("failed to build generation-boundary fixture");
                }
                yield built.ready().carrier().copyTag();
            }
        };
    }

    private static Tag advanceToGenerationMaximum(
            PlayerSkillAttachmentService service, ServerPlayer player) {
        var transition = prepared(service.prepareLatestTransitionToCurrent(
                player, GENERATION_BOUNDARY_SKILL_ID, GENERATION_BOUNDARY_SECOND));
        if (transition.targetGeneration() != Integer.MAX_VALUE || transition.isNoOp()) {
            throw new AssertionError("MAX-1 transition did not target Integer.MAX_VALUE");
        }
        assertApplied(service.publishPreparedTransition(player, transition),
                "MAX-1 to MAX transition");
        var maximumIdentity = requireReady(service.observe(player));
        var samePointer = prepared(service.prepareLatestTransitionToCurrent(
                player, GENERATION_BOUNDARY_SKILL_ID, GENERATION_BOUNDARY_SECOND));
        if (!samePointer.isNoOp()
                || samePointer.targetGeneration() != Integer.MAX_VALUE) {
            throw new AssertionError("MAX same-pointer transition was not a legal no-op");
        }
        assertNoOp(service.publishPreparedTransition(player, samePointer),
                "MAX same-pointer publication");
        if (requireReady(service.observe(player)) != maximumIdentity) {
            throw new AssertionError("MAX same-pointer no-op changed Ready identity");
        }
        assertTransitionRejected(
                service.prepareLatestTransitionToCurrent(
                        player, GENERATION_BOUNDARY_SKILL_ID, GENERATION_BOUNDARY_FIRST),
                PlayerSkillAttachmentService.TransitionRejectionCode.GENERATION_EXHAUSTED,
                "MAX changed transition");
        if (requireReady(service.observe(player)) != maximumIdentity) {
            throw new AssertionError("generation exhaustion changed Ready identity");
        }
        return maximumIdentity.carrier().copyTag();
    }

    private static PlayerSkillAttachmentReady requireReady(
            ObservedPlayerSkillAttachment observed) {
        if (observed instanceof ObservedPlayerSkillAttachment.Ready ready) {
            return ready.state();
        }
        throw new AssertionError("expected observed Ready Attachment");
    }

    private static PlayerSkillAttachmentService.PreparedPlayerSkillTransition prepared(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.TransitionPreparation>
                    result) {
        if (result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value()
                        instanceof PlayerSkillAttachmentService.Prepared prepared) {
            return prepared.transition();
        }
        throw new AssertionError("expected prepared latest transition");
    }

    private static PlayerSkillAttachmentService.LatestStateView requireLatest(
            PlayerSkillAttachmentService.Result<
                            Optional<PlayerSkillAttachmentService.LatestStateView>>
                    result) {
        if (result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value() instanceof Optional<?> optional
                && optional.orElse(null)
                        instanceof PlayerSkillAttachmentService.LatestStateView latest) {
            return latest;
        }
        throw new AssertionError("expected available present latest state");
    }

    private static PlayerSkillAttachmentService.PlayerSkillRootProjection
            requireRootProjection(
                    PlayerSkillAttachmentService.Result<
                                    PlayerSkillAttachmentService.PlayerSkillRootProjection>
                            result) {
        if (result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value()
                        instanceof PlayerSkillAttachmentService.PlayerSkillRootProjection roots) {
            return roots;
        }
        throw new AssertionError("expected available player skill root projection");
    }

    private static void assertApplied(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome>
                    result,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || available.value() != PlayerSkillAttachmentService.Applied.INSTANCE) {
            throw new AssertionError(operation + " did not apply");
        }
    }

    private static void assertNoOp(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome>
                    result,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || available.value() != PlayerSkillAttachmentService.NoOp.INSTANCE) {
            throw new AssertionError(operation + " was not a no-op");
        }
    }

    private static void assertMutationRejected(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome>
                    result,
            PlayerSkillAttachmentService.MutationRejectionCode expected,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || !(available.value()
                        instanceof PlayerSkillAttachmentService.MutationRejected rejected)
                || rejected.code() != expected) {
            throw new AssertionError(operation + " returned the wrong mutation rejection");
        }
    }

    private static void assertTransitionRejected(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.TransitionPreparation>
                    result,
            PlayerSkillAttachmentService.TransitionRejectionCode expected,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || !(available.value()
                        instanceof PlayerSkillAttachmentService.TransitionRejected rejected)
                || rejected.code() != expected) {
            throw new AssertionError(operation + " returned the wrong transition rejection");
        }
    }

    private static void assertCurrentness(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.TransitionCurrentness>
                    result,
            PlayerSkillAttachmentService.TransitionCurrentness expected,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || available.value() != expected) {
            throw new AssertionError(operation + " returned the wrong currentness");
        }
    }

    private static void assertUnavailable(
            PlayerSkillAttachmentService.Result<?> result,
            PlayerSkillAttachmentService.UnavailableReason expected,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable)
                || unavailable.reason() != expected) {
            throw new AssertionError(operation + " returned the wrong unavailable reason");
        }
    }

    private static void assertIllegalArgument(Runnable operation, String label) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + " did not fail fast");
    }

    private static void assertAvailableEquals(
            PlayerSkillAttachmentService.Result<?> result,
            Object expected,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || !expected.equals(available.value())) {
            throw new AssertionError(operation + " returned the wrong result");
        }
    }

    private enum LifecycleVariant {
        PRESERVED_BYTE,
        PRESERVED_LIST,
        MARKER,
        READY,
        READY_GENERATION_BOUNDARY
    }

    private record ConnectedPlayer(ServerPlayer player, EmbeddedChannel channel) {
    }
}
