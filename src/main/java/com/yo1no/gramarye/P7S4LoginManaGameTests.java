package com.yo1no.gramarye;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.config.JoinWorldTask;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Direct server-bound S4 qualification with bounded test-owned playerdata lifetimes. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class P7S4LoginManaGameTests {
    private static final String SAVED_DATA_NAME = "gramarye_skill_definitions";
    private static final AttributeKey<ConnectedPlayer> COOLDOWN_PLAYER =
            AttributeKey.valueOf("gramarye.p11.gametest.owned-player");
    private static final String COOLDOWN_STATE_OBSERVER = "gramarye-p11-gametest-state";
    private static final SkillId P9_RESERVED_SKILL_ID = new SkillId(
            UUID.fromString("74000000-0000-4000-8000-000000000901"));
    private static final SkillId P9_WITNESS_SKILL_ID = new SkillId(
            UUID.fromString("74000000-0000-4000-8000-000000000902"));
    private static final SavedData.Factory<SavedData> CACHE_ONLY = new SavedData.Factory<>(
            () -> { throw new AssertionError("P7 test expected the installed Store"); },
            (tag, provider) -> { throw new AssertionError("P7 test forbids cache-miss load"); });

    private P7S4LoginManaGameTests() {}

    static P9GameTestFixture openP9GameTestFixture(
            GameTestHelper helper,
            ServerPlayer actor,
            long fixtureId) {
        return openP9GameTestFixture(
                helper, actor, fixtureId, AppearanceDocument.Default.INSTANCE);
    }

    static P9GameTestFixture openP9GameTestFixture(
            GameTestHelper helper,
            ServerPlayer actor,
            long fixtureId,
            AppearanceDocument appearance) {
        return openP9GameTestFixture(helper, actor, fixtureId, appearance, 0, 4_000L);
    }

    static P9GameTestFixture openP9GameTestFixture(
            GameTestHelper helper,
            ServerPlayer actor,
            long fixtureId,
            AppearanceDocument appearance,
            int damageSchema,
            long magnitude) {
        Objects.requireNonNull(helper, "helper");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(appearance, "appearance");
        if (fixtureId <= 0) {
            throw new IllegalArgumentException("P9 GameTest fixture identity must be positive");
        }
        if (!((damageSchema == 0 && magnitude == 4_000L)
                || (damageSchema == 1 && (magnitude == 4_000L || magnitude == 5_000L)))) {
            throw new IllegalArgumentException("Fixture requires a supported exact damage input");
        }
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread()
                        && actor.getServer() == server
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor,
                "P9 GameTest fixture requires the exact current server actor");
        var author = unplacedPlayer(
                server,
                new UUID(
                        0x7910000000004000L,
                        0x8000000000000000L | fixtureId),
                "p9-s3-author");
        var fixture = cooldownLoginFixture(server, (exactServer, exactActor) -> {});
        try {
            var canonical = P9StarterSkillContent.canonicalDraft(new SkillId(new UUID(
                    0x7900000000004000L,
                    0x8000000000000000L | fixtureId)));
            var nodes = canonical.nodes();
            if (damageSchema == 1) {
                var damagePayload = P9DamageActionType.INSTANCE.payloadCodec().codec()
                        .encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                                new P9DamageActionPayloadV0(magnitude, 0L)).getOrThrow();
                var originalHit = nodes.get(1);
                var hit = new com.yo1no.gramarye.magic.definition.document.DraftNode(
                        originalHit.trigger(),
                        com.yo1no.gramarye.magic.definition.document.DraftActionSlot.present(
                                new com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope(
                                        P9StarterSkillContent.DAMAGE_ID, 1,
                                        new com.mojang.serialization.Dynamic<>(
                                                com.mojang.serialization.JsonOps.INSTANCE,
                                                damagePayload))),
                        originalHit.appearanceOverride());
                nodes = List.of(nodes.getFirst(), hit);
            }
            var draft = new SkillDraft(
                    canonical.draftSchemaVersion(),
                    canonical.skillId(),
                    canonical.baseRevision(),
                    nodes,
                    appearance);
            var reference = submitCanonical(
                    helper,
                    fixture,
                    author,
                    draft);
            return new P9GameTestFixture(server, actor, fixture, reference);
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

    static final class P9GameTestFixture implements AutoCloseable {
        private final MinecraftServer server;
        private final ServerPlayer actor;
        private final LoginFixture fixture;
        private final SkillReference reference;
        private boolean closed;

        private P9GameTestFixture(
                MinecraftServer server,
                ServerPlayer actor,
                LoginFixture fixture,
                SkillReference reference) {
            this.server = Objects.requireNonNull(server, "server");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.fixture = Objects.requireNonNull(fixture, "fixture");
            this.reference = Objects.requireNonNull(reference, "reference");
        }

        ServerPlayer actor() {
            return actor;
        }

        SkillReference reference() {
            return reference;
        }

        CastGeometryExecutionDataV0 geometry() {
            return expectedCastGeometry(actor);
        }

        SkillRuntimeService startRuntime(RuntimeExecutionPort port) {
            if (closed) {
                throw new IllegalStateException("P9 GameTest fixture is closed");
            }
            var runtime = directRuntime(fixture.store(), port);
            startDirectRuntime(runtime, server);
            return runtime;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                fixture.close();
            }
        }
    }

    static void withP9CallScopedRuntimeContext(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer actor,
            long fixtureId,
            BiConsumer<RuntimeEvent, RuntimeExecutionContext> assertion) {
        Objects.requireNonNull(helper, "helper");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assertion, "assertion");
        if (fixtureId <= 0) {
            throw new IllegalArgumentException("P8 call-scoped fixture identity must be positive");
        }
        helper.assertTrue(server.isSameThread(),
                "P8 call-scoped context must be injected on the actual server thread");

        var author = unplacedPlayer(
                server,
                new UUID(
                        0x7810000000004000L,
                        0x8000000000000000L | fixtureId),
                "p8-p9-author");
        var fixture = cooldownLoginFixture(server, (exactServer, exactActor) -> {});
        SkillRuntimeService runtime = null;
        Throwable primary = null;
        try {
            var reference = submitCanonical(
                    helper,
                    fixture,
                    author,
                    P9StarterSkillContent.canonicalDraft(new SkillId(new UUID(
                            0x7800000000004000L,
                            0x8000000000000000L | fixtureId))));
            int[] calls = {0};
            runtime = directRuntime(fixture.store(), (event, context) -> {
                calls[0]++;
                helper.assertTrue(calls[0] == 1,
                        "P8 fixture must receive exactly one real P5 execution callback");
                assertion.accept(event, context);
                return P6RuntimeExecutionPortAdapter.completedEmpty();
            });
            startDirectRuntime(runtime, server);
            var admission = runtime.admitAuthenticatedPlayerCast(
                    server, actor, reference, expectedCastGeometry(actor));
            helper.assertTrue(
                    admission instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "P8 fixture must admit one real server-derived P9 root");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(calls[0] == 1,
                    "P8 fixture must complete inside the sole real P5 port call");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanupFailure = null;
            if (runtime != null) {
                try {
                    runtime.handleRuntimeStopped(new ServerStoppedEvent(server));
                } catch (RuntimeException | Error failure) {
                    cleanupFailure = failure;
                }
            }
            try {
                fixture.close();
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
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

    @GameTest(batch = "p7_e2_native_terminals", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2NormalAndChangedTerminalsHandoffOnceAndQuarantineNeverHandoffs(
            GameTestHelper helper) {
        e2ReadyPhase(helper, new AtomicInteger(), false);
    }

    private static void e2ReadyPhase(GameTestHelper helper, AtomicInteger calls, boolean changedPhase) {
        var server = helper.getLevel().getServer();
        var player = placePlayer(server, 3, "p7s4-e2");
        runCooldownFixtureAfterTick(helper, () -> {
            try (player) {
            requireCooldownLoginComplete(player.actor());
            var tick = server.getTickCount();
            try (player; var fixture = cooldownLoginFixture(server, (exactServer, actor) -> {
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
            if (!changedPhase) {
                var unchanged = postObservedLogin(fixture, actor, 1);
                helper.assertTrue(unchanged.reconciliationVariant()
                            == P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES,
                    "fresh E2 fixture must end in NoChanges");
                helper.assertTrue(calls.get() == 1 && unchanged.continuationCalls() == 1,
                    "normal E2 completion must hand off exactly once");
            } else {
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
            }
            helper.assertTrue(server.getTickCount() == tick,
                    "recovery, E2 and handoff must finish in one synchronous server turn");
            }
            assertPlayerdataReleased(helper, server, playerId(3));
            server.getPlayerList().saveAll();
            assertPlayerdataReleased(helper, server, playerId(3));
            }
        }, () -> {
            if (changedPhase) { e2NativeQuarantine(helper, calls); }
            else { e2ReadyPhase(helper, calls, true); }
        }, failure -> closeFixtureAfterFailure(player, failure));
    }

    private static void e2NativeQuarantine(GameTestHelper helper, AtomicInteger calls) {
        var server = helper.getLevel().getServer();
        // A fresh source identity: test disk must never replace qualified retained MEMORY.
        var id = playerId(8);
        var claim = PlayerdataClaim.claim(server, id);
        var malformed = new CompoundTag();
        malformed.putInt("schema_version", -1);
        var holder = unplacedPlayer(server, id, "p7s4-q-input");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(holder);
        if (owner == null || owner.account(holder) != null) {
            throw new AssertionError("pre-login quarantine requires an unused exact source identity");
        }
        var root = PlayerSkillAttachmentGameTests.isolatedPlayerSkillsInput(holder, malformed);
        P4E2QualificationFacade facade = null;
        P4E2QualificationFacade.Session observation = null;
        Throwable primary = null;
        try {
        net.minecraft.nbt.NbtIo.writeCompressed(root, claim.directory.resolve(claim.primaryName));
        facade = nativeGameTestFacade(server);
        observation = facade.arm(server, id.getMostSignificantBits(), id.getLeastSignificantBits(),
                3, P4E2QualificationFacade.Phase.READY_FIRST);
        try (var player = placeCooldownPlayer(server,
                CommonListenerCookie.createInitial(new GameProfile(id, "p7s4-quarantine"), false), claim)) {
            var deferred = facade.consume(observation);
            observation = null;
            helper.assertTrue(deferred.reconciliationVariant()
                            == P4E2QualificationFacade.ReconciliationVariant.DEFERRED,
                    "quarantined skills must end in a nonnormal E2 terminal");
            helper.assertTrue(calls.get() == 2 && deferred.continuationCalls() == 1
                            && !com.yo1no.gramarye.magic.network.P7S4NetworkGameTests.hasNativeSession(id),
                    "nonnormal E2 completion must call the login port zero times");
            var attachments = nativeGameTestAttachments(server);
            player.closeAndInspectSaved(reloaded -> {
                helper.assertTrue(malformed.equals(attachmentPayload(reloaded, "gramarye:player_skills")),
                        "actual playerdata reload must preserve the exact malformed raw payload");
                var result = attachments.draftCount(reloaded);
                helper.assertTrue(result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable
                                && unavailable.reason() == PlayerSkillAttachmentService.UnavailableReason.PRESERVED_RAW_QUARANTINE,
                        "actual playerdata reload must retain the rejected quarantine state");
            });
        }
        } catch (IOException failure) {
            var wrapped = new AssertionError("owned pre-login input write failed", failure);
            primary = wrapped;
            throw wrapped;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (observation != null) {
                try { facade.discard(observation); }
                catch (RuntimeException | Error cleanup) {
                    if (primary == null) { throw cleanup; }
                    if (cleanup != primary) { primary.addSuppressed(cleanup); }
                }
            }
            try { claim.release(); }
            catch (RuntimeException | Error cleanup) {
                if (primary == null) { throw cleanup; }
                if (cleanup != primary) { primary.addSuppressed(cleanup); }
            }
        }
        assertPlayerdataReleased(helper, server, id);
        helper.runAfterDelay(1, () -> { assertPlayerdataReleased(helper, server, id); helper.succeed(); });
    }

    private static void closeFixtureAfterFailure(ConnectedPlayer player, Throwable primary) {
        try { player.close(); }
        catch (RuntimeException | Error cleanup) { if (cleanup != primary) { primary.addSuppressed(cleanup); } }
    }

    @GameTest(batch = "p7_e2_native_runtime", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2LoginPortRuntimeFailurePropagatesTheSameObject(GameTestHelper helper) {
        var player = placePlayer(helper.getLevel().getServer(), 4, "p7s4-runtime");
        runCooldownFixtureAfterTick(helper, () -> {
            try (player) {
            requireCooldownLoginComplete(player.actor());
            e2RuntimeAfterInitialDelivery(helper, player);
            }
        }, helper::succeed, failure -> closeFixtureAfterFailure(player, failure));
    }

    private static void e2RuntimeAfterInitialDelivery(GameTestHelper helper, ConnectedPlayer readyPlayer) {
        var server = helper.getLevel().getServer();
        var primary = new IllegalStateException("P7_S4_EXPECTED_LOGIN_RUNTIME");
        var calls = new AtomicInteger();
        var observed = false;
        try {
            try (var player = readyPlayer;
                    var fixture = cooldownLoginFixture(server, (exactServer, actor) -> {
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
        var fixtureLogoutBus = BusBuilder.builder().build();
        var logoutObserved = false;
        try {
            try (var player = placePlayer(server, 4, "p7s4-logout-fail")) {
                // Isolated fixture event propagation only. A real managed native logout
                // failure is UNKNOWN and must never be replayed to force detachment.
                fixtureLogoutBus.register(listener);
                try { fixtureLogoutBus.post(new PlayerEvent.PlayerLoggedOutEvent(player.actor())); }
                finally { fixtureLogoutBus.unregister(listener); }
            }
        } catch (RuntimeException exact) {
            helper.assertTrue(exact == logoutFailure,
                    "isolated event propagation must preserve the identical logout listener failure");
            logoutObserved = true;
        }
        helper.assertTrue(logoutObserved && listener.fired && logoutFailure.getSuppressed().length == 0,
                "one-shot fixture event fault must retain primary through one ordinary native close");
        assertPlayerdataReleased(helper, server, playerId(4));
        server.getPlayerList().saveAll();
        assertPlayerdataReleased(helper, server, playerId(4));
    }

    @GameTest(batch = "p7_e2_native_error", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void e2LoginPortErrorPropagatesTheSameObject(GameTestHelper helper) {
        var player = placePlayer(helper.getLevel().getServer(), 5, "p7s4-error");
        runCooldownFixtureAfterTick(helper, () -> {
            try (player) {
            requireCooldownLoginComplete(player.actor());
            e2ErrorAfterInitialDelivery(helper, player);
            }
        }, helper::succeed, failure -> closeFixtureAfterFailure(player, failure));
    }

    private static void e2ErrorAfterInitialDelivery(GameTestHelper helper, ConnectedPlayer readyPlayer) {
        var server = helper.getLevel().getServer();
        var primary = new AssertionError("P7_S4_EXPECTED_LOGIN_ERROR");
        var calls = new AtomicInteger();
        var observed = false;
        try {
            try (var player = readyPlayer;
                    var fixture = cooldownLoginFixture(server, (exactServer, actor) -> {
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
    }

    @GameTest(
            batch = "p9_s2_reserved",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void actualP9ReservedContinuationSurvivesRootAndClosesLateWithoutWorldEffects(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread(),
                "P9 RESERVED direct qualification requires the actual server thread");
        var player = placePlayer(server, 6, "p9-s2-reserved");
        final LoginFixture fixture;
        try {
            fixture = cooldownLoginFixture(server, (exactServer, actor) -> {});
        } catch (RuntimeException | Error failure) {
            player.close();
            throw failure;
        }

        SkillRuntimeService preparedRuntime = null;
        try {
            var actor = player.actor();
            var policy = SkillSubmissionPolicyProvider.defaults();
            var revisionZero = submitCanonical(
                    helper,
                    fixture,
                    actor,
                    P9StarterSkillContent.canonicalDraft(P9_RESERVED_SKILL_ID));
            assertApplied(
                    helper,
                    fixture.attachments().setEquipped(actor, 0, Optional.of(revisionZero)),
                    "revision zero must be the exact authenticated cast reference");

            var expectedGeometry = expectedCastGeometry(actor);
            var port = new ReservedContinuationPort(
                    helper, server, actor, revisionZero, expectedGeometry);
            preparedRuntime = new SkillRuntimeService(
                    fixture.store(),
                    policy,
                    new P5RuntimeProjector(ProfileAvailabilityView.unknown()),
                    new P5LoadedReferenceResolver(),
                    port);
            preparedRuntime.handleRuntimeStarted(
                    new ServerStartedEvent(server), directQualificationLimits());
            var ingress = new P7AuthenticatedPlayerCastIngress(
                    preparedRuntime, fixture.attachments(), fixture.store());
            var admission = ingress.authorizeAndAdmit(
                    server,
                    actor,
                    0,
                    (exactServer, exactActor) -> {
                        helper.assertTrue(
                                exactServer == server
                                        && exactActor == actor
                                        && server.isSameThread(),
                                "P9 target validation must use the exact current server owner");
                        return P7ServerAuthorizationBoundary.TargetDisposition.VALID;
                    });
            helper.assertTrue(
                    admission == P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                    "the real P7-to-P5 route must admit the server-derived geometry root");

            var revisionOneDraft = P9StarterSkillContent.canonicalDraft(P9_RESERVED_SKILL_ID);
            revisionOneDraft = new SkillDraft(
                    revisionOneDraft.draftSchemaVersion(),
                    revisionOneDraft.skillId(),
                    Optional.of(revisionZero.revision()),
                    revisionOneDraft.nodes(),
                    revisionOneDraft.appearance());
            var revisionOne = submitCanonical(
                    helper, fixture, actor, revisionOneDraft);
            helper.assertTrue(revisionOne.revision().value() == 1,
                    "the controlled Store must publish the exact successor revision");
            assertApplied(
                    helper,
                    fixture.attachments().setEquipped(actor, 0, Optional.of(revisionOne)),
                    "latest/equipped may advance only after P5 pinned revision zero");
            var latest = fixture.store().latestReference(server, P9_RESERVED_SKILL_ID);
            helper.assertTrue(
                    latest instanceof SkillSubsystemResult.Available<?> available
                            && available.value().equals(Optional.of(revisionOne)),
                    "the Store latest pointer must differ from the already-admitted exact root");
            helper.assertTrue(
                    P7ManaSnapshotBridge.observeBalance(
                                    P6RuntimeExecutionCapability.forRuntimeAdapter(), actor)
                            == 0L,
                    "the retained interval must begin from the installed available-zero mana truth");
            var retainedAttachments = actor.saveWithoutId(new CompoundTag())
                    .getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                    .copy();

            preparedRuntime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1 && port.opened() != null,
                    "actual P5 drain must invoke the injected port once and detach one permit");
            helper.assertTrue(
                    port.observedReference().equals(revisionZero),
                    "drain must use the pinned exact revision, never the newer latest pointer");
            helper.assertTrue(noLoadedEntity(server, port.opened().plannedProjectileId()),
                    "S2 planned identity must not manufacture a loaded projectile");

            exerciseS2ReservedFailurePaths(
                    helper, server, actor, fixture.store(), revisionOne, expectedGeometry);
            assertAttachmentsUnchanged(
                    helper, actor, retainedAttachments, "reserved failure-path fixtures");
            exerciseS2ActiveCaps(
                    helper, server, player, fixture.store(), revisionOne);
            assertAttachmentsUnchanged(
                    helper, actor, retainedAttachments, "active-capacity fixtures");

            var runtime = preparedRuntime;
            helper.runAfterDelay(1, () -> {
                Throwable primary = null;
                try {
                    runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
                    helper.assertTrue(port.calls() == 1,
                            "the retained RESERVED responsibility must publish no queued child");
                    helper.assertTrue(noLoadedEntity(server, port.opened().plannedProjectileId()),
                            "cross-tick RESERVED retention must remain free of world mutation");
                    assertAttachmentsUnchanged(
                            helper, actor, retainedAttachments, "cross-tick RESERVED retention");
                    helper.assertTrue(
                            runtime.cancel(server, port.rootEventToken())
                                    instanceof RuntimeCancellationResult.NotPending,
                            "the ordinary root event must be absent while its detached permit lives");
                    for (var elapsed = 0; elapsed < 99; elapsed++) {
                        runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
                    }
                    helper.assertTrue(
                            port.calls() == 1
                                    && noLoadedEntity(
                                            server, port.opened().plannedProjectileId()),
                            "bounded post-deadline retention must publish no retry or entity");
                    assertAttachmentsUnchanged(
                            helper,
                            actor,
                            retainedAttachments,
                            "bounded post-deadline RESERVED retention");

                    helper.assertTrue(
                            runtime.rejectProjectileContinuationCloseForGameTest(
                                            server,
                                            port.opened().permit(),
                                            port.rootEventToken().serverSlotToken(),
                                            port.rootEventToken().skillInstanceId(),
                                            port.observedAttribution(),
                                            new UUID(
                                                    port.rootEventToken()
                                                            .serverSlotToken()
                                                            .value(),
                                                    port.heldChildEventId()),
                                            ProjectileClosureReason.DEADLINE_REACHED)
                                    == RuntimePermitCloseDisposition.REJECTED,
                            "the synthetic wrong-thread core route must reject without mutation");
                    helper.assertTrue(
                            port.opened().permit().closeWithoutHit(
                                            server, ProjectileClosureReason.DEADLINE_REACHED)
                                    == RuntimePermitCloseDisposition.CLOSED,
                            "post-deadline server-thread close must release the retained formal permit");
                    helper.assertTrue(
                            port.opened().permit().closeWithoutHit(
                                            server, ProjectileClosureReason.DEADLINE_REACHED)
                                    == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                            "duplicate close must be the exact idempotent terminal result");
                    helper.assertTrue(
                            runtime.cancel(server, port.cancellationToken())
                                    instanceof RuntimeCancellationResult.NotPending,
                            "late close must remove the empty lineage and release its exact lease");

                    var later = runtime.admitAuthenticatedPlayerCast(
                            server, actor, revisionOne, expectedGeometry);
                    helper.assertTrue(
                            later instanceof RuntimeAdmissionResult.AcceptedMemoryOnly accepted
                                    && accepted.eventToken().eventId().value()
                                            > port.heldChildEventId(),
                            "a released held EventId must never be allocated to a later root");
                    var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) later;
                    helper.assertTrue(
                            runtime.cancel(server, accepted.cancellationToken())
                                    instanceof RuntimeCancellationResult.CancelledSkillInstance,
                            "post-close root cleanup must remove the sole queued event normally");
                    runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
                    helper.assertTrue(port.calls() == 1,
                            "all retained and post-close queues must be empty after cleanup");
                } catch (RuntimeException | Error failure) {
                    primary = failure;
                } finally {
                    cleanupDirectRuntimeFixture(runtime, player, fixture, primary);
                }
                if (primary != null) {
                    rethrow(primary);
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            cleanupDirectRuntimeFixture(preparedRuntime, player, fixture, failure);
            throw failure;
        }
    }

    @GameTest(
            batch = "p9_s2_actor_witness",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 300)
    public static void actualP9ActorWitnessRejectsRespawnDimensionAndLogoutBeforeTransfer(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread(),
                "P9 actor-witness qualification requires the actual server thread");
        var player = placePlayer(server, 7, "p9-s2-witness-a");
        final LoginFixture fixture;
        try {
            fixture = cooldownLoginFixture(server, (exactServer, actor) -> {});
        } catch (RuntimeException | Error failure) {
            player.close();
            throw failure;
        }

        // Native LoggedIn queues P7 initial sync for Post; GameTestTicker also precedes
        // native PLAY resumeFlushing. Let both original boundaries return before waiting.
        try {
            runCooldownFixtureAfterTick(helper,
                    () -> exerciseActorWitnessAfterLogin(helper, server, player, fixture),
                    helper::succeed,
                    primary -> cleanupDirectRuntimeFixture(null, player, fixture, primary));
        } catch (RuntimeException | Error primary) {
            cleanupDirectRuntimeFixture(null, player, fixture, primary);
            throw primary;
        }
    }

    private static void exerciseActorWitnessAfterLogin(GameTestHelper helper,
            MinecraftServer server, ConnectedPlayer player, LoginFixture fixture) {
        Throwable primary = null;
        try {
            var actor = player.current();
            requireCooldownLoginComplete(actor);
            var reference = submitCanonical(
                    helper,
                    fixture,
                    actor,
                    P9StarterSkillContent.canonicalDraft(P9_WITNESS_SKILL_ID));
            assertApplied(
                    helper,
                    fixture.attachments().setEquipped(actor, 0, Optional.of(reference)),
                    "the actor-witness fixture must equip its exact owned revision");

            exerciseReplacementBeforeDrain(
                    helper, server, player, fixture);
            exerciseReplacementBeforeDetach(
                    helper, server, player, fixture.store(), reference);
            exerciseFrozenDimensionRejection(
                    helper, server, player, fixture.store(), reference);
            exerciseReservedReplacementAndFreshAdmission(
                    helper, server, player, fixture.store(), reference);
            exerciseLogoutBeforeDrain(
                    helper, server, player, fixture.store(), reference);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            cleanupDirectRuntimeFixture(null, player, fixture, primary);
        }
    }

    private static void exerciseReplacementBeforeDrain(
            GameTestHelper helper,
            MinecraftServer server,
            ConnectedPlayer player,
            LoginFixture fixture) {
        var port = new CountingExecutionPort();
        var runtime = directRuntime(fixture.store(), port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            var actorA = player.current();
            var ingress = new P7AuthenticatedPlayerCastIngress(
                    runtime, fixture.attachments(), fixture.store());
            var admission = ingress.authorizeAndAdmit(
                    server,
                    actorA,
                    0,
                    (exactServer, exactActor) -> {
                        helper.assertTrue(
                                exactServer == server && exactActor == actorA,
                                "the witness must originate at the actual P7 actor boundary");
                        return P7ServerAuthorizationBoundary.TargetDisposition.VALID;
                    });
            helper.assertTrue(
                    admission == P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED,
                    "P7 must publish one root while exact actor A is current");

            var actorB = player.respawnCurrent();
            helper.assertTrue(
                    actorB != actorA
                            && actorB.getUUID().equals(actorA.getUUID())
                            && player.current() == actorB,
                    "actual respawn must install same-UUID actor B before P5 drain");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 0,
                    "same-UUID actor B must not execute actor A's queued P9 root");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 0,
                    "the terminal A root must not revive on a later drain while B is current");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseReplacementBeforeDetach(
            GameTestHelper helper,
            MinecraftServer server,
            ConnectedPlayer player,
            SkillDefinitionStoreService store,
            SkillReference reference) {
        var actorA = player.current();
        var geometry = expectedCastGeometry(actorA);
        var port = new ReplacementBeforeOpenPort(
                helper, server, player, actorA, reference, geometry);
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            var accepted = admitExactP9(
                    helper,
                    runtime,
                    server,
                    actorA,
                    reference,
                    geometry,
                    "pre-detach replacement fixture");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(
                    port.calls() == 1
                            && port.replacement() != actorA
                            && port.replacement().getUUID().equals(actorA.getUUID())
                            && port.openResult()
                                    instanceof RuntimeProjectileContinuationOpenResult.Rejected rejected
                            && rejected.reason()
                                    == RuntimeProjectileContinuationOpenRejectionReason
                                            .LIFECYCLE_UNAVAILABLE,
                    "replacement after resolution but before detach must reject with no permit");
            helper.assertTrue(
                    runtime.cancel(server, accepted.eventToken())
                            instanceof RuntimeCancellationResult.NotPending,
                    "pre-detach identity rejection must terminal the exact A event");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1,
                    "a rejected opener must publish no retry or queued continuation");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseFrozenDimensionRejection(
            GameTestHelper helper,
            MinecraftServer server,
            ConnectedPlayer player,
            SkillDefinitionStoreService store,
            SkillReference reference) {
        var actor = player.current();
        var geometry = expectedCastGeometry(actor);
        var port = new CountingExecutionPort();
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            var accepted = admitExactP9(
                    helper,
                    runtime,
                    server,
                    actor,
                    reference,
                    geometry,
                    "frozen-dimension fixture");
            var end = server.getLevel(Level.END);
            helper.assertTrue(end != null,
                    "P9 frozen-dimension qualification requires the End level");
            helper.assertTrue(player.changeCurrentDimension(end) == actor,
                    "ordinary dimension movement must preserve actor object identity");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(
                    port.calls() == 0
                            && runtime.cancel(server, accepted.eventToken())
                                    instanceof RuntimeCancellationResult.NotPending,
                    "same actor in the wrong frozen dimension must terminal before execution");
            helper.assertTrue(player.changeCurrentDimension(server.overworld()) == actor,
                    "the exact actor must return to the fixture's Overworld");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 0,
                    "returning to the frozen dimension must not revive terminal work");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseReservedReplacementAndFreshAdmission(
            GameTestHelper helper,
            MinecraftServer server,
            ConnectedPlayer player,
            SkillDefinitionStoreService store,
            SkillReference reference) {
        var actorA = player.current();
        var geometryA = expectedCastGeometry(actorA);
        var port = new ReservedContinuationPort(
                helper, server, actorA, reference, geometryA);
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            admitExactP9(
                    helper,
                    runtime,
                    server,
                    actorA,
                    reference,
                    geometryA,
                    "RESERVED actor-retention fixture");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1 && port.opened() != null,
                    "the exact A witness must survive dispatch through firm RESERVED detach");
            helper.assertTrue(noLoadedEntity(server, port.opened().plannedProjectileId()),
                    "S2 RESERVED custody must remain free of projectile world effects");

            var actorB = player.respawnCurrent();
            helper.assertTrue(
                    actorB != actorA && actorB.getUUID().equals(actorA.getUUID()),
                    "RESERVED fixture must replace A with same-UUID actor B");
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.OWNER_INVALIDATED)
                            == RuntimePermitCloseDisposition.CLOSED,
                    "invalid actor A must not be required to close its retained permit and witness");
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.OWNER_INVALIDATED)
                            == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                    "duplicate terminal cleanup must remain idempotent after actor replacement");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(
                    port.calls() == 1
                            && noLoadedEntity(server, port.opened().plannedProjectileId()),
                    "closed actor-A work must neither revive nor manufacture an entity for B");

            var fresh = admitExactP9(
                    helper,
                    runtime,
                    server,
                    actorB,
                    reference,
                    expectedCastGeometry(actorB),
                    "fresh actor-B fixture");
            helper.assertTrue(
                    fresh.eventToken().eventId().value() > port.heldChildEventId()
                            && runtime.cancel(server, fresh.cancellationToken())
                                    instanceof RuntimeCancellationResult.CancelledSkillInstance,
                    "B may own only a fresh admission with a new EventId and independent cleanup");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1,
                    "fresh B cancellation must not reactivate the closed A execution");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseLogoutBeforeDrain(
            GameTestHelper helper,
            MinecraftServer server,
            ConnectedPlayer player,
            SkillDefinitionStoreService store,
            SkillReference reference) {
        var actor = player.current();
        var port = new CountingExecutionPort();
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            var accepted = admitExactP9(
                    helper,
                    runtime,
                    server,
                    actor,
                    reference,
                    expectedCastGeometry(actor),
                    "logout fixture");
            player.logoutCurrent();
            helper.assertTrue(server.getPlayerList().getPlayer(actor.getUUID()) == null,
                    "actual logout must remove the admitted actor from the current-player map");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(
                    port.calls() == 0
                            && runtime.cancel(server, accepted.eventToken())
                                    instanceof RuntimeCancellationResult.NotPending,
                    "logged-out actor work must terminal before execution or permit creation");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 0,
                    "logout-terminal work must remain nonrevivable");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static RuntimeAdmissionResult.AcceptedMemoryOnly admitExactP9(
            GameTestHelper helper,
            SkillRuntimeService runtime,
            MinecraftServer server,
            ServerPlayer actor,
            SkillReference reference,
            CastGeometryExecutionDataV0 geometry,
            String role) {
        var admission = runtime.admitAuthenticatedPlayerCast(
                server, actor, reference, geometry);
        helper.assertTrue(
                admission instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                role + " must admit one exact authenticated P9 root");
        return (RuntimeAdmissionResult.AcceptedMemoryOnly) admission;
    }

    private static SkillReference submitCanonical(
            GameTestHelper helper,
            LoginFixture fixture,
            ServerPlayer actor,
            SkillDraft draft) {
        assertApplied(
                helper,
                fixture.attachments().putDraft(actor, draft),
                "canonical P9 Draft must publish through the registered Attachment owner");
        var outcome = SkillDefinitionSubmissionService.production(
                        fixture.attachments(),
                        fixture.store().submissionPort(),
                        SkillSubmissionPolicyProvider.defaults(),
                        ProfileAvailabilityView.unknown())
                .submit(actor, draft.skillId());
        helper.assertTrue(
                outcome instanceof SkillSubmissionCompositionOutcome.Committed committed
                        && committed.reference().skillId().equals(draft.skillId())
                        && !committed.report().hasErrors(),
                "canonical P9 Draft must traverse the real submission and Store commit path");
        return ((SkillSubmissionCompositionOutcome.Committed) outcome).reference();
    }

    private static void exerciseS2ReservedFailurePaths(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer actor,
            SkillDefinitionStoreService store,
            SkillReference reference,
            CastGeometryExecutionDataV0 geometry) {
        exerciseQueuedCancellation(helper, server, actor, store, reference, geometry);
        exerciseReloadCleanup(helper, server, actor, store, reference, geometry);
        exerciseStoppedCleanup(helper, server, actor, store, reference, geometry);
        exerciseRuntimeExceptionCleanup(helper, server, actor, store, reference, geometry);
        exerciseSameObjectErrorAndStoppedCleanup(
                helper, server, actor, store, reference, geometry);
    }

    private static void exerciseQueuedCancellation(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer actor,
            SkillDefinitionStoreService store,
            SkillReference reference,
            CastGeometryExecutionDataV0 geometry) {
        var port = LifecyclePort.completed(helper, server, actor, reference, geometry);
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            var admission = runtime.admitAuthenticatedPlayerCast(
                    server, actor, reference, geometry);
            helper.assertTrue(admission instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "queued-cancellation fixture must admit one real P9 root");
            var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) admission;
            helper.assertTrue(
                    runtime.cancel(server, accepted.cancellationToken())
                            instanceof RuntimeCancellationResult.CancelledSkillInstance cancelled
                            && cancelled.removedCount() == 1,
                    "pre-dispatch cancellation must remove the exact one-event lineage");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 0,
                    "a cancelled P9 root must never reach the opener or execution port");

            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "retained-cancellation fixture must admit a fresh real P9 root");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1 && port.opened() != null,
                    "retained cancellation must begin from one actual RESERVED permit");
            helper.assertTrue(
                    runtime.cancel(server, port.cancellationToken())
                            instanceof RuntimeCancellationResult.CancelledSkillInstance cancelled
                            && cancelled.removedCount() == 1,
                    "lineage cancellation must report the one detached continuation work unit");
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.OWNER_INVALIDATED)
                            == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                    "lineage cancellation must deindex and close its RESERVED permit once");
            helper.assertTrue(
                    runtime.cancel(server, port.cancellationToken())
                            instanceof RuntimeCancellationResult.NotPending,
                    "cancelled detached-only lineage must release its exact owner and lease");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseReloadCleanup(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer actor,
            SkillDefinitionStoreService store,
            SkillReference reference,
            CastGeometryExecutionDataV0 geometry) {
        var port = LifecyclePort.completed(helper, server, actor, reference, geometry);
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "reload fixture must admit one real P9 root");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1 && port.opened() != null,
                    "reload fixture must first retain one actual RESERVED permit");
            runtime.requestP9ReloadInvalidation();
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.ServerStopping,
                    "reload close-request bit must reject later P9 admission before mutation");
            runtime.completeP9Reload(server);
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.RELOAD_INVALIDATED)
                            == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                    "reload completion must remove and close the retained indexed permit once");
            helper.assertTrue(
                    runtime.cancel(server, port.cancellationToken())
                            instanceof RuntimeCancellationResult.NotPending,
                    "reload cleanup must remove the empty owner lineage and lease");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1,
                    "reload cleanup must leave no child or retry in the ordinary queue");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseStoppedCleanup(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer actor,
            SkillDefinitionStoreService store,
            SkillReference reference,
            CastGeometryExecutionDataV0 geometry) {
        var port = LifecyclePort.completed(helper, server, actor, reference, geometry);
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "stop fixture must admit one real P9 root");
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(port.calls() == 1 && port.opened() != null,
                    "stop fixture must first retain one actual RESERVED permit");
            helper.assertTrue(runtime.enterStoppingForTesting(server) == 1,
                    "Stopping cleanup must report its one successful permit transition");
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.SERVER_STOPPED)
                            == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                    "Stopping cleanup must close and deindex the retained permit exactly once");
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.ServerStopping,
                    "Stopping cleanup must retain the non-accepting runtime slot");
            runtime.handleRuntimeStopped(new ServerStoppedEvent(server));
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.SERVER_STOPPED)
                            == RuntimePermitCloseDisposition.REJECTED,
                    "Stopped cleanup must reject a late close after removing the runtime slot");
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.ServerNotRunning,
                    "Stopped cleanup must remove the complete runtime server slot");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseRuntimeExceptionCleanup(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer actor,
            SkillDefinitionStoreService store,
            SkillReference reference,
            CastGeometryExecutionDataV0 geometry) {
        var expected = new IllegalStateException("P9_S2_EXPECTED_PORT_RUNTIME");
        var port = LifecyclePort.throwingRuntime(
                helper, server, actor, reference, geometry, expected);
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "RuntimeException fixture must admit one real P9 root");
            RuntimeException observed = null;
            try {
                runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            } catch (RuntimeException exact) {
                observed = exact;
            }
            helper.assertTrue(observed == expected && expected.getSuppressed().length == 0,
                    "P5 must propagate the identical RuntimeException without cleanup masking");
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.RUNTIME_FAULT)
                            == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                    "RuntimeException cleanup must close and deindex the real permit once");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseSameObjectErrorAndStoppedCleanup(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer actor,
            SkillDefinitionStoreService store,
            SkillReference reference,
            CastGeometryExecutionDataV0 geometry) {
        var expected = new AssertionError("P9_S2_EXPECTED_PORT_ERROR");
        var port = LifecyclePort.throwingError(
                helper, server, actor, reference, geometry, expected);
        var runtime = directRuntime(store, port);
        Throwable primary = null;
        try {
            startDirectRuntime(runtime, server);
            var accepted = admitExactP9(
                    helper,
                    runtime,
                    server,
                    actor,
                    reference,
                    geometry,
                    "primary-Error diagnostic-isolation fixture");
            runtime.armP9TerminalDiagnosticFailureForTesting(
                    server, accepted.cancellationToken().skillInstanceId(), false);
            Error observed = null;
            try {
                runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            } catch (Error exact) {
                observed = exact;
            }
            helper.assertTrue(observed == expected && expected.getSuppressed().length == 0,
                    "a secondary diagnostic RuntimeException must not replace or suppress the primary Error");
            runtime.handleRuntimeStopped(new ServerStoppedEvent(server));
            helper.assertTrue(
                    port.opened().permit().closeWithoutHit(
                                    server, ProjectileClosureReason.RUNTIME_FAULT)
                            == RuntimePermitCloseDisposition.REJECTED
                            && runtime.admitAuthenticatedPlayerCast(
                                            server, actor, reference, geometry)
                                    instanceof RuntimeAdmissionResult.ServerNotRunning,
                    "Error best effort must clear witness/index and Stopped must finish the retained permit graph");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stopDirectRuntime(runtime, server, primary);
        }
    }

    private static void exerciseS2ActiveCaps(
            GameTestHelper helper,
            MinecraftServer server,
            ConnectedPlayer primaryPlayer,
            SkillDefinitionStoreService store,
            SkillReference reference) {
        var players = new ConnectedPlayer[8];
        players[0] = primaryPlayer;
        SkillRuntimeService runtime = null;
        Throwable primary = null;
        try {
            for (var index = 1; index < players.length; index++) {
                players[index] = placePlayer(
                        server, 20 + index, "p9-s2-cap-" + index);
            }
            var port = new CapacityPort(
                    helper, server, reference, primaryPlayer.actor().getUUID());
            runtime = directRuntime(store, port);
            startDirectRuntime(runtime, server);

            for (var playerIndex = 0; playerIndex < 4; playerIndex++) {
                admitP9Roots(
                        helper,
                        runtime,
                        server,
                        players[playerIndex].actor(),
                        reference,
                        16);
            }
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(
                    port.calls() == 64
                            && port.openedCount() == 64
                            && port.capacityRejections() == 0,
                    "the first four players must retain exactly 64 live RESERVED permits");

            admitP9Roots(
                    helper, runtime, server, primaryPlayer.actor(), reference, 1);
            for (var playerIndex = 4; playerIndex < 7; playerIndex++) {
                admitP9Roots(
                        helper,
                        runtime,
                        server,
                        players[playerIndex].actor(),
                        reference,
                        16);
            }
            admitP9Roots(
                    helper, runtime, server, players[7].actor(), reference, 15);
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(
                    port.calls() == 128
                            && port.openedCount() == 127
                            && port.capacityRejections() == 1,
                    "player cap max+1 must retain 16 and reject the seventeenth opener");

            admitP9Roots(
                    helper, runtime, server, players[7].actor(), reference, 1);
            runtime.handleRuntimePost(new ServerTickEvent.Post(() -> true, server));
            helper.assertTrue(
                    port.calls() == 129
                            && port.openedCount() == 128
                            && port.capacityRejections() == 1,
                    "eight players at 16 each must reach the exact 128-server active cap");
            var aboveServerCap = runtime.admitAuthenticatedPlayerCast(
                    server,
                    primaryPlayer.actor(),
                    reference,
                    expectedCastGeometry(primaryPlayer.actor()));
            helper.assertTrue(
                    aboveServerCap
                            instanceof RuntimeAdmissionResult.ActiveLineageCapacityExceeded full
                            && full.current() == 128
                            && full.maximum() == 128,
                    "server cap max+1 must reject before allocating a 129th lineage or permit");

            var plannedIds = new java.util.HashSet<UUID>(128);
            for (var index = 0; index < port.openedCount(); index++) {
                var opened = port.openedAt(index);
                helper.assertTrue(
                        plannedIds.add(opened.plannedProjectileId())
                                && noLoadedEntity(server, opened.plannedProjectileId()),
                        "each active cap slot must have one unique nonloaded planned identity");
            }
            helper.assertTrue(plannedIds.size() == 128,
                    "the stop-work fixture must expose exactly 128 real indexed permits");

            runtime.armP9TerminalDiagnosticFailureForTesting(
                    server, port.instanceIdAt(0), true);
            var closedWorkUnits = runtime.enterStoppingForTesting(server);
            var postStopObservations = 0;
            for (var index = 0; index < port.openedCount(); index++) {
                helper.assertTrue(
                        port.openedAt(index).permit().closeWithoutHit(
                                        server, ProjectileClosureReason.SERVER_STOPPED)
                                == RuntimePermitCloseDisposition.ALREADY_CLOSED,
                        "Stopping must drive every permit through its real owner close before late observation");
                postStopObservations++;
            }
            helper.assertTrue(
                    closedWorkUnits == 128
                            && postStopObservations == 128
                            && runtime.admitAuthenticatedPlayerCast(
                                            server,
                                            primaryPlayer.actor(),
                                            reference,
                                            expectedCastGeometry(primaryPlayer.actor()))
                                    instanceof RuntimeAdmissionResult.ServerStopping,
                    "one max-capacity Stopping pass must report 128 exact closes despite diagnostic Error");
            runtime.handleRuntimeStopped(new ServerStoppedEvent(server));
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(
                                    server,
                                    primaryPlayer.actor(),
                                    reference,
                                    expectedCastGeometry(primaryPlayer.actor()))
                            instanceof RuntimeAdmissionResult.ServerNotRunning,
                    "Stopped must remove the fully closed max-capacity slot");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            cleanupCapacityFixture(runtime, server, players, primary);
        }
    }

    private static void admitP9Roots(
            GameTestHelper helper,
            SkillRuntimeService runtime,
            MinecraftServer server,
            ServerPlayer actor,
            SkillReference reference,
            int count) {
        var geometry = expectedCastGeometry(actor);
        for (var index = 0; index < count; index++) {
            helper.assertTrue(
                    runtime.admitAuthenticatedPlayerCast(
                                    server, actor, reference, geometry)
                            instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "capacity fixture root admission must succeed before its tested cap");
        }
    }

    private static void cleanupCapacityFixture(
            SkillRuntimeService runtime,
            MinecraftServer server,
            ConnectedPlayer[] players,
            Throwable primary) {
        Throwable cleanupFailure = null;
        if (runtime != null) {
            try {
                runtime.handleRuntimeStopped(new ServerStoppedEvent(server));
            } catch (RuntimeException | Error failure) {
                cleanupFailure = failure;
            }
        }
        for (var index = players.length - 1; index >= 1; index--) {
            if (players[index] == null) {
                continue;
            }
            try {
                players[index].close();
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
        }
        if (cleanupFailure == null) {
            return;
        }
        if (primary != null) {
            if (cleanupFailure != primary) {
                primary.addSuppressed(cleanupFailure);
            }
            return;
        }
        rethrow(cleanupFailure);
    }

    private static SkillRuntimeService directRuntime(
            SkillDefinitionStoreService store, RuntimeExecutionPort port) {
        return new SkillRuntimeService(
                store,
                SkillSubmissionPolicyProvider.defaults(),
                new P5RuntimeProjector(ProfileAvailabilityView.unknown()),
                new P5LoadedReferenceResolver(),
                port);
    }

    private static void startDirectRuntime(
            SkillRuntimeService runtime, MinecraftServer server) {
        runtime.handleRuntimeStarted(
                new ServerStartedEvent(server), directQualificationLimits());
    }

    private static void stopDirectRuntime(
            SkillRuntimeService runtime, MinecraftServer server, Throwable primary) {
        try {
            runtime.handleRuntimeStopped(new ServerStoppedEvent(server));
        } catch (RuntimeException | Error cleanupFailure) {
            if (primary == null) {
                throw cleanupFailure;
            }
            if (cleanupFailure != primary) {
                primary.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void assertApplied(
            GameTestHelper helper,
            PlayerSkillAttachmentService.Result<
                            PlayerSkillAttachmentService.MutationOutcome>
                    result,
            String message) {
        helper.assertTrue(
                result instanceof PlayerSkillAttachmentService.Available<?> available
                        && available.value() == PlayerSkillAttachmentService.Applied.INSTANCE,
                message);
    }

    private static CastGeometryExecutionDataV0 expectedCastGeometry(ServerPlayer actor) {
        var eye = actor.getEyePosition();
        var look = actor.getLookAngle();
        var inverseLength = 1.0 / StrictMath.sqrt(
                look.x * look.x + look.y * look.y + look.z * look.z);
        var directionX = look.x * inverseLength;
        var directionY = look.y * inverseLength;
        var directionZ = look.z * inverseLength;
        return new CastGeometryExecutionDataV0(
                actor.serverLevel().dimension().location(),
                eye.x + directionX * 0.10,
                eye.y + directionY * 0.10 - 0.10,
                eye.z + directionZ * 0.10,
                encodeQ15(directionX),
                encodeQ15(directionY),
                encodeQ15(directionZ),
                0);
    }

    private static int encodeQ15(double component) {
        var rounded = StrictMath.rint(component * 32_767.0);
        return (int) Math.max(-32_767.0, Math.min(32_767.0, rounded));
    }

    private static boolean noLoadedEntity(MinecraftServer server, UUID entityId) {
        for (var level : server.getAllLevels()) {
            if (level.getEntity(entityId) != null) {
                return false;
            }
        }
        return true;
    }

    private static P5RuntimeLimits directQualificationLimits() {
        return P5RuntimeLimits.fromRequested(new P5RuntimeRequestedLimits(
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SKILL_INSTANCE,
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_PLAYER,
                MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER,
                MagicSafetyCeilings.MAX_ACTIVE_SKILL_INSTANCES_PER_BUDGET_ATTRIBUTION,
                MagicSafetyCeilings.MAX_ACTIVE_LINEAGES_PER_SERVER,
                MagicSafetyCeilings.MAX_ROOT_ADMISSIONS_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_PLAYER_PER_TICK,
                MagicSafetyCeilings.MAX_EXECUTIONS_PER_SERVER_PER_TICK,
                MagicSafetyCeilings.MAX_EVENTS_PER_LINEAGE,
                MagicSafetyCeilings.MAX_DEPTH_PER_LINEAGE,
                MagicSafetyCeilings.MAX_DIRECT_CHILDREN_PER_EVENT,
                MagicSafetyCeilings.MAX_ZERO_DELAY_CHILDREN_PER_EVENT,
                MagicSafetyCeilings.MAX_DELAY_TICKS,
                MagicSafetyCeilings.MAX_DEADLINE_HORIZON_TICKS,
                MagicSafetyCeilings.MAX_CANCELLATIONS_PER_TICK));
    }

    private static void cleanupDirectRuntimeFixture(
            SkillRuntimeService runtime,
            ConnectedPlayer player,
            LoginFixture fixture,
            Throwable primary) {
        Throwable cleanupFailure = null;
        if (runtime != null) {
            try {
                runtime.requestP9ReloadInvalidation();
                runtime.completeP9Reload(fixture.server());
            } catch (RuntimeException | Error failure) {
                cleanupFailure = failure;
            }
            try {
                runtime.handleRuntimeStopped(new ServerStoppedEvent(fixture.server()));
            } catch (RuntimeException | Error failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
        }
        try {
            player.close();
        } catch (RuntimeException | Error failure) {
            cleanupFailure = append(cleanupFailure, failure);
        }
        try {
            fixture.close();
        } catch (RuntimeException | Error failure) {
            cleanupFailure = append(cleanupFailure, failure);
        }
        if (cleanupFailure == null) {
            return;
        }
        if (primary != null) {
            if (cleanupFailure != primary) {
                primary.addSuppressed(cleanupFailure);
            }
            return;
        }
        rethrow(cleanupFailure);
    }

    private static Throwable append(Throwable current, Throwable additional) {
        if (current == null) {
            return additional;
        }
        if (current != additional) {
            current.addSuppressed(additional);
        }
        return current;
    }

    private static final class CountingExecutionPort implements RuntimeExecutionPort {
        private int calls;

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            calls++;
            return P6RuntimeExecutionPortAdapter.completedEmpty();
        }

        private int calls() {
            return calls;
        }
    }

    private static final class ReplacementBeforeOpenPort implements RuntimeExecutionPort {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final ConnectedPlayer player;
        private final ServerPlayer actor;
        private final SkillReference exactReference;
        private final CastGeometryExecutionDataV0 expectedGeometry;
        private int calls;
        private ServerPlayer replacement;
        private RuntimeProjectileContinuationOpenResult openResult;

        private ReplacementBeforeOpenPort(
                GameTestHelper helper,
                MinecraftServer server,
                ConnectedPlayer player,
                ServerPlayer actor,
                SkillReference exactReference,
                CastGeometryExecutionDataV0 expectedGeometry) {
            this.helper = Objects.requireNonNull(helper, "helper");
            this.server = Objects.requireNonNull(server, "server");
            this.player = Objects.requireNonNull(player, "player");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.exactReference = Objects.requireNonNull(exactReference, "exactReference");
            this.expectedGeometry = Objects.requireNonNull(expectedGeometry, "expectedGeometry");
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            calls++;
            helper.assertTrue(
                    calls == 1
                            && server.isSameThread()
                            && context.server() == server
                            && event.skillReference().equals(exactReference)
                            && event.executionData().equals(expectedGeometry)
                            && context.resolvedReferences().origin()
                                    instanceof ResolvedPlayerOrigin origin
                            && origin.player() == actor,
                    "pre-detach fixture must first resolve exact actor A");
            replacement = player.respawnCurrent();
            helper.assertTrue(
                    replacement != actor
                            && replacement.getUUID().equals(actor.getUUID())
                            && replacement.connection == actor.connection,
                    "respawn must replace A while retaining the same underlying connection");
            openResult = context.projectileContinuationOpener()
                    .openProjectileContinuation(ActionOutputKind.PROJECTILE, 0);
            return P6RuntimeExecutionPortAdapter.completedEmpty();
        }

        private int calls() {
            return calls;
        }

        private ServerPlayer replacement() {
            return Objects.requireNonNull(replacement, "replacement");
        }

        private RuntimeProjectileContinuationOpenResult openResult() {
            return Objects.requireNonNull(openResult, "openResult");
        }
    }

    private static final class CapacityPort implements RuntimeExecutionPort {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final SkillReference exactReference;
        private final UUID cappedPlayerId;
        private final RuntimeProjectileContinuationOpenResult.Opened[] opened =
                new RuntimeProjectileContinuationOpenResult.Opened[128];
        private final SkillInstanceId[] instanceIds = new SkillInstanceId[128];
        private int calls;
        private int openedCount;
        private int capacityRejections;

        private CapacityPort(
                GameTestHelper helper,
                MinecraftServer server,
                SkillReference exactReference,
                UUID cappedPlayerId) {
            this.helper = Objects.requireNonNull(helper, "helper");
            this.server = Objects.requireNonNull(server, "server");
            this.exactReference = Objects.requireNonNull(exactReference, "exactReference");
            this.cappedPlayerId = Objects.requireNonNull(cappedPlayerId, "cappedPlayerId");
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            calls++;
            helper.assertTrue(
                    server.isSameThread()
                            && context.server() == server
                            && event.skillReference().equals(exactReference)
                            && context.definition().reference().equals(exactReference)
                            && event.executionData() instanceof CastGeometryExecutionDataV0
                            && context.resolvedReferences().origin()
                                    instanceof ResolvedPlayerOrigin,
                    "capacity fixture must dispatch only exact server-owned P9 roots");
            var result = context.projectileContinuationOpener()
                    .openProjectileContinuation(ActionOutputKind.PROJECTILE, 0);
            if (result instanceof RuntimeProjectileContinuationOpenResult.Opened accepted) {
                helper.assertTrue(openedCount < opened.length,
                        "active continuation index exceeded its exact server cap");
                opened[openedCount] = accepted;
                instanceIds[openedCount] = event.skillInstanceId();
                openedCount++;
            } else {
                var rejected = (RuntimeProjectileContinuationOpenResult.Rejected) result;
                var origin = (ResolvedPlayerOrigin) context.resolvedReferences().origin();
                helper.assertTrue(
                        rejected.reason()
                                        == RuntimeProjectileContinuationOpenRejectionReason
                                                .CAPACITY_UNAVAILABLE
                                && origin.player().getUUID().equals(cappedPlayerId),
                        "only the seventeenth permit for one player may reject in-port");
                capacityRejections++;
            }
            return P6RuntimeExecutionPortAdapter.completedEmpty();
        }

        private int calls() {
            return calls;
        }

        private int openedCount() {
            return openedCount;
        }

        private int capacityRejections() {
            return capacityRejections;
        }

        private RuntimeProjectileContinuationOpenResult.Opened openedAt(int index) {
            return Objects.requireNonNull(opened[index], "opened continuation");
        }

        private SkillInstanceId instanceIdAt(int index) {
            return Objects.requireNonNull(instanceIds[index], "opened instance id");
        }
    }

    private static final class LifecyclePort implements RuntimeExecutionPort {
        private enum Mode {
            COMPLETED,
            THROW_RUNTIME,
            THROW_ERROR
        }

        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final ServerPlayer actor;
        private final SkillReference exactReference;
        private final CastGeometryExecutionDataV0 expectedGeometry;
        private final Mode mode;
        private final RuntimeException runtimeFailure;
        private final Error errorFailure;
        private int calls;
        private RuntimeCancellationToken cancellationToken;
        private RuntimeProjectileContinuationOpenResult.Opened opened;

        private LifecyclePort(
                GameTestHelper helper,
                MinecraftServer server,
                ServerPlayer actor,
                SkillReference exactReference,
                CastGeometryExecutionDataV0 expectedGeometry,
                Mode mode,
                RuntimeException runtimeFailure,
                Error errorFailure) {
            this.helper = Objects.requireNonNull(helper, "helper");
            this.server = Objects.requireNonNull(server, "server");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.exactReference = Objects.requireNonNull(exactReference, "exactReference");
            this.expectedGeometry = Objects.requireNonNull(expectedGeometry, "expectedGeometry");
            this.mode = Objects.requireNonNull(mode, "mode");
            this.runtimeFailure = runtimeFailure;
            this.errorFailure = errorFailure;
            if (mode == Mode.THROW_RUNTIME != (runtimeFailure != null)
                    || mode == Mode.THROW_ERROR != (errorFailure != null)) {
                throw new IllegalArgumentException("invalid lifecycle port failure pairing");
            }
        }

        private static LifecyclePort completed(
                GameTestHelper helper,
                MinecraftServer server,
                ServerPlayer actor,
                SkillReference reference,
                CastGeometryExecutionDataV0 geometry) {
            return new LifecyclePort(
                    helper, server, actor, reference, geometry, Mode.COMPLETED, null, null);
        }

        private static LifecyclePort throwingRuntime(
                GameTestHelper helper,
                MinecraftServer server,
                ServerPlayer actor,
                SkillReference reference,
                CastGeometryExecutionDataV0 geometry,
                RuntimeException failure) {
            return new LifecyclePort(
                    helper,
                    server,
                    actor,
                    reference,
                    geometry,
                    Mode.THROW_RUNTIME,
                    Objects.requireNonNull(failure, "failure"),
                    null);
        }

        private static LifecyclePort throwingError(
                GameTestHelper helper,
                MinecraftServer server,
                ServerPlayer actor,
                SkillReference reference,
                CastGeometryExecutionDataV0 geometry,
                Error failure) {
            return new LifecyclePort(
                    helper,
                    server,
                    actor,
                    reference,
                    geometry,
                    Mode.THROW_ERROR,
                    null,
                    Objects.requireNonNull(failure, "failure"));
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            calls++;
            helper.assertTrue(
                    calls == 1
                            && server.isSameThread()
                            && context.server() == server
                            && event.skillReference().equals(exactReference)
                            && context.definition().reference().equals(exactReference)
                            && event.executionData().equals(expectedGeometry)
                            && context.resolvedReferences().origin()
                                    instanceof ResolvedPlayerOrigin origin
                            && origin.player() == actor,
                    "lifecycle fixture must execute one exact real P9 root");
            cancellationToken = event.cancellationToken();
            var result = context.projectileContinuationOpener()
                    .openProjectileContinuation(ActionOutputKind.PROJECTILE, 0);
            helper.assertTrue(
                    result instanceof RuntimeProjectileContinuationOpenResult.Opened,
                    "lifecycle fixture must detach through the real P5 opener");
            opened = (RuntimeProjectileContinuationOpenResult.Opened) result;
            return switch (mode) {
                case COMPLETED -> P6RuntimeExecutionPortAdapter.completedEmpty();
                case THROW_RUNTIME -> throw runtimeFailure;
                case THROW_ERROR -> throw errorFailure;
            };
        }

        private int calls() {
            return calls;
        }

        private RuntimeCancellationToken cancellationToken() {
            return Objects.requireNonNull(cancellationToken, "cancellationToken");
        }

        private RuntimeProjectileContinuationOpenResult.Opened opened() {
            return opened;
        }
    }

    private static final class ReservedContinuationPort implements RuntimeExecutionPort {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final ServerPlayer actor;
        private final SkillReference exactReference;
        private final CastGeometryExecutionDataV0 expectedGeometry;
        private int calls;
        private long heldChildEventId;
        private RuntimeEventToken rootEventToken;
        private RuntimeCancellationToken cancellationToken;
        private RuntimeBudgetAttribution observedAttribution;
        private SkillReference observedReference;
        private RuntimeProjectileContinuationOpenResult.Opened opened;

        private ReservedContinuationPort(
                GameTestHelper helper,
                MinecraftServer server,
                ServerPlayer actor,
                SkillReference exactReference,
                CastGeometryExecutionDataV0 expectedGeometry) {
            this.helper = Objects.requireNonNull(helper, "helper");
            this.server = Objects.requireNonNull(server, "server");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.exactReference = Objects.requireNonNull(exactReference, "exactReference");
            this.expectedGeometry = Objects.requireNonNull(expectedGeometry, "expectedGeometry");
        }

        @Override
        public RuntimeExecutionBatch execute(
                RuntimeEvent event, RuntimeExecutionContext context) {
            calls++;
            helper.assertTrue(calls == 1,
                    "the P9 root must have exactly one actual execution-port invocation");
            helper.assertTrue(
                    server.isSameThread()
                            && context.server() == server
                            && event.skillReference().equals(exactReference)
                            && context.definition().reference().equals(exactReference)
                            && event.executionData().equals(expectedGeometry)
                            && context.resolvedReferences().origin()
                                    instanceof ResolvedPlayerOrigin origin
                            && origin.player() == actor
                            && context.resolvedReferences().target()
                                    == NoResolvedRuntimeTarget.INSTANCE,
                    "the actual port must receive the pinned revision and exact server snapshot");
            helper.assertTrue(
                    context.executionBudget().directChildCapacity() >= 1
                            && context.executionBudget().zeroDelayChildCapacity() >= 1,
                    "P5 must reserve firm direct/zero-delay budget before invoking the port");
            heldChildEventId = Math.addExact(event.eventId().value(), 1L);
            rootEventToken = new RuntimeEventToken(
                    context.serverSlotToken(), event.skillInstanceId(), event.eventId());
            cancellationToken = event.cancellationToken();
            observedAttribution = event.budgetAttribution();
            observedReference = event.skillReference();

            var result = context.projectileContinuationOpener()
                    .openProjectileContinuation(ActionOutputKind.PROJECTILE, 0);
            helper.assertTrue(
                    result instanceof RuntimeProjectileContinuationOpenResult.Opened,
                    "the real call-scoped opener must detach one formal RESERVED permit");
            opened = (RuntimeProjectileContinuationOpenResult.Opened) result;
            helper.assertTrue(
                    opened.plannedProjectileId().equals(new UUID(
                            ~context.serverSlotToken().value(), heldChildEventId)),
                    "planned projectile UUID must derive exactly from slot token and held EventId");
            var repeated = context.projectileContinuationOpener()
                    .openProjectileContinuation(ActionOutputKind.PROJECTILE, 0);
            helper.assertTrue(
                    repeated instanceof RuntimeProjectileContinuationOpenResult.Rejected rejected
                            && rejected.reason()
                                    == RuntimeProjectileContinuationOpenRejectionReason
                                            .INVARIANT_REJECTED,
                    "the call-scoped opener must remain exactly one-shot");
            return P6RuntimeExecutionPortAdapter.completedEmpty();
        }

        private int calls() {
            return calls;
        }

        private long heldChildEventId() {
            return heldChildEventId;
        }

        private RuntimeCancellationToken cancellationToken() {
            return Objects.requireNonNull(cancellationToken, "cancellationToken");
        }

        private RuntimeEventToken rootEventToken() {
            return Objects.requireNonNull(rootEventToken, "rootEventToken");
        }

        private RuntimeBudgetAttribution observedAttribution() {
            return Objects.requireNonNull(observedAttribution, "observedAttribution");
        }

        private SkillReference observedReference() {
            return Objects.requireNonNull(observedReference, "observedReference");
        }

        private RuntimeProjectileContinuationOpenResult.Opened opened() {
            return opened;
        }
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
        return loginFixture(server, port,
                PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests());
    }

    private static LoginFixture cooldownLoginFixture(
            MinecraftServer server, P7ServerAuthorizationBoundary.LoginReadyPort port) {
        return loginFixture(server, port, cooldownAttachments(server));
    }

    private static PlayerSkillAttachmentService cooldownAttachments(MinecraftServer server) {
        throw new AssertionError("P9 GameTests require the excluded original-root binding");
    }

    /** The exact original source-bound owner; never a second publication service. */
    public static PlayerSkillAttachmentService nativeGameTestAttachments(MinecraftServer server) {
        return cooldownAttachments(server);
    }

    private static P4E2QualificationFacade nativeGameTestFacade(MinecraftServer server) {
        throw new AssertionError("native E2 observation requires the excluded original-root binding");
    }

    private static LoginFixture loginFixture(MinecraftServer server,
            P7ServerAuthorizationBoundary.LoginReadyPort port,
            PlayerSkillAttachmentService attachments) {
        var storage = server.overworld().getDataStorage();
        var original = Objects.requireNonNull(storage.get(CACHE_ONLY, SAVED_DATA_NAME));
        var bus = BusBuilder.builder().build();
        var facade = new P4E2QualificationFacade();
        var store = SkillDefinitionStoreService.registerOn(
                bus, attachments, port, facade.storeView(), facade.playerView());
        var recovery = SkillSubmissionRecoveryService.create(attachments,
                store.submissionPort(), store.onlineReconciliationDependency(),
                facade.submissionView());
        recovery.registerOn(bus);
        bus.start();
        var fixture = new LoginFixture(server, bus, original, attachments, store, facade);
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

    private static void assertAttachmentsUnchanged(
            GameTestHelper helper,
            ServerPlayer actor,
            CompoundTag expected,
            String stage) {
        var actual = actor.saveWithoutId(new CompoundTag())
                .getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        var expectedRest = expected.copy();
        var actualRest = actual.copy();
        var key = "gramarye:cast_cooldowns";
        var before = expectedRest.get(key);
        var after = actualRest.get(key);
        expectedRest.remove(key);
        actualRest.remove(key);
        helper.assertTrue(expectedRest.equals(actualRest), stage + " changed another Attachment");
        helper.assertTrue(legalEmptyCooldownFloor(before, after, actor.getServer().overworld().getGameTime()),
                stage + " changed more than the trusted empty cooldown clock floor");
    }

    private static boolean legalEmptyCooldownFloor(Tag before, Tag after, long gameTime) {
        if (Objects.equals(before, after)) { return true; }
        if (!(before instanceof CompoundTag old) || !(after instanceof CompoundTag current)
                || !(old.get("clock_floor") instanceof net.minecraft.nbt.LongTag oldFloor)
                || !(current.get("clock_floor") instanceof net.minecraft.nbt.LongTag newFloor)
                || oldFloor.getAsLong() < 0 || newFloor.getAsLong() < oldFloor.getAsLong()
                || newFloor.getAsLong() > gameTime
                || !old.getAllKeys().equals(java.util.Set.of("schema_version", "kind", "clock_floor", "entries"))
                || !(old.get("schema_version") instanceof net.minecraft.nbt.IntTag version)
                || version.getAsInt() != 1
                || !(old.get("kind") instanceof net.minecraft.nbt.ByteTag kind) || kind.getAsByte() != 0
                || !(old.get("entries") instanceof net.minecraft.nbt.ListTag entries)
                || !entries.isEmpty()) { return false; }
        var oldRest = old.copy();
        var newRest = current.copy();
        oldRest.remove("clock_floor");
        newRest.remove("clock_floor");
        return oldRest.equals(newRest);
    }

    private static Tag attachmentPayload(ServerPlayer actor, String key) {
        return Objects.requireNonNull(actor.saveWithoutId(new CompoundTag())
                .getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(key));
    }

    private static UUID playerId(int suffix) {
        return new UUID(0x7400000000004000L, 0x8000000000000000L + suffix);
    }

    private static ServerPlayer unplacedPlayer(MinecraftServer server, int suffix, String name) {
        return unplacedPlayer(server, playerId(suffix), name);
    }

    private static ServerPlayer unplacedPlayer(
            MinecraftServer server, UUID playerId, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId, name), false);
        return new ServerPlayer(server, server.overworld(),
                cookie.gameProfile(), cookie.clientInformation());
    }

    private static ConnectedPlayer placePlayer(MinecraftServer server, int suffix, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(playerId(suffix), name), false);
        return placeCooldownPlayer(server, cookie);
    }

    /** Named stock GameTests retain ownership of their existing on-disk input and cleanup. */
    public static ServerPlayer connectNativeGameTestPlayer(
            MinecraftServer server, UUID playerId, String name) {
        if (!server.isSameThread() || server.getPlayerList().getPlayer(playerId) != null) {
            throw new IllegalStateException("native GameTest login requires an unoccupied exact UUID");
        }
        return placeCooldownPlayer(server,
                CommonListenerCookie.createInitial(new GameProfile(playerId, name), false), null).actor();
    }

    static ServerPlayer makeCooldownMockPlayer(GameTestHelper helper, long fixtureId) {
        if (fixtureId <= 0) { throw new IllegalArgumentException("positive fixture identity required"); }
        var server = helper.getLevel().getServer();
        var profile = new GameProfile(new UUID(0x79c0000000004000L,
                0x8000000000000000L | fixtureId), "p11-test-player");
        var fixture = placeCooldownPlayer(server, CommonListenerCookie.createInitial(profile, false));
        try {
            var actor = fixture.actor();
            if (actor.serverLevel() != helper.getLevel()) {
                throw new AssertionError("qualified mock must use the original GameTest level");
            }
            actor.setGameMode(GameType.CREATIVE);
            return actor;
        } catch (RuntimeException | Error failure) {
            try { fixture.close(); }
            catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    static void closeCooldownMockPlayer(ServerPlayer actor) {
        var fixture = actor.connection.getConnection().channel().attr(COOLDOWN_PLAYER).get();
        if (fixture == null || !fixture.actor().getUUID().equals(actor.getUUID())) {
            throw new AssertionError("exact owned cooldown GameTest player required");
        }
        fixture.close();
    }

    public static ServerPlayer respawnNativeGameTestPlayer(ServerPlayer before) {
        return respawnCooldownMockPlayer(before);
    }

    static ServerPlayer respawnCooldownMockPlayer(ServerPlayer before) {
        var server = before.getServer();
        var connection = before.connection.getConnection();
        var fixture = connection.channel().attr(COOLDOWN_PLAYER).get();
        var observer = (CooldownStateObserver) connection.channel().pipeline()
                .get(COOLDOWN_STATE_OBSERVER);
        if (!server.isSameThread() || fixture == null || fixture.current() != before
                || observer == null || !before.isAlive()) {
            throw new AssertionError("respawn control requires the exact living managed mock");
        }
        var previous = observer.state;
        before.kill();
        awaitNative(server, connection, () -> freshDeathState(previous, observer.state),
                "original death STATE was not delivered");
        var state = observer.state;
        var request = new P11TransitionProtocol.Request(state.scope(), state.connectionEpoch(),
                state.sceneSerial(), state.actorGeneration(), fixture.nextControlSequence(state),
                P11TransitionProtocol.Command.TRY, state.kind());
        before.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(
                new P11TransitionRequestPayload(request)));
        awaitNative(server, connection, () -> {
            var current = server.getPlayerList().getPlayer(before.getUUID());
            return current != null && current != before;
        }, "original authorized PERFORM_RESPAWN did not replace the actor");
        var replacement = server.getPlayerList().getPlayer(before.getUUID());
        if (replacement.connection != before.connection || replacement.connection.player != replacement) {
            throw new AssertionError("native respawn did not preserve the exact connection");
        }
        fixture.currentActor = replacement;
        return replacement;
    }

    /** Original first End completion, not a direct PlayerList respawn or a seeded wonGame flag. */
    public static ServerPlayer completeEndGameTestPlayer(ServerPlayer before) {
        var server = before.getServer();
        var connection = before.connection.getConnection();
        var fixture = connection.channel().attr(COOLDOWN_PLAYER).get();
        var observer = (CooldownStateObserver) connection.channel().pipeline().get(COOLDOWN_STATE_OBSERVER);
        if (!server.isSameThread() || fixture == null || fixture.current() != before
                || observer == null || before.isRemoved() || before.wonGame || before.seenCredits) {
            throw new AssertionError("End control requires the exact fresh current mock");
        }
        var end = Objects.requireNonNull(server.getLevel(net.minecraft.world.level.Level.END));
        if (before.serverLevel() != end) {
            var changed = before.changeDimension(new DimensionTransition(end, before, DimensionTransition.DO_NOTHING));
            before.hasChangedDimension();
            if (changed != before) { throw new AssertionError("original End travel did not retain the actor"); }
        }
        var previous = observer.state;
        before.showEndCredits();
        awaitNative(server, connection, () -> freshSceneState(previous, observer.state,
                P11TransitionProtocol.Kind.END), "original End STATE was not delivered");
        var state = observer.state;
        before.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new P11TransitionRequestPayload(
                new P11TransitionProtocol.Request(state.scope(), state.connectionEpoch(), state.sceneSerial(),
                        state.actorGeneration(), fixture.nextControlSequence(state),
                        P11TransitionProtocol.Command.TRY, state.kind()))));
        awaitNative(server, connection, () -> {
            var current = server.getPlayerList().getPlayer(before.getUUID());
            return current != null && current != before;
        }, "original authorized End completion did not replace the actor");
        var replacement = server.getPlayerList().getPlayer(before.getUUID());
        if (replacement.connection != before.connection || replacement.connection.player != replacement
                || !replacement.seenCredits || replacement.wonGame) {
            throw new AssertionError("original End completion did not preserve native connection/credit state");
        }
        fixture.currentActor = replacement;
        return replacement;
    }

    private static boolean freshDeathState(P11TransitionProtocol.State previous,
            P11TransitionProtocol.State current) {
        return freshSceneState(previous, current, P11TransitionProtocol.Kind.DEATH);
    }

    private static boolean freshSceneState(P11TransitionProtocol.State previous,
            P11TransitionProtocol.State current, P11TransitionProtocol.Kind kind) {
        return previous != null && current != null
                && current.scope() == P11TransitionProtocol.Scope.PLAY
                && current.kind() == kind
                && current.connectionEpoch() == previous.connectionEpoch()
                && current.sceneSerial() > previous.sceneSerial()
                && current.actorGeneration() >= previous.actorGeneration()
                && current.requestSeq() == 0
                && current.statusVersion() > previous.statusVersion();
    }

    /** Four native-control fixtures run only after native flushing and Post have resumed. */
    public static void runCooldownFixtureAfterTick(GameTestHelper helper, Runnable body,
            Runnable after, Consumer<Throwable> abandon) {
        var server = helper.getLevel().getServer();
        if (!server.isSameThread()) {
            throw new AssertionError("owned cooldown fixture scheduling requires the server thread");
        }
        var completion = new CompletableFuture<Throwable>();
        // Publication belongs to GameTestInfo.tick, not the external server task: native
        // test listeners are notified only when that tick observes its own terminal state.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(completion.isDone(),
                        "original queued cooldown fixture body has not returned"))
                .thenExecute(() -> {
                    var primary = completion.getNow(null);
                    if (primary != null) {
                        helper.testInfo.fail(primary);
                        return;
                    }
                    try { after.run(); }
                    catch (RuntimeException | Error failure) { helper.testInfo.fail(failure); }
                });
        // execute() runs inline on main; tell(TickTask) is the actual deferred native queue.
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (helper.testInfo.isDone()) {
                var primary = helper.testInfo.getError();
                if (primary == null) {
                    primary = new AssertionError("owned cooldown test ended before its queued body");
                }
                try { abandon.accept(primary); }
                catch (RuntimeException | Error cleanup) {
                    if (cleanup != primary) { primary.addSuppressed(cleanup); }
                }
                completion.complete(primary);
                return;
            }
            Throwable primary = null;
            try { body.run(); }
            catch (RuntimeException | Error failure) { primary = failure; }
            completion.complete(primary);
        }));
    }

    /** Read-only fixture precondition after an actual native post-login tick. */
    public static void requireCooldownLoginComplete(ServerPlayer actor) {
        var server = actor.getServer();
        var connection = actor.connection.getConnection();
        var fixture = connection.channel().attr(COOLDOWN_PLAYER).get();
        if (!server.isSameThread() || fixture == null || fixture.current() != actor
                || connection.getPacketListener() != actor.connection || !connection.isConnected()) {
            throw new AssertionError("initial sync requires the exact owned current mock");
        }
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        if (body == null || body.account.metadata != null
                || source.controlGate(actor.getUUID(), actor) != P11QualifiedSourceOwner.ControlGate.CLEAR) {
            throw new AssertionError("original post-login sync has not released its real H responsibility");
        }
    }

    /** Real negative only: a nonnormal E2 account cannot manufacture a completed respawn. */
    public static void assertNativeQuarantineBlocksRespawn(ServerPlayer actor) {
        var server = actor.getServer();
        var connection = actor.connection.getConnection();
        var fixture = connection.channel().attr(COOLDOWN_PLAYER).get();
        var observer = (CooldownStateObserver) connection.channel().pipeline().get(COOLDOWN_STATE_OBSERVER);
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        if (!server.isSameThread() || fixture == null || fixture.current() != actor || observer == null
                || body == null || !source.canCopy(body) || body.account.metadata == null
                || source.controlGate(actor.getUUID(), actor) != P11QualifiedSourceOwner.ControlGate.ACTIVE_TRANSITION) {
            throw new AssertionError("quarantine negative requires the real blocked metadata owner");
        }
        var previous = observer.state;
        actor.kill();
        awaitNative(server, connection, () -> freshDeathState(previous, observer.state),
                "quarantine negative did not observe the original fresh DEATH state");
        var state = observer.state;
        long sequence = fixture.nextControlSequence(state);
        actor.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new P11TransitionRequestPayload(
                new P11TransitionProtocol.Request(state.scope(), state.connectionEpoch(), state.sceneSerial(),
                        state.actorGeneration(), sequence, P11TransitionProtocol.Command.TRY, state.kind()))));
        awaitNative(server, connection, () -> observer.state != null && observer.state.requestSeq() == sequence
                && observer.state.outcome() == P11TransitionProtocol.Outcome.NOT_STARTED,
                "quarantine negative did not observe original refusal");
        var refused = observer.state;
        if (refused.reason() != P11TransitionProtocol.Reason.ACTIVE_TRANSITION
                || refused.availability() != P11TransitionProtocol.Availability.WAIT_NOTIFY
                || refused.connectionEpoch() != state.connectionEpoch() || refused.sceneSerial() != state.sceneSerial()
                || refused.actorGeneration() != state.actorGeneration() || refused.statusVersion() <= state.statusVersion()
                || server.getPlayerList().getPlayer(actor.getUUID()) != actor || source.body(actor) != body
                || body.account.metadata == null || !actor.isDeadOrDying()) {
            throw new AssertionError("blocked original C4a request must not install a successful replacement");
        }
    }

    private static ConnectedPlayer placeCooldownPlayer(
            MinecraftServer server, CommonListenerCookie cookie) {
        var playerdata = PlayerdataClaim.claim(server, cookie.gameProfile().getId());
        return placeCooldownPlayer(server, cookie, playerdata);
    }

    private static ConnectedPlayer placeCooldownPlayer(
            MinecraftServer server, CommonListenerCookie cookie, PlayerdataClaim playerdata) {
        cooldownAttachments(server);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var observer = new CooldownStateObserver();
        var channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel exact) {
                Connection.configureInMemoryPipeline(exact.pipeline(), PacketFlow.SERVERBOUND);
                connection.configurePacketHandler(exact.pipeline());
                exact.pipeline().addLast(COOLDOWN_STATE_OBSERVER, observer);
            }
        });
        try {
            NetworkRegistry.configureMockConnection(connection);
            var nativeCookie = new CommonListenerCookie(cookie.gameProfile(), cookie.latency(),
                    cookie.clientInformation(), cookie.transferred(),
                    NetworkRegistry.getConnectionType(connection));
            var configuration = new ServerConfigurationPacketListenerImpl(server, connection, nativeCookie);
            connection.setupInboundProtocol(ConfigurationProtocols.SERVERBOUND, configuration);
            connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
            configuration.returnToWorld();
            awaitNative(server, connection, () -> JoinWorldTask.TYPE.equals(
                    ((P11ConfigurationBoundary.Access) configuration).p11$currentConfigurationTask()),
                    "original P11 configuration task did not complete");
            configuration.handleConfigurationFinished(ServerboundFinishConfigurationPacket.INSTANCE);
            var actor = server.getPlayerList().getPlayer(cookie.gameProfile().getId());
            if (actor == null || actor.connection.getConnection() != connection
                    || connection.getPacketListener() != actor.connection) {
                throw new AssertionError("original Finish did not place the exact mock actor");
            }
            awaitNative(server, connection, () -> observer.state != null
                    && observer.state.kind() == P11TransitionProtocol.Kind.JOIN
                    && observer.state.outcome() == P11TransitionProtocol.Outcome.COMPLETED
                    && observer.state.targetActorGeneration() > 0,
                    "original JOIN completion was not delivered");
            var fixture = new ConnectedPlayer(server, actor, channel, playerdata);
            channel.attr(COOLDOWN_PLAYER).set(fixture);
            return fixture;
        } catch (RuntimeException | Error failure) {
            try {
                var actor = server.getPlayerList().getPlayer(cookie.gameProfile().getId());
                if (actor != null && actor.connection.getConnection() == connection) {
                    server.getPlayerList().remove(actor);
                }
                channel.finishAndReleaseAll();
                if (playerdata != null) { playerdata.release(); }
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private static void awaitNative(MinecraftServer server, Connection connection,
            BooleanSupplier completed, String failure) {
        int enteredTick = server.getTickCount();
        long until = System.nanoTime() + 5_000_000_000L;
        server.managedBlock(() -> completed.getAsBoolean()
                || !connection.isConnected() || System.nanoTime() >= until);
        if (!connection.isConnected() || !completed.getAsBoolean()) {
            var primary = new AssertionError(failure);
            try {
                primary.addSuppressed(new AssertionError(
                        cooldownTimeoutObservation(server, connection, enteredTick)));
            } catch (RuntimeException | Error ignoredDiagnostic) {
                // This test-only readout must never replace the original failed assertion.
            }
            throw primary;
        }
    }

    /** Fixed enums/scalars only: no profile, packet, Throwable text or actor serialization. */
    private static String cooldownTimeoutObservation(
            MinecraftServer server, Connection connection, int enteredTick) {
        String result = "P11_GT_CONTROL_TIMEOUT main=" + server.isSameThread()
                + " connected=" + connection.isConnected()
                + " enteredTick=" + Integer.toUnsignedLong(enteredTick)
                + " currentTick=" + Integer.toUnsignedLong(server.getTickCount());
        if (!server.isSameThread()) { return result; }
        var observer = (CooldownStateObserver) connection.channel().pipeline()
                .get(COOLDOWN_STATE_OBSERVER);
        var state = observer == null ? null : observer.state;
        if (state != null) {
            result += " state=" + state.scope() + "/" + state.kind() + "/" + state.outcome()
                    + "/" + state.availability() + "/" + state.reason()
                    + " c=" + state.connectionEpoch() + " scene=" + state.sceneSerial()
                    + " actor=" + state.actorGeneration() + " n=" + state.requestSeq()
                    + " v=" + state.statusVersion();
        } else { result += " state=UNAVAILABLE"; }
        var fixture = connection.channel().attr(COOLDOWN_PLAYER).get();
        if (fixture == null) { return result + " source=UNAVAILABLE"; }
        var actor = fixture.currentActor;
        result += " sent=" + fixture.controlSequence + " listenerCurrent="
                + (actor.connection != null && connection.getPacketListener() == actor.connection)
                + " rosterCurrent=" + (server.getPlayerList().getPlayer(actor.getUUID()) == actor)
                + " healthZero=" + (actor.getHealth() <= 0);
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        if (source == null || body == null) { return result + " source=UNAVAILABLE"; }
        result += " gate=" + source.controlGate(actor.getUUID(), actor)
                + " e=" + body.source.epoch() + " sourceV=" + body.source.version()
                + " complete=" + body.complete + " bodyFault=" + body.fault
                + " accountFault=" + body.account.fault + " candidate=" + (body.account.candidate != null);
        for (var kind : new P11ControlBudgets.Root[] {P11ControlBudgets.Root.WORK,
                P11ControlBudgets.Root.NATIVE_CREDIT, P11ControlBudgets.Root.OPERATION,
                P11ControlBudgets.Root.COMMAND_CONTEXT, P11ControlBudgets.Root.TRANSITION}) {
            result += " " + kind + "=" + body.account.nativeCounts[kind.ordinal()];
        }
        return result;
    }

    private static final class CooldownStateObserver extends ChannelOutboundHandlerAdapter {
        private P11TransitionProtocol.State state;

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                throws Exception {
            if (message instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof P11TransitionStatePayload payload) {
                promise.addListener(result -> {
                    if (result.isSuccess()) { state = payload.state(); }
                });
            }
            super.write(context, message, promise);
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
        private ServerPlayer currentActor;
        private long controlSequence;
        private boolean closed;

        private ConnectedPlayer(
                MinecraftServer server,
                ServerPlayer actor,
                EmbeddedChannel channel,
                PlayerdataClaim playerdata) {
            this.server = Objects.requireNonNull(server, "server");
            this.actor = Objects.requireNonNull(actor, "actor");
            this.channel = Objects.requireNonNull(channel, "channel");
            this.playerdata = playerdata; // null only for named fixtures that own their persisted input.
            this.currentActor = actor;
        }

        private ServerPlayer actor() {
            return actor;
        }

        private ServerPlayer current() {
            requireOpen();
            var current = server.getPlayerList().getPlayer(actor.getUUID());
            if (current == null || current != currentActor) {
                throw new AssertionError("test-owned exact current player is unavailable");
            }
            return current;
        }

        private long nextControlSequence(P11TransitionProtocol.State observed) {
            requireOpen();
            // A new scene's BINDING is n=0, not a reset of this physical connection's sender.
            controlSequence = Math.addExact(Math.max(controlSequence, observed.requestSeq()), 1);
            return controlSequence;
        }

        private ServerPlayer respawnCurrent() {
            var before = current();
            var replacement = respawnCooldownMockPlayer(before);
            currentActor = Objects.requireNonNull(replacement, "replacement");
            if (replacement == before
                    || !replacement.getUUID().equals(before.getUUID())
                    || replacement.connection != before.connection
                    || server.getPlayerList().getPlayer(actor.getUUID()) != replacement) {
                throw new AssertionError(
                        "actual respawn did not install the owned same-UUID replacement");
            }
            return replacement;
        }

        private ServerPlayer changeCurrentDimension(ServerLevel destination) {
            Objects.requireNonNull(destination, "destination");
            var current = current();
            var changed = current.changeDimension(new DimensionTransition(
                    destination, current, DimensionTransition.DO_NOTHING));
            current.hasChangedDimension();
            if (changed != current
                    || current.serverLevel() != destination
                    || server.getPlayerList().getPlayer(actor.getUUID()) != current) {
                throw new AssertionError(
                        "actual dimension movement did not preserve the owned actor object");
            }
            return current;
        }

        private void logoutCurrent() {
            requireOpen();
            removeExactCurrent();
        }

        private void closeAndInspectSaved(Consumer<ServerPlayer> inspection) {
            Objects.requireNonNull(inspection, "inspection");
            requireOpen();
            Throwable primary = null;
            try {
                removeExactCurrent();
                playerdata.requireOnlySavedPrimary();
                try (var readback = placeCooldownPlayer(server,
                        CommonListenerCookie.createInitial(actor.getGameProfile(), false), null)) {
                    var reloaded = readback.actor();
                    var source = P11NativeStorageBoundary.nativeSourceOwner(reloaded);
                    var body = source == null ? null : source.body(reloaded);
                    if (body == null || body.inputKind != P11QualifiedSourceOwner.InputKind.PRIMARY) {
                        throw new AssertionError("actual saved playerdata did not reload");
                    }
                    inspection.accept(reloaded);
                }
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
                if (current == currentActor) {
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
            var removing = currentActor;
            if (server.getPlayerList().getPlayer(actor.getUUID()) != removing) {
                throw new AssertionError("test-owned player is not the exact current player");
            }
            Throwable primary = null;
            try {
                server.getPlayerList().remove(removing);
            } catch (RuntimeException | Error failure) {
                primary = failure;
            }
            // Managed UNKNOWN is not a retry grant. Preserve the first original
            // failure and report remaining ownership instead of replaying remove.
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
            if (cleanupFailure == null && playerdata != null) {
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
            var old = directory.resolve(oldName);
            if (!artifacts.contains(primary) || artifacts.size() > 2
                    || artifacts.stream().anyMatch(path -> !path.equals(primary) && !path.equals(old))) {
                throw new AssertionError(
                        "actual player save did not publish the exact primary/optional native old pair: " + artifacts);
            }
            requireRegular(primary, "saved primary");
            if (artifacts.contains(old)) { requireRegular(old, "native previous primary"); }
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
            PlayerSkillAttachmentService attachments,
            SkillDefinitionStoreService store,
            P4E2QualificationFacade facade)
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
