package com.yo1no.gramarye;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual registered-command qualification of the bounded P9-S5 starter provisioning flow. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class P9S5ProvisioningGameTests {
    private static final String SAVED_DATA_NAME = "gramarye_skill_definitions";
    private static final String SERIALIZED_ATTACHMENT_KEY = "gramarye:player_skills";
    private static final String SLOT_OCCUPIED_KEY =
            "commands.gramarye.starter.slot_occupied";
    private static final int EQUIPPED_SLOT = 0;

    private static final UUID PRIMARY_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000001");
    private static final UUID MISSING_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000002");
    private static final UUID STALE_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000003");
    private static final UUID WRONG_OWNER_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000004");
    private static final UUID UNAVAILABLE_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000005");
    private static final UUID PRE_STORE_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000006");
    private static final UUID DRAFT_COLLISION_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000007");
    private static final UUID DRAFT_CAP_PLAYER_ID =
            UUID.fromString("95a50000-0000-4000-8000-000000000008");

    private static final SkillId SECOND_CANONICAL_SKILL_ID = new SkillId(
            UUID.fromString("95a50000-0000-4000-9000-000000000011"));
    private static final SkillId MISSING_SKILL_ID = new SkillId(
            UUID.fromString("95a50000-0000-4000-9000-000000000012"));
    private static final SkillId STALE_SKILL_ID = new SkillId(
            UUID.fromString("95a50000-0000-4000-9000-000000000013"));

    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new AssertionError("P9-S5 provisioning expected a cache hit");
                    },
                    (tag, provider) -> {
                        throw new AssertionError(
                                "P9-S5 provisioning must not read disk through cache get");
                    });

    private P9S5ProvisioningGameTests() {
    }

    @GameTest(
            batch = "p9_s5_provisioning",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 400)
    public static void registeredStarterCommandCoversProvisioningBranches(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(
                server.isSameThread(),
                "P9-S5 provisioning GameTest requires the actual server thread");
        assertRegisteredCommand(helper, server);

        var attachments = PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests();
        try (var store = installIsolatedStore(server, helper, attachments);
                var players = new OwnedPlayers(server)) {
            var primary = players.placeOwned(PRIMARY_PLAYER_ID, "p9s5-primary");
            var primaryReference = exerciseFreshRepeatReuseAndAmbiguity(
                    helper, server, attachments, store, primary);

            exercisePreStoreCanonicalDraftRecovery(
                    helper,
                    server,
                    attachments,
                    store,
                    players.placeOwned(PRE_STORE_PLAYER_ID, "p9s5-prestore"));
            exerciseDeterministicDraftCollision(
                    helper,
                    server,
                    attachments,
                    store,
                    players.place(DRAFT_COLLISION_PLAYER_ID, "p9s5-collision"));
            exerciseDraftCapRejection(
                    helper,
                    server,
                    attachments,
                    store,
                    players.place(DRAFT_CAP_PLAYER_ID, "p9s5-draftcap"));
            exerciseOccupiedMissing(
                    helper,
                    server,
                    attachments,
                    store,
                    players.placeOwned(MISSING_PLAYER_ID, "p9s5-missing"),
                    primary);
            exerciseOccupiedStale(
                    helper,
                    server,
                    attachments,
                    store,
                    players.placeOwned(STALE_PLAYER_ID, "p9s5-stale"),
                    primary);
            exerciseOccupiedWrongOwner(
                    helper,
                    server,
                    attachments,
                    store,
                    primaryReference,
                    players.placeOwned(WRONG_OWNER_PLAYER_ID, "p9s5-owner"),
                    primary);
            exerciseNonPlayerSources(
                    helper, server, attachments, store, primary, primaryReference);
            exerciseAttachmentUnavailable(
                    helper,
                    server,
                    attachments,
                    players.place(UNAVAILABLE_PLAYER_ID, "p9s5-badatt"));
        }
        helper.succeed();
    }

    private static SkillReference exerciseFreshRepeatReuseAndAmbiguity(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            OwnedPlayer ownedPlayer) {
        var player = ownedPlayer.current();
        var owner = new SkillOwnerId(player.getUUID());
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireDraftCount(attachments, player) == 0
                        && requireLatestStates(attachments, player).isEmpty()
                        && requireCommittedCount(store.service(), server, owner) == 0,
                "fresh normal player must begin with zero provisioning matches");
        assertPendingEmpty(store.port(), server, owner, "fresh zero-match");

        runStarter(helper, server, player.createCommandSourceStack(), 1, "fresh zero-match");
        var reference = requireEquipped(attachments, player).orElseThrow();
        helper.assertTrue(
                reference.skillId().equals(P9StarterSkillIdentityV0.forPlayer(player.getUUID()))
                        && reference.revision().value() == 0,
                "fresh command must equip the deterministic revision-zero starter identity");
        assertCanonicalOwnedReference(helper, store.service(), server, owner, reference);

        var draftsAfterFresh = requireDraftCount(attachments, player);
        var latestAfterFresh = requireLatestStates(attachments, player);
        var countAfterFresh = requireCommittedCount(store.service(), server, owner);
        var draftAfterFresh = requireAttachmentValue(
                        attachments.findDraft(player, reference.skillId()),
                        "fresh deterministic Draft")
                .orElseThrow();
        var documentAfterFresh = requireStoreValue(
                        store.service().find(server, reference),
                        "fresh exact Store document")
                .orElseThrow();
        var ownerAfterFresh = requireStoreValue(
                        store.service().ownerOf(server, reference.skillId()),
                        "fresh exact Store owner")
                .orElseThrow();
        helper.assertTrue(
                draftsAfterFresh == 1
                        && latestAfterFresh.equals(List.of(
                                new PlayerSkillAttachmentService.LatestStateView(
                                        reference.skillId(), Optional.of(reference), 1)))
                        && countAfterFresh == 1,
                "fresh command must publish one Draft, the exact generation-one latest route, "
                        + "and one Store history");
        var pendingAfterFresh = assertSinglePendingTransition(
                store.port(), server, owner, reference, "fresh committed publication");

        runStarter(helper, server, player.createCommandSourceStack(), 1, "repeat idempotence");
        helper.assertTrue(
                requireEquipped(attachments, player).equals(Optional.of(reference))
                        && requireDraftCount(attachments, player) == draftsAfterFresh
                        && requireLatestStates(attachments, player).equals(latestAfterFresh)
                        && requireCommittedCount(store.service(), server, owner) == countAfterFresh,
                "repeat command must return the same reference without any new publication");
        assertPendingSnapshot(
                store.port(), server, owner, pendingAfterFresh, reference,
                "repeat idempotence pending journal");

        assertApplied(
                attachments.setEquipped(player, EQUIPPED_SLOT, Optional.empty()),
                "clear equipped slot before pending-recovery rejection");
        var attachmentBeforePendingReject = attachmentPayload(player);
        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                0,
                "pending recovery blocks empty-slot provisioning");
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && attachmentPayload(player).equals(attachmentBeforePendingReject)
                        && requireDraftCount(attachments, player) == draftsAfterFresh
                        && requireAttachmentValue(
                                        attachments.findDraft(player, reference.skillId()),
                                        "pending-recovery preserved Draft")
                                .equals(Optional.of(draftAfterFresh))
                        && requireLatestStates(attachments, player).equals(latestAfterFresh)
                        && requireCommittedCount(store.service(), server, owner) == countAfterFresh
                        && requireStoreValue(
                                        store.service().find(server, reference),
                                        "pending-recovery preserved Store document")
                                .equals(Optional.of(documentAfterFresh))
                        && requireStoreValue(
                                        store.service().ownerOf(server, reference.skillId()),
                                        "pending-recovery preserved Store owner")
                                .equals(Optional.of(ownerAfterFresh)),
                "pending-recovery rejection must preserve the exact journal, Store and "
                        + "Attachment snapshot");
        assertPendingSnapshot(
                store.port(), server, owner, pendingAfterFresh, reference,
                "pending-recovery rejection terminal");

        player = ownedPlayer.reconnect();
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireDraftCount(attachments, player) == draftsAfterFresh
                        && requireAttachmentValue(
                                        attachments.findDraft(player, reference.skillId()),
                                        "post-recovery preserved Draft")
                                .equals(Optional.of(draftAfterFresh))
                        && requireLatestStates(attachments, player).equals(latestAfterFresh)
                        && requireCommittedCount(store.service(), server, owner) == countAfterFresh
                        && requireStoreValue(
                                        store.service().find(server, reference),
                                        "post-recovery preserved Store document")
                                .equals(Optional.of(documentAfterFresh))
                        && requireStoreValue(
                                        store.service().ownerOf(server, reference.skillId()),
                                        "post-recovery preserved Store owner")
                                .equals(Optional.of(ownerAfterFresh)),
                "actual reconnect must be the recovery trigger while preserving the exact "
                        + "committed candidate and empty slot");
        assertPendingEmpty(store.port(), server, owner, "login recovery terminal");
        player = ownedPlayer.reconnect();
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireDraftCount(attachments, player) == draftsAfterFresh
                        && requireLatestStates(attachments, player).equals(latestAfterFresh)
                        && requireCommittedCount(store.service(), server, owner) == countAfterFresh
                        && requireStoreValue(
                                        store.service().find(server, reference),
                                        "idempotent recovery Store document")
                                .equals(Optional.of(documentAfterFresh))
                        && requireStoreValue(
                                        store.service().ownerOf(server, reference.skillId()),
                                        "idempotent recovery Store owner")
                                .equals(Optional.of(ownerAfterFresh)),
                "a second actual reconnect must be an idempotent no-pending recovery and "
                        + "preserve the exact empty slot/latest/Store state");
        assertPendingEmpty(
                store.port(), server, owner, "second login recovery idempotence terminal");
        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                1,
                "unique semantic reuse");
        helper.assertTrue(
                requireEquipped(attachments, player).equals(Optional.of(reference))
                        && requireDraftCount(attachments, player) == draftsAfterFresh
                        && requireLatestStates(attachments, player).equals(latestAfterFresh)
                        && requireCommittedCount(store.service(), server, owner) == countAfterFresh,
                "empty slot with one canonical semantic candidate must reuse the exact reference");

        assertApplied(
                attachments.setEquipped(player, EQUIPPED_SLOT, Optional.empty()),
                "clear equipped slot before semantic ambiguity");
        var secondReference = submitCanonical(
                helper,
                server,
                attachments,
                store,
                player,
                SECOND_CANONICAL_SKILL_ID);
        helper.assertTrue(
                !secondReference.equals(reference)
                        && requireLatestStates(attachments, player).size() == 2
                        && requireCommittedCount(store.service(), server, owner) == 2,
                "ambiguity fixture must publish two distinct canonical semantic candidates");
        assertSinglePendingTransition(
                store.port(), server, owner, secondReference,
                "second committed publication");
        player = ownedPlayer.reconnect();
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireLatestStates(attachments, player).size() == 2
                        && requireAttachmentValue(
                                        attachments.findLatestState(
                                                player, secondReference.skillId()),
                                        "second canonical latest state")
                                .equals(Optional.of(
                                        new PlayerSkillAttachmentService.LatestStateView(
                                                secondReference.skillId(),
                                                Optional.of(secondReference),
                                                1)))
                        && requireCommittedCount(store.service(), server, owner) == 2,
                "actual reconnect must preserve both ambiguity candidates and the empty slot");
        assertPendingEmpty(store.port(), server, owner, "semantic ambiguity precondition");
        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                0,
                "greater-than-one semantic ambiguity");
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireLatestStates(attachments, player).size() == 2
                        && requireCommittedCount(store.service(), server, owner) == 2,
                "ambiguous command must preserve the empty slot and both candidates unchanged");
        return reference;
    }

    private static void exercisePreStoreCanonicalDraftRecovery(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            OwnedPlayer ownedPlayer) {
        var player = ownedPlayer.current();
        var owner = new SkillOwnerId(player.getUUID());
        var skillId = P9StarterSkillIdentityV0.forPlayer(player.getUUID());
        var canonicalDraft = P9StarterSkillContent.canonicalDraft(skillId);
        assertApplied(
                attachments.putDraft(player, canonicalDraft),
                "pre-Store canonical Draft fixture");
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireDraftCount(attachments, player) == 1
                        && requireAttachmentValue(
                                        attachments.findDraft(player, skillId),
                                        "pre-Store canonical Draft")
                                .equals(Optional.of(canonicalDraft))
                        && requireLatestStates(attachments, player).isEmpty()
                        && requireCommittedCount(store.service(), server, owner) == 0,
                "pre-Store fixture must contain only the exact deterministic canonical Draft");
        assertPendingEmpty(store.port(), server, owner, "pre-Store recovery precondition");

        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                1,
                "pre-Store deterministic canonical Draft recovery");
        var reference = requireEquipped(attachments, player).orElseThrow();
        helper.assertTrue(
                reference.skillId().equals(skillId)
                        && reference.revision().value() == 0
                        && requireDraftCount(attachments, player) == 1
                        && requireAttachmentValue(
                                        attachments.findDraft(player, skillId),
                                        "recovered canonical Draft")
                                .equals(Optional.of(canonicalDraft))
                        && requireLatestStates(attachments, player).size() == 1
                        && requireLatestStates(attachments, player)
                                .getFirst()
                                .pointer()
                                .equals(Optional.of(reference))
                        && requireCommittedCount(store.service(), server, owner) == 1,
                "pre-Store recovery must submit and equip the same deterministic route "
                        + "without minting another Draft");
        assertCanonicalOwnedReference(helper, store.service(), server, owner, reference);
        assertSinglePendingTransition(
                store.port(), server, owner, reference,
                "pre-Store committed publication");
        player = ownedPlayer.reconnect();
        helper.assertTrue(
                requireEquipped(attachments, player).equals(Optional.of(reference))
                        && requireDraftCount(attachments, player) == 1
                        && requireLatestStates(attachments, player).equals(List.of(
                                new PlayerSkillAttachmentService.LatestStateView(
                                        reference.skillId(), Optional.of(reference), 1)))
                        && requireCommittedCount(store.service(), server, owner) == 1,
                "actual reconnect must preserve the recovered deterministic route");
        assertPendingEmpty(store.port(), server, owner, "pre-Store login recovery terminal");
        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                1,
                "pre-Store recovery reconnect idempotence");
    }

    private static void exerciseDeterministicDraftCollision(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            ServerPlayer player) {
        var owner = new SkillOwnerId(player.getUUID());
        var skillId = P9StarterSkillIdentityV0.forPlayer(player.getUUID());
        var collidingDraft = P9StarterSkillContent.canonicalDraft(skillId).withNodes(List.of());
        assertApplied(
                attachments.putDraft(player, collidingDraft),
                "deterministic noncanonical Draft collision fixture");
        assertPendingEmpty(store.port(), server, owner, "Draft collision precondition");

        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                0,
                "deterministic noncanonical Draft collision");
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireDraftCount(attachments, player) == 1
                        && requireAttachmentValue(
                                        attachments.findDraft(player, skillId),
                                        "preserved colliding Draft")
                                .equals(Optional.of(collidingDraft))
                        && requireLatestStates(attachments, player).isEmpty()
                        && requireCommittedCount(store.service(), server, owner) == 0
                        && requireStoreValue(
                                        store.service().latestReference(server, skillId),
                                        "Draft collision Store latest")
                                .isEmpty()
                        && requireStoreValue(
                                        store.service().ownerOf(server, skillId),
                                        "Draft collision Store owner")
                                .isEmpty(),
                "noncanonical same-ID Draft must reject without overwrite, submission, or equip");
        assertPendingEmpty(store.port(), server, owner, "Draft collision terminal");
    }

    private static void exerciseDraftCapRejection(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            ServerPlayer player) {
        var owner = new SkillOwnerId(player.getUUID());
        var skillId = P9StarterSkillIdentityV0.forPlayer(player.getUUID());
        for (var index = 0; index < MagicSafetyCeilings.MAX_PLAYER_DRAFTS; index++) {
            var fillerId = new SkillId(new UUID(0x15A5000000004000L, index + 1L));
            helper.assertTrue(
                    !fillerId.equals(skillId),
                    "bounded Draft fixture must leave the deterministic starter route absent");
            assertApplied(
                    attachments.putDraft(
                            player,
                            P9StarterSkillContent.canonicalDraft(fillerId)
                                    .withNodes(List.of())),
                    "bounded Draft fixture " + index);
        }
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireDraftCount(attachments, player)
                                == MagicSafetyCeilings.MAX_PLAYER_DRAFTS
                        && requireAttachmentValue(
                                        attachments.findDraft(player, skillId),
                                        "absent deterministic Draft at cap")
                                .isEmpty()
                        && requireLatestStates(attachments, player).isEmpty()
                        && requireCommittedCount(store.service(), server, owner) == 0,
                "Draft-cap fixture must contain exactly 32 unrelated routes "
                        + "and no provisioned state");
        assertPendingEmpty(store.port(), server, owner, "Draft cap precondition");

        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                0,
                "32-Draft cap rejection");
        helper.assertTrue(
                requireEquipped(attachments, player).isEmpty()
                        && requireDraftCount(attachments, player)
                                == MagicSafetyCeilings.MAX_PLAYER_DRAFTS
                        && requireAttachmentValue(
                                        attachments.findDraft(player, skillId),
                                        "deterministic Draft after cap rejection")
                                .isEmpty()
                        && requireLatestStates(attachments, player).isEmpty()
                        && requireCommittedCount(store.service(), server, owner) == 0
                        && requireStoreValue(
                                        store.service().latestReference(server, skillId),
                                        "Draft cap Store latest")
                                .isEmpty()
                        && requireStoreValue(
                                        store.service().ownerOf(server, skillId),
                                        "Draft cap Store owner")
                                .isEmpty(),
                "Draft-cap rejection must not create a thirty-third Draft, submit, or equip");
        assertPendingEmpty(store.port(), server, owner, "Draft cap terminal");
    }

    private static void exerciseOccupiedMissing(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            OwnedPlayer ownedPlayer,
            OwnedPlayer nonRecipient) {
        var player = ownedPlayer.current();
        var missing = new SkillReference(MISSING_SKILL_ID, new SkillRevision(0));
        helper.assertTrue(
                requireStoreValue(store.service().find(server, missing), "missing lookup").isEmpty()
                        && requireEquipped(attachments, player).isEmpty(),
                "occupied-missing fixture must be otherwise legal and absent only in Store");
        assertApplied(
                attachments.setEquipped(player, EQUIPPED_SLOT, Optional.of(missing)),
                "occupied missing fixture");
        runStarterExpectingSlotOccupied(
                helper, server, ownedPlayer, nonRecipient, "occupied missing");
        helper.assertTrue(
                requireEquipped(attachments, player).equals(Optional.of(missing)),
                "occupied missing rejection must not overwrite the exact slot value");
    }

    private static void exerciseOccupiedStale(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            OwnedPlayer ownedPlayer,
            OwnedPlayer nonRecipient) {
        var player = ownedPlayer.current();
        var owner = new SkillOwnerId(player.getUUID());
        var committed = submitCanonical(
                helper, server, attachments, store, player, STALE_SKILL_ID);
        assertSinglePendingTransition(
                store.port(), server, owner, committed,
                "occupied-stale committed fixture");
        player = ownedPlayer.reconnect();
        assertPendingEmpty(
                store.port(), server, owner,
                "occupied-stale actual reconnect recovery terminal");
        helper.assertTrue(
                requireAttachmentValue(
                                attachments.findLatestState(player, committed.skillId()),
                                "occupied-stale exact latest state")
                        .equals(Optional.of(
                                new PlayerSkillAttachmentService.LatestStateView(
                                        committed.skillId(), Optional.of(committed), 1))),
                "occupied-stale fixture must recover the exact generation-one latest route");
        assertApplied(
                attachments.setEquipped(player, EQUIPPED_SLOT, Optional.of(committed)),
                "occupied stale positive-control equip");
        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                1,
                "occupied stale exact-reference positive control");
        var stale = new SkillReference(
                committed.skillId(), committed.revision().successor().orElseThrow());
        helper.assertTrue(
                requireStoreValue(store.service().find(server, stale), "stale lookup").isEmpty(),
                "occupied-stale fixture must name one absent successor revision");
        assertApplied(
                attachments.setEquipped(player, EQUIPPED_SLOT, Optional.of(stale)),
                "occupied stale fixture");
        runStarterExpectingSlotOccupied(
                helper, server, ownedPlayer, nonRecipient, "occupied stale");
        helper.assertTrue(
                requireEquipped(attachments, player).equals(Optional.of(stale)),
                "occupied stale rejection must not replace the stale exact reference");
    }

    private static void exerciseOccupiedWrongOwner(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            SkillReference foreignReference,
            OwnedPlayer ownedPlayer,
            OwnedPlayer nonRecipient) {
        var player = ownedPlayer.current();
        var owner = new SkillOwnerId(player.getUUID());
        helper.assertTrue(
                requireStoreValue(
                                store.service().find(server, foreignReference),
                                "wrong-owner exact Store document")
                        .isPresent()
                        && requireStoreValue(
                                        store.service().ownerOf(
                                                server, foreignReference.skillId()),
                                        "wrong-owner exact Store owner")
                                .filter(storeOwner -> !storeOwner.equals(owner))
                                .isPresent()
                        && requireEquipped(attachments, player).isEmpty(),
                "wrong-owner fixture must otherwise resolve one real unambiguous reference");
        assertApplied(
                attachments.setEquipped(
                        player, EQUIPPED_SLOT, Optional.of(foreignReference)),
                "occupied wrong-owner fixture");
        runStarterExpectingSlotOccupied(
                helper, server, ownedPlayer, nonRecipient, "occupied wrong owner");
        helper.assertTrue(
                requireEquipped(attachments, player).equals(Optional.of(foreignReference)),
                "wrong-owner rejection must not overwrite the foreign exact reference");
    }

    private static void exerciseNonPlayerSources(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            OwnedPlayer ownedPlayer,
            SkillReference exactReference) {
        var exactPlayer = ownedPlayer.current();
        assertCanonicalOwnedReference(
                helper,
                store.service(),
                server,
                new SkillOwnerId(exactPlayer.getUUID()),
                exactReference);
        assertApplied(
                attachments.setEquipped(
                        exactPlayer, EQUIPPED_SLOT, Optional.of(exactReference)),
                "entity-substitution exact-reference positive-control equip");
        runStarter(
                helper,
                server,
                exactPlayer.createCommandSourceStack(),
                1,
                "entity-substitution exact player positive control");
        var before = attachmentPayload(exactPlayer);
        runStarter(helper, server, server.createCommandSourceStack(), 0, "nonplayer source");

        var cow = Objects.requireNonNull(
                EntityType.COW.create(server.overworld()), "entity-substitution cow");
        try {
            helper.assertTrue(
                    server.overworld().addFreshEntity(cow),
                    "entity-substitution fixture must add its bounded nonplayer entity");
            runStarter(
                    helper,
                    server,
                    exactPlayer.createCommandSourceStack().withEntity(cow),
                    0,
                    "entity-substituted source");
            helper.assertTrue(
                    attachmentPayload(exactPlayer).equals(before)
                            && requireEquipped(attachments, exactPlayer)
                                    .equals(Optional.of(exactReference)),
                    "entity-substituted source must reject only the sender substitution and "
                            + "preserve the exact valid slot");
        } finally {
            cow.discard();
        }
    }

    private static void exerciseAttachmentUnavailable(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            ServerPlayer player) {
        loadAttachmentFixture(player, ByteTag.valueOf((byte) 19));
        var before = attachmentPayload(player);
        var observed = attachments.equippedAt(player, EQUIPPED_SLOT);
        helper.assertTrue(
                observed instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable
                        && unavailable.reason()
                                == PlayerSkillAttachmentService.UnavailableReason
                                        .PRESERVED_RAW_QUARANTINE,
                "negative-only malformed fixture must expose Attachment unavailable");
        runStarter(
                helper,
                server,
                player.createCommandSourceStack(),
                0,
                "Attachment unavailable");
        helper.assertTrue(
                before.equals(attachmentPayload(player)),
                "Attachment-unavailable rejection must preserve the exact raw payload");
    }

    private static SkillReference submitCanonical(
            GameTestHelper helper,
            MinecraftServer server,
            PlayerSkillAttachmentService attachments,
            StoreFixture store,
            ServerPlayer player,
            SkillId skillId) {
        assertApplied(
                attachments.putDraft(player, P9StarterSkillContent.canonicalDraft(skillId)),
                "canonical ambiguity/stale Draft publication");
        var outcome = SkillDefinitionSubmissionService.production(
                        attachments,
                        store.port(),
                        SkillSubmissionPolicyProvider.defaults(),
                        ProfileAvailabilityView.unknown())
                .submit(player, skillId);
        helper.assertTrue(
                outcome instanceof SkillSubmissionCompositionOutcome.Committed committed
                        && committed.reference().skillId().equals(skillId)
                        && !committed.report().hasErrors(),
                "controlled fixture must traverse real submission, journal, and Store services");
        var reference = ((SkillSubmissionCompositionOutcome.Committed) outcome).reference();
        assertCanonicalOwnedReference(
                helper,
                store.service(),
                server,
                new SkillOwnerId(player.getUUID()),
                reference);
        return reference;
    }

    private static void assertCanonicalOwnedReference(
            GameTestHelper helper,
            SkillDefinitionStoreService store,
            MinecraftServer server,
            SkillOwnerId owner,
            SkillReference reference) {
        var document = requireStoreValue(
                        store.find(server, reference), "canonical exact document")
                .orElseThrow();
        var actualOwner = requireStoreValue(
                        store.ownerOf(server, reference.skillId()), "canonical exact owner")
                .orElseThrow();
        helper.assertTrue(
                actualOwner.equals(owner)
                        && P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                                reference, document),
                "successful provisioning must bind exact owner and canonical gameplay content");
    }

    private static void assertRegisteredCommand(
            GameTestHelper helper, MinecraftServer server) {
        var gramarye = server.getCommands().getDispatcher().getRoot().getChild("gramarye");
        helper.assertTrue(
                gramarye != null && gramarye.getChild("starter") != null,
                "actual server dispatcher must contain registered /gramarye starter");
    }

    private static void runStarter(
            GameTestHelper helper,
            MinecraftServer server,
            CommandSourceStack source,
            int expectedResult,
            String stage) {
        var callbackCalls = new int[1];
        var callbackSuccess = new boolean[1];
        var callbackResult = new int[1];
        server.getCommands().performPrefixedCommand(
                source.withCallback((successful, result) -> {
                    callbackCalls[0]++;
                    callbackSuccess[0] = successful;
                    callbackResult[0] = result;
                }),
                "/gramarye starter");
        helper.assertTrue(
                callbackCalls[0] == 1
                        && callbackSuccess[0]
                        && callbackResult[0] == expectedResult,
                stage + " must execute one registered command leaf with result "
                        + expectedResult
                        + "; calls="
                        + callbackCalls[0]
                        + ", success="
                        + callbackSuccess[0]
                        + ", result="
                        + callbackResult[0]);
    }

    private static void runStarterExpectingSlotOccupied(
            GameTestHelper helper,
            MinecraftServer server,
            OwnedPlayer recipient,
            OwnedPlayer nonRecipient,
            String stage) {
        recipient.clearSystemChats();
        nonRecipient.clearSystemChats();
        runStarter(
                helper,
                server,
                recipient.current().createCommandSourceStack(),
                0,
                stage);
        var expected = new ClientboundSystemChatPacket(
                Component.empty()
                        .append(Component.translatable(SLOT_OCCUPIED_KEY))
                        .withStyle(ChatFormatting.RED),
                false);
        var recipientPackets = recipient.takeSystemChats();
        var nonRecipientPackets = nonRecipient.takeSystemChats();
        helper.assertTrue(
                recipientPackets.equals(List.of(expected)) && nonRecipientPackets.isEmpty(),
                stage
                        + " must return zero and emit exactly one red non-overlay SLOT_OCCUPIED "
                        + "response on only the invoking player's connection; recipient="
                        + recipientPackets
                        + ", nonRecipient="
                        + nonRecipientPackets);
    }

    private static Optional<SkillReference> requireEquipped(
            PlayerSkillAttachmentService attachments, ServerPlayer player) {
        return requireAttachmentValue(
                attachments.equippedAt(player, EQUIPPED_SLOT), "equipped slot zero");
    }

    private static int requireDraftCount(
            PlayerSkillAttachmentService attachments, ServerPlayer player) {
        return requireAttachmentValue(attachments.draftCount(player), "Draft count");
    }

    private static List<PlayerSkillAttachmentService.LatestStateView> requireLatestStates(
            PlayerSkillAttachmentService attachments, ServerPlayer player) {
        return requireAttachmentValue(
                attachments.observeLatestStates(player), "latest-state projection");
    }

    private static int requireCommittedCount(
            SkillDefinitionStoreService store,
            MinecraftServer server,
            SkillOwnerId owner) {
        return requireStoreValue(
                store.committedSkillCount(server, owner), "owner committed count");
    }

    private static void assertPendingEmpty(
            SkillDefinitionStoreSubmissionPort port,
            MinecraftServer server,
            SkillOwnerId owner,
            String stage) {
        var expected = new SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available(
                List.of());
        var pending = port.observePendingRecovery(server, owner);
        var journalStatus = port.journalStatus(server);
        var roots = port.journalRoots(server);
        if (!pending.equals(expected)
                || !(journalStatus
                        instanceof SkillDefinitionStoreSubmissionPort.JournalStatus.Ready ready)
                || ready.entryCount() != 0
                || !(roots
                        instanceof SkillDefinitionStoreSubmissionPort.JournalRootProjection
                                .Available availableRoots)
                || !availableRoots.references().isEmpty()) {
            throw new AssertionError(
                    stage
                            + " must have an exact empty recovery projection, physical journal, "
                            + "and root set: pending="
                            + pending
                            + ", status="
                            + journalStatus
                            + ", roots="
                            + roots);
        }
    }

    private static SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available
            assertSinglePendingTransition(
            SkillDefinitionStoreSubmissionPort port,
            MinecraftServer server,
            SkillOwnerId owner,
            SkillReference target,
            String stage) {
        if (target.revision().value() != 0) {
            throw new AssertionError(
                    stage + " must target the exact revision-zero starter publication: " + target);
        }
        var expected = new SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available(
                List.of(new SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain(
                        target.skillId(),
                        List.of(new SkillDefinitionStoreSubmissionPort.PendingRecoveryStep(
                                Optional.empty(), 0, target, 1)))));
        assertPendingSnapshot(port, server, owner, expected, target, stage);
        return expected;
    }

    private static void assertPendingSnapshot(
            SkillDefinitionStoreSubmissionPort port,
            MinecraftServer server,
            SkillOwnerId owner,
            SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available expected,
            SkillReference target,
            String stage) {
        var pending = port.observePendingRecovery(server, owner);
        var journalStatus = port.journalStatus(server);
        var roots = port.journalRoots(server);
        if (!pending.equals(expected)
                || !(journalStatus
                        instanceof SkillDefinitionStoreSubmissionPort.JournalStatus.Ready ready)
                || ready.entryCount() != 1
                || !(roots
                        instanceof SkillDefinitionStoreSubmissionPort.JournalRootProjection
                                .Available availableRoots)
                || !availableRoots.references().equals(List.of(target))) {
            throw new AssertionError(
                    stage
                            + " must preserve the exact empty/0 -> target/1 recovery step, "
                            + "one physical journal entry, and its exact root: pending="
                            + pending
                            + ", status="
                            + journalStatus
                            + ", roots="
                            + roots);
        }
    }

    private static void assertApplied(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome>
                    result,
            String stage) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || available.value() != PlayerSkillAttachmentService.Applied.INSTANCE) {
            throw new AssertionError(stage + " did not apply: " + result);
        }
    }

    private static <T> T requireAttachmentValue(
            PlayerSkillAttachmentService.Result<T> result, String stage) {
        return switch (result) {
            case PlayerSkillAttachmentService.Available<T> available -> available.value();
            case PlayerSkillAttachmentService.Unavailable<T> unavailable ->
                    throw new AssertionError(
                            stage + " was unavailable: " + unavailable.reason());
        };
    }

    private static <T> T requireStoreValue(
            SkillSubsystemResult<T> result, String stage) {
        return switch (result) {
            case SkillSubsystemResult.Available<T> available -> available.value();
            case SkillSubsystemResult.Unavailable<T> unavailable ->
                    throw new AssertionError(
                            stage + " was unavailable: " + unavailable.reason());
        };
    }

    private static void loadAttachmentFixture(ServerPlayer player, Tag payload) {
        var root = player.saveWithoutId(new CompoundTag());
        var attachments = root.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        attachments.put(SERIALIZED_ATTACHMENT_KEY, payload.copy());
        root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        player.load(root);
    }

    private static Tag attachmentPayload(ServerPlayer player) {
        return Objects.requireNonNull(player.saveWithoutId(new CompoundTag())
                        .getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                        .get(SERIALIZED_ATTACHMENT_KEY),
                "serialized player-skill Attachment")
                .copy();
    }

    private static StoreFixture installIsolatedStore(
            MinecraftServer server,
            GameTestHelper helper,
            PlayerSkillAttachmentService attachments) {
        var storage = server.overworld().getDataStorage();
        var original = Objects.requireNonNull(
                storage.get(CACHE_HIT_ONLY_FACTORY, SAVED_DATA_NAME),
                "startup SavedData adapter");
        IEventBus bus = BusBuilder.builder().build();
        var service = SkillDefinitionStoreService.registerOn(
                bus, attachments, (exactServer, exactActor) -> {});
        StoreFixture fixture = null;
        try {
            bus.start();
            bus.post(new ServerStartingEvent(server));
            fixture = new StoreFixture(server, bus, service, storage, original);
            helper.assertTrue(
                    service.submissionPort().journalStatus(server)
                                    instanceof SkillDefinitionStoreSubmissionPort.JournalStatus
                                            .Ready ready
                            && ready.entryCount() == 0,
                    "isolated real Store must begin with one bootstrapped empty journal");
            return fixture;
        } catch (RuntimeException | Error failure) {
            if (fixture == null) {
                stopAndRestoreStore(server, bus, storage, original, failure);
            } else {
                fixture.closeAfterFailure(failure);
            }
            throw failure;
        }
    }

    private static void stopAndRestoreStore(
            MinecraftServer server,
            IEventBus bus,
            DimensionDataStorage storage,
            SavedData original,
            Throwable primary) {
        Throwable cleanupFailure = null;
        try {
            bus.post(new ServerStoppedEvent(server));
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
        }
        try {
            storage.set(SAVED_DATA_NAME, original);
        } catch (RuntimeException | Error failure) {
            if (cleanupFailure == null) {
                cleanupFailure = failure;
            } else if (cleanupFailure != failure) {
                cleanupFailure.addSuppressed(failure);
            }
        }
        if (cleanupFailure == null) {
            return;
        }
        if (primary != null) {
            if (cleanupFailure != primary) {
                primary.addSuppressed(cleanupFailure);
            }
        } else {
            rethrow(cleanupFailure);
        }
    }

    private record StoreFixture(
            MinecraftServer server,
            IEventBus bus,
            SkillDefinitionStoreService service,
            DimensionDataStorage storage,
            SavedData original) implements AutoCloseable {
        private StoreFixture {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(bus, "bus");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(original, "original");
        }

        private SkillDefinitionStoreSubmissionPort port() {
            return service.submissionPort();
        }

        @Override
        public void close() {
            stopAndRestoreStore(server, bus, storage, original, null);
        }

        private void closeAfterFailure(Throwable failure) {
            stopAndRestoreStore(server, bus, storage, original, failure);
        }
    }

    private static final class OwnedPlayers implements AutoCloseable {
        private final MinecraftServer server;
        private final List<OwnedPlayer> players = new ArrayList<>();

        private OwnedPlayers(MinecraftServer server) {
            this.server = Objects.requireNonNull(server, "server");
        }

        private ServerPlayer place(UUID playerId, String name) {
            return placeOwned(playerId, name).current();
        }

        private OwnedPlayer placeOwned(UUID playerId, String name) {
            var player = OwnedPlayer.place(server, playerId, name);
            players.add(player);
            return player;
        }

        @Override
        public void close() {
            Throwable failure = null;
            for (var index = players.size() - 1; index >= 0; index--) {
                try {
                    players.get(index).close();
                } catch (RuntimeException | Error cleanup) {
                    if (failure == null) {
                        failure = cleanup;
                    } else if (failure != cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                }
            }
            if (failure != null) {
                rethrow(failure);
            }
        }
    }

    @ChannelHandler.Sharable
    private static final class OwnedPlayer extends ChannelOutboundHandlerAdapter
            implements AutoCloseable {
        private final MinecraftServer server;
        private final UUID playerId;
        private final String name;
        private final PlayerdataClaim playerdata;
        private final List<ClientboundSystemChatPacket> systemChats = new ArrayList<>();
        private EmbeddedChannel channel;
        private boolean closed;

        private OwnedPlayer(
                MinecraftServer server,
                UUID playerId,
                String name,
                EmbeddedChannel channel,
                PlayerdataClaim playerdata) {
            this.server = Objects.requireNonNull(server, "server");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.name = Objects.requireNonNull(name, "name");
            this.channel = Objects.requireNonNull(channel, "channel");
            this.playerdata = Objects.requireNonNull(playerdata, "playerdata");
        }

        private static OwnedPlayer place(
                MinecraftServer server, UUID playerId, String name) {
            var playerdata = PlayerdataClaim.claim(server, playerId);
            var cookie = CommonListenerCookie.createInitial(
                    new GameProfile(playerId, name), false);
            var player = new ServerPlayer(
                    server,
                    server.overworld(),
                    cookie.gameProfile(),
                    cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            var fixture = new OwnedPlayer(server, playerId, name, channel, playerdata);
            try {
                NetworkRegistry.configureMockConnection(connection);
                channel.pipeline().addLast(fixture);
                server.getPlayerList().placeNewPlayer(connection, player, cookie);
                if (server.getPlayerList().getPlayer(playerId) != player) {
                    throw new AssertionError(
                            "actual placement did not install the exact owned player");
                }
                return fixture;
            } catch (RuntimeException | Error failure) {
                try {
                    fixture.close();
                } catch (RuntimeException | Error cleanup) {
                    if (cleanup != failure) {
                        failure.addSuppressed(cleanup);
                    }
                }
                throw failure;
            }
        }

        private ServerPlayer current() {
            if (closed) {
                throw new IllegalStateException("owned player fixture is closed");
            }
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                throw new AssertionError("owned player is no longer current");
            }
            return player;
        }

        @Override
        public void write(
                ChannelHandlerContext context,
                Object message,
                ChannelPromise promise) throws Exception {
            if (message instanceof ClientboundSystemChatPacket systemChat) {
                systemChats.add(systemChat);
            }
            context.write(message, promise);
        }

        private void clearSystemChats() {
            runChannelTasks();
            systemChats.clear();
        }

        private List<ClientboundSystemChatPacket> takeSystemChats() {
            runChannelTasks();
            var snapshot = List.copyOf(systemChats);
            systemChats.clear();
            return snapshot;
        }

        private void runChannelTasks() {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
            channel.flushOutbound();
            channel.runPendingTasks();
        }

        private ServerPlayer reconnect() {
            var previous = current();
            server.getPlayerList().remove(previous);
            if (server.getPlayerList().getPlayer(playerId) != null
                    || server.getPlayerList().getPlayers().stream()
                            .anyMatch(player -> player.getUUID().equals(playerId))) {
                throw new AssertionError(
                        "owned player remained in PlayerList before reconnect");
            }
            channel.pipeline().remove(this);
            channel.finishAndReleaseAll();
            systemChats.clear();

            var cookie = CommonListenerCookie.createInitial(
                    new GameProfile(playerId, name), false);
            var replacement = new ServerPlayer(
                    server,
                    server.overworld(),
                    cookie.gameProfile(),
                    cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var replacementChannel = new EmbeddedChannel(connection);
            channel = replacementChannel;
            try {
                NetworkRegistry.configureMockConnection(connection);
                replacementChannel.pipeline().addLast(this);
                server.getPlayerList().placeNewPlayer(connection, replacement, cookie);
                if (server.getPlayerList().getPlayer(playerId) != replacement) {
                    throw new AssertionError(
                            "actual reconnect did not install the exact owned player");
                }
                return replacement;
            } catch (RuntimeException | Error failure) {
                try {
                    close();
                } catch (RuntimeException | Error cleanup) {
                    if (cleanup != failure) {
                        failure.addSuppressed(cleanup);
                    }
                }
                throw failure;
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            Throwable failure = null;
            try {
                var current = server.getPlayerList().getPlayer(playerId);
                if (current != null) {
                    server.getPlayerList().remove(current);
                }
                if (server.getPlayerList().getPlayer(playerId) != null
                        || server.getPlayerList().getPlayers().stream()
                                .anyMatch(player -> player.getUUID().equals(playerId))) {
                    throw new AssertionError(
                            "owned player remained in PlayerList after bounded removal");
                }
            } catch (RuntimeException | Error cleanup) {
                failure = cleanup;
            }
            try {
                if (channel.pipeline().context(this) != null) {
                    channel.pipeline().remove(this);
                }
                channel.finishAndReleaseAll();
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else if (failure != cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            try {
                playerdata.release();
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) {
                    failure = cleanup;
                } else if (failure != cleanup) {
                    failure.addSuppressed(cleanup);
                }
            } finally {
                closed = true;
            }
            if (failure != null) {
                rethrow(failure);
            }
        }
    }

    private static final class PlayerdataClaim {
        private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};

        private final Path playerdataDirectory;
        private final Path statsDirectory;
        private final Path advancementsDirectory;
        private final UUID playerId;

        private PlayerdataClaim(
                Path playerdataDirectory,
                Path statsDirectory,
                Path advancementsDirectory,
                UUID playerId) {
            this.playerdataDirectory = normalizeRoute(
                    playerdataDirectory, "playerdataDirectory");
            this.statsDirectory = normalizeRoute(statsDirectory, "statsDirectory");
            this.advancementsDirectory = normalizeRoute(
                    advancementsDirectory, "advancementsDirectory");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
        }

        private static PlayerdataClaim claim(
                MinecraftServer server, UUID playerId) {
            var claim = new PlayerdataClaim(
                    server.getWorldPath(LevelResource.PLAYER_DATA_DIR),
                    server.getWorldPath(LevelResource.PLAYER_STATS_DIR),
                    server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR),
                    playerId);
            if (server.getPlayerList().getPlayer(playerId) != null
                    || server.getPlayerList().getPlayers().stream()
                            .anyMatch(player -> player.getUUID().equals(playerId))) {
                throw new IllegalStateException(
                        "refusing to replace an already-live test player UUID");
            }
            claim.requireNoArtifacts("before placement");
            return claim;
        }

        private void release() {
            Throwable failure = null;
            for (var artifact : artifacts()) {
                try {
                    if (!Files.isRegularFile(artifact, NOFOLLOW)) {
                        throw new AssertionError(
                                "refusing non-regular owned player artifact " + artifact);
                    }
                    Files.delete(artifact);
                } catch (IOException | RuntimeException | Error cleanup) {
                    var exact = cleanup instanceof IOException
                            ? new AssertionError(
                                    "failed to delete exact owned player artifact "
                                            + artifact,
                                    cleanup)
                            : cleanup;
                    if (failure == null) {
                        failure = exact;
                    } else if (failure != exact) {
                        failure.addSuppressed(exact);
                    }
                }
            }
            if (failure != null) {
                rethrow(failure);
            }
            requireNoArtifacts("after cleanup");
        }

        private static Path normalizeRoute(Path route, String name) {
            return Objects.requireNonNull(route, name).toAbsolutePath().normalize();
        }

        private static boolean requireDirectoryIfPresent(Path directory, String route) {
            if (!Files.exists(directory, NOFOLLOW)) {
                return false;
            }
            if (!Files.isDirectory(directory, NOFOLLOW)) {
                throw new AssertionError(
                        route + " route is not a real directory: " + directory);
            }
            return true;
        }

        private void requireNoArtifacts(String stage) {
            var artifacts = artifacts();
            if (!artifacts.isEmpty()) {
                throw new AssertionError(
                        "refusing non-exclusive player artifact ownership "
                                + stage
                                + ": "
                                + artifacts);
            }
        }

        private List<Path> artifacts() {
            var artifacts = new ArrayList<Path>();
            artifacts.addAll(playerdataArtifacts());
            artifacts.addAll(jsonArtifacts(statsDirectory, "stats"));
            artifacts.addAll(jsonArtifacts(advancementsDirectory, "advancements"));
            artifacts.sort(Path::compareTo);
            return List.copyOf(artifacts);
        }

        private List<Path> playerdataArtifacts() {
            if (!requireDirectoryIfPresent(playerdataDirectory, "playerdata")) {
                return List.of();
            }
            try (var paths = Files.list(playerdataDirectory)) {
                return paths.filter(path -> ownedPlayerdataName(path.getFileName().toString()))
                        .map(path -> path.toAbsolutePath().normalize())
                        .sorted()
                        .toList();
            } catch (IOException failure) {
                throw new AssertionError(
                        "failed to enumerate exact playerdata route " + playerId,
                        failure);
            }
        }

        private List<Path> jsonArtifacts(Path directory, String route) {
            if (!requireDirectoryIfPresent(directory, route)) {
                return List.of();
            }
            var exactName = playerId + ".json";
            try (var paths = Files.list(directory)) {
                return paths.filter(path -> path.getFileName().toString().equals(exactName))
                        .map(path -> path.toAbsolutePath().normalize())
                        .toList();
            } catch (IOException failure) {
                throw new AssertionError(
                        "failed to enumerate exact " + route + " route " + playerId,
                        failure);
            }
        }

        private boolean ownedPlayerdataName(String name) {
            var id = playerId.toString();
            return name.equals(id + ".dat")
                    || name.equals(id + ".dat_old")
                    || (name.startsWith(id + "-") && name.endsWith(".dat"))
                    || (name.startsWith(id + "_corrupted_") && name.endsWith(".dat"));
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError(failure);
    }
}
