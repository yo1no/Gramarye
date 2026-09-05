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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Direct server-bound S4 qualification; no fixture escapes one synchronous test call. */
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
        try (var player = placePlayer(server, 3, "p7s4-e2");
                var fixture = loginFixture(server, (exactServer, actor) -> {
                    helper.assertTrue(exactServer == server
                                    && actor == server.getPlayerList().getPlayer(actor.getUUID())
                                    && server.isSameThread(),
                            "E2 must call the port on the exact server/current player/thread");
                    calls.incrementAndGet();
                })) {
            var actor = player.actor();
            var tick = server.getTickCount();
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
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2LoginPortRuntimeFailurePropagatesTheSameObject(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var primary = new IllegalStateException("P7_S4_EXPECTED_LOGIN_RUNTIME");
        var calls = new AtomicInteger();
        try (var player = placePlayer(server, 4, "p7s4-runtime");
                var fixture = loginFixture(server, (exactServer, actor) -> {
                    calls.incrementAndGet();
                    throw primary;
                })) {
            var observed = false;
            try {
                fixture.bus().post(new PlayerEvent.PlayerLoggedInEvent(player.actor()));
            } catch (RuntimeException exact) {
                helper.assertTrue(exact == primary,
                        "E2 and recovery must propagate the identical port RuntimeException");
                observed = true;
            }
            helper.assertTrue(observed && calls.get() == 1,
                    "a port fault must neither disappear nor invoke the port twice");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2LoginPortErrorPropagatesTheSameObject(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var primary = new AssertionError("P7_S4_EXPECTED_LOGIN_ERROR");
        var calls = new AtomicInteger();
        try (var player = placePlayer(server, 5, "p7s4-error");
                var fixture = loginFixture(server, (exactServer, actor) -> {
                    calls.incrementAndGet();
                    throw primary;
                })) {
            var observed = false;
            try {
                fixture.bus().post(new PlayerEvent.PlayerLoggedInEvent(player.actor()));
            } catch (Error exact) {
                helper.assertTrue(exact == primary,
                        "E2 and recovery must propagate the identical port Error");
                observed = true;
            }
            helper.assertTrue(observed && calls.get() == 1,
                    "an Error must neither disappear nor invoke the port twice");
        }
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
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, actor, cookie);
            return new ConnectedPlayer(actor, channel);
        } catch (RuntimeException | Error failure) {
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private record ConnectedPlayer(ServerPlayer actor, EmbeddedChannel channel)
            implements AutoCloseable {
        @Override
        public void close() {
            var server = actor.getServer();
            try {
                if (server.getPlayerList().getPlayer(actor.getUUID()) == actor) {
                    server.getPlayerList().remove(actor);
                }
            } finally {
                channel.finishAndReleaseAll();
            }
        }
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
