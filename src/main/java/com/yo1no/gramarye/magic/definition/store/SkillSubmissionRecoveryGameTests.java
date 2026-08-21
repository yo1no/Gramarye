package com.yo1no.gramarye.magic.definition.store;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Normal P4-D3-A login recovery tests using actual playerdata save and reload. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillSubmissionRecoveryGameTests {
    private static final String SAVED_DATA_NAME = "gramarye_skill_definitions";
    private static final UUID BASE_PLAYER_ID =
            UUID.fromString("d3a00000-0000-4000-8000-000000000011");
    private static final UUID INTERMEDIATE_PLAYER_ID =
            UUID.fromString("d3a00000-0000-4000-8000-000000000012");
    private static final UUID FINAL_PLAYER_ID =
            UUID.fromString("d3a00000-0000-4000-8000-000000000013");
    private static final SkillId BASE_SKILL_ID = new SkillId(
            UUID.fromString("d3a00000-0000-4000-8000-000000000001"));
    private static final SkillId INTERMEDIATE_SKILL_ID = new SkillId(
            UUID.fromString("d3a00000-0000-4000-8000-000000000002"));
    private static final SkillId FINAL_SKILL_ID = new SkillId(
            UUID.fromString("d3a00000-0000-4000-8000-000000000003"));
    private static final SkillId BASE_STALE_SKILL_ID = new SkillId(
            UUID.fromString("d3a00000-0000-4000-8000-000000000004"));
    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new AssertionError("recovery GameTest expected a cache hit");
                    },
                    (tag, provider) -> {
                        throw new AssertionError(
                                "recovery GameTest must not read disk through the cache");
                    });

    private SkillSubmissionRecoveryGameTests() {
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void persistedBaseReplaysPendingChainOnLogin(GameTestHelper helper) {
        runRecoveryScenario(helper, BASE_PLAYER_ID, BASE_SKILL_ID, PersistedPosition.BASE);
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void persistedIntermediateClearsPrefixBeforeReplayOnLogin(
            GameTestHelper helper) {
        runRecoveryScenario(
                helper,
                INTERMEDIATE_PLAYER_ID,
                INTERMEDIATE_SKILL_ID,
                PersistedPosition.INTERMEDIATE);
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void persistedFinalClearsPendingChainWithoutReplayOnLogin(
            GameTestHelper helper) {
        runRecoveryScenario(helper, FINAL_PLAYER_ID, FINAL_SKILL_ID, PersistedPosition.FINAL);
    }

    private static void runRecoveryScenario(
            GameTestHelper helper,
            UUID playerId,
            SkillId skillId,
            PersistedPosition position) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread(),
                "P4-D3-A GameTest requires the server logic thread");
        var attachments = PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests();
        var firstTarget = new SkillReference(skillId, new SkillRevision(0));
        var finalTarget = new SkillReference(skillId, new SkillRevision(1));
        var draft = fixtureDraft(skillId);
        ConnectedPlayer initial = null;
        ConnectedPlayer reloaded = null;
        InstalledRecoveryFixture fixture = null;
        try {
            removeOnlinePlayer(server, playerId);
            deletePlayerdata(server, playerId);

            initial = placePlayer(server, playerId, "p4-d3-a-initial");
            assertApplied(attachments.putDraft(initial.player(), draft),
                    "initial Draft publication");
            if (position == PersistedPosition.BASE) {
                var stale = new SkillReference(BASE_STALE_SKILL_ID, new SkillRevision(0));
                publishToCurrent(
                        attachments, initial.player(), BASE_STALE_SKILL_ID, stale);
                assertApplied(
                        attachments.setEquipped(initial.player(), 0, Optional.of(stale)),
                        "BASE stale equipped fixture publication");
            }
            if (position != PersistedPosition.BASE) {
                publishToCurrent(attachments, initial.player(), skillId, firstTarget);
            }
            if (position == PersistedPosition.FINAL) {
                publishToCurrent(attachments, initial.player(), skillId, finalTarget);
            }
            assertPersistedTuple(helper, attachments, initial.player(), skillId, position,
                    firstTarget, finalTarget);

            server.getPlayerList().saveAll();
            helper.assertTrue(Files.isRegularFile(playerdataPath(server, playerId)),
                    "playerdata save must create the persisted recovery input");
            server.getPlayerList().remove(initial.player());
            initial.channel().finishAndReleaseAll();
            initial = null;

            fixture = installRecoveryFixture(
                    server, new SkillOwnerId(playerId), skillId, firstTarget, finalTarget);
            var loginTick = server.getTickCount();
            reloaded = placePlayer(server, playerId, "p4-d3-a-reloaded");

            if (position == PersistedPosition.BASE) {
                helper.assertTrue(server.getTickCount() == loginTick,
                        "P4-D recovery and P4-E2 continuation must finish in one login tick");
                helper.assertTrue(
                        server.getPlayerList().getPlayer(playerId) == reloaded.player(),
                        "P4-E2 must reconcile the exact authenticated online player identity");
            }

            assertFinalAttachment(helper, attachments, reloaded.player(), draft, finalTarget);
            if (position == PersistedPosition.BASE) {
                assertBaseE2PrunedUnretainedRoots(helper, attachments, reloaded.player());
            }
            assertStoreUnchanged(helper, fixture);
            switch (position) {
                case BASE -> assertBaseJournal(helper, fixture, firstTarget, finalTarget);
                case INTERMEDIATE -> assertIntermediateJournal(
                        helper, fixture, firstTarget, finalTarget);
                case FINAL -> assertFinalJournal(helper, fixture);
            }
            helper.succeed();
        } finally {
            removeOnlinePlayer(server, playerId);
            if (initial != null) {
                initial.channel().finishAndReleaseAll();
            }
            if (reloaded != null) {
                reloaded.channel().finishAndReleaseAll();
            }
            if (fixture != null) {
                fixture.close();
            }
            deletePlayerdata(server, playerId);
        }
    }

    private static InstalledRecoveryFixture installRecoveryFixture(
            MinecraftServer server,
            SkillOwnerId owner,
            SkillId skillId,
            SkillReference firstTarget,
            SkillReference finalTarget) {
        var store = restoreStore(owner, skillId);
        var storeCarrier = requireCarrier(store);
        var journal = requireJournal(List.of(
                new PendingAttachmentJournalEntryPhysicalV0(
                        owner,
                        skillId,
                        0,
                        1,
                        Optional.empty(),
                        firstTarget),
                new PendingAttachmentJournalEntryPhysicalV0(
                        owner,
                        skillId,
                        1,
                        2,
                        Optional.of(firstTarget),
                        finalTarget)));
        var encodedJournal = requireEncodedJournal(journal);
        var innerCarrier = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                storeCarrier,
                encodedJournal.pending(),
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        Math.addExact(storeCarrier.storeByteCount(), encodedJournal.byteCount())));
        var fixtureAdapter = GramaryeSkillSavedData.ready(
                SkillSavedDataReadyCandidate.afterCarrierRebuild(
                        store,
                        innerCarrier,
                        new PipelineFactReport(List.of(), false),
                        false));
        var fixturePort = new SkillDefinitionStoreService().submissionPort();
        var bootstrap = fixturePort.bootstrapJournalCore(fixtureAdapter);
        if (!(bootstrap instanceof SkillDefinitionStoreSubmissionPort.BootstrapResult.Ready ready)
                || ready.entryCount() != 2
                || ready.rewritePublished()) {
            throw new AssertionError("recovery fixture journal did not bootstrap canonically");
        }

        var cached = server.overworld().getDataStorage().get(
                CACHE_HIT_ONLY_FACTORY, SAVED_DATA_NAME);
        if (!(cached instanceof GramaryeSkillSavedData adapter)) {
            throw new AssertionError("Overworld cache did not contain the Gramarye adapter");
        }
        var originalState = adapter.state();
        var originalDirty = adapter.isDirty();
        adapter.publishState(originalState, fixtureAdapter.state());
        adapter.setDirty(false);
        return new InstalledRecoveryFixture(
                adapter,
                originalState,
                originalDirty,
                store,
                storeCarrier,
                fixturePort,
                owner,
                skillId);
    }

    private static SkillDefinitionStore restoreStore(
            SkillOwnerId owner, SkillId skillId) {
        var revisionZero = new SkillRevision(0);
        var revisionOne = new SkillRevision(1);
        var restored = SkillDefinitionStore.restore(new SkillDefinitionStoreSnapshot(List.of(
                new SkillHistorySnapshot(
                        skillId,
                        owner,
                        List.of(
                                new SkillRevisionSnapshot(
                                        revisionZero,
                                        fixtureDocument(skillId, revisionZero, 0)),
                                new SkillRevisionSnapshot(
                                        revisionOne,
                                        fixtureDocument(skillId, revisionOne, 1)))))));
        if (restored instanceof SkillDefinitionStoreRestoreResult.Restored success) {
            return success.store();
        }
        throw new AssertionError("recovery Store fixture was rejected");
    }

    private static SkillDocument fixtureDocument(
            SkillId skillId, SkillRevision revision, int value) {
        var trigger = new CompoundTag();
        trigger.putInt("value", value);
        var action = new CompoundTag();
        action.putInt("value", value + 10);
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                skillId,
                revision,
                List.of(new NodeDocument(
                        new DefinitionEnvelope(
                                ResourceLocation.fromNamespaceAndPath(
                                        Gramarye.MOD_ID, "p4_d3_a_trigger"),
                                0,
                                new Dynamic<>(NbtOps.INSTANCE, trigger)),
                        new DefinitionEnvelope(
                                ResourceLocation.fromNamespaceAndPath(
                                        Gramarye.MOD_ID, "p4_d3_a_action"),
                                0,
                                new Dynamic<>(NbtOps.INSTANCE, action)),
                        AppearanceOverrideDocument.None.INSTANCE)),
                AppearanceDocument.Default.INSTANCE);
    }

    private static SkillDraft fixtureDraft(SkillId skillId) {
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                skillId,
                Optional.of(new SkillRevision(37)),
                List.of(),
                AppearanceDocument.Default.INSTANCE);
    }

    private static EncodedSkillStoreCarrier requireCarrier(SkillDefinitionStore store) {
        var result = SkillStoreCarrierBuilder.rebuild(store);
        if (result instanceof CarrierBuildResult.Success success) {
            return success.carrier();
        }
        throw new AssertionError("recovery Store carrier fixture was rejected");
    }

    private static PendingAttachmentJournal requireJournal(
            List<PendingAttachmentJournalEntryPhysicalV0> entries) {
        var result = PendingAttachmentJournal.admitPhysical(
                new PendingAttachmentJournalPhysicalV0(
                        PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION, entries));
        if (result instanceof PendingAttachmentJournal.DomainAdmission.Admitted admitted) {
            return admitted.journal();
        }
        throw new AssertionError("recovery journal fixture was rejected");
    }

    private static EncodedPendingAttachmentJournal requireEncodedJournal(
            PendingAttachmentJournal journal) {
        var result = PendingAttachmentJournalFraming.encode(journal);
        if (result instanceof PendingAttachmentJournalFraming.JournalEncodingResult.Encoded
                encoded) {
            return encoded.journal();
        }
        throw new AssertionError("recovery journal fixture could not be encoded");
    }

    private static void assertPersistedTuple(
            GameTestHelper helper,
            PlayerSkillAttachmentService attachments,
            ServerPlayer player,
            SkillId skillId,
            PersistedPosition position,
            SkillReference firstTarget,
            SkillReference finalTarget) {
        var latest = latestState(attachments, player, skillId);
        switch (position) {
            case BASE -> helper.assertTrue(latest.isEmpty(),
                    "BASE playerdata must retain the implicit empty generation-zero state");
            case INTERMEDIATE -> helper.assertTrue(
                    latest.filter(value -> value.pointer().equals(Optional.of(firstTarget))
                                    && value.mutationGeneration() == 1)
                            .isPresent(),
                    "INTERMEDIATE playerdata must contain the first exact target tuple");
            case FINAL -> helper.assertTrue(
                    latest.filter(value -> value.pointer().equals(Optional.of(finalTarget))
                                    && value.mutationGeneration() == 2)
                            .isPresent(),
                    "FINAL playerdata must contain the final exact target tuple");
        }
    }

    private static void assertFinalAttachment(
            GameTestHelper helper,
            PlayerSkillAttachmentService attachments,
            ServerPlayer player,
            SkillDraft draft,
            SkillReference finalTarget) {
        var latest = latestState(attachments, player, draft.skillId());
        helper.assertTrue(
                latest.filter(value -> value.pointer().equals(Optional.of(finalTarget))
                                && value.mutationGeneration() == 2)
                        .isPresent(),
                "login recovery must leave the final exact pointer/generation tuple");
        var observedDraft = attachments.findDraft(player, draft.skillId());
        helper.assertTrue(
                observedDraft instanceof PlayerSkillAttachmentService.Available<?> available
                        && available.value().equals(Optional.of(draft)),
                "login recovery must not replace or remove the persisted Draft");
    }

    private static void assertBaseE2PrunedUnretainedRoots(
            GameTestHelper helper,
            PlayerSkillAttachmentService attachments,
            ServerPlayer player) {
        var staleLatest = latestState(attachments, player, BASE_STALE_SKILL_ID);
        helper.assertTrue(
                staleLatest.filter(value -> value.pointer().isEmpty()
                                && value.mutationGeneration() == 2)
                        .isPresent(),
                "same-chain E2 must clear the stale latest pointer with one int successor");
        var equipped = attachments.equippedAt(player, 0);
        helper.assertTrue(
                equipped instanceof PlayerSkillAttachmentService.Available<?> available
                        && available.value().equals(Optional.empty()),
                "same-chain E2 must prune the stale equipped root in the same replacement");
    }

    private static void assertStoreUnchanged(
            GameTestHelper helper, InstalledRecoveryFixture fixture) {
        if (!(fixture.adapter().state() instanceof SkillSavedDataState.Ready ready)) {
            throw new AssertionError("recovery changed the Store adapter out of Ready");
        }
        helper.assertTrue(ready.store() == fixture.store(),
                "login recovery must retain the exact Store identity");
        helper.assertTrue(ready.storeCarrier() == fixture.storeCarrier(),
                "login recovery must retain the exact Store carrier identity");
    }

    private static void assertBaseJournal(
            GameTestHelper helper,
            InstalledRecoveryFixture fixture,
            SkillReference firstTarget,
            SkillReference finalTarget) {
        var chain = onlyChain(fixture, 2);
        helper.assertTrue(
                chain.steps().get(0).targetPointer().equals(firstTarget)
                        && chain.steps().get(1).targetPointer().equals(finalTarget),
                "BASE replay must retain the complete pending chain and both roots");
        helper.assertTrue(
                roots(fixture).equals(List.of(firstTarget, finalTarget)),
                "BASE replay must retain both pending journal roots");
        helper.assertFalse(fixture.adapter().isDirty(),
                "BASE replay must not clear or dirty the pending journal");
    }

    private static void assertIntermediateJournal(
            GameTestHelper helper,
            InstalledRecoveryFixture fixture,
            SkillReference firstTarget,
            SkillReference finalTarget) {
        var chain = onlyChain(fixture, 1);
        var suffix = chain.steps().getFirst();
        helper.assertTrue(
                suffix.expectedPointer().equals(Optional.of(firstTarget))
                        && suffix.expectedGeneration() == 1
                        && suffix.targetPointer().equals(finalTarget)
                        && suffix.targetGeneration() == 2,
                "INTERMEDIATE recovery must clear only the confirmed prefix before replay");
        helper.assertTrue(roots(fixture).equals(List.of(finalTarget)),
                "INTERMEDIATE recovery must retain the unreconciled suffix root");
        helper.assertTrue(fixture.adapter().isDirty(),
                "INTERMEDIATE prefix clear must dirty SavedData");
    }

    private static void assertFinalJournal(
            GameTestHelper helper, InstalledRecoveryFixture fixture) {
        var projection = fixture.port().observePendingRecoveryCore(
                fixture.adapter(), fixture.owner());
        helper.assertTrue(
                projection
                        instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                                .Available available
                        && available.chains().isEmpty(),
                "FINAL recovery must clear the complete owner route");
        helper.assertTrue(roots(fixture).isEmpty(),
                "FINAL recovery must remove all journal roots for the route");
        helper.assertTrue(fixture.adapter().isDirty(),
                "FINAL full-prefix clear must dirty SavedData");
    }

    private static SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain onlyChain(
            InstalledRecoveryFixture fixture, int expectedSteps) {
        var projection = fixture.port().observePendingRecoveryCore(
                fixture.adapter(), fixture.owner());
        if (projection
                        instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                                .Available available
                && available.chains().size() == 1
                && available.chains().getFirst().skillId().equals(fixture.skillId())
                && available.chains().getFirst().steps().size() == expectedSteps) {
            return available.chains().getFirst();
        }
        throw new AssertionError("recovery journal did not retain the expected owner chain");
    }

    private static List<SkillReference> roots(InstalledRecoveryFixture fixture) {
        var projection = fixture.port().journalRootsCore(fixture.adapter());
        if (projection
                instanceof SkillDefinitionStoreSubmissionPort.JournalRootProjection.Available
                        available) {
            return available.references();
        }
        throw new AssertionError("recovery journal roots became unavailable");
    }

    private static Optional<PlayerSkillAttachmentService.LatestStateView> latestState(
            PlayerSkillAttachmentService attachments,
            ServerPlayer player,
            SkillId skillId) {
        var result = attachments.findLatestState(player, skillId);
        if (result instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value() instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                return Optional.empty();
            }
            if (optional.orElseThrow()
                    instanceof PlayerSkillAttachmentService.LatestStateView latest) {
                return Optional.of(latest);
            }
        }
        throw new AssertionError("latest-state observation was unavailable");
    }

    private static void publishToCurrent(
            PlayerSkillAttachmentService attachments,
            ServerPlayer player,
            SkillId skillId,
            SkillReference target) {
        var prepared = attachments.prepareLatestTransitionToCurrent(player, skillId, target);
        if (!(prepared instanceof PlayerSkillAttachmentService.Available<?> available)
                || !(available.value() instanceof PlayerSkillAttachmentService.Prepared value)) {
            throw new AssertionError("recovery player fixture transition was not prepared");
        }
        assertApplied(
                attachments.publishPreparedTransition(player, value.transition()),
                "recovery player fixture transition");
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

    private static void removeOnlinePlayer(MinecraftServer server, UUID playerId) {
        var online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            server.getPlayerList().remove(online);
        }
    }

    private static Path playerdataPath(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(playerId + ".dat");
    }

    private static void deletePlayerdata(MinecraftServer server, UUID playerId) {
        var primary = playerdataPath(server, playerId);
        var old = primary.resolveSibling(playerId + ".dat_old");
        try {
            Files.deleteIfExists(primary);
            Files.deleteIfExists(old);
        } catch (IOException exception) {
            throw new AssertionError("failed to clean P4-D3-A playerdata fixture", exception);
        }
    }

    private enum PersistedPosition {
        BASE,
        INTERMEDIATE,
        FINAL
    }

    private record ConnectedPlayer(ServerPlayer player, EmbeddedChannel channel) {
    }

    private record InstalledRecoveryFixture(
            GramaryeSkillSavedData adapter,
            SkillSavedDataState originalState,
            boolean originalDirty,
            SkillDefinitionStore store,
            EncodedSkillStoreCarrier storeCarrier,
            SkillDefinitionStoreSubmissionPort port,
            SkillOwnerId owner,
            SkillId skillId) implements AutoCloseable {
        @Override
        public void close() {
            adapter.publishState(adapter.state(), originalState);
            adapter.setDirty(originalDirty);
        }
    }
}
