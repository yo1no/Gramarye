package com.yo1no.gramarye;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.definition.validation.ValidatedActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeDefinition;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeReferenceProjection;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.definition.validation.ValidatedTriggerReferenceProjection;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual-platform S3 proof; this controlled source never registers a gameplay trigger. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class P8S3PresentationGameTests {
    private static final ResourceLocation ACTIVE_CAST = id("active_cast");
    private static final ResourceLocation CONTROLLED_HIT = id("p8_controlled_hit");
    private static final RuntimeServerToken SERVER_TOKEN = new RuntimeServerToken(83L);
    private static final P6RuntimeExecutionBridge.AppliedFact ONE_APPLIED =
            new P6RuntimeExecutionBridge.AppliedFact(
                    P6RuntimeExecutionBridge.AppliedTerminal.SUCCEEDED,
                    1,
                    List.of(new P6RuntimeExecutionBridge.AppliedStep(
                            0, P6RuntimeExecutionBridge.AppliedStepKind.APPLIED)));
    private static final P6RuntimeExecutionBridge.AppliedFact TWO_APPLIED =
            new P6RuntimeExecutionBridge.AppliedFact(
                    P6RuntimeExecutionBridge.AppliedTerminal.SUCCEEDED,
                    2,
                    List.of(
                            new P6RuntimeExecutionBridge.AppliedStep(
                                    0, P6RuntimeExecutionBridge.AppliedStepKind.APPLIED),
                            new P6RuntimeExecutionBridge.AppliedStep(
                                    1, P6RuntimeExecutionBridge.AppliedStepKind.APPLIED)));
    private static final TriggerType<FixtureTriggerPayload> TRIGGER_TYPE =
            new FixtureTriggerType();
    private static final ActionType<FixtureActionPayload> ACTION_TYPE =
            new FixtureActionType();

    private P8S3PresentationGameTests() {}

    @GameTest(
            batch = "p8_s3",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 80)
    @SuppressWarnings("removal")
    public static void appliedFactHandoffRollsBackFailuresAndDrainsCurrentTick(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var transport = new RecordingTransport(player.getUUID());
        var service = new P8ServerPresentationService(transport);
        try {
            service.startForTesting(server, emptyCatalog());
            long runtimeTick = server.getTickCount();
            RuntimeFixture prior = runtimeFixture(server, player, 1L, runtimeTick, ACTIVE_CAST);
            P6RuntimeExecutionCapability capability =
                    P6RuntimeExecutionCapability.forRuntimeAdapter();
            boolean[] invoked = {false};
            var adapter = new P6RuntimeExecutionPortAdapter(
                    capability,
                    service,
                    (actualCapability,
                                    actor,
                                    actionTypeKey,
                                    requestId,
                                    sourceEventId,
                                    targetId,
                                    magnitude,
                                    manaCost,
                                    guard,
                                    observer) -> {
                        helper.assertTrue(actualCapability == capability,
                                "adapter must hand the exact capability to the P6 bridge");
                        helper.assertTrue(observer != null,
                                "adapter must install the call-scoped applied-fact observer");
                        invoked[0] = true;
                        observer.observe(ONE_APPLIED);
                    },
                    (ignoredEvent, ignoredContext) -> Optional.empty());
            RuntimeExecutionBatch adapterBatch = adapter.executeMapped(
                    prior.event(),
                    prior.context(),
                    new P6RuntimeExecutionInput(
                            player,
                            id("p8_fixture_action"),
                            player.getUUID(),
                            1L,
                            0L));
            helper.assertTrue(invoked[0]
                            && adapterBatch.outcome() instanceof RuntimePortOutcome.Completed
                            && adapterBatch.children() == RuntimeChildPlan.EMPTY,
                    "adapter must complete with the exact empty child plan after observer handoff");
            helper.assertTrue(sequences(service).equals(List.of(1L)),
                    "adapter observer handoff must buffer the controlled prior entry");

            RuntimeFixture first = runtimeFixture(server, player, 2L, runtimeTick, ACTIVE_CAST);
            transport.failSecondCaptureWith(CaptureFailure.RUNTIME_EXCEPTION);
            new P8AppliedFactHandoff(service, first.event(), first.context())
                    .observe(TWO_APPLIED);
            helper.assertTrue(sequences(service).equals(List.of(1L)),
                    "observer RuntimeException must publish no call prefix and preserve prior state");
            helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 3L,
                    "two-step RuntimeException rollback must retain both allocated sequence gaps");
            helper.assertTrue(service.hasRuntimeDiagnosticForTesting(
                            P8ServerRuntimeDiagnosticCode.OBSERVER_RUNTIME_EXCEPTION),
                    "observer RuntimeException must record its fixed diagnostic code");

            RuntimeFixture second = runtimeFixture(server, player, 3L, runtimeTick, ACTIVE_CAST);
            transport.failSecondCaptureWith(CaptureFailure.ERROR);
            Error actual = null;
            try {
                new P8AppliedFactHandoff(service, second.event(), second.context())
                        .observe(TWO_APPLIED);
            } catch (Error failure) {
                actual = failure;
            }
            helper.assertTrue(actual == transport.observerError,
                    "observer Error must propagate as the same object");
            helper.assertTrue(sequences(service).equals(List.of(1L)),
                    "observer Error must publish no call prefix and preserve prior state");
            helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 5L,
                    "two-step Error rollback must retain both allocated sequence gaps");

            transport.clearCaptureFailure();
            RuntimeFixture third = runtimeFixture(server, player, 4L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(third.event(), third.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "first complete applied fact must retain one distinct event");
            helper.assertTrue(service.offerApplied(third.event(), third.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.DEGRADED,
                    "same-tick exact identity must coalesce to the earliest event");
            List<PresentationEvent> retained = service.bufferedEventsForTesting();
            helper.assertTrue(retained.size() == 2
                            && retained.getFirst().sequence() == 1L
                            && retained.get(1).sequence() == 6L
                            && service.presentationSequenceHighWaterForTesting() == 7L,
                    "coalescing must preserve prior state, retain the earliest duplicate, and consume the later gap");

            var replacement = P8ServerPresentationService.newReloadIdentityForTesting();
            service.stageCandidateForTesting(replacement, emptyCatalog());
            helper.assertTrue(service.activateCandidateForTesting(replacement),
                    "controlled reload candidate must activate");
            helper.assertTrue(service.bufferedEventsForTesting().isEmpty()
                            && service.presentationSequenceHighWaterForTesting() == 7L,
                    "activation must clear old-generation events without resetting sequence");

            RuntimeFixture fourth = runtimeFixture(server, player, 5L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(fourth.event(), fourth.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "new generation must admit the next sequence");
            RuntimeFixture fifth = runtimeFixture(server, player, 6L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(fifth.event(), fifth.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "new generation must admit a second distinct sequence");
            RuntimeFixture sixth = runtimeFixture(server, player, 7L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(sixth.event(), sixth.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "new generation must admit a third distinct sequence");
            transport.submitModes = List.of(
                    SubmitMode.UNAVAILABLE,
                    SubmitMode.RUNTIME_EXCEPTION,
                    SubmitMode.ERROR);
            Error submitFailure = null;
            try {
                service.drainPresentationForTesting(server);
            } catch (Error failure) {
                submitFailure = failure;
            }
            helper.assertTrue(submitFailure == transport.submitError,
                    "transport Error must propagate as the same object after buffer expiry");
            helper.assertTrue(service.bufferedEventsForTesting().isEmpty()
                            && transport.submitAttempts.equals(List.of(8L, 9L, 10L))
                            && transport.submittedSequences.isEmpty(),
                    "drain must not retry UNAVAILABLE, must isolate RuntimeException, and must stop at the same Error");
            helper.assertTrue(service.hasRuntimeDiagnosticForTesting(
                            P8ServerRuntimeDiagnosticCode.EVENT_TRANSPORT_RUNTIME_EXCEPTION),
                    "isolated transport RuntimeException must record its fixed diagnostic code");
            RuntimeFixture postDrain = runtimeFixture(
                    server, player, 8L, runtimeTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(
                                    postDrain.event(), postDrain.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.DROPPED,
                    "a same-physical-tick offer after drain must be dropped without mutation");
            helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 10L,
                    "post-drain rejection must not allocate another presentation sequence");
            RuntimeFixture olderTick = runtimeFixture(
                    server,
                    player,
                    9L,
                    Math.subtractExact(runtimeTick, 1L),
                    ACTIVE_CAST);
            RuntimeException regressionFailure = null;
            try {
                service.offerApplied(
                        olderTick.event(), olderTick.context(), ONE_APPLIED);
            } catch (RuntimeException failure) {
                regressionFailure = failure;
            }
            helper.assertTrue(regressionFailure instanceof IllegalArgumentException
                            && service.presentationSequenceHighWaterForTesting() == 10L,
                    "an older runtime tick after drain must be rejected as regression before allocation");
            RuntimeException duplicateDrain = null;
            try {
                service.drainPresentationForTesting(server);
            } catch (RuntimeException failure) {
                duplicateDrain = failure;
            }
            helper.assertTrue(duplicateDrain instanceof IllegalStateException
                            && service.bufferedEventsForTesting().isEmpty(),
                    "a duplicate physical-tick drain must fail closed without resurrecting work");
            assertOfferReentryGuards(helper, server, player, runtimeTick);
        } finally {
            service.stopForTesting();
            server.getPlayerList().remove(player);
        }
        helper.assertTrue(service.presentationSequenceHighWaterForTesting() == 0L
                        && service.bufferedEventsForTesting().isEmpty(),
                "stop must clear sequence and current-tick presentation state");
        helper.succeed();
    }

    @GameTest(
            batch = "p8_s3",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 80)
    @SuppressWarnings("removal")
    public static void hitRecipientsUseActualWatchersAndRevalidateCurrentIdentity(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel targetLevel = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        Vec3 playerPosition = player.position();
        Entity origin = new ArmorStand(
                targetLevel, playerPosition.x, playerPosition.y, playerPosition.z);
        Entity target = new ArmorStand(
                targetLevel, playerPosition.x + 2.0D, playerPosition.y, playerPosition.z);
        boolean originAdded = targetLevel.addFreshEntity(origin);
        boolean targetAdded = targetLevel.addFreshEntity(target);
        var transport = new RecordingTransport(player.getUUID());
        var service = new P8ServerPresentationService(transport);
        service.startForTesting(server, emptyCatalog());
        helper.runAfterDelay(10L, () -> {
            boolean passed = false;
            String failure = "P8 HIT watcher integration did not complete";
            try {
                targetLevel.getChunkSource().move(player);
                var watchers = targetLevel
                        .getChunkSource()
                        .chunkMap
                        .getPlayersWatching(target);
                if (!originAdded || !targetAdded) {
                    failure = "actual server level did not accept the controlled entities";
                } else if (!watchers.contains(player)) {
                    failure = "actual ChunkMap watcher path did not contain the current player";
                } else {
                    long runtimeTick = server.getTickCount();
                    RuntimeFixture hit = runtimeFixture(
                            server,
                            player,
                            origin,
                            target,
                            1L,
                            runtimeTick,
                            CONTROLLED_HIT);
                    P8PresentationOfferOutcome outcome = service.offerApplied(
                            hit.event(), hit.context(), ONE_APPLIED);
                    List<P8BufferedPresentation> buffered =
                            service.bufferedPresentationsForTesting();
                    if (outcome != P8PresentationOfferOutcome.ACCEPTED
                            || buffered.size() != 1
                            || buffered.getFirst().authoritativeTick() != runtimeTick
                            || buffered.getFirst().sourceEventId() != 1L
                            || buffered.getFirst().appliedStepIndex() != 0
                            || buffered.getFirst().event().kind() != PresentationEventKind.HIT
                            || buffered.getFirst().event().sourceSummary().targetEntityId().isEmpty()
                            || buffered.getFirst().event().sourceSummary().targetEntityId()
                                    .orElseThrow() != target.getId()
                            || buffered.getFirst().targetEntityUuid().isEmpty()
                            || !buffered.getFirst().targetEntityUuid().orElseThrow()
                                    .equals(target.getUUID())
                            || buffered.getFirst().recipients().size() != 1
                            || buffered.getFirst().recipients().getFirst().category()
                                    != PresentationOrdering.RecipientCategory.ORDINARY) {
                        failure = "actual offerApplied HIT path did not retain the exact applied-step identity";
                    } else {
                        P8BufferedPresentation retained = buffered.getFirst();
                        P8SelectedRecipient selected = retained.recipients().getFirst();
                        if (!P8RecipientSelector.remainsEligible(
                                server, retained, selected, transport)) {
                            failure = "current ready watcher was not eligible before drain";
                        } else {
                            transport.advanceConnectionEpoch();
                            if (P8RecipientSelector.remainsEligible(
                                    server, retained, selected, transport)) {
                                failure = "drain eligibility accepted a stale connection epoch";
                            } else {
                                transport.restoreConnectionEpoch();
                                target.discard();
                                service.drainPresentationForTesting(server);
                                passed = service.bufferedEventsForTesting().isEmpty()
                                        && transport.submitAttempts.isEmpty();
                                failure = "post-tick drain did not revalidate the removed watched target";
                            }
                        }
                    }
                }
            } finally {
                service.stopForTesting();
                origin.discard();
                target.discard();
                server.getPlayerList().remove(player);
            }
            if (passed) {
                helper.succeed();
            } else {
                helper.fail(failure);
            }
        });
    }

    @GameTest(
            batch = "p8_s4",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 80)
    @SuppressWarnings("removal")
    public static void productionTransportEncodesCatalogBeforePresentationEvent(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Connection connection = player.connection.getConnection();
        EmbeddedChannel channel = (EmbeddedChannel) connection.channel();
        var service = P8ServerPresentationService.create();
        try {
            NetworkRegistry.configureMockConnection(connection);
            Connection.configureInMemoryPipeline(channel.pipeline(), PacketFlow.SERVERBOUND);
            ProtocolInfo<ClientGamePacketListener> playProtocol =
                    GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                            RegistryFriendlyByteBuf.decorator(
                                    player.registryAccess(),
                                    NetworkRegistry.getConnectionType(connection)));
            connection.setupOutboundProtocol(playProtocol);
            releaseOutbound(channel);

            service.startForTesting(server, emptyCatalog());
            helper.assertTrue(service.openConnectionForTesting(player).isPresent(),
                    "current player must receive one P8 connection epoch");
            long unreadyTick = server.getTickCount();
            RuntimeFixture unready = runtimeFixture(
                    server, player, 1L, unreadyTick, ACTIVE_CAST);
            helper.assertTrue(service.offerApplied(
                                    unready.event(), unready.context(), ONE_APPLIED)
                            == P8PresentationOfferOutcome.DROPPED
                            && service.bufferedEventsForTesting().isEmpty(),
                    "an unready production recipient must be dropped before buffering");
            var initialCatalogDrain = service.drainPresentationForTesting(server);

            EncodedPayload catalog = readOnlyP8Payload(channel, playProtocol);
            helper.assertTrue(catalog.payload() instanceof ProfileCatalogPayload payload
                            && payload.catalogGeneration() == 1L
                            && payload.bodySize()
                                    == service.activeCatalogBodyBytesForTesting()
                            && catalog.packetBytes()
                                    == payload.bodySize()
                                            + PresentationLimits
                                                    .PROFILE_CATALOG_PACKET_OVERHEAD_BYTES
                            && initialCatalogDrain.submissions() == 1
                            && initialCatalogDrain.chargedBytes() == catalog.packetBytes()
                            && service.connectionReadyForTesting(player.getUUID()),
                    "actual PLAY encoder submission must publish and charge the catalog first");

            helper.runAfterDelay(1L, () -> {
                try {
                    releaseOutbound(channel);
                    var noReplayDrain = service.drainPresentationForTesting(server);
                    helper.assertTrue(noReplayDrain.submissions() == 0
                                    && noReplayDrain.chargedBytes() == 0L
                                    && readOnlyP8Payloads(channel, playProtocol).isEmpty(),
                            "the unready event must not replay after catalog readiness");
                } catch (RuntimeException | Error failure) {
                    service.stopForTesting();
                    server.getPlayerList().remove(player);
                    channel.finishAndReleaseAll();
                    throw failure;
                }

                helper.runAfterDelay(1L, () -> {
                    boolean passed = false;
                    String failure = "P8 production event submission did not complete";
                    try {
                        releaseOutbound(channel);
                        long runtimeTick = server.getTickCount();
                        RuntimeFixture event = runtimeFixture(
                                server, player, 2L, runtimeTick, ACTIVE_CAST);
                        if (service.offerApplied(event.event(), event.context(), ONE_APPLIED)
                                != P8PresentationOfferOutcome.ACCEPTED) {
                            failure = "actual S3 offer did not enter the production transport";
                        } else {
                            service.drainPresentationForTesting(server);
                            EncodedPayload encodedEvent = readOnlyP8Payload(channel, playProtocol);
                            if (!(encodedEvent.payload()
                                    instanceof PresentationEventPayload payload)) {
                                failure = "actual second submission was not the P8 event payload";
                            } else if (payload.catalogGeneration() != 1L
                                    || payload.sequence() != 2L
                                    || encodedEvent.packetBytes()
                                            != payload.bodySize()
                                                    + PresentationLimits
                                                            .EVENT_PACKET_OVERHEAD_BYTES) {
                                failure = "actual event payload lost its generation, sequence, or charge";
                            } else {
                                var maximumCatalog = maximumConstructibleCatalogPayload();
                                int measuredMaximumCatalog =
                                        P8PacketSubmission.measureClientboundPlayPacket(
                                                player,
                                                maximumCatalog,
                                                PresentationLimits
                                                        .MAX_PROFILE_CATALOG_PACKET_CHARGE_BYTES);
                                P8PacketSubmission.send(player, maximumCatalog);
                                var encodedMaximumCatalog =
                                        readOnlyP8Payload(channel, playProtocol);
                                var maximumEvent = maximumLegalEventPayload();
                                int measuredMaximumEvent =
                                        P8PacketSubmission.measureClientboundPlayPacket(
                                                player,
                                                maximumEvent,
                                                PresentationLimits.MAX_EVENT_PACKET_CHARGE_BYTES);
                                P8PacketSubmission.send(player, maximumEvent);
                                var encodedMaximumEvent = readOnlyP8Payload(channel, playProtocol);
                                passed = maximumCatalog.entries().size()
                                                == PresentationLimits.MAX_PROFILE_INSTANCES
                                        && maximumCatalog.bodySize()
                                                <= PresentationLimits
                                                        .MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES
                                        && measuredMaximumCatalog
                                                == maximumCatalog.bodySize()
                                                        + PresentationLimits
                                                                .PROFILE_CATALOG_PACKET_OVERHEAD_BYTES
                                        && encodedMaximumCatalog.packetBytes()
                                                == measuredMaximumCatalog
                                        && encodedMaximumCatalog.payload()
                                                instanceof ProfileCatalogPayload
                                        && maximumEvent.bodySize()
                                                == PresentationLimits.MAX_LEGAL_EVENT_BODY_BYTES
                                        && measuredMaximumEvent == 926
                                        && encodedMaximumEvent.packetBytes()
                                                == measuredMaximumEvent
                                        && encodedMaximumEvent.payload()
                                                instanceof PresentationEventPayload;
                                if (!passed) {
                                    failure = "maximum legal codec layouts disagreed with actual "
                                            + "PLAY PacketEncoder charges";
                                }
                            }
                        }
                    } finally {
                        service.stopForTesting();
                        server.getPlayerList().remove(player);
                        channel.finishAndReleaseAll();
                    }
                    if (passed) {
                        helper.succeed();
                    } else {
                        helper.fail(failure);
                    }
                });
            });
        } catch (RuntimeException | Error failure) {
            service.stopForTesting();
            server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    @GameTest(
            batch = "p8_s4",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 80)
    @SuppressWarnings("removal")
    public static void catalogFailurePoliciesUseActualDrainAndBoundedAccounting(
            GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerPlayer secondPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer thirdPlayer = helper.makeMockServerPlayerInLevel();
        var players = List.of(player, secondPlayer, thirdPlayer);
        players.forEach(value ->
                NetworkRegistry.configureMockConnection(value.connection.getConnection()));

        var sendOrder = new ArrayList<String>();
        var retryEvents = new RecordingTransport(player.getUUID(), sendOrder);
        var retryCatalog = new ScriptedCatalogTransport(
                Set.of(),
                sendOrder,
                CatalogSubmitMode.READY,
                CatalogSubmitMode.RUNTIME_EXCEPTION,
                CatalogSubmitMode.READY);
        var retryService = new P8ServerPresentationService(retryEvents, retryCatalog);
        var terminalCatalog = new ScriptedCatalogTransport(
                Set.of(), new ArrayList<>(), CatalogSubmitMode.RUNTIME_EXCEPTION,
                CatalogSubmitMode.RUNTIME_EXCEPTION);
        var terminalService = new P8ServerPresentationService(
                UnavailableP8PresentationTransport.INSTANCE, terminalCatalog);
        var errorEvents = new RecordingTransport(player.getUUID());
        var errorCatalog = new ScriptedCatalogTransport(
                Set.of(),
                new ArrayList<>(),
                CatalogSubmitMode.READY,
                CatalogSubmitMode.ERROR);
        var errorService = new P8ServerPresentationService(errorEvents, errorCatalog);

        var orderedPlayers = new ArrayList<>(players);
        orderedPlayers.sort((left, right) ->
                compareUnsignedUuid(left.getUUID(), right.getUUID()));
        UUID unavailablePlayerId = orderedPlayers.getFirst().getUUID();
        var unavailableCatalog = new ScriptedCatalogTransport(
                Set.of(unavailablePlayerId),
                new ArrayList<>(),
                CatalogSubmitMode.READY,
                CatalogSubmitMode.READY);
        var unavailableService = new P8ServerPresentationService(
                UnavailableP8PresentationTransport.INSTANCE, unavailableCatalog);
        var services = List.of(
                retryService, terminalService, errorService, unavailableService);

        try {
            services.forEach(value -> value.startForTesting(server, emptyCatalog()));
            helper.assertTrue(retryService.openConnectionForTesting(player).isPresent()
                            && terminalService.openConnectionForTesting(player).isPresent()
                            && errorService.openConnectionForTesting(player).isPresent(),
                    "each service must open its exact initial current connection");
            for (ServerPlayer current : orderedPlayers) {
                helper.assertTrue(unavailableService.openConnectionForTesting(current).isPresent(),
                        "the bounded-preflight service must open every current connection");
            }

            int packetCharge = Math.addExact(
                    retryService.activeCatalogBodyBytesForTesting(),
                    PresentationLimits.PROFILE_CATALOG_PACKET_OVERHEAD_BYTES);
            var retryInitial = retryService.drainPresentationForTesting(server);
            var errorInitial = errorService.drainPresentationForTesting(server);
            var terminalFirst = terminalService.drainPresentationForTesting(server);
            var unavailableFirst = unavailableService.drainPresentationForTesting(server);
            helper.assertTrue(retryInitial.submissions() == 1
                            && retryInitial.chargedBytes() == packetCharge
                            && retryService.connectionAttemptsForTesting(player.getUUID()) == 1
                            && retryService.connectionReadyForTesting(player.getUUID())
                            && errorInitial.submissions() == 1
                            && errorInitial.chargedBytes() == packetCharge
                            && errorService.connectionReadyForTesting(player.getUUID())
                            && terminalFirst.submissions() == 1
                            && terminalFirst.chargedBytes() == packetCharge
                            && terminalService.connectionAttemptsForTesting(player.getUUID()) == 1
                            && !terminalService.connectionReadyForTesting(player.getUUID()),
                    "initial catalog drains must establish real readiness and the one retry key");
            helper.assertTrue(unavailableFirst.submissions()
                                    == PresentationLimits
                                            .MAX_CATALOG_SUBMISSIONS_PER_SERVER_TICK
                            && unavailableFirst.chargedBytes()
                                    == Math.multiplyExact(2L, packetCharge)
                            && unavailableService.connectionAttemptsForTesting(
                                            unavailablePlayerId)
                                    == 0
                            && !unavailableService.connectionReadyForTesting(
                                    unavailablePlayerId)
                            && unavailableCatalog.canSubmitCalls == 3
                            && unavailableCatalog.packetChargeCalls == 2
                            && unavailableCatalog.submissionCount == 2
                            && orderedPlayers.stream()
                                    .skip(1L)
                                    .allMatch(current -> unavailableService
                                                    .connectionAttemptsForTesting(
                                                            current.getUUID())
                                            == 1
                                            && unavailableService.connectionReadyForTesting(
                                                    current.getUUID())),
                    "terminal preflight refusal must consume no attempt and must not starve later keys");
            sendOrder.clear();

            helper.runAfterDelay(1L, () -> {
                try {
                    helper.assertTrue(retryService.openConnectionForTesting(secondPlayer).isPresent()
                                    && errorService.openConnectionForTesting(thirdPlayer).isPresent()
                                    && retryService.connectionReadyForTesting(player.getUUID())
                                    && errorService.connectionReadyForTesting(player.getUUID()),
                            "later catalog keys must not revoke the already-ready event player");
                    long runtimeTick = server.getTickCount();
                    RuntimeFixture orderedEvent = runtimeFixture(
                            server, player, 20L, runtimeTick, ACTIVE_CAST);
                    RuntimeFixture errorEvent = runtimeFixture(
                            server, player, 21L, runtimeTick, ACTIVE_CAST);
                    helper.assertTrue(retryService.offerApplied(
                                            orderedEvent.event(),
                                            orderedEvent.context(),
                                            ONE_APPLIED)
                                    == P8PresentationOfferOutcome.ACCEPTED
                                    && errorService.offerApplied(
                                                    errorEvent.event(),
                                                    errorEvent.context(),
                                                    ONE_APPLIED)
                                            == P8PresentationOfferOutcome.ACCEPTED,
                            "ready event players must retain both controlled current-tick events");

                    var retryFailure = retryService.drainPresentationForTesting(server);
                    var terminalSecond = terminalService.drainPresentationForTesting(server);
                    var unavailableLater = unavailableService.drainPresentationForTesting(server);
                    Error actualCatalogError = null;
                    try {
                        errorService.drainPresentationForTesting(server);
                    } catch (Error failure) {
                        actualCatalogError = failure;
                    }
                    helper.assertTrue(retryFailure.submissions() == 1
                                    && retryFailure.chargedBytes() == packetCharge
                                    && retryService.connectionAttemptsForTesting(
                                                    secondPlayer.getUUID())
                                            == 1
                                    && !retryService.connectionReadyForTesting(
                                            secondPlayer.getUUID())
                                    && retryService.connectionReadyForTesting(player.getUUID())
                                    && retryService.bufferedEventsForTesting().isEmpty()
                                    && sendOrder.equals(List.of("catalog", "event"))
                                    && retryEvents.submittedSequences.equals(List.of(1L))
                                    && retryService.hasRuntimeDiagnosticForTesting(
                                            P8ServerRuntimeDiagnosticCode
                                                    .CATALOG_TRANSPORT_RUNTIME_EXCEPTION)
                                    && terminalSecond.submissions() == 1
                                    && terminalSecond.chargedBytes() == packetCharge
                                    && terminalService.connectionAttemptsForTesting(
                                                    player.getUUID())
                                            == PresentationLimits
                                                    .MAX_CATALOG_SUBMISSION_ATTEMPTS
                                    && !terminalService.connectionReadyForTesting(
                                            player.getUUID())
                                    && actualCatalogError == errorCatalog.error
                                    && errorService.bufferedEventsForTesting().isEmpty()
                                    && errorService.connectionAttemptsForTesting(
                                                    thirdPlayer.getUUID())
                                            == 1
                                    && !errorService.connectionReadyForTesting(
                                            thirdPlayer.getUUID())
                                    && errorService.connectionReadyForTesting(player.getUUID())
                                    && errorEvents.submitAttempts.isEmpty()
                                    && unavailableLater.submissions() == 0
                                    && unavailableLater.chargedBytes() == 0L
                                    && unavailableCatalog.canSubmitCalls == 3
                                    && unavailableCatalog.submissionCount == 2,
                            "actual drain must charge the failure, send catalogs first, and clean Error ownership");
                } catch (RuntimeException | Error failure) {
                    stopServicesAndRemovePlayers(server, services, players);
                    throw failure;
                }

                helper.runAfterDelay(1L, () -> {
                    try {
                        var retrySuccess = retryService.drainPresentationForTesting(server);
                        var failedTerminal = terminalService.drainPresentationForTesting(server);
                        var errorTerminal = errorService.drainPresentationForTesting(server);
                        helper.assertTrue(retrySuccess.submissions() == 1
                                        && retrySuccess.chargedBytes() == packetCharge
                                        && retryService.connectionAttemptsForTesting(
                                                        secondPlayer.getUUID())
                                                == PresentationLimits
                                                        .MAX_CATALOG_SUBMISSION_ATTEMPTS
                                        && retryService.connectionReadyForTesting(
                                                secondPlayer.getUUID())
                                        && failedTerminal.submissions() == 0
                                        && failedTerminal.chargedBytes() == 0L
                                        && errorTerminal.submissions() == 0
                                        && errorTerminal.chargedBytes() == 0L
                                        && retryCatalog.submissionCount == 3
                                        && terminalCatalog.submissionCount
                                                == PresentationLimits
                                                        .MAX_CATALOG_SUBMISSION_ATTEMPTS
                                        && errorCatalog.submissionCount == 2,
                                "next-tick success and terminal failures must leave every key bounded");
                        retryEvents.submitAttempts.clear();
                        retryEvents.submittedSequences.clear();
                        retryEvents.submitModes = List.of(
                                SubmitMode.RUNTIME_EXCEPTION, SubmitMode.NULL_RESULT);
                    } catch (RuntimeException | Error failure) {
                        stopServicesAndRemovePlayers(server, services, players);
                        throw failure;
                    }

                    helper.runAfterDelay(1L, () -> {
                        try {
                            long eventTick = server.getTickCount();
                            RuntimeFixture runtimeFailureEvent = runtimeFixture(
                                    server, player, 22L, eventTick, ACTIVE_CAST);
                            RuntimeFixture nullResultEvent = runtimeFixture(
                                    server, player, 23L, eventTick, ACTIVE_CAST);
                            helper.assertTrue(retryService.offerApplied(
                                                    runtimeFailureEvent.event(),
                                                    runtimeFailureEvent.context(),
                                                    ONE_APPLIED)
                                            == P8PresentationOfferOutcome.ACCEPTED
                                            && retryService.offerApplied(
                                                            nullResultEvent.event(),
                                                            nullResultEvent.context(),
                                                            ONE_APPLIED)
                                                    == P8PresentationOfferOutcome.ACCEPTED,
                                    "the ready service must retain both event-failure fixtures");
                            NullPointerException nullInvariant = null;
                            try {
                                retryService.drainPresentationForTesting(server);
                            } catch (NullPointerException failure) {
                                nullInvariant = failure;
                            }
                            helper.assertTrue(nullInvariant != null
                                            && retryEvents.submitAttempts.equals(List.of(2L, 3L))
                                            && retryEvents.submittedSequences.isEmpty()
                                            && retryService.bufferedEventsForTesting().isEmpty()
                                            && retryService.connectionReadyForTesting(
                                                    player.getUUID())
                                            && retryService.connectionReadyForTesting(
                                                    secondPlayer.getUUID())
                                            && retryService.hasRuntimeDiagnosticForTesting(
                                                    P8ServerRuntimeDiagnosticCode
                                                            .EVENT_TRANSPORT_RUNTIME_EXCEPTION),
                                    "event RuntimeException must drop one delivery and null must stay an invariant");
                            retryEvents.submitAttempts.clear();
                            retryEvents.submittedSequences.clear();
                            retryEvents.submitModes = List.of(SubmitMode.ERROR);
                        } catch (RuntimeException | Error failure) {
                            stopServicesAndRemovePlayers(server, services, players);
                            throw failure;
                        }

                        helper.runAfterDelay(1L, () -> {
                            try {
                                long eventTick = server.getTickCount();
                                RuntimeFixture errorEvent = runtimeFixture(
                                        server, player, 24L, eventTick, ACTIVE_CAST);
                                helper.assertTrue(retryService.offerApplied(
                                                        errorEvent.event(),
                                                        errorEvent.context(),
                                                        ONE_APPLIED)
                                                == P8PresentationOfferOutcome.ACCEPTED,
                                        "the event Error fixture must be retained while ready");
                                Error actualEventError = null;
                                try {
                                    retryService.drainPresentationForTesting(server);
                                } catch (Error failure) {
                                    actualEventError = failure;
                                }
                                helper.assertTrue(actualEventError == retryEvents.submitError
                                                && retryEvents.submitAttempts.equals(List.of(4L))
                                                && retryService.bufferedEventsForTesting().isEmpty()
                                                && retryService.connectionReadyForTesting(
                                                        player.getUUID())
                                                && retryService.connectionReadyForTesting(
                                                        secondPlayer.getUUID()),
                                        "event Error must propagate itself without revoking readiness");
                            } finally {
                                stopServicesAndRemovePlayers(server, services, players);
                            }
                            helper.succeed();
                        });
                    });
                });
            });
        } catch (RuntimeException | Error failure) {
            stopServicesAndRemovePlayers(server, services, players);
            throw failure;
        }
    }

    private static EncodedPayload readOnlyP8Payload(
            EmbeddedChannel channel,
            ProtocolInfo<ClientGamePacketListener> playProtocol) {
        var payloads = readOnlyP8Payloads(channel, playProtocol);
        if (payloads.size() != 1) {
            throw new AssertionError(
                    "expected exactly one encoded P8 payload; observed=" + payloads.size());
        }
        return payloads.getFirst();
    }

    private static List<EncodedPayload> readOnlyP8Payloads(
            EmbeddedChannel channel,
            ProtocolInfo<ClientGamePacketListener> playProtocol) {
        channel.runPendingTasks();
        channel.flushOutbound();
        channel.runPendingTasks();
        Object outbound;
        var found = new ArrayList<EncodedPayload>();
        while ((outbound = channel.readOutbound()) != null) {
            try {
                if (!(outbound instanceof ByteBuf encoded)) {
                    continue;
                }
                int packetBytes = encoded.readableBytes();
                if (packetBytes == 0 || encoded.getUnsignedByte(encoded.readerIndex()) != 25) {
                    continue;
                }
                var packet = playProtocol.codec().decode(encoded);
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && (custom.payload() instanceof ProfileCatalogPayload
                                || custom.payload() instanceof PresentationEventPayload)) {
                    found.add(new EncodedPayload(packetBytes, custom.payload()));
                }
            } finally {
                ReferenceCountUtil.release(outbound);
            }
        }
        return List.copyOf(found);
    }

    private static void releaseOutbound(EmbeddedChannel channel) {
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(outbound);
        }
    }

    private static int compareUnsignedUuid(UUID left, UUID right) {
        int comparison = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return comparison != 0
                ? comparison
                : Long.compareUnsigned(
                        left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    private static void stopServicesAndRemovePlayers(
            MinecraftServer server,
            List<P8ServerPresentationService> services,
            List<ServerPlayer> players) {
        services.forEach(P8ServerPresentationService::stopForTesting);
        players.forEach(server.getPlayerList()::remove);
    }

    private static void assertOfferReentryGuards(
            GameTestHelper helper,
            MinecraftServer server,
            ServerPlayer player,
            long runtimeTick) {
        RuntimeFixture isCurrentFixture = runtimeFixture(
                server, player, 10L, runtimeTick, ACTIVE_CAST);
        var isCurrentTransport = new RecordingTransport(player.getUUID());
        var isCurrentService = new P8ServerPresentationService(isCurrentTransport);
        try {
            isCurrentService.startForTesting(server, emptyCatalog());
            helper.assertTrue(isCurrentService.offerApplied(
                                    isCurrentFixture.event(),
                                    isCurrentFixture.context(),
                                    ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "isCurrent reentry fixture must retain one delivery");
            isCurrentTransport.reenterOnNextIsCurrent(() -> isCurrentService.offerApplied(
                    isCurrentFixture.event(), isCurrentFixture.context(), ONE_APPLIED));
            RuntimeException isCurrentFailure = null;
            try {
                isCurrentService.drainPresentationForTesting(server);
            } catch (RuntimeException failure) {
                isCurrentFailure = failure;
            }
            helper.assertTrue(isCurrentFailure instanceof IllegalStateException
                            && isCurrentService.bufferedEventsForTesting().isEmpty(),
                    "synchronous isCurrent offer reentry must fail the invariant after buffer expiry");
        } finally {
            isCurrentService.stopForTesting();
        }

        RuntimeFixture submitFixture = runtimeFixture(
                server, player, 11L, runtimeTick, ACTIVE_CAST);
        var submitTransport = new RecordingTransport(player.getUUID());
        var submitService = new P8ServerPresentationService(submitTransport);
        try {
            submitService.startForTesting(server, emptyCatalog());
            helper.assertTrue(submitService.offerApplied(
                                    submitFixture.event(),
                                    submitFixture.context(),
                                    ONE_APPLIED)
                            == P8PresentationOfferOutcome.ACCEPTED,
                    "submit reentry fixture must retain one delivery");
            RuntimeException[] observedSubmitReentry = {null};
            submitTransport.reenterOnNextSubmit(() -> {
                try {
                    submitService.offerApplied(
                            submitFixture.event(), submitFixture.context(), ONE_APPLIED);
                } catch (RuntimeException failure) {
                    observedSubmitReentry[0] = failure;
                    throw failure;
                }
            });
            submitService.drainPresentationForTesting(server);
            helper.assertTrue(observedSubmitReentry[0] instanceof IllegalStateException
                            && submitService.bufferedEventsForTesting().isEmpty()
                            && submitTransport.submitAttempts.equals(List.of(1L))
                            && submitTransport.submittedSequences.isEmpty()
                            && submitService.hasRuntimeDiagnosticForTesting(
                                    P8ServerRuntimeDiagnosticCode.EVENT_TRANSPORT_RUNTIME_EXCEPTION),
                    "synchronous submit offer reentry must fail the invariant and be isolated by transport policy");
        } finally {
            submitService.stopForTesting();
        }
    }

    private static RuntimeFixture runtimeFixture(
            MinecraftServer server,
            ServerPlayer player,
            long eventId,
            long runtimeTick,
            ResourceLocation triggerKey) {
        return runtimeFixture(
                server,
                player,
                new ResolvedPlayerOrigin(player),
                NoResolvedRuntimeTarget.INSTANCE,
                eventId,
                runtimeTick,
                triggerKey);
    }

    private static RuntimeFixture runtimeFixture(
            MinecraftServer server,
            ServerPlayer player,
            Entity origin,
            Entity target,
            long eventId,
            long runtimeTick,
            ResourceLocation triggerKey) {
        return runtimeFixture(
                server,
                player,
                new ResolvedEntityOrigin(origin),
                new ResolvedEntityTarget(target),
                eventId,
                runtimeTick,
                triggerKey);
    }

    private static RuntimeFixture runtimeFixture(
            MinecraftServer server,
            ServerPlayer player,
            ResolvedRuntimeOrigin origin,
            ResolvedRuntimeTarget target,
            long eventId,
            long runtimeTick,
            ResourceLocation triggerKey) {
        SkillReference reference = new SkillReference(
                new SkillId(new UUID(0x8300000000004000L, eventId)),
                new SkillRevision(1));
        ValidatedNodeDefinition node = validatedNode();
        ValidatedSkillDefinition definition = construct(
                ValidatedSkillDefinition.class,
                new Class<?>[] {SkillReference.class, List.class, RuntimeNeutralAppearance.class},
                reference,
                List.of(node),
                RuntimeNeutralAppearance.Default.INSTANCE);
        var playerId = new RuntimePlayerId(player.getUUID());
        var instanceId = new SkillInstanceId(new UUID(0x8300000000005000L, eventId));
        var event = new RuntimeEvent(
                new EventId(eventId),
                instanceId,
                new RuntimeSkillInstanceSequence(eventId),
                new RuntimeCancellationToken(SERVER_TOKEN, instanceId),
                Optional.empty(),
                reference,
                0,
                runtimeTick,
                runtimeTick,
                Math.addExact(runtimeTick, 10L),
                0,
                0,
                RuntimeSchedulePersistence.MEMORY_ONLY,
                new PlayerRuntimeBudgetAttribution(SERVER_TOKEN, playerId),
                new PlayerOrigin(SERVER_TOKEN, player.serverLevel().dimension(), playerId),
                Optional.empty(),
                new RootTriggerCause(new TriggerEventKind(triggerKey)),
                NoRuntimeExecutionData.INSTANCE);
        var context = new RuntimeExecutionContext(
                server,
                definition,
                node,
                runtimeTick,
                SERVER_TOKEN,
                new ResolvedRuntimeReferenceContext(origin, target),
                new RuntimeExecutionBudget(0, 0, 0, 0, 0, 0, 0, 0, 0),
                () -> RuntimeExecutionGuardDecision.ALLOWED);
        return new RuntimeFixture(event, context);
    }

    private static List<Long> sequences(P8ServerPresentationService service) {
        return service.bufferedEventsForTesting().stream()
                .map(PresentationEvent::sequence)
                .toList();
    }

    private static ValidatedNodeDefinition validatedNode() {
        var trigger = new ResolvedTriggerDefinition<>(
                TRIGGER_TYPE, 0, new FixtureTriggerPayload(0));
        var action = new ResolvedActionDefinition<>(
                ACTION_TYPE, 0, new FixtureActionPayload(0));
        var triggerReferences = construct(
                ValidatedTriggerReferenceProjection.class,
                new Class<?>[] {
                    SourceSelection.class, TargetSelection.class, boolean.class, List.class
                },
                SourceSelection.NONE,
                TargetSelection.NONE,
                false,
                List.of());
        var actionReferences = construct(
                ValidatedActionReferenceProjection.class,
                new Class<?>[] {
                    SourceSelection.class, TargetSelection.class, List.class, Set.class
                },
                SourceSelection.NONE,
                TargetSelection.NONE,
                List.of(),
                Set.of());
        var references = construct(
                ValidatedNodeReferenceProjection.class,
                new Class<?>[] {
                    ValidatedTriggerReferenceProjection.class,
                    ValidatedActionReferenceProjection.class
                },
                triggerReferences,
                actionReferences);
        return construct(
                ValidatedNodeDefinition.class,
                new Class<?>[] {
                    int.class,
                    ResolvedTriggerDefinition.class,
                    ResolvedActionDefinition.class,
                    ValidatedNodeReferenceProjection.class,
                    RuntimeNeutralAppearanceOverride.class
                },
                0,
                trigger,
                action,
                references,
                RuntimeNeutralAppearanceOverride.None.INSTANCE);
    }

    private static P8ServerPresentationService.PreparedCatalog emptyCatalog() {
        return P8ServerPresentationService.loadCandidateForTesting(
                MagicRegistries.profileTypeRegistry(), Map.of());
    }

    private static ProfileCatalogPayload maximumConstructibleCatalogPayload() {
        var entries = new ArrayList<P8ProfileCatalogEntry>(
                PresentationLimits.MAX_PROFILE_INSTANCES);
        entries.add(new P8ProfileCatalogEntry(
                id("default_particle"),
                id("particle"),
                com.yo1no.gramarye.magic.presentation.api.ProfileChannel.PARTICLE,
                id("particle"),
                0,
                "{\"count\":8,\"lifetime_ticks\":20,\"particle\":"
                        + "\"minecraft:enchant\",\"size_milli_blocks\":250,"
                        + "\"speed_milli_blocks\":50}"));
        entries.add(new P8ProfileCatalogEntry(
                id("default_sound"),
                id("sound"),
                com.yo1no.gramarye.magic.presentation.api.ProfileChannel.SOUND,
                id("sound"),
                0,
                "{\"pitch_milli\":1000,\"sound\":"
                        + "\"minecraft:entity.experience_orb.pickup\","
                        + "\"volume_milli\":600}"));
        entries.add(new P8ProfileCatalogEntry(
                id("default_trail"),
                id("trail"),
                com.yo1no.gramarye.magic.presentation.api.ProfileChannel.TRAIL,
                id("trail"),
                0,
                "{\"lifetime_ticks\":16,\"particle\":\"minecraft:enchant\","
                        + "\"sample_interval_ticks\":2,\"segments\":8,"
                        + "\"size_milli_blocks\":200}"));
        var maximumType = exactLengthId("type", 128);
        var maximumConfiguration = maximumConfigurationJson();
        var channels = com.yo1no.gramarye.magic.presentation.api.ProfileChannel.values();
        for (var index = 0; index < 189; index++) {
            entries.add(new P8ProfileCatalogEntry(
                    exactLengthId(String.format(java.util.Locale.ROOT, "z%03d", index), 128),
                    maximumType,
                    channels[index % channels.length],
                    maximumType,
                    PresentationLimits.MAX_PROFILE_CONFIGURATION_VERSION,
                    maximumConfiguration));
        }
        entries.sort(Comparator.comparing(P8ProfileCatalogEntry::profileId));
        return new ProfileCatalogPayload(2L, entries);
    }

    private static String maximumConfigurationJson() {
        var values = new ArrayList<String>(16);
        for (var index = 0; index < 15; index++) {
            values.add("x".repeat(128));
        }
        values.add("x".repeat(70));
        var result = "{\"a\":[\"" + String.join("\",\"", values) + "\"]}";
        if (result.length() != 2_045) {
            throw new AssertionError("maximum configuration fixture has the wrong length");
        }
        return result;
    }

    private static PresentationEventPayload maximumLegalEventPayload() {
        var parameters = new LinkedHashMap<ResourceLocation, Integer>();
        for (var index = 0; index < PresentationLimits.MAX_EVENT_OVERRIDES; index++) {
            parameters.put(exactLengthId(Integer.toString(index), 32), index);
        }
        return new PresentationEventPayload(
                2L,
                PresentationEventKind.HIT,
                new PresentationSourceSummary(
                        OptionalInt.of(Integer.MAX_VALUE),
                        OptionalInt.of(Integer.MAX_VALUE)),
                exactLengthId("dimension", 128),
                new PresentationPosition(-30_000_000.0D, 2_048.0D, 30_000_000.0D),
                new PresentationDirection((short) 18_918, (short) 18_918, (short) 18_918),
                new PresentationAppearance(
                        Integer.MIN_VALUE,
                        Integer.MAX_VALUE,
                        PresentationLimits.MAX_INTENSITY_MILLI,
                        Optional.of(exactLengthId("sound", 128)),
                        Optional.of(exactLengthId("particle", 128)),
                        Optional.of(exactLengthId("trail", 128)),
                        parameters),
                Long.MIN_VALUE,
                Long.MAX_VALUE);
    }

    private static ResourceLocation exactLengthId(String distinguishing, int utf8Length) {
        int pathLength = utf8Length - 2;
        if (pathLength < distinguishing.length()) {
            throw new IllegalArgumentException("fixture identifier is longer than its bound");
        }
        var result = ResourceLocation.fromNamespaceAndPath(
                "x", "a".repeat(pathLength - distinguishing.length()) + distinguishing);
        if (result.toString().length() != utf8Length) {
            throw new AssertionError("maximum identifier fixture has the wrong length");
        }
        return result;
    }

    private static <T> T construct(
            Class<T> type, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("controlled P8 fixture construction failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("controlled P8 fixture construction failed", exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private record RuntimeFixture(RuntimeEvent event, RuntimeExecutionContext context) {}

    private record EncodedPayload(int packetBytes, CustomPacketPayload payload) {}

    private enum CaptureFailure {
        NONE,
        RUNTIME_EXCEPTION,
        ERROR
    }

    private enum SubmitMode {
        READY,
        UNAVAILABLE,
        RUNTIME_EXCEPTION,
        ERROR,
        NULL_RESULT
    }

    private enum CatalogSubmitMode {
        READY,
        RUNTIME_EXCEPTION,
        ERROR
    }

    private static final class ScriptedCatalogTransport implements P8CatalogTransport {
        private final Set<UUID> unavailablePlayers;
        private final List<String> submissionOrder;
        private final ArrayList<CatalogSubmitMode> modes;
        private final Error error = new AssertionError("same P8 catalog Error");
        private int canSubmitCalls;
        private int packetChargeCalls;
        private int submissionCount;

        private ScriptedCatalogTransport(
                Set<UUID> unavailablePlayers,
                List<String> submissionOrder,
                CatalogSubmitMode... modes) {
            this.unavailablePlayers = Set.copyOf(unavailablePlayers);
            this.submissionOrder = submissionOrder;
            this.modes = new ArrayList<>(List.of(modes));
        }

        @Override
        public boolean canSubmit(ServerPlayer player, ProfileCatalogPayload payload) {
            canSubmitCalls = Math.incrementExact(canSubmitCalls);
            return !unavailablePlayers.contains(player.getUUID());
        }

        @Override
        public int packetCharge(ServerPlayer player, ProfileCatalogPayload payload) {
            packetChargeCalls = Math.incrementExact(packetChargeCalls);
            return Math.addExact(
                    payload.bodySize(),
                    PresentationLimits.PROFILE_CATALOG_PACKET_OVERHEAD_BYTES);
        }

        @Override
        public void submit(ServerPlayer player, ProfileCatalogPayload payload) {
            submissionCount = Math.incrementExact(submissionCount);
            submissionOrder.add("catalog");
            CatalogSubmitMode mode = modes.removeFirst();
            if (mode == CatalogSubmitMode.RUNTIME_EXCEPTION) {
                throw new IllegalStateException("controlled P8 catalog RuntimeException");
            }
            if (mode == CatalogSubmitMode.ERROR) {
                throw error;
            }
        }
    }

    private static final class RecordingTransport implements P8PresentationTransport {
        private final UUID readyPlayer;
        private final List<String> submissionOrder;
        private final Error observerError = new AssertionError("same P8 observer Error");
        private final Error submitError = new AssertionError("same P8 transport Error");
        private final List<Long> submitAttempts = new ArrayList<>();
        private final List<Long> submittedSequences = new ArrayList<>();
        private CaptureFailure captureFailure = CaptureFailure.NONE;
        private int captureCalls;
        private long connectionEpoch = 1L;
        private List<SubmitMode> submitModes = List.of();
        private Runnable nextIsCurrentCallback;
        private Runnable nextSubmitCallback;

        private RecordingTransport(UUID readyPlayer) {
            this(readyPlayer, new ArrayList<>());
        }

        private RecordingTransport(UUID readyPlayer, List<String> submissionOrder) {
            this.readyPlayer = readyPlayer;
            this.submissionOrder = submissionOrder;
        }

        private void failSecondCaptureWith(CaptureFailure failure) {
            captureFailure = failure;
            captureCalls = 0;
        }

        private void clearCaptureFailure() {
            captureFailure = CaptureFailure.NONE;
            captureCalls = 0;
        }

        private void advanceConnectionEpoch() {
            connectionEpoch = 2L;
        }

        private void restoreConnectionEpoch() {
            connectionEpoch = 1L;
        }

        private void reenterOnNextIsCurrent(Runnable callback) {
            nextIsCurrentCallback = callback;
        }

        private void reenterOnNextSubmit(Runnable callback) {
            nextSubmitCallback = callback;
        }

        @Override
        public Optional<P8RecipientIdentity> captureReadyIdentity(
                ServerPlayer player, long catalogGeneration) {
            if (!player.getUUID().equals(readyPlayer)) {
                return Optional.empty();
            }
            captureCalls++;
            if (captureCalls == 2 && captureFailure == CaptureFailure.RUNTIME_EXCEPTION) {
                throw new IllegalStateException("controlled P8 observer RuntimeException");
            }
            if (captureCalls == 2 && captureFailure == CaptureFailure.ERROR) {
                throw observerError;
            }
            return Optional.of(new P8RecipientIdentity(readyPlayer, connectionEpoch));
        }

        @Override
        public boolean isCurrent(
                ServerPlayer player,
                P8RecipientIdentity identity,
                long catalogGeneration) {
            Runnable callback = nextIsCurrentCallback;
            nextIsCurrentCallback = null;
            if (callback != null) {
                callback.run();
            }
            return player.getUUID().equals(readyPlayer)
                    && identity.playerId().equals(readyPlayer)
                    && identity.connectionEpoch() == connectionEpoch;
        }

        @Override
        public P8PresentationSubmissionResult submit(
                P8RecipientIdentity identity, PresentationEvent event) {
            submitAttempts.add(event.sequence());
            submissionOrder.add("event");
            Runnable callback = nextSubmitCallback;
            nextSubmitCallback = null;
            if (callback != null) {
                callback.run();
            }
            SubmitMode submitMode = submitAttempts.size() <= submitModes.size()
                    ? submitModes.get(submitAttempts.size() - 1)
                    : SubmitMode.READY;
            if (submitMode == SubmitMode.UNAVAILABLE) {
                return P8PresentationSubmissionResult.UNAVAILABLE;
            }
            if (submitMode == SubmitMode.RUNTIME_EXCEPTION) {
                throw new IllegalStateException("controlled P8 transport RuntimeException");
            }
            if (submitMode == SubmitMode.ERROR) {
                throw submitError;
            }
            if (submitMode == SubmitMode.NULL_RESULT) {
                return null;
            }
            submittedSequences.add(event.sequence());
            return P8PresentationSubmissionResult.SUBMITTED;
        }
    }

    private record FixtureTriggerPayload(int value) implements TriggerPayload {
        private static final MapCodec<FixtureTriggerPayload> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("value").forGetter(FixtureTriggerPayload::value))
                        .apply(instance, FixtureTriggerPayload::new));
    }

    private record FixtureActionPayload(int value) implements ActionPayload {
        private static final MapCodec<FixtureActionPayload> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Codec.INT.fieldOf("value").forGetter(FixtureActionPayload::value))
                        .apply(instance, FixtureActionPayload::new));
    }

    private static final class FixtureTriggerType implements TriggerType<FixtureTriggerPayload> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(ACTIVE_CAST)),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<FixtureTriggerPayload> payloadCodec() {
            return FixtureTriggerPayload.CODEC;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                FixtureTriggerPayload payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    private static final class FixtureActionType implements ActionType<FixtureActionPayload> {
        private static final ActionCapabilities CAPABILITIES = new ActionCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ControlClass.NONE,
                AppearanceParameterPolicy.none());

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<FixtureActionPayload> payloadCodec() {
            return FixtureActionPayload.CODEC;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(
                FixtureActionPayload payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }
}
