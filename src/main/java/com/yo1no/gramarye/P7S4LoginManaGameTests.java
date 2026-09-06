package com.yo1no.gramarye;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Direct server-bound S4 qualification with bounded test-owned playerdata lifetimes. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class P7S4LoginManaGameTests {
    private static final String SAVED_DATA_NAME = "gramarye_skill_definitions";
    private static final SavedData.Factory<SavedData> CACHE_ONLY = new SavedData.Factory<>(
            () -> { throw new AssertionError("P7 test expected the installed Store"); },
            (tag, provider) -> { throw new AssertionError("P7 test forbids cache-miss load"); });

    private P7S4LoginManaGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void manaObservationPreservesAvailableAndMalformedAttachmentTruth(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var actor = unplacedPlayer(server, 1, "p7s4-mana");
        var capability = P6RuntimeExecutionCapability.forRuntimeAdapter();
        helper.assertTrue(P7ManaSnapshotBridge.observeBalance(capability, actor) == 0L,
                "absent registered mana truth must produce the available zero default");
        for (var balance : new long[] {0L, 731L, 1_000_000_000L}) {
            var payload = new CompoundTag();
            payload.putInt("schema_version", 0);
            payload.putLong("balance", balance);
            loadAttachment(actor, "gramarye:player_mana", payload);
            var before = attachmentPayload(actor, "gramarye:player_mana").copy();
            helper.assertTrue(P7ManaSnapshotBridge.observeBalance(capability, actor) == balance,
                    "mana observation must return the exact bounded persistent scalar");
            helper.assertTrue(before.equals(attachmentPayload(actor, "gramarye:player_mana")),
                    "observation must preserve the serialized Attachment truth");
        }
        var malformed = new CompoundTag();
        malformed.putInt("schema_version", 0);
        loadAttachment(actor, "gramarye:player_mana", malformed);
        var before = attachmentPayload(actor, "gramarye:player_mana").copy();
        helper.assertTrue(P7ManaSnapshotBridge.observeBalance(capability, actor) == -1L,
                "present malformed state must remain explicitly unavailable");
        helper.assertTrue(before.equals(attachmentPayload(actor, "gramarye:player_mana")),
                "unavailable observation must not repair or rewrite malformed truth");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void loginPortRejectsNoncurrentPlayerBeforeSessionOpen(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var actor = unplacedPlayer(server, 2, "p7s4-stale");
        var port = P7ServerAuthorizationBoundary.loginReadyPort(
                P6RuntimeExecutionCapability.forRuntimeAdapter());
        var rejected = false;
        try {
            port.onLoginReady(server, actor);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "unplaced actor must fail exact current-player identity");
        helper.assertTrue(server.getPlayerList().getPlayer(actor.getUUID()) == null,
                "rejected login must not publish a player or session recipient");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2NormalAndChangedTerminalsHandoffOnceAndQuarantineNeverHandoffs(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var calls = new AtomicInteger();
        var tick = server.getTickCount();
        try (var player = placePlayer(server, 3, "p7s4-e2");
                var fixture = loginFixture(server, (exactServer, actor) -> {
                    helper.assertTrue(exactServer == server
                                    && actor == server.getPlayerList().getPlayer(actor.getUUID())
                                    && server.isSameThread(),
                            "E2 must call the port on the exact server/current player/thread");
                    calls.incrementAndGet();
                })) {
            var actor = player.actor();
            var collisionRefused = false;
            try {
                placePlayer(server, 3, "p7s4-collision");
            } catch (IllegalStateException expected) {
                collisionRefused = true;
            }
            helper.assertTrue(collisionRefused
                            && server.getPlayerList().getPlayer(actor.getUUID()) == actor,
                    "fixture claim must refuse an already-live UUID without replacing its owner");
            var unchanged = postObservedLogin(fixture, actor, 1);
            helper.assertTrue(unchanged.reconciliationVariant()
                            == P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES,
                    "fresh E2 fixture must end in NoChanges");
            helper.assertTrue(calls.get() == 1 && unchanged.continuationCalls() == 1,
                    "normal E2 completion must hand off exactly once");

            var stale = new SkillReference(new SkillId(playerId(30)), new SkillRevision(0));
            var mutation = fixture.attachments().setEquipped(actor, 0, Optional.of(stale));
            helper.assertTrue(mutation instanceof PlayerSkillAttachmentService.Available<?>,
                    "test-only stale equipped fixture must publish normally");
            var changed = postObservedLogin(fixture, actor, 2);
            helper.assertTrue(changed.reconciliationVariant()
                            == P4E2QualificationFacade.ReconciliationVariant.CHANGED
                            && changed.setDataSuccesses() == 1,
                    "E2 must prune the missing exact equipped reference before handoff");
            helper.assertTrue(calls.get() == 2 && changed.continuationCalls() == 1,
                    "Changed E2 completion must hand off exactly once");

            var malformed = new CompoundTag();
            malformed.putInt("schema_version", -1);
            loadAttachment(actor, "gramarye:player_skills", malformed);
            var deferred = postObservedLogin(fixture, actor, 3);
            helper.assertTrue(deferred.reconciliationVariant()
                            == P4E2QualificationFacade.ReconciliationVariant.DEFERRED,
                    "quarantined skills must end in a nonnormal E2 terminal");
            helper.assertTrue(calls.get() == 2 && deferred.continuationCalls() == 1,
                    "nonnormal E2 completion must call the login port zero times");
            helper.assertTrue(server.getTickCount() == tick,
                    "recovery, E2 and handoff must finish in one synchronous server turn");
            player.closeAndInspectSaved(reloaded -> {
                helper.assertTrue(malformed.equals(attachmentPayload(
                                reloaded, "gramarye:player_skills")),
                        "actual playerdata reload must preserve the exact malformed raw payload");
                var result = fixture.attachments().draftCount(reloaded);
                helper.assertTrue(
                        result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable
                                && unavailable.reason()
                                        == PlayerSkillAttachmentService.UnavailableReason
                                                .PRESERVED_RAW_QUARANTINE,
                        "actual playerdata reload must retain the rejected quarantine state");
            });
        }
        assertPlayerdataReleased(helper, server, playerId(3));
        server.getPlayerList().saveAll();
        assertPlayerdataReleased(helper, server, playerId(3));
        helper.runAfterDelay(1, () -> {
            assertPlayerdataReleased(helper, server, playerId(3));
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2LoginPortRuntimeFailurePropagatesTheSameObject(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var primary = new IllegalStateException("P7_S4_EXPECTED_LOGIN_RUNTIME");
        var calls = new AtomicInteger();
        var observed = false;
        try {
            try (var player = placePlayer(server, 4, "p7s4-runtime");
                    var fixture = loginFixture(server, (exactServer, actor) -> {
                        calls.incrementAndGet();
                        throw primary;
                    })) {
                fixture.bus().post(new PlayerEvent.PlayerLoggedInEvent(player.actor()));
            }
        } catch (RuntimeException exact) {
            helper.assertTrue(exact == primary,
                    "E2 and recovery must propagate the identical port RuntimeException");
            observed = true;
        }
        helper.assertTrue(observed && calls.get() == 1,
                "a port fault must neither disappear nor invoke the port twice");
        helper.assertTrue(primary.getSuppressed().length == 0,
                "RuntimeException cleanup must complete without a suppressed terminal fault");
        assertPlayerdataReleased(helper, server, playerId(4));

        var logoutFailure = new IllegalStateException("P7_S4_EXPECTED_LOGOUT_RUNTIME");
        var listener = new OneShotLogoutFailure(playerId(4), logoutFailure);
        NeoForge.EVENT_BUS.register(listener);
        var logoutObserved = false;
        try {
            try (var ignored = placePlayer(server, 4, "p7s4-logout-failure")) {
                // The close path below exercises recovery after a real logout listener fault.
            }
        } catch (RuntimeException exact) {
            helper.assertTrue(exact == logoutFailure,
                    "terminal cleanup must propagate the identical logout listener failure");
            logoutObserved = true;
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.assertTrue(logoutObserved && logoutFailure.getSuppressed().length == 0,
                "one-shot logout failure must recover detachment and storage cleanup");
        assertPlayerdataReleased(helper, server, playerId(4));
        server.getPlayerList().saveAll();
        assertPlayerdataReleased(helper, server, playerId(4));
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2LoginPortErrorPropagatesTheSameObject(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var primary = new AssertionError("P7_S4_EXPECTED_LOGIN_ERROR");
        var calls = new AtomicInteger();
        var observed = false;
        try {
            try (var player = placePlayer(server, 5, "p7s4-error");
                    var fixture = loginFixture(server, (exactServer, actor) -> {
                        calls.incrementAndGet();
                        throw primary;
                    })) {
                fixture.bus().post(new PlayerEvent.PlayerLoggedInEvent(player.actor()));
            }
        } catch (Error exact) {
            helper.assertTrue(exact == primary,
                    "E2 and recovery must propagate the identical port Error");
            observed = true;
        }
        helper.assertTrue(observed && calls.get() == 1,
                "an Error must neither disappear nor invoke the port twice");
        helper.assertTrue(primary.getSuppressed().length == 0,
                "Error cleanup must complete without a suppressed terminal fault");
        assertPlayerdataReleased(helper, server, playerId(5));
        server.getPlayerList().saveAll();
        assertPlayerdataReleased(helper, server, playerId(5));
        helper.succeed();
    }

    private static P4E2QualificationFacade.Snapshot postObservedLogin(
            LoginFixture fixture, ServerPlayer actor, long caseId) {
        var id = actor.getUUID();
        var observation = fixture.facade().arm(actor.getServer(),
                id.getMostSignificantBits(), id.getLeastSignificantBits(), caseId,
                P4E2QualificationFacade.Phase.READY_FIRST);
        fixture.bus().post(new PlayerEvent.PlayerLoggedInEvent(actor));
        return fixture.facade().consume(observation);
    }

    private static LoginFixture loginFixture(
            MinecraftServer server, P7ServerAuthorizationBoundary.LoginReadyPort port) {
        var storage = server.overworld().getDataStorage();
        var original = Objects.requireNonNull(storage.get(CACHE_ONLY, SAVED_DATA_NAME));
        var bus = BusBuilder.builder().build();
        var attachments = PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests();
        var facade = new P4E2QualificationFacade();
        var store = SkillDefinitionStoreService.registerOn(
                bus, attachments, port, facade.storeView(), facade.playerView());
        var recovery = SkillSubmissionRecoveryService.create(attachments,
                store.submissionPort(), store.onlineReconciliationDependency(),
                facade.submissionView());
        recovery.registerOn(bus);
        bus.start();
        var fixture = new LoginFixture(server, bus, original, attachments, facade);
        try {
            bus.post(new ServerStartingEvent(server));
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

    private static void loadAttachment(ServerPlayer actor, String key, Tag payload) {
        var root = actor.saveWithoutId(new CompoundTag());
        var attachments = root.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        attachments.put(key, payload.copy());
        root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        actor.load(root);
    }

    private static Tag attachmentPayload(ServerPlayer actor, String key) {
        return Objects.requireNonNull(actor.saveWithoutId(new CompoundTag())
                .getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(key));
    }

    private static UUID playerId(int suffix) {
        return new UUID(0x7400000000004000L, 0x8000000000000000L + suffix);
    }

    private static ServerPlayer unplacedPlayer(MinecraftServer server, int suffix, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId(suffix), name), false);
        return new ServerPlayer(server, server.overworld(),
                cookie.gameProfile(), cookie.clientInformation());
    }

    private static ConnectedPlayer placePlayer(MinecraftServer server, int suffix, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId(suffix), name), false);
        var actor = unplacedPlayer(server, suffix, name);
        var playerdata = PlayerdataClaim.claim(server, actor.getUUID());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        var fixture = new ConnectedPlayer(server, actor, channel, playerdata);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, actor, cookie);
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

    private static void assertPlayerdataReleased(
            GameTestHelper helper, MinecraftServer server, UUID playerId) {
        helper.assertTrue(server.getPlayerList().getPlayer(playerId) == null
                        && server.getPlayerList().getPlayers().stream()
                                .noneMatch(player -> player.getUUID().equals(playerId)),
                "test-owned player must be absent from every live PlayerList view");
        var artifacts = PlayerdataClaim.routeArtifacts(server, playerId);
        helper.assertTrue(artifacts.isEmpty(),
                "test-owned playerdata must be absent after terminal cleanup: " + artifacts);
    }

    private static final class ConnectedPlayer implements AutoCloseable {
        private final MinecraftServer server;
        private final ServerPlayer actor;
        private final EmbeddedChannel channel;
        private final PlayerdataClaim playerdata;
        private boolean closed;

        private ConnectedPlayer(
                MinecraftServer server,
                ServerPlayer actor,
                EmbeddedChannel channel,
                PlayerdataClaim playerdata) {
            this.server = Objects.requireNonNull(server, "server");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.channel = Objects.requireNonNull(channel, "channel");
            this.playerdata = Objects.requireNonNull(playerdata, "playerdata");
        }

        private ServerPlayer actor() {
            return actor;
        }

        private void closeAndInspectSaved(Consumer<ServerPlayer> inspection) {
            Objects.requireNonNull(inspection, "inspection");
            requireOpen();
            Throwable primary = null;
            try {
                removeExactCurrent();
                playerdata.requireOnlySavedPrimary();
                var reloaded = new ServerPlayer(
                        server,
                        server.overworld(),
                        actor.getGameProfile(),
                        actor.clientInformation());
                if (server.getPlayerList().load(reloaded).isEmpty()) {
                    throw new AssertionError("actual saved playerdata did not reload");
                }
                inspection.accept(reloaded);
            } catch (RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                release(primary);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            Throwable primary = null;
            try {
                var current = server.getPlayerList().getPlayer(actor.getUUID());
                if (current == actor) {
                    removeExactCurrent();
                } else if (current != null) {
                    throw new AssertionError(
                            "test-owned UUID was replaced by a foreign live player");
                }
                requireDetached();
            } catch (RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                release(primary);
            }
        }

        private void removeExactCurrent() {
            if (server.getPlayerList().getPlayer(actor.getUUID()) != actor) {
                throw new AssertionError("test-owned player is not the exact current player");
            }
            Throwable primary = null;
            try {
                server.getPlayerList().remove(actor);
            } catch (RuntimeException | Error failure) {
                primary = failure;
            }
            if (isStillLive()) {
                try {
                    server.getPlayerList().remove(actor);
                } catch (RuntimeException | Error retryFailure) {
                    if (primary == null) {
                        primary = retryFailure;
                    } else if (retryFailure != primary) {
                        primary.addSuppressed(retryFailure);
                    }
                }
            }
            try {
                requireDetached();
            } catch (RuntimeException | Error detachFailure) {
                if (primary == null) {
                    primary = detachFailure;
                } else if (detachFailure != primary) {
                    primary.addSuppressed(detachFailure);
                }
            }
            if (primary != null) {
                rethrow(primary);
            }
        }

        private void requireDetached() {
            if (isStillLive()) {
                throw new AssertionError("test-owned player remained live after removal");
            }
        }

        private boolean isStillLive() {
            return server.getPlayerList().getPlayer(actor.getUUID()) != null
                    || server.getPlayerList().getPlayers().stream()
                            .anyMatch(player -> player == actor
                                    || player.getUUID().equals(actor.getUUID()));
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("test-owned player fixture is already closed");
            }
        }

        private void release(Throwable primary) {
            Throwable cleanupFailure = null;
            try {
                requireDetached();
            } catch (RuntimeException | Error failure) {
                cleanupFailure = failure;
            }
            if (cleanupFailure == null) {
                try {
                    playerdata.release();
                } catch (RuntimeException | Error failure) {
                    cleanupFailure = failure;
                }
            }
            try {
                channel.finishAndReleaseAll();
            } catch (RuntimeException | Error failure) {
                if (cleanupFailure == null) {
                    cleanupFailure = failure;
                } else if (cleanupFailure != failure) {
                    cleanupFailure.addSuppressed(failure);
                }
            } finally {
                closed = true;
            }
            if (cleanupFailure != null) {
                if (primary != null) {
                    if (cleanupFailure != primary) {
                        primary.addSuppressed(cleanupFailure);
                    }
                } else {
                    rethrow(cleanupFailure);
                }
            }
        }
    }

    private static final class PlayerdataClaim {
        private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};

        private final Path directory;
        private final UUID playerId;
        private final String primaryName;
        private final String oldName;
        private final String temporaryPrefix;
        private final String corruptedPrefix;

        private PlayerdataClaim(Path directory, UUID playerId) {
            this.directory = Objects.requireNonNull(directory, "directory")
                    .toAbsolutePath().normalize();
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.primaryName = playerId + ".dat";
            this.oldName = playerId + ".dat_old";
            this.temporaryPrefix = playerId + "-";
            this.corruptedPrefix = playerId + "_corrupted_";
        }

        private static PlayerdataClaim claim(MinecraftServer server, UUID playerId) {
            var claim = forRoute(server, playerId);
            claim.requireNoLivePlayer(server);
            claim.requireDirectory();
            claim.requireNoArtifacts("before fixture placement");
            return claim;
        }

        private static List<Path> routeArtifacts(MinecraftServer server, UUID playerId) {
            var claim = forRoute(server, playerId);
            claim.requireDirectory();
            return claim.artifacts();
        }

        private static PlayerdataClaim forRoute(MinecraftServer server, UUID playerId) {
            Objects.requireNonNull(server, "server");
            return new PlayerdataClaim(
                    server.getWorldPath(LevelResource.PLAYER_DATA_DIR), playerId);
        }

        private void requireOnlySavedPrimary() {
            var artifacts = artifacts();
            var primary = directory.resolve(primaryName);
            if (artifacts.size() != 1 || !artifacts.getFirst().equals(primary)) {
                throw new AssertionError(
                        "actual player save did not publish one exact primary: " + artifacts);
            }
            requireRegular(primary, "saved primary");
        }

        private void release() {
            Throwable cleanupFailure = null;
            for (var artifact : artifacts()) {
                try {
                    requireRegular(artifact, "test-owned cleanup artifact");
                    Files.delete(artifact);
                } catch (IOException failure) {
                    var wrapped = new AssertionError(
                            "failed to delete exact test-owned playerdata artifact " + artifact,
                            failure);
                    if (cleanupFailure == null) {
                        cleanupFailure = wrapped;
                    } else {
                        cleanupFailure.addSuppressed(wrapped);
                    }
                } catch (RuntimeException | Error failure) {
                    if (cleanupFailure == null) {
                        cleanupFailure = failure;
                    } else if (cleanupFailure != failure) {
                        cleanupFailure.addSuppressed(failure);
                    }
                }
            }
            if (cleanupFailure != null) {
                rethrow(cleanupFailure);
            }
            requireNoArtifacts("after terminal cleanup");
        }

        private void requireNoLivePlayer(MinecraftServer server) {
            if (server.getPlayerList().getPlayer(playerId) != null
                    || server.getPlayerList().getPlayers().stream()
                            .anyMatch(player -> player.getUUID().equals(playerId))) {
                throw new IllegalStateException(
                        "refusing to replace an already-live playerdata fixture owner");
            }
        }

        private void requireNoArtifacts(String stage) {
            var artifacts = artifacts();
            if (!artifacts.isEmpty()) {
                throw new AssertionError(
                        "refusing non-exclusive playerdata ownership " + stage + ": " + artifacts);
            }
        }

        private List<Path> artifacts() {
            requireDirectory();
            try (var paths = Files.list(directory)) {
                return paths.filter(path -> ownedName(path.getFileName().toString()))
                        .map(path -> path.toAbsolutePath().normalize())
                        .sorted()
                        .toList();
            } catch (IOException failure) {
                throw new AssertionError(
                        "failed to enumerate exact test-owned playerdata route " + playerId,
                        failure);
            }
        }

        private boolean ownedName(String name) {
            if (name.equals(primaryName) || name.equals(oldName)) {
                return true;
            }
            if (name.startsWith(temporaryPrefix) && name.endsWith(".dat")) {
                var token = name.substring(
                        temporaryPrefix.length(), name.length() - ".dat".length());
                return !token.isEmpty() && token.chars().allMatch(Character::isDigit);
            }
            if (name.startsWith(corruptedPrefix) && name.endsWith(".dat")) {
                var token = name.substring(
                        corruptedPrefix.length(), name.length() - ".dat".length());
                return timestampToken(token);
            }
            return false;
        }

        private static boolean timestampToken(String token) {
            if (token.length() != 19) {
                return false;
            }
            for (var index = 0; index < token.length(); index++) {
                var expectedSeparator = switch (index) {
                    case 4, 7, 13, 16 -> '-';
                    case 10 -> '_';
                    default -> '\0';
                };
                if (expectedSeparator == '\0') {
                    if (!Character.isDigit(token.charAt(index))) {
                        return false;
                    }
                } else if (token.charAt(index) != expectedSeparator) {
                    return false;
                }
            }
            return true;
        }

        private void requireDirectory() {
            var attributes = attributes(directory, "playerdata directory");
            if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                throw new AssertionError(
                        "test-owned playerdata root must be a nonsymlink directory: " + directory);
            }
        }

        private static void requireRegular(Path path, String role) {
            var attributes = attributes(path, role);
            if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                throw new AssertionError(role + " must be a nonsymlink regular file: " + path);
            }
        }

        private static BasicFileAttributes attributes(Path path, String role) {
            try {
                return Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
            } catch (IOException failure) {
                throw new AssertionError("failed to inspect " + role + ": " + path, failure);
            }
        }
    }

    private static final class OneShotLogoutFailure {
        private final UUID playerId;
        private final RuntimeException failure;
        private boolean fired;

        private OneShotLogoutFailure(UUID playerId, RuntimeException failure) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!fired && event.getEntity().getUUID().equals(playerId)) {
                fired = true;
                throw failure;
            }
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("unexpected checked fixture failure", failure);
    }

    private record LoginFixture(
            MinecraftServer server, IEventBus bus, SavedData original,
            PlayerSkillAttachmentService attachments, P4E2QualificationFacade facade)
            implements AutoCloseable {
        @Override
        public void close() {
            try {
                bus.post(new ServerStoppedEvent(server));
            } finally {
                server.overworld().getDataStorage().set(SAVED_DATA_NAME, original);
            }
        }
    }
}
