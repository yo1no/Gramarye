package com.yo1no.gramarye.magic.definition.store;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.player.P4D3PlayerProbe;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.P4D3SubmissionProbe;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One run-mode-selected dedicated lifecycle for every P4-D3-B server process. */
@GameTestHolder("gramarye_p4_d3")
@PrefixGameTestTemplate(false)
public final class P4D3MemoryGameTests {
    private P4D3MemoryGameTests() {
    }

    @GameTest(
            templateNamespace = "gramarye_p4_d3",
            template = "p4_d3_probe",
            timeoutTicks = 11_000)
    public static void runSelectedCrashOrCombinedPhase(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var mode = P4D3ProbeSupport.runMode();
        try {
            var manifest = P4D3ProbeSupport.readManifest(server);
            requireInputPhase(mode, manifest);
            switch (mode) {
                case CRASH_D_FIRST -> runPassiveFirst(helper, server, mode, manifest,
                        "D_PENDING_EXPECTED", "D_CANONICAL_DISK_STATE");
                case CRASH_D_RESTART -> runReplayRestart(
                        helper, server, mode, manifest, "D_REPLAYED_PENDING");
                case CRASH_E_FIRST -> runEFirst(helper, server, mode, manifest);
                case CRASH_E_RESTART -> runReplayRestart(
                        helper, server, mode, manifest, "E_REPLAYED_PENDING");
                case CRASH_F_FIRST -> runPassiveFirst(helper, server, mode, manifest,
                        "F_PENDING_FINAL", "F_FINAL_DISK_STATE");
                case CRASH_F_RESTART -> runFinalClearRestart(
                        helper, server, mode, manifest, "F_FINAL_CLEARED");
                case CRASH_G_FIRST -> runGFirst(server, mode, manifest);
                case CRASH_G_RESTART -> runReplayRestart(
                        helper, server, mode, manifest, "G_REPLAYED_AFTER_HALT");
                case CRASH_H_FIRST -> runHFirst(server, mode, manifest);
                case CRASH_H_RESTART -> runFinalClearRestart(
                        helper, server, mode, manifest, "H_RECLEARED_AFTER_HALT");
                case CRASH_I_FIRST -> runI(helper, server, mode, manifest, false);
                case CRASH_I_RESTART -> runI(helper, server, mode, manifest, true);
                case CRASH_J1_FIRST, CRASH_J1_RESTART ->
                        runJ1(helper, server, mode, manifest);
                case COMBINED_FIRST -> runCombinedFirst(helper, server, mode, manifest);
                case COMBINED_RESTART -> runCombinedRestart(helper, server, mode, manifest);
            }
        } catch (IOException exception) {
            throw new AssertionError("P4-D3 dedicated disk operation failed", exception);
        }
    }

    private static void runPassiveFirst(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest,
            String stateCode,
            String outcomeCode) throws IOException {
        var live = P4D3ProbeSupport.requireLive(server, manifest);
        requirePending(live, 1, false);
        P4D3ProbeSupport.requirePlayerdataUnchanged(server, manifest);
        complete(helper, server, mode, stateCode, outcomeCode, live);
    }

    private static void runEFirst(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest) throws IOException {
        var live = P4D3ProbeSupport.requireLive(server, manifest);
        requirePending(live, 1, false);
        var attachments = P4D3PlayerProbe.newService();
        var detached = detachedPlayer(server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-e-memory");
        P4D3PlayerProbe.install(detached, P4D3PlayerProbe.AttachmentShape.EXPECTED);
        publishToCurrent(attachments, detached, P4D3ProbeSupport.skillId(0),
                P4D3ProbeSupport.target(0, 0));
        requireTuple(
                P4D3ProbeSupport.playerTuple(detached, P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 1);
        P4D3ProbeSupport.requirePlayerdataUnchanged(server, manifest);
        P4D3ProbeServerLifecycle.sample(server);
        complete(helper, server, mode,
                "E_SETDATA_IN_MEMORY_PLAYERDATA_NOT_SAVED",
                "E_CANONICAL_DISK_STATE", live);
    }

    private static void runReplayRestart(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest,
            String outcomeCode) throws IOException {
        P4D3ProbeSupport.requireLive(server, manifest);
        var connected = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-replay");
        requireTuple(
                P4D3ProbeSupport.playerTuple(
                        connected.player(), P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 1);
        var live = P4D3ProbeSupport.observeLive(server);
        requirePending(live, 1, false);
        P4D3ProbeServerLifecycle.sample(server);
        saveRemoveAndRelease(server, connected);
        requireDiskTuple(server, P4D3ProbeSupport.skillId(0),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 1);
        complete(helper, server, mode, "READY_TARGET_PENDING", outcomeCode, live);
    }

    private static void runFinalClearRestart(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest,
            String outcomeCode) throws IOException {
        P4D3ProbeSupport.requireLive(server, manifest);
        var connected = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-final");
        requireTuple(
                P4D3ProbeSupport.playerTuple(
                        connected.player(), P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 1);
        var cleared = P4D3ProbeSupport.observeLive(server);
        requirePending(cleared, 0, true);
        P4D3ProbeServerLifecycle.sample(server);
        saveRemoveAndRelease(server, connected);
        P4D3ProbeSupport.saveSavedDataAndWait(server);
        var clean = P4D3ProbeSupport.observeLive(server);
        requirePending(clean, 0, false);
        requireDiskTuple(server, P4D3ProbeSupport.skillId(0),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 1);
        complete(helper, server, mode, "READY_FINAL_JOURNAL_CLEAR", outcomeCode, clean);
    }

    private static void runGFirst(
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest) throws IOException {
        P4D3ProbeSupport.requireLive(server, manifest);
        var connected = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-g-halt");
        requireTuple(
                P4D3ProbeSupport.playerTuple(
                        connected.player(), P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 1);
        var live = P4D3ProbeSupport.observeLive(server);
        requirePending(live, 1, false);
        P4D3ProbeSupport.requirePlayerdataUnchanged(server, manifest);
        haltG(server, mode, live);
    }

    private static void runHFirst(
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest) throws IOException {
        P4D3ProbeSupport.requireLive(server, manifest);
        var connected = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-h-halt");
        requireTuple(
                P4D3ProbeSupport.playerTuple(
                        connected.player(), P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 1);
        var cleared = P4D3ProbeSupport.observeLive(server);
        requirePending(cleared, 0, true);
        P4D3ProbeSupport.requirePlayerdataUnchanged(server, manifest);
        haltH(server, mode, cleared);
    }

    private static void runI(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest,
            boolean requireByteStableRestart) throws IOException {
        P4D3ProbeSupport.requireLive(server, manifest);
        var connected = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-conflict");
        requireTuple(
                P4D3ProbeSupport.playerTuple(
                        connected.player(), P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 7);
        var live = P4D3ProbeSupport.observeLive(server);
        requirePending(live, 1, false);
        saveRemoveAndRelease(server, connected);
        requireDiskTuple(server, P4D3ProbeSupport.skillId(0),
                Optional.of(P4D3ProbeSupport.target(0, 0)), 7);
        if (requireByteStableRestart) {
            P4D3ProbeSupport.requirePlayerdataUnchanged(server, manifest);
        }
        complete(helper, server, mode, "READY_THIRD_STATE_PENDING",
                "I_THIRD_STATE_CONFLICT", live);
    }

    private static void runJ1(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest) throws IOException {
        var unavailable = P4D3ProbeSupport.requireLive(server, manifest);
        if (unavailable.journalReady()) {
            throw new AssertionError("P4-D3 J1 journal unexpectedly became operational");
        }
        var connected = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-j1");
        requireTuple(
                P4D3ProbeSupport.playerTuple(
                        connected.player(), P4D3ProbeSupport.skillId(0)),
                Optional.empty(), 0);
        requireJ1StoreAndJournalBoundaries(server);
        saveRemoveAndRelease(server, connected);
        requireDiskTuple(server, P4D3ProbeSupport.skillId(0), Optional.empty(), 0);
        var after = P4D3ProbeSupport.observeLive(server);
        if (after.journalReady() || !after.storeChecksum().equals(manifest.storeChecksum())) {
            throw new AssertionError("P4-D3 J1 mutated Store or recovered its invalid journal");
        }
        complete(helper, server, mode, "J1_JOURNAL_UNAVAILABLE",
                "J1_BOOTSTRAP_TARGET_AUDIT_FAILED", after);
    }

    private static void runCombinedFirst(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest) throws IOException {
        var startup = P4D3ProbeSupport.requireLive(server, manifest);
        requireCombinedCurrent(startup, true);
        var attachments = P4D3PlayerProbe.newService();
        var connected = placePlayer(
                server, P4D3ProbeSupport.submissionPlayerId(), "p4d3-submit");
        requireTuple(
                P4D3ProbeSupport.playerTuple(
                        connected.player(), P4D3StoreJournalFixture.submissionSkillId()),
                Optional.empty(), 0);

        P4D3ProbeSupport.LiveFacts committed;
        try (var store = P4D3ProbeSupport.installCombinedStore(server, manifest)) {
            var isolatedCurrent = P4D3ProbeSupport.observeLive(server);
            requireCombinedCurrent(isolatedCurrent, true);
            try (var held = P4D3ProbeSupport.beginHeldSavedDataSave(server, manifest)) {
                var facts = P4D3SubmissionProbe.submitActual(
                        connected.player(),
                        attachments,
                        store.submissionPort(),
                        P4D3StoreJournalFixture.submissionSkillId(),
                        peak -> {
                            if (!peak.target().equals(
                                            P4D3StoreJournalFixture.submissionTarget())
                                    || peak.warningCount() != 1
                                    || peak.documentNodeCount() != 1
                                    || peak.validatedNodeCount() != 1) {
                                throw new AssertionError(
                                        "P4-D3 D2 preparation peak facts changed");
                            }
                            var current = P4D3ProbeSupport.observeLive(server);
                            requireCombinedCurrent(current, false);
                            store.retainAtPeak();
                            P4D3ProbeServerLifecycle.sample(server);
                        });
                if (!facts.target().equals(P4D3StoreJournalFixture.submissionTarget())
                        || facts.warningCount() != 1
                        || facts.documentNodeCount() != 1
                        || facts.validatedNodeCount() != 1
                        || facts.stageCounts().rejectionMapping() != 0) {
                    throw new AssertionError("P4-D3 actual D2 submission facts changed");
                }
                requireTuple(
                        P4D3ProbeSupport.playerTuple(
                                connected.player(),
                                P4D3StoreJournalFixture.submissionSkillId()),
                        Optional.of(P4D3StoreJournalFixture.submissionTarget()), 1);
                committed = P4D3ProbeSupport.observeLive(server);
                if (!committed.journalReady()
                        || committed.histories() != 2_049
                        || committed.revisions() != 4_096
                        || committed.journalEntries()
                                != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES
                        || committed.journalBytes()
                                != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_BYTES
                        || committed.rootCount()
                                != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES
                        || !committed.dirty()
                        || committed.rewriteRequired()
                        || committed.storeChecksum().equals(manifest.storeChecksum())) {
                    throw new AssertionError(
                            "P4-D3 committed Store/journal publication changed");
                }
                P4D3ProbeServerLifecycle.sample(server);
                saveRemoveAndRelease(server, connected);
                requireSubmissionDisk(server);
                store.retainAtPeak();
            }

            P4D3ProbeSupport.saveSavedDataAndWait(server);
            committed = P4D3ProbeSupport.observeLive(server);
            if (committed.dirty()
                    || committed.histories() != 2_049
                    || committed.revisions() != 4_096
                    || committed.journalEntries() != 4_096
                    || committed.journalBytes()
                            != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_BYTES) {
                throw new AssertionError("P4-D3 combined first strict save changed live state");
            }
            requireSubmissionDisk(server);
            store.retainAtPeak();
        }
        complete(helper, server, mode, "COMBINED_SUBMITTED_PENDING",
                "D2_COMMITTED_WARNING_REPORT_RETAINED", committed);
    }

    private static void runCombinedRestart(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.ManifestView manifest) throws IOException {
        var initial = P4D3ProbeSupport.requireLive(server, manifest);
        if (initial.dirty()
                || initial.histories() != 2_049
                || initial.revisions() != 4_096
                || initial.journalEntries() != 4_096
                || initial.journalBytes()
                        != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_BYTES) {
            throw new AssertionError("P4-D3 combined restart was not canonical Ready");
        }

        var first = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-combined-first-login");
        requireTuple(
                P4D3ProbeSupport.playerTuple(first.player(), P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 1)), 2);
        requireTuple(
                P4D3ProbeSupport.playerTuple(first.player(), P4D3ProbeSupport.skillId(1)),
                Optional.of(P4D3ProbeSupport.target(1, 1)), 2);
        var afterReplay = P4D3ProbeSupport.observeLive(server);
        if (afterReplay.journalEntries() != 4_094
                || afterReplay.rootCount() != 4_094
                || !afterReplay.dirty()
                || !afterReplay.storeChecksum().equals(manifest.storeChecksum())) {
            throw new AssertionError(
                    "P4-D3 combined FINAL-clear then BASE-replay state changed");
        }
        P4D3ProbeServerLifecycle.sample(server);
        saveRemoveAndRelease(server, first);
        requireSelectedCombinedDisk(server);

        var second = placePlayer(
                server, P4D3ProbeSupport.selectedPlayerId(), "p4d3-combined-second-login");
        requireTuple(
                P4D3ProbeSupport.playerTuple(second.player(), P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 1)), 2);
        requireTuple(
                P4D3ProbeSupport.playerTuple(second.player(), P4D3ProbeSupport.skillId(1)),
                Optional.of(P4D3ProbeSupport.target(1, 1)), 2);
        var cleared = P4D3ProbeSupport.observeLive(server);
        if (cleared.journalEntries() != 4_092
                || cleared.rootCount() != 4_092
                || !cleared.dirty()
                || !cleared.storeChecksum().equals(manifest.storeChecksum())) {
            throw new AssertionError("P4-D3 combined second login did not clear BASE chain");
        }
        P4D3ProbeServerLifecycle.sample(server);
        saveRemoveAndRelease(server, second);
        requireSelectedCombinedDisk(server);
        requireSubmissionDisk(server);

        P4D3ProbeSupport.saveSavedDataAndWait(server);
        var clean = P4D3ProbeSupport.observeLive(server);
        if (clean.journalEntries() != 4_092
                || clean.rootCount() != 4_092
                || clean.dirty()
                || !clean.storeChecksum().equals(manifest.storeChecksum())) {
            throw new AssertionError("P4-D3 combined restart strict save changed state");
        }
        complete(helper, server, mode, "COMBINED_SELECTED_CHAINS_CLEARED",
                "FINAL_CLEAR_BASE_REPLAY_RELOGIN_CLEAR", clean);
    }

    private static void haltG(
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.LiveFacts live) throws IOException {
        var metrics = finishMetrics(server);
        var published = P4D3ProbeSupport.publishPhase(
                server, mode, "G_REPLAY_APPLIED_PLAYERDATA_NOT_SAVED",
                "G_EXPECTED_HARD_HALT", metrics);
        System.out.println(P4D3ProbeSupport.summary(mode, published, live, metrics));
        System.out.flush();
        Runtime.getRuntime().halt(0);
        throw new AssertionError("P4-D3 G hard halt returned");
    }

    private static void haltH(
            MinecraftServer server,
            P4D3RunMode mode,
            P4D3ProbeSupport.LiveFacts live) throws IOException {
        var metrics = finishMetrics(server);
        var published = P4D3ProbeSupport.publishPhase(
                server, mode, "H_CLEAR_IN_MEMORY_NOT_SAVED",
                "H_EXPECTED_HARD_HALT", metrics);
        System.out.println(P4D3ProbeSupport.summary(mode, published, live, metrics));
        System.out.flush();
        Runtime.getRuntime().halt(0);
        throw new AssertionError("P4-D3 H hard halt returned");
    }

    private static void complete(
            GameTestHelper helper,
            MinecraftServer server,
            P4D3RunMode mode,
            String stateCode,
            String outcomeCode,
            P4D3ProbeSupport.LiveFacts live) throws IOException {
        var metrics = finishMetrics(server);
        var published = P4D3ProbeSupport.publishPhase(
                server, mode, stateCode, outcomeCode, metrics);
        System.out.println(P4D3ProbeSupport.summary(mode, published, live, metrics));
        helper.succeed();
    }

    private static P4D3ProbeSupport.HeapMetrics finishMetrics(MinecraftServer server) {
        P4D3ProbeServerLifecycle.sample(server);
        var metrics = P4D3ProbeServerLifecycle.finish(server);
        return new P4D3ProbeSupport.HeapMetrics(
                metrics.maximum(), metrics.initialCommitted(), metrics.sampledPeak(),
                metrics.poolPeakSum(), metrics.elapsedMillis());
    }

    private static void requireInputPhase(
            P4D3RunMode mode, P4D3ProbeSupport.ManifestView manifest) {
        var expected = mode.restart()
                ? mode.probeCase().token() + "-first-complete"
                : "prepared";
        if (manifest.probeCase() != mode.probeCase()
                || !manifest.phase().equals(expected)) {
            throw new AssertionError("P4-D3 input manifest phase differs from run mode");
        }
    }

    private static void requirePending(
            P4D3ProbeSupport.LiveFacts live, int entries, boolean dirty) {
        if (!live.journalReady()
                || live.journalEntries() != entries
                || live.rootCount() != entries
                || live.dirty() != dirty) {
            throw new AssertionError("P4-D3 live journal state differs from expected matrix");
        }
    }

    private static void publishToCurrent(
            PlayerSkillAttachmentService attachments,
            ServerPlayer player,
            SkillId skillId,
            com.yo1no.gramarye.magic.definition.document.SkillReference target) {
        var prepared = attachments.prepareLatestTransitionToCurrent(player, skillId, target);
        if (!(prepared instanceof PlayerSkillAttachmentService.Available<?> preparedAvailable)
                || !(preparedAvailable.value()
                        instanceof PlayerSkillAttachmentService.Prepared value)
                || value.transition().isNoOp()) {
            throw new AssertionError("P4-D3 E Attachment transition was not prepared");
        }
        var published = attachments.publishPreparedTransition(player, value.transition());
        if (!(published instanceof PlayerSkillAttachmentService.Available<?> publishedAvailable)
                || publishedAvailable.value()
                        != PlayerSkillAttachmentService.Applied.INSTANCE) {
            throw new AssertionError("P4-D3 E Attachment transition was not published");
        }
    }

    private static void requireJ1StoreAndJournalBoundaries(MinecraftServer server) {
        P4D3ProbeSupport.requireJ1ControlledStoreAccess(server);
    }

    private static void requireCombinedCurrent(
            P4D3ProbeSupport.LiveFacts live, boolean dirty) {
        if (!live.journalReady()
                || live.storeBytes() != P4D3StoreJournalFixture.STORE_BYTES
                || live.histories() != P4D3StoreJournalFixture.HISTORY_COUNT
                || live.revisions() != P4D3StoreJournalFixture.REVISION_COUNT
                || live.journalBytes() != P4D3StoreJournalFixture.CURRENT_JOURNAL_BYTES
                || live.journalEntries()
                        != P4D3StoreJournalFixture.CURRENT_JOURNAL_ENTRIES
                || live.rootCount() != P4D3StoreJournalFixture.CURRENT_JOURNAL_ENTRIES
                || live.dirty() != dirty
                || !live.rewriteRequired()) {
            throw new AssertionError("P4-D3 current combined envelope changed");
        }
    }

    private static void requireSubmissionDisk(MinecraftServer server) throws IOException {
        var disk = P4D3PlayerProbe.readPlayerdata(
                P4D3ProbeSupport.worldRoot(server), P4D3ProbeSupport.submissionPlayerId());
        requireTuple(
                disk.tuple(P4D3StoreJournalFixture.submissionSkillId()),
                Optional.of(P4D3StoreJournalFixture.submissionTarget()), 1);
        if (disk.draftCount() != 1
                || disk.witness() != P4D3PlayerProbe.PREPARED_WITNESS) {
            throw new AssertionError("P4-D3 submission Draft or witness changed on disk");
        }
    }

    private static void requireSelectedCombinedDisk(MinecraftServer server)
            throws IOException {
        var disk = P4D3PlayerProbe.readPlayerdata(
                P4D3ProbeSupport.worldRoot(server), P4D3ProbeSupport.selectedPlayerId());
        requireTuple(
                disk.tuple(P4D3ProbeSupport.skillId(0)),
                Optional.of(P4D3ProbeSupport.target(0, 1)), 2);
        requireTuple(
                disk.tuple(P4D3ProbeSupport.skillId(1)),
                Optional.of(P4D3ProbeSupport.target(1, 1)), 2);
        if (disk.witness() != P4D3PlayerProbe.PREPARED_WITNESS) {
            throw new AssertionError("P4-D3 selected-player witness changed on disk");
        }
    }

    private static void requireDiskTuple(
            MinecraftServer server,
            SkillId skillId,
            Optional<com.yo1no.gramarye.magic.definition.document.SkillReference> pointer,
            int generation) throws IOException {
        var disk = P4D3PlayerProbe.readPlayerdata(
                P4D3ProbeSupport.worldRoot(server), P4D3ProbeSupport.selectedPlayerId());
        requireTuple(disk.tuple(skillId), pointer, generation);
    }

    private static void requireTuple(
            P4D3PlayerProbe.Tuple tuple,
            Optional<com.yo1no.gramarye.magic.definition.document.SkillReference> pointer,
            int generation) {
        if (!tuple.pointer().equals(pointer) || tuple.generation() != generation) {
            throw new AssertionError("P4-D3 Attachment tuple differs from expected matrix");
        }
    }

    private static ServerPlayer detachedPlayer(
            MinecraftServer server, UUID playerId, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId, name), false);
        return new ServerPlayer(
                server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
    }

    private static ConnectedPlayer placePlayer(
            MinecraftServer server, UUID playerId, String name) {
        removeOnlinePlayer(server, playerId);
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId, name), false);
        var player = new ServerPlayer(
                server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        drainOutbound(channel);
        return new ConnectedPlayer(player, channel);
    }

    private static void saveRemoveAndRelease(
            MinecraftServer server, ConnectedPlayer connected) {
        server.getPlayerList().saveAll();
        server.getPlayerList().remove(connected.player());
        drainOutbound(connected.channel());
        connected.channel().finishAndReleaseAll();
    }

    private static void removeOnlinePlayer(MinecraftServer server, UUID playerId) {
        var current = server.getPlayerList().getPlayer(playerId);
        if (current != null) {
            server.getPlayerList().remove(current);
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

    private record ConnectedPlayer(ServerPlayer player, EmbeddedChannel channel) {
    }
}
