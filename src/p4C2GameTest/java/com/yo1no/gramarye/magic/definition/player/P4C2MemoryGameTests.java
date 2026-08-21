package com.yo1no.gramarye.magic.definition.player;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P4E2QualificationFacadeTestAccess;
import com.yo1no.gramarye.P4E2QualificationObservation;
import com.yo1no.gramarye.magic.definition.store.P4C2StoreProbe;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One dispatcher; the fixed run property selects exactly one lifecycle process. */
@GameTestHolder("gramarye_p4_c2")
@PrefixGameTestTemplate(false)
public final class P4C2MemoryGameTests {
    private static final long PLAYERDATA_QUOTA_BYTES = 64L * 1_024L * 1_024L;
    private static final ServerboundClientCommandPacket RESPAWN_PACKET =
            new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);

    private P4C2MemoryGameTests() {
    }

    @GameTest(
            templateNamespace = "gramarye_p4_c2",
            template = "p4_c2_probe",
            timeoutTicks = 12_000)
    public static void executeConfiguredPhase(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        if (!server.isSameThread()) {
            throw new AssertionError("P4-C2 GameTest is not on the server thread");
        }
        var mode = P4C2RunMode.fromToken(
                System.getProperty(P4C2RunMode.SYSTEM_PROPERTY, ""));
        var worldRoot = server.getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        var manifest = P4C2FixtureManifest.read(worldRoot);
        if (manifest.runMode() != mode || manifest.probeCase() != mode.probeCase()) {
            throw new AssertionError("P4-C2 run property and manifest differ");
        }
        requirePreparedPlayerdata(worldRoot, manifest, mode);

        P4C2StoreProbe.HeldFirstSave heldStore = null;
        P4C2StoreProbe.StoreFacts storeFacts = null;
        ConnectedPlayer connected = null;
        P4E2QualificationFacadeTestAccess.Handle observationHandle = null;
        var keepInventory = server.overworld().getGameRules()
                .getRule(GameRules.RULE_KEEPINVENTORY);
        var originalKeepInventory = keepInventory.get();
        Throwable primaryFailure = null;
        try {
            if (mode.probeCase() == P4C2ProbeCase.READY) {
                storeFacts = P4C2FixtureBuilder.requireReadyLive(server);
            } else if (mode.probeCase() == P4C2ProbeCase.PRESERVED_RAW) {
                var expectedStore = manifest.expectedStore(worldRoot);
                if (mode.restart()) {
                    storeFacts = P4C2StoreProbe.requireCleanRestart(
                            server, expectedStore);
                } else {
                    heldStore = P4C2StoreProbe.beginHeldFirstSave(
                            server, expectedStore);
                    storeFacts = heldStore.facts();
                }
            }
            P4C2ProbeServerLifecycle.sample(server);

            if (mode.probeCase() == P4C2ProbeCase.READY) {
                observationHandle = P4E2QualificationFacadeTestAccess.armReady(
                        server, manifest.probeCase().playerId(), mode.restart());
            }
            connected = placePlayer(server, manifest.probeCase());
            if (observationHandle != null) {
                var observation = P4E2QualificationFacadeTestAccess.consumeReady(
                        observationHandle);
                var phase = mode.restart()
                        ? P4E2QualificationObservation.Phase.RESTART
                        : P4E2QualificationObservation.Phase.FIRST;
                observation.requireReady(phase, manifest.probeCase().playerId());
                observation.writeNewIn(worldRoot.getParent());
            }
            var current = connected.player();
            var expectedInitialChecksum = mode.restart()
                    ? manifest.expectedAttachmentChecksum()
                    : manifest.probeCase() == P4C2ProbeCase.OVERSIZE
                            ? manifest.expectedAttachmentChecksum()
                            : manifest.sourceAttachmentChecksum();
            var expectedInitialBytes = mode.restart()
                    ? manifest.expectedAttachmentBytes()
                    : manifest.probeCase() == P4C2ProbeCase.OVERSIZE
                            ? manifest.expectedAttachmentBytes()
                            : manifest.sourceAttachmentBytes();
            assertState(current, manifest, expectedInitialChecksum, expectedInitialBytes);
            saveAndStrictReadback(
                    server,
                    current,
                    manifest,
                    mode.restart()
                            ? P4C2FixtureManifest.INITIAL_RESTART_WITNESS
                            : P4C2FixtureManifest.INITIAL_FIRST_WITNESS,
                    expectedInitialChecksum,
                    expectedInitialBytes,
                    false);
            if (mode == P4C2RunMode.READY_FIRST) {
                applyReadyFirstMutations(current);
                assertState(
                        current,
                        manifest,
                        manifest.expectedAttachmentChecksum(),
                        manifest.expectedAttachmentBytes());
            }
            P4C2ProbeServerLifecycle.sample(server);

            keepInventory.set(mode.restart(), server);
            current = actualDeathRespawn(server, current, manifest);
            P4C2ProbeServerLifecycle.sample(server);
            current = actualEndReturn(server, current, manifest);
            P4C2ProbeServerLifecycle.sample(server);

            var disk = saveAndStrictReadback(
                    server,
                    current,
                    manifest,
                    manifest.expectedWitness(),
                    manifest.expectedAttachmentChecksum(),
                    manifest.expectedAttachmentBytes(),
                    true);
            P4C2ProbeServerLifecycle.sample(server);
            server.getPlayerList().remove(current);
            drainOutbound(connected.channel());

            if (heldStore != null) {
                var closingStore = heldStore;
                heldStore = null;
                closingStore.close();
            }
            if (mode.probeCase() == P4C2ProbeCase.READY
                    && !P4C2FixtureBuilder.requireReadyLive(server).equals(storeFacts)) {
                throw new AssertionError("READY Store truth changed during the player lifecycle");
            }
            var metrics = P4C2ProbeServerLifecycle.finish(server);
            System.out.println(summary(
                    manifest, disk, storeFacts, metrics).line());
            helper.succeed();
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            P4E2QualificationFacadeTestAccess.discardPreservingPrimary(
                    observationHandle, primaryFailure);
            keepInventory.set(originalKeepInventory, server);
            if (heldStore != null) {
                heldStore.close();
            }
            if (connected != null) {
                connected.channel().finishAndReleaseAll();
            }
        }
    }

    private static void requirePreparedPlayerdata(
            java.nio.file.Path worldRoot,
            P4C2FixtureManifest manifest,
            P4C2RunMode mode) throws IOException {
        var path = P4C2FixtureManifest.playerdata(worldRoot, manifest.probeCase());
        if (Files.size(path) != manifest.expectedPlayerdataBytes()
                || !P4C2Hashing.sha256(path)
                        .equals(manifest.expectedPlayerdataChecksum())) {
            throw new AssertionError("P4-C2 playerdata does not match its input manifest");
        }
        var root = NbtIo.readCompressed(
                path, NbtAccounter.create(PLAYERDATA_QUOTA_BYTES));
        var inputWitness = mode.restart()
                ? P4C2FixtureManifest.FIRST_WITNESS
                : P4C2FixtureManifest.PREPARED_WITNESS;
        if (!(root.get("NeoForgeData") instanceof CompoundTag persistent)
                || persistent.getInt(P4C2FixtureManifest.WITNESS_KEY) != inputWitness) {
            throw new AssertionError("P4-C2 prepared witness differs from its phase");
        }
        var attachment = P4C2FileVerifier.attachment(root);
        if (P4C2FixtureBuilder.exactCount(attachment)
                        != (mode.restart()
                                ? manifest.expectedAttachmentBytes()
                                : manifest.sourceAttachmentBytes())
                || !P4C2Hashing.sha256(attachment).equals(mode.restart()
                        ? manifest.expectedAttachmentChecksum()
                        : manifest.sourceAttachmentChecksum())) {
            throw new AssertionError("P4-C2 prepared Attachment differs from manifest");
        }
    }

    private static void applyReadyFirstMutations(ServerPlayer player) {
        var service = new PlayerSkillAttachmentService();
        requireApplied(service.putDraft(player, P4C2FixtureBuilder.readyDraft(2)),
                "Ready Draft replacement");
        requireApplied(service.setEquipped(
                        player,
                        1,
                        Optional.of(P4C2FixtureBuilder.READY_FINAL_REFERENCE)),
                "Ready equipped replacement");
        requireApplied(service.setEditorState(
                        player,
                        new PlayerSkillAttachmentService.EditorStateView(
                                Optional.of(P4C2FixtureBuilder.READY_FINAL_EDITOR_ID),
                                OptionalInt.of(254))),
                "Ready editor replacement");
        var preparedResult = service.prepareLatestTransition(
                player,
                P4C2FixtureBuilder.READY_DRAFT_ID,
                Optional.of(P4C2FixtureBuilder.READY_INITIAL_REFERENCE),
                1,
                Optional.of(P4C2FixtureBuilder.READY_FINAL_REFERENCE));
        if (!(preparedResult instanceof PlayerSkillAttachmentService.Available<?> available)
                || !(available.value()
                        instanceof PlayerSkillAttachmentService.Prepared prepared)
                || prepared.transition().isNoOp()
                || prepared.transition().targetGeneration() != 2) {
            throw new AssertionError("Ready latest transition did not prepare");
        }
        requireApplied(
                service.publishPreparedTransition(player, prepared.transition()),
                "Ready latest publication");
    }

    private static ServerPlayer actualDeathRespawn(
            MinecraftServer server,
            ServerPlayer current,
            P4C2FixtureManifest manifest) {
        var oldState = current.getData(PlayerSkillAttachments.type());
        current.setHealth(0.0F);
        P4C2ProbeServerLifecycle.beginClone(
                server, current.getUUID(), true, false);
        var listener = current.connection;
        current.connection.handleClientCommand(RESPAWN_PACKET);
        P4C2ProbeServerLifecycle.finishClone(server);
        var replacement = listener.player;
        if (replacement == current
                || replacement.getHealth() <= 0.0F
                || server.getPlayerList().getPlayer(current.getUUID()) != replacement) {
            throw new AssertionError("actual death respawn did not replace the player");
        }
        var replacementState = replacement.getData(PlayerSkillAttachments.type());
        assertCloneState(oldState, replacementState, manifest);
        assertState(
                replacement,
                manifest,
                manifest.expectedAttachmentChecksum(),
                manifest.expectedAttachmentBytes());
        return replacement;
    }

    private static ServerPlayer actualEndReturn(
            MinecraftServer server,
            ServerPlayer current,
            P4C2FixtureManifest manifest) {
        var end = server.getLevel(Level.END);
        if (end == null) {
            throw new AssertionError("dedicated P4-C2 world has no End level");
        }
        var beforeTransfer = current.getData(PlayerSkillAttachments.type());
        var transferred = current.changeDimension(new DimensionTransition(
                end,
                current,
                DimensionTransition.DO_NOTHING));
        current.hasChangedDimension();
        if (transferred != current
                || current.serverLevel() != end
                || current.getData(PlayerSkillAttachments.type()) != beforeTransfer) {
            throw new AssertionError("ordinary End transfer replaced the Attachment/player");
        }

        P4C2ProbeServerLifecycle.beginClone(
                server, current.getUUID(), false, true);
        current.showEndCredits();
        if (!current.wonGame) {
            throw new AssertionError("showEndCredits did not enter End-return state");
        }
        var listener = current.connection;
        current.connection.handleClientCommand(RESPAWN_PACKET);
        P4C2ProbeServerLifecycle.finishClone(server);
        var replacement = listener.player;
        if (replacement == current
                || replacement.serverLevel() != server.overworld()
                || server.getPlayerList().getPlayer(current.getUUID()) != replacement) {
            throw new AssertionError("actual End return did not create Overworld player");
        }
        var replacementState = replacement.getData(PlayerSkillAttachments.type());
        assertCloneState(beforeTransfer, replacementState, manifest);
        assertState(
                replacement,
                manifest,
                manifest.expectedAttachmentChecksum(),
                manifest.expectedAttachmentBytes());
        return replacement;
    }

    private static void assertCloneState(
            PlayerSkillAttachmentState oldState,
            PlayerSkillAttachmentState newState,
            P4C2FixtureManifest manifest) {
        if (oldState == newState) {
            throw new AssertionError("copyOnDeath reused the Attachment state identity");
        }
        if (oldState instanceof PlayerSkillAttachmentPreservedRaw oldPreserved
                && newState instanceof PlayerSkillAttachmentPreservedRaw newPreserved) {
            var oldCopy = oldPreserved.copyRaw();
            var newCopy = newPreserved.copyRaw();
            if (!(oldCopy instanceof ByteArrayTag oldBytes)
                    || !(newCopy instanceof ByteArrayTag newBytes)
                    || !P4C2Hashing.payloadSha256(oldBytes).equals(manifest.payloadChecksum())
                    || !P4C2Hashing.payloadSha256(newBytes).equals(manifest.payloadChecksum())) {
                throw new AssertionError("PreservedRaw clone changed its structural value");
            }
            oldBytes.getAsByteArray()[0] ^= 0x7F;
            if (oldBytes.getAsByteArray()[0] == newBytes.getAsByteArray()[0]) {
                throw new AssertionError("PreservedRaw clone exposed a raw alias");
            }
            if (!(oldPreserved.copyRaw() instanceof ByteArrayTag freshOld)
                    || !(newPreserved.copyRaw() instanceof ByteArrayTag freshNew)
                    || !P4C2Hashing.payloadSha256(freshOld).equals(manifest.payloadChecksum())
                    || !P4C2Hashing.payloadSha256(freshNew).equals(manifest.payloadChecksum())) {
                throw new AssertionError("mutating a PreservedRaw copy changed hidden state");
            }
        }
    }

    private static void assertState(
            ServerPlayer player,
            P4C2FixtureManifest manifest,
            String expectedChecksum,
            long expectedBytes) {
        if (!player.hasData(PlayerSkillAttachments.type())) {
            throw new AssertionError("delivered P4-C2 Attachment became missing");
        }
        var state = player.getData(PlayerSkillAttachments.type());
        var service = new PlayerSkillAttachmentService();
        switch (manifest.probeCase()) {
            case READY -> {
                if (!(state instanceof PlayerSkillAttachmentReady ready)
                        || ready.carrier().encodedByteCount() != expectedBytes
                        || !P4C2Hashing.sha256(ready.carrier().copyTag())
                                .equals(expectedChecksum)
                        || ready.drafts().size() != manifest.expectedDrafts()
                        || ready.latestStates().size() != manifest.expectedLatest()
                        || ready.equipped().size() != manifest.expectedEquipped()) {
                    throw new AssertionError("P4-C2 Ready state differs from manifest");
                }
            }
            case PRESERVED_RAW -> {
                if (!(state instanceof PlayerSkillAttachmentPreservedRaw preserved)
                        || preserved.exactEncodedByteCount() != expectedBytes
                        || !(preserved.copyRaw() instanceof ByteArrayTag bytes)
                        || bytes.size() != P4C2FixtureBuilder.PRESERVED_PAYLOAD_BYTES
                        || !P4C2Hashing.payloadSha256(bytes)
                                .equals(manifest.payloadChecksum())
                        || !unavailable(service.draftCount(player),
                                PlayerSkillAttachmentService.UnavailableReason
                                        .PRESERVED_RAW_QUARANTINE)) {
                    throw new AssertionError("P4-C2 PreservedRaw state differs from manifest");
                }
            }
            case OVERSIZE -> {
                if (!(state instanceof PlayerSkillAttachmentOversizeMarker marker)
                        || marker.observedAtLeast()
                                != P4C2FixtureBuilder.OVERSIZE_ATTACHMENT_BYTES
                        || !unavailable(service.draftCount(player),
                                PlayerSkillAttachmentService.UnavailableReason
                                        .OVERSIZE_QUARANTINE)) {
                    throw new AssertionError("P4-C2 OversizeMarker state differs from manifest");
                }
            }
        }
    }

    private static boolean unavailable(
            PlayerSkillAttachmentService.Result<?> result,
            PlayerSkillAttachmentService.UnavailableReason reason) {
        return result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable
                && unavailable.reason() == reason;
    }

    private static DiskFacts saveAndStrictReadback(
            MinecraftServer server,
            ServerPlayer player,
            P4C2FixtureManifest manifest,
            int witness,
            String expectedAttachmentChecksum,
            long expectedAttachmentBytes,
            boolean requireFinalShape) throws IOException {
        player.getPersistentData().putInt(
                P4C2FixtureManifest.WITNESS_KEY, witness);
        server.getPlayerList().saveAll();
        var path = P4C2FixtureManifest.playerdata(
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),
                manifest.probeCase());
        var root = NbtIo.readCompressed(
                path, NbtAccounter.create(PLAYERDATA_QUOTA_BYTES));
        if (!(root.get("NeoForgeData") instanceof CompoundTag persistent)
                || persistent.getInt(P4C2FixtureManifest.WITNESS_KEY)
                        != witness) {
            throw new AssertionError("synchronous player save lost its current witness");
        }
        var attachment = P4C2FileVerifier.attachment(root);
        if (P4C2FixtureBuilder.exactCount(attachment) != expectedAttachmentBytes
                || !P4C2Hashing.sha256(attachment).equals(expectedAttachmentChecksum)) {
            throw new AssertionError("synchronous player save wrote a stale Attachment");
        }
        if (requireFinalShape) {
            P4C2FileVerifier.verifyAttachment(attachment, manifest);
        }
        return new DiskFacts(
                Files.size(path), P4C2Hashing.sha256(attachment));
    }

    private static P4C2ProbeSummary summary(
            P4C2FixtureManifest manifest,
            DiskFacts disk,
            P4C2StoreProbe.StoreFacts store,
            P4C2ProbeServerLifecycle.ServerMetrics metrics) {
        var unavailable = manifest.probeCase() != P4C2ProbeCase.READY;
        return new P4C2ProbeSummary(
                manifest.probeCase().token(),
                manifest.runMode().token(),
                manifest.expectedState(),
                manifest.expectedAttachmentBytes(),
                disk.compressedBytes(),
                unavailable ? "unavailable" : Integer.toString(manifest.expectedDrafts()),
                unavailable ? "unavailable" : Integer.toString(manifest.expectedLatest()),
                unavailable ? "unavailable" : Integer.toString(manifest.expectedEquipped()),
                metrics.maximum(),
                metrics.initialCommitted(),
                metrics.sampledPeak(),
                metrics.poolPeakSum(),
                metrics.elapsedMillis(),
                P4C2Hashing.witness(disk.attachmentChecksum()),
                store == null ? 0 : store.storeBytes(),
                store == null ? 0 : store.histories(),
                store == null ? 0 : store.revisions(),
                store == null ? "none" : P4C2Hashing.witness(store.checksum()));
    }

    private static ConnectedPlayer placePlayer(
            MinecraftServer server, P4C2ProbeCase probeCase) {
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(probeCase.playerId(), "p4c2-" + probeCase.token()),
                false);
        var player = new ServerPlayer(
                server,
                server.overworld(),
                cookie.gameProfile(),
                cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        drainOutbound(channel);
        return new ConnectedPlayer(player, channel);
    }

    private static void drainOutbound(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.runScheduledPendingTasks();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(message);
        }
    }

    private static void requireApplied(
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome> result,
            String operation) {
        if (!(result instanceof PlayerSkillAttachmentService.Available<?> available)
                || available.value() != PlayerSkillAttachmentService.Applied.INSTANCE) {
            throw new AssertionError(operation + " was not applied");
        }
    }

    private record ConnectedPlayer(ServerPlayer player, EmbeddedChannel channel) {
    }

    private record DiskFacts(long compressedBytes, String attachmentChecksum) {
    }
}
